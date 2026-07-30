package pt.servimatch.modules.proposals.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.proposals.ProposalsApi;
import pt.servimatch.modules.proposals.ProposalStatus;
import pt.servimatch.modules.proposals.ProposalAccepted;
import pt.servimatch.modules.proposals.ProposalSent;
import pt.servimatch.modules.proposals.internal.web.CreateProposalRequest;
import pt.servimatch.modules.proposals.internal.web.MoneyDto;
import pt.servimatch.modules.proposals.internal.web.PageMetaDto;
import pt.servimatch.modules.proposals.internal.web.ProposalDto;
import pt.servimatch.modules.proposals.internal.web.ProposalPageDto;
import pt.servimatch.modules.proposals.internal.web.ProviderSummaryDto;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.requests.ServiceRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class ProposalsService implements ProposalsApi {

    private final ProposalRepository repository;
    private final RequestsApi requestsApi;
    private final ProvidersApi providersApi;
    private final ApplicationEventPublisher events;

    ProposalsService(ProposalRepository repository, RequestsApi requestsApi, ProvidersApi providersApi,
                      ApplicationEventPublisher events) {
        this.repository = repository;
        this.requestsApi = requestsApi;
        this.providersApi = providersApi;
        this.events = events;
    }

    @Override
    public Optional<ProposalView> findById(UUID proposalId) {
        return repository.findById(proposalId)
                .map(row -> new ProposalView(row.id(), row.requestId(), row.providerId(), ProposalStatus.valueOf(row.status())));
    }

    @Transactional
    ProposalDto create(UUID requestId, UUID providerUserId, CreateProposalRequest request) {
        UUID providerId = providersApi.ensureProvisioned(providerUserId);
        ProvidersApi.ProviderEligibility eligibility = providersApi.checkEligibility(providerId).orElseThrow();
        if (!eligibility.isEligible()) {
            throw Problems.subscriptionRequired("É necessária uma subscrição ativa e perfil aprovado para enviar propostas.");
        }

        RequestsApi.RequestSummary requestSummary = requestsApi.get(requestId)
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        if (requestSummary.status() != ServiceRequestStatus.PUBLISHED && requestSummary.status() != ServiceRequestStatus.IN_NEGOTIATION) {
            throw Problems.conflict("Pedido não aceita propostas no estado atual (" + requestSummary.status() + ").");
        }

        repository.cancelActiveForResend(requestId, providerId);
        UUID proposalId = repository.insert(
                requestId, providerId, request.price().amountCents(), request.price().currency(),
                request.description(), request.leadTimeDays(), request.validUntil());

        requestsApi.markInNegotiation(requestId);
        events.publishEvent(new ProposalSent(proposalId, requestId, providerId, Instant.now()));

        return toDto(repository.findById(proposalId).orElseThrow());
    }

    /**
     * {@code GET /v1/proposals/me} — propostas enviadas pelo prestador
     * autenticado. Dono resolvido pelo controlador a partir do {@code sub}
     * do JWT; filtro por dono <b>sempre em SQL</b>
     * ({@link ProposalRepository#findPageForProvider}), nunca em memória.
     *
     * <p><b>Sem perfil de prestador → página vazia, não 403.</b> Um
     * {@code ROLE_PROVIDER} que nunca chegou a enviar propostas também nunca
     * tem {@code provider_profile}; listar o que (ainda) não existe não é
     * diferente de "zero resultados". {@code providerId} vem de
     * {@link ProvidersApi#findProviderIdByUserId} — não de
     * {@code ensureProvisioned} — para uma leitura não criar um perfil de
     * prestador como efeito lateral (mesmo raciocínio de
     * {@code RequestsController#getRequest}).
     *
     * <p><b>Sem gating de subscrição aqui, deliberadamente.</b> O ADR-0011
     * (D2) separa duas perguntas: P1 "este prestador pode operar?" — porta
     * de entrada de <em>escrever</em> (enviar proposta) e de
     * <em>descobrir</em> (inbox, pesquisa) — de "ver o que já é meu", que
     * este endpoint é. O CLAUDE.md deste agente é explícito: "o gating
     * limita escrever e descobrir, não ler o que já é teu". Uma proposta já
     * enviada não deixa de ser do prestador que a enviou só porque a
     * subscrição entretanto expirou (o cliente que a recebeu continua a
     * poder aceitá-la — {@code accept} não depende de {@code checkEligibility}
     * do lado do prestador); negar-lhe a leitura seria esconder-lhe o
     * próprio histórico, não impedir uma ação nova.
     */
    ProposalPageDto listMine(UUID userId, String cursor, int limit) {
        Optional<UUID> providerId = providersApi.findProviderIdByUserId(userId);
        if (providerId.isEmpty()) {
            return new ProposalPageDto(List.of(), new PageMetaDto(null));
        }

        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);
        List<ProposalRow> rows = repository.findPageForProvider(
                providerId.get(), after == null ? null : after.createdAt(), after == null ? null : after.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ProposalRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;

        // Toda a página é do mesmo prestador (o autenticado): um único
        // ProviderSummary resolvido uma vez, nunca por linha (CLAUDE.md —
        // ausência de N+1). Ao contrário de listForRequest, onde cada linha
        // pode ser de um prestador diferente e por isso continua, por agora,
        // uma leitura de summary por linha (ProvidersApi ainda não expõe
        // summary em lote — ver relatório de entrega, lacuna pré-existente,
        // não introduzida por este endpoint).
        ProviderSummaryDto summary = providersApi.summary(providerId.get())
                .map(s -> new ProviderSummaryDto(
                        s.id(), s.displayName(), s.headline(), s.companyName(), s.ratingAvg(), s.ratingCount(),
                        s.verified(), s.premiumBadge(), s.avatarUrl()))
                .orElse(null);

        List<ProposalDto> items = page.stream().map(row -> toDto(row, summary)).toList();
        return new ProposalPageDto(items, new PageMetaDto(nextCursor));
    }

    ProposalPageDto listForRequest(UUID requestId, UUID viewerId, boolean isAdmin, String cursor, int limit) {
        RequestsApi.RequestSummary requestSummary = requestsApi.get(requestId)
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        if (!isAdmin && !requestSummary.customerId().equals(viewerId)) {
            throw Problems.forbidden("Só o dono do pedido pode ver as propostas recebidas.");
        }

        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);
        List<ProposalRow> rows = repository.findPage(
                requestId, after == null ? null : after.createdAt(), after == null ? null : after.id(), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ProposalRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id())
                : null;

        return new ProposalPageDto(page.stream().map(this::toDto).toList(), new PageMetaDto(nextCursor));
    }

    /**
     * Aceitar proposta (CLAUDE.md — transição central, única transação): a
     * ordem importa — confirma o pedido (CAS) <b>antes</b> de tocar na
     * proposta e nas concorrentes, para que duas aceitações concorrentes
     * (propostas diferentes do mesmo pedido) disputem sempre o mesmo
     * recurso primeiro (a linha de {@code service_request}) em vez de
     * acabarem à espera uma da outra em sentidos opostos (evita deadlock;
     * ver relatório de entrega para a análise completa).
     */
    @Transactional
    ProposalDto accept(UUID proposalId, UUID customerId) {
        ProposalRow proposal = repository.findById(proposalId)
                .orElseThrow(() -> Problems.notFound("Proposta não encontrada."));
        RequestsApi.RequestSummary requestSummary = requestsApi.get(proposal.requestId())
                .orElseThrow(() -> Problems.notFound("Pedido não encontrado."));
        if (!requestSummary.customerId().equals(customerId)) {
            throw Problems.forbidden("Só o dono do pedido pode aceitar uma proposta.");
        }

        requestsApi.confirm(proposal.requestId());

        boolean accepted = repository.accept(proposalId);
        if (!accepted) {
            throw Problems.conflict("Proposta já não está em SENT (foi aceite, rejeitada, cancelada ou expirou).");
        }
        repository.supersedeOthers(proposal.requestId(), proposalId);

        // A Booking é criada pelo módulo bookings a reagir a este evento
        // (@ApplicationModuleListener), não por chamada síncrona daqui — ver
        // nota em ProposalAccepted (evita ciclo de módulos bookings<->proposals).
        UUID providerUserId = providersApi.findUserIdByProviderId(proposal.providerId()).orElse(null);

        events.publishEvent(new ProposalAccepted(
                proposalId, proposal.requestId(), customerId, proposal.providerId(), providerUserId, Instant.now()));

        return toDto(repository.findById(proposalId).orElseThrow());
    }

    private ProposalDto toDto(ProposalRow row) {
        ProviderSummaryDto summary = providersApi.summary(row.providerId())
                .map(s -> new ProviderSummaryDto(
                        s.id(), s.displayName(), s.headline(), s.companyName(), s.ratingAvg(), s.ratingCount(),
                        s.verified(), s.premiumBadge(), s.avatarUrl()))
                .orElse(null);
        return toDto(row, summary);
    }

    /** Variante para {@link #listMine}, com o {@code ProviderSummaryDto} já resolvido fora do loop (ver o seu javadoc). */
    private ProposalDto toDto(ProposalRow row, ProviderSummaryDto summary) {
        return new ProposalDto(
                row.id(), row.requestId(), row.providerId(), summary,
                new MoneyDto(row.priceAmountCents(), row.priceCurrency()),
                row.description(), row.leadTimeDays(), row.validUntil(), row.status(), row.createdAt());
    }
}

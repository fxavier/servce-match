package pt.servimatch.modules.proposals.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.proposals.ProposalAccepted;
import pt.servimatch.modules.proposals.internal.web.CreateProposalRequest;
import pt.servimatch.modules.proposals.internal.web.MoneyDto;
import pt.servimatch.modules.proposals.internal.web.ProposalDto;
import pt.servimatch.modules.proposals.internal.web.ProposalPageDto;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.requests.ServiceRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Núcleo de negócio de {@code proposals}: gating de subscrição
 * (CLAUDE.md — "verificado no servidor, sempre") e a transição central de
 * aceitação, incluindo a resolução de corrida por CAS em vez de exceção
 * genérica (CLAUDE.md — "resolve com bloqueio otimista e testa esse
 * cenário").
 */
@ExtendWith(MockitoExtension.class)
class ProposalsServiceTest {

    @Mock
    private ProposalRepository repository;
    @Mock
    private RequestsApi requestsApi;
    @Mock
    private ProvidersApi providersApi;

    private ApplicationEventPublisher events;
    private ProposalsService service;

    private final UUID requestId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerUserId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        events = mock(ApplicationEventPublisher.class);
        service = new ProposalsService(repository, requestsApi, providersApi, events);
    }

    @Test
    void createRejectsProviderWithoutActiveSubscription() {
        when(providersApi.ensureProvisioned(providerUserId)).thenReturn(providerId);
        when(providersApi.checkEligibility(providerId))
                .thenReturn(Optional.of(new ProvidersApi.ProviderEligibility(providerId, true, false)));

        CreateProposalRequest request = new CreateProposalRequest(new MoneyDto(1000L, "EUR"), "desc", null, null);

        assertThatThrownBy(() -> service.create(requestId, providerUserId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(requestsApi, never()).get(any());
    }

    @Test
    void acceptHappyPathConfirmsRequestAndPublishesEvent() {
        ProposalRow proposal = new ProposalRow(
                proposalIdFixture(), requestId, providerId, 5000L, "EUR", "desc", 3, null, "SENT", Instant.now());
        when(repository.findById(proposal.id())).thenReturn(Optional.of(proposal));
        when(requestsApi.get(requestId))
                .thenReturn(Optional.of(new RequestsApi.RequestSummary(requestId, customerId, UUID.randomUUID(), ServiceRequestStatus.IN_NEGOTIATION)));
        when(repository.accept(proposal.id())).thenReturn(true);
        when(providersApi.findUserIdByProviderId(providerId)).thenReturn(Optional.of(providerUserId));
        when(providersApi.summary(providerId)).thenReturn(Optional.empty());

        service.accept(proposal.id(), customerId);

        verify(requestsApi).confirm(requestId);
        verify(repository).supersedeOthers(requestId, proposal.id());
        verify(events).publishEvent(any(ProposalAccepted.class));
    }

    @Test
    void acceptFailsWithConflictWhenProposalLostTheRaceToAnotherAcceptance() {
        ProposalRow proposal = new ProposalRow(
                proposalIdFixture(), requestId, providerId, 5000L, "EUR", "desc", 3, null, "SENT", Instant.now());
        when(repository.findById(proposal.id())).thenReturn(Optional.of(proposal));
        when(requestsApi.get(requestId))
                .thenReturn(Optional.of(new RequestsApi.RequestSummary(requestId, customerId, UUID.randomUUID(), ServiceRequestStatus.IN_NEGOTIATION)));
        // CAS falha: outra proposta do mesmo pedido já a confirmou entretanto.
        when(repository.accept(proposal.id())).thenReturn(false);

        assertThatThrownBy(() -> service.accept(proposal.id(), customerId))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(repository, never()).supersedeOthers(any(), any());
        verify(events, never()).publishEvent(any(ProposalAccepted.class));
    }

    @Test
    void acceptRejectsNonOwnerOfRequest() {
        ProposalRow proposal = new ProposalRow(
                proposalIdFixture(), requestId, providerId, 5000L, "EUR", "desc", 3, null, "SENT", Instant.now());
        when(repository.findById(proposal.id())).thenReturn(Optional.of(proposal));
        UUID someoneElse = UUID.randomUUID();
        when(requestsApi.get(requestId))
                .thenReturn(Optional.of(new RequestsApi.RequestSummary(requestId, someoneElse, UUID.randomUUID(), ServiceRequestStatus.IN_NEGOTIATION)));

        assertThatThrownBy(() -> service.accept(proposal.id(), customerId))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(requestsApi, never()).confirm(any());
    }

    // ---------------------------------------------------------------- listMine (GET /v1/proposals/me)

    /**
     * Sem perfil de prestador → página vazia, não 403 (ver javadoc de
     * {@code ProposalsService#listMine}): uma leitura nunca cria um perfil
     * de prestador como efeito lateral.
     */
    @Test
    void listMineReturnsEmptyPageWhenAuthenticatedUserHasNoProviderProfile() {
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.empty());

        ProposalPageDto page = service.listMine(providerUserId, null, 20);

        assertThat(page.items()).isEmpty();
        verify(repository, never()).findPageForProvider(any(), any(), any(), anyInt());
    }

    /** Isolamento: as propostas devolvidas são sempre filtradas em SQL pelo provider_id do autenticado. */
    @Test
    void listMineDelegatesOwnerToTheRepository() {
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        ProposalRow proposal = new ProposalRow(
                proposalIdFixture(), requestId, providerId, 5000L, "EUR", "desc", 3, null, "SENT", Instant.now());
        when(repository.findPageForProvider(eq(providerId), any(), any(), eq(21))).thenReturn(List.of(proposal));
        when(providersApi.summary(providerId)).thenReturn(Optional.empty());

        ProposalPageDto page = service.listMine(providerUserId, null, 20);

        assertThat(page.items()).extracting(ProposalDto::providerId).containsExactly(providerId);
        verify(repository).findPageForProvider(eq(providerId), any(), any(), eq(21));
    }

    /** Página inteira do mesmo prestador: um único ProviderSummary resolvido, nunca um por linha (ausência de N+1). */
    @Test
    void listMineResolvesProviderSummaryOnceForTheWholePage() {
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        ProposalRow proposalA = new ProposalRow(
                proposalIdFixture(), requestId, providerId, 5000L, "EUR", "desc", 3, null, "SENT", Instant.now());
        ProposalRow proposalB = new ProposalRow(
                proposalIdFixture(), UUID.randomUUID(), providerId, 6000L, "EUR", "desc", 3, null, "SENT", Instant.now());
        when(repository.findPageForProvider(eq(providerId), any(), any(), eq(21))).thenReturn(List.of(proposalA, proposalB));
        when(providersApi.summary(providerId)).thenReturn(Optional.empty());

        service.listMine(providerUserId, null, 20);

        verify(providersApi, org.mockito.Mockito.times(1)).summary(providerId);
    }

    private static UUID proposalIdFixture() {
        return UUID.randomUUID();
    }
}

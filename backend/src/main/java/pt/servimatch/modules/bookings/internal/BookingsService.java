package pt.servimatch.modules.bookings.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.bookings.BookingStatus;
import pt.servimatch.modules.bookings.BookingsApi;
import pt.servimatch.modules.bookings.internal.web.BookingDetailDto;
import pt.servimatch.modules.bookings.internal.web.BookingDto;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.proposals.ProposalsApi;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.users.UsersApi;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class BookingsService implements BookingsApi {

    private final BookingRepository repository;
    private final ProposalsApi proposalsApi;
    private final RequestsApi requestsApi;
    private final ProvidersApi providersApi;
    private final UsersApi usersApi;

    BookingsService(BookingRepository repository, ProposalsApi proposalsApi, RequestsApi requestsApi,
                     ProvidersApi providersApi, UsersApi usersApi) {
        this.repository = repository;
        this.proposalsApi = proposalsApi;
        this.requestsApi = requestsApi;
        this.providersApi = providersApi;
        this.usersApi = usersApi;
    }

    @Override
    @Transactional
    public UUID createFromProposal(UUID proposalId) {
        return repository.insertIfAbsent(proposalId);
    }

    @Override
    public Optional<BookingView> findById(UUID bookingId) {
        return repository.findById(bookingId).map(this::toView);
    }

    @Override
    public Optional<BookingParticipants> participants(UUID bookingId) {
        return repository.findById(bookingId).flatMap(booking -> resolveParticipants(booking.proposalId())
                .map(p -> new BookingParticipants(bookingId, BookingStatus.valueOf(booking.status()), p.customerId(), p.providerUserId())));
    }

    /** Usado pelo controller deste módulo (completeBooking). */
    @Transactional
    BookingDto complete(UUID bookingId, UUID requesterUserId) {
        BookingRow booking = repository.findById(bookingId)
                .orElseThrow(() -> Problems.notFound("Marcação não encontrada."));
        ParticipantIds ids = resolveParticipants(booking.proposalId())
                .orElseThrow(() -> Problems.notFound("Não foi possível resolver os participantes da marcação."));
        if (!requesterUserId.equals(ids.customerId()) && !requesterUserId.equals(ids.providerUserId())) {
            throw Problems.forbidden("Só um participante da marcação a pode concluir.");
        }
        if (!repository.complete(bookingId)) {
            throw Problems.conflict("Marcação não está num estado que permita concluir (status atual: " + booking.status() + ").");
        }
        return toDto(repository.findById(bookingId).orElseThrow());
    }

    /**
     * {@code GET /v1/bookings/{bookingId}} (ecrã de avaliação). Autorização:
     * <b>404</b> se a marcação não existir (endpoint autenticado, mas a
     * existência do id não é, por si só, informação sensível — segue o
     * mesmo padrão de {@code complete}), <b>403</b> se existir mas o
     * autenticado não for nenhum dos dois participantes (o contrato declara
     * explicitamente este código para este endpoint, ao contrário de
     * {@code GET /v1/providers/{id}}, que é público e usa só 404 para não
     * servir de oráculo de existência a terceiros).
     *
     * <p>{@code counterpartUserId} resolve-se a partir dos mesmos valores
     * que a verificação de participação já leu ({@link ParticipantIds}),
     * sem nenhuma consulta adicional — é exatamente a tradução
     * {@code provider_profile.id → users.id} que falta do lado do cliente
     * para poder preencher {@code CreateReview.targetId} (ver javadoc de
     * {@code BookingDetailDto}).
     *
     * <p><b>{@code canReview} degradado (bloqueio conhecido):</b> deveria
     * ser {@code status == COMPLETED && ainda não avaliado por este autor},
     * mas {@code bookings} não pode consultar {@code review} — isso criaria
     * um ciclo de módulos ({@code reviews} já depende de {@code bookings}
     * via {@code BookingsApi}; {@code bookings → reviews} fecharia o
     * ciclo, o {@code allowedDependencies} de {@code bookings}
     * (package-info, backend-platform) não inclui {@code reviews}). Por
     * omissão de autorização explícita do {@code arquiteto} (ADR-0010 é
     * sobre SQL entre módulos, não cobre este caso, que é uma dependência
     * Java em ciclo), a regra fica degradada aqui de propósito, não em
     * silêncio: {@code canReview} reflete só o estado da marcação. A
     * duplicação continua impedida no servidor por
     * {@code uq_review_booking_author} — {@code POST /v1/reviews} devolve
     * {@code 409} numa segunda tentativa; só o botão na UI pode ficar
     * visível indevidamente até essa chamada. Ver relatório de entrega para
     * as duas soluções propostas (evento {@code ReviewCreated} consumido
     * por {@code bookings}, ou uma exceção ADR-0010 para um {@code EXISTS}
     * em SQL).
     */
    @Transactional(readOnly = true)
    BookingDetailDto detail(UUID bookingId, UUID requesterUserId) {
        BookingRow booking = repository.findById(bookingId)
                .orElseThrow(() -> Problems.notFound("Marcação não encontrada."));
        ParticipantIds ids = resolveParticipants(booking.proposalId())
                .orElseThrow(() -> Problems.notFound("Não foi possível resolver os participantes da marcação."));
        if (!requesterUserId.equals(ids.customerId()) && !requesterUserId.equals(ids.providerUserId())) {
            throw Problems.forbidden("Só um participante da marcação pode consultar o seu detalhe.");
        }

        UUID counterpartUserId = requesterUserId.equals(ids.customerId()) ? ids.providerUserId() : ids.customerId();
        String counterpartName = usersApi.findByIds(Set.of(counterpartUserId))
                .getOrDefault(counterpartUserId, new UsersApi.UserSummaryView(counterpartUserId, ""))
                .displayName();
        String requestTitle = requestsApi.findTitlesByIds(Set.of(ids.requestId())).getOrDefault(ids.requestId(), "");
        boolean canReview = BookingStatus.valueOf(booking.status()) == BookingStatus.COMPLETED;

        return new BookingDetailDto(
                booking.id(), booking.proposalId(), booking.scheduledStart(), booking.scheduledEnd(),
                booking.status(), booking.completedAt(), requestTitle, counterpartUserId, counterpartName, canReview);
    }

    private record ParticipantIds(UUID requestId, UUID customerId, UUID providerUserId) {
    }

    private Optional<ParticipantIds> resolveParticipants(UUID proposalId) {
        return proposalsApi.findById(proposalId).flatMap(proposal ->
                requestsApi.get(proposal.requestId()).flatMap(request ->
                        providersApi.findUserIdByProviderId(proposal.providerId())
                                .map(providerUserId -> new ParticipantIds(request.id(), request.customerId(), providerUserId))));
    }

    private BookingView toView(BookingRow row) {
        return new BookingView(row.id(), row.proposalId(), BookingStatus.valueOf(row.status()), row.completedAt());
    }

    private BookingDto toDto(BookingRow row) {
        return new BookingDto(row.id(), row.proposalId(), row.scheduledStart(), row.scheduledEnd(), row.status(), row.completedAt());
    }
}

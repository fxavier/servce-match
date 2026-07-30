package pt.servimatch.modules.chat.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.chat.internal.web.ConversationPageDto;
import pt.servimatch.modules.chat.internal.web.ConversationSummaryDto;
import pt.servimatch.modules.chat.internal.web.CreateMessageRequest;
import pt.servimatch.modules.chat.internal.web.ImageRefDto;
import pt.servimatch.modules.chat.internal.web.MessageDto;
import pt.servimatch.modules.chat.internal.web.MessagePageDto;
import pt.servimatch.modules.chat.internal.web.PageMetaDto;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.uploads.ImageRef;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import pt.servimatch.modules.users.UsersApi;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Núcleo de negócio de {@code chat}: autorização por participante e o
 * <em>gating</em> por subscrição do prestador (ARQUITETURA §3.3 — conversas
 * já em curso ficam <em>read-only</em> para o prestador quando a subscrição
 * expira, o cliente escreve sempre). A {@code Conversation} nasce de
 * {@code ProposalAccepted} via {@link ProposalAcceptedListener}, nunca por
 * chamada direta a partir de {@code proposals}.
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Service
@Lazy
class ChatService {

    private final ConversationRepository repository;
    private final UploadsApi uploadsApi;
    private final ProvidersApi providersApi;
    private final UsersApi usersApi;
    private final RequestsApi requestsApi;

    ChatService(ConversationRepository repository, UploadsApi uploadsApi, ProvidersApi providersApi,
                UsersApi usersApi, RequestsApi requestsApi) {
        this.repository = repository;
        this.uploadsApi = uploadsApi;
        this.providersApi = providersApi;
        this.usersApi = usersApi;
        this.requestsApi = requestsApi;
    }

    /**
     * {@code GET /v1/conversations}: conversas do autenticado, mais recente
     * primeiro. Autorização feita no {@code WHERE} de
     * {@link ConversationRepository#findPageForParticipant} — nunca carrega
     * conversas de outro participante para as filtrar aqui.
     *
     * <p>Resolução de nomes/títulos <b>em lote por página</b>, nunca num
     * ciclo de chamadas singulares: um único {@link UsersApi#findByIds} para
     * os interlocutores e um único
     * {@link RequestsApi#findTitlesByIds(java.util.Collection)} para os
     * títulos dos pedidos, ambos com o conjunto de ids <em>desta página</em>
     * — nunca um por linha. {@code counterpartAvatarSeed} é o
     * {@code users.id} do interlocutor (determinístico, sem PII) — nunca o
     * nome, que aqui fica sempre visível ao próprio participante mas não faz
     * sentido reutilizar como semente exposta.
     */
    @Transactional(readOnly = true)
    ConversationPageDto listConversations(UUID viewerId, String cursor, int limit) {
        UUID viewerProviderId = providersApi.findProviderIdByUserId(viewerId).orElse(null);
        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);

        List<ConversationSummaryRow> rows = repository.findPageForParticipant(viewerId, viewerProviderId, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ConversationSummaryRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).sortAt(), page.get(page.size() - 1).id())
                : null;

        // Interlocutor: quando o autenticado é o cliente, o interlocutor é o
        // utilizador por trás do provider_profile da conversa — providerId é
        // um provider_profile.id, não um users.id (mesma distinção que
        // BookingDetail.counterpartUserId documenta no contrato). Quando o
        // autenticado é o prestador, customer_id já É o users.id.
        Set<UUID> counterpartProviderIds = new HashSet<>();
        for (ConversationSummaryRow row : page) {
            if (row.customerId().equals(viewerId)) {
                counterpartProviderIds.add(row.providerId());
            }
        }
        Map<UUID, UUID> providerUserIds = providersApi.findUserIdsByProviderIds(counterpartProviderIds);

        Set<UUID> counterpartUserIds = new HashSet<>();
        for (ConversationSummaryRow row : page) {
            counterpartUserIds.add(counterpartUserId(row, viewerId, providerUserIds));
        }
        Map<UUID, UsersApi.UserSummaryView> names = usersApi.findByIds(counterpartUserIds);

        Set<UUID> requestIds = page.stream().map(ConversationSummaryRow::requestId).collect(Collectors.toSet());
        Map<UUID, String> titles = requestsApi.findTitlesByIds(requestIds);

        List<ConversationSummaryDto> items = page.stream()
                .map(row -> toSummaryDto(row, viewerId, providerUserIds, names, titles))
                .toList();
        return new ConversationPageDto(items, new PageMetaDto(nextCursor));
    }

    private static UUID counterpartUserId(ConversationSummaryRow row, UUID viewerId, Map<UUID, UUID> providerUserIds) {
        if (row.customerId().equals(viewerId)) {
            return providerUserIds.get(row.providerId());
        }
        return row.customerId();
    }

    private ConversationSummaryDto toSummaryDto(ConversationSummaryRow row, UUID viewerId, Map<UUID, UUID> providerUserIds,
                                                 Map<UUID, UsersApi.UserSummaryView> names, Map<UUID, String> titles) {
        UUID counterpartUserId = counterpartUserId(row, viewerId, providerUserIds);
        String counterpartName = counterpartUserId == null ? "" : names.getOrDefault(
                counterpartUserId, new UsersApi.UserSummaryView(counterpartUserId, "")).displayName();
        String counterpartAvatarSeed = counterpartUserId == null ? row.id().toString() : counterpartUserId.toString();
        return new ConversationSummaryDto(
                row.id(),
                counterpartName,
                counterpartAvatarSeed,
                row.lastMessagePreview(),
                row.lastMessageAt(),
                row.unreadCount(),
                titles.getOrDefault(row.requestId(), ""));
    }

    /** Chamado por {@link ProposalAcceptedListener}; idempotente (ver {@link ConversationRepository#createIfAbsent}). */
    @Transactional
    UUID ensureConversation(UUID requestId, UUID customerId, UUID providerId) {
        return repository.createIfAbsent(requestId, customerId, providerId);
    }

    @Transactional(readOnly = true)
    MessagePageDto listMessages(UUID conversationId, UUID viewerId, String cursor, int limit) {
        ConversationRow conversation = findConversation(conversationId);
        requireParticipant(conversation, viewerId);

        CursorCodec.Position after = CursorCodec.decode(cursor).orElse(null);
        List<MessageRow> rows = repository.findPage(conversationId, after, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<MessageRow> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? CursorCodec.encode(page.get(page.size() - 1).sentAt(), page.get(page.size() - 1).id())
                : null;

        Map<UUID, List<ImageRefDto>> attachmentsByMessage = resolveAttachments(page.stream().map(MessageRow::id).toList());
        List<MessageDto> items = page.stream()
                .map(row -> toDto(row, attachmentsByMessage.getOrDefault(row.id(), List.of())))
                .toList();

        return new MessagePageDto(items, new PageMetaDto(nextCursor));
    }

    /**
     * {@code sendMessage}: o cliente escreve sempre (CLAUDE.md §3.3 — "o
     * cliente continua a poder escrever"); o prestador só se estiver
     * elegível ({@link ProvidersApi.ProviderEligibility#isEligible()} —
     * aprovado e visível, o mesmo predicado usado por {@code proposals} e
     * {@code requests} para o mesmo gating por subscrição, ARQUITETURA
     * §3.3) nesse preciso instante — sem isso, uma conversa já existente
     * fica <em>read-only</em> para ele, e a mensagem é recusada com
     * {@code 403 subscription-required}, nunca criada.
     */
    @Transactional
    MessageDto sendMessage(UUID conversationId, UUID senderId, CreateMessageRequest request) {
        ConversationRow conversation = findConversation(conversationId);
        ParticipantRole role = requireParticipant(conversation, senderId);
        if (role == ParticipantRole.PROVIDER && !isProviderEligible(conversation.providerId())) {
            throw Problems.subscriptionRequired(
                    "Subscrição inativa: esta conversa está em modo só de leitura para o prestador.");
        }

        List<UUID> attachmentIds = request.attachmentIds() == null ? List.of() : request.attachmentIds();
        // confirmOwnedUpload valida posse, purpose e magic bytes, e lança 422
        // sozinha em qualquer falha (ver nota equivalente em
        // requests.internal.RequestsService.createDraft).
        for (UUID imageId : attachmentIds) {
            uploadsApi.confirmOwnedUpload(imageId, senderId, UploadPurpose.MESSAGE_ATTACHMENT);
        }

        UUID messageId = repository.insertMessage(conversationId, senderId, request.body());
        if (!attachmentIds.isEmpty()) {
            repository.linkAttachments(messageId, attachmentIds);
        }

        MessageRow row = repository.findMessageById(messageId).orElseThrow();
        Map<UUID, List<ImageRefDto>> attachmentsByMessage = resolveAttachments(List.of(messageId));
        return toDto(row, attachmentsByMessage.getOrDefault(messageId, List.of()));
    }

    private ConversationRow findConversation(UUID conversationId) {
        return repository.findById(conversationId)
                .orElseThrow(() -> Problems.notFound("Conversa não encontrada."));
    }

    private ParticipantRole requireParticipant(ConversationRow conversation, UUID viewerUserId) {
        if (conversation.customerId().equals(viewerUserId)) {
            return ParticipantRole.CUSTOMER;
        }
        boolean isProviderParticipant = providersApi.findProviderIdByUserId(viewerUserId)
                .map(providerId -> providerId.equals(conversation.providerId()))
                .orElse(false);
        if (isProviderParticipant) {
            return ParticipantRole.PROVIDER;
        }
        throw Problems.forbidden("Só os participantes da conversa podem aceder às mensagens.");
    }

    /**
     * Elegibilidade do prestador para escrever (ARQUITETURA §3.3): aprovado
     * <b>e</b> visível, não só visível — mesmo predicado que
     * {@code proposals.internal.ProposalsService} e
     * {@code requests.internal.RequestsController} aplicam via
     * {@link ProvidersApi.ProviderEligibility#isEligible()}. Um prestador
     * aprovado mas escondido por subscrição inativa, ou visível mas ainda
     * pendente de aprovação administrativa, não deve poder escrever.
     */
    private boolean isProviderEligible(UUID providerId) {
        return providersApi.checkEligibility(providerId)
                .map(ProvidersApi.ProviderEligibility::isEligible)
                .orElse(false);
    }

    /**
     * Resolve os anexos de um lote de mensagens num único
     * {@code UploadsApi#resolve} — nunca pedido a pedido (mesmo princípio
     * <em>set-based</em> de {@code MatchingApi#filterEligibleRequestIds},
     * ver relatório de entrega).
     */
    private Map<UUID, List<ImageRefDto>> resolveAttachments(List<UUID> messageIds) {
        List<MessageAttachmentRow> links = repository.findAttachmentsForMessages(messageIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        List<UUID> imageIds = links.stream().map(MessageAttachmentRow::imageAssetId).distinct().toList();
        Map<UUID, ImageRef> resolved = uploadsApi.resolve(imageIds).stream()
                .collect(Collectors.toMap(ImageRef::id, ref -> ref));

        Map<UUID, List<ImageRefDto>> byMessage = new java.util.LinkedHashMap<>();
        links.stream()
                .sorted(Comparator.comparingInt(MessageAttachmentRow::position))
                .forEach(link -> {
                    ImageRef ref = resolved.get(link.imageAssetId());
                    if (ref != null) {
                        byMessage.computeIfAbsent(link.messageId(), id -> new java.util.ArrayList<>())
                                .add(new ImageRefDto(ref.id(), ref.url(), ref.contentType()));
                    }
                });
        return byMessage;
    }

    private static MessageDto toDto(MessageRow row, List<ImageRefDto> attachments) {
        return new MessageDto(row.id(), row.conversationId(), row.senderId(), row.body(), attachments, row.sentAt(), row.readAt());
    }
}

package pt.servimatch.modules.chat.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.chat.internal.web.CreateMessageRequest;
import pt.servimatch.modules.chat.internal.web.ImageRefDto;
import pt.servimatch.modules.chat.internal.web.MessageDto;
import pt.servimatch.modules.chat.internal.web.MessagePageDto;
import pt.servimatch.modules.chat.internal.web.PageMetaDto;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.uploads.ImageRef;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    ChatService(ConversationRepository repository, UploadsApi uploadsApi, ProvidersApi providersApi) {
        this.repository = repository;
        this.uploadsApi = uploadsApi;
        this.providersApi = providersApi;
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

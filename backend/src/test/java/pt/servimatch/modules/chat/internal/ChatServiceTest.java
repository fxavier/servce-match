package pt.servimatch.modules.chat.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.chat.internal.web.ConversationPageDto;
import pt.servimatch.modules.chat.internal.web.CreateMessageRequest;
import pt.servimatch.modules.chat.internal.web.MessageDto;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.requests.RequestsApi;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import pt.servimatch.modules.users.UsersApi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Autorização por participante e <em>gating</em> de subscrição de
 * {@code chat} (ARQUITETURA §3.3): o caminho principal (participante lê/
 * escreve) e os casos de erro que interessam — não-participante recusado,
 * prestador não elegível recusado a escrever numa conversa já existente (o
 * cliente nunca é bloqueado). Elegibilidade é
 * {@link ProvidersApi.ProviderEligibility#isEligible()} (aprovado <b>e</b>
 * visível — mesmo predicado de {@code proposals}/{@code requests}, não só
 * a visibilidade), daí os dois casos de gating parcial abaixo.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository repository;
    @Mock
    private UploadsApi uploadsApi;
    @Mock
    private ProvidersApi providersApi;
    @Mock
    private UsersApi usersApi;
    @Mock
    private RequestsApi requestsApi;

    private ChatService service;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerUserId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChatService(repository, uploadsApi, providersApi, usersApi, requestsApi);
        lenient().when(repository.findAttachmentsForMessages(any())).thenReturn(List.of());
    }

    @Test
    void nonParticipantCannotListMessages() {
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(providersApi.findProviderIdByUserId(any())).thenReturn(Optional.empty());

        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> service.listMessages(conversationId, stranger, null, 20))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void customerCanAlwaysSendAMessageRegardlessOfProviderSubscription() {
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(repository.insertMessage(conversationId, customerId, "Olá, quando pode vir?")).thenReturn(UUID.randomUUID());
        UUID messageId = UUID.randomUUID();
        when(repository.insertMessage(conversationId, customerId, "Olá, quando pode vir?")).thenReturn(messageId);
        when(repository.findMessageById(messageId)).thenReturn(Optional.of(
                new MessageRow(messageId, conversationId, customerId, "Olá, quando pode vir?", Instant.now(), null)));

        MessageDto dto = service.sendMessage(conversationId, customerId, new CreateMessageRequest("Olá, quando pode vir?", null));

        assertThat(dto.senderId()).isEqualTo(customerId);
        verify(providersApi, never()).checkEligibility(any());
    }

    @Test
    void providerWithoutActiveSubscriptionCannotWriteToAnExistingConversation() {
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        when(providersApi.checkEligibility(providerId))
                .thenReturn(Optional.of(new ProvidersApi.ProviderEligibility(providerId, true, false)));

        assertThatThrownBy(() -> service.sendMessage(conversationId, providerUserId, new CreateMessageRequest("Já vou", null)))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .satisfies(ex -> assertThat(((ErrorResponseException) ex).getBody().getType().toString())
                        .isEqualTo("https://errors.servimatch.pt/subscription-required"));

        verify(repository, never()).insertMessage(any(), any(), any());
    }

    /**
     * Visível (aparece em pesquisas) mas ainda pendente de aprovação
     * administrativa: mesmo predicado de {@code proposals}/{@code requests}
     * — {@code isEligible() == approved && visible} — também bloqueia a
     * escrita, não só a ausência de visibilidade.
     */
    @Test
    void providerVisibleButNotApprovedCannotWriteToAnExistingConversation() {
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        when(providersApi.checkEligibility(providerId))
                .thenReturn(Optional.of(new ProvidersApi.ProviderEligibility(providerId, false, true)));

        assertThatThrownBy(() -> service.sendMessage(conversationId, providerUserId, new CreateMessageRequest("Já vou", null)))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .satisfies(ex -> assertThat(((ErrorResponseException) ex).getBody().getType().toString())
                        .isEqualTo("https://errors.servimatch.pt/subscription-required"));

        verify(repository, never()).insertMessage(any(), any(), any());
    }

    @Test
    void providerWithActiveSubscriptionCanWriteAndAttachmentsAreConfirmedWithTheMessageAttachmentPurpose() {
        UUID imageId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        when(providersApi.checkEligibility(providerId))
                .thenReturn(Optional.of(new ProvidersApi.ProviderEligibility(providerId, true, true)));
        when(repository.insertMessage(conversationId, providerUserId, "Envio foto")).thenReturn(messageId);
        when(repository.findMessageById(messageId)).thenReturn(Optional.of(
                new MessageRow(messageId, conversationId, providerUserId, "Envio foto", Instant.now(), null)));

        service.sendMessage(conversationId, providerUserId, new CreateMessageRequest("Envio foto", List.of(imageId)));

        verify(uploadsApi).confirmOwnedUpload(imageId, providerUserId, UploadPurpose.MESSAGE_ATTACHMENT);
        verify(repository).linkAttachments(messageId, List.of(imageId));
    }

    /**
     * {@code listConversations}: resolução em lote (um único
     * {@code UsersApi#findByIds}/{@code RequestsApi#findTitlesByIds} para a
     * página inteira, nunca por linha) e a tradução de papel — quando o
     * autenticado é o cliente, o interlocutor vem de
     * {@code ProvidersApi#findUserIdsByProviderIds} (lote, nunca
     * {@code findUserIdByProviderId} num ciclo por página); quando é o
     * prestador, {@code customer_id} já é o {@code users.id} do
     * interlocutor, sem tradução nenhuma.
     */
    @Test
    void listConversationsResolvesCounterpartNameThroughProviderProfileWhenViewerIsCustomer() {
        UUID requestId = UUID.randomUUID();
        ConversationSummaryRow row = new ConversationSummaryRow(
                conversationId, requestId, customerId, providerId, Instant.now(), "Já vou aí", 2, Instant.now());
        when(providersApi.findProviderIdByUserId(customerId)).thenReturn(Optional.empty());
        when(repository.findPageForParticipant(customerId, null, null, 21)).thenReturn(List.of(row));
        when(providersApi.findUserIdsByProviderIds(Set.of(providerId))).thenReturn(Map.of(providerId, providerUserId));
        when(usersApi.findByIds(Set.of(providerUserId)))
                .thenReturn(Map.of(providerUserId, new UsersApi.UserSummaryView(providerUserId, "Canalizações Silva")));
        when(requestsApi.findTitlesByIds(Set.of(requestId))).thenReturn(Map.of(requestId, "Fuga de água"));

        ConversationPageDto result = service.listConversations(customerId, null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).counterpartName()).isEqualTo("Canalizações Silva");
        assertThat(result.items().get(0).counterpartAvatarSeed()).isEqualTo(providerUserId.toString());
        assertThat(result.items().get(0).requestTitle()).isEqualTo("Fuga de água");
        assertThat(result.items().get(0).unreadCount()).isEqualTo(2);
        assertThat(result.page().nextCursor()).isNull();
    }

    @Test
    void listConversationsUsesCustomerIdDirectlyAsCounterpartWhenViewerIsProvider() {
        UUID requestId = UUID.randomUUID();
        ConversationSummaryRow row = new ConversationSummaryRow(
                conversationId, requestId, customerId, providerId, null, null, 0, Instant.now());
        when(providersApi.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        when(repository.findPageForParticipant(providerUserId, providerId, null, 21)).thenReturn(List.of(row));
        when(providersApi.findUserIdsByProviderIds(Set.of())).thenReturn(Map.of());
        when(usersApi.findByIds(Set.of(customerId)))
                .thenReturn(Map.of(customerId, new UsersApi.UserSummaryView(customerId, "Mariana Costa")));
        when(requestsApi.findTitlesByIds(Set.of(requestId))).thenReturn(Map.of(requestId, "Pintura T2"));

        ConversationPageDto result = service.listConversations(providerUserId, null, 20);

        assertThat(result.items().get(0).counterpartName()).isEqualTo("Mariana Costa");
        assertThat(result.items().get(0).counterpartAvatarSeed()).isEqualTo(customerId.toString());
        assertThat(result.items().get(0).lastMessagePreview()).isNull();
        assertThat(result.items().get(0).lastMessageAt()).isNull();
    }

    @Test
    void listConversationsSetsNextCursorWhenThereAreMoreRowsThanTheLimit() {
        List<ConversationSummaryRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID requestId = UUID.randomUUID();
            rows.add(new ConversationSummaryRow(
                    UUID.randomUUID(), requestId, customerId, UUID.randomUUID(), Instant.now(), "oi", 0, Instant.now()));
        }
        when(providersApi.findProviderIdByUserId(customerId)).thenReturn(Optional.empty());
        when(repository.findPageForParticipant(customerId, null, null, 3)).thenReturn(rows);
        when(providersApi.findUserIdsByProviderIds(any())).thenReturn(Map.of());
        when(usersApi.findByIds(any())).thenReturn(Map.of());
        when(requestsApi.findTitlesByIds(any())).thenReturn(Map.of());

        ConversationPageDto result = service.listConversations(customerId, null, 2);

        assertThat(result.items()).hasSize(2);
        assertThat(result.page().nextCursor()).isNotNull();
    }

    private ConversationRow conversation() {
        return new ConversationRow(conversationId, UUID.randomUUID(), customerId, providerId, Instant.now());
    }
}

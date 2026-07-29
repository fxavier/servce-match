package pt.servimatch.modules.chat.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.chat.internal.web.CreateMessageRequest;
import pt.servimatch.modules.chat.internal.web.MessageDto;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * escreve) e os dois casos de erro que interessam — não-participante
 * recusado, prestador sem subscrição ativa recusado a escrever numa
 * conversa já existente (o cliente nunca é bloqueado).
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository repository;
    @Mock
    private UploadsApi uploadsApi;

    private ChatService service;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerUserId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChatService(repository, uploadsApi);
        lenient().when(repository.findAttachmentsForMessages(any())).thenReturn(List.of());
    }

    @Test
    void nonParticipantCannotListMessages() {
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(repository.findProviderIdByUserId(any())).thenReturn(Optional.empty());

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
        verify(repository, never()).isProviderVisible(any());
    }

    @Test
    void providerWithoutActiveSubscriptionCannotWriteToAnExistingConversation() {
        when(repository.findById(conversationId)).thenReturn(Optional.of(conversation()));
        when(repository.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        when(repository.isProviderVisible(providerId)).thenReturn(false);

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
        when(repository.findProviderIdByUserId(providerUserId)).thenReturn(Optional.of(providerId));
        when(repository.isProviderVisible(providerId)).thenReturn(true);
        when(repository.insertMessage(conversationId, providerUserId, "Envio foto")).thenReturn(messageId);
        when(repository.findMessageById(messageId)).thenReturn(Optional.of(
                new MessageRow(messageId, conversationId, providerUserId, "Envio foto", Instant.now(), null)));

        service.sendMessage(conversationId, providerUserId, new CreateMessageRequest("Envio foto", List.of(imageId)));

        verify(uploadsApi).confirmOwnedUpload(imageId, providerUserId, UploadPurpose.MESSAGE_ATTACHMENT);
        verify(repository).linkAttachments(messageId, List.of(imageId));
    }

    private ConversationRow conversation() {
        return new ConversationRow(conversationId, UUID.randomUUID(), customerId, providerId, Instant.now());
    }
}

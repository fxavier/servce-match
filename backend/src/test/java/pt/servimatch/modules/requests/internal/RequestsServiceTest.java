package pt.servimatch.modules.requests.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.requests.RequestPublished;
import pt.servimatch.modules.requests.internal.web.AddressDto;
import pt.servimatch.modules.requests.internal.web.CreateServiceRequestRequest;
import pt.servimatch.modules.requests.internal.web.ServiceRequestDto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Núcleo de negócio de {@code requests}: transição {@code DRAFT → PUBLISHED}
 * (ARQUITETURA §4.3), ownership no servidor, e o predicado do gate de
 * criação (categoria inexistente/inativa).
 */
@ExtendWith(MockitoExtension.class)
class RequestsServiceTest {

    @Mock
    private RequestRepository repository;
    @Mock
    private CategoryLookup categoryLookup;
    @Mock
    private UploadAssetLinker uploadAssetLinker;

    private ApplicationEventPublisher events;
    private RequestsService service;

    private final UUID requestId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        events = mock(ApplicationEventPublisher.class);
        service = new RequestsService(repository, categoryLookup, uploadAssetLinker, events);
        lenient().when(uploadAssetLinker.findByRequestId(any())).thenReturn(List.of());
        lenient().when(categoryLookup.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void publishHappyPathTransitionsDraftToPublishedAndEmitsEvent() {
        ServiceRequestRow draft = row("DRAFT", null);
        ServiceRequestRow published = row("PUBLISHED", Instant.now());
        when(repository.findById(requestId)).thenReturn(Optional.of(draft));
        when(repository.publish(requestId)).thenReturn(Optional.of(published));

        ServiceRequestDto result = service.publish(requestId, ownerId);

        assertThat(result.status()).isEqualTo("PUBLISHED");
        verify(events).publishEvent(any(RequestPublished.class));
    }

    @Test
    void publishRejectsWhenRequestIsNotInDraftAnymore() {
        ServiceRequestRow current = row("PUBLISHED", Instant.now());
        when(repository.findById(requestId)).thenReturn(Optional.of(current));
        when(repository.publish(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(requestId, ownerId))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void publishRejectsNonOwner() {
        ServiceRequestRow draft = row("DRAFT", null);
        when(repository.findById(requestId)).thenReturn(Optional.of(draft));

        UUID someoneElse = UUID.randomUUID();
        assertThatThrownBy(() -> service.publish(requestId, someoneElse))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(repository, never()).publish(any());
    }

    @Test
    void createDraftRejectsInactiveOrMissingCategory() {
        when(categoryLookup.findActiveById(categoryId)).thenReturn(Optional.empty());
        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                categoryId, "Fuga de água na cozinha", null,
                new AddressDto("Rua Teste", null, "1000-001", "Lisboa", "1106", "PT", null),
                "NORMAL", null, null);

        assertThatThrownBy(() -> service.createDraft(ownerId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(repository, never()).insertDraft(any());
    }

    private ServiceRequestRow row(String status, Instant publishedAt) {
        return new ServiceRequestRow(
                requestId, ownerId, categoryId, "Fuga de água na cozinha", "desc",
                "Rua Teste", null, "1000-001", "Lisboa", "1106", "PT",
                null, null, "NORMAL", null, status, publishedAt, Instant.now());
    }
}

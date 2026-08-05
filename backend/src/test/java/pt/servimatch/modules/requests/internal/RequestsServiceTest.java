package pt.servimatch.modules.requests.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.categories.CategoriesApi;
import pt.servimatch.modules.matching.MatchingApi;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.requests.RequestPublished;
import pt.servimatch.modules.requests.UrgencyLevel;
import pt.servimatch.modules.uploads.ImageRef;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import pt.servimatch.modules.requests.internal.web.AddressDto;
import pt.servimatch.modules.requests.internal.web.CreateServiceRequestRequest;
import pt.servimatch.modules.requests.internal.web.ImageRefDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestDto;
import pt.servimatch.modules.requests.internal.web.ServiceRequestPageDto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private CategoriesApi categoriesApi;
    @Mock
    private UploadAssetLinker uploadAssetLinker;
    @Mock
    private ProvidersApi providersApi;
    @Mock
    private MatchingApi matchingApi;
    @Mock
    private UploadsApi uploadsApi;

    private ApplicationEventPublisher events;
    private RequestsService service;

    private final UUID requestId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        events = mock(ApplicationEventPublisher.class);
        service = new RequestsService(repository, categoriesApi, uploadAssetLinker, events, providersApi, matchingApi, uploadsApi);
        lenient().when(uploadAssetLinker.findByRequestId(any())).thenReturn(List.of());
        lenient().when(categoriesApi.findById(any())).thenReturn(Optional.empty());
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
        when(categoriesApi.findActiveById(categoryId)).thenReturn(Optional.empty());
        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                categoryId, "Fuga de água na cozinha", null,
                new AddressDto("Rua Teste", null, "1000-001", "Lisboa", "1106", "PT", null),
                UrgencyLevel.NORMAL, null, null);

        assertThatThrownBy(() -> service.createDraft(ownerId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(repository, never()).insertDraft(any());
    }

    /**
     * ADR-0010 fechado nesta onda: {@code createDraft} já não confirma o
     * asset por SQL direto — delega em {@code UploadsApi#confirmOwnedUpload}
     * (posse, purpose, magic bytes), uma chamada por {@code imageId}, com o
     * {@code purpose REQUEST_ATTACHMENT} correto.
     */
    @Test
    void createDraftConfirmsEachImageWithUploadsApiUsingTheRequestAttachmentPurpose() {
        UUID imageA = UUID.randomUUID();
        UUID imageB = UUID.randomUUID();
        when(categoriesApi.findActiveById(categoryId)).thenReturn(Optional.of(new CategoriesApi.CategoryView(categoryId, null, "cat", "Categoria", true)));
        when(repository.insertDraft(any())).thenReturn(requestId);
        when(repository.findById(requestId)).thenReturn(Optional.of(row("DRAFT", null)));

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                categoryId, "Fuga de água na cozinha", null,
                new AddressDto("Rua Teste", null, "1000-001", "Lisboa", "1106", "PT", null),
                UrgencyLevel.NORMAL, null, List.of(imageA, imageB));

        service.createDraft(ownerId, request);

        verify(uploadsApi).confirmOwnedUpload(imageA, ownerId, UploadPurpose.REQUEST_ATTACHMENT);
        verify(uploadsApi).confirmOwnedUpload(imageB, ownerId, UploadPurpose.REQUEST_ATTACHMENT);
        verify(uploadAssetLinker).linkToRequest(requestId, List.of(imageA, imageB));
    }

    /**
     * {@code toDto} junta {@code request_image} (só {@code imageId}+posição,
     * já sem {@code object_key}/{@code contentType} — ver relatório de
     * entrega) com {@code UploadsApi#resolve}, preservando a ordem de
     * {@code position} independentemente da ordem devolvida por
     * {@code resolve}.
     */
    @Test
    void createDraftReturnsImagesResolvedByUploadsApiAndOrderedByPosition() {
        UUID imageA = UUID.randomUUID();
        UUID imageB = UUID.randomUUID();
        when(categoriesApi.findActiveById(categoryId)).thenReturn(Optional.of(new CategoriesApi.CategoryView(categoryId, null, "cat", "Categoria", true)));
        when(repository.insertDraft(any())).thenReturn(requestId);
        when(repository.findById(requestId)).thenReturn(Optional.of(row("DRAFT", null)));
        when(uploadAssetLinker.findByRequestId(requestId)).thenReturn(List.of(
                new RequestImageRow(imageA, 0), new RequestImageRow(imageB, 1)));
        // resolve devolve fora de ordem de propósito: a ordem final tem de vir de "position", não de "resolve".
        when(uploadsApi.resolve(List.of(imageA, imageB))).thenReturn(List.of(
                new ImageRef(imageB, "https://signed.example/b", "image/jpeg"),
                new ImageRef(imageA, "https://signed.example/a", "image/jpeg")));

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                categoryId, "Fuga de água na cozinha", null,
                new AddressDto("Rua Teste", null, "1000-001", "Lisboa", "1106", "PT", null),
                UrgencyLevel.NORMAL, null, List.of(imageA, imageB));

        ServiceRequestDto dto = service.createDraft(ownerId, request);

        assertThat(dto.images()).extracting(ImageRefDto::id).containsExactly(imageA, imageB);
        assertThat(dto.images()).extracting(ImageRefDto::url)
                .containsExactly("https://signed.example/a", "https://signed.example/b");
    }

    // ---------------------------------------------------------------- listMine (GET /v1/requests)

    /**
     * Filtro de {@code status} não reconhecido é {@code 400}, nunca página
     * vazia silenciosa (CLAUDE.md/relatório de entrega): uma lista vazia
     * faria o cliente acreditar que não tem pedidos.
     */
    @Test
    void listMineRejectsInvalidStatusFilter() {
        assertThatThrownBy(() -> service.listMine(ownerId, "NOT_A_REAL_STATUS", null, 20))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(repository, never()).findPageForCustomer(any(), any(), any(), anyInt());
    }

    /** O dono pede a própria página: filtro de dono sempre delegado ao repositório (SQL), nunca decidido em memória. */
    @Test
    void listMineDelegatesOwnerAndStatusToTheRepository() {
        when(repository.findPageForCustomer(eq(ownerId), eq("PUBLISHED"), any(), eq(21)))
                .thenReturn(List.of(row("PUBLISHED", Instant.now())));

        ServiceRequestPageDto page = service.listMine(ownerId, "PUBLISHED", null, 20);

        assertThat(page.items()).hasSize(1);
        verify(repository).findPageForCustomer(eq(ownerId), eq("PUBLISHED"), any(), eq(21));
    }

    /** listMine é sempre a própria página do dono: morada exata, nunca mascarada. */
    @Test
    void listMineReturnsExactAddressSinceViewerIsAlwaysTheOwner() {
        when(repository.findPageForCustomer(eq(ownerId), isNull(), any(), anyInt()))
                .thenReturn(List.of(row("DRAFT", null)));

        ServiceRequestPageDto page = service.listMine(ownerId, null, null, 20);

        assertThat(page.items()).singleElement().satisfies(dto -> {
            assertThat(dto.address().line1()).isEqualTo("Rua Teste");
            assertThat(dto.address().postalCode()).isEqualTo("1000-001");
        });
    }

    // ---------------------------------------------------------------- listInbox (GET /v1/providers/me/requests)

    private final UUID providerId = UUID.randomUUID();

    /**
     * Defeito C4 (IDOR) fechado nesta onda: {@code ?status=DRAFT} devolvia
     * pedidos ainda não publicados de qualquer cliente, incluindo morada
     * completa e código postal, porque o {@code statusFilter} chegava cru à
     * consulta. {@code DRAFT} é um valor de enum válido — por isso não basta
     * a validação genérica de {@link ServiceRequestStatus#valueOf}; tem de
     * ser rejeitado pela allowlist do inbox, que é mais estrita do que a de
     * {@code listMine}.
     */
    @Test
    void listInboxRejectsDraftStatusFilterEvenThoughItIsAValidEnumValue() {
        assertThatThrownBy(() -> service.listInbox(providerId, "DRAFT", null, 20))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(repository, never()).findPage(any(), any(), any(), anyInt());
        verify(providersApi, never()).workedCategoryIds(any());
    }

    @Test
    void listInboxRejectsStatusFilterOutsideTheEnumEntirely() {
        assertThatThrownBy(() -> service.listInbox(providerId, "lixo", null, 20))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(repository, never()).findPage(any(), any(), any(), anyInt());
    }

    @Test
    void listInboxAcceptsPublishedStatusFilter() {
        when(providersApi.workedCategoryIds(providerId)).thenReturn(Set.of(categoryId));
        ServiceRequestRow published = row("PUBLISHED", Instant.now());
        when(repository.findPage(eq(List.of("PUBLISHED")), any(), any(), anyInt()))
                .thenReturn(List.of(published));
        when(matchingApi.filterEligibleRequestIds(eq(providerId), any())).thenReturn(Set.of(requestId));

        ServiceRequestPageDto page = service.listInbox(providerId, "PUBLISHED", null, 20);

        assertThat(page.items()).hasSize(1);
        verify(repository).findPage(eq(List.of("PUBLISHED")), any(), any(), anyInt());
    }

    /** Sem parâmetro: mantém o comportamento atual — allowlist {@code {PUBLISHED, IN_NEGOTIATION}} por omissão. */
    @Test
    void listInboxWithNoStatusFilterKeepsThePublishedAndInNegotiationDefault() {
        when(providersApi.workedCategoryIds(providerId)).thenReturn(Set.of(categoryId));
        when(repository.findPage(eq(List.of("PUBLISHED", "IN_NEGOTIATION")), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.listInbox(providerId, null, null, 20);

        verify(repository).findPage(eq(List.of("PUBLISHED", "IN_NEGOTIATION")), any(), any(), anyInt());
    }

    // ---------------------------------------------------------------- exposição de morada (getForViewer)

    @Test
    void getForViewerReturnsExactAddressToTheOwner() {
        when(repository.findById(requestId)).thenReturn(Optional.of(row("PUBLISHED", Instant.now())));

        ServiceRequestDto dto = service.getForViewer(requestId, ownerId, null, false);

        assertThat(dto.address().line1()).isEqualTo("Rua Teste");
        assertThat(dto.address().postalCode()).isEqualTo("1000-001");
    }

    @Test
    void getForViewerReturnsExactAddressToAdmin() {
        when(repository.findById(requestId)).thenReturn(Optional.of(row("PUBLISHED", Instant.now())));

        UUID admin = UUID.randomUUID();
        ServiceRequestDto dto = service.getForViewer(requestId, admin, null, true);

        assertThat(dto.address().line1()).isEqualTo("Rua Teste");
    }

    /**
     * Auditoria confirmada: um prestador elegível não pode receber
     * {@code line1}/{@code line2}, o código postal completo, nem as
     * coordenadas exatas — só granularidade de zona.
     */
    @Test
    void getForViewerMasksAddressForAnEligibleProvider() {
        ServiceRequestRow published = rowWithLocation("PUBLISHED", 38.716701, -9.139899);
        when(repository.findById(requestId)).thenReturn(Optional.of(published));
        UUID viewerProviderId = UUID.randomUUID();
        when(matchingApi.isEligible(any())).thenReturn(true);

        ServiceRequestDto dto = service.getForViewer(requestId, UUID.randomUUID(), viewerProviderId, false);

        assertThat(dto.address().line1()).isNull();
        assertThat(dto.address().line2()).isNull();
        assertThat(dto.address().postalCode()).isEqualTo("1000");
        assertThat(dto.address().city()).isEqualTo("Lisboa");
        assertThat(dto.address().location().lat()).isEqualTo(38.72);
        assertThat(dto.address().location().lon()).isEqualTo(-9.14);
    }

    /**
     * O arredondamento é uma grelha fixa (determinístico), não ruído
     * aleatório: repetir a mesma leitura tem de devolver sempre o mesmo par
     * — ao contrário de jitter aleatório, cuja média ao longo de repetições
     * recupera o ponto exato.
     */
    @Test
    void addressMaskingRoundingIsDeterministicAcrossRepeatedReads() {
        ServiceRequestRow published = rowWithLocation("PUBLISHED", 38.716701, -9.139899);
        when(repository.findById(requestId)).thenReturn(Optional.of(published));
        UUID viewerProviderId = UUID.randomUUID();
        when(matchingApi.isEligible(any())).thenReturn(true);

        ServiceRequestDto first = service.getForViewer(requestId, UUID.randomUUID(), viewerProviderId, false);
        ServiceRequestDto second = service.getForViewer(requestId, UUID.randomUUID(), viewerProviderId, false);

        assertThat(first.address().location()).isEqualTo(second.address().location());
    }

    private ServiceRequestRow row(String status, Instant publishedAt) {
        return new ServiceRequestRow(
                requestId, ownerId, categoryId, "Fuga de água na cozinha", "desc",
                "Rua Teste", null, "1000-001", "Lisboa", "1106", "PT",
                null, null, "NORMAL", null, status, publishedAt, Instant.now());
    }

    private ServiceRequestRow rowWithLocation(String status, double lat, double lon) {
        return new ServiceRequestRow(
                requestId, ownerId, categoryId, "Fuga de água na cozinha", "desc",
                "Rua Teste", null, "1000-001", "Lisboa", "1106", "PT",
                lat, lon, "NORMAL", null, status, Instant.now(), Instant.now());
    }
}

package pt.servimatch.modules.providers.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.billing.SubscriptionPlan;
import pt.servimatch.modules.billing.SubscriptionStatus;
import pt.servimatch.modules.categories.CategoriesApi;
import pt.servimatch.modules.providers.ProviderApprovalDecision;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.providers.internal.web.ProviderApprovalDto;
import pt.servimatch.modules.providers.internal.web.ProviderProfileDto;
import pt.servimatch.modules.providers.internal.web.UpdateProviderProfileRequest;
import pt.servimatch.modules.uploads.UploadPurpose;
import pt.servimatch.modules.uploads.UploadsApi;
import pt.servimatch.modules.users.UsersApi;
import pt.servimatch.platform.audit.AuditLogWriter;
import pt.servimatch.platform.audit.AuditMetadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Núcleo de negócio de {@code providers}: elegibilidade composta a partir de
 * {@code billing} (ADR-0011 D3), 404 indistinguível do perfil público
 * (ver {@link #getPublicProfile...}) e validação total antes da primeira
 * escrita em {@code PUT /v1/providers/me}.
 */
@ExtendWith(MockitoExtension.class)
class ProvidersServiceTest {

    @Mock
    private ProviderRepository repository;
    @Mock
    private UsersApi usersApi;
    @Mock
    private CategoriesApi categoriesApi;
    @Mock
    private UploadsApi uploadsApi;
    @Mock
    private SubscriptionLifecycle subscriptionLifecycle;
    @Mock
    private AuditLogWriter auditLogWriter;

    private ProvidersService service;

    private final UUID providerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID adminUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProvidersService(repository, usersApi, categoriesApi, uploadsApi, subscriptionLifecycle, auditLogWriter);
    }

    private ProviderProfileRow approvedRow() {
        return new ProviderProfileRow(
                providerId, userId, "Canalizador", "Bio", "Acme", true, "APPROVED",
                BigDecimal.valueOf(4.5), 10, null, null, null, Instant.now());
    }

    // ---------------------------------------------------------------- GET /v1/providers/{id} (público)

    @Test
    void getPublicProfileReturns404WhenProviderDoesNotExist() {
        when(repository.findById(providerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicProfile(providerId))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPublicProfileReturns404WithTheSameMessageWhenNotApproved() {
        ProviderProfileRow row = new ProviderProfileRow(
                providerId, userId, "H", null, null, false, "PENDING", BigDecimal.ZERO, 0, null, null, null, Instant.now());
        when(repository.findById(providerId)).thenReturn(Optional.of(row));

        String notApprovedMessage = catchNotFoundDetail(() -> service.getPublicProfile(providerId));

        // Mesmo cenário, desta vez aprovado mas sem visibilidade — mesma mensagem exata.
        ProviderProfileRow visibleButNotApproved = new ProviderProfileRow(
                providerId, userId, "H", null, null, false, "APPROVED", BigDecimal.ZERO, 0, null, null, null, Instant.now());
        when(repository.findById(providerId)).thenReturn(Optional.of(visibleButNotApproved));
        when(subscriptionLifecycle.isVisibilityEligible(providerId)).thenReturn(false);

        String notVisibleMessage = catchNotFoundDetail(() -> service.getPublicProfile(providerId));

        assertThat(notApprovedMessage).isEqualTo(notVisibleMessage);
    }

    @Test
    void getPublicProfileReturns404WhenSubscriptionDoesNotGrantVisibility() {
        when(repository.findById(providerId)).thenReturn(Optional.of(approvedRow()));
        when(subscriptionLifecycle.isVisibilityEligible(providerId)).thenReturn(false);

        assertThatThrownBy(() -> service.getPublicProfile(providerId))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPublicProfileHappyPathPassesNullBioAndLocationThrough() {
        ProviderProfileRow row = new ProviderProfileRow(
                providerId, userId, "Canalizador", null, null, true, "APPROVED",
                BigDecimal.valueOf(4.2), 3, null, null, null, Instant.now());
        when(repository.findById(providerId)).thenReturn(Optional.of(row));
        when(subscriptionLifecycle.isVisibilityEligible(providerId)).thenReturn(true);
        when(usersApi.findByIds(Set.of(userId))).thenReturn(Map.of(userId, new UsersApi.UserSummaryView(userId, "Ana")));
        when(repository.findWorkedCategoryIds(providerId)).thenReturn(Set.of());
        when(repository.findAdminRegionCodes(providerId)).thenReturn(List.of());
        when(repository.ratingDistribution(userId)).thenReturn(new RatingDistributionRow(1, 2, 0, 0, 0));
        when(repository.findPortfolio(providerId)).thenReturn(List.of());
        when(subscriptionLifecycle.findByProvider(providerId)).thenReturn(Optional.empty());

        ProviderProfileDto dto = service.getPublicProfile(providerId);

        assertThat(dto.bio()).isNull();
        assertThat(dto.location()).isNull();
        assertThat(dto.displayName()).isEqualTo("Ana");
        assertThat(dto.ratingDistribution()).containsEntry("5", 1).containsEntry("4", 2).containsEntry("3", 0);
        assertThat(dto.portfolioImageUrls()).isEmpty();
        assertThat(dto.premiumBadge()).isFalse();
    }

    // ---------------------------------------------------------------- checkEligibility (ADR-0011 D3)

    @Test
    void checkEligibilityComposesApprovalFromProvidersAndVisibilityFromBilling() {
        when(repository.findById(providerId)).thenReturn(Optional.of(approvedRow()));
        when(subscriptionLifecycle.isVisibilityEligible(providerId)).thenReturn(true);

        ProvidersApi.ProviderEligibility eligibility = service.checkEligibility(providerId).orElseThrow();

        assertThat(eligibility.approved()).isTrue();
        assertThat(eligibility.visible()).isTrue();
        assertThat(eligibility.isEligible()).isTrue();
    }

    @Test
    void checkEligibilityNeverReadsAVisibilityStateColumnItDoesNotConsultBilling() {
        ProviderProfileRow pendingApproval = new ProviderProfileRow(
                providerId, userId, "H", null, null, false, "PENDING", BigDecimal.ZERO, 0, null, null, null, Instant.now());
        when(repository.findById(providerId)).thenReturn(Optional.of(pendingApproval));
        when(subscriptionLifecycle.isVisibilityEligible(providerId)).thenReturn(true);

        ProvidersApi.ProviderEligibility eligibility = service.checkEligibility(providerId).orElseThrow();

        assertThat(eligibility.approved()).isFalse();
        assertThat(eligibility.isEligible()).isFalse();
        verify(subscriptionLifecycle).isVisibilityEligible(providerId);
    }

    // ---------------------------------------------------------------- PUT /v1/providers/me

    @Test
    void updateMyProfileValidatesEverythingBeforeAnyWriteWhenCategoryIsUnknown() {
        when(repository.findById(providerId)).thenReturn(Optional.of(approvedRow()));
        UUID unknownCategory = UUID.randomUUID();
        when(categoriesApi.findById(unknownCategory)).thenReturn(Optional.empty());
        UpdateProviderProfileRequest request = new UpdateProviderProfileRequest(
                "Headline", "Bio", List.of(unknownCategory), List.of(), List.of());

        assertThatThrownBy(() -> service.updateMyProfile(providerId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(repository, never()).updateEditableFields(any(), any(), any());
        verify(repository, never()).replaceCategories(any(), anySet());
        verify(repository, never()).replaceAdminRegionZones(any(), anySet());
        verify(repository, never()).replacePortfolio(any(), any());
        verifyNoInteractions(uploadsApi);
    }

    @Test
    void updateMyProfileValidatesEverythingBeforeAnyWriteWhenRegionCodeIsUnknown() {
        when(repository.findById(providerId)).thenReturn(Optional.of(approvedRow()));
        UpdateProviderProfileRequest request = new UpdateProviderProfileRequest(
                "Headline", "Bio", List.of(), List.of("PT-UNKNOWN"), List.of());

        assertThatThrownBy(() -> service.updateMyProfile(providerId, request))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(repository, never()).updateEditableFields(any(), any(), any());
    }

    @Test
    void updateMyProfileConfirmsPortfolioOwnershipBeforeWritingAnything() {
        when(repository.findById(providerId)).thenReturn(Optional.of(approvedRow()));
        UUID imageId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY))
                .when(uploadsApi).confirmOwnedUpload(imageId, userId, UploadPurpose.PROVIDER_PORTFOLIO);
        UpdateProviderProfileRequest request = new UpdateProviderProfileRequest(
                "Headline", "Bio", List.of(), List.of(), List.of(imageId));

        assertThatThrownBy(() -> service.updateMyProfile(providerId, request))
                .isInstanceOf(ErrorResponseException.class);

        verify(repository, never()).updateEditableFields(any(), any(), any());
        verify(repository, never()).replacePortfolio(any(), any());
    }

    @Test
    void updateMyProfileHappyPathReplacesAllListsAfterValidationPasses() {
        when(repository.findById(providerId))
                .thenReturn(Optional.of(approvedRow()))
                .thenReturn(Optional.of(approvedRow()));
        UUID categoryId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(categoriesApi.findById(categoryId)).thenReturn(Optional.of(new CategoriesApi.CategoryView(categoryId, null, "slug", "Nome", true)));
        when(usersApi.findByIds(anySet())).thenReturn(Map.of());
        when(repository.findWorkedCategoryIds(providerId)).thenReturn(Set.of());
        when(repository.findAdminRegionCodes(providerId)).thenReturn(List.of());
        when(repository.ratingDistribution(userId)).thenReturn(new RatingDistributionRow(0, 0, 0, 0, 0));
        when(repository.findPortfolio(providerId)).thenReturn(List.of());
        when(subscriptionLifecycle.findByProvider(providerId)).thenReturn(Optional.empty());

        UpdateProviderProfileRequest request = new UpdateProviderProfileRequest(
                "Nova headline", "Nova bio", List.of(categoryId), List.of("PT-LIS"), List.of(imageId));

        service.updateMyProfile(providerId, request);

        verify(uploadsApi).confirmOwnedUpload(imageId, userId, UploadPurpose.PROVIDER_PORTFOLIO);
        verify(repository).updateEditableFields(providerId, "Nova headline", "Nova bio");
        verify(repository).replaceCategories(eq(providerId), eq(Set.of(categoryId)));
        verify(repository).replaceAdminRegionZones(eq(providerId), eq(Set.of("PT-LIS")));
        verify(repository).replacePortfolio(providerId, List.of(imageId));
    }

    // ---------------------------------------------------------------- PATCH /v1/admin/providers/{id}/approval (ADR-0011 D7)

    private ProviderApprovalRow approvalRow(String status) {
        return new ProviderApprovalRow(providerId, status, null, null, null);
    }

    @Test
    void decideApprovalReturns404WhenProviderDoesNotExist() {
        when(repository.findApprovalState(providerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.APPROVED, null))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(repository, never()).applyApprovalDecision(any(), any(), any(), any(), any());
        verifyNoInteractions(auditLogWriter);
    }

    @Test
    void decideApprovalRejectsMissingReasonForRejectedWith422AndWritesNothing() {
        when(repository.findApprovalState(providerId)).thenReturn(Optional.of(approvalRow("PENDING")));

        assertThatThrownBy(() -> service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.REJECTED, "  "))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(repository, never()).applyApprovalDecision(any(), any(), any(), any(), any());
        verifyNoInteractions(auditLogWriter);
    }

    @Test
    void decideApprovalRejectsMissingReasonForSuspendedWith422() {
        when(repository.findApprovalState(providerId)).thenReturn(Optional.of(approvalRow("APPROVED")));

        assertThatThrownBy(() -> service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.SUSPENDED, null))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void decideApprovalAllowsApprovedWithoutAnyReason() {
        when(repository.findApprovalState(providerId)).thenReturn(Optional.of(approvalRow("PENDING")));
        Instant decidedAt = Instant.now();
        when(repository.applyApprovalDecision(providerId, "PENDING", "APPROVED", null, adminUserId))
                .thenReturn(Optional.of(new ProviderApprovalRow(providerId, "APPROVED", null, adminUserId, decidedAt)));

        ProviderApprovalDto dto = service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.APPROVED, null);

        assertThat(dto.approvalStatus().name()).isEqualTo("APPROVED");
        assertThat(dto.decidedBy()).isEqualTo(adminUserId);
        assertThat(dto.decidedAt()).isEqualTo(decidedAt);
    }

    @Test
    void decideApprovalRejectsTransitionOutsideTheAllowlistWith409AndWritesNothing() {
        when(repository.findApprovalState(providerId)).thenReturn(Optional.of(approvalRow("REJECTED")));

        assertThatThrownBy(() -> service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.APPROVED, null))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(repository, never()).applyApprovalDecision(any(), any(), any(), any(), any());
        verifyNoInteractions(auditLogWriter);
    }

    @Test
    void decideApprovalRereadsAndReturns409WhenTheCompareAndSetLosesARace() {
        when(repository.findApprovalState(providerId))
                .thenReturn(Optional.of(approvalRow("PENDING")))
                .thenReturn(Optional.of(approvalRow("REJECTED")));
        when(repository.applyApprovalDecision(providerId, "PENDING", "APPROVED", null, adminUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.APPROVED, null))
                .isInstanceOfSatisfying(ErrorResponseException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(auditLogWriter);
    }

    @Test
    void decideApprovalWritesAuditLogInTheSameCallWithActorAndTarget() {
        // AuditMetadata (platform/audit, fora do âmbito deste agente) não expõe
        // equals()/inspeção pública deliberadamente (só serialização interna do
        // escritor) — por isso este teste verifica actor/action/target/alvo, os
        // campos que a skill admin-moderation-endpoint exige na chamada, sem
        // inspecionar o conteúdo de "metadata" por reflexão. O conteúdo
        // {from, to} sem reason é garantido por leitura de código
        // (ProvidersService#decideApproval) e pela suíte de auditoria do
        // backend-platform.
        when(repository.findApprovalState(providerId)).thenReturn(Optional.of(approvalRow("PENDING")));
        when(repository.applyApprovalDecision(providerId, "PENDING", "REJECTED", "Documentos inválidos", adminUserId))
                .thenReturn(Optional.of(new ProviderApprovalRow(providerId, "REJECTED", "Documentos inválidos", adminUserId, Instant.now())));

        service.decideApproval(providerId, adminUserId, ProviderApprovalDecision.REJECTED, "Documentos inválidos");

        verify(auditLogWriter).record(
                eq(adminUserId),
                eq("provider.approval.decided"),
                eq("provider_profile"),
                eq(providerId),
                any(AuditMetadata.class));
    }

    // ---------------------------------------------------------------- APIs em lote

    @Test
    void findUserIdsByProviderIdsWithNullOrEmptyNeverTouchesTheDatabase() {
        assertThat(service.findUserIdsByProviderIds(null)).isEmpty();
        assertThat(service.findUserIdsByProviderIds(Set.of())).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    void findUserIdsByProviderIdsOmitsUnknownIds() {
        UUID unknown = UUID.randomUUID();
        when(repository.findByIds(Set.of(providerId, unknown))).thenReturn(List.of(approvedRow()));

        Map<UUID, UUID> result = service.findUserIdsByProviderIds(Set.of(providerId, unknown));

        assertThat(result).containsOnly(Map.entry(providerId, userId));
    }

    @Test
    void summariesWithNullOrEmptyNeverTouchesTheDatabase() {
        assertThat(service.summaries(null)).isEmpty();
        assertThat(service.summaries(Set.of())).isEmpty();

        verifyNoInteractions(repository);
    }

    private String catchNotFoundDetail(Runnable action) {
        try {
            action.run();
        } catch (ErrorResponseException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            return ex.getBody().getDetail();
        }
        throw new AssertionError("Esperava ErrorResponseException 404");
    }
}

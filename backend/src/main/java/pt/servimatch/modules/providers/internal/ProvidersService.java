package pt.servimatch.modules.providers.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.servimatch.modules.providers.ProvidersApi;
import pt.servimatch.modules.users.UsersApi;

import java.util.Optional;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class ProvidersService implements ProvidersApi {

    private final ProviderRepository repository;
    private final UsersApi usersApi;

    ProvidersService(ProviderRepository repository, UsersApi usersApi) {
        this.repository = repository;
        this.usersApi = usersApi;
    }

    @Override
    @Transactional
    public UUID ensureProvisioned(UUID userId) {
        Optional<ProviderProfileRow> existing = repository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        return repository.insertIfAbsent(userId)
                .orElseGet(() -> repository.findByUserId(userId)
                        .orElseThrow(() -> new IllegalStateException("Provisionamento JIT de provider falhou para user=" + userId))
                        .id());
    }

    @Override
    public Optional<UUID> findProviderIdByUserId(UUID userId) {
        return repository.findByUserId(userId).map(ProviderProfileRow::id);
    }

    @Override
    public Optional<UUID> findUserIdByProviderId(UUID providerId) {
        return repository.findById(providerId).map(ProviderProfileRow::userId);
    }

    @Override
    public Optional<ProviderEligibility> checkEligibility(UUID providerId) {
        return repository.findById(providerId)
                .map(row -> new ProviderEligibility(
                        row.id(),
                        "APPROVED".equals(row.approvalStatus()),
                        "VISIBLE".equals(row.visibilityState())));
    }

    @Override
    public Optional<ProviderSummaryView> summary(UUID providerId) {
        return repository.findById(providerId).map(row -> {
            String displayName = usersApi.findById(row.userId())
                    .map(UsersApi.UserView::displayName)
                    .orElse(row.headline() == null ? "Prestador" : row.headline());
            return new ProviderSummaryView(
                    row.id(),
                    displayName,
                    row.headline(),
                    row.companyName(),
                    row.ratingAvg().doubleValue(),
                    row.ratingCount(),
                    row.verified(),
                    // premiumBadge deriva do plano de subscrição (hasBadge); o módulo billing
                    // (backend-payments, fora deste âmbito) ainda não expõe essa leitura.
                    false,
                    null);
        });
    }
}

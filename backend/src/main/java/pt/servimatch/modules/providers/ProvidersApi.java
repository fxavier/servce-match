package pt.servimatch.modules.providers;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API pública do módulo {@code providers}. Encapsula o predicado de
 * elegibilidade do prestador (aprovação administrativa + visibilidade
 * derivada da subscrição, ARQUITETURA §3.3/§10.3) para que outros módulos
 * (proposals, requests) façam o <em>gating</em> por subscrição sem aceder à
 * tabela {@code subscription} (do módulo {@code billing}, fora deste
 * âmbito) nem aos internos de {@code providers}.
 */
public interface ProvidersApi {

    /**
     * Garante que existe um {@code ProviderProfile} para o utilizador,
     * criando-o (estado inicial {@code PENDING}/{@code HIDDEN}, ARQUITETURA
     * §9.2) na primeira chamada de um utilizador com role {@code PROVIDER}.
     * Idempotente.
     */
    UUID ensureProvisioned(UUID userId);

    Optional<UUID> findProviderIdByUserId(UUID userId);

    Optional<UUID> findUserIdByProviderId(UUID providerId);

    /**
     * Elegibilidade para operações que exigem prestador ativo: pesquisa,
     * matching, propostas, chat (ARQUITETURA §3.3). {@code visible} deriva
     * de {@code Subscription.status} e é escrito pelo módulo {@code billing}
     * ao reagir aos eventos de subscrição — este módulo só o lê.
     */
    Optional<ProviderEligibility> checkEligibility(UUID providerId);

    Optional<ProviderSummaryView> summary(UUID providerId);

    /**
     * Categorias que o prestador trabalha ({@code provider_category}, V4).
     * Usado por {@code requests.listProviderInbox} para restringir os
     * candidatos passados a {@code MatchingApi.filterEligibleRequestIds}
     * (que só filtra por cobertura geográfica — a categoria tem de ser
     * pré-filtrada pelo chamador, ver o seu javadoc). Conjunto vazio se o
     * prestador não existir ou não tiver nenhuma categoria associada.
     */
    Set<UUID> workedCategoryIds(UUID providerId);

    record ProviderEligibility(UUID providerId, boolean approved, boolean visible) {
        public boolean isEligible() {
            return approved && visible;
        }
    }

    record ProviderSummaryView(
            UUID id,
            String displayName,
            String headline,
            String companyName,
            double ratingAvg,
            int ratingCount,
            boolean verified,
            boolean premiumBadge,
            String avatarUrl) {
    }
}

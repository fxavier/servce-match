package pt.servimatch.modules.providers;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API pública do módulo {@code providers}. Encapsula o predicado de
 * elegibilidade do prestador (aprovação administrativa + visibilidade
 * derivada da subscrição, ADR-0011) para que outros módulos (proposals,
 * requests, reviews) façam o <em>gating</em> por subscrição sem aceder à
 * tabela {@code subscription} (do módulo {@code billing}) nem aos internos
 * de {@code providers}.
 */
public interface ProvidersApi {

    /**
     * Garante que existe um {@code ProviderProfile} para o utilizador,
     * criando-o (estado inicial {@code approval_status=PENDING}, ARQUITETURA
     * §9.2) na primeira chamada de um utilizador com role {@code PROVIDER}.
     * Idempotente.
     */
    UUID ensureProvisioned(UUID userId);

    Optional<UUID> findProviderIdByUserId(UUID userId);

    Optional<UUID> findUserIdByProviderId(UUID providerId);

    /**
     * Tradução em lote {@code provider_profile.id → users.id}, para módulos
     * que precisam de correlacionar vários prestadores numa página (ex.
     * lista de propostas, resultados de pesquisa) sem N+1 — nunca chamar
     * {@link #findUserIdByProviderId} dentro de um ciclo por página.
     *
     * <p>Semântica fixa: uma única consulta; {@code providerIds} nulo ou
     * vazio devolve {@link Map#of()} sem tocar na base de dados;
     * identificadores que não correspondem a nenhum prestador ficam
     * ausentes do mapa (nunca lança exceção); sem filtro de autorização —
     * cabe ao chamador validar o acesso a cada prestador devolvido.
     */
    Map<UUID, UUID> findUserIdsByProviderIds(Set<UUID> providerIds);

    /**
     * Elegibilidade para operações que exigem prestador ativo: pesquisa,
     * matching, propostas, chat, caixa de entrada (ADR-0011 D2/D3 — P1,
     * "elegibilidade de operação"). Composição de dois factos, cada um
     * respondido por um único dono: {@code approved} é
     * {@code provider_profile.approval_status = APPROVED} (facto deste
     * módulo); {@code visible} é
     * {@link pt.servimatch.modules.billing.SubscriptionLifecycle#isVisibilityEligible}
     * (facto exclusivo de {@code billing} — {@code ACTIVE}/{@code PAST_DUE}
     * com {@code current_period_end} ainda não passado), resolvido nesta
     * chamada, nunca a partir de um campo projetado. Antes do ADR-0011 este
     * método lia {@code provider_profile.visibility_state}, uma coluna sem
     * nenhum escritor em produção — o defeito que o ADR fecha.
     */
    Optional<ProviderEligibility> checkEligibility(UUID providerId);

    Optional<ProviderSummaryView> summary(UUID providerId);

    /**
     * Versão em lote de {@link #summary(UUID)} — para qualquer caminho que
     * monte uma página de vários prestadores (ex. lista de propostas), nunca
     * chamando {@link #summary(UUID)} num ciclo por página. Mesma semântica
     * de {@link #findUserIdsByProviderIds}: uma consulta, {@link Map#of()}
     * para {@code providerIds} nulo/vazio, ids desconhecidos ausentes do
     * mapa, sem filtro de autorização.
     */
    Map<UUID, ProviderSummaryView> summaries(Set<UUID> providerIds);

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

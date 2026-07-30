package pt.servimatch.modules.providers.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Espelha {@code components.schemas.ProviderProfile} ({@code allOf}
 * {@code ProviderSummary} + campos de perfil). {@code ratingDistribution} é
 * {@code Map<String, Integer>} com as chaves {@code "5".."1"} — o esquema do
 * contrato usa literais numéricos como nome de propriedade, o que não é um
 * identificador Java válido para um {@code record}; o mapa serializa para a
 * mesma forma JSON.
 *
 * <p>{@code bio} e {@code location} são deliberadamente nulos quando a
 * respetiva coluna ({@code provider_profile.bio}/{@code .location}, ambas
 * nullable, V4/V19) é {@code NULL} — nunca substituídos por um valor por
 * omissão (string vazia, {@code GeoPoint(0,0)}). O esquema do contrato não
 * declara os dois como {@code nullable}/{@code type: [X, "null"]}
 * (só os marca {@code required}, isto é, presentes); reportado ao
 * {@code api-contract} como incoerência a corrigir (ver relatório de
 * entrega) — até lá, o valor {@code null} no corpo é a representação mais
 * honesta de "ainda não preenchido", preferível a inventar um valor.
 */
public record ProviderProfileDto(
        UUID id,
        String displayName,
        String headline,
        String companyName,
        double ratingAvg,
        int ratingCount,
        boolean verified,
        boolean premiumBadge,
        String avatarUrl,
        String bio,
        List<String> categoryNames,
        List<ProviderZoneDto> zones,
        GeoPointDto location,
        Map<String, Integer> ratingDistribution,
        List<String> portfolioImageUrls,
        Instant memberSince) {
}

package pt.servimatch.modules.requests.internal.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Espelha {@code components.schemas.Address} ({@code openapi.yaml:976-985}).
 * {@code @Size} alinhado com as colunas {@code VARCHAR}/{@code CHAR} de
 * {@code service_request} (V7) — sem isto, um valor acima do limite
 * atravessava o bean validation e só a base de dados o rejeitava, com
 * {@code 409} em vez de {@code 400}/{@code 422} (achado M5 da auditoria; ver
 * {@link pt.servimatch.modules.requests.UrgencyLevel} para o mesmo padrão de
 * defeito no campo irmão {@code urgency}):
 * <ul>
 *   <li>{@code line1}/{@code line2} — {@code address_line1}/{@code address_line2 VARCHAR(200)}.</li>
 *   <li>{@code postalCode} — {@code address_postal_code VARCHAR(20)}.</li>
 *   <li>{@code city} — {@code address_city VARCHAR(120)}.</li>
 *   <li>{@code regionCode} — {@code address_region_code VARCHAR(20)}. Só o
 *       comprimento é validado aqui; a pertença a um catálogo de regiões
 *       conhecidas (o mesmo que {@code PUT /v1/providers/me} já aplica) fica
 *       por fazer — ver relatório de entrega do achado M5, bloqueado por
 *       {@code RegionCatalog} ser {@code internal} de {@code providers}.</li>
 *   <li>{@code country} — {@code address_country CHAR(2)}: exatamente 2
 *       quando presente; {@code null} continua a significar "usar o omisso
 *       PT", resolvido em {@code RequestsService#createDraft}.</li>
 * </ul>
 */
public record AddressDto(
        @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @Size(max = 20) String regionCode,
        @Size(min = 2, max = 2) String country,
        @Valid GeoPointDto location) {
}

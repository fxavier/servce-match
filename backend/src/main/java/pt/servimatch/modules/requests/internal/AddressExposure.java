package pt.servimatch.modules.requests.internal;

import pt.servimatch.modules.requests.internal.web.AddressDto;
import pt.servimatch.modules.requests.internal.web.GeoPointDto;

/**
 * Quanta morada um viewer recebe (auditoria de segurança confirmada: {@code
 * GET /v1/requests/{id}} e {@code GET /v1/providers/me/requests} devolviam o
 * mesmo DTO completo — {@code line1}/{@code line2}, código postal e
 * coordenadas exatas de casa do cliente — ao dono do pedido e a qualquer
 * prestador elegível, apesar de o contrato prometer "vista limitada" ao
 * prestador).
 *
 * <p>A decisão é <b>função do viewer, não da linha</b>: {@link RequestsController}/
 * {@link RequestsService} calculam-na uma vez por pedido HTTP (owner/ADMIN →
 * {@link #EXACT}; qualquer prestador → {@link #ZONE}) e aplicam-na à página
 * inteira — nunca há um {@code toDto} que decida sozinho, por linha, sem
 * receber explicitamente qual dos dois casos se aplica.
 */
enum AddressExposure {

    /** Dono do pedido ou {@code ADMIN}: morada completa, tal como guardada. */
    EXACT,

    /**
     * Qualquer prestador (elegível ou não — nunca chega a {@code toDto} sem
     * o ser, mas o predicado não distingue "elegível" de "adjudicado"):
     * sem {@code line1}/{@code line2}, código postal truncado ao prefixo
     * (antes do hífen do formato português {@code NNNN-NNN}), coordenadas
     * arredondadas a uma grelha fixa de 2 casas decimais (~1,1 km em
     * latitude, ~0,8 km em longitude à latitude de Portugal).
     *
     * <p>O arredondamento é <b>determinístico</b> (grelha fixa), nunca ruído
     * aleatório: repetir o mesmo pedido N vezes devolve sempre o mesmo par
     * (lat, lon) arredondado. Jitter aleatório pareceria mais privado à
     * primeira vista, mas a sua média ao longo de repetições converge para
     * o ponto exato — teria a aparência de proteção sem o efeito.
     */
    ZONE;

    private static final double GRID_SCALE = 100.0; // 2 casas decimais.

    AddressDto apply(ServiceRequestRow row) {
        GeoPointDto exactLocation = row.latitude() != null && row.longitude() != null
                ? new GeoPointDto(row.latitude(), row.longitude())
                : null;

        if (this == EXACT) {
            return new AddressDto(
                    row.addressLine1(), row.addressLine2(), row.addressPostalCode(), row.addressCity(),
                    row.addressRegionCode(), row.addressCountry(), exactLocation);
        }

        GeoPointDto zoneLocation = exactLocation == null
                ? null
                : new GeoPointDto(roundToGrid(exactLocation.lat()), roundToGrid(exactLocation.lon()));
        return new AddressDto(
                null, null, postalPrefix(row.addressPostalCode()), row.addressCity(),
                row.addressRegionCode(), row.addressCountry(), zoneLocation);
    }

    private static double roundToGrid(double value) {
        return Math.round(value * GRID_SCALE) / GRID_SCALE;
    }

    /** {@code "1000-001"} → {@code "1000"}. Sem hífen (formato não-PT): mantém, ou corta às primeiras 4 posições se mais longo. */
    private static String postalPrefix(String postalCode) {
        if (postalCode == null || postalCode.isBlank()) {
            return postalCode;
        }
        int dash = postalCode.indexOf('-');
        if (dash > 0) {
            return postalCode.substring(0, dash);
        }
        return postalCode.length() > 4 ? postalCode.substring(0, 4) : postalCode;
    }
}

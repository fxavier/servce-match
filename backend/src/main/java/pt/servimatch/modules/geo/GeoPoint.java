package pt.servimatch.modules.geo;

/**
 * Ponto de referência WGS84 (latitude/longitude), tal como devolvido pelo
 * cliente (geolocalização do browser/mobile) ou resolvido por geocodificação.
 *
 * <p>Tipo público partilhado entre {@code matching} e {@code search} — evita
 * duplicar a validação de intervalo e o significado de "lat"/"lon" em cada
 * módulo consumidor. Ver ADR-0004.
 */
public record GeoPoint(double lat, double lon) {

    public GeoPoint {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude fora do intervalo [-90, 90]: " + lat);
        }
        if (Double.isNaN(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Longitude fora do intervalo [-180, 180]: " + lon);
        }
    }
}

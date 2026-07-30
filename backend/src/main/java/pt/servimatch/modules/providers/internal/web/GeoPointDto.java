package pt.servimatch.modules.providers.internal.web;

/** Espelha {@code components.schemas.GeoPoint} — ver nota de duplicação em {@code ProviderSummaryDto}. */
public record GeoPointDto(double lat, double lon) {
}

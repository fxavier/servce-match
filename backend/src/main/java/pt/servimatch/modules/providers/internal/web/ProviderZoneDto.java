package pt.servimatch.modules.providers.internal.web;

/** Espelha {@code components.schemas.ProviderZone}. {@code label} vem de {@code RegionCatalog}, nunca da base de dados. */
public record ProviderZoneDto(String regionCode, String label) {
}

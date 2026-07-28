package pt.servimatch.platform.appversion;

/** Espelha {@code docs/api/openapi.yaml#/components/schemas/VersionStatus}. */
public record VersionStatusResponse(
        Status status,
        String minSupportedVersion,
        String latestVersion,
        String storeUrl,
        String message
) {
    public enum Status {
        OK, UPDATE_RECOMMENDED, UPDATE_REQUIRED
    }
}

package pt.servimatch.modules.notifications.internal;

/**
 * Espelha {@code docs/api/openapi.yaml#/components/schemas/Platform}, usado
 * em {@code RegisterDeviceToken}. Interno ao módulo: nenhum outro módulo
 * precisa de conhecer a plataforma de um dispositivo.
 */
public enum DevicePlatform {
    IOS,
    ANDROID,
    WEB
}

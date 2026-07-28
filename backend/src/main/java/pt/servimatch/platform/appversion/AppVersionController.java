package pt.servimatch.platform.appversion;

import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /v1/app/version-status} (operationId {@code getAppVersionStatus}),
 * público ({@code security: []} no contrato; ver
 * {@code pt.servimatch.config.SecurityConfig#PUBLIC_GET_ENDPOINTS}).
 *
 * <p>Regra puramente de configuração (ver {@link AppVersionProperties}):
 * sem tabela, sem <em>deploy</em> do cliente para mudar o comportamento —
 * atualizar {@code servimatch.app-version.platforms.*} no ambiente chega.
 * Não é um módulo Spring Modulith (não tem semântica de domínio nem estado
 * persistido) — vive em {@code platform} como o resto da infraestrutura
 * transversal.
 */
@RestController
@Validated
@EnableConfigurationProperties(AppVersionProperties.class)
class AppVersionController {

    private final AppVersionProperties properties;

    AppVersionController(AppVersionProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/v1/app/version-status")
    VersionStatusResponse getAppVersionStatus(
            @RequestParam AppVersionProperties.Platform platform,
            @RequestParam @Pattern(regexp = "\\d+(\\.\\d+){0,2}", message = "Versão semântica inválida (ex.: \"1.4.2\").") String appVersion) {

        AppVersionProperties.PlatformRule rule = properties.ruleFor(platform);
        SemVer current = SemVer.parse(appVersion);
        SemVer minSupported = SemVer.parse(rule.minSupportedVersion());
        SemVer latest = SemVer.parse(rule.latestVersion());

        VersionStatusResponse.Status status;
        if (current.compareTo(minSupported) < 0) {
            status = VersionStatusResponse.Status.UPDATE_REQUIRED;
        } else if (current.compareTo(latest) < 0) {
            status = VersionStatusResponse.Status.UPDATE_RECOMMENDED;
        } else {
            status = VersionStatusResponse.Status.OK;
        }

        return new VersionStatusResponse(status, rule.minSupportedVersion(), rule.latestVersion(), rule.storeUrl(), rule.message());
    }
}

package pt.servimatch.platform.appversion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * Regras de {@code GET /v1/app/version-status} — inteiramente por
 * configuração, sem tabela e sem <em>deploy</em> do cliente (decisão
 * registada no relatório de arbitragem desta onda): forçar/recomendar
 * atualização é uma alteração de {@code application.yml} (ou da variável de
 * ambiente equivalente), nunca uma migração nem uma parte do domínio.
 *
 * @param platforms regra por plataforma: {@code minSupportedVersion} (abaixo →
 *                  {@code UPDATE_REQUIRED}), {@code latestVersion} (abaixo →
 *                  {@code UPDATE_RECOMMENDED}), {@code storeUrl}/{@code message}
 *                  opcionais para a UI.
 */
@ConfigurationProperties(prefix = "servimatch.app-version")
public record AppVersionProperties(Map<Platform, PlatformRule> platforms) {

    public AppVersionProperties {
        platforms = platforms == null ? Map.of() : new EnumMap<>(platforms);
    }

    PlatformRule ruleFor(Platform platform) {
        PlatformRule rule = platforms.get(platform);
        // Sem regra configurada para a plataforma: nunca bloquear por omissão
        // (falha aberta) — devolve OK sem exigir nada.
        return rule != null ? rule : PlatformRule.PERMISSIVE_DEFAULT;
    }

    public enum Platform {
        IOS, ANDROID, WEB
    }

    public record PlatformRule(String minSupportedVersion, String latestVersion, String storeUrl, String message) {

        static final PlatformRule PERMISSIVE_DEFAULT = new PlatformRule("0.0.0", "0.0.0", null, null);
    }
}

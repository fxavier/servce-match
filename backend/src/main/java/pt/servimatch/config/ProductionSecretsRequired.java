package pt.servimatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Falha o arranque em produção (perfil {@code prod}) se os valores
 * efetivamente secretos não tiverem sido explicitamente definidos (achado 7
 * da auditoria de segurança).
 *
 * <p>{@code application-prod.yml} já remove, para
 * {@code spring.datasource.password} e {@code servimatch.uploads.secret-key},
 * o default de desenvolvimento presente em {@code application.yml}
 * ({@code servimatch}/{@code servimatch123} — credenciais públicas,
 * conhecidas deste repositório). Isso, por si só, <b>não chega</b>: o
 * binding de {@code @ConfigurationProperties} usado por
 * {@code DataSourceProperties} e {@code UploadsProperties} não lança quando
 * um placeholder {@code ${VAR}} fica por resolver — o campo fica, em
 * silêncio, com o literal {@code "${VAR}"}, e dependendo da configuração
 * (ex.: {@code hikari.initialization-fail-timeout: -1}) o arranque pode
 * completar-se na mesma, só falhando (de forma confusa) no primeiro pedido
 * real.
 *
 * <p>{@code @Value}, ao contrário, força resolução estrita do placeholder
 * (lança {@code PlaceholderResolutionException} se não houver default nem
 * variável de ambiente) — é por isso que este bean, cujo único propósito é
 * validar, injeta os dois valores só para forçar essa resolução no arranque,
 * nunca os lê nem os regista.
 */
@Configuration
@Profile("prod")
class ProductionSecretsRequired {

    ProductionSecretsRequired(
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${servimatch.uploads.secret-key}") String uploadsSecretKey) {
        // Intencionalmente vazio — a injeção acima já falhou o arranque se
        // a variável de ambiente correspondente estiver por definir.
    }
}

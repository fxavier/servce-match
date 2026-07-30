package pt.servimatch.platform.security;

/**
 * Porta de provisionamento <em>just-in-time</em> de utilizadores (ADR-0012
 * D9): no primeiro pedido autenticado de um {@code sub} do Keycloak
 * desconhecido, a linha correspondente em {@code users} é criada a partir
 * das claims do token.
 *
 * <p>Definida na plataforma ({@code platform/security}) porque "quando
 * provisionar" é uma decisão transversal — qualquer endpoint autenticado,
 * de qualquer módulo, precisa da mesma garantia antes de referenciar
 * {@code users.id}. A tabela {@code users} é do módulo {@code users}
 * (ADR-0012 D4: "{@code UsersApi.ensureProvisioned} continua a ser o único
 * escritor de {@code users} em produção"), que fornece a implementação
 * desta porta como bean Spring — este módulo (platform) nunca escreve na
 * tabela diretamente.
 *
 * <p>Consumida por {@link UserProvisioningFilter}, que a invoca via
 * {@link org.springframework.beans.factory.ObjectProvider} — nunca por
 * injeção direta de um único bean — precisamente para tolerar a ausência
 * temporária de implementação sem escolher um substituto em silêncio (ver
 * javadoc do filtro).
 */
public interface UserProvisioningPort {

    /**
     * Garante que existe uma linha {@code users} para {@code sub},
     * criando-a se for a primeira vez que este {@code sub} é visto.
     * Idempotente: chamadas concorrentes ou repetidas para o mesmo
     * {@code sub} nunca duplicam a linha nem falham por causa da
     * duplicação (a implementação de referência já existente,
     * {@code UsersApi.ensureProvisioned}, faz
     * {@code INSERT ... ON CONFLICT (keycloak_sub) DO NOTHING} com
     * releitura pelo perdedor da corrida — esta porta delega para lá).
     *
     * <p>Pode lançar {@link RuntimeException} por falha de infraestrutura
     * (ex.: base de dados indisponível). O chamador ({@link UserProvisioningFilter})
     * é responsável por apanhar essa exceção e nunca derrubar o pedido por
     * causa dela — um filtro escapa ao
     * {@code pt.servimatch.platform.error.GlobalExceptionHandler}.
     *
     * @param sub         subject do JWT Keycloak (ADR-0002/0012); nunca vazio.
     * @param email       melhor valor disponível nas claims
     *                    ({@code email}, com recurso a
     *                    {@code preferred_username}); nunca a chave de
     *                    identidade — só metadado.
     * @param displayName melhor valor disponível nas claims
     *                    ({@code name}, com recurso a
     *                    {@code preferred_username}/{@code email}).
     */
    void ensureProvisioned(String sub, String email, String displayName);
}

/**
 * Providers — perfil profissional/empresa, portfólio, categorias e zonas
 * de atuação do prestador; estado de visibilidade derivado da subscrição.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §6.3, §9.
 *
 * <p>{@code allowedDependencies} fechado nesta revisão (Onda 1b,
 * {@code backend-platform}), a partir dos imports reais: só {@code users}
 * ({@code UsersApi}). Não depende de {@code billing}, apesar de a
 * visibilidade derivar do estado da subscrição — essa leitura, hoje, é feita
 * do lado de {@code billing}/{@code payments}, não daqui. Um módulo que
 * precise de uma dependência nova pede-a a este agente.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Providers",
        allowedDependencies = {"modules.users"}
)
package pt.servimatch.modules.providers;

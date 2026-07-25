/**
 * Users — contas de utilizador, papéis e moradas; ligação entre a
 * identidade Keycloak ({@code sub} do JWT) e o registo de domínio
 * {@code User} (just-in-time provisioning no primeiro login válido).
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform} na Onda 0
 * (esqueleto vazio). Implementação pertence ao agente {@code backend-domain}
 * — ver {@code docs/AGENTES.md} e {@code docs/ARQUITETURA.md} §6.3, §9.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Users"
)
package pt.servimatch.modules.users;

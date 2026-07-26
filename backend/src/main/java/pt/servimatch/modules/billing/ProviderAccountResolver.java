package pt.servimatch.modules.billing;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolve o {@code provider_profile.id} do prestador autenticado a partir
 * do {@code sub} (subject) do JWT Keycloak (ARQUITETURA §8.5).
 *
 * <p><b>Nota de fronteira (pedido para o agente {@code arquiteto}/
 * {@code backend-domain}):</b> esta é, em rigor, uma capacidade do módulo
 * {@code providers}/{@code users} (resolver identidade → perfil de
 * prestador), não de {@code billing}. Como nenhum dos dois módulos tem
 * implementação neste worktree (execução paralela por onda, cada agente
 * isolado no seu próprio branch/worktree — ver CLAUDE.md §6), não é
 * possível depender da sua API pública em tempo de compilação aqui.
 *
 * <p>A implementação por omissão ({@code internal.JdbcProviderAccountResolver})
 * faz uma leitura só-de-consulta diretamente às tabelas {@code users} e
 * {@code provider_profile} (mesmo schema Postgres, monólito modular) — um
 * atalho pragmático e explicitamente assinalado, não a forma pretendida a
 * prazo. Quando o módulo {@code providers} tiver uma API pública
 * equivalente, esta interface deve ser substituída por uma dependência
 * declarada em {@code allowedDependencies} (billing → providers) escrita
 * pelo agente {@code backend-platform}.
 */
public interface ProviderAccountResolver {

    Optional<UUID> resolveProviderId(String keycloakSubject);
}

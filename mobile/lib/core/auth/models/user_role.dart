/// *Roles* de realm/cliente do Keycloak (ver ARQUITETURA.md §"Autenticação
/// e Autorização" e `infra/keycloak/realm-servimatch.json`).
///
/// É usado **apenas para adaptar a UX** (ADR-0008: app única por *role*).
/// Nunca é usado para decidir *gating* de subscrição ou autorização de
/// negócio — isso é sempre uma decisão do servidor (CLAUDE.md §4); o
/// cliente só espelha o que a API responde.
enum UserRole {
  customer,
  provider,
  admin;

  static UserRole? fromWireValue(String value) => switch (value.toUpperCase()) {
        'CUSTOMER' => UserRole.customer,
        'PROVIDER' => UserRole.provider,
        'ADMIN' => UserRole.admin,
        _ => null,
      };
}

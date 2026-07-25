# ADR-0002: Identidade delegada a Keycloak (OAuth2/OIDC)

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0001, ADR-0008 (app Flutter), ADR-0009 (autenticação de clientes nativos)

## Contexto e Problema

A especificação inicial descrevia, em simultâneo, autenticação com **JWT emitido pelo backend + Spring Security + BCrypt** *e* **Keycloak**. As duas abordagens são mutuamente exclusivas como *source of truth* de identidade. É necessário decidir de forma inequívoca onde reside a gestão de identidade, autenticação e emissão de tokens.

## Fatores de Decisão

- Superfície de segurança do código próprio (menos código sensível = menos risco).
- Funcionalidades prontas: verificação de email, políticas de password, MFA, proteção de força bruta.
- Caminho para SSO e integração com identidades externas no futuro.
- Custo operacional de manter mais uma peça de infraestrutura.

## Opções Consideradas

1. **Keycloak como IdP único** — backend como OAuth2 Resource Server.
2. **JWT gerido pelo backend** — emissão/validação/rotação e hashing de passwords próprios.

## Decisão

**Keycloak é o único Identity Provider.** O backend Spring Boot atua como **OAuth2 Resource Server** e **não** gere passwords, hashing, emissão/rotação de tokens nem proteção de força bruta.

- **SPA (public client):** Authorization Code Flow **+ PKCE** (nunca *implicit*).
- **Backend:** valida o *access token* (assinatura via **JWKS** do Keycloak; verifica `iss`, `aud`, `exp`) e deriva *authorities* das *roles*.
- **Ligação identidade↔domínio:** o `sub` do token é a chave estável para o registo `users` local; *just-in-time provisioning* no primeiro login válido.
- **Roles:** `CUSTOMER`, `PROVIDER`, `ADMIN` mapeadas para `GrantedAuthority`.

## Consequências

**Positivas**
- Elimina do código próprio a gestão de credenciais e tokens (menos superfície de ataque a auditar).
- MFA, verificação de email, políticas de password e *brute-force protection* prontos.
- SSO/identidades federadas triviais de adicionar depois.

**Negativas / Custos**
- Uma dependência de infraestrutura a operar: HA, backups do realm, monitorização.
- Latência adicional no fluxo de login (mitigada por *token caching* e *silent renew*).
- PII mínima duplicada entre Keycloak e domínio (gerir no âmbito do RGPD).

## Nota de segurança complementar (armazenamento de tokens no browser)

`localStorage`/`sessionStorage` para tokens é **rejeitado** (exposição a XSS). Recomenda-se o padrão **BFF** (backend guarda tokens; SPA usa sessão via cookie `HttpOnly`, `Secure`, `SameSite`) dado o tratamento de PII e pagamentos; alternativa mínima aceitável: **tokens em memória + PKCE + refresh rotation**. Esta nota aplica-se ao **cliente web**; os **clientes nativos (mobile)** usam um modelo distinto (RFC 8252 + *secure storage* do SO) definido em **ADR-0009**.

## Alternativas rejeitadas

- **JWT gerido no backend:** mais código sensível de autenticação para escrever, testar e auditar, reimplementando funcionalidades que o Keycloak já oferece de forma robusta.

## Ligações

- OAuth 2.0 (RFC 6749): https://www.rfc-editor.org/rfc/rfc6749
- PKCE (RFC 7636): https://www.rfc-editor.org/rfc/rfc7636
- OpenID Connect Core: https://openid.net/specs/openid-connect-core-1_0.html
- Spring Security — Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
- Keycloak: https://www.keycloak.org/documentation

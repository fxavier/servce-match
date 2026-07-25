---
name: keycloak-oidc-security
description: Integração de segurança do ServiMatch com Keycloak — backend como OAuth2 Resource Server, mapeamento de roles, provisionamento JIT do utilizador, e os fluxos corretos para SPA (BFF/PKCE) e app nativa (RFC 8252). Usa ao mexer em segurança, autenticação ou autorização em qualquer camada.
---

# Keycloak / OAuth2 / OIDC no ServiMatch

Decisão vinculativa (ADR-0002): **o Keycloak é o único Identity Provider**. O
backend valida tokens e mais nada. Se estiveres a escrever emissão de token,
hashing de password, rotação de refresh ou proteção de força bruta no backend,
estás a implementar a alternativa rejeitada — pára.

## Backend — Resource Server

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}   # valida iss e descobre o JWKS
          audiences: ${KEYCLOAK_AUDIENCE}
```

Checklist:
- Assinatura validada contra o **JWKS** do issuer (chaves em cache, com rotação
  suportada — não fixes a chave).
- `iss`, `aud` e `exp` verificados. Um Resource Server que ignora `aud` aceita
  tokens emitidos para outro cliente do mesmo realm.
- Roles do realm/cliente convertidas para `GrantedAuthority` com prefixo `ROLE_`
  através de um `JwtAuthenticationConverter` próprio.
- `SecurityFilterChain` **deny-by-default**; a lista de rotas públicas espelha
  exatamente os `security: []` do `openapi.yaml`.
- Sem estado: `SessionCreationPolicy.STATELESS`.

## Identidade do domínio — provisionamento JIT

A chave estável é o **`sub`** do token, guardado em `users.keycloak_sub` (UNIQUE).
No primeiro pedido autenticado de um `sub` desconhecido, cria-se o registo local.

Não uses email como chave: muda, pode ser reutilizado e nem sempre é único entre
provedores de identidade federados. Guarda no domínio a PII mínima necessária —
duplicar o perfil inteiro do Keycloak aumenta a superfície de RGPD sem benefício.

## Autorização

A role é grosseira (`CUSTOMER`, `PROVIDER`, `ADMIN`); ela diz *que tipo* de
utilizador é, não *se este* utilizador pode tocar *neste* recurso. Verifica
sempre a propriedade do recurso no serviço. A ausência desta verificação é o
IDOR clássico e é a falha mais comum em APIs deste tipo.

O gating por subscrição **não é uma role**: é estado de domínio, muda a qualquer
momento e verifica-se no serviço, não no filtro de segurança.

## Cliente web

Tokens **nunca** em `localStorage`/`sessionStorage`. Padrão adotado: BFF com
cookie `HttpOnly`, `Secure`, `SameSite` — e com proteção CSRF, que o cookie
reintroduz. Alternativa mínima: token em memória + Authorization Code com PKCE +
rotação de refresh. *Implicit flow* está morto; não o consideres.

## Cliente nativo (RFC 8252)

Authorization Code + PKCE pelo **browser do sistema** (ASWebAuthenticationSession
/ Custom Tabs) via AppAuth. Refresh token em Keychain/Keystore. Redirect por
App Links / Universal Links, não por custom scheme. Webview embebido é proibido.
Não há BFF no mobile — não é omissão, é a decisão do ADR-0009.

## Testes

- `spring-security-test` para os casos de tabela: sem token → 401; role errada →
  403; role certa → 200; token de outro `aud` → 401.
- Nos caminhos críticos, Keycloak em **Testcontainers** com o realm importado, e
  obtenção real de token. Só assim se exercita a validação de JWT a sério.

## Referências

- RFC 6749 (OAuth 2.0): https://www.rfc-editor.org/rfc/rfc6749
- RFC 7636 (PKCE): https://www.rfc-editor.org/rfc/rfc7636
- RFC 8252 (Native Apps): https://www.rfc-editor.org/rfc/rfc8252
- OpenID Connect Core: https://openid.net/specs/openid-connect-core-1_0.html
- Spring Security Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
- Keycloak: https://www.keycloak.org/documentation

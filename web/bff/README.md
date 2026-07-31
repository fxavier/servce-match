# `web/bff` — superfície `/auth/**`

Este ficheiro documenta as rotas `/auth/**` do BFF — **não** entram em
`docs/api/openapi.yaml` (ADR-0012 D1): esse contrato é do *resource server*,
partilhado com o mobile, que segue RFC 8252 e nunca fala com estas rotas.
Quem consome isto é exclusivamente `web/site` (`src/features/auth/bffClient.ts`).

Ver `docs/adr/0012-autenticacao-first-party-sobre-keycloak.md` para o porquê.
`infra/README.md` documenta o lado do realm (roles, service account,
armadilhas da Admin REST API).

## Sessão e CSRF (aplicam-se a todas as rotas abaixo)

- Toda a resposta de sucesso que autentica define o cookie de sessão
  `sm_sid` (`HttpOnly`, `Secure` em produção, `SameSite=Lax`). **Nenhum
  endpoint devolve `access_token`/`refresh_token`/`id_token`, em corpo ou
  cookie.**
- `POST /auth/register`, `POST /auth/login` e `POST /auth/logout` exigem o
  cabeçalho `X-CSRF-Token` a repetir o valor do cookie legível `sm_csrf`
  (double-submit, `src/csrf.ts`) — tal como qualquer outra escrita. Um
  cliente sem esse cookie ainda (primeira visita) obtém-no em qualquer `GET`
  anterior (ex.: `GET /auth/me`, que o site já chama ao arrancar).
- Erros são sempre `application/problem+json` (RFC 9457).

## `POST /auth/register`

Cria a conta no Keycloak (Admin REST API, *service account*), atribui a role
e autentica de imediato (ADR-0012 D2).

**Pedido**

```json
{
  "email": "novo.utilizador@example.pt",
  "password": "Sup3r$ecreto!",
  "name": "Novo Utilizador",
  "role": "CUSTOMER"
}
```

- `role`: só `"CUSTOMER"` ou `"PROVIDER"` — mesmos valores de `realm_access.roles`
  usados em `/auth/me`, para o site não precisar de mapear entre dois
  vocabulários. Qualquer outro valor (incluindo `"ADMIN"`) é rejeitado no
  servidor com `400`, allowlist fechada — nunca reencaminhado ao Keycloak.
- Política de password (espelha `infra/keycloak/realm-servimatch.json`):
  mínimo 10 carateres, pelo menos uma maiúscula, uma minúscula, um número, um
  caráter especial, e não pode conter o email.

**Respostas**

| Status | Corpo | Quando |
|---|---|---|
| `201` | `{ "registered": true }` — sempre este corpo, LITERALMENTE, com ou sem `Set-Cookie: sm_sid` conforme o login automático tenha ou não confirmado sessão. | Conta criada com sucesso **ou** email já em uso por outra conta. Estes dois casos são **indistinguíveis** por desenho (status, corpo e tempo de resposta — ADR-0012 D7.3): um `409` aqui seria o mesmo oráculo de enumeração que o login já fecha, só que no registo. O site nunca lê `session`/`user` deste corpo — chama sempre `GET /auth/me` a seguir para confirmar a sessão real (única fonte de verdade). Se o email já existia, o titular é avisado por email (fora da resposta HTTP; ponto de extensão ainda não implementado — ver comentário em `src/routes/auth.ts`), nunca o requerente. |
| `400` | `type: .../invalid-registration`, `invalidFields: string[]` | Email inválido, nome vazio, ou `role` fora da allowlist. |
| `400` | `type: .../weak-password`, `errors: [{code, message}]` | Password não cumpre a política (validada localmente antes de chamar o Keycloak; se o Keycloak ainda assim recusar por alguma regra residual, a resposta é a mesma, com uma mensagem genérica fechada — nunca o texto bruto do IdP). |
| `429` | `type: .../too-many-requests`, cabeçalho `Retry-After` | Rate limit por IP ou por email excedido. |
| `502` | `type: .../upstream-unavailable` | Falha a criar o utilizador ou a atribuir a role no Keycloak (email novo), ou falha de infraestrutura equivalente durante o caminho de conflito. Se a role falhar depois de o utilizador existir, o BFF tenta apagá-lo (rollback) antes de responder — uma conta sem role é pior que inexistente. |

## `POST /auth/login`

Direct Access Grant *server-to-server* (ADR-0012 D3). A password vive só
neste pedido: lida do corpo, enviada ao Keycloak, descartada — nunca em log,
sessão, cookie ou corpo de erro.

**Pedido**

```json
{ "email": "customer.test@servimatch.pt", "password": "DevLocal#2026" }
```

**Respostas**

| Status | Corpo |
|---|---|
| `200` | `{ "authenticated": true, "user": { "sub", "email", "username", "roles" } }` + `Set-Cookie: sm_sid` |
| `400` | `type: .../invalid-login` — faltam `email`/`password`. Não chega a contactar o Keycloak. |
| `401` | `type: .../invalid-credentials`, `title: "Credenciais inválidas."` — **email inexistente e password errada devolvem sempre esta mesma resposta, com o mesmo código, corpo E tempo de resposta** (ADR-0012 D7.3/D7.4). Não há como o cliente distinguir os dois casos, propositadamente. |
| `429` | `type: .../too-many-requests`, cabeçalho `Retry-After` — rate limit por IP ou por email, antes de qualquer chamada ao Keycloak. |

Login com sucesso **invalida qualquer sessão anterior do mesmo utilizador**
(fixação de sessão) e gera sempre um `sm_sid` novo.

## `POST /auth/logout`

**Contrato mudou (ADR-0012 D3): responde `204` sem corpo. Já não devolve
`logoutUrl`.** O site deixou de navegar para o Keycloak porque o utilizador
nunca o vê — não há URL de fim de sessão SSO a devolver. Perde-se o fim de
sessão SSO no Keycloak (o cookie `KEYCLOAK_IDENTITY` fica residual até
`ssoSessionIdleTimeout`); consequência aceite conscientemente pelo ADR.

Destrói a sessão do BFF e revoga o `refresh_token` no Keycloak
(*best-effort*) antes de responder. Idempotente: sem sessão ativa, também
responde `204`.

## `GET /auth/me`

Inalterado. `{ "authenticated": false }` ou
`{ "authenticated": true, "user": { "sub", "email", "username", "roles" } }`.

## Fluxo de regresso: `GET /auth/login` / `GET /auth/callback`

Authorization Code + PKCE, o fluxo antigo. **Desligado por omissão**
(`LEGACY_OIDC_FLOW_ENABLED=false`) — com a flag desligada estas duas rotas
não existem (`404`), e não há forma de as reativar por cabeçalho nem
parâmetro de pedido, só reiniciando o processo com a variável de ambiente a
`true`. `POST /auth/login` (novo) e `GET /auth/login` (antigo) coexistem sem
conflito — são métodos diferentes na mesma rota.

## Variáveis de ambiente novas (ver `.env.example`)

`KEYCLOAK_ADMIN_API_BASE_URL` (opcional, derivada de `KEYCLOAK_ISSUER_URI`),
`TRUST_PROXY_HOPS`, `AUTH_RATE_LIMIT_WINDOW_MS`/`_MAX_PER_IP`/`_MAX_PER_EMAIL`,
`LOGIN_TIMING_FLOOR_MS`/`_QUANTUM_MS`/`_MAX_DELAY_MS`,
`SESSION_ABSOLUTE_TTL_SECONDS`/`_SWEEP_INTERVAL_SECONDS`,
`LEGACY_OIDC_FLOW_ENABLED`.

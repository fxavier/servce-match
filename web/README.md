# ServiMatch — Web (SPA + BFF)

Cliente web do ServiMatch: SPA React/Vite/TypeScript (`app/`) + BFF
Node/Express (`bff/`) que guarda os tokens OIDC (ADR-0002). Ver
`../CLAUDE.md` (raiz do monorepo) para as regras de arquitetura e ownership.

## Layout

```
web/
├── app/        # SPA React + Vite + TypeScript
├── bff/        # Backend-for-Frontend: sessão por cookie HttpOnly, fala com o Keycloak
└── e2e/        # Playwright — fluxo crítico ponta a ponta
```

`app` e `bff` são dois *npm workspaces* (`web/package.json`). Correm em
processos separados mesmo em desenvolvimento.

## Porquê um BFF

`localStorage`/`sessionStorage` para tokens está proibido (ADR-0002 — XSS lê
ambos). O BFF autentica contra o Keycloak (Authorization Code + PKCE) e
guarda os tokens só no servidor; a SPA recebe apenas um cookie de sessão
`HttpOnly`/`Secure`/`SameSite`. O browser nunca vê o `access_token`.

## Arrancar em desenvolvimento

Pré-requisito: `infra/` a correr (`docker compose up` na raiz do
monorepo) — dá-te o Keycloak em `http://localhost:8081` e o backend em
`http://localhost:8080` (ou o esqueleto da Onda 0, que arranca e autentica
mas ainda devolve 404 nos endpoints de domínio).

```bash
npm install                                  # na raiz de web/ (workspaces)
cp bff/.env.example bff/.env                 # preenche KEYCLOAK_CLIENT_SECRET (infra/README.md)
cp app/.env.example app/.env                 # opcional, valores por omissão já servem

npm run dev:bff --workspace bff    # http://localhost:4000
npm run dev:app --workspace app    # http://localhost:5173 (proxy /api e /auth para o BFF)
```

Utilizadores de teste em `infra/README.md` (`customer.test@servimatch.pt`,
`provider.test@servimatch.pt`, `admin.test@servimatch.pt`).

## Cliente HTTP gerado

Nunca se escrevem chamadas HTTP à mão para o domínio (`/v1/...`) — o cliente
e os tipos são gerados a partir de `docs/api/openapi.yaml`:

```bash
npm run generate:api --workspace app
```

Gera `app/src/api/generated/schema.d.ts` (tipos) usado por `openapi-fetch`
em `app/src/api/client.ts`. Fica commitado de propósito (permite à pipeline
comparar contra uma nova geração e detetar deriva entre contrato e código).
Nunca editar à mão — se está errado, o contrato está errado.

Os únicos `fetch` escritos à mão são os três endpoints do próprio BFF
(`/auth/me`, `/auth/login`, `/auth/logout` — ver `app/src/auth/bffClient.ts`),
que **não** fazem parte do contrato OpenAPI (é infraestrutura de sessão do
cliente web, não uma capacidade de domínio partilhada com o mobile —
ADR-0009).

## Testes

```bash
npm run test                       # unitários + componente (app) e integração (bff), Vitest
npm run test:e2e                   # Playwright — fluxo crítico ponta a ponta
```

O `test:e2e` sobe automaticamente (via `webServer` do Playwright): um
Keycloak falso (`oauth2-mock-server`, Authorization Code + PKCE a sério, sem
UI de login), um backend de domínio falso derivado do contrato
(`e2e/mock-backend`), o BFF real e a SPA real. Não depende do
`docker compose` da raiz nem do backend Java — o objetivo é testar o nosso
código de autenticação e UI, não reimplementar os testes do backend.

## Qualidade

```bash
npm run lint
npm run typecheck
npm run build
```

TypeScript em `strict`, sem `any` sem justificação. Build e lint têm de ficar
limpos antes de qualquer commit.

# ServiMatch — Web (site + BFF)

Cliente web do ServiMatch: site React/Vite/TypeScript (`site/`) + BFF
Node/Express (`bff/`) que guarda os tokens OIDC (ADR-0002). Ver
`../CLAUDE.md` (raiz do monorepo) para as regras de arquitetura e ownership.

## Layout

```
web/
├── site/       # Site público — React 19 + Vite 7 + Tailwind v4 (ver site/README.md)
├── bff/        # Backend-for-Frontend: sessão por cookie HttpOnly, fala com o Keycloak
└── e2e/        # Playwright — fluxo crítico ponta a ponta
```

`site` e `bff` são dois *npm workspaces* (`web/package.json`). Correm em
processos separados mesmo em desenvolvimento.

## Porquê um BFF

`localStorage`/`sessionStorage` para tokens está proibido (ADR-0002 — XSS lê
ambos). O BFF autentica contra o Keycloak (Authorization Code + PKCE) e
guarda os tokens só no servidor; o site recebe apenas um cookie de sessão
`HttpOnly`/`Secure`/`SameSite`. O browser nunca vê o `access_token`.

## Arrancar em desenvolvimento

Pré-requisito: `infra/` a correr (`docker compose up` na raiz do
monorepo) — dá-te o Keycloak em `http://localhost:8081` e o backend em
`http://localhost:8080`.

```bash
npm install                                  # na raiz de web/ (workspaces)
cp bff/.env.example bff/.env                 # preenche KEYCLOAK_CLIENT_SECRET (infra/README.md)
cp site/.env.example site/.env               # opcional, VITE_USE_MOCKS=true já serve para prototipar

npm run dev:bff --workspace bff    # http://localhost:4000
npm run dev:site --workspace site  # http://localhost:5173 (proxy /api e /auth para o BFF)
```

Por omissão o site arranca em modo mock (`VITE_USE_MOCKS=true`) e não
precisa do BFF nem do backend a correr. Para testar o caminho real, muda
`VITE_USE_MOCKS=false` em `site/.env` — ver `site/README.md`.

Utilizadores de teste em `infra/README.md` (`customer.test@servimatch.pt`,
`provider.test@servimatch.pt`, `admin.test@servimatch.pt`).

## Cliente HTTP gerado

Nunca se escrevem chamadas HTTP à mão para o domínio (`/v1/...`) — o cliente
e os tipos são gerados a partir de `docs/api/openapi.yaml`:

```bash
npm run generate:api --workspace site
```

Gera `site/src/api/generated/schema.d.ts` (tipos) usado por `openapi-fetch`
em `site/src/services/http.ts`. Fica commitado de propósito (permite à
pipeline comparar contra uma nova geração e detetar deriva entre contrato e
código). Nunca editar à mão — se está errado, o contrato está errado.

Os únicos `fetch` escritos à mão são os três endpoints do próprio BFF
(`/auth/me`, `/auth/login`, `/auth/logout` — ver
`site/src/features/auth/bffClient.ts`), que **não** fazem parte do contrato
OpenAPI (é infraestrutura de sessão do cliente web, não uma capacidade de
domínio partilhada com o mobile — ADR-0009).

## Testes

```bash
npm run test                       # unitários + componente (site) e integração (bff), Vitest
npm run test:e2e                   # Playwright — fluxo crítico ponta a ponta
```

O `test:e2e` sobe automaticamente (via `webServer` do Playwright): um
Keycloak falso (`oauth2-mock-server`, Authorization Code + PKCE a sério, sem
UI de login), um backend de domínio falso derivado do contrato
(`e2e/mock-backend`), o BFF real e o site real (com `VITE_USE_MOCKS=false`,
para testar o caminho real, não a camada de mocks). Não depende do
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

# ServiMatch — site público (`web/site`)

Cliente web do ServiMatch: React 19 + Vite 7 + TypeScript strict + Tailwind
CSS v4. É a aplicação de frontend única do produto — substitui a SPA anterior
(`web/app`, removida). Fala sempre através do BFF (`web/bff`) em `/api` e
`/auth`; nunca diretamente com o backend Java nem com o Keycloak
(ADR-0002, ADR-0012).

## Arrancar

```bash
cd web              # a partir da raiz do monorepo
npm install          # workspaces: site + bff
cp bff/.env.example bff/.env   # preenche as variáveis do Keycloak local (infra/README.md)
npm run dev:bff --workspace bff    # noutro terminal — http://localhost:4000
npm run dev:site                    # http://localhost:5173
```

O site fala sempre com o backend real através do BFF (`/api`, `/auth`) — não
há modo mock nem `VITE_USE_MOCKS`. Os dados de desenvolvimento vivem na base
de dados (seed dev-only, ADR-0013) e chegam pelo backend real: sem
PostgreSQL + Keycloak (`docker compose up` na raiz do monorepo) a correr,
não há ecrã com dados. É o preço documentado no ADR-0013 D7 de a SPA ser
desenvolvida contra a API que vai para produção, não contra fixtures no
cliente.

## Autenticação sem IdP visível (ADR-0012)

Registo e login são formulários próprios da SPA (`/registar`, `/entrar`) —
o utilizador nunca vê o Keycloak, nem um redirect, nem a palavra "Keycloak"
em mensagem de erro. Consomem `POST /auth/register`/`POST /auth/login` no
BFF, que fala com o Keycloak *server-to-server* (Admin REST API para criar a
conta, Direct Access Grant para autenticar) — nenhum token, password ou
perfil sensível chega a `localStorage`/`sessionStorage`; a única fonte de
verdade da sessão no cliente é `GET /auth/me`
(`src/features/auth/AuthContext.tsx`).

- O login demora ~1s por desenho (piso de latência anti-enumeração no BFF,
  ADR-0012 D7.4): o botão fica desativado, sem duplo submit, e o cliente
  nunca tenta "otimizar" esse tempo.
- **Nunca distingas na UI** "email não existe" de "password errada" — o BFF
  devolve deliberadamente a mesma resposta.
- O registo é a única exceção documentada: um email já registado devolve
  `409 email-already-registered` (ver comentário em
  `web/bff/src/routes/auth.ts`) — o utilizador precisa de saber para poder
  agir. O login continua estritamente indistinguível.
- Reencaminhamento pós-sessão usa `returnTo` sanitizado
  (`src/lib/returnTo.ts`, espelha `web/bff/src/routes/auth.ts::sanitizeReturnTo`)
  num único ponto de navegação por ecrã (`LoginPage`/`RegisterPage`) — nunca
  dois `replace` concorrentes.

O toggle de tema claro/escuro é a **única** exceção a "nunca localStorage" —
guarda só a preferência (`sm-theme`), nunca sessão nem tokens. O rascunho do
assistente de publicação de pedido (`/pedidos/novo`) usa `sessionStorage`
(não `localStorage`), para sobreviver a uma navegação de topo/recarregamento
— ver comentário em `src/features/requests/draftStorage.ts`.

## Mapa de rotas

Públicas (sem login, indexáveis): `/`, `/como-funciona`, `/categorias`,
`/servicos/:slug`, `/prestadores`, `/prestadores/:id`, `/precos`, `/sobre`,
`/contactos`, `/faq`, `/termos`, `/privacidade`, `/entrar`, `/registar`, `*`
(404).

Autenticadas — cliente (`role: CUSTOMER`): `/pedidos/novo` (público até
publicar — o login só aparece no momento de submeter), `/painel`,
`/pedidos`, `/pedidos/:id`, `/conversas`, `/conversas/:id`,
`/avaliacoes/nova/:bookingId`.

Autenticadas — prestador (`role: PROVIDER`): `/pro`, `/pro/pedidos`,
`/pro/pedidos/:id`, `/pro/propostas`, `/pro/perfil`, `/pro/subscricao`.

## Design system

Tokens em `src/styles/theme.css` (`@theme` do Tailwind v4 — paleta navy/
orange/signal/mist, tipografia Archivo/Instrument Sans/IBM Plex Mono,
raios, sombras por tema, movimento). Base global, `::selection`, grão de
filme e utilitários compostos (`.text-gradient-energy`, `.surface-card`,
`.glow-accent`…) em `src/styles/global.css`. Dark é o tema por omissão; o
toggle fica em `src/hooks/useTheme.ts` + script inline em `index.html`
(evita flash antes da hidratação).

## Cliente HTTP gerado

```bash
npm run generate:api --workspace site
```

Gera `src/api/generated/schema.d.ts` a partir de
`../../docs/api/openapi.yaml` (`openapi-typescript`). Fica commitado de
propósito (deteta deriva contrato ↔ código na pipeline). Nunca editar à
mão — `src/services/types.ts` é o único ponto do resto da app que importa
o schema gerado. O cliente real (`openapi-fetch`) vive em
`src/services/http.ts`, e `src/services/http/*` é a **única** implementação
de cada serviço (sem bifurcação mock/HTTP — `src/services/index.ts` exporta
sempre a versão HTTP real). Os únicos `fetch` escritos à mão são os quatro
endpoints de sessão do BFF (`/auth/me`, `/auth/login`, `/auth/register`,
`/auth/logout` — `src/features/auth/bffClient.ts`), que não fazem parte do
contrato OpenAPI (ADR-0009/ADR-0012 D1: é infraestrutura de sessão do
cliente web, não uma capacidade de domínio partilhada com o mobile).

## Estado do contrato

Todos os endpoints que este site precisa já existem em
`docs/api/openapi.yaml`: perfil público do prestador
(`GET /v1/providers/{id}`), avaliações (`GET /v1/providers/{id}/reviews`),
perfil editável (`GET`/`PUT /v1/providers/me`), lista de conversas
(`GET /v1/conversations`), estado da subscrição (`GET /v1/subscriptions/me`,
`404` = nunca subscreveu, não um valor `NONE` no enum), detalhe de marcação
(`GET /v1/bookings/{bookingId}`), os meus pedidos (`GET /v1/requests`) e as
minhas propostas (`GET /v1/proposals/me`).

Duas lacunas conhecidas, ainda pendentes de pedido formal ao
`api-contract` (não contornadas com dados inventados no cliente):

1. **Agregados do dashboard do prestador** — sem endpoint de estatísticas
   dedicado; `services/http/providerDashboardService.ts` deriva o que
   consegue de `GET /v1/providers/me/requests`, `GET /v1/proposals/me` e
   `GET /v1/subscriptions/me`, e mostra zero em vez de inventar números
   (`estimatedRevenue`, `last30Days`).
2. **Correspondência `categoryIds`/`portfolioImageIds` no perfil editável**
   — `GET /v1/providers/me` devolve `categoryNames` (texto) e
   `portfolioImageUrls` (URLs assinados), mas `PUT` exige `categoryIds` e
   `portfolioImageIds`. A pré-seleção de categorias junta pelo nome (melhor
   esforço); o portfólio não tem forma de ser preservado num `PUT` de
   substituição total sem os IDs — `ProviderProfileEditPage` pede
   confirmação explícita antes de gravar se já existirem fotos.

Uma simplificação deliberada, não bloqueante: `GET /v1/search/providers`
devolve `ProviderSummary` (sem `location`), por isso a pesquisa de
prestadores não tem vista de mapa — só lista. Fabricar uma localização por
omissão no cliente seria pior do que não ter mapa nenhum.

## Testes

```bash
npm run test --workspace site       # Vitest — unitários + componente, fetch mockado
npm run test:e2e                    # a partir de web/ — Playwright, fluxo crítico real
```

Sem camada de mocks para os dados de domínio, os testes de componente que
exercitam `services/http/*` mockam `global.fetch` diretamente (ver
`src/test/mockFetch.ts` e `src/features/auth/bffClient.test.ts`,
`src/services/http/*.test.ts`) — nunca MSW nem uma segunda implementação de
serviço.

`test:e2e` sobe (via `webServer` do Playwright) um Keycloak falso
(`oauth2-mock-server`, Direct Access Grant + client credentials — já não
Authorization Code + PKCE, ADR-0012), uma Admin REST API falsa
(`../e2e/mock-oidc/server.ts`, só os quatro pedidos que o registo usa), um
backend de domínio falso derivado do contrato (`../e2e/mock-backend`), o BFF
real e este site — testa o caminho de registo/login real do BFF e o cliente
HTTP gerado, nunca uma camada de mocks do cliente (que não existe).

## Qualidade

```bash
npm run lint --workspace site
npm run typecheck --workspace site
npm run build --workspace site
```

TypeScript `strict`, sem `any` sem justificação explícita
(`eslint-disable` com comentário). Build, lint e testes têm de ficar
limpos antes de qualquer commit.

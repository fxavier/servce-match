# ServiMatch — site público (`web/site`)

Cliente web do ServiMatch: React 19 + Vite 7 + TypeScript strict + Tailwind
CSS v4. É a aplicação de frontend única do produto — substitui a SPA anterior
(`web/app`, removida). Fala sempre através do BFF (`web/bff`) em `/api` e
`/auth`; nunca diretamente com o backend Java nem com o Keycloak
(ADR-0002).

## Arrancar

```bash
cd web              # a partir da raiz do monorepo
npm install          # workspaces: site + bff
npm run dev:site     # http://localhost:5173, VITE_USE_MOCKS=true por omissão
```

Isto arranca **sem qualquer serviço externo**: dados portugueses credíveis
(31 categorias reais, 24 prestadores, 18 pedidos, 40 propostas, 3 planos —
confirmados contra o backend local a correr, ver `src/services/mock/fixtures/`),
latência artificial de 300–800 ms, cenários de erro RFC 9457 incluídos.

Para testar contra o BFF + backend reais:

```bash
cp .env.example .env
# edita .env: VITE_USE_MOCKS=false
npm run dev:bff --workspace bff     # noutro terminal, ver web/bff/README/.env.example
npm run dev:site
```

## Mock ↔ backend real (`VITE_USE_MOCKS`)

Um único ponto de decisão: `src/services/index.ts`. Todo o resto da app
importa `services` daí — nenhum componente sabe se está em modo mock.

| | `VITE_USE_MOCKS=true` (default em dev) | `VITE_USE_MOCKS=false` |
|---|---|---|
| Dados | `src/services/mock/*` — fixtures + latência simulada, tudo em memória | `src/services/http/*` — `openapi-fetch` contra `VITE_API_BASE` (BFF) |
| Autenticação | `/entrar` mostra um seletor de 3 perfis de demonstração, sessão só em `React state` (nunca `localStorage`/`sessionStorage`) | `/entrar` mostra um único botão "Entrar com o Keycloak" → `window.location.href = '/auth/login?returnTo=...'` (BFF, Authorization Code + PKCE real, cookie `HttpOnly`) |
| Gating de subscrição | Simulado no mock (ver painel de dev) | Decidido pelo backend real (403 `subscription-required`) |

O toggle de tema claro/escuro é a **única** exceção a "nunca localStorage" —
guarda só a preferência (`sm-theme`), nunca sessão nem tokens. O rascunho do
assistente de publicação de pedido (`/pedidos/novo`) usa `sessionStorage`
(não `localStorage`) para sobreviver ao redirect de topo do login real — ver
comentário em `src/features/requests/draftStorage.ts`.

## Contas de demonstração (modo mock)

| Perfil | Email (mesmo do realm Keycloak local) | O que mostra |
|---|---|---|
| Cliente | `customer.test@servimatch.pt` | Publica pedidos, vê propostas, aceita orçamentos |
| Prestador — subscrição ativa | `provider.test@servimatch.pt` | Inbox completa, pode enviar orçamentos |
| Prestador — sem subscrição | `provider.trial@servimatch.pt` (só existe no mock) | Painel de upsell desfocado em vez da inbox/formulário |

Painel de dev (canto inferior direito, só visível autenticado como
prestador em modo mock): toggle "Simular subscrição ativa" para alternar
entre os dois estados de gating sem trocar de conta.

## Mapa de rotas

Públicas (sem login, indexáveis): `/`, `/como-funciona`, `/categorias`,
`/servicos/:slug`, `/prestadores`, `/prestadores/:id`, `/precos`, `/sobre`,
`/contactos`, `/faq`, `/termos`, `/privacidade`, `/entrar`, `*` (404).

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
`src/services/http.ts`; os únicos `fetch` escritos à mão são os três
endpoints do BFF (`/auth/me`, `/auth/login`, `/auth/logout` —
`src/features/auth/bffClient.ts`), que não fazem parte do contrato
OpenAPI (ADR-0009).

## O que falta ligar ao backend real

Construir este site expôs lacunas no contrato — capacidades que a UI
precisa mas que `docs/api/openapi.yaml` ainda não expõe. Documentadas em
detalhe em `src/services/domainTypes.ts` e `src/services/http/*`
(cada método "gap" chama `notImplementedInContract(...)`, devolvendo um
`ProblemDetails` 501 explícito em vez de inventar dados ou um endpoint):

1. **Perfil público completo do prestador** — falta `GET /v1/providers/{id}`
   (bio, portfólio, zonas, distribuição de estrelas). O contrato só tem
   `ProviderSummary` (pesquisa/propostas).
2. **Lista de conversas** — falta `GET /v1/conversations`. O contrato só
   tem `GET /v1/conversations/{conversationId}/messages`.
3. **Estado da subscrição atual** — falta `GET /v1/subscriptions/me`. O
   contrato só tem `POST /v1/subscriptions` (iniciar checkout).
4. **Perfil editável do prestador** — falta `GET`/`PUT /v1/providers/me`
   (categorias, zonas, portfólio).
5. **Detalhe de uma `Booking`** — falta `GET /v1/bookings/{id}`. Só existe
   `POST .../complete`.
6. **Lista dos meus pedidos (cliente)** — falta `GET /v1/requests` com
   filtro por dono. Só existe `POST` (criar) e `GET` por id.
7. **Lista "as minhas propostas" (prestador)** — falta um endpoint
   dedicado; hoje só existe por pedido (`GET /v1/requests/{id}/proposals`)
   ou a inbox de pedidos elegíveis.
8. **Avaliações de um prestador** — falta `GET /v1/reviews?targetId=`. Só
   existe `POST /v1/reviews` (criar).
9. **Agregados do dashboard do prestador** — sem endpoint de estatísticas;
   o dashboard deriva o que consegue de `GET /v1/providers/me/requests` e
   mostra zero em vez de inventar números.

Nenhum destes foi contornado com dados inventados — em modo mock estão
totalmente simulados (dão a experiência completa); em modo HTTP real
devolvem um erro explícito e acionável, nunca um crash nem um número
fictício. Pedidos formais de extensão do contrato ficam para o
`api-contract`.

Duas simplificações deliberadas, não bloqueantes: (a) a contagem de
"profissionais ativos" por categoria não é mostrada em `/categorias` nem
na grelha da landing — não há agregado desse tipo no contrato; (b) o botão
"Conversar" numa proposta leva à lista de conversas (`/conversas`), não a
uma conversa específica — o contrato não liga `Proposal` a
`Conversation`.

## Testes

```bash
npm run test --workspace site       # Vitest — unitários + componente
npm run test:e2e                    # a partir de web/ — Playwright, fluxo crítico real
```

`test:e2e` sobe (via `webServer` do Playwright) um Keycloak falso
(`oauth2-mock-server`, Authorization Code + PKCE real), um backend de
domínio falso derivado do contrato, o BFF real e este site com
`VITE_USE_MOCKS=false` — testa o caminho de autenticação real e o cliente
HTTP gerado, não a camada de mocks.

## Qualidade

```bash
npm run lint --workspace site
npm run typecheck --workspace site
npm run build --workspace site
```

TypeScript `strict`, sem `any` sem justificação explícita
(`eslint-disable` com comentário). Build, lint e testes têm de ficar
limpos antes de qualquer commit.

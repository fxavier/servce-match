import { defineConfig, devices } from '@playwright/test';

const APP_PORT = 5175;
const BFF_PORT = 4001;
const MOCK_OIDC_PORT = 8092;
const MOCK_ADMIN_PORT = 8094;
const MOCK_BACKEND_PORT = 8093;

const APP_ORIGIN = `http://localhost:${APP_PORT}`;
const BFF_ORIGIN = `http://localhost:${BFF_PORT}`;
const MOCK_OIDC_ISSUER = `http://localhost:${MOCK_OIDC_PORT}`;
const MOCK_ADMIN_ORIGIN = `http://localhost:${MOCK_ADMIN_PORT}`;
const MOCK_BACKEND_ORIGIN = `http://localhost:${MOCK_BACKEND_PORT}`;

/**
 * E2E do fluxo crítico (CLAUDE.md §5): registar → publicar pedido → ver
 * propostas → aceitar proposta. Desde ADR-0012, autenticação é um
 * formulário próprio da SPA (`POST /auth/register`/`/auth/login` no BFF) —
 * não há mais Authorization Code + PKCE no caminho do utilizador
 * (`GET /auth/login`/`GET /auth/callback` ficam desligados,
 * `LEGACY_OIDC_FLOW_ENABLED=false`, o omisso). O BFF continua a falar
 * server-to-server com um Keycloak falso (`oauth2-mock-server`, Direct
 * Access Grant + client credentials) e com uma Admin REST API falsa
 * (`mock-oidc/server.ts`, só os quatro pedidos que o registo usa) — não
 * corre contra o Keycloak/backend reais, para não bloquear no trabalho
 * paralelo de `backend-domain`/`platform-infra` nesta onda.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  // O primeiro chunk lazy (React.lazy) de cada rota pode demorar mais que o
  // omisso do Vite dev server a compilar a frio na primeira navegação de
  // todo o conjunto de testes — o mesmo motivo documentado em
  // `web/site/src/routes/router.smoke.test.tsx`.
  expect: { timeout: 10_000 },
  use: {
    baseURL: APP_ORIGIN,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `PORT=${MOCK_OIDC_PORT} ADMIN_PORT=${MOCK_ADMIN_PORT} npx tsx mock-oidc/server.ts`,
      url: `${MOCK_OIDC_ISSUER}/.well-known/openid-configuration`,
      cwd: __dirname,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
    },
    {
      command: `PORT=${MOCK_BACKEND_PORT} npx tsx mock-backend/server.ts`,
      url: `${MOCK_BACKEND_ORIGIN}/v1/categories`,
      cwd: __dirname,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
    },
    {
      command: 'npx tsx ../bff/src/server.ts',
      url: `${BFF_ORIGIN}/healthz`,
      cwd: __dirname,
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
      env: {
        PORT: String(BFF_PORT),
        NODE_ENV: 'development',
        APP_ORIGIN,
        BFF_ORIGIN,
        BACKEND_ORIGIN: MOCK_BACKEND_ORIGIN,
        KEYCLOAK_ISSUER_URI: `${MOCK_OIDC_ISSUER}/realms/servimatch`,
        KEYCLOAK_ADMIN_API_BASE_URL: MOCK_ADMIN_ORIGIN,
        KEYCLOAK_CLIENT_ID: 'servimatch-bff',
        KEYCLOAK_CLIENT_SECRET: 'e2e-secret-not-for-production-use',
        COOKIE_SIGNING_SECRET: 'e2e-signing-secret-not-for-production-use',
        COOKIE_SECURE: 'false',
        // Prazo fixo do login (ADR-0012 D7.4) reduzido só para não pagar
        // ~1s por pedido de login neste E2E — o valor real de produção e o
        // seu comportamento anti-enumeração são testados ao nível de
        // unidade em `web/bff/test/loginTiming.test.ts`.
        LOGIN_TIMING_FLOOR_MS: '20',
        LOGIN_TIMING_QUANTUM_MS: '10',
      },
    },
    {
      command: 'npx vite --port 5175 --strictPort',
      url: APP_ORIGIN,
      cwd: '../site',
      reuseExistingServer: !process.env.CI,
      stdout: 'pipe',
      env: {
        VITE_BFF_ORIGIN: BFF_ORIGIN,
      },
    },
  ],
});

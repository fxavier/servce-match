import { defineConfig, devices } from '@playwright/test';

const APP_PORT = 5175;
const BFF_PORT = 4001;
const MOCK_OIDC_PORT = 8092;
const MOCK_BACKEND_PORT = 8093;

const APP_ORIGIN = `http://localhost:${APP_PORT}`;
const BFF_ORIGIN = `http://localhost:${BFF_PORT}`;
const MOCK_OIDC_ISSUER = `http://localhost:${MOCK_OIDC_PORT}`;
const MOCK_BACKEND_ORIGIN = `http://localhost:${MOCK_BACKEND_PORT}`;

/**
 * E2E do fluxo crítico (CLAUDE.md §5): autenticar → publicar pedido → ver
 * propostas → aceitar proposta. Não corre contra o Keycloak/backend reais —
 * usa um Keycloak falso (oauth2-mock-server, Authorization Code + PKCE a
 * sério) e um backend falso derivado do contrato, para não bloquear no
 * trabalho paralelo de `backend-domain`/`platform-infra` nesta onda.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    baseURL: APP_ORIGIN,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: `PORT=${MOCK_OIDC_PORT} npx tsx mock-oidc/server.ts`,
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
        KEYCLOAK_ISSUER_URI: MOCK_OIDC_ISSUER,
        KEYCLOAK_CLIENT_ID: 'servimatch-bff',
        KEYCLOAK_CLIENT_SECRET: 'e2e-secret-not-for-production-use',
        COOKIE_SIGNING_SECRET: 'e2e-signing-secret-not-for-production-use',
        COOKIE_SECURE: 'false',
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
        // Crítico: sem isto o site arrancaria em modo mock (default em dev)
        // e este E2E deixaria de testar o Authorization Code + PKCE real
        // contra o BFF — é precisamente o que este teste existe para provar.
        VITE_USE_MOCKS: 'false',
      },
    },
  ],
});

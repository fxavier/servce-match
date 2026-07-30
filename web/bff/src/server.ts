import { createApp } from './app.js';
import { loadConfig } from './config.js';
import { discoverKeycloak } from './oidc.js';
import { SessionStore } from './session.js';

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * A descoberta OIDC falha se o Keycloak ainda não estiver pronto (arranque a
 * frio em docker-compose, ou o mock do Keycloak nos testes E2E ainda a
 * subir). Repete com espera fixa em vez de desistir à primeira — evita
 * exigir uma ordem de arranque coordenada externamente.
 */
async function discoverWithRetry(
  config: Parameters<typeof discoverKeycloak>[0],
  attempts = 10,
  delayMs = 1000,
) {
  let lastError: unknown;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await discoverKeycloak(config);
    } catch (error) {
      lastError = error;
      // eslint-disable-next-line no-console
      console.warn(`[bff] descoberta OIDC falhou (tentativa ${attempt}/${attempts}), a repetir…`);
      await sleep(delayMs);
    }
  }
  throw lastError;
}

async function main(): Promise<void> {
  const config = loadConfig();
  const oidcConfig = await discoverWithRetry(config);
  const sessions = new SessionStore(config.session.absoluteTtlSeconds);
  const app = createApp({ config, oidcConfig, sessions });

  // Varrimento periódico de sessões cujo TTL absoluto passou (ADR-0012,
  // ciclo de vida da sessão) — `sessions.get()` já expira preguiçosamente
  // uma sessão consultada, mas isto liberta memória de sessões nunca mais
  // consultadas (ex.: browser fechado sem logout). `.unref()` para não
  // impedir o processo de terminar (ex.: em testes/scripts curtos).
  const sweepIntervalMs = config.session.sweepIntervalSeconds * 1000;
  setInterval(() => sessions.sweepExpired(), sweepIntervalMs).unref();

  app.listen(config.port, () => {
    // eslint-disable-next-line no-console
    console.log(`[bff] a ouvir em http://localhost:${config.port} (issuer: ${config.keycloak.issuerUri})`);
  });
}

main().catch((error: unknown) => {
  // eslint-disable-next-line no-console
  console.error('[bff] falha no arranque:', error);
  process.exit(1);
});

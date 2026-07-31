import type { Config } from '../src/config.js';

/**
 * Configuração de teste. `legacyOidcFlow.enabled` fica a `true` por omissão
 * AQUI (só nos testes) para não quebrar a suite existente do fluxo
 * Authorization Code + PKCE (`test/app.test.ts`) — em produção a omissão
 * (`config.ts`/`loadConfig`) é `false` (ADR-0012 D1/D10). Testes que
 * exercitam especificamente o comportamento por omissão (flag desligada)
 * fazem `testConfig({ legacyOidcFlow: { enabled: false } })` explicitamente.
 */
export function testConfig(overrides: Partial<Config> = {}): Config {
  return {
    port: 4000,
    nodeEnv: 'test',
    appOrigin: 'http://localhost:5173',
    bffOrigin: 'http://localhost:4000',
    backendOrigin: 'http://backend.test',
    keycloak: {
      issuerUri: 'https://kc.test/realms/servimatch',
      clientId: 'servimatch-bff',
      clientSecret: 'test-secret',
      adminApiBaseUrl: 'https://kc.test/admin/realms/servimatch',
    },
    cookies: {
      signingSecret: 'test-signing-secret-not-for-production-use-only',
      secure: false,
    },
    trustProxyHops: 0,
    legacyOidcFlow: {
      enabled: true,
    },
    authRateLimit: {
      windowMs: 60_000,
      maxPerIp: 20,
      maxPerEmail: 5,
    },
    loginTiming: {
      floorMs: 50,
      quantumMs: 20,
      maxDelayMs: 500,
    },
    registerTiming: {
      floorMs: 200,
      quantumMs: 50,
      maxDelayMs: 2_000,
    },
    session: {
      absoluteTtlSeconds: 60 * 60 * 12,
      sweepIntervalSeconds: 300,
    },
    ...overrides,
  };
}

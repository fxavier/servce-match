import type { Config } from '../src/config.js';

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
    },
    cookies: {
      signingSecret: 'test-signing-secret-not-for-production-use-only',
      secure: false,
    },
    ...overrides,
  };
}

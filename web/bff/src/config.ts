export interface Config {
  port: number;
  nodeEnv: string;
  appOrigin: string;
  bffOrigin: string;
  backendOrigin: string;
  keycloak: {
    issuerUri: string;
    clientId: string;
    clientSecret: string;
  };
  cookies: {
    signingSecret: string;
    secure: boolean;
  };
}

function required(env: NodeJS.ProcessEnv, name: string): string {
  const value = env[name];
  if (!value || value.trim() === '') {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function optional(env: NodeJS.ProcessEnv, name: string, fallback: string): string {
  const value = env[name];
  return value && value.trim() !== '' ? value : fallback;
}

/**
 * Carrega e valida a configuração a partir do ambiente. Falha cedo (fail
 * fast) se faltar algo — preferível a um BFF a arrancar "meio configurado"
 * e a falhar de forma confusa no primeiro pedido.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  return {
    port: Number(optional(env, 'PORT', '4000')),
    nodeEnv: optional(env, 'NODE_ENV', 'development'),
    appOrigin: optional(env, 'APP_ORIGIN', 'http://localhost:5173'),
    bffOrigin: optional(env, 'BFF_ORIGIN', 'http://localhost:4000'),
    backendOrigin: optional(env, 'BACKEND_ORIGIN', 'http://localhost:8080'),
    keycloak: {
      issuerUri: required(env, 'KEYCLOAK_ISSUER_URI'),
      clientId: required(env, 'KEYCLOAK_CLIENT_ID'),
      clientSecret: required(env, 'KEYCLOAK_CLIENT_SECRET'),
    },
    cookies: {
      signingSecret: required(env, 'COOKIE_SIGNING_SECRET'),
      secure: optional(env, 'COOKIE_SECURE', 'true') === 'true',
    },
  };
}

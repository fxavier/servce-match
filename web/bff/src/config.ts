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
    /**
     * Base da Admin REST API, ex.: `http://localhost:8081/admin/realms/servimatch`
     * (ADR-0012 D2). Por omissão derivada de `issuerUri` (troca `/realms/{realm}`
     * por `/admin/realms/{realm}`) — só precisa de ser definida explicitamente
     * se o Admin REST API viver noutro host/porta que o token endpoint.
     */
    adminApiBaseUrl: string;
  };
  cookies: {
    signingSecret: string;
    secure: boolean;
  };
  /**
   * Número explícito de saltos de proxy reverso a confiar para efeitos de
   * `X-Forwarded-For` (Express `trust proxy`). NUNCA `true`/`'true'` — um
   * `trust proxy` permissivo torna o rate limiting de D7 contornável com um
   * cabeçalho forjado (ADR-0012 D7, CLAUDE.md §4). Por omissão `0`: não
   * confia em nenhum salto, usa sempre o IP do socket TCP.
   */
  trustProxyHops: number;
  /**
   * Fluxo de regresso Authorization Code + PKCE (`GET /auth/login`,
   * `GET /auth/callback`) — ADR-0012 D1/D10, mantém-se no código como
   * caminho de regresso e para o mobile continuar RFC 8252 do lado do
   * backend/realm, mas fica desativado por omissão. Só pode ser ligado por
   * configuração do servidor — nunca por cabeçalho nem parâmetro de pedido.
   */
  legacyOidcFlow: {
    enabled: boolean;
  };
  /**
   * Rate limiting de `/auth/login` e `/auth/register`, por IP real e por
   * email, ANTES de contactar o Keycloak (ADR-0012 D7.1). O Keycloak deixa
   * de ver o IP do utilizador final com Direct Access Grant — esta é a
   * mitigação real.
   */
  authRateLimit: {
    windowMs: number;
    maxPerIp: number;
    maxPerEmail: number;
  };
  /**
   * Normalização de tempo de resposta do login (ADR-0012 D7.4) — prazo fixo
   * acima do p99 do caminho lento (password errada, que paga a derivação de
   * hash do Keycloak), com quantização para o múltiplo seguinte quando o
   * trabalho o excede, e uma válvula de escape para uma degradação do IdP
   * não segurar pedidos indefinidamente.
   */
  loginTiming: {
    floorMs: number;
    quantumMs: number;
    /** Válvula de escape: acima disto, responde sem esperar mais e regista telemetria. */
    maxDelayMs: number;
  };
  /**
   * Normalização de tempo de resposta do REGISTO (ADR-0012 D7.3/D7.4), pelo
   * mesmo mecanismo do login (`withNormalizedTiming`), mas com piso próprio:
   * o caminho de email novo pode fazer até 3 chamadas à Admin REST API
   * (criar utilizador, listar roles disponíveis, atribuir role) — mais
   * lento do que o caminho de email já existente (que não chama o Keycloak
   * de todo; só regista a tentativa na quota do login). `/auth/register`
   * nunca autentica nem estabelece sessão em nenhum dos dois casos — ver
   * `RegisterCoreResult` em `routes/auth.ts`. Sem um piso dimensionado para
   * o caminho mais pesado, a diferença de número de chamadas seria, ela
   * própria, um oráculo de tempo entre "email novo" e "email já registado".
   */
  registerTiming: {
    floorMs: number;
    quantumMs: number;
    /** Válvula de escape: acima disto, responde sem esperar mais e regista telemetria. */
    maxDelayMs: number;
  };
  session: {
    /**
     * TTL absoluto, fixado na criação e imune a renovação — uma sessão
     * roubada/vazada não pode ser mantida viva para sempre por refresh
     * contínuo.
     */
    absoluteTtlSeconds: number;
    /** Intervalo do varrimento periódico de sessões expiradas. */
    sweepIntervalSeconds: number;
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

function optionalInt(env: NodeJS.ProcessEnv, name: string, fallback: number): number {
  const value = env[name];
  if (!value || value.trim() === '') return fallback;
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed)) {
    throw new Error(`Invalid integer for environment variable: ${name}`);
  }
  return parsed;
}

function optionalBool(env: NodeJS.ProcessEnv, name: string, fallback: boolean): boolean {
  const value = env[name];
  if (value === undefined || value.trim() === '') return fallback;
  return value === 'true';
}

/**
 * Deriva a base da Admin REST API a partir do issuer OIDC:
 * `http://host:port/realms/{realm}` -> `http://host:port/admin/realms/{realm}`.
 * Falha alto e cedo se o issuer não tiver a forma esperada, em vez de
 * construir silenciosamente um URL de admin errado.
 */
function deriveAdminApiBaseUrl(issuerUri: string): string {
  const url = new URL(issuerUri);
  const match = url.pathname.match(/^\/realms\/([^/]+)\/?$/);
  if (!match) {
    throw new Error(
      `Cannot derive KEYCLOAK_ADMIN_API_BASE_URL from KEYCLOAK_ISSUER_URI (${issuerUri}); set it explicitly.`,
    );
  }
  const realm = match[1];
  return `${url.origin}/admin/realms/${realm}`;
}

/**
 * Carrega e valida a configuração a partir do ambiente. Falha cedo (fail
 * fast) se faltar algo — preferível a um BFF a arrancar "meio configurado"
 * e a falhar de forma confusa no primeiro pedido.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const issuerUri = required(env, 'KEYCLOAK_ISSUER_URI');
  const clientId = required(env, 'KEYCLOAK_CLIENT_ID');
  const clientSecret = required(env, 'KEYCLOAK_CLIENT_SECRET');

  return {
    port: optionalInt(env, 'PORT', 4000),
    nodeEnv: optional(env, 'NODE_ENV', 'development'),
    appOrigin: optional(env, 'APP_ORIGIN', 'http://localhost:5173'),
    bffOrigin: optional(env, 'BFF_ORIGIN', 'http://localhost:4000'),
    backendOrigin: optional(env, 'BACKEND_ORIGIN', 'http://localhost:8080'),
    keycloak: {
      issuerUri,
      clientId,
      clientSecret,
      adminApiBaseUrl: optional(
        env,
        'KEYCLOAK_ADMIN_API_BASE_URL',
        deriveAdminApiBaseUrl(issuerUri),
      ),
    },
    cookies: {
      signingSecret: required(env, 'COOKIE_SIGNING_SECRET'),
      secure: optional(env, 'COOKIE_SECURE', 'true') === 'true',
    },
    trustProxyHops: optionalInt(env, 'TRUST_PROXY_HOPS', 0),
    legacyOidcFlow: {
      enabled: optionalBool(env, 'LEGACY_OIDC_FLOW_ENABLED', false),
    },
    authRateLimit: {
      windowMs: optionalInt(env, 'AUTH_RATE_LIMIT_WINDOW_MS', 60_000),
      maxPerIp: optionalInt(env, 'AUTH_RATE_LIMIT_MAX_PER_IP', 20),
      maxPerEmail: optionalInt(env, 'AUTH_RATE_LIMIT_MAX_PER_EMAIL', 5),
    },
    loginTiming: {
      floorMs: optionalInt(env, 'LOGIN_TIMING_FLOOR_MS', 400),
      quantumMs: optionalInt(env, 'LOGIN_TIMING_QUANTUM_MS', 100),
      maxDelayMs: optionalInt(env, 'LOGIN_TIMING_MAX_DELAY_MS', 3_000),
    },
    registerTiming: {
      floorMs: optionalInt(env, 'REGISTER_TIMING_FLOOR_MS', 800),
      quantumMs: optionalInt(env, 'REGISTER_TIMING_QUANTUM_MS', 200),
      maxDelayMs: optionalInt(env, 'REGISTER_TIMING_MAX_DELAY_MS', 6_000),
    },
    session: {
      absoluteTtlSeconds: optionalInt(env, 'SESSION_ABSOLUTE_TTL_SECONDS', 60 * 60 * 12),
      sweepIntervalSeconds: optionalInt(env, 'SESSION_SWEEP_INTERVAL_SECONDS', 300),
    },
  };
}

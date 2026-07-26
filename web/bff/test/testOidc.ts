import * as jose from 'jose';
import * as client from 'openid-client';

/**
 * Constrói uma Configuration do openid-client com um `customFetch` que
 * simula um Keycloak isolado (sem rede real, sem Testcontainers) — cobre a
 * troca de código por tokens, o JWKS para validar o `id_token`, a revogação
 * e o end-session. Usa RS256 real (via `jose`) porque o openid-client valida
 * a assinatura do `id_token` a sério; um `id_token` falso sem chave válida
 * seria rejeitado, o que esconderia bugs em vez de os testar.
 */
export async function createTestOidcSetup() {
  const { publicKey, privateKey } = await jose.generateKeyPair('RS256');
  const kid = 'test-key';
  const jwk = await jose.exportJWK(publicKey);
  jwk.kid = kid;
  jwk.alg = 'RS256';
  jwk.use = 'sig';

  const issuer = 'https://kc.test/realms/servimatch';
  const serverMetadata: client.ServerMetadata = {
    issuer,
    authorization_endpoint: `${issuer}/protocol/openid-connect/auth`,
    token_endpoint: `${issuer}/protocol/openid-connect/token`,
    end_session_endpoint: `${issuer}/protocol/openid-connect/logout`,
    revocation_endpoint: `${issuer}/protocol/openid-connect/revoke`,
    jwks_uri: `${issuer}/protocol/openid-connect/certs`,
  };

  const clientId = 'servimatch-bff';
  const oidcConfig = new client.Configuration(serverMetadata, clientId, 'test-secret');

  let nextTokenResponse: Record<string, unknown> | (() => Record<string, unknown>) | undefined;

  /** Permite a um teste controlar a próxima resposta do token endpoint. */
  function queueTokenResponse(response: Record<string, unknown> | (() => Record<string, unknown>)): void {
    nextTokenResponse = response;
  }

  async function signIdToken(claims: {
    sub: string;
    email?: string;
    preferred_username?: string;
    nonce?: string;
  }): Promise<string> {
    return new jose.SignJWT({
      email: claims.email,
      preferred_username: claims.preferred_username,
      nonce: claims.nonce,
    })
      .setProtectedHeader({ alg: 'RS256', kid })
      .setSubject(claims.sub)
      .setIssuer(issuer)
      .setAudience(clientId)
      .setIssuedAt()
      .setExpirationTime('5m')
      .sign(privateKey);
  }

  function fakeAccessToken(
    sub: string,
    roles: string[],
    profile: { email?: string; preferredUsername?: string } = {},
  ): string {
    const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
    const payload = Buffer.from(
      JSON.stringify({
        sub,
        email: profile.email ?? `${sub}@example.test`,
        preferred_username: profile.preferredUsername ?? sub,
        realm_access: { roles },
        exp: Math.floor(Date.now() / 1000) + 300,
        aud: 'servimatch-backend',
      }),
    ).toString('base64url');
    // Sem verificação de assinatura no BFF (ver src/jwt.ts) — só o backend
    // (Resource Server) valida a assinatura a sério via JWKS.
    return `${header}.${payload}.test-signature`;
  }

  const revokedTokens: string[] = [];

  oidcConfig[client.customFetch] = async (...args: Parameters<typeof fetch>) => {
    const request = new Request(...args);
    const url = new URL(request.url);

    if (url.pathname.endsWith('/certs')) {
      return Response.json({ keys: [jwk] });
    }

    if (url.pathname.endsWith('/token')) {
      const body = nextTokenResponse
        ? typeof nextTokenResponse === 'function'
          ? nextTokenResponse()
          : nextTokenResponse
        : {};
      return Response.json({
        token_type: 'Bearer',
        expires_in: 300,
        ...body,
      });
    }

    if (url.pathname.endsWith('/revoke')) {
      const form = await request.formData();
      const token = form.get('token');
      if (typeof token === 'string') revokedTokens.push(token);
      return new Response(null, { status: 200 });
    }

    throw new Error(`Unhandled test fetch to ${request.url}`);
  };

  return {
    oidcConfig,
    issuer,
    clientId,
    signIdToken,
    fakeAccessToken,
    queueTokenResponse,
    revokedTokens,
  };
}

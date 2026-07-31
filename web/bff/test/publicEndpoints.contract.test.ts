import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { isPublicApiEndpoint } from '../src/publicEndpoints.js';

/**
 * Deriva a allowlist a partir do próprio contrato, para apanhar deriva entre
 * `docs/api/openapi.yaml`, `src/publicEndpoints.ts` e
 * `SecurityConfig.PUBLIC_GET_ENDPOINTS` (backend) — os três têm de
 * concordar.
 *
 * Deliberadamente NÃO um parser de YAML completo (nem dependência de
 * runtime): o formato de `docs/api/openapi.yaml` é gerado/mantido por um
 * único agente (`api-contract`) com indentação estável de 2 espaços por
 * nível, e isto corre só em teste, nunca no BFF em produção. Um
 * `path: -> method: -> security: []` por linha, na ordem em que aparecem, é
 * suficiente para extrair "que método+caminho o contrato declara público" —
 * se o formato do ficheiro alguma vez deixar de respeitar esta indentação,
 * este teste falha (ruidosamente) em vez de mentir.
 */
function extractSecuritylessOperationsFromContract(): Array<{ method: string; path: string }> {
  const contractPath = fileURLToPath(new URL('../../../docs/api/openapi.yaml', import.meta.url));
  const lines = readFileSync(contractPath, 'utf-8').split('\n');

  const pathLineRe = /^ {2}(\/\S+):$/;
  const methodLineRe = /^ {4}(get|post|put|delete|patch):$/;
  const securityLineRe = /^ {6}security: \[\]$/;

  const found: Array<{ method: string; path: string }> = [];
  let currentPath: string | undefined;
  let currentMethod: string | undefined;

  for (const line of lines) {
    const pathMatch = pathLineRe.exec(line);
    if (pathMatch) {
      currentPath = pathMatch[1];
      currentMethod = undefined;
      continue;
    }
    const methodMatch = methodLineRe.exec(line);
    if (methodMatch) {
      currentMethod = methodMatch[1];
      continue;
    }
    if (securityLineRe.test(line) && currentPath && currentMethod) {
      found.push({ method: currentMethod.toUpperCase(), path: currentPath });
    }
  }
  return found;
}

/** `{providerId}`, `{gateway}`, etc. -> um valor concreto de exemplo, para
 * poder chamar `isPublicApiEndpoint` com um caminho real. */
function toConcretePath(templatePath: string): string {
  return templatePath.replace(/\{[^}]+\}/g, 'sample-id');
}

describe('publicEndpoints — derivado do contrato (docs/api/openapi.yaml)', () => {
  const securityless = extractSecuritylessOperationsFromContract();

  it('encontrou operações com `security: []` no contrato (o parser não está a ler o ficheiro errado)', () => {
    expect(securityless.length).toBeGreaterThan(0);
  });

  it('tem exatamente 7 operações com `security: []` — 6 GET públicos + 1 POST (webhook, ver abaixo)', () => {
    // Se este número mudar, alguém alterou o contrato: confirma
    // conscientemente se o novo endpoint deve ou não atravessar o proxy do
    // BFF como público, e atualiza esta lista junto com
    // src/publicEndpoints.ts.
    expect(securityless).toHaveLength(7);
  });

  it('todas as operações GET com `security: []` no contrato são públicas segundo isPublicApiEndpoint', () => {
    const publicGetOperations = securityless.filter((op) => op.method === 'GET');
    expect(publicGetOperations).toHaveLength(6);

    for (const op of publicGetOperations) {
      const concretePath = toConcretePath(op.path);
      expect(isPublicApiEndpoint(op.method, concretePath)).toBe(true);
    }
  });

  it('a única operação não-GET com `security: []` é o webhook de pagamentos, e continua fora da allowlist', () => {
    const nonGetOperations = securityless.filter((op) => op.method !== 'GET');
    expect(nonGetOperations).toEqual([{ method: 'POST', path: '/v1/webhooks/payments/{gateway}' }]);

    for (const op of nonGetOperations) {
      const concretePath = toConcretePath(op.path);
      // Não é "público" no sentido deste proxy: autenticado pela assinatura
      // do gateway, verificada no backend, e chamado diretamente pelo
      // gateway — nunca através do BFF (ver comentário em publicEndpoints.ts).
      expect(isPublicApiEndpoint(op.method, concretePath)).toBe(false);
    }
  });

  it('sanidade: um endpoint de domínio comum, sem `security: []`, não aparece na lista derivada nem é público', () => {
    expect(securityless.some((op) => op.path === '/v1/requests')).toBe(false);
    expect(isPublicApiEndpoint('GET', '/v1/requests')).toBe(false);
  });
});

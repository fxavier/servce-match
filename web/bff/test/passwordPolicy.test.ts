import { describe, expect, it } from 'vitest';
import { genericPasswordPolicyProblem, validatePassword } from '../src/passwordPolicy.js';

describe('validatePassword (espelha infra/keycloak/realm-servimatch.json passwordPolicy)', () => {
  it('aceita uma password que cumpre todas as regras', () => {
    expect(validatePassword('Sup3r$ecreto!', 'user@example.pt')).toEqual([]);
  });

  it('reporta comprimento mínimo (10)', () => {
    const violations = validatePassword('Ab1!', 'user@example.pt');
    expect(violations.map((v) => v.code)).toContain('password-too-short');
  });

  it('reporta falta de dígito, minúscula, maiúscula e caráter especial, cada um com o seu código', () => {
    expect(validatePassword('SOMENTELETRAS', 'user@example.pt').map((v) => v.code)).toContain(
      'password-missing-digit',
    );
    expect(validatePassword('SOMENTELETRAS', 'user@example.pt').map((v) => v.code)).toContain(
      'password-missing-lowercase',
    );
    expect(validatePassword('somenteletras1!', 'user@example.pt').map((v) => v.code)).toContain(
      'password-missing-uppercase',
    );
    expect(validatePassword('SomenteLetras1', 'user@example.pt').map((v) => v.code)).toContain(
      'password-missing-special',
    );
  });

  it('rejeita password que contenha o email (notEmail)', () => {
    const violations = validatePassword('user@example.pt123!Aa', 'user@example.pt');
    expect(violations.map((v) => v.code)).toContain('password-contains-email');
  });

  it('todas as mensagens estão em português e nenhuma reencaminha texto do Keycloak', () => {
    const violations = validatePassword('abc', 'user@example.pt');
    for (const violation of violations) {
      expect(violation.message).not.toMatch(/[a-zA-Z]+Message|errorMessage/);
    }
  });
});

describe('genericPasswordPolicyProblem (falha residual do IdP, ADR-0012 D2)', () => {
  it('devolve sempre a mesma mensagem fechada, nunca o texto bruto do Keycloak', () => {
    const problem = genericPasswordPolicyProblem();
    expect(problem.code).toBe('password-policy-violation');
    expect(problem.message).toContain('política de segurança');
  });
});

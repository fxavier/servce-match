/**
 * Validação de password no registo, espelhando a política do realm
 * (`infra/keycloak/realm-servimatch.json` → `passwordPolicy`: `length(10)`,
 * `digits(1)`, `lowerCase(1)`, `upperCase(1)`, `specialChars(1)`,
 * `notUsername`, `notEmail`). Validar aqui primeiro evita um pedido à Admin
 * REST API destinado a falhar; a política real continua a ser imposta pelo
 * Keycloak (fonte de verdade), por isso qualquer falha residual do IdP
 * também é traduzida — nunca reencaminhada em bruto (ver
 * `genericPasswordPolicyProblem`).
 */
const MIN_LENGTH = 10;

export interface PasswordPolicyViolation {
  code: string;
  message: string;
}

export function validatePassword(password: string, email: string): PasswordPolicyViolation[] {
  const violations: PasswordPolicyViolation[] = [];

  if (password.length < MIN_LENGTH) {
    violations.push({
      code: 'password-too-short',
      message: `A password tem de ter pelo menos ${MIN_LENGTH} carateres.`,
    });
  }
  if (!/[0-9]/.test(password)) {
    violations.push({ code: 'password-missing-digit', message: 'A password tem de incluir pelo menos um número.' });
  }
  if (!/[a-z]/.test(password)) {
    violations.push({
      code: 'password-missing-lowercase',
      message: 'A password tem de incluir pelo menos uma letra minúscula.',
    });
  }
  if (!/[A-Z]/.test(password)) {
    violations.push({
      code: 'password-missing-uppercase',
      message: 'A password tem de incluir pelo menos uma letra maiúscula.',
    });
  }
  if (!/[^A-Za-z0-9]/.test(password)) {
    violations.push({
      code: 'password-missing-special',
      message: 'A password tem de incluir pelo menos um caráter especial.',
    });
  }

  const normalizedEmail = email.trim().toLowerCase();
  if (normalizedEmail.length > 0 && password.toLowerCase().includes(normalizedEmail)) {
    violations.push({ code: 'password-contains-email', message: 'A password não pode conter o email.' });
  }

  return violations;
}

/**
 * Conjunto fechado de uma única mensagem genérica para quando é o próprio
 * Keycloak (não a nossa pré-validação) que recusa a password — nunca se
 * reencaminha `errorMessage`/`error_description` do IdP tal e qual.
 */
export function genericPasswordPolicyProblem(): PasswordPolicyViolation {
  return {
    code: 'password-policy-violation',
    message:
      'A password não cumpre a política de segurança (mínimo 10 carateres, com maiúscula, minúscula, número e caráter especial).',
  };
}

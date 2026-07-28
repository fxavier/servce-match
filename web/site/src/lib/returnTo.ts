/**
 * Sanitiza o destino pós-login/logout no cliente — espelho da mesma lógica
 * no BFF (`web/bff/src/routes/auth.ts::sanitizeReturnTo`), com a mesma
 * correção de segurança (bypass por barra invertida, classe adjacente ao
 * CVE-2025-68470 no react-router: browsers normalizam `\` para `/`, por isso
 * `/\evil.example` e `\/evil.example` tornam-se `//evil.example` na
 * prática). Só aceita caminhos internos que comecem por `/` e não por `//`.
 */
export function sanitizeReturnTo(value: string | null | undefined): string {
  if (!value) return '/';
  const normalized = value.replace(/\\/g, '/');
  if (!normalized.startsWith('/') || normalized.startsWith('//')) {
    return '/';
  }
  return value;
}

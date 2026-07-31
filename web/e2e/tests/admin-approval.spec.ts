import { expect, test } from '@playwright/test';
import { SEED_ADMIN_EMAIL, SEED_PENDING_PROVIDER_ID } from '../fixtures.js';

/**
 * E2E do defeito C1 (`docs/ESTADO-DO-SISTEMA.md`): sem forma de aprovar um
 * prestador, `approval_status` fica `PENDING` para sempre. Cobre o fluxo
 * pela UI de `/admin` — login (ADR-0012, sem IdP visível) → decisão
 * (`PATCH /v1/admin/providers/{id}/approval`) → transição inválida tratada
 * sem partir → transição válida seguinte.
 *
 * Nota sobre âmbito: o contrato não tem (ainda) um `GET` para listar
 * prestadores `PENDING` — só o `PATCH` de decisão existe hoje
 * (`openapi.yaml:800`). Por isso este teste decide sobre um `providerId`
 * seed conhecido, tal como a consola em produção decide sobre um
 * `providerId` que o administrador já conhece — ver
 * `web/site/src/features/admin/AdminApprovalConsole.tsx`.
 */
test('administrador aprova e depois suspende um prestador — transição inválida não parte a UI', async ({
  page,
  context,
}) => {
  await page.goto('/entrar');
  await expect(page.getByRole('heading', { name: /^entrar$/i })).toBeVisible();

  await page.getByLabel(/^email/i).fill(SEED_ADMIN_EMAIL);
  await page.getByLabel(/^password/i).fill('QualquerPassword123!');
  await page.getByRole('button', { name: /^entrar$/i }).click();

  // Destino pós-login de uma conta só-ADMIN (features/auth/dashboardPath.ts):
  // sem CUSTOMER/PROVIDER, vai direto para /admin.
  await expect(page.getByRole('heading', { name: /aprovação de prestadores/i })).toBeVisible({ timeout: 10_000 });

  // Nunca o Keycloak, nem token legível em cookie.
  await expect(page.locator('body')).not.toContainText(/keycloak/i);
  const cookies = await context.cookies();
  for (const cookie of cookies) {
    expect(cookie.value).not.toMatch(/^eyJ/);
  }

  // PENDING → APPROVED (motivo opcional).
  await page.getByLabel(/identificador do prestador/i).fill(SEED_PENDING_PROVIDER_ID);
  await page.getByRole('radio', { name: /^aprovar$/i }).click();
  await page.getByRole('button', { name: /registar decisão/i }).click();

  await expect(page.getByText('APPROVED')).toBeVisible({ timeout: 10_000 });
  await expect(page.getByText(/decidido por/i)).toBeVisible();

  // Transição inválida (APPROVED → APPROVED, via "Aprovar" outra vez): o
  // servidor recusa com 409 — a consola mostra o erro sem rebentar, e o
  // formulário continua utilizável a seguir.
  await page.getByLabel(/identificador do prestador/i).fill(SEED_PENDING_PROVIDER_ID);
  await page.getByRole('radio', { name: /^aprovar$/i }).click();
  await page.getByRole('button', { name: /registar decisão/i }).click();

  await expect(page.getByRole('alert')).toBeVisible({ timeout: 10_000 });

  // APPROVED → SUSPENDED (motivo obrigatório — validado no cliente antes de ir ao servidor).
  await page.getByLabel(/identificador do prestador/i).fill(SEED_PENDING_PROVIDER_ID);
  await page.getByRole('radio', { name: /^suspender$/i }).click();
  await page.getByRole('button', { name: /registar decisão/i }).click();
  await expect(page.getByText(/o motivo é obrigatório/i)).toBeVisible();

  // `/^motivo$/` deixaria de bater certo assim que o campo passa a
  // obrigatório: o `<label>` inclui então o "*" no texto (a marca é só
  // `aria-hidden` para leitores de ecrã — `getByLabel` continua a olhar
  // para o texto completo do elemento).
  await page.getByLabel(/^motivo/i).fill('Reclamações confirmadas de vários clientes.');
  await page.getByRole('button', { name: /registar decisão/i }).click();

  await expect(page.getByText('SUSPENDED')).toBeVisible({ timeout: 10_000 });

  await expect(page.locator('body')).not.toContainText(/keycloak/i);
});

test('um cliente comum não vê a área de administração', async ({ page }) => {
  const email = `cliente.e2e.admin+${Date.now()}@example.com`;

  await page.goto('/registar');
  await page.getByLabel(/^nome/i).fill('Cliente Sem Acesso');
  await page.getByLabel(/^email/i).fill(email);
  await page.getByLabel(/^password/i).fill('ServiMatch2026!');
  await page.getByLabel(/^confirmar password/i).fill('ServiMatch2026!');
  await page.getByRole('button', { name: /criar conta/i }).click();
  await expect(page.getByRole('link', { name: /o meu painel/i })).toBeVisible({ timeout: 10_000 });

  // ProtectedRoute só esconde a UI (routes/ProtectedRoute.tsx) — a
  // autoridade real é o servidor — mas já basta para o cliente nunca ver o
  // formulário de decisão.
  await page.goto('/admin');
  await expect(page.getByRole('heading', { name: /não tens acesso a esta página/i })).toBeVisible({ timeout: 10_000 });
  await expect(page.getByRole('heading', { name: /aprovação de prestadores/i })).toHaveCount(0);
});

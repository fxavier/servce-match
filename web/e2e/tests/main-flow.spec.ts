import { expect, test } from '@playwright/test';

/**
 * Fluxo crítico ponta a ponta (CLAUDE.md §5): autenticar → publicar pedido →
 * ver propostas → aceitar proposta. Corre com um browser real, através do
 * BFF de verdade (Authorization Code + PKCE, cookie HttpOnly — ADR-0002)
 * contra um Keycloak e um backend simulados (ver e2e/mock-oidc,
 * e2e/mock-backend) para não bloquear no resto da onda.
 */
test('autenticar, publicar pedido, ver proposta e aceitar', async ({ page, context }) => {
  await page.goto('/login');
  await page.getByRole('button', { name: /entrar com o keycloak/i }).click();

  // Authorization Code + PKCE ponta a ponta: browser real a navegar através
  // do BFF e do IdP (mesmo que simulado) e a voltar autenticado.
  await expect(page.getByText(/sessão iniciada como/i)).toBeVisible();

  // O access_token nunca é acessível a JavaScript da página — só o cookie de
  // sessão do BFF (HttpOnly) e o cookie CSRF (não-HttpOnly, mas sem tokens).
  const cookies = await context.cookies();
  const sessionCookie = cookies.find((c) => c.name === 'sm_sid');
  expect(sessionCookie?.httpOnly).toBe(true);
  for (const cookie of cookies) {
    expect(cookie.value).not.toMatch(/^eyJ/); // não é um JWT em nenhum cookie legível
  }

  await page.getByRole('link', { name: /publicar novo pedido/i }).click();
  await expect(page.getByRole('heading', { name: /publicar um pedido/i })).toBeVisible();

  await page.getByLabel(/categoria/i).selectOption({ label: 'Canalização' });
  await page.getByLabel(/título do pedido/i).fill('Fuga na cozinha');
  await page.getByLabel(/^cidade$/i).fill('Lisboa');
  await page.getByRole('button', { name: /publicar pedido/i }).click();

  await expect(page.getByRole('heading', { name: 'Fuga na cozinha' })).toBeVisible();
  await expect(page.getByText('Estado: PUBLISHED')).toBeVisible();

  // Ver propostas — o backend "recebeu" uma proposta assincronamente (mock).
  await expect(page.getByText('Canalizações Silva')).toBeVisible();
  await expect(page.getByText(/75,00\s*€/)).toBeVisible();

  // Aceitar proposta.
  await page.getByRole('button', { name: /aceitar proposta/i }).click();
  await expect(page.getByText('Estado: ACCEPTED')).toBeVisible();
  await expect(page.getByRole('button', { name: /aceitar proposta/i })).toHaveCount(0);
});

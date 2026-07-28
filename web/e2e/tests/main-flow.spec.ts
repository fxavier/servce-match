import { expect, test } from '@playwright/test';

/**
 * Fluxo crítico ponta a ponta (CLAUDE.md §5): autenticar → publicar pedido →
 * ver propostas → aceitar proposta. Corre com um browser real, através do
 * BFF de verdade (Authorization Code + PKCE, cookie HttpOnly — ADR-0002)
 * contra um Keycloak e um backend simulados (ver e2e/mock-oidc,
 * e2e/mock-backend) para não bloquear no resto da onda.
 *
 * O site arranca com `VITE_USE_MOCKS=false` (ver playwright.config.ts) —
 * sem isto estaríamos a testar a camada de mocks em vez do caminho real de
 * autenticação e do cliente HTTP gerado.
 */
test('autenticar via Keycloak, publicar pedido, ver proposta e aceitar', async ({ page, context }) => {
  await page.goto('/entrar');
  await page.getByRole('button', { name: /entrar com o keycloak/i }).click();

  // Authorization Code + PKCE ponta a ponta: browser real a navegar através
  // do BFF e do IdP (mesmo que simulado) e a voltar autenticado à landing.
  await expect(page.getByRole('link', { name: /o meu painel/i })).toBeVisible();

  // O access_token nunca é acessível a JavaScript da página — só o cookie de
  // sessão do BFF (HttpOnly) e o cookie CSRF (não-HttpOnly, mas sem tokens).
  const cookies = await context.cookies();
  const sessionCookie = cookies.find((c) => c.name === 'sm_sid');
  expect(sessionCookie?.httpOnly).toBe(true);
  for (const cookie of cookies) {
    expect(cookie.value).not.toMatch(/^eyJ/); // não é um JWT em nenhum cookie legível
  }

  // Publicar um pedido — wizard de 4 passos.
  await page.goto('/pedidos/novo');
  await expect(page.getByRole('heading', { name: /publicar um pedido/i })).toBeVisible();

  await page.getByRole('radio', { name: 'Canalização' }).click();
  await page.getByRole('button', { name: /^seguinte$/i }).click();

  await page.getByLabel(/título do pedido/i).fill('Fuga na cozinha');
  await page.getByRole('button', { name: /^seguinte$/i }).click();

  await page.getByLabel(/morada/i).fill('Rua de Teste, 10');
  await page.getByLabel(/código postal/i).fill('1000-001');
  await page.getByLabel(/concelho/i).selectOption({ label: 'Lisboa' });
  await page.getByRole('button', { name: /^seguinte$/i }).click();

  // Passo 4 (fotografias, opcional) — publicar diretamente.
  await page.getByRole('button', { name: /publicar pedido/i }).click();

  await expect(page.getByRole('heading', { name: /pedido publicado/i })).toBeVisible();
  await page.getByRole('button', { name: /ver o meu pedido/i }).click();

  await expect(page.getByRole('heading', { name: 'Fuga na cozinha' })).toBeVisible();
  await expect(page.getByText('Canalização')).toBeVisible();

  // Ver propostas — o backend "recebeu" uma proposta assincronamente (mock).
  await expect(page.getByText('Canalizações Silva')).toBeVisible();
  await expect(page.getByText(/75,00\s*€/)).toBeVisible();

  // Aceitar proposta — abre diálogo de confirmação (§7: explica SUPERSEDED).
  await page.getByRole('button', { name: /^aceitar$/i }).click();
  await expect(page.getByRole('heading', { name: /aceitar este orçamento/i })).toBeVisible();
  await page.getByRole('button', { name: /confirmar aceitação/i }).click();

  await expect(page.getByRole('button', { name: /^aceitar$/i })).toHaveCount(0);
});

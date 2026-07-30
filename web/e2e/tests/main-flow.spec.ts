import { expect, test } from '@playwright/test';

/**
 * Fluxo crítico ponta a ponta (CLAUDE.md §5): registar → publicar pedido →
 * ver propostas → aceitar proposta.
 *
 * Desde o ADR-0012, registo e login são formulários próprios da SPA que
 * falam com `POST /auth/register`/`POST /auth/login` no BFF — não há mais
 * Authorization Code + PKCE no caminho do utilizador (o utilizador nunca vê
 * o Keycloak, nem um redirect, nem a palavra "Keycloak" em lado nenhum). O
 * BFF continua a falar *server-to-server* com um Keycloak falso
 * (`oauth2-mock-server`, Direct Access Grant + client credentials) e com
 * uma Admin REST API falsa (`../mock-oidc/server.ts`) para o registo —
 * simulados para não bloquear no trabalho paralelo de
 * `backend-domain`/`platform-infra` nesta onda, não para testar o Keycloak
 * em si.
 */
test('registar, publicar pedido, ver proposta e aceitar — nunca vendo o Keycloak', async ({ page, context }) => {
  const email = `cliente.e2e+${Date.now()}@example.com`;

  await page.goto('/registar');
  await expect(page.getByRole('heading', { name: /criar conta/i })).toBeVisible();

  await page.getByLabel(/^nome/i).fill('Mariana Costa');
  await page.getByLabel(/^email/i).fill(email);
  await page.getByLabel(/^password/i).fill('ServiMatch2026!');
  await page.getByLabel(/^confirmar password/i).fill('ServiMatch2026!');
  // "Sou cliente" já vem selecionado por omissão — o registo é sempre um
  // gesto explícito do utilizador, nunca um estado pré-preenchido a esconder
  // uma escolha (CLAUDE.md §4, ADR-0012).
  await page.getByRole('button', { name: /criar conta/i }).click();

  // Login imediato a seguir ao registo (ADR-0012 D2): sem redirect, sem
  // segundo ecrã — vai direto para o painel do cliente.
  await expect(page.getByRole('link', { name: /o meu painel/i })).toBeVisible({ timeout: 10_000 });

  // O utilizador nunca vê a palavra "Keycloak" em lado nenhum da página.
  await expect(page.locator('body')).not.toContainText(/keycloak/i);

  // O access_token/refresh_token nunca são acessíveis a JavaScript da
  // página — só o cookie de sessão do BFF (HttpOnly) e o cookie CSRF
  // (não-HttpOnly, mas sem tokens). Nenhum cookie legível contém um JWT.
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

  // Última verificação, ponta a ponta: em nenhum momento do fluxo a página
  // mencionou o IdP.
  await expect(page.locator('body')).not.toContainText(/keycloak/i);
});

test('login com uma conta já registada também nunca mostra o Keycloak', async ({ page, context }) => {
  const email = `cliente.e2e.login+${Date.now()}@example.com`;

  await page.goto('/registar');
  await page.getByLabel(/^nome/i).fill('Carlos Silva');
  await page.getByLabel(/^email/i).fill(email);
  await page.getByLabel(/^password/i).fill('ServiMatch2026!');
  await page.getByLabel(/^confirmar password/i).fill('ServiMatch2026!');
  await page.getByRole('button', { name: /criar conta/i }).click();
  await expect(page.getByRole('link', { name: /o meu painel/i })).toBeVisible({ timeout: 10_000 });

  // Termina sessão e volta a entrar pelo formulário próprio — não pelo
  // Keycloak (ADR-0012).
  await page.getByRole('button', { name: /terminar sessão/i }).click();
  await expect(page.getByRole('link', { name: /^entrar$/i })).toBeVisible();

  await page.goto('/entrar');
  await expect(page.getByRole('heading', { name: /^entrar$/i })).toBeVisible();
  await expect(page.locator('body')).not.toContainText(/keycloak/i);

  await page.getByLabel(/^email/i).fill(email);
  await page.getByLabel(/^password/i).fill('ServiMatch2026!');
  await page.getByRole('button', { name: /^entrar$/i }).click();

  await expect(page.getByRole('link', { name: /o meu painel/i })).toBeVisible({ timeout: 10_000 });

  const cookies = await context.cookies();
  const sessionCookie = cookies.find((c) => c.name === 'sm_sid');
  expect(sessionCookie?.httpOnly).toBe(true);
  for (const cookie of cookies) {
    expect(cookie.value).not.toMatch(/^eyJ/);
  }
  await expect(page.locator('body')).not.toContainText(/keycloak/i);
});

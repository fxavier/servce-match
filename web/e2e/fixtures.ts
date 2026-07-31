/**
 * Constantes partilhadas entre `mock-oidc/server.ts`, `mock-backend/server.ts`
 * e as specs de `tests/**` — sem efeitos secundários (nem `.listen()`, nem
 * `main()`). Os módulos de servidor arrancam ao serem importados (ver o
 * `main()` de `mock-oidc/server.ts`, invocado incondicionalmente); importar
 * esses módulos a partir de uma spec arranca-os uma SEGUNDA vez no processo
 * do Playwright e rebenta com `EADDRINUSE` contra a instância já lançada
 * pelo `webServer` do `playwright.config.ts` — por isso este ficheiro existe
 * à parte, sem nada além de literais.
 */

/** Ver `mock-oidc/server.ts` — conta `ADMIN` semeada fora do fluxo de registo. */
export const SEED_ADMIN_EMAIL = 'admin.e2e@example.com';

/** Ver `mock-backend/server.ts` — prestador `PENDING` semeado para o E2E de aprovação (defeito C1). */
export const SEED_PENDING_PROVIDER_ID = 'a419d818-cc7f-4910-a99b-c14cb157f3eb';

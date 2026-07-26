import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach } from 'vitest';
import { server } from './mocks/server';

// Chamado no topo do módulo (não dentro de `beforeAll`) de propósito: o MSW
// tem de substituir `globalThis.fetch` antes de `src/api/client.ts` ser
// carregado (que captura `fetch` no momento em que corre `createClient()`).
// Dentro de `beforeAll` isso corre tarde demais — os imports estáticos do
// ficheiro de teste já resolveram nessa altura.
server.listen({ onUnhandledRequest: 'error' });
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// BFF (sessão server-side, cookie HttpOnly) corre em localhost:4000 (ADR-0002).
// O site nunca fala diretamente com o backend nem com o Keycloak: tudo passa
// pelo BFF via /api e /auth, com cookies encaminhados (credentials: include).
const BFF_ORIGIN = process.env.VITE_BFF_ORIGIN ?? 'http://localhost:4000';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: BFF_ORIGIN, changeOrigin: true },
      '/auth': { target: BFF_ORIGIN, changeOrigin: true },
    },
  },
  build: {
    // Orçamento do bundle inicial (§10 da especificação): mantém o grosso do
    // peso em chunks só carregados por rota (React.lazy) — Leaflet e Motion
    // nunca entram no chunk de entrada.
    chunkSizeWarningLimit: 600,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    env: {
      // Fora de um browser real, `fetch`/`Request` (Node/undici, usados por
      // Vitest) não resolvem URLs relativos contra `window.location` como um
      // browser faria — `new URL('/api/...')` sem isto falha com "Failed to
      // parse URL". Só afeta testes (`services/http/*`); em dev/produção
      // continua a valer o omisso relativo `/api` (proxy do Vite/BFF,
      // ADR-0002), nunca isto.
      VITE_API_BASE: 'http://localhost/api',
    },
  },
});

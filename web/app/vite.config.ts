/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// BFF (server-side session, cookie HttpOnly) roda em localhost:4000 (ADR-0002).
// A SPA nunca fala diretamente com o backend nem com o Keycloak: tudo passa
// pelo BFF via /api e /auth, com cookies encaminhados (credentials: include).
const BFF_ORIGIN = process.env.VITE_BFF_ORIGIN ?? 'http://localhost:4000';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: BFF_ORIGIN, changeOrigin: true },
      '/auth': { target: BFF_ORIGIN, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});

import { http, HttpResponse } from 'msw';

/**
 * Mocks derivados do contrato (docs/api/openapi.yaml) — não inventam campos
 * novos, só populam os schemas existentes para os testes de componente.
 * Isto é o "mock derivado do contrato" pedido para não bloquear no backend.
 */
export const handlers = [
  http.get('/auth/me', () => HttpResponse.json({ authenticated: false })),

  http.get('/api/v1/categories', () =>
    HttpResponse.json([
      { id: 'cat-canalizacao', slug: 'canalizacao', name: 'Canalização', active: true, parentId: null },
      { id: 'cat-eletricidade', slug: 'eletricidade', name: 'Eletricidade', active: true, parentId: null },
    ]),
  ),
];

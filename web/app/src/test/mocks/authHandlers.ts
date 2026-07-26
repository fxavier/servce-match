import { http, HttpResponse } from 'msw';

export function authenticatedSession(overrides: { sub?: string; roles?: string[] } = {}) {
  return http.get('/auth/me', () =>
    HttpResponse.json({
      authenticated: true,
      user: {
        sub: overrides.sub ?? 'user-123',
        email: 'customer.test@servimatch.pt',
        username: 'customer.test',
        roles: overrides.roles ?? ['CUSTOMER'],
      },
    }),
  );
}

import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { server } from '../test/mocks/server';
import { ProviderInboxPage } from './ProviderInboxPage';

function renderPage() {
  return render(
    <MemoryRouter>
      <ProviderInboxPage />
    </MemoryRouter>,
  );
}

describe('ProviderInboxPage — gating de subscrição (caso de erro / estado de produto)', () => {
  it('trata 403 subscription-required como convite a subscrever, não como erro genérico', async () => {
    server.use(
      http.get('/api/v1/providers/me/requests', () =>
        HttpResponse.json(
          {
            type: 'https://errors.servimatch.pt/subscription-required',
            title: 'Subscrição inativa',
            status: 403,
            detail: 'É preciso uma subscrição ativa para veres a caixa de entrada.',
          },
          { status: 403, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    renderPage();

    expect(await screen.findByText(/subscrição ativa/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ver planos de subscrição/i })).toBeInTheDocument();
  });

  it('mostra a lista quando o servidor autoriza (caminho principal)', async () => {
    server.use(
      http.get('/api/v1/providers/me/requests', () =>
        HttpResponse.json({
          items: [
            {
              id: 'req-9',
              customerId: 'cust-1',
              title: 'Substituir torneira',
              status: 'PUBLISHED',
              createdAt: '2026-07-01T10:00:00Z',
              publishedAt: '2026-07-01T10:05:00Z',
            },
          ],
          page: { nextCursor: null },
        }),
      ),
    );

    renderPage();

    expect(await screen.findByText('Substituir torneira')).toBeInTheDocument();
  });
});

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { server } from '../test/mocks/server';
import { NewRequestPage } from './NewRequestPage';

function DetailStub() {
  const { requestId } = useParams<{ requestId: string }>();
  return <p>Pedido {requestId} publicado</p>;
}

function renderPage(route = '/requests/new') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/requests/new" element={<NewRequestPage />} />
        <Route path="/requests/:requestId" element={<DetailStub />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillMinimalForm(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('combobox', { name: /categoria/i });
  await user.selectOptions(screen.getByRole('combobox', { name: /categoria/i }), 'cat-canalizacao');
  await user.type(screen.getByLabelText(/título do pedido/i), 'Fuga na cozinha');
  await user.type(screen.getByLabelText(/^cidade$/i), 'Lisboa');
}

describe('NewRequestPage — publicar pedido (caminho principal)', () => {
  it('cria e publica o pedido, depois navega para o detalhe', async () => {
    server.use(
      http.post('/api/v1/requests', async () =>
        HttpResponse.json(
          { id: 'req-1', customerId: 'user-123', title: 'Fuga na cozinha', status: 'DRAFT', createdAt: new Date().toISOString() },
          { status: 201 },
        ),
      ),
      http.post('/api/v1/requests/:requestId/publish', ({ params }) =>
        HttpResponse.json({
          id: params.requestId,
          customerId: 'user-123',
          title: 'Fuga na cozinha',
          status: 'PUBLISHED',
          createdAt: new Date().toISOString(),
        }),
      ),
    );

    const user = userEvent.setup();
    renderPage();
    await fillMinimalForm(user);

    await user.click(screen.getByRole('button', { name: /publicar pedido/i }));

    expect(await screen.findByText('Pedido req-1 publicado')).toBeInTheDocument();
  });

  it('é navegável e operável por teclado (labels associadas, submissão por Enter)', async () => {
    server.use(
      http.post('/api/v1/requests', async () =>
        HttpResponse.json(
          { id: 'req-2', customerId: 'user-123', title: 'Fuga', status: 'DRAFT', createdAt: new Date().toISOString() },
          { status: 201 },
        ),
      ),
      http.post('/api/v1/requests/:requestId/publish', ({ params }) =>
        HttpResponse.json({
          id: params.requestId,
          customerId: 'user-123',
          title: 'Fuga',
          status: 'PUBLISHED',
          createdAt: new Date().toISOString(),
        }),
      ),
    );

    const user = userEvent.setup();
    renderPage();
    await fillMinimalForm(user);

    const titleInput = screen.getByLabelText(/título do pedido/i);
    titleInput.focus();
    await user.keyboard('{Enter}');

    expect(await screen.findByText('Pedido req-2 publicado')).toBeInTheDocument();
  });
});

describe('NewRequestPage — erro de validação (caso de erro)', () => {
  it('mostra o Problem Details RFC 9457 sem navegar', async () => {
    server.use(
      http.post('/api/v1/requests', () =>
        HttpResponse.json(
          {
            type: 'https://errors.servimatch.pt/validation',
            title: 'Dados inválidos',
            status: 422,
            detail: "O campo 'categoryId' é obrigatório.",
            errors: [{ field: 'categoryId', message: 'é obrigatório' }],
          },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const user = userEvent.setup();
    renderPage();
    await fillMinimalForm(user);
    await user.click(screen.getByRole('button', { name: /publicar pedido/i }));

    expect(await screen.findByText('Dados inválidos')).toBeInTheDocument();
    expect(screen.getAllByText(/é obrigatório/).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.queryByText(/publicado/)).not.toBeInTheDocument());
  });
});

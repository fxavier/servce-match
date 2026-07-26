import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';

interface Options {
  route?: string;
  /** Se a página usa `useParams` (ex. `/requests/:requestId`), passa o padrão da rota aqui. */
  path?: string;
}

export function renderWithProviders(ui: ReactElement, { route = '/', path }: Options = {}) {
  const content = path ? <Routes><Route path={path} element={ui} /></Routes> : ui;
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>{content}</AuthProvider>
    </MemoryRouter>,
  );
}

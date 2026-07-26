import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <main>
      <h1>Página não encontrada</h1>
      <p>
        <Link to="/">Voltar ao início</Link>
      </p>
    </main>
  );
}

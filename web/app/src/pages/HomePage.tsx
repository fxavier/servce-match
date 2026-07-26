import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

export function HomePage() {
  const { status, user, logout } = useAuth();

  return (
    <main>
      <h1>ServiMatch</h1>
      {status === 'authenticated' ? (
        <>
          <p>Sessão iniciada como {user?.username ?? user?.email ?? user?.sub}.</p>
          <nav aria-label="Principal">
            <ul>
              <li>
                <Link to="/requests/new">Publicar novo pedido</Link>
              </li>
              {user?.roles.includes('PROVIDER') ? (
                <li>
                  <Link to="/provider/inbox">Caixa de entrada de pedidos</Link>
                </li>
              ) : null}
            </ul>
          </nav>
          <button type="button" onClick={() => void logout()}>
            Terminar sessão
          </button>
        </>
      ) : (
        <p>
          <Link to="/login">Entra</Link> para publicar um pedido de serviço.
        </p>
      )}
    </main>
  );
}

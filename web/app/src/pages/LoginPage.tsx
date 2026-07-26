import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const ERROR_MESSAGES: Record<string, string> = {
  callback_expired: 'O pedido de login expirou. Tenta novamente.',
  callback_failed: 'Não foi possível concluir o login. Tenta novamente.',
};

export function LoginPage() {
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const returnTo = searchParams.get('returnTo') ?? '/';
  const errorCode = searchParams.get('error');

  return (
    <main>
      <h1>Entrar no ServiMatch</h1>
      <p>A autenticação é feita pelo Keycloak — nunca guardamos a tua password aqui.</p>
      {errorCode ? (
        <p role="alert">{ERROR_MESSAGES[errorCode] ?? 'Não foi possível autenticar. Tenta novamente.'}</p>
      ) : null}
      <button type="button" onClick={() => login(returnTo)}>
        Entrar com o Keycloak
      </button>
    </main>
  );
}

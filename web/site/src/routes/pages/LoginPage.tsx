/**
 * Em produção (`VITE_USE_MOCKS=false`) este ecrã não mostra o seletor de
 * perfis — `window.location.href = '/auth/login?returnTo=...'` redireciona
 * de imediato para o BFF, que conduz o Authorization Code + PKCE real contra
 * o Keycloak (ADR-0002). O seletor de perfis abaixo só existe em modo mock.
 */
import { useEffect, useState } from 'react';
import { Building2, User } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { SectionBackground } from '../../components/marketing/SectionBackground';
import { Reveal } from '../../components/motion/Reveal';
import { cn } from '../../lib/cn';
import { sanitizeReturnTo } from '../../lib/returnTo';
import { USE_MOCKS } from '../../services';
import { DEMO_PROFILES, type DemoProfileKey } from '../../features/auth/demoProfiles';
import { useAuth, useMockAuth } from '../../features/auth/useAuth';

export function LoginPage() {
  const [searchParams] = useSearchParams();
  const returnTo = sanitizeReturnTo(searchParams.get('returnTo'));

  if (!USE_MOCKS) {
    return <RealLoginRedirect returnTo={returnTo} />;
  }
  return <MockLoginScreen returnTo={returnTo} />;
}

const REAL_LOGIN_ERROR_MESSAGES: Record<string, string> = {
  callback_expired: 'O pedido de login expirou. Tenta novamente.',
  callback_failed: 'Não foi possível concluir o login. Tenta novamente.',
};

/** Ecrã real (produção): um único gesto explícito do utilizador desencadeia o
 * redirect Authorization Code + PKCE para o Keycloak via BFF — nunca automático. */
function RealLoginRedirect({ returnTo }: { returnTo: string }) {
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const errorCode = searchParams.get('error');

  return (
    <div className="mx-auto flex min-h-[60vh] max-w-md flex-col items-center justify-center px-5 text-center">
      <Seo title="Entrar" description="Inicia sessão no ServiMatch através do Keycloak." canonicalPath="/entrar" />
      <h1 className="text-h2 font-display font-bold text-foreground">Entrar no ServiMatch</h1>
      <p className="mt-3 text-body text-muted">A autenticação é feita pelo Keycloak — nunca guardamos a tua password aqui.</p>
      {errorCode ? (
        <p role="alert" className="mt-3 text-body font-medium text-orange-600">
          {REAL_LOGIN_ERROR_MESSAGES[errorCode] ?? 'Não foi possível autenticar. Tenta novamente.'}
        </p>
      ) : null}
      <button
        type="button"
        onClick={() => login(returnTo)}
        className="mt-6 inline-flex h-12 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-base font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110"
      >
        Entrar com o Keycloak
      </button>
    </div>
  );
}

const PROFILE_ICON: Record<DemoProfileKey, typeof User> = {
  customer: User,
  'provider-active': Building2,
  'provider-inactive': Building2,
};

function MockLoginScreen({ returnTo }: { returnTo: string }) {
  const navigate = useNavigate();
  const { loginAsDemoProfile, status, user } = useMockAuth();
  const [selected, setSelected] = useState<DemoProfileKey>('customer');

  useEffect(() => {
    if (status === 'authenticated' && user) {
      void navigate(returnTo, { replace: true });
    }
  }, [status, user, navigate, returnTo]);

  async function handleAuthenticate() {
    await loginAsDemoProfile(selected, returnTo);
  }

  return (
    <SectionBackground glow="both" grain className="min-h-[calc(100vh-4rem)]">
      <Seo title="Entrar" description="Escolha um perfil de demonstração para explorar o ServiMatch." canonicalPath="/entrar" />
      <div className="mx-auto grid min-h-[calc(100vh-4rem)] max-w-[1280px] gap-10 px-5 py-16 sm:px-8 lg:grid-cols-2 lg:items-center lg:px-10">
        <Reveal className="hidden lg:block">
          <p className="eyebrow text-signal-500">MODO DE DEMONSTRAÇÃO</p>
          <h1 className="mt-3 text-h1 font-display font-bold text-foreground">
            Explore o ServiMatch como <span className="text-gradient-energy">cliente</span> ou{' '}
            <span className="text-gradient-energy">prestador</span>
          </h1>
          <p className="mt-4 max-w-md text-body text-muted">
            Este protótipo usa autenticação simulada — nenhum token é guardado no navegador. Em produção, este ecrã é
            substituído pelo início de sessão real via Keycloak, através do BFF.
          </p>
        </Reveal>

        <Reveal delay={0.05}>
          <div className="surface-card mx-auto w-full max-w-md p-6">
            <h2 className="text-h2 font-display font-bold text-foreground lg:hidden">Entrar</h2>
            <p className="mt-1 text-body text-muted">Escolha um perfil de demonstração:</p>

            <div role="radiogroup" aria-label="Perfil de demonstração" className="mt-5 flex flex-col gap-3">
              {DEMO_PROFILES.map((profile) => {
                const Icon = PROFILE_ICON[profile.key];
                const isSelected = selected === profile.key;
                return (
                  <button
                    key={profile.key}
                    type="button"
                    role="radio"
                    aria-checked={isSelected}
                    onClick={() => setSelected(profile.key)}
                    className={cn(
                      'flex items-start gap-3 rounded-lg border p-4 text-left transition-colors',
                      isSelected ? 'border-orange-500 bg-orange-500/8' : 'border-line hover:border-orange-500/30',
                    )}
                  >
                    <span className="mt-0.5 inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-2">
                      <Icon aria-hidden="true" className="size-4.5 text-orange-500" strokeWidth={1.5} />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-foreground">{profile.label}</span>
                      <span className="mt-0.5 block text-caption text-muted">{profile.description}</span>
                    </span>
                  </button>
                );
              })}
            </div>

            <button
              type="button"
              onClick={() => void handleAuthenticate()}
              disabled={status === 'loading'}
              className="mt-6 inline-flex h-12 w-full items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 text-base font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110 disabled:opacity-60"
            >
              {status === 'loading' ? 'A entrar com o Keycloak…' : 'Entrar com o Keycloak'}
            </button>
            <p className="mt-3 text-center text-caption text-muted">
              Sessão simulada, em memória — nunca guardada em localStorage/sessionStorage.
            </p>
          </div>
        </Reveal>
      </div>
    </SectionBackground>
  );
}

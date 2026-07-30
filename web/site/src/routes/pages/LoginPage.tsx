/**
 * Formulário de login próprio da SPA (ADR-0012) — o utilizador nunca vê o
 * Keycloak: nem redirect, nem URL, nem a palavra em mensagem de erro.
 * Consome `POST /auth/login` no BFF (`features/auth/bffClient.ts`), que faz
 * um Direct Access Grant *server-to-server*; a resposta nunca inclui
 * tokens, só um cookie de sessão `HttpOnly`.
 */
import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { SectionBackground } from '../../components/marketing/SectionBackground';
import { Reveal } from '../../components/motion/Reveal';
import { ErrorState } from '../../components/ui/ErrorState';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { loginSchema, type LoginFormValues } from '../../features/auth/authSchemas';
import { defaultDashboardFor } from '../../features/auth/dashboardPath';
import { useAuth } from '../../features/auth/useAuth';
import { toProblem, type ProblemDetails } from '../../lib/problem';
import { sanitizeReturnTo } from '../../lib/returnTo';

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo = sanitizeReturnTo(searchParams.get('returnTo'));
  const { login } = useAuth();
  const [submitError, setSubmitError] = useState<ProblemDetails | undefined>(undefined);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  // Único ponto de navegação pós-sessão (evita dois `replace` concorrentes):
  // só este `onSubmit` chama `navigate` depois de autenticar.
  async function onSubmit(values: LoginFormValues) {
    setSubmitError(undefined);
    try {
      const user = await login(values);
      const target = returnTo !== '/' ? returnTo : defaultDashboardFor(user);
      void navigate(target, { replace: true });
    } catch (error) {
      setSubmitError(toProblem(error, 'Não foi possível entrar. Verifica os teus dados e tenta novamente.'));
    }
  }

  return (
    <SectionBackground glow="both" grain className="min-h-[calc(100vh-4rem)]">
      <Seo title="Entrar" description="Inicia sessão no ServiMatch." canonicalPath="/entrar" />
      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col justify-center px-5 py-16">
        <Reveal>
          <h1 className="text-h2 font-display font-bold text-foreground">Entrar</h1>
          <p className="mt-2 text-body text-muted">Entra com o teu email e a tua password.</p>

          <form
            onSubmit={(event) => void handleSubmit(onSubmit)(event)}
            noValidate
            className="surface-card mt-6 flex flex-col gap-4 p-6"
          >
            <Field id="login-email" label="Email" required error={errors.email?.message}>
              <Input
                id="login-email"
                type="email"
                autoComplete="email"
                invalid={Boolean(errors.email)}
                {...register('email')}
              />
            </Field>
            <Field id="login-password" label="Password" required error={errors.password?.message}>
              <Input
                id="login-password"
                type="password"
                autoComplete="current-password"
                invalid={Boolean(errors.password)}
                {...register('password')}
              />
            </Field>

            {submitError ? <ErrorState problem={submitError} /> : null}

            {/* Latência mínima ~1s é deliberada (anti-enumeração, ADR-0012 D7.4) —
                o botão fica desativado enquanto o pedido está em curso, sem
                tentar "otimizar" o tempo nem permitir duplo submit. */}
            <button
              type="submit"
              disabled={isSubmitting}
              className="mt-2 inline-flex h-12 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 text-base font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110 disabled:opacity-60"
            >
              {isSubmitting ? 'A entrar…' : 'Entrar'}
            </button>
          </form>

          <p className="mt-4 text-center text-caption text-muted">
            Ainda não tens conta?{' '}
            <Link
              to={`/registar?returnTo=${encodeURIComponent(returnTo)}`}
              className="font-medium text-orange-600 hover:underline"
            >
              Criar conta
            </Link>
          </p>
        </Reveal>
      </div>
    </SectionBackground>
  );
}

/**
 * Registo próprio da SPA (ADR-0012) — formulário (nome, email, password,
 * cliente ou prestador), sem redirect para o Keycloak. Consome
 * `POST /auth/register` no BFF: cria o utilizador no Keycloak via Admin
 * REST API e, na mesma resposta, tenta login imediato.
 *
 * Anti-enumeração (ADR-0012 D7.3, defeito C3.1 —
 * `docs/ESTADO-DO-SISTEMA.md`): email novo e email já registado devolvem a
 * MESMA resposta do BFF — mesmo `status` (`201`), mesmo corpo, mesmo tempo
 * (`web/bff/src/loginTiming.ts`). Este ecrã nunca tenta distinguir os dois
 * casos nem "ajudar" mais do que a resposta do servidor permite: se o
 * login automático pós-registo não confirmar sessão (`user` ausente), o
 * ecrã mostra sempre o mesmo passo seguinte ("entra com o email e a
 * password que indicaste"), quer a conta tenha acabado de ser criada quer
 * já existisse. O corpo do `201` não é a fonte de verdade da sessão — só
 * `GET /auth/me` é.
 */
import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Building2, User } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { SectionBackground } from '../../components/marketing/SectionBackground';
import { Reveal } from '../../components/motion/Reveal';
import { ErrorState } from '../../components/ui/ErrorState';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { cn } from '../../lib/cn';
import { registerSchema, type RegisterFormValues } from '../../features/auth/authSchemas';
import { defaultDashboardFor } from '../../features/auth/dashboardPath';
import { useAuth } from '../../features/auth/useAuth';
import { toProblem, type ProblemDetails } from '../../lib/problem';
import { sanitizeReturnTo } from '../../lib/returnTo';

const ROLE_OPTIONS = [
  { value: 'CUSTOMER' as const, label: 'Sou cliente', description: 'Quero publicar pedidos e receber orçamentos.', icon: User },
  { value: 'PROVIDER' as const, label: 'Sou prestador', description: 'Quero receber pedidos e enviar orçamentos.', icon: Building2 },
];

export function RegisterPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnTo = sanitizeReturnTo(searchParams.get('returnTo'));
  const { register: registerAccount } = useAuth();
  const [submitError, setSubmitError] = useState<ProblemDetails | undefined>(undefined);
  const [awaitingConfirmation, setAwaitingConfirmation] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { role: 'CUSTOMER' },
  });

  const role = watch('role');

  async function onSubmit(values: RegisterFormValues) {
    setSubmitError(undefined);
    try {
      const user = await registerAccount(values);
      if (user) {
        const target = returnTo !== '/' ? returnTo : defaultDashboardFor(user);
        void navigate(target, { replace: true });
        return;
      }
      // O BFF respondeu 201 (registo aceite — ver `web/bff/src/routes/auth.ts`,
      // ADR-0012 D7.3: o corpo é `{ registered: true }` LITERAL nos dois
      // casos, email novo ou já registado, e nunca `409` só por o email já
      // existir), mas `GET /auth/me` a seguir não confirmou sessão. Este
      // ramo é indistinguível, de propósito, entre "conta nova, login
      // automático falhou" e "email já tinha conta, a password submetida
      // não é a do titular" — não se repete a tentativa nem se assume qual
      // dos dois casos foi; pede-se para entrar manualmente.
      setAwaitingConfirmation(true);
    } catch (error) {
      setSubmitError(toProblem(error, 'Não foi possível concluir o registo. Tenta novamente.'));
    }
  }

  if (awaitingConfirmation) {
    return (
      <SectionBackground glow="both" grain className="min-h-[calc(100vh-4rem)]">
        <Seo title="Registo" description="Cria a tua conta no ServiMatch." canonicalPath="/registar" />
        <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col items-center justify-center px-5 py-16 text-center">
          {/* Título e texto deliberadamente neutros: este ecrã é o mesmo quer
              a conta tenha acabado de ser criada quer já existisse (ADR-0012
              D7.3) — "Conta criada" seria uma afirmação que nem sempre é
              verdade e que esta app não tem como confirmar aqui. */}
          <h1 className="text-h2 font-display font-bold text-foreground">Confirma o teu acesso</h1>
          <p className="mt-3 text-body text-muted">
            Não foi possível iniciar sessão automaticamente a seguir ao pedido de registo. Entra com o email e a
            password que indicaste.
          </p>
          <Link
            to={`/entrar?returnTo=${encodeURIComponent(returnTo)}`}
            className="mt-6 inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-sm font-medium text-accent-fg"
          >
            Ir para o ecrã de entrada
          </Link>
        </div>
      </SectionBackground>
    );
  }

  return (
    <SectionBackground glow="both" grain className="min-h-[calc(100vh-4rem)]">
      <Seo title="Criar conta" description="Cria a tua conta no ServiMatch." canonicalPath="/registar" />
      <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col justify-center px-5 py-16">
        <Reveal>
          <h1 className="text-h2 font-display font-bold text-foreground">Criar conta</h1>
          <p className="mt-2 text-body text-muted">Publica pedidos ou recebe-os — em menos de um minuto.</p>

          <form
            onSubmit={(event) => void handleSubmit(onSubmit)(event)}
            noValidate
            className="surface-card mt-6 flex flex-col gap-4 p-6"
          >
            <div role="radiogroup" aria-label="Tipo de conta" className="flex flex-col gap-2 sm:flex-row">
              {ROLE_OPTIONS.map((option) => {
                const Icon = option.icon;
                const isSelected = role === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    role="radio"
                    aria-checked={isSelected}
                    onClick={() => setValue('role', option.value, { shouldValidate: true })}
                    className={cn(
                      'flex flex-1 items-start gap-2.5 rounded-lg border p-3.5 text-left transition-colors',
                      isSelected ? 'border-orange-500 bg-orange-500/8' : 'border-line hover:border-orange-500/30',
                    )}
                  >
                    <span className="mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-surface-2">
                      <Icon aria-hidden="true" className="size-4 text-orange-500" strokeWidth={1.5} />
                    </span>
                    <span>
                      <span className="block text-sm font-semibold text-foreground">{option.label}</span>
                      <span className="mt-0.5 block text-caption text-muted">{option.description}</span>
                    </span>
                  </button>
                );
              })}
            </div>
            <input type="hidden" {...register('role')} />

            <Field id="register-name" label="Nome" required error={errors.name?.message}>
              <Input id="register-name" autoComplete="name" invalid={Boolean(errors.name)} {...register('name')} />
            </Field>
            <Field id="register-email" label="Email" required error={errors.email?.message}>
              <Input
                id="register-email"
                type="email"
                autoComplete="email"
                invalid={Boolean(errors.email)}
                {...register('email')}
              />
            </Field>
            <Field
              id="register-password"
              label="Password"
              required
              error={errors.password?.message}
              hint="Pelo menos 10 caracteres, com maiúscula, minúscula, número e caráter especial."
            >
              <Input
                id="register-password"
                type="password"
                autoComplete="new-password"
                invalid={Boolean(errors.password)}
                {...register('password')}
              />
            </Field>
            <Field id="register-confirm-password" label="Confirmar password" required error={errors.confirmPassword?.message}>
              <Input
                id="register-confirm-password"
                type="password"
                autoComplete="new-password"
                invalid={Boolean(errors.confirmPassword)}
                {...register('confirmPassword')}
              />
            </Field>

            {submitError ? <ErrorState problem={submitError} /> : null}

            <button
              type="submit"
              disabled={isSubmitting}
              className="mt-2 inline-flex h-12 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 text-base font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110 disabled:opacity-60"
            >
              {isSubmitting ? 'A criar conta…' : 'Criar conta'}
            </button>
          </form>

          <p className="mt-4 text-center text-caption text-muted">
            Já tens conta?{' '}
            <Link
              to={`/entrar?returnTo=${encodeURIComponent(returnTo)}`}
              className="font-medium text-orange-600 hover:underline"
            >
              Entrar
            </Link>
          </p>
        </Reveal>
      </div>
    </SectionBackground>
  );
}

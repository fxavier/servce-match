import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Info } from 'lucide-react';
import { Seo } from '../../components/Seo';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { useAuth } from '../../features/auth/useAuth';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

export function ProviderProfileEditPage() {
  const { user } = useAuth();
  const [saved, setSaved] = useState(false);

  const profileQuery = useQuery({
    queryKey: ['providers', 'detail', user?.id],
    queryFn: () => services.providers.get(user?.id as string),
    enabled: Boolean(user?.id),
  });

  const [headline, setHeadline] = useState('');
  const [bio, setBio] = useState('');

  if (profileQuery.isLoading) {
    return (
      <div className="max-w-2xl">
        <SkeletonText lines={5} />
      </div>
    );
  }

  const profile = profileQuery.data;

  return (
    <div className="max-w-2xl">
      <Seo title="O meu perfil" description="Edite o seu perfil público de prestador." />
      <h1 className="text-h2 font-display font-bold text-foreground">O meu perfil</h1>

      {profileQuery.error ? (
        <div className="mt-6">
          <ErrorState problem={toProblem(profileQuery.error)} onRetry={() => void profileQuery.refetch()} />
          <p className="mt-3 flex items-start gap-2 text-caption text-muted">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" strokeWidth={1.5} />
            Este ecrã depende de <code>GET/PUT /v1/providers/me</code>, que ainda não existe no contrato — só
            funciona em modo mock (<code>VITE_USE_MOCKS=true</code>) por agora.
          </p>
        </div>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            setSaved(true);
          }}
          className="surface-card mt-6 flex flex-col gap-4 p-6"
        >
          <Field id="profile-headline" label="Frase de destaque">
            <Input
              id="profile-headline"
              defaultValue={profile?.headline}
              value={headline || profile?.headline || ''}
              onChange={(event) => setHeadline(event.target.value)}
            />
          </Field>
          <Field id="profile-bio" label="Sobre a empresa" hint="Visível no seu perfil público.">
            <Textarea id="profile-bio" rows={5} value={bio || profile?.bio || ''} onChange={(event) => setBio(event.target.value)} />
          </Field>
          <p className="text-caption text-muted">
            Categorias e zonas de atuação são limitadas pelo seu plano atual — geridas em "Subscrição".
          </p>
          <button
            type="submit"
            className="inline-flex h-11 items-center justify-center self-start rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg"
          >
            Guardar alterações
          </button>
          {saved ? <p className="text-caption text-success">Alterações guardadas (simulado).</p> : null}
        </form>
      )}
    </div>
  );
}

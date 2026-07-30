import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Info } from 'lucide-react';
import { Seo } from '../../components/Seo';
import { Chip } from '../../components/ui/Chip';
import { ErrorState } from '../../components/ui/ErrorState';
import { Field } from '../../components/ui/Field';
import { Input } from '../../components/ui/Input';
import { SkeletonText } from '../../components/ui/Skeleton';
import { Textarea } from '../../components/ui/Textarea';
import { useCategories } from '../../features/categories/useCategories';
import { REGIONS } from '../../constants/regions';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

/**
 * Edição do perfil de prestador — `GET`/`PUT /v1/providers/me` (ADR
 * inexistente, apenas contrato: substituição total, ver
 * `UpdateProviderProfile`). Duas lacunas conhecidas, documentadas no README
 * em vez de contornadas com dados inventados:
 *
 * 1. `ProviderProfile` devolve `categoryNames` (texto), não `categoryIds` —
 *    a pré-seleção junta pelo nome contra `GET /v1/categories`, que é
 *    melhor esforço, não uma correspondência garantida por id.
 * 2. `ProviderProfile.portfolioImageUrls` são URLs assinados, sem o
 *    `imageId` que `PUT` exige em `portfolioImageIds` — não há forma de
 *    preservar o portfólio existente num `PUT` de substituição total sem
 *    essa correspondência. Por isso o formulário nunca envia
 *    `portfolioImageIds` vazio silenciosamente quando já há fotos: pede
 *    confirmação explícita.
 */
export function ProviderProfileEditPage() {
  const queryClient = useQueryClient();
  const { data: categories } = useCategories();
  const [saved, setSaved] = useState(false);
  const [headline, setHeadline] = useState('');
  const [bio, setBio] = useState('');
  const [categoryIds, setCategoryIds] = useState<string[]>([]);
  const [regionCodes, setRegionCodes] = useState<string[]>([]);
  const [confirmPortfolioLoss, setConfirmPortfolioLoss] = useState(false);

  const profileQuery = useQuery({
    queryKey: ['providers', 'me'],
    queryFn: () => services.providers.getMine(),
  });

  const profile = profileQuery.data;

  useEffect(() => {
    if (!profile || !categories) return;
    setHeadline(profile.headline ?? '');
    setBio(profile.bio);
    setCategoryIds(categories.filter((category) => profile.categoryNames.includes(category.name)).map((category) => category.id));
    setRegionCodes(profile.zones.map((zone) => zone.regionCode));
  }, [profile, categories]);

  const hasExistingPortfolio = (profile?.portfolioImageUrls.length ?? 0) > 0;

  const updateMutation = useMutation({
    mutationFn: () =>
      services.providers.updateMine({
        headline,
        bio,
        categoryIds,
        regionCodes,
        portfolioImageIds: [],
      }),
    onSuccess: () => {
      setSaved(true);
      void queryClient.invalidateQueries({ queryKey: ['providers', 'me'] });
    },
  });

  function toggleCategory(id: string) {
    setSaved(false);
    setCategoryIds((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  }

  function toggleRegion(code: string) {
    setSaved(false);
    setRegionCodes((prev) => (prev.includes(code) ? prev.filter((item) => item !== code) : [...prev, code]));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (hasExistingPortfolio && !confirmPortfolioLoss) return;
    setSaved(false);
    updateMutation.mutate();
  }

  if (profileQuery.isLoading) {
    return (
      <div className="max-w-2xl">
        <SkeletonText lines={5} />
      </div>
    );
  }

  return (
    <div className="max-w-2xl">
      <Seo title="O meu perfil" description="Edite o seu perfil público de prestador." />
      <h1 className="text-h2 font-display font-bold text-foreground">O meu perfil</h1>

      {profileQuery.error ? (
        <div className="mt-6">
          <ErrorState problem={toProblem(profileQuery.error)} onRetry={() => void profileQuery.refetch()} />
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="surface-card mt-6 flex flex-col gap-4 p-6">
          <Field id="profile-headline" label="Frase de destaque">
            <Input
              id="profile-headline"
              value={headline}
              onChange={(event) => {
                setSaved(false);
                setHeadline(event.target.value);
              }}
              maxLength={160}
            />
          </Field>
          <Field id="profile-bio" label="Sobre a empresa" hint="Visível no seu perfil público.">
            <Textarea
              id="profile-bio"
              rows={5}
              value={bio}
              onChange={(event) => {
                setSaved(false);
                setBio(event.target.value);
              }}
              maxLength={4000}
            />
          </Field>

          <div>
            <span className="text-sm font-medium text-foreground">Categorias</span>
            <div className="mt-2 flex flex-wrap gap-2">
              {(categories ?? []).map((category) => (
                <Chip key={category.id} selected={categoryIds.includes(category.id)} onClick={() => toggleCategory(category.id)}>
                  {category.name}
                </Chip>
              ))}
            </div>
          </div>

          <div>
            <span className="text-sm font-medium text-foreground">Zonas de atuação</span>
            <div className="mt-2 flex flex-wrap gap-2">
              {REGIONS.map((region) => (
                <Chip key={region.code} selected={regionCodes.includes(region.code)} onClick={() => toggleRegion(region.code)}>
                  {region.label}
                </Chip>
              ))}
            </div>
          </div>

          {hasExistingPortfolio ? (
            <div className="flex flex-col gap-2 rounded-md border border-orange-500/30 bg-orange-500/5 p-3.5">
              <p className="flex items-start gap-2 text-caption text-muted">
                <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" strokeWidth={1.5} />
                Este formulário ainda não gere fotos de portfólio individualmente — guardar substitui a lista completa
                de fotos por uma lista vazia (limitação atual, gap reportado ao <code>api-contract</code>).
              </p>
              <label htmlFor="confirm-portfolio-loss" className="flex items-center gap-2 text-caption text-foreground">
                <input
                  id="confirm-portfolio-loss"
                  type="checkbox"
                  checked={confirmPortfolioLoss}
                  onChange={(event) => setConfirmPortfolioLoss(event.target.checked)}
                />
                Entendo que isto remove as fotos de portfólio atuais.
              </label>
            </div>
          ) : null}

          <p className="text-caption text-muted">Categorias e zonas de atuação são limitadas pelo seu plano atual.</p>

          {updateMutation.isError ? <ErrorState problem={toProblem(updateMutation.error)} /> : null}

          <button
            type="submit"
            disabled={updateMutation.isPending || (hasExistingPortfolio && !confirmPortfolioLoss)}
            className="inline-flex h-11 items-center justify-center self-start rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg disabled:opacity-50"
          >
            {updateMutation.isPending ? 'A guardar…' : 'Guardar alterações'}
          </button>
          {saved ? <p className="text-caption text-success">Alterações guardadas.</p> : null}
        </form>
      )}
    </div>
  );
}

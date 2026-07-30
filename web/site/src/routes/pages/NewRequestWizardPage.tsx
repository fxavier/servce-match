import { useEffect, useMemo, useRef, useState } from 'react';
import { CheckCircle2, Loader2 } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { ErrorState } from '../../components/ui/ErrorState';
import { Reveal } from '../../components/motion/Reveal';
import { StepAddress, type StepAddressValue } from '../../features/requests/wizard/StepAddress';
import { StepCategory } from '../../features/requests/wizard/StepCategory';
import { StepDetails, type StepDetailsValue } from '../../features/requests/wizard/StepDetails';
import { StepPhotos, type PendingPhoto } from '../../features/requests/wizard/StepPhotos';
import { ReviewPanel } from '../../features/requests/wizard/ReviewPanel';
import { clearDraft, loadDraft, saveDraft } from '../../features/requests/draftStorage';
import { EMPTY_DRAFT, stepAddressSchema, stepCategorySchema, stepDetailsSchema, type WizardDraft } from '../../features/requests/wizardSchemas';
import { useAuth } from '../../features/auth/useAuth';
import { cn } from '../../lib/cn';
import { fieldErrorsFrom, toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { ServiceRequest } from '../../services/types';

const STEP_LABELS = ['Categoria', 'Descrição', 'Morada', 'Fotografias'];

export function NewRequestWizardPage() {
  const navigate = useNavigate();
  const { status } = useAuth();
  const [searchParams] = useSearchParams();

  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<WizardDraft>(EMPTY_DRAFT);
  const [photos, setPhotos] = useState<PendingPhoto[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [publishing, setPublishing] = useState(false);
  const [publishError, setPublishError] = useState<ReturnType<typeof toProblem>>();
  const [publishedRequest, setPublishedRequest] = useState<ServiceRequest | undefined>(undefined);
  const hasResumedRef = useRef(false);

  // Restaura o rascunho (guest ou pós-login) uma única vez.
  useEffect(() => {
    if (hasResumedRef.current) return;
    hasResumedRef.current = true;
    const stored = loadDraft();
    if (stored) {
      setDraft(stored);
      if (stored.line1) setStep(3);
      else if (stored.title) setStep(2);
      else if (stored.categoryId) setStep(1);
    } else {
      const categoria = searchParams.get('categoria');
      const zona = searchParams.get('zona');
      if (categoria || zona) {
        setDraft((prev) => ({ ...prev, categoryId: categoria ?? prev.categoryId, city: zona ?? prev.city }));
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- só corre uma vez, de propósito.
  }, []);

  // Auto-save do rascunho a cada alteração (§7).
  useEffect(() => {
    saveDraft(draft);
  }, [draft]);

  const publishRequest = useMemo(
    () => async (currentDraft: WizardDraft) => {
      setPublishing(true);
      setPublishError(undefined);
      try {
        const created = await services.requests.create({
          categoryId: currentDraft.categoryId,
          title: currentDraft.title,
          description: currentDraft.description || undefined,
          urgency: currentDraft.urgency,
          availability: currentDraft.availability || undefined,
          address: {
            line1: currentDraft.line1,
            postalCode: currentDraft.postalCode,
            city: currentDraft.city,
            regionCode: currentDraft.regionCode,
            country: 'PT',
            location: { lat: currentDraft.lat, lon: currentDraft.lon },
          },
        });
        const published = await services.requests.publish(created.id);
        clearDraft();
        setPublishedRequest(published);
      } catch (error) {
        setPublishError(toProblem(error, 'Não foi possível publicar o pedido.'));
      } finally {
        setPublishing(false);
      }
    },
    [],
  );

  // Retoma automaticamente a publicação pendente assim que a sessão fica autenticada.
  useEffect(() => {
    if (status === 'authenticated' && draft.pendingPublish && !publishing && !publishedRequest) {
      void publishRequest({ ...draft, pendingPublish: false });
      setDraft((prev) => ({ ...prev, pendingPublish: false }));
    }
  }, [status, draft, publishing, publishedRequest, publishRequest]);

  function updateDetails(patch: Partial<StepDetailsValue>) {
    setDraft((prev) => ({ ...prev, ...patch }));
  }

  function updateAddress(patch: Partial<StepAddressValue>) {
    setDraft((prev) => ({ ...prev, ...patch }));
  }

  function addPhotos(files: File[]) {
    const accepted = files.filter((file) => ['image/jpeg', 'image/png', 'image/webp'].includes(file.type));
    const next: PendingPhoto[] = accepted.map((file) => ({
      id: crypto.randomUUID(),
      name: file.name,
      previewUrl: URL.createObjectURL(file),
      progress: 0,
    }));
    setPhotos((prev) => [...prev, ...next]);
    setDraft((prev) => ({ ...prev, imageNames: [...prev.imageNames, ...accepted.map((f) => f.name)] }));
    // Barra de progresso simulada (§7 — não há upload real neste protótipo).
    next.forEach((photo) => {
      let progress = 0;
      const interval = setInterval(() => {
        progress += 20 + Math.random() * 30;
        setPhotos((prev) => prev.map((p) => (p.id === photo.id ? { ...p, progress: Math.min(100, progress) } : p)));
        if (progress >= 100) clearInterval(interval);
      }, 250);
    });
  }

  function removePhoto(id: string) {
    setPhotos((prev) => prev.filter((photo) => photo.id !== id));
  }

  function goNext() {
    if (step === 0) {
      const result = stepCategorySchema.safeParse({ categoryId: draft.categoryId });
      if (!result.success) {
        setErrors({ categoryId: result.error.issues[0]?.message ?? 'Escolha uma categoria.' });
        return;
      }
    }
    if (step === 1) {
      const result = stepDetailsSchema.safeParse(draft);
      if (!result.success) {
        setErrors(Object.fromEntries(result.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
        return;
      }
    }
    if (step === 2) {
      const result = stepAddressSchema.safeParse(draft);
      if (!result.success) {
        setErrors(Object.fromEntries(result.error.issues.map((issue) => [String(issue.path[0]), issue.message])));
        return;
      }
    }
    setErrors({});
    setStep((prev) => Math.min(prev + 1, STEP_LABELS.length - 1));
  }

  function goBack() {
    setErrors({});
    setStep((prev) => Math.max(prev - 1, 0));
  }

  async function handlePublish() {
    if (status === 'unauthenticated') {
      setDraft((prev) => {
        const withPending = { ...prev, pendingPublish: true };
        saveDraft(withPending);
        return withPending;
      });
      void navigate(`/entrar?returnTo=${encodeURIComponent('/pedidos/novo')}`);
      return;
    }
    await publishRequest(draft);
  }

  if (publishedRequest) {
    return <SuccessScreen request={publishedRequest} onViewRequest={() => void navigate(`/pedidos/${publishedRequest.id}`)} />;
  }

  const fieldErrors = publishError ? fieldErrorsFrom(publishError) : [];

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:px-10">
      <Seo title="Publicar um pedido" description="Publique um pedido de serviço e receba orçamentos grátis." canonicalPath="/pedidos/novo" />

      <Reveal>
        <p className="eyebrow text-signal-500">NOVO PEDIDO</p>
        <h1 className="text-h1 mt-3 font-display font-bold text-foreground">Publicar um pedido</h1>
      </Reveal>

      <ol className="mt-8 flex items-center gap-2" aria-label="Progresso do formulário">
        {STEP_LABELS.map((label, index) => (
          <li key={label} className="flex flex-1 flex-col gap-2">
            <div
              className={cn('h-1.5 rounded-full transition-colors', index <= step ? 'bg-orange-500' : 'bg-line')}
              aria-hidden="true"
            />
            <span className={cn('hidden text-caption sm:block', index === step ? 'font-medium text-foreground' : 'text-muted')}>
              {index + 1}. {label}
            </span>
          </li>
        ))}
      </ol>

      <div className="mt-10 flex flex-col gap-8 lg:flex-row">
        <div className="flex-1">
          {step === 0 ? (
            <StepCategory categoryId={draft.categoryId} onChange={(categoryId) => setDraft((prev) => ({ ...prev, categoryId }))} error={errors.categoryId} />
          ) : null}
          {step === 1 ? <StepDetails value={draft} onChange={updateDetails} errors={errors} /> : null}
          {step === 2 ? <StepAddress value={draft} onChange={updateAddress} errors={errors} /> : null}
          {step === 3 ? <StepPhotos photos={photos} onAdd={addPhotos} onRemove={removePhoto} /> : null}

          {publishError ? (
            <div className="mt-6">
              <ErrorState problem={publishError} />
              {fieldErrors.length > 0 ? (
                <ul className="mt-3 flex flex-col gap-1">
                  {fieldErrors.map((fieldError) => (
                    <li key={fieldError.field} className="text-caption text-orange-600">
                      {fieldError.field}: {fieldError.message}
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : null}

          <div className="mt-8 flex items-center justify-between">
            <button
              type="button"
              onClick={goBack}
              disabled={step === 0}
              className="inline-flex h-11 items-center justify-center rounded-full border border-line px-5 text-sm font-medium text-foreground disabled:opacity-40"
            >
              Anterior
            </button>
            {step < STEP_LABELS.length - 1 ? (
              <button
                type="button"
                onClick={goNext}
                className="inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-sm font-medium text-accent-fg hover:brightness-110"
              >
                Seguinte
              </button>
            ) : (
              <button
                type="button"
                onClick={() => void handlePublish()}
                disabled={publishing}
                className="inline-flex h-11 items-center justify-center gap-2 rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-sm font-medium text-accent-fg hover:brightness-110 disabled:opacity-60"
              >
                {publishing ? <Loader2 aria-hidden="true" className="size-4 animate-spin" /> : null}
                {publishing ? 'A publicar…' : 'Publicar pedido'}
              </button>
            )}
          </div>
        </div>

        <ReviewPanel draft={draft} photoCount={photos.length} />
      </div>
    </div>
  );
}

function SuccessScreen({ request, onViewRequest }: { request: ServiceRequest; onViewRequest: () => void }) {
  const reference = `SM-2026-${request.id.replace(/[^0-9]/g, '').slice(-4).padStart(4, '0')}`;

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-lg flex-col items-center justify-center px-5 text-center">
      <Seo title="Pedido publicado" description="O seu pedido foi publicado com sucesso." />
      <Reveal>
        <div className="mx-auto flex size-16 items-center justify-center rounded-full bg-success/12">
          <CheckCircle2 aria-hidden="true" className="size-8 text-success" strokeWidth={1.5} />
        </div>
        <h1 className="mt-6 text-h2 font-display font-bold text-foreground">Pedido publicado!</h1>
        <p className="mt-3 font-mono text-eyebrow tracking-[0.08em] text-muted">REFERÊNCIA {reference}</p>
        <p className="mt-4 text-body text-muted">
          Estamos a notificar prestadores elegíveis da sua zona e categoria. Costuma chegar a primeira proposta em
          menos de 4 horas.
        </p>
        <div className="mt-6 flex items-center justify-center gap-2" aria-hidden="true">
          <span className="size-2 animate-pulse rounded-full bg-signal-500 motion-reduce:animate-none" />
          <span className="size-2 animate-pulse rounded-full bg-signal-500 [animation-delay:150ms] motion-reduce:animate-none" />
          <span className="size-2 animate-pulse rounded-full bg-signal-500 [animation-delay:300ms] motion-reduce:animate-none" />
          <span className="ml-2 text-caption text-muted">A procurar prestadores…</span>
        </div>
        <button
          type="button"
          onClick={onViewRequest}
          className="mt-8 inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-sm font-medium text-accent-fg"
        >
          Ver o meu pedido
        </button>
      </Reveal>
    </div>
  );
}

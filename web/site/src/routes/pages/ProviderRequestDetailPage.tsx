import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2 } from 'lucide-react';
import { useParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { ProposalForm } from '../../features/proposals/ProposalForm';
import { SubscriptionUpsell } from '../../features/subscriptions/SubscriptionUpsell';
import { useAuth } from '../../features/auth/useAuth';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

export function ProviderRequestDetailPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const { user } = useAuth();
  const [blockedBySubscription, setBlockedBySubscription] = useState(false);
  const [sentProposal, setSentProposal] = useState(false);

  const requestQuery = useQuery({
    queryKey: ['requests', 'detail', requestId],
    queryFn: () => services.requests.get(requestId as string),
    enabled: Boolean(requestId),
  });

  // Avaliação real do próprio prestador para a pré-visualização do
  // orçamento — nunca um valor fixo inventado no cliente.
  const profileQuery = useQuery({
    queryKey: ['providers', 'me'],
    queryFn: () => services.providers.getMine(),
  });

  if (!requestId) return null;

  if (requestQuery.isLoading) {
    return (
      <div className="max-w-3xl">
        <SkeletonText lines={6} />
      </div>
    );
  }

  if (requestQuery.error || !requestQuery.data) {
    return <ErrorState problem={toProblem(requestQuery.error)} onRetry={() => void requestQuery.refetch()} />;
  }

  const request = requestQuery.data;

  return (
    <div className="max-w-3xl">
      <Seo title={request.title} description={request.description ?? request.title} />
      <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">{request.category?.name?.toUpperCase()}</p>
      <h1 className="mt-1 text-h2 font-display font-bold text-foreground">{request.title}</h1>
      {request.description ? <p className="mt-3 text-body text-muted">{request.description}</p> : null}
      <p className="mt-2 text-caption text-muted">
        {request.address?.city}
        {request.urgency ? ` · Urgência: ${request.urgency}` : ''}
      </p>

      <section className="mt-10" aria-labelledby="proposal-form-heading">
        <h2 id="proposal-form-heading" className="text-h2 font-display font-bold text-foreground">
          Enviar orçamento
        </h2>

        <div className="mt-5">
          {sentProposal ? (
            <div className="surface-card flex items-center gap-3 p-5">
              <CheckCircle2 aria-hidden="true" className="size-6 text-success" strokeWidth={1.5} />
              <p className="text-body text-foreground">Orçamento enviado com sucesso.</p>
            </div>
          ) : (
            <SubscriptionUpsell blocked={blockedBySubscription}>
              <ProposalForm
                requestId={requestId}
                providerName={user?.displayName ?? 'O seu perfil'}
                providerRating={profileQuery.data?.ratingAvg ?? 0}
                providerRatingCount={profileQuery.data?.ratingCount ?? 0}
                onSubscriptionRequired={() => setBlockedBySubscription(true)}
                onSuccess={() => setSentProposal(true)}
              />
            </SubscriptionUpsell>
          )}
        </div>
      </section>
    </div>
  );
}

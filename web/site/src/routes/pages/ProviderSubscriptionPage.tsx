import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CreditCard, Landmark } from 'lucide-react';
import { Seo } from '../../components/Seo';
import { Badge } from '../../components/ui/Badge';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonText } from '../../components/ui/Skeleton';
import { PricingCard } from '../../features/subscriptions/PricingCard';
import { formatDate } from '../../lib/dates';
import { formatMoney } from '../../lib/money';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { SubscriptionCheckout, SubscriptionPlan } from '../../services/types';

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Pendente',
  ACTIVE: 'Ativa',
  PAST_DUE: 'Pagamento em atraso',
  EXPIRED: 'Expirada',
  CANCELLED: 'Cancelada',
};

type Gateway = 'stripe' | 'eupago';

export function ProviderSubscriptionPage() {
  const [selectedPlan, setSelectedPlan] = useState<SubscriptionPlan | undefined>(undefined);
  const [gateway, setGateway] = useState<Gateway>('stripe');
  const [checkout, setCheckout] = useState<SubscriptionCheckout | undefined>(undefined);

  const currentQuery = useQuery({
    queryKey: ['subscription-current'],
    queryFn: () => services.subscriptions.current(),
  });

  const plansQuery = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: () => services.subscriptions.listPlans(),
  });

  const checkoutMutation = useMutation({
    mutationFn: (plan: SubscriptionPlan) =>
      services.subscriptions.create({ planId: plan.id, gateway, returnUrl: `${window.location.origin}/pro/subscricao` }),
    onSuccess: (result) => setCheckout(result),
  });

  function startCheckout(plan: SubscriptionPlan) {
    setSelectedPlan(plan);
    setCheckout(undefined);
    checkoutMutation.mutate(plan);
  }

  return (
    <div className="max-w-3xl">
      <Seo title="A minha subscrição" description="Estado da subscrição e planos disponíveis." />
      <h1 className="text-h2 font-display font-bold text-foreground">A minha subscrição</h1>

      {currentQuery.isLoading ? (
        <div className="mt-6">
          <SkeletonText lines={3} />
        </div>
      ) : currentQuery.data ? (
        <div className="surface-card mt-6 flex flex-wrap items-center justify-between gap-4 p-5">
          <div>
            <p className="text-caption text-muted">Estado atual</p>
            <Badge tone={currentQuery.data.status === 'ACTIVE' ? 'success' : 'accent'} className="mt-1.5">
              {STATUS_LABEL[currentQuery.data.status]}
            </Badge>
          </div>
          {currentQuery.data.currentPeriodStart && currentQuery.data.currentPeriodEnd ? (
            <p className="text-caption text-muted">
              Período atual: {formatDate(currentQuery.data.currentPeriodStart)} — {formatDate(currentQuery.data.currentPeriodEnd)}
            </p>
          ) : null}
        </div>
      ) : (
        <p className="mt-4 text-body text-muted">Ainda sem subscrição associada a este perfil.</p>
      )}

      <section className="mt-10">
        <h2 className="text-h2 font-display font-bold text-foreground">Mudar de plano</h2>

        <div className="mt-4 inline-flex rounded-full border border-line bg-surface p-1">
          {(['stripe', 'eupago'] as const).map((option) => (
            <button
              key={option}
              type="button"
              onClick={() => setGateway(option)}
              aria-pressed={gateway === option}
              className={`inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium ${
                gateway === option ? 'bg-orange-500 text-accent-fg' : 'text-muted'
              }`}
            >
              {option === 'stripe' ? <CreditCard aria-hidden="true" className="size-4" strokeWidth={1.5} /> : <Landmark aria-hidden="true" className="size-4" strokeWidth={1.5} />}
              {option === 'stripe' ? 'Cartão (Stripe)' : 'Multibanco'}
            </button>
          ))}
        </div>

        {plansQuery.error ? (
          <div className="mt-6">
            <ErrorState problem={toProblem(plansQuery.error)} onRetry={() => void plansQuery.refetch()} />
          </div>
        ) : plansQuery.isLoading ? (
          <div className="mt-6">
            <SkeletonText lines={4} />
          </div>
        ) : (
          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            {plansQuery.data?.map((plan) => (
              <div key={plan.id} className="flex flex-col gap-3">
                <PricingCard plan={plan} highlighted={plan.code === 'professional'} />
                <button
                  type="button"
                  onClick={() => startCheckout(plan)}
                  disabled={checkoutMutation.isPending && selectedPlan?.id === plan.id}
                  className="inline-flex h-10 items-center justify-center rounded-full border border-line text-sm font-medium text-foreground hover:border-orange-500/40 disabled:opacity-60"
                >
                  {checkoutMutation.isPending && selectedPlan?.id === plan.id ? 'A preparar…' : 'Escolher este plano'}
                </button>
              </div>
            ))}
          </div>
        )}

        {checkout ? <CheckoutResult checkout={checkout} plan={selectedPlan} /> : null}
        {checkoutMutation.isError ? (
          <div className="mt-4">
            <ErrorState problem={toProblem(checkoutMutation.error)} />
          </div>
        ) : null}
      </section>
    </div>
  );
}

function CheckoutResult({ checkout, plan }: { checkout: SubscriptionCheckout; plan: SubscriptionPlan | undefined }) {
  if (checkout.checkoutUrl) {
    return (
      <div className="surface-card mt-6 p-5">
        <p className="text-body text-foreground">
          Checkout iniciado para o plano <strong>{plan?.name}</strong>. Em produção seria redirecionado para:
        </p>
        <p className="mt-2 break-all font-mono text-caption text-orange-600">{checkout.checkoutUrl}</p>
      </div>
    );
  }

  if (checkout.paymentReference) {
    return (
      <div className="surface-card mt-6 p-5">
        <p className="eyebrow text-muted">REFERÊNCIA MULTIBANCO</p>
        <p className="mt-2 font-mono text-lg tracking-[0.04em] text-foreground">
          ENT {checkout.paymentReference.entity} · REF {checkout.paymentReference.reference}
        </p>
        {checkout.paymentReference.amount ? (
          <p className="mt-1 font-mono text-eyebrow tracking-[0.08em] text-muted">
            VALOR {formatMoney(checkout.paymentReference.amount).toUpperCase()}
          </p>
        ) : null}
      </div>
    );
  }

  return null;
}

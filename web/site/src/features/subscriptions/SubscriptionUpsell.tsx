import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Lock } from 'lucide-react';
import { Link } from 'react-router-dom';
import { PricingCard } from './PricingCard';
import { SkeletonCard } from '../../components/ui/Skeleton';
import { services } from '../../services';

/**
 * Painel de upsell (§7 — "a superfície de conversão mais importante do
 * produto"): conteúdo real desfocado por trás, gradiente, três planos, CTA.
 * O `blocked` vem sempre de uma resposta 403
 * `subscription-required` do servidor — nunca de uma suposição do cliente
 * (CLAUDE.md §4).
 */
export function SubscriptionUpsell({ blocked, children }: { blocked: boolean; children: ReactNode }) {
  const { data, isLoading } = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: () => services.subscriptions.listPlans(),
    enabled: blocked,
  });

  if (!blocked) return <>{children}</>;

  return (
    <div className="relative">
      <div aria-hidden="true" className="pointer-events-none select-none blur-sm">
        {children}
      </div>
      <div className="absolute inset-0 flex items-start justify-center bg-gradient-to-b from-background/40 via-background/85 to-background pt-10">
        <div className="mx-auto w-full max-w-3xl px-5 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-orange-500/12">
            <Lock aria-hidden="true" className="size-6 text-orange-600" strokeWidth={1.5} />
          </div>
          <h2 className="mt-4 text-h2 font-display font-bold text-foreground">Ative a sua subscrição para ver isto</h2>
          <p className="mt-2 text-body text-muted">
            Sem subscrição ativa não é elegível para matching nem pode enviar orçamentos. Escolha um plano para
            continuar.
          </p>
          {isLoading ? (
            <div className="mt-6 grid gap-4 sm:grid-cols-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <SkeletonCard key={index} />
              ))}
            </div>
          ) : (
            <div className="mt-6 grid gap-4 text-left sm:grid-cols-3">
              {data?.map((plan) => <PricingCard key={plan.id} plan={plan} highlighted={plan.code === 'professional'} />)}
            </div>
          )}
          <Link to="/pro/subscricao" className="mt-6 inline-block text-sm font-medium text-orange-600 hover:underline">
            Ver detalhes da subscrição →
          </Link>
        </div>
      </div>
    </div>
  );
}

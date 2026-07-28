import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { services } from '../../services';
import { PricingCard } from '../../features/subscriptions/PricingCard';
import { SkeletonCard } from '../ui/Skeleton';
import { ErrorState } from '../ui/ErrorState';
import { toProblem } from '../../lib/problem';
import { Reveal } from '../motion/Reveal';

export function PricingSummary() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: () => services.subscriptions.listPlans(),
  });

  return (
    <section className="border-y border-line bg-surface-2 py-[clamp(5rem,10vw,9rem)]">
      <div className="mx-auto max-w-[1280px] px-5 sm:px-8 lg:px-10">
        <Reveal>
          <p className="eyebrow text-signal-500">PARA PRESTADORES</p>
          <h2 className="mt-3 text-h1 font-display font-bold text-foreground">Planos simples, sem letra miúda</h2>
          <p className="mt-3 max-w-xl text-body text-muted">
            O cliente nunca paga. A subscrição é o que mantém o seu perfil visível, elegível para matching e a enviar
            orçamentos.
          </p>
        </Reveal>

        {error ? (
          <div className="mt-10">
            <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
          </div>
        ) : isLoading ? (
          <div className="mt-10 grid gap-6 sm:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <SkeletonCard key={index} />
            ))}
          </div>
        ) : (
          <div className="mt-10 grid gap-6 sm:grid-cols-3">
            {data?.map((plan) => <PricingCard key={plan.id} plan={plan} highlighted={plan.code === 'professional'} />)}
          </div>
        )}

        <p className="mt-8 text-center">
          <Link to="/precos" className="text-sm font-medium text-orange-600 hover:underline">
            Ver comparação completa e FAQ →
          </Link>
        </p>
      </div>
    </section>
  );
}

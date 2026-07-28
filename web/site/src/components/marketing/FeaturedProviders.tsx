import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { services } from '../../services';
import { ProviderCard } from '../../features/providers/ProviderCard';
import { SkeletonCard } from '../ui/Skeleton';
import { ErrorState } from '../ui/ErrorState';
import { toProblem } from '../../lib/problem';
import { Reveal } from '../motion/Reveal';
import { Stagger } from '../motion/Stagger';

export function FeaturedProviders() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['providers', 'featured'],
    queryFn: () => services.providers.featured(6),
  });

  return (
    <section className="mx-auto max-w-[1280px] px-5 py-[clamp(5rem,10vw,9rem)] sm:px-8 lg:px-10">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <Reveal>
          <p className="eyebrow text-signal-500">PRESTADORES</p>
          <h2 className="mt-3 text-h1 font-display font-bold text-foreground">Prestadores em destaque</h2>
        </Reveal>
        <Link to="/prestadores" className="text-sm font-medium text-orange-600 hover:underline">
          Ver todos →
        </Link>
      </div>

      {error ? (
        <div className="mt-10">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-10 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <SkeletonCard key={index} />
          ))}
        </div>
      ) : (
        <Stagger className="mt-10 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {data?.map((provider) => (
            <Stagger.Item key={provider.id}>
              <ProviderCard provider={provider} />
            </Stagger.Item>
          ))}
        </Stagger>
      )}
    </section>
  );
}

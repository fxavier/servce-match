import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  Droplets,
  Fan,
  Hammer,
  HardHat,
  PaintRoller,
  Sparkles,
  Trees,
  Truck,
  Wrench,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { Seo } from '../../components/Seo';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonCard } from '../../components/ui/Skeleton';
import { Reveal } from '../../components/motion/Reveal';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';
import type { Category } from '../../services/types';

const ICONS: Record<string, LucideIcon> = {
  canalizacao: Droplets,
  carpintaria: Hammer,
  climatizacao: Fan,
  eletricidade: Zap,
  jardinagem: Trees,
  limpezas: Sparkles,
  mudancas: Truck,
  'obras-remodelacoes': HardHat,
  pinturas: PaintRoller,
  serralharia: Wrench,
};

/**
 * Sem contagem de profissionais por categoria: o contrato não expõe esse
 * agregado (mesma decisão da grelha da landing — ver `CategoryGrid.tsx`).
 */
export function CategoriesPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: () => services.categories.list(),
  });

  const topLevel = data?.filter((category) => category.parentId === null) ?? [];
  const bySlug = (parent: Category) => data?.filter((category) => category.parentId === parent.id) ?? [];

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:px-10">
      <Seo
        title="Categorias de serviços"
        description="Todas as categorias e subcategorias de serviços do ServiMatch."
        canonicalPath="/categorias"
      />
      <Reveal>
        <p className="eyebrow text-signal-500">CATÁLOGO</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Todas as categorias</h1>
        <p className="mt-3 max-w-xl text-body text-muted">
          Escolha uma categoria para ver profissionais em destaque e preços de referência na sua zona.
        </p>
      </Reveal>

      {error ? (
        <div className="mt-10">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => (
            <SkeletonCard key={index} />
          ))}
        </div>
      ) : topLevel.length === 0 ? (
        <p className="mt-10 text-body text-muted">Sem categorias disponíveis de momento.</p>
      ) : (
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {topLevel.map((category) => {
            const subcategories = bySlug(category);
            const Icon = ICONS[category.slug] ?? Wrench;
            return (
              <div key={category.id} className="surface-card p-5">
                <div className="flex items-center gap-3">
                  <span className="inline-flex size-11 items-center justify-center rounded-lg bg-gradient-to-br from-navy-600 to-navy-700 text-white">
                    <Icon aria-hidden="true" className="size-5" strokeWidth={1.5} />
                  </span>
                  <Link to={`/servicos/${category.slug}`} className="text-card-title font-display font-semibold text-foreground hover:text-orange-600">
                    {category.name}
                  </Link>
                </div>
                {subcategories.length > 0 ? (
                  <ul className="mt-4 flex flex-col gap-1.5 border-t border-line pt-4">
                    {subcategories.map((sub) => (
                      <li key={sub.id}>
                        <Link to={`/servicos/${sub.slug}`} className="text-body text-muted hover:text-orange-600">
                          {sub.name}
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

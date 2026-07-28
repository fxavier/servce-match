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
import { Link } from 'react-router-dom';
import { Reveal } from '../motion/Reveal';
import { Stagger } from '../motion/Stagger';
import { SkeletonCard } from '../ui/Skeleton';
import { ErrorState } from '../ui/ErrorState';
import { useCategories } from '../../features/categories/useCategories';
import { toProblem } from '../../lib/problem';

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
 * Grelha de categorias (§7, ponto 3). Nota: o catálogo real semeado no
 * backend tem 10 categorias de topo, não 12 — usamos as 10 reais em vez de
 * inventar duas extra (prioridade #5 do briefing prevalece sobre o número
 * literal do prompt). A contagem de profissionais por categoria não é
 * mostrada aqui: o contrato não expõe um agregado desse tipo (nenhum
 * endpoint devolve "quantos prestadores têm esta categoria"), e mostrar um
 * número teria de vir de uma fixture importada fora de `services/`
 * (checklist §11) — preferimos não mostrar a inventar.
 */
export function CategoryGrid() {
  const { data: categories, isLoading, error, refetch } = useCategories();
  const topLevel = categories?.filter((category) => category.parentId === null) ?? [];

  return (
    <section className="mx-auto max-w-[1280px] px-5 py-[clamp(5rem,10vw,9rem)] sm:px-8 lg:px-10">
      <Reveal>
        <p className="eyebrow text-signal-500">O QUE PRECISA?</p>
        <h2 className="mt-3 text-h1 font-display font-bold text-foreground">Categorias mais pedidas</h2>
      </Reveal>

      {error ? (
        <div className="mt-10">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-10 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 10 }).map((_, index) => (
            <SkeletonCard key={index} />
          ))}
        </div>
      ) : (
        <Stagger className="mt-10 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {topLevel.map((category) => {
            const Icon = ICONS[category.slug] ?? Wrench;
            return (
              <Stagger.Item key={category.id}>
                <Link
                  to={`/servicos/${category.slug}`}
                  className="surface-card surface-card--interactive group flex h-full flex-col justify-between gap-6 p-5"
                >
                  <span className="inline-flex size-12 items-center justify-center rounded-lg bg-gradient-to-br from-navy-600 to-navy-700 text-white">
                    <Icon aria-hidden="true" className="size-6" strokeWidth={1.5} />
                  </span>
                  <p className="text-card-title font-display font-semibold text-foreground">{category.name}</p>
                </Link>
              </Stagger.Item>
            );
          })}
        </Stagger>
      )}
    </section>
  );
}

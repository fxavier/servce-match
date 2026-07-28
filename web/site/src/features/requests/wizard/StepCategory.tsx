import { useState } from 'react';
import { Search } from 'lucide-react';
import { Input } from '../../../components/ui/Input';
import { ErrorState } from '../../../components/ui/ErrorState';
import { SkeletonText } from '../../../components/ui/Skeleton';
import { cn } from '../../../lib/cn';
import { toProblem } from '../../../lib/problem';
import { useCategories } from '../../categories/useCategories';

export interface StepCategoryProps {
  categoryId: string;
  onChange: (categoryId: string) => void;
  error?: string;
}

export function StepCategory({ categoryId, onChange, error }: StepCategoryProps) {
  const [query, setQuery] = useState('');
  const { data: categories, isLoading, error: loadError, refetch } = useCategories();
  const filtered = (categories ?? []).filter((category) => category.name.toLowerCase().includes(query.toLowerCase()));

  return (
    <div>
      <h2 className="text-h2 font-display font-bold text-foreground">Que serviço precisa?</h2>
      <p className="mt-2 text-body text-muted">Escolha a categoria que melhor descreve o seu pedido.</p>

      <div className="relative mt-5">
        <Search aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted" strokeWidth={1.5} />
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Pesquisar categoria…"
          className="pl-10"
          aria-label="Pesquisar categoria"
        />
      </div>

      {error ? (
        <p role="alert" className="mt-3 text-caption font-medium text-orange-600">
          {error}
        </p>
      ) : null}

      {loadError ? (
        <div className="mt-5">
          <ErrorState problem={toProblem(loadError)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-5">
          <SkeletonText lines={4} />
        </div>
      ) : (
        <div role="radiogroup" aria-label="Categoria do pedido" className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {filtered.map((category) => {
            const selected = category.id === categoryId;
            return (
              <button
                key={category.id}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => onChange(category.id)}
                className={cn(
                  'rounded-lg border p-4 text-left text-sm font-medium transition-colors',
                  selected ? 'border-orange-500 bg-orange-500/8 text-foreground' : 'border-line text-muted hover:border-orange-500/30 hover:text-foreground',
                )}
              >
                {category.name}
              </button>
            );
          })}
          {filtered.length === 0 ? <p className="col-span-full text-body text-muted">Sem categorias com esse nome.</p> : null}
        </div>
      )}
    </div>
  );
}

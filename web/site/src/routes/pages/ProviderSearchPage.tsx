import { useMemo } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { Chip } from '../../components/ui/Chip';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Select } from '../../components/ui/Select';
import { SkeletonCard } from '../../components/ui/Skeleton';
import { Reveal } from '../../components/motion/Reveal';
import { ProviderCard } from '../../features/providers/ProviderCard';
import { getNextCursorParam } from '../../lib/cursor';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';
import { useCategories } from '../../features/categories/useCategories';
import { REGIONS } from '../../constants/regions';

type SortOption = 'relevance' | 'rating';

export function ProviderSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { data: categories } = useCategories();
  const topLevelCategories = categories?.filter((category) => category.parentId === null) ?? [];

  const categoryId = searchParams.get('categoria') ?? '';
  const regionCode = searchParams.get('zona') ?? '';
  const minRating = searchParams.get('avaliacao') ?? '';
  const verifiedOnly = searchParams.get('verificado') === '1';
  const premiumOnly = searchParams.get('premium') === '1';
  const sort = (searchParams.get('ordenar') as SortOption | null) ?? 'relevance';

  function updateParam(key: string, value: string | undefined) {
    const next = new URLSearchParams(searchParams);
    if (value) next.set(key, value);
    else next.delete(key);
    setSearchParams(next);
  }

  // `GET /v1/search/providers` só aceita categoryId/lat/lon/regionCode/q no
  // contrato — sem `minRating`, `verifiedOnly`, `premiumOnly` nem `sort`
  // (ver docs/api/openapi.yaml). Em vez de enviar parâmetros que o servidor
  // ignoraria em silêncio, filtra-se e ordena-se do lado do cliente sobre
  // os resultados reais já carregados — nunca se inventa um campo novo.
  const queryKey = ['providers', 'search', { categoryId, regionCode }];

  const { data, isLoading, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) =>
      services.providers.search({
        categoryId: categoryId || undefined,
        regionCode: regionCode || undefined,
        cursor: pageParam,
        limit: 9,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: getNextCursorParam,
  });

  const allProviders = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);

  const providers = useMemo(() => {
    const minRatingValue = minRating ? Number(minRating) : undefined;
    const filtered = allProviders.filter((provider) => {
      if (minRatingValue !== undefined && provider.ratingAvg < minRatingValue) return false;
      if (verifiedOnly && !provider.verified) return false;
      if (premiumOnly && !provider.premiumBadge) return false;
      return true;
    });
    if (sort === 'rating') {
      return [...filtered].sort((a, b) => b.ratingAvg - a.ratingAvg);
    }
    return filtered;
  }, [allProviders, minRating, verifiedOnly, premiumOnly, sort]);

  const activeFilterCount = [categoryId, regionCode, minRating, verifiedOnly, premiumOnly].filter(Boolean).length;

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:px-10">
      <Seo
        title="Encontrar prestadores"
        description="Pesquise prestadores de serviços verificados por categoria, concelho e avaliação."
        canonicalPath="/prestadores"
      />
      <Reveal>
        <p className="eyebrow text-signal-500">PESQUISA</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Encontrar prestadores</h1>
      </Reveal>

      <div className="mt-8 flex flex-wrap items-center gap-3">
        <Select
          aria-label="Categoria"
          value={categoryId}
          onChange={(event) => updateParam('categoria', event.target.value || undefined)}
          className="max-w-[200px]"
        >
          <option value="">Todas as categorias</option>
          {topLevelCategories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
        <Select
          aria-label="Concelho"
          value={regionCode}
          onChange={(event) => updateParam('zona', event.target.value || undefined)}
          className="max-w-[180px]"
        >
          <option value="">Todos os concelhos</option>
          {REGIONS.map((region) => (
            <option key={region.code} value={region.code}>
              {region.label}
            </option>
          ))}
        </Select>
        <Select
          aria-label="Avaliação mínima"
          value={minRating}
          onChange={(event) => updateParam('avaliacao', event.target.value || undefined)}
          className="max-w-[170px]"
        >
          <option value="">Qualquer avaliação</option>
          <option value="4.5">4,5 ★ ou mais</option>
          <option value="4">4,0 ★ ou mais</option>
        </Select>
        <Chip selected={verifiedOnly} onClick={() => updateParam('verificado', verifiedOnly ? undefined : '1')}>
          Verificado
        </Chip>
        <Chip selected={premiumOnly} onClick={() => updateParam('premium', premiumOnly ? undefined : '1')}>
          Premium
        </Chip>

        <div className="ml-auto flex items-center gap-3">
          <Select
            aria-label="Ordenar por"
            value={sort}
            onChange={(event) => updateParam('ordenar', event.target.value)}
            className="max-w-[170px]"
          >
            <option value="relevance">Relevância</option>
            <option value="rating">Melhor avaliação</option>
          </Select>
        </div>
      </div>

      <div className="mt-8">
        {error ? (
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        ) : isLoading ? (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, index) => (
              <SkeletonCard key={index} />
            ))}
          </div>
        ) : providers.length === 0 ? (
          <EmptyState
            title="Sem prestadores para estes filtros"
            description={activeFilterCount > 0 ? 'Tente alargar os filtros escolhidos.' : 'Ainda não há prestadores disponíveis.'}
          />
        ) : (
          <>
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {providers.map((provider) => (
                <ProviderCard key={provider.id} provider={provider} />
              ))}
            </div>
            {hasNextPage ? (
              <div className="mt-8 flex justify-center">
                <button
                  type="button"
                  onClick={() => void fetchNextPage()}
                  disabled={isFetchingNextPage}
                  className="inline-flex h-11 items-center justify-center rounded-full border border-line px-6 text-sm font-medium text-foreground hover:border-orange-500/40 disabled:opacity-60"
                >
                  {isFetchingNextPage ? 'A carregar…' : 'Carregar mais prestadores'}
                </button>
              </div>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}

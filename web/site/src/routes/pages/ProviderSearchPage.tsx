import { useMemo, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { List, Map as MapIcon } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';
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
import { cn } from '../../lib/cn';
import { services } from '../../services';
import { useCategories } from '../../features/categories/useCategories';
import { REGIONS } from '../../constants/regions';
import 'leaflet/dist/leaflet.css';

type SortOption = 'relevance' | 'rating' | 'distance';
type ViewMode = 'list' | 'map';

export function ProviderSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [view, setView] = useState<ViewMode>('list');
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

  const queryKey = ['providers', 'search', { categoryId, regionCode, minRating, verifiedOnly, premiumOnly, sort }];

  const { data, isLoading, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) =>
      services.providers.search({
        categoryId: categoryId || undefined,
        regionCode: regionCode || undefined,
        minRating: minRating ? Number(minRating) : undefined,
        verifiedOnly: verifiedOnly || undefined,
        premiumOnly: premiumOnly || undefined,
        sort,
        cursor: pageParam,
        limit: 9,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: getNextCursorParam,
  });

  const providers = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);

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
            <option value="distance">Mais próximo</option>
          </Select>
          <div role="group" aria-label="Modo de visualização" className="inline-flex rounded-full border border-line p-1">
            <button
              type="button"
              aria-pressed={view === 'list'}
              onClick={() => setView('list')}
              className={cn('inline-flex size-9 items-center justify-center rounded-full', view === 'list' && 'bg-orange-500 text-accent-fg')}
            >
              <List aria-hidden="true" className="size-4" strokeWidth={1.5} />
              <span className="sr-only">Ver em lista</span>
            </button>
            <button
              type="button"
              aria-pressed={view === 'map'}
              onClick={() => setView('map')}
              className={cn('inline-flex size-9 items-center justify-center rounded-full', view === 'map' && 'bg-orange-500 text-accent-fg')}
            >
              <MapIcon aria-hidden="true" className="size-4" strokeWidth={1.5} />
              <span className="sr-only">Ver no mapa</span>
            </button>
          </div>
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
        ) : view === 'map' ? (
          <div className="h-[520px] overflow-hidden rounded-lg border border-line">
            <MapContainer center={[39.5, -8]} zoom={7} className="h-full w-full" scrollWheelZoom={false}>
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />
              {providers.map((provider) => (
                <Marker key={provider.id} position={[provider.location.lat, provider.location.lon]}>
                  <Popup>
                    <strong>{provider.displayName}</strong>
                    <br />
                    {provider.headline}
                  </Popup>
                </Marker>
              ))}
            </MapContainer>
          </div>
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

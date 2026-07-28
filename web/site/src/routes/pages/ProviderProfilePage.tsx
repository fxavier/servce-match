import { useQuery } from '@tanstack/react-query';
import { BadgeCheck, MapPin, Sparkles } from 'lucide-react';
import { useParams } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { MapContainer, Marker, TileLayer } from 'react-leaflet';
import { Seo } from '../../components/Seo';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { RatingStars } from '../../components/ui/RatingStars';
import { SkeletonText } from '../../components/ui/Skeleton';
import { Reveal } from '../../components/motion/Reveal';
import { formatDate } from '../../lib/dates';
import { toProblem } from '../../lib/problem';
import { aggregateRatingJsonLd } from '../../lib/seo';
import { services } from '../../services';
import 'leaflet/dist/leaflet.css';

export function ProviderProfilePage() {
  const { id } = useParams<{ id: string }>();

  const providerQuery = useQuery({
    queryKey: ['providers', 'detail', id],
    queryFn: () => services.providers.get(id as string),
    enabled: Boolean(id),
  });

  const reviewsQuery = useQuery({
    queryKey: ['reviews', 'by-provider', id],
    queryFn: () => services.reviews.listForProvider(id as string),
    enabled: Boolean(id),
  });

  if (providerQuery.isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-24 sm:px-8">
        <SkeletonText lines={6} />
      </div>
    );
  }

  if (providerQuery.error || !providerQuery.data) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-24 sm:px-8">
        <ErrorState problem={toProblem(providerQuery.error)} onRetry={() => void providerQuery.refetch()} />
      </div>
    );
  }

  const provider = providerQuery.data;
  const { 5: five, 4: four, 3: three, 2: two, 1: one } = provider.ratingDistribution;
  const totalReviews = five + four + three + two + one || provider.ratingCount;

  return (
    <div>
      <Seo
        title={provider.displayName}
        description={provider.headline ?? provider.displayName}
        canonicalPath={`/prestadores/${provider.id}`}
        jsonLd={aggregateRatingJsonLd({ itemName: provider.displayName, ratingValue: provider.ratingAvg, reviewCount: provider.ratingCount })}
      />

      <div className="relative h-40 overflow-hidden bg-gradient-to-br from-navy-700 to-navy-600 sm:h-56" aria-hidden="true" />

      <div className="mx-auto max-w-5xl px-5 sm:px-8">
        <div className="-mt-14 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-end gap-4">
            <Avatar name={provider.displayName} imageUrl={provider.avatarUrl} size="lg" className="ring-4 ring-background" />
            <div className="pb-1">
              <div className="flex items-center gap-2">
                <h1 className="text-h2 font-display font-bold text-foreground">{provider.displayName}</h1>
                {provider.verified ? <Badge tone="signal"><BadgeCheck className="size-3.5" strokeWidth={1.5} />Verificado</Badge> : null}
                {provider.premiumBadge ? <Badge tone="accent"><Sparkles className="size-3.5" strokeWidth={1.5} />Premium</Badge> : null}
              </div>
              <p className="mt-1 text-body text-muted">{provider.headline}</p>
            </div>
          </div>
          <Link
            to={`/pedidos/novo`}
            className="inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110"
          >
            Pedir orçamento
          </Link>
        </div>

        <div className="mt-10 grid gap-10 lg:grid-cols-[2fr_1fr]">
          <div className="flex flex-col gap-10">
            <Reveal>
              <section aria-labelledby="bio-heading">
                <h2 id="bio-heading" className="text-h2 font-display font-bold text-foreground">
                  Sobre
                </h2>
                <p className="mt-3 text-body text-muted">{provider.bio || 'Este prestador ainda não adicionou uma descrição.'}</p>
                <div className="mt-4 flex flex-wrap gap-2">
                  {provider.categoryNames.map((name) => (
                    <Badge key={name} tone="neutral">
                      {name}
                    </Badge>
                  ))}
                </div>
              </section>
            </Reveal>

            {provider.zones.length > 0 ? (
              <Reveal>
                <section aria-labelledby="zones-heading">
                  <h2 id="zones-heading" className="text-h2 font-display font-bold text-foreground">
                    Zonas de atuação
                  </h2>
                  <ul className="mt-3 flex flex-wrap gap-2">
                    {provider.zones.map((zone) => (
                      <li key={zone.regionCode} className="flex items-center gap-1 text-caption text-muted">
                        <MapPin aria-hidden="true" className="size-3.5" strokeWidth={1.5} />
                        {zone.label}
                      </li>
                    ))}
                  </ul>
                  <div className="mt-4 h-64 overflow-hidden rounded-lg border border-line">
                    <MapContainer center={[provider.location.lat, provider.location.lon]} zoom={9} className="h-full w-full" scrollWheelZoom={false}>
                      <TileLayer
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                      />
                      <Marker position={[provider.location.lat, provider.location.lon]} />
                    </MapContainer>
                  </div>
                </section>
              </Reveal>
            ) : null}

            <Reveal>
              <section aria-labelledby="reviews-heading">
                <h2 id="reviews-heading" className="text-h2 font-display font-bold text-foreground">
                  Avaliações ({provider.ratingCount})
                </h2>
                {reviewsQuery.isLoading ? (
                  <div className="mt-4">
                    <SkeletonText lines={4} />
                  </div>
                ) : reviewsQuery.data && reviewsQuery.data.length > 0 ? (
                  <ul className="mt-4 flex flex-col divide-y divide-line border-y border-line">
                    {reviewsQuery.data.map((review) => (
                      <li key={review.id} className="flex flex-col gap-2 py-5">
                        <div className="flex items-center gap-3">
                          <Avatar name={review.authorName} size="sm" />
                          <div>
                            <p className="text-sm font-medium text-foreground">{review.authorName}</p>
                            <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">
                              {formatDate(review.createdAt).toUpperCase()}
                            </p>
                          </div>
                          <RatingStars value={review.rating} className="ml-auto" />
                        </div>
                        <p className="text-body text-muted">{review.comment}</p>
                        {review.providerResponse ? (
                          <div className="ml-4 rounded-md border-l-2 border-orange-500 bg-surface-2 p-3 text-caption text-muted">
                            <span className="font-medium text-foreground">Resposta do prestador: </span>
                            {review.providerResponse}
                          </div>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="mt-4">
                    <EmptyState title="Ainda sem avaliações" description="Este prestador ainda não tem avaliações verificadas." />
                  </div>
                )}
              </section>
            </Reveal>
          </div>

          <Reveal>
            <aside className="surface-card p-5">
              <RatingStars value={provider.ratingAvg} count={provider.ratingCount} size={18} />
              <div className="mt-4 flex flex-col gap-1.5">
                {([5, 4, 3, 2, 1] as const).map((star) => {
                  const count = provider.ratingDistribution[star];
                  const percentage = totalReviews > 0 ? Math.round((count / totalReviews) * 100) : 0;
                  return (
                    <div key={star} className="flex items-center gap-2 text-caption text-muted">
                      <span className="w-3">{star}</span>
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-2">
                        <div className="h-full bg-orange-500" style={{ width: `${percentage}%` }} />
                      </div>
                      <span className="w-8 text-right tabular-nums">{count}</span>
                    </div>
                  );
                })}
              </div>
              <p className="mt-4 font-mono text-eyebrow tracking-[0.08em] text-muted">
                NO SERVIMATCH DESDE {formatDate(provider.memberSince).toUpperCase()}
              </p>
            </aside>
          </Reveal>
        </div>
      </div>
    </div>
  );
}

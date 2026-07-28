import { BadgeCheck, MapPin, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Avatar } from '../../components/ui/Avatar';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { RatingStars } from '../../components/ui/RatingStars';
import type { ProviderProfile } from '../../services/domainTypes';

export interface ProviderCardProps {
  provider: ProviderProfile;
}

export function ProviderCard({ provider }: ProviderCardProps) {
  return (
    <Card interactive className="flex flex-col gap-3">
      <Link to={`/prestadores/${provider.id}`} className="flex flex-col gap-3 focus-visible:outline-none">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <Avatar name={provider.displayName} imageUrl={provider.avatarUrl} />
            <div>
              <p className="text-card-title font-display font-semibold text-foreground">{provider.displayName}</p>
              <p className="line-clamp-1 text-caption text-muted">{provider.headline}</p>
            </div>
          </div>
          {provider.premiumBadge ? (
            <span className="glow-accent rounded-full bg-orange-500/12 p-1.5" title="Premium">
              <Sparkles aria-hidden="true" className="size-4 text-orange-500" strokeWidth={1.5} />
            </span>
          ) : null}
        </div>

        <RatingStars value={provider.ratingAvg} count={provider.ratingCount} />

        <div className="flex flex-wrap gap-1.5">
          {provider.categoryNames.slice(0, 2).map((name) => (
            <Badge key={name} tone="neutral">
              {name}
            </Badge>
          ))}
          {provider.verified ? (
            <Badge tone="signal">
              <BadgeCheck aria-hidden="true" className="size-3.5" strokeWidth={1.5} />
              Verificado
            </Badge>
          ) : null}
        </div>

        {provider.zones[0] ? (
          <p className="flex items-center gap-1 text-caption text-muted">
            <MapPin aria-hidden="true" className="size-3.5" strokeWidth={1.5} />
            {provider.zones.map((zone) => zone.label).join(', ')}
          </p>
        ) : null}
      </Link>
    </Card>
  );
}

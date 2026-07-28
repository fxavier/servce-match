import { Star } from 'lucide-react';
import { cn } from '../../lib/cn';

export interface RatingStarsProps {
  value: number;
  count?: number;
  size?: number;
  className?: string;
  interactive?: boolean;
  onChange?: (value: number) => void;
}

/** Estrelas 1–5. Em modo interativo (avaliação nova) tem hover e é navegável por teclado. */
export function RatingStars({ value, count, size = 16, className, interactive = false, onChange }: RatingStarsProps) {
  const rounded = Math.round(value);

  if (interactive) {
    return (
      <div role="radiogroup" aria-label="Avaliação em estrelas" className={cn('flex items-center gap-1', className)}>
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            key={star}
            type="button"
            role="radio"
            aria-checked={value === star}
            aria-label={`${star} ${star === 1 ? 'estrela' : 'estrelas'}`}
            onClick={() => onChange?.(star)}
            className="rounded-full p-1 transition-transform hover:scale-110 motion-reduce:hover:scale-100"
          >
            <Star
              aria-hidden="true"
              size={size + 8}
              strokeWidth={1.5}
              className={star <= value ? 'fill-orange-500 text-orange-500' : 'fill-none text-line'}
            />
          </button>
        ))}
      </div>
    );
  }

  return (
    <span className={cn('inline-flex items-center gap-1', className)} aria-label={`${value.toFixed(1)} de 5 estrelas`}>
      <span className="flex" aria-hidden="true">
        {[1, 2, 3, 4, 5].map((star) => (
          <Star
            key={star}
            size={size}
            strokeWidth={1.5}
            className={star <= rounded ? 'fill-orange-500 text-orange-500' : 'fill-none text-line'}
          />
        ))}
      </span>
      <span className="text-caption font-medium text-foreground">{value.toFixed(1)}</span>
      {count !== undefined ? <span className="text-caption text-muted">({count})</span> : null}
    </span>
  );
}

import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

/**
 * Skeleton com a forma do conteúdo — nunca um spinner centrado (§10). Anima
 * um `pulse` suave; colapsa para estático com `prefers-reduced-motion`.
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-pulse rounded-md bg-surface-2 motion-reduce:animate-none', className)}
      {...props}
    />
  );
}

export function SkeletonText({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <div className={cn('flex flex-col gap-2', className)} aria-hidden="true">
      {Array.from({ length: lines }).map((_, index) => (
        <Skeleton key={index} className={cn('h-3.5', index === lines - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  );
}

export function SkeletonCard() {
  return (
    <div className="surface-card p-5" aria-hidden="true">
      <Skeleton className="mb-4 h-32 w-full rounded-md" />
      <Skeleton className="mb-2 h-4 w-3/4" />
      <SkeletonText lines={2} />
    </div>
  );
}

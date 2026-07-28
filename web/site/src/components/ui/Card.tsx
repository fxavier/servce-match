import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  interactive?: boolean;
  children: ReactNode;
}

/** Cartão base — `radius-lg`, sombra por tema via `--shadow-card` (§5.3). */
export function Card({ className, interactive = false, children, ...props }: CardProps) {
  return (
    <div className={cn('surface-card p-5', interactive && 'surface-card--interactive', className)} {...props}>
      {children}
    </div>
  );
}

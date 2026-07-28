import type { ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

export interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  selected?: boolean;
}

/** Chip selecionável — usado para urgência, filtros, etc. */
export function Chip({ className, selected = false, ...props }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={cn(
        'inline-flex h-11 items-center rounded-full border px-4 text-sm font-medium transition-colors',
        'duration-[var(--duration-micro)]',
        selected
          ? 'border-orange-500 bg-orange-500/12 text-orange-600 light:text-orange-600'
          : 'border-line bg-surface text-muted hover:border-orange-500/40',
        className,
      )}
      {...props}
    />
  );
}

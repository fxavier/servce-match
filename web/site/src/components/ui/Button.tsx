import { type ButtonHTMLAttributes, forwardRef } from 'react';
import { cn } from '../../lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'outline' | 'destructive';
type Size = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary:
    'bg-gradient-to-r from-orange-600 to-orange-400 text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110',
  secondary: 'bg-surface-2 text-foreground border border-line hover:border-orange-500/40',
  ghost: 'bg-transparent text-foreground hover:bg-surface-2',
  outline: 'bg-transparent text-foreground border border-line hover:border-orange-500/50',
  destructive: 'bg-orange-600 text-accent-fg hover:bg-orange-500',
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'h-9 px-3.5 text-sm gap-1.5',
  md: 'h-11 px-5 text-[0.95rem] gap-2',
  lg: 'h-[3.25rem] px-7 text-base gap-2.5',
};

/**
 * Botões usam `radius-full` de forma consistente (§5.3 — "escolhe um e sê
 * consistente"). Alvo de toque mínimo 44px garantido por `size="md"`/`"lg"`.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = 'primary', size = 'md', loading = false, disabled, children, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={cn(
        'inline-flex items-center justify-center rounded-full font-medium transition-all',
        'duration-[var(--duration-micro)] ease-[var(--ease-out)]',
        'disabled:opacity-50 disabled:pointer-events-none',
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <span className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent" aria-hidden="true" /> : null}
      {children}
    </button>
  );
});

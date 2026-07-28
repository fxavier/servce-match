import { type InputHTMLAttributes, forwardRef } from 'react';
import { cn } from '../../lib/cn';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, invalid = false, ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        'h-11 rounded-md border bg-surface px-3.5 text-body text-foreground placeholder:text-muted',
        'transition-colors duration-[var(--duration-micro)]',
        invalid ? 'border-orange-500' : 'border-line focus-visible:border-orange-500',
        className,
      )}
      {...props}
    />
  );
});

import { type TextareaHTMLAttributes, forwardRef } from 'react';
import { cn } from '../../lib/cn';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { className, invalid = false, ...props },
  ref,
) {
  return (
    <textarea
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        'min-h-28 rounded-md border bg-surface px-3.5 py-2.5 text-body text-foreground placeholder:text-muted',
        'transition-colors duration-[var(--duration-micro)]',
        invalid ? 'border-orange-500' : 'border-line focus-visible:border-orange-500',
        className,
      )}
      {...props}
    />
  );
});

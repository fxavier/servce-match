import { cloneElement, isValidElement, type ReactElement, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface FieldProps {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: ReactNode;
  className?: string;
}

/**
 * Casca de campo de formulário: `<label>` associado, erro ligado por
 * `aria-describedby` e anunciado com `role="alert"` (§10). O laranja
 * carrega a semântica de erro do produto — não introduz vermelho (§5.1).
 * Injeta `aria-describedby` no único filho (input/select/textarea) via
 * `cloneElement` — quem usa `Field` não precisa de repetir os ids.
 */
export function Field({ id, label, error, hint, required, children, className }: FieldProps) {
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = error ? errorId : hint ? hintId : undefined;

  const child = isValidElement(children)
    ? cloneElement(children as ReactElement<{ 'aria-describedby'?: string }>, {
        'aria-describedby': describedBy,
      })
    : children;

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-foreground">
        {label}
        {required ? (
          <span aria-hidden="true" className="text-orange-500">
            {' '}
            *
          </span>
        ) : null}
      </label>
      {child}
      {hint && !error ? (
        <p id={hintId} className="text-caption text-muted">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-caption font-medium text-orange-600">
          {error}
        </p>
      ) : null}
    </div>
  );
}

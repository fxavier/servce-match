import { cn } from '../../lib/cn';

export interface AvatarProps {
  name: string;
  imageUrl?: string | null;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZE_CLASSES = { sm: 'size-9 text-xs', md: 'size-12 text-sm', lg: 'size-20 text-xl' };

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return (first + last).toUpperCase();
}

/**
 * Avatar gerado por gradiente (navy-700 → navy-600) com as iniciais em
 * Archivo 700 (§6 — "não tens ficheiros de imagem"). Só usa `imageUrl`
 * quando existir de facto (nunca placeholder cinzento feio).
 */
export function Avatar({ name, imageUrl, size = 'md', className }: AvatarProps) {
  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt={name}
        width={80}
        height={80}
        loading="lazy"
        className={cn('rounded-full object-cover', SIZE_CLASSES[size], className)}
      />
    );
  }

  return (
    <span
      role="img"
      aria-label={name}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-navy-700 to-navy-600 font-display font-bold text-white',
        SIZE_CLASSES[size],
        className,
      )}
    >
      {initialsOf(name)}
    </span>
  );
}

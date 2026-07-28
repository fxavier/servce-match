import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/cn';

type Tone = 'neutral' | 'accent' | 'signal' | 'success' | 'warning' | 'whatsapp';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

const TONE_CLASSES: Record<Tone, string> = {
  neutral: 'bg-surface-2 text-muted border-line',
  accent: 'bg-orange-500/12 text-orange-600 light:text-orange-600 border-orange-500/30',
  signal: 'bg-signal-500/12 text-signal-500 border-signal-500/30',
  success: 'bg-success/12 text-success border-success/30',
  warning: 'bg-orange-500/12 text-orange-600 border-orange-500/30',
  whatsapp: 'bg-whatsapp/12 text-whatsapp-fg border-whatsapp/30',
};

export function Badge({ className, tone = 'neutral', children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs font-medium leading-none',
        TONE_CLASSES[tone],
        className,
      )}
      {...props}
    >
      {children}
    </span>
  );
}

import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface MarqueeProps {
  children: ReactNode;
  className?: string;
  durationSeconds?: number;
}

/**
 * Marquee lento em CSS puro (§6.2 — faixa de prova social). Sem autoplay de
 * carrossel de conteúdo interativo (anti-padrão §6); isto é decorativo,
 * pausa em hover e para totalmente sob `prefers-reduced-motion`.
 */
export function Marquee({ children, className, durationSeconds = 32 }: MarqueeProps) {
  return (
    <div className={cn('group overflow-hidden', className)}>
      <div
        className="flex w-max gap-16 motion-reduce:!animate-none group-hover:[animation-play-state:paused]"
        style={{ animation: `sm-marquee ${durationSeconds}s linear infinite` }}
      >
        <div className="flex shrink-0 items-center gap-16">{children}</div>
        <div aria-hidden="true" className="flex shrink-0 items-center gap-16">
          {children}
        </div>
      </div>
      <style>{`
        @keyframes sm-marquee {
          from { transform: translateX(0); }
          to { transform: translateX(-50%); }
        }
      `}</style>
    </div>
  );
}

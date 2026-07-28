import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

export interface SectionBackgroundProps {
  children: ReactNode;
  className?: string;
  /** Lado do brilho radial — laranja e cyan nunca no mesmo elemento (§6). */
  glow?: 'orange' | 'signal' | 'both' | 'none';
  grain?: boolean;
}

/**
 * Camadas de profundidade partilhadas por hero e separadores de secção
 * (§6): chão + brilhos radiais difusos + grão de filme. A grelha/mapa
 * vetorial ténue fica a cargo de cada secção específica (varia por contexto).
 */
export function SectionBackground({ children, className, glow = 'none', grain = false }: SectionBackgroundProps) {
  return (
    <div className={cn('relative overflow-hidden', grain && 'grain', className)}>
      {glow === 'orange' || glow === 'both' ? (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-40 -top-40 size-[560px] rounded-full bg-orange-500/18 blur-[120px]"
        />
      ) : null}
      {glow === 'signal' || glow === 'both' ? (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-40 -left-40 size-[520px] rounded-full bg-signal-500/12 blur-[120px]"
        />
      ) : null}
      <div className="relative">{children}</div>
    </div>
  );
}

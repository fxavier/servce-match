import { type ReactNode, useRef } from 'react';
import { motion, useReducedMotion, useScroll, useTransform } from 'motion/react';
import { cn } from '../../lib/cn';

export interface ParallaxProps {
  children: ReactNode;
  className?: string;
  /** Deslocamento máximo em px — mantém-se subtil (§6: ≤ 40px). */
  offset?: number;
}

/** Parallax subtil nas camadas de fundo do hero/separadores — desligado sob reduced motion. */
export function Parallax({ children, className, offset = 30 }: ParallaxProps) {
  const ref = useRef<HTMLDivElement>(null);
  const reduceMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({ target: ref, offset: ['start end', 'end start'] });
  const y = useTransform(scrollYProgress, [0, 1], reduceMotion ? [0, 0] : [-offset, offset]);

  return (
    <motion.div ref={ref} className={cn(className)} style={{ y }}>
      {children}
    </motion.div>
  );
}

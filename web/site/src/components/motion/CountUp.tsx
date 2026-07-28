import { useEffect, useRef, useState } from 'react';
import { useInView, useReducedMotion } from 'motion/react';

export interface CountUpProps {
  value: number;
  durationMs?: number;
  suffix?: string;
  prefix?: string;
  formatter?: (value: number) => string;
  className?: string;
}

/** Número-chave que conta até `value` ao entrar no viewport (§6.2, uma vez só). */
export function CountUp({ value, durationMs = 1200, suffix = '', prefix = '', formatter, className }: CountUpProps) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: '-80px' });
  const reduceMotion = useReducedMotion();
  const [display, setDisplay] = useState(reduceMotion ? value : 0);

  useEffect(() => {
    if (!inView) return;
    if (reduceMotion) {
      setDisplay(value);
      return;
    }
    let frame: number;
    const start = performance.now();
    function tick(now: number) {
      const progress = Math.min(1, (now - start) / durationMs);
      const eased = 1 - (1 - progress) ** 3;
      setDisplay(Math.round(eased * value));
      if (progress < 1) frame = requestAnimationFrame(tick);
    }
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [inView, value, durationMs, reduceMotion]);

  return (
    <span ref={ref} className={className}>
      {prefix}
      {formatter ? formatter(display) : display.toLocaleString('pt-PT')}
      {suffix}
    </span>
  );
}

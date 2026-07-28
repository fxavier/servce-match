import { useState } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import { Reveal } from '../motion/Reveal';
import { cn } from '../../lib/cn';

type Persona = 'customer' | 'provider';

const STEPS: Record<Persona, { number: string; title: string; description: string }[]> = {
  customer: [
    { number: '01', title: 'Descreva o pedido', description: 'Categoria, morada e algumas fotos — três minutos, sem registo obrigatório até publicar.' },
    { number: '02', title: 'Receba orçamentos', description: 'Prestadores elegíveis da sua zona respondem com preço e prazo, normalmente em poucas horas.' },
    { number: '03', title: 'Compare e aceite', description: 'Veja avaliações, converse e escolha o orçamento certo. O resto combina-se diretamente.' },
  ],
  provider: [
    { number: '01', title: 'Ative a subscrição', description: 'Escolha um plano, defina categorias e zonas de atuação — passa a ser visível no matching.' },
    { number: '02', title: 'Receba pedidos elegíveis', description: 'Pedidos da sua categoria e zona chegam à sua inbox, com toda a informação necessária.' },
    { number: '03', title: 'Envie orçamentos', description: 'Proponha preço e prazo, converse com o cliente e feche o serviço.' },
  ],
};

export function HowItWorksSection() {
  const [persona, setPersona] = useState<Persona>('customer');
  const reduceMotion = useReducedMotion();
  const steps = STEPS[persona];

  return (
    <section className="border-y border-line bg-surface-2 py-[clamp(5rem,10vw,9rem)]">
      <div className="mx-auto max-w-[1280px] px-5 sm:px-8 lg:px-10">
        <div className="flex flex-wrap items-end justify-between gap-6">
          <Reveal>
            <p className="eyebrow text-signal-500">COMO FUNCIONA</p>
            <h2 className="mt-3 text-h1 font-display font-bold text-foreground">Do pedido ao trabalho feito</h2>
          </Reveal>

          <div role="tablist" aria-label="Ver como funciona para" className="inline-flex rounded-full border border-line bg-surface p-1">
            {(['customer', 'provider'] as const).map((value) => (
              <button
                key={value}
                type="button"
                role="tab"
                aria-selected={persona === value}
                onClick={() => setPersona(value)}
                className={cn(
                  'rounded-full px-4 py-2 text-sm font-medium transition-colors',
                  persona === value ? 'bg-orange-500 text-accent-fg' : 'text-muted hover:text-foreground',
                )}
              >
                {value === 'customer' ? 'Sou cliente' : 'Sou profissional'}
              </button>
            ))}
          </div>
        </div>

        <AnimatePresence mode="wait">
          <motion.ol
            key={persona}
            initial={reduceMotion ? { opacity: 1 } : { opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduceMotion ? { opacity: 1 } : { opacity: 0, y: -12 }}
            transition={{ duration: 0.32, ease: [0.4, 0, 0.2, 1] }}
            className="mt-12 grid gap-8 sm:grid-cols-3"
          >
            {steps.map((step, index) => (
              <li key={step.number} className="relative">
                <span className="font-display text-5xl font-extrabold text-line" aria-hidden="true">
                  {step.number}
                </span>
                <p className="mt-4 text-card-title font-display font-semibold text-foreground">{step.title}</p>
                <p className="mt-2 text-body text-muted">{step.description}</p>
                {index < steps.length - 1 ? (
                  <span
                    aria-hidden="true"
                    className="absolute right-[-1.25rem] top-6 hidden h-px w-8 bg-gradient-to-r from-orange-500/60 to-transparent sm:block"
                  />
                ) : null}
              </li>
            ))}
          </motion.ol>
        </AnimatePresence>
      </div>
    </section>
  );
}

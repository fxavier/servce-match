import { type FormEvent, useState } from 'react';
import { ArrowRight, MapPin } from 'lucide-react';
import { motion, useReducedMotion } from 'motion/react';
import { useNavigate } from 'react-router-dom';
import { Select } from '../../components/ui/Select';
import { Input } from '../../components/ui/Input';
import { RatingStars } from '../../components/ui/RatingStars';
import { Reveal } from '../motion/Reveal';
import { SectionBackground } from './SectionBackground';
import { useCategories } from '../../features/categories/useCategories';

export function Hero() {
  const navigate = useNavigate();
  const [categoryId, setCategoryId] = useState('');
  const [location, setLocation] = useState('');
  const reduceMotion = useReducedMotion();
  const { data: categories } = useCategories();
  const topLevelCategories = categories?.filter((category) => category.parentId === null) ?? [];

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = new URLSearchParams();
    if (categoryId) params.set('categoria', categoryId);
    if (location) params.set('zona', location);
    void navigate(`/pedidos/novo${params.toString() ? `?${params.toString()}` : ''}`);
  }

  return (
    <SectionBackground glow="both" grain className="pb-16 pt-14 sm:pb-20 sm:pt-20 lg:pb-28 lg:pt-24">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 opacity-[0.06]"
        style={{
          backgroundImage:
            'linear-gradient(var(--color-line) 1px, transparent 1px), linear-gradient(90deg, var(--color-line) 1px, transparent 1px)',
          backgroundSize: '64px 64px',
        }}
      />
      <div className="mx-auto grid max-w-[1280px] gap-12 px-5 sm:px-8 lg:grid-cols-[1.05fr_0.95fr] lg:items-center lg:px-10">
        <div>
          <Reveal>
            <p className="eyebrow text-signal-500">SERVIÇOS LOCAIS · PORTUGAL</p>
          </Reveal>
          <Reveal delay={0.05}>
            <h1 className="mt-4 text-hero font-display font-extrabold text-foreground">
              Descreva o problema. Receba <span className="text-gradient-energy">orçamentos</span> de profissionais da
              sua zona.
            </h1>
          </Reveal>
          <Reveal delay={0.1}>
            <p className="mt-5 max-w-lg text-body-lg text-muted">
              Publique um pedido grátis e compare propostas de prestadores verificados — sem ligar a ninguém, sem
              esperar dias por resposta.
            </p>
          </Reveal>

          <Reveal delay={0.15}>
            <form
              onSubmit={handleSubmit}
              className="surface-card mt-8 flex flex-col gap-3 p-3 sm:flex-row sm:items-center"
              aria-label="Arranque rápido de pedido"
            >
              <label className="sr-only" htmlFor="hero-category">
                Categoria
              </label>
              <Select
                id="hero-category"
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
                className="sm:max-w-[220px]"
              >
                <option value="">Que serviço precisa?</option>
                {topLevelCategories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </Select>
              <label className="sr-only" htmlFor="hero-location">
                Concelho ou código postal
              </label>
              <div className="relative flex-1">
                <MapPin aria-hidden="true" className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted" strokeWidth={1.5} />
                <Input
                  id="hero-location"
                  value={location}
                  onChange={(event) => setLocation(event.target.value)}
                  placeholder="Concelho ou código postal"
                  className="pl-10"
                />
              </div>
              <button
                type="submit"
                className="inline-flex h-11 items-center justify-center gap-1.5 rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg shadow-[0_0_40px_-12px_var(--color-orange-500)] hover:brightness-110"
              >
                Pedir orçamentos grátis
                <ArrowRight aria-hidden="true" className="size-4" strokeWidth={1.5} />
              </button>
            </form>
          </Reveal>

          <Reveal delay={0.2}>
            <dl className="mt-8 flex flex-wrap gap-x-8 gap-y-3">
              <div>
                <dt className="sr-only">Profissionais ativos</dt>
                <dd className="font-mono text-eyebrow tracking-[0.08em] text-muted">+2 400 PROFISSIONAIS ATIVOS</dd>
              </div>
              <div>
                <dt className="sr-only">Tempo até à primeira proposta</dt>
                <dd className="font-mono text-eyebrow tracking-[0.08em] text-muted">1ª PROPOSTA EM &lt; 4 H</dd>
              </div>
              <div>
                <dt className="sr-only">Avaliação média</dt>
                <dd className="font-mono text-eyebrow tracking-[0.08em] text-muted">4,8 ★ MÉDIA</dd>
              </div>
            </dl>
          </Reveal>
        </div>

        <div className="relative hidden h-[420px] lg:block" aria-hidden="true">
          <motion.div
            initial={reduceMotion ? { opacity: 1 } : { opacity: 0, x: 24 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1], delay: 0.2 }}
            className="surface-card absolute left-0 top-6 w-72 p-4"
          >
            <p className="eyebrow text-muted">PEDIDO · SM-2026-0481</p>
            <p className="mt-2 font-display text-card-title font-semibold">Fuga de água na cozinha</p>
            <p className="mt-1 text-caption text-muted">Alvalade, Lisboa · Urgente</p>
            <div className="mt-3 flex items-center gap-2">
              <span className="inline-flex items-center rounded-full bg-signal-500/12 px-2.5 py-1 text-xs font-medium text-signal-500">
                A procurar prestadores…
              </span>
            </div>
          </motion.div>

          <motion.div
            initial={reduceMotion ? { opacity: 1 } : { opacity: 0, x: -24 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1], delay: 0.45 }}
            className="surface-card glow-accent absolute bottom-6 right-0 w-72 p-4"
          >
            <p className="eyebrow text-orange-500">NOVA PROPOSTA</p>
            <p className="mt-2 font-display text-card-title font-semibold">Canalizações Silva &amp; Filhos</p>
            <RatingStars value={4.9} count={214} className="mt-1" />
            <p className="mt-3 font-display text-xl font-bold tabular-nums">75,00 €</p>
            <p className="text-caption text-muted">Prazo estimado: 1 dia</p>
          </motion.div>

          <svg className="absolute inset-0 h-full w-full" viewBox="0 0 300 420" fill="none" aria-hidden="true">
            <motion.path
              d="M 90 140 C 160 200, 160 260, 210 320"
              stroke="var(--color-signal-500)"
              strokeWidth="2"
              strokeDasharray="6 6"
              initial={reduceMotion ? { pathLength: 1, opacity: 0.5 } : { pathLength: 0, opacity: 0 }}
              animate={{ pathLength: 1, opacity: 0.6 }}
              transition={{ duration: 1.2, delay: 0.6, ease: [0.22, 1, 0.36, 1] }}
            />
          </svg>
        </div>
      </div>
    </SectionBackground>
  );
}

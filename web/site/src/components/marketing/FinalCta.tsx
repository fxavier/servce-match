import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Reveal } from '../motion/Reveal';
import { SectionBackground } from './SectionBackground';

export function FinalCta() {
  return (
    <SectionBackground glow="orange" grain className="flex min-h-[70vh] items-center justify-center py-24">
      <div className="mx-auto max-w-2xl px-5 text-center sm:px-8">
        <Reveal>
          <h2 className="text-h1 font-display font-bold text-foreground">
            Pronto para resolver o seu <span className="text-gradient-energy">próximo</span> serviço?
          </h2>
          <p className="mt-4 text-body-lg text-muted">
            Publique um pedido em três minutos ou torne-se prestador e comece a receber pedidos qualificados hoje.
          </p>
          <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Link
              to="/pedidos/novo"
              className="inline-flex h-12 items-center justify-center gap-1.5 rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-6 text-base font-medium text-accent-fg shadow-[0_0_60px_-16px_var(--color-orange-500)] hover:brightness-110"
            >
              Publicar um pedido
              <ArrowRight aria-hidden="true" className="size-4" strokeWidth={1.5} />
            </Link>
            <Link
              to="/precos"
              className="inline-flex h-12 items-center justify-center rounded-full border border-line px-6 text-base font-medium text-foreground hover:border-orange-500/40"
            >
              Sou profissional
            </Link>
          </div>
        </Reveal>
      </div>
    </SectionBackground>
  );
}

import { Compass } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { SectionBackground } from '../../components/marketing/SectionBackground';
import { Reveal } from '../../components/motion/Reveal';

export function NotFoundPage() {
  return (
    <SectionBackground glow="signal" grain className="flex min-h-[70vh] items-center justify-center">
      <Seo title="Página não encontrada" description="A página que procura não existe ou foi movida." />
      <div className="mx-auto max-w-md px-5 py-24 text-center">
        <Reveal>
          <span className="font-display text-7xl font-extrabold text-line">404</span>
          <div className="mx-auto mt-4 flex size-14 items-center justify-center rounded-full bg-signal-500/12">
            <Compass aria-hidden="true" className="size-7 text-signal-500" strokeWidth={1.5} />
          </div>
          <h1 className="mt-6 text-h2 font-display font-bold text-foreground">Esta página não existe (ou mudou de sítio)</h1>
          <p className="mt-3 text-body text-muted">
            Verifique o endereço ou volte à página inicial para continuar a explorar o ServiMatch.
          </p>
          <Link
            to="/"
            className="mt-8 inline-flex h-11 items-center justify-center rounded-full bg-gradient-to-r from-orange-600 to-orange-400 px-5 text-sm font-medium text-accent-fg"
          >
            Voltar à página inicial
          </Link>
        </Reveal>
      </div>
    </SectionBackground>
  );
}

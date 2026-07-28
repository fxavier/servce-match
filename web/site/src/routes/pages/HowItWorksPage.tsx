import { Seo } from '../../components/Seo';
import { HowItWorksSection } from '../../components/marketing/HowItWorksSection';
import { Reveal } from '../../components/motion/Reveal';

export function HowItWorksPage() {
  return (
    <div>
      <Seo
        title="Como funciona"
        description="Veja como funciona o ServiMatch para clientes e para prestadores, do pedido ao trabalho concluído."
        canonicalPath="/como-funciona"
      />
      <div className="mx-auto max-w-[1280px] px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:px-10">
        <Reveal>
          <p className="eyebrow text-signal-500">GUIA RÁPIDO</p>
          <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Como funciona o ServiMatch</h1>
          <p className="mt-3 max-w-2xl text-body text-muted">
            Um marketplace simples: o cliente nunca paga para usar a plataforma, o prestador precisa de subscrição
            ativa para ser visível e enviar orçamentos.
          </p>
        </Reveal>
      </div>
      <HowItWorksSection />
    </div>
  );
}

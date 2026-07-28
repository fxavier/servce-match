import { Seo } from '../../components/Seo';
import { CategoryGrid } from '../../components/marketing/CategoryGrid';
import { FeaturedProviders } from '../../components/marketing/FeaturedProviders';
import { FinalCta } from '../../components/marketing/FinalCta';
import { Hero } from '../../components/marketing/Hero';
import { HowItWorksSection } from '../../components/marketing/HowItWorksSection';
import { PricingSummary } from '../../components/marketing/PricingSummary';
import { StatsStrip } from '../../components/marketing/StatsStrip';
import { Testimonials } from '../../components/marketing/Testimonials';
import { TrustMarquee } from '../../components/marketing/TrustMarquee';
import { localBusinessJsonLd } from '../../lib/seo';

/**
 * Landing (§7) — ordem literal do prompt: hero → prova social → categorias →
 * como funciona → prestadores em destaque → números → depoimentos → preços
 * → CTA final → rodapé (o rodapé vive em `PublicLayout`).
 */
export function LandingPage() {
  return (
    <>
      <Seo
        title="ServiMatch"
        description="Descreva o problema, receba orçamentos de profissionais verificados da sua zona. Canalização, eletricidade, limpeza, pintura e muito mais — grátis para clientes."
        canonicalPath="/"
        jsonLd={localBusinessJsonLd({
          name: 'ServiMatch',
          description: 'Marketplace de serviços locais em Portugal.',
          url: 'https://www.servimatch.pt/',
          areaServed: ['PT'],
        })}
      />
      <Hero />
      <TrustMarquee />
      <CategoryGrid />
      <HowItWorksSection />
      <FeaturedProviders />
      <StatsStrip />
      <Testimonials />
      <PricingSummary />
      <FinalCta />
    </>
  );
}

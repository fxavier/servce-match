import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { Seo } from '../../components/Seo';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonCard, SkeletonText } from '../../components/ui/Skeleton';
import { Reveal } from '../../components/motion/Reveal';
import { ProviderCard } from '../../features/providers/ProviderCard';
import { toProblem } from '../../lib/problem';
import { serviceJsonLd } from '../../lib/seo';
import { services } from '../../services';
import { NotFoundPage } from './NotFoundPage';

/** Copy de referência editorial (não é dado ao vivo) — âmbito documentado no README. */
const REFERENCE_PRICE_HINTS: Record<string, string> = {
  canalizacao: 'entre 60 € e 250 €, consoante o âmbito da intervenção',
  eletricidade: 'entre 50 € e 200 €, para intervenções pontuais',
  climatizacao: 'entre 150 € e 600 €, incluindo instalação',
  jardinagem: 'entre 40 € e 150 €, por visita',
  limpezas: 'entre 30 € e 120 €, consoante a área',
  mudancas: 'entre 150 € e 450 €, consoante o volume',
  'obras-remodelacoes': 'a partir de 2 000 €, consoante a divisão',
  pinturas: 'entre 400 € e 1 500 €, consoante a área',
  serralharia: 'entre 80 € e 300 €',
  carpintaria: 'entre 300 € e 1 200 €, para peças à medida',
};

const FAQ = [
  { q: 'Quanto custa pedir um orçamento?', a: 'Nada — publicar um pedido e receber orçamentos é sempre grátis para clientes.' },
  { q: 'Quanto tempo demora a receber propostas?', a: 'A maioria dos pedidos recebe a primeira proposta em menos de 4 horas, dependendo da categoria e da zona.' },
  { q: 'Os prestadores são verificados?', a: 'Todos os perfis passam por aprovação manual antes de poderem responder a pedidos.' },
];

export function CategoryLandingPage() {
  const { slug } = useParams<{ slug: string }>();

  const categoriesQuery = useQuery({
    queryKey: ['categories', 'all'],
    queryFn: () => services.categories.list(),
  });

  const category = categoriesQuery.data?.find((c) => c.slug === slug);

  const providersQuery = useQuery({
    queryKey: ['providers', 'by-category', category?.id],
    queryFn: () => services.providers.search({ categoryId: category?.id, limit: 6 }),
    enabled: Boolean(category),
  });

  if (categoriesQuery.isLoading) {
    return (
      <div className="mx-auto max-w-[1280px] px-5 py-24 sm:px-8 lg:px-10">
        <SkeletonText lines={4} />
      </div>
    );
  }

  if (categoriesQuery.error) {
    return (
      <div className="mx-auto max-w-[1280px] px-5 py-24 sm:px-8 lg:px-10">
        <ErrorState problem={toProblem(categoriesQuery.error)} onRetry={() => void categoriesQuery.refetch()} />
      </div>
    );
  }

  if (!category) {
    return <NotFoundPage />;
  }

  const priceHint = REFERENCE_PRICE_HINTS[category.slug] ?? 'variável consoante o âmbito do serviço';

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:px-10">
      <Seo
        title={category.name}
        description={`Encontre prestadores de ${category.name.toLowerCase()} perto de si. Receba orçamentos grátis e compare preços, avaliações e prazos.`}
        canonicalPath={`/servicos/${category.slug}`}
        jsonLd={serviceJsonLd({ name: category.name, description: `Serviços de ${category.name}`, areaServed: 'PT' })}
      />
      <Reveal>
        <p className="eyebrow text-signal-500">SERVIÇOS · {category.name.toUpperCase()}</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">{category.name}</h1>
        <p className="mt-3 max-w-2xl text-body text-muted">
          Publique um pedido de {category.name.toLowerCase()} e receba orçamentos de profissionais verificados da sua
          zona, normalmente em poucas horas.
        </p>
        <p className="mt-3 font-mono text-eyebrow tracking-[0.08em] text-muted">
          PREÇO DE REFERÊNCIA: {priceHint.toUpperCase()}
        </p>
      </Reveal>

      <section className="mt-14" aria-labelledby="featured-heading">
        <h2 id="featured-heading" className="text-h2 font-display font-bold text-foreground">
          Prestadores em destaque
        </h2>
        {providersQuery.error ? (
          <div className="mt-6">
            <ErrorState problem={toProblem(providersQuery.error)} onRetry={() => void providersQuery.refetch()} />
          </div>
        ) : providersQuery.isLoading ? (
          <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <SkeletonCard key={index} />
            ))}
          </div>
        ) : providersQuery.data && providersQuery.data.items.length > 0 ? (
          <div className="mt-6 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {providersQuery.data.items.map((provider) => (
              <ProviderCard key={provider.id} provider={provider} />
            ))}
          </div>
        ) : (
          <div className="mt-6">
            <EmptyState title="Ainda sem prestadores nesta categoria" description="Seja o primeiro a publicar um pedido — avisamos os prestadores assim que ativarem esta categoria." />
          </div>
        )}
      </section>

      <section className="mt-14" aria-labelledby="faq-heading">
        <h2 id="faq-heading" className="text-h2 font-display font-bold text-foreground">
          Perguntas frequentes
        </h2>
        <dl className="mt-6 flex flex-col divide-y divide-line border-y border-line">
          {FAQ.map((item) => (
            <div key={item.q} className="py-5">
              <dt className="text-card-title font-display font-semibold text-foreground">{item.q}</dt>
              <dd className="mt-2 text-body text-muted">{item.a}</dd>
            </div>
          ))}
        </dl>
      </section>
    </div>
  );
}

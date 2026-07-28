import { useQuery } from '@tanstack/react-query';
import { Check, Minus } from 'lucide-react';
import { Seo } from '../../components/Seo';
import { ErrorState } from '../../components/ui/ErrorState';
import { SkeletonCard } from '../../components/ui/Skeleton';
import { Reveal } from '../../components/motion/Reveal';
import { PricingCard } from '../../features/subscriptions/PricingCard';
import { toProblem } from '../../lib/problem';
import { services } from '../../services';

const FAQ = [
  { q: 'Posso mudar de plano depois?', a: 'Sim, a qualquer momento a partir de "A minha subscrição" — a mudança aplica-se no início do período seguinte.' },
  { q: 'O que acontece se a subscrição expirar?', a: 'O perfil deixa de ser elegível para matching e de poder enviar orçamentos até renovar.' },
  { q: 'Há compromisso de fidelização?', a: 'Não. Os planos são mensais, sem fidelização.' },
  { q: 'Os clientes pagam alguma coisa?', a: 'Não — publicar pedidos, receber propostas, conversar e avaliar é sempre grátis para clientes.' },
];

const COMPARISON: { feature: string; starter: string | boolean; professional: string | boolean; premium: string | boolean }[] = [
  { feature: 'Categorias', starter: 'Até 3', professional: 'Ilimitadas', premium: 'Ilimitadas' },
  { feature: 'Zonas de atuação', starter: 'Até 2', professional: 'Ilimitadas', premium: 'Ilimitadas' },
  { feature: 'Propostas por mês', starter: 'Ilimitadas', professional: 'Ilimitadas', premium: 'Ilimitadas' },
  { feature: 'Destaque na pesquisa', starter: false, professional: false, premium: true },
  { feature: 'Selo Premium no perfil', starter: false, professional: false, premium: true },
  { feature: 'Prioridade no matching', starter: false, professional: false, premium: true },
];

export function PricingPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: () => services.subscriptions.listPlans(),
  });

  return (
    <div className="mx-auto max-w-[1280px] px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8 lg:px-10">
      <Seo
        title="Preços para prestadores"
        description="Planos Starter, Professional e Premium para prestadores de serviços no ServiMatch. Sem fidelização, cancele quando quiser."
        canonicalPath="/precos"
      />
      <Reveal>
        <p className="eyebrow text-signal-500">PARA PRESTADORES</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Planos e preços</h1>
        <p className="mt-3 max-w-2xl text-body text-muted">
          O cliente nunca paga para usar o ServiMatch. A subscrição do prestador é o que mantém o perfil visível,
          elegível para matching e a poder enviar orçamentos.
        </p>

        <div className="mt-6 inline-flex items-center gap-3 rounded-full border border-line bg-surface p-1">
          <span className="rounded-full bg-orange-500 px-4 py-2 text-sm font-medium text-accent-fg">Mensal</span>
          <span className="flex cursor-not-allowed items-center gap-1.5 rounded-full px-4 py-2 text-sm font-medium text-muted" aria-disabled="true">
            Anual
            <span className="rounded-full bg-surface-2 px-2 py-0.5 text-xs">Em breve</span>
          </span>
        </div>
      </Reveal>

      {error ? (
        <div className="mt-10">
          <ErrorState problem={toProblem(error)} onRetry={() => void refetch()} />
        </div>
      ) : isLoading ? (
        <div className="mt-10 grid gap-6 sm:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <SkeletonCard key={index} />
          ))}
        </div>
      ) : (
        <div className="mt-10 grid gap-6 sm:grid-cols-3">
          {data?.map((plan) => <PricingCard key={plan.id} plan={plan} highlighted={plan.code === 'professional'} />)}
        </div>
      )}

      <section className="mt-16 overflow-x-auto" aria-labelledby="comparison-heading">
        <h2 id="comparison-heading" className="text-h2 font-display font-bold text-foreground">
          Comparação completa
        </h2>
        <table className="mt-6 w-full min-w-[640px] border-collapse text-left">
          <thead>
            <tr className="border-b border-line text-caption text-muted">
              <th scope="col" className="py-3 font-medium">
                Funcionalidade
              </th>
              <th scope="col" className="py-3 font-medium">
                Starter
              </th>
              <th scope="col" className="py-3 font-medium">
                Professional
              </th>
              <th scope="col" className="py-3 font-medium">
                Premium
              </th>
            </tr>
          </thead>
          <tbody>
            {COMPARISON.map((row) => (
              <tr key={row.feature} className="border-b border-line">
                <th scope="row" className="py-3.5 text-sm font-medium text-foreground">
                  {row.feature}
                </th>
                {[row.starter, row.professional, row.premium].map((value, index) => (
                  <td key={index} className="py-3.5 text-sm text-muted">
                    {typeof value === 'boolean' ? (
                      value ? (
                        <Check aria-hidden="true" className="size-4 text-orange-500" />
                      ) : (
                        <Minus aria-hidden="true" className="size-4 text-line" />
                      )
                    ) : (
                      value
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="mt-16" aria-labelledby="pricing-faq-heading">
        <h2 id="pricing-faq-heading" className="text-h2 font-display font-bold text-foreground">
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

import { CountUp } from '../motion/CountUp';
import { Reveal } from '../motion/Reveal';

const STATS = [
  { value: 2437, label: 'Profissionais ativos', suffix: '' },
  { value: 18, label: 'Concelhos cobertos', suffix: '' },
  { value: 4, label: 'Horas até à 1ª proposta (média)', suffix: 'h' },
  { value: 96, label: 'Pedidos resolvidos com sucesso', suffix: '%' },
];

export function StatsStrip() {
  return (
    <section className="mx-auto max-w-[1280px] px-5 py-[clamp(5rem,10vw,9rem)] sm:px-8 lg:px-10">
      <Reveal>
        <dl className="grid grid-cols-2 divide-y divide-line rounded-lg border border-line sm:grid-cols-4 sm:divide-x sm:divide-y-0">
          {STATS.map((stat) => (
            <div key={stat.label} className="p-6 text-center sm:p-8">
              <dt className="sr-only">{stat.label}</dt>
              <dd className="font-display text-4xl font-extrabold tabular-nums text-foreground sm:text-5xl">
                <CountUp value={stat.value} suffix={stat.suffix} />
              </dd>
              <p className="mt-2 text-caption text-muted">{stat.label}</p>
            </div>
          ))}
        </dl>
      </Reveal>
    </section>
  );
}

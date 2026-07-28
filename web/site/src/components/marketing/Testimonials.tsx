import { Avatar } from '../ui/Avatar';
import { Reveal } from '../motion/Reveal';

const TESTIMONIALS = [
  {
    quote:
      'Publiquei o pedido às 9h e às 13h já tinha três orçamentos comparáveis. Escolhi o Carlos e a fuga ficou resolvida no mesmo dia.',
    author: 'Mariana Costa',
    location: 'Lisboa',
  },
  {
    quote:
      'Como prestador, a diferença é não perder tempo a negociar com quem só quer "só perguntar". Chegam pedidos já qualificados da minha zona.',
    author: 'Carlos Silva',
    location: 'Canalizações Silva & Filhos · Lisboa',
  },
];

export function Testimonials() {
  return (
    <section className="border-y border-line py-[clamp(5rem,10vw,9rem)]">
      <div className="mx-auto grid max-w-[1280px] gap-12 px-5 sm:px-8 md:grid-cols-2 lg:px-10">
        {TESTIMONIALS.map((testimonial) => (
          <Reveal key={testimonial.author}>
            <figure>
              <blockquote className="font-display text-2xl font-semibold leading-snug text-foreground sm:text-3xl">
                “{testimonial.quote}”
              </blockquote>
              <figcaption className="mt-6 flex items-center gap-3">
                <Avatar name={testimonial.author} size="md" />
                <div>
                  <p className="text-sm font-medium text-foreground">{testimonial.author}</p>
                  <p className="font-mono text-eyebrow tracking-[0.08em] text-muted">
                    {testimonial.location.toUpperCase()}
                  </p>
                </div>
              </figcaption>
            </figure>
          </Reveal>
        ))}
      </div>
    </section>
  );
}

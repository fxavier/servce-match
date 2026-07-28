import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { Seo } from '../../components/Seo';
import { Reveal } from '../../components/motion/Reveal';
import { cn } from '../../lib/cn';

const FAQ_GROUPS = [
  {
    title: 'Para clientes',
    items: [
      { q: 'É mesmo grátis publicar um pedido?', a: 'Sim, sempre. Publicar pedidos, receber propostas, conversar com prestadores e avaliar é grátis para clientes, sem limite.' },
      { q: 'Como sei se um prestador é de confiança?', a: 'Todos os perfis passam por aprovação manual. Veja avaliações verificadas (só de trabalhos concluídos), selo de verificação e histórico na plataforma.' },
      { q: 'Posso cancelar um pedido?', a: 'Sim, a partir do detalhe do pedido, enquanto ainda não tiver aceite uma proposta.' },
    ],
  },
  {
    title: 'Para prestadores',
    items: [
      { q: 'Como funciona o matching?', a: 'Recebe pedidos elegíveis com base na sua subscrição ativa, categorias e zonas de atuação configuradas no perfil.' },
      { q: 'Posso mudar de plano?', a: 'Sim, a qualquer momento em "A minha subscrição" — a mudança aplica-se no início do período seguinte.' },
      { q: 'Como e quando recebo o pagamento do serviço?', a: 'O pagamento do serviço é combinado diretamente com o cliente, fora da plataforma. A subscrição é o único valor pago ao ServiMatch.' },
    ],
  },
];

export function FaqPage() {
  const [openKey, setOpenKey] = useState<string | undefined>(undefined);

  return (
    <div className="mx-auto max-w-3xl px-5 py-[clamp(3rem,6vw,5rem)] sm:px-8">
      <Seo title="Perguntas frequentes" description="Respostas às perguntas mais comuns sobre o ServiMatch." canonicalPath="/faq" />
      <Reveal>
        <p className="eyebrow text-signal-500">AJUDA</p>
        <h1 className="mt-3 text-h1 font-display font-bold text-foreground">Perguntas frequentes</h1>
      </Reveal>

      {FAQ_GROUPS.map((group) => (
        <section key={group.title} className="mt-10">
          <h2 className="text-h2 font-display font-bold text-foreground">{group.title}</h2>
          <dl className="mt-4 flex flex-col divide-y divide-line border-y border-line">
            {group.items.map((item) => {
              const key = `${group.title}-${item.q}`;
              const isOpen = openKey === key;
              return (
                <div key={key}>
                  <dt>
                    <button
                      type="button"
                      onClick={() => setOpenKey(isOpen ? undefined : key)}
                      aria-expanded={isOpen}
                      className="flex w-full items-center justify-between gap-4 py-5 text-left text-card-title font-display font-semibold text-foreground"
                    >
                      {item.q}
                      <ChevronDown
                        aria-hidden="true"
                        className={cn('size-5 shrink-0 text-muted transition-transform', isOpen && 'rotate-180')}
                        strokeWidth={1.5}
                      />
                    </button>
                  </dt>
                  {isOpen ? <dd className="pb-5 text-body text-muted">{item.a}</dd> : null}
                </div>
              );
            })}
          </dl>
        </section>
      ))}
    </div>
  );
}

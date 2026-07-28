import { BadgeCheck, MapPinned, MessageCircle, ShieldCheck, Star, Wallet } from 'lucide-react';
import { Marquee } from '../motion/Marquee';

const ITEMS = [
  { icon: ShieldCheck, label: 'Perfis de prestador aprovados manualmente' },
  { icon: BadgeCheck, label: 'Avaliações só de trabalhos concluídos' },
  { icon: MapPinned, label: 'Prestadores da sua zona' },
  { icon: Star, label: '4,8 ★ de avaliação média' },
  { icon: MessageCircle, label: 'Resposta média em menos de 4 horas' },
  { icon: Wallet, label: 'Grátis para clientes, sempre' },
];

/** Faixa compacta de prova social — sem sombra, `surface-2` (§6.2, ponto 2). */
export function TrustMarquee() {
  return (
    <div className="border-y border-line bg-surface-2 py-5">
      <Marquee>
        {ITEMS.map((item) => (
          <span key={item.label} className="flex items-center gap-2 text-caption font-medium text-muted">
            <item.icon aria-hidden="true" className="size-4 text-orange-500" strokeWidth={1.5} />
            {item.label}
          </span>
        ))}
      </Marquee>
    </div>
  );
}

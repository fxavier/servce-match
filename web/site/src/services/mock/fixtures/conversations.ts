import type { ConversationSummary } from '../../domainTypes';
import type { Message } from '../../types';

function minutesAgoIso(minutes: number): string {
  return new Date(Date.now() - minutes * 60 * 1000).toISOString();
}

export const CONVERSATIONS: ConversationSummary[] = [
  {
    id: 'cv-0001',
    counterpartName: 'Canalizações Silva & Filhos',
    counterpartAvatarSeed: 'Canalizações Silva & Filhos',
    lastMessagePreview: 'Perfeito, fico então à espera de vocês amanhã às 9h.',
    lastMessageAt: minutesAgoIso(12),
    unreadCount: 0,
    requestTitle: 'Fuga de água por baixo do lava-loiça',
  },
  {
    id: 'cv-0002',
    counterpartName: 'Mariana Costa',
    counterpartAvatarSeed: 'Mariana Costa',
    lastMessagePreview: 'Pode ser sexta-feira de manhã, sim.',
    lastMessageAt: minutesAgoIso(240),
    unreadCount: 2,
    requestTitle: 'Pintar T2 completo em Alvalade',
  },
];

const MESSAGES_CV1: Message[] = [
  { id: 'm-0001', conversationId: 'cv-0001', senderId: 'c-0001', body: 'Boa tarde. Vi que enviou proposta para a fuga na cozinha — pode vir ver ainda esta semana?', attachments: [], sentAt: minutesAgoIso(180), readAt: minutesAgoIso(178) },
  { id: 'm-0002', conversationId: 'cv-0001', senderId: 'p-0001', body: 'Boa tarde! Sim, consigo passar amanhã de manhã. Serve às 9h?', attachments: [], sentAt: minutesAgoIso(170), readAt: minutesAgoIso(168) },
  { id: 'm-0003', conversationId: 'cv-0001', senderId: 'c-0001', body: 'Serve perfeitamente. É um 3º andar com elevador, o código da porta é 1234.', attachments: [], sentAt: minutesAgoIso(160), readAt: minutesAgoIso(155) },
  { id: 'm-0004', conversationId: 'cv-0001', senderId: 'p-0001', body: 'Anotado. Vou levar já material para reparação de sifão, caso seja isso — evita uma segunda visita.', attachments: [], sentAt: minutesAgoIso(150), readAt: minutesAgoIso(140) },
  { id: 'm-0005', conversationId: 'cv-0001', senderId: 'c-0001', body: 'Muito obrigada pela previsão. Fico então à espera de vocês amanhã às 9h.', attachments: [], sentAt: minutesAgoIso(12), readAt: null },
];

const MESSAGES_CV2: Message[] = [
  { id: 'm-0101', conversationId: 'cv-0002', senderId: 'c-0001', body: 'Olá! Vi o seu perfil e gostava de perceber se conseguia começar já para a semana.', attachments: [], sentAt: minutesAgoIso(600), readAt: minutesAgoIso(590) },
  { id: 'm-0102', conversationId: 'cv-0002', senderId: 'p-0004', body: 'Boa tarde. Consigo sim — preciso só de confirmar a cor final da tinta consigo antes.', attachments: [], sentAt: minutesAgoIso(560), readAt: minutesAgoIso(555) },
  { id: 'm-0103', conversationId: 'cv-0002', senderId: 'c-0001', body: 'É branco Dulux Vivid White, já tenho os baldes comprados.', attachments: [], sentAt: minutesAgoIso(540), readAt: minutesAgoIso(530) },
  { id: 'm-0104', conversationId: 'cv-0002', senderId: 'p-0004', body: 'Perfeito, isso poupa-nos tempo. Consegue estar em casa numa sexta-feira de manhã para eu começar a preparação das paredes?', attachments: [], sentAt: minutesAgoIso(400), readAt: minutesAgoIso(390) },
  { id: 'm-0105', conversationId: 'cv-0002', senderId: 'c-0001', body: 'Pode ser sexta-feira de manhã, sim.', attachments: [], sentAt: minutesAgoIso(240), readAt: null },
  { id: 'm-0106', conversationId: 'cv-0002', senderId: 'p-0004', body: 'Combinado. Levo tudo o que preciso, só confirme a morada exata por favor.', attachments: [], sentAt: minutesAgoIso(238), readAt: null },
  { id: 'm-0107', conversationId: 'cv-0002', senderId: 'c-0001', body: 'Av. Rio de Janeiro 12, campainha "3º Dto".', attachments: [], sentAt: minutesAgoIso(235), readAt: null },
];

export const MESSAGES_BY_CONVERSATION: Record<string, Message[]> = {
  'cv-0001': MESSAGES_CV1,
  'cv-0002': MESSAGES_CV2,
};

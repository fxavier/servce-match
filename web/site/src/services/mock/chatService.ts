import type { ChatService } from '../interfaces';
import type { Message } from '../types';
import { CONVERSATIONS, MESSAGES_BY_CONVERSATION } from './fixtures/conversations';
import { mockCurrentUser } from './currentUser';
import { withLatency } from './latency';

const messagesByConversation: Record<string, Message[]> = structuredClone(MESSAGES_BY_CONVERSATION);

export const chatServiceMock: ChatService = {
  listConversations() {
    return withLatency(() => CONVERSATIONS);
  },
  listMessages(conversationId, params) {
    return withLatency(() => {
      const all = messagesByConversation[conversationId] ?? [];
      const limit = params?.limit ?? 50;
      const offset = params?.cursor ? Number(params.cursor) : 0;
      const page = all.slice(offset, offset + limit);
      const nextOffset = offset + limit;
      return { items: page, page: { nextCursor: nextOffset < all.length ? String(nextOffset) : null } };
    });
  },
  sendMessage(conversationId, body) {
    return withLatency(() => {
      const user = mockCurrentUser.get();
      const message: Message = {
        id: `m-${crypto.randomUUID().slice(0, 8)}`,
        conversationId,
        senderId: user?.id ?? 'c-0001',
        body: body.body,
        attachments: [],
        sentAt: new Date().toISOString(),
        readAt: null,
      };
      const existing = messagesByConversation[conversationId] ?? [];
      messagesByConversation[conversationId] = [...existing, message];

      // Resposta automática 2 s depois (§7 — mock de chat). Não bloqueia o
      // `sendMessage`; a UI reage a isto ao voltar a pedir a lista (polling
      // simples do `useQuery` do TanStack Query com `refetchInterval`).
      setTimeout(() => {
        const reply: Message = {
          id: `m-${crypto.randomUUID().slice(0, 8)}`,
          conversationId,
          senderId: conversationId === 'cv-0001' ? 'p-0001' : 'p-0004',
          body: 'Obrigado pela mensagem — respondo já a seguir.',
          attachments: [],
          sentAt: new Date().toISOString(),
          readAt: null,
        };
        messagesByConversation[conversationId] = [...(messagesByConversation[conversationId] ?? []), reply];
      }, 2000);

      return message;
    });
  },
};

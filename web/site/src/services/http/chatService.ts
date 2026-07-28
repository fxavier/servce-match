import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ChatService } from '../interfaces';
import { notImplementedInContract } from './notImplemented';

export const chatServiceHttp: ChatService = {
  listConversations() {
    // Gap #2 — sem GET /v1/conversations (lista) no contrato.
    return notImplementedInContract('lista de conversas');
  },
  async listMessages(conversationId, params) {
    const { data, error } = await api.GET('/v1/conversations/{conversationId}/messages', {
      params: { path: { conversationId }, query: { limit: params?.limit, cursor: params?.cursor } },
    });
    if (error) throwProblem(error);
    return data;
  },
  async sendMessage(conversationId, body) {
    const { data, error } = await api.POST('/v1/conversations/{conversationId}/messages', {
      params: { path: { conversationId } },
      body,
    });
    if (error) throwProblem(error);
    return data;
  },
};

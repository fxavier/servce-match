import { throwProblem } from '../../lib/problem';
import { api } from '../http';
import type { ChatService } from '../interfaces';

export const chatServiceHttp: ChatService = {
  async listConversations() {
    const { data, error } = await api.GET('/v1/conversations', { params: { query: { limit: 50 } } });
    if (error) throwProblem(error);
    return data.items;
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

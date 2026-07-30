import { categoriesServiceHttp } from './http/categoriesService';
import { chatServiceHttp } from './http/chatService';
import { providerDashboardServiceHttp } from './http/providerDashboardService';
import { providersServiceHttp } from './http/providersService';
import { proposalsServiceHttp } from './http/proposalsService';
import { requestsServiceHttp } from './http/requestsService';
import { reviewsServiceHttp } from './http/reviewsService';
import { subscriptionsServiceHttp } from './http/subscriptionsService';
import { uploadsServiceHttp } from './http/uploadsService';
import type { Services } from './interfaces';

/**
 * Único ponto de composição dos serviços (§8.4). Sempre a implementação
 * HTTP real (`openapi-fetch` contra o BFF) — os dados de desenvolvimento
 * vivem na base de dados (seed dev-only, ADR-0013) e chegam pelo backend
 * real, não por uma camada de mocks no cliente (ADR-0013 D7).
 */
export const services: Services = {
  categories: categoriesServiceHttp,
  providers: providersServiceHttp,
  requests: requestsServiceHttp,
  proposals: proposalsServiceHttp,
  chat: chatServiceHttp,
  reviews: reviewsServiceHttp,
  subscriptions: subscriptionsServiceHttp,
  uploads: uploadsServiceHttp,
  providerDashboard: providerDashboardServiceHttp,
};

export * from './interfaces';

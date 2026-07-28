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
import { categoriesServiceMock } from './mock/categoriesService';
import { chatServiceMock } from './mock/chatService';
import { providerDashboardServiceMock } from './mock/providerDashboardService';
import { providersServiceMock } from './mock/providersService';
import { proposalsServiceMock } from './mock/proposalsService';
import { requestsServiceMock } from './mock/requestsService';
import { reviewsServiceMock } from './mock/reviewsService';
import { subscriptionsServiceMock } from './mock/subscriptionsService';
import { uploadsServiceMock } from './mock/uploadsService';

/**
 * Único ponto de decisão mock ↔ HTTP real (§8.4). `VITE_USE_MOCKS=true` por
 * omissão em dev — nenhum componente faz `if (mock)`, todos importam
 * `services` daqui.
 */
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false';

export const services: Services = USE_MOCKS
  ? {
      categories: categoriesServiceMock,
      providers: providersServiceMock,
      requests: requestsServiceMock,
      proposals: proposalsServiceMock,
      chat: chatServiceMock,
      reviews: reviewsServiceMock,
      subscriptions: subscriptionsServiceMock,
      uploads: uploadsServiceMock,
      providerDashboard: providerDashboardServiceMock,
    }
  : {
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

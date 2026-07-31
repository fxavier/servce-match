/**
 * Interfaces de serviço — o único contrato que componentes/features
 * conhecem (§8.2). Cada área tem exatamente uma implementação
 * (`services/http/*`, sempre HTTP real contra o BFF); nenhum componente
 * importa `services/http/*` diretamente — todos passam por `services/index.ts`.
 */
import type { ProviderDashboardStats } from './domainTypes';
import type {
  BookingDetail,
  Category,
  ConversationSummary,
  CreateProposal,
  CreateReview,
  CreateServiceRequest,
  CreateSubscription,
  CreateUpload,
  Message,
  MessagePage,
  Proposal,
  ProposalPage,
  ProviderApproval,
  ProviderProfile,
  ProviderSummary,
  RequestStatus,
  Review,
  ReviewWithAuthor,
  ServiceRequest,
  ServiceRequestPage,
  Subscription,
  SubscriptionCheckout,
  SubscriptionPlan,
  UpdateProviderApproval,
  UpdateProviderProfile,
  UploadTarget,
} from './types';

export interface ListParams {
  limit?: number;
  cursor?: string;
}

export interface CategoriesService {
  list(params?: { parentId?: string }): Promise<Category[]>;
}

export interface ProviderSearchParams extends ListParams {
  categoryId?: string;
  lat?: number;
  lon?: number;
  regionCode?: string;
  q?: string;
  minRating?: number;
  verifiedOnly?: boolean;
  premiumOnly?: boolean;
  sort?: 'relevance' | 'rating' | 'distance';
}

export interface ProvidersService {
  /** `GET /v1/search/providers` — devolve `ProviderSummary`, sem `bio`/`location`/zonas (não fabricado no cliente). */
  search(params: ProviderSearchParams): Promise<{ items: ProviderSummary[]; page: { nextCursor: string | null } }>;
  /** `GET /v1/providers/{id}` — perfil público completo. */
  get(id: string): Promise<ProviderProfile>;
  featured(limit?: number): Promise<ProviderSummary[]>;
  /** `GET /v1/providers/me` — perfil editável do prestador autenticado. */
  getMine(): Promise<ProviderProfile>;
  /** `PUT /v1/providers/me` — substituição total (ver `UpdateProviderProfile`). */
  updateMine(body: UpdateProviderProfile): Promise<ProviderProfile>;
}

export interface RequestsService {
  listMine(params?: { status?: RequestStatus } & ListParams): Promise<ServiceRequestPage>;
  get(id: string): Promise<ServiceRequest>;
  create(body: CreateServiceRequest): Promise<ServiceRequest>;
  publish(id: string): Promise<ServiceRequest>;
  listProviderInbox(params?: { status?: RequestStatus } & ListParams): Promise<ServiceRequestPage>;
}

export interface ProposalsService {
  listForRequest(requestId: string, params?: ListParams): Promise<ProposalPage>;
  listMine(params?: ListParams): Promise<{ items: Proposal[]; page: { nextCursor: string | null } }>;
  create(requestId: string, body: CreateProposal): Promise<Proposal>;
  accept(proposalId: string): Promise<Proposal>;
}

export interface ChatService {
  listConversations(): Promise<ConversationSummary[]>;
  listMessages(conversationId: string, params?: ListParams): Promise<MessagePage>;
  sendMessage(conversationId: string, body: { body: string }): Promise<Message>;
}

export interface ReviewsService {
  listForProvider(providerId: string): Promise<ReviewWithAuthor[]>;
  create(body: CreateReview): Promise<Review>;
  getReviewableBooking(bookingId: string): Promise<BookingDetail>;
}

export interface SubscriptionsService {
  listPlans(): Promise<SubscriptionPlan[]>;
  current(): Promise<Subscription | undefined>;
  create(body: CreateSubscription): Promise<SubscriptionCheckout>;
}

export interface UploadsService {
  createUploadTarget(body: CreateUpload): Promise<UploadTarget>;
}

export interface ProviderDashboardService {
  stats(): Promise<ProviderDashboardStats>;
}

/**
 * Área `/admin` (CLAUDE.md, ARQUITETURA §4.1/§19.1). O contrato só define,
 * por agora, a decisão em si (`decideProviderApproval`,
 * `PATCH /v1/admin/providers/{providerId}/approval`) — **não existe**
 * nenhum `GET` para listar prestadores pendentes nem para ver o detalhe de
 * um que ainda não esteja visível publicamente. Isso é uma lacuna de
 * contrato (reportada ao `api-contract`, não contornada aqui): a UI que
 * consome este serviço decide por `providerId` conhecido, não por uma
 * lista fabricada no cliente.
 */
export interface AdminService {
  /**
   * `PATCH /v1/admin/providers/{providerId}/approval`. `idempotencyKey`
   * vai sempre preenchida — o mesmo valor entre um duplo clique não deve
   * produzir duas decisões (o servidor é a autoridade sobre a
   * idempotência; ver `platform/idempotency`).
   */
  decideProviderApproval(providerId: string, body: UpdateProviderApproval, idempotencyKey: string): Promise<ProviderApproval>;
}

export interface Services {
  categories: CategoriesService;
  providers: ProvidersService;
  requests: RequestsService;
  proposals: ProposalsService;
  chat: ChatService;
  reviews: ReviewsService;
  subscriptions: SubscriptionsService;
  uploads: UploadsService;
  providerDashboard: ProviderDashboardService;
  admin: AdminService;
}

/**
 * Interfaces de serviço — o único contrato que componentes/features
 * conhecem (§8.2). Cada área tem exatamente duas implementações
 * (`services/mock/*`, `services/http/*`); a seleção acontece uma única vez
 * em `services/index.ts`. Nenhum componente decide "mock ou http".
 */
import type {
  BookingSummary,
  ConversationSummary,
  ProviderDashboardStats,
  ProviderProfile,
  ReviewWithAuthor,
} from './domainTypes';
import type {
  Category,
  CreateProposal,
  CreateReview,
  CreateServiceRequest,
  CreateSubscription,
  CreateUpload,
  Message,
  MessagePage,
  Proposal,
  ProposalPage,
  RequestStatus,
  Review,
  ServiceRequest,
  ServiceRequestPage,
  Subscription,
  SubscriptionCheckout,
  SubscriptionPlan,
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
  search(params: ProviderSearchParams): Promise<{ items: ProviderProfile[]; page: { nextCursor: string | null } }>;
  get(id: string): Promise<ProviderProfile>;
  featured(limit?: number): Promise<ProviderProfile[]>;
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
  getReviewableBooking(bookingId: string): Promise<BookingSummary>;
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
}

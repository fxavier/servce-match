/**
 * Re-export dos tipos gerados a partir de `docs/api/openapi.yaml` (§8.1). O
 * ficheiro `api/generated/schema.d.ts` é gerado por `npm run generate:api`
 * (`openapi-typescript`) e nunca se edita à mão — se um campo está errado,
 * o contrato é que está errado; pede-se ao `api-contract`.
 *
 * Este módulo é o único ponto do resto da app que importa o schema gerado:
 * todos os `services/`, `features/` e `components/` importam modelos a
 * partir daqui, nunca diretamente de `api/generated/schema`.
 */
import type { components, operations } from '../api/generated/schema';

export type Schemas = components['schemas'];

export type ProblemDetails = Schemas['ProblemDetails'];
export type Platform = Schemas['Platform'];
export type VersionStatus = Schemas['VersionStatus'];
export type GeoPoint = Schemas['GeoPoint'];
export type Address = Schemas['Address'];
export type Category = Schemas['Category'];
export type UrgencyLevel = Schemas['UrgencyLevel'];
export type RequestStatus = Schemas['RequestStatus'];
export type ProposalStatus = Schemas['ProposalStatus'];
export type SubscriptionStatus = Schemas['SubscriptionStatus'];
export type Money = Schemas['Money'];
export type ImageRef = Schemas['ImageRef'];
export type UploadPurpose = Schemas['UploadPurpose'];
export type CreateUpload = Schemas['CreateUpload'];
export type UploadTarget = Schemas['UploadTarget'];
export type CreateServiceRequest = Schemas['CreateServiceRequest'];
export type ServiceRequest = Schemas['ServiceRequest'];
export type CreateProposal = Schemas['CreateProposal'];
export type Proposal = Schemas['Proposal'];
export type ProviderSummary = Schemas['ProviderSummary'];
export type CreateMessage = Schemas['CreateMessage'];
export type Message = Schemas['Message'];
export type Booking = Schemas['Booking'];
export type CreateReview = Schemas['CreateReview'];
export type Review = Schemas['Review'];
export type SubscriptionPlan = Schemas['SubscriptionPlan'];
export type CreateSubscription = Schemas['CreateSubscription'];
export type SubscriptionCheckout = Schemas['SubscriptionCheckout'];
export type Subscription = Schemas['Subscription'];
export type PageMeta = Schemas['PageMeta'];
export type ProposalPage = Schemas['ProposalPage'];
export type ServiceRequestPage = Schemas['ServiceRequestPage'];
export type ProviderSummaryPage = Schemas['ProviderSummaryPage'];
export type MessagePage = Schemas['MessagePage'];

export type ApiOperations = operations;

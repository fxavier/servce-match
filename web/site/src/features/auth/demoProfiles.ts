import type { SessionUser } from './types';

export type DemoProfileKey = 'customer' | 'provider-active' | 'provider-inactive';

export interface DemoProfile {
  key: DemoProfileKey;
  label: string;
  description: string;
  user: SessionUser;
  /** Só relevante para perfis de prestador — liga o `mockDb.providerSubscriptionActive` inicial. */
  subscriptionActive?: boolean;
}

/**
 * Três perfis de demonstração (§9) — os emails de cliente/prestador
 * coincidem de propósito com os utilizadores fixture reais do realm
 * Keycloak local (`infra/README.md`) para o mock e o backend real
 * contarem a mesma história. Não há um terceiro utilizador Keycloak
 * "prestador sem subscrição" — esse perfil só existe no mock, ligado ao
 * prestador `p-0009` (Banho & Obra) das fixtures.
 */
export const DEMO_PROFILES: DemoProfile[] = [
  {
    key: 'customer',
    label: 'Cliente',
    description: 'Publica pedidos, recebe e aceita orçamentos. Nunca paga na plataforma.',
    user: {
      id: 'c-0001',
      displayName: 'Mariana Costa',
      email: 'customer.test@servimatch.pt',
      roles: ['CUSTOMER'],
    },
  },
  {
    key: 'provider-active',
    label: 'Prestador — subscrição ativa',
    description: 'Canalizações Silva & Filhos — plano Professional ativo, vê a inbox completa.',
    user: {
      id: 'p-0001',
      displayName: 'Carlos Silva',
      email: 'provider.test@servimatch.pt',
      roles: ['PROVIDER'],
    },
    subscriptionActive: true,
  },
  {
    key: 'provider-inactive',
    label: 'Prestador — sem subscrição',
    description: 'Banho & Obra Remodelações — sem plano ativo, vê o painel de upsell.',
    user: {
      id: 'p-0009',
      displayName: 'Rita Nogueira',
      email: 'provider.trial@servimatch.pt',
      roles: ['PROVIDER'],
    },
    subscriptionActive: false,
  },
];

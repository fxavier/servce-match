/**
 * Sessão mock corrente — só existe para os serviços mock saberem "de quem"
 * são os pedidos/propostas em `listMine` (o equivalente ao `sub` do JWT que
 * o backend real leria). Nunca é usado para decidir autorização — isso é
 * sempre resposta do servidor (mock ou real); ver `AuthProvider` mock em
 * `features/auth`.
 */
export interface MockSessionUser {
  id: string;
  roles: ('CUSTOMER' | 'PROVIDER' | 'ADMIN')[];
}

let current: MockSessionUser | undefined;

export const mockCurrentUser = {
  get(): MockSessionUser | undefined {
    return current;
  },
  set(user: MockSessionUser | undefined) {
    current = user;
  },
};

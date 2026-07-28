/**
 * Texto editorial dos planos (bullets de destaque) — não é dado mock/HTTP
 * trocável, é copy estático indexado por `code` do plano (o nome/preço
 * vêm sempre do serviço real). Fica fora de `services/mock/` para não ser
 * confundido com fixture (checklist §11).
 */
export const PLAN_HIGHLIGHTS: Record<string, string[]> = {
  starter: ['Até 3 categorias', 'Até 2 zonas de atuação', 'Propostas ilimitadas', 'Suporte por email'],
  professional: ['Categorias ilimitadas', 'Zonas ilimitadas', 'Propostas ilimitadas', 'Suporte prioritário'],
  premium: ['Tudo do Professional', 'Destaque na pesquisa', 'Selo Premium no perfil', 'Prioridade no matching'],
};

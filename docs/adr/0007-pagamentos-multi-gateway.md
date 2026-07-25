# ADR-0007: Estratégia de pagamentos multi-gateway

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0002

## Contexto e Problema

A monetização assenta em **subscrições mensais dos prestadores**. O mercado-alvo (Portugal) usa fortemente **MB WAY** e **Multibanco**, mas a **recorrência automática** ("auto-renew") tem suporte muito diferente conforme o método e o fornecedor. É preciso uma estratégia de pagamentos que suporte recorrência com cartão e maximize a conversão local, sem acoplar o domínio a um gateway específico.

## Fatores de Decisão

- Suporte real a débito recorrente por método de pagamento.
- Conversão no mercado PT (métodos locais).
- Acoplamento do domínio ao gateway.
- Robustez do fluxo de webhooks (idempotência, reconciliação).

## Opções Consideradas

1. **Um único gateway internacional** (ex.: Stripe).
2. **Um único gateway local** (ex.: Eupago/IfthenPay).
3. **Multi-gateway atrás de uma abstração de domínio.**

## Decisão

Definir uma *port* de domínio **`PaymentGateway`** com adaptadores plugáveis. Referências de implementação:

- **Stripe Billing** — subscrição recorrente com **cartão** (auto-renew nativo, conduzido por webhooks).
- **Eupago / IfthenPay** — **MB WAY** e **Multibanco** para o mercado PT.
- **PayPal** — opcional.

**Recorrência por método:**

| Método | Auto-renew | Estratégia |
|---|---|---|
| Cartão (Stripe) | Nativo | Subscrição gerida pelo gateway; webhooks conduzem o estado |
| MB WAY | Parcial (onde suportado) | Confirmar por fornecedor; senão, *invoice-based* |
| Multibanco (referência) | Não | *Invoice-based*: nova referência por ciclo; sem pagamento até à data → `EXPIRED` |

**Requisitos de webhook (obrigatórios):** verificação de assinatura, **idempotência** (persistir `raw_event_id` com constraint única), processamento como evento de domínio (`PaymentSucceeded`/`PaymentFailed`), e **job de reconciliação** periódico (o gateway é a fonte de verdade do pagamento). A subscrição **nunca** é ativada por evento não verificado do cliente.

## Consequências

**Positivas**
- Auto-renew real via cartão com o menor esforço (Stripe).
- Cobertura dos métodos locais mais usados em PT.
- Domínio isolado do gateway; trocar/adicionar fornecedores é localizado.

**Negativas / Custos**
- Complexidade de suportar dois modelos (recorrente vs *invoice-based*).
- Necessidade de comunicar claramente ao prestador a renovação quando esta não é automática.
- Múltiplas integrações de webhook para manter e testar.

## Ligações

- Stripe Billing: https://docs.stripe.com/billing/subscriptions/overview
- Eupago: https://www.eupago.pt
- IfthenPay: https://www.ifthenpay.com

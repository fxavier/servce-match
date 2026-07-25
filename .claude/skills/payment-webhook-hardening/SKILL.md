---
name: payment-webhook-hardening
description: Como receber e processar webhooks de gateways de pagamento no ServiMatch de forma segura — verificação de assinatura, idempotência com garantia de base de dados, reentregas e desordem, reconciliação e especificidades de Multibanco e MB WAY. Usa ao integrar Stripe, Eupago, IfthenPay ou qualquer gateway.
---

# Webhooks de pagamento

Um webhook é um endpoint público que altera estado com impacto financeiro.
Trata-o com o cuidado correspondente.

## Sequência obrigatória

1. **Ler o corpo em bruto.** A assinatura é calculada sobre os bytes exatos; se
   o framework desserializar e re-serializar antes, a verificação falha ou, pior,
   passa a validar outra coisa.
2. **Verificar a assinatura** com o segredo do gateway, em comparação de tempo
   constante. Falha → `401`/`400` e fim. Nada chega ao domínio.
3. **Persistir o evento em bruto** com UNIQUE em `(gateway, raw_event_id)`.
   Violação da constraint → já processado → responde `200` e termina. A
   idempotência tem de estar na **base de dados**: verificação só em memória não
   sobrevive a duas instâncias a receber a mesma reentrega em simultâneo.
4. **Responder depressa** (`2xx`) e processar de forma assíncrona. Gateways têm
   timeouts curtos e reenviam o que consideram falhado; processar em linha
   transforma lentidão em duplicação.
5. **Aplicar ao domínio** com transição de estado explícita e validada.

## Reentregas e desordem

Todos os gateways reenviam. Alguns entregam fora de ordem (um `payment.failed`
depois de um `payment.succeeded` mais recente). Compara *timestamp*/versão do
evento com o estado atual e **ignora o que é mais antigo** em vez de o aplicar —
regredir uma subscrição ativa para falhada por causa de um evento atrasado é uma
falha visível para o cliente.

## Especificidades do mercado português

- **Multibanco**: a referência é de **uso único**. Não existe débito recorrente.
  A renovação é **por fatura**: gerar nova referência, notificar, aguardar
  confirmação. Modelar Multibanco como cartão é o erro estrutural mais provável
  deste módulo.
- **MB WAY**: confirmação é assíncrona e depende de ação do utilizador na app;
  há timeout e há rejeição. O estado `PENDING` tem de ser um estado real do
  domínio, com expiração.
- **Stripe (cartão)**: renovação automática nativa; o ciclo de vida vem do
  gateway e o domínio segue-o, não o duplica.

## Reconciliação

Webhooks perdem-se — por indisponibilidade, deploy, bug ou expiração de
tentativas do gateway. Um job periódico compara o estado local com o do gateway e
corrige divergências, registando cada correção.

Não é um extra: é a única garantia de que um cliente que pagou fica ativo mesmo
quando a entrega falhou. Sem ela, o sistema depende de um canal *best-effort*
para uma operação financeira.

## Segurança do endpoint

Público no contrato (`security: []`) porque autentica pela assinatura. Portanto:
limita o tamanho do corpo, rejeita cedo, aplica *rate limiting*, e nunca devolvas
detalhe de erro que ajude a sondar o formato esperado.

## Testes obrigatórios

Assinatura inválida rejeitada; evento duplicado sem efeito; eventos fora de
ordem; pagamento falhado → `PAST_DUE`; reconciliação corrige divergência
introduzida artificialmente; corpo malformado não gera 500.

## Referências

- Stripe webhooks: https://docs.stripe.com/webhooks
- Stripe Billing: https://docs.stripe.com/billing
- Eupago: https://eupago.readme.io/ ; IfthenPay: https://ifthenpay.com/docs/

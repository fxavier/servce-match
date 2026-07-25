# ADR-0006: Redis condicional (single vs multi-instância)

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0001

## Contexto e Problema

O backend é *stateless* para escalar horizontalmente. Vários mecanismos — *rate limiting*, cache, e *fan-out* de WebSocket (chat) — comportam-se de forma diferente em single-instância vs multi-instância. É preciso decidir quando introduzir Redis, evitando custo operacional prematuro.

## Fatores de Decisão

- Correção do *rate limiting* e do chat quando há mais de uma instância.
- Custo operacional de manter Redis (mais uma peça a operar/monitorizar).
- Simplicidade no MVP single-instância.

## Opções Consideradas

1. **Redis condicional:** em memória no processo quando há 1 instância; Redis quando ≥2.
2. **Redis sempre**, desde o início.
3. **Nunca Redis**, apenas estado em processo.

## Decisão

**Redis é opcional em single-instância e obrigatório em multi-instância.** Em single-instância:

- **Rate limiting (Bucket4j):** *buckets* em memória.
- **Cache:** cache local (Caffeine) onde aplicável.
- **WebSocket/STOMP:** *simple broker* em memória.

Ao escalar para ≥2 instâncias, introduzir Redis para:

- **Rate limiting distribuído** (Bucket4j + Redis) — sem isto, os limites são por-instância e contornáveis.
- **Cache partilhada** e invalidação coerente.
- **Relay/pub-sub** para *fan-out* de mensagens de chat entre nós (ou RabbitMQ como alternativa de relay).

## Consequências

**Positivas**
- MVP mais simples e barato de operar.
- Caminho claro e bem definido para escalar.

**Negativas / Custos**
- É preciso garantir que o código de *rate limiting*/cache/broker é abstraído para permitir a troca sem refactor doloroso.
- Rate limiting por-instância no MVP é uma limitação conhecida e aceite temporariamente.

## Ligações

- Bucket4j: https://bucket4j.com
- Spring Messaging / STOMP broker relay: https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html

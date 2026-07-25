---
name: backend-domain
description: Implementa os módulos de domínio do backend — users, providers, requests, proposals, bookings e reviews — como módulos Spring Modulith com máquinas de estado explícitas, eventos de domínio e testes. Usa-o para regras de negócio, agregados, transições de estado e os endpoints REST correspondentes do contrato OpenAPI.
tools: Read, Write, Edit, Glob, Grep, Bash
model: sonnet
---

Implementas o núcleo de negócio do ServiMatch como módulos Spring Modulith
isolados.

## Âmbito de escrita

- `backend/src/main/java/pt/servimatch/modules/users/**`
- `.../modules/providers/**`
- `.../modules/requests/**`
- `.../modules/proposals/**`
- `.../modules/bookings/**`
- `.../modules/reviews/**`
- Testes unitários e de módulo correspondentes

Não escreves em `platform/`, `config/`, `pom.xml` nem nas migrações. Precisas de
uma dependência? Pede ao `backend-platform`. Precisas de uma tabela ou índice?
Pede ao `db-migrations`.

## Regras de módulo (ADR-0001)

- Cada módulo expõe **API pública mínima** no pacote de topo; tudo o resto vive
  em subpacotes internos e é invisível para os outros módulos.
- Comunicação entre módulos: **eventos de domínio** com
  `@ApplicationModuleListener`, não chamadas diretas a serviços internos.
- Consumidores de eventos são **idempotentes** — a entrega é *at-least-once*.
- Transação: publica o evento dentro da transação que muda o estado; o registry
  garante a entrega. Não faças efeitos externos (email, push, pagamento) dentro
  da transação de escrita.
- Usa a skill `spring-modulith-module` para a estrutura concreta.

## Máquinas de estado (explícitas, não `if` espalhados)

- `ServiceRequest`: DRAFT → PUBLISHED → IN_NEGOTIATION → CONFIRMED → IN_PROGRESS
  → COMPLETED, com CANCELLED alcançável a partir dos estados não terminais.
- `Proposal`: SENT → ACCEPTED | REJECTED | CANCELLED | EXPIRED | SUPERSEDED.
- `Booking`: CONFIRMED → IN_PROGRESS → COMPLETED | CANCELLED | NO_SHOW.

Transição inválida devolve erro de domínio traduzido para RFC 9457 pelo handler
central — nunca uma exceção genérica de runtime. Cada máquina de estado tem um
teste que percorre o caminho feliz **e** rejeita pelo menos uma transição
ilegal por estado.

## Invariantes de negócio

- **Avaliação verificada**: só é possível criar uma review quando existe um
  `Booking` no estado `COMPLETED` entre aquele cliente e aquele prestador. Não
  existe review sem prestação concluída.
- **Gating por subscrição**: pesquisa, matching, envio de propostas e abertura de
  conversa exigem subscrição `ACTIVE`. É verificado **no servidor**, sempre. O
  cliente nunca é autoridade sobre o seu plano.
- **Identidade**: o `sub` do token Keycloak é a chave estável do registo `users`;
  provisionamento *just-in-time* no primeiro login válido. Nunca chaveies o
  utilizador por email — o email muda.
- **Aceitar uma proposta** confirma o pedido e invalida as concorrentes
  (`SUPERSEDED`) numa única transação. Corrida entre duas aceitações simultâneas
  tem de resultar em exatamente uma aceite — resolve com bloqueio otimista e
  testa esse cenário.

## Critérios de aceitação

- `ApplicationModules.verify()` passa: zero dependências para internals alheios.
- Cada endpoint implementado corresponde exatamente ao `openapi.yaml` (caminho,
  códigos, forma do corpo). Divergência resolve-se pelo contrato, não pelo código.
- Testes cobrem caminho principal, transição ilegal e violação de autorização.
- Nenhuma PII em logs.

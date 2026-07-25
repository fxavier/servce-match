---
name: flyway-postgis-migration
description: Convenções de migrações Flyway no ServiMatch — numeração com vários autores em paralelo, padrão expand/contract sem downtime, extensões PostGIS, índices concorrentes e integridade no schema. Usa sempre que criares ou alterares tabelas, colunas, índices ou constraints.
---

# Migrações Flyway

## Nomenclatura e numeração

`V<seq>__<descricao_snake_case>.sql` em
`backend/src/main/resources/db/migration/`.

Com vários agentes a pedir alterações em paralelo, colisões de número são
inevitáveis se cada um escolher o seu. Por isso o schema tem **um proprietário
único** (`db-migrations`), que atribui a sequência no momento em que escreve e
verifica colisões antes de fechar o trabalho.

**Uma migração aplicada é imutável.** Editá-la parte o checksum do Flyway em
todos os ambientes onde já correu. Corrige sempre com uma migração nova.

## Expand / contract

Alteração destrutiva nunca acontece de uma vez:

1. **Expand** — adicionar a estrutura nova (coluna nullable, tabela, índice). A
   aplicação antiga continua a funcionar.
2. **Migrar** — backfill em lotes; a aplicação nova escreve nos dois sítios.
3. **Contract** — só numa release posterior, quando nada lê a estrutura antiga:
   NOT NULL, remoção da coluna, remoção do código de compatibilidade.

Backfill de tabela grande faz-se em lotes com commit intermédio. Um `UPDATE` sem
`WHERE` numa tabela grande bloqueia escritas e o deploy fica refém dele.

## Índices

```sql
-- migração isolada, sem transação
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_x ON t (col);
```

`CONCURRENTLY` não corre dentro de transação — a migração tem de ser marcada
como não transacional. Se falhar, deixa o índice `INVALID`: verifica e recria.

Toda a FK usada em junções tem índice do lado que junta. Índice que nenhum plano
usa é custo puro em cada escrita — remove-o.

## Integridade no schema

Constraints não são redundância da validação da aplicação: são a única defesa
que sobrevive a concorrência, a bugs de domínio e a scripts manuais.

Obrigatórios neste projeto:
- `users.keycloak_sub` UNIQUE.
- `device_token.token` UNIQUE; índice em `user_id`.
- Evento de pagamento: UNIQUE `(gateway, raw_event_id)` — é isto que torna os
  webhooks idempotentes; sem esta constraint a idempotência em código não
  resiste a duas instâncias em paralelo.
- CHECK de coerência em `provider_service_area` conforme `mode`.
- Dinheiro: `*_amount_cents BIGINT NOT NULL` + `currency CHAR(3) NOT NULL`.

## Extensões

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

Numa migração inicial e apenas aí. Requer imagem com PostGIS disponível — nos
testes, a imagem de Testcontainers tem de ser a mesma família da de produção.

## Verificação

- As migrações correm de raiz num ambiente limpo em cada execução de CI. Se só
  funcionam sobre uma base existente, não são migrações — são scripts manuais.
- Toda a migração destrutiva exige ADR e plano de reversão documentado.

## Referências

- Flyway: https://documentation.red-gate.com/fd
- `CREATE INDEX CONCURRENTLY`: https://www.postgresql.org/docs/current/sql-createindex.html

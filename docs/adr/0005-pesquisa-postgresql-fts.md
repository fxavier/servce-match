# ADR-0005: Pesquisa com PostgreSQL Full-Text Search primeiro

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0004

## Contexto e Problema

A plataforma precisa de pesquisa de prestadores, categorias e pedidos com filtros. É preciso decidir a tecnologia de pesquisa para o MVP sem operar prematuramente um motor de pesquisa dedicado.

## Fatores de Decisão

- Requisitos de relevância no arranque (moderados).
- Custo operacional de um segundo *datastore* (sincronização, indexação, monitorização).
- Volume esperado no MVP.
- Caminho de evolução para relevância avançada.

## Opções Consideradas

1. **PostgreSQL Full-Text Search** (`tsvector` + índice GIN; `pg_trgm` para *fuzzy*/typo-tolerance).
2. **OpenSearch/Elasticsearch** desde o início.

## Decisão

Usar **PostgreSQL FTS** no MVP: colunas `tsvector` com índice **GIN** sobre título/descrição de pedidos e nomes de categorias; **`pg_trgm`** para correspondência aproximada. Migrar para **OpenSearch/Elasticsearch** apenas quando os requisitos de relevância, facetas ou escala o justificarem.

## Consequências

**Positivas**
- Zero infraestrutura adicional; consistência transacional com o resto dos dados.
- Suficiente para pesquisa por palavras-chave e filtros no MVP.

**Negativas / Custos**
- Relevância e *ranking* menos sofisticados que um motor dedicado.
- Facetas e agregações complexas são mais limitadas.

## Gatilhos de reavaliação (quando migrar)

- Necessidade de *ranking* aprendido, sinónimos avançados, ou pesquisa multilíngue rica.
- Latência de pesquisa a degradar sob carga apesar de indexação adequada.
- Requisitos de *analytics*/agregações que sobrecarregam o PostgreSQL.

## Ligações

- PostgreSQL Full Text Search: https://www.postgresql.org/docs/current/textsearch.html
- pg_trgm: https://www.postgresql.org/docs/current/pgtrgm.html

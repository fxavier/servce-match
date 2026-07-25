-- Event Publication Registry do Spring Modulith (ADR-0001): garante entrega
-- at-least-once de eventos entre módulos. `application.yml` desliga a
-- auto-inicialização deste esquema (spring.modulith.events.jdbc.schema-
-- initialization.enabled=false, ARQUITETURA §5.6) — é o Flyway, não o
-- JdbcEventPublicationRepository, que cria esta tabela.
--
-- DDL extraído sem alterações do recurso oficial do dialeto PostgreSQL
-- empacotado no JAR (fonte de verdade, não reescrito à mão):
--   spring-modulith-events-jdbc:1.4.12!/schema-postgresql.sql
--
-- Não inclui `event_publication_archive`: essa tabela só é necessária com
-- spring.modulith.events.jdbc.completion-mode=ARCHIVE (confirmado por
-- decompilação de DatabaseSchemaLocator#getSchemaResource, que só a
-- adiciona quando JdbcRepositorySettings#isArchiveCompletion() é
-- verdadeiro). Este projeto usa completion-mode=UPDATE (application.yml) —
-- as publicações completas são atualizadas in-place, não movidas. Se um
-- agente mudar completion-mode para ARCHIVE, pede uma migração nova a este
-- agente para acrescentar `event_publication_archive` (não a
-- pré-criar sem uso: aditivo primeiro, sem esquema morto).
CREATE TABLE IF NOT EXISTS event_publication
(
  id               UUID NOT NULL,
  listener_id      TEXT NOT NULL,
  event_type       TEXT NOT NULL,
  serialized_event TEXT NOT NULL,
  publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date  TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);

-- Índices tal como definidos no schema-postgresql.sql oficial: servem
-- exatamente as duas queries que o JdbcEventPublicationRepository executa
-- (confirmado por decompilação da classe):
--   1. hash(serialized_event) — usado em
--      "WHERE serialized_event = ? AND listener_id = ? AND completion_date
--      IS NULL" (markCompleted / findIncompletePublicationsByEventAndTargetIdentifier).
--   2. btree(completion_date) — usado em
--      "WHERE completion_date IS NULL ORDER BY publication_date ASC"
--      (a query que falhou no arranque) e em
--      "WHERE completion_date IS NOT NULL" / "completion_date < ?"
--      (findCompletedPublications, housekeeping/purge).
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Bloco A: dois campos de ConversationSummary sem coluna/estratégia que os
-- sirva — docs/api/openapi.yaml.
--
-- 1) unreadCount — exige saber o que cada participante já leu.
--    `conversation` (V9) é bilateral **por construção**: exatamente
--    `customer_id` e `provider_id`, ambos NOT NULL, nunca uma terceira
--    parte. Nessas condições uma marca de água por participante
--    (`last_read_by_customer_at` / `last_read_by_provider_at`, dois
--    campos nesta própria tabela) é preferível a uma tabela de
--    participantes: uma tabela de participantes resolveria N participantes
--    variável, que não é o caso aqui, e obrigaria sempre a um JOIN extra
--    (ou dois) só para responder "o que é que eu já li" — o caminho mais
--    quente de todos em listConversations. `unreadCount` fica então:
--    `COUNT(message) WHERE conversation_id = ? AND sender_id <> :me
--     AND sent_at > COALESCE(last_read_by_<eu>_at, '-infinity')`,
--    servido pelo índice já existente `idx_message_conversation_id_sent_at`
--    (conversation_id, sent_at DESC) — sem necessidade de índice novo.
--    Se este agregado por linha vier a pesar em listConversations (N
--    conversas × 1 count cada), a evolução natural é denormalizar também
--    `unread_count_for_customer`/`unread_count_for_provider` a par de
--    last_message_at (ver trigger abaixo) — não faz parte desta migração
--    por não haver, ainda, evidência de que o custo o justifique.
-- 2) lastMessageAt / lastMessagePreview — "conversas mais recentes
--    primeiro" sobre MAX(sent_at) por conversa não é indexável (é uma
--    agregação sobre outra tabela). Denormaliza-se para a própria
--    conversation, mantido por trigger em cada INSERT de message, com
--    backfill imediato para as linhas que já existam.
ALTER TABLE conversation
    ADD COLUMN last_message_at              TIMESTAMPTZ,
    ADD COLUMN last_message_preview         VARCHAR(280),
    ADD COLUMN last_read_by_customer_at     TIMESTAMPTZ,
    ADD COLUMN last_read_by_provider_at     TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION update_conversation_last_message()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE conversation
       SET last_message_at = NEW.sent_at,
           last_message_preview = left(NEW.body, 280)
     WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_message_update_conversation_last_message
    AFTER INSERT ON message
    FOR EACH ROW EXECUTE FUNCTION update_conversation_last_message();

-- Backfill: base recém-migrada não tem mensagens ainda, mas a migração tem
-- de ser correta e re-executável (idempotente por natureza, um UPDATE) em
-- qualquer ambiente onde já existam linhas de "message" anteriores a este
-- ficheiro — não é o nosso caso hoje, mas é o que a skill
-- flyway-postgis-migration exige de qualquer expand/backfill.
UPDATE conversation c
   SET last_message_at = last_msg.sent_at,
       last_message_preview = left(last_msg.body, 280)
  FROM (
      SELECT DISTINCT ON (conversation_id) conversation_id, sent_at, body
        FROM message
       ORDER BY conversation_id, sent_at DESC, id DESC
  ) AS last_msg
 WHERE c.id = last_msg.conversation_id;

-- listConversations é "mais recente primeiro" por atividade
-- (COALESCE(last_message_at, created_at) — uma conversa sem mensagens ainda
-- ordena pela sua própria criação). Os índices simples da V9
-- (idx_conversation_customer_id / idx_conversation_provider_id) são
-- prefixo estrito destes: substituídos, porque um índice que só serve a
-- igualdade obrigava a ordenar em memória, e o novo serve os dois na mesma
-- pesquisa. A expressão COALESCE sobre duas colunas timestamptz é IMMUTABLE
-- (determinística e sem acesso a estado externo), condição exigida para
-- indexar uma expressão.
DROP INDEX idx_conversation_customer_id;
DROP INDEX idx_conversation_provider_id;
CREATE INDEX idx_conversation_customer_id_activity
    ON conversation (customer_id, (COALESCE(last_message_at, created_at)) DESC, id DESC);
CREATE INDEX idx_conversation_provider_id_activity
    ON conversation (provider_id, (COALESCE(last_message_at, created_at)) DESC, id DESC);

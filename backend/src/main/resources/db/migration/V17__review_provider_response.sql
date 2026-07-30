-- Bloco A (ADR-0013 D1 preparatório): a resposta do prestador a uma
-- avaliação está prevista no contrato (`ReviewWithAuthor.providerResponse`,
-- docs/api/openapi.yaml) mas `review` (V10) não tem coluna para a guardar —
-- o próprio contrato documenta isto no `description` do campo, prometendo
-- que fica `null` "até essa coluna ser acrescentada pelo db-migrations".
--
-- provider_response_at é obrigatório a par de provider_response: uma
-- resposta sem instante não é ordenável ("mais recente primeiro" nunca é
-- pedido para respostas, mas auditoria e um eventual backfill de
-- notificação precisam de saber quando foi escrita) nem recuperável por
-- reprocessamento em lote. CHECK garante que os dois andam sempre juntos —
-- não há forma de gravar um sem o outro passando pela aplicação.
ALTER TABLE review
    ADD COLUMN provider_response    VARCHAR(2000),
    ADD COLUMN provider_response_at TIMESTAMPTZ;

ALTER TABLE review
    ADD CONSTRAINT chk_review_provider_response_coherence CHECK (
        (provider_response IS NULL AND provider_response_at IS NULL)
        OR (provider_response IS NOT NULL AND provider_response_at IS NOT NULL)
    );

-- GET /v1/providers/{providerId}/reviews (listProviderReviews) é paginado
-- por cursor, "mais recentes primeiro" (ReviewWithAuthorPage). O índice da
-- V10 (idx_review_target_id) só cobre a igualdade por prestador — a
-- ordenação por created_at ficava a cargo de um sort em memória, cujo custo
-- cresce com o histórico de avaliações do prestador, exatamente o que o
-- cursor existe para evitar. Substituído (estrito prefixo do novo) por um
-- índice composto que serve a igualdade e a ordenação/paginação na mesma
-- pesquisa por índice: WHERE target_id = ? ORDER BY created_at DESC, id DESC.
DROP INDEX idx_review_target_id;
CREATE INDEX idx_review_target_id_created_at_id ON review (target_id, created_at DESC, id DESC);

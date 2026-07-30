-- Bloco A: dois caminhos de leitura por dono, paginados por cursor "mais
-- recente primeiro" (docs/api/openapi.yaml), cujos índices existentes só
-- cobrem a igualdade e obrigam a ordenar em memória — o índice composto
-- (dono, created_at DESC, id DESC) resolve igualdade e ordenação na mesma
-- pesquisa por índice, que é exatamente o que a paginação por cursor exige
-- para não degradar com o histórico.
--
-- GET /v1/requests (listMyRequests): "pedidos do cliente autenticado, mais
-- recentes primeiro". idx_service_request_customer_id (V7) é estrito
-- prefixo do novo índice — substituído.
DROP INDEX idx_service_request_customer_id;
CREATE INDEX idx_service_request_customer_id_created_at_id
    ON service_request (customer_id, created_at DESC, id DESC);

-- GET /v1/proposals/me (listMyProposals): "propostas enviadas pelo
-- prestador autenticado, mais recentes primeiro". idx_proposal_provider_id_status
-- (V8, provider_id, status) não é prefixo deste índice — segunda coluna
-- diferente — e mantém-se: serve consultas que filtram por estado sem
-- precisarem de ordenação por data (ex. elegibilidade/matching). O novo
-- índice é adicional, não substituto.
CREATE INDEX idx_proposal_provider_id_created_at_id
    ON proposal (provider_id, created_at DESC, id DESC);

-- GET /v1/requests/{requestId}/proposals (listRequestProposals): "propostas
-- recebidas para o pedido", paginado. idx_proposal_request_id (V8) é
-- estrito prefixo do novo índice — substituído.
DROP INDEX idx_proposal_request_id;
CREATE INDEX idx_proposal_request_id_created_at_id
    ON proposal (request_id, created_at DESC, id DESC);

-- Agregado "proposals" (ARQUITETURA §4.4, §9.2).
CREATE TABLE proposal (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    provider_id         UUID NOT NULL REFERENCES provider_profile (id) ON DELETE CASCADE,
    -- Dinheiro: inteiro na menor unidade + ISO-4217, nunca float (CLAUDE.md §5).
    price_amount_cents  BIGINT NOT NULL CHECK (price_amount_cents >= 0),
    price_currency      CHAR(3) NOT NULL DEFAULT 'EUR',
    description         VARCHAR(2000) NOT NULL,
    lead_time_days      INTEGER CHECK (lead_time_days IS NULL OR lead_time_days >= 0),
    valid_until         TIMESTAMPTZ,
    -- Máquina de estados §4.4.
    status              VARCHAR(20) NOT NULL DEFAULT 'SENT'
                            CHECK (status IN ('SENT', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'SUPERSEDED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- No máximo uma proposta ativa (SENT) por prestador e por pedido (§4.4):
-- reenviar substitui a anterior, não soma. Índice único parcial em vez de
-- UNIQUE(request_id, provider_id) porque o histórico de propostas
-- terminais (REJECTED/EXPIRED/...) do mesmo par tem de poder coexistir.
CREATE UNIQUE INDEX uq_proposal_request_provider_active
    ON proposal (request_id, provider_id)
    WHERE status = 'SENT';

CREATE INDEX idx_proposal_request_id ON proposal (request_id);
-- Caixa de entrada do prestador filtrada por estado (§11.2 listProviderInbox).
CREATE INDEX idx_proposal_provider_id_status ON proposal (provider_id, status);

CREATE TRIGGER trg_proposal_set_updated_at
    BEFORE UPDATE ON proposal
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

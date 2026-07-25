-- Agregado "payments" (ADR-0007, CLAUDE.md §4).
--
-- Eventos de webhook em bruto: base da idempotência. UNIQUE(gateway,
-- raw_event_id) é o que garante que dois webhooks entregues em duplicado (ou
-- processados por duas instâncias em paralelo) só produzem um efeito.
-- Nenhuma subscrição é ativada a partir de um evento não persistido aqui.
CREATE TABLE payment_gateway_event (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway              VARCHAR(20) NOT NULL CHECK (gateway IN ('stripe', 'eupago', 'ifthenpay', 'paypal')),
    raw_event_id         VARCHAR(200) NOT NULL,
    event_type           VARCHAR(100),
    payload              JSONB NOT NULL,
    -- Verificação de assinatura do gateway antes de processar (§12.3);
    -- persistida para auditoria/reconciliação mesmo quando falha.
    signature_verified   BOOLEAN NOT NULL DEFAULT false,
    processing_status    VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                             CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')),
    error_detail         TEXT,
    received_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at         TIMESTAMPTZ
);

-- Idempotência dos webhooks (ADR-0007, skill flyway-postgis-migration):
-- constraint obrigatória, não apenas verificação em código de aplicação.
CREATE UNIQUE INDEX uq_payment_gateway_event_gateway_raw_event_id
    ON payment_gateway_event (gateway, raw_event_id);
-- Job de reconciliação (§12.3): varre eventos ainda não processados.
CREATE INDEX idx_payment_gateway_event_processing_status ON payment_gateway_event (processing_status);

CREATE TABLE payment (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id      UUID REFERENCES subscription (id) ON DELETE SET NULL,
    provider_id          UUID NOT NULL REFERENCES provider_profile (id) ON DELETE RESTRICT,
    gateway_event_id     UUID REFERENCES payment_gateway_event (id) ON DELETE SET NULL,
    amount_cents         BIGINT NOT NULL CHECK (amount_cents >= 0),
    currency             CHAR(3) NOT NULL DEFAULT 'EUR',
    gateway              VARCHAR(20) NOT NULL CHECK (gateway IN ('stripe', 'eupago', 'ifthenpay', 'paypal')),
    gateway_payment_id   VARCHAR(200) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Identificador do pagamento no gateway é único por gateway (ARQUITETURA §9.4).
CREATE UNIQUE INDEX uq_payment_gateway_payment_id ON payment (gateway, gateway_payment_id);
CREATE INDEX idx_payment_subscription_id ON payment (subscription_id);
CREATE INDEX idx_payment_provider_id ON payment (provider_id);
CREATE INDEX idx_payment_gateway_event_id ON payment (gateway_event_id);

CREATE TRIGGER trg_payment_set_updated_at
    BEFORE UPDATE ON payment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

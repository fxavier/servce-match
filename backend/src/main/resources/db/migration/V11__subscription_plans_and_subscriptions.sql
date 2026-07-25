-- Agregado "subscriptions" (ARQUITETURA §3.4, §12.1, §9.2).
CREATE TABLE subscription_plan (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(60) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    price_amount_cents  BIGINT NOT NULL CHECK (price_amount_cents >= 0),
    price_currency      CHAR(3) NOT NULL DEFAULT 'EUR',
    billing_interval    VARCHAR(10) NOT NULL DEFAULT 'MONTHLY' CHECK (billing_interval IN ('MONTHLY')),
    -- Limites como dados do plano, não constantes no código (§3.4). NULL =
    -- ilimitado (cf. maxCategories/maxAreas no contrato).
    max_categories      INTEGER CHECK (max_categories IS NULL OR max_categories > 0),
    max_areas           INTEGER CHECK (max_areas IS NULL OR max_areas > 0),
    ranking_boost       INTEGER NOT NULL DEFAULT 0,
    has_badge           BOOLEAN NOT NULL DEFAULT false,
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_subscription_plan_code ON subscription_plan (code);

CREATE TRIGGER trg_subscription_plan_set_updated_at
    BEFORE UPDATE ON subscription_plan
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE subscription (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id               UUID NOT NULL REFERENCES provider_profile (id) ON DELETE RESTRICT,
    plan_id                   UUID NOT NULL REFERENCES subscription_plan (id) ON DELETE RESTRICT,
    -- Ciclo de vida §12.1.
    status                    VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'ACTIVE', 'PAST_DUE', 'EXPIRED', 'CANCELLED')),
    gateway                   VARCHAR(20) NOT NULL CHECK (gateway IN ('stripe', 'eupago', 'ifthenpay', 'paypal')),
    gateway_customer_id       VARCHAR(200),
    gateway_subscription_id   VARCHAR(200),
    current_period_start      TIMESTAMPTZ,
    current_period_end        TIMESTAMPTZ,
    cancel_at_period_end      BOOLEAN NOT NULL DEFAULT false,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Só uma subscrição em estado não-terminal por prestador de cada vez — evita
-- duas ACTIVE/PENDING/PAST_DUE em simultâneo (condição de corrida em
-- checkout duplicado).
CREATE UNIQUE INDEX uq_subscription_provider_non_terminal
    ON subscription (provider_id)
    WHERE status IN ('PENDING', 'ACTIVE', 'PAST_DUE');

CREATE INDEX idx_subscription_provider_id ON subscription (provider_id);
-- Job de expiração (§12.1): varre current_period_end < now() para casos sem
-- webhook (ARQUITETURA §9.4 índice crítico).
CREATE INDEX idx_subscription_status_period_end ON subscription (status, current_period_end);

CREATE TRIGGER trg_subscription_set_updated_at
    BEFORE UPDATE ON subscription
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

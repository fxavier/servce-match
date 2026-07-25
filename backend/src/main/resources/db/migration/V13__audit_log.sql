-- AuditLog (ARQUITETURA §9.1, §8.6): log estruturado e imutável de ações
-- sensíveis (alterações de subscrição, moderação, acessos admin). actor_id
-- pode ser nulo para ações despoletadas pelo sistema (jobs, webhooks).
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id        UUID REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(120) NOT NULL,
    target_type     VARCHAR(80) NOT NULL,
    target_id       UUID,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- Correlação por correlation_id, nunca por email/PII (CLAUDE.md §4).
    correlation_id  VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_actor_id ON audit_log (actor_id);
CREATE INDEX idx_audit_log_correlation_id ON audit_log (correlation_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);

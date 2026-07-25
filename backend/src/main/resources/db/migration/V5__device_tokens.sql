-- DeviceToken — multi-dispositivo por utilizador para push (FCM/APNs).
-- Entra no MVP web mesmo antes da app mobile existir (ADR-0008); retroajustar
-- sai caro. Suporta POST/DELETE /v1/device-tokens do contrato.
CREATE TABLE device_token (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token         VARCHAR(512) NOT NULL,
    platform      VARCHAR(10) NOT NULL CHECK (platform IN ('IOS', 'ANDROID', 'WEB')),
    app_version   VARCHAR(30),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- registerDeviceToken é idempotente por token (contrato): upsert na aplicação
-- depende desta unicidade.
CREATE UNIQUE INDEX uq_device_token_token ON device_token (token);
CREATE INDEX idx_device_token_user_id ON device_token (user_id);

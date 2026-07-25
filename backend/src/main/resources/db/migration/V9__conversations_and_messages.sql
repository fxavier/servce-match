-- Agregado "chat" (ARQUITETURA §9.2, §11.3, §13).
CREATE TABLE conversation (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id   UUID NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    customer_id  UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    provider_id  UUID NOT NULL REFERENCES provider_profile (id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Uma conversa por par (pedido, prestador) — evita duplicar tópicos quando o
-- mesmo prestador reenvia proposta ao mesmo pedido.
CREATE UNIQUE INDEX uq_conversation_request_provider ON conversation (request_id, provider_id);
CREATE INDEX idx_conversation_customer_id ON conversation (customer_id);
CREATE INDEX idx_conversation_provider_id ON conversation (provider_id);

CREATE TABLE message (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    sender_id        UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    body             VARCHAR(4000) NOT NULL,
    sent_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at          TIMESTAMPTZ
);

-- listMessages devolve ordem cronológica inversa paginada por cursor.
CREATE INDEX idx_message_conversation_id_sent_at ON message (conversation_id, sent_at DESC);
CREATE INDEX idx_message_sender_id ON message (sender_id);

CREATE TABLE message_attachment (
    message_id      UUID NOT NULL REFERENCES message (id) ON DELETE CASCADE,
    image_asset_id  UUID NOT NULL REFERENCES upload_asset (id) ON DELETE RESTRICT,
    position         INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (message_id, image_asset_id)
);

CREATE INDEX idx_message_attachment_image_asset_id ON message_attachment (image_asset_id);

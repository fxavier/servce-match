-- Suporte a POST /v1/uploads (createUpload): URL pré-assinado + imageId a
-- referenciar depois em imageIds/attachmentIds. O ficheiro nunca atravessa o
-- backend (ARQUITETURA §11.2); esta tabela é o registo do "contrato de
-- upload" — pedido, ainda não confirmado, confirmado após verificação por
-- magic bytes, ou expirado se nunca referenciado.
CREATE TABLE upload_asset (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose          VARCHAR(30) NOT NULL CHECK (purpose IN (
                         'REQUEST_ATTACHMENT',
                         'MESSAGE_ATTACHMENT',
                         'PROVIDER_PORTFOLIO',
                         'PROVIDER_DOCUMENT',
                         'AVATAR'
                     )),
    -- Chave de armazenamento gerada pelo servidor (nunca a partir do
    -- fileName do cliente) — impede path traversal (CLAUDE.md §4).
    object_key       VARCHAR(500) NOT NULL,
    -- Tipo declarado no momento do pedido de upload; parte da assinatura do
    -- URL. A validação real por magic bytes acontece na confirmação e é
    -- responsabilidade da aplicação, não do schema.
    content_type     VARCHAR(100) NOT NULL CHECK (content_type IN (
                         'image/jpeg', 'image/png', 'image/webp', 'application/pdf'
                     )),
    content_length   BIGINT NOT NULL CHECK (content_length > 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED')),
    confirmed_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_upload_asset_object_key ON upload_asset (object_key);
CREATE INDEX idx_upload_asset_owner_user_id ON upload_asset (owner_user_id);
-- Job de recolha de imageId nunca referenciado (§11.2): varre PENDING com
-- expires_at vencido.
CREATE INDEX idx_upload_asset_status_expires_at ON upload_asset (status, expires_at);

CREATE TRIGGER trg_upload_asset_set_updated_at
    BEFORE UPDATE ON upload_asset
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

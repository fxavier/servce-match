-- Bloco A: portefólio do prestador. `ProviderProfile.portfolioImageUrls`
-- (leitura, ImageRef[] resolvidas com URL assinada) e
-- `UpdateProviderProfile.portfolioImageIds` (escrita, substituição total —
-- docs/api/openapi.yaml) não têm tabela nenhuma a servi-los hoje.
--
-- Guarda-se a referência ao `upload_asset` (purpose='PROVIDER_PORTFOLIO'),
-- nunca um URL em texto: um URL de S3/MinIO cru quebra o padrão de URL
-- assinado com expiração exigido por CLAUDE.md §4 — o URL correto de servir
-- é sempre resolvido no momento da leitura a partir do object_key.
CREATE TABLE provider_portfolio (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id     UUID NOT NULL REFERENCES provider_profile (id) ON DELETE CASCADE,
    image_asset_id  UUID NOT NULL REFERENCES upload_asset (id) ON DELETE RESTRICT,
    -- PUT /v1/providers/me é substituição total da lista (posição = ordem no
    -- array enviado pelo cliente); sem posição a ordem de exibição fica
    -- indefinida (dependente da ordem física das linhas).
    position        INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A mesma imagem carregada não pode aparecer duas vezes no portefólio do
-- mesmo prestador — o corpo de UpdateProviderProfile é uma lista de ids,
-- duplicados nela seriam um erro de cliente sem sinal nenhum sem esta
-- constraint.
CREATE UNIQUE INDEX uq_provider_portfolio_provider_image ON provider_portfolio (provider_id, image_asset_id);
-- Caminho de leitura: montar portfolioImageUrls pela ordem declarada
-- (GET /v1/providers/{id} e /me) — igualdade por provider_id + ordenação por
-- position na mesma pesquisa por índice.
CREATE INDEX idx_provider_portfolio_provider_id_position ON provider_portfolio (provider_id, position);
-- FK do lado que junta a upload_asset (job de expiração/limpeza de
-- PENDING não referenciados também teria de saber se uma imagem CONFIRMED
-- está em uso num portefólio antes de a poder considerar órfã no futuro).
CREATE INDEX idx_provider_portfolio_image_asset_id ON provider_portfolio (image_asset_id);

-- Bloco A: dois campos do contrato sem coluna que os sirva em
-- provider_profile (V4).
--
-- 1) ProviderProfile.location (GeoPoint, obrigatório) — a página pública do
--    prestador devolve um único ponto de referência. Isto não é o mesmo
--    dado que provider_service_area.center: a cobertura pode ser inteira em
--    modo ADMIN_REGION (sem nenhum center preenchido), e mesmo em RADIUS um
--    prestador pode ter várias áreas. location é o ponto de exibição da
--    ficha pública (ex. sede/zona principal), independente de quantas áreas
--    de cobertura existam ou do seu modo. Nullable: um perfil recém-criado
--    pode não o ter definido ainda (o mesmo padrão de service_request.location
--    antes do geocoding, V7). Preenchimento é decisão de aplicação
--    (backend-domain), fora do âmbito desta migração.
-- 2) ProviderSummary.avatarUrl (nullable) — não existe hoje forma de um
--    prestador associar uma imagem de avatar; upload_asset já suporta
--    purpose='AVATAR' (V6) mas nada refere a esse upload. Guarda-se a
--    referência ao asset, nunca um URL cru (mesma razão do portefólio,
--    CLAUDE.md §4): o URL assinado é resolvido na leitura.
ALTER TABLE provider_profile
    ADD COLUMN location         geography(Point, 4326),
    ADD COLUMN avatar_asset_id  UUID REFERENCES upload_asset (id) ON DELETE SET NULL;

-- FK do lado que junta.
CREATE INDEX idx_provider_profile_avatar_asset_id ON provider_profile (avatar_asset_id);

-- Sem índice GiST sobre location: ao contrário de
-- provider_service_area.center (consultado por ST_DWithin no predicado de
-- matching/pesquisa, ADR-0004 §10.3), não existe hoje nenhum caminho de
-- leitura que filtre ou ordene provider_profile por proximidade a este
-- ponto — é só exibido. Um índice sem consulta que o use é custo puro em
-- cada escrita (skill flyway-postgis-migration). Se um caminho de "prestadores
-- perto de mim" vier a usar esta coluna diretamente (hoje usa
-- provider_service_area), o índice entra nessa migração, a par da consulta
-- que o justifica.

-- Agregado "requests" (ARQUITETURA §4.3, §9.2, §9.4; ADR-0004; ADR-0005).
-- Address é embutido (nunca referenciado por id) porque o contrato devolve o
-- endereço completo dentro do próprio ServiceRequest, sem endpoint próprio de
-- morada para pedidos. Não existe tabela de moradas reutilizáveis (entidade
-- Address adiada, cf. ARQUITETURA §9.1): volta quando existir um endpoint
-- que a leia ou escreva.
CREATE TABLE service_request (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id           UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    category_id           UUID NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    title                 VARCHAR(140) NOT NULL CHECK (char_length(title) >= 3),
    description           VARCHAR(4000),
    address_line1         VARCHAR(200),
    address_line2         VARCHAR(200),
    address_postal_code   VARCHAR(20),
    address_city          VARCHAR(120),
    address_region_code   VARCHAR(20),
    address_country       CHAR(2) NOT NULL DEFAULT 'PT',
    -- Resolvido de forma assíncrona pelo geocoding (§10.1); pode ser nulo
    -- enquanto o pedido está em DRAFT ou se o geocoding falhar (degrada para
    -- matching por ADMIN_REGION via address_region_code).
    location              geography(Point, 4326),
    urgency               VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                              CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    availability          VARCHAR(500),
    -- Máquina de estados §4.3.
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN (
                                  'DRAFT', 'PUBLISHED', 'IN_NEGOTIATION',
                                  'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
                              )),
    search_tsv            tsvector GENERATED ALWAYS AS (
                               setweight(to_tsvector('portuguese', immutable_unaccent(coalesce(title, ''))), 'A') ||
                               setweight(to_tsvector('portuguese', immutable_unaccent(coalesce(description, ''))), 'B')
                           ) STORED,
    published_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- publishedAt só faz sentido a partir de PUBLISHED; DRAFT nunca o tem.
    CONSTRAINT chk_service_request_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR (status <> 'DRAFT')
    )
);

-- Matching por raio (§10.2, ADR-0004); parcial porque location pode ser nulo
-- em DRAFT/antes do geocoding.
CREATE INDEX idx_service_request_location ON service_request USING GIST (location)
    WHERE location IS NOT NULL;
CREATE INDEX idx_service_request_search_tsv ON service_request USING GIN (search_tsv);
CREATE INDEX idx_service_request_title_trgm ON service_request USING GIN (title gin_trgm_ops);
-- Predicado de listagem/inbox do prestador (§10.3): filtra por estado e
-- categoria antes do critério geográfico.
CREATE INDEX idx_service_request_status_category_id ON service_request (status, category_id);
CREATE INDEX idx_service_request_customer_id ON service_request (customer_id);
-- Matching por região administrativa (§10.2).
CREATE INDEX idx_service_request_region_code ON service_request (address_region_code);

CREATE TRIGGER trg_service_request_set_updated_at
    BEFORE UPDATE ON service_request
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Associação pedido <-> imagens já carregadas (imageIds do contrato).
CREATE TABLE request_image (
    request_id      UUID NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    image_asset_id  UUID NOT NULL REFERENCES upload_asset (id) ON DELETE RESTRICT,
    position         INTEGER NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (request_id, image_asset_id)
);

CREATE INDEX idx_request_image_image_asset_id ON request_image (image_asset_id);

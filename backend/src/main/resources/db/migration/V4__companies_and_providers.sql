-- Agregado "providers" (ARQUITETURA §9.2, ADR-0004).
CREATE TABLE company (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    tax_id      VARCHAR(30),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- NIF único apenas quando preenchido (empresa em nome individual pode não o
-- ter registado ainda).
CREATE UNIQUE INDEX uq_company_tax_id ON company (tax_id) WHERE tax_id IS NOT NULL;

CREATE TRIGGER trg_company_set_updated_at
    BEFORE UPDATE ON company
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE provider_profile (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company_id        UUID REFERENCES company (id) ON DELETE SET NULL,
    headline          VARCHAR(160),
    bio               TEXT,
    verified          BOOLEAN NOT NULL DEFAULT false,
    -- Ciclo de aprovação administrativa do perfil (ARQUITETURA §4.1, admin).
    approval_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                          CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    -- Derivado de subscription.status (ARQUITETURA §12.1); escrito pelo
    -- módulo subscriptions ao reagir a SubscriptionActivated/Expired.
    visibility_state  VARCHAR(20) NOT NULL DEFAULT 'HIDDEN'
                          CHECK (visibility_state IN ('VISIBLE', 'HIDDEN')),
    rating_avg        NUMERIC(3, 2) NOT NULL DEFAULT 0
                          CHECK (rating_avg >= 0 AND rating_avg <= 5),
    rating_count      INTEGER NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    -- Suporta `q` em GET /v1/search/providers (ADR-0005); não descrito
    -- explicitamente no §9.4 da arquitetura (que só cobre service_request e
    -- category), mas exigido pelo contrato — decisão de modelação.
    search_tsv        tsvector GENERATED ALWAYS AS (
                          to_tsvector(
                              'portuguese',
                              immutable_unaccent(coalesce(headline, '') || ' ' || coalesce(bio, ''))
                          )
                      ) STORED,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 1–1 com users.
CREATE UNIQUE INDEX uq_provider_profile_user_id ON provider_profile (user_id);
CREATE INDEX idx_provider_profile_company_id ON provider_profile (company_id);
-- Suporta o predicado central de matching/pesquisa (§10.3): filtra por
-- visível + aprovado antes de qualquer critério geográfico ou de categoria.
CREATE INDEX idx_provider_profile_visibility ON provider_profile (visibility_state, approval_status);
CREATE INDEX idx_provider_profile_search_tsv ON provider_profile USING GIN (search_tsv);
CREATE INDEX idx_provider_profile_headline_trgm ON provider_profile USING GIN (headline gin_trgm_ops);

CREATE TRIGGER trg_provider_profile_set_updated_at
    BEFORE UPDATE ON provider_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- N–M provider <-> category.
CREATE TABLE provider_category (
    provider_id  UUID NOT NULL REFERENCES provider_profile (id) ON DELETE CASCADE,
    category_id  UUID NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider_id, category_id)
);

-- FK do lado que junta no predicado de matching (JOIN ... ON category_id = :category).
CREATE INDEX idx_provider_category_category_id ON provider_category (category_id);

-- Cobertura geográfica do prestador — dois modos combináveis (ADR-0004).
CREATE TABLE provider_service_area (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id  UUID NOT NULL REFERENCES provider_profile (id) ON DELETE CASCADE,
    mode         VARCHAR(20) NOT NULL CHECK (mode IN ('RADIUS', 'ADMIN_REGION')),
    center       geography(Point, 4326),
    radius_m     INTEGER CHECK (radius_m IS NULL OR radius_m > 0),
    region_code  VARCHAR(20),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Coerência por modo: RADIUS exige center+radius_m e nenhum region_code;
    -- ADMIN_REGION exige region_code e nenhum center/radius_m. Sem isto
    -- acumulam-se áreas que não correspondem a ninguém, sem sinal de erro.
    CONSTRAINT chk_provider_service_area_mode_coherence CHECK (
        (mode = 'RADIUS'
            AND center IS NOT NULL AND radius_m IS NOT NULL AND region_code IS NULL)
        OR
        (mode = 'ADMIN_REGION'
            AND region_code IS NOT NULL AND center IS NULL AND radius_m IS NULL)
    )
);

CREATE INDEX idx_provider_service_area_provider_id ON provider_service_area (provider_id);
-- Predicado de matching usa ST_DWithin(center, ...) só quando mode=RADIUS;
-- parcial porque ADMIN_REGION nunca tem center preenchido (índice mais
-- pequeno, sem entradas mortas).
CREATE INDEX idx_provider_service_area_center ON provider_service_area USING GIST (center)
    WHERE center IS NOT NULL;
CREATE INDEX idx_provider_service_area_region_code ON provider_service_area (region_code);

CREATE TRIGGER trg_provider_service_area_set_updated_at
    BEFORE UPDATE ON provider_service_area
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

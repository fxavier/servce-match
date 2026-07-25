-- Agregado "users" (ARQUITETURA §9, ADR-0002).
--
-- keycloak_sub é a chave natural externa e estável com o IdP: nunca o email
-- (um utilizador pode mudar de email no Keycloak sem perder a ligação ao
-- registo de domínio).
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_sub  VARCHAR(255) NOT NULL,
    email         VARCHAR(320) NOT NULL,
    display_name  VARCHAR(160) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                      CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Elo estável com o IdP (ADR-0002): único ponto de correlação com o Keycloak.
CREATE UNIQUE INDEX uq_users_keycloak_sub ON users (keycloak_sub);
-- Não é chave; apenas apoio a pesquisa administrativa/suporte.
CREATE INDEX idx_users_email ON users (email);

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Moradas reutilizáveis do utilizador ("gestão de moradas", ARQUITETURA §4.1
-- e entidade Address em §9.1). O contrato v1.0.0 ainda não expõe endpoints
-- dedicados de CRUD de moradas (só o Address embutido em ServiceRequest),
-- mas a entidade já está prevista no modelo de dados de referência; criá-la
-- agora evita uma migração retroativa cara quando o endpoint chegar (mesmo
-- racional do device_token adiantado por ADR-0008).
CREATE TABLE user_address (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label        VARCHAR(80),
    line1        VARCHAR(200) NOT NULL,
    line2        VARCHAR(200),
    postal_code  VARCHAR(20),
    city         VARCHAR(120),
    region_code  VARCHAR(20),
    country      CHAR(2) NOT NULL DEFAULT 'PT',
    location     geography(Point, 4326),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_address_user_id ON user_address (user_id);
-- Parcial: só indexa moradas com coordenadas resolvidas (geocoding é
-- assíncrono, cf. §10.1); linhas sem location nunca entram num ST_DWithin.
CREATE INDEX idx_user_address_location ON user_address USING GIST (location)
    WHERE location IS NOT NULL;

CREATE TRIGGER trg_user_address_set_updated_at
    BEFORE UPDATE ON user_address
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

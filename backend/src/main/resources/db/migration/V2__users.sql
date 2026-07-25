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

-- Catálogo hierárquico de categorias/subcategorias (ARQUITETURA §9.2, §9.4).
-- FTS sobre "name" com GIN (ADR-0005); trigram para pesquisa tolerante a
-- erros de escrita no autocomplete de categoria.
CREATE TABLE category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID REFERENCES category (id) ON DELETE RESTRICT,
    slug        VARCHAR(140) NOT NULL,
    name        VARCHAR(140) NOT NULL CHECK (char_length(name) >= 2),
    active      BOOLEAN NOT NULL DEFAULT true,
    search_tsv  tsvector GENERATED ALWAYS AS (
                    to_tsvector('portuguese', immutable_unaccent(coalesce(name, '')))
                ) STORED,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_category_slug ON category (slug);
-- FK do lado que junta (categoria pai <- filhas).
CREATE INDEX idx_category_parent_id ON category (parent_id);
CREATE INDEX idx_category_search_tsv ON category USING GIN (search_tsv);
CREATE INDEX idx_category_name_trgm ON category USING GIN (name gin_trgm_ops);

CREATE TRIGGER trg_category_set_updated_at
    BEFORE UPDATE ON category
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Extensões necessárias ao schema ServiMatch.
--
-- postgis   : tipos e funções geoespaciais para cobertura de prestadores e
--             localização de pedidos (ADR-0004).
-- pg_trgm   : índices de trigramas para pesquisa aproximada / tolerante a
--             erros de escrita (ADR-0005).
-- unaccent  : normalização de acentos (pt-PT) para que "canalizacao" e
--             "canalização" sejam equivalentes em pesquisa por texto.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Wrapper IMMUTABLE sobre unaccent(): a função da extensão não é declarada
-- IMMUTABLE por omissão (o dicionário podia mudar em teoria), o que impede o
-- seu uso em colunas GENERATED/índices de expressão. O dicionário "unaccent"
-- é estático no nosso caso, pelo que o wrapper é seguro.
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$
  SELECT unaccent('unaccent', $1)
$$;

-- Função partilhada para manter updated_at coerente a nível de schema,
-- independentemente de a camada de aplicação a definir explicitamente em
-- cada escrita. Aplicada por trigger em cada tabela que tenha updated_at.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

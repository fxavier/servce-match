-- Extensões necessárias no esquema de domínio (base de dados POSTGRES_DB).
-- Corre apenas contra a base de dados principal, não contra "keycloak"
-- (o Keycloak gere o seu próprio esquema via Liquibase no arranque).
--
-- postgis   -> ADR-0004 (geolocalização e matching por proximidade)
-- pg_trgm   -> ADR-0005 (pesquisa full-text / fuzzy matching)
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

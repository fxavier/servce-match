# Architecture Decision Records (ADR) — ServiMatch

Registo de decisões arquiteturais no formato [MADR](https://adr.github.io/madr/). Cada ADR captura uma decisão significativa, o contexto, as opções ponderadas e as consequências. Os ADRs são imutáveis: uma decisão substituída não é apagada — cria-se um novo ADR com estado `Aceite` e marca-se o anterior como `Substituído por ADR-XXXX`.

| ADR | Título | Estado |
|---|---|---|
| [ADR-0001](0001-modular-monolith-spring-modulith.md) | Modular Monolith com Spring Modulith | Aceite |
| [ADR-0002](0002-identidade-keycloak-oauth2-oidc.md) | Identidade delegada a Keycloak (OAuth2/OIDC) | Aceite |
| [ADR-0003](0003-versao-stack-backend.md) | Versão do stack backend (Spring Boot 3.5 vs 4.x) | Aceite |
| [ADR-0004](0004-geolocalizacao-matching-postgis.md) | Geolocalização e matching com PostGIS | Aceite |
| [ADR-0005](0005-pesquisa-postgresql-fts.md) | Pesquisa com PostgreSQL Full-Text Search primeiro | Aceite |
| [ADR-0006](0006-redis-condicional.md) | Redis condicional (single vs multi-instância) | Aceite |
| [ADR-0007](0007-pagamentos-multi-gateway.md) | Estratégia de pagamentos multi-gateway | Aceite |
| [ADR-0008](0008-app-movel-flutter.md) | Aplicação móvel Flutter (multi-cliente, app única, fast-follow) | Aceite |
| [ADR-0009](0009-autenticacao-clientes-nativos.md) | Autenticação de clientes nativos (RFC 8252 / AppAuth + PKCE) | Aceite |

## Estados possíveis

- **Proposto** — em discussão, ainda não vinculativo.
- **Aceite** — decisão em vigor.
- **Substituído** — substituído por um ADR posterior (indicar qual).
- **Descontinuado** — já não aplicável.

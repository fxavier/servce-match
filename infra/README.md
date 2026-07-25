# infra — ambiente local do ServiMatch

`docker compose up` deixa o sistema utilizável com **um comando**, incluindo
o realm `servimatch` já importado no Keycloak. Este README cobre só o
essencial: arrancar, parar, resetar, e obter um token de teste. A decisão
arquitetural por trás de cada peça está em `docs/adr/` (0002 Keycloak, 0004
PostGIS, 0006 Redis condicional, 0007 pagamentos).

## Serviços e portas

| Serviço | Imagem (fixa) | Porta local | Propósito |
|---|---|---|---|
| `postgres` | `postgis/postgis:16-3.4` | `5432` | PostgreSQL + PostGIS + `pg_trgm`; também aloja a base de dados `keycloak` |
| `keycloak` | `quay.io/keycloak/keycloak:26.7.0` | `8081` (→8080 no container) | IdP único (ADR-0002); realm `servimatch` importado no arranque |
| `redis` | `redis:8.8.1-alpine` | `6379` | Opcional (ADR-0006) — só com `--profile redis` |
| `minio` | `minio/minio:RELEASE.2025-09-07T16-13-09Z` | `9000` (API), `9001` (consola) | Substituto local de S3 para uploads |
| `minio-init` | `minio/mc:RELEASE.2025-08-13T08-35-41Z` | — | Job de arranque único: cria o bucket `servimatch-uploads` e garante que é privado |

Todos os serviços têm healthcheck; `keycloak` e `minio-init` esperam pelas
suas dependências (`depends_on: condition: service_healthy`) antes de
arrancar.

## Arrancar

A partir da **raiz do repositório** (para o `--env-file` apanhar o `.env` lá,
já que o `docker-compose.yml` vive em `infra/`):

```bash
cp .env.example .env
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

Acompanhar o arranque (Keycloak demora ~30-60s a importar o realm):

```bash
docker compose -f infra/docker-compose.yml --env-file .env ps
```

Quando `keycloak` aparecer `healthy`, o realm está importado e pronto.

Verificação rápida:

```bash
curl -s http://localhost:8081/realms/servimatch/.well-known/openid-configuration | jq .issuer
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9000/minio/health/live
```

### Redis (opcional, ADR-0006)

Redis só é necessário em multi-instância (*rate limiting* distribuído,
cache partilhada, *relay* de WebSocket). Em single-instância o backend usa
memória local (Bucket4j/Caffeine) e não precisa dele. Para o ligar:

```bash
docker compose -f infra/docker-compose.yml --env-file .env --profile redis up -d
```

## Parar

```bash
docker compose -f infra/docker-compose.yml --env-file .env down
```

Isto preserva os volumes nomeados (dados do Postgres, do MinIO e do Redis
sobrevivem). Voltar a subir (`up -d`) restaura o mesmo estado sem passos
manuais — é a garantia de arranque idempotente exigida para este ambiente.

## Reset completo (apagar dados)

```bash
docker compose -f infra/docker-compose.yml --env-file .env down -v
```

O `-v` remove os volumes nomeados (`servimatch-postgres-data`,
`servimatch-minio-data`, `servimatch-redis-data`). O próximo `up -d` recria
tudo do zero: base de dados vazia (com extensões `postgis`/`pg_trgm` já
ativas via `infra/postgres/init/`), realm reimportado do zero a partir de
`infra/keycloak/realm-servimatch.json`, bucket MinIO recriado.

## Obter um token de teste

O realm importado tem três utilizadores fixture, **só para desenvolvimento
local** — as credenciais abaixo nunca são reutilizadas noutro ambiente e não
protegem nada de sensível:

| Username | Password | Role |
|---|---|---|
| `customer.test@servimatch.pt` | `DevLocal#2026` | `CUSTOMER` |
| `provider.test@servimatch.pt` | `DevLocal#2026` | `PROVIDER` |
| `admin.test@servimatch.pt` | `DevLocal#2026` | `ADMIN` |

Os três clients "reais" do realm (`servimatch-web`, `servimatch-mobile`,
`servimatch-bff`) seguem Authorization Code + PKCE (ADR-0002/ADR-0009) e
**não** têm Resource Owner Password Credentials ativo — não dá para obter
um token deles por `curl` sem passar pelo browser, e isso é propositado.

Para testar por linha de comandos existe um quarto client,
**`servimatch-local-test`**, exclusivamente para desenvolvimento/CI, com
ROPC ativo:

```bash
curl -s -X POST http://localhost:8081/realms/servimatch/protocol/openid-connect/token \
  -d 'grant_type=password' \
  -d 'client_id=servimatch-local-test' \
  -d 'username=customer.test@servimatch.pt' \
  -d 'password=DevLocal#2026' \
  -d 'scope=openid' | jq .access_token
```

O access token resultante tem `aud=servimatch-backend` (audiência exigida
pelo Resource Server, ver `backend/.env.example` / `KEYCLOAK_AUDIENCE`) e
`realm_access.roles` com a role do utilizador — pronto a usar em
`Authorization: Bearer <token>` contra a API.

Para testar o fluxo real (Authorization Code + PKCE) do `servimatch-web` ou
do `servimatch-mobile`, é preciso passar pelo browser — usa a app web/mobile
apontada para este Keycloak (`KEYCLOAK_ISSUER_URI=http://localhost:8081/realms/servimatch`),
não `curl`.

### Consola de administração do Keycloak

`http://localhost:8081/admin/` — utilizador `admin` / password `admin`
(valores de `.env`, `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD`). Só para o
realm `master` deste ambiente local descartável; nunca reutilizados noutro
ambiente.

## MinIO

Consola web: `http://localhost:9001` (login com `MINIO_ROOT_USER`/
`MINIO_ROOT_PASSWORD` de `.env`). O bucket `servimatch-uploads` é criado
automaticamente pelo job `minio-init` e mantido **privado** — os ficheiros
nunca são servidos diretamente a partir do bucket; o backend emite sempre um
URL pré-assinado de utilização única e com expiração (`POST /v1/uploads`,
ver `docs/ARQUITETURA.md` §8.6/§11.2).

## Estrutura

```
infra/
├── docker-compose.yml
├── keycloak/
│   └── realm-servimatch.json   # realm versionado, importado no arranque
├── postgres/
│   └── init/                   # scripts docker-entrypoint-initdb.d
│       ├── 001-create-databases.sh   # cria a BD "keycloak" além da principal
│       └── 002-enable-extensions.sql # postgis + pg_trgm
└── minio/
    └── init-buckets.sh         # bucket "servimatch-uploads", privado
```

## O que muda no realm quando o backend existe

O client Resource Server chama-se `servimatch-backend` (bearer-only, não
inicia login). Os quatro clients de front-end
(`servimatch-web`/`servimatch-bff`/`servimatch-mobile`/`servimatch-local-test`)
têm cada um um *protocol mapper* dedicado (`oidc-audience-mapper`) que
garante `aud=servimatch-backend` em todo o access token emitido — sem isto,
o `AudienceValidator` do backend (ADR-0002: validação de `iss`/`aud`/`exp`)
rejeitaria todos os pedidos autenticados com 401. Se precisares de outro
client (ex.: um serviço interno adicional) a chamar a API, ou de outra
audiência, adiciona o mesmo tipo de mapper ao novo client — não reintroduzas
um `clientScopes` de topo no realm sem cuidado: o *import* do Keycloak só
semeia os *client scopes* nativos (`roles`, `profile`, `email`,
`web-origins`, `acr`, `basic`, ...) quando o realm **não** declara nenhum
`clientScopes` próprio; declarar um substitui-os em vez de os complementar.

## Troubleshooting

- **`keycloak` fica `starting` durante muito tempo / nunca fica `healthy`**:
  corre `docker compose -f infra/docker-compose.yml --env-file .env logs keycloak`.
  A causa mais comum é um JSON inválido em `realm-servimatch.json` (o
  Keycloak rejeita campos desconhecidos — nada de comentários dentro do
  JSON) ou um valor de `description` de client acima de 255 caracteres.
- **Porta já em uso**: ajusta `POSTGRES_PORT`/`KEYCLOAK_PORT`/`REDIS_PORT`/
  `MINIO_PORT`/`MINIO_CONSOLE_PORT` no `.env`.
- **`minio-init` falha**: corre
  `docker compose -f infra/docker-compose.yml --env-file .env logs minio-init`;
  o job é idempotente (`mc mb --ignore-existing`), por isso um `up -d` a
  seguir a uma falha transitória do MinIO é suficiente.

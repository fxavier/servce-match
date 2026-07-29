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
| `minio-init` | `minio/mc:RELEASE.2025-08-13T08-35-41Z` | — | Job de arranque: cria o bucket `servimatch-uploads`, garante que é privado, e depois fica em idle (`tail -f /dev/null`) em vez de terminar — um container terminado faz `docker compose up --wait` sair com erro mesmo com exit 0 |

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

O realm importado tem quatro utilizadores fixture, **só para desenvolvimento
local** — as credenciais abaixo nunca são reutilizadas noutro ambiente e não
protegem nada de sensível. O **UUID é fixo** (campo `id` do utilizador no
JSON do realm) para que o `sub` do token OIDC seja estável entre reimports —
o seed da base de dados liga `users.keycloak_sub` a estes valores; sem `id`
explícito no JSON, o Keycloak gera um UUID novo a cada `--import-realm` e o
seed deixa de encontrar o utilizador correspondente.

| Username | Password | Role | UUID (`id` no realm = `sub` no token) |
|---|---|---|---|
| `customer.test@servimatch.pt` | `DevLocal#2026` | `CUSTOMER` | `07799610-a03f-40aa-a713-948d9d1b929d` |
| `provider.test@servimatch.pt` | `DevLocal#2026` | `PROVIDER` | `d3d6d736-c434-41eb-b3dc-2201a3f69c4a` |
| `admin.test@servimatch.pt` | `DevLocal#2026` | `ADMIN` | `35295b6c-a788-4f2b-bc22-8edc292aaf81` |
| `provider.trial@servimatch.pt` | `DevLocal#2026` | `PROVIDER` | `980712fa-004b-487e-9946-ddc65cadec49` |

`provider.trial@servimatch.pt` (Rita Nogueira) é o prestador **sem
subscrição ativa** usado pelo perfil de demonstração do site (gating por
subscrição, ADR-0011) — ao contrário de `provider.test@servimatch.pt`, cujo
estado de subscrição é o que o seed da base de dados lhe atribuir.

Verificado empiricamente (reimport a sério, container efémero,
`quay.io/keycloak/keycloak:26.7.0`): o `sub` do access token emitido para
cada um destes utilizadores é exatamente o `id` acima, em qualquer client
(`servimatch-local-test` por ROPC, ou `servimatch-bff` por Direct Access
Grant — ver secção seguinte).

Dois dos clients "reais" do realm (`servimatch-web`, `servimatch-mobile`)
seguem apenas Authorization Code + PKCE (ADR-0002/ADR-0009) — não têm
Resource Owner Password Credentials ativo, não dá para obter um token deles
por `curl` sem passar pelo browser, e isso é propositado.

Para testar por linha de comandos existe um client dedicado,
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

### Login e registo por formulário próprio (BFF server-to-server)

O produto tem registo e login em formulários próprios (não redireciona para
a UI do Keycloak). O BFF fala diretamente com o Keycloak, dois caminhos:

- **Login**: Direct Access Grant (`grant_type=password`, RFC 6749 §4.3) no
  client confidencial `servimatch-bff` (`directAccessGrantsEnabled: true`,
  `publicClient: false`, autenticado com `KEYCLOAK_BFF_CLIENT_SECRET`).
- **Registo**: `POST {KEYCLOAK_ADMIN_API_BASE_URL}/admin/realms/servimatch/users`
  (Admin REST API), autenticado com um token `client_credentials` do service
  account do mesmo client (`serviceAccountsEnabled: true`).

O `standardFlowEnabled` (Authorization Code + PKCE) do `servimatch-bff`
mantém-se ativo — é o caminho de regresso do BFF e o único usado pelo mobile.

Duas flags do realm mudaram para este fluxo não partir logo à nascença
(ADR-0012):

- **`verifyEmail: false`** (era `true`). Com `true`, um utilizador criado por
  `POST /admin/realms/servimatch/users` com `emailVerified:false` — o estado
  honesto de um registo que ainda não confirmou o email — fica com a
  *required action* `VERIFY_EMAIL`; o Direct Access Grant imediato a seguir
  ao registo devolve `invalid_grant` opaco, sem indicar a causa. É o
  encadeamento central do produto (registo → login automático), por isso
  isto partia o fluxo inteiro, não um caso lateral. A alternativa óbvia
  seria criar o utilizador já com `emailVerified:true` — **rejeitada**: seria
  afirmar um facto falso num claim do token. A verificação de email passa a
  **regra de domínio** (backend, fora do âmbito deste agente), não *required
  action* do Keycloak. Confirmado empiricamente (container efémero): com
  `verifyEmail:false`, um utilizador criado com `emailVerified:false` fica
  com `requiredActions: []` e o Direct Access Grant imediato a seguir devolve
  `200` com `email_verified:false` no token — honesto e sem bloquear o login.
- **`registrationAllowed: false`** (era `true`, ADR-0012 D6). Com o registo a
  passar a ser feito pelo BFF (Admin REST API), a página de auto-registo do
  Keycloak ficava como segunda porta de entrada — sem *rate limiting* do BFF
  e sem *allowlist* de roles: um utilizador que se registasse por ali ficava
  sem `CUSTOMER` nem `PROVIDER`, e levava 403 em todo o `/v1/**` sem forma
  óbvia de perceber porquê. `registrationAllowed` só desliga a página de
  self-service da UI do Keycloak; não afeta a Admin REST API, que é o
  caminho que o BFF usa.

**Um único client (`servimatch-bff`) para login e registo, não dois.** O
`arquiteto` sugeriu separar o service account num client próprio
(`servimatch-bff-admin`), para que o secret que autentica logins de
utilizadores não seja o mesmo que pode criar utilizadores novos. Mantive um
único client, por três razões: (1) é o que este ficheiro já tinha construído
e validado ponta-a-ponta antes desta nota chegar, e um client extra é mais
uma superfície a manter sincronizada com o BFF sem necessidade comprovada;
(2) o `servimatch-bff` já é, por natureza, o único componente que fala
diretamente com o Keycloak em nome do produto (ADR-0002) — separar o secret
de admin não reduz quem o vê (só o BFF), só multiplica a rotação; (3) em
desenvolvimento local o secret já é descartável e nunca reutilizado. O
compromisso é real e fica documentado: o mesmo `KEYCLOAK_BFF_CLIENT_SECRET`
autentica tanto Direct Access Grant (login de qualquer utilizador cujas
credenciais o BFF já validou) como a Admin REST API (criação de
utilizadores) — quem o obtiver pode criar contas novas no realm, não só
autenticar-se. Se isto passar a incomodar em staging/produção (rotação
independente, *blast radius* menor), é uma decisão de ADR novo, não uma
correção silenciosa aqui.

Testar o login por Direct Access Grant manualmente:

```bash
curl -s -X POST http://localhost:8081/realms/servimatch/protocol/openid-connect/token \
  -d 'grant_type=password' \
  -d 'client_id=servimatch-bff' \
  -d 'client_secret=dev-only-bff-secret-never-reused-elsewhere' \
  -d 'username=customer.test@servimatch.pt' \
  -d 'password=DevLocal#2026' \
  -d 'scope=openid' | jq .access_token
```

Testar a Admin REST API pelo service account (para registo de utilizadores):

```bash
ADMIN_API_TOKEN=$(curl -s -X POST http://localhost:8081/realms/servimatch/protocol/openid-connect/token \
  -d 'grant_type=client_credentials' \
  -d 'client_id=servimatch-bff' \
  -d 'client_secret=dev-only-bff-secret-never-reused-elsewhere' | jq -r .access_token)

curl -s -X POST http://localhost:8081/admin/realms/servimatch/users \
  -H "Authorization: Bearer $ADMIN_API_TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"novo@example.com","email":"novo@example.com","enabled":true,"emailVerified":false,"firstName":"Novo","lastName":"Registo"}'
```

**Service account do `servimatch-bff`** (`service-account-servimatch-bff`,
`id` fixo `65d60604-b03b-4a20-8c55-f141bde0e122`) tem só os client roles
`manage-users` e `view-users` de `realm-management` — o mínimo para criar/
consultar utilizadores e atribuir-lhes roles, nunca para gerir o realm em
si. Em JSON de realm isto **não** é uma entrada em `clients[].serviceAccount*`
mas sim uma entrada normal em `users[]`, com `serviceAccountClientId` a
apontar para o client dono e `clientRoles` a mapear `"realm-management":
["manage-users", "view-users"]`. Formato confirmado empiricamente: um
`GET /admin/realms/{realm}/partial-export` não inclui utilizadores; foi
preciso `kc.sh export --users realm_file` (container efémero, sem afetar o
stack partilhado) para ver a forma exata que o próprio Keycloak gera para
um service account, e um reimport a sério desse ficheiro confirmou que é
importável e que o `sub` emitido para os quatro utilizadores fixture bate
certo com os UUID fixos acima.

Duas armadilhas da Admin REST API com este conjunto mínimo de roles,
confirmadas empiricamente com um Keycloak 26.7.0 efémero (não assumir, testar
se mudares algo aqui):

- `GET /admin/realms/{realm}/roles` devolve **403** — `manage-users`/
  `view-users` não dão acesso à gestão de roles do realm em geral. Para
  descobrir o `id` de uma role a atribuir a um utilizador (ex.: `CUSTOMER`
  no registo), o caminho que funciona é
  `GET /admin/realms/{realm}/users/{id}/role-mappings/realm/available`
  (devolve as roles disponíveis, com `id`, `name`, etc., já com o âmbito de
  `view-users`).
- `POST /admin/realms/{realm}/users/{id}/role-mappings/realm` exige a
  **representação completa da role** (pelo menos `id` + `name`) no corpo —
  um array com só `{"name": "CUSTOMER"}` (sem `id`) devolve **404 "Role not
  found"**. Usa sempre o objeto devolvido por `.../role-mappings/realm/available`
  tal e qual, sem reconstruir à mão.

### Proteção por força bruta e Direct Access Grant

`bruteForceProtected: true` está ativo no realm com valores sensatos
(`failureFactor: 5`, `waitIncrementSeconds: 60`, `maxFailureWaitSeconds: 900`,
sem `permanentLockout`). Mas a proteção por IP do Keycloak vê o IP de quem
lhe fala diretamente — com Direct Access Grant, isso é sempre o BFF, nunca o
browser do utilizador final. Isto não é uma lacuna a corrigir no realm: um
IdP nunca vê o IP real do cliente final num fluxo ROPC server-to-server, seja
qual for a configuração. A mitigação real tem de estar no BFF (rate limiting
por IP/username no próprio endpoint de login, antes de chamar o Keycloak) —
âmbito de quem implementa o BFF, não deste ficheiro de realm. O bloqueio por
utilizador do Keycloak (`failureFactor`) continua válido como segunda linha,
já que esse é contado pelo `username` tentado, não pelo IP.

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

## Cópia de teste do realm (backend)

`backend/src/test/resources/keycloak/realm-servimatch.json` é uma cópia
deliberada deste ficheiro, usada por testes que precisam de um Keycloak real
sem depender do `docker-compose` local (`SharedKeycloak`, `RealmFileSyncTest`
verifica byte-a-byte que não diverge). Não é âmbito de escrita deste agente
— sempre que este ficheiro muda, quem o resincroniza é o `qa-e2e`.

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
  seguir a uma falha transitória do MinIO é suficiente. O container fica
  `Up` (não `Exited`) mesmo depois de o bucket estar criado — isso é
  propositado, não um sinal de que ficou preso; ver comentário no
  `docker-compose.yml`.

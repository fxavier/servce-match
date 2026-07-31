# ServiMatch — Verificação total do sistema

Data: 2026-07-30 · Árvore auditada: `main` @ `eb14f06`
**Revisto: 2026-07-30, após a fusão de `feat/dados-reais/onda-1` — baseline atual `main` @ `62437e1` (PR #1). Ver §0.**
Método: auditoria estática paralela (contrato, segurança, dados, frontends, infra) com evidência ficheiro:linha.
Limitação assumida: **nada foi compilado nem executado** — o sandbox tem Java 11 (o projeto exige 21), sem Maven, sem Flutter, e o `web/node_modules` foi instalado em macOS (falta `@rollup/rollup-linux-x64-gnu`). Todos os veredictos são de análise estática. As afirmações sobre "verde/vermelho" no CI não foram observadas, foram derivadas do código.

---

## 0. Fusão concluída — o que fechou e o que sobreviveu

**Estado à data da revisão:** `feat/dados-reais/onda-1` foi fundida em `main` (`62437e1`, PR #1). Verificado: `git merge-base --is-ancestor` confirma a contenção, o `git diff main feat/dados-reais/onda-1` é **vazio**, e os ficheiros-chave estão em disco (`db/seed/`, `application-local.yml`, `platform/seed/SeedLocationsGuard.java`, `billing/SubscriptionVisibilitySql.java`, `web/bff/src/{rateLimit,keycloakAdmin,loginTiming}.ts`). `schema.d.ts` tem agora 26 de 26 paths. 15 controllers, incluindo `ProvidersController` e `ProviderReviewsController`.

A árvore de trabalho está limpa — só `docs/ESTADO-DO-SISTEMA.md` e `.claude/settings.local.json` por versionar.

**O que a fusão fechou** (reverificado contra `62437e1`, não assumido):

| Item | Estado |
|---|---|
| ADR-0012 (auth first-party) | ✔ implementado |
| ADR-0013 (seed dev-only) + `SeedLocationsGuard` | ✔ **C8 fechado** |
| Rate limiting `/auth/**` no BFF | ✔ implementado |
| ADR-0011 D1 — `visibility_state` fora dos predicados | ✔ **C2 (parte `visibility_state`) fechado**: zero ocorrências em `src/main/java` |
| Literal único dos estados de visibilidade | ✔ `SubscriptionVisibilitySql.java` |
| `schema.d.ts` sincronizado | ✔ 26/26 |
| Escritores de `provider_category` / `provider_service_area` | ✔ `ProviderRepository:170,190` |
| 7 endpoints em falta + 6 serviços web a lançar 501 | ✔ fechados |

**O que sobreviveu à fusão** (todos reverificados em `62437e1`):

| # | Defeito | Evidência atual |
|---|---|---|
| **C1** | `approval_status` sem escritor de produção; `PATCH /v1/admin/providers/{id}/approval` **continua inexistente** | `grep -rn "admin/providers" src/main/java` → vazio; único `approval_status` em `src/main/java` é a **leitura** em `EligibilityRepository:79` |
| **C2** (residual) | `rating_avg` / `rating_count` sem escritor — e continuam a ser critério de `ORDER BY` na pesquisa | `grep rating_avg` com `SET`/`INSERT` → vazio |
| **C3.1** | `409 email-already-registered` no registo — a violação que a fusão **introduziu** | `web/bff/src/routes/auth.ts:199` |
| **C4** | IDOR: `parseStatusFilter` existe (`RequestsService:174`) e é aplicado em `listMine:161`, mas **`listInbox:265-266` continua a passar o `statusFilter` cru** | `RequestsService.java:265-266` |
| **C5** | Zero Dockerfiles, zero IaC, um único workflow (`ci.yml`), sem deploy | `find` → vazio |
| **C6** | `application-prod.yml` continua a remover **apenas** `DB_PASSWORD` e `UPLOADS_S3_SECRET_KEY` | ficheiro tem 5 linhas úteis |
| ~~C7~~ | **Corrigido — o meu diagnóstico estava errado.** `SecurityConfig:190` põe `/actuator/**` atrás de `hasRole("ADMIN")`, com o raciocínio comentado nas linhas 183-189 (métricas não são dado que um `CUSTOMER` criado por registo público deva ler). Eu tinha lido as linhas 130-131 do tree **pré-fusão**. Fica em aberto apenas o ponto menor de operação: um scraper Prometheus precisa de um JWT com role `ADMIN`, o que sugere porta de gestão separada — melhoria, não defeito | `SecurityConfig.java:182-190` |

A ordem de prioridade não muda: **C1 continua a ser o defeito que mantém o produto desligado**, e agora é o único bloqueador de topo, porque tudo o resto que dependia da fusão já caiu. C3.1 subiu de prioridade por ser regressão nova.

### Registo histórico — porque `main` estava enganador antes da fusão

Mantido porque o padrão de processo é o achado mais reutilizável deste relatório: durante um período, `main` continha o commit `3e936af feat(infra): enable direct access grant and service account for the BFF` — que abriu `directAccessGrantsEnabled` + `serviceAccountsEnabled` + `realm-management:{manage-users,view-users}` no client `servimatch-bff` — **sem o código que os consome**. A infra foi fundida à frente da aplicação, deixando capacidade privilegiada de criar utilizadores no realm ligada, sem consumidor e sem rate limiting. A fusão resolveu-o. A lição fica: alterações de realm não devem fundir separadamente do código que as justifica.

Comparação `main`(pré-fusão) vs `onda-1`, para referência:

| Item | Veredicto contra `main` | Realidade em `onda-1` |
|---|---|---|
| ADR-0012 (auth first-party) | 0% implementado | Implementado (`web/bff/src/keycloakAdmin.ts`, `rateLimit.ts`, `loginTiming.ts`, `passwordPolicy.ts`) |
| ADR-0013 (seed dev-only) | 0% implementado | Implementado, incl. `platform/seed/SeedLocationsGuard.java` + `db/seed/V900..V905` |
| Rate limiting `/auth/**` no BFF | Ausente | Presente, com `trust proxy` numérico e teste |
| ADR-0011 D1 (`visibility_state` fora dos predicados) | Violado — 3 predicados leem-no | Cumprido — predicados passam a ler só `approval_status` |
| Literal único dos estados de visibilidade | Violado — 3 literais divergentes | Cumprido — `billing/SubscriptionVisibilitySql.java` |
| `schema.d.ts` gerado | 18 de 26 paths (stale) | 26 de 26 |
| `GET/PUT /v1/providers/me`, `GET /v1/providers/{id}`, `.../reviews`, `GET /v1/subscriptions/me` | Inexistentes | `ProvidersController`, `ProviderReviewsController`, `SubscriptionController:77` |
| `provider_category` / `provider_service_area` sem escritor | Confirmado | Escritores em `ProviderRepository:170,190` |
| 6 serviços web a lançar 501 | Confirmado | `notImplementedInContract` eliminado |

---

## 1. Estado por componente

Percentagens são estimativas fundamentadas na evidência recolhida, não medições.

| Área | `main` | `onda-1` | Nota |
|---|---|---|---|
| Contrato OpenAPI 3.1 | 100% | 100% | 26 paths / 30 operações, lint + *breaking change check* no CI |
| ADR (0001–0013) | 100% escritos | — | 13 ADR; ADR-0011 e 0013 escritos e **não** implementados em `main` |
| Backend — esqueleto Modulith | 95% | 95% | 15 módulos + `platform`, `ApplicationModules.verify()` no CI |
| Backend — endpoints do contrato | 20/30 | 27/30 | Falta em ambos: `PATCH /v1/admin/providers/{id}/approval`, `GET /v1/requests`, `GET /v1/proposals/me` |
| Backend — schema Flyway | 100% | 100% | V1–V16, PostGIS + FTS pt corretos, CHECK em todos os enums |
| Backend — segurança (Resource Server) | 95% | 95% | Genuinamente bem feito; ver §2 |
| Backend — webhooks/idempotência | 95% | 95% | Assinatura antes de efeito, quarentena, `UNIQUE(gateway, raw_event_id)`, reconciliação agendada |
| Backend — uploads | 90% | 90% | *Magic bytes*, URL assinado com expiração |
| Web — UI / design system | 90% | 92% | 27 páginas, 22 rotas, 20 componentes; **sem área de admin** |
| Web — ligação real ao backend | 55% | ~90% | `main`: 6 serviços lançam 501, 2 fabricam dados |
| Web — BFF | 85% (ADR-0002) / 0% (ADR-0012) | ~90% | `main`: sem `POST /auth/register`, sem rate limiting |
| Mobile — core (auth/rede) | 80% | 80% | ADR-0009 cumprido ponta a ponta, `flutter_appauth`, secure storage |
| Mobile — cliente de API | 27% | 27% | 8 de 30 operações, escrito à mão |
| Mobile — features | 25% | 25% | 4 de 9 slices; perfil Prestador é `comingSoon` |
| Infra — ambiente local | 85% | 85% | Imagens fixas, healthchecks, init idempotente |
| Infra — CI | 65% | 65% | Cobre o básico; não é gate de confiança (§4) |
| **Infra — deploy / IaC / observabilidade recolhida / backups** | **0%** | **0%** | Zero Dockerfiles, zero Terraform/k8s, zero collector, zero backup |
| Testes — backend | 70% | 75% | Testcontainers + Keycloak real; ver §3 |
| Testes — web unit | 35% | 40% | 1 de 27 páginas com teste de comportamento |
| Testes — E2E | 20% | 25% | 1 teste, escrito e **desligado no CI** |
| Testes — mobile | 40% | 40% | 6 ficheiros sólidos; 0 integração, 0 golden |

---

## 2. Defeitos críticos

Ordenados por severidade. Cada um com o que está errado, porque importa, e como se fecha.

### C1 — `provider_profile.approval_status` é lido por todos os predicados de decisão e **nunca escrito em produção**

Presente em **`main` e em `onda-1`**. É o defeito mais grave do sistema.

- Leitores: `matching/internal/EligibilityRepository.java:35,65` (`:50,79` em `onda-1`), `search/internal/ProviderSearchRepository.java:52` (`:62`), `providers/internal/ProvidersService.java:55`.
- Escritores em produção: **nenhum**. `ProviderRepository.java:52-58` insere apenas `user_id`. Não existe `UPDATE`.
- `DEFAULT 'PENDING'` (`V4__companies_and_providers.sql`). O endpoint que o mudaria — `PATCH /v1/admin/providers/{providerId}/approval` (`openapi.yaml:800`) — **não existe em nenhum ramo**.

**Efeito em produção:** nenhum prestador é jamais aprovado. `GET /v1/search/providers` e `findEligibleProviderIds` devolvem **0 resultados para qualquer consulta**. Quem paga a subscrição nunca aparece. Falha fechada, portanto não é fuga de dados — é o produto desligado.

**Porque não foi detetado:** seis ficheiros de teste fabricam `approval_status='APPROVED'` por `INSERT`/`UPDATE` direto. É exatamente a violação do ADR-0011 D9 que o `CLAUDE.md` §5 descreve ("foi assim que o *gating* por subscrição passou meses verde e desligado"), repetida na coluna seguinte. Em `onda-1` agrava-se: `db/seed/V901__seed_providers.sql` aprova prestadores no *seed* **dev-only** — logo funciona em desenvolvimento e falha silenciosamente em produção, que é o pior modo de falha possível.

**Como se fecha** (`backend-domain`, com o contrato já pronto):
1. Implementar `PATCH /v1/admin/providers/{providerId}/approval` em `modules/providers`, `@PreAuthorize("hasRole('ADMIN')")`, transição `compare-and-set` `PENDING → APPROVED|REJECTED`, escrita em `audit_log` (tabela V13, hoje sem escritores).
2. Acrescentar `GET /v1/providers/{providerId}` e `.../reviews` a `SecurityConfig.PUBLIC_GET_ENDPOINTS` — o contrato declara-os `security: []` e a lista (`SecurityConfig.java:66-71`) não os inclui: devolveriam 401.
3. Teste de **transição** (`qa-e2e`): prestador criado → `PENDING` → invisível na pesquisa → `PATCH` por ADMIN → visível. Só depois disto os `INSERT` diretos dos outros testes ficam toleráveis por D9.
4. Interface mínima de admin no web (`web-frontend`) — hoje 0%, e a ARQUITETURA §4.1/§19.1 exige-a.

### C2 — Mesmo defeito em quatro outras colunas de decisão

| Coluna | Lida por | Escritor de produção |
|---|---|---|
| `provider_profile.visibility_state` | `EligibilityRepository:36,66`, `ProviderSearchRepository:53`, `ProvidersService:56` | nenhum (`main`); **removida dos predicados em `onda-1`** ✔ |
| `provider_category.(provider_id, category_id)` | `EligibilityRepository:42,72`, `ProviderSearchRepository:60`, `ProviderRepository:46` | nenhum (`main`); **`ProviderRepository:170` em `onda-1`** ✔ |
| `provider_service_area.{mode,center,radius_m,region_code}` | `CoverageSql:92-102` — o predicado central do ADR-0004 | nenhum (`main`); **`ProviderRepository:190` em `onda-1`** ✔ |
| `provider_profile.rating_avg` / `rating_count` | `ORDER BY p.rating_avg DESC` (`ProviderSearchRepository:71`), `ProvidersApi.summary` | **nenhum em ambos** — `ReviewRepository:32` insere em `review` e não atualiza agregado. Já registado no ADR-0011 D9, continua aberto |
| `provider_profile.verified`, `.company_id`, `booking.scheduled_start/_end`, `message.read_at` | lidos e expostos em DTO | **nenhum em ambos** — valores de omissão permanentes |

**Como se fecha:** para `rating_avg`/`rating_count`, listener em `reviews` que recalcula o agregado na criação de avaliação (`backend-domain`), com teste da transição. Para os restantes: ou se implementa o escritor, ou se remove a coluna e o campo do DTO — expor `verified: false` constante é pior que não expor.

**Controlo estrutural que evita a recorrência** (escalar ao `arquiteto`): teste de arquitetura que, para cada coluna referida num predicado `WHERE` de módulo, exige um escritor identificável em `src/main`. Falha o build. É a única forma de este defeito não voltar pela terceira vez.

### C3 — Duas violações de invariante fechadas em verde por uma citação falsa de ADR

Isto é um defeito de processo com consequências de segurança, e é o que torna a revisão por *diff* inútil aqui.

1. `web/bff/src/routes/auth.ts:190-198` (`onda-1`) devolve `409 email-already-registered` no registo, e o comentário invoca *"divergência deliberada da anti-enumeração estrita (ADR-0012 D7.3)"* como autorização. **D7.3 proíbe-o explicitamente**: `docs/adr/0012-...:236` diz *"no registo, mesma resposta para email novo e para email já registado — caso contrário o oráculo apenas mudou de porta"*. A violação está fixada por `web/bff/test/auth.register.test.ts:117-131`, cujo nome cita o mesmo D7.3. Agrava: o registo não passa por `withNormalizedTiming`, logo o `409` é também caminho rápido.
2. `backend/src/test/java/pt/servimatch/gating/SubscriptionGatingAcrossModulesIntegrationTest.java:41-50` justifica fabricar `visibility_state` por SQL afirmando que *"o listener `billing → providers` … já tem cobertura própria"*. **Esse listener não existe.** O único consumidor de `SubscriptionActivated` é `notifications/internal/listeners/SubscriptionNotificationListener.java:8`, que só notifica.

**Como se fecha:** (a) `web-bff` uniformiza o registo para `201` + email ao titular, com `withNormalizedTiming`, e o teste passa a asseverar a indistinguibilidade; (b) `qa-e2e` corrige o javadoc e o teste; (c) controlo permanente: **toda a citação de ADR em comentário ou nome de teste que autorize uma divergência de invariante é verificada contra o texto do ADR em revisão**. Barato, e teria apanhado ambos.

### C4 — IDOR: prestador lê rascunhos de qualquer cliente

`GET /v1/providers/me/requests?status=DRAFT`. O `@RequestParam String status` (`RequestsController.java:89`) chega cru a `RequestsService.java:212-214`, sem validação contra o enum:

```java
List<String> statuses = statusFilter != null
        ? List.of(statusFilter)
        : List.of(PUBLISHED.name(), IN_NEGOTIATION.name());
```

Devolve pedidos não publicados com endereço completo e código postal. Que é defeito e não decisão prova-se pelo caminho de detalhe, que exclui `DRAFT` explicitamente (`RequestsService.java:156`). Em `onda-1` existe `parseStatusFilter` mas aplicado **só** a `listMine` — `listInbox` continua vulnerável. Nenhum teste cobre `?status=`.

**Como se fecha** (`backend-domain`): `parseStatusFilter` também em `listInbox`, com *allowlist* `{PUBLISHED, IN_NEGOTIATION}` e `400` fora dela; teste com `?status=DRAFT` e `?status=lixo`.

### C5 — Do CI verde ao sistema a correr não existe nenhum passo

Confirmado por `find`: **zero `Dockerfile`**, zero `*.tf`, zero `Chart.yaml`/`kustomization.yaml`, zero workflow de deploy ou release, zero `jib`/`buildpacks` no `pom.xml`. O `CLAUDE.md` §1 descreve `infra/` como contendo "CI/CD, IaC" — não contém nem um nem outro. `ARQUITETURA.md:765` lista destinos possíveis sem escolher nenhum; não existe ADR de deploy.

Somam-se: migrações **não reversíveis** (16 `V*`, nenhum *undo*, nenhuma estratégia); **sem graceful shutdown** (agravado por `republish-outstanding-events-on-restart: true`, `application.yml:33` — SIGTERM corta handlers a meio); **sem limites de recursos** e `hikari.initialization-fail-timeout: -1` (`:52`) faz o arranque ter sucesso sem base de dados; **zero estratégia de backup executável** apesar de `ARQUITETURA.md:889` prometer PITR.

**Como se fecha** (`platform-infra`, exige ADR novo — é decisão de arquitetura, não *patch*): ADR de deploy que escolha o alvo; Dockerfiles multi-stage para backend e BFF; workflow de release com publicação de imagem; IaC do alvo escolhido; `server.shutdown: graceful` + `timeout-per-shutdown-phase`; pool Hikari dimensionado e `initialization-fail-timeout` positivo; PITR do Postgres + backup do realm + versionamento do bucket, **com teste de restauro**.

### C6 — `application-prod.yml` só remove dois defaults de desenvolvimento

O desenho é o correto (o perfil `prod` existe para que a variável em falta rebente no arranque), mas só cobre `spring.datasource.password` e `servimatch.uploads.secret-key` (`application-prod.yml:19-25`). Continuam a cair em valores de dev, em produção, sem erro: `DB_URL`, `DB_USERNAME`, `KEYCLOAK_JWK_SET_URI`, **`KEYCLOAK_ISSUER_URI` → `http://localhost:8081/...`** (`:76` — `http`, para o IdP), `REDIS_PASSWORD` vazio, `UPLOADS_S3_ENDPOINT`/`ACCESS_KEY`.

Uma variável Keycloak em falta em produção não é falha de segurança — é indisponibilidade total silenciosa (JWKS inalcançável → 401 em tudo).

**Como se fecha** (`backend-platform`): `application-prod.yml` remove o default de **todas** as variáveis sem valor sensato em produção; teste que arranca o perfil `prod` sem ambiente e exige falha.

### ~~C7~~ — retirado: diagnóstico meu incorreto

Afirmei que `/actuator/prometheus` caía em `anyRequest().authenticated()` e era legível por qualquer `CUSTOMER`. **Falso.** `SecurityConfig.java:190` põe `/actuator/**` atrás de `hasRole("ADMIN")`, e as linhas 183-189 explicam exatamente o raciocínio que eu apresentei como se faltasse. Li as linhas 130-131 do tree pré-fusão e generalizei sem reverificar depois da fusão.

Fica em aberto um ponto menor de operação, não de segurança: com esta regra, um scraper Prometheus precisa de um JWT com role `ADMIN`. O padrão habitual é porta de gestão separada (`management.server.port`), o que resolve o *scraping* sem credenciais de administração. Melhoria, não defeito — sem severidade atribuída.

### C8 — ADR-0013 sem implementação em `main`

Os dois controlos que o `CLAUDE.md` §4 declara não negociáveis — ficheiro fora do artefacto de produção; arranque a **abortar** se as *locations* efetivas incluírem `db/seed` sem `local`/`dev` — não existem em `main`: sem `<excludes>` no `pom.xml`, sem `FlywayConfigurationCustomizer` (`grep db/seed` em `main/java` → vazio). Atenuante: `db/seed/**` também não existe em `main`, logo não há exposição atual.

**Em `onda-1` está bem feito e o ponto crítico está certo:** `platform/seed/SeedLocationsGuard.java:48-84` é um `FlywayConfigurationCustomizer`, pelo que vê as *locations* **efetivas** já com `SPRING_FLYWAY_LOCATIONS` aplicado, e **antes** de `migrate()`. Fecha o vetor que o ADR identifica. Resolve-se por fusão, não por código novo.

---

## 3. Defeitos de severidade média

| # | Defeito | Evidência | Dono |
|---|---|---|---|
| M1 | `servimatch-local-test`: client **público** com `directAccessGrantsEnabled` + audience mapper para o backend. É o cenário que o ADR-0012 D6 declara indefensável. Rotulado "dev/CI", mas vive no **mesmo** ficheiro de realm que alimenta o compose e os testes — a barreira é um comentário | `infra/keycloak/realm-servimatch.json` | `platform-infra` |
| M2 | *Takeover* de device token: `ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id` sem verificar o dono atual (o `DELETE` está corretamente restringido) | `notifications/internal/DeviceTokenRepository.java:30-31` | `backend-platform` |
| M3 | *Open redirect* encadeado: `returnUrl` sem *allowlist* → `success_url`/`cancel_url` do Stripe | `payments/web/SubscriptionController.java:87-89`, `stripe/StripePaymentGateway.java:65-66` | `backend-payments` |
| M4 | Endereço completo exposto a qualquer prestador elegível antes de compromisso (`onda-1` mitiga com `AddressExposure.java`; ausente em `main`) | `requests/internal/RequestsService.java:248-250` | `backend-domain` |
| M5 | Cadeia de correlação partida no web: `proxy.ts:17` não reencaminha `x-correlation-id` e o BFF não gera nenhum. O mobile fá-lo bem (`correlation_interceptor.dart:18`). Contradiz `ARQUITETURA.md:199` | `web/bff/src/proxy.ts:17` | `web-bff` |
| M6 | Sem controlo preventivo de PII em logs: nenhum `logback-spring.xml`, nenhum *appender* de mascaramento, nenhum teste. Cumprido hoje por disciplina, não por controlo | `application.yml:207-213` | `backend-platform` |
| M7 | `sampling.probability: 1.0` com OTLP para `http://localhost:4318` onde não há collector — ruído de erro contínuo, sem override por perfil | `application.yml:200-205` | `backend-platform` |
| M8 | Um único client `servimatch-bff` concentra DAG (autenticar-se como qualquer um) **e** *service account* `manage-users` (criar contas). O ADR-0012 D8 recomenda separar em dois clients. Rotação exigida pelo ADR não está documentada | `realm-servimatch.json` | `platform-infra` |
| M9 | Paginação de `GET /v1/search/providers` é **OFFSET** disfarçado de cursor (`"off:" + nextOffset` em Base64). Ordenação determinística, mas inserção entre páginas salta/repete e a profundidade é O(N). Agrava-se por `rating_avg` (critério de ordenação) ser constante 0 — ver C2 | `search/internal/SearchCursor.java:19-27`, `ProviderSearchRepository.java:73` | `backend-matching` |
| M10 | Sem `assetlinks.json` nem `apple-app-site-association` em lado nenhum do repositório. Sem eles o App Link não verifica e **o login mobile não fecha o ciclo** | — | `platform-infra` |
| M11 | `ARQUITETURA.md:469` e `:667-672` afirmam que `visibility_state` "foi removido pelo ADR-0011". Em `main` a coluna existe e três predicados leem-na. Documentação e código afirmam o oposto sobre o invariante de monetização | `docs/ARQUITETURA.md` vs `V4:30` | `arquiteto` |
| M12 | Race no primeiro `docker compose up`: `pg_isready` sem `-h` liga pelo socket Unix e responde "ready" antes de `001-create-databases.sh` criar a BD `keycloak`. Recupera por `restart`, mas o primeiro arranque não é limpo | `infra/docker-compose.yml:37` | `platform-infra` |
| M13 | `tail -f /dev/null` no `minio-init` e healthcheck do MinIO por `curl` (o Keycloak já contorna a ausência de `curl` com `/dev/tcp`; aqui não) | `docker-compose.yml:125,139` | `platform-infra` |
| M14 | Dois lockfiles em `web/`: `package-lock.json` **e** `pnpm-lock.yaml` + `pnpm-workspace.yaml`. O CI usa `npm ci`. Um dos dois está a mentir sobre as versões instaladas | `web/` | `web-frontend` |
| M15 | `audit_log` (V13) e `company` (V4) sem escritores nem (para `audit_log`) leitores. `company` **é** lida por `LEFT JOIN`, logo `companyName` é sempre `null`. Contradiz `ARQUITETURA §8.6` | `V13`, `ProviderRepository.java:68` | `backend-domain` |
| M16 | Dados fabricados no cliente em modo real: `providersService.ts:19-22` devolve **coordenadas fixas de Lisboa** para todos os prestadores (postas no mapa) e `memberSince: new Date()`; `providerDashboardService.ts:84` devolve `subscriptionStatus: 'PENDING'` fixo. Corrigido em `onda-1` | `web/site/src/services/http/` | `web-frontend` |
| M17 | Upload de fotografias 0% ligado: `createUploadTarget` **sem um único chamador**; o wizard anima uma barra de progresso simulada e cria o pedido sem `images` | `NewRequestWizardPage.tsx:119-127` | `web-frontend` |
| M18 | `.env.example` da raiz tem duas afirmações **falsas**: descreve `KEYCLOAK_ADMIN_API_BASE_URL` como usada pelo BFF (não é lida, nem consta do `.env.example` do BFF) e um client scope `servimatch-backend-audience` "atribuído por omissão" que não existe no realm (`clientScopes: []`; são mappers inline) | `.env.example:39-63` | `platform-infra` |
| M19 | Três índices GIN pagos e nunca lidos: `service_request.search_tsv` (sem endpoint de pesquisa de pedidos) e `category.search_tsv` (`CategoryRepository:46` lista sem `q`) | `V7:49`, `V3:20` | `db-migrations` |
| M20 | Sem geocoding (`ARQUITETURA §10.1`): `service_request.location` só é preenchido se o cliente enviar `lat`/`lon`. Sem coordenadas, o pedido é invisível ao *matching* geográfico | `RequestRepository.java:64-65` | `backend-matching` |

---

## 4. O CI não é um gate de confiança

O que verifica bem: `mvn verify` com Testcontainers, `ApplicationModules.verify()` (com guarda contra ausência do teste), Redocly lint + *breaking change* vs `main`, lint/test/build do web, `flutter analyze --fatal-infos` + test, gitleaks, Trivy CRITICAL/HIGH com `exit-code: 1`.

O que falta, por ordem de retorno:

1. **Clientes gerados nunca verificados contra o contrato.** É a causa raiz direta da deriva de `main`: `608b28d` acrescentou 8 endpoints ao contrato, `generate:api` nunca correu, o `schema.d.ts` ficou com 18 de 26 paths e o CI passou verde. `web/README.md:58-60` **afirma** que o ficheiro está commitado "para a pipeline comparar contra uma nova geração e detetar deriva" — essa comparação não existe. Correção: `npm run generate:api && git diff --exit-code` (3 linhas). Equivalente para o cliente Dart.
2. **E2E Playwright escritos e desligados.** `web/e2e/` está completo e `test:e2e` existe; o CI não o invoca.
3. **`typecheck` nunca corre**, sobretudo no BFF — cujo `build` é `tsc` que emite. Erros de tipo passam por acidente.
4. **Nenhuma conformidade backend↔contrato.** O contrato é lintado, o backend é testado, ninguém compara os dois. Sem Spring REST Docs, sem Schemathesis, sem validação de resposta.
5. **Mobile nunca compilado** — só `analyze` + `test`. Erros de Gradle/Pods/App Links só aparecem à mão.
6. **Sem execução agendada**: um CVE publicado após o último commit em `main` nunca é detetado. Sem `schedule:` nem `workflow_dispatch:`.
7. **Trivy sem `misconfig`** (`scan-type: fs`, `ignore-unfixed: true`): o compose e o realm nunca são analisados.
8. **Sem SAST** (CodeQL/Semgrep) e **sem gate de cobertura**. `docs/AGENTES.md` chama ao CI "o árbitro entre agentes"; sem conformidade de contrato nem cobertura, não arbitra o que importa.
9. **Actions fixadas por tag, não por SHA** — tags são mutáveis, e este pipeline é a única barreira antes de `main`.
10. `gitleaks-action@v3` exige `GITLEAKS_LICENSE` em repos de organização — falharia por licença, não por segredo.

---

## 5. O que está genuinamente bem feito

Registado para não ser refeito nem "corrigido" por engano.

- **Backend como Resource Server puro.** `pom.xml:89-97` só `oauth2-resource-server`; zero `keycloak-admin-client`, zero bcrypt, zero authorization-server. Nenhum dos 7 DTOs de escrita aceita password. `anyRequest().authenticated()` por omissão.
- **Tokens fora do browser.** Nenhum token em `localStorage`/`sessionStorage` — os únicos usos são tema e rascunho de pedido, com teste a provar a distinção. Nenhuma resposta do BFF devolve token.
- **BFF (ADR-0002):** PKCE completo, `state`+`nonce` verificados, cookie transitório assinado, tokens só em memória do servidor, CSRF *double-submit* com comparação em tempo constante, proxy com *allowlist* de headers (nunca reencaminha `cookie`), *silent refresh*, RFC 9457 próprio, sem CORS por desenho. `sanitizeReturnTo` normaliza `\` antes de validar, citando o CVE-2025-68470.
- **Webhooks de pagamento:** assinatura verificada **antes** de qualquer efeito, com quarentena sob chave sintética para não envenenar a chave de idempotência; `UNIQUE (gateway, raw_event_id)`; reconciliação agendada.
- **Uploads:** *magic bytes* contra o `contentType` declarado, nunca extensão; URL assinado com expiração.
- **PostGIS bem resolvido.** `geography(Point,4326)`, GIST parciais, **zero `ST_Distance`** em produção. O pré-filtro de raio constante em `CoverageSql.java:92-102` (necessário porque o raio real é coluna e o planeador não constrói a bbox) seguido do `ST_DWithin` exato, dentro de `candidates AS MATERIALIZED`, com `V16 CHECK (radius_m <= 100000)` a fechar a suposição — e `MatchingEligibilityTest` a verificar `Index Cond` por `EXPLAIN`. Trabalho de qualidade acima da média.
- **FTS português** com `immutable_unaccent()` (wrapper IMMUTABLE necessário para colunas GENERATED), `setweight('A'/'B')`, GIN + trigram, `websearch_to_tsquery` + `ts_rank`.
- **SQL injection e mass assignment: cumpridos** em todo o backend. Todos os valores externos por parâmetro nomeado; nenhum DTO de escrita expõe `status`, `approvalStatus`, `providerId`, `userId` ou `role`; transições sempre *compare-and-set* no servidor.
- **Dinheiro:** `amountCents BIGINT` + `currency CHAR(3)` sem exceção; a única conversão decimal está na fronteira do gateway e usa `RoundingMode.UNNECESSARY`.
- **Paginação keyset correta** em requests, proposals e chat (`(created_at, id)` com tie-breaker) — a exceção é a pesquisa (M9).
- **`trusted-proxies`**: vazio por omissão no backend, `X-Forwarded-For` só honrado a partir de proxy confiável, com testes; e no BFF de `onda-1` o `trust proxy` é numérico, nunca `true`.
- **Mobile:** `flutter_appauth` com *system browser*, zero webview embebido, `flutter_secure_storage` com `encryptedSharedPreferences`, access token nunca persistido, App Links `https://` com `autoVerify` e entitlements, force-update, refresh serializado com retry único no 401. ADR-0009 cumprido ponta a ponta.
- **Segredos:** nenhum `.env` versionado, em nenhum ramo, nunca (`git log --all --diff-filter=A` vazio). Service account com `manage-users`+`view-users`, **não** `realm-admin`.
- **`CorrelationIdFilter`** inserido antes do rate limit e da autenticação, pelo que 401 e 429 também são correlacionáveis.
- **Testes com Keycloak real** (`FullCustomerJourneyRealAuthIntegrationTest`) e `OpenApiContractComplianceTest` — a intenção certa, limitada pelos escritores ausentes (C1/C2).

---

## 6. Plano de execução

### Onda 0 — decisão, sequencial e bloqueante

**0.1 — Fusão de `feat/dados-reais/onda-1`. ✔ CONCLUÍDA** (`62437e1`, PR #1). A baseline é agora defensável.

**0.2 (`arquiteto`) — Reconciliar `ARQUITETURA.md` com o código** (M11) e registar em ADR o estado real de implementação do 0011/0012/0013. A fusão fechou parte da divergência (`visibility_state` saiu mesmo dos predicados, como o doc já afirmava), mas o ADR-0011 continua parcialmente por implementar — D7 (endpoint de aprovação) e D9 (`rating_avg`) estão abertos. Reverificar o texto antes de o dar por correto.

**0.3 (`platform-infra`) — Fechar C5 por decisão, não por código:** ADR de deploy que escolha o alvo. Sem essa escolha, Dockerfiles e IaC são adivinhação.

### Onda 1 — paralela, arranca já

| Agente | Trabalho | Critério de aceitação |
|---|---|---|
| `api-contract` | Nada novo. O contrato está completo e à frente das implementações | — |
| `backend-domain` | **C1** (`PATCH .../approval` + `audit_log` + `PUBLIC_GET_ENDPOINTS`), **C4** (`parseStatusFilter` em `listInbox`), **C2** (listener de `rating_avg`), M4, M15 | Prestador aprovado pelo caminho de produção aparece na pesquisa; `?status=DRAFT` devolve 400; `rating_avg` muda ao criar avaliação |
| `backend-platform` | **C6** (defaults de `prod`), **C7** (porta de gestão), M2, M6 (`logback-spring.xml` com mascaramento), M7 (sampling por perfil) | Arranque em `prod` sem ambiente **falha**; `/actuator/prometheus` fora da porta pública |
| `backend-matching` | M9 (cursor keyset na pesquisa), M20 (geocoding, ou decisão explícita de o adiar com validação a exigir `lat`/`lon`) | Página estável sob inserção concorrente |
| `backend-payments` | M3 (*allowlist* de `returnUrl`) | `returnUrl` externo rejeitado com 400 |
| `web-bff` | **C3.1** (registo indistinguível + `withNormalizedTiming`), M5 (`x-correlation-id` no proxy) | Teste que assevera igualdade de status, corpo e tempo entre email novo e existente |
| `web-frontend` | Área de **admin** (aprovação de prestadores — sem ela C1 não é utilizável), M16, M17 (upload real), M14 (um lockfile) | Admin aprova prestador pela UI; foto anexada ao pedido chega ao MinIO |
| `platform-infra` | M1 (realm separado para dev/CI), M8 (dois clients + rotação documentada), M10 (`assetlinks.json`/AASA), M12, M13, M18 | `docker compose up` limpo em volume vazio; login mobile fecha o ciclo em dispositivo |
| `db-migrations` | M19 (remover índices não lidos ou justificar), estratégia de migração reversível (C5) | — |

### Onda 2 — paralela, após Onda 1

| Agente | Trabalho | Critério de aceitação |
|---|---|---|
| `qa-e2e` | Teste de **transição** para cada coluna de C1/C2; corrigir o javadoc mentiroso de `SubscriptionGatingAcrossModulesIntegrationTest`; E2E do lado prestador (inbox → proposta → subscrição → gating por 403); ligar Playwright no CI | Nenhum `INSERT` direto de teste sem teste da transição correspondente |
| `platform-infra` | **Gate de CI** (§4, itens 1–5 primeiro — o item 1 são 3 linhas e fecha uma classe inteira de defeito) | `generate:api && git diff --exit-code` verde; E2E a correr; `typecheck` do BFF no job |
| `security-auditor` | Reauditar C1–C4 e M1–M8 fechados; verificar que nenhuma citação de ADR em comentário/teste autoriza uma divergência que o ADR proíbe | — |
| `arquiteto` | **Teste de arquitetura** que exige escritor de produção para cada coluna lida em predicado de decisão (C2, controlo estrutural) | Build falha se uma coluna de decisão perder o escritor |

### Onda 3 — prontidão operacional

`platform-infra`, após o ADR de deploy: Dockerfiles multi-stage, workflow de release, IaC, graceful shutdown + limites + pool dimensionado, stack de observabilidade no compose (collector OTel + Prometheus + Grafana), SLOs de `ARQUITETURA.md:171-186` transformados em regras de alerta, PITR + backup do realm + bucket versionado **com teste de restauro**.

---

## 7. Os dois pontos que exigem decisão sua

A primeira decisão — fundir a `onda-1` — está tomada e executada. Restam duas:

1. **O produto pode ir para produção sem interface de admin?** Se não houver forma de aprovar um prestador, `approval_status` fica `PENDING` para sempre e a pesquisa devolve sempre vazio (C1). Não é uma lacuna de conveniência: é o produto desligado. Alternativa mínima, se a UI de admin for demasiado para já: implementar só o endpoint `PATCH` com role `ADMIN` e aprovar por chamada direta até existir ecrã — mas com o teste de transição, senão o defeito continua invisível.
2. **Qual o alvo de deploy?** Sem essa escolha, C5 permanece aberto e nada do que está construído chega a um utilizador real.

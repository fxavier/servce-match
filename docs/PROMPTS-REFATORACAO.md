# Prompts — refatoração para dados reais e autenticação sem Keycloak visível

Prompts prontos a colar no Claude Code. Cada bloco é um agente. Os agentes de uma
mesma **onda** têm caminhos de escrita disjuntos e correm em paralelo; as ondas são
sequenciais.

## Objetivo da refatoração

1. O site deixa de usar a camada de mocks e passa a consumir o backend real.
2. Todos os mockdata passam a existir na base de dados como seed, para o site
   continuar a mostrar os mesmos dados credíveis.
3. Registo e login passam a formulários da própria aplicação. O utilizador nunca vê
   o Keycloak — nem redirect, nem URL, nem a palavra em mensagem de erro.

## Decisões já tomadas (não voltar a discutir)

| Tema | Decisão |
|---|---|
| Autenticação | BFF headless sobre o Keycloak: `POST /auth/login` com Direct Access Grant (client confidencial), `POST /auth/register` com a Admin REST API via service account. Backend continua **só** Resource Server. Exige ADR novo a substituir parcialmente o 0002/0009. |
| Mockdata | Seed **dev-only** em `backend/src/main/resources/db/seed/**`, banda `V900+`, carregado só nos profiles `local`/`dev`. Categorias e planos ficam no `V15` (dados de referência, correm em todo o lado). |
| Âmbito | Web + backend. Mobile intocado — o ADR-0009 (RFC 8252) continua integralmente em vigor para nativos. |

## Como correr

```bash
# um worktree por agente evita conflitos de índice quando correm em simultâneo
git worktree add ../sm-<agente> feat/<agente>/dados-reais
```

Se correres tudo no mesmo worktree, avisa cada agente: **nunca** `git commit`,
`git checkout`, `git stash`, `git restore` ou `git clean` — só `git diff` e
`git status` para leitura.

**Antes de começar**, confirma que o ambiente resolve dependências: `mvn -q
dependency:go-offline` no `backend/` e `pnpm install` no `web/`. Sem acesso ao
Maven Central, o backend não compila e os agentes de domínio entregam código não
verificado — que é o maior risco desta refatoração.

---

# Onda 0 — bloqueante

Três agentes em paralelo. Nada da Onda 1 arranca antes de os três terminarem.

## 0.1 — `arquiteto`

```
És o agente `arquiteto` do monorepo ServiMatch. Lê primeiro: CLAUDE.md (§2, §3, §4, §5),
docs/adr/0002-identidade-keycloak-oauth2-oidc.md, docs/adr/0009-autenticacao-clientes-nativos.md,
docs/adr/README.md e um ADR existente para copiares o formato MADR exato,
web/bff/src/routes/auth.ts (fluxo OIDC atual) e
backend/src/main/resources/db/migration/V15__seed_categories_and_subscription_plans.sql
(o racional de seed já documentado).

Escreves apenas em: docs/adr/0011-*.md (novo), docs/adr/0012-*.md (novo),
docs/adr/README.md, CLAUDE.md. Nada mais. Não corras git commit.

Escreve dois ADR em MADR (pt-PT), no mesmo rigor e nível de detalhe dos existentes.

ADR-0011 — Autenticação por credenciais first-party sobre o Keycloak.
Decisão do dono do produto: o utilizador nunca vê o Keycloak. Registo e login em
formulários da própria SPA; o BFF fala com o Keycloak server-to-server:
- POST /auth/register -> Admin REST API (POST /admin/realms/servimatch/users) com o
  service account do client confidencial servimatch-bff (client credentials), roles
  manage-users + view-users de realm-management. Login imediato a seguir.
- POST /auth/login -> token endpoint com grant_type=password (Direct Access Grant),
  client confidencial + secret. A resposta nunca chega ao browser: o BFF guarda
  access/refresh na sessão server-side e devolve só o cookie HttpOnly/Secure/SameSite,
  exatamente como o fluxo de authorization code faz hoje.
- POST /auth/logout deixa de devolver logoutUrl de fim de sessão SSO visível.
- O backend continua APENAS Resource Server. O invariante §4 mantém-se: o que muda é
  quem recolhe as credenciais, não quem valida a identidade.

Documenta honestamente as consequências — é aqui que está o valor do ADR:
- RFC 9700 e o OAuth 2.1 draft desaconselham/removem o ROPC. Justifica o caso restrito
  em que continua tolerado: cliente first-party, confidencial, mesmo domínio de
  confiança que o IdP, credenciais nunca persistidas nem reencaminhadas. Cita com URL.
- Perdas concretas: MFA/OTP, required actions (verificar email, atualizar password,
  aceitar termos), identity brokering e login social deixam de ser executáveis — o
  CredentialGrant devolve invalid_grant opaco nesses casos. Qualquer um destes
  requisitos obriga a reabrir este ADR. Não é extensão, é incompatibilidade.
- A deteção de brute force do Keycloak passa a ver o IP do BFF, não o do utilizador.
  Mitigação obrigatória: rate limiting por IP real no BFF antes de chamar o Keycloak,
  e resposta que não permite enumeração de utilizadores — mesma mensagem E mesmo tempo
  para email inexistente e password errada.
- O client secret do BFF passa a ser crítico: quem o tiver autentica-se como qualquer
  utilizador cuja password conheça, e o service account cria utilizadores.
- O registo cria o utilizador no Keycloak E a linha em `users`. Documenta o risco de
  divergência e a estratégia: provisioning idempotente no backend a partir do `sub` do
  token no primeiro pedido autenticado — Keycloak é a fonte de verdade, a linha local é
  derivada. Sem transação distribuída.

Marca que substitui parcialmente o ADR-0002 e o ADR-0009 apenas na recolha de
credenciais no cliente web. O mobile mantém RFC 8252.

ADR-0012 — Dados de demonstração como seed dev-only, fora das migrações versionadas.
Os fixtures de web/site/src/services/mock/fixtures/** passam a existir na base de
dados, nunca em produção. Ficheiros SQL em backend/src/main/resources/db/seed/**,
carregados por Flyway só quando o profile é local/dev (spring.flyway.locations
diferenciado), idempotentes (ON CONFLICT DO NOTHING sobre chave natural), banda de
versões V900+ para nunca colidirem com migrações reais.
Argumenta a distinção já implícita no V15: dados de REFERÊNCIA (categorias, planos) são
decisões de negócio auditáveis e correm em todo o lado; dados de DEMONSTRAÇÃO
(prestadores fictícios, pedidos, propostas, conversas, avaliações) são andaime de
desenvolvimento e não podem contaminar produção. Documenta a alternativa rejeitada
(migração V normal) e porquê.
Documenta o requisito de consistência: os utilizadores de demonstração têm de coincidir
com os do realm Keycloak local, ligados por users.keycloak_sub — o seed usa os UUID
fixos do realm, nunca gen_random_uuid(), senão o login real não encontra o perfil.
Exige que a barreira seja MECANISMO e não configuração: os ficheiros de seed não devem
sequer ir dentro do artefacto de produção, e o arranque deve abortar se as locations
efetivas incluírem db/seed sem local/dev ativo. Configuração sozinha não chega —
SPRING_FLYWAY_LOCATIONS numa variável de ambiente tem precedência sobre qualquer YAML.

Atualiza CLAUDE.md: §4 reescreve o bullet OAuth2/BFF sem enfraquecer nenhum invariante
(tokens continuam proibidos em localStorage; webview embebido continua proibido em
mobile) e acrescenta os invariantes novos (client secret fora do repositório, resposta
de login não enumerável, rate limiting por IP real); §3 acrescenta
backend/src/main/resources/db/seed/** à matriz de ownership, dono db-migrations.

Nada de listas genéricas de prós e contras — cada consequência tem de ser algo que
alguém possa vir a partir em produção. Termina com um resumo ≤25 linhas das decisões
que os outros agentes têm de respeitar.
```

## 0.2 — `api-contract`

```
És o agente `api-contract` do monorepo ServiMatch. Lê primeiro: CLAUDE.md §2 e §5;
docs/api/openapi.yaml NA ÍNTEGRA (tens de replicar o estilo, os components/schemas, o
envelope de paginação por cursor, os ProblemDetails e as convenções de security);
web/site/src/services/domainTypes.ts (a lista de lacunas está no cabeçalho, escrita por
quem construiu o site); web/site/src/services/interfaces.ts; os fixtures em
web/site/src/services/mock/fixtures/*.ts; e as migrações V4, V9, V10, V11.

Escreves apenas em docs/api/openapi.yaml. Não corras git commit.

O site vai deixar de usar mocks e passar a consumir o backend. Sete capacidades da UI
não têm endpoint. Acrescenta-os de forma ESTRITAMENTE ADITIVA (ADR-0008 e a skill
openapi-contract-first): não removes, não renomeias, não apertas validação, não mudas o
significado de enums já publicados.

1. GET /v1/providers/{providerId} — perfil público, sem auth (a página é indexável; há
   Seo.tsx e sitemap.xml). Schema novo com allOf de ProviderSummary + bio, categoryNames,
   zones [{regionCode,label}], location, ratingDistribution (contagens por estrela),
   portfolioImageUrls, memberSince. 404 para inexistente ou não VISIBLE.
2. GET /v1/providers/{providerId}/reviews — público, paginado por cursor. Serve o tipo
   ReviewWithAuthor de domainTypes.ts. authorName é PII reduzida: nome próprio +
   inicial do apelido, reduzido no SERVIDOR — justifica em comentário YAML, o endpoint é
   público e indexável. providerResponse: verifica no V10 se a coluna existe; se não
   existir, mantém o campo nullable e comenta que exige coluna nova do db-migrations.
3. GET e PUT /v1/providers/me — perfil editável do prestador (role PROVIDER). O PUT é
   substituição total das listas. 403 se não for prestador.
4. GET /v1/conversations — conversas do autenticado, paginadas por cursor. Item: id,
   counterpartName, counterpartAvatarSeed, lastMessagePreview, lastMessageAt (ambos
   nullable — a conversa existe antes da 1.ª mensagem), unreadCount, requestTitle.
5. GET /v1/subscriptions/me — estado da subscrição do prestador. CUIDADO: só usa um valor
   NONE se ele já existir no enum publicado; acrescentar valor a enum publicado parte
   switch exaustivos em apps já instaladas, e o modo de falha cai no gating. Prefere 404
   para "sem subscrição" e justifica em comentário YAML.
6. GET /v1/bookings/{bookingId} — detalhe da marcação, para o ecrã de avaliação. Só o
   cliente ou o prestador da marcação; 403 caso contrário. Inclui obrigatoriamente o
   users.id da contraparte (ex. counterpartUserId): sem ele, POST /v1/reviews é impossível
   de preencher a partir desta resposta — targetId é um users.id, mas providerId é um
   provider_profile.id. Defeito assimétrico, invisível em teste manual pelo lado do
   prestador.
7. GET /v1/requests — pedidos do cliente autenticado, filtro status opcional, cursor.
   POST /v1/requests já existe: acrescenta só a operação get ao path item, sem tocar no post.
8. GET /v1/proposals/me — propostas enviadas pelo prestador, paginadas.

Regras: erros RFC 9457 sob https://errors.servimatch.pt/, reutilizando os componentes já
definidos; dinheiro sempre amountCents inteiro + currency ISO-4217; paginação por cursor
opaco com envelope { items, page: { nextCursor } }; reutiliza schemas existentes em vez de
duplicar. NÃO documentes /auth/** — é superfície do BFF, não do contrato do backend;
acrescenta só um comentário YAML no topo a dizê-lo.

Nota de encaminhamento a registar no ficheiro: /v1/providers/me tem de ser resolvido antes
de /v1/providers/{providerId}.

Valida antes de terminares:
  npx --yes @redocly/cli@latest lint docs/api/openapi.yaml
e confirma com `git diff --numstat docs/api/openapi.yaml` que são 0 remoções. Reporta o
output da validação e, em ≤30 linhas, cada endpoint com método, path, auth, operationId e
o nome EXATO dos schemas novos — o backend e o frontend implementam contra esses nomes.
```

## 0.3 — `platform-infra`

```
És o agente `platform-infra` do monorepo ServiMatch. Lê primeiro: CLAUDE.md §3 e §4;
infra/keycloak/realm-servimatch.json NA ÍNTEGRA; infra/docker-compose.yml; infra/README.md;
.env.example da raiz; web/bff/.env.example; web/bff/src/config.ts e web/bff/src/oidc.ts.

Escreves apenas em infra/**, .github/workflows/** e .env.example da raiz. Não corras git commit.

O produto passa a ter registo e login em formulários próprios. O BFF passa a falar com o
Keycloak server-to-server: login por grant_type=password (Direct Access Grant) com o client
confidencial servimatch-bff + secret, e registo pela Admin REST API autenticado por service
account do mesmo client. O realm atual tem servimatch-bff com publicClient: false mas
directAccessGrantsEnabled: false e serviceAccountsEnabled: false — é isso que muda.

1. realm-servimatch.json:
   - servimatch-bff: directAccessGrantsEnabled: true, serviceAccountsEnabled: true,
     publicClient continua false, standardFlowEnabled como está (o authorization code fica
     como caminho de regresso e para o mobile). Garante secret de dev, claramente marcado
     como tal.
   - Service account com os client roles manage-users e view-users de realm-management. Em
     JSON de realm isto é uma entrada em `users` com username service-account-servimatch-bff,
     serviceAccountClientId: "servimatch-bff" e clientRoles: { "realm-management": [...] }.
     CONFIRMA o formato exato contra a documentação oficial de import de realm — um realm que
     não importa bloqueia todos os outros agentes.
   - Acrescenta o terceiro utilizador fixture provider.trial@servimatch.pt (Rita Nogueira,
     role PROVIDER, emailVerified true) — é o prestador SEM subscrição ativa usado pelo perfil
     de demonstração do site.
   - Atribui UUID FIXOS aos utilizadores fixture e documenta-os em infra/README.md. O seed da
     base de dados liga users.keycloak_sub a estes valores; sem id explícito no JSON, o sub
     muda a cada import e o seed parte.
   - bruteForceProtected: true com valores sensatos, E documenta em infra/README.md que com
     Direct Access Grant a proteção por IP fica cega (vê o IP do BFF) e que a mitigação real é
     rate limiting no BFF.
2. docker-compose.yml: garante que o Keycloak arranca com o realm importado e que as variáveis
   novas (secret do client, URL de admin) estão declaradas.
3. .env.example da raiz + infra/README.md: variáveis novas com valores de exemplo, nunca um
   segredo real. Documenta como obter um token por Direct Access Grant com curl para testar
   manualmente, e os três utilizadores fixture com email/password/UUID/role.
4. Verificação: python3 -c "import json;json.load(open('infra/keycloak/realm-servimatch.json'))"
   e, se houver Docker, um import a sério com quay.io/keycloak/keycloak. Se não houver Docker,
   diz-o explicitamente em vez de assumires que passou.

Nota: com apenas manage-users+view-users, um GET /roles da Admin API devolve 403 — o id da role
tem de vir de GET /users/{id}/role-mappings/realm/available, e o POST de atribuição exige a
representação completa da role. Confirma isto empiricamente se puderes e documenta em
infra/README.md; poupa horas ao agente do BFF.

Reporta em ≤25 linhas: nome do client e secret de dev, os UUID dos utilizadores fixture (o seed
depende deles), variáveis de ambiente novas e o resultado da validação.
```

---

# Onda 1 — paralela

Oito agentes com caminhos de escrita disjuntos. Arrancam todos ao mesmo tempo depois
da Onda 0.

## 1.1 — `backend-platform`

```
És o agente `backend-platform`. Escreves apenas em: backend/pom.xml,
backend/src/main/java/pt/servimatch/config/**, .../platform/**,
.../modules/*/package-info.java (TODOS, inclusive de módulos de outros agentes),
backend/src/main/resources/application*.yml, e .../modules/{uploads,notifications}/**.
Correm 7 agentes em paralelo neste worktree: nunca git commit/checkout/stash/restore/clean.
Ficheiros modificados fora do teu âmbito são de colegas — não lhes toques.

Lê primeiro: CLAUDE.md §3 e §4, os ADR 0011 e 0012 (acabados de escrever),
application.yml, todos os package-info.java existentes, e ModularityTests.java.

1. FLYWAY — seed dev-only (ADR-0012). O db-migrations vai criar db/seed/** com dados de
   demonstração que nunca podem correr em produção; só tu mexes no YAML. Base (produção):
   classpath:db/migration. Profiles local e dev: classpath:db/migration,classpath:db/seed.
   Escolhe entre blocos on-profile e ficheiros application-{local,dev}.yml conforme o que já
   existe no projeto, e confirma que application-multi-instance.yml não colide.
   A barreira tem de ser MECANISMO, não configuração:
   - exclui db/seed/** do artefacto empacotado (maven-jar-plugin; o repackage do
     spring-boot-maven-plugin reembrulha o JAR deste plugin, logo o que excluis aqui não existe
     no artefacto final). NÃO uses exclusão de <resources> — isso tira o seed também de
     target/classes e parte mvn spring-boot:run com o perfil local, que é o caso de uso a servir.
   - aborta o arranque se as locations EFETIVAS incluírem db/seed sem local/dev ativo. Um
     FlywayConfigurationCustomizer é melhor que um @Component: corre antes de migrate() e vê as
     locations já resolvidas, com perfis e SPRING_FLYWAY_LOCATIONS aplicados. Apanha também
     SPRING_PROFILES_ACTIVE=prod,dev.
   Comenta que isto é fronteira de segurança e não conveniência: um locations errado mete
   prestadores fictícios em produção, e as migrações são irreversíveis.

2. FRONTEIRAS DE MÓDULO. Só tu escreves package-info.java — é isso que impede o
   ApplicationModules.verify() de ser auto-certificação. Ganham funcionalidade nesta onda:
   chat (hoje só tem package-info, sem implementação), providers, reviews, bookings, billing,
   requests, proposals. Declara allowedDependencies MÍNIMAS, com javadoc de uma linha por
   entrada a dizer que capacidade concreta a justifica. Não abras nada "por precaução".
   Atenção a ciclos: providers->reviews fecha ciclo com reviews->providers; bookings->reviews
   idem. Onde a alternativa Java for impossível por ciclo, o caminho é o acesso SQL entre
   módulos do ADR-0010 — lê-o antes de decidires, e escala ao arquiteto em vez de autorizares.
   Nota: eventos consumidos entre módulos têm de viver no pacote de TOPO do módulo (como
   proposals.ProposalAccepted). Um subpacote sem @NamedInterface não é importável de fora.

3. SEGURANÇA E PROVISIONING (ADR-0011). O backend continua APENAS Resource Server — não
   acrescentes emissão de tokens nem endpoints de auth.
   O ADR-0011 exige provisioning idempotente: no primeiro pedido autenticado de um sub
   desconhecido, a linha em `users` é criada a partir das claims. Implementa na PLATAFORMA, não
   num módulo: define a porta (interface, ex. UserProvisioningPort com
   void ensureProvisioned(String sub, String email, String displayName)) em platform/security/ e
   um filtro que a invoca; a implementação é do módulo users (a tabela é dele). Usa
   ObjectProvider em vez de bean no-op com @ConditionalOnMissingBean — fora de
   auto-configuração a ordem de registo não é garantida e o no-op pode ganhar em silêncio.
   Não faças um SELECT por pedido: cache dos sub já provisionados (Caffeine se já estiver no
   POM). Falhas logadas sem PII e sem derrubar o pedido — um filtro escapa ao
   GlobalExceptionHandler.
   Confirma a cobertura de autorização dos endpoints novos: /v1/providers/{id} e
   /v1/providers/{id}/reviews públicos; /v1/providers/me, /v1/subscriptions/me e
   /v1/proposals/me exigem PROVIDER; GET /v1/requests exige CUSTOMER; /v1/conversations e
   /v1/bookings/{id} exigem autenticação. CUIDADO COM A ORDEM DOS MATCHERS: um padrão
   /v1/providers/* casa com "me" — /v1/providers/me tem de ser avaliado ANTES da regra pública.
   Segue o padrão que já existe (caminho decide público-vs-autenticado, roles em @PreAuthorize);
   não introduzas um segundo mecanismo.
   Revê ainda o que management.endpoints.web.exposure.include expõe: um /actuator/prometheus na
   porta aplicacional cai em anyRequest().authenticated() e fica legível por qualquer conta
   criada pelo registo público que acabámos de abrir.

4. Se precisares de dependência nova, o POM é teu — mas mantém a baseline do ADR-0003
   (Boot 3.5.x, Modulith 1.4.x, Java 21). Não subas versões.

O ModularityTests só corre com rede. Antes de terminares, faz a passagem que ele faria:
extrai os `import pt.servimatch.modules.*` reais de cada módulo (grep -rho), compara com as
allowedDependencies declaradas, corre deteção de ciclos sobre o grafo, e confirma que nenhum
import cruzado toca em subpacote de outro módulo (internal/web/dto). Reporta a tabela.

Reporta ainda: o allowedDependencies final de cada módulo tocado; a assinatura exata e o
pacote da porta de provisioning; as regras de autorização acrescentadas; e a configuração
Flyway por profile. Os outros agentes dependem destes nomes.
```

## 1.2 — `db-migrations`

```
És o agente `db-migrations`. Escreves apenas em
backend/src/main/resources/db/migration/** (só ficheiros NOVOS — as existentes são imutáveis)
e backend/src/main/resources/db/seed/** (novo). NÃO mexas em application*.yml, é do
backend-platform. Correm 7 agentes em paralelo: nunca git commit/checkout/stash/restore/clean.

Lê primeiro: docs/adr/0012-*.md (é a tua especificação), CLAUDE.md §5, TODAS as migrações
V*.sql existentes, a skill .claude/skills/flyway-postgis-migration/, TODOS os fixtures em
web/site/src/services/mock/fixtures/*.ts e mock/db.ts (são os dados que vais transformar em
SQL), docs/api/openapi.yaml (os campos que o contrato promete têm de existir no schema), e
infra/keycloak/realm-servimatch.json + infra/README.md (os UUID dos utilizadores fixture).

BLOCO A — migração de schema.
Percorre CADA schema novo do openapi.yaml e confirma campo a campo se há coluna que o sirva.
Cria o que faltar. Casos que já sabemos que faltam:
- resposta do prestador a uma avaliação: a tabela review não a tem. Acrescenta também o
  momento — uma resposta sem instante não é ordenável nem recuperável por backfill.
- portefólio do prestador: se não houver tabela, cria-a. Referencia upload_asset em vez de
  guardar URLs cruas: guardar URLs de S3 em texto quebra o padrão de URL assinado com
  expiração exigido pelo CLAUDE.md §4.
- unreadCount das conversas é o caso não-óbvio: exige saber o que cada participante já leu.
  Marca de água por participante (last_read_at em conversation) é preferível a tabela de
  participantes se a conversa for bilateral por construção — decide, justifica em comentário.
- ordenação de conversas por atividade: MAX(sent_at) por conversa não é indexável. Considera
  denormalizar last_message_at/preview com trigger + backfill.
Índices para TODOS os caminhos de leitura novos, incluindo os compostos que a paginação por
cursor exige: (dono, created_at DESC, id DESC). Um índice só por dono obriga a ordenar em
memória e o custo cresce com o histórico — que é exatamente o que o cursor existe para evitar.
Antes de criar, mede: verifica cardinalidade real antes de decidir se o filtro opcional entra
no índice, e remove índices que fiquem prefixo estrito dos novos (cada índice a mais é escrita
mais lenta em todas as inserções).

BLOCO B — seed dev-only.
Transforma TODOS os fixtures do mock em SQL. É o coração da tarefa: o site tem de continuar a
mostrar os mesmos dados credíveis depois de a camada de mocks desaparecer.
1. UUID fixos, determinísticos, escritos à mão. NUNCA gen_random_uuid() — sem isso o seed não é
   re-executável e nada consegue referenciar nada.
2. Idempotente: ON CONFLICT DO NOTHING. Tem de correr duas vezes sem erro.
3. Os utilizadores de demonstração ligam ao Keycloak por users.keycloak_sub, com os UUID EXATOS
   documentados em infra/README.md pelo platform-infra. Se o login real não encontrar o perfil
   semeado, todo o trabalho dos outros agentes fica invisível — é o ponto de falha mais provável
   de toda a refatoração.
4. Os restantes prestadores das fixtures precisam de linha em users (provider_profile.user_id é
   NOT NULL). keycloak_sub sintético com prefixo que não possa colidir com um sub real, e
   comentário a dizer que não fazem login.
5. Geografia: respeita os CHECK de provider_service_area (RADIUS exige center+radius_m e
   region_code NULL; ADMIN_REGION o inverso) e o máximo de raio. Coordenadas reais de Portugal
   coerentes com web/site/src/constants/regions.ts.
6. rating_avg/rating_count têm de bater certo com as review semeadas — calcula-os por UPDATE
   agregado no fim, nunca à mão. Números inconsistentes fazem a UI mentir.
7. Categorias e planos JÁ existem no V15 e correm em todo o lado. Não os dupliques: referencia
   por slug/code, resolvendo o id por SELECT. Se um fixture usar categoria que o V15 não tem,
   semeia-a no seed ou reporta — não alteres o V15.
8. Banda de versões V900+, ficheiros divididos por área.

VERIFICAÇÃO — obrigatória e a sério. Levanta um PostgreSQL local com PostGIS, corre todas as
migrações seguidas do seed por ordem com psql (não precisas de Flyway), e depois:
- confirma zero erros;
- corre o seed uma SEGUNDA vez e confirma zero erros (idempotência);
- mostra contagens por tabela, rating_avg vs média real das reviews, e um SELECT que simule o
  perfil público a partir do keycloak_sub do prestador de demonstração;
- confirma por EXPLAIN que os índices novos são usados pelos caminhos novos.
Se o PostGIS não instalar, valida o SQL estaticamente e diz claramente que a verificação foi
estática — não finjas que correu.

Reporta: ficheiros criados; colunas/tabelas novas e porquê; contagens do seed; output das
queries de verificação; e qualquer campo do contrato que não consigas servir (para reencaminhar
ao api-contract).
```

## 1.3 — `backend-domain-providers`

```
És o agente `backend-domain-providers` (módulos providers e users). Escreves apenas em
backend/src/main/java/pt/servimatch/modules/{providers,users}/** e nos testes correspondentes,
EXCETO package-info.java (é do backend-platform). Correm 7 agentes em paralelo: nunca
git commit/checkout/stash/restore/clean.

Antes de escreveres uma classe, abre o equivalente num módulo maduro (modules/search,
modules/requests) e replica EXATAMENTE o mecanismo de acesso a dados, os *Row records, a
construção de DTO, o Problems.java do módulo, o controller e a paginação por cursor. Confirma
cada import com grep/ls contra um ficheiro real — nunca de memória.

Lê: CLAUDE.md §2/§4/§5, docs/adr/0010, docs/api/openapi.yaml (os teus schemas — o contrato é a
fonte de verdade, não inventes campos nem os omitas), modules/search/internal/* (já lê
provider_profile com PostGIS), as migrações V4/V10/V16, web/site/src/services/domainTypes.ts e
as páginas ProviderProfilePage.tsx e ProviderProfileEditPage.tsx.

1. GET /v1/providers/{providerId} — perfil público, sem autenticação.
   404 se não existir, ou se não estiver APPROVED/VISIBLE — mesma mensagem para os três casos,
   senão é oráculo de enumeração. ratingDistribution é UMA query agregada
   (count(*) FILTER (WHERE rating = n)), não cinco; atenção que review.target_id aponta para
   users.id, não para provider_profile.id — confirma no V10. location e bio são nullable: trata
   o NULL, não o mascares. zones[].label não está persistido (só region_code): resolve a etiqueta
   de um catálogo estático no módulo, alinhado com web/site/src/constants/regions.ts, e comenta
   que é catálogo e não tabela. Poucas queries fixas, sem N+1.

2. GET e PUT /v1/providers/me — role PROVIDER. Resolve pelo sub do JWT -> users.keycloak_sub ->
   provider_profile. Utilizador com a role mas sem perfil: decide entre 404 e criação implícita,
   justifica em comentário, e sê consistente com o resto. O PUT é substituição total das listas:
   DELETE+INSERT numa transação, com TODA a validação antes da primeira escrita (radius dentro
   do máximo, region_code conhecido, categorias existentes, mode coerente com o CHECK) — uma
   violação de FK a escapar como 500 é um bug, o correto é 4xx ProblemDetails. verified,
   approval_status, visibility_state e rating_avg NÃO são editáveis: ignora-os explicitamente e
   comenta porquê.

3. Provisioning JIT (ADR-0011). Só o teu módulo escreve em `users`. Implementa a porta que o
   backend-platform definiu em platform/security/ como @Component em users/internal/. Idempotente
   e resistente a corrida (ON CONFLICT sobre keycloak_sub — confirma no V2 que o índice único
   existe; se não existir, PARA e reporta, a migração não é tua). Dois pedidos simultâneos do
   mesmo utilizador novo não podem gerar duas linhas nem um 500. Nunca escrevas email em log.

4. Visibilidade derivada da subscrição. provider_profile.visibility_state tem o default HIDDEN e
   pode nunca ser escrito por código aplicacional — verifica. Se ninguém consumir os eventos de
   subscrição de modules/billing, um prestador com subscrição ativa fica HIDDEN para sempre em
   produção, a inbox dá 403 permanente e search/matching nunca o devolvem. O seed mascara isto
   (semeia VISIBLE), o que torna o bug invisível em dev e garantido em produção. Implementa o
   listener com @ApplicationModuleListener (não @EventListener síncrono — a event_publication
   existe para isso). Escreve estado ABSOLUTO e idempotente (WHERE visibility_state <> :alvo),
   com a condição de aprovação DENTRO do UPDATE para decisão e escrita serem atómicas. ACTIVE ->
   VISIBLE só se APPROVED (uma subscrição paga não contorna moderação); estados terminais ->
   HIDDEN; desconhecido -> HIDDEN (falha fechada). Não apanhes exceções: deixar propagar é o que
   faz o registo reentregar. Se precisares de allowedDependencies novas, PARA e pede ao
   backend-platform.
   Nota: um listener reage a eventos, e uma subscrição que JÁ está ativa nunca reemite o evento —
   portanto o parque existente fica preso em HIDDEN. É preciso um varrimento de reconciliação;
   se não couber no teu módulo, reporta com a assinatura de que precisas.

5. APIs de módulo em lote. Outros módulos vão precisar de nomes de utilizador e da tradução
   provider_profile.id -> users.id por página. Expõe LOTE (findByIds), nunca singular — uma
   variante singular num caminho por página é N+1 garantido. Semântica: uma query, mapa vazio
   para null/vazio sem tocar na BD, ids inexistentes ausentes sem exceção, sem filtro de
   autorização, com javadoc a dizer que o chamador é que valida. Não exponhas email — estes
   nomes acabam em respostas públicas.

Testes unitários no estilo dos existentes, caminho principal e pelo menos um caso de erro por
endpoint. Reporta: ficheiros; as queries principais e os índices de que dependem; as decisões
justificadas; e o que precisas de outro agente (coluna -> db-migrations; fronteira ->
backend-platform; campo impossível -> api-contract).
```

## 1.4 — `backend-domain-social`

```
És o agente `backend-domain-social` (módulos chat, reviews e bookings). Escreves apenas em
backend/src/main/java/pt/servimatch/modules/{chat,reviews,bookings}/** e nos testes
correspondentes, EXCETO package-info.java. Não toques em
src/test/java/pt/servimatch/{ModularityTests.java,config,platform,testsupport}. Correm 7
agentes em paralelo: nunca git commit/checkout/stash/restore/clean.

Antes de escreveres, replica exatamente o padrão de modules/requests e modules/proposals
(acesso a dados, *Row records, DTO, Problems.java, controller, CursorCodec). Confirma cada
import com grep/ls contra ficheiro real.

Lê: CLAUDE.md §2/§4/§5, docs/adr/0010, docs/api/openapi.yaml (os teus schemas), as migrações
V9 e V10, modules/reviews/** e modules/bookings/** (o que já existe), e — importante —
modules/chat/ tem SÓ package-info.java, sem implementação nenhuma: vais construí-la de raiz.
Lê também os fixtures conversations.ts e reviews.ts, domainTypes.ts, e as páginas
ConversationsListPage, ConversationThreadPage, NewReviewPage e ProviderProfilePage.

1. MÓDULO CHAT — de raiz.
   GET /v1/conversations: conversas do autenticado, cursor, com counterpartName,
   counterpartAvatarSeed, lastMessagePreview, lastMessageAt (nullable — a conversa existe antes
   da 1.ª mensagem), unreadCount, requestTitle.
   Ordena por lastMessageAt DESC com desempate estável por id. SEM desempate, a paginação por
   cursor salta e repete linhas quando há timestamps iguais — é o erro clássico aqui.
   Evita N+1 agressivamente: última mensagem por conversa em LATERAL ou window function, não uma
   query por conversa; unreadCount idem; nomes resolvidos EM LOTE por página através das APIs
   públicas dos outros módulos, nunca num ciclo e nunca por JOIN que atravesse a fronteira.
   GET e POST /v1/conversations/{id}/messages: já estão no contrato e continuam sem implementação.
   AUTORIZAÇÃO É O PONTO CRÍTICO: só um participante lê ou escreve, e a verificação faz-se no
   WHERE da query que lê, não com um if depois de carregar tudo. Decide 403 vs 404, justifica, e
   sê consistente. O POST é escrita não idempotente -> aceita Idempotency-Key (confirma se o
   filtro de platform/idempotency/ é automático ou exige registo). Valida o corpo (não vazio,
   comprimento coerente com a coluna) e devolve 400 ProblemDetails — nunca deixes uma violação de
   constraint virar 500. Ao enviar, atualiza o que o unreadCount do interlocutor precisa.

2. REVIEWS — GET /v1/providers/{providerId}/reviews.
   Público, cursor, createdAt DESC com desempate por id. authorName reduzido NO SERVIDOR a nome
   próprio + inicial do apelido, como função testável e não SQL inline — o endpoint é público e
   indexável, e o nome completo cria um registo permanente e pesquisável de quem contratou o quê.
   Junta corretamente: review.target_id -> users.id -> provider_profile.user_id (confirma no V10).
   404 se o prestador não existir ou não estiver visível, coerente com GET /v1/providers/{id}.

3. BOOKINGS — GET /v1/bookings/{bookingId}.
   Só o cliente ou o prestador da marcação; verifica em SQL. Inclui o users.id da contraparte
   (o contrato exige-o): quando o autenticado é o cliente, é o users.id POR TRÁS do providerId
   — providerId é o id do perfil, não do utilizador, e é essa tradução em falta que torna
   POST /v1/reviews impossível de preencher do lado do cliente. Resolve-o a partir de valores que
   a cadeia de autorização já leu, sem query adicional. Testa a assimetria nos dois sentidos: é o
   caso que ninguém apanha em teste manual porque se testa sempre pelo lado do prestador.
   canReview = COMPLETED e ainda sem avaliação deste autor. Se o "sem avaliação" fechar ciclo de
   módulos, pede autorização ao arquiteto para um EXISTS em SQL (ADR-0010) em vez de degradares a
   regra em silêncio — degradada, a UI mostra o botão e o servidor devolve 409.

APIs de módulo de outros (títulos de pedido, nomes de utilizador) NÃO têm filtro de autorização,
por design. Só lhes podes passar ids que já validaste — passar um @PathVariable diretamente
transforma-as num oráculo de dados alheios.

Testes unitários, com destaque para os de autorização negada, que são os que mais importam aqui.
Reporta: ficheiros; queries principais e índices; a decisão 403-vs-404 justificada; nomes de
coluna que assumiste do db-migrations e precisam de reconciliação; e bloqueios para outros
agentes.
```

## 1.5 — `backend-domain-requests`

```
És o agente `backend-domain-requests` (módulos requests e proposals). Escreves apenas em
backend/src/main/java/pt/servimatch/modules/{requests,proposals}/** e nos testes
correspondentes, EXCETO package-info.java. Correm 7 agentes em paralelo: nunca
git commit/checkout/stash/restore/clean.

Estás em dois módulos já maduros — replica exatamente o que lá está e confirma cada import com
grep/ls. O teu modelo mais próximo é o GET /v1/providers/me/requests (inbox do prestador) que já
existe.

Lê: CLAUDE.md §2/§4/§5, docs/api/openapi.yaml (os endpoints novos e os schemas
ServiceRequestPage/ProposalPage que tens de reutilizar sem alterar), todo o modules/requests/**
e modules/proposals/**, as migrações V7 e V8, e os mocks requestsService.ts e proposalsService.ts
(a semântica exata que a UI espera manter).

1. GET /v1/requests — pedidos do cliente autenticado (role CUSTOMER).
   Filtro status opcional; valor inválido é 400 ProblemDetails, NUNCA lista vazia silenciosa (uma
   lista vazia faz o cliente acreditar que não há dados). Cursor com desempate estável por id —
   segue o CursorCodec existente, não inventes um segundo esquema. Resolve o cliente pelo sub do
   JWT; filtra por dono EM SQL, nunca em memória. POST /v1/requests já existe: acrescenta a
   operação GET ao controller sem alterar o POST. Imagens e categoria da página inteira em UMA
   query cada (WHERE ... IN (:ids)), nunca uma por pedido.

2. GET /v1/proposals/me — propostas do prestador autenticado (role PROVIDER).
   Mesmas regras de ordenação estável e ausência de N+1. Utilizador com a role mas sem perfil de
   prestador: decide entre lista vazia e 403, justifica, e alinha com o que o GET /v1/providers/me
   faz (outro agente implementa-o; lê o código dele perto do fim).
   O mock devolve contexto do pedido (título, estado) junto de cada proposta. Verifica se cabe nos
   campos já publicados; se NÃO couber, NÃO inventes o campo — devolve o que o contrato permite e
   reporta a lacuna ao api-contract. Inventar campo num schema publicado é o que o CLAUDE.md §2
   proíbe.

3. EXPOSIÇÃO DE MORADA — a decisão de privacidade mais consequente do backend.
   O service_request leva morada completa, código postal e coordenadas exatas de casa do cliente.
   Verifica o predicado atual de visibilidade para prestadores. Se qualquer PROVIDER conseguir ler
   a morada de qualquer pedido não-DRAFT, isso transforma qualquer conta de prestador numa
   ferramenta de colheita de moradas residenciais com nome associado — e o acesso mantém-se depois
   de a adjudicação ir para outro. Regra: morada exata só ao dono e a ADMIN; qualquer prestador
   recebe granularidade de zona (sem line1/line2, código postal truncado ao prefixo, coordenadas
   arredondadas). O arredondamento é DETERMINÍSTICO, nunca jitter aleatório — repetir N vezes e
   tirar a média recupera o ponto exato.
   A decisão é função do viewer, não da linha: calcula-se uma vez por pedido HTTP e aplica-se à
   página inteira, sem lookup por item. Não deixes existir um caminho de construção de DTO que não
   decida explicitamente quanta morada expõe — um valor por omissão convida a repetir o defeito.
   Se o prestador adjudicado deixar de ter caminho para a morada exata, isso é uma regressão de
   fluxo: reporta ao api-contract o campo que falta em BookingDetail.

4. GATING POR SUBSCRIÇÃO. É regra de domínio no servidor (CLAUDE.md §4) e o cliente nunca é
   autoridade sobre o seu plano. Verifica como a inbox já o aplica e REUTILIZA o mecanismo — não
   escrevas uma segunda cópia da regra. O gating limita escrever e descobrir, não ler o que já é
   teu: decide se se aplica a GET /v1/proposals/me e defende a decisão em comentário.

5. URLs de imagem servem-se assinados com expiração via UploadsApi, nunca construídos à mão
   (CLAUDE.md §4). Se a fronteira do módulo ainda não permitir o import, pede ao backend-platform
   e deixa TODO ligado ao pedido concreto.

Testes: caminho principal e pelo menos um caso de erro por endpoint, incluindo obrigatoriamente
"cliente A não vê pedidos de cliente B" e "status inválido -> 400". Reporta: ficheiros; queries e
índices em falta (a migração é do db-migrations, pede); decisões justificadas; lacunas de
contrato.
```

## 1.6 — `backend-payments`

```
És o agente `backend-payments` (módulos billing e payments). Escreves apenas em
backend/src/main/java/pt/servimatch/modules/{billing,payments}/** e nos testes correspondentes,
EXCETO package-info.java. Correm 7 agentes em paralelo: nunca git commit/checkout/stash/restore/clean.

Lê: CLAUDE.md §4 — em particular "o gating por subscrição é uma regra de domínio no servidor; um
cliente nunca é autoridade sobre o seu plano; a UI só espelha o que o servidor decide". É o
coração desta tarefa. Depois docs/adr/0007, docs/api/openapi.yaml (o endpoint novo
GET /v1/subscriptions/me e o schema respetivo), todo o modules/billing/**, a migração V11, e
web/site/src/routes/pages/ProviderSubscriptionPage.tsx.

1. GET /v1/subscriptions/me (role PROVIDER).
   Resolve o prestador com o ProviderAccountResolver que já existe — não escrevas uma segunda
   resolução. Um prestador pode ter várias linhas em subscription ao longo do tempo: define
   explicitamente a regra (a não-terminal mais recente) e implementa-a em SQL determinístico com
   ORDER BY estável. Ambiguidade aqui traduz-se em gating errado. Confirma no enum quais os
   estados terminais. PAST_DUE é o caso subtil: é uma subscrição existente mas possivelmente sem
   direito a gating — devolve-a e NÃO decidas o gating dentro deste endpoint. Verifica onde o
   gating é aplicado hoje e confirma que não crias uma segunda fonte de verdade; escreve
   explicitamente em comentário que a UI espelha e o servidor decide.
   "Sem subscrição" é 404 ProblemDetails com o type do contrato — nunca 200 com corpo vazio nem
   status a null (acrescentar um valor NONE ao enum parte switch exaustivos em apps instaladas, e
   o modo de falha cai no gating).

2. EVENTOS DE SUBSCRIÇÃO COMO API PÚBLICA. Se os eventos (SubscriptionActivated/PastDue/
   Expired/Cancelled) viverem num subpacote sem @NamedInterface, NÃO são importáveis de fora do
   módulo — e o módulo providers precisa deles para manter visibility_state. Move-os para o pacote
   de topo, à imagem de proposals.ProposalAccepted. Não alteres a forma dos records: só o pacote.
   ATENÇÃO: a tabela event_publication guarda o nome qualificado do tipo. Mudar de pacote invalida
   publicações ainda por entregar que estejam persistidas — em produção isso é uma migração de
   dados a sério, e o modo de falha é silencioso (o prestador paga, o evento fica preso, fica
   HIDDEN). Escreve isto em comentário no código e PEDE a migração ao db-migrations.

3. RECONCILIAÇÃO. Um listener reage a eventos, mas uma subscrição que JÁ está ativa nunca reemite
   o evento. Logo, o parque existente fica preso no default HIDDEN e a pesquisa devolve zero — e
   o seed mascara isto, portanto só é observável em produção. É preciso um varrimento de
   reconciliação.
   CUIDADO COM O CICLO: se billing tem allowedDependencies vazias e providers já depende de
   billing, chamar ProvidersApi a partir de billing fecha ciclo e o ModularityTests rebenta. NÃO
   abras a dependência — inverte o fluxo: billing expõe a leitura (que prestadores devem estar
   visíveis / escondidos) e o job vive em providers, que já tem a fronteira aberta. Deriva os
   estados de um único sítio (o enum), nunca copiados no SQL — foi de cópias da mesma regra que o
   defeito nasceu. As duas listas têm de ser disjuntas por construção, senão a reconciliação
   alterna VISIBLE/HIDDEN.
   Confirma explicitamente (não assumas) se é preciso locking em multi-instância: se as escritas
   forem UPDATE condicionais de valor absoluto, duas instâncias serializam na linha e a segunda
   afeta zero linhas — desperdício, não corrupção.

4. Confirma que GET /v1/subscription-plans serve tudo o que a página de preços real precisa e que
   o dinheiro sai como amountCents + currency. Se faltar campo, reporta ao api-contract — não
   inventes. POST /v1/subscriptions continua como está; não lhe toques salvo bug real.

Testes: prestador com subscrição ativa; só histórico terminal (-> 404); PAST_DUE; utilizador
autenticado sem perfil de prestador. Reporta: a regra de seleção da subscrição corrente e os
estados terminais; onde o gating é realmente aplicado hoje (ficheiro e linha) e confirmação de
que não o duplicaste; e as lacunas para outros agentes.
```

## 1.7 — `web-bff`

```
És o agente `web-bff`. Escreves apenas em web/bff/**. NÃO escrevas em web/site/** — há um agente
a trabalhar lá em paralelo (podes lê-lo). Nunca git commit/checkout/stash/restore/clean.

Aqui tens toolchain a sério: pnpm funciona e há Vitest configurado. CORRE os testes e o
typecheck — não entregues nada por inspeção visual.

Lê: docs/adr/0011-*.md (é a tua especificação completa — lê-o inteiro antes de escrever uma
linha), CLAUDE.md §4, todo o web/bff/src/**, todo o web/bff/test/**, e infra/README.md, onde o
platform-infra documentou o realm, os UUID fixos e as peculiaridades da Admin API do Keycloak que
descobriu a correr um Keycloak a sério.

1. POST /auth/register. Corpo: email, password, nome, papel (cliente ou prestador).
   - token do service account (client_credentials) COM CACHE e reobtenção em 401, não um pedido
     novo por registo;
   - POST /admin/realms/servimatch/users com enabled: true, emailVerified: true e credenciais
     type=password temporary=false;
   - emailVerified: true e ZERO requiredActions são OBRIGATÓRIOS: com uma required action pendente
     (VERIFY_EMAIL, UPDATE_PASSWORD), o Direct Access Grant a seguir falha com invalid_grant opaco
     e o utilizador fica preso sem mensagem útil. O ADR-0011 documenta-o como incompatibilidade
     conhecida;
   - atribui a role de realm. Nota: com apenas manage-users+view-users, GET /roles dá 403 — o id
     da role vem de GET /users/{id}/role-mappings/realm/available e o POST exige a representação
     completa;
   - faz login imediatamente e devolve a sessão;
   - ROLLBACK: se a atribuição de role falhar depois de o utilizador ser criado, apaga-o (uma conta
     sem role é pior que inexistente). Se o apagar também falhar, regista o órfão de forma
     correlacionável, sem PII;
   - email já existente -> 409. Nota o conflito com a anti-enumeração: no REGISTO revelar que o
     email existe é inevitável (o utilizador precisa de saber); no LOGIN é proibido. Trata os dois
     de forma deliberadamente diferente e comenta porquê;
   - valida a password antes de chamar o Keycloak e traduz os erros de política para
     ProblemDetails em português, a partir de um conjunto fechado — nunca reencaminhes a mensagem
     do IdP em bruto.

2. POST /auth/login. grant_type=password com client_id + client_secret do client confidencial.
   OS TOKENS NUNCA CHEGAM AO BROWSER: guarda access/refresh/id na sessão server-side existente e
   devolve só o cookie HttpOnly/Secure/SameSite, reutilizando sessions.create e setSessionCookie —
   não escrevas um segundo mecanismo de sessão.
   ANTI-ENUMERAÇÃO: email inexistente e password errada devolvem a mesma resposta E o mesmo tempo.
   Um PISO mínimo de latência NÃO CHEGA e é ele próprio um oráculo: o caminho "a conta existe" paga
   derivação de hash e a contabilidade de força bruta do Keycloak (quickLoginCheckMilliSeconds),
   logo excede o piso e a diferença continua mensurável. Usa PRAZO FIXO acima do p99 do caminho
   lento, com quantização para o múltiplo seguinte quando o trabalho o exceder, e uma válvula de
   escape com telemetria para uma degradação do IdP não segurar pedidos indefinidamente.
   O TESTE só vale se o duplo do Keycloak SIMULAR a assimetria — dormir no caminho da conta
   existente e não no outro. Um teste que afirma "ambos >= X" passa sempre e não testa nada:
   assere a DIFERENÇA entre os dois, não o mínimo. Falsifica a tua própria suite (repõe a
   semântica de piso e confirma que o teste fica vermelho) antes de a dares por boa.
   RATE LIMITING por IP real E por email, antes de chamar o Keycloak — é a mitigação real, não um
   extra, porque a proteção do Keycloak passa a ver o IP do BFF. Configura trust proxy com número
   explícito de saltos; um trust proxy permissivo torna o limite contornável com X-Forwarded-For
   forjado. O pedido do atacante não pode gastar a quota de força bruta da vítima.
   Nunca registes password, email ou token em log.

3. POST /auth/logout: revoga o refresh token, destrói a sessão, e DEIXA de devolver logoutUrl (o
   utilizador não pode ser reencaminhado para um fim de sessão do IdP visível). O site consome
   logoutUrl hoje — muda o contrato do BFF e documenta-o; o agente do site trata do lado dele.

4. CICLO DE VIDA DA SESSÃO. Verifica se existe renovação com o refresh token: sem ela a sessão
   morre ao fim de minutos e o site parte de forma intermitente, que é o pior tipo de bug.
   Implementa-a com proteção contra renovações concorrentes do mesmo session.id. E acrescenta o que
   provavelmente falta: TTL ABSOLUTO fixado na criação e imune à renovação (senão uma sessão que
   vaze é permanente enquanto o refresh renovar), varrimento dos expirados, id novo em cada login
   (fixação de sessão), e destruição da sessão anterior do mesmo utilizador. Testa a expiração com
   relógio controlado, nunca com sleep.

5. Mantém o fluxo OIDC antigo no código como caminho de regresso, DESATIVADO por configuração
   (flag em config.ts, por omissão desligada) — o ADR-0011 prevê o regresso e o mobile continua em
   RFC 8252. Confirma que não é reativável por cabeçalho nem por parâmetro de pedido. Atenção ao
   conflito entre o GET /auth/login antigo e o POST novo: são métodos diferentes, mas testa-o.

6. CSRF: confirma que login, registo e logout ficam cobertos, ou justifica explicitamente porque
   não precisam. Não deixes a decisão implícita.

7. Atualiza web/bff/.env.example e config.ts (URL de admin, secret, rate limiting, flag do fluxo
   legado, TTL de sessão). Nunca um segredo real. Falha no arranque com mensagem clara se faltar
   variável obrigatória — melhor rebentar no boot que às 3h da manhã.

Verificação obrigatória: npx tsc --noEmit && pnpm test. Não terminas com typecheck a falhar nem
testes vermelhos. Reporta: endpoints novos com corpo de pedido e resposta EXATOS (o agente do site
implementa contra isto), variáveis novas, decisões de segurança, e os números reais de tsc/test.
```

## 1.8 — `web-site`

```
És o agente `web-site`. Escreves apenas em web/site/** e web/e2e/**. NÃO escrevas em web/bff/** —
há um agente a construir lá os endpoints de auth que vais consumir; lê o código dele, sobretudo
web/bff/src/routes/auth.ts, e VOLTA A LÊ-LO perto do fim para confirmares as assinaturas reais em
vez de as assumires. Nunca git commit/checkout/stash/restore/clean.

Aqui tens toolchain a sério. CORRE typecheck, lint, testes e build — não entregues por inspeção
visual.

Lê: CLAUDE.md §2/§4/§5, os ADR 0011 e 0012, docs/api/openapi.yaml (foi muito alargado — todos os
endpoints de que precisavas passaram a existir), todo o web/site/src/services/**, todo o
web/site/src/features/auth/**, LoginPage.tsx, DevMockPanel.tsx e .env.example.

OBJETIVO 1 — o site deixa de usar mockdata.
Os dados de demonstração passaram todos para a base de dados (seed dev-only) e o backend serve-os.
- Elimina web/site/src/services/mock/** (fixtures, db, latency, mockProblem, currentUser) e a
  bifurcação em services/index.ts: services passa a ser sempre a implementação HTTP.
- Remove VITE_USE_MOCKS de .env.example, de vite-env.d.ts e de todo o código. Remove o painel de
  mocks e os perfis de demonstração de features/auth/demoProfiles.ts — o login passa a ser real e
  um seletor de perfil falso deixa de fazer sentido.
- services/http/notImplemented.ts tem de DESAPARECER, e com ele todos os notImplementedInContract:
  perfil público do prestador, avaliações de prestador, perfil editável, lista de conversas,
  estado da subscrição, detalhe de marcação, os meus pedidos, as minhas propostas. Todos têm
  endpoint agora — implementa-os.
- REGENERA web/site/src/api/generated/schema.d.ts a partir do openapi.yaml (vê o script no
  package.json). Código gerado nunca se edita à mão.
- Os testes sobre a camada mock desaparecem com ela: substitui-os por testes equivalentes sobre a
  camada HTTP com fetch mockado, para não perderes cobertura.

Coisas que VÃO PARTIR se as ignorares:
- ProviderProfile.location e .bio são NULLABLE no contrato (a cobertura pode ser só por região).
  domainTypes.ts declara-os não-nuláveis e a página faz provider.location.lat — parte. Corrige
  tipos e render.
- GET /v1/subscriptions/me devolve 404 quando não há subscrição (não há valor NONE no enum).
  Mapeia 404 -> "sem subscrição"; não deixes borbulhar como erro.
- POST /auth/logout deixa de devolver logoutUrl: bffClient.ts e AuthContext.tsx consomem-no hoje.
- ProposalsService.listMine não aceita status, mas o contrato publica-o. Acrescenta.
- ratingAvg/ratingCount reais são MUITO menores que os das fixtures (3 avaliações, não 214) — o
  seed recalcula-os a partir das avaliações verdadeiras. Não é bug: a UI tem de aguentar números
  pequenos e estados vazios com dignidade, e nada de agregados fabricados no cliente para encher
  um gráfico.
- Campos opcionais (requestTitle, lastMessageAt, comment) passam a vir mesmo null: omite linhas em
  vez de renderizar "null".
- Se um ecrã precisar de um campo que o contrato não tem, é lacuna a reportar ao api-contract —
  não o inventes no cliente.

OBJETIVO 2 — registo e login sem Keycloak visível.
O utilizador nunca vê o Keycloak: nem redirect, nem URL, nem a palavra em mensagem de erro.
- Reescreve LoginPage como formulário próprio (email + password) e cria uma página de REGISTO
  (nome, email, password, escolha entre cliente e prestador), com rota no router.
- Consomem POST /auth/login e POST /auth/register do BFF. Lê as assinaturas reais no código dele.
- Validação no cliente com o estilo já usado no projeto (vê features/requests/wizardSchemas.ts). A
  validação do cliente é conveniência; a autoridade é o servidor.
- NUNCA distingas na UI "email não existe" de "password errada" — o BFF devolve deliberadamente a
  mesma resposta e a UI não pode desfazer essa proteção com uma mensagem mais prestável.
- O login demora ~1s por desenho (anti-enumeração): estado de carregamento, botão desativado, sem
  duplo submit. Não tentes "otimizar" o tempo.
- AuthContext: sem redirect, sem logoutUrl, mantendo GET /auth/me como fonte de verdade da sessão.
  NUNCA guardes token, password ou perfil sensível em localStorage/sessionStorage.
- Depois do registo o utilizador fica autenticado (o BFF faz login imediato): reencaminha conforme
  o papel, reutilizando lib/returnTo.ts, que já está protegido contra open redirect. Um ÚNICO
  ponto de navegação pós-sessão — dois replace concorrentes anulam-se.

OBJETIVO 3 — coerência.
web/e2e/** é teu. Com o site a falar com o backend real e o fluxo de autenticação mudado, uma
suite que testa o Authorization Code passa a verde e afirma o contrário do que o sistema faz —
pior que nenhuma. Atualiza os duplos (incluindo os endpoints da Admin API que o registo usa) ou
reaponta os testes; decide e explica. Assere explicitamente que a página nunca menciona o IdP e
que nenhum cookie legível contém um JWT. Atualiza o README e a secção "O que falta ligar ao
backend real", que passa a estar quase vazia.

Verificação obrigatória: npx tsc --noEmit && pnpm lint && pnpm test && pnpm build. Não terminas
com typecheck a falhar, lint sujo, testes vermelhos ou build partido. Um teste que parte por causa
da remoção dos mocks reescreve-se; se for genuinamente obsoleto, apaga-se E diz-se qual e porquê.
Reporta os números reais, não impressões.
```

---

# Onda 2 — verificação

Depois de a Onda 1 fechar. Os dois primeiros correm em paralelo; o terceiro depois.

## 2.1 — `qa-e2e`

```
És o agente `qa-e2e`. Escreves em backend/src/test/** (testes de integração transversais) e podes
ler tudo. Nunca git commit/checkout/stash/restore/clean.

O objetivo é provar que a refatoração funciona ponta a ponta, não que compila.

1. Corre o build completo: mvn -B verify no backend, e no web pnpm -r typecheck/lint/test/build.
   Reporta TODOS os falhanços com ficheiro e mensagem. O ModularityTests é o mais provável de
   rebentar — a matriz de allowedDependencies não é validável sem ele.
2. Levanta o ambiente local (docker compose): Postgres com as migrações e o seed, Keycloak com o
   realm, MinIO. Confirma que o seed correu e que os três utilizadores de demonstração existem na
   base de dados COM o keycloak_sub certo. Este é o ponto de falha mais provável de toda a
   refatoração: se o sub não bater certo, o login funciona e o perfil não aparece.
3. Teste de integração do fluxo completo, com base de dados real (Testcontainers, ver a skill
   testcontainers-integration-test): registo -> sessão -> publicar pedido -> prestador vê na inbox
   -> envia proposta -> cliente aceita -> conversa criada -> marcação -> avaliação.
4. Verifica, com pedidos reais, que os dados que o site mostra vêm do seed: pesquisa de
   prestadores devolve resultados, o perfil público abre, a lista de conversas tem conteúdo, o
   estado de subscrição do prestador de demonstração está correto e o do prestador trial devolve
   404.
5. Testes de autorização negada em todos os endpoints novos: cliente A com id de cliente B,
   não-participante numa conversa, marcação alheia. Um IDOR aqui é a falha mais provável e a mais
   cara.
6. Confirma que nenhum token é observável a partir do browser: inspeciona as respostas de
   /auth/login e /auth/me e os cookies.

Reporta uma lista de defeitos ordenada por gravidade, cada um com ficheiro, reprodução e o agente
dono. Não corrijas código de outros módulos — reporta.
```

## 2.2 — `security-auditor`

```
És o agente `security-auditor`. És ESTRITAMENTE READ-ONLY: não escreves, não corriges, não crias
ficheiros no repositório. Só lês, verificas e reportas.

Lê primeiro CLAUDE.md §4 (é a régua), os ADR 0002, 0009, 0011 e 0012, e a skill
keycloak-oidc-security. Usa git diff contra o commit base para veres o que mudou — mas não
confies só no diff: alguns problemas estão no que NÃO mudou.

A. Fluxo de credenciais no BFF (web/bff/src/**, incluindo os testes).
   Algum token chega ao browser, no corpo, num cookie legível ou em log? Prova-o lendo o código.
   Cookies HttpOnly+Secure+SameSite, e o SameSite é o correto para este fluxo? A anti-enumeração
   é real — mesma resposta E mesmo tempo — ou o mecanismo de latência é ele próprio um oráculo (um
   piso menor que o caminho lento deixa a diferença mensurável)? Há teste que DISTINGA os
   caminhos, ou só afirma que ambos passam de um mínimo? Rate limiting por IP e por email, e o
   trust proxy impede um X-Forwarded-For forjado de o contornar? O store é em memória: o que
   acontece com múltiplas instâncias, e está documentado? CSRF cobre login, registo e logout? O
   client secret e o token do service account aparecem em log, erro ou resposta? O rollback do
   registo deixa janela com utilizador sem role? A sessão expira em absoluto, ou vive enquanto o
   refresh renovar? O id de sessão é regenerado no login (fixação de sessão)? O fluxo OIDC antigo
   é reativável por cabeçalho ou parâmetro?

B. Autorização nos endpoints novos do backend. Para CADA um: a autorização é em SQL ou antes de
   ler dados, não com um if depois de carregar tudo? Um utilizador lê dados de outro trocando um
   id no path (IDOR)? Presta atenção especial a /v1/bookings/{id}, /v1/conversations/{id}/messages,
   /v1/requests e /v1/proposals/me. Os endpoints públicos expõem PII ou identificadores internos —
   o nome do autor é reduzido no SERVIDOR? Sai algum users.id, email ou booking_id? A ordem dos
   matchers impede que /v1/providers/me caia na regra pública (um * casa com "me")? E as APIs de
   módulo sem filtro de autorização (findByIds, findTitlesByIds): algum chamador lhes passa ids
   vindos diretamente do cliente? Isso seria um oráculo de enumeração — verifica TODOS os
   chamadores.

C. Seed e profiles. spring.flyway.locations só inclui db/seed em local/dev? Há caminho em que
   produção o apanhe — combinações de profiles (prod,dev), precedência de SPRING_FLYWAY_LOCATIONS
   sobre YAML, ficheiros de seed dentro do artefacto empacotado? O seed contém segredos? Os
   keycloak_sub sintéticos podem colidir com subs reais? O secret de dev do realm está marcado
   como tal?

D. O que o compilador não verifica. Procura imports inexistentes, assinaturas divergentes entre
   chamador e chamado, SQL a referenciar colunas que não existem nas migrações, e @PreAuthorize em
   falta em endpoints que o contrato marca como exigindo role mas que o SecurityConfig deixa em
   anyRequest().authenticated().

E. Regressões silenciosas. visibility_state passou a ser a única autoridade de elegibilidade: o
   que acontece no PRIMEIRO deploy, antes de qualquer evento novo? Há reparação do estado
   existente, ou o marketplace fica vazio? Confirma se o job de reconciliação existe em CÓDIGO ou
   se é só uma intenção escrita no ADR. Se os eventos mudaram de pacote, falta a migração de
   event_publication?

Ordena por gravidade real (Crítico/Alto/Médio/Baixo). Para cada achado: ficheiro:linha, o que
está errado, COMO SE EXPLORA ou como falha em concreto, e a correção mínima. "Melhorar validação"
não é um achado. Se auditares uma área e ela estiver bem, DIZ que a auditaste e está bem — a
ausência de achado só vale se souberes que foi procurada. Se não conseguires verificar algo, di-lo
em vez de o dar por bom.
```

## 2.3 — reconciliação

Depois dos relatórios da Onda 2, os defeitos cruzados voltam ao agente dono. Padrão do
prompt de reconciliação:

```
Achado da auditoria/QA, gravidade <X>, e és o dono do caminho que falta.

<O sintoma exato, com ficheiro:linha.>
<Como falha ou como se explora, em concreto.>
<Por que razão não é visível hoje — se o seed ou um teste o mascara, diz.>

Corrige. <Restrição de fronteira relevante: que módulo é teu, o que NÃO podes tocar, e a quem
pedir o resto.>
<Se houver decisão arquitetural envolvida: quem já a arbitrou e qual foi — o agente não a
reabre.>

Acrescenta o teste que fixa o caso. Reporta o que mudou e o que fica para outro agente.
```

---

# Riscos conhecidos

| Risco | Sintoma | Mitigação |
|---|---|---|
| `keycloak_sub` do seed não bate certo com o realm | Login funciona, perfil não aparece, site parece vazio | Item 3 do prompt do `db-migrations`; verificação explícita na Onda 2 |
| `visibility_state` preso em `HIDDEN` | Pesquisa e matching devolvem zero em produção; o seed mascara em dev | Job de reconciliação (prompt 1.6 item 3), não só o listener |
| Enumeração de utilizadores no login | Atacante mede tempos e enumera a base | Prazo fixo, não piso; teste que assere a diferença |
| Morada exposta a qualquer prestador | Colheita de moradas residenciais com nome associado | Prompt 1.5 item 3 |
| Seed em produção | Prestadores fictícios numa base real, irreversível | Exclusão do artefacto + guarda no arranque (prompt 1.1 item 1) |
| Sem acesso ao Maven Central | Backend não compila; agentes de domínio entregam código não verificado | Confirmar `dependency:go-offline` ANTES de arrancar |
| `ModularityTests` vermelho no primeiro build | `allowedDependencies` desalinhadas com os imports reais | Passagem de coerência no fim do prompt 1.1 |

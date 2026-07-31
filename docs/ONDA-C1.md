# Onda C1 — plano de execução e prompts

Baseline: `main` @ `62437e1` (pós-fusão da `onda-1`).
Objetivo: fechar **C1** (aprovação de prestador — o defeito que mantém a pesquisa vazia em produção), **C3.1** (regressão de anti-enumeração no registo) e **C4** (IDOR em `listInbox`).
Referência: `docs/ESTADO-DO-SISTEMA.md`.

---

## 0. Agentes: não são precisos agentes novos

O roster em `.claude/agents/` já cobre esta onda com âmbitos disjuntos. Criar um
agente novo para `modules/providers` ou para `web/site` duplicaria um dono
existente e quebraria a matriz do `CLAUDE.md` §3 — que é precisamente o que torna
a execução paralela segura.

Agentes usados, e porquê cada um:

| Agente | Âmbito nesta onda | Porque é ele |
|---|---|---|
| `db-migrations` | `V17` — colunas de decisão administrativa | Único dono de `db/migration/**` |
| `backend-platform` | `platform/audit/**`, `package-info.java` de `providers` | Único dono de `platform/**` e das fronteiras de módulo |
| `backend-domain-providers` | `PATCH /v1/admin/providers/{id}/approval` | Dono de `modules/providers/**` |
| `backend-domain-requests` | C4 — `listInbox` | Dono de `modules/requests/**` |
| `web-bff` | C3.1 — registo | Dono de `web/bff/**` |
| `web-site` | Área `/admin` + E2E | Dono de `web/site/**` e `web/e2e/**` |
| `qa-e2e` | Teste de transição | Dono de `backend/src/test/**` transversal |
| `security-auditor` | Revisão final | Leitura apenas |

## Skills novas a instalar antes de arrancar

Duas skills novas suportam esta onda. Estão em `docs/_skills-para-instalar/` e
**ainda não estão ativas** — uma skill só é carregada a partir de
`.claude/skills/`. A partir da raiz do repositório:

```bash
mv docs/_skills-para-instalar/estado-com-escritor       .claude/skills/
mv docs/_skills-para-instalar/admin-moderation-endpoint .claude/skills/
rmdir docs/_skills-para-instalar
```

Verifica com `ls .claude/skills/` — devem passar a ser 11 diretórios.
Sem este passo, os prompts B1 e C1 mandam ler skills que não existem, e os
agentes seguem sem elas.

- **`estado-com-escritor`** — o procedimento do ADR-0011 D9. É o controlo que
  impede a quarta repetição do defeito. Obrigatória para `backend-domain-providers`
  e `qa-e2e`.
- **`admin-moderation-endpoint`** — padrão de endpoint de moderação: `ROLE_ADMIN`,
  transições como allowlist, `409`, `audit_log` transacional, `Idempotency-Key`.

---

## 1. Factos verificados que os agentes não devem redescobrir

Estabelecidos contra `62437e1`. Poupam uma ronda de exploração a cada agente.

- O contrato **já tem** o endpoint: `openapi.yaml:800`, `operationId: decideProviderApproval`. O `api-contract` **não entra nesta onda** — não há alteração de contrato a fazer.
- Transições que o contrato exige: `PENDING → APPROVED`, `PENDING → REJECTED`, `APPROVED → SUSPENDED`. Qualquer outra → `409`.
- `reason` é **obrigatório** para `REJECTED` e `SUSPENDED` (ausência → `422`), opcional para `APPROVED`.
- Dois enums distintos no contrato: `ProviderApprovalStatus` `[PENDING, APPROVED, REJECTED, SUSPENDED]` e `ProviderApprovalDecision` `[APPROVED, REJECTED, SUSPENDED]`. `PENDING` nunca é destino.
- Resposta `ProviderApproval` exige `providerId`, `approvalStatus`, `decidedBy`, `decidedAt`; `reason` é nullable.
- `V4:27` **já aceita** `SUSPENDED` no `CHECK` — a migração **não** mexe no enum.
- `provider_profile` **não tem** `decided_by`/`decided_at`/`reason` → migração necessária.
- `audit_log` **já existe** (V13) com a forma certa (`actor_id`, `action`, `target_type`, `target_id`, `metadata` JSONB, `correlation_id`). Não precisa de migração — precisa do **primeiro escritor** do projeto.
- `SecurityConfig` já tem `PUBLIC_GET_ENDPOINTS`, `AUTHENTICATED_BEFORE_PUBLIC_GET_ENDPOINTS` e `/actuator/**` atrás de `hasRole("ADMIN")`. O `PATCH /v1/admin/**` cai em `anyRequest().authenticated()` — a autorização de role faz-se por `@PreAuthorize` no controller.
- `providers/package-info.java` tem hoje `allowedDependencies = {modules.users, modules.billing, modules.categories, modules.uploads}`.
- Único escritor de `approval_status` em todo o `src/main/java`: **nenhum**. A única ocorrência é a leitura em `EligibilityRepository:79`.

---

## 2. Ondas

Paralelismo dentro de cada onda; as ondas são sequenciais.

**Onda A (paralela, 5 agentes)** — `db-migrations`, `backend-platform`, `backend-domain-requests`, `web-bff`, `web-site`.
Caminhos disjuntos. `web-site` arranca já porque trabalha contra o contrato, não contra a implementação (CLAUDE.md §2).

**Onda B (1 agente)** — `backend-domain-providers`. Depende da migração (A1) e do `AuditLogWriter` (A2).

**Onda C (paralela, 2 agentes)** — `qa-e2e` e `security-auditor`. Depois de B.

Isolamento: um *git worktree* por agente. Nenhum agente faz `git commit`,
`checkout`, `stash`, `restore` ou `clean` — só `diff`/`status`.

---

## 3. Prompts prontos

Cada bloco é colável tal e qual no Claude Code. Os cinco da Onda A podem correr
em simultâneo.

### A1 — `db-migrations`

```
Usa o agente db-migrations. Lê primeiro CLAUDE.md, docs/adr/0011-*.md e a skill
flyway-postgis-migration.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeito C1. provider_profile.approval_status
é lido por predicados de elegibilidade e nunca escrito em produção. Vamos
implementar PATCH /v1/admin/providers/{providerId}/approval, que precisa de
persistir quem decidiu, quando e porquê.

Tarefa: cria V17__provider_approval_decision.sql com as colunas que hoje faltam
em provider_profile:
- approval_reason      TEXT NULL          (motivo da decisão; obrigatório no
                                           serviço para REJECTED/SUSPENDED, mas
                                           nullable no schema porque APPROVED
                                           pode não ter motivo)
- approval_decided_by  UUID NULL REFERENCES users(id) ON DELETE SET NULL
- approval_decided_at  TIMESTAMPTZ NULL

Restrições e verificações:
- NÃO toques no CHECK de approval_status: V4:27 já aceita
  ('PENDING','APPROVED','REJECTED','SUSPENDED'). Confirma antes de escrever.
- Nullable é correto: as linhas existentes estão em PENDING e nunca foram
  decididas. Não inventes um DEFAULT nem faças backfill.
- Acrescenta um CHECK de coerência: se approval_status <> 'PENDING' então
  approval_decided_by e approval_decided_at são NOT NULL. Justifica em comentário
  SQL. Verifica que as linhas existentes (todas PENDING) satisfazem o CHECK.
- Índice: avalia se idx_provider_profile_visibility (V4:53, sobre
  (visibility_state, approval_status)) ainda faz sentido agora que
  visibility_state saiu de todos os predicados de produção — o ADR-0011 D1 manda
  substituí-lo por um índice só sobre approval_status. Se concordares, fá-lo
  nesta migração e explica porquê; se discordares, di-lo e não mexas.
- Comentário de cabeçalho a citar ADR-0011 D7 e o defeito C1.

Não escrevas em backend/src/main/java, pom.xml, application*.yml nem db/seed.
Critério de aceitação: a migração aplica em base limpa e sobre uma base já
migrada; explica como verificaste.
```

### A2 — `backend-platform`

```
Usa o agente backend-platform. Lê primeiro CLAUDE.md (§3 e §4), docs/adr/0001,
0010 e 0011, e a skill spring-modulith-module.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeito C1. A tabela audit_log existe desde
V13 e não tem um único escritor em todo o projeto — CLAUDE.md §4 e ARQUITETURA
§8.6 exigem auditoria de ações sensíveis. A primeira ação auditada será a decisão
administrativa de aprovação de prestador, implementada por
backend-domain-providers na onda seguinte.

Tarefa 1 — cria platform/audit:
- Uma API mínima que os módulos possam chamar para registar uma ação:
  actor_id, action, target_type, target_id, metadata (JSONB), correlation_id.
- correlation_id vem de CorrelationIdSupport.currentOrNull(), NUNCA de parâmetro
  do chamador.
- Participa na transação do chamador (nada de REQUIRES_NEW): auditoria fora da
  transação é auditoria que se perde exatamente quando é precisa. Documenta esta
  escolha em javadoc.
- PII proibida no metadata (CLAUDE.md §4): o alvo identifica-se por UUID. Se
  conseguires impor isto por tipo em vez de por convenção, fá-lo; se não,
  documenta a regra no javadoc da API.
- Escrita por JdbcClient, coerente com o resto do backend (não há JPA no projeto).

Tarefa 2 — fronteiras de módulo:
- Decide e declara como modules/providers pode chamar platform/audit sem violar
  ApplicationModules.verify(). Verifica primeiro como platform/idempotency e
  platform/error são consumidos pelos módulos hoje e segue o mesmo padrão — não
  inventes um novo.
- Se for preciso alterar providers/package-info.java (allowedDependencies ou
  @NamedInterface), fá-lo tu: esses ficheiros são teus mesmo dentro de módulos de
  outros agentes. Documenta a dependência nova com motivo, no estilo do
  package-info atual.

Não escrevas em modules/**/ (exceto package-info.java), db/migration/**, web/**
nem mobile/**.
Critério de aceitação: ApplicationModules.verify() passa; teste unitário do
escritor de auditoria a cobrir caminho principal e um caso de erro.
```

### A3 — `backend-domain-requests`

```
Usa o agente backend-domain-requests. Lê primeiro CLAUDE.md.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeito C4 — IDOR confirmado em 62437e1.

Em backend/src/main/java/pt/servimatch/modules/requests/internal/RequestsService.java
existe parseStatusFilter (linha ~174) e é aplicado em listMine (linha ~161). Mas
listInbox (linha ~259) continua a passar o statusFilter cru:

    List<String> statuses = statusFilter != null
            ? List.of(statusFilter)
            : List.of(PUBLISHED.name(), IN_NEGOTIATION.name());

Consequência: GET /v1/providers/me/requests?status=DRAFT devolve pedidos ainda
não publicados de qualquer cliente, incluindo morada completa e código postal. O
caminho de detalhe exclui DRAFT explicitamente (linha ~156), o que prova que isto
é defeito e não decisão.

Tarefa:
- Aplica validação também em listInbox. Nota que a allowlist do inbox NÃO é a
  mesma de listMine: o cliente pode legitimamente listar os seus DRAFT; o
  prestador não. Para o inbox a allowlist é {PUBLISHED, IN_NEGOTIATION}. Decide
  se reutilizas parseStatusFilter com um parâmetro de allowlist ou se crias um
  segundo método — escolhe e justifica.
- Valor fora da allowlist → 400 Problem Details (RFC 9457), type sob
  https://errors.servimatch.pt/. Não devolvas 403: o estado pedido é inválido
  para este endpoint, não é uma questão de permissão.
- Testes: ?status=DRAFT rejeitado com 400; ?status=lixo rejeitado com 400;
  ?status=PUBLISHED aceite; sem parâmetro mantém o comportamento atual. Verifica
  que nenhum teste existente dependia do comportamento antigo.
- Verifica se o mesmo padrão (RequestParam de estado a chegar cru a uma query)
  existe noutro sítio do teu âmbito. Se existir, corrige e diz onde.

Não escrevas fora de modules/requests/** e dos seus testes.
```

### A4 — `web-bff`

```
Usa o agente web-bff. Lê primeiro CLAUDE.md §4 e docs/adr/0012-*.md — em especial
a decisão D7.3, e lê-a por inteiro antes de escrever código.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeito C3.1. Regressão introduzida pela
fusão da onda-1.

web/bff/src/routes/auth.ts:199 devolve 409 'email-already-registered' quando o
email já existe, contra 201 para email novo. É um oráculo de enumeração de
utilizadores. O comentário nas linhas ~190-198 invoca "divergência deliberada da
anti-enumeração estrita (ADR-0012 D7.3)" como autorização — mas D7.3
(docs/adr/0012-*.md:236) diz exatamente o contrário: "no registo, mesma resposta
para email novo e para email já registado — caso contrário o oráculo apenas mudou
de porta". CLAUDE.md §4 diz o mesmo. O comentário está errado e o teste
web/bff/test/auth.register.test.ts:117-131 fixa a violação em verde.

Tarefa:
- Registo com email já existente passa a devolver a MESMA resposta que um registo
  novo: mesmo status, mesmo corpo, mesmo tempo. O aviso ao titular vai por email,
  nunca na resposta HTTP.
- O caminho de conflito TEM de passar por withNormalizedTiming (web/bff/src/loginTiming.ts),
  como o login já faz. Hoje não passa, pelo que o 409 é também um caminho rápido —
  corrigir só o status code deixaria o oráculo aberto por temporização.
- Corrige o comentário que cita mal o ADR. Não deixes uma citação de ADR que
  afirme o contrário do que o ADR diz.
- Reescreve auth.register.test.ts: em vez de assegurar o 409, assevera a
  indistinguibilidade — status igual, corpo igual, e tempo dentro da mesma janela
  quantizada, entre email novo e email já registado.
- Se o envio de email ao titular ainda não existir, NÃO o inventes: implementa a
  resposta indistinguível, deixa o ponto de extensão explícito e reporta a lacuna
  no fim. Não bloqueies nisso.

Não escrevas fora de web/bff/**. Confirma que continua a valer o invariante:
nenhuma resposta do BFF ao browser contém access_token ou refresh_token.
```

### A5 — `web-site`

```
Usa o agente web-site. Lê primeiro CLAUDE.md, docs/ARQUITETURA.md §4.1 e §19.1, e
a skill openapi-contract-first.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeito C1. Não existe área de administração
no web/site. Sem forma de aprovar um prestador, approval_status fica PENDING para
sempre e GET /v1/search/providers devolve vazio para qualquer consulta em
produção. É o produto desligado.

O contrato JÁ tem o endpoint (openapi.yaml:800, operationId decideProviderApproval)
e o schema.d.ts já está sincronizado (26/26 paths). Trabalha contra o contrato: o
backend está a ser implementado em paralelo pelo backend-domain-providers, e não
esperas por ele.

Tarefa — área /admin, protegida por role ADMIN:
- Lista de prestadores pendentes de aprovação, com detalhe suficiente para decidir.
- Ação de decisão: APPROVED, REJECTED, SUSPENDED. Motivo obrigatório para
  REJECTED e SUSPENDED (o servidor devolve 422 se faltar) e opcional para
  APPROVED — reflete isso na validação do formulário, sem duplicar a regra de
  negócio: o servidor continua a ser a autoridade.
- Transições válidas: PENDING → APPROVED|REJECTED, APPROVED → SUSPENDED. A UI não
  oferece transições inválidas, mas trata 409 do servidor sem partir — o cliente
  nunca é autoridade sobre o estado.
- Envia Idempotency-Key na decisão (o contrato aceita-o) para que um duplo clique
  não produza duas decisões.
- Erros: usa o tratamento de Problem Details que já existe no site. 403 para não-ADMIN.
- Estados de loading/erro/vazio, como no resto do site. Reutiliza os componentes
  de components/ui — não crias um design paralelo para o admin.

Restrições:
- ProtectedRoute não é autoritativo (declara-o no próprio ficheiro): a proteção
  real é do servidor. A rota /admin esconde a UI, não protege o dado.
- Nada de dados fabricados no caminho real. Se um campo não vier do contrato, não
  o inventes — este é o defeito M16 que a onda-1 acabou de fechar; não o reabras.

Acrescenta um teste E2E Playwright em web/e2e do fluxo admin (o mock-backend em
web/e2e/mock-backend precisa do endpoint novo — acrescenta-o lá, é teu).
Critério de aceitação: lint, typecheck e testes verdes; o fluxo aprovar/rejeitar
funciona contra o mock-backend.
```

### B1 — `backend-domain-providers` (depende de A1 e A2)

```
Usa o agente backend-domain-providers. Lê primeiro CLAUDE.md, docs/adr/0011-*.md
(sobretudo D7 e D9), e as skills admin-moderation-endpoint e estado-com-escritor.
Lê ambas as skills antes de escrever código — a segunda define o critério pelo
qual este trabalho será aceite.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeito C1 — o mais grave do sistema.
provider_profile.approval_status é lido por EligibilityRepository:79,
ProviderSearchRepository e ProvidersService, tem DEFAULT 'PENDING', e NÃO TEM UM
ÚNICO ESCRITOR em src/main/java. Nenhum prestador é jamais aprovado; a pesquisa
devolve 0 resultados para qualquer consulta em produção. Os testes fabricam
'APPROVED' por SQL direto em seis ficheiros, e o seed dev-only também — por isso
funciona em desenvolvimento e falha em silêncio em produção.

Já feito por outros agentes nesta onda (confirma antes de assumir):
- V17 acrescentou approval_reason, approval_decided_by, approval_decided_at.
- platform/audit expõe um escritor de audit_log; providers/package-info.java já
  declara a dependência necessária.

Tarefa: implementa PATCH /v1/admin/providers/{providerId}/approval conforme
openapi.yaml:800. Segue a skill admin-moderation-endpoint na íntegra. Pontos
não negociáveis:
- @PreAuthorize("hasRole('ADMIN')") em método público. Role verificada antes de
  carregar o alvo, para não criar oráculo 403/404.
- Dois tipos Java distintos, espelhando o contrato: ProviderApprovalStatus (4
  valores) e ProviderApprovalDecision (3 — PENDING nunca é destino).
- Transições como allowlist declarativa: PENDING→APPROVED, PENDING→REJECTED,
  APPROVED→SUSPENDED. Qualquer outra → 409 com o estado atual no corpo.
- reason obrigatório para REJECTED e SUSPENDED → 422 se faltar. Opcional para
  APPROVED. O reason é PII potencial: guarda-o na coluna, NUNCA em log.
- UPDATE ... WHERE id = :id AND approval_status = :esperado. Zero linhas → relê e
  devolve 409.
- Escrita em audit_log na MESMA transação, action "provider.approval.decided",
  metadata com {from, to} — sem email, nome ou telefone.
- Idempotency-Key via platform/idempotency. Não escrevas mecanismo novo.
- decidedBy é o UUID do administrador resolvido a partir do sub do JWT.

Verificação obrigatória antes de fechares (skill estado-com-escritor):
1. grep -rn "SET approval_status" backend/src/main/java tem agora de devolver a
   TUA linha. Antes devolvia vazio.
2. Escreve o teste de transição no teu âmbito (modules/providers): prestador
   criado pelo caminho de produção fica PENDING; PATCH por ADMIN muda para
   APPROVED; PATCH inválido devolve 409; sem role ADMIN devolve 403. O teste
   ponta-a-ponta que liga isto à pesquisa é do qa-e2e — não o escrevas tu, mas
   deixa claro no relatório final o que ele tem de cobrir.
3. Confirma que ProvidersApi/checkEligibility passa a poder mudar de resultado em
   produção. Se descobrires que ainda há outra metade da conjunção sem escritor,
   PARA e reporta — não a contornes.

Não escrevas em db/migration/**, platform/**, package-info.java, config/**,
pom.xml, web/** nem noutros módulos. Precisas de algo aí? Pede e reporta.
```

### C1 — `qa-e2e` (depois de B1)

```
Usa o agente qa-e2e. Lê primeiro CLAUDE.md §5, docs/adr/0011-*.md (D9) e a skill
estado-com-escritor. Lê também a skill testcontainers-integration-test.

Contexto: docs/ESTADO-DO-SISTEMA.md, defeitos C1 e C3. O endpoint de aprovação
acabou de ser implementado pelo backend-domain-providers.

Tarefa 1 — teste de transição ponta-a-ponta (é o que fecha o C1 de verdade):
Prestador criado pelo caminho de produção → confirma que NÃO aparece em
GET /v1/search/providers nem no inbox de matching → PATCH aprovação por ADMIN →
confirma que PASSA a aparecer. Assevera os DOIS lados: se só asseverares o lado
positivo, um regresso do defeito volta a passar despercebido. Nada de SQL direto
para pôr o prestador em APPROVED — o objetivo do teste é exatamente exercitar a
escrita.

Tarefa 2 — limpar a dívida de fixtures (ADR-0011 D9):
Seis ficheiros de teste fabricam approval_status='APPROVED' por INSERT/UPDATE
direto. Agora que existe transição de produção, esses INSERT passam a ser
toleráveis como atalho de setup — mas cada um tem de ter um comentário que nomeie
o teste da Tarefa 1. Faz esse varrimento.

Tarefa 3 — corrigir uma mentira documentada:
backend/src/test/java/pt/servimatch/gating/SubscriptionGatingAcrossModulesIntegrationTest.java,
javadoc nas linhas ~41-50, afirma que "o listener billing → providers que escreve
provider_profile.visibility_state ... já tem cobertura própria ... ver
SubscriptionLifecycleStateMachineTest". Esse listener NUNCA existiu e esse teste
não o cobre. A onda-1 removeu visibility_state de todos os predicados de
produção, pelo que o javadoc está agora duplamente errado. Corrige o texto para
descrever o que o teste realmente faz. Se os UPDATE a visibility_state nas linhas
~312 e ~335 já não tiverem efeito sobre nenhum predicado, remove-os.

Regra geral: nenhum INSERT direto de teste sem teste da transição correspondente,
ou sem comentário que nomeie esse teste — e o teste nomeado tem de existir.
Verifica que existe, não confies no comentário.

Não escrevas fora de backend/src/test/** e web/e2e/**.
```

### C2 — `security-auditor` (depois de B1, em paralelo com C1)

```
Usa o agente security-auditor. Modo leitura apenas — não corrijas nada, relata.

Contexto: docs/ESTADO-DO-SISTEMA.md. Esta onda fechou C1, C3.1 e C4. Reaudita.

1. C1 — PATCH /v1/admin/providers/{id}/approval: autorização ROLE_ADMIN efetiva e
   não contornável; sem oráculo de enumeração no par 403/404; transições sem
   caminho para reverter a PENDING; compare-and-set seguro sob concorrência;
   reason (PII potencial) fora de logs, métricas e corpos de erro; audit_log sem
   PII e na mesma transação; Idempotency-Key sem replay indevido.
2. C3.1 — registo: mesmo status, mesmo corpo e mesmo tempo para email novo e
   existente. Verifica a temporização a sério, não só o status code. Confirma que
   nenhum comentário ou nome de teste cita um ADR para autorizar uma divergência
   que o ADR proíbe.
3. C4 — listInbox: ?status= com valores inesperados não devolve pedidos DRAFT nem
   morada de clientes. Testa também maiúsculas/minúsculas, valores duplicados e
   lista separada por vírgulas.
4. Regressões: confirma que nenhum invariante do CLAUDE.md §4 se degradou nesta
   onda. Em especial os que a onda-1 acabou de fechar — rate limiting em
   /auth/**, SeedLocationsGuard, e visibility_state fora dos predicados.
5. Controlo de processo: para cada citação de ADR nova em comentário ou nome de
   teste que autorize uma divergência de invariante, verifica o texto do ADR.
   Foi assim que C3.1 passou.

Reporta com severidade e evidência ficheiro:linha.
```

---

## 4. Critério de aceitação da onda

1. `grep -rn "SET approval_status" backend/src/main/java` devolve pelo menos uma linha. Hoje devolve vazio. **É o teste mais importante desta onda.**
2. Existe um teste que verifica *invisível antes, visível depois* pelo caminho de produção — sem SQL direto.
3. `ApplicationModules.verify()` passa com a dependência nova para `platform/audit`.
4. `GET /v1/providers/me/requests?status=DRAFT` devolve `400`.
5. Registo com email existente é indistinguível de registo novo em status, corpo e tempo.
6. Um administrador aprova um prestador pela UI e o prestador passa a aparecer na pesquisa.
7. `mvn verify`, lint, typecheck, testes web e Playwright verdes.

## 5. O que esta onda deliberadamente não faz

- **`rating_avg`/`rating_count` continuam sem escritor** (C2 residual) — mesmo padrão do C1, no módulo `reviews`, dono `backend-domain-social`. Ficou fora por escolha de âmbito. Continua a ser critério de `ORDER BY` na pesquisa com valor constante 0.
- **C5** (deploy, IaC, backups) e **C6** (defaults de `prod`) — exigem o ADR de deploy primeiro.
- **Teste de arquitetura** que imponha a regra da skill `estado-com-escritor` no build. É o controlo que impede a quarta repetição; requer o `arquiteto` e fica para a onda seguinte.

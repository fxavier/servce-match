# ADR-0011: Elegibilidade do prestador resolvida na leitura, sem projeção desnormalizada

- **Estado:** Aceite
- **Data:** 2026-07-29
- **Decisores:** `arquiteto`
- **Relacionado:** ADR-0001 (Modular Monolith), ADR-0004 (PostGIS/matching §10.3),
  ADR-0005 (pesquisa), ADR-0007 (pagamentos), ADR-0010 (acesso SQL entre módulos)

## Contexto e Problema

A auditoria da Onda 2 concluiu que o *gating* por subscrição — o mecanismo de
monetização do produto — **não funciona em nenhum dos dois sentidos**. A
verificação do estado em `main` (`fe1b117`) confirma o relato e acrescenta-lhe
três factos que mudam o âmbito da decisão.

### 1. Existem três definições de elegibilidade, não duas

| Onde | Predicado efetivo |
|---|---|
| `matching.EligibilityRepository` (`isEligible`, `findEligibleProviderIds`) | `approval_status='APPROVED' AND visibility_state='VISIBLE' AND EXISTS(subscription status='ACTIVE')` |
| `search.ProviderSearchRepository` | idem (`JOIN subscription s ON … s.status='ACTIVE'`) |
| `providers.ProvidersService.checkEligibility` | `approval_status='APPROVED' AND visibility_state='VISIBLE'` — **subscrição nunca consultada** |
| `billing.SubscriptionLifecycle.isVisibilityEligible` | `status ∈ {ACTIVE, PAST_DUE}` (grace, via `SubscriptionStatus.grantsVisibility()`) |
| `billing.SubscriptionLifecycle.hasActiveSubscription` | `status = ACTIVE` |

Os dois últimos métodos existem, estão implementados e estão cobertos por
`SubscriptionLifecycleStateMachineTest` — e **não têm um único chamador em código
de produção**. O módulo que é dono da regra publicou-a corretamente; nenhum
consumidor a usa. O que os consumidores usam é uma reimplementação da regra em
SQL (`status='ACTIVE'`, sem *grace*) e uma leitura de um campo que ninguém
escreve.

### 2. `visibility_state` não guarda informação nenhuma

`ARQUITETURA.md` §12.1 (linha 660) define-o como função **total** de
`subscription.status`: `ACTIVE`/`PAST_DUE`(grace) → `VISIBLE`;
`EXPIRED`/`CANCELLED` → `HIDDEN`. Não há um segundo input — não existe pausa
voluntária do prestador, não existe suspensão distinta (essa é
`approval_status='SUSPENDED'`). É, por construção, uma cópia de um estado de
outro módulo, e não uma decisão própria de `providers`.

Escritas a `visibility_state` em código de produção: **zero**. `DEFAULT 'HIDDEN'`
na V4, e nada o altera. Consequências nos dois sentidos, ambas verificadas:

- **Quem não paga continua a operar.** Com `subscription.status='EXPIRED'` e o
  perfil marcado à mão como `APPROVED`/`VISIBLE`:
  `GET /v1/providers/me/requests` → 200 com morada completa do cliente;
  `POST /v1/requests/{id}/proposals` → 201; `POST /v1/conversations/{id}/messages`
  → 201.
- **Quem paga nunca aparece.** Sem via legítima para `VISIBLE`, todo o prestador
  em produção fica `HIDDEN`: `GET /v1/search/providers` devolve 0 resultados para
  qualquer consulta e o predicado do ADR-0004 não seleciona ninguém. Os caminhos
  SQL não estão "protegidos" — estão a **negar toda a gente**, e a verificação de
  subscrição que contêm é, na prática, código morto atrás de um campo constante.

### 3. `approval_status` tem exatamente o mesmo defeito, e não foi reportado

`approval_status` tem `DEFAULT 'PENDING'` na V4, é lido pelos mesmos três
predicados e **também não tem nenhuma escrita em código de produção**. Não existe
endpoint de aprovação em `docs/api/openapi.yaml` (não existe sequer superfície
`/v1/admin/**`). Corrigir só a subscrição deixaria o *gating* a negar toda a
gente pela outra metade da conjunção. As duas metades falham pela mesma causa.

### 4. A causa comum, e porque nenhum teste a apanhou

`MatchingEligibilityTest`, `RequestVisibilityIntegrationTest` e
`AcceptProposalIntegrationTest` inserem `approval_status='APPROVED'` e
`visibility_state='VISIBLE'` por `INSERT` direto. Testam o **consumidor** do
campo e nunca a transição que o devia produzir. O teste não falhou porque o teste
fabrica o estado cuja produção é o defeito.

O mesmo padrão repete-se noutros campos: `rating_avg`/`rating_count` são lidos
(`ORDER BY` da pesquisa, `ProvidersApi.summary`) e nunca escritos em produção —
`reviews` insere na tabela `review` e não atualiza agregado nenhum; o único
`INSERT` que lhes dá valor é uma *fixture*. E `premiumBadge` tem já duas
respostas divergentes para a mesma pergunta: `false` fixo em
`ProvidersService.summary`, `subscription_plan.has_badge` na pesquisa.

A classe de erro é esta: **um campo derivado cujo produtor não existe, com o
consumidor testado contra uma fixture que assume o resultado.** É esta classe que
a decisão tem de fechar, não só a instância.

## Fatores de Decisão

- **O invariante de segurança do CLAUDE.md §4 não é negociável**: o *gating* por
  subscrição é regra de domínio no servidor. Um mecanismo que depende de um
  produtor inexistente não é uma regra — é uma intenção.
- **Uma pergunta, uma resposta.** Duas representações do mesmo facto voltam a
  divergir; já divergiram três vezes neste código.
- **Custo real no caminho quente**, medido em operações de índice, não em receio.
- **Modos de falha**, não só desempenho: qual das opções falha fechada e qual
  falha aberta quando um componente não corre.
- **Direção das dependências entre módulos** (ADR-0001): `verify()` rejeita
  ciclos, pelo que autorizar `providers → billing` fecha permanentemente
  `billing → providers`. A escolha da direção tem de ser deliberada.
- **Preservar o desempenho do predicado *set-based*** do ADR-0004 §10.3.

## Opções Consideradas

1. **Manter a desnormalização e `billing` passa a escrever `visibility_state`**
   ao reagir aos seus próprios eventos (`SubscriptionActivated`/`PastDue`/
   `Expired`/`Cancelled`).
2. **Resolver o estado da subscrição na leitura**; `visibility_state` desaparece
   como input de decisão.
3. **Híbrido**: resolver na leitura com cache (Redis/`@Cacheable`) e TTL curto.
4. **Concentrar tudo em `matching`**: os módulos de domínio deixam de ter
   predicado próprio e passam todos por `MatchingApi`.

## Decisão

Adota-se a **opção 2**, com o âmbito exato abaixo. Cada ponto é vinculativo.

### D1 — `visibility_state` deixa de existir como input de decisão

Nenhum predicado — Java ou SQL — volta a ler `visibility_state`. Remove-se a
coluna, o `CHECK` e o índice `idx_provider_profile_visibility` (substituído por um
índice sobre `approval_status`), em migração nova de `db-migrations`. Não se
mantém "como cache": um campo que ninguém lê e ninguém escreve é dívida com
aparência de funcionalidade.

### D2 — Dois predicados, com nomes que dizem o que fazem

A confusão central é tratar como uma só coisa duas perguntas diferentes:

- **P1 — elegibilidade de operação (âmbito conta):** *este prestador pode operar
  de todo?* = `approval_status='APPROVED'` **E** subscrição concede visibilidade.
  Porta de entrada de: acesso ao *inbox*, criação de proposta, escrita no chat,
  presença na pesquisa.
- **P2 — correspondência pedido↔prestador (âmbito pedido):** = P1 **E** categoria
  trabalhada **E** cobertura geográfica. É o predicado do ADR-0004 §10.3. Porta de
  entrada de: ver um pedido concreto, `findEligibleProviderIds`, seleção para
  notificação.

P2 contém P1. Nenhuma superfície pública pode expor um predicado que satisfaça
parcialmente qualquer um dos dois com um nome que prometa o todo.

### D3 — Cada facto tem um dono, e a composição de P1 vive em `providers`

- O facto "subscrição" é respondido **apenas** por `billing`, através de
  `SubscriptionLifecycle.isVisibilityEligible(providerId)`, que já existe e já está
  testado. **Não se cria listener novo, nem método novo.**
- O facto "aprovação" é respondido **apenas** por `providers` (`approval_status`).
- `ProvidersApi.checkEligibility` passa a compor os dois e torna-se verdadeira. O
  `record ProviderEligibility` mantém a forma `(approved, visible)`, mas `visible`
  passa a significar "a subscrição concede visibilidade agora", resolvido na
  chamada. O javadoc que hoje afirma que o campo "é escrito pelo módulo `billing`
  ao reagir aos eventos de subscrição" descreve um mecanismo que nunca existiu e é
  corrigido no mesmo *commit*.
- Os chamadores (`requests`, `proposals`, `chat`) **não** compõem nada: continuam
  a chamar `checkEligibility().isEligible()`. Espalhar a composição por três
  *call sites* reproduziria exatamente o erro que este ADR fecha — bastaria um
  esquecer-se.

### D4 — A semântica única do *gating* é `ACTIVE ∨ PAST_DUE`, e o literal tem um só sítio

`ARQUITETURA.md` §12.1 promete *grace period* em `PAST_DUE`; os predicados SQL
escrevem `status='ACTIVE'` e anulam-no. Prevalece a §12.1: uma falha de cartão não
pode desligar o negócio do prestador no mesmo instante.

- **P1 e P2 usam ambos `status ∈ {ACTIVE, PAST_DUE}`**, isto é,
  `SubscriptionStatus.grantsVisibility()`. Os SQL de `matching` e `search` mudam de
  `= 'ACTIVE'` para o conjunto.
- A duração do *grace* é imposta pela transição `PAST_DUE → CANCELLED` em
  `billing`, nunca por quem lê.
- `hasActiveSubscription` (estrito) **não é predicado de *gating*** e não pode ter
  chamadores fora de `billing`/`payments`.
- Para que o conjunto de estados não volte a ser reescrito à mão em cada SQL,
  `billing` publica-o como fragmento único e reutilizável (à imagem de
  `geo.CoverageSql`, cujo javadoc já documenta a razão de a *string* ser
  partilhada com o teste). **Um literal, um sítio.** É este o mecanismo que impede
  a redivergência; sem ele, a decisão é só uma intenção.

Isto não contradiz o ADR-0010: `matching` e `search` continuam a **ler**
`subscription`, sem reinterpretar a regra — passam a consumir a definição de
`billing` em vez de a copiar.

### D5 — A subscrição deixa de depender de um job para negar acesso

`isVisibilityEligible` lê só `status`, que só sai de `ACTIVE` quando o job de
expiração corre. Se o job atrasar ou falhar, o prestador continua a operar sem
pagar — uma falha **aberta** num invariante de segurança. O predicado passa a
exigir também `current_period_end >= now()` (com tolerância explícita e
configurada, não implícita). O job continua a existir, para publicar os eventos e
consolidar o estado; deixa de ser o que separa quem paga de quem não paga.

`subscription.current_period_end` é anulável (V11). A comparação com `NULL` dá
desconhecido e **nega** — que é o resultado correto e deliberado: uma subscrição
que nunca chegou a ser paga (`PENDING` que falha o pagamento e vai a `PAST_DUE`
sem nunca ter tido período) não pode receber *grace*. Não se "corrige" com
`COALESCE` nem com um período infinito.

### D6 — `filterEligibleRequestIds`: nome honesto **e** predicado completo

Responde à pergunta 1 do relatório. Ambos, não um ou outro:

- **Renomear** para `filterRequestIdsCoveredBy(providerId, candidateIds)` —
  cobertura geográfica é o que faz, e o nome atual promete elegibilidade a quem o
  lê num *call site*. O mesmo se aplica a `MatchingApi.isEligible`, cuja consulta
  já é P2 completa e cujo nome deve dizê-lo (`matchesRequest`).
- **Acrescentar P1 ao SQL** do método de lote. O custo é uma condição adicional
  sobre um único `provider_id`, em colunas indexadas, avaliada uma vez por
  consulta — indistinguível do ruído. O ganho é que o *inbox* deixa de poder
  vazar moradas de clientes se um controlador futuro se esquecer do *gate* de
  entrada. Defesa em profundidade a custo praticamente nulo é para se ter.
- O pré-filtro de **categoria** continua a ser do chamador
  (`requests.listInbox`), porque vive na mesma consulta paginada e movê-lo
  custaria uma junção extra sem benefício. Fica dito no javadoc, como já está.

### D7 — A aprovação de prestador entra no contrato

Responde à pergunta 2. **É trabalho de contrato para o `api-contract`**, não
operação fora da API. Superfície mínima: `PATCH /v1/admin/providers/{providerId}/approval`,
restrita a `ROLE_ADMIN`, com transições `PENDING → APPROVED|REJECTED` e
`APPROVED → SUSPENDED`, corpo com motivo obrigatório para as negativas, aditiva
nos termos do ADR-0008.

Fica **expressamente rejeitado** resolver isto por `UPDATE` manual ou mudando o
`DEFAULT` da coluna para `'APPROVED'`. Um `DEFAULT 'APPROVED'` elimina o controlo
de aprovação sem o discutir; e uma operação fora da API não tem autenticação,
autorização, registo de auditoria nem `Idempotency-Key` — para um ato que decide
quem pode transacionar na plataforma, isso não é aceitável (CLAUDE.md §4).

### D8 — `billing` não depende de `providers`; `providers` depende de `billing`

`verify()` rejeita ciclos entre módulos, pelo que a direção tem de ser escolhida
uma vez. Hoje `billing` tem `allowedDependencies = {}` e o seu `package-info`
**pede** a adição de `providers`, por causa de `JdbcProviderAccountResolver`
(resolve `sub` Keycloak → `provider_profile.id` lendo `users` e `provider_profile`
em SQL bruto). Essa leitura é pontual, não *set-based*, e portanto **nunca esteve
autorizada pelo ADR-0010** — que exige consulta de conjunto cuja alternativa por
API produziria N+1.

O único consumidor de `ProviderAccountResolver` é
`payments.web.SubscriptionController`. Nenhuma lógica de domínio de `billing`
precisa dele. Decide-se:

- **`ProviderAccountResolver` sai de `billing`.** `payments.web` passa a resolver
  identidade → prestador com `UsersApi` + `ProvidersApi`, que é onde essa
  capacidade pertence. `JdbcProviderAccountResolver` e o seu SQL a `users`/
  `provider_profile` desaparecem.
- `billing` fica com `allowedDependencies = {}` e o pedido registado no seu
  `package-info` é **recusado com fundamento**: não é necessário.
- `providers` passa a `{"modules.users", "modules.billing"}`.
- Grafo resultante, sem ciclo: `billing → {}`; `users → {}`;
  `providers → {users, billing}`; `payments → {billing, providers, users}`;
  `notifications → {users, proposals, providers, billing::events}`.

As alterações a `package-info.java` são do `backend-platform`, a pedido, nunca
unilaterais.

### D9 — Regra de *fixture* (a decisão que fecha a classe de erro)

**Um teste não pode escrever, por SQL direto, um estado que código de produção
tem a obrigação de produzir.** A *fixture* leva o sistema a esse estado pelo
caminho de produção (API pública do módulo dono). Quando isso for
desproporcionado, o `INSERT` direto é tolerado **apenas se** existir, no mesmo
conjunto de testes, um teste que exercite a **transição** que produz o estado.

Corolário verificável, aplicável em revisão: **toda a coluna lida por um
predicado de decisão tem de ter, em produção, pelo menos um escritor
identificável.** Se não tem, é defeito — não é "por implementar". Aplicando-o
hoje, ficam por resolver, além de `visibility_state` e `approval_status`:
`rating_avg`/`rating_count` (lidos na ordenação da pesquisa, sem produtor) e a
divergência de `premiumBadge`. Ficam registados aqui como achados; não são
resolvidos por este ADR.

## Racional

**Porque não a opção 1 (listener em `billing`).** É a que parece mais barata e é a
mais cara. Exige um listener novo, idempotente sobre entrega *at-least-once*, mais
uma história de reprocessamento para os eventos perdidos entre a introdução do
código e o *backfill*, mais uma reconciliação para o caso de o listener falhar em
silêncio — e mantém as duas representações que já divergiram três vezes. Sobretudo,
**falha aberta**: quando a projeção fica velha, o prestador que deixou de pagar
continua a operar, que é precisamente o defeito em investigação. Uma correção cujo
modo de falha é o próprio defeito não é uma correção, é uma repetição com mais
peças.

Acresce que o argumento de desempenho a seu favor não sobrevive à medição do que
custa a alternativa. A opção 2 acrescenta a `checkEligibility` **uma** procura por
índice em `subscription` (`provider_id`, já coberto pelo índice único parcial
`uq_subscription_provider_non_terminal`), na mesma transação e na mesma ligação,
num pedido que a seguir executa uma consulta paginada com predicado PostGIS. Nos
caminhos *set-based* — que são os verdadeiramente quentes — o custo é **zero**:
`findEligibleProviderIds` e a pesquisa já fazem a junção a `subscription` hoje; a
única alteração é o conjunto de estados. Trocar um modo de falha aberto por uma
procura por índice é um bom negócio; recusá-lo por desempenho seria otimizar o
caminho que não é o gargalo.

**Porque não a opção 3 (cache).** Cache é a opção 1 com o *staleness* medido em
segundos em vez de indeterminado — o mesmo modo de falha, mais infraestrutura. E o
ADR-0006 tornou o Redis condicional: um invariante de segurança não deve passar a
depender de um componente que a arquitetura declara opcional. Se o perfil vier a
mostrar que `checkEligibility` domina o tempo de resposta — o que hoje nada indica
—, a cache continua disponível **por baixo** desta decisão, porque com D1 já só
existe uma fonte de verdade para invalidar.

**Porque não a opção 4 (tudo em `matching`).** Faria `chat`, `proposals` e
`requests` dependerem de `matching` para uma pergunta que não tem nada de
geográfico ("este prestador pode operar?"), e obrigaria `matching` a depender de
`providers` e `billing`, engordando o módulo mais quente com regras que não são
dele. A separação P1/P2 dá o mesmo resultado sem essa concentração.

**Porque a opção 2, em resumo.** Elimina o estado que pode divergir em vez de o
sincronizar. Usa uma API que já existe, já está testada e a que só falta um
chamador. Não introduz listener, evento, job nem *backfill*. E, com D4, deixa a
regra num só sítio — sem o quê a mesma divergência voltaria pela terceira vez.

## Consequências

**Positivas**

- O *gating* passa a funcionar nos dois sentidos, e falha **fechada**: sem
  subscrição legível que conceda visibilidade, nega.
- Desaparece a possibilidade estrutural de divergência: um facto, um dono, um
  literal.
- Menos peças: nenhum listener novo, nenhum *backfill*, uma coluna e um índice a
  menos, `ProviderAccountResolver` e o seu SQL bruto removidos.
- O *grace period* do §12.1 passa a existir de facto, em vez de estar prometido
  na documentação e anulado no SQL.
- D9 dá ao revisor um critério aplicável sem ler o módulo todo.

**Negativas / Custos**

- **`providers` passa a depender de `billing`.** É acoplamento real e novo, e
  fecha para sempre a direção inversa. Se um dia `billing` precisar mesmo de
  `providers`, o caminho será um evento ou uma inversão por interface — mais caro
  do que hoje.
- **Uma procura por índice adicional** por chamada a `checkEligibility`, em
  caminhos quentes. Aceite conscientemente; se o perfil o desmentir, o remédio é
  cache sobre uma fonte única, não o regresso à projeção.
- **`checkEligibility` deixa de ser respondível com a linha já carregada** do
  `provider_profile`. Quem quiser a ficha e a elegibilidade paga duas leituras.
- **Trabalho de contrato não previsto**: o endpoint de aprovação (D7) mete
  `api-contract` num caminho que se julgava só de implementação, e abre a
  superfície `/v1/admin/**`, que ainda não existe e traz consigo `ROLE_ADMIN`,
  auditoria e testes próprios.
- **Migração destrutiva.** Remover `visibility_state` é irreversível sem
  restauro; qualquer consumidor não inventariado (relatórios, SQL operacional,
  *dashboards*) parte sem erro de compilação — exatamente o custo que o ADR-0010
  já tinha declarado.
- **D9 encarece as *fixtures***: passar pelo caminho de produção é mais lento de
  escrever e mais lento de correr do que um `INSERT`. É o preço de o teste voltar
  a poder falhar.
- **O trabalho cresceu**: `approval_status` (D7) não estava no âmbito reportado e
  está no caminho crítico. Sem ele, o *gating* corrigido nega toda a gente.

## Alternativas rejeitadas

- **Listener em `billing` a escrever `visibility_state` (opção 1):** rejeitada por
  manter duas representações do mesmo facto e por falhar aberta — o modo de falha
  é o defeito em causa. O ganho de leitura que a justificaria é uma procura por
  índice.
- **Cache com TTL (opção 3):** rejeitada agora; disponível depois. O mesmo
  *staleness* com mais infraestrutura, e faria um invariante de segurança depender
  do Redis, que o ADR-0006 declara condicional.
- **Concentrar os predicados em `matching` (opção 4):** rejeitada por acoplar
  módulos sem necessidade geográfica ao módulo mais quente e por lhe dar regras
  que não são dele.
- **Manter `visibility_state` "como cache":** rejeitada — sem escritor e sem
  leitor é dívida com aparência de funcionalidade.
- **`DEFAULT 'APPROVED'` em `approval_status`:** rejeitada — elimina o controlo de
  aprovação em vez de o implementar.
- **Aprovação por operação manual fora da API:** rejeitada — sem autorização,
  auditoria nem idempotência, num ato que decide quem pode transacionar.
- **Compor P1 em cada chamador:** rejeitada — é a forma exata do defeito atual;
  bastaria um chamador esquecer-se.

## Ligações

- Spring Modulith — o que `ApplicationModules.verify()` verifica (ausência de
  ciclos, acesso a `internal`, `allowedDependencies`):
  https://docs.spring.io/spring-modulith/reference/verification.html
- Spring Modulith — Event Publication Registry e garantias de entrega
  (fundamenta o custo de idempotência/reprocessamento da opção 1):
  https://docs.spring.io/spring-modulith/reference/events.html
- PostgreSQL — índices parciais (base do `uq_subscription_provider_non_terminal`
  usado pela procura por `provider_id`):
  https://www.postgresql.org/docs/current/indexes-partial.html
- `ARQUITETURA.md` §10.3 (predicado de matching), §12.1 (ciclo de vida da
  subscrição e derivação da visibilidade), §3.3 (elegibilidade do prestador).

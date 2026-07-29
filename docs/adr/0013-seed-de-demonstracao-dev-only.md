# ADR-0013: Dados de demonstração como *seed* dev-only, fora das migrações versionadas

- **Estado:** Aceite
- **Data:** 2026-07-29
- **Decisores:** `arquiteto`
- **Relacionado:** ADR-0004 (predicado PostGIS), ADR-0011 (elegibilidade na
  leitura, e a regra de *fixture* D9), ADR-0012 (autenticação *first-party*),
  V15 (`seed_categories_and_subscription_plans`)

## Contexto e Problema

`web/site/src/services/mock/fixtures/**` — sete ficheiros, ~580 linhas — contém
prestadores, pedidos, propostas, conversas, avaliações, categorias e planos
fictícios. A SPA está a ser desenvolvida contra eles, em memória.

O problema aparece no momento em que a SPA se liga ao backend real. Numa base de
dados de desenvolvimento acabada de migrar existem **categorias e planos** (V15)
e mais nada: zero prestadores, zero pedidos, zero propostas, zero conversas, zero
avaliações. A pesquisa devolve lista vazia, o *inbox* do prestador está vazio, o
ecrã de propostas está vazio, e o chat não tem com quem falar.

O efeito prático é que **ligar ao backend piora a aplicação**. É o incentivo
exato para os *mocks* nunca saírem, para a SPA continuar a ser desenvolvida
contra um contrato que ninguém verifica, e para a divergência entre o que a SPA
mostra e o que a API devolve só aparecer em produção.

A pergunta que este ADR fecha não é "como é que pomos dados na base de dados" —
é **onde é que passa a fronteira entre dados que correm em todo o lado e dados
que não podem chegar a produção**, e qual é o mecanismo que a torna verdadeira.

### O V15 já respondeu a metade da pergunta

A migração `V15__seed_categories_and_subscription_plans.sql` documenta, no
próprio ficheiro, o critério para os dados que lá estão:

> *"Dados de referência sem os quais a aplicação não é utilizável: sem `category`,
> `POST /v1/requests` devolve 400 (…); sem `subscription_plan`,
> `GET /v1/subscription-plans` devolve lista vazia e nenhum prestador consegue
> desbloquear o gating de subscrição."*

E justifica ser `V__` e não `R__`: *"preço e limites de plano são decisões de
negócio auditáveis (…) cada alteração deve ficar registada como um passo datado e
revisto na história do schema."*

Isso é a definição de **dado de referência**, e está bem. O que falta é o critério
para o outro tipo — e a ausência desse critério é o que faz com que "o Zé Canalizador
de Braga com 4.8 estrelas" acabe, por omissão, dentro de uma migração `V__`.

### A tensão com o ADR-0011 D9, que tem de ser resolvida e não ignorada

O ADR-0011 D9, promovido a `CLAUDE.md` §5, diz: *"Fixtures não fabricam estado
que a produção tem de produzir."* Um *seed* de demonstração faz exatamente isso —
escreve `approval_status='APPROVED'` e subscrições ativas à mão. Se este ADR não
disser explicitamente onde é que D9 se aplica e onde não, o *seed* torna-se uma
porta das traseiras que anula D9 sem o revogar. É o que o D5 abaixo fecha.

## Fatores de Decisão

- **A distinção referência/demonstração tem de ser mecanicamente verificável**,
  não uma convenção de nomes que se erode ao terceiro ficheiro.
- **O que corre em produção é o que está no artefacto**, não o que está no YAML.
  Qualquer barreira que dependa de alguém acertar numa variável de ambiente é uma
  sugestão.
- **Modo de falha**: se a barreira falhar, o sintoma é silencioso (dados a mais
  numa base de dados) e só é notado por um utilizador.
- **Não abrir uma exceção ao ADR-0011 D9.**
- **Histórico e reprodutibilidade**: duas máquinas de desenvolvimento têm de ter
  a mesma base de dados, e o que correu tem de estar registado.

## Opções Consideradas

1. **Migração `V__` normal**, como o V15, com os dados de demonstração a correr
   em todos os ambientes.
2. **Ficheiros em `db/seed/**`, banda `V900+`**, carregados por Flyway só em
   `local`/`dev`, fora do artefacto de produção, com verificação de arranque.
3. **Migração repetível `R__`** com guarda em SQL (`WHERE current_setting(...)`
   ou equivalente) a impedir a execução fora de desenvolvimento.
4. **Fora do Flyway**: um `CommandLineRunner` `@Profile("dev")` em Java, ou um
   script `psql` montado no `docker-compose`.

## Decisão

Adota-se a **opção 2**. Cada ponto é vinculativo.

### D1 — Dois tipos de dados, um critério de arbitragem verificável

| | Referência | Demonstração |
|---|---|---|
| Exemplos | categorias, planos de subscrição | prestadores, pedidos, propostas, conversas, mensagens, avaliações, subscrições |
| Porquê existem | sem eles um utilizador real não conclui um fluxo | para um humano ver ecrãs preenchidos |
| Onde | `db/migration/V__` | `db/seed/V9xx__` |
| Onde corre | todos os ambientes | `local` e `dev`, só |
| Natureza | decisão de negócio auditável | andaime de desenvolvimento |

**Critério de arbitragem, aplicável em revisão sem discussão:** *se a linha tiver
de existir numa base de dados de produção vazia para que um utilizador real
consiga concluir um fluxo, é referência; caso contrário é demonstração.*

Um plano tem de existir para alguém subscrever — é referência. Um prestador
fictício não é pré-condição de nada — é demonstração. O critério não tem zona
cinzenta útil, e é essa a intenção.

**Dono de ambos: `db-migrations`.** Não se cria dono novo; cria-se caminho novo
(`CLAUDE.md` §3).

### D2 — Localização, banda de versões e idempotência

- **Caminho:** `backend/src/main/resources/db/seed/`.
- **Banda `V900__`–`V999__`, reservada.** Nenhuma migração real usa `≥ 900`. A
  reserva é registada por escrito pelo `db-migrations` junto às migrações, para
  que a banda não seja consumida por distração dentro de três anos.
- **Idempotente**, pela mesma regra do V15: `ON CONFLICT DO NOTHING` sobre a
  chave natural. Re-executável sobre uma base já semeada, sem erro.
- **Ids literais, nunca `gen_random_uuid()`.** Onde a tabela não tem chave
  natural (`provider_profile`, `service_request`, `proposal`, `conversation`,
  `review`), o *seed* declara UUID fixos no próprio ficheiro. Isso resolve três
  problemas de uma vez: dá chave de conflito para a idempotência, torna as
  chaves estrangeiras entre ficheiros de *seed* escrevíveis, e faz com que duas
  máquinas de desenvolvimento tenham a **mesma** base de dados — sem o quê um
  *bug* reproduzido numa máquina não se reproduz na outra.

### D3 — A barreira é mecanismo, não configuração

Três camadas. **Todas obrigatórias**, porque nenhuma sozinha chega, e por esta
ordem de eficácia:

1. **O ficheiro não entra no artefacto de produção.** `db/seed/**` é excluído do
   JAR no perfil de *build* de produção (exclusão de recurso no `backend/pom.xml`
   ou *source set* separado). **Um ficheiro que não existe não corre**, por muita
   variável de ambiente que se ponha. É a única camada que não depende de ninguém
   acertar em nada. Dono: `backend-platform`.
2. **`spring.flyway.locations` diferenciado por perfil.** `application.yml`
   mantém `classpath:db/migration`; `application-local.yml` e
   `application-dev.yml` acrescentam `classpath:db/seed`. Dono:
   `backend-platform`. Esta camada é conveniência — não é a barreira.
3. **O arranque aborta** se as *locations* efetivas contiverem `db/seed` sem
   `local` nem `dev` entre os perfis ativos. Verificação sobre o `Environment`
   resolvido, a lançar antes de o Flyway migrar. Dono: `backend-platform`.

**Porque é que a camada 2 sozinha não é uma barreira.** A ordem de
`PropertySource` do Spring Boot é explícita: *"Later property sources can override
the values defined in earlier ones"*, e a lista coloca *config data*
(`application.yml`) em **3.º** e as **variáveis de ambiente do SO em 5.º**. Ou
seja, `SPRING_FLYWAY_LOCATIONS` no ambiente **sobrepõe-se a qualquer YAML** do
repositório. Uma variável mal copiada de um ambiente de *staging* para produção
basta. A camada 3 existe precisamente para transformar isso num arranque falhado
em vez de uma base de dados de produção com prestadores fictícios.

**Porque é que a camada 3 não torna a 1 dispensável.** A 3 corre dentro da
aplicação e depende de o código de verificação estar correto e presente. A 1
corre no *build* e é verificável com `unzip -l` sobre o artefacto. São defesas de
naturezas diferentes: uma protege de configuração errada, a outra de um artefacto
errado. Com a 1 em vigor, a 3 nunca dispara em produção — dispara em *preview* e
*staging* construídos a partir do perfil de *build* de desenvolvimento, que é
exatamente onde o engano é provável.

### D4 — Consistência com o Keycloak: os `keycloak_sub` são os UUID do realm

`users.keycloak_sub` é a chave que liga a linha local ao utilizador do IdP
(V2, ADR-0002). Os utilizadores de demonstração do *seed* têm de ter o
`keycloak_sub` **exatamente igual** ao `id` do utilizador correspondente em
`infra/keycloak/realm-servimatch.json`.

**Facto verificado, e é um bloqueio:** os três utilizadores do realm
(`customer.test@servimatch.pt`, `provider.test@servimatch.pt`,
`admin.test@servimatch.pt`) **não declaram `"id"`**. O Keycloak gera um UUID
aleatório no *import* — diferente em cada máquina e diferente a cada
`docker compose down -v`. Nessas condições **é impossível escrever o *seed***.

**Requisito para `platform-infra`, pré-condição deste ADR:** acrescentar `"id"`
explícito e fixo a cada utilizador do realm de desenvolvimento.

**Porque é que isto importa mais do que parece.** Se o *seed* usar um `sub`
inventado, o sintoma não é um erro — é pior. O login funciona; o
`UsersApi.ensureProvisioned` (ADR-0012 D9) cria uma **segunda** linha `users`
com o `sub` verdadeiro; o utilizador entra num perfil vazio; e todos os dados de
demonstração ficam pendurados numa linha `users` órfã que ninguém alcança. Nada
falha, nada regista erro, e o programador conclui que "o seed não funcionou".

**Exceção explícita ao ADR-0012 D9.** O ADR-0012 diz que o único escritor de
`users` em produção é o provisionamento JIT. O *seed* **é** um segundo escritor
de `users` — mas apenas em `local`/`dev`, e escreve exatamente o que o JIT
escreveria. Como o JIT é `INSERT ... ON CONFLICT (keycloak_sub) DO NOTHING`, os
dois convergem para a mesma linha, qualquer que seja a ordem. Esta é a **única**
exceção autorizada, e está autorizada porque D3 garante que o *seed* nunca corre
onde a regra do 0012 se aplica.

### D5 — O *seed* não é *fixture* de teste, e não pode passar a ser

**Nenhum teste automatizado — unitário, de integração ou e2e — pode depender do
conteúdo de `db/seed/**`.** Os testes continuam integralmente sujeitos ao
ADR-0011 D9 e ao `CLAUDE.md` §5.

Mecanismo, não intenção: **o perfil de teste não inclui `db/seed` nas
*locations***, e o `qa-e2e` não o pode acrescentar (`application*.yml` é do
`backend-platform` — `CLAUDE.md` §3). Um teste que precise de um prestador
elegível continua a ter de o levar a esse estado pelo caminho de produção.

**Porquê.** Um teste que passa porque existe um prestador de demonstração na base
é um teste que não exercita a transição que cria prestadores. É o defeito do
ADR-0011 outra vez, com outro nome e com a agravante de vir embrulhado numa
decisão arquitetural. Sem este ponto, este ADR seria a forma de contornar o
anterior.

### D6 — O *seed* tem de satisfazer os predicados reais, e isso é dívida assumida

Um *seed* que insira prestadores mas não satisfaça o *gating* do ADR-0011
(`approval_status='APPROVED'` **e** subscrição a conceder visibilidade, com
`current_period_end` no futuro) nem o predicado do ADR-0004 (categoria trabalhada
**e** cobertura geográfica) **não serve para nada**: a SPA continua a ver listas
vazias e ninguém percebe porquê. Portanto o *seed* **tem** de escrever essas
colunas à mão.

Isto é, literalmente, o padrão que o ADR-0011 D9 condena. É tolerado aqui, e só
aqui, por duas razões que têm de se verificar em conjunto: **não é teste** (D5) e
**não é produção** (D3). Registe-se sem ambiguidade: **a existência do *seed* não
é argumento para adiar o produtor real de nenhuma destas colunas.** O endpoint de
aprovação de prestador (ADR-0011 D7) continua a ser trabalho por fazer, e o *seed*
não o substitui — apenas torna mais fácil não dar por ele.

**Consequência prática que morde:** `current_period_end` tem de ser uma data
relativa (`now() + interval …`). Sendo o *seed* idempotente
(`ON CONFLICT DO NOTHING`), a segunda execução **não** atualiza a data. Uma base
de desenvolvimento semeada há três meses tem subscrições expiradas, e os
prestadores desaparecem da pesquisa **sem erro nenhum** — o *gating* está a
funcionar corretamente, e é exatamente por isso que é confuso. Mitigações: usar
um intervalo absurdamente longo (`now() + interval '10 years'`) e assumir que a
base de dados de desenvolvimento é descartável (`docker compose down -v`).

### D7 — O que acontece aos *mocks* da SPA

Os `fixtures` de `web/site/**` são do `web-frontend` e não são alterados por este
ADR. O que este ADR decide é o seu **estatuto**:

- Os ficheiros SQL de *seed* derivam dos *fixtures* da SPA **uma vez**. A partir
  daí, **os *fixtures* deixam de ser a fonte de verdade dos dados de
  desenvolvimento**.
- Manter os dois conjuntos vivos e sincronizados à mão é divergência garantida —
  é a mesma classe de erro que o ADR-0011 documenta em três instâncias
  diferentes.
- **Recomendação vinculativa ao `web-frontend`:** os *mocks* ficam restritos a
  testes de componente. Deixam de ser um **modo de execução** da aplicação.

**Custo real, e não é pequeno:** o modo "SPA sem backend" deixa de existir. Quem
trabalhar no frontend passa a precisar de PostgreSQL + Keycloak a correr para ver
qualquer ecrã com dados. Isso é uma perda concreta de produtividade e de
autonomia do `web-frontend`, e é o preço de a SPA passar a ser desenvolvida
contra a API que vai para produção.

## Racional

**Porque não a opção 1 (migração `V__` normal).** Não é um risco hipotético e não
se resolve com disciplina. Um prestador fictício numa migração normal aparece na
pesquisa pública em produção, é selecionado pelo predicado do ADR-0004, recebe um
pedido de um cliente real com morada real, e nunca responde. Uma avaliação
fictícia de 4.8 estrelas influencia a decisão de compra de uma pessoa. Além do
dano reputacional, é uma prática comercial que não é defensável perante um
regulador. E o remédio — uma migração de limpeza — tem de acertar em todas as
linhas derivadas (propostas, conversas, mensagens, avaliações, subscrições) sem
apagar dados reais que entretanto se lhes ligaram. O V15 fez a escolha certa para
o tipo de dados que contém; estendê-la a dados de demonstração seria aplicar o
critério certo ao caso errado.

**Porque não a opção 3 (`R__` com guarda em SQL).** A guarda é uma condição
**dentro** do ficheiro, e o ficheiro está no artefacto e é lido em todos os
ambientes. Passa a existir uma barreira cuja correção depende de uma expressão
SQL estar certa e de uma definição de sessão estar presente — a mesma classe de
proteção que a camada 2 do D3, com menos visibilidade. Acresce que `R__`
re-executa sempre que o *checksum* muda, comportamento que o próprio V15 já
rejeitou por escrito para dados de negócio.

**Porque não a opção 4 (runner Java ou script no `docker-compose`).** Tira o
*seed* do histórico do Flyway — deixa de haver registo do que correu e quando, o
que é precisamente o argumento que o V15 usou a favor de `V__`. Duplica um
mecanismo de migração que já existe e já está configurado. E devolve a proteção
ao domínio da configuração: um perfil `dev` ativo por engano num arranque de
produção e o *runner* corre. A variante "script no `docker-compose`" tem um mérito
real — nunca está dentro do artefacto —, mas não funciona para quem corre o
backend a partir do IDE contra a base de dados do *compose*, que é o modo de
trabalho normal, e perde na mesma o histórico.

**Porque a opção 2, em resumo.** Reutiliza o mecanismo que já existe e já tem
histórico. Faz a barreira principal ser a ausência física do ficheiro — a única
que não depende de ninguém acertar em nada. E deixa uma segunda barreira que
converte o engano mais provável (variável de ambiente herdada) num arranque
falhado em vez de uma contaminação silenciosa.

## Consequências

**Positivas**

- A SPA passa a poder ser desenvolvida contra a API real sem regredir em
  qualidade de demonstração — que é a condição para o contrato ser verificado
  cedo em vez de em produção.
- A distinção referência/demonstração fica com um critério aplicável em revisão
  (D1), em vez de depender de bom senso.
- A barreira principal (D3.1) é verificável fora da aplicação, com `unzip -l`.
- D4 força a fixação dos `id` do realm, que é uma melhoria de reprodutibilidade
  independente deste ADR: hoje duas máquinas têm `sub` diferentes para o mesmo
  utilizador de teste.
- D5 fecha a porta das traseiras ao ADR-0011 D9 antes de ela ser aberta.

**Negativas / Custos**

- **Os dados de demonstração existem em duplicado** — TypeScript na SPA e SQL no
  backend — e vão divergir. D7 mitiga ao retirar estatuto de fonte de verdade aos
  primeiros, mas não elimina a duplicação enquanto os *mocks* existirem.
- **O `web-frontend` perde autonomia**: sem PostgreSQL e Keycloak a correr, não
  há ecrã com dados. É trabalho de ambiente que antes não era preciso.
- **Uma base de desenvolvimento antiga degrada-se em silêncio** (D6): as
  subscrições do *seed* expiram, os prestadores somem da pesquisa, e o *gating* a
  funcionar corretamente parece uma avaria.
- **Trocar de perfil sobre a mesma base parte o arranque.** Depois de correr com
  `db/seed`, o `flyway_schema_history` tem as `V9xx` registadas; arrancar sem
  `db/seed` faz o `validate` falhar com *"Detected applied migration not resolved
  locally"*. **A mitigação por `ignore-migration-patterns: "*:missing"` é
  expressamente rejeitada** — desligaria uma verificação real também para as
  migrações a sério. A mitigação correta é a base de dados de desenvolvimento ser
  descartável.
- **A banda `V900+` é uma convenção que só um humano faz cumprir.** Se um dia uma
  migração real lá chegar, a colisão é confusa de diagnosticar.
- **D3 exige trabalho ao `backend-platform` em três sítios** (`pom.xml`, perfis,
  verificação de arranque) para uma funcionalidade que só serve desenvolvimento.
  É desproporcionado em esforço e é deliberado: a alternativa barata é a que
  falha em silêncio.
- **D6 é dívida reconhecida**: o *seed* escreve à mão colunas cujo produtor de
  produção ainda não existe, e torna mais confortável não o escrever.
- **D4 depende de uma alteração noutro âmbito** (`platform-infra`). Até os `id`
  do realm estarem fixos, este ADR não é implementável — e implementá-lo à mesma
  produz o modo de falha silencioso descrito em D4.

## Alternativas rejeitadas

- **Migração `V__` normal (opção 1):** rejeitada — dados fictícios em produção,
  com dano a utilizadores reais e limpeza difícil.
- **`R__` com guarda em SQL (opção 3):** rejeitada — o ficheiro continua no
  artefacto, a barreira volta a ser configuração, e `R__` reexecuta por *checksum*
  (o V15 já o rejeitou para dados de negócio).
- **Runner Java `@Profile("dev")` ou script no `docker-compose` (opção 4):**
  rejeitada — perde o histórico do Flyway e devolve a proteção a um perfil ativo.
- **`gen_random_uuid()` no *seed*:** rejeitada — quebra a idempotência, impede
  chaves estrangeiras entre ficheiros e faz duas máquinas terem bases diferentes.
- **`keycloak_sub` inventado no *seed*:** rejeitada — produz o modo de falha
  silencioso de D4 (perfil duplicado, dados órfãos, nenhum erro).
- **Testes a lerem `db/seed`:** rejeitada — reabriria o ADR-0011 D9.
- **`ignore-migration-patterns: "*:missing"` para tolerar a troca de perfil:**
  rejeitada — desliga uma verificação real por causa de um incómodo de
  desenvolvimento.
- **Apenas configuração (`spring.flyway.locations` por perfil) como barreira:**
  rejeitada — variáveis de ambiente do SO sobrepõem-se ao YAML na ordem de
  `PropertySource` do Spring Boot.

## Ligações

- **Spring Boot — Externalized Configuration**, ordem de `PropertySource`
  (*config data* em 3.º, variáveis de ambiente do SO em 5.º, *"later property
  sources can override the values defined in earlier ones"*):
  https://docs.spring.io/spring-boot/reference/features/external-config.html
- **Flyway — `locations`** (prefixos `classpath:`/`filesystem:`, múltiplas
  localizações; omissão em Java: `classpath:db/migration`):
  https://documentation.red-gate.com/fd/flyway-locations-setting-277579008.html
- **Flyway — `ignoreMigrationPatterns` e `validate`** (*"Detected applied
  migration not resolved locally"*):
  https://documentation.red-gate.com/fd/customize-validation-rules-with-ignoremigrationpatterns-212140651.html
- **Spring Boot — Database Initialization / Flyway**:
  https://docs.spring.io/spring-boot/how-to/data-initialization.html
- `backend/src/main/resources/db/migration/V15__seed_categories_and_subscription_plans.sql`
  (o racional de *seed* de referência já escrito, e a base de D1).
- `backend/src/main/resources/db/migration/V2__users.sql`
  (`uq_users_keycloak_sub`, a chave que D4 tem de respeitar).
- `infra/keycloak/realm-servimatch.json` (utilizadores de desenvolvimento, hoje
  sem `id` fixo).
- `web/site/src/services/mock/fixtures/**` (origem única dos dados de D7).
- `ARQUITETURA.md` §9 (modelo de dados), §10.3 (predicado de matching), §12.1
  (ciclo de vida da subscrição).

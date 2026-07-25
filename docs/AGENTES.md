# Execução paralela de agentes no monorepo ServiMatch

Este documento define **quem faz o quê**, **por que ordem** e **o que impede dois
agentes de se atropelarem**. As definições estão em `.claude/agents/` e os
procedimentos partilhados em `.claude/skills/`.

## 1. Por que é que o paralelismo precisa de regras

Num monorepo, agentes concorrentes falham por três motivos, sempre os mesmos:

1. **Escrita no mesmo ficheiro** — dois agentes editam `pom.xml`, `package.json`
   ou uma migração com o mesmo número.
2. **Contrato divergente** — backend e clientes assumem formas diferentes da
   mesma API porque nenhum é a fonte de verdade.
3. **Acoplamento invisível** — um módulo passa a depender de internals de outro e
   o trabalho deixa de ser separável.

As três contramedidas deste repositório: **ownership exclusivo por caminho**
(secção 3 do `CLAUDE.md`), **contrato primeiro** com um único escritor
(`api-contract`), e **verificação de fronteiras do Spring Modulith a falhar o
build**. Sem estas três, aumentar o número de agentes aumenta o retrabalho, não
a velocidade.

## 2. Agentes

| Agente | Responsabilidade | Escreve em |
|---|---|---|
| `arquiteto` | Decisões transversais, ADR, arbitragem de fronteiras | `docs/ARQUITETURA.md`, `docs/adr/**`, `.claude/**` |
| `api-contract` | Contrato OpenAPI, compatibilidade, geração de clientes | `docs/api/**` |
| `backend-platform` | Build, config, segurança, erros, rate limiting, eventos, observabilidade; módulos transversais `uploads` e `notifications`; `package-info.java` de **todos** os módulos | `backend/pom.xml`, `platform/**`, `config/**`, `application*.yml`, `modules/{uploads,notifications}/**`, `modules/*/package-info.java` |
| `backend-domain` | users, providers, requests, proposals, bookings, reviews, categories, chat | `backend/**/modules/{esses}/**` |
| `backend-matching` | Matching geográfico, geocodificação, pesquisa FTS | `backend/**/modules/{matching,geo,search}/**` |
| `backend-payments` | Subscrições, gateways, webhooks, reconciliação | `backend/**/modules/{billing,payments}/**` |
| `db-migrations` | Schema PostgreSQL/PostGIS e migrações Flyway | `backend/src/main/resources/db/migration/**` |
| `web-frontend` | SPA React + Vite + TS e BFF | `web/**` |
| `mobile-flutter` | App Flutter iOS + Android | `mobile/**` |
| `platform-infra` | docker-compose, realm Keycloak, CI/CD, segredos | `infra/**`, `.github/workflows/**` |
| `security-auditor` | Auditoria (só leitura) | — |
| `qa-e2e` | Testes de integração e E2E transversais | `backend/src/test/**`, `e2e/**` |

**Módulos acrescentados depois da Onda 0** (as sete operações do contrato v1.0.0
que ficaram sem dono; ver `CLAUDE.md` §3 para o mapa endpoint → módulo):
`categories` e `chat` para o `backend-domain`, `uploads` e `notifications` para o
`backend-platform`. `GET /v1/app/version-status` não é módulo — vive em
`platform/appversion`, porque as regras são configuração e não têm tabela.

Critério aplicado: o módulo fica com o agente que já detém a **regra que o
governa**. `chat` nasce de `ProposalAccepted` e é bloqueado pelo *gating* de
subscrição, ambos já invariantes do `backend-domain`; `categories` é lido em
tempo de compilação por `providers` e `requests`, do mesmo agente. `uploads` e
`notifications` não têm semântica de domínio e concentram invariantes de
segurança e integrações externas (*magic bytes*, URL assinado, FCM/email,
segredos), que são do `backend-platform`.

## 3. Ondas

### Onda 0 — sequencial, bloqueante

Nada em paralelo arranca antes de isto estar feito, porque tudo o resto depende
destes artefactos.

1. `arquiteto` — ADR-0001 a ADR-0009 aceites e sem decisões em aberto (ADR-0003
   fechado em Boot 3.5.x + Modulith 1.4.x + Java 21). Revalida a coerência entre
   `docs/ARQUITETURA.md`, os ADR e o contrato antes de dar seguimento. **Feito.**
2. `api-contract` — `POST /v1/uploads` está no contrato; 18 caminhos /
   20 operações / 35 schemas, validado em OpenAPI 3.1. Falta apenas **congelar**
   a versão do contrato para a primeira iteração e gerar os clientes. *Parcial.*
3. Em paralelo entre si, mas antes da Onda 1:
   - `backend-platform` — esqueleto Maven compilável, segurança, erros, eventos.
   - `db-migrations` — schema inicial com PostGIS, índices e constraints.
   - `platform-infra` — `docker-compose` a arrancar com o realm Keycloak importado.

**Porta de saída:** `mvn verify` verde com `ApplicationModules.verify()`,
`docker compose up` funcional, contrato validado.

### Onda 1 — paralela

| Agente | Depende de |
|---|---|
| `backend-domain` | esqueleto + schema |
| `backend-matching` | esqueleto + schema (índices geo) |
| `backend-payments` | esqueleto + schema (tabela de eventos em bruto) |
| `web-frontend` | contrato congelado |
| `mobile-flutter` | contrato congelado (arranca em *fast-follow*, ADR-0008) |

Os três agentes de backend escrevem em módulos disjuntos e comunicam por eventos
de domínio; os dois de frontend consomem o mesmo contrato. Nenhum toca em
ficheiros do outro.

**Porta de saída:** build verde por área, testes de módulo a passar, nenhuma
violação de fronteira.

### Onda 1b — depois de integrada a Onda 1

Os módulos atribuídos depois da Onda 0 (`categories`, `chat`, `uploads`,
`notifications` e `platform/appversion`) implementam-se aqui, não em paralelo com
a Onda 1: `chat` depende do evento `ProposalAccepted` de `proposals` e
`notifications` subscreve eventos dos três agentes de backend. Antecipá-los
significaria escrever contra tipos de evento que ainda estão a mudar.

Ordem dentro da onda: `backend-platform` cria os `package-info.java` dos quatro
módulos e entrega `uploads` primeiro — `chat` e `requests` dependem da sua API
pública para anexos. Depois, `categories` + `chat` (`backend-domain`) e
`notifications` (`backend-platform`) correm em paralelo.

**Porta de saída:** as sete operações do contrato v1.0.0 sem dono passam a ter
implementação e teste; `ApplicationModules.verify()` verde com as fronteiras
novas declaradas.

### Onda 2 — paralela, verificação

- `qa-e2e` — integração transversal com infraestrutura real e E2E dos fluxos
  críticos.
- `security-auditor` — auditoria só de leitura antes do merge.

**Porta de saída:** suíte estável e sem achados críticos por resolver.

## 4. Protocolo de coordenação

1. **Fora do âmbito, pára.** Um agente que precise de escrever num caminho que não
   é seu não o faz: reporta e escala. Isto é a regra que faz o resto funcionar.
2. **Dependências novas pedem-se ao dono do ficheiro de build** (`backend-platform`
   para o POM; `web-frontend` para `package.json`; `mobile-flutter` para
   `pubspec.yaml`). Dois agentes nunca editam o mesmo manifesto na mesma onda.
3. **Alterações de contrato a meio de uma onda** são pedidas ao `api-contract`,
   que as aplica, valida a compatibilidade e anuncia; os consumidores regeneram.
4. **Migrações pedem-se ao `db-migrations`**, que atribui a numeração. É o que
   evita duas migrações `V7__`.
5. **Fronteiras de módulo declara-as o `backend-platform`.** Os
   `package-info.java` com `@ApplicationModule` — incluindo `allowedDependencies`
   — são dele, mesmo nos módulos que outro agente implementa. Um agente que
   precise de uma dependência de módulo nova pede-a com motivo. Se cada agente
   pudesse alargar as suas próprias dependências, `ApplicationModules.verify()`
   deixaria de verificar o que quer que fosse.
6. **Cada agente entrega o ramo verde**: compila, lint limpo, testes a passar. Um
   ramo vermelho bloqueia os outros e anula o ganho do paralelismo.
7. **Isolamento**: um *git worktree* por agente quando correrem em simultâneo na
   mesma máquina, e uma branch `feat/<agente>/<assunto>` por unidade de trabalho.

## 5. Invocação

Cada agente é despachado com um objetivo e as suas fronteiras já implícitas na
definição. Numa onda paralela, os agentes independentes são lançados **na mesma
mensagem**, para correrem em concorrência em vez de em série.

Exemplo de onda 1:

```
Lança em paralelo:
- backend-domain:   implementa o módulo requests conforme o contrato (DRAFT→PUBLISHED→…),
                    com testes de transição ilegal.
- backend-matching: implementa o predicado de elegibilidade nos dois modos de cobertura,
                    com Testcontainers PostGIS.
- backend-payments: implementa a receção idempotente de webhooks e o ciclo de vida
                    da subscrição.
- web-frontend:     implementa o fluxo publicar pedido → ver propostas → aceitar,
                    sobre o cliente gerado.
```

## 6. Limites honestos deste modelo

- **O paralelismo não é gratuito.** Cada agente extra acrescenta custo de
  coordenação e de revisão. Com poucas pessoas a rever, correr cinco agentes em
  simultâneo pode produzir mais trabalho pendente do que capacidade de o validar.
- **A porta de saída de cada onda é o gargalo real.** Se o CI for lento ou
  instável, o modelo degrada-se: os agentes ficam bloqueados uns pelos outros e o
  ganho evapora-se. A fiabilidade da pipeline é pré-requisito, não acessório.
- **A matriz de ownership é uma convenção, não um mecanismo.** O que a torna
  efetiva é a revisão e o CI (verificação de fronteiras, validação do contrato).
  Sem isso, é apenas documentação.
- **A Onda 0 é genuinamente sequencial.** Tentar antecipar a Onda 1 sobre um
  contrato instável é a forma mais rápida de produzir três implementações
  incompatíveis do mesmo endpoint.

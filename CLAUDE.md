# ServiMatch — Guia do Monorepo (contexto para agentes)

Marketplace B2C que liga clientes a prestadores de serviços locais em Portugal.
Fonte de verdade da arquitetura: **`docs/ARQUITETURA.md`** e os **ADR** em `docs/adr/`.
Contrato de API: **`docs/api/openapi.yaml`** (OpenAPI 3.1).

Antes de escrever qualquer código, lê o ADR relevante. Se a tua alteração
contradiz um ADR, **não a implementes**: escreve um novo ADR (skill `adr-madr`)
e escala ao agente `arquiteto`.

---

## 1. Layout

```
service-match/
├── CLAUDE.md                 # este ficheiro
├── .claude/
│   ├── agents/               # definições dos agentes
│   └── skills/               # skills partilhadas (procedimentos reutilizáveis)
├── docs/
│   ├── ARQUITETURA.md        # especificação funcional + arquitetura
│   ├── AGENTES.md            # plano de execução paralela (ondas)
│   ├── adr/                  # decisões arquiteturais (MADR)
│   └── api/openapi.yaml      # contrato único, partilhado por web e mobile
├── backend/                  # Spring Boot + Spring Modulith (Java 21)
├── web/                      # React + Vite + TypeScript (+ BFF)
├── mobile/                   # Flutter (iOS + Android)
└── infra/                    # docker-compose, realm Keycloak, CI/CD, IaC
```

## 2. Regra de ouro: contrato primeiro

`docs/api/openapi.yaml` é o **único** ponto de acordo entre backend, web e mobile.

1. Qualquer mudança de API começa por uma alteração ao contrato, feita **apenas**
   pelo agente `api-contract`.
2. Backend, web e mobile **implementam** o contrato; nunca o inferem do código
   uns dos outros.
3. Código gerado (cliente Dart, tipos TypeScript, DTOs) **nunca é editado à mão**
   e nunca é a fonte de verdade — regenera-se.
4. A evolução é **aditiva**: não se remove nem se renomeia campo, não se aperta
   validação, não se muda o significado de um valor de enum já publicado. Ver
   `docs/adr/0008` e a skill `openapi-contract-first`.

## 3. Matriz de ownership (escrita exclusiva)

Um caminho tem **um único agente com direito de escrita**. Os restantes podem ler.
É isto que torna a execução paralela segura.

| Caminho | Agente proprietário |
|---|---|
| `docs/ARQUITETURA.md`, `docs/adr/**` | `arquiteto` |
| `docs/api/**` | `api-contract` |
| `backend/pom.xml`, `backend/**/config/**`, `backend/**/platform/**`, `backend/src/main/resources/application*.yml`, `backend/**/modules/{uploads,notifications}/**`, `backend/**/modules/*/package-info.java` | `backend-platform` |
| `backend/**/modules/{users,providers,requests,proposals,bookings,reviews,categories,chat}/**` — exceto `package-info.java` | `backend-domain` |
| `backend/**/modules/{matching,geo,search}/**` — exceto `package-info.java` | `backend-matching` |
| `backend/**/modules/{billing,payments}/**` — exceto `package-info.java` | `backend-payments` |
| `backend/src/main/resources/db/migration/**` | `db-migrations` |
| `backend/src/test/**` (testes de integração transversais) | `qa-e2e` |
| `web/**` | `web-frontend` |
| `mobile/**` | `mobile-flutter` |
| `infra/**`, `.github/workflows/**` | `platform-infra` |
| `.claude/**`, `CLAUDE.md` | `arquiteto` |

**Ficheiros de build partilhados** (`backend/pom.xml`, `web/package.json`,
`mobile/pubspec.yaml`) são pontos de conflito garantidos. Regra: quem precisa de
uma dependência nova **pede-a** ao proprietário do ficheiro em vez de a
adicionar. Nunca dois agentes editam o mesmo POM na mesma onda.

**Todo o endpoint do contrato tem um módulo dono.** Regra de colocação: endpoint
com **estado persistido** → módulo em `modules/`; endpoint **derivado apenas de
configuração** → `platform/`. Daí `GET /v1/app/version-status` viver em
`platform/appversion` (regras por configuração, sem tabela) e não ser módulo.
Mapa das capacidades que não estavam atribuídas:

| Endpoint | Módulo | Agente |
|---|---|---|
| `GET /v1/categories` | `modules/categories` | `backend-domain` |
| `GET`/`POST /v1/conversations/{id}/messages` | `modules/chat` | `backend-domain` |
| `POST /v1/uploads` | `modules/uploads` | `backend-platform` |
| `POST`/`DELETE /v1/device-tokens` | `modules/notifications` | `backend-platform` |
| `GET /v1/app/version-status` | `platform/appversion` | `backend-platform` |

**`package-info.java` é declaração de fronteira, não implementação.** O
`@ApplicationModule` (nome, `allowedDependencies`, interfaces nomeadas) é escrito
**apenas** pelo `backend-platform`, inclusive dentro de módulos de outros agentes.
Nenhum agente alarga as suas próprias dependências permitidas — precisas de uma
dependência de módulo nova, pedes, com motivo. É o que impede que
`ApplicationModules.verify()` passe a ser auto-certificação.

## 4. Invariantes de segurança (não negociáveis)

Derivam dos ADR 0002 e 0009. Qualquer PR que os viole é rejeitado, sem exceção.

- O backend é **apenas OAuth2 Resource Server**. Não emite tokens, não faz hash
  de passwords, não gere refresh tokens. O IdP é o Keycloak.
- **Nunca** guardar tokens em `localStorage`/`sessionStorage`. Web: padrão BFF com
  cookie `HttpOnly`/`Secure`/`SameSite`. Mobile: RFC 8252 + Keychain/Keystore.
- **Nunca** usar webview embebido para autenticação em mobile (RFC 8252).
- O *gating* por subscrição é uma **regra de domínio no servidor**. Um cliente
  nunca é autoridade sobre o seu plano; a UI só espelha o que o servidor decide.
- **A elegibilidade resolve-se na leitura, nunca a partir de estado projetado**
  (ADR-0011). O facto "subscrição" só é respondido por `billing`
  (`SubscriptionLifecycle`); o facto "aprovação" só por `providers`. Não se copia
  o estado da subscrição para outra tabela, e o conjunto de estados que concede
  visibilidade (`ACTIVE`/`PAST_DUE` + período válido) tem **um único literal** no
  código, publicado por `billing`. Quem duplica a regra reabre o defeito.
- Webhooks de pagamento: verificar assinatura, ser idempotentes (`raw_event_id`
  único) e ter job de reconciliação. Nunca ativar subscrição a partir de um
  evento não verificado.
- Uploads: validar por *magic bytes*, nunca por extensão; servir por URL assinado
  com expiração.
- Segredos não entram no repositório. `.env.example` sim, `.env` não.
- PII em logs: proibida. Correlacionar por `correlation_id`, não por email.

## 5. Convenções

- **Java 21 LTS**; Spring Boot 3.5.x + Spring Modulith 1.4.x. Baseline **fechada**
  pelo ADR-0003; a migração para Boot 4.x tem critérios definidos e exige ADR novo.
- **Erros HTTP**: RFC 9457 Problem Details, `type` sob `https://errors.servimatch.pt/`.
- **Dinheiro**: sempre `amountCents` (inteiro) + `currency` ISO-4217. Nunca `double`.
- **Paginação**: cursor, envelope `{ items, page: { nextCursor } }`.
- **Escritas não idempotentes**: aceitar cabeçalho `Idempotency-Key`.
- **Idioma**: código, identificadores e mensagens de commit em inglês;
  documentação e ADR em português (pt-PT).
- **Commits**: Conventional Commits com âmbito do módulo —
  `feat(matching): add radius coverage predicate`.
- **Branches**: `feat/<agente>/<assunto>`, uma por agente e por onda.
- Nada é considerado feito sem teste automatizado a cobrir o caminho principal
  **e** pelo menos um caso de erro.
- **Fixtures não fabricam estado que a produção tem de produzir** (ADR-0011 D9).
  Um teste leva o sistema ao estado pelo caminho de produção (API pública do
  módulo dono). `INSERT` direto só é tolerável se existir, no mesmo conjunto de
  testes, um teste que exercite a **transição** que produz esse estado. Corolário
  aplicável em revisão: toda a coluna lida por um predicado de decisão tem de ter,
  em produção, pelo menos um escritor identificável — se não tem, é defeito, não é
  "por implementar". Foi assim que o *gating* por subscrição passou meses verde e
  desligado.

## 6. Protocolo de execução paralela

Detalhe completo em `docs/AGENTES.md`. Resumo:

- **Onda 0 (sequencial, bloqueante)**: `arquiteto` → `api-contract` →
  `backend-platform` + `db-migrations` + `platform-infra`. Produz contrato,
  esqueleto compilável, schema e ambiente local. Nada mais arranca antes.
- **Onda 1 (paralela)**: `backend-domain`, `backend-matching`, `backend-payments`,
  `web-frontend`, `mobile-flutter`.
- **Onda 2 (paralela, só leitura + testes)**: `qa-e2e`, `security-auditor`.

Regras de coordenação:

1. Um agente que precise de escrever fora do seu âmbito **para** e reporta. Não
   negoceia sozinho a fronteira.
2. Alterações de contrato durante uma onda são pedidos ao `api-contract`, que as
   aplica e anuncia; os consumidores regeneram o cliente.
3. Cada agente deixa o ramo verde: compila, lint limpo, testes a passar.
4. Isolamento recomendado: um *git worktree* por agente quando corram em
   simultâneo sobre a mesma máquina.

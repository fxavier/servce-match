# Prompts de arranque para o Claude Code

Um prompt por onda. **Não juntes as ondas num só prompt**: a Onda 0 é
genuinamente sequencial e tentar antecipar a Onda 1 sobre um esqueleto que ainda
não compila produz três implementações incompatíveis do mesmo endpoint.

Entre ondas, confirma a porta de saída (secção 3 de `docs/AGENTES.md`) antes de
colar o prompt seguinte.

---

## Prompt 0 — pré-voo (uma vez, antes de tudo)

```
Estás na raiz do monorepo ServiMatch. Antes de qualquer trabalho, executa esta
verificação de pré-voo e reporta o resultado em tabela, sem corrigir nada por
iniciativa própria:

1. Confirma que existem: CLAUDE.md, docs/ARQUITETURA.md, docs/AGENTES.md,
   docs/adr/0001..0009 + README.md, docs/api/openapi.yaml.
2. Confirma que .claude/agents/ tem 12 ficheiros .md e .claude/skills/ tem 9
   SKILL.md. Lista os nomes.
3. Valida docs/api/openapi.yaml: parse YAML, validação OpenAPI 3.1, e contagem
   de caminhos/operações/schemas. O esperado é 18/20/35.
4. Verifica se a pasta é um repositório git (.git presente).
5. Confirma que não há ADR com decisões em aberto.

Se (4) falhar, corre `git init`, cria um .gitignore adequado a um monorepo
Java + Node + Flutter, e faz o commit inicial com todos os documentos.
Não faças mais nada. Não escrevas código de aplicação.
```

---

## Prompt 1 — Onda 0 (esqueleto, schema, ambiente)

```
Lê CLAUDE.md e docs/AGENTES.md e executa a Onda 0.

Passo A (sequencial, primeiro e sozinho) — subagente `api-contract`:
congela a versão do contrato para a primeira iteração (regista a versão em
docs/api/openapi.yaml e o critério de compatibilidade), e prepara os alvos de
geração de cliente para web e mobile. Não acrescenta nem altera endpoints.

Passo B — só depois de A terminar, lança EM PARALELO, numa única mensagem, três
subagentes. Escrevem em caminhos disjuntos e nenhum toca no âmbito do outro:

- `backend-platform`: esqueleto Maven compilável em backend/ com Java 21,
  Spring Boot 3.5.x e Spring Modulith 1.4.x (ADR-0003, baseline fechada e não
  negociável). Inclui: configuração de OAuth2 Resource Server contra Keycloak,
  @RestControllerAdvice com RFC 9457, filtro de Idempotency-Key, rate limiting
  Bucket4j, Event Publication Registry, observabilidade (Actuator + Micrometer +
  correlation id) e o teste ApplicationModules.verify() a falhar o build em caso
  de violação de fronteira. É o único que escreve em backend/pom.xml.

- `db-migrations`: migrações Flyway iniciais em
  backend/src/main/resources/db/migration com PostGIS ativo, o schema descrito
  em docs/ARQUITETURA.md §9, os índices críticos (§9.4) e as constraints
  obrigatórias — users.keycloak_sub UNIQUE, device_token.token UNIQUE,
  UNIQUE (gateway, raw_event_id) na tabela de eventos em bruto. É o único que
  atribui numeração de migrações.

- `platform-infra`: infra/ com docker-compose (PostgreSQL+PostGIS, Keycloak,
  Redis, MinIO, MailHog), realm Keycloak versionado em
  infra/keycloak/realm-servimatch.json com os roles CUSTOMER/PROVIDER/ADMIN e os
  clients de web e mobile, .env.example (nunca .env), e workflows de CI em
  .github/workflows incluindo a verificação de compatibilidade retroativa do
  OpenAPI.

Regras que se aplicam aos três:
- Ownership exclusivo por caminho (secção 3 do CLAUDE.md). Quem precisar de
  escrever fora do seu âmbito PARA e reporta — não negoceia a fronteira sozinho.
- Dependências novas no pom.xml pedem-se ao `backend-platform`; nenhum outro lhe
  toca.
- Invariantes de segurança da secção 4 do CLAUDE.md são inegociáveis.
- Cada um deixa o seu âmbito verde e reporta o que ficou por fazer.

Porta de saída, que verificas TU no fim e me reportas explicitamente:
`mvn verify` verde com ApplicationModules.verify(), `docker compose up`
funcional com o realm importado, contrato validado. Se algum falhar, diz-me o
que falhou em vez de avançar para a Onda 1.
```

---

## Prompt 2 — Onda 1 (domínio e clientes, paralelo)

Só depois da porta de saída da Onda 0 estar verde.

```
Porta de saída da Onda 0 confirmada. Executa a Onda 1 de docs/AGENTES.md:
lança EM PARALELO, numa única mensagem, os cinco subagentes abaixo. Escrevem em
caminhos disjuntos e comunicam por eventos de domínio, nunca por chamadas
diretas entre módulos.

- `backend-domain`: módulos users, providers, requests, proposals, bookings,
  reviews. Máquinas de estado de ServiceRequest e Proposal conforme
  docs/ARQUITETURA.md §4.3 e §4.4, com teste para cada transição ilegal.
  Invariantes: review só a partir de Booking COMPLETED; gating por subscrição é
  regra de servidor; bloqueio otimista na aceitação concorrente de propostas.

- `backend-matching`: módulos matching, geo, search. Predicado de elegibilidade
  nos dois modos de cobertura (RADIUS e ADMIN_REGION), ST_DWithin sobre
  geography com índice GiST, geocodificação assíncrona e fora do caminho
  crítico, FTS PostgreSQL. Inclui EXPLAIN a provar uso de índice.

- `backend-payments`: módulos billing e payments. Porta PaymentGateway com
  implementações Stripe e Eupago/IfthenPay; Multibanco modelado como referência
  de uso único e renovação por fatura, nunca como cartão; receção de webhooks
  assinada, idempotente por (gateway, raw_event_id) e processada de forma
  assíncrona; job de reconciliação.

- `web-frontend`: SPA React + Vite + TypeScript com BFF. Fluxo publicar pedido →
  ver propostas → aceitar, sobre o cliente gerado a partir do contrato. Tokens
  em cookie HttpOnly/Secure/SameSite via BFF — nunca em localStorage nem
  sessionStorage.

- `mobile-flutter`: app Flutter com Riverpod, go_router, dio+retrofit gerado do
  contrato. Autenticação RFC 8252 com AppAuth + PKCE no browser do sistema,
  tokens em Keychain/Keystore, nunca webview embebido. Ecrã de force-update
  sobre GET /v1/app/version-status.

Regras: ownership exclusivo por caminho; código gerado nunca é editado à mão;
alterações de contrato pedem-se ao `api-contract` e não se fazem localmente;
nada é dado como feito sem teste do caminho principal e de pelo menos um caso de
erro.

No fim, reporta por agente: o que ficou pronto, o que ficou por fazer e qualquer
pedido que tenha ficado bloqueado à espera do dono de outro caminho.
```

---

## Prompt 3 — Onda 2 (verificação, paralelo)

```
Executa a Onda 2 de docs/AGENTES.md: lança EM PARALELO, numa única mensagem,
dois subagentes.

- `qa-e2e`: testes de integração com Testcontainers e infraestrutura real
  (PostGIS, Keycloak, Redis, MinIO) — nunca H2, nunca geo ou JWT mockados.
  Cobre obrigatoriamente: matriz de autorização por role, transições ilegais de
  estado, webhook com assinatura inválida, webhook duplicado sem efeito, eventos
  fora de ordem, upload com magic bytes divergentes do contentType declarado, e
  o fluxo E2E completo pedido → matching → proposta → aceitação → booking →
  review.

- `security-auditor`: auditoria só de leitura contra a secção 4 do CLAUDE.md e
  os ADR 0002 e 0009. Classifica cada achado por severidade, com ficheiro e
  linha, e diz o que falha e porquê. Não corrige nada.

Reporta os achados por severidade e diz-me quais bloqueiam o merge.
```

---

## Nota sobre paralelismo real

O ganho vem de os subagentes serem lançados **na mesma mensagem** — em mensagens
separadas correm em série. E vem também de os caminhos serem disjuntos: dois
subagentes a escrever no mesmo ficheiro produzem a última escrita a ganhar, sem
aviso.

O gargalo real não é o número de agentes: é a tua capacidade de rever o que eles
produzem. Cinco agentes em paralelo geram mais trabalho pendente de validação do
que uma pessoa valida numa sessão. Se for esse o caso, corre a Onda 1 em dois
lotes (os três de backend primeiro, os dois de frontend depois) em vez de
aumentar o paralelismo.

# ServiMatch — Especificação Funcional e Arquitetura

| | |
|---|---|
| **Documento** | Especificação Funcional e Arquitetura de Referência |
| **Produto** | ServiMatch — Marketplace de serviços locais |
| **Versão** | 1.1 (refinada; inclui cliente móvel Flutter) |
| **Estado** | Baseline para MVP |
| **Data** | 2026-07-24 |
| **Âmbito** | Clientes: Web (React) e Mobile (Flutter, iOS + Android) · Backend (Spring Boot, Modular Monolith) · Keycloak |

> **Nota de leitura.** Este documento reescreve e consolida a especificação original, resolvendo inconsistências (nomeadamente a autenticação, que passa a ser exclusivamente **Keycloak + OAuth2/OIDC**), acrescentando requisitos não-funcionais, decisões arquiteturais justificadas (ADRs), modelo de dados detalhado, máquinas de estado, contratos de API essenciais e estratégia de matching geográfico. As recomendações estão marcadas com **Recomendação** e as decisões formais em **ADR-xx**. A **v1.1** promove a aplicação móvel **Flutter** de "evolução futura" a **cliente de primeira classe** — app única para **Cliente + Prestador**, **iOS + Android**, sequenciada como *fast-follow* do web (ver ADR-0008 e ADR-0009).

---

## 1. Sumário Executivo

O ServiMatch é um **marketplace B2C** que liga clientes a profissionais/empresas de serviços locais (limpeza, canalização, eletricidade, pintura, carpintaria, jardinagem, construção, remodelações, climatização, mudanças, reparações, assistência técnica, manutenção, entre outros).

O modelo de receita é **subscrição do lado da oferta**: o cliente usa a plataforma gratuitamente e paga zero por publicar pedidos; o prestador precisa de uma **subscrição ativa** para ser visível, receber pedidos e enviar propostas. Isto define um *marketplace com monetização single-sided* — decisão que simplifica a operação no MVP (sem gestão de escrow nem fluxo de dinheiro entre partes), a troco de um risco de arranque (*chicken-and-egg*) que a estratégia de go-to-market tem de endereçar.

Arquiteturalmente, adota-se um **Modular Monolith com Spring Modulith**, com fronteiras de módulo explícitas e comunicação inter-módulo por **eventos de domínio**, mantendo a opção de extrair módulos para serviços independentes se e quando o volume o justificar. A autenticação e a gestão de identidade são delegadas ao **Keycloak** (OAuth2/OIDC), ficando o backend como *OAuth2 Resource Server* sem lógica de credenciais própria.

O sistema é **multi-cliente**: uma aplicação **web (React)** e uma aplicação **móvel (Flutter, iOS + Android)** consomem a mesma API REST. Isto torna a **estabilidade e o versionamento do contrato de API** um requisito central (§11.4), porque os clientes móveis não se atualizam de forma instantânea. A app móvel é uma **app única adaptável por *role*** (Cliente ou Prestador após login) e arranca como *fast-follow* do web, reutilizando o backend já validado.

---

## 2. Visão Geral e Contexto

### 2.1 Problema

A contratação de serviços locais é fragmentada e de baixa confiança: o cliente não sabe quem está disponível na sua zona, não consegue comparar orçamentos com facilidade e não tem sinais de reputação fiáveis. O prestador, por seu lado, não tem um canal previsível de aquisição de leads qualificados.

### 2.2 Proposta de valor

- **Cliente:** publica um pedido uma vez e recebe propostas de prestadores **qualificados, ativos e da sua zona**, com chat e avaliações para reduzir incerteza. Custo zero.
- **Prestador:** acesso a *leads* segmentados por categoria e localização mediante subscrição mensal previsível.

### 2.3 Princípios de arquitetura

1. **Simplicidade primeiro.** Modular monolith antes de microserviços; extrair só perante pressão real de escala/organização (ver ADR-01).
2. **Fronteiras explícitas.** Cada módulo expõe uma API mínima e comunica por eventos; nada de acesso direto a tabelas de outro módulo.
3. **Identidade delegada.** Zero gestão de passwords/tokens no código de aplicação (ver ADR-02).
4. **Estado como cidadão de primeira classe.** Entidades centrais (pedido, proposta, subscrição) modeladas como máquinas de estado explícitas.
5. **Observabilidade desde o dia 1.** Logs estruturados, métricas e tracing distribuído por defeito.
6. **API-first / multi-cliente.** A API REST é o contrato partilhado por web e mobile: *client-agnostic*, versionada e com **compatibilidade retroativa** (um cliente móvel numa versão antiga tem de continuar a funcionar). O contrato é descrito em **OpenAPI** e verificado por testes de contrato.

---

## 3. Modelo de Negócio

### 3.1 Tipo de marketplace

**B2C, monetização single-sided (lado da oferta).** O fluxo de pagamento no MVP é exclusivamente **prestador → plataforma** (subscrição). Não há fluxo de dinheiro cliente → prestador dentro da plataforma no MVP; o pagamento do serviço é acordado e liquidado fora da plataforma. Escrow/pagamentos entre partes ficam para evolução futura (ver §20).

### 3.2 Cliente

Utiliza a plataforma **gratuitamente**. Pode: criar conta, publicar pedidos, receber propostas, conversar com prestadores, escolher o orçamento, avaliar serviços. Não há qualquer custo associado à publicação de pedidos.

### 3.3 Prestador

Requer **subscrição ativa** para operar. A tabela abaixo formaliza o que é permitido em cada estado — este é o ponto de gating central do produto e deve ser aplicado de forma consistente em pesquisa, matching, propostas e chat.

| Capacidade | Sem subscrição / Expirada | Com subscrição ativa |
|---|:---:|:---:|
| Criar conta / completar perfil | ✅ | ✅ |
| Configurar categorias e zonas | ✅ | ✅ |
| Manter histórico e dados registados | ✅ | ✅ |
| Aparecer em pesquisas | ❌ | ✅ |
| Ser incluído no matching / receber pedidos | ❌ | ✅ |
| Enviar propostas | ❌ | ✅ |
| Contactar clientes (chat) | ❌ | ✅ |
| Ser recomendado pelo sistema | ❌ | ✅ |

**Semântica de expiração.** Ao expirar a subscrição: o perfil permanece registado, o histórico permanece disponível, o prestador deixa de ser visível, deixa de entrar no matching, deixa de poder enviar propostas e o chat de novas conversas é bloqueado. **Recomendação:** conversas já em curso à data da expiração ficam em modo *read-only* para o prestador (evita cortar negociações a meio e reduz churn), enquanto novas conversas são bloqueadas — este comportamento deve ser explícito no produto.

### 3.4 Planos

O MVP suporta **planos mensais**. Estrutura de referência:

| Plano | Preço/mês | Benefícios |
|---|---|---|
| Starter | € 19,90 | Até 3 categorias e 2 zonas |
| Professional | € 39,90 | Categorias e zonas ilimitadas |
| Premium | € 69,90 | Maior destaque, prioridade nas pesquisas, selo Premium |

**Decisão de MVP.** Basta **um único plano ativo** no arranque, mas o modelo de dados deve suportar múltiplos planos e *feature limits* desde já (§9), para não exigir migração de esquema quando os planos forem introduzidos. Os limites (nº de categorias, nº de zonas, prioridade de ranking, selo) devem ser **dados configuráveis no plano**, não constantes no código.

### 3.5 Objetivos de negócio (mensuráveis)

- Encontrar profissionais próximos e ativos — *time-to-first-proposal* baixo.
- Comparar propostas de forma normalizada (preço, prazo, reputação).
- Reduzir fricção de contratação — *time-to-hire* baixo.
- Aumentar confiança via avaliações verificadas (só quem teve serviço concluído avalia).
- Criar receita recorrente previsível (MRR) via subscrições.

---

## 4. Requisitos Funcionais

### 4.1 Personas e capacidades

**Cliente:** registo/login (via Keycloak), recuperação de password (Keycloak), gestão de perfil, gestão de moradas, criação de pedidos, upload de fotografias, acompanhamento de propostas, chat, avaliações, histórico.

**Prestador:** perfil profissional e de empresa, portfólio, fotografias, certificações, categorias, zonas de atuação, agenda/disponibilidade, receção de pedidos, envio de propostas, chat, avaliações, histórico, gestão da subscrição.

**Administrador:** gestão de utilizadores e prestadores, gestão de categorias e zonas, gestão de planos, gestão de pagamentos, aprovação de perfis, moderação, auditoria, estatísticas.

### 4.2 Fluxo principal (happy path)

1. **Cliente** cria pedido → escolhe categoria → define localização → descrição → fotografias → publica.
2. **Sistema (Matching)** seleciona prestadores que cumprem **todos** os critérios: subscrição ativa **E** cobrem a zona **E** trabalham a categoria **E** perfil ativo/aprovado. Emite notificação a esses prestadores.
3. **Prestador** analisa, conversa e envia proposta.
4. **Cliente** recebe várias propostas, compara, conversa e aceita uma.
5. **Serviço:** agendamento → execução → conclusão → avaliação (bilateral).

> A aceitação de uma proposta **não** implica exclusividade automática de fecho das restantes: recomenda-se transitar as restantes propostas para `SUPERSEDED` (informativo) quando o pedido passa a `CONFIRMED`, mantendo-as auditáveis (§4.4).

### 4.3 Máquina de estados — `ServiceRequest`

```
DRAFT ──publicar──▶ PUBLISHED ──1ª proposta──▶ IN_NEGOTIATION ──aceitar proposta──▶ CONFIRMED
  │                    │                              │                                  │
  │                    └──────────────────────────────┴───────────► CANCELLED           ▼
  └──► CANCELLED                                                                     IN_PROGRESS
                                                                                         │
                                                                                         ▼
                                                                                     COMPLETED
```

Transições válidas (guardas resumidas):

| De | Para | Guarda |
|---|---|---|
| DRAFT | PUBLISHED | Campos obrigatórios preenchidos; cliente autenticado |
| PUBLISHED | IN_NEGOTIATION | Existe ≥1 proposta `SENT` |
| IN_NEGOTIATION | CONFIRMED | Cliente aceita uma proposta |
| PUBLISHED / IN_NEGOTIATION | CANCELLED | Ação do cliente ou admin; sem serviço em execução |
| CONFIRMED | IN_PROGRESS | Marcação/agenda ativada |
| IN_PROGRESS | COMPLETED | Confirmação de conclusão |
| COMPLETED | — | Terminal; habilita avaliação |

### 4.4 Máquina de estados — `Proposal`

```
SENT ──cliente aceita──▶ ACCEPTED
  │
  ├──prestador cancela──▶ CANCELLED
  ├──cliente rejeita────▶ REJECTED
  └──validade expira────▶ EXPIRED
Quando o pedido é CONFIRMED por outra proposta: SENT ──▶ SUPERSEDED
```

Cada proposta tem: preço, descrição, prazo (dias/data), validade (data-limite). **Recomendação:** um pedido admite **no máximo uma proposta ativa por prestador** (constraint única `(request_id, provider_id)` sobre propostas em estado não-terminal); reenviar substitui a anterior por versão.

### 4.5 Estados de subscrição — ver §12. Estados de agenda/`Booking` — ver §13.

### 4.6 Restantes funcionalidades

- **Autenticação:** delegada a Keycloak (login, registo, recuperação de password, verificação de email, MFA opcional). O backend **não** emite nem valida credenciais próprias (§8).
- **Catálogo:** categorias e subcategorias hierárquicas; pesquisa e filtros.
- **Chat:** mensagens, fotografias, documentos, emojis, notificações.
- **Agenda:** disponibilidade, marcações, lembretes.
- **Avaliações:** classificação (1–5), comentários, histórico. **Só avalia quem teve um `Booking` em `COMPLETED`** (avaliação verificada). São **bilaterais**: cliente avalia prestador e prestador avalia cliente sobre a mesma marcação, cada um uma só vez (§9.2).
- **Notificações:** push (FCM) e email.

---

## 5. Requisitos Não-Funcionais (NFR)

> Alvos de referência para o MVP e primeiros meses. Devem ser tratados como *SLOs* e verificados por métricas (§17).

### 5.1 Desempenho

- Latência de leitura (listagens, pesquisa) **p95 < 300 ms**, **p99 < 800 ms** (excluindo geocoding externo).
- Latência de escrita (criar pedido/proposta) **p95 < 500 ms**.
- Entrega de notificação de novo pedido a prestadores elegíveis **< 5 s** (via evento assíncrono).
- Geocoding e matching não devem bloquear a resposta HTTP de publicação: **matching é assíncrono** (evento `RequestPublished`).

### 5.2 Escalabilidade

- Backend **stateless** (sem sessão HTTP local) → escala horizontal atrás de load balancer.
- Estado partilhado (rate limiting distribuído, cache, relay de WebSocket) externalizado em **Redis** quando houver >1 instância (ver ADR-06).
- Base de dados como primeiro gargalo esperado: índices adequados (§9.4), *connection pool* (HikariCP) dimensionado, réplicas de leitura como evolução.

### 5.3 Disponibilidade e resiliência

- Alvo MVP **99,5%** (janela de manutenção permitida); evoluir para 99,9%.
- *Timeouts* e *circuit breakers* em toda a integração externa (Keycloak, geocoding, gateway de pagamentos, S3, FCM) — Resilience4j.
- Idempotência obrigatória em webhooks de pagamento e em operações de escrita sensíveis (§12.4).
- *Graceful degradation:* falha de geocoding externo → fila de *retry*; falha de FCM → fallback para email.

### 5.4 Segurança

Ver §8 (detalhe). Requisitos transversais: TLS em todo o lado, princípio do menor privilégio, validação e *sanitização* de todos os inputs, *rate limiting*, auditoria de ações sensíveis, conformidade RGPD.

### 5.5 Observabilidade

- **Logs** estruturados em JSON com *correlation id* (propagado do gateway ao evento assíncrono).
- **Métricas** via Micrometer → Prometheus; dashboards por módulo e por SLO.
- **Tracing** distribuído (OpenTelemetry) cobrindo HTTP + processamento de eventos.
- **Health/readiness** via Spring Boot Actuator.

### 5.6 Manutenibilidade

- Fronteiras de módulo verificadas em teste (Spring Modulith `ApplicationModules.verify()`).
- Cobertura de testes com foco em domínio e fluxos de estado (§17).
- Migrações versionadas (Flyway), *zero* alterações manuais de esquema.

### 5.7 Privacidade / RGPD

Base legal por finalidade, minimização de dados, direito ao apagamento e à portabilidade, retenção definida, PII cifrada em repouso pelo fornecedor de storage/DB, separação entre identidade (Keycloak) e dados de domínio (§18).

---

## 6. Arquitetura de Software

### 6.1 Estilo: Modular Monolith (Spring Modulith)

Uma única aplicação deployável, internamente particionada em **módulos de aplicação** com fronteiras verificadas em tempo de teste. Cada módulo:

- Expõe uma **API pública mínima** (interfaces/serviços e DTOs) e mantém o resto *package-private*.
- **Não acede** ao esquema de tabelas de outro módulo; a integração faz-se por **chamada à API pública** (síncrona) ou por **evento de domínio** (assíncrona, preferida para efeitos colaterais).
- É candidato a extração futura para serviço próprio sem reescrita do domínio.

### 6.2 Vista de contexto (C4 nível 1, textual)

```
   ┌───────────┐   ┌────────────────┐   OIDC    ┌────────────┐
   │ SPA React │   │  App Flutter   │  OAuth2   │  Keycloak  │
   │  (web)    │   │ (iOS+Android)  │ ────────▶ │   (IdP)    │
   └─────┬─────┘   └───────┬────────┘   +PKCE   └─────┬──────┘
         │                 │                          │ JWKS
         └──── Bearer JWT ─┴──── HTTPS ───┐           │
                                          ▼           ▼
          ┌──────────────────────────────────────────────┐
          │  Backend ServiMatch (Spring Boot + Modulith)   │
          │  OAuth2 Resource Server                        │
          │  users │ providers │ requests │ proposals │... │
          └──┬───────┬───────┬────────┬────────┬───────────┘
             │       │       │        │        │
        PostgreSQL Redis  Object   Geocoding  FCM / SMTP
        (+PostGIS)        Store    (Nominatim) (push iOS/Android
                          (S3/R2)      │        /web + email)
                                  Payment Gateway
                               (Stripe/Eupago/IfthenPay)
```

Ambos os clientes (web e mobile) são consumidores da **mesma API REST** e autenticam contra o **mesmo Keycloak**, diferindo apenas no fluxo de obtenção/armazenamento de tokens (web: BFF/cookie ou PKCE em memória, §8.3; mobile: RFC 8252 + *secure storage*, §8.3.1 / ADR-0009).

### 6.3 Módulos e dependências permitidas

| Módulo | Responsabilidade | Depende de (API pública) | Publica eventos |
|---|---|---|---|
| `common` | tipos partilhados, erros, utilitários | — | — |
| `auth` (adapter) | mapeamento de identidade Keycloak ↔ utilizador de domínio | users | `UserProvisioned` |
| `users` | conta de domínio, papéis, moradas | common | `UserRegistered` |
| `customers` | perfil de cliente | users | — |
| `providers` | perfil profissional, empresa, portfólio, categorias, zonas | users, categories | `ProviderActivated`, `ProviderDeactivated` |
| `categories` | catálogo, subcategorias | common | — |
| `requests` | pedidos, imagens, estados | customers, categories | `RequestPublished`, `RequestConfirmed`, `RequestCancelled` |
| `proposals` | propostas, estados | requests, providers | `ProposalSent`, `ProposalAccepted` |
| `subscriptions` | planos, subscrições, ciclo de vida | providers | `SubscriptionActivated`, `SubscriptionExpired` |
| `payments` | pagamentos de planos, webhooks | subscriptions | `PaymentSucceeded`, `PaymentFailed` |
| `chat` | conversas, mensagens | requests, users | `MessageSent` |
| `schedule` | disponibilidade, marcações, lembretes | proposals | `BookingConfirmed`, `BookingCompleted` |
| `reviews` | avaliações verificadas | schedule | `ReviewSubmitted` |
| `notifications` | push (FCM/APNs) e email, preferências, registo de `DeviceToken` (multi-dispositivo) | (subscreve eventos) | — |
| `admin` | gestão, moderação, estatísticas | (leitura transversal via APIs) | — |

**Padrão de integração-chave.** `requests` publica `RequestPublished`; `matching` (dentro de `providers`/serviço de matching) e `notifications` reagem de forma **assíncrona** (`@ApplicationModuleListener`, transacional e por defeito assíncrono no Spring Modulith). Isto mantém a publicação do pedido rápida e desacoplada, e torna o efeito "notificar prestadores elegíveis" resiliente (com *event publication registry* para reentrega em falha).

### 6.4 Consistência transacional

- Escrita e publicação de evento na **mesma transação** local; entrega do evento após *commit*.
- Para o efeito assíncrono, usar o **Event Publication Registry** do Spring Modulith (tabela de eventos publicados) para garantir *at-least-once* e reentrega após falha/reinício — evita perder notificações de matching.
- Handlers assíncronos devem ser **idempotentes**.

---

## 7. Decisões Arquiteturais (ADRs)

### ADR-01 — Modular Monolith em vez de microserviços
**Contexto:** produto novo, equipa pequena, volume incerto. **Decisão:** Spring Modulith, monolito modular deployável como unidade única. **Consequências:** menor custo operacional e cognitivo, transações locais simples, *refactoring* de fronteiras barato; em troca, escala de organização/deploy limitada — mitigada pela disciplina de módulos e eventos que permite extração futura. **Alternativa rejeitada:** microserviços desde o início (complexidade de rede, transações distribuídas e observabilidade não justificada pelo volume).

### ADR-02 — Identidade delegada a Keycloak (OAuth2/OIDC)
**Contexto:** a spec original misturava JWT próprio + Spring Security + BCrypt **e** Keycloak. **Decisão:** **Keycloak é o único IdP**; o backend é *OAuth2 Resource Server* e não gere passwords, hashing, emissão/rotação de tokens nem *brute-force*. **Consequências:** menos superfície de segurança no código próprio, MFA/verificação de email/políticas de password prontas, SSO futuro trivial; em troca, uma dependência de infraestrutura a operar (HA, backups do realm). **Alternativa rejeitada:** JWT gerido no backend (mais código sensível de auth a manter e auditar). Detalhe em §8.

### ADR-03 — Versão do stack backend (Spring Boot 3.5 vs 4.x)
**Contexto:** em julho/2026 coexistem **Spring Boot 3.5.x** (linha madura, ex. 3.5.13) e **Spring Boot 4.1** GA (Spring Framework 7, baseline Java 17+), com **Spring Modulith 2.1** alinhado ao ecossistema mais recente. **Decisão (fechada):** a baseline do projeto é **Spring Boot 3.5.x + Spring Modulith 1.4.x + Java 21 LTS**, fixada no `pom.xml` e não alterável sem ADR substituto. **Racional:** um MVP não deve absorver risco de *early adoption* no framework base; a atualização para 4.x é incremental e fica planeada, com critérios de saída explícitos (compatibilidade verificada dependência a dependência, MVP estabilizado, suíte de integração a cobrir auth/geo/pagamentos, ou aproximação do fim de suporte da linha 3.5.x). **Alternativa rejeitada:** arrancar em Boot 4.1 + Modulith 2.1 — defensável para quem privilegie longevidade sobre previsibilidade de prazo, mas troca risco conhecido por risco de ecossistema durante a construção do produto. Detalhe e critérios em `docs/adr/0003-versao-stack-backend.md`. *(Confirmar a matriz de compatibilidade nas fontes em §21 antes de fixar as versões exatas.)*

### ADR-04 — Geolocalização e matching com PostGIS
**Decisão:** modelar zonas de atuação com **PostGIS** (`geography(Point)` + raio, ou regiões administrativas), matching por `ST_DWithin` com índice GiST. **Racional:** matching por proximidade é requisito central; PostGIS é a ferramenta correta e evita reinventar cálculo geoespacial. **Não é overengineering** — é a base natural sobre PostgreSQL. Detalhe em §10.

### ADR-05 — Pesquisa: PostgreSQL FTS primeiro
**Decisão:** *full-text search* nativo do PostgreSQL (`tsvector` + índice GIN, `pg_trgm` para *fuzzy*) no MVP; OpenSearch/Elasticsearch só quando os requisitos de relevância/escala o exigirem. **Racional:** evita operar um segundo *datastore* prematuramente.

### ADR-06 — Redis condicional
**Decisão:** Redis **opcional em single-instance**, **obrigatório em multi-instance** para *rate limiting* distribuído (Bucket4j), cache e relay de WebSocket. **Racional:** não pagar custo operacional antes de escalar horizontalmente.

### ADR-07 — Estratégia de pagamentos multi-gateway
**Decisão:** abstrair o gateway atrás de uma *port* de domínio (`PaymentGateway`) com implementações plugáveis; **Stripe Billing** como referência para subscrição recorrente com cartão, **Eupago/IfthenPay** para métodos locais (MB WAY, Multibanco). **Racional e risco:** a recorrência automática é trivial com cartão (Stripe); com Multibanco (referências one-shot) a renovação é *invoice-based* (nova referência por ciclo). Ver §12.

---

## 8. Segurança

### 8.1 Modelo de identidade e autenticação

A segurança assenta em **Keycloak + OAuth2 + OpenID Connect**. Responsabilidades:

- **Keycloak (IdP):** registo, login, verificação de email, recuperação e políticas de password, **proteção contra força bruta**, MFA opcional, emissão de *access token* (JWT) e *refresh token*, rotação de tokens, gestão de sessões, mapeamento de *roles*.
- **Backend (Resource Server):** valida o *access token* (assinatura via **JWKS** do Keycloak, `iss`, `aud`, `exp`), extrai *authorities* das *roles* e autoriza os endpoints. **Não** guarda credenciais nem emite tokens.
- **SPA (public client):** **Authorization Code Flow + PKCE** (nunca *implicit*). Não recebe *client secret*.

### 8.2 Fluxo (SPA)

```
1. SPA redireciona para Keycloak (authorization endpoint) com PKCE challenge.
2. Utilizador autentica-se no Keycloak (login/registo/MFA).
3. Keycloak devolve authorization code → SPA troca por tokens (com PKCE verifier).
4. SPA chama a API com Authorization: Bearer <access_token>.
5. Backend valida o JWT contra o JWKS do Keycloak e autoriza por role.
6. Refresh via refresh token com rotação (silent renew).
```

### 8.3 Armazenamento de tokens no browser — decisão de risco

**Problema:** `localStorage`/`sessionStorage` expõem tokens a XSS. **Opções:**

| Opção | Segurança | Complexidade | Nota |
|---|---|---|---|
| Tokens em memória + refresh rotation (SPA) | Média-alta | Baixa | Aceitável para MVP; perde sessão em *reload* (mitigado por *silent renew*) |
| **BFF (Backend-for-Frontend)** com cookie `HttpOnly`+`SameSite` | Alta | Média | Tokens nunca chegam ao JS; recomendado para dados sensíveis/pagamentos |
| Tokens em `localStorage` | Baixa | Baixa | **Rejeitado** |

**Recomendação:** dado que a plataforma lida com PII e pagamentos, **adotar o padrão BFF** (o backend guarda os tokens server-side e expõe ao SPA uma sessão via cookie `HttpOnly`, `Secure`, `SameSite=Lax/Strict`), com CSRF token para operações de escrita. Se o BFF for considerado excessivo para o MVP, **tokens em memória + PKCE + refresh rotation** é o mínimo aceitável; `localStorage` está fora de questão.

### 8.3.1 Clientes nativos (mobile) — fluxo e armazenamento

Em apps nativas o modelo é **diferente e mais favorável** que no browser: segue-se a **RFC 8252 (OAuth 2.0 for Native Apps)**.

- **Fluxo:** Authorization Code **+ PKCE** através do *system browser* (ASWebAuthenticationSession no iOS, Custom Tabs no Android) via **AppAuth** (`flutter_appauth`). **Nunca** um *webview* embebido (permite interceção de credenciais e é rejeitado pelas lojas/IdPs).
- **Armazenamento de tokens:** *refresh token* no **armazenamento seguro do SO** — **Keychain** (iOS) / **Keystore**/`EncryptedSharedPreferences` (Android) via `flutter_secure_storage`; *access tokens* de curta duração, com **refresh token rotation**.
- **Redirect URI:** preferir **App Links / Universal Links** (https reivindicado pela app) a *custom scheme*, para evitar sequestro do *redirect*.
- **Reforços opcionais** (dado o contexto de PII/pagamentos): *biometric unlock* para reabrir sessão, *certificate pinning*, deteção de *root/jailbreak*, e revogação de *refresh token* no logout.

Ao contrário do browser, um cliente nativo **pode** guardar tokens com segurança (armazenamento isolado por app + cifra do SO), pelo que o padrão BFF **não** é necessário no mobile. Detalhe e alternativas em **ADR-0009**.

### 8.4 Autorização

- *Roles* de realm/cliente Keycloak: `CUSTOMER`, `PROVIDER`, `ADMIN`, mapeadas para `GrantedAuthority`.
- Autorização a dois níveis: **por role** (endpoint) e **por ownership** (o recurso pertence ao *principal*) — ex.: um cliente só vê/edita os seus pedidos; um prestador só as suas propostas. Verificação de *ownership* no serviço, nunca só no frontend.
- **Gating de subscrição** aplicado no backend como regra de domínio (não confiar no cliente): prestador sem subscrição ativa é excluído de matching, pesquisa, envio de propostas e chat novo.

### 8.5 Ligação identidade ↔ domínio

O `sub` (subject) do token Keycloak é a **chave estável** que liga a identidade ao registo `users` de domínio. No primeiro login válido, o backend faz *just-in-time provisioning* (cria o `User` local a partir das *claims*), emitindo `UserProvisioned`. PII mínima duplicada; a *source of truth* de credenciais é o Keycloak.

### 8.6 Controlos transversais

- **Rate limiting** (Bucket4j) por IP e por utilizador em endpoints sensíveis (login-adjacent, criação de pedidos/propostas, upload); distribuído via Redis em multi-instância.
- **Validação de uploads:** tipo MIME real (magic bytes, não extensão), tamanho máximo, contagem máxima, *re-encoding*/*stripping* de metadados (EXIF), *scan* antivírus opcional, nomes gerados server-side, servir por URLs assinadas com expiração. Nunca servir uploads a partir do domínio da app sem `Content-Disposition`/CSP adequados.
- **Cabeçalhos de segurança:** HSTS, CSP restritiva, `X-Content-Type-Options`, `Referrer-Policy`.
- **Auditoria:** log estruturado e imutável de ações sensíveis (login-adjacent, alterações de subscrição, moderação, acessos admin) com *actor*, *action*, *target*, *timestamp*, *correlation id*.
- **RGPD:** §18.
- **Segredos:** fora do repositório (variáveis de ambiente/secret manager); rotação periódica.

---

## 9. Modelo de Dados

### 9.1 Entidades principais

`User`, `Role`, `Address` (adiada — ver nota abaixo), `CustomerProfile`, `ProviderProfile`, `Company`, `Category`, `ProviderCategory`, `ProviderServiceArea`, `ServiceRequest`, `RequestImage`, `Proposal`, `Conversation`, `Message`, `Booking`, `Review`, `SubscriptionPlan`, `Subscription`, `Payment`, `Notification`, `DeviceToken`, `AuditLog`.

> **`Address` não está no schema v1.** A capacidade "gestão de moradas" (§4.1) mantém-se no roteiro, mas a tabela de moradas reutilizáveis **só é criada quando existir um endpoint que a leia ou escreva**. No contrato v1.0.0 a morada de um pedido é **embebida** em `ServiceRequest` (`address_text` + `location`), não referenciada por `id`: nada aponta para uma tabela de moradas. Criá-la agora não compra nada — é uma tabela nova, sem dados a migrar, pelo que adicioná-la mais tarde custa exatamente o mesmo. Não confundir com `DeviceToken`, que é criada em v1 porque o contrato **já** expõe `/v1/device-tokens`.

### 9.2 Campos-chave e relações (resumo)

**User** — `id (UUID)`, `keycloak_sub (único)`, `email`, `display_name`, `status`, `created_at`. 1–1 com `CustomerProfile` e/ou `ProviderProfile`; 1–N `Address` **quando esta entrar** (§9.1).

**ProviderProfile** — `id`, `user_id`, `company_id?`, `headline`, `bio`, `verified`, `approval_status`, `visibility_state` (derivado da subscrição), `rating_avg`, `rating_count`. N–M com `Category` (via `ProviderCategory`); 1–N `ProviderServiceArea`.

**ProviderServiceArea** — `id`, `provider_id`, `mode` (`RADIUS` | `ADMIN_REGION`), `center geography(Point,4326)?`, `radius_m?`, `region_code?`. Índice **GiST** sobre `center`.

**Category** — `id`, `parent_id?` (hierarquia), `slug`, `name`, `active`. FTS sobre `name`.

**ServiceRequest** — `id`, `customer_id`, `category_id`, `title`, `description`, `location geography(Point,4326)`, `address_text`, `urgency`, `availability`, `status`, `created_at`, `published_at?`. Índice GiST sobre `location`; GIN (`tsvector`) sobre `title+description`; índice sobre `(status, category_id)`.

**RequestImage** — `id`, `request_id`, `object_key`, `content_type`, `size_bytes`, `position`.

**Proposal** — `id`, `request_id`, `provider_id`, `price_cents`, `currency`, `description`, `lead_time_days`, `valid_until`, `status`, `created_at`. **Único** `(request_id, provider_id)` para propostas em estado não-terminal.

**Conversation** — `id`, `request_id`, `customer_id`, `provider_id`, `created_at`. **Message** — `id`, `conversation_id`, `sender_id`, `body`, `attachments[]`, `sent_at`, `read_at?`.

**Booking** — `id`, `proposal_id`, `scheduled_start`, `scheduled_end?`, `status`, `completed_at?`.

**Review** — `id`, `booking_id`, `author_id`, `target_id`, `rating (1..5)`, `comment`, `created_at`. **Único** `(booking_id, author_id)`, **não** `booking_id`: a avaliação é bilateral (§4.2, §4.6), logo a mesma marcação admite **duas** avaliações — uma por participante — mas nenhum autor avalia a mesma marcação duas vezes. É este duplicado por autor que o contrato rejeita com `409`. Constraints: `author_id <> target_id` e só se `Booking.status = COMPLETED`.

**SubscriptionPlan** — `id`, `code`, `name`, `price_cents`, `interval` (`MONTHLY`), `max_categories?`, `max_areas?`, `ranking_boost`, `has_badge`, `active`. (Limites como dados, não código.)

**Subscription** — `id`, `provider_id`, `plan_id`, `status`, `current_period_start`, `current_period_end`, `cancel_at_period_end`, `gateway_customer_id`, `gateway_subscription_id?`. Índice sobre `(status, current_period_end)` para *jobs* de expiração.

**Payment** — `id`, `subscription_id?`, `provider_id`, `amount_cents`, `currency`, `gateway`, `gateway_payment_id`, `status`, `created_at`, `raw_event_id` (idempotência).

**DeviceToken** — `id`, `user_id`, `token`, `platform` (`IOS` | `ANDROID` | `WEB`), `app_version`, `last_seen_at`, `created_at`. Suporta **múltiplos dispositivos por utilizador** para push (FCM/APNs); removido/invalidado no *logout*. Único sobre `(token)`; índice sobre `user_id`.

**AuditLog** — `id`, `actor_id`, `action`, `target_type`, `target_id`, `metadata (jsonb)`, `correlation_id`, `created_at`.

### 9.3 Notas de modelação

- **UUID** como PK (evita enumeração e facilita geração distribuída); `created_at`/`updated_at` em todas as entidades.
- **Dinheiro** em inteiros de menor unidade (`*_cents`) + `currency`; nunca `float`.
- **Enums de estado** persistidos como texto/`varchar` com *check constraint*, não *ordinal*.
- **Multi-tenant lógico** por *ownership* (não há tenants; é B2C), mas todas as *queries* de recurso filtram por dono.
- **PII** concentrada em poucas tabelas para facilitar apagamento RGPD.

### 9.4 Índices críticos

- GiST: `service_request.location`, `provider_service_area.center`.
- GIN (`tsvector`): `service_request` e `category` (FTS); `pg_trgm` para *fuzzy*.
- B-tree: `subscription (status, current_period_end)`, `proposal (request_id, provider_id)` único parcial, `user (keycloak_sub)` único, `payment (gateway, gateway_payment_id)` único.

---

## 10. Matching e Geolocalização

### 10.1 Geocoding

Moradas → coordenadas via **Nominatim (OpenStreetMap)**. **Risco operacional:** a instância pública do Nominatim impõe **1 req/s** e proíbe uso pesado; para produção é necessário **auto-hospedar** o Nominatim ou usar um fornecedor de geocoding com SLA. Geocoding deve ser **assíncrono e com cache** (a mesma morada não é geocodificada duas vezes) e **fora do caminho crítico** da publicação do pedido.

### 10.2 Modelo de cobertura do prestador

Dois modos, combináveis:

1. **Raio** — prestador define base (`center`) + `radius_m`. Match: `ST_DWithin(area.center, request.location, area.radius_m)`.
2. **Regiões administrativas** — prestador seleciona concelhos/distritos (`region_code`); o pedido é mapeado à região da sua localização. Match: `request.region_code IN (áreas do prestador)`.

**Recomendação:** suportar ambos; **raio** dá melhor experiência (proximidade real) e **regiões** são mais intuitivas de configurar e independentes de geocoding preciso. No MVP, começar por **regiões administrativas** (determinístico, sem dependência forte de geocoding) e ativar **raio** assim que o geocoding self-hosted estiver estável.

### 10.3 Algoritmo de matching (assíncrono)

Ao receber `RequestPublished`:

```sql
-- pseudo-SQL do predicado central
SELECT p.id
FROM provider_profile p
JOIN subscription s ON s.provider_id = p.id AND s.status = 'ACTIVE'
JOIN provider_category pc ON pc.provider_id = p.id AND pc.category_id = :category
JOIN provider_service_area a ON a.provider_id = p.id
WHERE p.approval_status = 'APPROVED'
  AND p.visibility_state = 'VISIBLE'
  AND (
        (a.mode = 'RADIUS' AND ST_DWithin(a.center, :requestPoint, a.radius_m))
     OR (a.mode = 'ADMIN_REGION' AND a.region_code = :requestRegion)
  );
```

Ordenação para notificação/recomendação: `ranking_boost` do plano (Premium primeiro) → `rating_avg` → proximidade → aleatoriedade controlada (evita favorecer sempre os mesmos). Resultado alimenta `notifications` e a listagem de recomendação.

---

## 11. Contratos de API (essenciais)

### 11.1 Convenções

- REST/JSON, versionado por prefixo `/{api}/v1`.
- Autenticação `Authorization: Bearer <JWT>` (ou cookie de sessão via BFF).
- **Paginação** por cursor (`?limit=&cursor=`) nas listagens de grande cardinalidade; *page-based* aceitável para admin.
- **Erros** no formato **RFC 9457 (Problem Details)**: `type`, `title`, `status`, `detail`, `instance`, `errors[]`.
- Idempotência via header `Idempotency-Key` em `POST` sensíveis.

### 11.2 Endpoints de referência (não exaustivo)

| Método | Rota | Role | Descrição |
|---|---|---|---|
| POST | `/v1/requests` | CUSTOMER | Criar pedido (DRAFT) |
| POST | `/v1/requests/{id}/publish` | CUSTOMER | Publicar (dispara matching) |
| GET | `/v1/requests/{id}` | owner/ADMIN | Detalhe |
| GET | `/v1/providers/me/requests` | PROVIDER (ativo) | Pedidos elegíveis recebidos |
| POST | `/v1/requests/{id}/proposals` | PROVIDER (ativo) | Enviar proposta |
| POST | `/v1/proposals/{id}/accept` | CUSTOMER | Aceitar proposta → CONFIRMED |
| GET | `/v1/search/providers` | público/CUSTOMER | Pesquisa filtrada (só ativos) |
| POST | `/v1/conversations/{id}/messages` | participante | Enviar mensagem |
| POST | `/v1/subscriptions` | PROVIDER | Subscrever plano |
| POST | `/v1/webhooks/payments/{gateway}` | (assinatura) | Webhook de pagamento |
| POST | `/v1/bookings/{id}/complete` | participante | Concluir → habilita review |
| POST | `/v1/reviews` | participante | Avaliar (Booking COMPLETED) |
| POST | `/v1/uploads` | autenticado | Emitir URL pré-assinado + `imageId` |

**Uploads.** O ficheiro **nunca atravessa o backend**: `POST /v1/uploads` devolve um URL pré-assinado de utilização única e o `imageId` a referenciar depois em `imageIds`/`attachmentIds`. O `contentType` e o `contentLength` declarados fazem parte da assinatura — divergir invalida o `PUT`. A chave de armazenamento é gerada pelo servidor (o `fileName` do cliente é informativo, para impedir *path traversal*), e a verificação por *magic bytes* (§8.6) ocorre quando o `imageId` é associado a um recurso, o que dispensa um endpoint `/complete`. Um `imageId` nunca referenciado expira e é recolhido. Contrato completo em `docs/api/openapi.yaml` (`createUpload`).

### 11.3 Real-time (chat)

WebSocket **STOMP** sobre SockJS em `/ws`, autenticado por token no *handshake*; tópicos por conversa; mensagens persistidas em `Message`; entrega push (FCM) quando o destinatário está offline. Cliente web via `@stomp/stompjs`; cliente **mobile via `stomp_dart_client`** (WebSocket nativo, sem SockJS). Em multi-instância, *relay* externo (Redis/RabbitMQ) para *fan-out* entre nós (ADR-0006).

### 11.4 Versionamento e compatibilidade (crítico para mobile)

Com apps publicadas nas lojas, os clientes **não se atualizam instantaneamente** (revisão de loja + adoção pelo utilizador). Consequências obrigatórias:

- **Versionamento de API** por prefixo (`/v1`) e **evolução tolerante**: mudanças **aditivas**, nunca remover/reinterpretar campos existentes; depreciação por fases com janela de suporte.
- **Force-update:** endpoint `GET /v1/app/version-status` (ou header em cada resposta) que, dado `platform` + `app_version`, devolve `OK` | `UPDATE_RECOMMENDED` | `UPDATE_REQUIRED`. O cliente aplica *soft prompt* ou *hard gate*. Regras geridas por *feature flag*/config **sem** novo deploy do cliente.
- **Deep links / notificações:** App Links/Universal Links que abrem o ecrã correto (pedido, proposta, conversa) a partir de um push.
- **Contrato único:** especificação **OpenAPI** partilhada; geração de cliente Dart (ex.: `openapi-generator`/`retrofit`) e testes de contrato para impedir divergência entre web e mobile.

---

## 12. Subscrições e Pagamentos

### 12.1 Ciclo de vida da subscrição

```
(criação/checkout) ──▶ PENDING ──pagamento ok──▶ ACTIVE ──fim do período sem renovar──▶ EXPIRED
                          │                         │                                     │
                          └──pagamento falha──▶ PAST_DUE ──retries falham──▶ CANCELLED    │
                                                   │                                       │
                                                   └──pagamento ok──▶ ACTIVE               │
        ACTIVE ──cancelar──▶ (cancel_at_period_end=true) ──fim do período──▶ CANCELLED ◀──┘
```

O campo **`visibility_state`** do prestador é **derivado** de `Subscription.status`: `ACTIVE`/`PAST_DUE`(grace) → `VISIBLE`; `EXPIRED`/`CANCELLED` → `HIDDEN`. Transições disparadas por **webhooks** e por um **job de expiração** (varre `current_period_end < now` para casos sem webhook).

### 12.2 Recorrência por método de pagamento

| Método | Recorrência automática | Estratégia |
|---|---|---|
| Cartão (Stripe Billing) | Sim (nativa) | Subscrição gerida pelo gateway; webhooks conduzem o estado |
| MB WAY (Eupago/IfthenPay) | Parcial (débito recorrente onde suportado) | Confirmar suporte por fornecedor; senão, *invoice-based* |
| Multibanco (referência) | Não | *Invoice-based*: gerar nova referência por ciclo; sem pagamento até à data → `EXPIRED` |

**Recomendação:** para o MVP com verdadeiro *auto-renew*, **Stripe Billing** é o caminho de menor esforço. Para maximizar conversão em PT, oferecer **MB WAY/Multibanco** via Eupago/IfthenPay em modelo *invoice-based* (renovação assistida). Abstrair tudo atrás da *port* `PaymentGateway` (ADR-07).

### 12.3 Webhooks — requisitos

- **Verificação de assinatura** do gateway antes de processar.
- **Idempotência:** persistir `raw_event_id`; ignorar duplicados (constraint única).
- Processar como **evento de domínio** (`PaymentSucceeded`/`PaymentFailed`) → atualizar `Subscription` → atualizar `visibility_state`.
- **Reconciliação:** job periódico compara estado local vs gateway (fonte de verdade do pagamento é o gateway).

### 12.4 Antifraude e consistência

- Nunca ativar subscrição por evento não verificado do cliente; só por webhook autenticado do gateway.
- *Grace period* configurável em `PAST_DUE` antes de esconder o prestador.

---

## 13. Chat, Agenda e Notificações

- **Chat:** §11.3. Anexos (fotos/documentos) via *object store* com URLs assinadas; validação de uploads (§8.6).
- **Agenda/`Booking`:** disponibilidade do prestador, marcação a partir de proposta aceite, lembretes. Estados: `CONFIRMED → IN_PROGRESS → COMPLETED` (ou `CANCELLED`/`NO_SHOW`). `BookingCompleted` habilita avaliação.
- **Notificações:** camada única que **subscreve eventos de domínio** e decide canal por preferências do utilizador — **FCM** (push, entregue a Android e iOS/APNs e web) e **email** (Spring Mail). Envia para todos os `DeviceToken` ativos do utilizador (multi-dispositivo); fila com *retry* e *fallback* push→email; *tokens* inválidos são removidos.

---

## 14. Stack Tecnológica

> Versões indicativas confirmadas em julho/2026; validar sempre a matriz de compatibilidade (fontes em §21).

### 14.1 Frontend (Web)

React 19, Vite, TypeScript, Tailwind CSS, shadcn/ui, React Router, TanStack Query, React Hook Form, Zod, cliente HTTP (Axios ou `fetch` + wrapper), **React Leaflet** (mapas), **oidc-client-ts / keycloak-js** (OIDC), Firebase Cloud Messaging (push web).

### 14.2 Frontend (Mobile — Flutter)

App única para **Cliente + Prestador**, **iOS + Android** a partir de um código base único.

- **Base:** Flutter (canal *stable*) + Dart.
- **Estado:** **Riverpod** (recomendado pela testabilidade e simplicidade) — *alternativa:* Bloc para equipas que já o dominam.
- **Navegação:** `go_router` (deep links, guards por *role*/subscrição).
- **Rede:** `dio` (interceptors para *auth*/refresh/retry) + `retrofit`; modelos imutáveis com `freezed` + `json_serializable`.
- **Autenticação:** `flutter_appauth` (OAuth2 Auth Code + PKCE, RFC 8252) + `flutter_secure_storage` (Keychain/Keystore). Ver §8.3.1 / ADR-0009.
- **Mapas:** `flutter_map` (tiles OpenStreetMap — consistente com o web/Leaflet) — *alternativa:* `google_maps_flutter`.
- **Push:** `firebase_messaging` (FCM; APNs no iOS via FCM) + `flutter_local_notifications`.
- **Chat em tempo real:** `stomp_dart_client` (STOMP sobre WebSocket).
- **Media:** `image_picker`, `cached_network_image`.
- **i18n:** `intl` + `flutter_localizations` (pt-PT).
- **Observabilidade:** Firebase Crashlytics e/ou Sentry.
- **CI/CD e distribuição:** Fastlane/Codemagic; **TestFlight** (iOS) e **Play Console** (Android); *beta* via Firebase App Distribution.
- **Testes:** `flutter_test`, `mocktail`, `integration_test`, *golden tests*.

### 14.3 Backend

Java 21 (LTS) — *alternativa:* Java 25 (LTS mais recente). **Spring Boot 3.5.x** (ADR-03), Spring Modulith, Spring Security (**OAuth2 Resource Server**), Spring Validation, Spring Data JPA, PostgreSQL + **PostGIS**, Flyway, Redis (condicional, ADR-06), Bucket4j (*rate limiting*), Resilience4j (resiliência), MapStruct, Lombok, Maven, Micrometer/OpenTelemetry (observabilidade). **Sem** JWT próprio/BCrypt (delegado a Keycloak).

### 14.4 Identidade

**Keycloak** (26.x) como IdP — OAuth2/OIDC, roles, MFA, *brute-force protection*.

### 14.5 Base de dados e armazenamento

PostgreSQL (+PostGIS). *Object store*: Amazon S3 / Cloudflare R2 (produção), **MinIO** (desenvolvimento) — API compatível com S3.

### 14.6 Pesquisa, mapas, comunicações

FTS PostgreSQL → OpenSearch/Elasticsearch (futuro). OpenStreetMap + **Nominatim** (geocoding, self-hosted em produção) + Leaflet. Spring Mail (email). FCM (push).

### 14.7 Pagamentos

*Port* `PaymentGateway` com adaptadores: **Stripe** (recorrência cartão), **Eupago/IfthenPay** (MB WAY/Multibanco), **PayPal** (opcional).

### 14.8 Deploy e infraestrutura

Web: Vercel. Mobile: **App Store** (iOS) e **Google Play** (Android), com pipeline Fastlane/Codemagic e canais de *beta* (TestFlight / Firebase App Distribution). Backend: Railway / Render / DigitalOcean / AWS. Containers: Docker + Docker Compose (dev). Keycloak, PostgreSQL, Redis, MinIO no `docker-compose` de desenvolvimento.

---

## 15. Estrutura de Código

### 15.1 Frontend (feature-based)

```
src/
├── app/            # bootstrap, providers, config
├── assets/
├── components/     # UI partilhada (shadcn/ui)
├── features/
│   ├── authentication/   # integração OIDC
│   ├── customers/
│   ├── providers/
│   ├── requests/
│   ├── proposals/
│   ├── subscriptions/
│   ├── chat/
│   ├── notifications/
│   ├── reviews/
│   └── admin/
├── hooks/          # hooks partilhados
├── layouts/
├── lib/            # clientes (api, oidc, query)
├── pages/          # composição de rotas
├── routes/         # definição/guards de rota
├── services/       # chamadas à API por feature
├── store/          # estado global (mínimo; preferir TanStack Query)
├── types/
└── utils/
```

### 15.2 Backend (por módulo)

```
com.servimatch
├── common
├── auth          # adapter Keycloak ↔ domínio
├── users
├── customers
├── providers
├── categories
├── requests
├── proposals
├── subscriptions
├── payments
├── chat
├── schedule
├── reviews
├── notifications
└── admin
```

Cada módulo mantém estrutura uniforme: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `validator`, `config`, `events` (eventos publicados/escutados). Tipos de integração inter-módulo ficam **públicos**; o resto **package-private** para que o Spring Modulith verifique as fronteiras.

### 15.3 Mobile (Flutter, feature-first)

```
lib/
├── main.dart
├── app/            # bootstrap, router (go_router), tema, DI (Riverpod)
├── core/
│   ├── auth/       # AppAuth + secure storage (RFC 8252)
│   ├── network/    # dio, interceptors, retrofit, tratamento de erros
│   ├── config/     # ambientes, feature flags, version-status/force-update
│   └── push/       # firebase_messaging, deep links
├── shared/         # widgets, extensões, i18n
├── features/
│   ├── authentication/
│   ├── customer/         # fluxos do perfil Cliente
│   ├── provider/         # fluxos do perfil Prestador
│   ├── requests/
│   ├── proposals/
│   ├── subscriptions/
│   ├── chat/             # stomp_dart_client
│   ├── reviews/
│   └── notifications/
└── l10n/           # pt-PT
```

A UI adapta-se ao *role* (Cliente vs Prestador) após login; a lógica de acesso a *features* de prestador respeita o **gating de subscrição** devolvido pelo backend (nunca decidido só no cliente).

---

## 16. Observabilidade e Operação

- **Logs** JSON com `correlation_id` propagado HTTP → evento → webhook.
- **Métricas** Micrometer → Prometheus; SLOs por §5.
- **Tracing** OpenTelemetry (HTTP + async).
- **Actuator** para health/readiness/liveness (integração com o orquestrador).
- **Migrações** Flyway em *startup* controlado (ou passo de CI/CD dedicado).
- **Backups**: PostgreSQL (PITR), realm do Keycloak, *object store* versionado.
- **Ambientes**: dev (Docker Compose) → staging → produção, com *secrets* geridos externamente.

---

## 17. Estratégia de Testes

| Nível | Foco | Ferramentas |
|---|---|---|
| Unitário | domínio, máquinas de estado, guardas, cálculos (preço, matching) | JUnit 5, AssertJ, Mockito |
| Módulo/fronteiras | verificação de fronteiras e documentação de módulos | **Spring Modulith** `ApplicationModules.verify()` + *docs* |
| Integração | JPA/PostGIS, FTS, repositórios, webhooks | **Testcontainers** (Postgres+PostGIS, Keycloak, Redis, MinIO) |
| Segurança | autorização por role e por *ownership*, gating de subscrição | Spring Security Test, MockMvc/WebTestClient |
| Contrato | API pública, webhooks de pagamento | testes de contrato / OpenAPI |
| E2E (crítico) | fluxo pedido→proposta→aceitação→review | Playwright (frontend) |

Prioridade: cobrir **transições de estado** e o **predicado de matching/gating** (regras de negócio de maior risco). Testes de integração usam Testcontainers para paridade com produção (PostGIS/Keycloak reais, não *mocks*).

---

## 18. RGPD e Compliance

- **Separação de responsabilidades:** credenciais e *source of truth* de identidade no **Keycloak**; dados de domínio na app, ligados por `keycloak_sub`.
- **Base legal por finalidade** e **minimização** (só o necessário).
- **Direitos do titular:** acesso, retificação, **apagamento** (com estratégia para dados ligados a histórico/faturação — anonimização em vez de *hard delete* onde a lei de retenção fiscal o exigir), **portabilidade** (export).
- **Retenção** definida por tipo de dado (ex.: pagamentos por obrigação fiscal; chat por período limitado).
- **Cifra** em repouso (DB/object store) e em trânsito (TLS).
- **Registo de tratamento** e **auditoria** de acessos a PII.
- **Consentimento** para notificações de marketing separado do funcional.

---

## 19. Âmbito do MVP e Faseamento

### 19.1 MVP (v1)

Registo/autenticação (Keycloak), perfis de cliente e prestador, catálogo de categorias, pedido com localização e fotografias, **matching por categoria + zona (regiões administrativas)**, propostas, chat, avaliações verificadas, **um plano de subscrição**, pagamento do plano (Stripe como referência), painel administrativo essencial, notificações por email. Push (FCM) pode entrar logo a seguir.

### 19.2 Cortes conscientes no MVP

- Matching por **raio/PostGIS** e geocoding self-hosted → v1.1 (começar por regiões).
- Múltiplos planos e *ranking boost* → dados já suportados, ativação comercial depois.
- OpenSearch, agenda avançada, MFA obrigatório → pós-MVP.
- **App móvel Flutter → *fast-follow* imediato após o MVP web**: não bloqueia o lançamento web e reutiliza o backend/API já validado. O contrato de API (versionamento + force-update, §11.4) e a entidade `DeviceToken` devem, porém, ser preparados **já no MVP** para não exigir migração quando o mobile arrancar. Ver ADR-0008.

### 19.3 Faseamento sugerido

1. **Fundações:** Keycloak + Resource Server, `users`, migrações, CI/CD, observabilidade base.
2. **Núcleo do marketplace:** categorias, pedidos, matching por região, propostas.
3. **Confiança e comunicação:** chat, avaliações, notificações.
4. **Monetização:** subscrições + pagamentos + gating.
5. **Admin e endurecimento:** moderação, auditoria, *rate limiting* distribuído, hardening de segurança.
6. **App móvel (fast-follow):** app Flutter (Cliente + Prestador, iOS + Android) sobre a API estabilizada — autenticação nativa (RFC 8252), push nativo, deep links, *force-update*, publicação nas lojas.

---

## 20. Evoluções Futuras

**Pagamentos cliente↔prestador com escrow**, videochamada, IA para recomendação de profissionais, pesquisa inteligente (OpenSearch + *ranking* aprendido), agenda integrada e Google Calendar, assinatura eletrónica de orçamentos, programa de fidelização, dashboard analítico para prestadores, campanhas promocionais. A par: extração seletiva de módulos (ex.: `notifications`, `chat`) para serviços próprios se o volume o justificar (ADR-0001). *(A app móvel Flutter deixou de ser evolução futura — passou a âmbito na v1.1, §14.2 / ADR-0008.)*

---

## 21. Riscos e Mitigações

| Risco | Impacto | Mitigação |
|---|---|---|
| Arranque *two-sided* (poucos prestadores → poucos clientes) | Alto | GTM focado na oferta; período promocional de subscrição; *seeding* por região/categoria |
| Recorrência de pagamento limitada em métodos PT (Multibanco) | Médio | Stripe (cartão) para auto-renew; *invoice-based* para métodos locais; comunicação clara de renovação |
| Limites/ToS do Nominatim público | Médio | Self-host do Nominatim ou fornecedor com SLA; cache de geocoding |
| Operação do Keycloak (HA, backups) | Médio | HA + backups do realm; testes de restauro; monitorização |
| XSS → roubo de token no SPA | Alto | Padrão BFF (cookie HttpOnly) ou tokens em memória; CSP; nunca `localStorage` |
| Fugas entre fronteiras de módulo | Médio | `ApplicationModules.verify()` em CI; revisão de dependências |
| Consistência de eventos (notificações perdidas) | Médio | Event Publication Registry (Modulith) + handlers idempotentes |
| Custo/complexidade prematura (microserviços, OpenSearch, Redis) | Médio | Adiar por ADRs 0001/0005/0006 até haver pressão real |
| Clientes móveis não atualizáveis à força (revisão de loja + adoção lenta) | Médio-alto | API versionada e **tolerante** + mecanismo de **force-update** (§11.4); nunca quebrar contrato existente |
| Divergência de comportamento entre web e mobile | Médio | Contrato **OpenAPI** único + testes de contrato; geração de cliente Dart |
| Roubo de token / dispositivo comprometido (mobile) | Médio | Secure storage (Keychain/Keystore), RFC 8252, refresh rotation, opções de biometria/pinning (ADR-0009) |
| Tempo/rejeição de revisão nas lojas (App Store/Play) | Médio | Canais de *beta* (TestFlight/App Distribution), *release* faseado, *feature flags* server-side |

---

## 22. Referências

- Spring Boot — releases e ciclo de vida: https://spring.io/projects/spring-boot ; endoflife.date: https://endoflife.date/spring-boot ; anúncio 3.5.13: https://spring.io/blog/2026/03/26/spring-boot-3-5-13-available-now/
- Spring Modulith — 2.1 GA: https://spring.io/blog/2026/06/11/spring-modulith-2-1-ga-2-0-7-and-1-4-12-released/ ; docs: https://docs.spring.io/spring-modulith/reference/
- Spring Security — OAuth2 Resource Server: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
- Keycloak — releases: https://www.keycloak.org/2026/07/keycloak-2670-released ; docs: https://www.keycloak.org/documentation
- OAuth 2.0 (RFC 6749): https://www.rfc-editor.org/rfc/rfc6749 ; PKCE (RFC 7636): https://www.rfc-editor.org/rfc/rfc7636 ; OIDC Core: https://openid.net/specs/openid-connect-core-1_0.html
- Problem Details (RFC 9457): https://www.rfc-editor.org/rfc/rfc9457
- PostGIS — `ST_DWithin`: https://postgis.net/docs/ST_DWithin.html ; PostgreSQL FTS: https://www.postgresql.org/docs/current/textsearch.html
- Nominatim — política de uso: https://operations.osmfoundation.org/policies/nominatim/
- Stripe Billing (subscrições): https://docs.stripe.com/billing/subscriptions/overview ; Eupago: https://www.eupago.pt ; IfthenPay: https://www.ifthenpay.com
- Bucket4j: https://bucket4j.com ; Resilience4j: https://resilience4j.readme.io ; Testcontainers: https://testcontainers.com
- **Mobile:** Flutter: https://docs.flutter.dev ; OAuth 2.0 for Native Apps (RFC 8252): https://www.rfc-editor.org/rfc/rfc8252 ; flutter_appauth: https://pub.dev/packages/flutter_appauth ; flutter_secure_storage: https://pub.dev/packages/flutter_secure_storage ; flutter_map: https://pub.dev/packages/flutter_map ; firebase_messaging: https://pub.dev/packages/firebase_messaging ; stomp_dart_client: https://pub.dev/packages/stomp_dart_client ; Riverpod: https://riverpod.dev ; go_router: https://pub.dev/packages/go_router

---

*Documento de arquitetura de referência do ServiMatch. As decisões marcadas ADR são revisitáveis à medida que surgem dados de utilização; o desenho privilegia simplicidade e modularidade, mantendo um caminho de evolução incremental sem reescrita.*

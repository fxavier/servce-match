# ADR-0012: Autenticação por credenciais *first-party* sobre o Keycloak

- **Estado:** Aceite
- **Data:** 2026-07-29
- **Decisores:** Dono do produto (requisito), `arquiteto` (forma e limites)
- **Relacionado:** ADR-0002 (Keycloak como IdP), ADR-0009 (clientes nativos),
  ADR-0011 (elegibilidade na leitura), ADR-0013 (seed dev-only)
- **Substituição:** substitui **parcialmente** o ADR-0002 e o ADR-0009, e
  **apenas** na *recolha de credenciais no cliente web*. Tudo o resto desses dois
  ADR permanece em vigor: Keycloak é o IdP único, o backend é só Resource Server,
  a validação é por JWKS, a ligação ao domínio é pelo `sub`, e o **mobile
  mantém-se integralmente na RFC 8252**.

## Contexto e Problema

O dono do produto decidiu que **o utilizador nunca vê o Keycloak**. Registo e
login acontecem em formulários da própria SPA, sem redireccionamento, sem mudança
de domínio e sem página de terceiro.

O que existe hoje contradiz isso de forma direta. `web/bff/src/routes/auth.ts`
implementa `GET /auth/login` como um *redirect* para o *authorization endpoint*
do Keycloak (Authorization Code + PKCE, `state`, `nonce`, cookie de trânsito
assinado), e `GET /auth/callback` troca o código por tokens que ficam numa
`SessionStore` server-side. O utilizador vê, obrigatoriamente, a página de login
do Keycloak. **Registo não existe de todo na SPA**: `ARQUITETURA.md` §7 diz
"registo/login (via Keycloak)" e o realm tem `registrationAllowed: true`, ou
seja, o registo é a página de auto-registo do Keycloak.

O requisito é de produto e de marca, não é técnico. Mas colide com três coisas ao
mesmo tempo, e a decisão só é honesta se as nomear:

1. **O ADR-0002**, que fixou Authorization Code + PKCE para a SPA.
2. **A RFC 9700 §2.4**, que diz, sem qualificação, que o *resource owner password
   credentials grant* **MUST NOT be used**.
3. **O estado do realm** (`infra/keycloak/realm-servimatch.json`): o client
   `servimatch-bff` é confidencial mas tem `directAccessGrantsEnabled: false` e
   `serviceAccountsEnabled: false`. Ambos teriam de passar a `true`. Isso não é
   um detalhe de configuração — é a decisão, escrita noutro sítio.

O que **não** está em causa: quem valida a identidade. Continua a ser o Keycloak.
O que muda é **quem recolhe as credenciais** e as apresenta ao IdP.

## Fatores de Decisão

- **Requisito de produto declarado não negociável** (controlo do funil de registo,
  marca, ausência de salto de domínio).
- **Os invariantes do `CLAUDE.md` §4 não se flexibilizam**: nenhum token chega ao
  browser; o backend não ganha uma linha de código de credenciais.
- **Conformidade**: RFC 9700 §2.4 e OAuth 2.1 (`draft-ietf-oauth-v2-1-15`, §1.8).
- **O que se perde funcionalmente** — e se o que se perde é reversível ou é uma
  parede.
- **Onde passa a estar o risco**: com a mudança, o BFF passa a ser o alvo que a
  página de login do Keycloak era.
- **Reversibilidade**: quanto custa voltar atrás quando um dos gatilhos disparar.

## Opções Consideradas

1. **Manter Authorization Code + PKCE e personalizar o Keycloak** — tema próprio
   (FreeMarker ou `keycloakify`) servido em `auth.servimatch.pt`, com a marca
   ServiMatch, incluindo a página de registo.
2. **Formulários na SPA + BFF confidencial a falar com o Keycloak
   *server-to-server*** — Admin REST API para o registo, Direct Access Grant para
   o login.
3. **Formulários na SPA que fazem `POST` direto ao *token endpoint*** do Keycloak
   a partir do browser.
4. **Abandonar o Keycloak** e implementar autenticação própria no backend.

## Decisão

Adota-se a **opção 2**, com o âmbito exato abaixo. Cada ponto é vinculativo.
A recomendação técnica do `arquiteto` — e as condições que forçam a reabertura
deste ADR — estão no *Racional*, e não são decorativas.

### D1 — Superfície: três rotas no BFF, e nenhuma no contrato do backend

- `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`.
- `GET /auth/login` e `GET /auth/callback` **deixam de ser caminho de
  utilizador**. Dois caminhos de login vivos é superfície a mais e é a forma
  garantida de um deles nunca ser testado.
- **Estas rotas não entram em `docs/api/openapi.yaml`.** Arbitragem de fronteira,
  explícita: `openapi.yaml` é o contrato do *resource server*, consumido por web
  **e** mobile (`CLAUDE.md` §2). Publicar `/auth/login` nesse contrato seria
  convidar o `mobile-flutter` a implementá-lo, o que violaria o ADR-0009 e a RFC
  8252. A superfície `/auth/**` é documentada pelo `web-frontend` em
  `web/bff/README.md`, onde o `api-contract` não escreve e o mobile não vai
  buscar contrato.
- O `verifyCsrf` global já montado em `web/bff/src/app.ts` (*double submit*,
  cookie legível por JS + header `x-csrf-token`) aplica-se a estas rotas por
  serem `POST`, e **não pode ser contornado para elas**. É o que impede
  *login-CSRF* — forçar a vítima a ficar autenticada na conta do atacante.

### D2 — Registo: Admin REST API com o *service account* do client confidencial

- O BFF obtém um token com `grant_type=client_credentials` do client
  `servimatch-bff` e chama `POST /admin/realms/servimatch/users`
  ([Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)).
- `UserRepresentation`: `username` = email (o realm tem
  `registrationEmailAsUsername: true`), `enabled: true`, credencial de tipo
  `password` com `temporary: false`.
- Privilégios do *service account*: `manage-users` (criar) e `view-users` (ler),
  do client `realm-management`. **`realm-admin` está proibido.** O conjunto
  mínimo exato para *também* atribuir a *role* de domínio tem de ser **verificado
  contra o realm** pelo `platform-infra` durante a implementação, não assumido —
  se a atribuição de *role* obrigar a privilégios de gestão de *roles*,
  **recomenda-se** atribuí-la por **grupo** (`groups` na criação) ou por *default
  role* do realm, para que o *service account* não precise de mais nada além de
  `manage-users`.
- **A *role* pedida pelo browser é filtrada por *allowlist* no servidor:
  `{CUSTOMER, PROVIDER}`.** Reencaminhar o campo tal como chega é escalada de
  privilégio direta — `ADMIN` existe no realm e é atribuível pela mesma chamada.
  Isto é um invariante, não uma validação de conveniência.
- Login imediato a seguir (D3), na mesma resposta. Sujeito a D5.

### D3 — Login: Direct Access Grant *server-to-server*, resposta nunca vista pelo browser

- `POST /realms/servimatch/protocol/openid-connect/token` com
  `grant_type=password`, autenticação do client confidencial por `client_secret`,
  `scope=openid profile email`.
- **A resposta do Keycloak não atravessa o BFF.** *Access*, *refresh* e *id
  token* vão para a `SessionStore` existente; ao browser vai apenas o
  `Set-Cookie` de sessão (`HttpOnly`, `Secure`, `SameSite`) e um corpo sem
  qualquer token — exatamente o que `/auth/callback` já faz hoje. O proxy
  `/api/**` continua a injetar o `Authorization: Bearer` a partir da sessão.
- **A password vive num único pedido**: lida do corpo, enviada ao Keycloak,
  descartada. Nunca em log, métrica, sessão, cookie, corpo de erro, nem
  reencaminhada ao backend.
- `POST /auth/logout` deixa de devolver `logoutUrl`. Devolve `204`. Continua a
  destruir a sessão do BFF e a revogar o *refresh token* (`tokenRevocation`), que
  é o que efetivamente termina o acesso. **O que se perde é o fim de sessão SSO
  no Keycloak** — a `KEYCLOAK_IDENTITY` fica viva no browser até expirar por
  `ssoSessionIdleTimeout` (1800s). Como já não há navegação para o Keycloak, essa
  sessão não é reutilizável por este cliente; passa a ser um cookie residual, não
  uma sessão ativa. Consequência aceite conscientemente, não esquecida.

### D4 — O backend não muda uma linha

Continua **apenas** Resource Server. Critérios verificáveis em revisão:
`backend/pom.xml` não ganha `keycloak-admin-client` nem equivalente; nenhum
endpoint `/v1/**` recebe password; `UsersApi.ensureProvisioned` continua a ser o
**único** escritor de `users` em produção. O invariante do `CLAUDE.md` §4
mantém-se intacto porque o que mudou foi a recolha, não a validação.

### D5 — O que deixa de ser executável: incompatibilidade, não lacuna

O *token endpoint* com `grant_type=password` responde `invalid_grant` — opaco,
sem indicação legível por máquina de qual é o problema — sempre que a conta tenha
uma *required action* pendente ou exija passos adicionais. Ficam **fora de
alcance** enquanto este ADR vigorar:

- **Required actions**: `VERIFY_EMAIL`, `UPDATE_PASSWORD`, `UPDATE_PROFILE`,
  `TERMS_AND_CONDITIONS`.
- **MFA/OTP.** O fluxo `direct grant` do Keycloak é composto por execuções e
  admite variantes, mas qualquer uma delas transfere a recolha do código de
  segundo fator **para a nossa SPA** — deixa de ser um problema do IdP e passa a
  ser código nosso, com o custo de auditoria que o ADR-0002 tinha eliminado.
- **Identity brokering e login social** (Google, Apple). Não têm representação
  possível num `grant_type=password`. É uma parede, não uma limitação.

**Cada um destes requisitos obriga a substituir este ADR**, não a estendê-lo.

**Consequência imediata e bloqueante, verificada no realm atual:** o realm tem
`"verifyEmail": true`. Um utilizador criado por D2 com `emailVerified: false`
recebe a *required action* `VERIFY_EMAIL` e o login imediato de D2 **falha** com
`invalid_grant`. Não há forma de contornar isto dentro do fluxo. As saídas são
três, e só uma é aceitável:

- **Rejeitada:** criar com `emailVerified: true`. É afirmar num *claim* algo que
  não aconteceu; qualquer *relying party* que confie em `email_verified` passa a
  confiar numa mentira nossa.
- **Rejeitada:** manter `verifyEmail: true` e aceitar que não há login após
  registo. Contradiz o requisito que motiva este ADR.
- **Decidida:** `verifyEmail: false` no realm, e **a verificação de email deixa
  de ser uma *required action* de autenticação e passa a ser uma regra de
  domínio** — o utilizador entra, mas o servidor recusa publicar pedido ou
  proposta antes de verificado, pela mesma lógica do `CLAUDE.md` §4 (*gating* é
  regra de domínio no servidor). Trabalho por dono: coluna e estado
  (`db-migrations`), *gate* e envio (`backend-domain`), endpoints
  (`api-contract`), realm (`platform-infra`). Se o desenho crescer, é ADR
  próprio.
- **Enquanto isso não existir, o produto fica sem verificação de email nenhuma.**
  Isto tem de ser aceite por escrito ou o registo não abre. Não é um detalhe a
  resolver depois: `email_verified` no token passa a ser sempre falso e **nenhum
  predicado o pode ler**.

**Acoplamento novo que antes não existia:** uma opção mudada no *admin console*
do Keycloak — ativar uma *required action* por omissão, ligar `verifyEmail`,
exigir MFA numa política — **parte o login de toda a gente sem alterar uma linha
de código**. Mitigação obrigatória: teste de integração contra o realm real
(Testcontainers Keycloak, já previsto em `ARQUITETURA.md` §16) que exerça registo
+ login imediato, e que falhe se a configuração do realm regredir.

### D6 — Configuração do realm que passa a ser parte da decisão

- `directAccessGrantsEnabled: true` e `serviceAccountsEnabled: true` **apenas**
  em `servimatch-bff`. Nunca em `servimatch-web` nem em `servimatch-mobile`, que
  são clients públicos: um Direct Access Grant sem autenticação de client é o
  cenário que a RFC 9700 descreve sem nenhuma atenuante.
- **`registrationAllowed: false`.** Com o registo a passar pelo BFF, a página de
  auto-registo do Keycloak é uma segunda porta, exposta à internet, sem o nosso
  *rate limiting*, sem a *allowlist* de *roles* de D2. Um utilizador que se
  registe por lá entra sem `CUSTOMER` nem `PROVIDER` e recebe `403` em todos os
  endpoints `/v1/**` — falha silenciosa e difícil de diagnosticar.
- `bruteForceProtected: true` mantém-se: continua a proteger **por utilizador**,
  que é a parte que não depende do IP. Ver D7.

### D7 — Brute force, enumeração e *timing* passam a ser responsabilidade do BFF

O Keycloak passa a ver **um único IP: o do BFF**. Consequências concretas:

- A proteção por utilizador (`failureFactor: 5`, `maxFailureWaitSeconds: 900`)
  continua a funcionar. A capacidade de distinguir **origens** desaparece.
- O log de eventos do Keycloak passa a registar o mesmo IP em todos os eventos de
  login. **Um incidente de *credential stuffing* deixa de ser investigável a
  partir do Keycloak** — o único sítio onde existe o IP real passa a ser o BFF.
  Por isso o registo de auditoria de ações *login-adjacent* (`ARQUITETURA.md`
  §8.6) passa a ser emitido pelo BFF, com `correlation_id` e **nunca com email**
  (`CLAUDE.md` §4).

Mitigações **obrigatórias**, todas no BFF, todas antes de contactar o Keycloak:

1. **Rate limiting por IP real** em `/auth/login` e `/auth/register`, com balde
   próprio e mais apertado do que qualquer limite global.
2. **`X-Forwarded-For` só é honrado a partir de proxies explicitamente
   confiáveis.** Este defeito já foi encontrado e corrigido do lado do backend: o
   `RateLimitFilter` aceitava o cabeçalho de qualquer origem, o que tornava o
   limite contornável rodando o valor, e ganhou
   `servimatch.rate-limit.trusted-proxies` (lista de CIDR, **vazia por
   omissão**). O BFF é Express e tem exatamente o mesmo problema:
   `app.set('trust proxy', true)` é **proibido**; a lista tem de ser explícita. O
   Keycloak documenta a mesma classe de risco para si próprio: *"If these headers
   are incorrectly configured, rogue clients can inject false values and trick
   Keycloak into thinking the client is connecting from a different IP address
   than the actual one."*
3. **Resposta não enumerável.** Mesmo código, mesmo corpo `application/problem+json`
   e mesmo `type` para "email não existe" e para "password errada". E, no registo,
   **mesma resposta para email novo e para email já registado** — caso contrário
   o oráculo apenas mudou de porta. O aviso ao titular vai por email, não na
   resposta HTTP.
4. **Piso de latência.** O caminho "utilizador não existe" devolve muito mais
   depressa do que "password errada", que passa pela função de derivação de chave
   do Keycloak. Mesma mensagem com tempos diferentes continua a ser um oráculo.
   Mitigação: normalizar para um piso fixo. É imperfeita — mede-se em ruído de
   rede — e diz-se que é imperfeita.

### D8 — O client secret muda de categoria de risco

Antes deste ADR, quem tivesse o secret do `servimatch-bff` precisava **também**
de intercetar um *authorization code* para obter alguma coisa. Depois deste ADR,
quem tiver o secret pode, a partir de qualquer sítio da internet:

- **autenticar-se como qualquer utilizador cuja password conheça** (o Direct
  Access Grant não exige interação, nem *redirect URI*, nem PKCE); e
- **usar o *service account* para criar utilizadores** — e, consoante os
  privilégios efetivos, alterá-los.

Regras que daí decorrem:

- O secret **não entra no repositório**. O valor em
  `infra/keycloak/realm-servimatch.json`
  (`dev-only-bff-secret-never-reused-elsewhere`) é dev-only e tem de continuar a
  sê-lo — o nome já o diz e passa a ser levado à letra.
- Rotação definida e ensaiada, não "quando houver incidente".
- Em produção, só em gestor de segredos.
- **Recomendação: separar em dois clients confidenciais** — um para o login
  (`servimatch-bff`, Direct Access Grant, sem *service account*) e outro para a
  administração (`servimatch-bff-admin`, *service account* com `manage-users`,
  sem *direct grant*). O custo é mais um client e mais um secret a operar. O
  ganho é que uma fuga do secret de login **não** dá poder de criar utilizadores,
  e vice-versa. Sem esta separação, um único segredo concentra os dois poderes.

### D9 — Divergência Keycloak ↔ `users`: assimetria deliberada, sem transação distribuída

O registo cria o utilizador no **Keycloak**. A linha em `users` **não é criada
pelo BFF** — aparece no primeiro pedido autenticado ao backend, por
`UsersApi.ensureProvisioned`, que já existe e já é idempotente
(`INSERT ... ON CONFLICT (keycloak_sub) DO NOTHING`, com releitura pelo perdedor
da corrida).

**Decisão: o BFF não escreve na base de dados de domínio, nem passa a ter acesso
a ela.** O Keycloak é a fonte de verdade da identidade; a linha `users` é
derivada e reconstituível a partir do `sub`.

Modos de falha, todos avaliados:

| Cenário | Resultado | Compensação |
|---|---|---|
| Criação no Keycloak OK, login imediato falha | Utilizador existe no IdP, sem linha local; entra mais tarde sem problema | Nenhuma |
| Criação OK, utilizador nunca volta | Conta órfã no Keycloak, sem linha local | Nenhuma — é ruído, não corrupção |
| Linha `users` sem utilizador no Keycloak | **Impossível** — o único escritor é o JIT, a partir de um token válido | N/A |

É esta assimetria — o lado derivado nunca pode existir sozinho — que dispensa
*saga* e 2PC. Não se acrescenta nenhum dos dois.

**O que se perde, e é real:** deixa de haver um evento de domínio no instante do
registo. O primeiro é o `UserProvisioned` do JIT, que pode chegar dias depois ou
nunca. **Quem quiser a métrica "número de registos" tem de a ir buscar aos
eventos do Keycloak, não à tabela `users`** — e uma contagem sobre `users` vai
sistematicamente abaixo da realidade sem indicar erro nenhum.

**Achado registado, não resolvido aqui:** o JIT só escreve no `INSERT`. Um email
alterado no Keycloak não propaga para `users.email`, que fica desatualizado em
silêncio. Já era verdade antes deste ADR; passa a ser mais visível agora que o
registo é nosso.

### D10 — Âmbito da substituição, e o mobile

- **Substituído:** a recolha de credenciais no **cliente web**.
- **Não substituído:** Keycloak como IdP único; backend como Resource Server
  puro; validação por JWKS (`iss`/`aud`/`exp`); *roles* `CUSTOMER`/`PROVIDER`/
  `ADMIN`; ligação ao domínio por `sub` com provisionamento JIT; proibição de
  tokens em `localStorage`/`sessionStorage`.
- **O mobile mantém-se integralmente no ADR-0009**: RFC 8252, *system browser*,
  PKCE, *secure storage*. `servimatch-mobile` continua público e com
  `directAccessGrantsEnabled: false`. **Não é negociável**: um cliente público
  não pode guardar um secret, e um Direct Access Grant sem autenticação de client
  não tem nenhuma das atenuantes que justificam o caso restrito abaixo.
- **Custo assumido:** os dois clientes passam a ter experiências de login
  diferentes — o web com formulário próprio, o mobile com o browser do sistema a
  mostrar o Keycloak. A divergência é deliberada e é o preço de não estender o
  ROPC a um cliente onde ele é indefensável.

## Racional

### Porque é que o ROPC é tolerável *aqui*, e a fronteira exata dessa tolerância

A RFC 9700 §2.4 é categórica: o *resource owner password credentials grant*
**"MUST NOT be used"**. E fundamenta-o com três razões, que vale a pena confrontar
uma a uma com este caso, porque é a confrontação — e não a citação — que decide:

1. *"insecurely exposes the credentials of the resource owner to the client"* e
   aumenta a superfície de ataque, *"credentials can leak in more places than
   just the authorization server"*. **Aplica-se.** Passa a haver um segundo sítio
   onde a password existe em claro em memória: o BFF. Mitigado por D3 (vida de um
   pedido, nunca persistida, nunca reencaminhada), não eliminado.
2. *"training users to enter their credentials in places other than the
   authorization server"*. **Não se aplica com a mesma força.** O cliente é
   *first-party*, do mesmo produto, no mesmo domínio de confiança que o IdP, e o
   utilizador nunca conheceu outro sítio onde entrar as credenciais deste
   produto. O risco que a frase descreve é o de habituar o utilizador a dar a
   password a terceiros; aqui não há terceiro.
3. *"is not designed to work with two-factor authentication and authentication
   processes that require multiple user interaction steps"*. **Aplica-se
   inteiramente**, e é exatamente o conteúdo do D5. Não é atenuado por nada — é
   aceite como perda.

O caso restrito em que a tolerância se sustenta é, portanto, este e só este:
**cliente *first-party*, confidencial (autenticado por secret no *token
endpoint*), no mesmo domínio de confiança que o IdP, com as credenciais a
existirem apenas na duração de um pedido e nunca reencaminhadas para outro
componente.** Retire-se qualquer um dos quatro e a decisão cai. É por isso que o
mobile (público) está expressamente fora (D10) e que os clients públicos ficam
com `directAccessGrantsEnabled: false` (D6).

O OAuth 2.1 (`draft-ietf-oauth-v2-1-15`, 2 de março de 2026, §1.8) confirma a
direção: *"some features available in OAuth 2.0, such as the Implicit or Resource
Owner Credentials grant types, are not specified in OAuth 2.1."* Esta decisão
escolhe deliberadamente um mecanismo que a norma seguinte já não descreve. Isso é
dívida com data de validade, e está declarado como tal.

### Recomendação técnica do `arquiteto`, e os gatilhos que a tornam obrigatória

**A opção 1 é tecnicamente superior e é a que eu recomendaria se o requisito de
produto fosse renegociável.** Um tema Keycloak em `auth.servimatch.pt` entrega a
marca e o domínio próprio — o argumento do "domínio de terceiro" não sobrevive ao
exame —, custa um tema (FreeMarker ou `keycloakify`) e **preserva tudo o que o D5
destrói**: MFA, *required actions*, verificação de email, *identity brokering*,
recuperação de password, *brute force* com o IP verdadeiro, e conformidade com a
RFC 9700. O que não entrega é o fluxo numa só página: há um *redirect* de ida e
volta, o botão "voltar" comporta-se de forma diferente do resto da SPA e a
validação do formulário não é a mesma. É uma decisão de funil, e o dono do
produto tomou-a com essa informação.

**Este ADR é substituído — não estendido — assim que qualquer um destes se
verificar:**

- MFA passa a ser requisito para qualquer *role*;
- verificação de email obrigatória volta a ser *required action* do IdP;
- entra login social ou federação com qualquer identidade externa;
- é preciso apresentar termos e condições com aceitação registada no IdP;
- passa a haver um cliente que não seja *first-party*.

### Porque não a opção 3 (browser fala direto com o *token endpoint*)

Junta o pior dos dois mundos: os tokens chegam ao JavaScript, o que viola
frontalmente o `CLAUDE.md` §4; obriga a um client público, portanto sem
autenticação de client, que é o cenário sem atenuantes da RFC 9700; e expõe o
*token endpoint* à internet com o IP do utilizador mas **sem** nenhum dos nossos
controlos de D7. Rejeitada sem hesitação.

### Porque não a opção 4 (autenticação própria no backend)

É literalmente a alternativa que o ADR-0002 rejeitou, com os mesmos argumentos:
reintroduz *hashing* de passwords, emissão e rotação de tokens, e proteção de
força bruta no nosso código, para escrever, testar e auditar. O requisito de
produto é sobre o **formulário**, não sobre o IdP; satisfazê-lo deitando fora o
IdP é responder à pergunta errada.

## Consequências

**Positivas**

- O requisito de produto é cumprido: registo e login sem sair da SPA.
- Nenhum token chega ao browser — o `CLAUDE.md` §4 fica **mais** apertado, não
  menos, porque D3 proíbe explicitamente token no corpo da resposta de login.
- O backend não muda: continua auditável como Resource Server puro (D4).
- O provisionamento JIT já existente absorve o registo sem uma linha nova de
  coordenação, e a assimetria de D9 torna a corrupção estruturalmente impossível.
- O registo passa a ter *rate limiting*, *allowlist* de *roles* e auditoria
  nossos — que a página de auto-registo do Keycloak não tinha.

**Negativas / Custos**

- **MFA, *required actions* e login social ficam inacessíveis** (D5). Não é uma
  funcionalidade adiada; é uma porta fechada até este ADR ser substituído.
- **`verifyEmail: false` deixa o produto sem verificação de email** até a regra
  de domínio existir. Numa plataforma com PII e pagamentos, isto é um risco
  aberto com dono e prazo, não um detalhe.
- **Uma opção do *admin console* do Keycloak passa a poder partir o login de
  todos os utilizadores** sem alterar código, e o sintoma é um `invalid_grant`
  opaco. Só um teste de integração contra o realm real o apanha.
- **A recuperação de password não tem solução dentro deste ADR.** Com a página de
  login do Keycloak fora do caminho do utilizador, o único mecanismo disponível é
  `execute-actions-email` com `UPDATE_PASSWORD` a partir do BFF — e o link desse
  email abre **uma página do Keycloak**, que é precisamente o que o requisito
  proíbe. **Exceção explícita, aceite.** Duas verificações obrigatórias antes de
  a implementar: (a) a pesquisa do utilizador por email é outro oráculo de
  enumeração e tem de responder sempre igual; (b) **é preciso confirmar em
  ambiente real se a ação fica pendente no utilizador** — se ficar, qualquer
  pessoa que conheça um email tranca o login da vítima com um pedido, porque a
  *required action* pendente faz o Direct Access Grant devolver `invalid_grant`.
- **O secret do BFF concentra dois poderes** (D8): personificação e criação de
  utilizadores. Sem a separação em dois clients, uma única fuga dá ambos.
- **O IP real deixa de existir no Keycloak** (D7): a investigação forense de
  *credential stuffing* passa a depender inteiramente dos logs do BFF, que hoje
  não os produz.
- **Respostas não enumeráveis custam UX**: o utilizador que se engana no email
  não é avisado, e quem se tenta registar com um email já existente recebe a
  mesma resposta de sucesso. É a única forma de não ter oráculo, e é pior de usar.
- **Web e mobile divergem na experiência de login** (D10) — dois fluxos, dois
  conjuntos de testes, duas histórias de suporte.
- **Perde-se o fim de sessão SSO** no *logout* (D3): cookie residual no Keycloak
  até expirar.
- **A métrica de registos deixa de ser obtível da tabela `users`** (D9), e a
  contagem errada não dá erro.
- **Dívida com data**: OAuth 2.1 não especifica este *grant*. Cada ano que passa
  torna o caminho de volta mais caro.

## Alternativas rejeitadas

- **Authorization Code + PKCE com tema Keycloak (opção 1):** rejeitada **por
  requisito de produto**, não por mérito técnico — é a opção tecnicamente
  superior e volta a ser a decisão assim que qualquer gatilho do *Racional*
  disparar.
- **`POST` direto do browser ao *token endpoint* (opção 3):** rejeitada — tokens
  no JavaScript, client público, sem os controlos do BFF.
- **Autenticação própria no backend (opção 4):** rejeitada — é o que o ADR-0002
  já rejeitou, e responde à pergunta errada.
- **`emailVerified: true` no registo:** rejeitada — afirmar um facto falso num
  *claim* em que terceiros confiam.
- **Direct Access Grant no cliente móvel:** rejeitada — cliente público, sem
  secret, sem nenhuma das quatro condições que sustentam a tolerância.
- **`app.set('trust proxy', true)` no Express:** rejeitada — é a versão Express do
  defeito que o `RateLimitFilter` acabou de corrigir; torna o *rate limiting* de
  D7 contornável com um cabeçalho.
- **Reencaminhar o campo `role` do registo tal como chega:** rejeitada — escalada
  a `ADMIN` com uma linha de JSON.
- **Criar a linha `users` a partir do BFF:** rejeitada — daria acesso à base de
  dados de domínio a um componente que não é dono de nenhum módulo, e destruiria
  a assimetria de D9 que dispensa transação distribuída.

## Ligações

- **RFC 9700 §2.4** — *OAuth 2.0 Security Best Current Practice*, Resource Owner
  Password Credentials Grant ("MUST NOT be used"):
  https://www.rfc-editor.org/rfc/rfc9700.html
- **OAuth 2.1**, `draft-ietf-oauth-v2-1-15` (2026-03-02), §1.8 — *"such as the
  Implicit or Resource Owner Credentials grant types, are not specified in OAuth
  2.1"*: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-1
- **RFC 6749 §4.3** — definição original do *grant* usado:
  https://www.rfc-editor.org/rfc/rfc6749#section-4.3
- **RFC 8252** — OAuth 2.0 for Native Apps (aplicável ao mobile, inalterado):
  https://www.rfc-editor.org/rfc/rfc8252
- **Keycloak Admin REST API** — `POST /admin/realms/{realm}/users`:
  https://www.keycloak.org/docs-api/latest/rest-api/index.html
- **Keycloak — Using a reverse proxy** (`proxy-headers`,
  `proxy-trusted-addresses`, aviso sobre injeção de `X-Forwarded-For`):
  https://www.keycloak.org/server/reverseproxy
- **Keycloak — Server Administration Guide** (proteção contra força bruta,
  *required actions*, fluxos de autenticação):
  https://www.keycloak.org/docs/latest/server_admin/index.html
- **OWASP — Authentication Cheat Sheet** (respostas genéricas e *timing* para não
  permitir enumeração de utilizadores):
  https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- **Express — `trust proxy`**: https://expressjs.com/en/guide/behind-proxies.html
- `ARQUITETURA.md` §8.1–§8.5 (responsabilidades Keycloak/backend, fluxo do web,
  ligação `sub` ↔ `users`), §8.6 (auditoria de ações *login-adjacent*), §16
  (Testcontainers com Keycloak real).
- `web/bff/src/routes/auth.ts`, `web/bff/src/app.ts` (CSRF *double submit*),
  `web/bff/src/session.ts`, `infra/keycloak/realm-servimatch.json`.

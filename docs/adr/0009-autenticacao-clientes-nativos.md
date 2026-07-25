# ADR-0009: Autenticação de clientes nativos (RFC 8252 / AppAuth + PKCE + secure storage)

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0002 (Keycloak), ADR-0008 (app Flutter)

## Contexto e Problema

A app móvel autentica contra o **mesmo Keycloak** que o web (ADR-0002), mas o modelo de ameaça e as opções de armazenamento **diferem** entre um browser e uma app nativa. É preciso fixar o fluxo de autenticação e o armazenamento de tokens no mobile, sem reintroduzir os riscos que levaram a rejeitar `localStorage` no web.

## Fatores de Decisão

- Segurança do fluxo de autorização em ambiente nativo.
- Proteção do *refresh token* em repouso no dispositivo.
- Conformidade com as melhores práticas de IdPs e lojas.
- Experiência do utilizador (persistência de sessão, biometria).

## Opções Consideradas

1. **RFC 8252 (AppAuth): Auth Code + PKCE via *system browser* + *secure storage* do SO.**
2. **Webview embebido** com captura do fluxo.
3. **Reutilizar o padrão BFF do web** também no mobile.

## Decisão

Seguir a **RFC 8252 (OAuth 2.0 for Native Apps)**:

- **Fluxo:** Authorization Code **+ PKCE** através do *system browser* — ASWebAuthenticationSession (iOS) / Custom Tabs (Android) — via **AppAuth** (`flutter_appauth`). **Proibido** *webview* embebido.
- **Armazenamento:** *refresh token* em **Keychain** (iOS) / **Keystore** / `EncryptedSharedPreferences` (Android) via `flutter_secure_storage`; *access token* de curta duração, preferencialmente só em memória; **refresh token rotation** ativa.
- **Redirect URI:** **App Links / Universal Links** (https reivindicado) em vez de *custom scheme*, para evitar sequestro do *redirect*.
- **Logout:** revogação do *refresh token* no Keycloak e limpeza do *secure storage*.
- **Reforços (recomendados para PII/pagamentos):** *biometric unlock* para reabrir sessão, *certificate pinning* nas chamadas à API, deteção de *root/jailbreak*.

O backend permanece **OAuth2 Resource Server** e valida o JWT de forma idêntica independentemente do cliente (web ou mobile) — a diferença é apenas na obtenção/guarda do token.

## Racional

Ao contrário do browser (onde qualquer XSS lê `localStorage`, o que motivou o BFF no web — ADR-0002), uma app nativa tem **armazenamento isolado por aplicação e cifrado pelo SO**, pelo que guardar o *refresh token* em Keychain/Keystore é seguro e é a prática recomendada pela RFC 8252. O *system browser* evita que a app veja as credenciais do utilizador e permite SSO com outras apps.

## Consequências

**Positivas**
- Alinhado com a norma e com os requisitos de IdPs/lojas.
- *Refresh token* protegido em repouso; sessão persistente sem expor tokens ao código da app.
- Sem necessidade de BFF no mobile (menos infraestrutura para o caso móvel).

**Negativas / Custos**
- Configuração de App Links/Universal Links por plataforma (associação de domínio).
- Complexidade adicional de biometria/pinning quando ativados.
- Gestão de *edge cases* (relógio, expiração, revogação, troca de dispositivo).

## Alternativas rejeitadas

- **Webview embebido:** permite interceção de credenciais, quebra SSO e é desencorajado pela RFC 8252 e por IdPs.
- **BFF no mobile:** desnecessário — o dispositivo já oferece armazenamento seguro; adicionaria latência e estado de sessão server-side sem benefício.

## Ligações

- OAuth 2.0 for Native Apps (RFC 8252): https://www.rfc-editor.org/rfc/rfc8252
- PKCE (RFC 7636): https://www.rfc-editor.org/rfc/rfc7636
- AppAuth: https://appauth.io ; flutter_appauth: https://pub.dev/packages/flutter_appauth
- flutter_secure_storage: https://pub.dev/packages/flutter_secure_storage
- Keycloak: https://www.keycloak.org/documentation

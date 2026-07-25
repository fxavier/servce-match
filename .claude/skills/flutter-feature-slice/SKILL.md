---
name: flutter-feature-slice
description: Estrutura e convenções para implementar uma funcionalidade na app Flutter do ServiMatch — organização feature-first, Riverpod, go_router, cliente de rede gerado do OpenAPI, tratamento de erros Problem Details, offline e testes. Usa ao escrever qualquer código em mobile/.
---

# Fatia de funcionalidade em Flutter

## Estrutura

```
lib/
├── main.dart
├── app/                  # bootstrap, router, tema, providers globais
├── core/
│   ├── auth/             # AppAuth (RFC 8252) + secure storage
│   ├── network/          # dio, interceptors, cliente gerado, erros
│   ├── config/           # ambientes, feature flags, version-status
│   └── push/             # firebase_messaging, deep links
├── shared/               # widgets e utilitários transversais
├── features/<feature>/
│   ├── data/             # repositórios sobre o cliente gerado
│   ├── domain/           # modelos e regras da feature (freezed)
│   └── presentation/     # ecrãs, widgets, controladores Riverpod
└── l10n/                 # pt-PT
```

Uma feature não importa de `presentation` de outra feature. Partilha faz-se por
`shared/` ou por `core/`. Sem esta regra, a app converte-se num monólito de UI ao
fim de três funcionalidades.

## Rede

O cliente é **gerado** de `docs/api/openapi.yaml` (retrofit + dio, modelos
freezed/json_serializable) para `core/network/generated/`. Não editar, não
escrever chamadas HTTP à mão.

Interceptors obrigatórios:
- **Auth** — injeta o access token; ao receber 401 tenta refresh **uma vez**,
  serializando os pedidos concorrentes (senão dez pedidos disparam dez refreshes
  e o IdP rejeita-os); se falhar, termina a sessão.
- **Correlation** — propaga `correlation_id` para os logs do servidor.
- **Erros** — converte `application/problem+json` num tipo de domínio com o
  `type` preservado. A UI ramifica pelo `type`, nunca pela mensagem.

Trata `subscription-required` como estado de produto: leva ao ecrã de subscrição,
não a um alerta de erro.

## Estado

Riverpod, com o estado assíncrono a expor explicitamente carregamento, dados,
vazio e erro. Todo o ecrã com I/O trata os quatro **mais offline** — mobile perde
rede, e assumir sempre-ligado é um defeito, não um caso extremo.

## Compatibilidade

Ao arranque, `GET /v1/app/version-status`:
- `OK` — segue.
- `UPDATE_RECOMMENDED` — aviso dispensável.
- `UPDATE_REQUIRED` — ecrã bloqueante com link para a loja.

O parse tem de tolerar campos desconhecidos: o servidor evolui de forma aditiva e
uma app instalada não pode rebentar por causa de um campo novo.

## Push e deep links

Registar `deviceToken` após login (`POST /v1/device-tokens`), remover no logout,
renovar quando o FCM rodar. Deep links via go_router, com App Links/Universal
Links coerentes com os redirect URIs de autenticação.

## Testes

`flutter_test` + `mocktail` para lógica e controladores; `integration_test` para
os fluxos críticos (login, publicar pedido, aceitar proposta); *golden tests* nos
componentes visuais já estáveis — antes de estabilizarem só produzem ruído.

## Referências

- flutter_appauth: https://pub.dev/packages/flutter_appauth
- flutter_secure_storage: https://pub.dev/packages/flutter_secure_storage
- Riverpod: https://riverpod.dev ; go_router: https://pub.dev/packages/go_router
- retrofit: https://pub.dev/packages/retrofit ; freezed: https://pub.dev/packages/freezed

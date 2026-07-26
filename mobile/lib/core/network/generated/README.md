# Cliente gerado a partir de `docs/api/openapi.yaml`

**Não editar `*.g.dart` / `*.freezed.dart` à mão.** São produzidos por
`build_runner` a partir de:

- `servimatch_api.dart` — interface `retrofit` (`@RestApi`) com um método por
  `operationId` do contrato.
- `models/*.dart` — modelos `freezed` + `json_serializable`, um por schema do
  contrato (mesmo nome, mesmos campos, mesma obrigatoriedade).

## Comando de geração

```bash
cd mobile
flutter pub get
dart run build_runner build --delete-conflicting-outputs
```

Corre isto sempre que `docs/api/openapi.yaml` mudar (o `api-contract` avisa
os consumidores) ou sempre que editares as interfaces/modelos acima.

## Nota sobre esta ronda (fast-follow, onda 1)

Não existe, neste momento, um gerador (`openapi-generator` ou equivalente)
que produza diretamente **retrofit + freezed** — a stack escolhida no
`CLAUDE.md`/skill `flutter-feature-slice` — a partir de OpenAPI 3.1. As
alternativas disponíveis (ex. gerador `dart-dio`) produzem um cliente Dio
próprio com modelos `built_value`, o que trocaria de stack a meio do
projeto. Optou-se por manter a interface `retrofit` e os modelos `freezed`
**escritos à mão para espelhar 1:1 o contrato** (nome de schema, campos,
obrigatoriedade, enums) e deixar a `build_runner` gerar toda a
implementação (serialização JSON, corpo dos métodos HTTP) — é o fluxo
normal de trabalho com `retrofit` em Dart, que **sempre** exige uma
interface anotada manualmente (não existe geração 100% automática da
interface a partir do OpenAPI para esta combinação).

Cobertura atual (suficiente para autenticar → criar pedido → ver
propostas): `getAppVersionStatus`, `registerDeviceToken`,
`deleteDeviceToken`, `listCategories`, `createRequest`, `getRequest`,
`publishRequest`, `listRequestProposals`.

Por implementar em rondas seguintes (mesma abordagem, mesmo comando): as
restantes operações do contrato (`createProposal`, `acceptProposal`,
`listProviderInbox`, `searchProviders`, chat, bookings, reviews,
subscrições). Se a equipa adotar um gerador OpenAPI dedicado no futuro,
substitui-se este diretório por inteiro — os consumidores (`features/**`)
só dependem de `models/models.dart` e `servimatch_api.dart`, nunca dos
ficheiros `.g.dart`/`.freezed.dart` diretamente.

## Aviso conhecido do gerador (`toJson()`)

`dart run build_runner build` avisa, para os `@Body()` (`RegisterDeviceToken`,
`CreateServiceRequest`): *"must provide a `toJson()` method which return a
Map"*. É uma limitação de ordenação de fases entre `retrofit_generator` e
`freezed`/`json_serializable` (o primeiro resolve `servimatch_api.dart`
antes de os `.freezed.dart` dos modelos existirem nessa passagem) — os
modelos **têm** `toJson()`, gerado corretamente. Por causa do aviso, o
código gerado passa o objeto `freezed` diretamente como `data:` em vez de
chamar `.toJson()` explicitamente; funciona à mesma porque
`BaseOptions.contentType` está fixado como `application/json`
(`dio_provider.dart`), o que faz o transformador por omissão do Dio chamar
`jsonEncode(data)` — e o `jsonEncode` do `dart:convert`, quando não sabe
codificar um objeto diretamente, invoca `data.toJson()` dinamicamente. Não
mexer nisto à mão; se um dia o aviso desaparecer (nova versão do gerador),
o comportamento não muda.

## Compatibilidade

Todos os modelos toleram campos desconhecidos (comportamento por omissão do
`json_serializable`: só lê o que conhece). Os enums que o servidor pode
alargar de forma aditiva (`RequestStatus`, `ProposalStatus`,
`VersionStatus.status`) têm um valor `unknown` de reserva via
`@JsonKey(unknownEnumValue: ...)`, para que um valor novo não rebente o
parse — a UI trata `unknown` como um estado neutro, nunca como erro fatal.

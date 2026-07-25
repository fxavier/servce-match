---
name: openapi-contract-first
description: Procedimento para alterar, validar e consumir o contrato OpenAPI 3.1 do ServiMatch, incluindo regras de compatibilidade retroativa e geração de clientes para web e Flutter. Usa sempre que tocares em docs/api/openapi.yaml ou em código gerado a partir dele.
---

# Contrato primeiro

`docs/api/openapi.yaml` é o acordo entre backend, web e mobile. Escrever código
antes de acordar o contrato produz três interpretações diferentes da mesma API.

## Compatibilidade retroativa

Uma app móvel instalada pode ficar meses sem atualizar. Assume sempre clientes
antigos em produção.

**Permitido (aditivo):** novo endpoint; novo campo **opcional** na resposta; novo
campo opcional no pedido com valor por omissão; novo valor de enum **apenas** em
campos que os clientes tratam com fallback; alargar validação (aceitar mais).

**Proibido sem versão major:** remover ou renomear campo; tornar obrigatório um
campo antes opcional; apertar validação (`maxLength` menor, novo `pattern`);
mudar tipo; mudar o significado de um valor existente; mudar código de estado ou
`type` de erro já publicado.

**Depreciação:** marca `deprecated: true`, documenta a alternativa, mede o uso
por versão de cliente e só remove quando o uso for residual — nunca por
calendário isolado.

## Convenções deste contrato

- Erros: `application/problem+json` (RFC 9457). O `type` é URI estável sob
  `https://errors.servimatch.pt/`; é por ele que os clientes ramificam.
- Dinheiro: `amountCents` (inteiro) + `currency` (ISO-4217).
- Paginação: cursor, envelope `{ items, page: { nextCursor } }`.
- Escritas não idempotentes: cabeçalho `Idempotency-Key`.
- Segurança global `oidc`; público exige `security: []` **explícito** e justificado.
- Todo o `operationId` é único, estável e em `camelCase` — é o nome do método
  gerado nos clientes; mudá-lo parte o código de todos.

## Armadilha de YAML (erro já ocorrido neste ficheiro)

Num escalar não citado, `": "` é interpretado como separador de mapeamento.
Descrições em português com `(ex.: algo)` fazem o parse rebentar com
`mapping values are not allowed in this context`. Escreve `(ex. algo)` ou cita a
string inteira.

## Validação

```bash
python3 - <<'PY'
import yaml, json, sys
spec = yaml.safe_load(open('docs/api/openapi.yaml'))
print('openapi:', spec['openapi'])
print('paths:', len(spec['paths']))
print('operations:', sum(1 for p in spec['paths'].values()
      for m in p if m in ('get','post','put','patch','delete')))
print('schemas:', len(spec['components']['schemas']))
PY
```

Compara as contagens antes e depois da alteração: uma descida inesperada
significa que apagaste algo sem reparar.

Validação de schema e diff de compatibilidade na pipeline (`platform-infra`):
usar um validador OpenAPI 3.1 e um comparador de contratos que falhe o build em
alterações *breaking*.

## Geração de clientes

- **Flutter**: cliente Dart gerado para `mobile/lib/core/network/generated/`.
- **Web**: tipos e cliente TypeScript gerados para `web/src/api/generated/`.

Código gerado **não se edita** e não se corrige à mão — se está errado, o
contrato está errado ou a configuração do gerador está errada. Adiciona os
diretórios gerados ao lint ignore e regenera na pipeline para detetar deriva
entre contrato e código commitado.

## Referências

- OpenAPI 3.1: https://spec.openapis.org/oas/v3.1.0
- RFC 9457 (Problem Details): https://www.rfc-editor.org/rfc/rfc9457
- OpenAPI Generator: https://openapi-generator.tech/docs/generators

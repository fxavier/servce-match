---
name: adr-madr
description: Como escrever e manter Architecture Decision Records no formato MADR usado no ServiMatch, incluindo quando uma decisão merece ADR, o modelo a seguir e as regras de imutabilidade e substituição. Usa antes de registar qualquer decisão arquitetural em docs/adr/.
---

# ADR no formato MADR

## Quando escrever um ADR

Escreve quando a decisão é **cara de reverter** ou **atravessa fronteiras de
equipa/módulo**: escolha de stack, fronteira de módulo, modelo de identidade,
estratégia de dados, integração externa, política de compatibilidade.

Não escrevas ADR para escolhas locais e reversíveis (nome de classe, layout de
pacote dentro de um módulo, biblioteca utilitária sem impacto externo). ADRs a
mais diluem os que importam.

Se estás a implementar algo que **contradiz** um ADR existente: pára. Não
implementes primeiro e documentes depois — escreve o ADR novo e escala ao
`arquiteto`.

## Modelo

```markdown
# ADR-XXXX: <título na forma de decisão>

- **Estado:** Proposto | Aceite | Substituído por ADR-YYYY | Descontinuado
- **Data:** AAAA-MM-DD
- **Decisores:** <quem>
- **Relacionado:** ADR-...

## Contexto e Problema
O que nos obriga a decidir. Factos, não preferências.

## Fatores de Decisão
Critérios pelos quais as opções são avaliadas.

## Opções Consideradas
1. ...
2. ...

## Decisão
A opção escolhida, sem ambiguidade, com o âmbito exato do que fica decidido.

## Racional
Por que esta e não as outras.

## Consequências
**Positivas** — o que ganhamos.
**Negativas / Custos** — o que passamos a ter de pagar, operar ou aceitar.

## Alternativas rejeitadas
Cada uma com o motivo concreto da rejeição.

## Ligações
Fontes primárias: documentação oficial, RFC, repositórios oficiais.
```

Um ADR sem consequências negativas explícitas está incompleto: toda a decisão
tem custo, e o valor do registo é sobretudo para quem, daqui a um ano, vai
perguntar "porque é que isto está assim?".

## Regras

- **Imutabilidade.** Um ADR aceite não se reescreve. Muda-se de ideias criando um
  ADR novo e marcando o anterior como `Substituído por ADR-YYYY`. Apagar história
  de decisões destrói o valor do registo.
- **Numeração sequencial** de quatro dígitos, ficheiro
  `NNNN-titulo-em-kebab-case.md`.
- **Atualiza sempre `docs/adr/README.md`** ao acrescentar um ADR.
- **Coerência**: `docs/ARQUITETURA.md` e `docs/api/openapi.yaml` não podem
  contradizer um ADR aceite. Divergência é defeito.
- Factos voláteis (versões, preços, limites de serviço) levam link para a fonte
  primária e data de verificação.

## Registo atual

ADR-0001 a ADR-0009 estão aceites e **sem decisões em aberto** (monólito modular,
Keycloak, versão do stack, PostGIS, FTS, Redis condicional, pagamentos
multi-gateway, app Flutter, autenticação nativa).

ADR-0003 fixa a baseline em Spring Boot 3.5.x + Spring Modulith 1.4.x + Java 21
LTS, com critérios explícitos para a migração futura a Boot 4.x. Migrar não é
uma edição a este ADR: é um ADR novo que o substitui.

## Referências

- MADR: https://adr.github.io/madr/
- ADR (visão geral): https://adr.github.io/

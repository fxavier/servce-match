---
name: estado-com-escritor
description: Procedimento para garantir que toda a coluna lida por um predicado de decisão tem escritor em produção e teste da transição (ADR-0011 D9, CLAUDE.md §5). Usa quando implementares ou revires elegibilidade, gating, aprovação, visibilidade, estado de subscrição, ou qualquer coluna que decida o que um utilizador vê. Usa também antes de fabricares estado por INSERT/UPDATE direto num teste.
---

# Coluna de decisão exige escritor de produção

## O defeito que esta skill existe para impedir

Uma coluna é lida por um `WHERE` que decide o que o utilizador vê. Nenhum código
de produção a escreve. Os testes fabricam-na por `INSERT`/`UPDATE` direto. O
build fica verde, a suite passa, e a funcionalidade está desligada em produção
desde o primeiro dia.

Aconteceu três vezes no ServiMatch: `subscription` (ADR-0011), depois
`visibility_state`, depois `approval_status` — cada uma descoberta muito depois,
sempre pelo mesmo caminho. Não é descuido: é o modo de falha natural de um
sistema onde o leitor e o escritor pertencem a agentes/ondas diferentes e o
teste consegue substituir-se ao escritor em falta.

**Falha fechada, não aberta.** O `DEFAULT` costuma ser o valor restritivo
(`PENDING`, `HIDDEN`), pelo que o sintoma não é fuga de dados — é um resultado
vazio que parece "ainda não há dados". É por isso que passa despercebido.

## Procedimento — antes de escrever a query

Quando escreveres um predicado que lê uma coluna para decidir visibilidade,
elegibilidade, acesso ou ordenação:

1. **Identifica o escritor.** `grep -rn "SET <coluna>\|INSERT INTO <tabela>" backend/src/main/java`.
   Filtra `src/test/**` e `db/seed/**` — nenhum dos dois conta.
2. **Se não houver escritor, para.** Não escrevas a query. Tens duas saídas
   legítimas e nenhuma terceira:
   - **Implementar o escritor** na mesma onda, no módulo dono do facto.
   - **Não ler a coluna.** Se o facto não tem produtor, o predicado que o lê é
     ficção. Remove a coluna do `WHERE` e do DTO — expor `verified: false`
     constante é pior que não expor o campo.
3. **Se o escritor for um listener de evento, confirma que o listener existe.**
   Não confies no javadoc, num `package-info`, nem num comentário de teste.
   `grep` pela classe. Duas violações no ServiMatch foram fixadas em verde por
   comentários a citar listeners e ADR que não diziam o que o comentário
   afirmava.
4. **Se o escritor só existir no seed**, é pior que não existir: funciona em
   desenvolvimento e falha em silêncio em produção. O seed é dev-only
   (ADR-0013); nunca conta como produtor.

## Procedimento — o teste de transição

Um teste que fabrica o estado final não prova nada sobre o caminho que o produz.
Para cada coluna de decisão, a suite tem de conter **um** teste que leve o
sistema ao estado **pelo caminho de produção**:

```
estado inicial por omissão  →  verificar que o efeito NÃO acontece
        ↓ (chamada à API pública do módulo dono — nunca SQL)
estado alvo                 →  verificar que o efeito acontece
```

O primeiro passo é o que apanha o defeito: se a coluna nunca for escrita, a
asserção "não acontece" passa em ambos os lados e o teste é decorativo. Assevera
sempre os dois lados.

Exemplo, para `approval_status`:

```java
// 1. Prestador criado pelo caminho de produção — fica PENDING
UUID providerId = criarPrestadorViaApi(...);
assertThat(pesquisar("canalizador")).doesNotContain(providerId);

// 2. Transição pelo caminho de produção — PATCH, não UPDATE
patchApproval(providerId, "APPROVED", adminToken);

// 3. Agora aparece
assertThat(pesquisar("canalizador")).contains(providerId);
```

## Quando é tolerável fabricar estado por SQL num teste

Só quando existir, **no mesmo conjunto de testes**, um teste que exercite a
transição que produz esse estado (CLAUDE.md §5, ADR-0011 D9). O `INSERT` direto
passa então a ser um atalho de *setup* para um facto já coberto — não um
substituto do produtor.

Se escreveres um `INSERT` direto, o comentário que o acompanha nomeia o teste da
transição correspondente, e esse teste tem de existir. Se não existir, o
comentário é uma mentira que sobrevive à revisão por *diff*.

## Verificação antes de fechar o ramo

```bash
# 1. Colunas lidas em predicados
grep -rn "approval_status\|visibility_state\|rating_avg" backend/src/main/java | grep -iE "where|and |order by"

# 2. Escritores em produção (tem de devolver pelo menos uma linha por coluna)
grep -rn "SET approval_status\|SET rating_avg" backend/src/main/java

# 3. Fabricação em testes (cada uma precisa do seu teste de transição)
grep -rn "SET approval_status\|SET visibility_state" backend/src/test
```

Uma coluna que apareça em (1) e não em (2) é defeito, não é "por implementar".

## Corolário para revisão de código

Ao rever um PR que acrescenta um `WHERE` sobre uma coluna de estado, a pergunta
não é "esta query está correta?" — é **"quem escreve esta coluna em produção, e
onde está o teste que exercita essa escrita?"**. Se a resposta vier de um
comentário e não de um ficheiro, verifica o ficheiro.

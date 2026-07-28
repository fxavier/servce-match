# ADR-0010: Acesso a tabelas de outro módulo — leitura permitida sob condições, escrita proibida

- **Estado:** Aceite
- **Data:** 2026-07-28
- **Decisores:** `arquiteto`
- **Relacionado:** ADR-0001 (Modular Monolith), ADR-0004 (PostGIS), ADR-0005 (FTS)

## Contexto e Problema

O ADR-0001 estabelece módulos com fronteiras verificadas e proíbe, em texto, o
acesso ao esquema de tabelas de outro módulo. A implementação da Onda 1 mostrou
duas coisas que obrigam a fechar esta regra com precisão em vez de a repetir:

1. **A regra não é verificada.** `ApplicationModules.verify()` verifica ausência
   de ciclos, acesso a pacotes `internal` e `allowedDependencies` — tudo sobre
   **tipos e pacotes Java**. Uma consulta SQL a uma tabela de outro módulo é,
   para essa verificação, invisível. A regra existia sem mecanismo.

2. **Existe hoje acesso SQL entre módulos, e a maior parte dele é justificado.**
   Estado real em `main`:

   | Módulo | Lê tabelas de | Tabelas |
   |---|---|---|
   | `matching` | `providers`, `billing`, `requests` | `provider_profile`, `provider_category`, `provider_service_area`, `subscription`, `service_request` |
   | `search` | `providers`, `billing`, `users` | `provider_profile`, `provider_category`, `company`, `subscription`, `subscription_plan`, `users` |
   | `billing` | `providers`, `users` | `provider_profile`, `users` |
   | `requests` | `categories` (por implementar), `uploads` (por implementar) | `category` (leitura), `upload_asset` (**escrita**) |

   As três primeiras linhas são consultas **de conjunto**: o predicado de
   elegibilidade (ADR-0004 §10.3) e a pesquisa (ADR-0005) resolvem
   subscrição + aprovação + categoria + cobertura geográfica numa única consulta
   indexada. Fazê-lo através de APIs Java por prestador significaria N+1 e a
   perda do `Index Cond` do GiST — exatamente o defeito de desempenho que §10.3
   documenta em detalhe. A quarta linha é de natureza diferente: são substitutos
   temporários de módulos que ainda não existem.

Não decidir mantém a pior das situações: um documento que proíbe, um código que
faz, e nenhum critério para distinguir o caso legítimo do descuido.

## Fatores de Decisão

- **Desempenho verificável**: não obrigar a soluções que destroem o uso de
  índices ou introduzem N+1 num caminho crítico.
- **Custo do acoplamento invisível**: uma dependência que o *build* não vê é uma
  dependência que se degrada sem aviso.
- **Propriedade do esquema**: todas as migrações pertencem a um único agente
  (`db-migrations`), pelo que o esquema já **não** é privado por módulo — o que
  torna a leitura entre módulos menos anárquica do que parece, mas também
  concentra o risco de uma alteração de coluna partir consumidores distantes.
- **Preservar a opção de extração futura** do ADR-0001.
- **Não flexibilizar invariantes de segurança**: o *gating* por subscrição tem de
  continuar a ser decidido no servidor (CLAUDE.md §4).

## Opções Consideradas

1. **Proibição estrita.** Cada módulo só toca nas suas tabelas; tudo o resto por
   API pública Java.
2. **Sem regra** (situação de facto): cada agente decide caso a caso.
3. **Leitura permitida sob condições enumeradas, escrita proibida.**
4. **Vistas SQL publicadas.** Cada módulo publica uma `VIEW` como superfície de
   leitura estável e os outros só consultam a vista.

## Decisão

Adota-se a **opção 3**, com este âmbito exato:

- **Leitura (`SELECT`) de tabelas de outro módulo é permitida** quando (a) a
  consulta é *set-based* e a alternativa por API pública produziria N+1 ou
  impediria o uso de um índice, **e** (b) a consulta está declarada no *javadoc*
  do repositório que a executa, nomeando as tabelas e o módulo dono, **e** (c)
  existe teste de integração com base de dados real a cobri-la. As leituras da
  tabela em §Contexto ficam autorizadas por este ADR.
- **Escrita (`INSERT`/`UPDATE`/`DELETE`) em tabela de outro módulo é proibida,
  sem exceção.** A única escrita existente — `requests` a marcar
  `upload_asset` como `CONFIRMED` — é **dívida com prazo**: retira-se quando o
  módulo `uploads` existir (Onda 1b), passando a uma chamada à `UploadsApi`. Até
  lá, a validação por *magic bytes* (CLAUDE.md §4) **não está a ser feita** neste
  caminho; é a razão principal para a dívida ter prazo e não ser normalizada.
  O mesmo se aplica à leitura de `category` por `requests`, que termina quando
  `categories` existir.
- Um módulo **não** ganha por esta via o direito de reinterpretar regras de outro:
  `matching` e `search` leem `subscription.status`, mas o significado do estado e
  as transições continuam a ser exclusivamente de `billing`.
- Esta autorização é **enumerada, não geral**: acrescentar uma leitura entre
  módulos fora da tabela acima é um pedido ao `arquiteto`, não uma decisão do
  agente que a precisa.

## Racional

A opção 1 é a mais limpa no papel e a errada aqui: o produto vive do predicado
geográfico, e obrigá-lo a passar por chamadas Java por prestador tornaria o
caminho central lento de uma forma que o ADR-0004 existe precisamente para
evitar. Trocar desempenho verificado por pureza de fronteira, num monólito que
partilha a mesma transação e a mesma base de dados, é pagar um custo real por um
benefício simbólico.

A opção 2 já demonstrou o seu custo: três agentes tomaram a mesma decisão em
privado, cada um com uma justificação diferente, e um deles remeteu explicitamente
a fundamentação para `docs/adr/` — onde não existia nada.

A opção 4 é a evolução natural **se** a dor aparecer. Não se adota agora porque
acrescenta objetos de esquema e migrações a manter, e porque uma vista sobre
`provider_profile` não elimina o acoplamento — desloca-o, ao custo de mais uma
peça. Fica registada como caminho de saída: se uma alteração de esquema partir
`matching` ou `search` mais do que uma vez, publicam-se vistas.

## Consequências

**Positivas**
- O caminho crítico mantém as propriedades de desempenho medidas em §10.3.
- A regra passa a distinguir o caso legítimo do descuido, com critérios que um
  revisor consegue aplicar.
- A dívida (`upload_asset`, `category`) fica com condição de saída explícita, em
  vez de se dissolver no código.

**Negativas / Custos**
- **Acoplamento que o compilador não vê.** Renomear uma coluna de
  `provider_profile` ou `subscription` parte `matching`, `search` e `billing` sem
  qualquer erro de compilação e sem falhar `ApplicationModules.verify()`. A rede
  de segurança é o teste de integração — se ele não existir ou for saltado, a
  falha aparece em produção.
- **A opção de extração do ADR-0001 fica mais cara** para `matching` e `search`:
  extraí-los para serviço próprio exigiria replicação de dados ou uma API
  dedicada, não apenas mover pacotes. É um custo real cobrado ao valor de saída
  que o ADR-0001 declarava.
- **A lista tem de ser mantida.** Uma lista desatualizada é pior do que nenhuma,
  porque dá falsa confiança a quem a lê.
- Aumenta a carga sobre o `db-migrations`, que passa a ter de saber que uma
  coluna aparentemente local tem consumidores noutros módulos.

## Alternativas rejeitadas

- **Proibição estrita:** rejeitada por transformar o predicado central num N+1
  sem uso de índice; contradiria o ADR-0004 na prática enquanto o respeitava na
  forma.
- **Sem regra:** rejeitada por já ter produzido três justificações divergentes
  para a mesma prática e nenhuma verificação.
- **Vistas SQL publicadas:** adiada, não descartada; custo de esquema e migrações
  não se justifica antes da primeira quebra real. É o caminho de evolução se o
  custo negativo acima se materializar.

## Ligações

- Verificação do Spring Modulith (o que `verify()` cobre): https://docs.spring.io/spring-modulith/reference/verification.html
- PostGIS `ST_DWithin` (uso de índice por comparação de *bounding box*): https://postgis.net/docs/ST_DWithin.html
- `WITH ... AS MATERIALIZED` (PostgreSQL 12+): https://www.postgresql.org/docs/current/queries-with.html

---
name: postgis-geo-matching
description: Como escrever consultas geoespaciais corretas e indexáveis com PostGIS no ServiMatch — geography vs geometry, ST_DWithin, índices GiST, os dois modos de cobertura (raio e região administrativa) e a política de uso do Nominatim. Usa ao implementar matching, cobertura de prestadores ou geocodificação.
---

# PostGIS e matching geográfico

## geography vs geometry — decidir antes de escrever a primeira query

- `geography` (SRID 4326): distâncias em **metros**, cálculo sobre o esferoide.
  É o que queres para "prestadores até 15 km".
- `geometry` com SRID 4326: distâncias em **graus**. `ST_DWithin(g, p, 15000)`
  compila, executa e devolve lixo silenciosamente. É o erro clássico e não dá
  erro nenhum.

Para Portugal continental, `geography` é a escolha certa: a diferença de precisão
face a uma projeção métrica é irrelevante para este caso de uso e evita
conversões espalhadas pelo código.

## Índices

```sql
CREATE INDEX idx_service_area_center ON provider_service_area USING GIST (center);
```

`ST_DWithin` usa o índice; `ST_Distance(...) < x` **não** usa e força varrimento
completo. Escreve sempre `ST_DWithin`.

**Mas `ST_DWithin` com a distância numa coluna também não usa o índice.** O
índice só é ativado se o planeador conseguir construir a caixa
`center && _st_expand(:ponto, d)`, o que exige `d` **constante no momento do
planeamento**. Com `radius_m` a variar por linha, o plano mostra a condição como
`Filter`, nunca como `Index Cond` — medido neste repositório com 20 000
prestadores. A correção é um `ST_DWithin` redundante com limite constante como
pré-filtro, antes do exato, e um `CHECK` no esquema que garanta que nenhum raio
o excede. Ver `docs/ARQUITETURA.md` §10.3 e `geo.CoverageSql`: reutiliza esse
fragmento em vez de reescrever o predicado.

Confirma o uso do índice com `EXPLAIN (ANALYZE, BUFFERS)` e volume realista. Com
poucas linhas o planeador prefere *seq scan* e o teste não prova nada. Asserta
sobre o **plano** (`Index Cond`, `CTE Scan`), não sobre tempo de execução.

## Dois modos de cobertura

O prestador define a área por raio **ou** por região administrativa. O MVP arranca
em `ADMIN_REGION`, porque não exige coordenadas fiáveis de todos os prestadores
nem geocodificação de qualidade no primeiro dia; `RADIUS` entra depois sem
alterar o predicado.

```sql
AND (
      (a.mode = 'RADIUS'
       AND ST_DWithin(a.center, :requestPoint, :maxRadiusM)   -- pré-filtro constante: dá o Index Cond
       AND ST_DWithin(a.center, :requestPoint, a.radius_m))   -- correção exata
   OR (a.mode = 'ADMIN_REGION' AND a.region_code = :requestRegion)
)
```

Se embrulhares isto numa CTE, usa `AS MATERIALIZED`: sem o qualificador o
PostgreSQL pode embutir a CTE e reavaliar a condição por linha externa
(`loops=17066` em vez de `loops=1`, medido). O pré-filtro e o `MATERIALIZED`
resolvem problemas **diferentes** — não bastam um sem o outro, e o teste de
regressão verifica os dois sinais em separado.

Um `OR` sobre modos diferentes pode produzir planos maus à medida que o volume
cresce. Se acontecer, separa em dois ramos (`UNION ALL` por modo) — mas só com
`EXPLAIN` que o justifique, não preventivamente (aqui, o `BitmapOr` combina o
GiST de `center` com o índice de `region_code` sem problema).

Garante coerência com CHECK no schema: `RADIUS` exige `center` e `radius_m`;
`ADMIN_REGION` exige `region_code`. Sem isso acumulas linhas que não
correspondem a ninguém e o matching perde prestadores sem sinal de erro.

## Geocodificação — Nominatim

A instância pública limita a **1 pedido/segundo** e proíbe uso pesado
(https://operations.osmfoundation.org/policies/nominatim/). Em produção,
auto-alojar é requisito.

Desenho obrigatório:
- Assíncrona e **cacheada** por morada normalizada; nunca no caminho crítico da
  criação do pedido.
- Falha degrada para `ADMIN_REGION`; não bloqueia o utilizador.
- `User-Agent` identificável, *rate limiting* e *backoff* do lado do cliente.
- Guarda a resposta em bruto: reprocessar é mais barato que voltar a pedir.

## Testes

Testcontainers com imagem **PostGIS** (não Postgres puro, não H2). Casos:
dentro do raio, fora do raio, exatamente na fronteira, região correspondente e
não correspondente, prestador sem subscrição ativa, prestador não aprovado.
Coordenadas fixas e reais (ex. Lisboa/Porto) para os resultados serem legíveis
quando o teste falhar.

## Referências

- PostGIS `ST_DWithin`: https://postgis.net/docs/ST_DWithin.html
- Tipo geography: https://postgis.net/docs/using_postgis_dbmanagement.html#PostGIS_Geography
- Política do Nominatim: https://operations.osmfoundation.org/policies/nominatim/

# ADR-0004: Geolocalização e matching com PostGIS

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0005

## Contexto e Problema

O núcleo do produto é ligar clientes a prestadores **da sua zona**. É preciso decidir como modelar as "zonas de atuação" dos prestadores e como executar o matching geográfico de pedidos, sem introduzir infraestrutura desnecessária.

## Fatores de Decisão

- Precisão do matching por proximidade real.
- Facilidade de configuração para o prestador.
- Dependência de geocoding externo (ver risco Nominatim, ADR relacionado / §10 da arquitetura).
- Não adicionar um segundo *datastore* prematuramente.

## Opções Consideradas

1. **PostGIS** (extensão PostgreSQL) com `geography(Point)` + raio e/ou regiões administrativas.
2. **Cálculo geoespacial em aplicação** (fórmula de Haversine sobre lat/lon em colunas simples).
3. **Serviço/base de dados geoespacial dedicado.**

## Decisão

Usar **PostGIS** sobre a mesma instância PostgreSQL. Modelar a cobertura do prestador em dois modos combináveis:

- **RADIUS:** `center geography(Point,4326)` + `radius_m`; match por `ST_DWithin(center, requestPoint, radius_m)`.
- **ADMIN_REGION:** `region_code` (concelho/distrito); match por igualdade com a região do pedido.

Índice **GiST** sobre as colunas `geography`. **No MVP**, começar por **ADMIN_REGION** (determinístico, sem dependência forte de geocoding preciso) e ativar **RADIUS** quando o geocoding self-hosted estiver estável.

## Consequências

**Positivas**
- Ferramenta correta para o problema; índices espaciais eficientes (GiST).
- Sem segundo *datastore*; transações e joins com o resto do modelo.
- Suporta desde já os dois modelos de cobertura.

**Negativas / Custos**
- Requer a extensão PostGIS instalada e presente nos ambientes de teste (Testcontainers com imagem PostGIS).
- O modo RADIUS depende de geocoding fiável (mitigado pelo faseamento).

## Alternativas rejeitadas

- **Haversine em aplicação:** não beneficia de índices espaciais; degrada com o volume e reimplementa o que o PostGIS já resolve.
- **Datastore geoespacial dedicado:** *overengineering* para o volume atual.

## Ligações

- PostGIS `ST_DWithin`: https://postgis.net/docs/ST_DWithin.html
- PostGIS geography: https://postgis.net/workshops/postgis-intro/geography.html

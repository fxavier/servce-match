# ADR-0003: Versão do stack backend (Spring Boot 3.5 vs 4.x)

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0001

## Contexto e Problema

Em julho de 2026 coexistem duas linhas do Spring Boot: **3.5.x** (linha madura, ex.: 3.5.13) e **4.1** (GA, assente em Spring Framework 7, baseline Java 17+). O **Spring Modulith 2.1** alinha com o ecossistema mais recente, enquanto a série **1.4.x** acompanha o Boot 3.x. É necessário escolher a baseline do MVP, ponderando maturidade de ecossistema contra longevidade.

## Fatores de Decisão

- Maturidade e estabilidade das dependências transitivas (drivers, starters, bibliotecas de terceiros).
- Risco de *early adoption* num MVP com prazo curto.
- Custo de atualização futura.
- Suporte LTS do Java (21 vs 25).

## Opções Consideradas

1. **Spring Boot 3.5.x + Spring Modulith 1.4.x + Java 21 (LTS).**
2. **Spring Boot 4.1 + Spring Modulith 2.1 + Java 21/25.**

## Decisão

**Decidido e fechado:** o MVP arranca em **Spring Boot 3.5.x + Spring Modulith 1.4.x + Java 21 (LTS)**. Estas versões são fixadas no `backend/pom.xml` pelo agente `backend-platform` e não são negociáveis por módulo: nenhum agente altera a baseline sem um ADR que substitua este.

A atualização para **Boot 4.x + Modulith 2.x** é trabalho planeado, não uma questão em aberto. Executa-se quando **todos** os critérios abaixo se verificarem, e é registada num ADR novo que substitui este:

1. Todas as dependências diretas do `pom.xml` publicam versão compatível com Spring Framework 7 (verificação explícita, dependência a dependência — não por amostragem).
2. O MVP está em produção e estabilizado; a migração não compete com entrega de funcionalidade.
3. Existe suíte de integração com Testcontainers a cobrir os caminhos críticos (auth, geo, pagamentos), que é o que torna a migração verificável em vez de esperançosa.
4. A linha 3.5.x aproxima-se do fim de suporte OSS, ou surge necessidade concreta de funcionalidade exclusiva do Framework 7.

## Racional

Um MVP não deve absorver o risco de *early adoption* no framework base. A linha 3.5.x é madura, amplamente suportada por bibliotecas de terceiros, e a atualização para 4.x é incremental (não é reescrita). Java 21 é LTS e suficiente; Java 25 fica fora do âmbito deste ADR — adotar-se-á, se for o caso, junto com a atualização do Boot, para concentrar num único evento o risco de mudança de baseline.

Manter a escolha "em aberto" tinha um custo real: cada agente que fixasse versões teria de a interpretar, e duas interpretações divergentes no mesmo monorepo produzem um `pom.xml` incoerente. Uma decisão fechada com critérios de saída explícitos preserva a mesma flexibilidade sem esse custo.

## Consequências

**Positivas**
- Máxima estabilidade de ecossistema durante o desenvolvimento do MVP.
- Menos tempo perdido a contornar incompatibilidades de bibliotecas.

**Negativas / Custos**
- Uma atualização de major (3.x → 4.x) fica no backlog técnico, com custo diferido e não eliminado.
- Não se aproveitam desde já as novidades do Spring Framework 7.
- Quanto mais tarde a migração, maior o *delta* acumulado — daí os critérios de saída serem verificáveis e não uma intenção vaga.

## Alternativas rejeitadas

**Spring Boot 4.1 + Spring Modulith 2.1 desde o início.** Rejeitada por risco de ecossistema num MVP com prazo curto: em julho de 2026 a linha 4.x é GA mas as dependências transitivas de terceiros (drivers, starters, bibliotecas de integração) ainda não têm a mesma cobertura comprovada que a linha 3.5.x. O ganho — evitar uma migração futura — não compensa o custo de depurar incompatibilidades de bibliotecas durante a construção do produto. É uma escolha defensável para quem privilegie longevidade sobre previsibilidade de prazo; não é a deste projeto.

**Java 25 como baseline inicial.** Rejeitada por acumular duas mudanças de baseline (linguagem e framework) num único arranque, sem benefício funcional identificado para o MVP.

## Ligações

- Spring Boot — ciclo de vida/suporte: https://endoflife.date/spring-boot
- Spring Boot 3.5.13: https://spring.io/blog/2026/03/26/spring-boot-3-5-13-available-now/
- Spring Modulith 2.1 GA: https://spring.io/blog/2026/06/11/spring-modulith-2-1-ga-2-0-7-and-1-4-12-released/
- Java (ciclo LTS): https://www.oracle.com/java/technologies/java-se-support-roadmap.html

> **Nota:** confirmar sempre a matriz de compatibilidade entre Spring Boot, Spring Modulith e Java antes de fixar versões no `pom.xml`.

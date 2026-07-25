# ADR-0001: Modular Monolith com Spring Modulith

- **Estado:** Aceite
- **Data:** 2026-07-24
- **Decisores:** Equipa ServiMatch
- **Relacionado:** ADR-0006 (Redis condicional)

## Contexto e Problema

O ServiMatch é um produto novo, com equipa pequena e volume de utilizadores incerto no arranque. É necessário definir o estilo arquitetural do backend de forma a maximizar velocidade de entrega e simplicidade operacional no MVP, sem fechar o caminho para escalar a organização e o deploy mais tarde.

## Fatores de Decisão

- Custo cognitivo e operacional para uma equipa pequena.
- Simplicidade de transações e consistência de dados.
- Custo de refactoring de fronteiras enquanto o domínio ainda está a estabilizar.
- Capacidade de evoluir para extração de serviços sem reescrita.
- Observabilidade e debugging.

## Opções Consideradas

1. **Modular Monolith (Spring Modulith)** — aplicação única, particionada em módulos com fronteiras verificadas em teste.
2. **Monolito tradicional em camadas** — sem fronteiras de módulo explícitas.
3. **Microserviços desde o início** — serviços independentes por domínio.

## Decisão

Adotar um **Modular Monolith com Spring Modulith**: uma única aplicação deployável, internamente dividida em módulos de aplicação com fronteiras verificadas por `ApplicationModules.verify()` e comunicação inter-módulo preferencialmente por **eventos de domínio** (`@ApplicationModuleListener`), recorrendo ao *Event Publication Registry* para entrega fiável.

## Consequências

**Positivas**
- Transações locais simples; sem transações distribuídas nem sagas no MVP.
- Menor custo operacional (um artefacto, um pipeline, uma base de dados).
- Refactoring de fronteiras barato enquanto o domínio evolui.
- Fronteiras explícitas e verificadas evitam o "monolito espaguete".
- Caminho de extração futura para serviços próprios sem reescrita do domínio.

**Negativas / Custos**
- Escala de organização e de deploy limitada a médio prazo.
- Disciplina necessária: acesso a tabelas de outro módulo é proibido (só API pública ou eventos), o que exige revisão contínua.
- Um deploy afeta toda a aplicação (mitigável com feature flags).

## Alternativas rejeitadas

- **Microserviços desde o início:** complexidade de rede, transações distribuídas, e observabilidade não justificada pelo volume atual. Reavaliar quando houver pressão real de escala/organização.
- **Monolito em camadas sem módulos:** perde-se a verificação de fronteiras e o caminho de extração fica comprometido.

## Ligações

- Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- "Modular Monolith" (S. Zimmermann / rationale geral): https://spring.io/blog/2022/10/21/introducing-spring-modulith

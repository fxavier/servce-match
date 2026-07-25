---
name: testcontainers-integration-test
description: Padrão de teste de integração do backend ServiMatch com Testcontainers — PostgreSQL/PostGIS, Keycloak, Redis e MinIO reais, obtenção de token verdadeiro, isolamento entre testes e desempenho da suíte. Usa ao escrever qualquer teste que atravesse a fronteira da aplicação.
---

# Testes de integração com Testcontainers

## Princípio

Substituir infraestrutura por mocks torna o teste rápido e inútil precisamente
onde este sistema é frágil: geoespacial, validação de JWT, migrações e storage.
H2 não tem PostGIS, não tem `tsvector` em português e não valida a mesma
sintaxe — um teste verde em H2 não diz nada sobre produção.

## Configuração base

```java
@SpringBootTest
@Testcontainers
abstract class IntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.4")
                .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

Containers **estáticos** e partilhados pela suíte: um container por classe de
teste multiplica o tempo da pipeline por dezenas. Ativa também o *reuse* local.

As migrações Flyway correm de raiz em cada arranque — é a validação contínua de
que o schema é reconstruível.

## Keycloak real nos caminhos críticos

Arranca o Keycloak em container com o realm importado de
`infra/keycloak/realm-servimatch.json` e obtém token pelo endpoint de token. É a
única forma de exercitar validação de assinatura, `iss`, `aud` e expiração.

Para a matriz de autorização (401/403/200 por role), `spring-security-test` é
suficiente e muito mais rápido. Usa cada ferramenta onde ela prova algo:
Keycloak real para o mecanismo, mock para a combinatória.

## Isolamento

Cada teste deixa a base num estado conhecido. Escolhe **uma** estratégia e usa-a
de forma consistente: transação com rollback (rápida, mas não serve para código
que gere transações próprias, como consumidores `@ApplicationModuleListener`), ou
truncatura das tabelas afetadas depois de cada teste.

Nunca dependas da ordem de execução nem de dados deixados por outro teste. É a
principal causa de suítes que falham só no CI.

## Assíncrono

Eventos do Spring Modulith são processados após commit e noutra thread. Não uses
`Thread.sleep`. Usa Awaitility com condição explícita e timeout, ou verifica as
publicações com `AssertablePublishedEvents` quando o efeito não for observável.

## Desempenho

- Containers partilhados, `reuse` ativado, paralelização por classe quando o
  isolamento o permitir.
- Se a suíte de integração ultrapassar o orçamento da pipeline, paraleliza ou
  divide por etapas — não desativa testes nem os marcas como ignorados.

## Referências

- Testcontainers: https://java.testcontainers.org/
- Spring Boot + Testcontainers: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
- Awaitility: https://github.com/awaitility/awaitility

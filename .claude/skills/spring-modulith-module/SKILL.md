---
name: spring-modulith-module
description: Como criar ou alterar um módulo de domínio no backend ServiMatch com Spring Modulith — estrutura de pacotes, API pública mínima, eventos entre módulos, transações e testes de fronteira. Usa quando fores escrever código em backend/src/main/java/pt/servimatch/modules.
---

# Módulo Spring Modulith

## Estrutura

```
pt.servimatch.modules.<modulo>/
├── <Modulo>Api.java          # API pública: interface + DTOs expostos
├── events/                   # eventos publicados (parte do contrato público)
└── internal/                 # tudo o resto: entidades, repositórios, serviços
    ├── <Entidade>.java
    ├── <Entidade>Repository.java
    ├── <Modulo>Service.java
    └── <Modulo>Controller.java
```

Regra: um módulo só pode importar do pacote de topo de outro módulo e dos seus
`events`. Importar de `internal` alheio é violação e o build falha.

## Comunicação entre módulos

Preferir eventos a chamadas diretas. Chamada síncrona só quando o resultado é
necessário para completar a operação em curso.

```java
// Publicação — dentro da transação que muda o estado
@Transactional
public void accept(UUID proposalId) {
    var proposal = repository.findByIdForUpdate(proposalId).orElseThrow(...);
    proposal.accept();                       // regra de domínio no agregado
    events.publishEvent(new ProposalAccepted(proposal.id(), proposal.requestId()));
}

// Consumo — noutro módulo, transação separada, após commit do produtor
@ApplicationModuleListener
void on(ProposalAccepted event) { ... }
```

`@ApplicationModuleListener` = `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`
+ `@Transactional(REQUIRES_NEW)`. Com o Event Publication Registry ativo a
entrega é **at-least-once** e sobrevive a reinício.

Consequências que tens de assumir, não descobrir em produção:
- **O consumidor tem de ser idempotente.** Vai receber o mesmo evento mais do que
  uma vez.
- **A ordem entre eventos não é garantida.**
- O evento carrega **identificadores e o mínimo de dados**, não o agregado
  inteiro; o consumidor lê o que precisa pela API pública do produtor.

## Efeitos externos

Email, push, chamadas a gateways e escrita em object storage acontecem **no
consumidor do evento**, nunca dentro da transação que altera o estado. Um
timeout do fornecedor de email não pode reverter a criação de um pedido.

## Testes

```java
class ModuleBoundaryTests {
    static final ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test void verifiesBoundaries() { modules.verify(); }
    @Test void documents() { new Documenter(modules).writeDocumentation(); }
}
```

Para o módulo isolado usa `@ApplicationModuleTest`, que arranca apenas o módulo
e permite verificar eventos publicados com `PublishedEvents` / `AssertablePublishedEvents`.

Se `modules.verify()` falhar, corrige o acoplamento. Não adiciones exceções à
configuração para desbloquear: essa verificação é o que permite vários agentes
trabalharem em módulos diferentes ao mesmo tempo sem se partirem uns aos outros.

## Referências

- Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- Event Publication Registry: https://docs.spring.io/spring-modulith/reference/events.html

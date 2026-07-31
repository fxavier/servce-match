---
name: admin-moderation-endpoint
description: Como implementar um endpoint de moderação administrativa no ServiMatch — autorização ROLE_ADMIN, máquina de transições com 409, motivo obrigatório, Idempotency-Key, escrita em audit_log e resposta RFC 9457. Usa para PATCH /v1/admin/** e para qualquer decisão humana que altere estado de elegibilidade de outro utilizador.
---

# Endpoint de moderação administrativa

Aplica-se a decisões tomadas por um humano com poder sobre a conta de outro:
aprovação de prestador, suspensão, moderação de conteúdo. São os endpoints com
maior rácio entre dano potencial e volume de tráfego.

## 1. Autorização — explícita, nunca herdada

```java
@PreAuthorize("hasRole('ADMIN')")
```

Nunca dependas de `anyRequest().authenticated()`. O `ADMIN` não é atribuível por
registo (CLAUDE.md §4: allowlist `{CUSTOMER, PROVIDER}` no BFF), pelo que a role
só chega por atribuição fora da aplicação — mas isso não dispensa a anotação.

O método anotado é **público**, não *package-private*: `@PreAuthorize` num método
não-público é ignorado em silêncio por proxy CGLIB.

Um `403` de moderação não revela se o alvo existe. Verifica a role **antes** de
carregar o alvo, senão o par `403`/`404` torna-se oráculo de enumeração.

## 2. Máquina de transições — allowlist, não denylist

Declara as transições válidas como dado, não como cadeia de `if`:

```java
private static final Map<ApprovalStatus, Set<ApprovalStatus>> TRANSICOES = Map.of(
        PENDING,  Set.of(APPROVED, REJECTED),
        APPROVED, Set.of(SUSPENDED),
        REJECTED, Set.of(),
        SUSPENDED, Set.of());
```

Regras:

- Transição não permitida → **`409`** com Problem Details, `type` sob
  `https://errors.servimatch.pt/`, e o estado atual no corpo.
- Alvo inexistente → `404`.
- `decision` fora do enum → `400`. Valida contra o enum, nunca aceites a string
  crua — é a mesma classe de defeito do `statusFilter` em `listInbox`.
- Motivo obrigatório em falta → `422` (distinto de `400`: a sintaxe está certa,
  a semântica não).
- **`PENDING` nunca é destino.** O contrato separa `ProviderApprovalStatus`
  (4 valores, o que a coluna pode conter) de `ProviderApprovalDecision`
  (3 valores, o que a decisão pode atribuir). Espelha essa separação em dois
  tipos Java distintos; um único enum reabre o caminho para reverter a `PENDING`.

## 3. Escrita — compare-and-set, uma transação

```sql
UPDATE provider_profile
   SET approval_status = :novo, approval_reason = :motivo,
       approval_decided_by = :adminId, approval_decided_at = now()
 WHERE id = :providerId AND approval_status = :esperado
```

`WHERE ... AND <coluna> = :esperado` é o que torna a transição segura sob
concorrência. Zero linhas afetadas → relê o estado e devolve `409`. Nunca leias,
decidas e escrevas em statements separados sem esta guarda.

O motivo é PII potencial (texto livre sobre uma pessoa): guarda-o na coluna,
**nunca** o registes em log.

## 4. Auditoria — obrigatória, na mesma transação

Toda a decisão de moderação escreve em `audit_log` (V13), na **mesma transação**
da escrita de estado. Auditoria fora da transação é auditoria que se perde
exatamente quando é precisa.

```java
auditLog.record(
        adminUserId,                    // actor_id
        "provider.approval.decided",    // action — <agregado>.<facto>.<verbo>
        "provider_profile",             // target_type
        providerId,                     // target_id
        Map.of("from", atual, "to", novo));  // metadata JSONB
```

`correlation_id` vem de `CorrelationIdSupport.currentOrNull()`, não de parâmetro.
**Nunca ponhas email, nome ou telefone no `metadata`** (CLAUDE.md §4) — o alvo
identifica-se por UUID.

## 5. Idempotência

O contrato aceita `Idempotency-Key` neste endpoint. Reutiliza
`platform/idempotency`; não escrevas um mecanismo novo. Repetição da mesma chave
com o mesmo corpo devolve a resposta original sem reaplicar a transição — o que
importa aqui é que um duplo clique do administrador não produza duas entradas de
auditoria.

## 6. Efeito a jusante — verifica-o, não o assumas

Um endpoint de moderação existe para mudar o que outra pessoa vê. Se a mudança
não se propagar aos predicados de leitura, o endpoint é decorativo.

Antes de fechares: aplica a skill `estado-com-escritor`. A coluna que acabaste de
escrever é lida por algum `WHERE`? Existe um teste que verifique **os dois
lados** — invisível antes, visível depois — pelo caminho de produção?

## 7. Checklist

- [ ] `@PreAuthorize("hasRole('ADMIN')")` em método público
- [ ] Role verificada antes de carregar o alvo
- [ ] Transições como allowlist declarativa; `409` fora dela
- [ ] Enum de decisão distinto do enum de estado
- [ ] `422` para motivo obrigatório em falta; `400` para decisão inválida
- [ ] `UPDATE ... WHERE estado = :esperado`, zero linhas → `409`
- [ ] `audit_log` na mesma transação, sem PII no `metadata`
- [ ] `Idempotency-Key` via `platform/idempotency`
- [ ] Problem Details RFC 9457 em todos os erros
- [ ] Motivo nunca em log
- [ ] Teste de transição com asserção dos dois lados

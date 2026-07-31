# Runbook — executar a Onda C1 no Claude Code

Convenção do projeto: `docs/AGENTES.md` §5 — numa onda paralela, os agentes
independentes são lançados **na mesma mensagem**, para correrem em concorrência
em vez de em série. É isso que as mensagens abaixo fazem.

Uma sessão de Claude Code, aberta na raiz do repositório. Não precisas de
worktrees separados: as definições em `.claude/agents/` já assumem coexistência
no mesmo worktree (ver a secção "Coexistência" em `backend-domain-providers.md`)
e os âmbitos desta onda são disjuntos. Worktrees por agente tornariam as
dependências A→B mais difíceis de gerir, não mais fáceis.

---

## 0. Pré-voo (uma vez, antes de tudo)

```bash
cd /Users/xavier/dev/code/workspace/2026/service-match

# 1. Instalar as skills — sem isto, B1 e C1 mandam ler skills que não existem
mv docs/_skills-para-instalar/estado-com-escritor       .claude/skills/
mv docs/_skills-para-instalar/admin-moderation-endpoint .claude/skills/
rmdir docs/_skills-para-instalar
ls .claude/skills/            # tem de mostrar 11 diretórios

# 2. Versionar o planeamento
git add docs/ESTADO-DO-SISTEMA.md docs/ONDA-C1.md docs/prompts-onda-c1 .claude/skills
git commit -m "docs: state audit, wave C1 plan, decision-writer and admin-moderation skills"

# 3. Ramo de trabalho da onda
git checkout -b feat/onda-c1/aprovacao-prestador

# 4. Linha de base do defeito — TEM de devolver vazio agora
grep -rn "SET approval_status" backend/src/main/java
```

Aquele último comando é a medida do sucesso desta onda. Guarda o resultado: no
fim tem de devolver pelo menos uma linha.

---

## 1. Onda A — cola isto no Claude Code

Cinco agentes em paralelo. A mensagem é curta de propósito: cada agente lê o seu
prompt do ficheiro, o que evita erros de transcrição e mantém o contexto do
orquestrador leve.

```
Lança estes cinco agentes EM PARALELO, na mesma mensagem, um por prompt.
Cada um lê o seu ficheiro de prompt e segue-o à letra:

- db-migrations           → docs/prompts-onda-c1/a1-db-migrations.txt
- backend-platform        → docs/prompts-onda-c1/a2-backend-platform.txt
- backend-domain-requests → docs/prompts-onda-c1/a3-backend-domain-requests.txt
- web-bff                 → docs/prompts-onda-c1/a4-web-bff.txt
- web-site                → docs/prompts-onda-c1/a5-web-site.txt

Contexto partilhado: docs/ONDA-C1.md (secção 1 tem factos já verificados contra
62437e1 — não os redescubram) e docs/ESTADO-DO-SISTEMA.md (defeitos C1, C3.1, C4).

Regras desta onda, para todos:
- Os âmbitos são disjuntos. Nenhum agente escreve fora do seu âmbito. Um agente
  que precise de tocar em caminho alheio PARA e reporta — não negoceia sozinho a
  fronteira (CLAUDE.md §6).
- Nenhum agente faz git commit, checkout, stash, restore ou clean. Só diff/status.
  Eu faço os commits entre ondas.
- Cada agente deixa o seu trabalho a compilar, com lint limpo e testes a passar.
- No fim, cada um reporta: o que fez, o que decidiu e porquê, e o que ficou por
  fazer no seu âmbito.

Quando os cinco terminarem, faz um resumo consolidado. Não arranques a Onda B.
```

### Porta de saída da Onda A

Antes de avançar, verifica tu — não aceites o relatório dos agentes como prova:

```bash
ls backend/src/main/resources/db/migration/V17*        # A1 entregou
ls backend/src/main/java/pt/servimatch/platform/audit/ # A2 entregou
git diff --stat                                        # âmbitos disjuntos?
```

Se dois agentes tocaram no mesmo ficheiro, para e resolve antes da Onda B — é
sinal de fronteira mal desenhada, não de conflito trivial.

```bash
git add -A && git commit -m "feat(onda-c1): migration, audit writer, inbox status allowlist, register anti-enumeration, admin UI"
```

---

## 2. Onda B — um agente, depende de A1 e A2

```
Lança o agente backend-domain-providers com o prompt em
docs/prompts-onda-c1/b1-backend-domain-providers.txt.

Antes de começar, confirma que a Onda A entregou o que ele assume:
- backend/src/main/resources/db/migration/V17*.sql existe e acrescentou
  approval_reason, approval_decided_by, approval_decided_at
- platform/audit expõe um escritor de audit_log
- providers/package-info.java declara a dependência necessária

Se alguma dessas premissas falhar, PARA e diz-me — não contornes a falta.

Este é o agente que fecha o C1, o defeito mais grave do sistema. O critério não é
"o endpoint responde 200": é que `grep -rn "SET approval_status" backend/src/main/java`
passe a devolver uma linha, e que a aprovação mude o que a pesquisa devolve.
```

### Porta de saída da Onda B

```bash
grep -rn "SET approval_status" backend/src/main/java   # tem de devolver ≥1 linha
cd backend && mvn verify                                # inclui ApplicationModules.verify()
```

Se o `grep` continuar vazio, o C1 **não** foi fechado, independentemente do que o
agente relate. É esse o teste.

```bash
git add -A && git commit -m "feat(providers): admin approval decision endpoint with audit trail"
```

---

## 3. Onda C — dois agentes em paralelo

```
Lança estes dois agentes EM PARALELO, na mesma mensagem:

- qa-e2e           → docs/prompts-onda-c1/c1-qa-e2e.txt
- security-auditor → docs/prompts-onda-c1/c2-security-auditor.txt

O qa-e2e escreve (backend/src/test/**, web/e2e/**). O security-auditor é leitura
apenas — relata, não corrige.

O teste do qa-e2e é o que fecha o C1 de verdade: sem ele, o defeito volta sem
ninguém dar por isso. Assevera os DOIS lados — invisível antes, visível depois.
```

### Porta de saída da onda

```bash
cd backend && mvn verify
cd ../web && npm run lint && npm test && npm run test:e2e
cd ../mobile && flutter analyze --fatal-infos && flutter test
```

Os sete critérios de aceitação estão em `docs/ONDA-C1.md` §4. O primeiro é o que
importa.

---

## 4. Se preferires correr agente a agente

Nada obriga ao paralelismo. Para lançar um só:

```
Usa o agente <nome> com o prompt em docs/prompts-onda-c1/<ficheiro>.txt.
```

Mais lento, mas mais fácil de rever. Faz sentido para o B1, que é o mais denso e
o que carrega o defeito crítico — podes querer segui-lo de perto em vez de o
misturar com outros na mesma janela.

---

## 5. Modos de falha prováveis

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| Agente diz que não encontra a skill | Faltou o `mv` do pré-voo | Instala e relança esse agente |
| Dois agentes editaram o mesmo ficheiro | Fronteira mal desenhada, não conflito trivial | Para; decide o dono; escala ao `arquiteto` |
| `ApplicationModules.verify()` falha depois da Onda A | `platform/audit` consumido sem a fronteira declarada | É do `backend-platform` (A2), não do consumidor. Devolve-lhe |
| B1 relata sucesso mas o `grep` continua vazio | Escreveu por caminho que não é `UPDATE ... SET` (ex. ORM, SQL noutro ficheiro) | Procura o escritor real; se não existir, o C1 não fechou |
| E2E do `web-site` falha por endpoint em falta | O `mock-backend` não foi atualizado | É do `web-site` (A5); o âmbito inclui `web/e2e/**` |
| Agente tenta `git commit` | Contraria a definição dele | Recusa; os commits são teus, entre ondas |

## 6. O que fica para depois

Fora do âmbito por escolha, registado em `docs/ONDA-C1.md` §5: o escritor de
`rating_avg` (C2 residual, `backend-domain-social`), o ADR de deploy com tudo o
que dele depende (C5), os defaults de `prod` (C6), e o teste de arquitetura que
imporia a regra da skill `estado-com-escritor` no build — que é o controlo que
impede a quarta repetição deste defeito, e precisa do `arquiteto`.

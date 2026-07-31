-- Persiste a decisão administrativa sobre provider_profile.approval_status
-- (ADR-0011 §D7; defeito C1 — docs/ESTADO-DO-SISTEMA.md). A coluna existe
-- desde V4 e é lida por todos os predicados de elegibilidade
-- (EligibilityRepository, ProviderSearchRepository, ProvidersService), mas
-- não tinha, até agora, quem/quando/porquê da decisão: nada em produção a
-- movia para além do DEFAULT 'PENDING'. PATCH /v1/admin/providers/{id}/approval
-- (openapi.yaml:800) precisa destas três colunas para responder ao contrato
-- (ProviderApproval exige decidedBy/decidedAt; reason é opcional só para
-- APPROVED).
--
-- NÃO se mexe no CHECK de approval_status (V4:27): já aceita
-- ('PENDING','APPROVED','REJECTED','SUSPENDED') — confirmado antes de
-- escrever esta migração. O enum do domínio não muda.

-- Motivo da decisão. Nullable no esquema porque APPROVED pode não ter
-- motivo; REJECTED/SUSPENDED sem motivo é regra de serviço (422), não de
-- schema — o schema não pode distinguir "motivo omitido" de "decisão para
-- um estado que não o exige" sem repetir a máquina de estados em SQL.
ALTER TABLE provider_profile
    ADD COLUMN approval_reason TEXT NULL;

-- Quem decidiu. ON DELETE SET NULL: perder o utilizador administrador não
-- pode impedir o DELETE em users nem apagar o histórico de decisões — só
-- perde a atribuição a uma pessoa concreta, mantendo approval_decided_at e
-- approval_reason.
ALTER TABLE provider_profile
    ADD COLUMN approval_decided_by UUID NULL REFERENCES users (id) ON DELETE SET NULL;

-- Quando decidiu.
ALTER TABLE provider_profile
    ADD COLUMN approval_decided_at TIMESTAMPTZ NULL;

-- Nenhum DEFAULT e nenhum backfill: as linhas existentes estão todas em
-- PENDING e nunca foram decididas — inventar um valor para
-- approval_decided_by/approval_decided_at seria fabricar uma decisão que
-- não aconteceu.

-- Coerência: uma vez fora de PENDING, a decisão tem de ter instante. Sem
-- este CHECK, um UPDATE feito fora do serviço de aprovação (SQL manual, uma
-- migração futura descuidada) poderia deixar approval_status='APPROVED' sem
-- nenhum rasto de quando — o mesmo modo de silêncio que o defeito C1 já
-- explorou uma vez com o DEFAULT.
--
-- O CHECK exige APENAS approval_decided_at, não approval_decided_by, e isto
-- foi verificado empiricamente, não assumido: com ambas as colunas exigidas,
-- ON DELETE SET NULL (acima) e este CHECK contradizem-se. Quando o
-- administrador que decidiu é apagado de users, o FK põe
-- approval_decided_by a NULL numa linha já fora de PENDING; se o CHECK
-- também exigisse approval_decided_by NOT NULL, essa mesma operação violaria
-- o CHECK e bloquearia o DELETE em users — exatamente o que o comentário da
-- FK (acima) promete que não acontece. Testado num Postgres efémero: INSERT
-- de uma linha APPROVED com decided_by preenchido seguido de DELETE do
-- administrador falha com "violates check constraint
-- chk_provider_profile_approval_decision_coherence" se decided_by entrar no
-- CHECK. approval_decided_at não sofre este problema (nada lhe faz SET
-- NULL), pelo que é a âncora fiável de "houve decisão"; decided_by é
-- atribuição best-effort, sempre preenchida pelo serviço no momento da
-- decisão (ver PATCH /v1/admin/providers/{id}/approval), mas pode
-- legitimamente perder-se depois sem que a linha deixe de ser uma decisão
-- válida.
-- reason fica de fora do CHECK deliberadamente: exigi-lo aqui obrigaria o
-- schema a saber que REJECTED/SUSPENDED o exigem e APPROVED não, duplicando
-- a máquina de estados do serviço; essa regra de negócio fica em
-- modules/providers (422 se faltar), o schema só garante o instante.
ALTER TABLE provider_profile
    ADD CONSTRAINT chk_provider_profile_approval_decision_coherence CHECK (
        approval_status = 'PENDING'
        OR approval_decided_at IS NOT NULL
    );
-- Verificado: todas as linhas hoje têm approval_status='PENDING' (DEFAULT
-- da V4, sem escritor em produção até este momento — ver ESTADO-DO-SISTEMA.md
-- C1), pelo que o CHECK, validado imediatamente (sem NOT VALID), é
-- satisfeito por toda a tabela existente sem exceção a inventariar.

-- idx_provider_profile_visibility (V4:53) indexava (visibility_state,
-- approval_status) para o predicado central de matching/pesquisa. A onda-1
-- retirou visibility_state de todos os predicados de produção (ADR-0011
-- D1 — confirmado: zero ocorrências em src/main/java fora de comentários
-- que documentam a remoção). Um índice cuja primeira coluna nenhum
-- predicado usa mais não é seletividade grátis: é escrita paga em cada
-- INSERT/UPDATE de provider_profile e espaço em disco por um valor que já
-- ninguém filtra por ele. approval_status sozinho é a coluna que os três
-- predicados (EligibilityRepository, ProviderSearchRepository,
-- ProvidersService) e agora também o UPDATE ... WHERE id = :id AND
-- approval_status = :esperado do endpoint de aprovação usam a sério —
-- substitui-se o índice composto por um índice de coluna única sobre ela.
-- A coluna visibility_state em si (e o seu CHECK) não é removida nesta
-- migração: essa é uma alteração destrutiva fora do âmbito pedido aqui
-- (exige o seu próprio plano de reversão, ADR-0011 D1) e não faz parte da
-- tarefa desta migração.
DROP INDEX idx_provider_profile_visibility;
CREATE INDEX idx_provider_profile_approval_status ON provider_profile (approval_status);

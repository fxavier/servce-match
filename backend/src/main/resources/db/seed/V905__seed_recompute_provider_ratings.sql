-- Seed dev-only (ADR-0013) — NÃO corre em produção (D3: fora do artefacto
-- de build de produção, fora de spring.flyway.locations fora de
-- local/dev, arranque aborta se aplicável — camadas do backend-platform).
-- Gerado uma vez a partir de web/site/src/services/mock/fixtures/**, que
-- deixam de ser fonte de verdade dos dados de desenvolvimento a partir daqui
-- (ADR-0013 D7). Idempotente: ON CONFLICT DO NOTHING sobre chave natural ou
-- sobre o id literal fixo (nunca gen_random_uuid() — D2).
--
-- Banda reservada V900-V999 para ficheiros de backend/src/main/resources/db/seed/
-- (ADR-0013 D2). Nenhuma migração de produção usa >= 900.

-- rating_avg/rating_count nunca são escritos à mão em nenhum ficheiro deste
-- seed — só aqui, por agregação real sobre "review", exatamente a mesma
-- fórmula que um produtor de produção teria de usar (ADR-0011 D9 regista
-- esta coluna como "lida sem produtor" — este UPDATE é idempotente e
-- correto, mas não substitui esse produtor: ver relatório do db-migrations).
-- Sem WHERE: corre sobre toda a tabela, não só sobre os prestadores deste
-- seed — correto mesmo que um dia existam prestadores reais a par destes,
-- porque o resultado depende só das reviews existentes, não de uma lista
-- fixa de ids. Tabela sem volume de produção nesta fase (mesmo raciocínio
-- do V16): um UPDATE sem WHERE é aceitável aqui.
UPDATE provider_profile pp
   SET rating_avg = COALESCE(agg.avg_rating, 0),
       rating_count = COALESCE(agg.review_count, 0)
  FROM (
      SELECT u.id AS user_id, ROUND(AVG(r.rating)::numeric, 2) AS avg_rating, COUNT(*) AS review_count
        FROM review r
        JOIN users u ON u.id = r.target_id
       GROUP BY u.id
  ) AS agg
 WHERE pp.user_id = agg.user_id;

-- Prestadores sem nenhuma review seedada ficam a 0/0 (não há linha "agg"
-- para eles) — números verdadeiros, não os valores decorativos que
-- fixtures/providers.ts atribuía a todos os 24 mesmo sem review nenhuma por
-- trás (ex.: p-0012 tinha ratingAvg:4.2/ratingCount:45 no mock sem UMA
-- review correspondente em fixtures/reviews.ts). Isto é intencional: a
-- alternativa seria fabricar reviews que não existem só para justificar um
-- número, exatamente o que o ADR-0011 D9 proíbe.
UPDATE provider_profile pp
   SET rating_avg = 0, rating_count = 0
 WHERE NOT EXISTS (
     SELECT 1 FROM review r JOIN users u ON u.id = r.target_id WHERE u.id = pp.user_id
 );

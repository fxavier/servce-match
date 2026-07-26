-- Dados de referência sem os quais a aplicação não é utilizável:
--   - sem `category`, POST /v1/requests devolve 400 (categoryId obrigatório,
--     nenhuma categoria existe para escolher);
--   - sem `subscription_plan`, GET /v1/subscription-plans devolve lista vazia
--     e nenhum prestador consegue desbloquear o gating de subscrição.
--
-- Migração versionada (V__), não repetível (R__): preço e limites de plano
-- são decisões de negócio auditáveis (ARQUITETURA §3.4) — cada alteração
-- deve ficar registada como um passo datado e revisto na história do
-- schema, não re-executada silenciosamente sempre que o ficheiro mude
-- (que é o comportamento esperado de um R__). Correções futuras a estes
-- dados são novas migrações V__, nunca uma edição deste ficheiro — mesma
-- regra de imutabilidade que já se aplica a V1..V14.
--
-- Idempotente: ON CONFLICT DO NOTHING sobre a chave natural (slug/code),
-- nunca sobre um id fixo — re-executável sem erro numa base já semeada.

-- Categorias de topo (ARQUITETURA §9.2 / §9.4 — mercado português de
-- serviços locais).
INSERT INTO category (slug, name) VALUES
    ('canalizacao', 'Canalização'),
    ('eletricidade', 'Eletricidade'),
    ('limpezas', 'Limpezas'),
    ('pinturas', 'Pinturas'),
    ('jardinagem', 'Jardinagem'),
    ('mudancas', 'Mudanças'),
    ('climatizacao', 'Climatização'),
    ('serralharia', 'Serralharia'),
    ('carpintaria', 'Carpintaria e Marcenaria'),
    ('obras-remodelacoes', 'Obras e Remodelações')
ON CONFLICT (slug) DO NOTHING;

-- Subcategorias — parent_id resolvido pelo slug do pai, nunca por id fixo.
INSERT INTO category (parent_id, slug, name)
SELECT parent.id, child.slug, child.name
FROM (VALUES
    ('canalizacao', 'desentupimentos', 'Desentupimentos'),
    ('canalizacao', 'reparacao-fugas', 'Reparação de Fugas'),
    ('canalizacao', 'instalacao-sanitarios', 'Instalação de Sanitários'),

    ('eletricidade', 'instalacoes-electricas', 'Instalações Elétricas'),
    ('eletricidade', 'quadros-electricos', 'Quadros Elétricos'),
    ('eletricidade', 'iluminacao', 'Iluminação'),

    ('limpezas', 'limpeza-domestica', 'Limpeza Doméstica'),
    ('limpezas', 'limpeza-escritorios', 'Limpeza de Escritórios'),
    ('limpezas', 'limpeza-pos-obra', 'Limpeza Pós-Obra'),

    ('pinturas', 'pintura-interiores', 'Pintura de Interiores'),
    ('pinturas', 'pintura-exteriores', 'Pintura de Exteriores'),

    ('jardinagem', 'manutencao-jardins', 'Manutenção de Jardins'),
    ('jardinagem', 'poda-arvores', 'Poda de Árvores'),

    ('mudancas', 'mudancas-residenciais', 'Mudanças Residenciais'),
    ('mudancas', 'mudancas-escritorio', 'Mudanças de Escritório'),

    ('climatizacao', 'instalacao-ar-condicionado', 'Instalação de Ar Condicionado'),
    ('climatizacao', 'manutencao-avac', 'Manutenção de AVAC'),

    ('serralharia', 'portoes-automaticos', 'Portões Automáticos'),

    ('carpintaria', 'mobiliario-medida', 'Mobiliário à Medida'),

    ('obras-remodelacoes', 'remodelacao-cozinhas', 'Remodelação de Cozinhas'),
    ('obras-remodelacoes', 'remodelacao-banheiros', 'Remodelação de Casas de Banho')
) AS child(parent_slug, slug, name)
JOIN category parent ON parent.slug = child.parent_slug
ON CONFLICT (slug) DO NOTHING;

-- Planos de subscrição — estrutura de referência de ARQUITETURA §3.4;
-- limites e boost como dados (§9.2), não constantes no código
-- (pt.servimatch.modules.billing.SubscriptionPlan). max_categories/max_areas
-- NULL == ilimitado.
INSERT INTO subscription_plan
    (code, name, price_amount_cents, price_currency, billing_interval,
     max_categories, max_areas, ranking_boost, has_badge, active)
VALUES
    ('starter', 'Starter', 1990, 'EUR', 'MONTHLY', 3, 2, 0, false, true),
    ('professional', 'Professional', 3990, 'EUR', 'MONTHLY', NULL, NULL, 0, false, true),
    ('premium', 'Premium', 6990, 'EUR', 'MONTHLY', NULL, NULL, 10, true, true)
ON CONFLICT (code) DO NOTHING;

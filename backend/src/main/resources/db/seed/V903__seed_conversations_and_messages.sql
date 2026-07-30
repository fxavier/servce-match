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

-- Conversas (fixtures/conversations.ts). Existem já durante IN_NEGOTIATION/
-- PUBLISHED — o chat acontece precisamente durante a negociação, antes de
-- qualquer proposta ser aceite; nenhuma FK/trigger exige proposta ACCEPTED
-- para uma conversation existir (só review exige booking COMPLETED, V10).
INSERT INTO conversation (id, request_id, customer_id, provider_id, created_at) VALUES
    ('bfe1cd99-c14d-5064-ba4d-1770344c011d', 'e51d2d85-f4de-51c9-b417-143c9f451ea6', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', '98229d44-928e-59a0-bd3f-22686bfe713b', now() - make_interval(mins => 210)),
    ('3e57f9cf-896d-50bb-8421-b62d2a1977b3', '95100bf7-4255-5baa-979b-f032d86b41ca', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'dda72dd5-12a0-523b-8b38-699ef2db5656', now() - make_interval(mins => 630))
ON CONFLICT (id) DO NOTHING;

-- Mensagens (fixtures/conversations.ts) — o trigger
-- trg_message_update_conversation_last_message (V20) mantém
-- conversation.last_message_at/last_message_preview atualizados a cada
-- INSERT, pela ordem em que estas linhas são inseridas (crescente por
-- sent_at dentro de cada conversa, tal como no fixture).
INSERT INTO message (id, conversation_id, sender_id, body, sent_at, read_at) VALUES
    ('db750c03-2b22-5cc8-a82e-071edf46e09e', 'bfe1cd99-c14d-5064-ba4d-1770344c011d', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'Boa tarde. Vi que enviou proposta para a fuga na cozinha — pode vir ver ainda esta semana?', now() - make_interval(mins => 180), now() - make_interval(mins => 178)),
    ('09409e58-0f92-5ee3-8b11-77b388ee247e', 'bfe1cd99-c14d-5064-ba4d-1770344c011d', '1f88f1a6-8b95-558f-bcfd-0c825e2d11f9', 'Boa tarde! Sim, consigo passar amanhã de manhã. Serve às 9h?', now() - make_interval(mins => 170), now() - make_interval(mins => 168)),
    ('6efc24f5-a4ee-5b26-9ff3-2d09e1851d9d', 'bfe1cd99-c14d-5064-ba4d-1770344c011d', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'Serve perfeitamente. É um 3º andar com elevador, o código da porta é 1234.', now() - make_interval(mins => 160), now() - make_interval(mins => 155)),
    ('651e73cc-a92f-5c85-877f-40e2e5a4928f', 'bfe1cd99-c14d-5064-ba4d-1770344c011d', '1f88f1a6-8b95-558f-bcfd-0c825e2d11f9', 'Anotado. Vou levar já material para reparação de sifão, caso seja isso — evita uma segunda visita.', now() - make_interval(mins => 150), now() - make_interval(mins => 140)),
    ('f8ce81c4-e144-515b-9176-36433b045d8e', 'bfe1cd99-c14d-5064-ba4d-1770344c011d', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'Muito obrigada pela previsão. Fico então à espera de vocês amanhã às 9h.', now() - make_interval(mins => 12), NULL),
    ('aec304d9-3615-5616-b952-34fe0d3cfd68', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'Olá! Vi o seu perfil e gostava de perceber se conseguia começar já para a semana.', now() - make_interval(mins => 600), now() - make_interval(mins => 590)),
    ('027c1560-5c6d-5d76-9761-bd2d46fd46b3', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'ca0edb3e-b5f6-580e-9267-0db35b037d0a', 'Boa tarde. Consigo sim — preciso só de confirmar a cor final da tinta consigo antes.', now() - make_interval(mins => 560), now() - make_interval(mins => 555)),
    ('cad15b75-edfc-5324-b6ab-b13942912c2b', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'É branco Dulux Vivid White, já tenho os baldes comprados.', now() - make_interval(mins => 540), now() - make_interval(mins => 530)),
    ('6d8bbf28-22ba-5798-94f3-9510f399d179', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'ca0edb3e-b5f6-580e-9267-0db35b037d0a', 'Perfeito, isso poupa-nos tempo. Consegue estar em casa numa sexta-feira de manhã para eu começar a preparação das paredes?', now() - make_interval(mins => 400), now() - make_interval(mins => 390)),
    ('c7171f62-2c24-5970-9e99-9fd14de4e398', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'Pode ser sexta-feira de manhã, sim.', now() - make_interval(mins => 240), NULL),
    ('e70cdfc7-3b89-5345-ae74-2f8b7b09f242', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'ca0edb3e-b5f6-580e-9267-0db35b037d0a', 'Combinado. Levo tudo o que preciso, só confirme a morada exata por favor.', now() - make_interval(mins => 238), NULL),
    ('88c4de89-1fed-5109-9c30-2ffee8d05cac', '3e57f9cf-896d-50bb-8421-b62d2a1977b3', 'af3ddf84-3a13-56ec-beca-cfd0589e44ff', 'Av. Rio de Janeiro 12, campainha "3º Dto".', now() - make_interval(mins => 235), NULL)
ON CONFLICT (id) DO NOTHING;

-- Marca de água de leitura por participante (V20): derivada das próprias
-- mensagens, não escrita à solta — o watermark de cada lado fica logo a
-- seguir ao "readAt" da mensagem mais recente da CONTRAPARTE que essa
-- pessoa já leu. Mensagens da contraparte mais recentes do que o watermark
-- contam como não lidas (unreadCount, calculado na leitura sobre
-- idx_message_conversation_id_sent_at — sem necessidade de índice novo,
-- ver V20). Isto é mais rigoroso do que o fixture original, cujo
-- unreadCount era um número solto sem relação com os readAt de cada
-- mensagem (cv-0002 tinha unreadCount:2 mas só uma mensagem do prestador
-- ficava por ler nos dados) — aqui o número que a API vier a mostrar é
-- sempre verdade, calculado, nunca inventado.
UPDATE conversation SET last_read_by_customer_at = now() - make_interval(mins => 140), last_read_by_provider_at = now() - make_interval(mins => 155) WHERE id = 'bfe1cd99-c14d-5064-ba4d-1770344c011d';
UPDATE conversation SET last_read_by_customer_at = now() - make_interval(mins => 390), last_read_by_provider_at = now() - make_interval(mins => 530) WHERE id = '3e57f9cf-896d-50bb-8421-b62d2a1977b3';

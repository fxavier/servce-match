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

-- Uma empresa por prestador (fixtures/providers.ts: companyName).
INSERT INTO company (id, name) VALUES
    ('d7185685-2d24-5e8d-b73e-ad061e2ed9d8', 'Canalizações Silva & Filhos, Lda.'),
    ('7c0e5138-a881-5f50-a2b9-0c036f0ce898', 'EletroPonto Unipessoal, Lda.'),
    ('83e3bbb7-df0c-57b3-be61-9db45e91443d', 'Jardins da Serra, Lda.'),
    ('ed5d4dd8-e42c-5e7b-9d9f-4e8959e807a8', 'Bettencourt Pinturas Unipessoal, Lda.'),
    ('4ff00adf-850b-57e8-a163-56b7d9279e1a', 'Marcenaria Nortenha, Lda.'),
    ('6f6dfd9a-7903-5b57-ac0e-0186a94d9530', 'ClimaTejo Climatização, Lda.'),
    ('6330cc48-aa9e-5faf-9f0b-eb53e964e991', 'MudaJá Transportes, Lda.'),
    ('c246d82e-62f8-5d87-a7f5-7191ae99f1dc', 'Remodela Cozinhas & Cia, Lda.'),
    ('c05664a5-7902-597e-a2be-edc2d1479f04', 'Banho & Obra, Lda.'),
    ('489b87f0-b2ae-5d9e-b827-6054070c0ebb', 'Serralharia Atlântico, Lda.'),
    ('0ab5cfd5-c8ad-54dd-ba33-31e1c7fc95cf', 'Limpa Já Serviços de Limpeza, Lda.'),
    ('8f2b8c6f-3365-51a0-9260-94f8baf25b5f', 'Limpezas Coimbra Norte, Lda.'),
    ('4d697656-211d-5a33-9d6f-1e6e4aa1df33', 'Faro Sul Elétrica, Lda.'),
    ('8d200848-c2bf-5bb8-a8bb-ee771b48ec70', 'Poda & Verde, Lda.'),
    ('01abe1d6-e41e-542c-898e-f1f30ac0d9b2', 'Douro Pinturas Exteriores, Lda.'),
    ('ab80c8bf-dcf9-50ca-bed9-7feec101cccf', 'Quadros & Cia, Lda.'),
    ('c10b4404-b888-5b8b-a397-edd108f01b65', 'Mudanças Express Norte, Lda.'),
    ('985d9fbf-c3d7-5ce2-b740-b687139f66d8', 'Carpintaria do Vale, Lda.'),
    ('b4485911-258c-5b9b-94d2-43037fa91a05', 'ArFresco Lda.'),
    ('bb08336f-6f18-51e8-815d-b02e2a403c4e', 'D24 Serviços Urgentes, Lda.'),
    ('3c324136-f10a-5537-b3cf-7d2f0292289d', 'Sanitários Braga Instalações, Lda.'),
    ('a63c72e6-438a-54ed-a7cf-382bb2b5f7e8', 'Escritórios Limpos Lda.'),
    ('f2f155b4-394d-5d6d-b7a9-83bd8ee04390', 'Obras & Remodelações Sul, Lda.'),
    ('2785bffa-126e-5f34-8932-1b8f6bb7b242', 'Portões Norte, Lda.')
ON CONFLICT (id) DO NOTHING;

-- approval_status='APPROVED' e visibility_state='VISIBLE' são escritas à mão
-- aqui de propósito (ADR-0013 D6, exceção explícita e só aqui ao ADR-0011
-- D9): sem isto o predicado de elegibilidade nega toda a gente e a SPA fica
-- vazia. visibility_state é lida ainda pelo predicado atual — ADR-0011 D1
-- marca esta coluna para remoção por uma migração própria de db-migrations;
-- este seed escreve-a defensivamente até essa migração aterrar, e deixa de
-- o fazer nessa altura (o INSERT deixa de listar a coluna, não há mais
-- nada a fazer aqui).
-- rating_avg/rating_count ficam a 0 aqui — recalculados por UPDATE agregado
-- no fim deste ficheiro de seed, nunca escritos à mão (v905), para nunca
-- divergirem do que as reviews realmente seed(adas) somam.
INSERT INTO provider_profile
    (id, user_id, company_id, headline, bio, verified, approval_status,
     visibility_state, location, created_at) VALUES
    ('98229d44-928e-59a0-bd3f-22686bfe713b', '1f88f1a6-8b95-558f-bcfd-0c825e2d11f9', 'd7185685-2d24-5e8d-b73e-ad061e2ed9d8', 'Fugas, desentupimentos e instalações — resposta em 2h', 'Trinta anos a resolver problemas de água em Lisboa. Equipa própria, sem subcontratação, orçamento sempre por escrito antes de começar.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 1200)),
    ('8b1f8146-c699-5c4a-97ea-9a9b24af4a4c', '89b8db04-2f94-5ff1-9a14-3869cd07173a', '7c0e5138-a881-5f50-a2b9-0c036f0ce898', 'Certificação elétrica e quadros — trabalho limpo, prazos cumpridos', 'Eletricista certificado com foco em segurança: nunca saímos de um serviço sem testar tudo três vezes.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 860)),
    ('7bbf7afd-6ac0-51d9-894e-a58aedd95d9b', 'a542393a-0442-57e6-8bdc-944fe36ba238', '83e3bbb7-df0c-57b3-be61-9db45e91443d', 'Manutenção de jardins e poda técnica em Sintra e Cascais', 'Paisagismo e manutenção com plano anual — cuidamos do seu jardim como se fosse nosso.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.3817, 38.8029), 4326)::geography, now() - make_interval(days => 640)),
    ('dda72dd5-12a0-523b-8b38-699ef2db5656', 'ca0edb3e-b5f6-580e-9267-0db35b037d0a', 'ed5d4dd8-e42c-5e7b-9d9f-4e8959e807a8', 'Pintura de interiores com acabamento premium', 'Especialistas em interiores — preparação de superfície cuidada, tintas de baixo COV.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 500)),
    ('8efbfdf7-429b-51f2-90a5-3b2ffba4b3fe', '7b9871a5-cb8d-57c3-9a7c-5d5066375e87', '4ff00adf-850b-57e8-a163-56b7d9279e1a', 'Mobiliário à medida — do projeto à instalação', 'Carpintaria tradicional com desenho 3D antes de cortar madeira. Sem surpresas no final.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.6291, 41.1579), 4326)::geography, now() - make_interval(days => 420)),
    ('93c197eb-bcfe-5e5f-9fb3-139b18f1c82d', 'fcfe72e5-e0f9-5875-b51f-433a69ebbb2e', '6f6dfd9a-7903-5b57-ac0e-0186a94d9530', 'Ar condicionado — venda, instalação e manutenção', 'Parceiros de marcas certificadas (Daikin, Mitsubishi). Contratos de manutenção anual disponíveis.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 980)),
    ('9f5253de-bc30-5fad-93b0-dcd1a96e09a7', 'a1f79912-9178-54d4-b50c-bf613ca381b0', '6330cc48-aa9e-5faf-9f0b-eb53e964e991', 'Mudanças residenciais e de escritório sem stress', 'Equipa de 4 pessoas, camião próprio, seguro de transporte incluído em todos os orçamentos.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 750)),
    ('088a40d0-f27c-5d5c-83f5-fee0f51ce2ab', 'ca0a2b76-8e7a-5764-a840-8ce68feb5fd7', 'c246d82e-62f8-5d87-a7f5-7191ae99f1dc', 'Remodelação de cozinhas chave-na-mão', 'Projeto, obra e acabamentos — uma única equipa responsável do início ao fim.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 310)),
    ('af63993b-5534-589f-ad17-7c1b45f30f17', '734e45a9-69e4-52a3-90ff-bbd631d79745', 'c05664a5-7902-597e-a2be-edc2d1479f04', 'Casas de banho remodeladas em 5 a 8 dias', 'Impermeabilização, canalização e acabamentos coordenados — sem andar a contratar três empresas diferentes.', false, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.6291, 41.1579), 4326)::geography, now() - make_interval(days => 190)),
    ('afefb80e-f721-5f89-ba4e-deaa1e6e629c', 'b1367637-c68b-5f80-bda2-be4452eb36f4', '489b87f0-b2ae-5d9e-b827-6054070c0ebb', 'Portões automáticos e serralharia em geral', 'Fabrico e instalação de gradeamentos, portões e automatismos com garantia de 2 anos.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.4265, 41.5454), 4326)::geography, now() - make_interval(days => 560)),
    ('b0e5c90a-09b6-580c-b0ac-af48cf427292', '26f2eaa0-0ab6-54ab-ba0d-2fe623948b3a', '0ab5cfd5-c8ad-54dd-ba33-31e1c7fc95cf', 'Limpeza doméstica regular ou pontual', 'Equipa fixa por cliente — a mesma pessoa de confiança em cada visita, produtos ecológicos disponíveis.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 1100)),
    ('8e7d8dbb-bef9-5ed1-87bf-08914bb9a04a', 'f6e336ef-0f31-5077-b50a-6073eaf34e2e', '8f2b8c6f-3365-51a0-9260-94f8baf25b5f', 'Limpeza pós-obra e de escritórios', 'Especialistas em remover pó e resíduos de obra — deixamos o espaço pronto a habitar.', false, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.4103, 40.2033), 4326)::geography, now() - make_interval(days => 220)),
    ('555087bc-3d9d-5e0e-82f9-0fa97af8f639', '58a57aa6-fb49-5f36-9ecd-28267ca1947c', '4d697656-211d-5a33-9d6f-1e6e4aa1df33', 'Iluminação decorativa e técnica', 'Do LED técnico ao candeeiro de design — projeto de iluminação incluído nos orçamentos maiores.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-7.9304, 37.0194), 4326)::geography, now() - make_interval(days => 400)),
    ('dfcbd8f2-967b-5b2e-950a-a74f8fe4b93b', '2038e1c9-755a-5cc2-965b-1245950f0b54', '8d200848-c2bf-5bb8-a8bb-ee771b48ec70', 'Poda de árvores certificada, com seguro', 'Trabalho em altura com todos os certificados de segurança em dia — árvores de grande porte é a nossa especialidade.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.4265, 41.5454), 4326)::geography, now() - make_interval(days => 300)),
    ('1eb29080-f40f-5873-8bc2-17bd720af77d', '6e2ce5c4-d7e3-52f5-8ee5-4258253d6da6', '01abe1d6-e41e-542c-898e-f1f30ac0d9b2', 'Fachadas e pintura exterior com andaime incluído', 'Tratamento anti-humidade antes de pintar — a maior parte dos problemas de fachada não são só estéticos.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.6118, 41.1239), 4326)::geography, now() - make_interval(days => 480)),
    ('1e2db731-88f5-562c-9879-7c0a54157765', '49539c2d-6d2e-5e66-98af-42b5e57dcb93', 'ab80c8bf-dcf9-50ca-bed9-7feec101cccf', 'Certificação e atualização de quadros elétricos', 'Regularização de instalações antigas para norma atual — inclui relatório técnico.', false, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 140)),
    ('de689f00-1087-54af-ade2-c8a398fe111a', '1eb6d386-af58-5df1-a746-1cc48dc937d6', 'c10b4404-b888-5b8b-a397-edd108f01b65', 'Mudanças residenciais rápidas no Porto', 'Orçamento em 24h, execução em janela de 3h — ideal para quem tem prazos apertados.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.6291, 41.1579), 4326)::geography, now() - make_interval(days => 610)),
    ('e8044662-777d-5cf7-b9c1-c615f0ea284f', '292c72d8-f07b-5a83-861d-a15610297129', '985d9fbf-c3d7-5ce2-b740-b687139f66d8', 'Roupeiros e mobiliário embutido à medida', 'Projeto gratuito com visualização 3D antes de fechar orçamento.', false, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.4103, 40.2033), 4326)::geography, now() - make_interval(days => 260)),
    ('7976f2bc-bed8-5dea-b792-b1dbfdab540d', 'c9b16ae9-dca0-5c85-b547-9318382e4cf9', 'b4485911-258c-5b9b-94d2-43037fa91a05', 'AVAC residencial e comercial', 'Manutenção preventiva com relatório fotográfico após cada visita.', false, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-7.9304, 37.0194), 4326)::geography, now() - make_interval(days => 95)),
    ('3c74e3b0-b169-51cd-a754-625cac018a42', '22228cf2-26bf-5324-8d0f-c8293cf3ecbd', 'bb08336f-6f18-51e8-815d-b02e2a403c4e', 'Desentupimentos e fugas — resposta 24 horas', 'Equipa de prevenção sempre de prontidão. Diagnóstico com câmara antes de qualquer obra.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 900)),
    ('91f2a2fd-1244-5750-9ef5-6d2efa32dafd', 'b1ff50e7-6152-587e-8075-fc582274be51', '3c324136-f10a-5537-b3cf-7d2f0292289d', 'Instalação de sanitários e canalização de casas de banho', 'Trabalho coordenado com o azulejador para não parar a obra a meio.', false, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.4265, 41.5454), 4326)::geography, now() - make_interval(days => 75)),
    ('a4c82319-6fa8-526c-bc0e-ac074c04e535', '8d1e0005-8386-5a8d-bb63-896eef37f379', 'a63c72e6-438a-54ed-a7cf-382bb2b5f7e8', 'Limpeza de escritórios com contrato mensal', 'Serviço fora de horas para não interromper a operação do cliente.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-9.1393, 38.7223), 4326)::geography, now() - make_interval(days => 700)),
    ('80153a4a-8fcb-5f8a-a637-69c9ee47e340', '119e886d-7761-51c1-b5ce-d9c2569d1cd1', 'f2f155b4-394d-5d6d-b7a9-83bd8ee04390', 'Remodelações completas de apartamentos', 'Gestão de obra chave-na-mão, com acompanhamento semanal ao cliente.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-7.9304, 37.0194), 4326)::geography, now() - make_interval(days => 380)),
    ('87b46f7e-4d94-5976-b22c-e777763a0a85', '0045430b-679b-5430-b3fc-e13e08c78f09', '2785bffa-126e-5f34-8932-1b8f6bb7b242', 'Automatização de portões existentes', 'Instalamos motores em portões já existentes — não é preciso trocar o portão todo.', true, 'APPROVED', 'VISIBLE', ST_SetSRID(ST_MakePoint(-8.6118, 41.1239), 4326)::geography, now() - make_interval(days => 230))
ON CONFLICT (id) DO NOTHING;

-- Categorias trabalhadas por prestador (fixtures/providers.ts: categoryIds), resolvidas por slug contra V15.
INSERT INTO provider_category (provider_id, category_id)
SELECT v.provider_id, c.id FROM (VALUES
    ('98229d44-928e-59a0-bd3f-22686bfe713b'::uuid, 'canalizacao'),
    ('98229d44-928e-59a0-bd3f-22686bfe713b'::uuid, 'desentupimentos'),
    ('98229d44-928e-59a0-bd3f-22686bfe713b'::uuid, 'reparacao-fugas'),
    ('8b1f8146-c699-5c4a-97ea-9a9b24af4a4c'::uuid, 'eletricidade'),
    ('8b1f8146-c699-5c4a-97ea-9a9b24af4a4c'::uuid, 'instalacoes-electricas'),
    ('8b1f8146-c699-5c4a-97ea-9a9b24af4a4c'::uuid, 'quadros-electricos'),
    ('7bbf7afd-6ac0-51d9-894e-a58aedd95d9b'::uuid, 'jardinagem'),
    ('7bbf7afd-6ac0-51d9-894e-a58aedd95d9b'::uuid, 'manutencao-jardins'),
    ('7bbf7afd-6ac0-51d9-894e-a58aedd95d9b'::uuid, 'poda-arvores'),
    ('dda72dd5-12a0-523b-8b38-699ef2db5656'::uuid, 'pinturas'),
    ('dda72dd5-12a0-523b-8b38-699ef2db5656'::uuid, 'pintura-interiores'),
    ('8efbfdf7-429b-51f2-90a5-3b2ffba4b3fe'::uuid, 'carpintaria'),
    ('8efbfdf7-429b-51f2-90a5-3b2ffba4b3fe'::uuid, 'mobiliario-medida'),
    ('93c197eb-bcfe-5e5f-9fb3-139b18f1c82d'::uuid, 'climatizacao'),
    ('93c197eb-bcfe-5e5f-9fb3-139b18f1c82d'::uuid, 'instalacao-ar-condicionado'),
    ('93c197eb-bcfe-5e5f-9fb3-139b18f1c82d'::uuid, 'manutencao-avac'),
    ('9f5253de-bc30-5fad-93b0-dcd1a96e09a7'::uuid, 'mudancas'),
    ('9f5253de-bc30-5fad-93b0-dcd1a96e09a7'::uuid, 'mudancas-residenciais'),
    ('9f5253de-bc30-5fad-93b0-dcd1a96e09a7'::uuid, 'mudancas-escritorio'),
    ('088a40d0-f27c-5d5c-83f5-fee0f51ce2ab'::uuid, 'obras-remodelacoes'),
    ('088a40d0-f27c-5d5c-83f5-fee0f51ce2ab'::uuid, 'remodelacao-cozinhas'),
    ('af63993b-5534-589f-ad17-7c1b45f30f17'::uuid, 'obras-remodelacoes'),
    ('af63993b-5534-589f-ad17-7c1b45f30f17'::uuid, 'remodelacao-banheiros'),
    ('afefb80e-f721-5f89-ba4e-deaa1e6e629c'::uuid, 'serralharia'),
    ('afefb80e-f721-5f89-ba4e-deaa1e6e629c'::uuid, 'portoes-automaticos'),
    ('b0e5c90a-09b6-580c-b0ac-af48cf427292'::uuid, 'limpezas'),
    ('b0e5c90a-09b6-580c-b0ac-af48cf427292'::uuid, 'limpeza-domestica'),
    ('8e7d8dbb-bef9-5ed1-87bf-08914bb9a04a'::uuid, 'limpezas'),
    ('8e7d8dbb-bef9-5ed1-87bf-08914bb9a04a'::uuid, 'limpeza-pos-obra'),
    ('8e7d8dbb-bef9-5ed1-87bf-08914bb9a04a'::uuid, 'limpeza-escritorios'),
    ('555087bc-3d9d-5e0e-82f9-0fa97af8f639'::uuid, 'eletricidade'),
    ('555087bc-3d9d-5e0e-82f9-0fa97af8f639'::uuid, 'iluminacao'),
    ('dfcbd8f2-967b-5b2e-950a-a74f8fe4b93b'::uuid, 'jardinagem'),
    ('dfcbd8f2-967b-5b2e-950a-a74f8fe4b93b'::uuid, 'poda-arvores'),
    ('1eb29080-f40f-5873-8bc2-17bd720af77d'::uuid, 'pinturas'),
    ('1eb29080-f40f-5873-8bc2-17bd720af77d'::uuid, 'pintura-exteriores'),
    ('1e2db731-88f5-562c-9879-7c0a54157765'::uuid, 'eletricidade'),
    ('1e2db731-88f5-562c-9879-7c0a54157765'::uuid, 'quadros-electricos'),
    ('de689f00-1087-54af-ade2-c8a398fe111a'::uuid, 'mudancas'),
    ('de689f00-1087-54af-ade2-c8a398fe111a'::uuid, 'mudancas-residenciais'),
    ('e8044662-777d-5cf7-b9c1-c615f0ea284f'::uuid, 'carpintaria'),
    ('e8044662-777d-5cf7-b9c1-c615f0ea284f'::uuid, 'mobiliario-medida'),
    ('7976f2bc-bed8-5dea-b792-b1dbfdab540d'::uuid, 'climatizacao'),
    ('7976f2bc-bed8-5dea-b792-b1dbfdab540d'::uuid, 'manutencao-avac'),
    ('3c74e3b0-b169-51cd-a754-625cac018a42'::uuid, 'canalizacao'),
    ('3c74e3b0-b169-51cd-a754-625cac018a42'::uuid, 'desentupimentos'),
    ('91f2a2fd-1244-5750-9ef5-6d2efa32dafd'::uuid, 'canalizacao'),
    ('91f2a2fd-1244-5750-9ef5-6d2efa32dafd'::uuid, 'instalacao-sanitarios'),
    ('a4c82319-6fa8-526c-bc0e-ac074c04e535'::uuid, 'limpezas'),
    ('a4c82319-6fa8-526c-bc0e-ac074c04e535'::uuid, 'limpeza-escritorios'),
    ('80153a4a-8fcb-5f8a-a637-69c9ee47e340'::uuid, 'obras-remodelacoes'),
    ('80153a4a-8fcb-5f8a-a637-69c9ee47e340'::uuid, 'remodelacao-cozinhas'),
    ('80153a4a-8fcb-5f8a-a637-69c9ee47e340'::uuid, 'remodelacao-banheiros'),
    ('87b46f7e-4d94-5976-b22c-e777763a0a85'::uuid, 'serralharia'),
    ('87b46f7e-4d94-5976-b22c-e777763a0a85'::uuid, 'portoes-automaticos')
) AS v(provider_id, category_slug)
JOIN category c ON c.slug = v.category_slug
ON CONFLICT DO NOTHING;

-- Cobertura por região administrativa (modo ADMIN_REGION) — reflete
-- diretamente as "zonas" do fixture (regionCodes por prestador). RADIUS não
-- é usado aqui: os fixtures não têm um centro+raio por prestador, só uma
-- lista de concelhos servidos, que é exatamente a leitura de ADMIN_REGION
-- (CHECK chk_provider_service_area_mode_coherence exige region_code e
-- center/radius_m nulos neste modo).
INSERT INTO provider_service_area (id, provider_id, mode, region_code) VALUES
    ('8c1d7f4a-0302-5f66-968a-af89e7919d11', '98229d44-928e-59a0-bd3f-22686bfe713b', 'ADMIN_REGION', 'PT-LIS'),
    ('59df8192-eefc-5f51-95d8-ab5214b63a06', '98229d44-928e-59a0-bd3f-22686bfe713b', 'ADMIN_REGION', 'PT-OEI'),
    ('bcce2403-fa3f-5e93-a01e-dbf481707e34', '8b1f8146-c699-5c4a-97ea-9a9b24af4a4c', 'ADMIN_REGION', 'PT-LIS'),
    ('a342b183-dc72-58b4-8b9d-471f49983562', '7bbf7afd-6ac0-51d9-894e-a58aedd95d9b', 'ADMIN_REGION', 'PT-SNT'),
    ('022f86b7-9e96-57d3-ac78-5dd3a7e85b86', '7bbf7afd-6ac0-51d9-894e-a58aedd95d9b', 'ADMIN_REGION', 'PT-CSC'),
    ('5a797b7d-c91b-5bae-841d-7b333df7b26e', 'dda72dd5-12a0-523b-8b38-699ef2db5656', 'ADMIN_REGION', 'PT-LIS'),
    ('77ee9b2a-5d39-5062-bfdc-460e42bf1253', 'dda72dd5-12a0-523b-8b38-699ef2db5656', 'ADMIN_REGION', 'PT-ALM'),
    ('ab57e94b-eda9-5d11-a22b-c68a9a64aaab', '8efbfdf7-429b-51f2-90a5-3b2ffba4b3fe', 'ADMIN_REGION', 'PT-PRT'),
    ('051bce87-4573-546b-a380-58ae25e6bf17', '8efbfdf7-429b-51f2-90a5-3b2ffba4b3fe', 'ADMIN_REGION', 'PT-VNG'),
    ('37a7469e-c3c6-53b2-bc27-d2655102dc59', '93c197eb-bcfe-5e5f-9fb3-139b18f1c82d', 'ADMIN_REGION', 'PT-LIS'),
    ('a7898a3d-8dd8-5a16-a677-bdd0be3efaae', '93c197eb-bcfe-5e5f-9fb3-139b18f1c82d', 'ADMIN_REGION', 'PT-LOU'),
    ('55de8273-5251-59f2-b582-1c86f05df8fb', '9f5253de-bc30-5fad-93b0-dcd1a96e09a7', 'ADMIN_REGION', 'PT-LIS'),
    ('8cf3cbcc-858d-5f69-866d-a7363cba1cae', '9f5253de-bc30-5fad-93b0-dcd1a96e09a7', 'ADMIN_REGION', 'PT-OEI'),
    ('0efcbf11-85d7-5c49-9cd3-9c2e2bfee6a8', '9f5253de-bc30-5fad-93b0-dcd1a96e09a7', 'ADMIN_REGION', 'PT-ALM'),
    ('780e64f5-319f-5762-b4c4-e377af9fb23e', '088a40d0-f27c-5d5c-83f5-fee0f51ce2ab', 'ADMIN_REGION', 'PT-LIS'),
    ('670aedae-3df3-5da7-8386-775a6d74caf2', 'af63993b-5534-589f-ad17-7c1b45f30f17', 'ADMIN_REGION', 'PT-PRT'),
    ('0471b449-ebed-56c3-8b62-90350e1eec39', 'af63993b-5534-589f-ad17-7c1b45f30f17', 'ADMIN_REGION', 'PT-MTS'),
    ('2ede0930-2564-51bb-8f42-40ba0c9dcef3', 'afefb80e-f721-5f89-ba4e-deaa1e6e629c', 'ADMIN_REGION', 'PT-BRG'),
    ('9c2ce622-37d3-544b-94a5-5b01504e9046', 'b0e5c90a-09b6-580c-b0ac-af48cf427292', 'ADMIN_REGION', 'PT-LIS'),
    ('d103ac11-8079-57bc-b042-8004aaf6724d', 'b0e5c90a-09b6-580c-b0ac-af48cf427292', 'ADMIN_REGION', 'PT-OEI'),
    ('63470513-b6b3-59fe-8309-cbc7615e15ea', 'b0e5c90a-09b6-580c-b0ac-af48cf427292', 'ADMIN_REGION', 'PT-CSC'),
    ('ddd28f79-82bd-5088-bffd-c3b30acbe835', '8e7d8dbb-bef9-5ed1-87bf-08914bb9a04a', 'ADMIN_REGION', 'PT-CBR'),
    ('b9c36984-1ed9-5010-944c-65f5e7e22d5d', '555087bc-3d9d-5e0e-82f9-0fa97af8f639', 'ADMIN_REGION', 'PT-FAR'),
    ('885d3d13-1dd0-5ae7-b989-7528a6b6d7ca', 'dfcbd8f2-967b-5b2e-950a-a74f8fe4b93b', 'ADMIN_REGION', 'PT-BRG'),
    ('c8b91ac3-9b57-550a-b928-7c8ec1ec2655', 'dfcbd8f2-967b-5b2e-950a-a74f8fe4b93b', 'ADMIN_REGION', 'PT-PRT'),
    ('bb6f502c-f2e3-5aa1-af89-9ae241ec1a49', '1eb29080-f40f-5873-8bc2-17bd720af77d', 'ADMIN_REGION', 'PT-VNG'),
    ('159a598e-3f32-53e0-acab-67641d361eec', '1eb29080-f40f-5873-8bc2-17bd720af77d', 'ADMIN_REGION', 'PT-PRT'),
    ('eb58a4b4-d224-55be-903a-74fdb9a67d89', '1e2db731-88f5-562c-9879-7c0a54157765', 'ADMIN_REGION', 'PT-LIS'),
    ('3126f6c7-9bc8-5bdb-9f00-7af14b628843', '1e2db731-88f5-562c-9879-7c0a54157765', 'ADMIN_REGION', 'PT-LOU'),
    ('67f7cc0f-bdbd-5763-8ca4-cba3c02018a7', 'de689f00-1087-54af-ade2-c8a398fe111a', 'ADMIN_REGION', 'PT-PRT'),
    ('27784b78-fc20-5430-b6c9-965ef7074777', 'de689f00-1087-54af-ade2-c8a398fe111a', 'ADMIN_REGION', 'PT-MTS'),
    ('cbc127a4-6ae7-5d4a-a4ef-c4538e8cd9cb', 'de689f00-1087-54af-ade2-c8a398fe111a', 'ADMIN_REGION', 'PT-VNG'),
    ('ab09e934-48c1-5623-bc71-ae7939357b69', 'e8044662-777d-5cf7-b9c1-c615f0ea284f', 'ADMIN_REGION', 'PT-CBR'),
    ('87e6ca1b-e459-5295-81c6-fa59382dca56', 'e8044662-777d-5cf7-b9c1-c615f0ea284f', 'ADMIN_REGION', 'PT-LIS'),
    ('19ab6a83-094b-5204-9987-e5e5792cf31f', '7976f2bc-bed8-5dea-b792-b1dbfdab540d', 'ADMIN_REGION', 'PT-FAR'),
    ('e327211d-424d-526c-985c-49624bd2ca6a', '7976f2bc-bed8-5dea-b792-b1dbfdab540d', 'ADMIN_REGION', 'PT-CSC'),
    ('e66e2d38-156d-5db1-bda0-43df30279094', '3c74e3b0-b169-51cd-a754-625cac018a42', 'ADMIN_REGION', 'PT-LIS'),
    ('e2431951-8dfe-5aed-8e69-cdc1a5ffab55', '3c74e3b0-b169-51cd-a754-625cac018a42', 'ADMIN_REGION', 'PT-ALM'),
    ('225d1e20-92f3-558b-8a1e-51532f98f649', '91f2a2fd-1244-5750-9ef5-6d2efa32dafd', 'ADMIN_REGION', 'PT-BRG'),
    ('f95e0f02-ff6b-5a3e-a1ba-d3ea9e69a9ac', 'a4c82319-6fa8-526c-bc0e-ac074c04e535', 'ADMIN_REGION', 'PT-LIS'),
    ('3ade77ea-bae1-581a-b437-47a8acde122d', 'a4c82319-6fa8-526c-bc0e-ac074c04e535', 'ADMIN_REGION', 'PT-OEI'),
    ('efb2820f-60ec-51bf-991f-dd5e4dfc48a1', '80153a4a-8fcb-5f8a-a637-69c9ee47e340', 'ADMIN_REGION', 'PT-FAR'),
    ('20628327-cbbd-552f-92ff-d04b64338b69', '87b46f7e-4d94-5976-b22c-e777763a0a85', 'ADMIN_REGION', 'PT-VNG'),
    ('6f6347f8-9a57-5c15-a788-6531ca33a4ff', '87b46f7e-4d94-5976-b22c-e777763a0a85', 'ADMIN_REGION', 'PT-MTS')
ON CONFLICT (id) DO NOTHING;

-- Subscrição ativa para todos os prestadores exceto p-0009 (Rita Nogueira /
-- provider.trial@servimatch.pt) — esse é, de propósito, o prestador "sem
-- subscrição" da demonstração (infra/README.md, features/auth/demoProfiles.ts).
-- current_period_end = now() + 10 anos (ADR-0013 D6): o seed é idempotente
-- (ON CONFLICT DO NOTHING), por isso uma segunda execução não atualiza a
-- data — um período curto tornaria a base de desenvolvimento "expirada" em
-- silêncio ao fim de poucos dias.
INSERT INTO subscription
    (id, provider_id, plan_id, status, gateway, gateway_customer_id,
     gateway_subscription_id, current_period_start, current_period_end) VALUES
    ('ff93f459-7551-59d7-8c9a-e00b4e5c4769', '98229d44-928e-59a0-bd3f-22686bfe713b', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0001', 'seed-sub-p-0001', now() - interval '30 days', now() + interval '10 years'),
    ('7432d6f7-e919-5eee-bf22-c0fe06e100ee', '8b1f8146-c699-5c4a-97ea-9a9b24af4a4c', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0002', 'seed-sub-p-0002', now() - interval '30 days', now() + interval '10 years'),
    ('609e2ed0-a284-5671-9263-2f1f3118019f', '7bbf7afd-6ac0-51d9-894e-a58aedd95d9b', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0003', 'seed-sub-p-0003', now() - interval '30 days', now() + interval '10 years'),
    ('cb53af12-fa40-5846-9d48-e5082bcd62e8', 'dda72dd5-12a0-523b-8b38-699ef2db5656', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0004', 'seed-sub-p-0004', now() - interval '30 days', now() + interval '10 years'),
    ('ea7f920a-2fcc-51af-9d12-49f31b6fe540', '8efbfdf7-429b-51f2-90a5-3b2ffba4b3fe', (SELECT id FROM subscription_plan WHERE code = 'premium'), 'ACTIVE', 'stripe', 'seed-cus-p-0005', 'seed-sub-p-0005', now() - interval '30 days', now() + interval '10 years'),
    ('ecafc56a-09f5-5a4a-a169-963310c0b2e1', '93c197eb-bcfe-5e5f-9fb3-139b18f1c82d', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0006', 'seed-sub-p-0006', now() - interval '30 days', now() + interval '10 years'),
    ('2e390038-f9df-530f-a470-da2ea7b38ce6', '9f5253de-bc30-5fad-93b0-dcd1a96e09a7', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0007', 'seed-sub-p-0007', now() - interval '30 days', now() + interval '10 years'),
    ('0767caf1-decb-5180-897f-c4caefca6628', '088a40d0-f27c-5d5c-83f5-fee0f51ce2ab', (SELECT id FROM subscription_plan WHERE code = 'premium'), 'ACTIVE', 'stripe', 'seed-cus-p-0008', 'seed-sub-p-0008', now() - interval '30 days', now() + interval '10 years'),
    ('b7111b4a-6302-5474-a0bb-bead28e0acee', 'afefb80e-f721-5f89-ba4e-deaa1e6e629c', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0010', 'seed-sub-p-0010', now() - interval '30 days', now() + interval '10 years'),
    ('ec60e620-167c-59b1-a741-69e3c25bb584', 'b0e5c90a-09b6-580c-b0ac-af48cf427292', (SELECT id FROM subscription_plan WHERE code = 'premium'), 'ACTIVE', 'stripe', 'seed-cus-p-0011', 'seed-sub-p-0011', now() - interval '30 days', now() + interval '10 years'),
    ('c49911ff-52f8-5d3a-93f2-2fba4db286c3', '8e7d8dbb-bef9-5ed1-87bf-08914bb9a04a', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0012', 'seed-sub-p-0012', now() - interval '30 days', now() + interval '10 years'),
    ('a26881bb-5f97-5def-869c-629cd4e7e38f', '555087bc-3d9d-5e0e-82f9-0fa97af8f639', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0013', 'seed-sub-p-0013', now() - interval '30 days', now() + interval '10 years'),
    ('d4538544-a9b8-5d3b-b231-b970b2946ade', 'dfcbd8f2-967b-5b2e-950a-a74f8fe4b93b', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0014', 'seed-sub-p-0014', now() - interval '30 days', now() + interval '10 years'),
    ('2cbb6157-7839-5f35-abc6-3009e86c6500', '1eb29080-f40f-5873-8bc2-17bd720af77d', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0015', 'seed-sub-p-0015', now() - interval '30 days', now() + interval '10 years'),
    ('5283f055-d5fd-5d3e-8760-30049ce63bc1', '1e2db731-88f5-562c-9879-7c0a54157765', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0016', 'seed-sub-p-0016', now() - interval '30 days', now() + interval '10 years'),
    ('7984b40a-ecea-5dac-b8aa-75d87642b371', 'de689f00-1087-54af-ade2-c8a398fe111a', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0017', 'seed-sub-p-0017', now() - interval '30 days', now() + interval '10 years'),
    ('d44d4329-732f-5ae5-afe2-096a7b90e29f', 'e8044662-777d-5cf7-b9c1-c615f0ea284f', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0018', 'seed-sub-p-0018', now() - interval '30 days', now() + interval '10 years'),
    ('df84e5b4-7e45-5149-aec1-cfb717f5805a', '7976f2bc-bed8-5dea-b792-b1dbfdab540d', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0019', 'seed-sub-p-0019', now() - interval '30 days', now() + interval '10 years'),
    ('9e30e1c9-bb55-5048-b8e7-98739306d2db', '3c74e3b0-b169-51cd-a754-625cac018a42', (SELECT id FROM subscription_plan WHERE code = 'premium'), 'ACTIVE', 'stripe', 'seed-cus-p-0020', 'seed-sub-p-0020', now() - interval '30 days', now() + interval '10 years'),
    ('34a284e5-038d-52e4-a0a7-a3b16fe73dd7', '91f2a2fd-1244-5750-9ef5-6d2efa32dafd', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0021', 'seed-sub-p-0021', now() - interval '30 days', now() + interval '10 years'),
    ('6bae6066-be4d-5ab9-bae8-112c864d7725', 'a4c82319-6fa8-526c-bc0e-ac074c04e535', (SELECT id FROM subscription_plan WHERE code = 'professional'), 'ACTIVE', 'stripe', 'seed-cus-p-0022', 'seed-sub-p-0022', now() - interval '30 days', now() + interval '10 years'),
    ('ca4083b1-eb27-50d5-9bfc-bc1f230403c4', '80153a4a-8fcb-5f8a-a637-69c9ee47e340', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0023', 'seed-sub-p-0023', now() - interval '30 days', now() + interval '10 years'),
    ('fd7b1706-2a35-5673-9257-3aec5662ed6f', '87b46f7e-4d94-5976-b22c-e777763a0a85', (SELECT id FROM subscription_plan WHERE code = 'starter'), 'ACTIVE', 'stripe', 'seed-cus-p-0024', 'seed-sub-p-0024', now() - interval '30 days', now() + interval '10 years')
ON CONFLICT (id) DO NOTHING;

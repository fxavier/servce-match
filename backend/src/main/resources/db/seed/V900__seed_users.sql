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

-- users.keycloak_sub é a chave que liga a linha local ao Keycloak
-- (V2, ADR-0002). Os quatro utilizadores de demonstração usam o keycloak_sub
-- EXATO documentado em infra/README.md / infra/keycloak/realm-servimatch.json
-- — sem isto o login funciona mas o provisionamento JIT
-- (UsersApi.ensureProvisioned, ADR-0012 D9) cria uma SEGUNDA linha "users"
-- com o sub verdadeiro, e todos os dados deste seed ficam pendurados numa
-- linha órfã que ninguém alcança (ADR-0013 D4). Este é o ponto de falha mais
-- provável de todo o seed: se um dia o login destes utilizadores devolver um
-- perfil vazio, é aqui que se confirma primeiro.
--
-- Os restantes utilizadores (prestadores sem login de demonstração e
-- clientes de apoio aos pedidos/reviews) têm keycloak_sub SINTÉTICO com
-- prefixo 'seed:' — nunca vai bater com um "sub" real emitido pelo Keycloak
-- (que é sempre um UUID), por isso não pode colidir nem ser confundido com
-- um utilizador que consiga fazer login.
INSERT INTO users (id, keycloak_sub, email, display_name, status) VALUES
    ('af3ddf84-3a13-56ec-beca-cfd0589e44ff', '07799610-a03f-40aa-a713-948d9d1b929d', 'customer.test@servimatch.pt', 'Mariana Costa', 'ACTIVE'),
    ('1f88f1a6-8b95-558f-bcfd-0c825e2d11f9', 'd3d6d736-c434-41eb-b3dc-2201a3f69c4a', 'provider.test@servimatch.pt', 'Carlos Silva', 'ACTIVE'),
    ('734e45a9-69e4-52a3-90ff-bbd631d79745', '980712fa-004b-487e-9946-ddc65cadec49', 'provider.trial@servimatch.pt', 'Rita Nogueira', 'ACTIVE'),
    ('547f86bc-7e87-5cd9-b4de-db862b02c0d9', '35295b6c-a788-4f2b-bc22-8edc292aaf81', 'admin.test@servimatch.pt', 'Administração ServiMatch', 'ACTIVE'),
    ('fd56b08b-89e8-57f3-a42d-bdedea637cb0', 'seed:customer:c-0002', 'c-0002@seed.servimatch.invalid', 'Miguel Ferreira', 'ACTIVE'),
    ('a17393e9-4a87-5718-8bfc-d5878a5f8917', 'seed:customer:c-0003', 'c-0003@seed.servimatch.invalid', 'Sofia Almeida', 'ACTIVE'),
    ('4bd03a3d-d77e-5587-9bd7-21de371f59a0', 'seed:customer:c-0004', 'c-0004@seed.servimatch.invalid', 'João Henriques', 'ACTIVE'),
    ('d570f2e8-068d-5d0a-9dd5-682343cf922f', 'seed:customer:c-0005', 'c-0005@seed.servimatch.invalid', 'Beatriz Marques', 'ACTIVE'),
    ('89b8db04-2f94-5ff1-9a14-3869cd07173a', 'seed:provider:p-0002', 'p-0002@seed.servimatch.invalid', 'Duarte Antunes', 'ACTIVE'),
    ('a542393a-0442-57e6-8bdc-944fe36ba238', 'seed:provider:p-0003', 'p-0003@seed.servimatch.invalid', 'Marta Pinheiro', 'ACTIVE'),
    ('ca0edb3e-b5f6-580e-9267-0db35b037d0a', 'seed:provider:p-0004', 'p-0004@seed.servimatch.invalid', 'Vasco Coelho', 'ACTIVE'),
    ('7b9871a5-cb8d-57c3-9a7c-5d5066375e87', 'seed:provider:p-0005', 'p-0005@seed.servimatch.invalid', 'Helena Guerreiro', 'ACTIVE'),
    ('fcfe72e5-e0f9-5875-b51f-433a69ebbb2e', 'seed:provider:p-0006', 'p-0006@seed.servimatch.invalid', 'Rodrigo Matias', 'ACTIVE'),
    ('a1f79912-9178-54d4-b50c-bf613ca381b0', 'seed:provider:p-0007', 'p-0007@seed.servimatch.invalid', 'Isabel Neves', 'ACTIVE'),
    ('ca0a2b76-8e7a-5764-a840-8ce68feb5fd7', 'seed:provider:p-0008', 'p-0008@seed.servimatch.invalid', 'Fernando Azevedo', 'ACTIVE'),
    ('b1367637-c68b-5f80-bda2-be4452eb36f4', 'seed:provider:p-0010', 'p-0010@seed.servimatch.invalid', 'Cristina Rocha', 'ACTIVE'),
    ('26f2eaa0-0ab6-54ab-ba0d-2fe623948b3a', 'seed:provider:p-0011', 'p-0011@seed.servimatch.invalid', 'Luís Barbosa', 'ACTIVE'),
    ('f6e336ef-0f31-5077-b50a-6073eaf34e2e', 'seed:provider:p-0012', 'p-0012@seed.servimatch.invalid', 'Ana Paula Salgado', 'ACTIVE'),
    ('58a57aa6-fb49-5f36-9ecd-28267ca1947c', 'seed:provider:p-0013', 'p-0013@seed.servimatch.invalid', 'Manuel Duarte', 'ACTIVE'),
    ('2038e1c9-755a-5cc2-965b-1245950f0b54', 'seed:provider:p-0014', 'p-0014@seed.servimatch.invalid', 'Joana Esteves', 'ACTIVE'),
    ('6e2ce5c4-d7e3-52f5-8ee5-4258253d6da6', 'seed:provider:p-0015', 'p-0015@seed.servimatch.invalid', 'Alexandre Moreira', 'ACTIVE'),
    ('49539c2d-6d2e-5e66-98af-42b5e57dcb93', 'seed:provider:p-0016', 'p-0016@seed.servimatch.invalid', 'Gabriela Xavier', 'ACTIVE'),
    ('1eb6d386-af58-5df1-a746-1cc48dc937d6', 'seed:provider:p-0017', 'p-0017@seed.servimatch.invalid', 'Óscar Freitas', 'ACTIVE'),
    ('292c72d8-f07b-5a83-861d-a15610297129', 'seed:provider:p-0018', 'p-0018@seed.servimatch.invalid', 'Vera Sampaio', 'ACTIVE'),
    ('c9b16ae9-dca0-5c85-b547-9318382e4cf9', 'seed:provider:p-0019', 'p-0019@seed.servimatch.invalid', 'Ivo Peixoto', 'ACTIVE'),
    ('22228cf2-26bf-5324-8d0f-c8293cf3ecbd', 'seed:provider:p-0020', 'p-0020@seed.servimatch.invalid', 'Raquel Cunha', 'ACTIVE'),
    ('b1ff50e7-6152-587e-8075-fc582274be51', 'seed:provider:p-0021', 'p-0021@seed.servimatch.invalid', 'Tomás Batista', 'ACTIVE'),
    ('8d1e0005-8386-5a8d-bb63-896eef37f379', 'seed:provider:p-0022', 'p-0022@seed.servimatch.invalid', 'Lídia Franco', 'ACTIVE'),
    ('119e886d-7761-51c1-b5ce-d9c2569d1cd1', 'seed:provider:p-0023', 'p-0023@seed.servimatch.invalid', 'Diogo Amorim', 'ACTIVE'),
    ('0045430b-679b-5430-b3fc-e13e08c78f09', 'seed:provider:p-0024', 'p-0024@seed.servimatch.invalid', 'Célia Brandão', 'ACTIVE'),
    ('645a096c-2bf9-5681-b783-e237373e4e42', 'seed:review-author:rv-0001', 'rv-0001@seed.servimatch.invalid', 'Mariana Costa', 'ACTIVE'),
    ('6ed52f6a-1e7a-5ab4-b6a1-3db95609d235', 'seed:review-author:rv-0002', 'rv-0002@seed.servimatch.invalid', 'João Pereira', 'ACTIVE'),
    ('4d4f0844-e33b-55b6-b9b9-036fe9b3d870', 'seed:review-author:rv-0003', 'rv-0003@seed.servimatch.invalid', 'Beatriz Santos', 'ACTIVE'),
    ('d3f516fa-c978-5c67-879b-9d241fc0afb5', 'seed:review-author:rv-0004', 'rv-0004@seed.servimatch.invalid', 'Ricardo Almeida', 'ACTIVE'),
    ('bf2bbf27-fe18-54ff-ac01-f7fb066d2822', 'seed:review-author:rv-0005', 'rv-0005@seed.servimatch.invalid', 'Inês Ferreira', 'ACTIVE'),
    ('32a5d77c-90fd-5bdd-a6d7-1e2fe5051f80', 'seed:review-author:rv-0006', 'rv-0006@seed.servimatch.invalid', 'Tiago Rodrigues', 'ACTIVE'),
    ('33bfec3f-cec5-5380-b6ce-20e4d760b1e2', 'seed:review-author:rv-0007', 'rv-0007@seed.servimatch.invalid', 'Carla Sousa', 'ACTIVE'),
    ('03eb7835-7f54-5af9-90c9-07d9cd47e860', 'seed:review-author:rv-0008', 'rv-0008@seed.servimatch.invalid', 'Pedro Martins', 'ACTIVE'),
    ('7ac9bd75-f280-517e-8135-84ec4e9ca690', 'seed:review-author:rv-0009', 'rv-0009@seed.servimatch.invalid', 'Sofia Carvalho', 'ACTIVE'),
    ('c0a06186-7469-5193-bb56-3481ddbddc8b', 'seed:review-author:rv-0010', 'rv-0010@seed.servimatch.invalid', 'André Gonçalves', 'ACTIVE'),
    ('ea498945-b00d-54c0-8454-04e82ea98669', 'seed:review-author:rv-0011', 'rv-0011@seed.servimatch.invalid', 'Cátia Lopes', 'ACTIVE'),
    ('6d0ab47c-496b-57f2-bf68-94086ff9142c', 'seed:review-author:rv-0012', 'rv-0012@seed.servimatch.invalid', 'Nuno Ribeiro', 'ACTIVE'),
    ('92046f4d-d6af-5525-9195-d6c334566e3c', 'seed:review-author:rv-0013', 'rv-0013@seed.servimatch.invalid', 'Patrícia Nunes', 'ACTIVE'),
    ('db11df67-c9cf-56bd-9846-f3f3b19ebbfd', 'seed:review-author:rv-0014', 'rv-0014@seed.servimatch.invalid', 'Miguel Fernandes', 'ACTIVE'),
    ('962c02b1-fd6c-5d9b-bbad-28c19c8704d4', 'seed:review-author:rv-0015', 'rv-0015@seed.servimatch.invalid', 'Ana Correia', 'ACTIVE'),
    ('05ccc75a-b356-5550-8cc2-c6b98009c4cd', 'seed:review-author:rv-0016', 'rv-0016@seed.servimatch.invalid', 'Rui Teixeira', 'ACTIVE'),
    ('96bb029b-8cc0-5d6a-ba46-2655f6e5df14', 'seed:review-author:rv-0017', 'rv-0017@seed.servimatch.invalid', 'Filipa Marques', 'ACTIVE'),
    ('b86e75bf-620e-5200-9b51-4becc8ae1d93', 'seed:review-author:rv-0018', 'rv-0018@seed.servimatch.invalid', 'Bruno Cardoso', 'ACTIVE'),
    ('3502b3c8-a45c-574c-a34c-fd0c35c6577b', 'seed:review-author:rv-0019', 'rv-0019@seed.servimatch.invalid', 'Teresa Baptista', 'ACTIVE'),
    ('763f8684-dc2c-5771-8bc9-77f5aff4f0e0', 'seed:review-author:rv-0020', 'rv-0020@seed.servimatch.invalid', 'Hugo Vieira', 'ACTIVE')
ON CONFLICT (keycloak_sub) DO NOTHING;

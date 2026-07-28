/**
 * Categories — catálogo de categorias/subcategorias hierárquicas
 * (ARQUITETURA §6.3: "catálogo, subcategorias"). Módulo-folha: não depende
 * de nenhum outro módulo de domínio.
 *
 * <p>Fronteira declarada pelo agente {@code backend-platform}. Implementação
 * pertence ao agente {@code backend-domain} — ver {@code docs/AGENTES.md}.
 * Substitui, quando implementado, a leitura direta à tabela {@code category}
 * hoje feita por {@code pt.servimatch.modules.requests.internal.CategoryLookup}
 * (compromisso deliberado e temporário, assinalado no próprio ficheiro).
 * Quando isso acontecer, {@code requests} passa a depender de
 * {@code categories} — atualização de {@code allowedDependencies} a pedir a
 * este agente, não a fazer sozinho.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Categories",
        allowedDependencies = {}
)
package pt.servimatch.modules.categories;

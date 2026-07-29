package pt.servimatch.modules.categories;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo {@code categories} — catálogo hierárquico de
 * categorias/subcategorias (V3, semeado por V15). Substitui, para os
 * módulos consumidores, a leitura direta da tabela {@code category} feita
 * anteriormente por {@code requests.internal.CategoryLookup} (compromisso
 * deliberado e temporário, fechado nesta onda — ADR-0010).
 */
public interface CategoriesApi {

    /**
     * Categoria por id, independentemente de estar ativa — usado para
     * <em>render</em> de um recurso que já referencia a categoria (ex.
     * {@code ServiceRequest.category}), mesmo que ela tenha sido desativada
     * depois de criado.
     */
    Optional<CategoryView> findById(UUID categoryId);

    /**
     * Categoria por id, só se estiver ativa — usado para validar a escolha
     * de categoria num pedido de criação/edição (ex.
     * {@code CreateServiceRequest.categoryId}).
     */
    Optional<CategoryView> findActiveById(UUID categoryId);

    record CategoryView(UUID id, UUID parentId, String slug, String name, boolean active) {
    }
}

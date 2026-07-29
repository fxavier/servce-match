package pt.servimatch.modules.categories.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.categories.internal.web.CategoryDto;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /v1/categories} — público (contrato {@code security: []}, já
 * refletido em {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}). Sem
 * {@code parentId}, devolve as categorias de topo; com {@code parentId},
 * devolve as subcategorias diretas dessa categoria.
 *
 * <p>{@code @Lazy}: ver nota em
 * {@code pt.servimatch.modules.requests.internal.RequestsController}.
 */
@RestController
@Lazy
class CategoriesController {

    private final CategoryRepository repository;

    CategoriesController(CategoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/v1/categories")
    List<CategoryDto> listCategories(@RequestParam(required = false) UUID parentId) {
        return repository.findActive(parentId).stream()
                .map(row -> new CategoryDto(row.id(), row.parentId(), row.slug(), row.name(), row.active()))
                .toList();
    }
}

package pt.servimatch.modules.categories.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import pt.servimatch.modules.categories.CategoriesApi;

import java.util.Optional;
import java.util.UUID;

// @Lazy em todos os beans deste módulo: ver nota em
// pt.servimatch.modules.users.internal.UserRepository.
@Service
@Lazy
class CategoriesService implements CategoriesApi {

    private final CategoryRepository repository;

    CategoriesService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CategoryView> findById(UUID categoryId) {
        return repository.findById(categoryId).map(CategoriesService::toView);
    }

    @Override
    public Optional<CategoryView> findActiveById(UUID categoryId) {
        return repository.findActiveById(categoryId).map(CategoriesService::toView);
    }

    static CategoryView toView(CategoryRow row) {
        return new CategoryView(row.id(), row.parentId(), row.slug(), row.name(), row.active());
    }
}

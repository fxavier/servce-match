package pt.servimatch.modules.requests.internal;

import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Leitura mínima e só-de-leitura da tabela {@code category} (V3), diretamente
 * por SQL em vez de uma chamada a uma {@code CategoriesApi} pública.
 *
 * <p><b>Compromisso deliberado e temporário.</b> O módulo {@code categories}
 * está atribuído a este agente mas foi explicitamente excluído desta onda
 * (fica para a Onda 1b, ver relatório de entrega). {@code service_request}
 * referencia {@code category_id} e o contrato ({@code ServiceRequest.category})
 * exige devolver id/slug/name/active embutidos — sem módulo {@code categories}
 * implementado, a alternativa seria não suportar de todo a criação de pedidos.
 * Este acesso fica confinado a este único ficheiro `internal`, para ser
 * substituído por uma chamada a {@code CategoriesApi.findById} assim que esse
 * módulo existir (Onda 1b) — nada fora deste ficheiro sabe que a leitura é
 * direta.
 *
 * <p>{@code @Lazy}: ver nota equivalente em
 * {@code pt.servimatch.modules.users.internal.UserRepository}.
 */
@Component
@Lazy
class CategoryLookup {

    private final JdbcClient jdbcClient;

    CategoryLookup(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<CategoryRow> findActiveById(UUID categoryId) {
        return jdbcClient.sql("SELECT id, parent_id, slug, name, active FROM category WHERE id = :id AND active = true")
                .param("id", categoryId)
                .query(this::mapRow)
                .optional();
    }

    Optional<CategoryRow> findById(UUID categoryId) {
        return jdbcClient.sql("SELECT id, parent_id, slug, name, active FROM category WHERE id = :id")
                .param("id", categoryId)
                .query(this::mapRow)
                .optional();
    }

    private CategoryRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CategoryRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("parent_id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getBoolean("active"));
    }
}

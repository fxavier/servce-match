package pt.servimatch.modules.categories.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code GET /v1/categories} isoladamente (standalone
 * MockMvc, sem contexto Spring completo) — sem {@code parentId} devolve as
 * categorias de topo, com {@code parentId} devolve as subcategorias diretas.
 * A hierarquia real, semeada por V15, é provada contra PostGIS real em
 * {@code CategoriesApiIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CategoriesControllerTest {

    @Mock
    private CategoryRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoriesController(repository)).build();
    }

    @Test
    void withoutParentIdReturnsTopLevelCategories() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findActive(isNull())).thenReturn(List.of(new CategoryRow(id, null, "canalizacao", "Canalização", true)));

        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].slug").value("canalizacao"))
                .andExpect(jsonPath("$[0].parentId").doesNotExist());
    }

    @Test
    void withParentIdReturnsItsDirectSubcategories() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(repository.findActive(eq(parentId)))
                .thenReturn(List.of(new CategoryRow(childId, parentId, "desentupimentos", "Desentupimentos", true)));

        mockMvc.perform(get("/v1/categories").param("parentId", parentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(childId.toString()))
                .andExpect(jsonPath("$[0].parentId").value(parentId.toString()));

        verify(repository).findActive(parentId);
    }
}

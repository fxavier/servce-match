package pt.servimatch.modules.categories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pt.servimatch.testsupport.SharedPostgis;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova contra PostGIS real ({@link SharedPostgis}, catálogo semeado por
 * V15) que {@code GET /v1/categories} é público e devolve o catálogo
 * hierárquico verdadeiro: categorias de topo sem {@code parentId}, e as
 * subcategorias diretas de "Canalização" com {@code ?parentId=}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("requests-it")
class CategoriesApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private CategoriesApi categoriesApi;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);
    }

    @Test
    void listCategoriesIsPublicAndReturnsTheSeededTopLevelCatalog() throws Exception {
        mockMvc.perform(get("/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='canalizacao')]").exists())
                .andExpect(jsonPath("$[?(@.slug=='eletricidade')]").exists())
                .andExpect(jsonPath("$[?(@.parentId!=null)]").doesNotExist());
    }

    @Test
    void listCategoriesWithParentIdReturnsItsDirectSubcategoriesOnly() throws Exception {
        UUID canalizacaoId = idBySlug("canalizacao");

        mockMvc.perform(get("/v1/categories").param("parentId", canalizacaoId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='desentupimentos')]").exists())
                .andExpect(jsonPath("$[?(@.slug=='reparacao-fugas')]").exists())
                // nenhuma categoria de topo (nem de outro ramo) deve aparecer.
                .andExpect(jsonPath("$[?(@.slug=='eletricidade')]").doesNotExist());
    }

    @Test
    void categoriesApiFindByIdResolvesTheSeededCategoryRegardlessOfActiveState() {
        UUID canalizacaoId = idBySlug("canalizacao");

        var view = categoriesApi.findById(canalizacaoId).orElseThrow();

        assertThat(view.slug()).isEqualTo("canalizacao");
        assertThat(view.active()).isTrue();

        assertThat(categoriesApi.findActiveById(canalizacaoId)).isPresent();
        assertThat(categoriesApi.findById(UUID.randomUUID())).isEmpty();
    }

    private UUID idBySlug(String slug) {
        return jdbcClient.sql("SELECT id FROM category WHERE slug = :slug")
                .param("slug", slug)
                .query(UUID.class)
                .single();
    }
}

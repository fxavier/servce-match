package pt.servimatch.modules.search.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pt.servimatch.modules.search.internal.dto.PageMeta;
import pt.servimatch.modules.search.internal.dto.ProviderSummary;
import pt.servimatch.modules.search.internal.dto.ProviderSummaryPage;

/**
 * Testa o contrato HTTP de {@code GET /v1/search/providers} isoladamente
 * (standalone MockMvc, sem contexto Spring completo — não precisa de
 * DataSource): caminho principal (200, envelope {@code items}/{@code page})
 * e um caso de erro (parâmetros inválidos, 400 RFC 9457). A correção
 * geoespacial (índice GiST, fronteiras de raio, modos de cobertura) é
 * provada à parte com PostGIS real — ver relatório da tarefa.
 */
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchProvidersService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(service)).build();
    }

    @Test
    void returnsPageEnvelopeOnHappyPath() throws Exception {
        UUID providerId = UUID.randomUUID();
        when(service.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ProviderSummaryPage(
                        List.of(new ProviderSummary(
                                providerId, "Ana Canalizadora", "Canalizações", null, 4.5f, 12, true, false, null)),
                        new PageMeta(null)));

        mockMvc.perform(get("/v1/search/providers").param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(providerId.toString()))
                .andExpect(jsonPath("$.items[0].displayName").value("Ana Canalizadora"))
                .andExpect(jsonPath("$.page.nextCursor").doesNotExist());
    }

    @Test
    void returnsRfc9457BadRequestWhenParametersAreInvalid() throws Exception {
        // A validação real (ex.: lat sem lon) é testada em
        // SearchProvidersServiceTest; aqui isola-se o contrato do
        // controlador: uma InvalidSearchParametersException do serviço tem
        // de virar RFC 9457 400, não uma exceção não tratada.
        when(service.search(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InvalidSearchParametersException("lat/lon", "lat e lon têm de ser fornecidos em conjunto."));

        mockMvc.perform(get("/v1/search/providers").param("lat", "38.72"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/validation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("lat/lon"));
    }
}

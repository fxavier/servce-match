package pt.servimatch.modules.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;
import pt.servimatch.testsupport.SharedPostgis;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova ponta-a-ponta, contra Postgres ({@link SharedPostgis}) e MinIO real
 * ({@link SharedMinio}) — não apenas que o código compila, mas que a
 * assinatura funciona de facto: o cliente de teste faz um {@code PUT} real
 * ao {@code uploadUrl} devolvido por {@code createUpload} e um {@code GET}
 * real ao URL devolvido por {@link UploadsApi#resolve}, exatamente como um
 * cliente web/mobile faria.
 *
 * <p>Caso de erro coberto (CLAUDE.md §5): ficheiro cujos <em>magic bytes</em>
 * não correspondem ao {@code contentType} declarado é rejeitado na
 * confirmação, mesmo tendo sido aceite pelo {@code PUT} (o object storage
 * não valida conteúdo — só o backend, no momento da confirmação).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("uploads-it")
class UploadsApiIntegrationTest {

    private static final byte[] VALID_JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01
    };

    private static final byte[] NOT_A_JPEG_BYTES = "isto não é uma imagem JPEG, só texto".getBytes(StandardCharsets.UTF_8);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UploadsApi uploadsApi;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgis::jdbcUrl);
        registry.add("spring.datasource.username", SharedPostgis::username);
        registry.add("spring.datasource.password", SharedPostgis::password);

        registry.add("servimatch.uploads.endpoint", SharedMinio::endpoint);
        registry.add("servimatch.uploads.region", () -> SharedMinio.REGION);
        registry.add("servimatch.uploads.bucket", () -> SharedMinio.BUCKET);
        registry.add("servimatch.uploads.access-key", () -> SharedMinio.ACCESS_KEY);
        registry.add("servimatch.uploads.secret-key", () -> SharedMinio.SECRET_KEY);
        registry.add("servimatch.uploads.path-style-access", () -> "true");
    }

    @Test
    void createConfirmAndResolveRoundTripAgainstRealMinio() throws Exception {
        String subject = "kc-uploads-it-" + UUID.randomUUID();
        JsonNode created = createUpload(subject, "image/jpeg", VALID_JPEG_BYTES.length);
        UUID imageId = UUID.fromString(created.get("imageId").asText());

        putToPresignedUrl(created, VALID_JPEG_BYTES);

        UUID ownerId = ownerUserIdFor(imageId);
        UUID confirmed = uploadsApi.confirmOwnedUpload(imageId, ownerId, UploadPurpose.REQUEST_ATTACHMENT);
        assertThat(confirmed).isEqualTo(imageId);

        // idempotente: confirmar outra vez o mesmo imageId não falha nem relê o armazenamento.
        assertThat(uploadsApi.confirmOwnedUpload(imageId, ownerId, UploadPurpose.REQUEST_ATTACHMENT)).isEqualTo(imageId);

        List<ImageRef> resolved = uploadsApi.resolve(List.of(imageId));
        assertThat(resolved).hasSize(1);
        ImageRef ref = resolved.get(0);
        assertThat(ref.id()).isEqualTo(imageId);
        assertThat(ref.contentType()).isEqualTo("image/jpeg");

        HttpResponse<byte[]> getResponse = httpClient.send(
                HttpRequest.newBuilder(URI.create(ref.url())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(getResponse.body()).isEqualTo(VALID_JPEG_BYTES);
    }

    @Test
    void confirmRejectsFileWhoseMagicBytesDoNotMatchDeclaredContentType() throws Exception {
        String subject = "kc-uploads-it-" + UUID.randomUUID();
        JsonNode created = createUpload(subject, "image/jpeg", NOT_A_JPEG_BYTES.length);
        UUID imageId = UUID.fromString(created.get("imageId").asText());

        // O object storage aceita o PUT sem validar conteúdo — só o backend,
        // na confirmação, rejeita.
        putToPresignedUrl(created, NOT_A_JPEG_BYTES);

        UUID ownerId = ownerUserIdFor(imageId);
        assertThatThrownBy(() -> uploadsApi.confirmOwnedUpload(imageId, ownerId, UploadPurpose.REQUEST_ATTACHMENT))
                .isInstanceOf(ErrorResponseException.class)
                .satisfies(ex -> assertThat(((ErrorResponseException) ex).getStatusCode().value()).isEqualTo(422));

        assertThat(jdbcClient.sql("SELECT status FROM upload_asset WHERE id = :id")
                        .param("id", imageId)
                        .query(String.class)
                        .single())
                .isEqualTo("PENDING");
    }

    @Test
    void createUploadRejectsUnsupportedContentTypeForPurpose() throws Exception {
        String subject = "kc-uploads-it-" + UUID.randomUUID();
        mockMvc.perform(post("/v1/uploads")
                        .with(jwt().jwt(builder -> builder.subject(subject).claim("email", subject + "@example.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purpose":"AVATAR","fileName":"doc.pdf","contentType":"application/pdf","contentLength":1024}
                                """))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("https://errors.servimatch.pt/unsupported-media-type"));
    }

    private JsonNode createUpload(String subject, String contentType, long contentLength) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "purpose", "REQUEST_ATTACHMENT",
                "fileName", "foto.jpg",
                "contentType", contentType,
                "contentLength", contentLength));

        String responseJson = mockMvc.perform(post("/v1/uploads")
                        .with(jwt().jwt(builder -> builder.subject(subject).claim("email", subject + "@example.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageId").exists())
                .andExpect(jsonPath("$.uploadUrl").exists())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseJson);
    }

    private void putToPresignedUrl(JsonNode created, byte[] content) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(created.get("uploadUrl").asText()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        // java.net.http.HttpClient calcula e proíbe a definição manual de
        // "Host"/"Content-Length" (cabeçalhos restritos) — o cliente HTTP
        // trata deles sozinho a partir do corpo real, exatamente como o
        // navegador/OkHttp de um cliente real fariam; a assinatura continua
        // a cobri-los (o servidor exige-os no pedido, só não podemos
        // defini-los explicitamente por este cliente de teste em concreto).
        created.get("headers").fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                request.header(name, entry.getValue().asText());
            }
        });
        HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("PUT ao URL pré-assinado: %s", response.body()).isBetween(200, 299);
    }

    private UUID ownerUserIdFor(UUID imageId) {
        return jdbcClient.sql("SELECT owner_user_id FROM upload_asset WHERE id = :id")
                .param("id", imageId)
                .query(UUID.class)
                .single();
    }
}

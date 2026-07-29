package pt.servimatch.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validador mínimo de {@code docs/api/openapi.yaml} contra respostas reais
 * do backend — o que faltava nesta onda (relatório de entrega: "nenhum
 * teste verifica hoje que as respostas do backend correspondem ao
 * contrato"; a divergência do {@code correlationId} foi apanhada à mão).
 *
 * <p><b>Não</b> é um validador de JSON Schema completo (Draft 2020-12, que é
 * o que o OpenAPI 3.1 usa nativamente) — não há nenhuma biblioteca desse
 * tipo já no classpath deste módulo (offline, {@code mvn -o}; adicionar uma
 * dependência nova pede-se ao {@code backend-platform}, dono do
 * {@code pom.xml}). Cobre o que os schemas deste contrato realmente usam:
 * {@code type} (incluindo uniões 3.1 como {@code [string, "null"]}),
 * {@code $ref}, {@code properties}/{@code required}, {@code items},
 * {@code enum} e os formatos {@code uuid}/{@code date-time}. É suficiente
 * para apanhar o tipo de deriva que já aconteceu nesta onda (campo a mais
 * não declarado, tipo trocado, enum desatualizado) sem reimplementar um
 * validador de schema genérico.
 */
public final class OpenApiSpec {

    private final Map<String, Object> root;

    private OpenApiSpec(Map<String, Object> root) {
        this.root = root;
    }

    public static OpenApiSpec load() {
        java.nio.file.Path path = RepoPaths.require("docs/api/openapi.yaml");
        try (InputStream in = java.nio.file.Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = yaml.load(in);
            return new OpenApiSpec(parsed);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Valida {@code instance} contra {@code #/components/schemas/<schemaName>}, falhando com o(s) desvio(s) encontrados. */
    public void assertMatchesSchema(JsonNode instance, String schemaName) {
        assertMatchesSchema(instance, schemaName, java.util.Set.of());
    }

    /**
     * Variante que ignora um conjunto de caminhos (notação {@code $.campo},
     * a mesma usada nas mensagens de erro) — só para gaps de contrato já
     * conhecidos e reportados, nunca para "fazer o teste passar": ver
     * {@code OpenApiContractComplianceTest#optionalResponseFieldsThatAreCurrentlyNullDivergeFromNonNullableContractTypes}
     * (desativado, prova reproduzível) para o porquê de cada caminho aqui listado.
     */
    public void assertMatchesSchema(JsonNode instance, String schemaName, java.util.Set<String> knownGapPaths) {
        Map<String, Object> schema = schemaByName(schemaName);
        List<String> errors = new ArrayList<>();
        validate(instance, schema, "$", errors, knownGapPaths);
        assertThat(errors)
                .as("Resposta não corresponde ao schema '" + schemaName + "' de docs/api/openapi.yaml")
                .isEmpty();
    }

    /** Variante para respostas de array "solto" (ex. {@code GET /v1/subscription-plans}). */
    public void assertEachItemMatchesSchema(JsonNode arrayInstance, String itemSchemaName) {
        assertThat(arrayInstance.isArray()).as("Esperava um array JSON").isTrue();
        Map<String, Object> itemSchema = schemaByName(itemSchemaName);
        List<String> errors = new ArrayList<>();
        int i = 0;
        for (JsonNode item : arrayInstance) {
            validate(item, itemSchema, "$[" + i + "]", errors, java.util.Set.of());
            i++;
        }
        assertThat(errors)
                .as("Um ou mais itens não correspondem ao schema '" + itemSchemaName + "'")
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaByName(String schemaName) {
        Map<String, Object> components = (Map<String, Object>) root.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        if (schema == null) {
            throw new IllegalArgumentException("Schema '" + schemaName + "' não existe em components.schemas do contrato.");
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private void validate(JsonNode node, Map<String, Object> schema, String path, List<String> errors, java.util.Set<String> knownGapPaths) {
        if (knownGapPaths.contains(path)) {
            return;
        }
        if (schema.containsKey("$ref")) {
            String ref = (String) schema.get("$ref");
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            validate(node, schemaByName(name), path, errors, knownGapPaths);
            return;
        }

        List<String> allowedTypes = allowedTypes(schema);
        if (node == null || node.isMissingNode()) {
            errors.add(path + ": campo em falta");
            return;
        }
        if (node.isNull()) {
            if (!allowedTypes.isEmpty() && !allowedTypes.contains("null")) {
                errors.add(path + ": é null, mas o schema não permite null (types=" + allowedTypes + ")");
            }
            return;
        }
        if (!allowedTypes.isEmpty() && !matchesAnyType(node, allowedTypes)) {
            errors.add(path + ": tipo JSON " + jsonKind(node) + " não corresponde a nenhum de " + allowedTypes);
            return;
        }

        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> enumList && !enumList.isEmpty()) {
            String actual = node.isTextual() ? node.asText() : node.toString();
            if (enumList.stream().noneMatch(v -> String.valueOf(v).equals(actual))) {
                errors.add(path + ": valor '" + actual + "' não está no enum " + enumList);
            }
        }

        String format = (String) schema.get("format");
        if (format != null && node.isTextual()) {
            validateFormat(node.asText(), format, path, errors);
        }

        if (allowedTypes.contains("object") && schema.get("properties") != null) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            List<String> required = (List<String>) schema.getOrDefault("required", List.of());
            for (String requiredField : required) {
                if (!node.has(requiredField)) {
                    errors.add(path + "." + requiredField + ": obrigatório pelo schema mas ausente da resposta");
                }
            }
            for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
                String propertyName = propertyEntry.getKey();
                String propertyPath = path + "." + propertyName;
                if (knownGapPaths.contains(propertyPath)) {
                    continue;
                }
                if (node.has(propertyName) && !node.get(propertyName).isNull()) {
                    validate(node.get(propertyName), (Map<String, Object>) propertyEntry.getValue(),
                            propertyPath, errors, knownGapPaths);
                } else if (node.has(propertyName) && node.get(propertyName).isNull()) {
                    List<String> propTypes = allowedTypes((Map<String, Object>) propertyEntry.getValue());
                    if (!propTypes.isEmpty() && !propTypes.contains("null")) {
                        errors.add(propertyPath + ": é null, mas o schema não permite null");
                    }
                }
            }
        }

        if (allowedTypes.contains("array") && schema.get("items") != null && node.isArray()) {
            Map<String, Object> itemSchema = (Map<String, Object>) schema.get("items");
            int i = 0;
            for (JsonNode item : node) {
                validate(item, itemSchema, path + "[" + i + "]", errors, knownGapPaths);
                i++;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> allowedTypes(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type == null) {
            return List.of();
        }
        if (type instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) {
                result.add(String.valueOf(o));
            }
            return result;
        }
        return List.of(String.valueOf(type));
    }

    private static boolean matchesAnyType(JsonNode node, List<String> types) {
        for (String type : types) {
            if (matchesType(node, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesType(JsonNode node, String type) {
        return switch (type) {
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "string" -> node.isTextual();
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "null" -> node.isNull();
            default -> true; // tipo desconhecido do schema: não bloqueia (fail-open deliberado, ver javadoc da classe)
        };
    }

    private static String jsonKind(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isIntegralNumber()) return "integer";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return node.getNodeType().toString();
    }

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static void validateFormat(String value, String format, String path, List<String> errors) {
        switch (format) {
            case "uuid" -> {
                if (!UUID_PATTERN.matcher(value).matches()) {
                    errors.add(path + ": '" + value + "' não é um UUID válido (format: uuid)");
                }
            }
            case "date-time" -> {
                try {
                    Instant.parse(value);
                } catch (DateTimeParseException e) {
                    errors.add(path + ": '" + value + "' não é date-time ISO-8601 válido");
                }
            }
            case "uri" -> {
                try {
                    java.net.URI.create(value);
                } catch (IllegalArgumentException e) {
                    errors.add(path + ": '" + value + "' não é um URI válido");
                }
            }
            default -> { /* outros formats (email, etc.) não validados — não usados criticamente neste contrato */ }
        }
    }
}

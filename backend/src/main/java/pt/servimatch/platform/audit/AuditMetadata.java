package pt.servimatch.platform.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contexto adicional de uma entrada de {@code audit_log} (V13), imposto por
 * tipo a nunca conter um objeto de domínio inteiro.
 *
 * <p>{@link Builder#put} só tem sobrecargas para {@link String},
 * {@link UUID}, {@code boolean}, {@code long} e {@link Instant} — não existe
 * {@code put(String, Object)}. Um chamador não consegue, portanto, despejar
 * (dump) um DTO, uma entidade ou um {@code Map} aninhado em metadata; só
 * consegue montar pares chave/valor escalares, um de cada vez. É o mesmo
 * motivo por que {@link pt.servimatch.platform.audit.AuditLogWriter} exige
 * que o alvo se identifique por {@link UUID} ({@code targetId}) em vez de,
 * por exemplo, o seu email.
 *
 * <p><b>O que o tipo não resolve, e fica por convenção (CLAUDE.md §4):</b>
 * um valor {@link String} continua a poder conter PII se um chamador lá
 * puser deliberadamente um email, nome ou telefone. Não o faças — usa
 * identificadores estáveis (UUID, enum, código) como valor.
 */
public final class AuditMetadata {

    private static final AuditMetadata EMPTY = new AuditMetadata(Map.of());

    private final Map<String, Object> values;

    private AuditMetadata(Map<String, Object> values) {
        this.values = values;
    }

    public static AuditMetadata empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Visibilidade de pacote: só {@link JdbcAuditLogWriter} precisa da forma interna para serializar. */
    Map<String, Object> asMap() {
        return values;
    }

    public static final class Builder {

        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String key, String value) {
            values.put(requireKey(key), value);
            return this;
        }

        public Builder put(String key, UUID value) {
            values.put(requireKey(key), value);
            return this;
        }

        public Builder put(String key, boolean value) {
            values.put(requireKey(key), value);
            return this;
        }

        public Builder put(String key, long value) {
            values.put(requireKey(key), value);
            return this;
        }

        public Builder put(String key, Instant value) {
            values.put(requireKey(key), value);
            return this;
        }

        public AuditMetadata build() {
            return new AuditMetadata(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
        }

        private static String requireKey(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("chave de metadata de auditoria não pode ser vazia");
            }
            return key;
        }
    }
}

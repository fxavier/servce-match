package pt.servimatch.platform.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuditMetadata} não depende de base de dados nem de Spring: o que
 * há a provar é a forma do valor (só escalares, ver o javadoc da classe) e
 * a rejeição de chaves inválidas.
 */
class AuditMetadataTest {

    @Test
    void buildsOnlyFromScalarTypesAndPreservesInsertionOrder() {
        UUID targetId = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-07-30T10:00:00Z");

        AuditMetadata metadata = AuditMetadata.builder()
                .put("from", "PENDING")
                .put("to", "APPROVED")
                .put("providerId", targetId)
                .put("reasonProvided", false)
                .put("attempt", 1L)
                .put("decidedAt", decidedAt)
                .build();

        assertThat(metadata.asMap())
                .containsExactly(
                        java.util.Map.entry("from", "PENDING"),
                        java.util.Map.entry("to", "APPROVED"),
                        java.util.Map.entry("providerId", targetId),
                        java.util.Map.entry("reasonProvided", false),
                        java.util.Map.entry("attempt", 1L),
                        java.util.Map.entry("decidedAt", decidedAt));
    }

    @Test
    void emptyIsAlwaysAnEmptyMap() {
        assertThat(AuditMetadata.empty().asMap()).isEmpty();
    }

    @Test
    void rejectsBlankOrNullKeysInsteadOfSilentlyAcceptingUnusableMetadata() {
        assertThatThrownBy(() -> AuditMetadata.builder().put("", "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditMetadata.builder().put("   ", "value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditMetadata.builder().put((String) null, "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

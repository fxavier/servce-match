package pt.servimatch.testsupport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code src/test/resources/keycloak/realm-servimatch.json} (usado por
 * {@link SharedKeycloak} para um contentor hermético, sem depender do
 * {@code docker-compose} local) é uma cópia deliberada de
 * {@code infra/keycloak/realm-servimatch.json} (fonte de verdade, agente
 * {@code platform-infra}) — não uma referência de caminho relativo, frágil
 * ao diretório de trabalho do Maven.
 *
 * <p>Uma cópia sem verificação de deriva é pior do que nenhuma (mesmo
 * argumento do ADR-0010 sobre a lista de leituras entre módulos): este
 * teste falha, alto e cedo, assim que as duas divergirem — nesse caso
 * resincroniza <b>esta</b> cópia a partir de {@code infra/}, nunca o
 * inverso ({@code infra/**} não é âmbito de escrita deste agente).
 */
class RealmFileSyncTest {

    @Test
    void testRealmCopyMatchesInfraSourceOfTruthByteForByte() throws IOException {
        Path infraRealm = RepoPaths.require("infra/keycloak/realm-servimatch.json");
        String sourceOfTruth = RepoPaths.readUtf8(infraRealm);

        String testCopy;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("keycloak/realm-servimatch.json")) {
            assertThat(in).as("backend/src/test/resources/keycloak/realm-servimatch.json em falta").isNotNull();
            testCopy = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(testCopy)
                .as("A cópia de teste divergiu de infra/keycloak/realm-servimatch.json — resincroniza "
                        + "backend/src/test/resources/keycloak/realm-servimatch.json a partir do ficheiro de infra "
                        + "(fonte de verdade, agente platform-infra).")
                .isEqualTo(sourceOfTruth);
    }
}

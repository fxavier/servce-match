package pt.servimatch.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Localiza ficheiros do monorepo fora de {@code backend/} (ex.
 * {@code infra/keycloak/realm-servimatch.json}) a partir do diretório de
 * trabalho do Maven, sem assumir um único valor fixo para esse diretório —
 * {@code mvn} tanto pode correr a partir de {@code backend/} (o caso comum,
 * onde vive o {@code pom.xml}) como da raiz do monorepo. Sobe no máximo
 * {@value #MAX_LEVELS_UP} níveis a partir de {@code user.dir} à procura do
 * caminho pedido; falha alto e cedo (não silenciosamente) se não encontrar,
 * porque um teste que dependa disto sem o ficheiro é um falso positivo.
 */
final class RepoPaths {

    private static final int MAX_LEVELS_UP = 4;

    private RepoPaths() {
    }

    /** @param relativeFromRepoRoot ex. {@code "infra/keycloak/realm-servimatch.json"} */
    static Path require(String relativeFromRepoRoot) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd;
        for (int level = 0; level <= MAX_LEVELS_UP; level++) {
            Path attempt = candidate.resolve(relativeFromRepoRoot);
            if (Files.isRegularFile(attempt)) {
                return attempt;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        throw new IllegalStateException(
                "Não encontrei '" + relativeFromRepoRoot + "' a subir a partir de " + cwd
                        + " (" + MAX_LEVELS_UP + " níveis). O monorepo mudou de forma, ou o teste corre de um cwd inesperado.");
    }

    static String readUtf8(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

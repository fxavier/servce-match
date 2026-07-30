package pt.servimatch.modules.reviews.internal;

/**
 * Reduz {@code users.display_name} a "primeiro nome + inicial do apelido"
 * (ex. {@code "Mariana Costa"} → {@code "Mariana C."}) para
 * {@code ReviewWithAuthor.authorName} — {@code GET
 * /v1/providers/{providerId}/reviews} é público e indexável, o nome
 * completo do autor <b>nunca</b> sai por este endpoint (contrato,
 * {@code docs/api/openapi.yaml}). Função pura e testável de propósito —
 * nada disto é SQL inline: a redução tem casos de fronteira (nome com um
 * só token, espaços a mais, apelidos compostos) fáceis de errar em texto
 * dentro de uma consulta e impossíveis de testar isoladamente lá.
 */
final class AuthorNameReducer {

    private AuthorNameReducer() {
    }

    /**
     * @param displayName nome completo tal como guardado em {@code users.display_name}.
     * @return primeiro nome + espaço + inicial do apelido seguida de ponto
     *         (o último token do nome, não necessariamente o "apelido"
     *         gramatical — mesma simplificação usada em toda a UI, ver
     *         {@code authorAvatarSeed}). Nome com um único token devolve
     *         apenas esse token, sem inicial (não há apelido a reduzir).
     *         {@code null}/vazio/só espaços devolve {@code ""} — nunca
     *         lança, para não derrubar uma página inteira de avaliações por
     *         um registo de dados sujo.
     */
    static String reduce(String displayName) {
        if (displayName == null) {
            return "";
        }
        String trimmed = displayName.strip().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] tokens = trimmed.split(" ");
        if (tokens.length == 1) {
            return tokens[0];
        }
        String firstName = tokens[0];
        String lastToken = tokens[tokens.length - 1];
        char initial = lastToken.isEmpty() ? '?' : Character.toUpperCase(lastToken.charAt(0));
        return firstName + " " + initial + ".";
    }
}

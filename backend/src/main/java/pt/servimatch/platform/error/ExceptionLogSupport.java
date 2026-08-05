package pt.servimatch.platform.error;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Descrição de uma exceção segura para escrever em log — usada pelos
 * handlers de {@link GlobalExceptionHandler} que apanham exceções cuja
 * mensagem pode conter dados de utilizador.
 *
 * <p><b>O problema concreto:</b> o pgjdbc constrói
 * {@link PSQLException#getMessage()} (e, por isso,
 * {@link Throwable#toString()} e tudo o que o Logback escreve quando um
 * {@code Throwable} é passado como último argumento ao SLF4J) a partir de
 * {@link ServerErrorMessage#toString()}, que inclui, por omissão, o campo
 * {@code DETAIL} devolvido pelo servidor. Para uma violação de constraint
 * (`23xxx`), esse `DETAIL` é literalmente a linha rejeitada —
 * {@code "Failing row contains (...)"} — com todos os valores das colunas,
 * incluindo PII (morada, email, texto livre). Nenhum código deste backend
 * pode chamar {@code getMessage()}, {@code getLocalizedMessage()},
 * {@code toString()} numa {@link PSQLException} (direta ou indiretamente,
 * nomeadamente passando o {@code Throwable} a um logger), nem
 * {@link ServerErrorMessage#getDetail()}, {@code #getHint()} ou
 * {@code #getWhere()} — só os campos abaixo, que são identificadores de
 * schema (nome de constraint, tabela) e não dados.
 *
 * <p>A descrição devolvida contém apenas:
 * <ul>
 *   <li>o nome da classe da exceção de topo e, se distinta, da causa mais
 *       específica — nomes de classe são identificadores de código, não dados;</li>
 *   <li>o {@code SQLState} (código de 5 carateres, standard SQL, sem dados —
 *       disponível em qualquer {@link SQLException}, não é específico do
 *       driver);</li>
 *   <li>quando a causa é uma {@link PSQLException} com
 *       {@link PSQLException#getServerErrorMessage()} não nulo (nem sempre é
 *       o caso — exceções do lado do cliente, como falha de ligação, não têm
 *       {@code ServerErrorMessage}), o nome da constraint e da tabela
 *       envolvidas, via {@link ServerErrorMessage#getConstraint()} e
 *       {@link ServerErrorMessage#getTable()};</li>
 *   <li>um punhado dos primeiros frames do stack trace da exceção apanhada
 *       pelo handler — {@link StackTraceElement} nunca contém texto livre,
 *       só classe/método/ficheiro/linha, e por isso identifica o ponto do
 *       código sem risco de PII.</li>
 * </ul>
 *
 * <p>Para diagnóstico além disto — por exemplo, o valor exato que violou a
 * constraint — usa o {@code correlation_id} devolvido no Problem Details para
 * cruzar com o tracing distribuído (span do pedido) ou reproduzir localmente;
 * nunca voltar a passar a exceção completa ao logger de produção.
 */
final class ExceptionLogSupport {

    private static final int MAX_STACK_FRAMES = 5;

    private ExceptionLogSupport() {
    }

    /** Descrição segura de {@code ex}, pronta para um único argumento de log estruturado. */
    static String describe(Throwable ex) {
        StringBuilder description = new StringBuilder("exceptionType=").append(ex.getClass().getName());

        Throwable rootCause = mostSpecificCause(ex);
        if (rootCause != ex) {
            description.append(", rootCauseType=").append(rootCause.getClass().getName());
        }

        findSqlException(ex).ifPresent(sqlException -> {
            appendIfPresent(description, "sqlState", sqlException.getSQLState());
            if (sqlException instanceof PSQLException psqlException) {
                ServerErrorMessage serverError = psqlException.getServerErrorMessage();
                // getServerErrorMessage() é @Nullable: exceções do lado do
                // cliente (ligação recusada, timeout) não têm resposta do
                // servidor para analisar — não há constraint/tabela a extrair,
                // e não há mais nenhum campo seguro a tentar em alternativa.
                if (serverError != null) {
                    appendIfPresent(description, "constraint", serverError.getConstraint());
                    appendIfPresent(description, "table", serverError.getTable());
                }
            }
        });

        appendStackLocation(description, ex);
        return description.toString();
    }

    private static Throwable mostSpecificCause(Throwable ex) {
        Throwable current = ex;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        seen.put(current, Boolean.TRUE);
        while (current.getCause() != null && seen.putIfAbsent(current.getCause(), Boolean.TRUE) == null) {
            current = current.getCause();
        }
        return current;
    }

    /** Primeira {@link SQLException} na cadeia de causas, se existir. */
    private static java.util.Optional<SQLException> findSqlException(Throwable ex) {
        Throwable current = ex;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (current != null && seen.putIfAbsent(current, Boolean.TRUE) == null) {
            if (current instanceof SQLException sqlException) {
                return java.util.Optional.of(sqlException);
            }
            current = current.getCause();
        }
        return java.util.Optional.empty();
    }

    private static void appendIfPresent(StringBuilder description, String label, String value) {
        if (value != null && !value.isBlank()) {
            description.append(", ").append(label).append('=').append(value);
        }
    }

    private static void appendStackLocation(StringBuilder description, Throwable ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace.length == 0) {
            return;
        }
        StringJoiner joiner = new StringJoiner(" <- ");
        for (int i = 0; i < Math.min(MAX_STACK_FRAMES, stackTrace.length); i++) {
            joiner.add(stackTrace[i].toString());
        }
        description.append(", at=").append(joiner);
    }
}

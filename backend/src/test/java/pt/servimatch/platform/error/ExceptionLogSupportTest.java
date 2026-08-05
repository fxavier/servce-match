package pt.servimatch.platform.error;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unidade de {@link ExceptionLogSupport}, isolada do handler e do Spring:
 * o que {@code describe()} devolve nunca contém a mensagem/Detail de uma
 * {@link PSQLException} (onde o pgjdbc embute a linha rejeitada pelo
 * PostgreSQL), só identificadores de schema e de código.
 *
 * <p>O cenário de dados replica literalmente o achado da auditoria: um
 * {@code service_request} com morada e descrição reais na linha rejeitada.
 * Ver também {@link GlobalExceptionHandlerLoggingTest}, que prova o mesmo
 * através do caminho real do handler (Logback), não só desta função.
 */
class ExceptionLogSupportTest {

    private static final String LEAKED_STREET = "Rua Secreta 123";
    private static final String LEAKED_CITY = "Lisboa";
    private static final String LEAKED_TITLE = "Auditoria C2 pedido";
    private static final String LEAKED_DESCRIPTION = "Descricao de teste com detalhes do cliente";
    private static final String CONSTRAINT_NAME = "service_request_urgency_check";
    private static final String TABLE_NAME = "service_request";
    private static final String SQL_STATE = "23514";

    @Test
    void describesConstraintAndSqlStateWithoutLeakingRejectedRowData() {
        PSQLException psqlException = auditFindingPsqlException();
        DataIntegrityViolationException wrapper = springStyleWrapper(psqlException);

        String description = ExceptionLogSupport.describe(wrapper);

        assertThat(description)
                .contains("exceptionType=" + DataIntegrityViolationException.class.getName())
                .contains("rootCauseType=" + PSQLException.class.getName())
                .contains("sqlState=" + SQL_STATE)
                .contains("constraint=" + CONSTRAINT_NAME)
                .contains("table=" + TABLE_NAME);

        assertThat(description)
                .doesNotContain(LEAKED_STREET)
                .doesNotContain(LEAKED_CITY)
                .doesNotContain(LEAKED_TITLE)
                .doesNotContain(LEAKED_DESCRIPTION)
                .doesNotContain("Failing row contains")
                .doesNotContain("Detail:");
    }

    @Test
    void degradesGracefullyWhenCauseChainHasNoSqlException() {
        Exception plain = new IllegalStateException("cliente@example.pt tentou uma operação inválida");

        String description = ExceptionLogSupport.describe(plain);

        assertThat(description).contains("exceptionType=" + IllegalStateException.class.getName());
        assertThat(description).doesNotContain("cliente@example.pt");
        assertThat(description).doesNotContain("tentou uma operação inválida");
    }

    @Test
    void degradesGracefullyWhenPsqlExceptionHasNoServerErrorMessage() {
        // Construtor "client-side": falha de ligação, sem resposta do servidor
        // a analisar — getServerErrorMessage() é @Nullable e devolve null.
        PSQLException clientSide = new PSQLException("connection refused", null);

        String description = ExceptionLogSupport.describe(clientSide);

        assertThat(description).contains("exceptionType=" + PSQLException.class.getName());
        assertThat(description).doesNotContain("connection refused");
        assertThat(description).doesNotContain("constraint=");
    }

    /**
     * Reconstrói, byte a byte, a exceção que o pgjdbc teria produzido para o
     * pedido real da auditoria: {@code ServerErrorMessage} é construído a
     * partir do formato de fios do protocolo (par de carateres-tipo + valor,
     * terminado a {@code \0} — ver {@code ServerErrorMessage(String)}).
     */
    private static PSQLException auditFindingPsqlException() {
        String detail = "Failing row contains (9153106f-0000-0000-0000-000000000000, "
                + "f6762345-0000-0000-0000-000000000000, 8fb87155-0000-0000-0000-000000000000, "
                + LEAKED_TITLE + ", " + LEAKED_DESCRIPTION + ", " + LEAKED_STREET + ", null, "
                + "1000-001, " + LEAKED_CITY + ", PT-11, PT, ...).";
        String raw = "S" + "ERROR" + '\0'
                + "C" + SQL_STATE + '\0'
                + "M" + "new row for relation \"" + TABLE_NAME + "\" violates check constraint \""
                + CONSTRAINT_NAME + "\"" + '\0'
                + "D" + detail + '\0'
                + "n" + CONSTRAINT_NAME + '\0'
                + "t" + TABLE_NAME + '\0';
        return new PSQLException(new ServerErrorMessage(raw));
    }

    /**
     * Spring's {@code SQLErrorCodeSQLExceptionTranslator} embute a
     * {@code getMessage()} da causa na própria mensagem do wrapper — por
     * isso o teste também tem de replicar isto, não só o cenário "ideal" em
     * que só a causa carrega PII.
     */
    private static DataIntegrityViolationException springStyleWrapper(PSQLException cause) {
        return new DataIntegrityViolationException(
                "PreparedStatementCallback; SQL [insert into service_request (...) values (...)]; "
                        + cause.getMessage() + "; nested exception is " + cause,
                cause);
    }
}

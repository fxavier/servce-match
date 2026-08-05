package pt.servimatch.platform.error;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.config.IdempotencyConfig;
import pt.servimatch.config.RateLimitConfig;
import pt.servimatch.config.SecurityConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova (achado A1 da auditoria de segurança, ALTO): uma violação de
 * integridade com dados reconhecíveis (morada, título, descrição de um
 * pedido) no corpo da linha rejeitada pelo PostgreSQL <b>não aparece no
 * log</b>, nem via {@link GlobalExceptionHandler#handleDataIntegrityViolation}
 * nem via {@link GlobalExceptionHandler#handleUnexpected}.
 *
 * <p>Anexa um {@link ListAppender} diretamente ao logger de
 * {@link GlobalExceptionHandler} e inspeciona os eventos <i>reais</i>
 * escritos pelo handler através do caminho HTTP completo (MockMvc) — não
 * chama {@code ExceptionLogSupport} diretamente (isso é
 * {@link ExceptionLogSupportTest}, ao nível da unidade). Um teste que só
 * confirmasse o código de estado HTTP não provaria nada sobre o que foi
 * escrito no log; é isso que este teste substitui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, classes = GlobalExceptionHandlerLoggingTest.TestConfig.class)
@AutoConfigureMockMvc
class GlobalExceptionHandlerLoggingTest {

    private static final String LEAKED_STREET = "Rua Secreta 123";
    private static final String LEAKED_CITY = "Lisboa";
    private static final String LEAKED_TITLE = "Auditoria C2 pedido";
    private static final String LEAKED_DESCRIPTION = "Descricao de teste com detalhes do cliente";
    private static final String LEAKED_EMAIL = "cliente-real@example.pt";
    private static final String CONSTRAINT_NAME = "service_request_urgency_check";
    private static final String SQL_STATE = "23514";

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> appender;
    private Logger handlerLogger;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.setContext(handlerLogger.getLoggerContext());
        appender.start();
        handlerLogger.addAppender(appender);
        handlerLogger.setLevel(Level.ALL);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(appender);
    }

    @Test
    void dataIntegrityViolationLogsConstraintNameButNeverTheRejectedRow() throws Exception {
        mockMvc.perform(get("/v1/_probe/data-integrity")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isConflict());

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.get(0);

        // O mecanismo da fuga original: um Throwable passado ao logger fica
        // anexado ao evento e o Logback imprime a cadeia de causas via
        // toString(). Garantir que já não há nenhum Throwable no evento é a
        // prova de que a via de fuga foi fechada, não só de que os dados de
        // teste não apareceram por acaso.
        assertThat(event.getThrowableProxy()).isNull();

        String logged = event.getFormattedMessage();
        assertThat(logged)
                .as("mensagem de log completa")
                .contains("exceptionType=" + DataIntegrityViolationException.class.getName())
                .contains("rootCauseType=" + PSQLException.class.getName())
                .contains("sqlState=" + SQL_STATE)
                .contains("constraint=" + CONSTRAINT_NAME);

        assertThat(logged)
                .doesNotContain(LEAKED_STREET)
                .doesNotContain(LEAKED_CITY)
                .doesNotContain(LEAKED_TITLE)
                .doesNotContain(LEAKED_DESCRIPTION)
                .doesNotContain("Failing row contains")
                .doesNotContain("Detail:");
    }

    @Test
    void unhandledExceptionLogsTypeButNeverAFreeTextMessageThatMayCarryPii() throws Exception {
        mockMvc.perform(get("/v1/_probe/unexpected")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isInternalServerError());

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.get(0);

        assertThat(event.getThrowableProxy()).isNull();

        String logged = event.getFormattedMessage();
        assertThat(logged).contains("exceptionType=" + IllegalStateException.class.getName());
        assertThat(logged).doesNotContain(LEAKED_EMAIL);
    }

    @Configuration
    @EnableAutoConfiguration(excludeName = "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration")
    @Import({SecurityConfig.class, RateLimitConfig.class, IdempotencyConfig.class, GlobalExceptionHandler.class})
    static class TestConfig {

        @RestController
        static class ProbeController {

            @GetMapping("/v1/_probe/data-integrity")
            public String dataIntegrity() {
                throw springStyleWrapper(auditFindingPsqlException());
            }

            @GetMapping("/v1/_probe/unexpected")
            public String unexpected() {
                throw new IllegalStateException(
                        "Operação inválida para o cliente " + LEAKED_EMAIL + " — estado do recurso não permite continuar.");
            }
        }

        private static PSQLException auditFindingPsqlException() {
            String tableName = "service_request";
            String detail = "Failing row contains (9153106f-0000-0000-0000-000000000000, "
                    + "f6762345-0000-0000-0000-000000000000, 8fb87155-0000-0000-0000-000000000000, "
                    + LEAKED_TITLE + ", " + LEAKED_DESCRIPTION + ", " + LEAKED_STREET + ", null, "
                    + "1000-001, " + LEAKED_CITY + ", PT-11, PT, ...).";
            String raw = "S" + "ERROR" + '\0'
                    + "C" + SQL_STATE + '\0'
                    + "M" + "new row for relation \"" + tableName + "\" violates check constraint \""
                    + CONSTRAINT_NAME + "\"" + '\0'
                    + "D" + detail + '\0'
                    + "n" + CONSTRAINT_NAME + '\0'
                    + "t" + tableName + '\0';
            return new PSQLException(new ServerErrorMessage(raw));
        }

        private static DataIntegrityViolationException springStyleWrapper(PSQLException cause) {
            return new DataIntegrityViolationException(
                    "PreparedStatementCallback; SQL [insert into service_request (...) values (...)]; "
                            + cause.getMessage() + "; nested exception is " + cause,
                    cause);
        }
    }
}

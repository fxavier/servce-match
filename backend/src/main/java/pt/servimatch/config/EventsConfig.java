package pt.servimatch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.servimatch.platform.events.EventPublicationHousekeeping;
import pt.servimatch.platform.events.EventsProperties;

/**
 * Configuração do Event Publication Registry do Spring Modulith (ADR-0001):
 * a entrega em si (at-least-once, reentrega em arranque, monitor de
 * staleness) é ligada por propriedade nativa em {@code application.yml}
 * ({@code spring.modulith.events.*}); esta classe liga apenas o expurgo
 * periódico de publicações completadas.
 *
 * <p>{@link EventPublicationHousekeeping} só é criado quando existe um
 * backend de persistência para o registo (JDBC, ver {@code pom.xml}) — nos
 * perfis de teste, onde a fonte de dados é excluída para não depender de
 * base de dados real, este bean simplesmente não existe.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EventsProperties.class)
public class EventsConfig {

    @Bean
    @ConditionalOnBean(CompletedEventPublications.class)
    public EventPublicationHousekeeping eventPublicationHousekeeping(
            CompletedEventPublications completedEventPublications, EventsProperties properties) {
        return new EventPublicationHousekeeping(completedEventPublications, properties);
    }
}

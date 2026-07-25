package pt.servimatch.platform.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Expurgo periódico de publicações já {@code COMPLETED} no Event
 * Publication Registry, para o histórico não crescer sem limite (o modo de
 * conclusão por omissão, {@code UPDATE}, mantém as entradas concluídas para
 * auditoria — ver {@code spring.modulith.events.completion-mode}).
 *
 * <p>A entrega em si é assíncrona e <b>at-least-once</b>: os consumidores
 * (handlers {@code @ApplicationModuleListener} nos módulos de domínio) têm
 * de ser <b>idempotentes</b>, porque um evento pode ser reentregue após
 * falha, reinício ou marcação como {@code FAILED} pelo monitor de
 * staleness.
 */
public class EventPublicationHousekeeping {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationHousekeeping.class);

    private final CompletedEventPublications completedEventPublications;
    private final EventsProperties properties;

    public EventPublicationHousekeeping(CompletedEventPublications completedEventPublications, EventsProperties properties) {
        this.completedEventPublications = completedEventPublications;
        this.properties = properties;
    }

    @Scheduled(cron = "${servimatch.events.purge-cron:0 15 3 * * *}")
    public void purgeOldCompletedPublications() {
        log.debug("Purging completed event publications older than {}", properties.completedRetention());
        completedEventPublications.deletePublicationsOlderThan(properties.completedRetention());
    }
}

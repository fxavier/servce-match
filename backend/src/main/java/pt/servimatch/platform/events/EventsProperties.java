package pt.servimatch.platform.events;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Retenção de housekeeping do Event Publication Registry (Spring Modulith).
 * A entrega em si (at-least-once, reentrega, staleness monitor) é
 * configurada diretamente pelas propriedades nativas
 * {@code spring.modulith.events.*} em {@code application.yml}; esta classe
 * cobre apenas o expurgo periódico de publicações já completadas, que o
 * Modulith não agenda sozinho.
 *
 * @param completedRetention publicações {@code COMPLETED} mais antigas do que isto são apagadas.
 */
@ConfigurationProperties(prefix = "servimatch.events")
public record EventsProperties(Duration completedRetention) {
    public EventsProperties {
        if (completedRetention == null) {
            completedRetention = Duration.ofDays(7);
        }
    }
}

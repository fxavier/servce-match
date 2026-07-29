package pt.servimatch.platform.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalização de {@link RateLimitProperties}: em particular, que uma lista
 * de {@code trusted-proxies} ausente ou preenchida com valores em branco
 * (o binding de {@code servimatch.rate-limit.trusted-proxies=} — variável
 * de ambiente por definir — pode produzir uma lista com uma única string
 * vazia, consoante a fonte) resulta sempre numa lista vazia, nunca numa que
 * faça {@link org.springframework.security.web.util.matcher.IpAddressMatcher}
 * rebentar no arranque.
 */
class RateLimitPropertiesTest {

    @Test
    void missingTrustedProxiesDefaultsToEmptyList() {
        RateLimitProperties properties = new RateLimitProperties(true, 0, null, null, null, null);

        assertThat(properties.trustedProxies()).isEmpty();
        assertThat(properties.capacity()).isEqualTo(120);
        assertThat(properties.refillPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.backend()).isEqualTo("in-memory");
    }

    @Test
    void blankTrustedProxyEntriesAreFilteredOut() {
        RateLimitProperties properties = new RateLimitProperties(
                true, 120, Duration.ofMinutes(1), "in-memory", Arrays.asList("", "  ", null), null);

        assertThat(properties.trustedProxies()).isEmpty();
    }

    @Test
    void webhookDefaultsApplyWhenMissing() {
        RateLimitProperties properties = new RateLimitProperties(true, 120, Duration.ofMinutes(1), "in-memory", null, null);

        assertThat(properties.webhook().capacity()).isEqualTo(20);
        assertThat(properties.webhook().refillPeriod()).isEqualTo(Duration.ofMinutes(1));
    }
}

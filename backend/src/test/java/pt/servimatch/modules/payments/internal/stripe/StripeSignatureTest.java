package pt.servimatch.modules.payments.internal.stripe;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class StripeSignatureTest {

    private static final String SECRET = "whsec_test_secret";

    @Test
    void acceptsAValidSignature() {
        byte[] body = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();
        String header = "t=" + timestamp + ",v1=" + hmac(SECRET, timestamp + "." + new String(body, StandardCharsets.UTF_8));

        assertThat(StripeSignature.verify(body, header, SECRET, 300)).isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        byte[] originalBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();
        String header = "t=" + timestamp + ",v1=" + hmac(SECRET, timestamp + "." + new String(originalBody, StandardCharsets.UTF_8));

        byte[] tamperedBody = "{\"id\":\"evt_1_tampered\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(StripeSignature.verify(tamperedBody, header, SECRET, 300)).isFalse();
    }

    @Test
    void rejectsWrongSecret() {
        byte[] body = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = Instant.now().getEpochSecond();
        String header = "t=" + timestamp + ",v1=" + hmac("wrong_secret", timestamp + "." + new String(body, StandardCharsets.UTF_8));

        assertThat(StripeSignature.verify(body, header, SECRET, 300)).isFalse();
    }

    @Test
    void rejectsAnExpiredTimestamp() {
        byte[] body = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
        long oldTimestamp = Instant.now().minusSeconds(3600).getEpochSecond();
        String header = "t=" + oldTimestamp + ",v1=" + hmac(SECRET, oldTimestamp + "." + new String(body, StandardCharsets.UTF_8));

        assertThat(StripeSignature.verify(body, header, SECRET, 300)).isFalse();
    }

    @Test
    void rejectsAMissingHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(StripeSignature.verify(body, null, SECRET, 300)).isFalse();
        assertThat(StripeSignature.verify(body, "", SECRET, 300)).isFalse();
        assertThat(StripeSignature.verify(body, "garbage-header-format", SECRET, 300)).isFalse();
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

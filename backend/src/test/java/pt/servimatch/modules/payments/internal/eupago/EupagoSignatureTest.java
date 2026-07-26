package pt.servimatch.modules.payments.internal.eupago;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class EupagoSignatureTest {

    private static final String SECRET = "eupago_test_secret";

    @Test
    void acceptsAValidSignature() {
        byte[] body = "{\"identifier\":\"sub-1\",\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        String header = hmac(SECRET, body);

        assertThat(EupagoSignature.verify(body, header, SECRET)).isTrue();
    }

    @Test
    void rejectsATamperedBody() {
        byte[] body = "{\"identifier\":\"sub-1\",\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        String header = hmac(SECRET, body);
        byte[] tampered = "{\"identifier\":\"sub-1\",\"status\":\"failed\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(EupagoSignature.verify(tampered, header, SECRET)).isFalse();
    }

    @Test
    void rejectsMissingSecretOrHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(EupagoSignature.verify(body, null, SECRET)).isFalse();
        assertThat(EupagoSignature.verify(body, "abc", null)).isFalse();
        assertThat(EupagoSignature.verify(body, "", SECRET)).isFalse();
    }

    private static String hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

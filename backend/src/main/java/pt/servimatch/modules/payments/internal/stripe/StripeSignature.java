package pt.servimatch.modules.payments.internal.stripe;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verificação da assinatura de webhook do Stripe
 * (<a href="https://docs.stripe.com/webhooks#verify-official-libraries">docs.stripe.com/webhooks</a>):
 * cabeçalho {@code Stripe-Signature: t=<timestamp>,v1=<hmac-sha256 hex>[,v1=...]}.
 * A assinatura é HMAC-SHA256 do segredo sobre {@code "<timestamp>.<corpo em bruto>"}
 * (bytes exatos, antes de qualquer parse). Comparação em tempo constante;
 * timestamp fora da tolerância é rejeitado (proteção contra *replay*).
 */
final class StripeSignature {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private StripeSignature() {
    }

    static boolean verify(byte[] rawBody, String signatureHeader, String secret, long toleranceSeconds) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        Long timestamp = null;
        java.util.List<String> v1Signatures = new java.util.ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            if ("t".equals(key)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            } else if ("v1".equals(key)) {
                v1Signatures.add(value);
            }
        }
        if (timestamp == null || v1Signatures.isEmpty()) {
            return false;
        }
        long age = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (age > toleranceSeconds) {
            return false;
        }
        String signedPayload = timestamp + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expectedHex = hmacHex(secret, signedPayload);
        boolean matched = false;
        for (String candidate : v1Signatures) {
            matched |= constantTimeEquals(expectedHex, candidate.toLowerCase(Locale.ROOT));
        }
        return matched;
    }

    private static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}

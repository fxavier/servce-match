package pt.servimatch.modules.payments.internal.eupago;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verificação de assinatura para os *callbacks* Eupago/IfthenPay.
 *
 * <p><b>Assunção documentada:</b> nem a Eupago nem a IfthenPay publicam um
 * esquema de assinatura HMAC uniforme e verificável de forma tão direta
 * como o Stripe (usam antes, tipicamente, uma "chave anti-phishing"
 * partilhada incluída no próprio corpo do callback, específica de cada
 * conta/API). Para não implementar uma verificação de credibilidade
 * duvidosa, este adaptador usa aqui um esquema HMAC-SHA256 genérico sobre
 * o corpo em bruto (cabeçalho {@code X-Signature}, comparação em tempo
 * constante) — o mesmo padrão de segurança do Stripe, com o segredo por
 * fornecedor vindo de {@code EUPAGO_WEBHOOK_SECRET}/{@code IFTHENPAY_WEBHOOK_SECRET}.
 * <b>Antes de produção</b>, confirmar contra a documentação oficial do
 * fornecedor (chave anti-phishing vs. HMAC de cabeçalho) e ajustar apenas
 * esta classe — o resto do pipeline (idempotência, ordenação,
 * reconciliação) não muda.
 */
final class EupagoSignature {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private EupagoSignature() {
    }

    static boolean verify(byte[] rawBody, String signatureHeader, String secret) {
        if (signatureHeader == null || signatureHeader.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        String expected = hmacHex(secret, rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}

package pt.servimatch.modules.payments.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Segredos e configuração dos gateways — <b>sempre</b> por variável de
 * ambiente (CLAUDE.md §4: "Segredos não entram no repositório"), nunca em
 * {@code application.yml} (que este agente não edita — CLAUDE.md §3).
 *
 * <p>Nomes de variável alinhados com o esqueleto já deixado em
 * {@code .env.example} pela onda anterior ("Pagamentos (ADR-0007)...
 * Preenchido pelo backend-payments"): {@code STRIPE_API_KEY},
 * {@code STRIPE_WEBHOOK_SECRET}, {@code EUPAGO_API_KEY}. As restantes
 * (webhook secret do Eupago, credenciais IfthenPay, URLs base) seguem a
 * mesma convenção plana (sem prefixo {@code SERVIMATCH_}) — ficheiro
 * {@code .env.example} incompleto para estas; ver relatório desta onda
 * pedindo ao dono desse ficheiro que complete a lista abaixo.
 *
 * <p>Todos os valores por omissão são vazios/públicos (URLs de API); a
 * ausência de uma chave não impede o arranque — só impede chamadas reais
 * ao gateway em causa (falha no momento do uso, não no arranque, como o
 * resto do backend — ver {@code application.yml} `hikari.initialization-fail-timeout`).
 */
@Component
public class PaymentsProperties {

    @Value("${STRIPE_API_KEY:}")
    private String stripeApiKey;

    @Value("${STRIPE_WEBHOOK_SECRET:}")
    private String stripeWebhookSecret;

    @Value("${STRIPE_BASE_URL:https://api.stripe.com}")
    private String stripeBaseUrl;

    @Value("${EUPAGO_API_KEY:}")
    private String eupagoApiKey;

    @Value("${EUPAGO_WEBHOOK_SECRET:}")
    private String eupagoWebhookSecret;

    @Value("${EUPAGO_BASE_URL:https://clientes.eupago.pt/api}")
    private String eupagoBaseUrl;

    @Value("${IFTHENPAY_API_KEY:}")
    private String ifthenpayApiKey;

    @Value("${IFTHENPAY_WEBHOOK_SECRET:}")
    private String ifthenpayWebhookSecret;

    @Value("${IFTHENPAY_BASE_URL:https://ifthenpay.com/api}")
    private String ifthenpayBaseUrl;

    /** Tolerância ao *replay* na verificação de assinatura Stripe (timestamp do cabeçalho vs. agora). */
    @Value("${STRIPE_WEBHOOK_TOLERANCE_SECONDS:300}")
    private long stripeWebhookToleranceSeconds;

    /**
     * Política de {@code PAST_DUE} (CLAUDE.md: "estado de tolerância com
     * política explícita... não um limbo indefinido"). Janela total e
     * número de tentativas de pagamento falhadas toleradas antes do job de
     * varrimento cancelar automaticamente — ver
     * {@code internal.PastDueSweepJob}.
     */
    @Value("${SERVIMATCH_BILLING_PAST_DUE_GRACE_DAYS:7}")
    private long pastDueGraceDays;

    @Value("${SERVIMATCH_BILLING_PAST_DUE_MAX_ATTEMPTS:3}")
    private int pastDueMaxAttempts;

    public String stripeApiKey() {
        return stripeApiKey;
    }

    public String stripeWebhookSecret() {
        return stripeWebhookSecret;
    }

    public String stripeBaseUrl() {
        return stripeBaseUrl;
    }

    public long stripeWebhookToleranceSeconds() {
        return stripeWebhookToleranceSeconds;
    }

    public String eupagoApiKey() {
        return eupagoApiKey;
    }

    public String eupagoWebhookSecret() {
        return eupagoWebhookSecret;
    }

    public String eupagoBaseUrl() {
        return eupagoBaseUrl;
    }

    public String ifthenpayApiKey() {
        return ifthenpayApiKey;
    }

    public String ifthenpayWebhookSecret() {
        return ifthenpayWebhookSecret;
    }

    public String ifthenpayBaseUrl() {
        return ifthenpayBaseUrl;
    }

    public long pastDueGraceDays() {
        return pastDueGraceDays;
    }

    public int pastDueMaxAttempts() {
        return pastDueMaxAttempts;
    }

    // Setters usados apenas em testes (construção manual fora do contentor
    // Spring, para exercitar os adaptadores reais contra um stub HTTP local
    // ou com segredos de teste conhecidos — ver skill testcontainers-integration-test).

    public void setStripeApiKey(String stripeApiKey) {
        this.stripeApiKey = stripeApiKey;
    }

    public void setStripeWebhookSecret(String stripeWebhookSecret) {
        this.stripeWebhookSecret = stripeWebhookSecret;
    }

    public void setStripeBaseUrl(String stripeBaseUrl) {
        this.stripeBaseUrl = stripeBaseUrl;
    }

    public void setStripeWebhookToleranceSeconds(long stripeWebhookToleranceSeconds) {
        this.stripeWebhookToleranceSeconds = stripeWebhookToleranceSeconds;
    }

    public void setEupagoApiKey(String eupagoApiKey) {
        this.eupagoApiKey = eupagoApiKey;
    }

    public void setEupagoWebhookSecret(String eupagoWebhookSecret) {
        this.eupagoWebhookSecret = eupagoWebhookSecret;
    }

    public void setEupagoBaseUrl(String eupagoBaseUrl) {
        this.eupagoBaseUrl = eupagoBaseUrl;
    }

    public void setPastDueGraceDays(long pastDueGraceDays) {
        this.pastDueGraceDays = pastDueGraceDays;
    }

    public void setPastDueMaxAttempts(int pastDueMaxAttempts) {
        this.pastDueMaxAttempts = pastDueMaxAttempts;
    }
}

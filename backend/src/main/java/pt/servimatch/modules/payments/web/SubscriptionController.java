package pt.servimatch.modules.payments.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.billing.ProviderAccountResolver;
import pt.servimatch.modules.billing.Subscription;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.billing.SubscriptionPlan;
import pt.servimatch.modules.payments.CheckoutRequest;
import pt.servimatch.modules.payments.CheckoutResult;
import pt.servimatch.modules.payments.GatewayCode;
import pt.servimatch.modules.payments.PaymentGateway;
import pt.servimatch.modules.payments.internal.GatewayRegistry;
import pt.servimatch.modules.payments.internal.JdbcPaymentRepository;
import pt.servimatch.modules.payments.web.dto.CreateSubscriptionRequest;
import pt.servimatch.modules.payments.web.dto.MoneyDto;
import pt.servimatch.modules.payments.web.dto.PaymentReferenceDto;
import pt.servimatch.modules.payments.web.dto.SubscriptionCheckoutResponse;
import pt.servimatch.modules.payments.web.dto.SubscriptionResponse;
import pt.servimatch.platform.error.ProblemDetailsSupport;
import pt.servimatch.platform.error.ProblemType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code POST /v1/subscriptions} — inicia o pagamento de um plano
 * (checkout Stripe ou referência Eupago/IfthenPay). <b>Nunca</b> ativa a
 * subscrição aqui: devolve sempre {@code PENDING} — a transição para
 * {@code ACTIVE} só acontece via {@code PaymentWebhookController}, a
 * partir de um evento de gateway verificado (CLAUDE.md §4).
 *
 * <p>{@code GET /v1/subscriptions/me} — mesmo recurso (mesmo prefixo de
 * caminho), por isso vive na mesma classe: devolve a subscrição mais
 * recente do prestador autenticado, em qualquer estado (contrato:
 * "não só a ativa"). É <b>só leitura</b> — nunca decide gating. O
 * <em>gating</em> por subscrição é sempre resolvido no servidor por
 * {@link SubscriptionLifecycle#isVisibilityEligible}/{@code hasActiveSubscription}
 * (hoje: {@code matching}/{@code search}, ver ADR-0011); este endpoint só
 * espelha o estado para a UI o mostrar — nunca uma segunda fonte de
 * verdade (CLAUDE.md §4).
 *
 * <p>Vive em {@code payments} (não em {@code billing}) porque orquestra a
 * chamada ao gateway, respeitando a direção de dependência declarada em
 * ARQUITETURA §6.3 (`payments` depende de `subscriptions`/`billing`, não o
 * inverso).
 */
@RestController
public class SubscriptionController {

    private final SubscriptionLifecycle subscriptionLifecycle;
    private final ProviderAccountResolver providerAccountResolver;
    private final GatewayRegistry gatewayRegistry;
    private final JdbcPaymentRepository paymentRepository;

    public SubscriptionController(SubscriptionLifecycle subscriptionLifecycle,
                                   ProviderAccountResolver providerAccountResolver,
                                   GatewayRegistry gatewayRegistry,
                                   JdbcPaymentRepository paymentRepository) {
        this.subscriptionLifecycle = subscriptionLifecycle;
        this.providerAccountResolver = providerAccountResolver;
        this.gatewayRegistry = gatewayRegistry;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/v1/subscriptions/me")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Object> getMySubscription(@AuthenticationPrincipal Jwt jwt) {
        Optional<UUID> providerId = providerAccountResolver.resolveProviderId(jwt.getSubject());
        if (providerId.isEmpty()) {
            // Sem perfil de prestador resolvível: o contrato não distingue este
            // caso de "nunca subscreveu" com um corpo próprio (só 404 está
            // documentado para GET /v1/subscriptions/me) — mesma resposta.
            return subscriptionNotFound();
        }

        return subscriptionLifecycle.findMostRecentByProvider(providerId.get())
                .<ResponseEntity<Object>>map(subscription -> ResponseEntity.ok(SubscriptionResponse.from(subscription)))
                .orElseGet(this::subscriptionNotFound);
    }

    private ResponseEntity<Object> subscriptionNotFound() {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, "Subscrição não encontrada",
                "O prestador autenticado nunca subscreveu nenhum plano.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problemDetail);
    }

    @PostMapping("/v1/subscriptions")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Object> createSubscription(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody CreateSubscriptionRequest request) {
        Optional<UUID> providerId = providerAccountResolver.resolveProviderId(jwt.getSubject());
        if (providerId.isEmpty()) {
            return unprocessable("Perfil de prestador não encontrado para o utilizador autenticado.");
        }

        Optional<SubscriptionPlan> plan = subscriptionLifecycle.findPlan(request.planId());
        if (plan.isEmpty() || !plan.get().active()) {
            return unprocessable("Plano de subscrição inexistente ou inativo.");
        }

        Optional<GatewayCode> gatewayCode = GatewayCode.fromValue(request.gateway());
        Optional<PaymentGateway> gateway = gatewayCode.flatMap(gatewayRegistry::find);
        if (gateway.isEmpty()) {
            return unprocessable("Gateway de pagamento não suportado.");
        }

        Subscription subscription = subscriptionLifecycle.createPending(providerId.get(), plan.get().id(), gatewayCode.get().value());

        String returnUrl = (request.returnUrl() == null || request.returnUrl().isBlank())
                ? "https://app.servimatch.pt/prestador/subscricao"
                : request.returnUrl();
        CheckoutRequest checkoutRequest = new CheckoutRequest(
                subscription.id(), providerId.get(), subscription.id().toString(),
                plan.get().priceAmountCents(), plan.get().priceCurrency(), plan.get().name(), returnUrl);
        CheckoutResult checkout = gateway.get().startCheckout(checkoutRequest);

        String gatewayPaymentId = checkout.gatewayPaymentId() != null ? checkout.gatewayPaymentId() : subscription.id().toString();
        paymentRepository.insertPending(subscription.id(), providerId.get(), gatewayCode.get().value(),
                gatewayPaymentId, plan.get().priceAmountCents(), plan.get().priceCurrency(), Instant.now());

        if (checkout.gatewayCustomerId() != null || checkout.gatewaySubscriptionId() != null) {
            subscriptionLifecycle.attachGatewayIds(subscription.id(), checkout.gatewayCustomerId(), checkout.gatewaySubscriptionId());
        }

        PaymentReferenceDto referenceDto = checkout.paymentReference() == null ? null : new PaymentReferenceDto(
                checkout.paymentReference().entity(),
                checkout.paymentReference().reference(),
                new MoneyDto(checkout.paymentReference().amountCents(), checkout.paymentReference().currency()));

        SubscriptionCheckoutResponse body = new SubscriptionCheckoutResponse(
                subscription.id(), subscription.status().name(), checkout.checkoutUrl(), referenceDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private ResponseEntity<Object> unprocessable(String detail) {
        ProblemDetail problemDetail = ProblemDetailsSupport.of(
                HttpStatus.UNPROCESSABLE_ENTITY, ProblemType.VALIDATION, "Pedido não processável", detail);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problemDetail);
    }
}

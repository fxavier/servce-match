package pt.servimatch.modules.payments;

import java.util.Map;

/**
 * Porta de domínio (ADR-0007): o domínio conhece "cobrar", "renovar",
 * "cancelar" e eventos — nunca Stripe ou Eupago diretamente. Duas
 * implementações reais: {@code internal.stripe.StripePaymentGateway}
 * (cartão, auto-renew nativo) e
 * {@code internal.eupago.EupagoIfthenPayPaymentGateway} (MB WAY/Multibanco,
 * *invoice-based* — ver ADR-0007 §"Recorrência por método").
 *
 * <p><b>Ordem obrigatória ao receber um webhook</b> (skill
 * {@code payment-webhook-hardening}): 1) ler o corpo em bruto; 2)
 * {@link #verifySignature} sobre esses bytes exatos, em tempo constante —
 * falha aqui e nada mais deste tipo é chamado; 3) só depois
 * {@link #parseEvent}. Nunca inverter esta ordem.
 */
public interface PaymentGateway {

    GatewayCode code();

    /** Inicia o pagamento de um plano: sessão de checkout (Stripe) ou referência (Eupago/IfthenPay). */
    CheckoutResult startCheckout(CheckoutRequest request);

    /**
     * Verifica a assinatura do gateway sobre o corpo em bruto (bytes
     * exatos recebidos, antes de qualquer parse/desserialização). Tem de
     * ser feita em comparação de tempo constante.
     */
    boolean verifySignature(byte[] rawBody, Map<String, String> headers);

    /**
     * Extrai um identificador de evento estável para idempotência, sem
     * validar nada — chamado antes da verificação de assinatura, mas
     * <b>só é usado como chave de deduplicação real</b>
     * ({@code payment_gateway_event.raw_event_id}, {@code UNIQUE(gateway,
     * raw_event_id)}) quando a assinatura se confirma válida. Se a
     * assinatura falhar, o evento é gravado para auditoria sob uma chave
     * sintética própria (quarentena), nunca sob este id — de outro modo um
     * evento forjado com um {@code raw_event_id} previsível (ex.:
     * Eupago/IfthenPay, cujo id deriva de dados que o próprio remetente
     * conhece) poderia ocupar a linha do evento genuíno e bloqueá-lo para
     * sempre. Nunca aciona efeito de domínio.
     */
    String peekEventId(byte[] rawBody);

    /**
     * Interpreta o corpo já verificado como um evento de domínio
     * normalizado. Só deve ser chamado depois de {@link #verifySignature}
     * devolver {@code true}.
     */
    GatewayEvent parseEvent(byte[] rawBody, Map<String, String> headers);

    /** Consulta o estado atual de um pagamento/subscrição junto do gateway — fonte de verdade para a reconciliação. */
    ReconciledPaymentState reconcile(String correlationReference);
}

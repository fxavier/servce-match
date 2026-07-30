package pt.servimatch.modules.billing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * API pública do módulo {@code billing}: planos, criação de subscrição e
 * transições de ciclo de vida (ARQUITETURA §12.1). É a <b>única</b> forma
 * como o módulo {@code payments} (que a consome, ver ARQUITETURA §6.3:
 * {@code payments} depende de {@code subscriptions}/{@code billing}) e
 * quaisquer outros módulos futuros devem mutar ou ler o estado de uma
 * subscrição — nunca por leitura/escrita direta às tabelas
 * {@code subscription}/{@code subscription_plan} (CLAUDE.md §4).
 *
 * <p><b>Todas as transições de estado exigem um evento de gateway já
 * verificado</b> (assinatura válida) do lado de quem chama. Nenhum método
 * aqui aceita "porque o cliente disse que pagou" — apenas o processador de
 * webhook, depois de verificar a assinatura, ou o job de reconciliação,
 * depois de confirmar o estado junto do gateway, devem invocar
 * {@link #activate}/{@link #markPastDue}/{@link #expire}/{@link #cancel}.
 *
 * <p>Implementações têm de respeitar o invariante de estados terminais:
 * {@code EXPIRED} e {@code CANCELLED} nunca transitam para outro estado
 * (proteção contra eventos tardios/fora de ordem que tentem "ressuscitar"
 * uma subscrição já terminada).
 */
public interface SubscriptionLifecycle {

    java.util.List<SubscriptionPlan> listActivePlans();

    Optional<SubscriptionPlan> findPlan(UUID planId);

    Optional<Subscription> findById(UUID subscriptionId);

    Optional<Subscription> findByProvider(UUID providerId);

    /**
     * Subscrição mais recente do prestador, em <b>qualquer</b> estado —
     * inclui {@code EXPIRED}/{@code CANCELLED} (contrato de
     * {@code GET /v1/subscriptions/me}: "subscrição mais recente ... em
     * qualquer estado ... não só a ativa"). "Mais recente" é
     * {@code created_at DESC} com desempate determinístico — ver
     * {@code JdbcSubscriptionRepository.findMostRecentByProvider}.
     * {@code Optional.empty()} apenas quando o prestador nunca teve nenhuma
     * linha em {@code subscription} (nunca subscreveu).
     *
     * <p><b>Não é gating.</b> Ao contrário de {@link #findByProvider}, não
     * filtra por estado — devolve o histórico completo, uma linha. Para
     * decidir se o prestador pode operar, usar {@link #isVisibilityEligible}
     * ou {@link #hasActiveSubscription}; nunca inferir gating a partir do
     * {@code status} devolvido aqui num consumidor fora deste módulo — o
     * servidor decide, a UI só espelha (CLAUDE.md §4).
     */
    Optional<Subscription> findMostRecentByProvider(UUID providerId);

    /**
     * Correlaciona pelo identificador de subscrição do lado do gateway
     * (ex.: renovações automáticas Stripe, cuja fatura só traz
     * {@code subscription}, não o nosso {@code client_reference_id}).
     */
    Optional<Subscription> findByGatewaySubscriptionId(String gateway, String gatewaySubscriptionId);

    /**
     * Cria uma subscrição em {@code PENDING} para o {@code providerId} e
     * {@code planId} indicados. Se o prestador já tiver uma subscrição em
     * estado não-terminal, a violação do índice único parcial
     * ({@code uq_subscription_provider_non_terminal}) propaga como
     * {@link org.springframework.dao.DataIntegrityViolationException},
     * mapeada para {@code 409} pelo {@code GlobalExceptionHandler} —
     * garantia ao nível da base de dados, não apenas verificação em
     * memória (mesma corrida possível em duplo-clique/checkout duplicado).
     */
    Subscription createPending(UUID providerId, UUID planId, String gateway);

    /** Associa identificadores do gateway (cliente/subscrição) sem mudar o estado. */
    Subscription attachGatewayIds(UUID subscriptionId, String gatewayCustomerId, String gatewaySubscriptionId);

    /**
     * Aplica um pagamento confirmado: {@code PENDING}/{@code PAST_DUE} →
     * {@code ACTIVE}, com o novo período. No-op (devolve o estado atual,
     * sem publicar evento) se a subscrição já estiver num estado terminal
     * ou se {@code eventOccurredAt} for anterior ao já aplicado (guarda
     * contra desordem é responsabilidade adicional de quem chama, ao nível
     * do evento de pagamento — ver {@code modules.payments}; este método
     * garante apenas o invariante de estado terminal).
     */
    Subscription activate(UUID subscriptionId, Instant periodStart, Instant periodEnd, Instant eventOccurredAt);

    /** Aplica uma falha de pagamento: {@code PENDING}/{@code ACTIVE}/{@code PAST_DUE} → {@code PAST_DUE}. */
    Subscription markPastDue(UUID subscriptionId, Instant eventOccurredAt);

    /** {@code ACTIVE} → {@code EXPIRED} (fim de período sem renovação). No-op se já terminal. */
    Subscription expire(UUID subscriptionId, Instant eventOccurredAt);

    /** {@code ACTIVE}/{@code PAST_DUE} → {@code CANCELLED}. No-op se já terminal. */
    Subscription cancel(UUID subscriptionId, Instant eventOccurredAt);

    /** Gating estrito usado pelo predicado de matching (ARQUITETURA §10.3): só {@code ACTIVE}. */
    boolean hasActiveSubscription(UUID providerId);

    /**
     * Gating de visibilidade — P1 "elegibilidade de operação" (ARQUITETURA
     * §12.1, ADR-0011 D2/D4/D5): {@code ACTIVE} ou {@code PAST_DUE} (grace)
     * <b>e</b> {@code current_period_end} ainda não passado (com tolerância
     * de relógio explícita, ver
     * {@link SubscriptionVisibilitySql#DEFAULT_PERIOD_END_TOLERANCE_SECONDS}).
     * O segundo requisito evita a falha aberta em que uma subscrição cujo
     * período já terminou continua a conceder visibilidade só porque o job
     * de expiração ({@code payments.internal.SubscriptionPeriodEndJob}) ainda
     * não correu — o predicado deixa de depender desse job para negar
     * acesso.
     *
     * <p>Este é o método que a composição de P1 em {@code providers}
     * (ADR-0011 D3, {@code ProvidersApi.checkEligibility}) deve chamar — não
     * uma reimplementação local da regra.
     */
    boolean isVisibilityEligible(UUID providerId);

    /** Subscrições {@code ACTIVE} cujo {@code current_period_end} já passou — candidatas ao job de expiração. */
    java.util.List<Subscription> findActiveSubscriptionsPastPeriodEnd(Instant now);

    /** Subscrições atualmente em {@code PAST_DUE} — candidatas ao varrimento de janela/tentativas. */
    java.util.List<Subscription> findPastDueSubscriptions();

    /** Todas as subscrições no estado indicado — usado pela reconciliação para varrer candidatas por estado. */
    java.util.List<Subscription> findByStatus(SubscriptionStatus status);
}

package pt.servimatch.modules.requests;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados de {@code ServiceRequest} (ARQUITETURA §4.3):
 *
 * <pre>
 * DRAFT ──publicar──▶ PUBLISHED ──1ª proposta──▶ IN_NEGOTIATION ──aceitar proposta──▶ CONFIRMED
 *   │                    │                              │                                  │
 *   │                    └──────────────────────────────┴───────────► CANCELLED           ▼
 *   └──► CANCELLED                                                                     IN_PROGRESS
 *                                                                                          │
 *                                                                                          ▼
 *                                                                                      COMPLETED
 * </pre>
 *
 * <p>Explícita e centralizada em vez de {@code if} espalhados pelos
 * serviços (CLAUDE.md): qualquer transição passa por {@link #verifyCanTransitionTo}.
 * Nesta onda só {@code DRAFT→PUBLISHED} (publishRequest), {@code
 * PUBLISHED→IN_NEGOTIATION} (1ª proposta) e {@code IN_NEGOTIATION→CONFIRMED}
 * (aceitar proposta) são alcançadas por um endpoint; {@code IN_PROGRESS},
 * {@code COMPLETED} e {@code CANCELLED} estão modeladas e testadas mas sem
 * endpoint que as dispare ainda (nenhum dos 9 endpoints deste agente as
 * aciona) — ficam prontas para quando existir.
 */
public enum ServiceRequestStatus {

    DRAFT,
    PUBLISHED,
    IN_NEGOTIATION,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<ServiceRequestStatus, Set<ServiceRequestStatus>> ALLOWED = new EnumMap<>(ServiceRequestStatus.class);

    static {
        ALLOWED.put(DRAFT, EnumSet.of(PUBLISHED, CANCELLED));
        ALLOWED.put(PUBLISHED, EnumSet.of(IN_NEGOTIATION, CANCELLED));
        ALLOWED.put(IN_NEGOTIATION, EnumSet.of(CONFIRMED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(IN_PROGRESS));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(ServiceRequestStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(ServiceRequestStatus.class));
    }

    public boolean canTransitionTo(ServiceRequestStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** @throws IllegalStateException se a transição não é permitida a partir deste estado. */
    public void verifyCanTransitionTo(ServiceRequestStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Transição ilegal de %s para %s".formatted(this, target));
        }
    }
}

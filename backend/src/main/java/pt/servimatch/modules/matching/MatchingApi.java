package pt.servimatch.modules.matching;

import java.util.Set;
import java.util.UUID;
import pt.servimatch.modules.geo.GeoPoint;

/**
 * API pública do módulo de matching: o predicado central de elegibilidade
 * prestador↔pedido (ADR-0004 §10.3) — subscrição ativa, aprovação, cobertura
 * de zona (raio ou região administrativa) e categoria trabalhada.
 *
 * <p>Consumida por {@code search} (filtro geográfico de
 * {@code GET /v1/search/providers}) e — quando existir a tempo —
 * por {@code backend-domain} em {@code GET /v1/providers/me/requests}
 * (ver relatório da tarefa).
 */
public interface MatchingApi {

    /**
     * O prestador indicado é elegível (cobre a categoria e a
     * localização/região)? Verificação pontual, para um único prestador —
     * não usar em ciclo sobre muitos prestadores (usar
     * {@link #findEligibleProviderIds} nesse caso).
     */
    boolean isEligible(EligibilityQuery query);

    /**
     * Todos os prestadores elegíveis para uma categoria e uma
     * localização/região — o predicado central do ADR-0004, devolvido como
     * conjunto (sem iterar por prestador). {@code point} e
     * {@code regionCode} são ambos opcionais; sem nenhum dos dois devolve
     * sempre um conjunto vazio (não há cobertura possível).
     */
    Set<UUID> findEligibleProviderIds(UUID categoryId, GeoPoint point, String regionCode);

    /**
     * De um conjunto de pedidos candidatos (já filtrados pelo chamador por
     * categoria/estado — este módulo não conhece essas regras), devolve os
     * que o prestador indicado cobre geograficamente. Not-N+1: uma única
     * consulta para todo o lote, não uma por pedido.
     *
     * <p>Pensado para {@code GET /v1/providers/me/requests}
     * (backend-domain): a listagem de pedidos elegíveis recebidos por um
     * prestador. Se {@code candidateRequestIds} estiver vazio devolve um
     * conjunto vazio sem consultar a base de dados.
     */
    Set<UUID> filterEligibleRequestIds(UUID providerId, Set<UUID> candidateRequestIds);
}

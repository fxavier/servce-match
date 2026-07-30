package pt.servimatch.modules.billing;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fragmento SQL único e reutilizável do predicado "esta subscrição concede
 * visibilidade ao prestador agora" (ADR-0011 D4/D5) — à imagem de
 * {@code geo.CoverageSql}: publicado aqui para que nenhum consumidor volte a
 * copiar a lista de estados ou a comparação de período à mão.
 *
 * <h2>Porquê existe (ADR-0011)</h2>
 * <p>A auditoria da Onda 2 encontrou a mesma regra escrita de três formas
 * diferentes: {@code matching.EligibilityRepository} e
 * {@code search.ProviderSearchRepository} tinham {@code status='ACTIVE'}
 * (sem o *grace* de {@code PAST_DUE} prometido em ARQUITETURA §12.1), e
 * {@code billing.SubscriptionLifecycle.isVisibilityEligible} tinha
 * {@code ACTIVE ∨ PAST_DUE} mas sem verificar {@code current_period_end} —
 * o que a deixava "aberta" caso o job de expiração atrasasse ou falhasse
 * (ver {@code internal.SubscriptionPeriodEndJob}). Este ficheiro é o único
 * sítio onde a regra é escrita; qualquer alteração à semântica de
 * visibilidade (ex.: adicionar um estado) muda-se aqui e propaga-se a todos
 * os consumidores.
 *
 * <h2>Estados</h2>
 * <p>{@link #GRANTING_STATUSES_SQL_LIST} é derivada, em tempo de
 * carregamento da classe, de {@link SubscriptionStatus#grantsVisibility()} —
 * não é um literal paralelo que possa divergir do enum: se
 * {@code grantsVisibility()} mudar, esta lista muda com ele, sem edição
 * manual.
 *
 * <h2>Fim de período (D5)</h2>
 * <p>Um estado {@code ACTIVE}/{@code PAST_DUE} sozinho não basta: sem
 * verificar {@code current_period_end}, uma subscrição cujo período já
 * terminou continua a conceder visibilidade até o job de expiração correr —
 * uma falha <b>aberta</b> num invariante de segurança (CLAUDE.md §4). O
 * predicado exige por isso também {@code current_period_end >= now() - tolerância}.
 * {@code current_period_end} é anulável (V11): uma subscrição
 * {@code PAST_DUE} que nunca chegou a ter período (ex.: {@code PENDING} cujo
 * primeiro pagamento falhou) tem {@code current_period_end IS NULL}, e a
 * comparação com {@code NULL} dá desconhecido em SQL — o que
 * <b>nega</b> corretamente o acesso (não se "corrige" com {@code COALESCE}
 * nem com um período infinito; ver ADR-0011 D5).
 *
 * <p>A tolerância é sempre <b>explícita</b> — vinculada como parâmetro
 * nomeado {@code :visibilityToleranceSeconds}, nunca implícita num
 * {@code now()} de aplicação comparado a um {@code now()} de base de dados
 * de outra instância. {@link #DEFAULT_PERIOD_END_TOLERANCE_SECONDS} é zero:
 * o alargamento de janela de tolerância a pagamento em atraso já existe e é
 * governado só por {@code billing} (a duração do <em>grace</em> de
 * {@code PAST_DUE} é imposta pela transição {@code PAST_DUE → CANCELLED},
 * nunca por quem lê este predicado — ADR-0011 D5); esta tolerância cobre
 * apenas desvio de relógio na fronteira exata do período, não uma segunda
 * política de <em>grace</em>.
 *
 * <h2>Uso</h2>
 * <p>Este predicado só resolve <b>P1 — elegibilidade de operação</b>
 * (ADR-0011 D2): não inclui {@code approval_status} (facto de
 * {@code providers}) nem categoria/cobertura geográfica (P2, ADR-0004
 * §10.3). Embutir {@link #grantsVisibilityPredicate(String)} numa consulta
 * que junta {@code subscription}, vinculando {@code :visibilityToleranceSeconds}
 * (tipicamente {@link #DEFAULT_PERIOD_END_TOLERANCE_SECONDS}). Consumidores
 * previstos por ADR-0011 D4: {@code matching.EligibilityRepository} e
 * {@code search.ProviderSearchRepository}, que hoje escrevem
 * {@code status = 'ACTIVE'} à mão — pedido para o agente
 * {@code backend-matching} trocar por este fragmento, o que exige
 * {@code allowedDependencies} desses módulos passar a incluir
 * {@code modules.billing} (pedido ao {@code backend-platform}, nunca decisão
 * unilateral do consumidor — CLAUDE.md §3).
 */
public final class SubscriptionVisibilitySql {

    /**
     * Tolerância por omissão (segundos) da comparação de
     * {@code current_period_end}. Zero — ver javadoc da classe sobre porque
     * não é aqui que vive o <em>grace</em> de {@code PAST_DUE}.
     */
    public static final int DEFAULT_PERIOD_END_TOLERANCE_SECONDS = 0;

    /** Estados que concedem visibilidade agora, derivados de {@link SubscriptionStatus#grantsVisibility()}. */
    public static final Set<SubscriptionStatus> GRANTING_STATUSES = Arrays.stream(SubscriptionStatus.values())
            .filter(SubscriptionStatus::grantsVisibility)
            .collect(Collectors.toUnmodifiableSet());

    /** {@link #GRANTING_STATUSES}, pronta a embutir num {@code IN (...)} SQL — ex.: {@code 'ACTIVE','PAST_DUE'}. */
    public static final String GRANTING_STATUSES_SQL_LIST = GRANTING_STATUSES.stream()
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(","));

    /**
     * Fragmento de predicado pronto a embutir num {@code WHERE}/{@code JOIN ... ON}.
     * Requer o parâmetro nomeado {@code :visibilityToleranceSeconds} (inteiro,
     * segundos) vinculado pelo chamador.
     *
     * @param subscriptionTableAlias alias da tabela/junção {@code subscription} na
     *                                consulta do chamador (ex.: {@code "s"}); {@code null} ou
     *                                vazio se a consulta não tiver alias (tabela referida sem
     *                                qualificador).
     */
    public static String grantsVisibilityPredicate(String subscriptionTableAlias) {
        String prefix = (subscriptionTableAlias == null || subscriptionTableAlias.isBlank())
                ? ""
                : subscriptionTableAlias.trim() + ".";
        return prefix + "status IN (" + GRANTING_STATUSES_SQL_LIST + ")"
                + " AND " + prefix + "current_period_end >= (now() - (:visibilityToleranceSeconds::int || ' seconds')::interval)";
    }

    private SubscriptionVisibilitySql() {
    }
}

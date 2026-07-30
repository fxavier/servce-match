package pt.servimatch.modules.billing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que o fragmento único (ADR-0011 D4) fica sempre derivado de
 * {@link SubscriptionStatus#grantsVisibility()} — nunca um literal paralelo
 * que possa divergir do enum se este for alterado no futuro.
 */
class SubscriptionVisibilitySqlTest {

    @Test
    void grantingStatusesMatchEnumGrantsVisibility() {
        assertThat(SubscriptionVisibilitySql.GRANTING_STATUSES)
                .containsExactlyInAnyOrder(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            assertThat(SubscriptionVisibilitySql.GRANTING_STATUSES.contains(status))
                    .as("estado %s", status)
                    .isEqualTo(status.grantsVisibility());
        }
    }

    @Test
    void predicateQualifiesColumnsWithGivenAlias() {
        String predicate = SubscriptionVisibilitySql.grantsVisibilityPredicate("s");

        assertThat(predicate)
                .contains("s.status IN (")
                .contains("s.current_period_end >= (now() - (:visibilityToleranceSeconds::int || ' seconds')::interval)")
                .contains(SubscriptionVisibilitySql.GRANTING_STATUSES_SQL_LIST);
    }

    @Test
    void predicateWithoutAliasUsesBareColumnNames() {
        String predicate = SubscriptionVisibilitySql.grantsVisibilityPredicate(null);

        assertThat(predicate).startsWith("status IN (");
        assertThat(predicate).doesNotContain(".status").doesNotContain(".current_period_end");
    }
}

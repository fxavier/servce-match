package pt.servimatch.modules.billing.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.servimatch.modules.billing.SubscriptionLifecycle;
import pt.servimatch.modules.billing.web.dto.SubscriptionPlanResponse;

import java.util.List;

/**
 * {@code GET /v1/subscription-plans} — público (contrato {@code security: []},
 * já refletido em {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}).
 */
@RestController
public class SubscriptionPlanController {

    private final SubscriptionLifecycle subscriptionLifecycle;

    public SubscriptionPlanController(SubscriptionLifecycle subscriptionLifecycle) {
        this.subscriptionLifecycle = subscriptionLifecycle;
    }

    @GetMapping("/v1/subscription-plans")
    public List<SubscriptionPlanResponse> listSubscriptionPlans() {
        return subscriptionLifecycle.listActivePlans().stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }
}

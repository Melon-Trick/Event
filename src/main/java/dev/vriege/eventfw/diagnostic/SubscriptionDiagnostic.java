package dev.vriege.eventfw.diagnostic;

import dev.vriege.eventfw.listener.Subscription;
import java.util.Objects;

/**
 * Weakly consistent inspection of an ordered type-matching candidate. Inspection never runs handlers or filters.
 *
 * @param subscription live handle, which may change state after inspection
 * @param eligibility structural eligibility at inspection time
 */
public record SubscriptionDiagnostic(Subscription subscription, Eligibility eligibility) {
    /** Validates both diagnostic components. */
    public SubscriptionDiagnostic {
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(eligibility, "eligibility");
    }
}

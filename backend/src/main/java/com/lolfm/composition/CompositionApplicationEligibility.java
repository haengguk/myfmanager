package com.lolfm.composition;

/** Whether an observed attempt has an approved, existing score domain for future gain screening. */
public enum CompositionApplicationEligibility {
    ELIGIBLE_EXISTING_SCORE_DOMAIN,
    INELIGIBLE_NO_EXISTING_SCORE_DOMAIN,
    INELIGIBLE_NOT_SCORE_PRODUCING_ATTEMPT,
    INELIGIBLE_AMBIGUOUS_APPLICATION_POINT,
    DEFERRED_NO_STRUCTURED_ACTION;

    public boolean eligible() {
        return this == ELIGIBLE_EXISTING_SCORE_DOMAIN;
    }
}

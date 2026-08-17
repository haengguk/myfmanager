package com.lolfm.composition;

/** Pure zero-reference severity routing for Phase 13D-4C.5. */
public record CompositionSeverityDecisionAdjustment(
        String applicationKey,
        double baselineSeverityInput,
        double severityModifier,
        double finalSeverityInput
) {
    public CompositionSeverityDecisionAdjustment {
        if (!Double.isFinite(baselineSeverityInput) || !Double.isFinite(severityModifier)
                || !Double.isFinite(finalSeverityInput)) {
            throw new IllegalArgumentException("Severity adjustment values must be finite");
        }
        if (severityModifier != 0.0 || finalSeverityInput != baselineSeverityInput) {
            throw new IllegalArgumentException("Phase 13D-4C.5 severity must remain a zero reference");
        }
    }
}

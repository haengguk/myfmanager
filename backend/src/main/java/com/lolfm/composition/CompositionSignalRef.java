package com.lolfm.composition;

/** Structured reference to a composition signal. */
public sealed interface CompositionSignalRef
        permits PatternSignalRef, CapabilitySignalRef {
    String stableId();

    default double value(CompositionInteractionInput input) {
        if (this instanceof PatternSignalRef pattern) {
            return input.patternReadiness().get(pattern.pattern());
        }
        return input.capabilityCoverage().get(((CapabilitySignalRef) this).capability());
    }
}

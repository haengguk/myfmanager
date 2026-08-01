package com.lolfm.composition;

import java.util.Objects;

public record CapabilitySignalRef(CompositionCapability capability) implements CompositionSignalRef {
    public CapabilitySignalRef {
        Objects.requireNonNull(capability, "capability");
    }

    @Override
    public String stableId() {
        return "CAPABILITY/" + capability.name();
    }
}

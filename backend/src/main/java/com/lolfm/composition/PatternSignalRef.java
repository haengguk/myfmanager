package com.lolfm.composition;

import java.util.Objects;

public record PatternSignalRef(CompositionPattern pattern) implements CompositionSignalRef {
    public PatternSignalRef {
        Objects.requireNonNull(pattern, "pattern");
    }

    @Override
    public String stableId() {
        return "PATTERN/" + pattern.name();
    }
}

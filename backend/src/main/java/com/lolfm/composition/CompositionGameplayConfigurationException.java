package com.lolfm.composition;

/** Explicit configuration failure; CANDIDATE never silently falls back. */
public final class CompositionGameplayConfigurationException extends IllegalStateException {
    private final String code;

    public CompositionGameplayConfigurationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}

package com.lolfm.application;

import java.util.Objects;

/** Typed failure for expected roster and Draft rejection at the real-match preflight boundary. */
public final class RealDraftMatchPreflightException extends IllegalArgumentException {
    public RealDraftMatchPreflightException(String message) {
        super(message);
    }

    public RealDraftMatchPreflightException(IllegalArgumentException cause) {
        super(Objects.requireNonNull(cause, "cause").getMessage(), cause);
    }
}

package com.lolfm.league;

import java.sql.SQLTransientException;
import java.util.Objects;
import org.springframework.dao.TransientDataAccessException;

/** Typed job failure policy. Exception messages never determine retryability. */
final class LeagueJobFailureClassifier {
    private LeagueJobFailureClassifier() {}

    static Failure classify(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        for (Throwable cursor = failure; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof ExplicitFailure explicit) {
                return new Failure(explicit.failureClass(), explicit.failureCode());
            }
            if (cursor instanceof TransientDataAccessException
                    || cursor instanceof SQLTransientException) {
                return new Failure(LeaguePersistenceState.FailureClass.TRANSIENT,
                        "TRANSIENT_DATA_ACCESS_FAILURE");
            }
        }
        return new Failure(LeaguePersistenceState.FailureClass.DETERMINISTIC,
                "AUTOMATED_SERIES_EXECUTION_FAILED");
    }

    static ExplicitFailure transientWorker(String failureCode, Throwable cause) {
        return new ExplicitFailure(LeaguePersistenceState.FailureClass.TRANSIENT,
                failureCode, cause);
    }

    static ExplicitFailure deterministic(String failureCode, Throwable cause) {
        return new ExplicitFailure(LeaguePersistenceState.FailureClass.DETERMINISTIC,
                failureCode, cause);
    }

    static Failure deterministicResult(String failureCode) {
        return new Failure(LeaguePersistenceState.FailureClass.DETERMINISTIC,
                stableCode(failureCode, "AUTOMATED_SERIES_DETERMINISTIC_FAILURE"));
    }

    private static String stableCode(String value, String fallback) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{2,159}")
                ? value : fallback;
    }

    record Failure(
            LeaguePersistenceState.FailureClass failureClass,
            String failureCode
    ) {}

    static final class ExplicitFailure extends RuntimeException {
        private final LeaguePersistenceState.FailureClass failureClass;
        private final String failureCode;

        private ExplicitFailure(
                LeaguePersistenceState.FailureClass failureClass,
                String failureCode,
                Throwable cause
        ) {
            super(stableCode(failureCode, "WORKER_EXECUTION_FAILURE"), cause);
            this.failureClass = Objects.requireNonNull(failureClass, "failureClass");
            this.failureCode = stableCode(failureCode, "WORKER_EXECUTION_FAILURE");
        }

        LeaguePersistenceState.FailureClass failureClass() { return failureClass; }
        String failureCode() { return failureCode; }
    }
}

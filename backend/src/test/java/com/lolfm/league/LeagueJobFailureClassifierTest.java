package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;

class LeagueJobFailureClassifierTest {
    @Test
    void classifiesByTypeAndExplicitCodeNeverByMessageText() {
        var sql = LeagueJobFailureClassifier.classify(new RuntimeException(
                new SQLTransientConnectionException("message has no retry token")));
        assertThat(sql.failureClass())
                .isEqualTo(LeaguePersistenceState.FailureClass.TRANSIENT);
        assertThat(sql.failureCode()).isEqualTo("TRANSIENT_DATA_ACCESS_FAILURE");

        var worker = LeagueJobFailureClassifier.classify(
                LeagueJobFailureClassifier.transientWorker(
                        "WORKER_TEMPORARY_UNAVAILABLE", new RuntimeException("fatal")));
        assertThat(worker.failureClass())
                .isEqualTo(LeaguePersistenceState.FailureClass.TRANSIENT);
        assertThat(worker.failureCode()).isEqualTo("WORKER_TEMPORARY_UNAVAILABLE");

        var misleading = LeagueJobFailureClassifier.classify(
                new IllegalStateException("TRANSIENT_TIMEOUT_CONNECTION"));
        assertThat(misleading.failureClass())
                .isEqualTo(LeaguePersistenceState.FailureClass.DETERMINISTIC);
        assertThat(misleading.failureCode())
                .isEqualTo("AUTOMATED_SERIES_EXECUTION_FAILED");
    }
}

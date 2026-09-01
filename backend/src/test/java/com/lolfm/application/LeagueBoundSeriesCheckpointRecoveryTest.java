package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.TeamSide;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeagueBoundSeriesCheckpointRecoveryTest {
    @Test
    void processLossAfterSimulationReservationReleasesOnlyTheLostLease() {
        Instant created = Instant.parse("2026-08-01T00:00:00Z");
        String history = SeriesIdentity.historyHash(0, Set.of());
        String payload = SeriesIdentity.sha256("payload\n");
        SeriesSimulationReservation reservation = new SeriesSimulationReservation(
                "reservation-token", "simulate-command", payload, 1, 20,
                created, created.plusSeconds(900), SeriesIdentity.sha256("input\n"));
        SeriesGame game = new SeriesGame("game-1", 1, "GEN", "T1", TeamSide.BLUE,
                73L, List.of(), history, SeriesGameStatus.SIMULATION_IN_PROGRESS,
                null, 1, null, reservation, null, null, null);
        SeriesCommandReceipt command = new SeriesCommandReceipt(
                "simulate-command", "SIMULATE", payload,
                SeriesCommandCompletion.IN_PROGRESS, 1, SeriesStatus.ACTIVE,
                1, game.gameId(), game.status(), null, null, null,
                null, reservation.token(), null, null, false);
        SeriesAggregate persisted = new SeriesAggregate(
                "series_" + "1".repeat(64), 1, SeriesStatus.ACTIVE, null,
                SeriesFormat.BO3, "GEN", "T1", "GEN", "GEN", "73", 73L,
                Map.of("GEN", 0, "T1", 0), List.of(game), Set.of(), history,
                null, created, created, created.plusSeconds(7200),
                Map.of(command.commandId(), command), SeriesOrigin.LEAGUE_BOUND,
                "2".repeat(64), "GEN");

        SeriesAggregate recovered =
                JdbcLeagueBoundSeriesCheckpointAdapter.recoverLostReservation(persisted);

        assertThat(recovered.revision()).isEqualTo(persisted.revision());
        assertThat(recovered.rootSeed()).isEqualTo(persisted.rootSeed());
        assertThat(recovered.historyHash()).isEqualTo(persisted.historyHash());
        assertThat(recovered.score()).isEqualTo(persisted.score());
        assertThat(recovered.currentGame().reservation()).isNull();
        assertThat(recovered.currentGame().status()).isEqualTo(
                SeriesGameStatus.SIMULATION_FAILED_RETRYABLE);
        assertThat(recovered.commandReceipts().get("simulate-command").completion())
                .isEqualTo(SeriesCommandCompletion.FAILED);
        assertThat(recovered.commandReceipts().get("simulate-command").retryable())
                .isTrue();
    }
}

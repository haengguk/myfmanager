package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.lolfm.controller.SeriesApiV1Exception;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeriesLifecycleServiceTest {
    @Test
    void cancelInvalidatesChildAndReservationAndExactReplayMutatesNothing() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);
        SeriesRepository repository = new SeriesRepository(
                clock, new SeriesLifecycleConfiguration());
        SeriesAggregate reserved = reservedAggregate(repository);
        repository.create("create", "create-payload", reserved);
        SeriesLifecycleService lifecycle = new SeriesLifecycleService(
                mock(LckTeamAssembler.class), mock(PlayerControlledDraftEngine.class),
                repository, mock(SeriesMatchExecutor.class));
        SeriesApiV1Dtos.CancelRequest request = new SeriesApiV1Dtos.CancelRequest(
                SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA, 0, "cancel");

        lifecycle.cancel(reserved.seriesId(), request);
        SeriesAggregate cancelled = lifecycle.get(reserved.seriesId());
        assertThat(cancelled.revision()).isOne();
        assertThat(cancelled.status()).isEqualTo(SeriesStatus.CANCELLED);
        assertThat(cancelled.currentGame().status())
                .isEqualTo(SeriesGameStatus.DRAFT_CANCELLED);
        assertThat(cancelled.currentGame().reservation()).isNull();
        assertThat(cancelled.currentGame().childDraft().status())
                .isEqualTo(PlayerDraftSessionStatus.CANCELLED);
        assertThat(cancelled.score()).containsOnlyKeys("GEN", "T1")
                .containsValues(0, 0);
        assertThat(cancelled.consumedPicks()).isEmpty();

        lifecycle.cancel(reserved.seriesId(), request);
        SeriesAggregate replayed = lifecycle.get(reserved.seriesId());
        assertThat(replayed).isEqualTo(cancelled);

        assertThatThrownBy(() -> lifecycle.cancel(reserved.seriesId(),
                new SeriesApiV1Dtos.CancelRequest(
                        SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA, 1, "cancel")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error -> {
                    assertThat(error.code()).isEqualTo(
                            "SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
                    assertThat(error.currentRevision()).isEqualTo(1L);
                });
        assertThat(lifecycle.get(reserved.seriesId())).isEqualTo(cancelled);
    }

    private static SeriesAggregate reservedAggregate(SeriesRepository repository) {
        Instant now = repository.now();
        String historyHash = SeriesIdentity.historyHash(0, Set.of());
        var progress = mock(PlayerControlledDraftEngine.Progress.class);
        SeriesChildDraft child = new SeriesChildDraft(
                "draft", 1, 10, PlayerDraftSessionStatus.COMPLETED,
                now, now, repository.childExpiresAt(now, repository.parentExpiresAt(now)),
                progress);
        SeriesSimulationReservation reservation = new SeriesSimulationReservation(
                "token", "simulate", "simulate-payload", 0, 10, now,
                repository.reservationExpiresAt(now), "binding");
        SeriesGame game = new SeriesGame(
                "game", 1, "GEN", "T1", TeamSide.BLUE, 73L, List.of(), historyHash,
                SeriesGameStatus.SIMULATION_IN_PROGRESS, null, 1, child, reservation,
                null, null, null);
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put("GEN", 0);
        score.put("T1", 0);
        return new SeriesAggregate(
                "series-cancel", 0, SeriesStatus.ACTIVE, null, SeriesFormat.BO3,
                "GEN", "T1", "GEN", "GEN", "73", 73L, score, List.of(game),
                Set.of(), historyHash, null, now, now, repository.parentExpiresAt(now),
                Map.of());
    }
}

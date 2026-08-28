package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lolfm.champion.ChampionId;
import com.lolfm.controller.SeriesApiV1Exception;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class SeriesLifecycleHardeningTest {
    private static final Instant START = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    void exactOldDraftReplayKeepsItsOriginalGameChildAndGeneration() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine.Progress progress = completedProgress(1);
        SeriesChildDraft originalChild = new SeriesChildDraft(
                "game-1-child-1", 1, 0, PlayerDraftSessionStatus.COMPLETED,
                START, START, START.plus(Duration.ofMinutes(30)), progress);
        SeriesAggregate aggregate = afterOneCommittedGame(repository, originalChild);
        var request = new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, 0, "old-create");
        String payload = payload("CREATE_DRAFT", request.schemaVersion(),
                Long.toString(request.expectedRevision()), request.clientCommandId());
        SeriesGame game1 = aggregate.games().getFirst();
        SeriesCommandReceipt receipt = commandReceipt(
                request.clientCommandId(), "CREATE_DRAFT", payload,
                SeriesCommandCompletion.SUCCEEDED, 1, SeriesStatus.ACTIVE,
                game1, originalChild, originalChild.childId(), null, null, false);
        SeriesChildDraft actionChild = new SeriesChildDraft(
                originalChild.childId(), originalChild.generation(), 1,
                PlayerDraftSessionStatus.COMPLETED, originalChild.createdAt(),
                originalChild.lastActivityAt(), originalChild.expiresAt(), progress);
        var actionRequest = new SeriesApiV1Dtos.DraftActionRequest(
                SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA, 1, 0,
                "old-action", "g1b0");
        String actionPayload = payload("DRAFT_ACTION", actionRequest.schemaVersion(),
                Long.toString(actionRequest.expectedSeriesRevision()),
                Long.toString(actionRequest.expectedDraftRevision()),
                actionRequest.clientCommandId(), actionRequest.championId(), "1");
        SeriesCommandReceipt actionReceipt = commandReceipt(
                actionRequest.clientCommandId(), "DRAFT_ACTION", actionPayload,
                SeriesCommandCompletion.SUCCEEDED, 2, SeriesStatus.ACTIVE,
                game1, actionChild, hash("old-action-result"), null, null, false);
        aggregate = aggregate.copy(aggregate.revision(), aggregate.status(),
                aggregate.terminalReason(), aggregate.score(), aggregate.games(),
                aggregate.consumedPicks(), aggregate.historyHash(),
                aggregate.winnerTeamCode(), aggregate.lastActivityAt(),
                aggregate.expiresAt(), Map.of(
                        receipt.commandId(), receipt,
                        actionReceipt.commandId(), actionReceipt));
        repository.create("create-series", "series-payload", aggregate);
        SeriesLifecycleService lifecycle = service(repository,
                mock(PlayerControlledDraftEngine.class),
                (binding, draft) -> execution(binding, TeamSide.BLUE));

        SeriesLifecycleService.ChildExecution replay = lifecycle.createDraft(
                aggregate.seriesId(), request);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.aggregate().currentGame().gameNumber()).isEqualTo(2);
        assertThat(replay.game().gameNumber()).isOne();
        assertThat(replay.game().gameId()).isEqualTo(game1.gameId());
        assertThat(replay.child()).isEqualTo(originalChild);
        assertThat(replay.child().generation()).isOne();
        assertThat(replay.child()).isNotSameAs(replay.aggregate().currentGame().childDraft());
        PlayerDraftSessionView replayView = replay.child().view(replay.game());
        assertThat(replayView.seriesGameNumber()).isEqualTo(replay.game().gameNumber());
        assertThat(replayView.blueTeamCode()).isEqualTo(replay.game().blueTeamCode());
        assertThat(replayView.redTeamCode()).isEqualTo(replay.game().redTeamCode());
        assertThat(replayView.controlledSide()).isEqualTo(replay.game().controlledSide());
        assertThat(replayView.matchSeed()).isEqualTo(replay.game().matchSeed());

        SeriesLifecycleService.ChildExecution actionReplay = lifecycle.draftAction(
                aggregate.seriesId(), 1, actionRequest);
        assertThat(actionReplay.replayed()).isTrue();
        assertThat(actionReplay.game().gameId()).isEqualTo(game1.gameId());
        assertThat(actionReplay.child()).isEqualTo(actionChild);
        assertThat(actionReplay.child().revision()).isOne();
        SeriesAggregate beforeConflict = lifecycle.get(aggregate.seriesId());
        assertThatThrownBy(() -> lifecycle.draftAction(
                beforeConflict.seriesId(), 2, actionRequest))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_ID_PAYLOAD_CONFLICT"));
        assertThat(lifecycle.get(aggregate.seriesId())).isEqualTo(beforeConflict);
    }

    @Test
    void generationOneReplayCannotPretendTheReplacementGenerationTwoIsItsResult() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        SeriesAggregate base = baseAggregate(repository, SeriesFormat.BO3);
        PlayerControlledDraftEngine.Progress progress = mock(
                PlayerControlledDraftEngine.Progress.class);
        SeriesChildDraft generationOne = new SeriesChildDraft(
                "generation-one", 1, 0, PlayerDraftSessionStatus.ACTIVE,
                START, START, START.plus(Duration.ofMinutes(30)), progress);
        SeriesChildDraft generationTwo = new SeriesChildDraft(
                "generation-two", 2, 0, PlayerDraftSessionStatus.ACTIVE,
                START, START, START.plus(Duration.ofMinutes(30)), progress);
        SeriesGame current = new SeriesGame(
                base.currentGame().gameId(), 1, "GEN", "T1", TeamSide.BLUE,
                base.currentGame().matchSeed(), List.of(), base.historyHash(),
                SeriesGameStatus.DRAFT_ACTIVE, null, 2, generationTwo,
                null, null, null, null);
        var request = new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, 0, "generation-one-create");
        String commandPayload = payload("CREATE_DRAFT", request.schemaVersion(),
                Long.toString(request.expectedRevision()), request.clientCommandId());
        SeriesCommandReceipt command = commandReceipt(
                request.clientCommandId(), "CREATE_DRAFT", commandPayload,
                SeriesCommandCompletion.SUCCEEDED, 1, SeriesStatus.ACTIVE,
                current, generationOne, generationOne.childId(), null, null, false);
        SeriesAggregate aggregate = base.replaceCurrentGame(current, 3,
                START, base.expiresAt(), Map.of(command.commandId(), command));
        repository.create("create-series", "series-payload", aggregate);
        SeriesLifecycleService lifecycle = service(repository,
                mock(PlayerControlledDraftEngine.class),
                (binding, draft) -> execution(binding, TeamSide.BLUE));

        SeriesLifecycleService.ChildExecution replay = lifecycle.createDraft(
                aggregate.seriesId(), request);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.child()).isEqualTo(generationOne);
        assertThat(replay.child().generation()).isOne();
        assertThat(replay.aggregate().currentGame().childDraft()).isEqualTo(generationTwo);
        assertThat(replay.aggregate().revision()).isEqualTo(3);
    }

    @Test
    void sameSimulationCommandExecutesOnceDifferentCommandConflictsAndCancelWins()
            throws Exception {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        BlockingExecutor matches = new BlockingExecutor(false);
        SeriesAggregate aggregate = createWithCompletedCurrentGame(
                repository, drafts, SeriesFormat.BO3, Map.of());
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        var request = simulateRequest(aggregate, "simulate-1");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> lifecycle.simulate(
                    aggregate.seriesId(), 1, request));
            matches.awaitEntered();

            SeriesLifecycleService.SimulationExecution duplicate = lifecycle.simulate(
                    aggregate.seriesId(), 1, request);
            assertThat(duplicate.inProgress()).isTrue();
            assertThat(matches.calls()).isOne();

            SeriesAggregate reserved = lifecycle.get(aggregate.seriesId());
            assertThatThrownBy(() -> lifecycle.simulate(aggregate.seriesId(), 1,
                    new SeriesApiV1Dtos.SimulateRequest(
                            SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                            reserved.revision(), request.expectedDraftRevision(),
                            "simulate-2")))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error -> {
                        assertThat(error.code()).isEqualTo(
                                "SERIES_SIMULATION_ALREADY_IN_PROGRESS");
                        assertThat(error.retryable()).isTrue();
                    });
            assertThat(matches.calls()).isOne();

            lifecycle.cancel(aggregate.seriesId(), new SeriesApiV1Dtos.CancelRequest(
                    SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA, reserved.revision(), "cancel"));
            matches.release();
            assertThatThrownBy(() -> get(first))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                            assertThat(error.code()).isEqualTo(
                                    "SERIES_SIMULATION_RESERVATION_INVALIDATED"));
        }

        SeriesAggregate cancelled = repository.get(aggregate.seriesId());
        assertThat(cancelled.status()).isEqualTo(SeriesStatus.CANCELLED);
        assertThat(cancelled.score()).containsEntry("GEN", 0).containsEntry("T1", 0);
        assertThat(cancelled.consumedPicks()).isEmpty();
        assertThat(cancelled.commandReceipts().get("simulate-1").completion())
                .isEqualTo(SeriesCommandCompletion.FAILED);
        assertThat(cancelled.commandReceipts().get("simulate-1").errorCode())
                .isEqualTo("SERIES_SIMULATION_RESERVATION_INVALIDATED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leaseExpiryCanRetryAndLateOldSuccessOrIntegrityFailureCannotOverwriteNewCommit(
            boolean lateIntegrityFailure
    ) throws Exception {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        FirstCallBlockingExecutor matches = new FirstCallBlockingExecutor(
                lateIntegrityFailure);
        SeriesAggregate aggregate = createWithCompletedCurrentGame(
                repository, drafts, SeriesFormat.BO3, Map.of());
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        var oldRequest = simulateRequest(aggregate, "old-simulation");

        try (var executor = Executors.newSingleThreadExecutor()) {
            var oldWorker = executor.submit(() -> lifecycle.simulate(
                    aggregate.seriesId(), 1, oldRequest));
            matches.awaitFirstEntered();
            clock.advance(Duration.ofMinutes(5));
            SeriesAggregate released = repository.get(aggregate.seriesId());
            assertThat(released.currentGame().status())
                    .isEqualTo(SeriesGameStatus.SIMULATION_FAILED_RETRYABLE);
            assertThat(released.commandReceipts().get("old-simulation").errorCode())
                    .isEqualTo("SERIES_SIMULATION_LEASE_EXPIRED");

            assertThatThrownBy(() -> lifecycle.simulate(
                    aggregate.seriesId(), 1, oldRequest))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error -> {
                        assertThat(error.code()).isEqualTo(
                                "SERIES_SIMULATION_LEASE_EXPIRED");
                        assertThat(error.retryable()).isTrue();
                    });
            SeriesLifecycleService.SimulationExecution retry = lifecycle.simulate(
                    aggregate.seriesId(), 1, new SeriesApiV1Dtos.SimulateRequest(
                            SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA, released.revision(),
                            released.currentGame().childDraft().revision(), "retry-simulation"));
            assertThat(retry.game().status()).isEqualTo(SeriesGameStatus.COMMITTED);
            assertThat(matches.calls()).isEqualTo(2);

            matches.releaseFirst();
            assertThatThrownBy(() -> get(oldWorker))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                            assertThat(error.code()).isEqualTo(
                                    "SERIES_SIMULATION_RESERVATION_INVALIDATED"));
        }

        SeriesAggregate after = lifecycle.get(aggregate.seriesId());
        assertThat(after.score().values().stream().mapToInt(Integer::intValue).sum()).isOne();
        assertThat(after.consumedPicks()).hasSize(10);
        assertThat(after.commandReceipts().get("old-simulation").completion())
                .isEqualTo(SeriesCommandCompletion.FAILED);
        assertThat(after.commandReceipts().get("retry-simulation").completion())
                .isEqualTo(SeriesCommandCompletion.SUCCEEDED);
    }

    @Test
    void lateOldFailureAfterCancelCannotOverwriteCancellation() throws Exception {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        BlockingExecutor matches = new BlockingExecutor(true);
        SeriesAggregate aggregate = createWithCompletedCurrentGame(
                repository, drafts, SeriesFormat.BO3, Map.of());
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        var request = simulateRequest(aggregate, "late-failure");

        try (var executor = Executors.newSingleThreadExecutor()) {
            var worker = executor.submit(() -> lifecycle.simulate(
                    aggregate.seriesId(), 1, request));
            matches.awaitEntered();
            SeriesAggregate reserved = lifecycle.get(aggregate.seriesId());
            lifecycle.cancel(aggregate.seriesId(), new SeriesApiV1Dtos.CancelRequest(
                    SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA, reserved.revision(), "cancel"));
            matches.release();
            assertThatThrownBy(() -> get(worker))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                            assertThat(error.code()).isEqualTo(
                                    "SERIES_SIMULATION_RESERVATION_INVALIDATED"));
        }

        SeriesAggregate after = repository.get(aggregate.seriesId());
        assertThat(after.status()).isEqualTo(SeriesStatus.CANCELLED);
        assertThat(after.score().values().stream().mapToInt(Integer::intValue).sum()).isZero();
        assertThat(after.commandReceipts().get("late-failure").errorCode())
                .isEqualTo("SERIES_SIMULATION_RESERVATION_INVALIDATED");
    }

    @Test
    void runtimeIntegrityAndNoResultFailuresReplayWithoutReexecution() {
        assertFailureReplay(new RuntimeFailingExecutor(),
                "SERIES_SIMULATION_FAILED", SeriesStatus.ACTIVE, true);
        assertFailureReplay(new IntegrityFailingExecutor(),
                "SERIES_ENGINE_OUTPUT_INTEGRITY_FAILED", SeriesStatus.BLOCKED, false);
        assertFailureReplay(new NoResultExecutor(),
                "SERIES_GAME_NO_DECISIVE_RESULT", SeriesStatus.BLOCKED, false);
    }

    @Test
    void receiptCapacityPreflightAllowsExactReplayAndPrioritizesPayloadConflict() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        CountingWinnerExecutor matches = new CountingWinnerExecutor();
        SeriesAggregate base = baseAggregate(repository, SeriesFormat.BO3);
        SeriesGame game = completedGame(base.currentGame(), 1);
        LinkedHashMap<String, SeriesCommandReceipt> filled = new LinkedHashMap<>();
        for (int index = 0; index < 255; index++) {
            SeriesCommandReceipt receipt = commandReceipt(
                    "prior-" + index, "CANCEL_DRAFT", "payload-" + index,
                    SeriesCommandCompletion.SUCCEEDED, base.revision(), base.status(),
                    game, null, "prior", null, null, false);
            filled.put(receipt.commandId(), receipt);
        }
        SeriesAggregate aggregate = base.replaceCurrentGame(game, base.revision(),
                base.lastActivityAt(), base.expiresAt(), filled);
        repository.create("create-series", "series-payload", aggregate);
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        var request = simulateRequest(aggregate, "slot-256");

        SeriesLifecycleService.SimulationExecution first = lifecycle.simulate(
                aggregate.seriesId(), 1, request);
        assertThat(first.aggregate().commandReceipts()).hasSize(256);
        assertThat(matches.calls()).isOne();
        SeriesLifecycleService.SimulationExecution replay = lifecycle.simulate(
                aggregate.seriesId(), 1, request);
        assertThat(replay.replayed()).isTrue();
        assertThat(matches.calls()).isOne();

        SeriesAggregate beforeRejected = lifecycle.get(aggregate.seriesId());
        assertThatThrownBy(() -> lifecycle.cancel(aggregate.seriesId(),
                new SeriesApiV1Dtos.CancelRequest(SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA,
                        beforeRejected.revision(), "new-at-capacity")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_RECEIPT_CAPACITY_REACHED"));
        assertThat(lifecycle.get(aggregate.seriesId())).isEqualTo(beforeRejected);

        assertThatThrownBy(() -> lifecycle.draftAction(aggregate.seriesId(),
                beforeRejected.currentGame().gameNumber(),
                new SeriesApiV1Dtos.DraftActionRequest(
                        SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA,
                        beforeRejected.revision(), 0, "action-at-capacity", "g1b0")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_RECEIPT_CAPACITY_REACHED"));
        assertThatThrownBy(() -> lifecycle.simulate(aggregate.seriesId(),
                beforeRejected.currentGame().gameNumber(),
                new SeriesApiV1Dtos.SimulateRequest(
                        SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                        beforeRejected.revision(), 0, "simulate-at-capacity")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_RECEIPT_CAPACITY_REACHED"));
        assertThat(matches.calls()).isOne();
        assertThat(lifecycle.get(aggregate.seriesId())).isEqualTo(beforeRejected);

        assertThatThrownBy(() -> lifecycle.simulate(aggregate.seriesId(), 1,
                new SeriesApiV1Dtos.SimulateRequest(
                        SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                        request.expectedSeriesRevision() + 1,
                        request.expectedDraftRevision(), request.clientCommandId())))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_ID_PAYLOAD_CONFLICT"));
        assertThat(matches.calls()).isOne();
    }

    @Test
    void poolExhaustionIsAStableFailedReceiptAndDoesNotRepeatPreflight() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(false);
        SeriesAggregate aggregate = baseAggregate(repository, SeriesFormat.BO5);
        repository.create("create-series", "series-payload", aggregate);
        SeriesLifecycleService lifecycle = service(repository, drafts,
                (binding, draft) -> execution(binding, TeamSide.BLUE));
        var request = new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,
                aggregate.revision(), "pool-exhausted");

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> lifecycle.createDraft(aggregate.seriesId(), request))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error -> {
                        assertThat(error.code()).isEqualTo(
                                "SERIES_HARD_FEARLESS_POOL_EXHAUSTED");
                        assertThat(error.status()).isEqualTo(
                                HttpStatus.UNPROCESSABLE_ENTITY);
                        assertThat(error.currentRevision()).isOne();
                    });
        }
        verify(drafts, times(1)).canCompleteSeriesDraft(anySet());
        SeriesAggregate blocked = repository.get(aggregate.seriesId());
        assertThat(blocked.status()).isEqualTo(SeriesStatus.BLOCKED);
        assertThat(blocked.commandReceipts().get("pool-exhausted").completion())
                .isEqualTo(SeriesCommandCompletion.FAILED);
    }

    @Test
    void deterministicStateMachineCoversBo3AndBo5SweepAndFullDistance() {
        assertSeries(SeriesFormat.BO3, List.of("GEN", "GEN"), 2);
        assertSeries(SeriesFormat.BO3, List.of("GEN", "T1", "GEN"), 3);
        assertSeries(SeriesFormat.BO5, List.of("GEN", "GEN", "GEN"), 3);
        assertSeries(SeriesFormat.BO5,
                List.of("GEN", "T1", "GEN", "T1", "GEN"), 5);
    }

    @Test
    void replayComparesTheEntireAggregateIncludingRevisionNeutralFields() {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        SeriesAggregate aggregate = createWithCompletedCurrentGame(
                repository, drafts, SeriesFormat.BO3, Map.of());
        ReplayMutatingExecutor matches = new ReplayMutatingExecutor(
                repository, aggregate.seriesId());
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        lifecycle.simulate(aggregate.seriesId(), 1,
                simulateRequest(aggregate, "commit"));

        assertThatThrownBy(() -> lifecycle.replay(aggregate.seriesId(), 1,
                new SeriesApiV1Dtos.ReplayRequest(
                        SeriesApiV1Dtos.REPLAY_REQUEST_SCHEMA, "replay")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_GAME_REPLAY_MUTATION_DETECTED"));
        assertThat(matches.calls()).isEqualTo(2);
    }

    private static void assertFailureReplay(
            CountingExecutor matches, String expectedCode,
            SeriesStatus expectedStatus, boolean retryable
    ) {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        SeriesAggregate aggregate = createWithCompletedCurrentGame(
                repository, drafts, SeriesFormat.BO3, Map.of());
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        var request = simulateRequest(aggregate, "failed-command");

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> lifecycle.simulate(
                    aggregate.seriesId(), 1, request))
                    .isInstanceOfSatisfying(SeriesApiV1Exception.class, error -> {
                        assertThat(error.code()).isEqualTo(expectedCode);
                        assertThat(error.retryable()).isEqualTo(retryable);
                    });
        }
        assertThat(matches.calls()).isOne();
        SeriesAggregate failed = repository.get(aggregate.seriesId());
        assertThat(failed.status()).isEqualTo(expectedStatus);
        assertThat(failed.commandReceipts().get("failed-command").completion())
                .isEqualTo(SeriesCommandCompletion.FAILED);
    }

    private static void assertSeries(
            SeriesFormat format, List<String> winners, int expectedGames
    ) {
        MutableClock clock = new MutableClock(START);
        SeriesRepository repository = repository(clock, 256);
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        when(drafts.canCompleteSeriesDraft(anySet())).thenReturn(true);
        WinnerSequenceExecutor matches = new WinnerSequenceExecutor(winners);
        SeriesAggregate initial = baseAggregate(repository, format);
        repository.create("create-series", "series-payload", initial);
        SeriesLifecycleService lifecycle = service(repository, drafts, matches);
        ArrayList<Long> seeds = new ArrayList<>();

        while (lifecycle.get(initial.seriesId()).status() == SeriesStatus.ACTIVE) {
            SeriesAggregate beforeDraft = lifecycle.get(initial.seriesId());
            attachCompletedCurrentGame(repository, beforeDraft.seriesId());
            SeriesAggregate ready = lifecycle.get(initial.seriesId());
            SeriesGame game = ready.currentGame();
            seeds.add(game.matchSeed());
            SeriesLifecycleService.SimulationExecution simulated = lifecycle.simulate(
                    ready.seriesId(), game.gameNumber(), simulateRequest(
                            ready, "simulate-" + game.gameNumber()));
            assertThat(simulated.game().status()).isEqualTo(SeriesGameStatus.COMMITTED);
        }

        SeriesAggregate completed = lifecycle.get(initial.seriesId());
        assertThat(completed.status()).isEqualTo(SeriesStatus.COMPLETED);
        assertThat(completed.games()).hasSize(expectedGames);
        assertThat(completed.consumedPicks()).hasSize(expectedGames * 10);
        assertThat(matches.calls()).isEqualTo(expectedGames);
        assertThat(new HashSet<>(seeds)).hasSize(expectedGames);
        for (int index = 0; index < completed.games().size(); index++) {
            SeriesGame game = completed.games().get(index);
            assertThat(game.gameNumber()).isEqualTo(index + 1);
            assertThat(game.historyBefore()).hasSize(index * 10);
            assertThat(game.controlledSide()).isEqualTo(
                    game.blueTeamCode().equals("GEN") ? TeamSide.BLUE : TeamSide.RED);
            if (index > 0) {
                SeriesGame previous = completed.games().get(index - 1);
                assertThat(game.blueTeamCode()).isEqualTo(previous.redTeamCode());
                assertThat(game.redTeamCode()).isEqualTo(previous.blueTeamCode());
            }
        }
        if (expectedGames == 5) {
            assertThat(completed.games().get(4).historyBefore()).hasSize(40);
        }
    }

    private static SeriesLifecycleService service(
            SeriesRepository repository,
            PlayerControlledDraftEngine drafts,
            SimpleExecution matches
    ) {
        return new SeriesLifecycleService(mock(LckTeamAssembler.class), drafts,
                repository, matches::execute);
    }

    private static SeriesRepository repository(MutableClock clock, int receipts) {
        return new SeriesRepository(clock, new SeriesLifecycleConfiguration(
                32, Duration.ofMinutes(120), Duration.ofMinutes(30),
                Duration.ofMinutes(5), receipts));
    }

    private static SeriesAggregate createWithCompletedCurrentGame(
            SeriesRepository repository,
            PlayerControlledDraftEngine drafts,
            SeriesFormat format,
            Map<String, SeriesCommandReceipt> receipts
    ) {
        SeriesAggregate base = baseAggregate(repository, format);
        SeriesGame completed = completedGame(base.currentGame(), 1);
        SeriesAggregate aggregate = base.replaceCurrentGame(completed, base.revision(),
                base.lastActivityAt(), base.expiresAt(), receipts);
        repository.create("create-series", "series-payload", aggregate);
        return aggregate;
    }

    private static SeriesAggregate baseAggregate(
            SeriesRepository repository, SeriesFormat format
    ) {
        String seriesId = "series-" + format.name().toLowerCase();
        String historyHash = SeriesIdentity.historyHash(0, Set.of());
        SeriesGame game = new SeriesGame(
                SeriesIdentity.gameId(seriesId, 1), 1, "GEN", "T1", TeamSide.BLUE,
                SeriesIdentity.deriveGameSeed(seriesId, "73", 1,
                        "GEN", "T1", "GEN", historyHash),
                List.of(), historyHash, SeriesGameStatus.DRAFT_PENDING, null,
                0, null, null, null, null, null);
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put("GEN", 0);
        score.put("T1", 0);
        return new SeriesAggregate(seriesId, 0, SeriesStatus.ACTIVE, null, format,
                "GEN", "T1", "GEN", "GEN", "73", 73L, score, List.of(game),
                Set.of(), historyHash, null, START, START,
                repository.parentExpiresAt(START), Map.of());
    }

    private static SeriesAggregate afterOneCommittedGame(
            SeriesRepository repository, SeriesChildDraft receiptChild
    ) {
        SeriesAggregate base = baseAggregate(repository, SeriesFormat.BO3);
        PlayerControlledDraftResult draft = completedDraft(1);
        SeriesGameReceipt matchReceipt = mock(SeriesGameReceipt.class);
        MatchEngineV1Output.MatchResultSummaryV1 summary = mock(
                MatchEngineV1Output.MatchResultSummaryV1.class);
        when(summary.winner()).thenReturn(TeamSide.BLUE);
        SeriesChildDraft simulated = new SeriesChildDraft(
                "current-game-1-child", 2, 5, PlayerDraftSessionStatus.SIMULATED,
                START, START, START.plus(Duration.ofMinutes(30)), completedProgress(1));
        SeriesGame game1 = new SeriesGame(base.currentGame().gameId(), 1, "GEN", "T1",
                TeamSide.BLUE, base.currentGame().matchSeed(), List.of(),
                base.historyHash(), SeriesGameStatus.COMMITTED, null, 2,
                simulated, null, draft, summary, matchReceipt);
        Set<ChampionId> consumed = new HashSet<>();
        consumed.addAll(draft.bluePicks());
        consumed.addAll(draft.redPicks());
        String historyHash = SeriesIdentity.historyHash(1, consumed);
        SeriesGame game2 = new SeriesGame(SeriesIdentity.gameId(base.seriesId(), 2),
                2, "T1", "GEN", TeamSide.RED,
                SeriesIdentity.deriveGameSeed(base.seriesId(), "73", 2,
                        "T1", "GEN", "GEN", historyHash),
                List.copyOf(consumed), historyHash, SeriesGameStatus.DRAFT_PENDING,
                null, 0, null, null, null, null, null);
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put("GEN", 1);
        score.put("T1", 0);
        return base.copy(2, SeriesStatus.ACTIVE, null, score,
                List.of(game1, game2), consumed, historyHash, null,
                START, repository.parentExpiresAt(START), Map.of());
    }

    private static void attachCompletedCurrentGame(
            SeriesRepository repository, String seriesId
    ) {
        repository.mutate(seriesId, aggregate -> {
            SeriesGame current = completedGame(
                    aggregate.currentGame(), aggregate.currentGame().gameNumber());
            SeriesAggregate updated = aggregate.replaceCurrentGame(
                    current, aggregate.revision() + 1, aggregate.lastActivityAt(),
                    aggregate.expiresAt(), aggregate.commandReceipts());
            return new SeriesRepository.Mutation<>(updated, null);
        });
    }

    private static SeriesGame completedGame(SeriesGame game, int gameNumber) {
        PlayerControlledDraftEngine.Progress progress = completedProgress(gameNumber);
        SeriesChildDraft child = new SeriesChildDraft(
                "child-" + gameNumber, 1, 10, PlayerDraftSessionStatus.COMPLETED,
                START, START, START.plus(Duration.ofMinutes(30)), progress);
        return new SeriesGame(game.gameId(), game.gameNumber(), game.blueTeamCode(),
                game.redTeamCode(), game.controlledSide(), game.matchSeed(),
                game.historyBefore(), game.historyBeforeHash(),
                SeriesGameStatus.DRAFT_COMPLETED, null, 1, child, null,
                progress.result(), null, null);
    }

    private static PlayerControlledDraftEngine.Progress completedProgress(int gameNumber) {
        PlayerControlledDraftEngine.Progress progress = mock(
                PlayerControlledDraftEngine.Progress.class);
        PlayerControlledDraftResult result = completedDraft(gameNumber);
        when(progress.complete()).thenReturn(true);
        when(progress.result()).thenReturn(result);
        return progress;
    }

    private static PlayerControlledDraftResult completedDraft(int gameNumber) {
        PlayerControlledDraftResult draft = mock(PlayerControlledDraftResult.class);
        List<ChampionId> blue = new ArrayList<>();
        List<ChampionId> red = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            blue.add(new ChampionId("g" + gameNumber + "b" + index));
            red.add(new ChampionId("g" + gameNumber + "r" + index));
        }
        when(draft.bluePicks()).thenReturn(blue);
        when(draft.redPicks()).thenReturn(red);
        when(draft.draftIdentity()).thenReturn(hash("draft-" + gameNumber));
        return draft;
    }

    private static SeriesApiV1Dtos.SimulateRequest simulateRequest(
            SeriesAggregate aggregate, String commandId
    ) {
        return new SeriesApiV1Dtos.SimulateRequest(
                SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA, aggregate.revision(),
                aggregate.currentGame().childDraft().revision(), commandId);
    }

    private static SeriesCommandReceipt commandReceipt(
            String commandId, String type, String payload,
            SeriesCommandCompletion completion, long revision,
            SeriesStatus seriesStatus, SeriesGame game, SeriesChildDraft child,
            String identity, String errorCode, Integer httpStatus, boolean retryable
    ) {
        return new SeriesCommandReceipt(commandId, type, payload, completion,
                revision, seriesStatus, game.gameNumber(), game.gameId(), game.status(),
                child == null ? null : child.revision(),
                child == null ? null : child.childId(),
                child == null ? null : child.generation(), child, identity,
                errorCode, httpStatus, retryable);
    }

    private static String payload(String... fields) {
        StringBuilder canonical = new StringBuilder(
                "payloadSchema=SERIES_COMMAND_PAYLOAD_V1\n");
        for (int index = 0; index < fields.length; index++) {
            canonical.append("field").append(index).append('=')
                    .append(fields[index]).append('\n');
        }
        return SeriesIdentity.sha256(canonical.toString());
    }

    private static String hash(String value) {
        return SeriesIdentity.sha256("value=" + value + "\n");
    }

    private static SeriesMatchExecutor.Execution execution(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            TeamSide winner
    ) {
        MatchEngineV1Input input = mock(MatchEngineV1Input.class);
        MatchEngineV1Output output = mock(MatchEngineV1Output.class);
        MatchEngineV1Output.MatchResultSummaryV1 summary = mock(
                MatchEngineV1Output.MatchResultSummaryV1.class);
        when(summary.winner()).thenReturn(winner);
        when(output.resultSummary()).thenReturn(summary);
        SeriesGameReceipt receipt = mock(SeriesGameReceipt.class);
        when(receipt.outputHash()).thenReturn(hash(
                binding.gameId() + ":" + winner));
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        when(receipt.policyId()).thenReturn(policy.policyId());
        when(receipt.policyHash()).thenReturn(policy.policyHash());
        when(receipt.runtimeProfileId()).thenReturn(
                policy.retainedRuntimeProfileId().name());
        when(receipt.configurationHash()).thenReturn(policy.configurationHash());
        when(receipt.engineImplementationVersion()).thenReturn(
                policy.engineImplementationVersion());
        when(receipt.activeGameplayRulesVersion()).thenReturn(
                policy.activeGameplayRulesVersion());
        return new SeriesMatchExecutor.Execution(input, output, receipt);
    }

    private static <T> T get(java.util.concurrent.Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(error.getCause());
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface SimpleExecution {
        SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft);

        default SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding
        ) {
            return execute(binding, null);
        }
    }

    private interface CountingExecutor extends SimpleExecution {
        int calls();
    }

    private static final class CountingWinnerExecutor implements CountingExecutor {
        private final AtomicInteger calls = new AtomicInteger();
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            calls.incrementAndGet();
            return execution(binding, TeamSide.BLUE);
        }
        @Override public int calls() { return calls.get(); }
    }

    private static final class WinnerSequenceExecutor implements CountingExecutor {
        private final ArrayDeque<String> winners;
        private final AtomicInteger calls = new AtomicInteger();
        private WinnerSequenceExecutor(List<String> winners) {
            this.winners = new ArrayDeque<>(winners);
        }
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            calls.incrementAndGet();
            String winner = winners.removeFirst();
            return execution(binding, winner.equals(binding.blueTeamCode())
                    ? TeamSide.BLUE : TeamSide.RED);
        }
        @Override public int calls() { return calls.get(); }
    }

    private static final class BlockingExecutor implements CountingExecutor {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();
        private final boolean fail;
        private BlockingExecutor(boolean fail) { this.fail = fail; }
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            if (fail) throw new IllegalStateException("late failure");
            return execution(binding, TeamSide.BLUE);
        }
        void awaitEntered() { await(entered); }
        void release() { release.countDown(); }
        @Override public int calls() { return calls.get(); }
    }

    private static final class FirstCallBlockingExecutor implements CountingExecutor {
        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();
        private final boolean failFirstWithIntegrity;
        private FirstCallBlockingExecutor(boolean failFirstWithIntegrity) {
            this.failFirstWithIntegrity = failFirstWithIntegrity;
        }
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            int call = calls.incrementAndGet();
            if (call == 1) {
                firstEntered.countDown();
                await(releaseFirst);
                if (failFirstWithIntegrity) {
                    throw new SeriesMatchIntegrityException("late integrity failure");
                }
            }
            return execution(binding, TeamSide.BLUE);
        }
        void awaitFirstEntered() { await(firstEntered); }
        void releaseFirst() { releaseFirst.countDown(); }
        @Override public int calls() { return calls.get(); }
    }

    private static final class RuntimeFailingExecutor implements CountingExecutor {
        private final AtomicInteger calls = new AtomicInteger();
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            calls.incrementAndGet();
            throw new IllegalStateException("runtime");
        }
        @Override public int calls() { return calls.get(); }
    }

    private static final class IntegrityFailingExecutor implements CountingExecutor {
        private final AtomicInteger calls = new AtomicInteger();
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            calls.incrementAndGet();
            throw new SeriesMatchIntegrityException("integrity");
        }
        @Override public int calls() { return calls.get(); }
    }

    private static final class NoResultExecutor implements CountingExecutor {
        private final AtomicInteger calls = new AtomicInteger();
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            calls.incrementAndGet();
            return execution(binding, null);
        }
        @Override public int calls() { return calls.get(); }
    }

    private static final class ReplayMutatingExecutor implements CountingExecutor {
        private final SeriesRepository repository;
        private final String seriesId;
        private final AtomicInteger calls = new AtomicInteger();
        private SeriesMatchExecutor.Execution committed;
        private ReplayMutatingExecutor(SeriesRepository repository, String seriesId) {
            this.repository = repository;
            this.seriesId = seriesId;
        }
        @Override public SeriesMatchExecutor.Execution execute(
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                PlayerControlledDraftResult draft
        ) {
            if (calls.incrementAndGet() == 1) {
                committed = execution(binding, TeamSide.BLUE);
                return committed;
            }
            repository.mutate(seriesId, aggregate -> {
                SeriesAggregate changed = aggregate.copy(aggregate.revision(),
                        aggregate.status(), aggregate.terminalReason(), aggregate.score(),
                        aggregate.games(), aggregate.consumedPicks(), aggregate.historyHash(),
                        aggregate.winnerTeamCode(), aggregate.lastActivityAt(),
                        aggregate.expiresAt().plusSeconds(1), aggregate.commandReceipts());
                return new SeriesRepository.Mutation<>(changed, null);
            });
            return committed;
        }
        @Override public int calls() { return calls.get(); }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

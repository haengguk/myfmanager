package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lolfm.controller.PlayerDraftApiV1Exception;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlayerDraftSimulationHardeningTest {
    @Test
    void firstAndRepeatSimulationStoreOnlyReceiptAndReexecuteExactly() {
        Fixture fixture = fixture();
        MatchEngineV1Output output = mock(MatchEngineV1Output.class);
        SimulationReceipt receipt = receipt('a');
        when(fixture.simulations().execute(any())).thenReturn(
                new PlayerDraftMatchSimulationExecutor.Execution(output, receipt));

        PlayerDraftApiV1Service.SimulationExecution first = fixture.service().simulate(
                "session", request());
        PlayerDraftApiV1Service.SimulationExecution repeat = fixture.service().simulate(
                "session", request());

        assertThat(first.output()).isSameAs(output);
        assertThat(repeat.output()).isSameAs(output);
        assertThat(first.session().status()).isEqualTo(PlayerDraftSessionStatus.SIMULATED);
        PlayerDraftSession stored = fixture.repository().get("session");
        assertThat(stored.simulationReceipt()).isEqualTo(receipt);
        assertThat(stored.revision()).isZero();
        verify(fixture.simulations(), org.mockito.Mockito.times(2)).execute(any());
    }

    @Test
    void receiptMismatchDoesNotOverwriteStableReceiptOrSessionState() {
        Fixture fixture = fixture();
        MatchEngineV1Output firstOutput = mock(MatchEngineV1Output.class);
        MatchEngineV1Output changedOutput = mock(MatchEngineV1Output.class);
        SimulationReceipt stable = receipt('a');
        when(fixture.simulations().execute(any())).thenReturn(
                new PlayerDraftMatchSimulationExecutor.Execution(firstOutput, stable),
                new PlayerDraftMatchSimulationExecutor.Execution(changedOutput, receipt('b')));

        fixture.service().simulate("session", request());
        assertThatThrownBy(() -> fixture.service().simulate("session", request()))
                .isInstanceOfSatisfying(PlayerDraftApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo("PLAYER_DRAFT_INTERNAL_ERROR"));

        PlayerDraftSession stored = fixture.repository().get("session");
        assertThat(stored.status()).isEqualTo(PlayerDraftSessionStatus.SIMULATED);
        assertThat(stored.simulationReceipt()).isEqualTo(stable);
        assertThat(stored.revision()).isZero();
    }

    @Test
    void executionFailureLeavesCompletedSessionWithoutPartialReceipt() {
        Fixture fixture = fixture();
        when(fixture.simulations().execute(any())).thenThrow(
                PlayerDraftApiV1Exception.internal(new IllegalStateException("boom")));

        assertThatThrownBy(() -> fixture.service().simulate("session", request()))
                .isInstanceOf(PlayerDraftApiV1Exception.class);
        PlayerDraftSession stored = fixture.repository().get("session");
        assertThat(stored.status()).isEqualTo(PlayerDraftSessionStatus.COMPLETED);
        assertThat(stored.simulationReceipt()).isNull();
        assertThat(stored.revision()).isZero();
    }

    @Test
    void concurrentSimulationOfOneSessionIsSerializedAndBothReturnExactOutput()
            throws Exception {
        Fixture fixture = fixture();
        MatchEngineV1Output output = mock(MatchEngineV1Output.class);
        SimulationReceipt receipt = receipt('a');
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        when(fixture.simulations().execute(any())).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            try {
                if (call == 1) {
                    firstEntered.countDown();
                    if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("first simulation was not released");
                    }
                } else {
                    secondEntered.countDown();
                }
                return new PlayerDraftMatchSimulationExecutor.Execution(output, receipt);
            } finally {
                active.decrementAndGet();
            }
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> fixture.service().simulate("session", request()));
            assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> fixture.service().simulate("session", request()));
            assertThat(secondEntered.await(250, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            assertThat(first.get().output()).isSameAs(output);
            assertThat(second.get().output()).isSameAs(output);
        }

        assertThat(calls).hasValue(2);
        assertThat(maximumActive).hasValue(1);
        assertThat(fixture.repository().get("session").simulationReceipt()).isEqualTo(receipt);
    }

    private static Fixture fixture() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        PlayerDraftSessionRepository repository = new PlayerDraftSessionRepository(
                clock, 4, Duration.ofMinutes(30));
        PlayerControlledDraftEngine.Progress progress =
                mock(PlayerControlledDraftEngine.Progress.class);
        when(progress.complete()).thenReturn(true);
        Instant created = repository.now();
        repository.create(new PlayerDraftSession(
                "session", 0, PlayerDraftSessionStatus.COMPLETED, "GEN", "T1",
                TeamSide.BLUE, 73L, created, repository.expiresAt(created),
                progress, Map.of(), null));
        PlayerDraftMatchSimulationExecutor simulations =
                mock(PlayerDraftMatchSimulationExecutor.class);
        PlayerDraftApiV1Service service = new PlayerDraftApiV1Service(
                mock(LckTeamAssembler.class), mock(PlayerControlledDraftEngine.class),
                repository, simulations);
        return new Fixture(repository, simulations, service);
    }

    private static PlayerDraftApiV1Dtos.SimulateRequest request() {
        return new PlayerDraftApiV1Dtos.SimulateRequest(
                PlayerDraftApiV1Dtos.SIMULATE_REQUEST_SCHEMA);
    }

    private static SimulationReceipt receipt(char hashCharacter) {
        String hash = String.valueOf(hashCharacter).repeat(64);
        return new SimulationReceipt(
                SimulationReceipt.SCHEMA, "PLAYER_CONTROLLED_DRAFT:GEN:T1:GAME:1:SEED:73",
                "policy", hash, "PRODUCTION_MATCHUP_COMPOSITION_V1", hash,
                "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9",
                "MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3", hash, hash, hash, hash, hash,
                hash, "PLAYER_CONTROLLED_DRAFT_V1", hash, hash, hash, hash, hash,
                SimulationRandomFingerprint.SCHEMA, 100L, hash,
                SimulationRandomFingerprint.TRACE_HASH_ALGORITHM,
                TeamSide.BLUE, 1800, GameEndReason.NEXUS_DESTROYED);
    }

    private record Fixture(
            PlayerDraftSessionRepository repository,
            PlayerDraftMatchSimulationExecutor simulations,
            PlayerDraftApiV1Service service
    ) {
    }
}

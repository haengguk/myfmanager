package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lolfm.controller.SeriesApiV1Exception;
import com.lolfm.domain.Team;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeriesFrontendReadinessContractTest {
    private static final Instant START = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void allowedCommandsDropsAllNewMutationsAtConfiguredReceiptCapacityForEveryState() {
        SeriesLifecycleConfiguration configuration = configuration(256);
        SeriesApiV1ResponseMapper mapper = mapper(configuration);

        List<StateCase> cases = List.of(
                new StateCase(SeriesStatus.ACTIVE, SeriesGameStatus.DRAFT_PENDING,
                        false, List.of("CREATE_DRAFT_SESSION", "CANCEL_SERIES")),
                new StateCase(SeriesStatus.ACTIVE, SeriesGameStatus.DRAFT_ACTIVE,
                        false, List.of("SUBMIT_DRAFT_ACTION", "CANCEL_DRAFT_SESSION",
                        "CANCEL_SERIES")),
                new StateCase(SeriesStatus.ACTIVE, SeriesGameStatus.DRAFT_COMPLETED,
                        false, List.of("SIMULATE", "CANCEL_SERIES")),
                new StateCase(SeriesStatus.ACTIVE,
                        SeriesGameStatus.SIMULATION_FAILED_RETRYABLE,
                        false, List.of("SIMULATE", "CANCEL_SERIES")),
                new StateCase(SeriesStatus.BLOCKED, SeriesGameStatus.BLOCKED,
                        false, List.of("GET", "CANCEL_SERIES")),
                new StateCase(SeriesStatus.ACTIVE,
                        SeriesGameStatus.SIMULATION_IN_PROGRESS,
                        true, List.of("GET", "CANCEL_SERIES")));

        for (StateCase state : cases) {
            SeriesAggregate at255 = aggregate(state, 255);
            assertThat(mapper.series(at255).allowedCommands())
                    .as("255 receipts: %s / %s", state.seriesStatus(), state.gameStatus())
                    .containsExactlyElementsOf(state.expectedBeforeCapacity());

            SeriesAggregate at256 = aggregate(state, 256);
            assertThat(mapper.series(at256).allowedCommands())
                    .as("256 receipts: %s / %s", state.seriesStatus(), state.gameStatus())
                    .containsExactly("GET");
        }
    }

    @Test
    void publicProjectionAndServiceEligibilityAgreeAtThe255To256Boundary() {
        SeriesLifecycleConfiguration configuration = configuration(256);
        SeriesRepository repository = new SeriesRepository(
                Clock.fixed(START, ZoneOffset.UTC), configuration);
        LckTeamAssembler teams = teams();
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        SeriesMatchExecutor matches = mock(SeriesMatchExecutor.class);
        SeriesLifecycleService lifecycle = new SeriesLifecycleService(
                teams, drafts, repository, matches);
        SeriesApiV1ResponseMapper mapper = mapper(
                configuration, teams, mock(PlayerControlledDraftEngine.class));

        SeriesAggregate at255 = aggregate(new StateCase(
                SeriesStatus.ACTIVE, SeriesGameStatus.DRAFT_PENDING, false,
                List.of("CREATE_DRAFT_SESSION", "CANCEL_SERIES")), 255);
        assertThat(mapper.series(at255).allowedCommands())
                .containsExactly("CREATE_DRAFT_SESSION", "CANCEL_SERIES");

        SeriesChildDraft historicalChild = new SeriesChildDraft(
                "historical-child", 1, 0, PlayerDraftSessionStatus.ACTIVE,
                START, START, START.plus(Duration.ofMinutes(30)),
                mock(PlayerControlledDraftEngine.Progress.class));
        var replayRequest = new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,
                at255.revision(), "receipt-255");
        String replayPayload = payload("CREATE_DRAFT", replayRequest.schemaVersion(),
                Long.toString(replayRequest.expectedRevision()),
                replayRequest.clientCommandId());
        SeriesCommandReceipt replayReceipt = receipt(
                replayRequest.clientCommandId(), "CREATE_DRAFT", replayPayload,
                at255, historicalChild);
        LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                at255.commandReceipts());
        receipts.put(replayReceipt.commandId(), replayReceipt);
        SeriesAggregate at256 = at255.copy(at255.revision(), at255.status(),
                at255.terminalReason(), at255.score(), at255.games(),
                at255.consumedPicks(), at255.historyHash(), at255.winnerTeamCode(),
                at255.lastActivityAt(), at255.expiresAt(), receipts);
        repository.create("create-series", "create-payload", at256);

        assertThat(at256.commandReceipts()).hasSize(256);
        assertThat(mapper.series(at256).allowedCommands()).containsExactly("GET");

        SeriesLifecycleService.ChildExecution exactReplay = lifecycle.createDraft(
                at256.seriesId(), replayRequest);
        assertThat(exactReplay.replayed()).isTrue();
        assertThat(exactReplay.child()).isEqualTo(historicalChild);
        assertThat(lifecycle.get(at256.seriesId())).isEqualTo(at256);

        assertThatThrownBy(() -> lifecycle.createDraft(at256.seriesId(),
                new SeriesApiV1Dtos.DraftCreateRequest(
                        SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,
                        at256.revision(), "new-draft-at-capacity")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_RECEIPT_CAPACITY_REACHED"));
        assertThatThrownBy(() -> lifecycle.cancel(at256.seriesId(),
                new SeriesApiV1Dtos.CancelRequest(
                        SeriesApiV1Dtos.CANCEL_REQUEST_SCHEMA,
                        at256.revision(), "new-cancel-at-capacity")))
                .isInstanceOfSatisfying(SeriesApiV1Exception.class, error ->
                        assertThat(error.code()).isEqualTo(
                                "SERIES_COMMAND_RECEIPT_CAPACITY_REACHED"));
        assertThat(lifecycle.get(at256.seriesId())).isEqualTo(at256);
        verifyNoInteractions(drafts, matches);
    }

    private static SeriesApiV1ResponseMapper mapper(
            SeriesLifecycleConfiguration configuration
    ) {
        PlayerControlledDraftEngine drafts = mock(PlayerControlledDraftEngine.class);
        return mapper(configuration, teams(), drafts);
    }

    private static SeriesApiV1ResponseMapper mapper(
            SeriesLifecycleConfiguration configuration,
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts
    ) {
        when(drafts.activeDraftMetaVersion()).thenReturn("draft-meta");
        when(drafts.activeRequiredLegalRoleKeyHash()).thenReturn("1".repeat(64));
        when(drafts.activeActualLegalRoleKeyHash()).thenReturn("2".repeat(64));
        return new SeriesApiV1ResponseMapper(
                teams, mock(PlayerDraftApiV1ResponseMapper.class), drafts,
                configuration);
    }

    private static LckTeamAssembler teams() {
        LckTeamAssembler teams = mock(LckTeamAssembler.class);
        Team gen = mock(Team.class);
        Team t1 = mock(Team.class);
        when(gen.getName()).thenReturn("Gen.G");
        when(t1.getName()).thenReturn("T1");
        when(teams.assemble("GEN")).thenReturn(gen);
        when(teams.assemble("T1")).thenReturn(t1);
        return teams;
    }

    private static SeriesAggregate aggregate(StateCase state, int receiptCount) {
        String seriesId = "series_" + "a".repeat(64);
        String historyHash = SeriesIdentity.historyHash(0, Set.of());
        SeriesSimulationReservation reservation = state.reserved()
                ? new SeriesSimulationReservation(
                "reservation", "reservation-command", "reservation-payload", 0, 0,
                START, START.plus(Duration.ofMinutes(5)), "binding") : null;
        SeriesGame game = new SeriesGame(
                SeriesIdentity.gameId(seriesId, 1), 1, "GEN", "T1", TeamSide.BLUE,
                73L, List.of(), historyHash, state.gameStatus(), null,
                0, null, reservation, null, null, null);
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put("GEN", 0);
        score.put("T1", 0);
        SeriesAggregate base = new SeriesAggregate(
                seriesId, 0, state.seriesStatus(),
                state.seriesStatus() == SeriesStatus.BLOCKED ? "BLOCKED" : null,
                SeriesFormat.BO3, "GEN", "T1", "GEN", "GEN", "73", 73L,
                score, List.of(game), Set.of(), historyHash, null,
                START, START, START.plus(Duration.ofMinutes(120)), Map.of());
        LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>();
        for (int index = 0; index < receiptCount; index++) {
            SeriesCommandReceipt receipt = receipt(
                    "receipt-" + index, "CANCEL_DRAFT", "payload-" + index,
                    base, null);
            receipts.put(receipt.commandId(), receipt);
        }
        return base.copy(base.revision(), base.status(), base.terminalReason(),
                base.score(), base.games(), base.consumedPicks(), base.historyHash(),
                base.winnerTeamCode(), base.lastActivityAt(), base.expiresAt(), receipts);
    }

    private static SeriesCommandReceipt receipt(
            String commandId,
            String type,
            String payload,
            SeriesAggregate aggregate,
            SeriesChildDraft child
    ) {
        SeriesGame game = aggregate.currentGame();
        return new SeriesCommandReceipt(
                commandId, type, payload, SeriesCommandCompletion.SUCCEEDED,
                aggregate.revision(), aggregate.status(), game.gameNumber(), game.gameId(),
                game.status(), child == null ? null : child.revision(),
                child == null ? null : child.childId(),
                child == null ? null : child.generation(), child, "result",
                null, null, false);
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

    private static SeriesLifecycleConfiguration configuration(int receipts) {
        return new SeriesLifecycleConfiguration(
                32, Duration.ofMinutes(120), Duration.ofMinutes(30),
                Duration.ofMinutes(5), receipts);
    }

    private record StateCase(
            SeriesStatus seriesStatus,
            SeriesGameStatus gameStatus,
            boolean reserved,
            List<String> expectedBeforeCapacity
    ) { }
}

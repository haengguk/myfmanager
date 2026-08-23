package com.lolfm.application;

import com.lolfm.application.Phase13GB1RealMatchHarness.AuditMatchRun;
import com.lolfm.application.Phase13GB1RealMatchHarness.PreparedFixture;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.HoldoutJob;
import com.lolfm.application.Phase13GB3FrozenHoldoutContract.RunGuard;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.JungleTempoActionType;
import com.lolfm.simulator.JungleTempoReadinessStatus;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Complete authenticated B3 row, checkpoint, and worker evidence model. */
public final class Phase13GB3HoldoutModel {
    public static final String MATCH_ROW_SCHEMA = "PHASE_13G_B3_HOLDOUT_MATCH_ROW_V1";
    public static final String SYNTHETIC_ROW_SCHEMA = "PHASE_13G_B3_SYNTHETIC_MATCH_ROW_V1";
    public static final String CHECKPOINT_SCHEMA = "PHASE_13G_B3_FIXTURE_CHECKPOINT_V1";
    public static final String ROW_EVIDENCE_SCHEMA =
            "PHASE_13G_B3_MATCH_EXECUTION_EVIDENCE_V1";
    public static final String EXECUTION_EVIDENCE_SCHEMA =
            "PHASE_13G_B3_CHECKPOINT_EXECUTION_EVIDENCE_V1";
    public static final String WORKER_RECEIPT_SCHEMA = "PHASE_13G_B3_WORKER_RECEIPT_V1";
    public static final String RECEIPT_MANIFEST_SCHEMA =
            "PHASE_13G_B3_CHECKPOINT_RECEIPT_MANIFEST_V1";

    private Phase13GB3HoldoutModel() {
    }

    public record FinalPlayerState(
            TeamSide side,
            Position position,
            String playerId,
            String championId,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            int totalExperience,
            int level,
            String itemStage,
            boolean alive,
            boolean canFarm,
            int respawnAtSeconds,
            int farmResumeAtSeconds
    ) {
    }

    public record MatchRow(
            String schemaVersion,
            String jobId,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            int seedIndex,
            long seed,
            int profileIndex,
            SimulationRuntimeProfileId profileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            String resourceProvenanceHash,
            String rosterIdentityHash,
            String seriesHistoryBeforeHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String replayProvenanceHash,
            String timelineHash,
            String structuredDiagnosticsHash,
            long randomDrawCount,
            String randomTraceHash,
            String winnerTeamCode,
            TeamSide winnerSide,
            GameEndReason endReason,
            int durationSeconds,
            int blueKills,
            int redKills,
            int blueGold,
            int redGold,
            int blueDragons,
            int redDragons,
            int blueTowers,
            int redTowers,
            Phase13GB1RealMatchHarness.JungleCheckpoint blueJungleFinal,
            Phase13GB1RealMatchHarness.JungleCheckpoint redJungleFinal,
            int blueSupportCs,
            int redSupportCs,
            int jungleEconomyEvaluations,
            int jungleEconomyEligibleOutcomes,
            int jungleEconomySkippedOutcomes,
            int jungleEconomyAwardedCs,
            int jungleEconomyAwardedGold,
            int jungleEconomyAwardedExperience,
            int tempoEconomyUpdates,
            int tempoContinuityResets,
            double tempoCreditAddedSeconds,
            int tempoGankReadyObservations,
            int tempoCounterGankReadyObservations,
            int tempoGankConsumptions,
            int tempoCounterGankConsumptions,
            int jungleGankEvaluations,
            int jungleGankTriggerSuccesses,
            int jungleGankFallthroughs,
            int jungleGankAttempts,
            int counterGankAttempts,
            int laneCombatAttempts,
            long integrityErrorCount,
            boolean integrityClean,
            long economyIntegrityErrors,
            long progressionIntegrityErrors,
            long championPowerIntegrityErrors,
            long championMatchupIntegrityErrors,
            long compositionIntegrityErrors,
            long combatOutcomeIntegrityErrors,
            long objectivePriorityIntegrityErrors,
            long structureIntegrityErrors,
            long lanePhaseIntegrityErrors,
            long midGameMacroIntegrityErrors,
            List<Phase13GB2CalibrationModel.JungleObservation> jungleObservations,
            List<FinalPlayerState> finalPlayerStates
    ) {
        public MatchRow {
            boolean official = MATCH_ROW_SCHEMA.equals(schemaVersion)
                    && sampleLane == Phase13GB1AuditSchedule.SampleLane.HOLDOUT;
            boolean synthetic = SYNTHETIC_ROW_SCHEMA.equals(schemaVersion)
                    && sampleLane == Phase13GB1AuditSchedule.SampleLane.DRY_RUN;
            if (!official && !synthetic) {
                throw new IllegalArgumentException("B3 row schema and sample lane differ");
            }
            jungleObservations = List.copyOf(jungleObservations);
            finalPlayerStates = List.copyOf(finalPlayerStates);
            if (finalPlayerStates.size() != 10
                    || finalPlayerStates.stream().map(state ->
                            state.side() + "|" + state.position()).distinct().count() != 10) {
                throw new IllegalArgumentException("B3 row needs ten structured final players");
            }
            if (integrityClean != (integrityErrorCount == 0)) {
                throw new IllegalArgumentException("B3 integrity total and clean flag differ");
            }
        }

        static MatchRow from(
                HoldoutJob job,
                PreparedFixture prepared,
                AuditMatchRun run
        ) {
            requireMatches(job, run);
            return create(
                    MATCH_ROW_SCHEMA,
                    job.jobId(),
                    job.fixtureId(),
                    job.fixtureLane(),
                    job.pairId(),
                    job.blueTeamCode(),
                    job.redTeamCode(),
                    job.seriesGameNumber(),
                    job.sampleLane(),
                    job.seedIndex(),
                    job.seed(),
                    job.profileIndex(),
                    job.profileId(),
                    prepared,
                    run);
        }

        static MatchRow synthetic(
                Phase13GB1AuditSchedule.Fixture fixture,
                int profileIndex,
                PreparedFixture prepared,
                AuditMatchRun run
        ) {
            if (run.sampleLane() != Phase13GB1AuditSchedule.SampleLane.DRY_RUN
                    || run.seed() != Phase13GB1AuditSchedule.dryRunSeed(fixture)
                    || run.profileId() != Phase13GB3FrozenHoldoutContract.PROFILE_ORDER
                            .get(profileIndex)) {
                throw new IllegalArgumentException("Invalid B3 synthetic smoke row");
            }
            return create(
                    SYNTHETIC_ROW_SCHEMA,
                    fixture.fixtureId() + "|DRY_RUN|0|" + run.profileId(),
                    fixture.fixtureId(),
                    fixture.fixtureLane(),
                    fixture.pairId(),
                    fixture.blueTeamCode(),
                    fixture.redTeamCode(),
                    fixture.seriesGameNumber(),
                    Phase13GB1AuditSchedule.SampleLane.DRY_RUN,
                    0,
                    run.seed(),
                    profileIndex,
                    run.profileId(),
                    prepared,
                    run);
        }

        private static MatchRow create(
                String schema,
                String jobId,
                String fixtureId,
                Phase13GB1AuditSchedule.FixtureLane fixtureLane,
                String pairId,
                String blueTeamCode,
                String redTeamCode,
                int seriesGameNumber,
                Phase13GB1AuditSchedule.SampleLane sampleLane,
                int seedIndex,
                long seed,
                int profileIndex,
                SimulationRuntimeProfileId profileId,
                PreparedFixture prepared,
                AuditMatchRun run
        ) {
            var economy = run.jungleEconomyDiagnostics();
            var tempo = run.jungleTempoDiagnostics();
            var combat = run.combatDiagnostics();
            var integrity = run.integrityDiagnostics();
            List<FinalPlayerState> players = finalPlayers(prepared, run);
            return new MatchRow(
                    schema, jobId, fixtureId, fixtureLane, pairId,
                    blueTeamCode, redTeamCode, seriesGameNumber, sampleLane,
                    seedIndex, seed, profileIndex, profileId,
                    run.configurationHash(), run.activeGameplayRulesVersion(),
                    run.engineImplementationVersion(), run.resourceProvenanceHash(),
                    run.rosterIdentityHash(), run.seriesHistoryBeforeHash(),
                    run.draftDecisionHash(), run.finalDraftHash(),
                    run.finalAssignmentHash(), run.replayProvenanceHash(),
                    run.timelineHash(), run.structuredDiagnosticsHash(),
                    run.randomFingerprint().randomDrawCount(),
                    run.randomFingerprint().randomTraceHash(),
                    run.winnerTeamCode(), run.winnerSide(), run.endReason(),
                    run.durationSeconds(), run.blueKills(), run.redKills(),
                    run.blueGold(), run.redGold(), run.blueDragons(), run.redDragons(),
                    run.blueTowers(), run.redTowers(), run.blueJungle(), run.redJungle(),
                    supportCs(players, TeamSide.BLUE), supportCs(players, TeamSide.RED),
                    economy.evaluations(), economy.eligibleOutcomes(),
                    economy.skippedByReason().values().stream()
                            .mapToInt(Integer::intValue).sum(),
                    economy.awardedCs(), economy.awardedGold(), economy.awardedExperience(),
                    tempo.economyUpdates(), tempo.continuityResets(),
                    tempo.totalCreditAddedSeconds(),
                    tempo.gankReadinessByStatus().getOrDefault(
                            JungleTempoReadinessStatus.READY, 0),
                    tempo.counterGankReadinessByStatus().getOrDefault(
                            JungleTempoReadinessStatus.READY, 0),
                    tempo.actualConsumptions().getOrDefault(JungleTempoActionType.GANK, 0),
                    tempo.actualConsumptions().getOrDefault(
                            JungleTempoActionType.COUNTER_GANK, 0),
                    combat.jungleGankEvaluations(), combat.jungleGankTriggerSuccesses(),
                    combat.jungleGankFallthroughs(), combat.jungleGankAttempts(),
                    combat.counterGankAttempts(), combat.laneCombatAttempts(),
                    integrity.errorCount(), integrity.clean(),
                    integrity.economy().errorCount(), integrity.progression().errorCount(),
                    integrity.championPower().errorCount(),
                    integrity.championMatchup().errorCount(),
                    integrity.composition().errorCount(),
                    integrity.combatOutcome().errorCount(),
                    integrity.objectivePriority().errorCount(),
                    integrity.structure().errorCount(), integrity.lanePhase().errorCount(),
                    integrity.midGameMacro().errorCount(), observations(run), players);
        }

        private static void requireMatches(HoldoutJob job, AuditMatchRun run) {
            if (!job.fixtureId().equals(run.fixtureId())
                    || job.fixtureLane() != run.fixtureLane()
                    || job.sampleLane() != run.sampleLane()
                    || job.seed() != run.seed()
                    || job.profileId() != run.profileId()
                    || !job.blueTeamCode().equals(run.blueTeamCode())
                    || !job.redTeamCode().equals(run.redTeamCode())
                    || job.seriesGameNumber() != run.seriesGameNumber()) {
                throw new IllegalArgumentException("B3 execution differs from frozen job");
            }
        }

        private static List<FinalPlayerState> finalPlayers(
                PreparedFixture prepared,
                AuditMatchRun run
        ) {
            MatchSnapshot snapshot = run.timeline().getSnapshots().getLast();
            ArrayList<FinalPlayerState> result = new ArrayList<>(10);
            for (TeamSide side : TeamSide.values()) {
                for (Position position : Position.values()) {
                    PlayerSnapshot player = snapshot.getPlayerSnapshots().stream()
                            .filter(value -> value.getTeamSide() == side
                                    && value.getPosition() == position)
                            .findFirst().orElseThrow();
                    var playerId = prepared.realDraftFixture().playerIdsByMatchSlot()
                            .get(new PlayerKey(side, position));
                    if (playerId == null) {
                        throw new IllegalStateException("Missing structured B3 player ID");
                    }
                    result.add(new FinalPlayerState(
                            side, position, playerId.value(), player.getChampionId(),
                            player.getKills(), player.getDeaths(), player.getAssists(),
                            player.getCs(), player.getGold(), player.getTotalExperience(),
                            player.getLevel(), player.getItemStage().name(), player.isAlive(),
                            player.isCanFarm(), player.getRespawnAtSeconds(),
                            player.getFarmResumeAtSeconds()));
                }
            }
            return List.copyOf(result);
        }

        private static int supportCs(List<FinalPlayerState> players, TeamSide side) {
            return players.stream().filter(value -> value.side() == side
                    && value.position() == Position.SUPPORT).findFirst().orElseThrow().cs();
        }

        private static List<Phase13GB2CalibrationModel.JungleObservation> observations(
                AuditMatchRun run
        ) {
            ArrayList<Phase13GB2CalibrationModel.JungleObservation> result =
                    new ArrayList<>();
            for (int requested : Phase13GB3FrozenHoldoutContract.FIXED_CHECKPOINT_SECONDS) {
                if (run.durationSeconds() < requested) continue;
                MatchSnapshot snapshot = run.timeline().getSnapshots().stream()
                        .filter(value -> value.getTimeSeconds() >= requested)
                        .findFirst().orElseThrow();
                result.add(observation("FIXED", requested, snapshot, run, TeamSide.BLUE));
                result.add(observation("FIXED", requested, snapshot, run, TeamSide.RED));
            }
            MatchSnapshot last = run.timeline().getSnapshots().getLast();
            result.add(observation("FINAL", run.durationSeconds(), last, run, TeamSide.BLUE));
            result.add(observation("FINAL", run.durationSeconds(), last, run, TeamSide.RED));
            return List.copyOf(result);
        }

        private static Phase13GB2CalibrationModel.JungleObservation observation(
                String kind,
                int requested,
                MatchSnapshot snapshot,
                AuditMatchRun run,
                TeamSide side
        ) {
            PlayerSnapshot player = snapshot.getPlayerSnapshots().stream()
                    .filter(value -> value.getTeamSide() == side
                            && value.getPosition() == Position.JUNGLE)
                    .findFirst().orElseThrow();
            var identity = side == TeamSide.BLUE ? run.blueJungle() : run.redJungle();
            return new Phase13GB2CalibrationModel.JungleObservation(
                    kind, requested, snapshot.getTimeSeconds(), side,
                    identity.playerId(), identity.championId(), player.getKills(),
                    player.getDeaths(), player.getAssists(), player.getCs(), player.getGold(),
                    player.getTotalExperience(), player.getLevel(),
                    player.getItemStage().name(), player.isAlive(), player.isCanFarm());
        }
    }

    public record DeterminismReplayEvidence(
            String fixtureId,
            int seedIndex,
            long seed,
            SimulationRuntimeProfileId profileId,
            String replayProvenanceHash,
            String timelineHash,
            String structuredDiagnosticsHash,
            long randomDrawCount,
            String randomTraceHash,
            boolean fullStructuredDiagnosticsExact,
            boolean exact
    ) {
        static DeterminismReplayEvidence from(
                String fixtureId,
                int seedIndex,
                long seed,
                AuditMatchRun original,
                AuditMatchRun replay
        ) {
            boolean structured = original.structuredDiagnostics()
                    .equals(replay.structuredDiagnostics());
            boolean exact = original.replayProvenanceHash().equals(
                            replay.replayProvenanceHash())
                    && original.timelineHash().equals(replay.timelineHash())
                    && original.structuredDiagnosticsHash().equals(
                            replay.structuredDiagnosticsHash())
                    && original.randomFingerprint().equals(replay.randomFingerprint())
                    && Objects.equals(original.winnerSide(), replay.winnerSide())
                    && original.endReason() == replay.endReason()
                    && original.integrityDiagnostics().equals(replay.integrityDiagnostics())
                    && structured;
            return new DeterminismReplayEvidence(
                    fixtureId, seedIndex, seed, original.profileId(),
                    original.replayProvenanceHash(), original.timelineHash(),
                    original.structuredDiagnosticsHash(),
                    original.randomFingerprint().randomDrawCount(),
                    original.randomFingerprint().randomTraceHash(), structured, exact);
        }
    }

    public record MatchExecutionEvidence(
            String schemaVersion,
            String jobId,
            String replayProvenanceHash,
            String timelineHash,
            String structuredDiagnosticsHash,
            String rowPayloadSha256
    ) {
        public MatchExecutionEvidence {
            if (!ROW_EVIDENCE_SCHEMA.equals(schemaVersion)) throw new IllegalArgumentException();
            Phase13GB3FrozenHoldoutContract.requireHash(
                    replayProvenanceHash, "replayProvenanceHash");
            Phase13GB3FrozenHoldoutContract.requireHash(timelineHash, "timelineHash");
            Phase13GB3FrozenHoldoutContract.requireHash(
                    structuredDiagnosticsHash, "structuredDiagnosticsHash");
            Phase13GB3FrozenHoldoutContract.requireHash(
                    rowPayloadSha256, "rowPayloadSha256");
        }
    }

    public record CheckpointExecutionEvidence(
            String schemaVersion,
            String fixedDraftPayloadSha256,
            String determinismReplayPayloadSha256,
            List<MatchExecutionEvidence> rows,
            String combinedPayloadSha256
    ) {
        public CheckpointExecutionEvidence {
            if (!EXECUTION_EVIDENCE_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException();
            }
            rows = List.copyOf(rows);
            Phase13GB3FrozenHoldoutContract.requireHash(
                    fixedDraftPayloadSha256, "fixedDraftPayloadSha256");
            Phase13GB3FrozenHoldoutContract.requireHash(
                    determinismReplayPayloadSha256, "determinismReplayPayloadSha256");
            Phase13GB3FrozenHoldoutContract.requireHash(
                    combinedPayloadSha256, "combinedPayloadSha256");
        }
    }

    public record FixtureCheckpoint(
            String schemaVersion,
            String frozenContractHash,
            String runGuardHash,
            RunGuard runGuard,
            Phase13GB2CalibrationModel.FixedDraftRow fixedDraft,
            DeterminismReplayEvidence determinismReplay,
            CheckpointExecutionEvidence executionEvidence,
            List<MatchRow> rows
    ) {
        public FixtureCheckpoint {
            if (!CHECKPOINT_SCHEMA.equals(schemaVersion)) throw new IllegalArgumentException();
            Phase13GB3FrozenHoldoutContract.requireHash(
                    frozenContractHash, "frozenContractHash");
            Phase13GB3FrozenHoldoutContract.requireHash(runGuardHash, "runGuardHash");
            rows = List.copyOf(rows);
            if (rows.size() != Phase13GB3FrozenHoldoutContract.EXPECTED_ROWS_PER_FIXTURE
                    || executionEvidence.rows().size() != rows.size()) {
                throw new IllegalArgumentException("B3 checkpoint requires exactly 40 rows");
            }
        }
    }

    public record CheckpointPayloadReceipt(
            int fixtureIndex,
            String fixtureId,
            String fileName,
            int rowCount,
            String checkpointPayloadSha256
    ) {
        public CheckpointPayloadReceipt {
            if (fixtureIndex < 0
                    || rowCount != Phase13GB3FrozenHoldoutContract.EXPECTED_ROWS_PER_FIXTURE) {
                throw new IllegalArgumentException("Invalid B3 checkpoint receipt");
            }
            Phase13GB3FrozenHoldoutContract.requireHash(
                    checkpointPayloadSha256, "checkpointPayloadSha256");
        }
    }

    public record WorkerReceipt(
            String schemaVersion,
            String frozenContractHash,
            String runGuardHash,
            int shardIndex,
            int shardCount,
            String workerJvmIdentityHash,
            List<String> ownedFixtureIds,
            List<CheckpointPayloadReceipt> checkpoints
    ) {
        public WorkerReceipt {
            if (!WORKER_RECEIPT_SCHEMA.equals(schemaVersion)
                    || shardCount != 4 || shardIndex < 0 || shardIndex >= shardCount) {
                throw new IllegalArgumentException("Invalid B3 worker receipt");
            }
            Phase13GB3FrozenHoldoutContract.requireHash(
                    frozenContractHash, "frozenContractHash");
            Phase13GB3FrozenHoldoutContract.requireHash(runGuardHash, "runGuardHash");
            Phase13GB3FrozenHoldoutContract.requireHash(
                    workerJvmIdentityHash, "workerJvmIdentityHash");
            ownedFixtureIds = List.copyOf(ownedFixtureIds);
            checkpoints = List.copyOf(checkpoints);
        }
    }

    public record CheckpointReceiptManifest(
            String schemaVersion,
            String frozenContractHash,
            String runGuardHash,
            int expectedWorkerShardCount,
            int workerReceiptCount,
            int distinctFreshJvmCount,
            int checkpointCount,
            String checkpointPayloadManifestHash,
            List<CheckpointPayloadReceipt> checkpoints
    ) {
        public CheckpointReceiptManifest {
            if (!RECEIPT_MANIFEST_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Invalid B3 receipt manifest schema");
            }
            Phase13GB3FrozenHoldoutContract.requireHash(
                    frozenContractHash, "frozenContractHash");
            Phase13GB3FrozenHoldoutContract.requireHash(runGuardHash, "runGuardHash");
            Phase13GB3FrozenHoldoutContract.requireHash(
                    checkpointPayloadManifestHash, "checkpointPayloadManifestHash");
            checkpoints = List.copyOf(checkpoints);
        }
    }
}

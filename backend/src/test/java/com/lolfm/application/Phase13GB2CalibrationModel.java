package com.lolfm.application;

import com.lolfm.application.Phase13GB1AuditArtifactWriter.SourceTreeIdentity;
import com.lolfm.application.Phase13GB1RealMatchHarness.AuditMatchRun;
import com.lolfm.application.Phase13GB1RealMatchHarness.JungleCheckpoint;
import com.lolfm.application.Phase13GB2CalibrationContract.CalibrationJob;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compact, serializable B2 evidence model; complete B1 diagnostics remain hash-bound. */
public final class Phase13GB2CalibrationModel {
    public static final String MATCH_ROW_SCHEMA = "PHASE_13G_B2_CALIBRATION_MATCH_ROW_V1";
    public static final String CHECKPOINT_SCHEMA = "PHASE_13G_B2_FIXTURE_CHECKPOINT_V1";

    private Phase13GB2CalibrationModel() {
    }

    public record RunGuard(
            String schemaVersion,
            String scheduleVersion,
            String scheduleHash,
            String engineImplementationVersion,
            Map<SimulationRuntimeProfileId, String> configurationHashes,
            String resourceProvenanceHash,
            SourceTreeIdentity productionSourceTree,
            SourceTreeIdentity phase13GBHarnessSourceTree,
            int expectedFixtureCount,
            int expectedCalibrationMatchCount,
            int holdoutMatchExecutionCount
    ) {
        public RunGuard {
            if (!Phase13GB2CalibrationContract.SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B2 run guard schema");
            }
            Objects.requireNonNull(scheduleVersion, "scheduleVersion");
            Objects.requireNonNull(scheduleHash, "scheduleHash");
            Objects.requireNonNull(engineImplementationVersion, "engineImplementationVersion");
            EnumMap<SimulationRuntimeProfileId, String> copy =
                    new EnumMap<>(SimulationRuntimeProfileId.class);
            copy.putAll(configurationHashes);
            configurationHashes = Collections.unmodifiableMap(copy);
            Objects.requireNonNull(resourceProvenanceHash, "resourceProvenanceHash");
            Objects.requireNonNull(productionSourceTree, "productionSourceTree");
            Objects.requireNonNull(phase13GBHarnessSourceTree, "phase13GBHarnessSourceTree");
            if (configurationHashes.size()
                    != Phase13GB2CalibrationContract.EXPECTED_PROFILES_PER_SEED
                    || expectedFixtureCount != Phase13GB2CalibrationContract.EXPECTED_FIXTURES
                    || expectedCalibrationMatchCount
                            != Phase13GB2CalibrationContract.EXPECTED_MATCHES
                    || holdoutMatchExecutionCount != 0) {
                throw new IllegalArgumentException("B2 run guard violates frozen scope");
            }
        }
    }

    public record FixedDraftRow(
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            int productionOrchestrationCount,
            String fixtureReusePolicy,
            String seriesHistoryBeforeHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String blueBans,
            String redBans,
            String bluePicks,
            String redPicks,
            String canonicalAssignments
    ) {
        static FixedDraftRow from(
                Phase13GB1RealMatchHarness.PreparedFixture prepared,
                AuditMatchRun reference
        ) {
            var fixture = prepared.fixture();
            var result = prepared.realDraftFixture();
            var draft = result.draftResult();
            StringBuilder assignments = new StringBuilder();
            for (TeamSide side : TeamSide.values()) {
                for (Position position : Position.values()) {
                    if (!assignments.isEmpty()) assignments.append(';');
                    var assignment = result.matchChampionAssignments()
                            .get(new PlayerKey(side, position));
                    assignments.append(side).append(':').append(position).append('=')
                            .append(assignment.championId().value());
                }
            }
            return new FixedDraftRow(
                    fixture.fixtureId(),
                    fixture.fixtureLane(),
                    fixture.pairId(),
                    fixture.blueTeamCode(),
                    fixture.redTeamCode(),
                    fixture.seriesGameNumber(),
                    prepared.productionOrchestrationCount(),
                    prepared.reusePolicy(),
                    reference.seriesHistoryBeforeHash(),
                    reference.draftDecisionHash(),
                    reference.finalDraftHash(),
                    reference.finalAssignmentHash(),
                    championIds(draft.blueBans()),
                    championIds(draft.redBans()),
                    championIds(draft.bluePicks()),
                    championIds(draft.redPicks()),
                    assignments.toString());
        }

        private static String championIds(List<com.lolfm.champion.ChampionId> values) {
            return values.stream().map(com.lolfm.champion.ChampionId::value)
                    .collect(java.util.stream.Collectors.joining(";"));
        }
    }

    public record JungleObservation(
            String checkpointKind,
            int requestedTimeSeconds,
            int actualTimeSeconds,
            TeamSide side,
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
            boolean canFarm
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
            JungleCheckpoint blueJungleFinal,
            JungleCheckpoint redJungleFinal,
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
            List<JungleObservation> jungleObservations
    ) {
        public MatchRow {
            if (!MATCH_ROW_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B2 match row schema");
            }
            jungleObservations = List.copyOf(jungleObservations);
            if (sampleLane != Phase13GB1AuditSchedule.SampleLane.CALIBRATION) {
                throw new IllegalArgumentException("B2 match row consumed a non-calibration seed");
            }
            if (integrityClean != (integrityErrorCount == 0)) {
                throw new IllegalArgumentException("B2 integrity total and clean flag differ");
            }
        }

        static MatchRow from(CalibrationJob job, AuditMatchRun run) {
            requireMatchesJob(job, run);
            var economy = run.jungleEconomyDiagnostics();
            var tempo = run.jungleTempoDiagnostics();
            var combat = run.combatDiagnostics();
            var integrity = run.integrityDiagnostics();
            return new MatchRow(
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
                    run.configurationHash(),
                    run.activeGameplayRulesVersion(),
                    run.engineImplementationVersion(),
                    run.resourceProvenanceHash(),
                    run.rosterIdentityHash(),
                    run.seriesHistoryBeforeHash(),
                    run.draftDecisionHash(),
                    run.finalDraftHash(),
                    run.finalAssignmentHash(),
                    run.replayProvenanceHash(),
                    run.timelineHash(),
                    run.structuredDiagnosticsHash(),
                    run.randomFingerprint().randomDrawCount(),
                    run.randomFingerprint().randomTraceHash(),
                    run.winnerTeamCode(),
                    run.winnerSide(),
                    run.endReason(),
                    run.durationSeconds(),
                    run.blueKills(),
                    run.redKills(),
                    run.blueGold(),
                    run.redGold(),
                    run.blueDragons(),
                    run.redDragons(),
                    run.blueTowers(),
                    run.redTowers(),
                    run.blueJungle(),
                    run.redJungle(),
                    economy.evaluations(),
                    economy.eligibleOutcomes(),
                    economy.skippedByReason().values().stream()
                            .mapToInt(Integer::intValue).sum(),
                    economy.awardedCs(),
                    economy.awardedGold(),
                    economy.awardedExperience(),
                    tempo.economyUpdates(),
                    tempo.continuityResets(),
                    tempo.totalCreditAddedSeconds(),
                    tempo.gankReadinessByStatus().getOrDefault(
                            JungleTempoReadinessStatus.READY, 0),
                    tempo.counterGankReadinessByStatus().getOrDefault(
                            JungleTempoReadinessStatus.READY, 0),
                    tempo.actualConsumptions().getOrDefault(JungleTempoActionType.GANK, 0),
                    tempo.actualConsumptions().getOrDefault(
                            JungleTempoActionType.COUNTER_GANK, 0),
                    combat.jungleGankEvaluations(),
                    combat.jungleGankTriggerSuccesses(),
                    combat.jungleGankFallthroughs(),
                    combat.jungleGankAttempts(),
                    combat.counterGankAttempts(),
                    combat.laneCombatAttempts(),
                    integrity.errorCount(),
                    integrity.clean(),
                    integrity.economy().errorCount(),
                    integrity.progression().errorCount(),
                    integrity.championPower().errorCount(),
                    integrity.championMatchup().errorCount(),
                    integrity.composition().errorCount(),
                    integrity.combatOutcome().errorCount(),
                    integrity.objectivePriority().errorCount(),
                    integrity.structure().errorCount(),
                    integrity.lanePhase().errorCount(),
                    integrity.midGameMacro().errorCount(),
                    observations(run));
        }

        private static void requireMatchesJob(CalibrationJob job, AuditMatchRun run) {
            if (!job.fixtureId().equals(run.fixtureId())
                    || job.fixtureLane() != run.fixtureLane()
                    || job.sampleLane() != run.sampleLane()
                    || job.seed() != run.seed()
                    || job.profileId() != run.profileId()
                    || !job.blueTeamCode().equals(run.blueTeamCode())
                    || !job.redTeamCode().equals(run.redTeamCode())
                    || job.seriesGameNumber() != run.seriesGameNumber()) {
                throw new IllegalArgumentException("B2 execution differs from its frozen job");
            }
        }

        private static List<JungleObservation> observations(AuditMatchRun run) {
            ArrayList<JungleObservation> result = new ArrayList<>();
            for (int requested : Phase13GB2CalibrationContract.FIXED_CHECKPOINT_SECONDS) {
                if (run.durationSeconds() < requested) continue;
                MatchSnapshot snapshot = run.timeline().getSnapshots().stream()
                        .filter(value -> value.getTimeSeconds() >= requested)
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "Missing B2 snapshot at or after checkpoint " + requested
                                        + " for " + run.fixtureId()
                                        + " jobProfile=" + run.profileId()
                                        + " duration=" + run.durationSeconds()
                                        + " snapshotRange="
                                        + run.timeline().getSnapshots().getFirst()
                                                .getTimeSeconds()
                                        + ".."
                                        + run.timeline().getSnapshots().getLast()
                                                .getTimeSeconds()
                                        + " snapshotCount="
                                        + run.timeline().getSnapshots().size()));
                result.add(observation("FIXED", requested, snapshot, run, TeamSide.BLUE));
                result.add(observation("FIXED", requested, snapshot, run, TeamSide.RED));
            }
            MatchSnapshot last = run.timeline().getSnapshots().getLast();
            result.add(observation(
                    "FINAL", run.durationSeconds(), last, run, TeamSide.BLUE));
            result.add(observation(
                    "FINAL", run.durationSeconds(), last, run, TeamSide.RED));
            return List.copyOf(result);
        }

        private static JungleObservation observation(
                String kind,
                int requested,
                MatchSnapshot snapshot,
                AuditMatchRun run,
                TeamSide side
        ) {
            PlayerSnapshot player = snapshot.getPlayerSnapshots().stream()
                    .filter(value -> value.getTeamSide() == side
                            && value.getPosition() == Position.JUNGLE)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Missing B2 jungle checkpoint participant " + side));
            JungleCheckpoint identity = side == TeamSide.BLUE
                    ? run.blueJungle() : run.redJungle();
            return new JungleObservation(
                    kind,
                    requested,
                    snapshot.getTimeSeconds(),
                    side,
                    identity.playerId(),
                    identity.championId(),
                    player.getKills(),
                    player.getDeaths(),
                    player.getAssists(),
                    player.getCs(),
                    player.getGold(),
                    player.getTotalExperience(),
                    player.getLevel(),
                    player.getItemStage().name(),
                    player.isAlive(),
                    player.isCanFarm());
        }
    }

    public record FixtureCheckpoint(
            String schemaVersion,
            String runGuardHash,
            RunGuard runGuard,
            FixedDraftRow fixedDraft,
            DeterminismReplayEvidence determinismReplay,
            List<MatchRow> rows
    ) {
        public FixtureCheckpoint {
            if (!CHECKPOINT_SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B2 checkpoint schema");
            }
            Objects.requireNonNull(runGuardHash, "runGuardHash");
            Objects.requireNonNull(runGuard, "runGuard");
            Objects.requireNonNull(fixedDraft, "fixedDraft");
            Objects.requireNonNull(determinismReplay, "determinismReplay");
            rows = List.copyOf(rows);
            if (rows.size() != Phase13GB2CalibrationContract.EXPECTED_ROWS_PER_FIXTURE
                    || rows.stream().anyMatch(row -> !row.fixtureId()
                            .equals(fixedDraft.fixtureId()))) {
                throw new IllegalArgumentException(
                        "A B2 fixture checkpoint must contain exactly 120 matching rows");
            }
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
            boolean structuredExact = original.structuredDiagnostics()
                    .equals(replay.structuredDiagnostics());
            boolean exact = original.fixtureId().equals(replay.fixtureId())
                    && original.seed() == replay.seed()
                    && original.profileId() == replay.profileId()
                    && original.replayProvenanceHash().equals(replay.replayProvenanceHash())
                    && original.timelineHash().equals(replay.timelineHash())
                    && original.structuredDiagnosticsHash()
                            .equals(replay.structuredDiagnosticsHash())
                    && original.randomFingerprint().equals(replay.randomFingerprint())
                    && Objects.equals(original.winnerSide(), replay.winnerSide())
                    && original.endReason() == replay.endReason()
                    && original.integrityDiagnostics().equals(replay.integrityDiagnostics())
                    && structuredExact;
            return new DeterminismReplayEvidence(
                    fixtureId,
                    seedIndex,
                    seed,
                    original.profileId(),
                    original.replayProvenanceHash(),
                    original.timelineHash(),
                    original.structuredDiagnosticsHash(),
                    original.randomFingerprint().randomDrawCount(),
                    original.randomFingerprint().randomTraceHash(),
                    structuredExact,
                    exact);
        }
    }

    public record MarginalRow(
            String comparisonId,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            String pairId,
            String blueTeamCode,
            String redTeamCode,
            int seedIndex,
            long seed,
            SimulationRuntimeProfileId fromProfile,
            SimulationRuntimeProfileId toProfile,
            TeamSide fromWinner,
            TeamSide toWinner,
            boolean winnerFlipped,
            int durationDelta,
            int blueGoldEdgeDelta,
            int totalKillsDelta,
            int totalDragonsDelta,
            int totalTowersDelta,
            int blueJungleCsDelta,
            int blueJungleGoldDelta,
            int blueJungleExperienceDelta,
            int blueJungleLevelDelta,
            int redJungleCsDelta,
            int redJungleGoldDelta,
            int redJungleExperienceDelta,
            int redJungleLevelDelta,
            int jungleGankAttemptsDelta,
            int counterGankAttemptsDelta
    ) {
        static MarginalRow from(
                Phase13GB2CalibrationContract.MarginalComparison comparison,
                MatchRow from,
                MatchRow to
        ) {
            if (!from.fixtureId().equals(to.fixtureId())
                    || from.seed() != to.seed()
                    || from.seedIndex() != to.seedIndex()
                    || from.profileId() != comparison.fromProfile()
                    || to.profileId() != comparison.toProfile()) {
                throw new IllegalArgumentException("Unpaired B2 marginal comparison");
            }
            return new MarginalRow(
                    comparison.comparisonId(),
                    from.fixtureId(),
                    from.fixtureLane(),
                    from.pairId(),
                    from.blueTeamCode(),
                    from.redTeamCode(),
                    from.seedIndex(),
                    from.seed(),
                    comparison.fromProfile(),
                    comparison.toProfile(),
                    from.winnerSide(),
                    to.winnerSide(),
                    !Objects.equals(from.winnerSide(), to.winnerSide()),
                    to.durationSeconds() - from.durationSeconds(),
                    goldEdge(to) - goldEdge(from),
                    totalKills(to) - totalKills(from),
                    totalDragons(to) - totalDragons(from),
                    totalTowers(to) - totalTowers(from),
                    to.blueJungleFinal().cs() - from.blueJungleFinal().cs(),
                    to.blueJungleFinal().gold() - from.blueJungleFinal().gold(),
                    to.blueJungleFinal().totalExperience()
                            - from.blueJungleFinal().totalExperience(),
                    to.blueJungleFinal().level() - from.blueJungleFinal().level(),
                    to.redJungleFinal().cs() - from.redJungleFinal().cs(),
                    to.redJungleFinal().gold() - from.redJungleFinal().gold(),
                    to.redJungleFinal().totalExperience()
                            - from.redJungleFinal().totalExperience(),
                    to.redJungleFinal().level() - from.redJungleFinal().level(),
                    to.jungleGankAttempts() - from.jungleGankAttempts(),
                    to.counterGankAttempts() - from.counterGankAttempts());
        }

        private static int goldEdge(MatchRow row) {
            return row.blueGold() - row.redGold();
        }

        private static int totalKills(MatchRow row) {
            return row.blueKills() + row.redKills();
        }

        private static int totalDragons(MatchRow row) {
            return row.blueDragons() + row.redDragons();
        }

        private static int totalTowers(MatchRow row) {
            return row.blueTowers() + row.redTowers();
        }
    }

    static Map<String, MatchRow> rowsByProfile(List<MatchRow> rows) {
        LinkedHashMap<String, MatchRow> result = new LinkedHashMap<>();
        for (MatchRow row : rows) {
            String key = row.fixtureId() + '|' + row.seedIndex() + '|' + row.profileId();
            if (result.put(key, row) != null) {
                throw new IllegalArgumentException("Duplicate B2 match row " + key);
            }
        }
        return Map.copyOf(result);
    }
}

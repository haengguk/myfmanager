package com.lolfm.simulator;

import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Team;
import java.util.Objects;

/** Test-side bridge that exposes structured diagnostics without widening the production API. */
public final class Phase13GB1SimulationExecutor {
    private Phase13GB1SimulationExecutor() {
    }

    public static Execution execute(
            ConfiguredMatchSimulatorFactory simulators,
            Team blueTeam,
            Team redTeam,
            MatchChampionAssignments assignments,
            SimulationRuntimeProfileId profileId,
            long seed,
            String blueTeamCode,
            String redTeamCode
    ) {
        Objects.requireNonNull(simulators, "simulators");
        Objects.requireNonNull(assignments, "assignments");
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                seed,
                "PHASE_13G_B_FIXED_DRAFT",
                Objects.requireNonNull(blueTeamCode, "blueTeamCode"),
                Objects.requireNonNull(redTeamCode, "redTeamCode"),
                false);
        MatchSimulator.SimulationResult result = simulators.create(
                        Objects.requireNonNull(profileId, "profileId"),
                        SimulationInstrumentation.enabled())
                .simulateWithSideDiagnostics(blueTeam, redTeam, assignments, random);
        SimulationRandomFingerprint fingerprint = random.fingerprint();
        if (result.randomDrawCount() != fingerprint.randomDrawCount()
                || !result.randomTraceHash().equals(fingerprint.randomTraceHash())) {
            throw new IllegalStateException("Structured Random diagnostics differ from fingerprint");
        }
        return new Execution(
                result.timeline(),
                result.endReason(),
                result.winnerSide(),
                result.duplicateEconomyResolutions(),
                result.combatExecutionStats(),
                result.progressionExecutionStats(),
                result.jungleEconomyExecutionStats(),
                result.jungleTempoExecutionStats(),
                fingerprint);
    }

    public record Execution(
            MatchTimeline timeline,
            GameEndReason endReason,
            TeamSide winnerSide,
            int duplicateEconomyResolutions,
            CombatExecutionStatsSnapshot combat,
            ProgressionExecutionStatsSnapshot progression,
            JungleEconomyExecutionStatsSnapshot jungleEconomy,
            JungleTempoExecutionStatsSnapshot jungleTempo,
            SimulationRandomFingerprint randomFingerprint
    ) {
        public Execution {
            Objects.requireNonNull(timeline, "timeline");
            Objects.requireNonNull(endReason, "endReason");
            Objects.requireNonNull(combat, "combat");
            Objects.requireNonNull(progression, "progression");
            Objects.requireNonNull(jungleEconomy, "jungleEconomy");
            Objects.requireNonNull(jungleTempo, "jungleTempo");
            Objects.requireNonNull(randomFingerprint, "randomFingerprint");
        }
    }
}

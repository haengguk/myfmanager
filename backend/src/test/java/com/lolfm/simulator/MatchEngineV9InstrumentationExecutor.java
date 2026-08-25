package com.lolfm.simulator;

import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Team;
import java.util.Objects;

/** Test-side bridge for observational instrumentation parity without widening production API. */
public final class MatchEngineV9InstrumentationExecutor {
    private MatchEngineV9InstrumentationExecutor() {
    }

    public static Result execute(
            ConfiguredMatchSimulatorFactory simulators,
            Team blueTeam,
            Team redTeam,
            MatchChampionAssignments assignments,
            SimulationRuntimeProfileId profileId,
            SimulationInstrumentation instrumentation,
            long seed,
            String blueTeamCode,
            String redTeamCode
    ) {
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                seed, "PHASE_13G_B_FIXED_DRAFT", blueTeamCode, redTeamCode, false);
        var result = Objects.requireNonNull(simulators).create(profileId, instrumentation)
                .simulateWithSideDiagnostics(blueTeam, redTeam, assignments, random);
        SimulationRandomFingerprint fingerprint = random.fingerprint();
        if (result.randomDrawCount() != fingerprint.randomDrawCount()
                || !result.randomTraceHash().equals(fingerprint.randomTraceHash())) {
            throw new IllegalStateException("Instrumentation parity Random trace mismatch");
        }
        return new Result(result.timeline(), fingerprint);
    }

    public record Result(MatchTimeline timeline, SimulationRandomFingerprint randomFingerprint) { }
}

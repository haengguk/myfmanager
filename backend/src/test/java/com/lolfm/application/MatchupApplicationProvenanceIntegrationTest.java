package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;

import com.lolfm.champion.ChampionMatchupApplicationPoint;
import com.lolfm.champion.ChampionMatchupLaneScope;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.MatchEngineV9InstrumentationExecutor;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MatchupApplicationProvenanceIntegrationTest {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;

    @Test
    void offIsExactZeroAndGeometricRecordsStructuredConsumedApplications() {
        RealDraftMatchResult prepared = orchestrator.orchestrate(
                "GEN", "T1", 73L, SimulationRuntimeProfileId.BASELINE_V1);
        var baseline = execute(prepared, SimulationRuntimeProfileId.BASELINE_V1);
        var matchup = execute(prepared, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        var off = baseline.structuredDiagnostics().championMatchup();
        var on = matchup.structuredDiagnostics().championMatchup();

        assertThat(off.consumedApplicationCount()).isZero();
        assertThat(off.nonZeroConsumedApplicationCount()).isZero();
        assertThat(off.applicationProvenance()).isEmpty();
        assertThat(on.consumedApplicationCount()).isPositive();
        assertThat(on.nonZeroConsumedApplicationCount()).isPositive();
        assertThat(on.duplicateConsumedApplicationErrors()).isZero();
        assertThat(on.applicationBindingErrors()).isZero();
        assertThat(on.directRandomCalls()).isZero();
        assertThat(on.applicationProvenance()).allSatisfy(value -> {
            assertThat(value.applicationIdentity()).startsWith("MATCHUP_APPLICATION:");
            assertThat(value.consumerScoreAfter() - value.consumerScoreBefore())
                    .isEqualTo(value.actualConsumerInputDelta());
            assertThat(value.pairApplications()).isNotEmpty();
            if (value.applicationPoint()
                    == ChampionMatchupApplicationPoint.COMBAT_PROGRESSION_SCORE) {
                assertThat(value.structuredActionId()).isNotBlank();
                assertThat(value.stateMutationLineage()).isNull();
            } else {
                assertThat(value.structuredActionId()).isNull();
                assertThat(value.stateMutationLineage()).isNotNull();
            }
            value.pairApplications().forEach(pair -> {
                assertThat(pair.source().position()).isEqualTo(pair.opponent().position());
                assertThat(pair.source().playerKey().side())
                        .isNotEqualTo(pair.opponent().playerKey().side());
            });
        });
        assertThat(on.applicationProvenance()).extracting(value -> value.applicationPoint())
                .contains(ChampionMatchupApplicationPoint.LANE_OPPORTUNITY_PRESSURE,
                        ChampionMatchupApplicationPoint.COMBAT_PROGRESSION_SCORE);
        assertThat(on.applicationProvenance()).extracting(value -> value.laneScope())
                .contains(ChampionMatchupLaneScope.TOP, ChampionMatchupLaneScope.MID,
                        ChampionMatchupLaneScope.BOT);
        EnumSet<Position> positions = EnumSet.noneOf(Position.class);
        on.applicationProvenance().forEach(value -> value.pairApplications()
                .forEach(pair -> positions.add(pair.source().position())));
        assertThat(positions).containsExactlyInAnyOrder(Position.values());
        assertThat(on.stateConsumerProvenance()).allSatisfy(value -> {
            assertThat(value.consumerActionId()).startsWith("COMBAT_AT:");
            assertThat(value.consumerTimeSeconds())
                    .isGreaterThanOrEqualTo(value.mutationTimeSeconds());
        });
        var indirect = on.stateConsumerProvenance().stream().findFirst().orElseThrow();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactIndirectBinding(
                on.stateConsumerProvenance(), indirect.consumerTimeSeconds(),
                List.of(indirect.consumerActionId()),
                List.of(indirect.consumerContext()))).isTrue();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactIndirectBinding(
                on.stateConsumerProvenance(), indirect.consumerTimeSeconds(),
                List.of("WRONG_ACTION"), List.of(indirect.consumerContext()))).isFalse();
        assertThat(MatchEngineV9FreshRequalificationRunner.unresolvedStateObserved(
                on.applicationProvenance(), indirect.consumerTimeSeconds())).isTrue();
        var direct = on.applicationProvenance().stream()
                .filter(value -> value.applicationPoint()
                        == ChampionMatchupApplicationPoint.COMBAT_PROGRESSION_SCORE)
                .findFirst().orElseThrow();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactDirectBinding(
                direct, direct.simulationTimeSeconds(), List.of(direct.structuredActionId()),
                List.of(direct.context()), List.of(direct.applicationStage()))).isTrue();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactDirectBinding(
                direct, direct.simulationTimeSeconds() + 1,
                List.of(direct.structuredActionId()), List.of(direct.context()),
                List.of(direct.applicationStage()))).isFalse();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactDirectBinding(
                direct, direct.simulationTimeSeconds(), List.of("WRONG_ACTION"),
                List.of(direct.context()), List.of(direct.applicationStage()))).isFalse();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactDirectBinding(
                direct, direct.simulationTimeSeconds(), List.of(direct.structuredActionId()),
                List.of(direct.context()
                        == com.lolfm.simulator.ProgressionCombatContext.LANE_COMBAT
                        ? com.lolfm.simulator.ProgressionCombatContext.GENERIC_SKIRMISH
                        : com.lolfm.simulator.ProgressionCombatContext.LANE_COMBAT),
                List.of(direct.applicationStage()))).isFalse();
        assertThat(MatchEngineV9FreshRequalificationRunner.exactDirectBinding(
                direct, direct.simulationTimeSeconds(), List.of(direct.structuredActionId()),
                List.of(direct.context()),
                List.of(direct.applicationStage()
                        == com.lolfm.simulator.ProgressionApplicationStage.COMBAT_SCORE
                        ? com.lolfm.simulator.ProgressionApplicationStage.INITIATIVE
                        : com.lolfm.simulator.ProgressionApplicationStage.COMBAT_SCORE)))
                .isFalse();
    }

    @Test
    void sameSeedInstrumentationAndNewMatchIsolationRemainExact() {
        RealDraftMatchResult prepared = orchestrator.orchestrate(
                "DK", "HLE", -73L, SimulationRuntimeProfileId.BASELINE_V1);
        var first = execute(prepared, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        var replay = execute(prepared, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        var disabled = MatchEngineV9InstrumentationExecutor.execute(
                simulators, prepared.blueTeam(), prepared.redTeam(),
                prepared.matchChampionAssignments(),
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationInstrumentation.disabled(), prepared.matchSeed(), "DK", "HLE");

        assertCompleteTimelineEquals(first.timeline(), replay.timeline());
        assertThat(first.randomFingerprint()).isEqualTo(replay.randomFingerprint());
        assertThat(first.structuredDiagnostics().championMatchup())
                .isEqualTo(replay.structuredDiagnostics().championMatchup());
        assertCompleteTimelineEquals(first.timeline(), disabled.timeline());
        assertThat(disabled.randomFingerprint()).isEqualTo(first.randomFingerprint());
        assertThat(first.structuredDiagnostics().championMatchup()
                .applicationProvenance().getFirst().applicationSequence()).isOne();
        assertThat(replay.structuredDiagnostics().championMatchup()
                .applicationProvenance().getFirst().applicationSequence()).isOne();
    }

    private Phase13GB1SimulationExecutor.Execution execute(
            RealDraftMatchResult prepared, SimulationRuntimeProfileId profile) {
        return Phase13GB1SimulationExecutor.execute(
                simulators, prepared.blueTeam(), prepared.redTeam(),
                prepared.matchChampionAssignments(), profile, prepared.matchSeed(),
                prepared.blueTeamCode(), prepared.redTeamCode());
    }
}

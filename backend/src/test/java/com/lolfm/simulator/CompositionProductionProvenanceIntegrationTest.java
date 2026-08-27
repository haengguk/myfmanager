package com.lolfm.simulator;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.composition.CompositionApplicationProvenance;
import com.lolfm.composition.CompositionPublicEventIdentity;
import com.lolfm.composition.CompositionScoreOrientation;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.factory.DummyDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CompositionProductionProvenanceIntegrationTest {
    private static final long SYNTHETIC_SEED = 0x434f4d5056320011L;

    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ChampionCatalog champions;

    @Test
    void productionApplicationsBindAttemptConsumerOutcomeAndPublicActionExactlyOnce() {
        MatchSimulator.SimulationResult result = execute(SimulationInstrumentation.enabled());
        CompositionRuntimeDiagnostics diagnostics = result.compositionRuntimeDiagnostics();

        assertThat(diagnostics.schemaVersion()).isEqualTo(CompositionRuntimeDiagnostics.SCHEMA_VERSION);
        assertThat(diagnostics.mode().name()).isEqualTo("PRODUCTION_V2");
        assertThat(diagnostics.gameplayApplicationCount()).isPositive();
        assertThat(diagnostics.gameplayApplicationCount()).isEqualTo(diagnostics.modifierConsumedCount());
        assertThat(diagnostics.totalCompositionEffectApplicationCount())
                .isEqualTo(diagnostics.applicationProvenance().stream()
                        .filter(CompositionApplicationProvenance::applicationApplied).count());
        assertThat(diagnostics.winnerDecisionProvenance()).hasSize(diagnostics.gameplayApplicationCount());
        assertThat(diagnostics.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::applicationApplied).toList()).allSatisfy(value -> {
                    assertThat(value.gameplayConsumerIdentity()).isNotEqualTo("NOT_REACHED");
                    assertThat(value.publicBindingStatus()).isNotEqualTo("NOT_BOUND");
                    assertThat(value.publicEventBindings()).isNotEmpty();
                    assertThat(value.randomDrawOrdinal()).isNotNull();
                    assertThat(value.randomSample()).isNotNull();
                    assertThat(value.publicEventBindings()).allSatisfy(binding -> {
                        assertThat(binding.actionId()).isNotBlank();
                        assertThat(binding.eventOrdinal()).isLessThan(result.timeline().getEvents().size());
                        assertThat(binding).isEqualTo(CompositionPublicEventIdentity.from(
                                result.timeline().getEvents().get(binding.eventOrdinal()),
                                binding.eventOrdinal()));
                    });
                });
        assertThat(diagnostics.applicationProvenance().stream()
                .filter(CompositionApplicationProvenance::modifierConsumed).toList()).allSatisfy(value -> {
                    assertThat(value.modifierCalculated()).isTrue();
                    assertThat(value.modifier()).isEqualTo(value.rawCompositionEdge() * value.frozenGain());
                    assertThat(value.totalCompositionInputDelta())
                            .isEqualTo(value.adjustedGap() - value.baselineGap());
                    assertThat(value.candidateScoreBeforeClamp() - value.baselineScoreBeforeClamp())
                            .isCloseTo(value.modifier()
                                    + value.existingNonScalarCompositionDelta(), within(1e-12));
                    assertThat(value.clampEffect()).isCloseTo(
                            value.candidateClampDelta() - value.baselineClampDelta(), within(1e-12));
                    assertThat(value.totalCompositionInputDelta()).isCloseTo(
                            value.modifier() + value.existingNonScalarCompositionDelta()
                                    + value.clampEffect(), within(1e-12));
                    assertThat(value.context()).isIn(TeamCompositionContext.SKIRMISH,
                            TeamCompositionContext.TEAMFIGHT, TeamCompositionContext.SIEGE,
                            TeamCompositionContext.BASE_DEFENSE);
                });
        assertThat(diagnostics.applicationProvenance().stream()
                .filter(value -> value.context() == TeamCompositionContext.SKIRMISH
                        && value.modifierConsumed()).toList()).isNotEmpty().allSatisfy(value -> {
                    assertThat(Double.doubleToRawLongBits(value.existingNonScalarCompositionDelta()))
                            .isEqualTo(Double.doubleToRawLongBits(0.0d));
                    assertThat(Double.doubleToRawLongBits(value.clampEffect()))
                            .isEqualTo(Double.doubleToRawLongBits(0.0d));
                });
        assertThat(diagnostics.applicationProvenance()).anySatisfy(value -> {
            assertThat(value.context()).isIn(TeamCompositionContext.TEAMFIGHT,
                    TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE);
            assertThat(value.existingNonScalarCompositionDelta()).isNotZero();
        });
        assertThat(diagnostics.applicationProvenance().stream()
                .filter(value -> value.context() == TeamCompositionContext.OBJECTIVE_SETUP
                        || value.context() == TeamCompositionContext.SIDE_LANE).toList()).allSatisfy(value -> {
                    assertThat(value.approvalStatus()).isEqualTo("DISABLED_NOT_APPROVED");
                    assertThat(value.modifierConsumed()).isFalse();
                    if (value.applicationApplied()) {
                        assertThat(value.context()).isEqualTo(TeamCompositionContext.OBJECTIVE_SETUP);
                        assertThat(value.existingNonScalarEffectConsumed()).isTrue();
                        assertThat(value.routingPerspectiveSide())
                                .isEqualTo(value.attemptOwnerSide());
                        assertThat(value.scoreOrientation())
                                .isEqualTo(CompositionScoreOrientation.BLUE_MINUS_RED);
                        assertThat(value.perspectiveSide()).isEqualTo(TeamSide.BLUE);
                    }
                });
        assertThat(diagnostics.candidateApplications()).isEmpty();
        assertThat(diagnostics.localDecisionComparisons()).isEmpty();
        assertThat(diagnostics.directRandomCallCount()).isZero();
        assertThat(diagnostics.compositionRandomDrawCount()).isZero();
        assertThat(diagnostics.duplicateApplicationPointCount()).isZero();
        assertThat(diagnostics.multiContextAttemptCount()).isZero();
        assertThat(diagnostics.conflictingPerspectiveCount()).isZero();
        assertThat(diagnostics.duplicatePublicBindingCount()).isZero();
        assertThat(diagnostics.conflictingPublicBindingCount()).isZero();
        assertThat(result.jungleEconomyExecutionStats().evaluations()).isZero();
        assertThat(result.jungleEconomyExecutionStats().awardedCs()).isZero();
        assertThat(result.jungleEconomyExecutionStats().awardedGold()).isZero();
        assertThat(result.jungleEconomyExecutionStats().awardedExperience()).isZero();
        assertThat(result.jungleTempoExecutionStats().economyUpdates()).isZero();
        assertThat(result.jungleTempoExecutionStats().actualConsumptions().values())
                .containsOnly(0);
    }

    @Test
    void instrumentationToggleIsExactForTimelineRandomWinnerAndCompositionProvenance() {
        MatchSimulator.SimulationResult enabled = execute(SimulationInstrumentation.enabled());
        MatchSimulator.SimulationResult disabled = execute(SimulationInstrumentation.disabled());

        assertCompleteTimelineEquals(disabled.timeline(), enabled.timeline());
        assertThat(disabled.winnerSide()).isEqualTo(enabled.winnerSide());
        assertThat(disabled.endReason()).isEqualTo(enabled.endReason());
        assertThat(disabled.randomDrawCount()).isEqualTo(enabled.randomDrawCount());
        assertThat(disabled.randomTraceHash()).isEqualTo(enabled.randomTraceHash());
        assertThat(disabled.compositionRuntimeDiagnostics().applicationProvenance())
                .isEqualTo(enabled.compositionRuntimeDiagnostics().applicationProvenance());
        assertThat(disabled.compositionRuntimeDiagnostics().winnerDecisionProvenance())
                .isEqualTo(enabled.compositionRuntimeDiagnostics().winnerDecisionProvenance());
    }

    @Test
    void sameSeedReplayIsExactAndFreshMatchStateDoesNotAccumulate() {
        MatchSimulator.SimulationResult first = execute(SimulationInstrumentation.enabled());
        MatchSimulator.SimulationResult replay = execute(SimulationInstrumentation.enabled());

        assertCompleteTimelineEquals(replay.timeline(), first.timeline());
        assertThat(replay.randomTraceHash()).isEqualTo(first.randomTraceHash());
        assertThat(replay.compositionRuntimeDiagnostics().applicationProvenance())
                .isEqualTo(first.compositionRuntimeDiagnostics().applicationProvenance());
        assertThat(replay.compositionRuntimeDiagnostics().applicationProvenance().getFirst().attemptId().sequence())
                .isEqualTo(1L);
    }

    private MatchSimulator.SimulationResult execute(SimulationInstrumentation instrumentation) {
        DummyDataFactory teams = new DummyDataFactory();
        var assignments = new ChampionSelectionValidator(champions).resolve(null);
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                SYNTHETIC_SEED, "COMPOSITION_V9_PROVENANCE_FOCUSED_TEST", "BLUE", "RED", false);
        return simulators.create(
                        SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1,
                        instrumentation)
                .simulateWithSideDiagnostics(teams.createBlueTeam(), teams.createRedTeam(), assignments, random);
    }
}

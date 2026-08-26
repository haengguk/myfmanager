package com.lolfm.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositionProductionApplicationProvenanceTest {
    @Test
    void productionSeparatesAttemptEligibilityApplicationConsumptionAndLocalOutcome() {
        CompositionRuntimeState state = production(101L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));

        var pending = state.snapshot();
        assertThat(pending.actualAttemptCount()).isEqualTo(1);
        assertThat(pending.gameplayApplicationCount()).isZero();
        assertThat(pending.modifierCalculatedCount()).isZero();
        assertThat(pending.applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.approvalStatus()).isEqualTo("APPROVED_FROZEN_PRODUCTION_V2");
            assertThat(value.applicationApplied()).isFalse();
            assertThat(value.modifierConsumed()).isFalse();
        });

        CompositionWinnerDecisionProvenance provenance = decision(state, id,
                TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, -1.0, TeamSide.RED, TeamSide.BLUE,
                null, null, CompositionCombatRole.SYMMETRIC);
        state.recordWinnerDecisionProvenance(provenance);
        state.recordWinnerDecisionProvenance(provenance);

        MatchEvent event = new MatchEvent(1000, MatchEventType.TEAMFIGHT, "display text", null, null, List.of());
        event.setActionId("COMBAT_AT:1000");
        state.bindPublicAction(id, event);
        state.bindPublicAction(id, event);

        var diagnostics = state.snapshot();
        assertThat(diagnostics.schemaVersion()).isEqualTo(CompositionRuntimeDiagnostics.SCHEMA_VERSION);
        assertThat(diagnostics.gameplayApplicationCount()).isEqualTo(1);
        assertThat(diagnostics.modifierCalculatedCount()).isEqualTo(1);
        assertThat(diagnostics.modifierConsumedCount()).isEqualTo(1);
        assertThat(diagnostics.localDecisionChangedCount()).isEqualTo(1);
        assertThat(diagnostics.localDecisionUnchangedCount()).isZero();
        assertThat(diagnostics.publicActionBindingCount()).isEqualTo(1);
        assertThat(diagnostics.candidateApplications()).isEmpty();
        assertThat(diagnostics.localDecisionComparisons()).isEmpty();
        assertThat(diagnostics.applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.attemptId()).isEqualTo(id);
            assertThat(value.modifierCalculated()).isTrue();
            assertThat(value.applicationApplied()).isTrue();
            assertThat(value.modifierConsumed()).isTrue();
            assertThat(value.modifier()).isEqualTo(value.rawCompositionEdge() * value.frozenGain());
            assertThat(value.localDecisionChanged()).isTrue();
            assertThat(value.randomDrawOrdinal()).isEqualTo(19L);
            assertThat(value.randomSample()).isEqualTo(.37);
            assertThat(value.publicActionId()).isEqualTo("COMBAT_AT:1000");
            assertThat(value.publicBindingStatus()).isEqualTo("BOUND_STRUCTURED_ACTION_ID");
        });
    }

    @Test
    void nonZeroModifierMayBeConsumedWithoutChangingLocalOutcome() {
        CompositionRuntimeState state = production(102L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        state.recordWinnerDecisionProvenance(decision(state, id, TeamCompositionContext.TEAMFIGHT,
                CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                20.0, TeamSide.BLUE, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC));

        var application = state.snapshot().applicationProvenance().getFirst();
        assertThat(application.modifierConsumed()).isTrue();
        assertThat(application.localDecisionChanged()).isFalse();
        assertThat(state.snapshot().localDecisionUnchangedCount()).isEqualTo(1);
    }

    @Test
    void winnerUnchangedButExistingFightGradeChangeIsRecordedAsLocalCause() {
        CompositionRuntimeState state = production(108L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        state.recordWinnerDecisionProvenance(decision(state, id, TeamCompositionContext.TEAMFIGHT,
                CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                3.0, TeamSide.BLUE, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC));
        state.recordProductionFightGradeDecision(id, "SMALL_WIN", "NORMAL_WIN", true);
        state.recordProductionFightGradeDecision(id, "SMALL_WIN", "NORMAL_WIN", true);

        assertThat(state.snapshot().applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.localDecisionChanged()).isTrue();
            assertThat(value.baselineLocalResult()).isEqualTo("BLUE|GRADE:SMALL_WIN");
            assertThat(value.finalLocalResult()).isEqualTo("BLUE|GRADE:NORMAL_WIN");
        });
    }

    @Test
    void existingNonScalarCompositionInputIsSeparatedFromFrozenScalarModifier() {
        CompositionRuntimeState state = production(109L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        CompositionWinnerDecisionProvenance scalarOnly = decision(state, id,
                TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                3.0, TeamSide.BLUE, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC);
        double existingSupportToolDelta = 1.25;
        state.recordWinnerDecisionProvenance(copyWithCandidateScore(scalarOnly,
                scalarOnly.candidateScore() + existingSupportToolDelta));

        assertThat(state.snapshot().applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.modifier()).isEqualTo(value.rawCompositionEdge() * value.frozenGain());
            assertThat(value.existingNonScalarCompositionDelta())
                    .isCloseTo(existingSupportToolDelta, within(1e-12));
            assertThat(value.totalCompositionInputDelta())
                    .isCloseTo(value.modifier() + existingSupportToolDelta, within(1e-12));
            assertThat(value.adjustedGap() - value.baselineGap())
                    .isEqualTo(value.totalCompositionInputDelta());
        });
    }

    @Test
    void unsupportedContextIsStructuredDisabledAndCannotReachConsumer() {
        CompositionRuntimeState state = production(103L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(new CompositionAttemptDescriptor(id,
                CompositionActionType.OBJECTIVE_SETUP, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.FORMAL, ObjectiveType.DRAGON, true, null, null, 700,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null));

        var value = state.snapshot().applicationProvenance().getFirst();
        assertThat(value.context()).isEqualTo(TeamCompositionContext.OBJECTIVE_SETUP);
        assertThat(value.approvalStatus()).isEqualTo("DISABLED_NOT_APPROVED");
        assertThat(value.modifierCalculated()).isFalse();
        assertThat(value.applicationApplied()).isFalse();
        assertThat(value.modifierConsumed()).isFalse();
        assertThat(state.snapshot().directRandomCallCount()).isZero();
        assertThat(state.snapshot().compositionRandomDrawCount()).isZero();
    }

    @Test
    void scalarDisabledObjectiveFightBindsExistingNonScalarEffectWithoutInventingModifier() {
        CompositionRuntimeState state = production(110L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(new CompositionAttemptDescriptor(id,
                CompositionActionType.OBJECTIVE_SETUP, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.FORMAL, ObjectiveType.DRAGON, true, null, null, 700,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null));
        state.recordExistingNonScalarDecisionProvenance(id,
                "ObjectiveFightResolver.teamfightScore.supportToolExecution",
                -0.25, 0.75, .495, .515, .50, 27L, TeamSide.RED, TeamSide.BLUE);
        MatchEvent event = new MatchEvent(700, MatchEventType.TEAMFIGHT,
                "display text is not identity", null, null, List.of());
        event.setActionId("OBJECTIVE_DECISION:DRAGON:700:BLUE:FIGHT");
        event.setCombatSource(CombatSource.OBJECTIVE_FIGHT);
        state.bindPublicAction(id, event);

        var diagnostics = state.snapshot();
        assertThat(diagnostics.gameplayApplicationCount()).isZero();
        assertThat(diagnostics.modifierConsumedCount()).isZero();
        assertThat(diagnostics.existingNonScalarEffectConsumedCount()).isEqualTo(1);
        assertThat(diagnostics.totalCompositionEffectApplicationCount()).isEqualTo(1);
        assertThat(diagnostics.applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.approvalStatus()).isEqualTo("DISABLED_NOT_APPROVED");
            assertThat(value.gameplayEffectStatus())
                    .isEqualTo("SCALAR_DISABLED_EXISTING_NON_SCALAR_EFFECT_CONSUMED");
            assertThat(value.applicationApplied()).isTrue();
            assertThat(value.modifierCalculated()).isFalse();
            assertThat(value.modifierConsumed()).isFalse();
            assertThat(value.modifier()).isZero();
            assertThat(value.existingNonScalarEffectConsumed()).isTrue();
            assertThat(value.existingNonScalarCompositionDelta()).isEqualTo(1.0);
            assertThat(value.localDecisionChanged()).isTrue();
            assertThat(value.publicCombatSource()).isEqualTo(CombatSource.OBJECTIVE_FIGHT.name());
        });
    }

    @Test
    void frozenFormulaAndAttemptIdentityRejectConflictingOrMutatedConsumption() {
        CompositionRuntimeState state = production(104L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        CompositionWinnerDecisionProvenance valid = decision(state, id, TeamCompositionContext.TEAMFIGHT,
                CompositionActionType.TEAMFIGHT, CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                2.0, TeamSide.BLUE, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC);
        CompositionWinnerDecisionProvenance mutated = copyWithModifier(valid, valid.compositionModifier() + 1.0);

        assertThatThrownBy(() -> state.recordWinnerDecisionProvenance(mutated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen semantics");
        state.recordWinnerDecisionProvenance(valid);
        assertThatThrownBy(() -> state.recordWinnerDecisionProvenance(copyWithModifier(valid,
                valid.compositionModifier() + 1.0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting");
    }

    @Test
    void baseDefensePreservesStructuredAttackerDefenderRoles() {
        CompositionRuntimeState state = production(105L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.BASE_DEFENSE,
                CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE, TeamSide.RED, TeamSide.BLUE));
        state.recordWinnerDecisionProvenance(decision(state, id, TeamCompositionContext.BASE_DEFENSE,
                CompositionActionType.BASE_DEFENSE, CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,
                1.0, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED, TeamSide.BLUE,
                CompositionCombatRole.DEFENDER));

        assertThat(state.snapshot().applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.attackingSide()).isEqualTo(TeamSide.RED);
            assertThat(value.defendingSide()).isEqualTo(TeamSide.BLUE);
            assertThat(value.perspectiveSide()).isEqualTo(TeamSide.BLUE);
            assertThat(value.opponentSide()).isEqualTo(TeamSide.RED);
        });
    }

    @Test
    void offAndFreshMatchStateRemainExactZeroAndIsolated() {
        CompositionRuntimeState off = CompositionRuntimeState.off(106L);
        off.initialize(assignments());
        assertThat(off.snapshot().applicationProvenance()).isEmpty();
        assertThat(off.snapshot().gameplayApplicationCount()).isZero();
        assertThat(off.snapshot().modifierConsumedCount()).isZero();

        CompositionRuntimeState first = production(107L);
        first.recordActualAttempt(attempt(first.createActualAttemptId(), CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        CompositionRuntimeState second = production(107L);
        assertThat(second.snapshot().actualAttemptCount()).isZero();
        assertThat(second.snapshot().applicationProvenance()).isEmpty();
    }

    private static CompositionRuntimeState production(long seed) {
        CompositionRuntimeState state = new CompositionRuntimeState(TeamCompositionGameplayMode.PRODUCTION_V2, seed);
        state.initialize(assignments());
        return state;
    }

    private static com.lolfm.champion.MatchChampionAssignments assignments() {
        return new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper())).resolve(null);
    }

    private static CompositionAttemptDescriptor attempt(GameplayAttemptId id, CompositionActionType action,
                                                        CompositionBaselineScoreDomain domain,
                                                        TeamSide owner, TeamSide defender) {
        return new CompositionAttemptDescriptor(id, action, owner, owner, defender, FightScale.FORMAL,
                null, false, null, null, 1000, domain, 42.0, 39.0);
    }

    private static CompositionWinnerDecisionProvenance decision(
            CompositionRuntimeState state, GameplayAttemptId id, TeamCompositionContext context,
            CompositionActionType action, CompositionBaselineScoreDomain domain, double baseline,
            TeamSide baselineWinner, TeamSide runtimeWinner, TeamSide attacker, TeamSide defender,
            CompositionCombatRole role) {
        double edge = state.edgeFor(TeamSide.BLUE, context);
        double gain = FrozenCompositionProductionCandidate.winnerGain(context);
        double modifier = edge * gain;
        return new CompositionWinnerDecisionProvenance(state.matchSeed(), -1, id,
                context.name() + "|" + action.name() + "|" + domain.name(), context, action, domain,
                1000, TeamSide.BLUE, attacker, defender, role,
                CompositionRuntimeDecisionKind.UNIFORM_NOISE_THRESHOLD,
                CompositionRuntimeComparisonOperator.NOISY_SCORE_GREATER_THAN_OR_EQUAL_ZERO,
                baseline, edge, gain, modifier, baseline + modifier, .55, .60, .37, 19L, .40,
                baselineWinner, runtimeWinner, 1000, 1000, 1, 1, 5, 5, 100.0, 100.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                false, false, false, false, false, false, 0, 0, 3, 3, 2, 2, true, true,
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT,
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT,
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT,
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT, List.of());
    }

    private static CompositionWinnerDecisionProvenance copyWithModifier(
            CompositionWinnerDecisionProvenance x, double modifier) {
        return new CompositionWinnerDecisionProvenance(x.matchSeed(), x.caseIndex(), x.attemptId(),
                x.applicationKey(), x.context(), x.actionType(), x.scoreDomain(), x.timeSeconds(),
                x.perspectiveSide(), x.attackingSide(), x.defendingSide(), x.perspectiveRole(),
                x.decisionKind(), x.comparisonOperator(), x.baselineScore(), x.compositionEdge(),
                x.selectedGain(), modifier, x.candidateScore(), x.baselineProbability(),
                x.candidateProbability(), x.randomSample(), x.randomDrawOrdinal(), x.runtimeThreshold(),
                x.baselineCounterfactualWinner(), x.runtimeWinner(), x.blueGold(), x.redGold(),
                x.blueKills(), x.redKills(), x.blueAliveCount(), x.redAliveCount(),
                x.blueBaseTeamPower(), x.redBaseTeamPower(), x.goldContribution(), x.killContribution(),
                x.levelContribution(), x.itemContribution(), x.progressionContribution(),
                x.championPowerContribution(), x.matchupContribution(), x.blueDragonSoul(), x.redDragonSoul(),
                x.blueBaronBuff(), x.redBaronBuff(), x.blueElderBuff(), x.redElderBuff(),
                x.blueTowersDestroyed(), x.redTowersDestroyed(), x.blueInhibitorsRemaining(),
                x.redInhibitorsRemaining(), x.blueNexusTurretsRemaining(), x.redNexusTurretsRemaining(),
                x.blueNexusAlive(), x.redNexusAlive(), x.economyAvailability(), x.progressionAvailability(),
                x.championPowerAvailability(), x.matchupAvailability(), x.scoreStages());
    }

    private static CompositionWinnerDecisionProvenance copyWithCandidateScore(
            CompositionWinnerDecisionProvenance x, double candidateScore) {
        return new CompositionWinnerDecisionProvenance(x.matchSeed(), x.caseIndex(), x.attemptId(),
                x.applicationKey(), x.context(), x.actionType(), x.scoreDomain(), x.timeSeconds(),
                x.perspectiveSide(), x.attackingSide(), x.defendingSide(), x.perspectiveRole(),
                x.decisionKind(), x.comparisonOperator(), x.baselineScore(), x.compositionEdge(),
                x.selectedGain(), x.compositionModifier(), candidateScore, x.baselineProbability(),
                x.candidateProbability(), x.randomSample(), x.randomDrawOrdinal(), x.runtimeThreshold(),
                x.baselineCounterfactualWinner(), x.runtimeWinner(), x.blueGold(), x.redGold(),
                x.blueKills(), x.redKills(), x.blueAliveCount(), x.redAliveCount(),
                x.blueBaseTeamPower(), x.redBaseTeamPower(), x.goldContribution(), x.killContribution(),
                x.levelContribution(), x.itemContribution(), x.progressionContribution(),
                x.championPowerContribution(), x.matchupContribution(), x.blueDragonSoul(), x.redDragonSoul(),
                x.blueBaronBuff(), x.redBaronBuff(), x.blueElderBuff(), x.redElderBuff(),
                x.blueTowersDestroyed(), x.redTowersDestroyed(), x.blueInhibitorsRemaining(),
                x.redInhibitorsRemaining(), x.blueNexusTurretsRemaining(), x.redNexusTurretsRemaining(),
                x.blueNexusAlive(), x.redNexusAlive(), x.economyAvailability(), x.progressionAvailability(),
                x.championPowerAvailability(), x.matchupAvailability(), x.scoreStages());
    }
}

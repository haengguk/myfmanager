package com.lolfm.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.CompositionV9ApplicationCausalityRunner;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.simulator.Lane;
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
        state.bindPublicAction(id, event, 0);
        state.bindPublicAction(id, event, 0);

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
            assertThat(value.publicBindingStatus()).isEqualTo("BOUND_EXACT_STRUCTURED_EVENT_SET");
            assertThat(value.publicEventOrdinal()).isZero();
            assertThat(value.publicStructuredPayloadSha256()).hasSize(64);
        });
        assertThat(diagnostics.duplicatePublicBindingCount()).isEqualTo(1);
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
                -0.25, 0.75, 1.0, .495, .515, .50, 27L,
                TeamSide.RED, TeamSide.BLUE);
        MatchEvent event = new MatchEvent(700, MatchEventType.TEAMFIGHT,
                "display text is not identity", null, null, List.of());
        event.setActionId("OBJECTIVE_DECISION:DRAGON:700:BLUE:FIGHT");
        event.setCombatSource(CombatSource.OBJECTIVE_FIGHT);
        state.bindPublicAction(id, event, 0);

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
    void objectiveRoutingPerspectiveAndCanonicalOrientationCoverBothDirectionsAndUnchangedOutcome() {
        CompositionApplicationProvenance blueToRed = objectiveApplication(
                TeamSide.BLUE, 0.40, -0.40, TeamSide.BLUE, TeamSide.RED);
        CompositionApplicationProvenance redToBlue = objectiveApplication(
                TeamSide.RED, -0.40, 0.40, TeamSide.RED, TeamSide.BLUE);
        CompositionApplicationProvenance unchanged = objectiveApplication(
                TeamSide.RED, -0.80, -0.70, TeamSide.RED, TeamSide.RED);

        assertObjectiveOrientation(blueToRed, TeamSide.BLUE);
        assertObjectiveOrientation(redToBlue, TeamSide.RED);
        assertObjectiveOrientation(unchanged, TeamSide.RED);
        assertThat(blueToRed.localDecisionChanged()).isTrue();
        assertThat(blueToRed.baselineLocalResult()).isEqualTo("BLUE");
        assertThat(blueToRed.finalLocalResult()).isEqualTo("RED");
        assertThat(redToBlue.localDecisionChanged()).isTrue();
        assertThat(redToBlue.baselineLocalResult()).isEqualTo("RED");
        assertThat(redToBlue.finalLocalResult()).isEqualTo("BLUE");
        assertThat(unchanged.localDecisionChanged()).isFalse();
        assertThat(blueToRed.existingNonScalarCompositionDelta())
                .isEqualTo(-redToBlue.existingNonScalarCompositionDelta());
        assertThat(blueToRed.randomSample()).isEqualTo(redToBlue.randomSample());
        assertThat(blueToRed.randomDrawOrdinal()).isEqualTo(redToBlue.randomDrawOrdinal());
    }

    @Test
    void sameTickUnrelatedChangedAttemptCannotClaimAnotherEventsDivergence() {
        CompositionRuntimeState state = production(111L);
        GameplayAttemptId firstId = state.createActualAttemptId();
        state.recordActualAttempt(attempt(firstId, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        state.recordWinnerDecisionProvenance(decision(state, firstId,
                TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                -1.0, TeamSide.RED, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC));
        GameplayAttemptId secondId = state.createActualAttemptId();
        state.recordActualAttempt(attempt(secondId, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.RED, TeamSide.BLUE));
        state.recordWinnerDecisionProvenance(decision(state, secondId,
                TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                -1.0, TeamSide.RED, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC));

        MatchEvent firstEvent = publicEvent(1000, MatchEventType.TEAMFIGHT,
                "ACTION_A", "PARENT_A", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:TOP");
        MatchEvent secondEvent = publicEvent(1000, MatchEventType.TEAMFIGHT_RESULT,
                "ACTION_B", "PARENT_B", CombatSource.TEAMFIGHT, Lane.MID, "RED:MID");
        state.bindPublicAction(firstId, firstEvent, 0);
        state.bindPublicAction(secondId, secondEvent, 1);
        CompositionPublicEventIdentity secondIdentity =
                CompositionPublicEventIdentity.from(secondEvent, 1);
        var divergence = new CompositionV9ApplicationCausalityRunner.StructuredDivergenceIdentity(
                CompositionV9ApplicationCausalityRunner.DivergenceScope.EVENT, 1, 1000,
                secondIdentity.actionId(), secondIdentity.parentActionId(), secondIdentity.eventType(),
                secondIdentity.combatSource(), secondIdentity.combatLane(), "0".repeat(64),
                secondIdentity.structuredPayloadSha256());
        List<CompositionApplicationProvenance> traces = state.snapshot().applicationProvenance();

        assertThat(CompositionV9ApplicationCausalityRunner.exactEventBindingMatches(
                traces.getFirst(), divergence)).isFalse();
        assertThat(CompositionV9ApplicationCausalityRunner.exactEventBindingMatches(
                traces.get(1), divergence)).isTrue();
    }

    @Test
    void publicBindingIsIdempotentOnlyForTheCompleteStructuredEventIdentity() {
        CompositionRuntimeState state = production(112L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        MatchEvent original = publicEvent(1000, MatchEventType.TEAMFIGHT,
                "ACTION", "PARENT", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:TOP");
        state.bindPublicAction(id, original, 0);
        state.bindPublicAction(id, original, 0);

        List<MatchEvent> conflicts = List.of(
                publicEvent(1001, MatchEventType.TEAMFIGHT,
                        "ACTION", "PARENT", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:TOP"),
                publicEvent(1000, MatchEventType.TEAMFIGHT,
                        "OTHER_ACTION", "PARENT", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:TOP"),
                publicEvent(1000, MatchEventType.TEAMFIGHT,
                        "ACTION", "OTHER_PARENT", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:TOP"),
                publicEvent(1000, MatchEventType.TEAMFIGHT_RESULT,
                        "ACTION", "PARENT", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:TOP"),
                publicEvent(1000, MatchEventType.TEAMFIGHT,
                        "ACTION", "PARENT", CombatSource.OBJECTIVE_FIGHT, Lane.TOP, "BLUE:TOP"),
                publicEvent(1000, MatchEventType.TEAMFIGHT,
                        "ACTION", "PARENT", CombatSource.TEAMFIGHT, Lane.MID, "BLUE:TOP"),
                publicEvent(1000, MatchEventType.TEAMFIGHT,
                        "ACTION", "PARENT", CombatSource.TEAMFIGHT, Lane.TOP, "BLUE:MID"));
        for (MatchEvent conflict : conflicts) {
            assertThatThrownBy(() -> state.bindPublicAction(id, conflict, 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Conflicting public action binding");
        }
        assertThatThrownBy(() -> state.bindPublicAction(id, original, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ordinal");
        assertThat(state.snapshot().duplicatePublicBindingCount()).isEqualTo(1);
        assertThat(state.snapshot().conflictingPublicBindingCount()).isEqualTo(8);
    }

    @Test
    void decompositionSeparatesExplicitNonScalarAndDifferentialClampEffect() {
        CompositionRuntimeState state = production(113L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        CompositionWinnerDecisionProvenance original = decision(state, id,
                TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                2.0, TeamSide.BLUE, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC);
        double existing = 1.25;
        double baselinePreClamp = 100.0;
        double candidatePreClamp = baselinePreClamp + original.compositionModifier() + existing;
        CompositionWinnerDecisionProvenance clamped = copyWithDecomposition(original,
                baselinePreClamp, candidatePreClamp, 50.0, 50.0, existing);
        state.recordWinnerDecisionProvenance(clamped);

        assertThat(state.snapshot().applicationProvenance()).singleElement().satisfies(value -> {
            assertThat(value.candidateScoreBeforeClamp() - value.baselineScoreBeforeClamp())
                    .isCloseTo(value.modifier() + existing, within(1e-12));
            assertThat(value.clampEffect()).isCloseTo(
                    value.candidateClampDelta() - value.baselineClampDelta(), within(1e-12));
            assertThat(value.totalCompositionInputDelta()).isZero();
            assertThat(value.modifier() + value.existingNonScalarCompositionDelta()
                    + value.clampEffect()).isCloseTo(0.0, within(1e-12));
        });
    }

    @Test
    void skirmishExistingNonScalarComponentAndClampAreBitExactPositiveZero() {
        CompositionRuntimeState state = production(114L);
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(attempt(id, CompositionActionType.SKIRMISH,
                CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, TeamSide.BLUE, TeamSide.RED));
        state.recordWinnerDecisionProvenance(decision(state, id, TeamCompositionContext.SKIRMISH,
                CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE,
                1.0, TeamSide.BLUE, TeamSide.BLUE, null, null, CompositionCombatRole.SYMMETRIC));

        CompositionApplicationProvenance value = state.snapshot().applicationProvenance().getFirst();
        long positiveZero = Double.doubleToRawLongBits(0.0d);
        assertThat(Double.doubleToRawLongBits(value.existingNonScalarCompositionDelta()))
                .isEqualTo(positiveZero);
        assertThat(Double.doubleToRawLongBits(value.clampEffect())).isEqualTo(positiveZero);
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

    private static CompositionApplicationProvenance objectiveApplication(
            TeamSide owner, double baselineScore, double runtimeScore,
            TeamSide baselineWinner, TeamSide runtimeWinner) {
        CompositionRuntimeState state = production(115L + owner.ordinal());
        GameplayAttemptId id = state.createActualAttemptId();
        state.recordActualAttempt(new CompositionAttemptDescriptor(id,
                CompositionActionType.OBJECTIVE_SETUP, owner, owner, owner.opposite(),
                FightScale.FORMAL, ObjectiveType.DRAGON, true, null, null, 700,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null));
        state.recordExistingNonScalarDecisionProvenance(id,
                "ObjectiveFightResolver.teamfightScore.supportToolExecution",
                baselineScore, runtimeScore, runtimeScore - baselineScore,
                .49, .51, .50, 31L, baselineWinner, runtimeWinner);
        return state.snapshot().applicationProvenance().getFirst();
    }

    private static void assertObjectiveOrientation(
            CompositionApplicationProvenance value, TeamSide owner) {
        assertThat(value.attemptOwnerSide()).isEqualTo(owner);
        assertThat(value.routingPerspectiveSide()).isEqualTo(owner);
        assertThat(value.perspectiveSide()).isEqualTo(TeamSide.BLUE);
        assertThat(value.opponentSide()).isEqualTo(TeamSide.RED);
        assertThat(value.scoreOrientation()).isEqualTo(CompositionScoreOrientation.BLUE_MINUS_RED);
        assertThat(value.randomSample()).isEqualTo(.50);
        assertThat(value.randomDrawOrdinal()).isEqualTo(31L);
    }

    private static MatchEvent publicEvent(int time, MatchEventType type, String actionId,
                                          String parentActionId, CombatSource source, Lane lane,
                                          String actorPlayerId) {
        MatchEvent event = new MatchEvent(time, type, "display text is not identity",
                "display killer", "display victim", List.of("display assist"));
        event.setActionId(actionId);
        event.setParentActionId(parentActionId);
        event.setCombatSource(source);
        event.setCombatLane(lane);
        event.setActorPlayerId(actorPlayerId);
        return event;
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
                1000, TeamSide.BLUE, CompositionScoreOrientation.BLUE_MINUS_RED,
                attacker, defender, role,
                CompositionRuntimeDecisionKind.UNIFORM_NOISE_THRESHOLD,
                CompositionRuntimeComparisonOperator.NOISY_SCORE_GREATER_THAN_OR_EQUAL_ZERO,
                baseline, edge, gain, modifier, baseline + modifier,
                baseline, baseline + modifier, 0.0, 0.0, 0.0, 0.0,
                .55, .60, .37, 19L, .40,
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
                x.perspectiveSide(), x.scoreOrientation(), x.attackingSide(), x.defendingSide(), x.perspectiveRole(),
                x.decisionKind(), x.comparisonOperator(), x.baselineScore(), x.compositionEdge(),
                x.selectedGain(), modifier,
                x.baselineScoreBeforeClamp() + modifier + x.existingNonScalarCompositionComponent()
                        + x.candidateClampDelta(),
                x.baselineScoreBeforeClamp(),
                x.baselineScoreBeforeClamp() + modifier + x.existingNonScalarCompositionComponent(),
                x.existingNonScalarCompositionComponent(), x.baselineClampDelta(),
                x.candidateClampDelta(), x.clampEffect(), x.baselineProbability(),
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
                x.perspectiveSide(), x.scoreOrientation(), x.attackingSide(), x.defendingSide(), x.perspectiveRole(),
                x.decisionKind(), x.comparisonOperator(), x.baselineScore(), x.compositionEdge(),
                x.selectedGain(), x.compositionModifier(), candidateScore,
                x.baselineScoreBeforeClamp(), candidateScore,
                candidateScore - x.baselineScoreBeforeClamp() - x.compositionModifier(),
                x.baselineClampDelta(), 0.0, -x.baselineClampDelta(), x.baselineProbability(),
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

    private static CompositionWinnerDecisionProvenance copyWithDecomposition(
            CompositionWinnerDecisionProvenance x,
            double baselinePreClamp, double candidatePreClamp,
            double baselinePostClamp, double candidatePostClamp,
            double existingNonScalar) {
        double baselineClamp = baselinePostClamp - baselinePreClamp;
        double candidateClamp = candidatePostClamp - candidatePreClamp;
        double clampEffect = candidateClamp - baselineClamp;
        return new CompositionWinnerDecisionProvenance(x.matchSeed(), x.caseIndex(), x.attemptId(),
                x.applicationKey(), x.context(), x.actionType(), x.scoreDomain(), x.timeSeconds(),
                x.perspectiveSide(), x.scoreOrientation(), x.attackingSide(), x.defendingSide(),
                x.perspectiveRole(), x.decisionKind(), x.comparisonOperator(), baselinePostClamp,
                x.compositionEdge(), x.selectedGain(), x.compositionModifier(), candidatePostClamp,
                baselinePreClamp, candidatePreClamp, existingNonScalar, baselineClamp,
                candidateClamp, clampEffect, x.baselineProbability(), x.candidateProbability(),
                x.randomSample(), x.randomDrawOrdinal(), x.runtimeThreshold(),
                x.baselineCounterfactualWinner(), x.runtimeWinner(), x.blueGold(), x.redGold(),
                x.blueKills(), x.redKills(), x.blueAliveCount(), x.redAliveCount(),
                x.blueBaseTeamPower(), x.redBaseTeamPower(), x.goldContribution(), x.killContribution(),
                x.levelContribution(), x.itemContribution(), x.progressionContribution(),
                x.championPowerContribution(), x.matchupContribution(), x.blueDragonSoul(),
                x.redDragonSoul(), x.blueBaronBuff(), x.redBaronBuff(), x.blueElderBuff(),
                x.redElderBuff(), x.blueTowersDestroyed(), x.redTowersDestroyed(),
                x.blueInhibitorsRemaining(), x.redInhibitorsRemaining(),
                x.blueNexusTurretsRemaining(), x.redNexusTurretsRemaining(), x.blueNexusAlive(),
                x.redNexusAlive(), x.economyAvailability(), x.progressionAvailability(),
                x.championPowerAvailability(), x.matchupAvailability(), x.scoreStages());
    }
}

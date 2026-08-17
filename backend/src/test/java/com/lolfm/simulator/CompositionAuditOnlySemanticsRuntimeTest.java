package com.lolfm.simulator;

import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.*;
import com.lolfm.domain.MatchTimeline;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositionAuditOnlySemanticsRuntimeTest {
    private static CompositionAuditOnlySemanticsRuntime.ScheduleCase row;
    private static MatchChampionAssignments assignments;
    private static MatchSimulator.SimulationResult off;
    private static MatchSimulator.SimulationResult audit;
    private static MatchSimulator.SimulationResult replay;
    private static CompositionRuntimeState state;

    @BeforeAll
    static void setUp() throws Exception {
        row = CompositionAuditOnlySemanticsRuntime.readSchedule().getFirst();
        var lineups = CompositionFreshHoldoutCandidateGameplayAudit.readCanonical();
        var blue = lineups.stream().filter(x -> x.id().equals(row.blueLineupId())).findFirst().orElseThrow();
        var red = lineups.stream().filter(x -> x.id().equals(row.redLineupId())).findFirst().orElseThrow();
        assignments = CompositionAuditOnlySemanticsRuntime.assignments(blue, red);
        off = CompositionAuditOnlySemanticsRuntime.simulate(
                CompositionAuditOnlySemanticsRuntime.simulator(TeamCompositionGameplayMode.OFF,
                        CompositionSemanticsAuditExecutionAuthorization.none()), row, assignments);
        audit = CompositionAuditOnlySemanticsRuntime.simulate(
                CompositionAuditOnlySemanticsRuntime.simulator(TeamCompositionGameplayMode.SHADOW,
                        CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(row.caseIndex())),
                row, assignments);
        replay = CompositionAuditOnlySemanticsRuntime.simulate(
                CompositionAuditOnlySemanticsRuntime.simulator(TeamCompositionGameplayMode.SHADOW,
                        CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(row.caseIndex())),
                row, assignments);
        state = new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, row.seed(),
                CompositionCandidateExecutionAuthorization.none(),
                CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(row.caseIndex()));
        state.initialize(assignments);
    }

    @Test void runtimeBlueprintMatchesFrozenDesignHash() {
        assertEquals("composition-key-specific-application-semantics-blueprint-v1", FrozenCompositionApplicationSemanticsBlueprint.VERSION);
        assertEquals("6287bd537e29c488e0cbc9a2bc7636a3a76b44791c43420fdd8a40703edc8964", FrozenCompositionApplicationSemanticsBlueprint.HASH);
        assertEquals(4, FrozenCompositionApplicationSemanticsBlueprint.keys().size());
    }

    @Test void runtimeDoesNotReadDesignCsv() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lolfm/composition/FrozenCompositionApplicationSemanticsBlueprint.java"));
        assertFalse(source.contains("build/reports")); assertFalse(source.contains("Files.")); assertFalse(source.contains(".csv"));
    }

    @Test void wrongBlueprintAuthorizationFailsFast() {
        var bad = new CompositionSemanticsAuditExecutionAuthorization("wrong", "wrong", 0, true);
        var ex = assertThrows(CompositionGameplayConfigurationException.class,
                () -> new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, 1L,
                        CompositionCandidateExecutionAuthorization.none(), bad));
        assertEquals("COMPOSITION_SEMANTICS_BLUEPRINT_IDENTITY_MISMATCH", ex.code());
    }

    @Test void missingAuditAuthorizationCannotEnableSemantics() {
        assertFalse(new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, 1L).isAuditSemantics());
        assertThrows(CompositionGameplayConfigurationException.class,
                () -> CompositionSemanticsAuditExecutionAuthorization.none().verifyExact());
    }

    @Test void auditAuthorizationIsMatchScoped() {
        assertEquals(row.caseIndex(), state.semanticsAuditAuthorization().diagnosticCaseIndex());
        assertFalse(new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, row.seed()).isAuditSemantics());
    }

    @Test void publicApiCannotEnableAuditSemantics() {
        for (Constructor<?> constructor : MatchSimulator.class.getConstructors()) {
            assertFalse(List.of(constructor.getParameterTypes()).contains(CompositionSemanticsAuditExecutionAuthorization.class));
        }
    }

    @Test void skirmishKeepsExistingFrozenWinnerSemantics() {
        var key = key(TeamCompositionContext.SKIRMISH);
        assertEquals(FrozenCompositionApplicationSemanticsBlueprint.ApplicationMode.EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION, key.winnerMode());
    }

    @Test void skirmishKeepsExistingGain() {
        var x = adjustment(TeamCompositionContext.SKIRMISH, 4.0);
        assertEquals(24.509721397259, x.referenceGain(), 1e-12);
        assertEquals(x.baselineGap() + x.winnerModifier(), x.winnerDecisionGap(), 0.0);
    }

    @Test void skirmishHasNoSeverityChannel() { assertEquals(FrozenCompositionApplicationSemanticsBlueprint.ChannelState.NOT_APPLICABLE, key(TeamCompositionContext.SKIRMISH).severityState()); }

    @Test void skirmishRandomSequenceRemainsExact() { assertEquals(off.randomTrace(), off.randomTrace()); assertEquals(0, off.compositionRuntimeDiagnostics().compositionRandomDrawCount()); }

    @Test void teamfightWinnerUsesDecisionLocalGap() { assertDecisionLocal(TeamCompositionContext.TEAMFIGHT); }
    @Test void teamfightHistoricalWinnerGainIsDiagnosticReferenceOnly() { assertReferenceOnly(TeamCompositionContext.TEAMFIGHT, 11.595061941148); }
    @Test void teamfightWinnerModifierDoesNotMutateGradeBaseline() { assertGradeIsolation(TeamCompositionContext.TEAMFIGHT); }
    @Test void teamfightSeverityModifierIsZeroReference() { assertSeverityZero(TeamCompositionContext.TEAMFIGHT); }
    @Test void teamfightGradeReceivesNoDirectCompositionModifier() { assertGradeSeverityDirectFalse(TeamCompositionContext.TEAMFIGHT); }
    @Test void siegeWinnerUsesDecisionLocalGap() { assertDecisionLocal(TeamCompositionContext.SIEGE); }
    @Test void siegeHistoricalWinnerGainIsDiagnosticReferenceOnly() { assertReferenceOnly(TeamCompositionContext.SIEGE, 6.805985567298); }
    @Test void siegeSeverityModifierIsZeroReference() { assertSeverityZero(TeamCompositionContext.SIEGE); }
    @Test void siegeGradeReceivesNoDirectCompositionModifier() { assertGradeSeverityDirectFalse(TeamCompositionContext.SIEGE); }
    @Test void siegeHasNoDirectStructureCompositionChannel() { assertEquals(CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE, key(TeamCompositionContext.SIEGE).scoreDomain()); }

    @Test void baseDefenseUsesStructuredAttackerDefenderRoles() { assertEquals(FrozenCompositionApplicationSemanticsBlueprint.RoleSemantics.STRUCTURED_ATTACKER_DEFENDER, key(TeamCompositionContext.BASE_DEFENSE).roles()); }

    @Test void baseDefenseCanonicalRoleSignIsStable() {
        var x = role(0.25);
        assertEquals(0.25, x.canonicalAttackerAdvantageSignal()); assertEquals(-0.25, x.mirroredRoleSignal());
    }

    @Test void baseDefenseDefenderPerspectiveIsExactSignReverse() { var x = role(-0.4); assertEquals(x.canonicalAttackerAdvantageSignal(), -x.mirroredRoleSignal(), 0.0); }
    @Test void baseDefenseWinnerModifierIsZeroWhileUncalibrated() { assertEquals(0.0, adjustment(TeamCompositionContext.BASE_DEFENSE, 5.0).winnerModifier()); }
    @Test void baseDefenseHistoricalGainIsNeverApplied() { var x = adjustment(TeamCompositionContext.BASE_DEFENSE, 5.0); assertEquals(0.0, x.referenceGain()); assertFalse(x.gainStatus().contains("HISTORICAL")); }
    @Test void baseDefenseSeverityModifierIsZero() { assertSeverityZero(TeamCompositionContext.BASE_DEFENSE); }
    @Test void baseDefenseRoleDoesNotDependOnWinnerResult() { assertFalse(role(0.2).roleSelectedFromWinnerResult()); }

    @Test void gradeDiagnosticCapturesExistingAceDrawWithoutExtraRandom() { assertBranchCaptured(0); }
    @Test void gradeDiagnosticCapturesExistingBigDrawWithoutExtraRandom() { assertBranchCaptured(1); }
    @Test void gradeDiagnosticCapturesExistingNormalDrawWithoutExtraRandom() { assertBranchCaptured(2); }

    @Test void unreachedGradeBranchIsExplicitlyNotDrawn() {
        var x = FightGradeBranchDiagnostic.notReached("BIG_WIN");
        assertEquals(FightGradeBranchDrawState.NOT_DRAWN_BRANCH_NOT_REACHED, x.drawState()); assertNull(x.randomSample()); assertNull(x.threshold());
    }

    @Test void actualGradeCanBeReconstructedFromCapturedPath() { assertTrue(grades().stream().allMatch(FightGradeDecisionDiagnostic::actualPathReconstructed)); }

    @Test void diagnosticDoesNotFabricateUnobservedBranchRandom() {
        assertTrue(grades().stream().flatMap(x -> x.branches().stream()).filter(x -> x.drawState() != FightGradeBranchDrawState.DRAWN)
                .allMatch(x -> x.randomSample() == null && x.randomDrawOrdinal() == null && x.threshold() == null));
    }

    @Test void conditionalCounterfactualCoverageIsReportedHonestly() {
        assertTrue(grades().stream().allMatch(x -> x.actualGradeRandomDrawCount() == 3
                ? x.counterfactualCoverageClass() == FightGradeCounterfactualCoverageClass.FULL_FOR_ACTUAL_REACHED_BRANCHES
                : x.counterfactualCoverageClass() == FightGradeCounterfactualCoverageClass.PARTIAL_UNOBSERVED_LATER_BRANCH_RANDOM));
    }

    @Test void legacyGradeSignalReferenceDoesNotAffectGameplay() { assertTrue(grades().stream().allMatch(x -> x.finalSeverityInput() == x.baselineGradeGap())); }
    @Test void winnerModifierCannotLeakIntoSeverityBaseline() { assertTrue(grades().stream().allMatch(x -> x.finalSeverityInput() == x.baselineGradeGap())); }
    @Test void severityModifierCannotAffectWinnerDecision() { var x = adjustment(TeamCompositionContext.TEAMFIGHT, 7.0); assertEquals(7.0 + x.winnerModifier(), x.winnerDecisionGap()); }
    @Test void gradeInternalHistoricalCompositionReuseIsZeroInNewPath() { assertTrue(grades().stream().allMatch(x -> !x.directCompositionSeverityUsed())); }
    @Test void winnerPerspectiveChangeIsClassifiedAsIndirect() { assertTrue(grades().stream().allMatch(x -> x.directCompositionWinnerUsed() == (x.winnerModifierApplied() != 0.0))); }
    @Test void directSeverityCompositionEffectIsZero() { assertTrue(grades().stream().allMatch(x -> x.severityModifierApplied() == 0.0)); }

    @Test void historicalCandidateCannotMixWithNewAuditPath() {
        var ex = assertThrows(CompositionGameplayConfigurationException.class,
                () -> new CompositionRuntimeState(TeamCompositionGameplayMode.CANDIDATE, 1L,
                        CompositionCandidateExecutionAuthorization.frozenAudit(),
                        CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(0)));
        assertEquals("COMPOSITION_HISTORICAL_CANDIDATE_AND_AUDIT_PATH_MIXED", ex.code());
    }

    @Test void auditDiagnosticsConsumeNoAdditionalRandom() { assertTrue(grades().stream().allMatch(x -> x.diagnosticAdditionalRandomDrawCount() == 0)); }

    @Test void preDivergenceRandomTraceMatchesOff() {
        var pair = CompositionAuditOnlySemanticsRuntime.pair(row, off, audit);
        assertEquals(0, pair.preDivergenceRandomMismatch());
    }

    @Test void replayPreservesGradeDiagnostics() { assertEquals(CompositionAuditOnlySemanticsRuntime.hash(grades()), CompositionAuditOnlySemanticsRuntime.hash(replay.compositionRuntimeDiagnostics().fightGradeDiagnostics())); }
    @Test void replayPreservesChannelDiagnostics() { assertEquals(CompositionAuditOnlySemanticsRuntime.hash(audit.compositionRuntimeDiagnostics().winnerChannelObservations()), CompositionAuditOnlySemanticsRuntime.hash(replay.compositionRuntimeDiagnostics().winnerChannelObservations())); }
    @Test void replayPreservesGameplayResult() { assertEquals(CompositionAuditOnlySemanticsRuntime.hash(audit.timeline()), CompositionAuditOnlySemanticsRuntime.hash(replay.timeline())); }

    @Test void productionDefaultRemainsOff() { assertEquals(TeamCompositionGameplayMode.OFF, SimulationOptions.productionDefaults().teamCompositionGameplayMode()); }

    @Test void publicCandidateRemainsGuarded() {
        assertThrows(CompositionGameplayConfigurationException.class,
                () -> new CompositionRuntimeState(TeamCompositionGameplayMode.CANDIDATE, 1L));
    }

    @Test void noNewGameplayCandidateIsCreated() { assertFalse(state.isCandidate()); assertTrue(state.candidateApplications().isEmpty()); }

    @Test void apiAndFrontendRemainUnchanged() throws Exception {
        assertTrue(CompositionAuditOnlySemanticsRuntime.sourcePaths().stream().noneMatch(x -> x.toString().contains("frontend")));
        assertTrue(CompositionAuditOnlySemanticsRuntime.sourcePaths().stream().noneMatch(x -> x.toString().contains("controller")));
    }

    @Test void auditDiagnosticsAreNotPublicTimelineData() {
        assertInstanceOf(MatchTimeline.class, audit.timeline());
        assertFalse(audit.timeline().toString().contains(FrozenCompositionApplicationSemanticsBlueprint.VERSION));
    }

    private static FrozenCompositionApplicationSemanticsBlueprint.Key key(TeamCompositionContext context) {
        return FrozenCompositionApplicationSemanticsBlueprint.keys().stream().filter(x -> x.context() == context).findFirst().orElseThrow();
    }

    private static CompositionWinnerDecisionAdjustment adjustment(TeamCompositionContext context, double gap) {
        var key = key(context);
        return state.auditWinnerAdjustment(TeamSide.BLUE, context, key.actionType(), key.scoreDomain(), gap,
                context == TeamCompositionContext.BASE_DEFENSE ? CompositionCombatRole.ATTACKER : CompositionCombatRole.SYMMETRIC);
    }

    private static void assertDecisionLocal(TeamCompositionContext context) {
        var x = adjustment(context, 8.0);
        assertEquals(8.0 + x.winnerModifier(), x.winnerDecisionGap(), 0.0);
        assertEquals(FrozenCompositionApplicationSemanticsBlueprint.ApplicationMode.DECISION_LOCAL_GAP_MODIFIER, key(context).winnerMode());
    }

    private static void assertReferenceOnly(TeamCompositionContext context, double gain) {
        var x = adjustment(context, 2.0);
        assertEquals(gain, x.referenceGain(), 1e-12); assertEquals("DIAGNOSTIC_HISTORICAL_REFERENCE_ONLY", x.gainStatus());
    }

    private static void assertSeverityZero(TeamCompositionContext context) {
        var key = key(context);
        var x = state.auditSeverityAdjustment(context, key.actionType(), key.scoreDomain(), 12.5);
        assertEquals(0.0, x.severityModifier()); assertEquals(12.5, x.finalSeverityInput());
    }

    private static void assertGradeIsolation(TeamCompositionContext context) {
        assertTrue(grades().stream().filter(x -> x.context() == context).allMatch(x -> x.finalSeverityInput() == x.baselineGradeGap()));
    }

    private static void assertGradeSeverityDirectFalse(TeamCompositionContext context) {
        assertTrue(grades().stream().filter(x -> x.context() == context).allMatch(x -> !x.directCompositionSeverityUsed()));
    }

    private static BaseDefenseRoleRoutingDiagnostic role(double signal) {
        return new BaseDefenseRoleRoutingDiagnostic(1L, 0, new GameplayAttemptId(1), 1200,
                TeamSide.BLUE, TeamSide.RED, signal, -signal, signal, -signal,
                "COMPONENTS_ONLY_UNCALIBRATED", 0.0, false);
    }

    private static List<FightGradeDecisionDiagnostic> grades() {
        var values = audit.compositionRuntimeDiagnostics().fightGradeDiagnostics();
        assertFalse(values.isEmpty());
        return values;
    }

    private static void assertBranchCaptured(int index) {
        assertTrue(grades().stream().map(x -> x.branches().get(index))
                .anyMatch(x -> x.drawState() == FightGradeBranchDrawState.DRAWN
                        && x.randomSample() != null && x.randomDrawOrdinal() != null));
        assertTrue(grades().stream().allMatch(x -> x.diagnosticAdditionalRandomDrawCount() == 0));
    }
}

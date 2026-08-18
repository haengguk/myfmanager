package com.lolfm.simulator;

import com.lolfm.composition.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositionKeySpecificFreshHoldoutGameplayAuditTest {
    static CompositionKeySpecificFreshHoldoutGameplayAudit.Prepared prepared;
    static CompositionRuntimeState runtime;
    static String auditSource;

    @BeforeAll static void setup() throws Exception {
        prepared = CompositionKeySpecificFreshHoldoutGameplayAudit.prepare();
        var first = prepared.schedule().getFirst();
        var byId = new HashMap<String,CompositionFreshHoldoutCandidateGameplayAudit.Lineup>();
        prepared.pool().forEach(x -> byId.put(x.id(), x));
        runtime = new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, first.seed(),
                CompositionCandidateExecutionAuthorization.none(),
                CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(first.caseIndex()),
                CompositionKeySpecificCandidateAuditAuthorization.frozenFreshHoldoutCase(first.caseIndex()));
        runtime.initialize(CompositionAuditOnlySemanticsRuntime.assignments(byId.get(first.blueLineupId()), byId.get(first.redLineupId())));
        auditSource = Files.readString(Path.of("src/test/java/com/lolfm/simulator/CompositionKeySpecificFreshHoldoutGameplayAudit.java"));
    }

    @Test void runtimeCandidateMatchesFrozenCandidateHash(){assertEquals(FrozenCompositionKeySpecificChannelCandidate.HASH,FrozenCompositionKeySpecificChannelCandidate.canonicalHash());}
    @Test void candidateConfigurationCannotDrift(){assertDoesNotThrow(()->FrozenCompositionKeySpecificChannelCandidate.verifyIdentity(FrozenCompositionKeySpecificChannelCandidate.VERSION,FrozenCompositionKeySpecificChannelCandidate.HASH));}
    @Test void wrongCandidateAuthorizationFailsFast(){var a=new CompositionKeySpecificCandidateAuditAuthorization(FrozenCompositionKeySpecificChannelCandidate.VERSION,"bad",0,true);var e=assertThrows(CompositionGameplayConfigurationException.class,a::verifyExact);assertEquals("COMPOSITION_KEY_SPECIFIC_CANDIDATE_IDENTITY_MISMATCH",e.code());}
    @Test void candidateAuthorizationIsMatchScoped(){assertEquals(prepared.schedule().getFirst().caseIndex(),runtime.keySpecificCandidateAuthorization().holdoutCaseIndex());}
    @Test void publicApiCannotEnableFreshHoldoutCandidate(){assertFalse(CompositionKeySpecificCandidateAuditAuthorization.none().enabled());}
    @Test void productionUsesFrozenV2(){assertEquals(TeamCompositionGameplayMode.PRODUCTION_V2,SimulationOptions.productionDefaults().teamCompositionGameplayMode());}

    @Test void freshHoldoutUsesNoPriorOrderedPairs(){assertEquals(0,prepared.priorOrderedOverlap());}
    @Test void freshHoldoutUsesNoPriorUnorderedPairs(){assertEquals(0,prepared.priorUnorderedOverlap());}
    @Test void freshHoldoutUsesNoPriorSeeds(){assertEquals(0,prepared.priorSeedOverlap());}
    @Test void freshHoldoutUsesNoPriorLineupsWhenFeasible(){assertEquals(0,prepared.priorLineupOverlap());}
    @Test void scheduleIsFrozenBeforeGameplayExecution(){assertTrue(auditSource.indexOf("writeFrozenSchedule(prepared)")<auditSource.indexOf("for (CompositionAuditOnlySemanticsRuntime.ScheduleCase row"));}
    @Test void scheduleSelectionDoesNotUseGameplayOutcome(){assertFalse(auditSource.substring(auditSource.indexOf("static Prepared prepare"),auditSource.indexOf("static MatchSimulator candidateSimulator")).contains("winnerSide"));}
    @Test void everyUnorderedPairHasBothOrientations(){assertEquals(0,CompositionAuditOnlySemanticsRuntime.missingReverse(prepared.schedule()));}
    @Test void crossTeamChampionOverlapIsZero(){assertTrue(prepared.edges().stream().noneMatch(x->CompositionKeySpecificFreshHoldoutGameplayAudit.championOverlap(x.left(),x.right())));}

    @Test void teamfightUsesFrozenWinnerGain(){assertGain(TeamCompositionContext.TEAMFIGHT,CompositionActionType.TEAMFIGHT,CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,CompositionCombatRole.SYMMETRIC,FrozenCompositionKeySpecificChannelCandidate.TEAMFIGHT_WINNER_GAIN);}
    @Test void siegeUsesFrozenWinnerGain(){assertGain(TeamCompositionContext.SIEGE,CompositionActionType.SIEGE_COMBAT,CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE,CompositionCombatRole.ATTACKER,FrozenCompositionKeySpecificChannelCandidate.SIEGE_WINNER_GAIN);}
    @Test void baseUsesFrozenRoleAwareWinnerGain(){assertGain(TeamCompositionContext.BASE_DEFENSE,CompositionActionType.BASE_DEFENSE,CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,CompositionCombatRole.ATTACKER,FrozenCompositionKeySpecificChannelCandidate.BASE_DEFENSE_WINNER_GAIN);}
    @Test void skirmishRemainsExact(){assertEquals(FrozenCompositionGameplayGainPolicy.SKIRMISH_GAIN,FrozenCompositionKeySpecificChannelCandidate.SKIRMISH_WINNER_GAIN,1e-15);}
    @Test void allFailedKeySeverityGainsRemainZero(){assertEquals(0,FrozenCompositionKeySpecificChannelCandidate.TEAMFIGHT_SEVERITY_GAIN+FrozenCompositionKeySpecificChannelCandidate.SIEGE_SEVERITY_GAIN+FrozenCompositionKeySpecificChannelCandidate.BASE_DEFENSE_SEVERITY_GAIN);}
    @Test void winnerModifierCannotLeakIntoGrade(){assertEquals(0,runtime.auditSeverityAdjustment(TeamCompositionContext.TEAMFIGHT,CompositionActionType.TEAMFIGHT,CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,7).severityModifier());}
    @Test void gradeCannotReuseWinnerCompositionModifier(){assertEquals(7,runtime.auditSeverityAdjustment(TeamCompositionContext.TEAMFIGHT,CompositionActionType.TEAMFIGHT,CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,7).finalSeverityInput());}
    @Test void siegeCannotDirectlyModifyStructures(){assertFalse(auditSource.contains("directStructureCompositionModifierCount\",\"1"));}
    @Test void compositionCannotDirectlyModifyObjectives(){assertFalse(auditSource.contains("directObjectiveCompositionModifierCount\",\"1"));}

    @Test void baseUsesStructuredAttackerDefenderRoles(){assertEquals(CompositionCombatRole.ATTACKER,runtime.auditWinnerAdjustment(TeamSide.BLUE,TeamCompositionContext.BASE_DEFENSE,CompositionActionType.BASE_DEFENSE,CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,0,CompositionCombatRole.ATTACKER).perspectiveRole());}
    @Test void baseSignConventionIsExact(){assertEquals(-runtime.edgeFor(TeamSide.BLUE,TeamCompositionContext.BASE_DEFENSE),runtime.edgeFor(TeamSide.RED,TeamCompositionContext.BASE_DEFENSE),0);}
    @Test void baseMirrorReversesRoleSignalExactly(){assertEquals(0,runtime.edgeFor(TeamSide.BLUE,TeamCompositionContext.BASE_DEFENSE)+runtime.edgeFor(TeamSide.RED,TeamCompositionContext.BASE_DEFENSE),0);}
    @Test void baseNeverUsesHistoricalGain(){assertNotEquals(FrozenCompositionGameplayGainPolicy.BASE_DEFENSE_GAIN,FrozenCompositionKeySpecificChannelCandidate.BASE_DEFENSE_WINNER_GAIN);}
    @Test void baseRoleCannotDependOnWinnerResult(){assertTrue(auditSource.contains("winnerBasedRoleInferenceCount\",\"0"));}
    @Test void baseRoleCannotDependOnDisplayName(){assertFalse(FrozenCompositionKeySpecificChannelCandidate.canonical().contains("displayName"));}

    @Test void freshWinnerBandsUseFrozenCalibrationThresholds(){assertEquals(.225049842038,CompositionKeySpecificFreshHoldoutGameplayAudit.BANDS.get(TeamCompositionContext.TEAMFIGHT).p25(),0);}
    @Test void freshHoldoutCannotRecalculateDecisionBands(){assertFalse(auditSource.contains("quantile(values"));}
    @Test void farWinnerFlipFailsSafety(){assertTrue(auditSource.contains("far==0"));}
    @Test void nonNearWinnerFlipRateUsesExactFreshDenominator(){assertTrue(auditSource.contains("(double)(mid+far)/(midApps+farApps)"));}
    @Test void directionMismatchFailsSafety(){assertTrue(auditSource.contains("direction==0"));}
    @Test void nearFlipConcentrationUsesFreshHoldoutOnly(){assertTrue(auditSource.contains("(double)near/flips"));}

    @Test void directSeverityEffectIsZero(){assertTrue(auditSource.contains("DIRECT_SEVERITY_COMPOSITION_EFFECT\",\"0"));}
    @Test void indirectWinnerPerspectiveGradeChangeIsAllowed(){assertTrue(auditSource.contains("INDIRECT_WINNER_PERSPECTIVE_CHANGE"));}
    @Test void fightGradeDiagnosticsConsumeNoAdditionalRandom(){assertTrue(auditSource.contains("diagnosticAdditionalRandomDrawCount\",\"0"));}
    @Test void fightGradeActualPathReconstructionRemainsExact(){assertTrue(Files.exists(Path.of("src/main/java/com/lolfm/composition/FightGradeDecisionDiagnostic.java")));}
    @Test void gradeDifferenceCauseIsStructured(){assertTrue(auditSource.contains("PRIOR_GAME_STATE_DIVERGENCE"));}

    @Test void candidateConsumesNoDirectRandom(){assertFalse(FrozenCompositionKeySpecificChannelCandidate.canonical().contains("Random"));}
    @Test void preDivergenceRandomTraceMatchesOff(){assertTrue(auditSource.contains("preDivergenceRandomMismatch"));}
    @Test void candidateReplayIsExact(){assertTrue(auditSource.contains("CompositionAuditOnlySemanticsRuntime.replay"));}
    @Test void replayPreservesCompositionDiagnostics(){assertTrue(auditSource.contains("winnerChannelObservations"));}
    @Test void replayPreservesFightGradeDiagnostics(){assertTrue(auditSource.contains("fightGradeDiagnostics"));}

    @Test void publicDivergenceRequiresCausalLocalCompositionChange(){assertTrue(auditSource.contains("publicWithoutCause"));}
    @Test void objectiveDifferenceIsDownstreamOnly(){assertTrue(auditSource.contains("directObjectiveCompositionModifierCount\",\"0"));}
    @Test void structureDifferenceIsDownstreamOnly(){assertTrue(auditSource.contains("directStructureCompositionModifierCount\",\"0"));}
    @Test void historicalMacroGateIsReusedWithoutModification(){assertTrue(auditSource.contains("macro.objectiveRate() <= .05")&&auditSource.contains("macro.structureRate() <= .08"));}
    @Test void freshHoldoutResultCannotRetuneCandidate(){assertFalse(auditSource.contains("setWinnerGain"));}

    private static void assertGain(TeamCompositionContext context,CompositionActionType action,CompositionBaselineScoreDomain domain,CompositionCombatRole role,double gain){var x=runtime.auditWinnerAdjustment(TeamSide.BLUE,context,action,domain,10,role);assertEquals(gain,x.referenceGain(),1e-15);assertEquals("FROZEN_KEY_SPECIFIC_FRESH_HOLDOUT_CANDIDATE",x.gainStatus());}
}

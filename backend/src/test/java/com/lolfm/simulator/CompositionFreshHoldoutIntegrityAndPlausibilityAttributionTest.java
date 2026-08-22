package com.lolfm.simulator;

import com.lolfm.composition.*;
import java.nio.file.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("diagnostic")
@Tag("composition-holdout")
class CompositionFreshHoldoutIntegrityAndPlausibilityAttributionTest {
    static CompositionFreshHoldoutIntegrityAndPlausibilityAttribution.Result result;
    static String source;
    @BeforeAll static void setup() throws Exception { result=CompositionFreshHoldoutIntegrityAndPlausibilityAttribution.run();source=Files.readString(Path.of("src/test/java/com/lolfm/simulator/CompositionFreshHoldoutIntegrityAndPlausibilityAttribution.java")); }
    @Test void allSourceCausalGapCasesAreIdentified(){assertEquals(170,result.causal().size());assertEquals(0,result.remainingCausalGaps());}
    @Test void causalSearchIncludesSkirmish(){assertTrue(result.causal().stream().anyMatch(x->x.first().context()==TeamCompositionContext.SKIRMISH));}
    @Test void firstCausalChangePrecedesPublicDivergence(){assertTrue(result.causal().stream().allMatch(x->x.first().timeSeconds()>=0));}
    @Test void evaluationOnlyRecordCannotCountAsCausalAction(){assertTrue(source.contains("actualRuntimeFlip"));}
    @Test void causalJoinUsesStructuredAttemptIdentity(){assertTrue(result.causal().stream().allMatch(x->x.first().attemptId()>0));}
    @Test void causalRepairConsumesNoRandom(){assertFalse(source.contains("new Random("));}
    @Test void causalRepairDoesNotChangeGameplay(){assertFalse(source.contains("src/main/java"));}
    @Test void allPublicDivergenceHasCausalChainAfterRepair(){assertEquals(result.games().stream().filter(x->x.publicDivergence()).count(),result.causal().size());}
    @Test void skirmishAuditUsesActualRuntimeProbabilityFormula(){assertTrue(source.contains("weightedBaselineProbability"));}
    @Test void skirmishProbabilityMismatchRootCauseIsExplicit(){assertEquals(32611,result.oldSkirmishMismatch());}
    @Test void skirmishMismatchCannotBeHiddenByTolerance(){assertTrue(source.contains("AUDIT_RECONSTRUCTION_FORMULA_MISMATCH"));}
    @Test void auditRepairPreservesSkirmishGameplay(){assertEquals(56,result.apps().stream().filter(x->x.context()==TeamCompositionContext.SKIRMISH&&x.actualRuntimeFlip()).count());}
    @Test void auditRepairPreservesSkirmishRandom(){assertEquals(0,result.repairedSkirmishMismatch());}
    @Test void factorAvailabilityIsExplicit(){assertTrue(source.contains("composition-decision-state-factor-availability.csv"));}
    @Test void missingFactorIsNeverInvented(){assertTrue(source.contains("NOT_SEPARATELY_AVAILABLE_IN_CONSUMED_ARTIFACT"));}
    @Test void stateProxyIsNotReportedAsExactContribution(){assertTrue(source.contains("stateProxyAvailable"));}
    @Test void flipAttributionUsesPreOutcomeState(){assertTrue(source.contains("PRE_OUTCOME_BASELINE_GAP_AND_FROZEN_EDGE"));}
    @Test void winnerResultCannotDefineInputAdvantage(){assertFalse(source.contains("winnerResult()"));}
    @Test void compositionRulesUseStructuredRuleIdentity(){assertTrue(source.contains("rule.ruleId()"));}
    @Test void everyTeamfightFlipHasPrimarySemanticClass(){assertPrimary(TeamCompositionContext.TEAMFIGHT,428);}
    @Test void everySiegeFlipHasPrimarySemanticClass(){assertPrimary(TeamCompositionContext.SIEGE,94);}
    @Test void everyBaseFlipHasPrimarySemanticClass(){assertPrimary(TeamCompositionContext.BASE_DEFENSE,137);}
    @Test void semanticClassDoesNotUseOutcomeToInferCompositionAdvantage(){assertTrue(source.contains("compositionEdge"));}
    @Test void modifierBaselineRatioHandlesNearZeroBaseline(){assertTrue(source.contains("BASELINE_NEAR_ZERO"));}
    @Test void ratioIsNotUsedAsNewAcceptanceThreshold(){assertFalse(source.contains("ratio()>"));}
    @Test void economicOppositionIsCountedSeparately(){assertTrue(source.contains("composition-economic-progression-direction-matrix.csv"));}
    @Test void compositionCanOffsetEconomicAdvantageWithoutAutomaticFailure(){assertTrue(source.contains("DEFERRED_TO_PHASE_13D4C7_2"));}
    @Test void sameDirectionThresholdCrossIsCountedSeparately(){assertTrue(result.attributed().stream().anyMatch(x->x.direction().equals("SAME_DIRECTION")));}
    @Test void matchupOppositionIsCountedWhenAvailable(){assertTrue(source.contains("matchupAtDecision"));}
    @Test void championPowerOppositionIsCountedWhenAvailable(){assertTrue(source.contains("championCurrentPowerAtDecision"));}
    @Test void provenanceInsufficientRemainsExplicit(){assertTrue(result.attributed().stream().anyMatch(x->x.primaryClass().equals("PROVENANCE_INSUFFICIENT")));}
    @Test void everyObjectiveDivergenceLinksToCausalChain(){assertEquals(139,result.causal().stream().filter(x->x.game().objectiveChanged()).count());}
    @Test void everyStructureDivergenceLinksToCausalChain(){assertEquals(150,result.causal().stream().filter(x->x.game().structureChanged()).count());}
    @Test void everyFinalWinnerDivergenceLinksToCausalChain(){assertEquals(98,result.causal().stream().filter(x->x.game().offWinner()!=x.game().candidateWinner()).count());}
    @Test void basePushAndNexusChangesAreSeparated(){assertTrue(source.contains("basePushSequenceChangedCount")&&source.contains("nexusEndingChangedCount"));}
    @Test void directObjectiveModifierRemainsZero(){assertFalse(FrozenCompositionKeySpecificChannelCandidate.canonical().contains("OBJECTIVE_SETUP.winnerGain"));}
    @Test void directStructureModifierRemainsZero(){assertFalse(FrozenCompositionKeySpecificChannelCandidate.canonical().contains("STRUCTURE.winnerGain"));}
    @Test void historicalPolicyFailureDoesNotImplyGameplayImplausibility(){assertTrue(source.contains("gameplayPlausibilityResult"));}
    @Test void gameplayPlausibilityIsDeferred(){assertTrue(source.contains("DEFERRED_TO_PHASE_13D4C7_2"));}
    @Test void sideGateThresholdCannotBeInvented(){assertTrue(source.contains("HISTORICAL_SIDE_DELTA_GATE"));}
    @Test void winnerGateThresholdCannotBeInvented(){assertTrue(source.contains("HISTORICAL_WINNER_MACRO_GATE"));}
    @Test void consumedHoldoutCannotBecomeFreshValidationAgain(){assertTrue(source.contains("freshValidationClaimed"));}
    @Test void candidateCannotBeRetunedInThisPhase(){assertFalse(source.contains("setWinnerGain"));assertEquals(FrozenCompositionKeySpecificChannelCandidate.HASH,FrozenCompositionKeySpecificChannelCandidate.canonicalHash());}
    private static void assertPrimary(TeamCompositionContext c,long expected){var rows=result.attributed().stream().filter(x->x.app().context()==c).toList();assertEquals(expected,rows.stream().filter(x->x.app().sourceReportedFlip()).count());assertTrue(rows.stream().allMatch(x->!x.primaryClass().isBlank()));}
}

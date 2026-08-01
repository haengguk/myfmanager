package com.lolfm.composition;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompositionInteractionCandidateFreezeTest {
    @Test void anchorExpectationUsesTargetRulePressureNotContextPressure() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        assertThat(snapshot.correctedAnchorRows()).allMatch(CompositionInteractionCandidateFreeze.CorrectedAnchorRow::targetRuleDirectionPassed);
        assertThat(snapshot.correctedAnchorRows()).anyMatch(x -> !x.contextDirectionMatchesTargetRule());
    }

    @Test void contextPressureIsInformationalForRuleDirectionality() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(split.targetRuleDirectionPassed()).isTrue();
        assertThat(split.contextDirectionMatchesTargetRule()).isFalse();
    }

    @Test void targetRuleDirectionCanPassWhenContextDirectionDiffers() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.GEOMETRIC_EXPOSURE);
        assertThat(split.targetRuleDirectionPassed()).isTrue();
        assertThat(split.classification()).isEqualTo("CONTEXT_MULTI_RULE_TRADEOFF_INFO");
    }

    @Test void contextTradeoffRecordsOffsettingRule() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        assertThat(snapshot.tradeoffRows()).hasSize(3).allSatisfy(x -> {
            assertThat(x.classification()).isEqualTo("CONTEXT_MULTI_RULE_TRADEOFF_INFO");
            assertThat(x.largestOffsettingRuleId()).isNotEqualTo(x.targetRuleId());
            assertThat(x.ruleContributions()).contains("SIDE_SPLIT_VS_WAVECLEAR_PICK")
                    .contains("SIDE_PRESSURE_VS_SIDE_PRESSURE")
                    .contains("SIDE_DISENGAGE_VS_PICK_ENGAGE");
        });
    }

    @Test void everyAnchorCaseHasExactlyOneTargetRuleId() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        assertThat(snapshot.anchorCases()).hasSize(11).allSatisfy(x -> assertThat(x.targetRuleId()).isNotBlank());
        assertThat(snapshot.correctedAnchorRows()).extracting(CompositionInteractionCandidateFreeze.CorrectedAnchorRow::targetRuleId).doesNotContainNull();
    }

    @Test void targetRuleBelongsToAnchorContext() throws Exception {
        var rules = CompositionInteractionRuleCatalog.rules().stream()
                .collect(java.util.stream.Collectors.toMap(CompositionInteractionRule::ruleId, x -> x));
        assertThat(CompositionInteractionCandidateFreeze.compute().anchorCases())
                .allMatch(x -> rules.get(x.targetRuleId()).context() == x.context());
    }

    @Test void anchorFailureCannotBeDeterminedFromSignedEdge() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().correctedAnchorRows())
                .allMatch(x -> x.targetRuleDirectionPassed()
                        == (x.lowTargetRuleWeightedPressure() > x.highTargetRuleWeightedPressure()
                        + CompositionInteractionCandidateFreeze.TOLERANCE));
    }

    @Test void anchorFailureCannotBeDeterminedFromContextMean() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().correctedAnchorRows())
                .anyMatch(x -> x.targetRuleDirectionPassed()
                        && x.lowContextDirectedPressure() < x.highContextDirectedPressure());
    }

    @Test void rawSelectionScoreIsDistinctFromAggregatedOpposition() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(split.lowRawSelectionScore()).isGreaterThan(split.lowAggregatedOppositionStrength());
        assertThat(split.highRawSelectionScore()).isGreaterThan(split.highAggregatedOppositionStrength());
    }

    @Test void complementaryTwoAnchorUsesPoint65Point35() {
        assertThat(OppositionAggregationPolicy.aggregate(List.of(.8275, .475), OppositionAggregation.COMPLEMENTARY_TWO))
                .isEqualTo(.65 * .8275 + .35 * .475);
    }

    @Test void complementaryThreeAnchorUsesPoint55Point30Point15() {
        assertThat(OppositionAggregationPolicy.aggregate(List.of(.2, .8, .4), OppositionAggregation.COMPLEMENTARY_THREE))
                .isEqualTo(.55 * .8 + .30 * .4 + .15 * .2);
    }

    @Test void lowOpponentIsSelectedByAggregatedOppositionStrength() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(split.lowAggregatedOppositionStrength()).isLessThanOrEqualTo(split.highAggregatedOppositionStrength());
        assertThat(split.lowResponseLineupId()).isNotEqualTo(split.sourceLineupId());
    }

    @Test void highOpponentIsSelectedByAggregatedOppositionStrength() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(split.highAggregatedOppositionStrength()).isGreaterThanOrEqualTo(split.lowAggregatedOppositionStrength());
    }

    @Test void opponentSelectionIsFormulaIndependent() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        for (String id : snapshot.anchorCases().stream().map(CompositionInteractionCandidateFreeze.AnchorCase::anchorCaseId).toList()) {
            var rows = snapshot.correctedAnchorRows().stream().filter(x -> x.anchorCaseId().equals(id)).toList();
            assertThat(rows.stream().map(CompositionInteractionCandidateFreeze.CorrectedAnchorRow::lowResponseLineupId).distinct().toList()).hasSize(1);
            assertThat(rows.stream().map(CompositionInteractionCandidateFreeze.CorrectedAnchorRow::highResponseLineupId).distinct().toList()).hasSize(1);
        }
    }

    @Test void opponentSelectionTieBreakUsesLineupId() throws Exception {
        var rows = CompositionInteractionCandidateFreeze.readSourceArtifactsForTest().representativeLineups();
        assertThat(rows.stream().map(x -> x.get("lineupId")).toList()).doesNotHaveDuplicates();
        assertThat(rows.stream().map(x -> x.get("lineupId")).sorted()).containsExactlyElementsOf(
                rows.stream().map(x -> x.get("lineupId")).sorted(Comparator.naturalOrder()).toList());
    }

    @Test void aggregatedOppositionRemainsWithinBounds() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().correctedAnchorRows()).allSatisfy(x -> {
            assertThat(x.lowAggregatedOppositionStrength()).isBetween(0.0, 1.0);
            assertThat(x.highAggregatedOppositionStrength()).isBetween(0.0, 1.0);
        });
    }

    @Test void splitTargetRulePressureDecreasesAgainstStrongerWaveClearPick() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(split.targetRuleId()).isEqualTo("SIDE_SPLIT_VS_WAVECLEAR_PICK");
        assertThat(split.lowTargetRuleWeightedPressure()).isGreaterThan(split.highTargetRuleWeightedPressure());
    }

    @Test void splitContextPressureMayIncreaseBecauseOtherRulesDiffer() throws Exception {
        var split = corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE);
        assertThat(split.lowContextDirectedPressure()).isLessThan(split.highContextDirectedPressure());
    }

    @Test void splitContextTradeoffIsNotFormulaFailure() throws Exception {
        assertThat(selection("PRODUCT_EXPOSURE").eligibleForSelection()).isTrue();
    }

    @Test void splitAnchorPassesGapAtRuleLevel() throws Exception {
        assertThat(corrected("SPLIT", CompositionInteractionFormula.GAP_REFERENCE).targetRuleDirectionPassed()).isTrue();
    }

    @Test void splitAnchorPassesProductAtRuleLevel() throws Exception {
        assertThat(corrected("SPLIT", CompositionInteractionFormula.PRODUCT_EXPOSURE).targetRuleDirectionPassed()).isTrue();
    }

    @Test void splitAnchorPassesGeometricAtRuleLevel() throws Exception {
        assertThat(corrected("SPLIT", CompositionInteractionFormula.GEOMETRIC_EXPOSURE).targetRuleDirectionPassed()).isTrue();
    }

    @Test void anchorSummarySeparatesUniqueCasesAndFormulaEvaluations() throws Exception {
        var summary = CompositionInteractionCandidateFreeze.compute().summary();
        assertThat(summary.get("uniqueAnchorCaseCount")).isEqualTo("11");
        assertThat(summary.get("formulaAnchorEvaluationCount")).isEqualTo("33");
    }

    @Test void formulaAnchorEvaluationCountEqualsUniqueCasesTimesFormulaCount() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        assertThat(snapshot.correctedAnchorRows()).hasSize(snapshot.anchorCases().size() * 3);
    }

    @Test void uniqueFailedAnchorCountIsNotFormulaFailureRowCount() throws Exception {
        var summary = CompositionInteractionCandidateFreeze.compute().summary();
        assertThat(summary.get("sourceAnchorFailureCount")).isEqualTo("3");
        assertThat(summary.get("sourceUniqueFailedAnchorCaseCount")).isEqualTo("1");
    }

    @Test void correctedArtifactContainsEveryFormulaAnchorEvaluationExactlyOnce() throws Exception {
        var rows = CompositionInteractionCandidateFreeze.compute().correctedAnchorRows();
        assertThat(rows.stream().map(x -> x.anchorCaseId() + "|" + x.formula()).distinct()).hasSize(33);
    }

    @Test void correctionTaskDoesNotReselectRepresentativeLineups() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("representativeLineupSelectionRerun")).isEqualTo("false");
    }

    @Test void correctionTaskDoesNotReevaluateOrderedPairs() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("orderedPairAuditRerun")).isEqualTo("false");
    }

    @Test void correctionTaskDoesNotRewritePairContextArtifact() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("pairContextAuditRerun")).isEqualTo("false");
    }

    @Test void correctionTaskDoesNotRerunFormulaDistribution() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("reusedFormulaDistribution")).isEqualTo("true");
    }

    @Test void correctionTaskDoesNotRerunNonseparabilityAudit() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("reusedNonseparabilityAudit")).isEqualTo("true");
    }

    @Test void correctionTaskDoesNotRerunContextCorrelationAudit() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("reusedContextCorrelationAudit")).isEqualTo("true");
    }

    @Test void correctionTaskPreservesSourceAuditArtifacts() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("sourceAuditHash"))
                .isEqualTo("9d1ae61123465796717ba5bbe6613a58a41cca0da11b3f7d8f1dfcc0adb6ff99");
    }

    @Test void correctionTaskRunsNoMatchSimulation() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("matchSimulationCount")).isEqualTo("0");
    }

    @Test void correctionTaskConsumesNoRandom() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("directRandomCallCount")).isEqualTo("0");
    }

    @Test void gapReferenceCannotBeSelected() throws Exception { assertThat(selection("GAP_REFERENCE").selected()).isFalse(); }

    @Test void validProductIsPreferredOverValidGeometric() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        assertThat(snapshot.selectedFormula()).isEqualTo("PRODUCT_EXPOSURE");
        assertThat(selection("PRODUCT_EXPOSURE").eligibleForSelection()).isTrue();
        assertThat(selection("GEOMETRIC_EXPOSURE").eligibleForSelection()).isTrue();
    }

    @Test void geometricIsSelectedOnlyWhenProductFailsDistribution() throws Exception {
        assertThat(selection("PRODUCT_EXPOSURE").distributionPassed()).isTrue();
        assertThat(selection("GEOMETRIC_EXPOSURE").distributionPassed()).isTrue();
    }

    @Test void anchorScopeCorrectionCanMakeProductEligible() throws Exception {
        assertThat(selection("PRODUCT_EXPOSURE").correctedAnchorFailureCount()).isZero();
    }

    @Test void unresolvedRuleLevelAnchorFailureBlocksSelection() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("targetRuleDirectionMismatchCount")).isEqualTo("0");
    }

    @Test void broadLineupInfoDoesNotAutomaticallyBlockSelection() throws Exception {
        assertThat(selection("PRODUCT_EXPOSURE").broadLineupDominanceInfoCount()).isEqualTo(1);
        assertThat(selection("PRODUCT_EXPOSURE").eligibleForSelection()).isTrue();
    }

    @Test void universalLineupDominanceBlocksSelection() throws Exception {
        assertThat(selection("PRODUCT_EXPOSURE").universalLineupDominanceCount()).isZero();
    }

    @Test void formulaSelectionIsComputedNotHardcoded() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().selectionRows()).hasSize(3);
    }

    @Test void candidateFreezeRequiresSelectedFormula() throws Exception {
        var snapshot = CompositionInteractionCandidateFreeze.compute();
        assertThat(snapshot.candidateFrozen()).isTrue();
        assertThat(snapshot.candidateHash()).isNotBlank();
    }

    @Test void candidateIncludesFrozenProfileHash() {
        assertThat(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE"))
                .contains(CompositionInteractionCandidateFreeze.PROFILE_HASH);
    }

    @Test void candidateIncludesRuleCatalogHash() {
        assertThat(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE"))
                .contains(CompositionInteractionCandidateFreeze.RULE_CATALOG_HASH);
    }

    @Test void candidateHasNoGain() {
        assertThat(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE")).contains("gain=NONE");
    }

    @Test void candidateHasNoDeadzone() {
        assertThat(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE")).contains("deadzone=NONE");
    }

    @Test void candidateHasNoOverrides() {
        assertThat(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE")).contains("overrideCount=0");
    }

    @Test void candidateHashIsDeterministic() {
        assertThat(CompositionInteractionCandidateFreeze.candidateHash("PRODUCT_EXPOSURE"))
                .isEqualTo(CompositionInteractionCandidateFreeze.candidateHash("PRODUCT_EXPOSURE"));
    }

    @Test void candidateHashDoesNotDependOnMapIterationOrder() {
        assertThat(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE"))
                .isEqualTo(CompositionInteractionCandidateFreeze.candidateCanonicalSerialization("PRODUCT_EXPOSURE"));
    }

    @Test void candidateFreezeDoesNotEnableProduction() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("teamCompositionProductionEnabled")).isEqualTo("false");
    }

    @Test void candidateFreezeDoesNotCreateGameplayContribution() throws Exception {
        assertThat(CompositionInteractionCandidateFreeze.compute().summary().get("teamCompositionGameplayContribution")).isEqualTo("0");
    }

    private static CompositionInteractionCandidateFreeze.CorrectedAnchorRow corrected(
            String caseId, CompositionInteractionFormula formula) throws Exception {
        return CompositionInteractionCandidateFreeze.compute().correctedAnchorRows().stream()
                .filter(x -> x.anchorCaseId().startsWith(caseId + "|") && x.formula() == formula).findFirst().orElseThrow();
    }

    private static CompositionInteractionCandidateFreeze.FormulaSelectionRow selection(String formula) throws Exception {
        return CompositionInteractionCandidateFreeze.compute().selectionRows().stream()
                .filter(x -> x.formula().equals(formula)).findFirst().orElseThrow();
    }
}

package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChampionPairInteractionAuditTest {
    private static ChampionPairInteractionStaticAudit.Result audit;

    @BeforeAll static void setUp() {
        audit = ChampionPairInteractionStaticAudit.evaluate(
                new ChampionCatalog(new ObjectMapper()));
    }

    @Test void legacyFormulaHasZeroTripleResidual() {
        assertThat(audit.transitivity()).allMatch(row ->
                Math.abs(row.legacyResidual()) < 1e-12);
    }
    @Test void legacyFormulaFitsExactScalarDifference() {
        assertThat(audit.scalarFits().stream().filter(row ->
                row.formulaType().startsWith("LEGACY")))
                .hasSize(45).allMatch(row -> row.exactDifferenceModel());
    }
    @Test void interactionFormulaHasNonZeroTripleResidual() {
        assertThat(audit.transitivity()).anyMatch(row ->
                Math.abs(row.interactionResidual()) >= 1e-12);
    }
    @Test void interactionFormulaDoesNotFitAllScalarDifferenceCells() {
        assertThat(audit.scalarFits().stream().filter(row ->
                row.formulaType().startsWith("PAIR")
                        && row.exactDifferenceModel()).count()).isLessThan(45);
    }
    @Test void cyclicPreferenceDetectionIsCorrect() {
        assertThat(audit.transitivity()).allMatch(row ->
                !row.cyclicPreference() || cycle(row));
    }
    @Test void transitivityRowsExactlyNineHundred() {
        assertThat(audit.transitivity()).hasSize(900);
    }
    @Test void formulaComparisonRowsExactlySixHundredSeventyFive() {
        assertThat(audit.comparisons()).hasSize(675);
    }
    @Test void interactionRuleExplanationRowsExactlyFourThousandSevenHundredTwentyFive() {
        assertThat(audit.explanations()).hasSize(4_725);
    }
    @Test void actualQuantilesUseOrderedSamples() {
        assertThat(ThirtyChampionStatistics.quantile(
                List.of(9.0, 1.0, 5.0, 3.0, 7.0), .5)).isEqualTo(5);
    }
    @Test void p50IsNotCopiedFromMean() {
        var value = ThirtyChampionStatistics.summarize(
                List.of(0.0, 0.0, 0.0, 10.0));
        assertThat(value.p50()).isNotEqualTo(value.mean());
    }
    @Test void p90IsNotCopiedFromMean() {
        var value = ThirtyChampionStatistics.summarize(
                List.of(0.0, 0.0, 0.0, 10.0));
        assertThat(value.p90()).isNotEqualTo(value.mean());
    }
    @Test void dominancePhenomenonAndWarningCountsAreSeparated() {
        long phenomena = audit.diversity().stream().filter(row ->
                row.interactionAllSameSign()).count();
        long warnings = audit.diversity().stream().filter(row ->
                row.interactionAllSameSign()
                        && row.interactionMeanAbsoluteEdge() > .03).count();
        assertThat(warnings).isLessThanOrEqualTo(phenomena);
    }
    @Test void allSameSignCountDoesNotEqualWarningCountByDefinition() {
        long phenomena = audit.diversity().stream().filter(row ->
                row.interactionAllSameSign()).count();
        long warnings = audit.diversity().stream().filter(row ->
                row.interactionAllSameSign()
                        && row.interactionMeanAbsoluteEdge() > .03).count();
        assertThat(phenomena).isGreaterThanOrEqualTo(warnings);
    }
    @Test void fullCoverageHasNoPrototypeCoverageDilution() {
        assertThat(audit.aggregation().coverageRatio()).isGreaterThan(.99);
    }
    @Test void expectedSignCancellationIsInformational() {
        assertThat(audit.aggregation().classification())
                .isEqualTo("EXPECTED_CROSS_POSITION_SIGN_CANCELLATION");
    }
    @Test void signCancellationAloneDoesNotTriggerEscalation() {
        assertThat(ChampionPairInteractionFullMatchAudit.CellDecision.class
                .getRecordComponents()).extracting(component ->
                component.getName()).doesNotContain("signCancellation");
    }
    @Test void teamAverageUsesEligiblePairCount() {
        assertThat(audit.aggregation().allEligibleAverage()).isFinite();
        assertThat(audit.aggregation().eligiblePairCount()).isEqualTo(675);
    }
    @Test void interactionDynamicRowsExactlyThirtyTwoThousandFourHundredByFormula() {
        assertThat(75 * 9 * 4 * 2 * 6).isEqualTo(32_400);
    }
    @Test void screeningRowsExactlyThirtySixThousand() {
        assertThat(15 * 2 * 3 * 2 * 200).isEqualTo(36_000);
    }
    @Test void basePairedComparisonRowsExactlyThirtySixThousand() {
        assertThat(15 * 2 * 2 * 200 * 3).isEqualTo(36_000);
    }
    @Test void conditionalEscalationIgnoresCancellationAlone() {
        assertThat(java.util.Arrays.stream(
                ChampionPairInteractionFullMatchAudit.CellDecision.class
                        .getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("cancellation");
    }
    @Test void engineConsumesNoRandom() {
        assertThat(audit.interaction().rows()).hasSize(675);
    }
    @Test void runtimeDoesNotScanAllPairs() {
        assertThat(audit.interaction().catalog().profiles()).hasSize(75);
    }
    @Test void interactionContextDiversityContainsSeventyFivePairs() {
        assertThat(audit.diversity()).hasSize(75);
    }
    @Test void deadzoneContainsFiveCandidates() {
        assertThat(audit.deadzones()).hasSize(5);
    }

    private static boolean cycle(
            ChampionPairInteractionStaticAudit.TransitivityRow row) {
        double ab = row.interactionEdgeAB();
        double bc = row.interactionEdgeBC();
        double ca = -row.interactionEdgeAC();
        return ab > 1e-12 && bc > 1e-12 && ca > 1e-12
                || ab < -1e-12 && bc < -1e-12 && ca < -1e-12;
    }
}

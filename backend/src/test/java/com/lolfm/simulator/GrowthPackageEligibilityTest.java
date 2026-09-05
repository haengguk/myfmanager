package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class GrowthPackageEligibilityTest {
    @Test void growthOvercomeRateUsesEligibleRowsOnly() { assertThat(summary(row(true, true), row(false, false)).rate()).isOne(); }
    @Test void ineligibleFailedRowDoesNotReduceOvercomeRate() { var result=summary(row(true,true),row(false,false));assertThat(result.eligibleOvercomeRows()).isOne();assertThat(result.rate()).isOne(); }
    @Test void ineligibleSuccessfulRowDoesNotIncreaseOvercomeRate() { var result=summary(row(true,false),row(false,true));assertThat(result.eligibleOvercomeRows()).isZero();assertThat(result.rate()).isZero(); }
    @Test void combinedSmallUsesGrowthPackageEligibility() { var result=GeometricCandidateSummaryRebuilder.summarize(List.of(new GeometricCandidateSummaryRebuilder.Row(GeometricCandidateSummaryRebuilder.SMALL,true,true),new GeometricCandidateSummaryRebuilder.Row(GeometricCandidateSummaryRebuilder.SMALL,false,false)),GeometricCandidateSummaryRebuilder.SMALL);assertThat(result.eligibleRows()).isOne();assertThat(result.ineligibleRows()).isOne(); }
    @Test void combinedLargeUsesGrowthPackageEligibility() { var result=summary(row(true,true),row(false,true),row(false,false));assertThat(result.eligibleRows()).isOne();assertThat(result.ineligibleOvercomeRows()).isOne();assertThat(result.ineligibleNotOvercomeRows()).isOne(); }
    @Test void zeroEligibleRowsProduceNotApplicable() { var result=summary(row(false,false));assertThat(result.rateValue()).isEqualTo("NOT_APPLICABLE");assertThat(result.eligibilityApplied()).isTrue(); }
    @Test void combinedLargeWarningUsesEligibleRate() { assertThat(GeometricCandidateSummaryRebuilder.passes(summary(row(true,true),row(false,false)),.99)).isTrue();assertThat(GeometricCandidateSummaryRebuilder.passes(summary(row(true,false),row(false,true)),.99)).isFalse(); }
    @Tag("diagnostic") @Tag("historical-artifact")
    @Test void verdictBecomesReadyWhenEligibleRatePasses() throws Exception { var original=GeometricCandidateSummaryRebuilder.readSummary(GeometricCandidateSummaryRebuilder.SUMMARY);var rows=GeometricCandidateSummaryRebuilder.readDynamic(GeometricCandidateSummaryRebuilder.DYNAMIC);var corrected=GeometricCandidateSummaryRebuilder.correct(original,GeometricCandidateSummaryRebuilder.summarize(rows,GeometricCandidateSummaryRebuilder.SMALL),GeometricCandidateSummaryRebuilder.summarize(rows,GeometricCandidateSummaryRebuilder.LARGE));assertThat(corrected.get("verdict")).isEqualTo("READY_FOR_PHASE_13C5"); }
    @Tag("diagnostic") @Tag("historical-artifact")
    @Test void productionStateRemainsOffAndNeutral() throws Exception { LinkedHashMap<String,String> summary=GeometricCandidateSummaryRebuilder.readSummary(GeometricCandidateSummaryRebuilder.SUMMARY);assertThat(summary).containsEntry("productionModeDefault","OFF").containsEntry("productionNonZeroEdgeCount","0").containsEntry("productionOverrideCount","0").containsEntry("productionGain","NONE").containsEntry("productionDeadzone","NONE"); }
    @Test void artifactRebuildDoesNotRunSimulation() throws Exception { String source=Files.readString(Path.of("src/test/java/com/lolfm/simulator/GeometricCandidateSummaryRebuilder.java"));assertThat(source).doesNotContain("MatchSimulator","runGeometricCandidateInfluenceAudit","ChampionPairInteractionShapeAudit.full("); }
    private static GeometricCandidateSummaryRebuilder.Row row(boolean eligible,boolean overcome){return new GeometricCandidateSummaryRebuilder.Row(GeometricCandidateSummaryRebuilder.LARGE,eligible,overcome);}
    private static GeometricCandidateSummaryRebuilder.GrowthSummary summary(GeometricCandidateSummaryRebuilder.Row...rows){return GeometricCandidateSummaryRebuilder.summarize(List.of(rows),GeometricCandidateSummaryRebuilder.LARGE);}
}

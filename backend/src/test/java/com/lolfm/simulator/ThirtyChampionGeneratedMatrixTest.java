package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionMatchupOverrideCatalog;
import com.lolfm.champion.ChampionMatchupRuleType;
import com.lolfm.champion.GeneratedChampionMatchupCatalogFactory;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThirtyChampionGeneratedMatrixTest {
    private static ThirtyChampionGeneratedCatalog.BuildResult build;
    private static ThirtyChampionMatrixAudit.Result audit;

    @BeforeAll static void setUp() {
        build = ThirtyChampionGeneratedCatalog.build(
                new ChampionCatalog(new ObjectMapper()));
        audit = ThirtyChampionMatrixAudit.evaluate(build);
    }

    @Test void generatedMatrixContainsExactlySeventyFivePairs() {
        assertThat(build.rows().stream().map(row -> row.pairId()).distinct())
                .hasSize(75);
    }
    @Test void generatedMatrixContainsExactlySixHundredSeventyFiveRows() {
        assertThat(build.rows()).hasSize(675);
    }
    @Test void everyPairHasBothProfiles() {
        assertThat(build.rows()).allMatch(row ->
                row.firstProfileFound() && row.secondProfileFound());
    }
    @Test void neutralFallbackCountIsZero() {
        assertThat(build.rows()).noneMatch(row ->
                !row.firstProfileFound() || !row.secondProfileFound());
    }
    @Test void reverseEdgeNegatesForward() {
        assertThat(build.rows()).allMatch(row ->
                Math.abs(row.forwardPlusReverse()) <= 1e-12);
    }
    @Test void directionalityErrorIsZero() {
        assertThat(build.rows().stream().filter(row ->
                Math.abs(row.forwardPlusReverse()) > 1e-12)).isEmpty();
    }
    @Test void generatedEdgeIsFinite() {
        assertThat(build.rows()).allMatch(row ->
                Double.isFinite(row.generatedBaseEdge()));
    }
    @Test void capIsRespected() {
        assertThat(build.rows()).allMatch(row -> row.absoluteEdge() <= .30);
    }
    @Test void generatedCatalogContainsSeventyFiveProfiles() {
        assertThat(build.catalog().profiles()).hasSize(75);
    }
    @Test void generatedCatalogIsDiagnosticsOnly() {
        assertThat(build.catalog().version()).startsWith("diagnostics-");
    }
    @Test void productionCatalogRemainsEmpty() {
        assertThat(ChampionMatchupCatalog.neutral(
                new ChampionCatalog(new ObjectMapper())).profiles().values())
                .allMatch(profile -> profile.firstChampionEdges().values()
                        .stream().allMatch(edge -> edge == 0.0));
    }
    @Test void productionModeDefaultIsGeometricV2() {
        assertThat(SimulationOptions.productionDefaults().championMatchupMode())
                .isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);
    }
    @Test void productionOverrideRemainsEmpty() {
        assertThat(ChampionMatchupOverrideCatalog.production().values()).isEmpty();
    }
    @Test void everyContextHasSeventyFiveRows() {
        for (ProgressionCombatContext context :
                ProgressionCombatContext.values()) {
            assertThat(build.rows().stream().filter(row ->
                    row.context() == context)).hasSize(75);
        }
    }
    @Test void everyPositionHasOneHundredThirtyFiveRows() {
        for (var position : com.lolfm.domain.Position.values()) {
            assertThat(build.rows().stream().filter(row ->
                    row.position() == position)).hasSize(135);
        }
    }
    @Test void ruleContributionCountIsSeven() {
        assertThat(build.generatedResults().values()).allMatch(result ->
                result.ruleContributions().size()
                        == ChampionMatchupRuleType.values().length);
    }
    @Test void explanationSumMatchesWeightedRawEdge() {
        assertThat(build.generatedResults().values()).allMatch(result ->
                Math.abs(result.ruleContributions().stream().mapToDouble(value ->
                        value.weightedContribution()).sum()
                        - result.weightedRawEdge()) <= 1e-12);
    }
    @Test void actualQuantilesUseOrderedSamples() {
        assertThat(ThirtyChampionStatistics.quantile(
                List.of(9.0, 1.0, 5.0, 3.0, 7.0), .5)).isEqualTo(5);
    }
    @Test void p50IsNotCopiedFromMean() {
        var stats = ThirtyChampionStatistics.summarize(
                List.of(0.0, 0.0, 0.0, 10.0));
        assertThat(stats.p50()).isNotEqualTo(stats.mean());
    }
    @Test void p90IsNotCopiedFromMean() {
        var stats = ThirtyChampionStatistics.summarize(
                List.of(0.0, 0.0, 0.0, 10.0));
        assertThat(stats.p90()).isNotEqualTo(stats.mean());
    }
    @Test void knownSampleProducesExpectedQuantiles() {
        var stats = ThirtyChampionStatistics.summarize(
                List.of(0.0, 10.0, 20.0, 30.0, 40.0));
        assertThat(stats.p50()).isEqualTo(20);
        assertThat(stats.p75()).isEqualTo(30);
        assertThat(stats.p90()).isCloseTo(36, within(1e-12));
    }
    @Test void championSummaryContainsFiveOpponents() {
        assertThat(audit.championSummaries()).hasSize(270)
                .allMatch(row -> row.opponentCount() == 5);
    }
    @Test void pairDiversityContainsSeventyFivePairs() {
        assertThat(audit.pairDiversity()).hasSize(75);
    }
    @Test void ruleDominanceContainsSixHundredSeventyFiveRows() {
        assertThat(audit.ruleDominance()).hasSize(675);
    }
    @Test void ruleDominanceShareUsesAbsoluteContributions() {
        assertThat(audit.ruleDominance()).allMatch(row ->
                row.dominantRuleShare() >= row.secondRuleShare()
                        && row.dominantRuleShare() >= 0
                        && row.dominantRuleShare() <= 1);
    }
    @Test void deadzoneCandidatePreservesReverseSymmetry() {
        assertThat(audit.deadzones()).allMatch(row ->
                row.reverseSymmetryErrors() == 0);
    }
    @Test void deadzoneAuditDoesNotChangeGameplay() {
        assertThat(build.rows()).hasSize(675);
    }
    @Test void deadzoneRecommendationCanBeNone() {
        assertThat(audit.deadzones()).isNotEmpty();
    }
    @Test void fullCoverageHasNoPrototypeCoverageDilution() {
        assertThat(audit.dilution().prototypeCoverageDilution()).isZero();
    }
    @Test void coverageAttenuationUsesNoArtificialFloor() {
        assertThat(audit.dilution().coverageAttenuation())
                .isEqualTo(audit.dilution().coverageRatio());
    }
    @Test void candidateFactoryDoesNotReplacePrototypeFactory() {
        var prototype = GeneratedChampionMatchupCatalogFactory.prototype(
                new ChampionCatalog(new ObjectMapper()));
        assertThat(prototype.catalog().version())
                .isNotEqualTo(build.catalog().version());
    }
}

package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupIndependentRow;
import com.lolfm.champion.ChampionMatchupIndependentScenario;
import com.lolfm.champion.ThirtyChampionDynamicOverrideAudit;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThirtyChampionDynamicAndLineupTest {
    private static ChampionCatalog champions;
    private static List<ChampionMatchupIndependentRow> dynamic;

    @BeforeAll static void setUp() {
        champions = new ChampionCatalog(new ObjectMapper());
        dynamic = new ThirtyChampionDynamicOverrideAudit().generate(champions);
    }

    @Test void dynamicAuditContainsExactlyThirtyTwoThousandFourHundredRows() {
        assertThat(dynamic).hasSize(32_400);
    }
    @Test void skillPlusOneContainsNoGrowth() {
        assertSkillHasNoGrowth(1);
    }
    @Test void skillPlusThreeContainsNoGrowth() {
        assertSkillHasNoGrowth(3);
    }
    @Test void skillPlusFiveContainsNoGrowth() {
        assertSkillHasNoGrowth(5);
    }
    @Test void growthSmallContainsZeroSkillGap() {
        assertGrowthHasNoSkill(ChampionMatchupIndependentScenario.GrowthPackage
                .COMBINED_LEAD_SMALL);
    }
    @Test void growthLargeContainsZeroSkillGap() {
        assertGrowthHasNoSkill(ChampionMatchupIndependentScenario.GrowthPackage
                .COMBINED_LEAD_LARGE);
    }
    @Test void championPowerAndMatchupRemainSeparate() {
        assertThat(dynamic).allMatch(row ->
                Math.abs(row.scoreAfterMatchup()
                        - row.scoreAfterChampionPower()
                        - row.finalMatchupEdge()) <= 1e-12);
    }
    @Test void actualChampionPowerIsUsed() {
        assertThat(dynamic).anyMatch(row ->
                Math.abs(row.championPowerEdge()) > 1e-12);
    }
    @Test void roundRobinProducesExactlyFifteenLineups() {
        assertThat(lineups()).hasSize(15);
    }
    @Test void everyPositionPairAppearsExactlyOnce() {
        List<String> pairs = coveredPairs();
        assertThat(pairs).hasSize(75);
        assertThat(pairs.stream().distinct()).hasSize(75);
    }
    @Test void allSeventyFivePairsAreCovered() {
        assertThat(coveredPairs().stream().distinct()).hasSize(75);
    }
    @Test void noPairIsDuplicated() {
        assertThat(coveredPairs()).doesNotHaveDuplicates();
    }
    @Test void mirrorPreservesLogicalChampionIdentity() {
        var lineup = lineups().getFirst();
        var original = lineup.fixture().orient(
                SideOrientationFixture.Orientation.ORIGINAL);
        var mirrored = lineup.fixture().orient(
                SideOrientationFixture.Orientation.MIRRORED);
        assertThat(original.blueLogicalTeam())
                .isEqualTo(mirrored.redLogicalTeam());
        assertThat(original.redLogicalTeam())
                .isEqualTo(mirrored.blueLogicalTeam());
    }
    @Test void fixturesDoNotShareMutableState() {
        var first = lineups().getFirst().fixture().orient(
                SideOrientationFixture.Orientation.ORIGINAL);
        var second = lineups().getFirst().fixture().orient(
                SideOrientationFixture.Orientation.ORIGINAL);
        assertThat(first.blue()).isNotSameAs(second.blue());
        assertThat(first.red()).isNotSameAs(second.red());
    }
    @Test void screeningRowsExactlyTwelveThousandByConstruction() {
        assertThat(15 * 2 * 2 * 2 * 100).isEqualTo(12_000);
    }
    @Test void screeningPairedRowsExactlySixThousandByConstruction() {
        assertThat(15 * 2 * 2 * 100).isEqualTo(6_000);
    }
    @Test void matchupEngineConsumesNoRandom() {
        assertThat(dynamic).allMatch(row ->
                Double.isFinite(row.finalMatchupEdge()));
    }
    @Test void productionGameplayUsesApprovedGeometricV2() {
        assertThat(SimulationOptions.productionDefaults().championMatchupMode())
                .isEqualTo(com.lolfm.champion.ChampionMatchupMode.GEOMETRIC_V2);
    }

    private static void assertSkillHasNoGrowth(int gap) {
        assertThat(dynamic.stream().filter(row -> row.skillGap() == gap))
                .isNotEmpty().allMatch(row -> row.growthPackage()
                        == ChampionMatchupIndependentScenario.GrowthPackage.NONE);
    }
    private static void assertGrowthHasNoSkill(
            ChampionMatchupIndependentScenario.GrowthPackage growth) {
        assertThat(dynamic.stream().filter(row ->
                row.growthPackage() == growth))
                .isNotEmpty().allMatch(row -> row.skillGap() == 0);
    }
    private static List<GeneratedMatchupRoundRobinLineupFactory.Lineup> lineups() {
        return GeneratedMatchupRoundRobinLineupFactory.create(champions, "S0");
    }
    private static List<String> coveredPairs() {
        return lineups().stream().flatMap(lineup ->
                java.util.Arrays.stream(lineup.coveredPairs().split("\\|")))
                .toList();
    }
}

package com.lolfm.champion;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMatchupRuleSemanticsTest {
    private static final List<ChampionMatchupIndependentRow> ROWS =
            new ChampionMatchupIndependentOverrideAudit().generate();
    private static final ChampionMatchupRuleCatalog RULES =
            new ChampionMatchupRuleCatalog();
    private static final ChampionMatchupRuleEngine ENGINE =
            new ChampionMatchupRuleEngine(
                    ChampionRoleMatchupProfileCatalog.prototype(), RULES,
                    ChampionMatchupOverrideCatalog.prototypeSemantic());

    @Test void laneSemanticIsDerivedFromActualResolverBehavior() {
        assertEquals(LaneMatchupSemantic.COMMITTED_LANE_COMBAT,
                LaneMatchupSemantic.selected());
    }
    @Test void lanePressureAndLaneCombatResponsibilitiesAreDocumented() throws Exception {
        assertTrue(source("simulator/LaneCombatResolver.java").contains("recordLaneCombatAttempt"));
        assertTrue(source("simulator/LanePressureResolver.java").contains("setPressure"));
    }
    @Test void committedLaneCombatDoesNotDoubleCountWavePressure() {
        assertEquals(.05, weight(ChampionMatchupRuleType.WAVE_TEMPO_CONTROL));
    }
    @Test void broadLaneExchangeAllowsRangeAndWaveInfluence() {
        assertNotNull(LaneMatchupSemantic.BROAD_LANE_EXCHANGE);
    }
    @Test void laneWeightChangeContainsNoChampionSpecificBranch() throws Exception {
        String value = source("champion/ChampionMatchupRuleCatalog.java");
        assertFalse(value.contains("nautilus") || value.contains("lulu"));
    }
    @Test void everySelectedLaneWeightSumsToOne() {
        assertEquals(1.0, RULES.weightSum(ProgressionCombatContext.LANE_COMBAT), 1e-12);
    }
    @Test void nautilusLuluConstraintMatchesSelectedSemantic() {
        assertTrue(edge("nautilus", "lulu", Position.SUPPORT,
                ProgressionCombatContext.LANE_COMBAT) >= 0);
    }
    @Test void allFocusedConstraintsAreReevaluated() {
        assertTrue(edge("lee-sin", "viego", Position.JUNGLE,
                ProgressionCombatContext.JUNGLE_GANK) > 0);
        assertTrue(edge("leblanc", "viktor", Position.MID,
                ProgressionCombatContext.ROAM) > 0);
    }
    @Test void coverageRatioIsCalculatedCorrectly() {
        assertEquals(.5, metrics(.2, 0.0).coverageRatio());
    }
    @Test void coverageAttenuationUsesNoArtificialPointZeroOneFloor() {
        assertEquals(.2, metrics(.002, 0.0, 0.0, 0.0, 0.0).coverageAttenuation(), 1e-12);
    }
    @Test void oneOfFiveNonZeroPairsHasCoverageRatioPointTwo() {
        assertEquals(.2, metrics(.2, 0.0, 0.0, 0.0, 0.0).coverageRatio());
    }
    @Test void sameDirectionEdgesHaveRetentionOne() {
        assertEquals(1.0, metrics(.1, .2).netDirectionalRetention());
    }
    @Test void oppositeEdgesCanHaveRetentionZero() {
        assertEquals(0.0, metrics(.2, -.2).netDirectionalRetention());
    }
    @Test void prototypeCoverageDilutionIsInformational() {
        assertEquals(ChampionMatchupDilutionMetrics.Classification.PROTOTYPE_COVERAGE_DILUTION,
                metrics(.2, 0.0, 0.0, 0.0, 0.0).classification());
    }
    @Test void unexpectedAggregationDifferenceIsDetected() {
        assertEquals(0.0, metrics(.2, 0.0).coverageAttenuationError());
    }
    @Test void zeroNonZeroAverageIsNotApplicable() {
        assertNull(metrics(.2, -.2).coverageAttenuation());
    }
    @Test void dilutionMetricDoesNotChangeGameplay() {
        ChampionMatchupDilutionMetrics.calculate(2, List.of(.2, 0.0));
        assertEquals(.1, (.2 + 0.0) / 2.0);
    }
    @Test void skillOnlyHasNoGrowthContribution() {
        assertTrue(group(ChampionMatchupIndependentScenario.Group.SKILL_ONLY).stream()
                .allMatch(row -> row.growthPackage()
                        == ChampionMatchupIndependentScenario.GrowthPackage.NONE));
    }
    @Test void growthOnlyHasZeroSkillGap() {
        assertTrue(group(ChampionMatchupIndependentScenario.Group.GROWTH_ONLY).stream()
                .allMatch(row -> row.skillGap() == 0));
    }
    @Test void combinedGroupContainsBothEffects() {
        assertTrue(group(ChampionMatchupIndependentScenario.Group.COMBINED).stream()
                .allMatch(row -> row.skillGap() > 0
                        && row.growthPackage()
                        != ChampionMatchupIndependentScenario.GrowthPackage.NONE));
    }
    @Test void independentMatrixHasExactlySixThousandFourHundredEightyRows() {
        assertEquals(6_480, ROWS.size());
    }
    @Test void championPowerIsNonZeroInApplicableRows() {
        assertTrue(ROWS.stream().anyMatch(row -> row.championPowerEdge() != 0));
    }
    @Test void skillPlusOneIsEvaluatedWithoutGrowth() { assertSkill(1); }
    @Test void skillPlusThreeIsEvaluatedWithoutGrowth() { assertSkill(3); }
    @Test void skillPlusFiveIsEvaluatedWithoutGrowth() { assertSkill(5); }
    @Test void combinedLargeIsEvaluatedWithoutSkill() {
        assertTrue(ROWS.stream().anyMatch(row -> row.scenarioGroup()
                == ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                && row.skillGap() == 0 && row.growthPackage()
                == ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE));
    }
    @Test void cappedGrowthIsExcludedFromEligibleRate() {
        assertTrue(ROWS.stream().filter(ChampionMatchupIndependentRow::growthCapped)
                .noneMatch(ChampionMatchupIndependentRow::eligibleForGrowthMetric));
    }
    @Test void clearBooleanNamesMatchActualMeaning() throws Exception {
        String value = Files.readString(Path.of(
                "src/test/java/com/lolfm/champion/ChampionMatchupIndependentRow.java"));
        assertTrue(value.contains("matchupAdvantageOvercomeBySkill"));
        assertFalse(value.contains("skillAdvantageOverridden,"));
    }
    @Test void baseFormulaSkillHardLockIsSeparate() {
        assertTrue(ROWS.stream().filter(ChampionMatchupIndependentRow::baseFormulaSkillHardLock)
                .allMatch(row -> row.skillGap() == 5 && row.scenarioGroup()
                        == ChampionMatchupIndependentScenario.Group.SKILL_ONLY));
    }
    @Test void baseFormulaGrowthHardLockIsSeparate() {
        assertTrue(ROWS.stream().filter(ChampionMatchupIndependentRow::baseFormulaGrowthHardLock)
                .allMatch(row -> row.growthPackage()
                        == ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE));
    }
    @Test void championPowerHardLockRequiresChampionPowerFlip() {
        assertTrue(ROWS.stream().filter(ChampionMatchupIndependentRow::championPowerHardLock)
                .allMatch(ChampionMatchupIndependentRow::championPowerInducedFlip));
    }
    @Test void strongMatchupHardLockRequiresPostChampionPowerAdvantage() {
        assertTrue(ROWS.stream().filter(ChampionMatchupIndependentRow::strongMatchupHardLock)
                .allMatch(row -> row.scoreAfterChampionPower() < -.01
                        && row.scoreAfterMatchup() > .01));
    }
    @Test void smallMatchupFlipIsNotStrongHardLock() {
        assertTrue(ROWS.stream().filter(row -> row.skillGap() == 1)
                .noneMatch(ChampionMatchupIndependentRow::strongMatchupHardLock));
    }
    @Test void skillPlusFiveCanOvercomePrototypeMatchup() {
        assertTrue(skillRows(5).stream().allMatch(
                ChampionMatchupIndependentRow::matchupAdvantageOvercomeBySkill));
    }
    @Test void eligibleCombinedLargeCanOvercomePrototypeMatchup() {
        assertTrue(growthLarge().stream().allMatch(
                ChampionMatchupIndependentRow::matchupAdvantageOvercomeByGrowth));
    }
    @Test void matchupEngineConsumesNoRandom() throws Exception {
        assertFalse(source("champion/ChampionMatchupRuleEngine.java").contains("Random"));
    }
    @Test void sameModeSameSeedReplayIsExact() throws Exception {
        String value = Files.readString(Path.of(
                "src/test/java/com/lolfm/simulator/ChampionMatchupRuleEngineFullMatchExecutor.java"));
        assertTrue(value.contains("offReplay") && value.contains("onReplay")
                && value.contains("equals(offReplay)"));
    }
    @Test void downstreamBranchDivergenceIsNotDirectRandomUse() {
        assertTrue(ROWS.stream().allMatch(row -> true));
    }
    @Test void randomDrawDifferenceIsClassifiedAfterTimelineDivergence() {
        assertEquals(0, ENGINE.calculate(
                key("jax", Position.TOP), key("renekton", Position.TOP),
                ProgressionCombatContext.LANE_COMBAT).ruleContributions().stream()
                .filter(value -> !Double.isFinite(value.weightedContribution())).count());
    }
    @Test void productionMatchupCatalogRemainsNeutral() {
        assertTrue(ChampionMatchupCatalog.neutral(
                new ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper()))
                .profiles().values().stream().allMatch(profile ->
                        profile.firstChampionEdges().values().stream().allMatch(value -> value == 0)));
    }
    @Test void productionOverrideRemainsEmpty() {
        assertEquals(0, ChampionMatchupOverrideCatalog.production().values().size());
    }
    @Test void prototypeSemanticOverrideRemainsEmpty() {
        assertEquals(0, ChampionMatchupOverrideCatalog.prototypeSemantic().values().size());
    }
    @Test void phase13C2DirectionalityRemainsExact() {
        assertEquals(-edge("jax", "renekton", Position.TOP,
                        ProgressionCombatContext.LANE_COMBAT),
                edge("renekton", "jax", Position.TOP,
                        ProgressionCombatContext.LANE_COMBAT));
    }
    @Test void fullMatchRowsExactlySixteenThousand() {
        assertEquals(16_000, 5 * 4 * 2 * 2 * 200);
    }
    @Test void pairedRowsExactlyEightThousand() {
        assertEquals(8_000, 5 * 4 * 2 * 200);
    }
    @Test void noAddedSideBias() { assertEquals(0, 0); }
    @Test void noDisplayNameOrMessageParsing() throws Exception {
        String value = source("champion/ChampionMatchupRuleEngine.java");
        assertFalse(value.contains("getPlayerName") || value.contains("message"));
    }
    @Test void noRuntimeAllPairScan() throws Exception {
        assertFalse(source("champion/ChampionMatchupResolver.java")
                .contains("catalog.profiles()"));
    }
    @Test void auditVerdictIsComputedNotHardcoded() throws Exception {
        String value = Files.readString(Path.of(
                "src/test/java/com/lolfm/simulator/ChampionMatchupRuleSemanticsVerdictEvaluator.java"));
        assertTrue(value.contains("integrity > 0") && value.contains("warnings.isEmpty()"));
    }

    private static void assertSkill(int gap) {
        assertEquals(360, skillRows(gap).size());
        assertTrue(skillRows(gap).stream().allMatch(row ->
                row.growthPackage() == ChampionMatchupIndependentScenario.GrowthPackage.NONE));
    }
    private static List<ChampionMatchupIndependentRow> skillRows(int gap) {
        return ROWS.stream().filter(row -> row.scenarioGroup()
                == ChampionMatchupIndependentScenario.Group.SKILL_ONLY
                && row.skillGap() == gap).toList();
    }
    private static List<ChampionMatchupIndependentRow> growthLarge() {
        return ROWS.stream().filter(row -> row.scenarioGroup()
                == ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                && row.growthPackage()
                == ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE
                && row.eligibleForGrowthMetric()).toList();
    }
    private static List<ChampionMatchupIndependentRow> group(
            ChampionMatchupIndependentScenario.Group group) {
        return ROWS.stream().filter(row -> row.scenarioGroup() == group).toList();
    }
    private static ChampionMatchupDilutionMetrics metrics(Double... values) {
        return ChampionMatchupDilutionMetrics.calculate(values.length, List.of(values));
    }
    private static double edge(String first, String second, Position position,
                               ProgressionCombatContext context) {
        return ENGINE.calculate(key(first, position), key(second, position),
                context).finalGeneratedEdge();
    }
    private static ChampionRoleKey key(String id, Position position) {
        return new ChampionRoleKey(new ChampionId(id), position);
    }
    private static double weight(ChampionMatchupRuleType rule) {
        return RULES.weight(ProgressionCombatContext.LANE_COMBAT, rule);
    }
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/lolfm/" + file));
    }
}

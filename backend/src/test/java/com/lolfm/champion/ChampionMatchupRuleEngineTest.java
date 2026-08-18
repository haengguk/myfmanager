package com.lolfm.champion;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChampionMatchupRuleEngineTest {
    private final ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
    private final ChampionRoleMatchupProfileCatalog profiles =
            ChampionRoleMatchupProfileCatalog.prototype();
    private final ChampionMatchupRuleCatalog rules = new ChampionMatchupRuleCatalog();
    private final ChampionMatchupRuleEngine engine = new ChampionMatchupRuleEngine(
            profiles, rules, ChampionMatchupOverrideCatalog.prototypeSemantic());

    @Test void roleProfileContainsAllFifteenTraits() {
        assertEquals(15, profiles.profiles().values().iterator().next().traits().size());
    }

    @Test void roleProfileRejectsMissingTrait() {
        assertThrows(IllegalArgumentException.class, () ->
                new ChampionRoleMatchupProfile(key("jax", Position.TOP), "v", Map.of()));
    }

    @Test void roleProfileRejectsOutOfRangeTrait() {
        EnumMap<ChampionMatchupTrait, Integer> traits = completeTraits(10);
        traits.put(ChampionMatchupTrait.POKE, 21);
        assertThrows(IllegalArgumentException.class, () ->
                new ChampionRoleMatchupProfile(key("jax", Position.TOP), "v", traits));
    }

    @Test void roleProfileIsImmutable() {
        EnumMap<ChampionMatchupTrait, Integer> traits = completeTraits(10);
        ChampionRoleMatchupProfile profile =
                new ChampionRoleMatchupProfile(key("jax", Position.TOP), "v", traits);
        traits.put(ChampionMatchupTrait.POKE, 20);
        assertEquals(10, profile.trait(ChampionMatchupTrait.POKE));
        assertThrows(UnsupportedOperationException.class, () ->
                profile.traits().put(ChampionMatchupTrait.POKE, 1));
    }

    @Test void sameChampionCanHaveDifferentPositionProfiles() {
        assertNotEquals(key("jax", Position.TOP), key("jax", Position.MID));
    }

    @Test void prototypeProfileCatalogContainsExactlyTenProfiles() {
        assertEquals(10, profiles.profiles().size());
    }

    @Test void productionProfileCatalogContainsEveryFullLegalRole() {
        assertEquals(212, ChampionRoleMatchupProfileCatalog.production().profiles().size());
    }

    @Test void everyContextWeightSumsToOne() {
        for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
            assertEquals(1.0, rules.weightSum(context), 1e-12);
        }
    }

    @Test void generatedEdgeIsAntisymmetric() {
        for (Focused pair : focused()) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                double forward = calculate(pair, context).finalGeneratedEdge();
                double reverse = engine.calculate(pair.right(), pair.left(), context)
                        .finalGeneratedEdge();
                assertEquals(forward, -reverse, 0.0);
            }
        }
    }

    @Test void everyRuleContributionIsFiniteAndExplanationSums() {
        for (Focused pair : focused()) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                ChampionMatchupGeneratedResult result = calculate(pair, context);
                assertEquals(7, result.ruleContributions().size());
                double sum = result.ruleContributions().stream()
                        .mapToDouble(ChampionMatchupRuleContribution::weightedContribution)
                        .sum();
                assertEquals(result.weightedRawEdge(), sum, 1e-12);
                result.ruleContributions().forEach(value ->
                        assertTrue(Double.isFinite(value.antisymmetricRuleEdge())));
            }
        }
    }

    @Test void negativeZeroIsNormalized() {
        var missing = engine.calculate(
                key("ornn", Position.TOP), key("gwen", Position.TOP),
                ProgressionCombatContext.LANE_COMBAT);
        assertEquals(Double.doubleToRawLongBits(0.0),
                Double.doubleToRawLongBits(missing.finalGeneratedEdge()));
    }

    @Test void generatedEdgeRespectsPrototypeCap() {
        for (Focused pair : focused()) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                assertTrue(Math.abs(calculate(pair, context).finalGeneratedEdge()) <= .30);
            }
        }
    }

    @Test void missingProfileFallsBackToNeutral() {
        var result = engine.calculate(key("ornn", Position.TOP), key("jax", Position.TOP),
                ProgressionCombatContext.LANE_COMBAT);
        assertTrue(result.neutralFallback());
        assertEquals(0.0, result.finalGeneratedEdge());
    }

    @Test void expectedDirectionConstraintsHold() {
        assertPositive("renekton", "jax", Position.TOP, ProgressionCombatContext.LANE_COMBAT);
        assertPositive("lee-sin", "viego", Position.JUNGLE, ProgressionCombatContext.JUNGLE_GANK);
        assertPositive("leblanc", "viktor", Position.MID, ProgressionCombatContext.LANE_COMBAT);
        assertPositive("leblanc", "viktor", Position.MID, ProgressionCombatContext.ROAM);
        assertSmallOrNonPositive("leblanc", "viktor", Position.MID,
                ProgressionCombatContext.LATE_GAME_SIEGE);
        assertPositive("lucian", "jinx", Position.ADC, ProgressionCombatContext.LANE_COMBAT);
        assertSmallOrNonPositive("lucian", "jinx", Position.ADC,
                ProgressionCombatContext.TEAMFIGHT);
        assertPositive("nautilus", "lulu", Position.SUPPORT,
                ProgressionCombatContext.JUNGLE_GANK);
        assertTrue(edge("nautilus", "lulu", Position.SUPPORT,
                ProgressionCombatContext.BASE_DEFENSE) < 0.0);
    }

    @Test void generatedPrototypeCatalogRemainsFrozenThirtyDiagnostics() {
        var build = GeneratedChampionMatchupCatalogFactory.prototype(
                HistoricalChampionCatalog.initialThirty());
        assertEquals(75, build.catalog().profiles().size());
        assertEquals(675, build.generatedResults().size());
        long both = build.generatedResults().entrySet().stream()
                .filter(entry -> entry.getValue().sourceProfileFound()
                        && entry.getValue().opponentProfileFound())
                .map(entry -> entry.getKey().pair()).distinct().count();
        assertEquals(5, both);
    }

    @Test void productionCatalogRemainsEntirelyNeutral() {
        assertTrue(ChampionMatchupCatalog.neutral(champions).profiles().values().stream()
                .flatMap(profile -> profile.firstChampionEdges().values().stream())
                .allMatch(value -> value == 0.0));
        assertEquals(0, ChampionMatchupOverrideCatalog.production().values().size());
    }

    @Test void syntheticOverrideIsAdditiveAndReverses() {
        ChampionMatchupPair pair = ChampionMatchupPair.of(
                champions.get(new ChampionId("renekton")),
                champions.get(new ChampionId("jax")));
        ChampionMatchupOverride override = new ChampionMatchupOverride(
                pair, Position.TOP, ProgressionCombatContext.LANE_COMBAT, .04,
                MatchupOverrideReason.DIAGNOSTIC_SYNTHETIC, "path test",
                ChampionMatchupOverrideCatalog.SYNTHETIC_VERSION);
        ChampionMatchupOverrideCatalog catalog = new ChampionMatchupOverrideCatalog(
                ChampionMatchupOverrideCatalog.SYNTHETIC_VERSION, false, List.of(override));
        double forward = catalog.adjustment(pair.first(), pair.second(), Position.TOP,
                ProgressionCombatContext.LANE_COMBAT);
        double reverse = catalog.adjustment(pair.second(), pair.first(), Position.TOP,
                ProgressionCombatContext.LANE_COMBAT);
        assertEquals(.04, forward);
        assertEquals(-forward, reverse);
    }

    private ChampionMatchupGeneratedResult calculate(
            Focused pair, ProgressionCombatContext context) {
        return engine.calculate(pair.left(), pair.right(), context);
    }

    private void assertPositive(String a, String b, Position p,
                                ProgressionCombatContext context) {
        assertTrue(edge(a, b, p, context) > 0.0,
                () -> a + "/" + b + "/" + context + "=" + edge(a, b, p, context));
    }

    private void assertSmallOrNonPositive(String a, String b, Position p,
                                          ProgressionCombatContext context) {
        double value = edge(a, b, p, context);
        assertTrue(value <= 0.0 || Math.abs(value) <= .03,
                () -> a + "/" + b + "/" + context + "=" + value);
    }

    private double edge(String a, String b, Position p,
                        ProgressionCombatContext context) {
        return engine.calculate(key(a, p), key(b, p), context).finalGeneratedEdge();
    }

    private static ChampionRoleKey key(String champion, Position position) {
        return new ChampionRoleKey(new ChampionId(champion), position);
    }

    private static EnumMap<ChampionMatchupTrait, Integer> completeTraits(int value) {
        EnumMap<ChampionMatchupTrait, Integer> result =
                new EnumMap<>(ChampionMatchupTrait.class);
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) result.put(trait, value);
        return result;
    }

    private static List<Focused> focused() {
        return List.of(
                new Focused(key("renekton", Position.TOP), key("jax", Position.TOP)),
                new Focused(key("lee-sin", Position.JUNGLE), key("viego", Position.JUNGLE)),
                new Focused(key("leblanc", Position.MID), key("viktor", Position.MID)),
                new Focused(key("lucian", Position.ADC), key("jinx", Position.ADC)),
                new Focused(key("nautilus", Position.SUPPORT), key("lulu", Position.SUPPORT)));
    }

    private record Focused(ChampionRoleKey left, ChampionRoleKey right) { }
}

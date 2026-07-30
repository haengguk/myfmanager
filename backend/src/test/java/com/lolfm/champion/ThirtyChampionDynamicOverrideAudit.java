package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;

public final class ThirtyChampionDynamicOverrideAudit {
    private static final double EPSILON = .01;
    private final ChampionMatchupRuleEngine matchup = new ChampionMatchupRuleEngine(
            ThirtyChampionRoleProfiles.catalog(), new ChampionMatchupRuleCatalog(),
            ChampionMatchupOverrideCatalog.production());
    private final DynamicCombatScoreEvaluator scores = new DynamicCombatScoreEvaluator(
            ChampionPowerProfileCatalog.loadDefault());
    private final ChampionMatchupAuditPlayerFactory players =
            new ChampionMatchupAuditPlayerFactory();

    public List<ChampionMatchupIndependentRow> generate(ChampionCatalog champions) {
        List<ChampionMatchupIndependentRow> rows = new ArrayList<>(32_400);
        for (var position : com.lolfm.domain.Position.values()) {
            List<ChampionDefinition> pool = champions.forPosition(position);
            for (int left = 0; left < pool.size(); left++) {
                for (int right = left + 1; right < pool.size(); right++) {
                    for (ProgressionCombatContext context :
                            ProgressionCombatContext.values()) {
                        for (ChampionMatchupAuditPlayerFactory.AuditState state :
                                ChampionMatchupAuditPlayerFactory.AuditState.values()) {
                            for (boolean reverse : List.of(false, true)) {
                                for (Scenario scenario : Scenario.values()) {
                                    rows.add(row(pool.get(left).id(), pool.get(right).id(),
                                            position, context, state, reverse, scenario));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (rows.size() != 32_400) {
            throw new IllegalStateException("Expected 32,400 rows: " + rows.size());
        }
        return List.copyOf(rows);
    }

    private ChampionMatchupIndependentRow row(
            ChampionId first, ChampionId second,
            com.lolfm.domain.Position position,
            ProgressionCombatContext context,
            ChampionMatchupAuditPlayerFactory.AuditState state,
            boolean reverse, Scenario scenario
    ) {
        ChampionId source = reverse ? second : first;
        ChampionId opponent = reverse ? first : second;
        ChampionMatchupGeneratedResult generated = matchup.calculate(
                new ChampionRoleKey(source, position),
                new ChampionRoleKey(opponent, position), context);
        boolean sourceFavored = generated.finalGeneratedEdge() >= 0;
        ChampionId favoredId = sourceFavored ? source : opponent;
        ChampionId challengerId = sourceFavored ? opponent : source;
        var favored = players.create(position, state, 0);
        var challenger = players.create(position, state, scenario.skillGap);
        var growth = players.applyGrowth(challenger, scenario.growth);
        DynamicCombatScoreBreakdown favoredScore =
                scores.evaluate(favored.player(), favoredId, context);
        DynamicCombatScoreBreakdown challengerScore =
                scores.evaluate(challenger.player(), challengerId, context);
        double attributes = edge(favoredScore.playerAttributeContribution(),
                challengerScore.playerAttributeContribution());
        double gold = edge(favoredScore.currentGoldContribution(),
                challengerScore.currentGoldContribution());
        double commonLevel = edge(favoredScore.commonLevelContribution(),
                challengerScore.commonLevelContribution());
        double commonItem = edge(favoredScore.commonItemContribution(),
                challengerScore.commonItemContribution());
        double common = commonLevel + commonItem;
        double championLevel = edge(favoredScore.championLevelContribution(),
                challengerScore.championLevelContribution());
        double championItem = edge(favoredScore.championItemContribution(),
                challengerScore.championItemContribution());
        double championContext = edge(favoredScore.championContextContribution(),
                challengerScore.championContextContribution());
        double championPower = championLevel + championItem + championContext;
        double before = attributes + gold + common;
        double afterChampion = before + championPower;
        double finalMatchup = Math.abs(generated.finalGeneratedEdge());
        double afterMatchup = afterChampion + finalMatchup;
        boolean skill = scenario.skillGap > 0;
        boolean growthScenario = scenario.growth
                != ChampionMatchupIndependentScenario.GrowthPackage.NONE;
        boolean highSkill = scenario.skillGap == 5;
        boolean highGrowth = scenario.growth
                == ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE
                && growth.eligibleForRequestedPackageRate();
        return new ChampionMatchupIndependentRow(
                first.value() + "/" + second.value(), position, context, state.name(),
                reverse ? "REVERSE" : "CANONICAL_FORWARD",
                skill ? ChampionMatchupIndependentScenario.Group.SKILL_ONLY
                        : growthScenario
                        ? ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                        : ChampionMatchupIndependentScenario.Group.SKILL_ONLY,
                scenario.skillGap, scenario.growth, favoredId, challengerId,
                growth.requestedKillLead(), growth.achievedKillLead(),
                growth.requestedLevelLead(), growth.achievedLevelLead(),
                growth.requestedItemStageLead(), growth.achievedItemStageLead(),
                growth.leadCapped(), growth.capReason(),
                growth.eligibleForRequestedPackageRate(),
                attributes, 0, gold, commonLevel, commonItem, common, before,
                championLevel, championItem, championContext, championPower,
                afterChampion, Math.abs(generated.generatedBaseEdge()),
                Math.abs(generated.overrideAdjustment()), finalMatchup, afterMatchup,
                ordering(before), ordering(afterChampion), ordering(afterMatchup),
                changed(before, afterChampion), changed(afterChampion, afterMatchup),
                skill && afterMatchup < -EPSILON,
                growthScenario && growth.eligibleForRequestedPackageRate()
                        && afterMatchup < -EPSILON,
                skill && afterChampion < -EPSILON && afterMatchup > EPSILON,
                growthScenario && afterChampion < -EPSILON && afterMatchup > EPSILON,
                championPower > EPSILON && afterChampion < -EPSILON,
                highSkill && before >= -EPSILON,
                highGrowth && before >= -EPSILON,
                (highSkill || highGrowth) && before < -EPSILON
                        && afterChampion > EPSILON,
                (highSkill || highGrowth) && afterChampion < -EPSILON
                        && afterMatchup > EPSILON);
    }

    private static double edge(double favored, double challenger) {
        double value = favored - challenger;
        return value == 0 ? 0 : value;
    }
    private static String ordering(double edge) {
        return Math.abs(edge) <= EPSILON ? "TIE"
                : edge > 0 ? "FAVORED" : "CHALLENGER";
    }
    private static boolean changed(double before, double after) {
        return before < -EPSILON && after > EPSILON
                || before > EPSILON && after < -EPSILON;
    }

    private enum Scenario {
        BASELINE(0, ChampionMatchupIndependentScenario.GrowthPackage.NONE),
        SKILL_PLUS_1(1, ChampionMatchupIndependentScenario.GrowthPackage.NONE),
        SKILL_PLUS_3(3, ChampionMatchupIndependentScenario.GrowthPackage.NONE),
        SKILL_PLUS_5(5, ChampionMatchupIndependentScenario.GrowthPackage.NONE),
        GROWTH_COMBINED_SMALL(0,
                ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_SMALL),
        GROWTH_COMBINED_LARGE(0,
                ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE);
        private final int skillGap;
        private final ChampionMatchupIndependentScenario.GrowthPackage growth;
        Scenario(int skillGap,
                 ChampionMatchupIndependentScenario.GrowthPackage growth) {
            this.skillGap = skillGap;
            this.growth = growth;
        }
    }
}

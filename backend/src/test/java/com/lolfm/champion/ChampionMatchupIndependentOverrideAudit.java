package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.List;

public final class ChampionMatchupIndependentOverrideAudit {
    private static final double EPSILON = .01;
    private final ChampionMatchupRuleEngine matchup = new ChampionMatchupRuleEngine(
            ChampionRoleMatchupProfileCatalog.prototype(),
            new ChampionMatchupRuleCatalog(),
            ChampionMatchupOverrideCatalog.prototypeSemantic());
    private final DynamicCombatScoreEvaluator scores = new DynamicCombatScoreEvaluator(
            ChampionPowerProfileCatalog.loadDefault());
    private final ChampionMatchupAuditPlayerFactory players =
            new ChampionMatchupAuditPlayerFactory();

    public List<ChampionMatchupIndependentRow> generate() {
        List<ChampionMatchupIndependentRow> rows = new ArrayList<>(6_480);
        for (FocusedPair pair : FocusedPair.values()) {
            for (ProgressionCombatContext context :
                    ProgressionCombatContext.values()) {
                for (ChampionMatchupAuditPlayerFactory.AuditState state :
                        ChampionMatchupAuditPlayerFactory.AuditState.values()) {
                    for (boolean reverse : List.of(false, true)) {
                        for (ChampionMatchupIndependentScenario scenario :
                                ChampionMatchupIndependentScenario.all()) {
                            rows.add(row(pair, context, state, reverse, scenario));
                        }
                    }
                }
            }
        }
        if (rows.size() != 6_480) {
            throw new IllegalStateException(
                    "Expected 6,480 independent rows: " + rows.size());
        }
        return List.copyOf(rows);
    }

    private ChampionMatchupIndependentRow row(
            FocusedPair pair,
            ProgressionCombatContext context,
            ChampionMatchupAuditPlayerFactory.AuditState state,
            boolean reverse,
            ChampionMatchupIndependentScenario scenario
    ) {
        ChampionId source = reverse ? pair.second : pair.first;
        ChampionId opponent = reverse ? pair.first : pair.second;
        ChampionMatchupGeneratedResult generated = matchup.calculate(
                new ChampionRoleKey(source, pair.position),
                new ChampionRoleKey(opponent, pair.position), context);
        boolean sourceFavored = generated.finalGeneratedEdge() >= 0.0;
        ChampionId favoredId = sourceFavored ? source : opponent;
        ChampionId challengerId = sourceFavored ? opponent : source;
        var favored = players.create(pair.position, state, 0);
        var challenger = players.create(pair.position, state, scenario.skillGap());
        var growth = players.applyGrowth(challenger, scenario.growthPackage());
        DynamicCombatScoreBreakdown favoredScore = scores.evaluate(
                favored.player(), favoredId, context);
        DynamicCombatScoreBreakdown challengerScore = scores.evaluate(
                challenger.player(), challengerId, context);
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
        double beforeChampion = attributes + gold + common;
        double afterChampion = beforeChampion + championPower;
        double baseMatchup = Math.abs(generated.generatedBaseEdge());
        double override = Math.abs(generated.overrideAdjustment());
        double finalMatchup = Math.abs(generated.finalGeneratedEdge());
        double afterMatchup = afterChampion + finalMatchup;
        boolean highSkill = scenario.group()
                == ChampionMatchupIndependentScenario.Group.SKILL_ONLY
                && scenario.skillGap() == 5
                && scenario.growthPackage()
                == ChampionMatchupIndependentScenario.GrowthPackage.NONE;
        boolean highGrowth = scenario.group()
                == ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                && scenario.skillGap() == 0
                && scenario.growthPackage()
                == ChampionMatchupIndependentScenario.GrowthPackage.COMBINED_LEAD_LARGE
                && growth.eligibleForRequestedPackageRate();
        boolean skillScenario = scenario.group()
                == ChampionMatchupIndependentScenario.Group.SKILL_ONLY
                && scenario.skillGap() > 0;
        boolean growthScenario = scenario.group()
                == ChampionMatchupIndependentScenario.Group.GROWTH_ONLY
                && scenario.growthPackage()
                != ChampionMatchupIndependentScenario.GrowthPackage.NONE;
        return new ChampionMatchupIndependentRow(
                pair.name(), pair.position, context, state.name(),
                reverse ? "REVERSE" : "CANONICAL_FORWARD",
                scenario.group(), scenario.skillGap(), scenario.growthPackage(),
                favoredId, challengerId,
                growth.requestedKillLead(), growth.achievedKillLead(),
                growth.requestedLevelLead(), growth.achievedLevelLead(),
                growth.requestedItemStageLead(), growth.achievedItemStageLead(),
                growth.leadCapped(), growth.capReason(),
                growth.eligibleForRequestedPackageRate(),
                attributes, 0.0, gold, commonLevel, commonItem, common,
                beforeChampion, championLevel, championItem, championContext,
                championPower, afterChampion, baseMatchup, override, finalMatchup,
                afterMatchup, ordering(beforeChampion), ordering(afterChampion),
                ordering(afterMatchup),
                changedWinner(beforeChampion, afterChampion),
                changedWinner(afterChampion, afterMatchup),
                skillScenario && afterMatchup < -EPSILON,
                growthScenario && growth.eligibleForRequestedPackageRate()
                        && afterMatchup < -EPSILON,
                skillScenario && afterChampion < -EPSILON
                        && afterMatchup > EPSILON,
                growthScenario && afterChampion < -EPSILON
                        && afterMatchup > EPSILON,
                championPower > EPSILON && afterChampion < -EPSILON,
                highSkill && beforeChampion >= -EPSILON,
                highGrowth && beforeChampion >= -EPSILON,
                (highSkill || highGrowth) && beforeChampion < -EPSILON
                        && afterChampion > EPSILON,
                (highSkill || highGrowth) && afterChampion < -EPSILON
                        && afterMatchup > EPSILON);
    }

    private static double edge(double favored, double challenger) {
        double value = favored - challenger;
        return value == 0.0 ? 0.0 : value;
    }

    private static String ordering(double edge) {
        return Math.abs(edge) <= EPSILON ? "TIE"
                : edge > 0.0 ? "FAVORED" : "CHALLENGER";
    }

    private static boolean changedWinner(double before, double after) {
        return before < -EPSILON && after > EPSILON
                || before > EPSILON && after < -EPSILON;
    }

    private enum FocusedPair {
        RENEKTON_JAX(Position.TOP, "renekton", "jax"),
        LEE_SIN_VIEGO(Position.JUNGLE, "lee-sin", "viego"),
        LEBLANC_VIKTOR(Position.MID, "leblanc", "viktor"),
        LUCIAN_JINX(Position.ADC, "lucian", "jinx"),
        NAUTILUS_LULU(Position.SUPPORT, "nautilus", "lulu");

        private final Position position;
        private final ChampionId first;
        private final ChampionId second;

        FocusedPair(Position position, String first, String second) {
            this.position = position;
            this.first = new ChampionId(first);
            this.second = new ChampionId(second);
        }
    }
}

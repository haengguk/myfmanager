package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionMatchupTrait;
import com.lolfm.champion.ChampionPowerProfileCatalog;
import com.lolfm.champion.ChampionPowerProfileEvaluator;
import com.lolfm.champion.ThirtyChampionGeneratedCatalog;
import com.lolfm.champion.ThirtyChampionRoleProfiles;
import java.util.ArrayList;
import java.util.List;

final class ThirtyChampionPowerDuplicationAudit {
    private ThirtyChampionPowerDuplicationAudit() {
    }

    static List<Row> evaluate(ThirtyChampionGeneratedCatalog.BuildResult build) {
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        ChampionPowerProfileEvaluator power = new ChampionPowerProfileEvaluator(
                new ChampionPowerProfileCatalog(new ObjectMapper(), champions));
        List<Row> result = new ArrayList<>();
        for (ProgressionCombatContext context :
                ProgressionCombatContext.values()) {
            List<Double> matchup = new ArrayList<>();
            List<Double> early = new ArrayList<>();
            List<Double> mid = new ArrayList<>();
            List<Double> late = new ArrayList<>();
            List<Double> traits = new ArrayList<>();
            List<ChampionId> ids = new ArrayList<>();
            for (var entry : ThirtyChampionRoleProfiles.entries()) {
                ChampionId id = entry.profile().roleKey().championId();
                var relevant = build.rows().stream().filter(row ->
                        row.position() == entry.profile().roleKey().position()
                                && row.context() == context
                                && (row.canonicalFirstChampion().equals(id.value())
                                || row.canonicalSecondChampion().equals(id.value())))
                        .toList();
                double mean = relevant.stream().mapToDouble(row ->
                        row.canonicalFirstChampion().equals(id.value())
                                ? row.generatedBaseEdge() : -row.generatedBaseEdge())
                        .average().orElseThrow();
                matchup.add(mean);
                early.add(power.evaluate(id, 6, ItemProgressStage.FIRST_CORE,
                        context).clampedPlayerChampionPower());
                mid.add(power.evaluate(id, 11, ItemProgressStage.SECOND_CORE,
                        context).clampedPlayerChampionPower());
                late.add(power.evaluate(id, 18, ItemProgressStage.FULL_BUILD,
                        context).clampedPlayerChampionPower());
                traits.add(entry.profile().traits().values().stream()
                        .mapToInt(Integer::intValue).average().orElseThrow());
                ids.add(id);
            }
            result.add(new Row(context, ids.size(),
                    ThirtyChampionStatistics.pearson(matchup, early),
                    ThirtyChampionStatistics.spearman(matchup, early),
                    ThirtyChampionStatistics.pearson(matchup, mid),
                    ThirtyChampionStatistics.spearman(matchup, mid),
                    ThirtyChampionStatistics.pearson(matchup, late),
                    ThirtyChampionStatistics.spearman(matchup, late),
                    ThirtyChampionStatistics.pearson(matchup, traits),
                    ThirtyChampionStatistics.spearman(matchup, traits),
                    warning(matchup, early, mid, late)));
        }
        return List.copyOf(result);
    }

    private static String warning(List<Double> matchup, List<Double> early,
                                  List<Double> mid, List<Double> late) {
        long high = List.of(early, mid, late).stream().filter(values ->
                Math.abs(ThirtyChampionStatistics.pearson(matchup, values)) > .8
                || Math.abs(ThirtyChampionStatistics.spearman(matchup, values)) > .8)
                .count();
        return high >= 3 ? "ABSOLUTE_POWER_DUPLICATION_REVIEW" : "NONE";
    }

    record Row(ProgressionCombatContext context, int championCount,
               double earlyPearson, double earlySpearman,
               double midPearson, double midSpearman,
               double latePearson, double lateSpearman,
               double traitMeanPearson, double traitMeanSpearman,
               String warning) {
    }
}

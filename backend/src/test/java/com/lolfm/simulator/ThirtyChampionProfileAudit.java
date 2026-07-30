package com.lolfm.simulator;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupTrait;
import com.lolfm.champion.ThirtyChampionRoleProfiles;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ThirtyChampionProfileAudit {
    private ThirtyChampionProfileAudit() {
    }

    static Result evaluate(ChampionCatalog champions) {
        List<ThirtyChampionRoleProfiles.Entry> entries =
                ThirtyChampionRoleProfiles.entries();
        List<ProfileRow> profiles = entries.stream().map(entry -> {
            var profile = entry.profile();
            List<Double> values = profile.traits().values().stream()
                    .map(Number::doubleValue).toList();
            double mean = values.stream().mapToDouble(Double::doubleValue)
                    .average().orElseThrow();
            long elite = values.stream().filter(value -> value >= 17).count();
            long minimum = values.stream().filter(value -> value <= 4).count();
            long middle = values.stream().filter(value ->
                    value >= 9 && value <= 12).count();
            long atMostEight = values.stream().filter(value -> value <= 8).count();
            long atLeastThirteen = values.stream().filter(value -> value >= 13).count();
            List<String> warnings = new ArrayList<>();
            if (mean > 15) warnings.add("PROFILE_ALL_HIGH_WARNING");
            if (mean < 6) warnings.add("PROFILE_ALL_LOW_WARNING");
            if (elite >= 8) warnings.add("PROFILE_EXCESSIVE_ELITE_TRAITS");
            if (minimum >= 8) warnings.add("PROFILE_EXCESSIVE_MINIMUM_TRAITS");
            if (ThirtyChampionStatistics.standardDeviation(values) < 2 && middle >= 10) {
                warnings.add("PROFILE_FLAT_WARNING");
            }
            if (atMostEight == 0) warnings.add("PROFILE_NO_CLEAR_WEAKNESS");
            if (atLeastThirteen == 0) warnings.add("PROFILE_NO_CLEAR_STRENGTH");
            return new ProfileRow(profile.roleKey().championId().value(),
                    profile.roleKey().position(), values.stream()
                    .mapToInt(Double::intValue).sum(), mean,
                    ThirtyChampionStatistics.quantile(values, .5),
                    values.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                    values.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
                    ThirtyChampionStatistics.standardDeviation(values),
                    elite, minimum, middle, String.join("|", warnings));
        }).toList();
        List<PositionTraitRow> positionTraits = new ArrayList<>();
        for (Position position : Position.values()) {
            for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
                var positioned = entries.stream().filter(entry ->
                        entry.profile().roleKey().position() == position).toList();
                List<Double> values = positioned.stream().map(entry ->
                        (double) entry.profile().trait(trait)).toList();
                double deviation = ThirtyChampionStatistics.standardDeviation(values);
                String highest = positioned.stream().max(Comparator.comparingInt(
                        entry -> entry.profile().trait(trait))).orElseThrow()
                        .profile().roleKey().championId().value();
                String lowest = positioned.stream().min(Comparator.comparingInt(
                        entry -> entry.profile().trait(trait))).orElseThrow()
                        .profile().roleKey().championId().value();
                String warning = deviation == 0 ? "POSITION_TRAIT_NO_VARIANCE"
                        : deviation < 1 ? "POSITION_TRAIT_LOW_VARIANCE" : "NONE";
                double mean = values.stream().mapToDouble(Double::doubleValue)
                        .average().orElseThrow();
                long outliers = values.stream().filter(value ->
                        Math.abs(value - mean) >= 2.5 * deviation && deviation > 0).count();
                if (outliers > 0) warning += "|POSITION_TRAIT_EXTREME_OUTLIER";
                positionTraits.add(new PositionTraitRow(position, trait,
                        values.size(), mean,
                        ThirtyChampionStatistics.quantile(values, .5),
                        values.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
                        values.stream().mapToDouble(Double::doubleValue).max().orElseThrow(),
                        deviation, values.stream().distinct().count(),
                        highest, lowest, warning));
            }
        }
        long catalogKeys = champions.all().stream().filter(definition ->
                entries.stream().anyMatch(entry ->
                        entry.profile().roleKey().championId().equals(definition.id())
                        && entry.profile().roleKey().position()
                        == definition.primaryPosition())).count();
        return new Result(entries, profiles, positionTraits, catalogKeys);
    }

    record Result(List<ThirtyChampionRoleProfiles.Entry> entries,
                  List<ProfileRow> profileRows,
                  List<PositionTraitRow> positionTraitRows,
                  long catalogKeyCount) {
        Result {
            entries = List.copyOf(entries);
            profileRows = List.copyOf(profileRows);
            positionTraitRows = List.copyOf(positionTraitRows);
        }
    }

    record ProfileRow(String champion, Position position, int traitSum,
                      double traitMean, double traitMedian, double traitMin,
                      double traitMax, double traitStandardDeviation,
                      long countAtLeast17, long countAtMost4,
                      long countBetween9And12, String warnings) {
    }

    record PositionTraitRow(Position position, ChampionMatchupTrait trait,
                            int championCount, double mean, double median,
                            double min, double max, double standardDeviation,
                            long uniqueValueCount, String highestChampion,
                            String lowestChampion, String warnings) {
    }
}

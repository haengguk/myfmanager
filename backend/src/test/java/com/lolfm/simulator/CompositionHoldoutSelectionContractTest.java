package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CompositionHoldoutSelectionContractTest {
    @Test
    void boundedSelectionIsDeterministicLegalAndHonorsExclusions() {
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> canonical = fixtureLineups();
        Set<String> excluded = Set.of("lineup-2");

        var first = CompositionFreshHoldoutCandidateGameplayAudit.selectHoldoutForContract(
                canonical, excluded, 3, 1);
        var replay = CompositionFreshHoldoutCandidateGameplayAudit.selectHoldoutForContract(
                canonical, excluded, 3, 1);

        assertThat(first).containsExactlyElementsOf(replay).hasSize(3);
        assertThat(first).extracting(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id)
                .doesNotContainAnyElementsOf(excluded)
                .doesNotHaveDuplicates();
        assertThat(first).allSatisfy(lineup -> {
            assertThat(lineup.champions())
                    .containsOnlyKeys(CompositionFreshHoldoutCandidateGameplayAudit.POSITIONS);
            assertThat(lineup.champions().values()).doesNotHaveDuplicates();
            assertThat(lineup.metrics()).isNotEmpty();
        });
    }

    @Test
    void orderedScheduleCreatesExactMirrorsWithOneSeedPerPair() {
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> lineups = fixtureLineups();
        var firstPair = new CompositionFreshHoldoutCandidateGameplayAudit.UnorderedPair(
                0, 1, lineups.get(0), lineups.get(1), "hash-1");
        var secondPair = new CompositionFreshHoldoutCandidateGameplayAudit.UnorderedPair(
                2, 3, lineups.get(2), lineups.get(3), "hash-2");

        var schedule = CompositionFreshHoldoutCandidateGameplayAudit.orderedSchedule(
                List.of(firstPair, secondPair));

        assertThat(schedule).hasSize(4);
        for (int index = 0; index < schedule.size(); index += 2) {
            var original = schedule.get(index);
            var mirror = schedule.get(index + 1);
            assertThat(original.seed()).isEqualTo(mirror.seed());
            assertThat(original.groupIndex()).isEqualTo(mirror.groupIndex());
            assertThat(original.blue()).isEqualTo(mirror.red());
            assertThat(original.red()).isEqualTo(mirror.blue());
            assertThat(original.orientation()).isZero();
            assertThat(mirror.orientation()).isEqualTo(1);
        }
    }

    private static List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> fixtureLineups() {
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> result = new ArrayList<>();
        for (int lineup = 0; lineup < 6; lineup++) {
            int fixtureIndex = lineup;
            EnumMap<Position, ChampionId> champions = new EnumMap<>(Position.class);
            int positionIndex = 0;
            for (Position position : CompositionFreshHoldoutCandidateGameplayAudit.POSITIONS) {
                champions.put(position, new ChampionId(
                        "contract-" + lineup + "-" + positionIndex++));
            }
            Map<String, Double> metrics = new HashMap<>();
            CompositionFreshHoldoutCandidateGameplayAudit.PATTERNS.forEach(pattern ->
                    metrics.put(pattern, fixtureIndex % 2 == 0 ? 0.8 : 0.6));
            CompositionFreshHoldoutCandidateGameplayAudit.CAPS.forEach(capability ->
                    metrics.put(capability, fixtureIndex / 10.0));
            result.add(new CompositionFreshHoldoutCandidateGameplayAudit.Lineup(
                    "lineup-" + lineup, champions, metrics));
        }
        return List.copyOf(result);
    }
}

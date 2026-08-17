package com.lolfm.simulator;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositionFreshHoldoutCandidateGameplayAuditTest {
    private List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> canonical;
    private List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> pool;
    private List<CompositionFreshHoldoutCandidateGameplayAudit.UnorderedPair> pairs;
    private List<CompositionFreshHoldoutCandidateGameplayAudit.ScheduleRow> schedule;
    private Set<String> excluded;
    private Set<String> priorPairs;

    @BeforeAll
    void preparePureFixtures() throws IOException {
        canonical = CompositionFreshHoldoutCandidateGameplayAudit.readCanonical();
        Set<String> representatives = CompositionFreshHoldoutCandidateGameplayAudit.readSingleColumnIds(
                CompositionFreshHoldoutCandidateGameplayAudit.REPRESENTATIVES, "lineupId");
        excluded = new HashSet<>(representatives);
        excluded.addAll(CompositionFreshHoldoutCandidateGameplayAudit.readScheduleLineups(
                CompositionFreshHoldoutCandidateGameplayAudit.PRIOR_4A));
        excluded.addAll(CompositionFreshHoldoutCandidateGameplayAudit.readScheduleLineups(
                CompositionFreshHoldoutCandidateGameplayAudit.PRIOR_4C));
        priorPairs = CompositionFreshHoldoutCandidateGameplayAudit.readPriorPairs(
                CompositionFreshHoldoutCandidateGameplayAudit.PRIOR_4A,
                CompositionFreshHoldoutCandidateGameplayAudit.PRIOR_4C);
        pool = CompositionFreshHoldoutCandidateGameplayAudit.selectHoldout(canonical, excluded);
        pairs = CompositionFreshHoldoutCandidateGameplayAudit.selectPairs(pool, priorPairs);
        schedule = CompositionFreshHoldoutCandidateGameplayAudit.orderedSchedule(pairs);
    }

    @Test
    void canonicalLegalLineupCountIsSevenThousandSevenHundredSeventySix() {
        assertThat(canonical).hasSize(7_776);
    }

    @Test
    void holdoutPoolExcludesRepresentativeSixty() throws IOException {
        Set<String> representatives = CompositionFreshHoldoutCandidateGameplayAudit.readSingleColumnIds(
                CompositionFreshHoldoutCandidateGameplayAudit.REPRESENTATIVES, "lineupId");
        assertThat(pool).extracting(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id)
                .doesNotContainAnyElementsOf(representatives);
    }

    @Test
    void holdoutPoolContainsExactlyTwoHundredFortyLineups() {
        assertThat(pool).hasSize(240);
    }

    @Test
    void holdoutPoolHasNoDuplicateLineup() {
        assertThat(pool).extracting(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void holdoutPoolUsesOnlyLegalRoleAssignments() {
        assertThat(pool).allSatisfy(lineup -> {
            assertThat(lineup.champions()).containsOnlyKeys(CompositionFreshHoldoutCandidateGameplayAudit.POSITIONS);
            assertThat(lineup.champions().values()).doesNotHaveDuplicates();
            for (Position position : CompositionFreshHoldoutCandidateGameplayAudit.POSITIONS) {
                assertThat(lineup.champions().get(position)).isNotNull();
            }
        });
    }

    @Test
    void holdoutPoolRepresentsAllThirtyChampions() {
        Set<ChampionId> champions = pool.stream()
                .flatMap(lineup -> lineup.champions().values().stream())
                .collect(Collectors.toSet());
        assertThat(champions).hasSize(30);
    }

    @Test
    void holdoutPoolMeetsPatternCoverage() {
        assertThat(CompositionFreshHoldoutCandidateGameplayAudit.PATTERNS)
                .allSatisfy(pattern -> assertThat(pool.stream()
                        .filter(lineup -> lineup.metric(pattern) >= .70).count()).isGreaterThanOrEqualTo(20));
    }

    @Test
    void holdoutPoolSelectionIsDeterministic() {
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> second =
                CompositionFreshHoldoutCandidateGameplayAudit.selectHoldout(canonical, excluded);
        assertThat(second).extracting(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id)
                .containsExactlyElementsOf(pool.stream()
                        .map(CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id).toList());
    }

    @Test
    void holdoutPoolSelectionUsesNoGameplayOutcome() {
        assertThat(pool).allSatisfy(lineup -> assertThat(lineup.metrics()).isNotEmpty());
        assertThat(CompositionFreshHoldoutCandidateGameplayAudit.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("selectHoldout"))
                .hasSize(1);
    }

    @Test
    void holdoutPoolSelectionUsesNoRandom() {
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> first =
                CompositionFreshHoldoutCandidateGameplayAudit.selectHoldout(canonical, excluded);
        List<CompositionFreshHoldoutCandidateGameplayAudit.Lineup> second =
                CompositionFreshHoldoutCandidateGameplayAudit.selectHoldout(canonical, excluded);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void unorderedScheduleContainsExactlyOneThousandPairs() {
        assertThat(pairs).hasSize(1_000);
    }

    @Test
    void orderedScheduleContainsExactlyTwoThousandCases() {
        assertThat(schedule).hasSize(2_000);
    }

    @Test
    void everyUnorderedPairHasBothOrientations() {
        assertThat(schedule).allSatisfy(row -> assertThat(row.orientation()).isIn(0, 1));
        assertThat(schedule.stream().collect(Collectors.groupingBy(
                CompositionFreshHoldoutCandidateGameplayAudit.ScheduleRow::pairHash)).values())
                .allSatisfy(rows -> assertThat(rows).hasSize(2));
    }

    @Test
    void mirroredOrientationsUseSameSeed() {
        for (int i = 0; i < schedule.size(); i += 2) {
            assertThat(schedule.get(i).seed()).isEqualTo(schedule.get(i + 1).seed());
            assertThat(schedule.get(i).groupIndex()).isEqualTo(schedule.get(i + 1).groupIndex());
        }
    }

    @Test
    void scheduleHasNoPreviousPairOverlap() {
        assertThat(schedule).allSatisfy(row ->
                assertThat(priorPairs).doesNotContain(normalized(row.blue().id(), row.red().id())));
    }

    @Test
    void scheduleHasNoSelfPair() {
        assertThat(schedule).allSatisfy(row -> assertThat(row.blue().id()).isNotEqualTo(row.red().id()));
    }

    @Test
    void scheduleHasNoDuplicateUnorderedPair() {
        assertThat(schedule.stream()
                .map(row -> normalized(row.blue().id(), row.red().id()))
                .distinct().count()).isEqualTo(1_000);
    }

    @Test
    void scheduleHasNoCrossTeamChampionOverlap() {
        assertThat(pairs).allSatisfy(pair -> {
            Set<ChampionId> left = new HashSet<>(pair.left().champions().values());
            left.retainAll(pair.right().champions().values());
            assertThat(left).isEmpty();
        });
    }

    @Test
    void everyHoldoutLineupAppearsOnBothSides() {
        Map<String, Long> blue = schedule.stream().collect(Collectors.groupingBy(
                row -> row.blue().id(), Collectors.counting()));
        Map<String, Long> red = schedule.stream().collect(Collectors.groupingBy(
                row -> row.red().id(), Collectors.counting()));
        assertThat(pool).allSatisfy(lineup -> {
            assertThat(blue.getOrDefault(lineup.id(), 0L)).isGreaterThan(0);
            assertThat(red.getOrDefault(lineup.id(), 0L)).isGreaterThan(0);
        });
    }

    @Test
    void blueAndRedAppearanceCountsMatchPerLineup() {
        Map<String, Long> blue = schedule.stream().collect(Collectors.groupingBy(
                row -> row.blue().id(), Collectors.counting()));
        Map<String, Long> red = schedule.stream().collect(Collectors.groupingBy(
                row -> row.red().id(), Collectors.counting()));
        assertThat(pool).allSatisfy(lineup ->
                assertThat(blue.getOrDefault(lineup.id(), 0L))
                        .isEqualTo(red.getOrDefault(lineup.id(), 0L)));
    }

    @Test
    void zeroAppearanceLineupIsIncludedInMinimumCalculation() {
        Map<String, Long> appearances = new HashMap<>();
        pool.forEach(lineup -> appearances.put(lineup.id(), 0L));
        schedule.forEach(row -> {
            appearances.merge(row.blue().id(), 1L, Long::sum);
            appearances.merge(row.red().id(), 1L, Long::sum);
        });
        assertThat(appearances).hasSize(pool.size());
        assertThat(appearances.values()).allMatch(count -> count > 0);
    }

    @Test
    void scheduleSelectionIsDeterministic() {
        List<CompositionFreshHoldoutCandidateGameplayAudit.UnorderedPair> second =
                CompositionFreshHoldoutCandidateGameplayAudit.selectPairs(pool, priorPairs);
        assertThat(second).isEqualTo(pairs);
        assertThat(CompositionFreshHoldoutCandidateGameplayAudit.orderedSchedule(second))
                .isEqualTo(schedule);
    }

    
    void legalUnorderedPairGraphCountIncludesAllFreshEdges() {
        assertThat(CompositionFreshHoldoutCandidateGameplayAudit.legalUnorderedPairCount(pool)).isEqualTo(11_870);
    }

    private static String normalized(String first, String second) {
        return first.compareTo(second) < 0 ? first + "|" + second : second + "|" + first;
    }
}

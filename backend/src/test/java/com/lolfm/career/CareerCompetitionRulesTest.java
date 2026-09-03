package com.lolfm.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CareerCompetitionRulesTest {
    private static final String CAREER = "career_" + "1".repeat(64);
    private static final String INPUT = "2".repeat(64);
    private final CareerCompetitionRules rules = new CareerCompetitionRules(
            new ObjectMapper().findAndRegisterModules());

    @Test
    void roadToMsiAndPlayInUseOnlyStructuredOrderedWinnerLoserEdges() {
        CareerCompetitionAggregate road = CareerCompetitionAggregate.materialize(
                rules, CAREER, 2027, "LCK_ROAD_TO_MSI", "T01", 41L,
                INPUT, ranking());
        assertThat(road.fixtures()).hasSize(5);
        assertThat(match(road, "M1")).extracting(
                CareerCompetitionAggregate.Fixture::firstTeamCode,
                CareerCompetitionAggregate.Fixture::secondTeamCode,
                CareerCompetitionAggregate.Fixture::seriesFormat,
                CareerCompetitionAggregate.Fixture::hardFearless)
                .containsExactly("T05", "T06", "BO5", true);
        assertThat(match(road, "M2").secondTeamCode()).isNull();
        assertThat(match(road, "M3").date()).isEqualTo(LocalDate.of(2027, 6, 12));

        road = complete(road, "M1", "T05", 1);
        assertThat(match(road, "M2").secondTeamCode()).isEqualTo("T05");
        road = complete(road, "M2", "T04", 2);
        assertThat(match(road, "M4").secondTeamCode()).isEqualTo("T04");
        road = complete(road, "M3", "T01", 3);
        assertThat(road.qualificationOutputs()).containsEntry("MSI_LCK_SEED_1", "T01");
        assertThat(match(road, "M5").firstTeamCode()).isEqualTo("T02");
        road = complete(road, "M4", "T03", 4);
        assertThat(match(road, "M5").secondTeamCode()).isEqualTo("T03");
        CareerCompetitionAggregate beforeFinal = road;
        CareerCompetitionAggregate.CompletionResult finalResult = apply(
                road, "M5", "T02", 5);
        road = finalResult.aggregate();
        assertThat(road.qualificationOutputs()).containsEntry("MSI_LCK_SEED_2", "T02");
        assertThat(road.revision()).isEqualTo(5);
        CareerCompetitionAggregate.CompletionResult replay = road.applyVerifiedCompletion(
                "M5", match(road, "M5").seriesId(),
                match(road, "M5").firstTeamCode(), match(road, "M5").secondTeamCode(),
                "T02", hash(5));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.aggregate()).isEqualTo(road);
        assertThatThrownBy(() -> beforeFinal.applyVerifiedCompletion("M5",
                match(beforeFinal, "M5").seriesId(), "T99", "T03", "T02",
                hash(9))).isInstanceOf(IllegalArgumentException.class);

        CareerCompetitionAggregate playIn = CareerCompetitionAggregate.materialize(
                rules, CAREER, 2027, "LCK_PLAY_IN", "T03", 41L, INPUT,
                ranking().subList(0, 4));
        playIn = complete(playIn, "M1", "T01", 11);
        playIn = complete(playIn, "M2", "T04", 12);
        assertThat(playIn.qualificationOutputs())
                .containsEntry("LCK_PLAYOFF_SEED_5", "T01")
                .containsEntry("LCK_SEASON_PLACE_8", "T03");
        assertThat(match(playIn, "M3")).extracting(
                CareerCompetitionAggregate.Fixture::firstTeamCode,
                CareerCompetitionAggregate.Fixture::secondTeamCode)
                .containsExactly("T02", "T04");
        playIn = complete(playIn, "M3", "T02", 13);
        assertThat(playIn.qualificationOutputs())
                .containsEntry("LCK_PLAYOFF_SEED_6", "T02")
                .containsEntry("LCK_SEASON_PLACE_7", "T04");
    }

    @Test
    void r3r4SplitCarriesRecordsAndAllocatesFortyStableConflictFreeFixtures() {
        CareerCompetitionAggregate.R3R4Stage first =
                CareerCompetitionAggregate.materializeR3R4(CAREER, 2027, "T01",
                        41L, INPUT, ranking());
        CareerCompetitionAggregate.R3R4Stage replay =
                CareerCompetitionAggregate.materializeR3R4(CAREER, 2027, "T01",
                        41L, INPUT, ranking());
        assertThat(first).isEqualTo(replay);
        assertThat(first.legend()).extracting(
                CareerCompetitionAggregate.SeededTeam::teamCode)
                .containsExactly("T01", "T02", "T03", "T04", "T05");
        assertThat(first.rise()).extracting(
                CareerCompetitionAggregate.SeededTeam::teamCode)
                .containsExactly("T06", "T07", "T08", "T09", "T10");
        assertThat(first.fixtures()).hasSize(40).allSatisfy(value -> {
            assertThat(value.date()).isBetween(LocalDate.of(2027, 7, 29),
                    LocalDate.of(2027, 8, 23));
            assertThat(value.seriesFormat()).isEqualTo("BO3");
            assertThat(value.hardFearless()).isTrue();
        });
        assertThat(first.fixtures()).filteredOn(value ->
                "PLAYER_CONTROLLED".equals(value.executionMode())).hasSize(8);
        Map<String, Integer> pairs = new HashMap<>();
        Map<LocalDate, HashSet<String>> dailyTeams = new HashMap<>();
        first.fixtures().forEach(value -> {
            String pair = List.of(value.firstTeamCode(), value.secondTeamCode()).stream()
                    .sorted().reduce((a, b) -> a + ":" + b).orElseThrow();
            pairs.merge(value.groupId() + ":" + pair, 1, Integer::sum);
            HashSet<String> teams = dailyTeams.computeIfAbsent(value.date(),
                    ignored -> new HashSet<>());
            assertThat(teams.add(value.firstTeamCode())).isTrue();
            assertThat(teams.add(value.secondTeamCode())).isTrue();
        });
        assertThat(pairs).hasSize(20).allSatisfy((pair, count) ->
                assertThat(count).isEqualTo(2));
    }

    @Test
    void resourceProvenanceFailsClosedForKnownCupAndPlayoffGaps() {
        assertThat(rules.resourceHash()).isEqualTo(
                CareerCompetitionRules.RESOURCE_HASH);
        assertThat(rules.rawSources()).hasSize(4);
        assertThat(rules.rule("LCK_CUP").blockingReason())
                .isEqualTo("INITIAL_CYCLE_PRIOR_SEASON_RESULT_REQUIRED");
        assertThat(rules.rule("LCK_PLAYOFFS").ruleStatus())
                .isEqualTo("RULE_SOURCE_INCOMPLETE");
        assertThat(rules.rule("LCK_PLAYOFFS").scheduledMonthDays()).hasSize(10);
        assertThat(rules.competitions()).filteredOn(value ->
                "EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED".equals(
                        value.blockingReason())).hasSize(4);
    }

    private static List<CareerCompetitionAggregate.SeededTeam> ranking() {
        return java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(seed ->
                new CareerCompetitionAggregate.SeededTeam(seed, "T%02d".formatted(seed),
                        19 - seed, seed - 1, 36 - seed, seed)).toList();
    }

    private static CareerCompetitionAggregate complete(
            CareerCompetitionAggregate value, String matchId, String winner, int receipt
    ) {
        return apply(value, matchId, winner, receipt).aggregate();
    }

    private static CareerCompetitionAggregate.CompletionResult apply(
            CareerCompetitionAggregate value, String matchId, String winner, int receipt
    ) {
        CareerCompetitionAggregate.Fixture fixture = match(value, matchId);
        return value.applyVerifiedCompletion(matchId, fixture.seriesId(),
                fixture.firstTeamCode(), fixture.secondTeamCode(), winner, hash(receipt));
    }

    private static CareerCompetitionAggregate.Fixture match(
            CareerCompetitionAggregate value, String matchId
    ) {
        return value.fixtures().stream().filter(candidate -> matchId.equals(
                candidate.matchId())).findFirst().orElseThrow();
    }

    private static String hash(int value) {
        return Integer.toHexString(value).repeat(64).substring(0, 64);
    }
}

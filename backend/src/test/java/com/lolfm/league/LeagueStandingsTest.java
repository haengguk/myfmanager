package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LeagueStandingsTest {
    @Test
    void primaryOrderUsesSeriesWinsThenGameDifferentialThenGameWins() {
        LeagueSchedule schedule = LeagueDomainTestFixtures.schedule();
        LeagueStandings differential = standings(schedule,
                completion(schedule, "GEN", "T1", "GEN", 2, 0, "d1"),
                completion(schedule, "GEN", "HLE", "HLE", 2, 1, "d2"),
                completion(schedule, "DK", "BRO", "DK", 2, 1, "d3"),
                completion(schedule, "DK", "DNS", "DNS", 2, 0, "d4"));

        assertThat(indexOf(differential.ranking(73L), "GEN"))
                .isLessThan(indexOf(differential.ranking(73L), "DK"));

        LeagueStandings gameWins = standings(schedule,
                completion(schedule, "GEN", "T1", "GEN", 2, 1, "g1"),
                completion(schedule, "GEN", "HLE", "HLE", 2, 1, "g2"),
                completion(schedule, "DK", "BRO", "DK", 2, 0, "g3"),
                completion(schedule, "DK", "DNS", "DNS", 2, 0, "g4"));

        LeagueStanding gen = gameWins.rows().get("GEN");
        LeagueStanding dk = gameWins.rows().get("DK");
        assertThat(gen.seriesWins()).isEqualTo(dk.seriesWins());
        assertThat(gen.gameDifferential()).isEqualTo(dk.gameDifferential());
        assertThat(gen.gameWins()).isGreaterThan(dk.gameWins());
        assertThat(indexOf(gameWins.ranking(73L), "GEN"))
                .isLessThan(indexOf(gameWins.ranking(73L), "DK"));
    }

    @Test
    void miniLeagueHeadToHeadBreaksAnOtherwiseExactPrimaryTie() {
        LeagueSchedule schedule = LeagueDomainTestFixtures.schedule();
        LeagueStandings standings = standings(schedule,
                completion(schedule, "GEN", "T1", "GEN", 2, 0, "m1"),
                completion(schedule, "T1", "DK", "T1", 2, 0, "m2"),
                completion(schedule, "GEN", "HLE", "HLE", 2, 0, "m3"));

        LeagueStanding gen = standings.rows().get("GEN");
        LeagueStanding t1 = standings.rows().get("T1");
        assertThat(gen.seriesWins()).isEqualTo(t1.seriesWins());
        assertThat(gen.gameDifferential()).isEqualTo(t1.gameDifferential());
        assertThat(gen.gameWins()).isEqualTo(t1.gameWins());

        LeagueRanking ranking = standings.ranking(73L);
        assertThat(indexOf(ranking, "GEN")).isLessThan(indexOf(ranking, "T1"));
        assertThat(ranked(ranking, "GEN").miniLeagueSeriesWins()).isEqualTo(1);
        assertThat(ranked(ranking, "T1").miniLeagueSeriesWins()).isZero();
        assertThat(ranked(ranking, "GEN").deterministicDrawHash()).isNull();
        assertThat(ranked(ranking, "T1").deterministicDrawHash()).isNull();
    }

    @Test
    void completeTieUsesStableSeasonSeedDrawAndEmitsTrace() {
        LeagueSchedule schedule = LeagueDomainTestFixtures.schedule();
        LeagueStandings empty = LeagueStandings.empty(schedule.teamCodes());

        LeagueRanking first = empty.ranking(73L);
        LeagueRanking replay = empty.ranking(73L);
        LeagueRanking changedSeed = empty.ranking(74L);

        assertThat(first).isEqualTo(replay);
        assertThat(first.teams()).hasSize(10);
        assertThat(first.teams().stream().map(LeagueRanking.RankedTeam::teamCode).toList())
                .isNotEqualTo(changedSeed.teams().stream()
                        .map(LeagueRanking.RankedTeam::teamCode).toList());
        assertThat(first.tieBreakTrace()).hasSize(1);
        LeagueRanking.TieBreakTrace trace = first.tieBreakTrace().getFirst();
        assertThat(trace.algorithm()).isEqualTo(
                LeagueIdentity.TIE_BREAK_DRAW_ALGORITHM);
        assertThat(trace.tiedTeamCodes()).containsExactlyElementsOf(
                LeagueDomainTestFixtures.TEAM_CODES);
        assertThat(trace.candidateDrawHashes()).hasSize(10);
        assertThat(trace.resolvedOrder()).containsExactlyElementsOf(
                first.teams().stream().map(LeagueRanking.RankedTeam::teamCode).toList());
    }

    private static LeagueStandings standings(
            LeagueSchedule schedule,
            VerifiedLeagueFixtureCompletion... completions
    ) {
        LeagueStandings result = LeagueStandings.empty(schedule.teamCodes());
        for (VerifiedLeagueFixtureCompletion completion : completions) {
            result = result.apply(schedule, completion);
        }
        return result;
    }

    private static VerifiedLeagueFixtureCompletion completion(
            LeagueSchedule schedule,
            String first,
            String second,
            String winner,
            int winnerGameWins,
            int loserGameWins,
            String receiptKey
    ) {
        return LeagueDomainTestFixtures.completion(schedule, first, second, winner,
                winnerGameWins, loserGameWins, receiptKey);
    }

    private static int indexOf(LeagueRanking ranking, String teamCode) {
        List<String> order = ranking.teams().stream()
                .map(LeagueRanking.RankedTeam::teamCode).toList();
        return order.indexOf(teamCode);
    }

    private static LeagueRanking.RankedTeam ranked(
            LeagueRanking ranking,
            String teamCode
    ) {
        return ranking.teams().stream().filter(value -> value.teamCode().equals(teamCode))
                .findFirst().orElseThrow();
    }
}

package com.lolfm.league;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LeagueDomainTestFixtures {
    static final List<String> TEAM_CODES = List.of(
            "BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX", "KT", "NS", "T1");
    static final long ROOT_SEED = 73L;

    private LeagueDomainTestFixtures() {
    }

    static String leagueId() {
        return LeagueIdentity.leagueId("league-domain-test");
    }

    static String seasonId() {
        return LeagueIdentity.seasonId(leagueId(), "season-domain-test");
    }

    static LeagueSeasonFrozenSnapshot snapshot() {
        LinkedHashMap<String, String> teams = new LinkedHashMap<>();
        TEAM_CODES.reversed().forEach(team -> teams.put(team, hash("team=" + team)));
        return new LeagueSeasonFrozenSnapshot(teams,
                hash("player-resource"),
                hash("champion-draft-resource"),
                hash("matchup-composition-resource"),
                hash("production-runtime"));
    }

    static LeagueSchedule schedule() {
        return schedule(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }

    static LeagueSchedule schedule(
            LeagueSeasonMode mode,
            String managedTeamCode,
            long rootSeed,
            LeagueSchedulePolicy policy
    ) {
        return new LeagueScheduleGenerator().generate(seasonId(), rootSeed, TEAM_CODES,
                mode, managedTeamCode, policy);
    }

    static LeagueFixture fixture(LeagueSchedule schedule, String first, String second) {
        return schedule.fixtures().stream()
                .filter(value -> value.containsTeam(first) && value.containsTeam(second))
                .findFirst().orElseThrow();
    }

    static VerifiedLeagueFixtureCompletion completion(
            LeagueSchedule schedule,
            String first,
            String second,
            String winner,
            int winnerGameWins,
            int loserGameWins,
            String receiptKey
    ) {
        LeagueFixture fixture = fixture(schedule, first, second);
        String loser = winner.equals(first) ? second : first;
        return opaqueCompletion(fixture.fixtureId(), hash("receipt=" + receiptKey),
                winner, loser, winnerGameWins, loserGameWins);
    }

    /** Test-only reflection keeps production free of an unverified completion factory. */
    static VerifiedLeagueFixtureCompletion opaqueCompletion(
            String fixtureId,
            String receiptHash,
            String winner,
            String loser,
            int winnerWins,
            int loserWins
    ) {
        try {
            var constructor = VerifiedLeagueFixtureCompletion.class.getDeclaredConstructor(
                    String.class, String.class, String.class, String.class,
                    int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(fixtureId, receiptHash, winner, loser,
                    winnerWins, loserWins);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    static String hash(String value) {
        return LeagueIdentity.sha256("testIdentitySchema=AI_LEAGUE_TEST_V1\nvalue="
                + value + "\n");
    }

    static Map<String, String> teamSnapshots() {
        return snapshot().teamSnapshotIdentities();
    }
}

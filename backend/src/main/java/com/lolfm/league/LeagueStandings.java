package com.lolfm.league;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable standings and exactly-once receipt application ledger. */
public final class LeagueStandings {
    public static final String STANDINGS_POLICY_ID =
            "AI_LEAGUE_V1_SERIES_WIN_GAME_MINI_SEEDED_DRAW_STANDINGS_V1";

    private static final Comparator<LeagueStanding> PRIMARY_ORDER =
            Comparator.comparingInt(LeagueStanding::seriesWins).reversed()
                    .thenComparing(Comparator.comparingInt(
                            LeagueStanding::gameDifferential).reversed())
                    .thenComparing(Comparator.comparingInt(
                            LeagueStanding::gameWins).reversed());

    private final Map<String, LeagueStanding> rows;
    private final Map<String, VerifiedLeagueFixtureCompletion> completionsByFixture;
    private final Map<String, String> fixtureByReceiptHash;

    private LeagueStandings(
            Map<String, LeagueStanding> rows,
            Map<String, VerifiedLeagueFixtureCompletion> completionsByFixture,
            Map<String, String> fixtureByReceiptHash
    ) {
        this.rows = immutableSorted(rows);
        this.completionsByFixture = immutableSorted(completionsByFixture);
        this.fixtureByReceiptHash = immutableSorted(fixtureByReceiptHash);
    }

    public static LeagueStandings empty(List<String> teamCodes) {
        Objects.requireNonNull(teamCodes, "teamCodes");
        TreeMap<String, LeagueStanding> rows = new TreeMap<>();
        teamCodes.forEach(teamCode -> {
            LeagueStanding previous = rows.put(teamCode,
                    new LeagueStanding(teamCode, 0, 0, 0, 0));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate standings team");
            }
        });
        if (rows.size() != LeagueV1OperationalConfiguration.defaults()
                .activeSeasonTeamCount()) {
            throw new IllegalArgumentException("V1 standings require exactly 10 teams");
        }
        return new LeagueStandings(rows, Map.of(), Map.of());
    }

    public LeagueStandings apply(
            LeagueSchedule schedule,
            VerifiedLeagueFixtureCompletion completion
    ) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(completion, "completion");
        if (!rows.keySet().equals(Set.copyOf(schedule.teamCodes()))) {
            throw new IllegalArgumentException("Standings/schedule membership mismatch");
        }
        VerifiedLeagueFixtureCompletion existing = completionsByFixture.get(
                completion.fixtureId());
        if (existing != null) {
            if (existing.equals(completion)) {
                return this;
            }
            throw new IllegalArgumentException("Fixture already has a different receipt");
        }
        String receiptFixture = fixtureByReceiptHash.get(
                completion.canonicalFixtureReceiptHash());
        if (receiptFixture != null) {
            throw new IllegalArgumentException(
                    "Canonical fixture receipt was already applied to " + receiptFixture);
        }

        LeagueFixture fixture = schedule.fixture(completion.fixtureId());
        validateCompletion(fixture, completion);

        TreeMap<String, LeagueStanding> nextRows = new TreeMap<>(rows);
        LeagueStanding winner = nextRows.get(completion.winnerTeamCode());
        LeagueStanding loser = nextRows.get(completion.loserTeamCode());
        nextRows.put(winner.teamCode(), winner.recordWin(completion.winnerGameWins(),
                completion.loserGameWins()));
        nextRows.put(loser.teamCode(), loser.recordLoss(completion.loserGameWins(),
                completion.winnerGameWins()));

        TreeMap<String, VerifiedLeagueFixtureCompletion> nextCompletions =
                new TreeMap<>(completionsByFixture);
        nextCompletions.put(completion.fixtureId(), completion);
        TreeMap<String, String> nextReceiptLedger = new TreeMap<>(fixtureByReceiptHash);
        nextReceiptLedger.put(completion.canonicalFixtureReceiptHash(),
                completion.fixtureId());
        return new LeagueStandings(nextRows, nextCompletions, nextReceiptLedger);
    }

    public Map<String, LeagueStanding> rows() {
        return rows;
    }

    public int appliedFixtureCount() {
        return completionsByFixture.size();
    }

    public Map<String, VerifiedLeagueFixtureCompletion> appliedCompletions() {
        return completionsByFixture;
    }

    public LeagueRanking ranking(long seasonRootSeed) {
        ArrayList<LeagueStanding> primary = new ArrayList<>(rows.values());
        primary.sort(PRIMARY_ORDER.thenComparing(LeagueStanding::teamCode));
        ArrayList<LeagueRanking.RankedTeam> ranked = new ArrayList<>();
        ArrayList<LeagueRanking.TieBreakTrace> traces = new ArrayList<>();
        int start = 0;
        while (start < primary.size()) {
            int end = start + 1;
            while (end < primary.size() && primaryEqual(primary.get(start),
                    primary.get(end))) {
                end++;
            }
            rankPrimaryGroup(primary.subList(start, end), seasonRootSeed, ranked, traces);
            start = end;
        }
        ArrayList<LeagueRanking.RankedTeam> positioned = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            LeagueRanking.RankedTeam current = ranked.get(index);
            positioned.add(new LeagueRanking.RankedTeam(index + 1, current.standing(),
                    current.miniLeagueSeriesWins(), current.miniLeagueGameDifferential(),
                    current.deterministicDrawHash()));
        }
        return new LeagueRanking(STANDINGS_POLICY_ID, positioned, traces);
    }

    private void rankPrimaryGroup(
            List<LeagueStanding> group,
            long seasonRootSeed,
            List<LeagueRanking.RankedTeam> ranked,
            List<LeagueRanking.TieBreakTrace> traces
    ) {
        Set<String> groupTeams = group.stream().map(LeagueStanding::teamCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<String, MiniStanding> mini = miniStandings(groupTeams);
        ArrayList<LeagueStanding> ordered = new ArrayList<>(group);
        ordered.sort(Comparator.<LeagueStanding>comparingInt(
                        value -> mini.get(value.teamCode()).seriesWins()).reversed()
                .thenComparing(Comparator.comparingInt(
                        (LeagueStanding value) -> mini.get(value.teamCode())
                                .gameDifferential()).reversed())
                .thenComparing(LeagueStanding::teamCode));

        int start = 0;
        while (start < ordered.size()) {
            int end = start + 1;
            MiniStanding key = mini.get(ordered.get(start).teamCode());
            while (end < ordered.size()
                    && key.equals(mini.get(ordered.get(end).teamCode()))) {
                end++;
            }
            List<LeagueStanding> tied = ordered.subList(start, end);
            if (tied.size() == 1) {
                LeagueStanding value = tied.getFirst();
                MiniStanding valueMini = mini.get(value.teamCode());
                ranked.add(new LeagueRanking.RankedTeam(1, value,
                        valueMini.seriesWins(), valueMini.gameDifferential(), null));
            } else {
                appendSeededDraw(tied, mini, seasonRootSeed, ranked, traces);
            }
            start = end;
        }
    }

    private void appendSeededDraw(
            List<LeagueStanding> tied,
            Map<String, MiniStanding> mini,
            long seasonRootSeed,
            List<LeagueRanking.RankedTeam> ranked,
            List<LeagueRanking.TieBreakTrace> traces
    ) {
        List<String> canonicalTeams = tied.stream().map(LeagueStanding::teamCode)
                .sorted().toList();
        TreeMap<String, String> hashes = new TreeMap<>();
        canonicalTeams.forEach(team -> hashes.put(team,
                LeagueIdentity.tieBreakCandidateHash(seasonRootSeed, canonicalTeams, team)));
        List<String> resolved = canonicalTeams.stream()
                .sorted(Comparator.comparing((String team) -> hashes.get(team))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        Map<String, LeagueStanding> standingByTeam = tied.stream().collect(
                java.util.stream.Collectors.toMap(LeagueStanding::teamCode, value -> value));
        resolved.forEach(team -> {
            MiniStanding valueMini = mini.get(team);
            ranked.add(new LeagueRanking.RankedTeam(1, standingByTeam.get(team),
                    valueMini.seriesWins(), valueMini.gameDifferential(), hashes.get(team)));
        });
        traces.add(new LeagueRanking.TieBreakTrace(
                LeagueIdentity.TIE_BREAK_DRAW_ALGORITHM,
                canonicalTeams,
                LeagueIdentity.tieBreakCommonInputHash(seasonRootSeed, canonicalTeams),
                hashes,
                resolved));
    }

    private Map<String, MiniStanding> miniStandings(Set<String> groupTeams) {
        HashMap<String, MiniStanding> result = new HashMap<>();
        groupTeams.forEach(team -> result.put(team, new MiniStanding(0, 0)));
        completionsByFixture.values().stream()
                .filter(completion -> groupTeams.contains(completion.winnerTeamCode())
                        && groupTeams.contains(completion.loserTeamCode()))
                .forEach(completion -> {
                    MiniStanding winner = result.get(completion.winnerTeamCode());
                    MiniStanding loser = result.get(completion.loserTeamCode());
                    int differential = completion.winnerGameWins()
                            - completion.loserGameWins();
                    result.put(completion.winnerTeamCode(),
                            new MiniStanding(winner.seriesWins() + 1,
                                    winner.gameDifferential() + differential));
                    result.put(completion.loserTeamCode(),
                            new MiniStanding(loser.seriesWins(),
                                    loser.gameDifferential() - differential));
                });
        return Map.copyOf(result);
    }

    private static void validateCompletion(
            LeagueFixture fixture,
            VerifiedLeagueFixtureCompletion completion
    ) {
        if (!fixture.teamCodes().equals(Set.of(completion.winnerTeamCode(),
                completion.loserTeamCode()))
                || completion.winnerGameWins() != fixture.seriesFormat().winsRequired()
                || completion.loserGameWins() >= fixture.seriesFormat().winsRequired()
                || completion.winnerGameWins() + completion.loserGameWins()
                > fixture.seriesFormat().maximumGames()) {
            throw new IllegalArgumentException(
                    "Verified completion does not match frozen fixture/format");
        }
    }

    private static boolean primaryEqual(LeagueStanding first, LeagueStanding second) {
        return first.seriesWins() == second.seriesWins()
                && first.gameDifferential() == second.gameDifferential()
                && first.gameWins() == second.gameWins();
    }

    private static <T> Map<String, T> immutableSorted(Map<String, T> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private record MiniStanding(int seriesWins, int gameDifferential) {
    }
}

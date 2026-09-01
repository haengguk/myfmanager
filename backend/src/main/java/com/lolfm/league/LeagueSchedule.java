package com.lolfm.league;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Fully validated immutable V1 schedule and frozen fixture execution mapping. */
public final class LeagueSchedule {
    private final String scheduleIdentity;
    private final String seasonId;
    private final long seasonRootSeed;
    private final LeagueSchedulePolicy policy;
    private final LeagueSeasonMode seasonMode;
    private final String managedTeamCode;
    private final List<String> teamCodes;
    private final List<LeagueRound> rounds;
    private final List<LeagueFixture> fixtures;
    private final Map<String, LeagueFixture> fixturesById;

    LeagueSchedule(
            String scheduleIdentity,
            String seasonId,
            long seasonRootSeed,
            LeagueSchedulePolicy policy,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            List<String> teamCodes,
            List<LeagueRound> rounds
    ) {
        LeagueSeasonFrozenSnapshot.requireSha256(scheduleIdentity, "scheduleIdentity");
        LeagueIdentity.requireSeasonId(seasonId);
        this.scheduleIdentity = scheduleIdentity;
        this.seasonId = seasonId;
        this.seasonRootSeed = seasonRootSeed;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.seasonMode = Objects.requireNonNull(seasonMode, "seasonMode");
        this.managedTeamCode = managedTeamCode;
        this.teamCodes = canonicalTeams(teamCodes);
        this.rounds = List.copyOf(Objects.requireNonNull(rounds, "rounds"));
        ArrayList<LeagueFixture> flat = new ArrayList<>();
        this.rounds.forEach(round -> flat.addAll(round.fixtures()));
        this.fixtures = List.copyOf(flat);
        TreeMap<String, LeagueFixture> byId = new TreeMap<>();
        this.fixtures.forEach(fixture -> {
            if (byId.put(fixture.fixtureId(), fixture) != null) {
                throw new IllegalArgumentException("Duplicate fixture ID");
            }
        });
        this.fixturesById = Collections.unmodifiableMap(byId);
        validate();
    }

    public String scheduleIdentity() {
        return scheduleIdentity;
    }

    public String seasonId() {
        return seasonId;
    }

    public long seasonRootSeed() {
        return seasonRootSeed;
    }

    public LeagueSchedulePolicy policy() {
        return policy;
    }

    public LeagueSeasonMode seasonMode() {
        return seasonMode;
    }

    public String managedTeamCode() {
        return managedTeamCode;
    }

    public List<String> teamCodes() {
        return teamCodes;
    }

    public List<LeagueRound> rounds() {
        return rounds;
    }

    public List<LeagueFixture> fixtures() {
        return fixtures;
    }

    public LeagueFixture fixture(String fixtureId) {
        LeagueFixture fixture = fixturesById.get(fixtureId);
        if (fixture == null) {
            throw new IllegalArgumentException("Unknown fixture: " + fixtureId);
        }
        return fixture;
    }

    private void validate() {
        int expectedRounds = policy.expectedRoundCount(teamCodes.size());
        int expectedFixtures = policy.expectedFixtureCount(teamCodes.size());
        if (rounds.size() != expectedRounds || fixtures.size() != expectedFixtures
                || rounds.size() * teamCodes.size() / 2 != fixtures.size()) {
            throw new IllegalArgumentException("Schedule cardinality invariant");
        }
        for (int index = 0; index < rounds.size(); index++) {
            LeagueRound round = rounds.get(index);
            if (round.roundNumber() != index + 1
                    || round.fixtures().size() != teamCodes.size() / 2) {
                throw new IllegalArgumentException("Schedule round ordering invariant");
            }
            Set<String> roundTeams = new HashSet<>();
            round.fixtures().forEach(fixture -> roundTeams.addAll(fixture.teamCodes()));
            if (!roundTeams.equals(Set.copyOf(teamCodes))) {
                throw new IllegalArgumentException("Every team must play once per round");
            }
        }

        Map<String, List<LeagueFixture>> byPair = new HashMap<>();
        fixtures.forEach(fixture -> {
            if (!teamCodes.containsAll(fixture.teamCodes())) {
                throw new IllegalArgumentException("Fixture team outside frozen membership");
            }
            LeagueFixtureExecutionMode expectedMode = managedTeamCode != null
                    && fixture.containsTeam(managedTeamCode)
                    ? LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                    : LeagueFixtureExecutionMode.FULL_AUTO;
            if (fixture.executionMode() != expectedMode) {
                throw new IllegalArgumentException("Fixture execution mode invariant");
            }
            byPair.computeIfAbsent(fixture.pairId(), ignored -> new ArrayList<>())
                    .add(fixture);
        });
        int expectedPairFixtures = policy.scheduleFormat().legs();
        byPair.forEach((pairId, pairFixtures) -> {
            if (pairFixtures.size() != expectedPairFixtures) {
                throw new IllegalArgumentException("Pair leg cardinality invariant: " + pairId);
            }
            if (expectedPairFixtures == 2) {
                LeagueFixture first = pairFixtures.stream()
                        .filter(value -> value.legNumber() == 1).findFirst().orElseThrow();
                LeagueFixture second = pairFixtures.stream()
                        .filter(value -> value.legNumber() == 2).findFirst().orElseThrow();
                if (!first.game1BlueTeamCode().equals(second.game1RedTeamCode())
                        || !first.game1RedTeamCode().equals(second.game1BlueTeamCode())) {
                    throw new IllegalArgumentException("Paired legs must mirror Game 1 side");
                }
            }
        });
    }

    private static List<String> canonicalTeams(List<String> values) {
        Objects.requireNonNull(values, "teamCodes");
        ArrayList<String> result = new ArrayList<>(values);
        result.forEach(LeagueIdentity::requireTeamCode);
        result.sort(String::compareTo);
        if (result.size() != LeagueV1OperationalConfiguration.defaults()
                .activeSeasonTeamCount()
                || new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("V1 schedule requires 10 unique teams");
        }
        return List.copyOf(result);
    }
}

package com.lolfm.league;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One deterministic round in which each frozen team appears exactly once. */
public record LeagueRound(int roundNumber, List<LeagueFixture> fixtures) {
    public LeagueRound {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("Invalid round number");
        }
        fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
        if (fixtures.isEmpty()) {
            throw new IllegalArgumentException("Round must contain fixtures");
        }
        Set<String> teams = new HashSet<>();
        Set<String> fixtureIds = new HashSet<>();
        fixtures.forEach(fixture -> {
            if (fixture.roundNumber() != roundNumber
                    || !fixtureIds.add(fixture.fixtureId())) {
                throw new IllegalArgumentException("Round fixture/team uniqueness invariant");
            }
            fixture.teamCodes().forEach(team -> {
                if (!teams.add(team)) {
                    throw new IllegalArgumentException(
                            "Round fixture/team uniqueness invariant");
                }
            });
        });
    }
}

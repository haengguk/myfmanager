package com.lolfm.league;

import com.lolfm.application.SeriesFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Stateless circle-method generator with canonical team order and mirrored second legs. */
public final class LeagueScheduleGenerator {
    public LeagueSchedule generate(
            String seasonId,
            long seasonRootSeed,
            Collection<String> teamCodes,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            LeagueSchedulePolicy policy
    ) {
        LeagueIdentity.requireSeasonId(seasonId);
        Objects.requireNonNull(teamCodes, "teamCodes");
        Objects.requireNonNull(seasonMode, "seasonMode");
        Objects.requireNonNull(policy, "policy");
        ArrayList<String> canonicalTeams = new ArrayList<>(teamCodes);
        canonicalTeams.forEach(LeagueIdentity::requireTeamCode);
        canonicalTeams.sort(String::compareTo);
        int requiredTeamCount = LeagueV1OperationalConfiguration.defaults()
                .activeSeasonTeamCount();
        if (canonicalTeams.size() != requiredTeamCount
                || new HashSet<>(canonicalTeams).size() != requiredTeamCount) {
            throw new IllegalArgumentException("V1 schedule requires exactly 10 unique teams");
        }
        validateMode(seasonMode, managedTeamCode, canonicalTeams);

        String scheduleIdentity = LeagueIdentity.scheduleIdentity(
                seasonId, policy, canonicalTeams);
        List<LeagueRound> firstLeg = firstLeg(scheduleIdentity, seasonId, seasonRootSeed,
                canonicalTeams, seasonMode, managedTeamCode, policy.seriesFormat());
        ArrayList<LeagueRound> rounds = new ArrayList<>(firstLeg);
        if (policy.scheduleFormat() == LeagueScheduleFormat.DOUBLE_ROUND_ROBIN) {
            rounds.addAll(mirroredSecondLeg(scheduleIdentity, seasonId, seasonRootSeed,
                    firstLeg, seasonMode, managedTeamCode, policy.seriesFormat()));
        }
        return new LeagueSchedule(scheduleIdentity, seasonId, seasonRootSeed, policy,
                seasonMode, managedTeamCode, canonicalTeams, rounds);
    }

    private static List<LeagueRound> firstLeg(
            String scheduleIdentity,
            String seasonId,
            long seasonRootSeed,
            List<String> teams,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            SeriesFormat seriesFormat
    ) {
        ArrayList<String> rotation = new ArrayList<>(teams);
        ArrayList<LeagueRound> rounds = new ArrayList<>();
        int roundsPerLeg = teams.size() - 1;
        for (int roundIndex = 0; roundIndex < roundsPerLeg; roundIndex++) {
            int roundNumber = roundIndex + 1;
            ArrayList<LeagueFixture> fixtures = new ArrayList<>();
            for (int pairIndex = 0; pairIndex < teams.size() / 2; pairIndex++) {
                String left = rotation.get(pairIndex);
                String right = rotation.get(rotation.size() - 1 - pairIndex);
                String blue = (roundIndex + pairIndex) % 2 == 0 ? left : right;
                String red = blue.equals(left) ? right : left;
                fixtures.add(fixture(scheduleIdentity, seasonId, seasonRootSeed,
                        roundNumber, 1, blue, red, seasonMode, managedTeamCode,
                        seriesFormat));
            }
            rounds.add(new LeagueRound(roundNumber, fixtures));
            String last = rotation.removeLast();
            rotation.add(1, last);
        }
        return List.copyOf(rounds);
    }

    private static List<LeagueRound> mirroredSecondLeg(
            String scheduleIdentity,
            String seasonId,
            long seasonRootSeed,
            List<LeagueRound> firstLeg,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            SeriesFormat seriesFormat
    ) {
        ArrayList<LeagueRound> result = new ArrayList<>();
        int offset = firstLeg.size();
        for (LeagueRound sourceRound : firstLeg) {
            int roundNumber = sourceRound.roundNumber() + offset;
            List<LeagueFixture> fixtures = sourceRound.fixtures().stream()
                    .map(source -> fixture(scheduleIdentity, seasonId, seasonRootSeed,
                            roundNumber, 2, source.game1RedTeamCode(),
                            source.game1BlueTeamCode(), seasonMode, managedTeamCode,
                            seriesFormat))
                    .toList();
            result.add(new LeagueRound(roundNumber, fixtures));
        }
        return List.copyOf(result);
    }

    private static LeagueFixture fixture(
            String scheduleIdentity,
            String seasonId,
            long seasonRootSeed,
            int roundNumber,
            int legNumber,
            String game1BlueTeamCode,
            String game1RedTeamCode,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            SeriesFormat seriesFormat
    ) {
        String first = game1BlueTeamCode.compareTo(game1RedTeamCode) < 0
                ? game1BlueTeamCode : game1RedTeamCode;
        String second = first.equals(game1BlueTeamCode)
                ? game1RedTeamCode : game1BlueTeamCode;
        String pairId = "pair_" + first + "_" + second;
        String fixtureId = LeagueIdentity.fixtureId(scheduleIdentity, roundNumber,
                legNumber, pairId, game1BlueTeamCode, game1RedTeamCode, seriesFormat);
        long fixtureRootSeed = LeagueIdentity.fixtureRootSeed(seasonId, seasonRootSeed,
                scheduleIdentity, fixtureId, roundNumber, legNumber,
                game1BlueTeamCode, game1RedTeamCode);
        String boundSeriesId = LeagueIdentity.boundSeriesId(fixtureId, fixtureRootSeed,
                first, second, seriesFormat);
        LeagueFixtureExecutionMode executionMode = seasonMode
                == LeagueSeasonMode.HYBRID_MANAGER
                && (first.equals(managedTeamCode) || second.equals(managedTeamCode))
                ? LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                : LeagueFixtureExecutionMode.FULL_AUTO;
        return new LeagueFixture(fixtureId, pairId, roundNumber, legNumber,
                first, second, game1BlueTeamCode, game1RedTeamCode, seriesFormat,
                executionMode, fixtureRootSeed, boundSeriesId, first);
    }

    private static void validateMode(
            LeagueSeasonMode mode,
            String managedTeamCode,
            List<String> teams
    ) {
        if (mode == LeagueSeasonMode.HYBRID_MANAGER) {
            LeagueIdentity.requireTeamCode(managedTeamCode);
            if (!teams.contains(managedTeamCode)) {
                throw new IllegalArgumentException("Managed team is outside frozen membership");
            }
        } else if (managedTeamCode != null) {
            throw new IllegalArgumentException(
                    "Spectator full-auto season cannot have a managed team");
        }
    }
}

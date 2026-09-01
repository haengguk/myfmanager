package com.lolfm.league;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Canonical ranked projection plus explicit seeded-draw evidence. */
public record LeagueRanking(
        String standingsPolicyId,
        List<RankedTeam> teams,
        List<TieBreakTrace> tieBreakTrace
) {
    public LeagueRanking {
        if (!LeagueStandings.STANDINGS_POLICY_ID.equals(standingsPolicyId)) {
            throw new IllegalArgumentException("Unknown standings policy");
        }
        teams = List.copyOf(Objects.requireNonNull(teams, "teams"));
        tieBreakTrace = List.copyOf(Objects.requireNonNull(tieBreakTrace,
                "tieBreakTrace"));
        for (int index = 0; index < teams.size(); index++) {
            if (teams.get(index).position() != index + 1) {
                throw new IllegalArgumentException("Ranking position invariant");
            }
        }
    }

    public record RankedTeam(
            int position,
            LeagueStanding standing,
            int miniLeagueSeriesWins,
            int miniLeagueGameDifferential,
            String deterministicDrawHash
    ) {
        public RankedTeam {
            if (position < 1 || standing == null || miniLeagueSeriesWins < 0) {
                throw new IllegalArgumentException("Invalid ranked team");
            }
            if (deterministicDrawHash != null) {
                LeagueSeasonFrozenSnapshot.requireSha256(deterministicDrawHash,
                        "deterministicDrawHash");
            }
        }

        public String teamCode() {
            return standing.teamCode();
        }
    }

    public record TieBreakTrace(
            String algorithm,
            List<String> tiedTeamCodes,
            String canonicalInputHash,
            Map<String, String> candidateDrawHashes,
            List<String> resolvedOrder
    ) {
        public TieBreakTrace {
            if (!LeagueIdentity.TIE_BREAK_DRAW_ALGORITHM.equals(algorithm)) {
                throw new IllegalArgumentException("Unknown tie-break algorithm");
            }
            tiedTeamCodes = List.copyOf(Objects.requireNonNull(tiedTeamCodes,
                    "tiedTeamCodes"));
            resolvedOrder = List.copyOf(Objects.requireNonNull(resolvedOrder,
                    "resolvedOrder"));
            LeagueSeasonFrozenSnapshot.requireSha256(canonicalInputHash,
                    "canonicalInputHash");
            TreeMap<String, String> canonicalHashes = new TreeMap<>(Objects.requireNonNull(
                    candidateDrawHashes, "candidateDrawHashes"));
            canonicalHashes.values().forEach(hash ->
                    LeagueSeasonFrozenSnapshot.requireSha256(hash, "candidateDrawHash"));
            candidateDrawHashes = Collections.unmodifiableMap(canonicalHashes);
            if (tiedTeamCodes.size() < 2
                    || !tiedTeamCodes.equals(tiedTeamCodes.stream().sorted().toList())
                    || !java.util.Set.copyOf(tiedTeamCodes).equals(
                    java.util.Set.copyOf(resolvedOrder))
                    || !candidateDrawHashes.keySet().equals(
                    java.util.Set.copyOf(tiedTeamCodes))) {
                throw new IllegalArgumentException("Tie-break trace invariant");
            }
        }
    }
}

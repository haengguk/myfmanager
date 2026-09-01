package com.lolfm.league;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable identities for all authored inputs that may affect one V1 season. */
public record LeagueSeasonFrozenSnapshot(
        Map<String, String> teamSnapshotIdentities,
        String playerResourceIdentity,
        String championDraftResourceIdentity,
        String matchupCompositionResourceIdentity,
        String productionRuntimeIdentity,
        String snapshotIdentity
) {
    public LeagueSeasonFrozenSnapshot(
            Map<String, String> teamSnapshotIdentities,
            String playerResourceIdentity,
            String championDraftResourceIdentity,
            String matchupCompositionResourceIdentity,
            String productionRuntimeIdentity
    ) {
        this(teamSnapshotIdentities, playerResourceIdentity, championDraftResourceIdentity,
                matchupCompositionResourceIdentity, productionRuntimeIdentity,
                identity(teamSnapshotIdentities, playerResourceIdentity,
                        championDraftResourceIdentity, matchupCompositionResourceIdentity,
                        productionRuntimeIdentity));
    }

    public LeagueSeasonFrozenSnapshot {
        Objects.requireNonNull(teamSnapshotIdentities, "teamSnapshotIdentities");
        TreeMap<String, String> canonicalTeams = new TreeMap<>();
        teamSnapshotIdentities.forEach((teamCode, identity) -> {
            LeagueIdentity.requireTeamCode(teamCode);
            requireSha256(identity, "teamSnapshotIdentity");
            if (canonicalTeams.put(teamCode, identity) != null) {
                throw new IllegalArgumentException("Duplicate team snapshot: " + teamCode);
            }
        });
        if (canonicalTeams.size()
                != LeagueV1OperationalConfiguration.defaults().activeSeasonTeamCount()) {
            throw new IllegalArgumentException("V1 season requires exactly 10 team snapshots");
        }
        teamSnapshotIdentities = Collections.unmodifiableMap(canonicalTeams);
        requireSha256(playerResourceIdentity, "playerResourceIdentity");
        requireSha256(championDraftResourceIdentity, "championDraftResourceIdentity");
        requireSha256(matchupCompositionResourceIdentity,
                "matchupCompositionResourceIdentity");
        requireSha256(productionRuntimeIdentity, "productionRuntimeIdentity");
        requireSha256(snapshotIdentity, "snapshotIdentity");
        String expected = identity(teamSnapshotIdentities, playerResourceIdentity,
                championDraftResourceIdentity, matchupCompositionResourceIdentity,
                productionRuntimeIdentity);
        if (!expected.equals(snapshotIdentity)) {
            throw new IllegalArgumentException("Frozen snapshot identity mismatch");
        }
    }

    public String teamSnapshotIdentity(String teamCode) {
        String result = teamSnapshotIdentities.get(teamCode);
        if (result == null) {
            throw new IllegalArgumentException("Unknown frozen team: " + teamCode);
        }
        return result;
    }

    private static String identity(
            Map<String, String> teams,
            String player,
            String championDraft,
            String matchupComposition,
            String runtime
    ) {
        Objects.requireNonNull(teams, "teams");
        StringBuilder canonical = new StringBuilder(
                "snapshotSchema=AI_LEAGUE_SEASON_FROZEN_SNAPSHOT_V1\n");
        new TreeMap<>(teams).forEach((teamCode, value) -> canonical.append("team=")
                .append(teamCode).append('|').append(value).append('\n'));
        canonical.append("playerResourceIdentity=").append(player).append('\n')
                .append("championDraftResourceIdentity=").append(championDraft).append('\n')
                .append("matchupCompositionResourceIdentity=")
                .append(matchupComposition).append('\n')
                .append("productionRuntimeIdentity=").append(runtime).append('\n');
        return LeagueIdentity.sha256(canonical.toString());
    }

    static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }
}

package com.lolfm.player;

import java.util.Objects;

/** Explicit authored dataset selection; never inferred from the JSON being loaded. */
public record PlayerResourceSpec(
        String leagueCode, int teamCount, String version, String snapshotAt,
        String sha256, String dataCutoff
) {
    public PlayerResourceSpec {
        Objects.requireNonNull(leagueCode, "leagueCode");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(snapshotAt, "snapshotAt");
        Objects.requireNonNull(sha256, "sha256");
        if (!leagueCode.matches("[A-Z]+") || teamCount < 1
                || !version.matches("[a-z0-9-]+") || snapshotAt.isBlank()
                || sha256.isBlank()) {
            throw new IllegalArgumentException("Invalid player resource specification");
        }
    }

    public int playerCount() { return teamCount * 5; }
    public String resource() { return "/players/" + version + ".json"; }
}

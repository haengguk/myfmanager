package com.lolfm.league;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Unified Auto/Player authority envelope. V1 remains the compact fixture body while this
 * explicit V2 adds League ownership, Player binding and per-game Draft authority.
 */
public record LeagueFixtureCompletionReceiptV2(
        String schemaVersion,
        String canonicalHashAlgorithm,
        String leagueId,
        String playerSeriesBindingHash,
        LeagueFixtureCompletionReceiptV1 fixtureReceipt,
        List<LeagueFixtureDraftAuthorityReceiptV1> orderedDraftAuthorityReceipts,
        String canonicalFixtureReceiptHash
) {
    public static final String SCHEMA = "AI_LEAGUE_FIXTURE_COMPLETION_RECEIPT_V2";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_FIXTURE_RECEIPT_LINES_TRAILING_NEWLINE_V2";
    public static final int MAX_CANONICAL_BYTES = 128 * 1024;

    public LeagueFixtureCompletionReceiptV2 {
        if (!SCHEMA.equals(schemaVersion) || !HASH_ALGORITHM.equals(canonicalHashAlgorithm)) {
            throw new IllegalArgumentException("Unsupported unified League receipt schema");
        }
        LeagueIdentity.requireLeagueId(leagueId);
        Objects.requireNonNull(fixtureReceipt, "fixtureReceipt");
        orderedDraftAuthorityReceipts = List.copyOf(orderedDraftAuthorityReceipts);
        if (orderedDraftAuthorityReceipts.size() != fixtureReceipt.actualGameCount()) {
            throw new IllegalArgumentException("Draft authority/game cardinality mismatch");
        }
        for (int index = 0; index < orderedDraftAuthorityReceipts.size(); index++) {
            LeagueFixtureDraftAuthorityReceiptV1 authority =
                    orderedDraftAuthorityReceipts.get(index);
            if (authority.gameNumber() != index + 1
                    || authority.executionMode() != fixtureReceipt.executionMode()) {
                throw new IllegalArgumentException("Draft authority order/mode mismatch");
            }
        }
        if (fixtureReceipt.executionMode() == LeagueFixtureExecutionMode.PLAYER_CONTROLLED) {
            LeagueSeasonFrozenSnapshot.requireSha256(
                    playerSeriesBindingHash, "playerSeriesBindingHash");
        } else if (playerSeriesBindingHash != null) {
            throw new IllegalArgumentException("FULL_AUTO receipt cannot claim Player binding");
        }
        String expectedHash = LeagueIdentity.sha256(payloadText(
                schemaVersion, canonicalHashAlgorithm, leagueId, playerSeriesBindingHash,
                fixtureReceipt, orderedDraftAuthorityReceipts));
        if (canonicalFixtureReceiptHash == null) {
            canonicalFixtureReceiptHash = expectedHash;
        } else if (!expectedHash.equals(canonicalFixtureReceiptHash)) {
            throw new IllegalArgumentException("Canonical unified fixture receipt hash mismatch");
        }
        if ((payloadText(schemaVersion, canonicalHashAlgorithm, leagueId,
                playerSeriesBindingHash, fixtureReceipt, orderedDraftAuthorityReceipts)
                + "canonicalFixtureReceiptHash=" + canonicalFixtureReceiptHash + '\n')
                .getBytes(StandardCharsets.UTF_8).length > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("Unified fixture receipt exceeds compact limit");
        }
    }

    public byte[] canonicalBytes() {
        return canonicalText().getBytes(StandardCharsets.UTF_8);
    }

    public String canonicalText() {
        return payloadText(schemaVersion, canonicalHashAlgorithm, leagueId,
                playerSeriesBindingHash, fixtureReceipt, orderedDraftAuthorityReceipts)
                + "canonicalFixtureReceiptHash=" + canonicalFixtureReceiptHash + '\n';
    }

    private static String payloadText(
            String schema,
            String algorithm,
            String leagueId,
            String bindingHash,
            LeagueFixtureCompletionReceiptV1 fixture,
            List<LeagueFixtureDraftAuthorityReceiptV1> authorities
    ) {
        StringBuilder value = new StringBuilder();
        value.append("schemaVersion=").append(schema).append('\n')
                .append("canonicalHashAlgorithm=").append(algorithm).append('\n')
                .append("leagueId=").append(leagueId).append('\n')
                .append("playerSeriesBindingHash=")
                .append(bindingHash == null ? "NONE" : bindingHash).append('\n')
                .append("fixtureReceiptBegin\n")
                .append(fixture.canonicalText())
                .append("fixtureReceiptEnd\n");
        authorities.forEach(authority -> value.append(authority.canonicalText()));
        return value.toString();
    }

    public String seasonId() { return fixtureReceipt.seasonId(); }
    public String fixtureId() { return fixtureReceipt.fixtureId(); }
    public String boundSeriesId() { return fixtureReceipt.boundSeriesId(); }
    public LeagueFixtureExecutionMode executionMode() { return fixtureReceipt.executionMode(); }
    public String firstTeamCode() { return fixtureReceipt.firstTeamCode(); }
    public String secondTeamCode() { return fixtureReceipt.secondTeamCode(); }
    public int firstTeamGameWins() { return fixtureReceipt.firstTeamGameWins(); }
    public int secondTeamGameWins() { return fixtureReceipt.secondTeamGameWins(); }
    public String winnerTeamCode() { return fixtureReceipt.winnerTeamCode(); }
    public String loserTeamCode() { return fixtureReceipt.loserTeamCode(); }
    public int actualGameCount() { return fixtureReceipt.actualGameCount(); }
    public List<LeagueFixtureGameReceiptV1> orderedGameReceipts() {
        return fixtureReceipt.orderedGameReceipts();
    }
}

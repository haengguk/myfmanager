package com.lolfm.league;

import com.lolfm.application.SeriesFormat;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Canonical compact authority shared by automated and future player-controlled fixtures. */
public record LeagueFixtureCompletionReceiptV1(
        String schemaVersion,
        String canonicalHashAlgorithm,
        String seasonId,
        String fixtureId,
        String boundSeriesId,
        LeagueFixtureExecutionMode executionMode,
        String firstTeamCode,
        String secondTeamCode,
        String game1BlueTeamCode,
        String game1RedTeamCode,
        SeriesFormat seriesFormat,
        long fixtureRootSeed,
        String fixtureRootSeedAlgorithm,
        String gameSeedAlgorithm,
        String scheduleIdentity,
        String productDecisionHash,
        String frozenSnapshotIdentity,
        String firstTeamSnapshotIdentity,
        String secondTeamSnapshotIdentity,
        String playerResourceIdentity,
        String championDraftResourceIdentity,
        String matchupCompositionResourceIdentity,
        String productionRuntimeIdentity,
        String resourceProvenanceHash,
        List<LeagueFixtureGameReceiptV1> orderedGameReceipts,
        int firstTeamGameWins,
        int secondTeamGameWins,
        String winnerTeamCode,
        String loserTeamCode,
        int actualGameCount,
        String canonicalFixtureReceiptHash
) {
    public static final String SCHEMA = "AI_LEAGUE_FIXTURE_COMPLETION_RECEIPT_V1";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_FIXTURE_RECEIPT_LINES_TRAILING_NEWLINE_V1";
    public static final int MAX_CANONICAL_BYTES = 128 * 1024;

    public LeagueFixtureCompletionReceiptV1 {
        if (!SCHEMA.equals(required(schemaVersion, "schemaVersion"))
                || !HASH_ALGORITHM.equals(required(
                canonicalHashAlgorithm, "canonicalHashAlgorithm"))) {
            throw new IllegalArgumentException("Unsupported League fixture receipt schema");
        }
        LeagueIdentity.requireSeasonId(seasonId);
        if (fixtureId == null || !fixtureId.matches("fixture_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fixtureId");
        }
        if (boundSeriesId == null || !boundSeriesId.matches("series_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("boundSeriesId");
        }
        Objects.requireNonNull(executionMode, "executionMode");
        LeagueIdentity.requireTeamCode(firstTeamCode);
        LeagueIdentity.requireTeamCode(secondTeamCode);
        LeagueIdentity.requireTeamCode(game1BlueTeamCode);
        LeagueIdentity.requireTeamCode(game1RedTeamCode);
        if (firstTeamCode.compareTo(secondTeamCode) >= 0
                || !Set.of(firstTeamCode, secondTeamCode)
                .equals(Set.of(game1BlueTeamCode, game1RedTeamCode))) {
            throw new IllegalArgumentException("Fixture receipt team invariant");
        }
        Objects.requireNonNull(seriesFormat, "seriesFormat");
        fixtureRootSeedAlgorithm = required(
                fixtureRootSeedAlgorithm, "fixtureRootSeedAlgorithm");
        gameSeedAlgorithm = required(gameSeedAlgorithm, "gameSeedAlgorithm");
        LeagueSeasonFrozenSnapshot.requireSha256(scheduleIdentity, "scheduleIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(productDecisionHash, "productDecisionHash");
        LeagueSeasonFrozenSnapshot.requireSha256(
                frozenSnapshotIdentity, "frozenSnapshotIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                firstTeamSnapshotIdentity, "firstTeamSnapshotIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                secondTeamSnapshotIdentity, "secondTeamSnapshotIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                playerResourceIdentity, "playerResourceIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                championDraftResourceIdentity, "championDraftResourceIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                matchupCompositionResourceIdentity, "matchupCompositionResourceIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                productionRuntimeIdentity, "productionRuntimeIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                resourceProvenanceHash, "resourceProvenanceHash");
        orderedGameReceipts = List.copyOf(orderedGameReceipts);
        if (orderedGameReceipts.size() != actualGameCount
                || actualGameCount < seriesFormat.winsRequired()
                || actualGameCount > seriesFormat.maximumGames()) {
            throw new IllegalArgumentException("Fixture receipt game cardinality");
        }
        LeagueIdentity.requireTeamCode(winnerTeamCode);
        LeagueIdentity.requireTeamCode(loserTeamCode);
        if (!Set.of(firstTeamCode, secondTeamCode)
                .equals(Set.of(winnerTeamCode, loserTeamCode))) {
            throw new IllegalArgumentException("Fixture winner/loser identity invariant");
        }
        validateScoreAndOrder(orderedGameReceipts, firstTeamCode, secondTeamCode,
                seriesFormat, firstTeamGameWins, secondTeamGameWins,
                winnerTeamCode, loserTeamCode);
        String expectedHash = LeagueIdentity.sha256(payloadText(
                schemaVersion, canonicalHashAlgorithm, seasonId, fixtureId, boundSeriesId,
                executionMode, firstTeamCode, secondTeamCode, game1BlueTeamCode,
                game1RedTeamCode, seriesFormat, fixtureRootSeed, fixtureRootSeedAlgorithm,
                gameSeedAlgorithm, scheduleIdentity, productDecisionHash,
                frozenSnapshotIdentity, firstTeamSnapshotIdentity,
                secondTeamSnapshotIdentity, playerResourceIdentity,
                championDraftResourceIdentity, matchupCompositionResourceIdentity,
                productionRuntimeIdentity, resourceProvenanceHash, orderedGameReceipts,
                firstTeamGameWins, secondTeamGameWins, winnerTeamCode, loserTeamCode,
                actualGameCount));
        if (canonicalFixtureReceiptHash == null) {
            canonicalFixtureReceiptHash = expectedHash;
        } else {
            LeagueSeasonFrozenSnapshot.requireSha256(
                    canonicalFixtureReceiptHash, "canonicalFixtureReceiptHash");
            if (!expectedHash.equals(canonicalFixtureReceiptHash)) {
                throw new IllegalArgumentException("Canonical fixture receipt hash mismatch");
            }
        }
        String completeCanonical = payloadText(
                schemaVersion, canonicalHashAlgorithm, seasonId, fixtureId, boundSeriesId,
                executionMode, firstTeamCode, secondTeamCode, game1BlueTeamCode,
                game1RedTeamCode, seriesFormat, fixtureRootSeed, fixtureRootSeedAlgorithm,
                gameSeedAlgorithm, scheduleIdentity, productDecisionHash,
                frozenSnapshotIdentity, firstTeamSnapshotIdentity,
                secondTeamSnapshotIdentity, playerResourceIdentity,
                championDraftResourceIdentity, matchupCompositionResourceIdentity,
                productionRuntimeIdentity, resourceProvenanceHash, orderedGameReceipts,
                firstTeamGameWins, secondTeamGameWins, winnerTeamCode, loserTeamCode,
                actualGameCount)
                + "canonicalFixtureReceiptHash=" + canonicalFixtureReceiptHash + '\n';
        if (completeCanonical.getBytes(StandardCharsets.UTF_8).length
                > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("League fixture receipt exceeds compact limit");
        }
    }

    public String canonicalText() {
        return payloadText(schemaVersion, canonicalHashAlgorithm, seasonId, fixtureId,
                boundSeriesId, executionMode, firstTeamCode, secondTeamCode,
                game1BlueTeamCode, game1RedTeamCode, seriesFormat, fixtureRootSeed,
                fixtureRootSeedAlgorithm, gameSeedAlgorithm, scheduleIdentity,
                productDecisionHash, frozenSnapshotIdentity, firstTeamSnapshotIdentity,
                secondTeamSnapshotIdentity, playerResourceIdentity,
                championDraftResourceIdentity, matchupCompositionResourceIdentity,
                productionRuntimeIdentity, resourceProvenanceHash, orderedGameReceipts,
                firstTeamGameWins, secondTeamGameWins, winnerTeamCode, loserTeamCode,
                actualGameCount)
                + "canonicalFixtureReceiptHash=" + canonicalFixtureReceiptHash + '\n';
    }

    public byte[] canonicalBytes() {
        return canonicalText().getBytes(StandardCharsets.UTF_8);
    }

    private static void validateScoreAndOrder(
            List<LeagueFixtureGameReceiptV1> games,
            String first,
            String second,
            SeriesFormat format,
            int firstWins,
            int secondWins,
            String winner,
            String loser
    ) {
        int countedFirst = 0;
        int countedSecond = 0;
        for (int index = 0; index < games.size(); index++) {
            LeagueFixtureGameReceiptV1 game = games.get(index);
            if (game.gameNumber() != index + 1 || game.winnerTeamCode() == null
                    || !Set.of(first, second).contains(game.winnerTeamCode())) {
                throw new IllegalArgumentException("Fixture game order/winner invariant");
            }
            if (game.winnerTeamCode().equals(first)) countedFirst++;
            else countedSecond++;
            boolean clinched = countedFirst == format.winsRequired()
                    || countedSecond == format.winsRequired();
            if (clinched != (index == games.size() - 1)) {
                throw new IllegalArgumentException("Fixture early termination invariant");
            }
        }
        String expectedWinner = countedFirst > countedSecond ? first : second;
        String expectedLoser = expectedWinner.equals(first) ? second : first;
        if (countedFirst != firstWins || countedSecond != secondWins
                || !expectedWinner.equals(winner) || !expectedLoser.equals(loser)
                || Math.max(firstWins, secondWins) != format.winsRequired()) {
            throw new IllegalArgumentException("Fixture final score invariant");
        }
    }

    private static String payloadText(
            String schemaVersion,
            String canonicalHashAlgorithm,
            String seasonId,
            String fixtureId,
            String boundSeriesId,
            LeagueFixtureExecutionMode executionMode,
            String firstTeamCode,
            String secondTeamCode,
            String game1BlueTeamCode,
            String game1RedTeamCode,
            SeriesFormat seriesFormat,
            long fixtureRootSeed,
            String fixtureRootSeedAlgorithm,
            String gameSeedAlgorithm,
            String scheduleIdentity,
            String productDecisionHash,
            String frozenSnapshotIdentity,
            String firstTeamSnapshotIdentity,
            String secondTeamSnapshotIdentity,
            String playerResourceIdentity,
            String championDraftResourceIdentity,
            String matchupCompositionResourceIdentity,
            String productionRuntimeIdentity,
            String resourceProvenanceHash,
            List<LeagueFixtureGameReceiptV1> games,
            int firstWins,
            int secondWins,
            String winner,
            String loser,
            int actualGameCount
    ) {
        StringBuilder value = new StringBuilder();
        append(value, "schemaVersion", schemaVersion);
        append(value, "canonicalHashAlgorithm", canonicalHashAlgorithm);
        append(value, "seasonId", seasonId);
        append(value, "fixtureId", fixtureId);
        append(value, "boundSeriesId", boundSeriesId);
        append(value, "executionMode", executionMode);
        append(value, "firstTeamCode", firstTeamCode);
        append(value, "secondTeamCode", secondTeamCode);
        append(value, "game1BlueTeamCode", game1BlueTeamCode);
        append(value, "game1RedTeamCode", game1RedTeamCode);
        append(value, "seriesFormat", seriesFormat);
        append(value, "fixtureRootSeed", fixtureRootSeed);
        append(value, "fixtureRootSeedAlgorithm", fixtureRootSeedAlgorithm);
        append(value, "gameSeedAlgorithm", gameSeedAlgorithm);
        append(value, "scheduleIdentity", scheduleIdentity);
        append(value, "productDecisionHash", productDecisionHash);
        append(value, "frozenSnapshotIdentity", frozenSnapshotIdentity);
        append(value, "firstTeamSnapshotIdentity", firstTeamSnapshotIdentity);
        append(value, "secondTeamSnapshotIdentity", secondTeamSnapshotIdentity);
        append(value, "playerResourceIdentity", playerResourceIdentity);
        append(value, "championDraftResourceIdentity", championDraftResourceIdentity);
        append(value, "matchupCompositionResourceIdentity", matchupCompositionResourceIdentity);
        append(value, "productionRuntimeIdentity", productionRuntimeIdentity);
        append(value, "resourceProvenanceHash", resourceProvenanceHash);
        for (LeagueFixtureGameReceiptV1 game : games) {
            append(value, "gameReceiptBegin", game.gameNumber());
            value.append(game.canonicalText());
            append(value, "gameReceiptEnd", game.gameNumber());
        }
        append(value, "firstTeamGameWins", firstWins);
        append(value, "secondTeamGameWins", secondWins);
        append(value, "winnerTeamCode", winner);
        append(value, "loserTeamCode", loser);
        append(value, "actualGameCount", actualGameCount);
        return value.toString();
    }

    private static void append(StringBuilder target, String field, Object value) {
        target.append(field).append('=').append(value).append('\n');
    }

    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isBlank() || result.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(field);
        }
        return result;
    }
}

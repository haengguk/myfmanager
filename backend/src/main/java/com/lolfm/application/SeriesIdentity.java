package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Canonical deterministic identities used by the lifecycle; it consumes no gameplay Random. */
public final class SeriesIdentity {
    public static final String GAME_SEED_ALGORITHM =
            "SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1";
    public static final String SERIES_ID_ALGORITHM =
            "SERIES_ID_SHA256_CANONICAL_CREATE_COMMAND_V1";

    private SeriesIdentity() {}

    public static String seriesId(String canonicalCreateCommand) {
        return "series_" + sha256(canonicalCreateCommand);
    }

    public static String gameId(String seriesId, int gameNumber) {
        return "game_" + sha256("gameIdSchema=SERIES_GAME_ID_V1\nseriesId=" + seriesId
                + "\ngameNumber=" + gameNumber + "\n");
    }

    public static String childId(String seriesId, int gameNumber, int generation) {
        return "draft_" + sha256("childIdSchema=SERIES_CHILD_DRAFT_ID_V1\nseriesId="
                + seriesId + "\ngameNumber=" + gameNumber + "\ngeneration=" + generation
                + "\n");
    }

    public static String reservationToken(
            String seriesId, int gameNumber, String commandId, long revision
    ) {
        return sha256("reservationSchema=SERIES_SIMULATION_RESERVATION_V1\nseriesId="
                + seriesId + "\ngameNumber=" + gameNumber + "\ncommandId=" + commandId
                + "\nrevision=" + revision + "\n");
    }

    public static long deriveGameSeed(
            String seriesId,
            String canonicalRootSeed,
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            String managedTeamCode,
            String historyBeforeHash
    ) {
        String canonical = "seedSchema=SERIES_GAME_SEED_V1\n"
                + "domain=LOL_MANAGER_SERIES_GAME_DRAFT_AND_MATCH_V1\n"
                + "seriesId=" + seriesId + '\n'
                + "rootSeed=" + canonicalRootSeed + '\n'
                + "seriesGameNumber=" + gameNumber + '\n'
                + "blueTeamCode=" + blueTeamCode + '\n'
                + "redTeamCode=" + redTeamCode + '\n'
                + "managedTeamCode=" + managedTeamCode + '\n'
                + "seriesHistoryBeforeHash=" + historyBeforeHash + '\n';
        byte[] digest = digest(canonical);
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    }

    public static String historyHash(int committedGames, Set<ChampionId> exclusions) {
        return RealDraftSelectionContextFactory.seriesHistoryHash(
                committedGames, Set.copyOf(exclusions));
    }

    public static String sha256(String canonical) {
        return HexFormat.of().formatHex(digest(canonical));
    }

    private static byte[] digest(String canonical) {
        if (canonical == null || !canonical.endsWith("\n")) {
            throw new IllegalArgumentException("Canonical identity requires trailing newline");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

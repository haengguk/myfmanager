package com.lolfm.career;

import com.lolfm.league.LeagueIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Versioned, stateless Career identifiers and hashes. It consumes no Random. */
public final class CareerIdentity {
    public static final String CAREER_SCHEMA = "CAREER_SAVE_V1";
    public static final String BINDING_SCHEMA = "CAREER_LEAGUE_BINDING_V1";
    public static final String COMMAND_SCHEMA = "CAREER_CREATE_COMMAND_RECEIPT_V1";
    public static final String SEED_ALGORITHM =
            "CAREER_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1";

    private CareerIdentity() {}

    public static String canonicalCommandId(String value) {
        if (value == null || !value.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("clientCommandId");
        }
        return UUID.fromString(value).toString().toLowerCase(Locale.ROOT);
    }

    public static String normalizeDisplayName(String value, String field) {
        if (value == null) throw new IllegalArgumentException(field);
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        int length = normalized.codePointCount(0, normalized.length());
        boolean control = normalized.codePoints().anyMatch(Character::isISOControl);
        if (length < 1 || length > 80 || control) {
            throw new IllegalArgumentException(field);
        }
        return normalized;
    }

    public static String careerId(String commandId) {
        return "career_" + sha256("careerIdSchema=CAREER_ID_V1\n"
                + "clientCommandId=" + canonicalCommandId(commandId) + '\n');
    }

    public static String leagueId(String careerId) {
        requireCareerId(careerId);
        return LeagueIdentity.leagueId("career-v1-" + careerId);
    }

    public static String seasonId(String careerId, String leagueId) {
        requireCareerId(careerId);
        if (!leagueId.equals(leagueId(careerId))) {
            throw new IllegalArgumentException("Career League identity mismatch");
        }
        return LeagueIdentity.seasonId(leagueId, "career-season-v1");
    }

    public static long rootSeed(String careerId) {
        requireCareerId(careerId);
        String canonical = "seedSchema=CAREER_ROOT_SEED_V1\n"
                + "algorithm=" + SEED_ALGORITHM + '\n'
                + "careerId=" + careerId + '\n';
        return ByteBuffer.wrap(digest(canonical)).getLong();
    }

    public static String createPayloadHash(
            String requestSchema,
            String saveName,
            String managerName,
            String managedTeamCode
    ) {
        return sha256("payloadSchema=CAREER_CREATE_PAYLOAD_V1\n"
                + "requestSchema=" + requestSchema + '\n'
                + "saveName=" + saveName + '\n'
                + "managerName=" + managerName + '\n'
                + "managedTeamCode=" + managedTeamCode + '\n');
    }

    public static String bindingHash(
            String careerId,
            String managedTeamCode,
            java.time.LocalDate startDate,
            java.time.LocalDate currentDate,
            String leagueId,
            String seasonId,
            long rootSeed,
            String frozenSnapshotHash,
            String productDecisionHash,
            String referenceCatalogVersion,
            String referenceCatalogHash
    ) {
        requireCareerId(careerId);
        return sha256("bindingSchema=" + BINDING_SCHEMA + '\n'
                + "careerId=" + careerId + '\n'
                + "managedTeamCode=" + managedTeamCode + '\n'
                + "startDate=" + startDate + '\n'
                + "currentDate=" + currentDate + '\n'
                + "leagueId=" + leagueId + '\n'
                + "seasonId=" + seasonId + '\n'
                + "seedAlgorithm=" + SEED_ALGORITHM + '\n'
                + "rootSeed=" + rootSeed + '\n'
                + "frozenSnapshotHash=" + frozenSnapshotHash + '\n'
                + "productDecisionHash=" + productDecisionHash + '\n'
                + "referenceCatalogVersion=" + referenceCatalogVersion + '\n'
                + "referenceCatalogHash=" + referenceCatalogHash + '\n');
    }

    public static void requireCareerId(String value) {
        if (value == null || !value.matches("career_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("careerId");
        }
    }

    public static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field);
        }
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}

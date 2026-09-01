package com.lolfm.league;

import com.lolfm.application.SeriesFormat;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical League identities and seeds. It is stateless and consumes no Random. */
public final class LeagueIdentity {
    public static final String FIXTURE_ROOT_SEED_ALGORITHM =
            "AI_LEAGUE_FIXTURE_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1";
    public static final String TIE_BREAK_DRAW_ALGORITHM =
            "AI_LEAGUE_STANDINGS_TIE_BREAK_SHA256_LEXICAL_V1";
    public static final String GAME_SEED_ALGORITHM =
            "AI_LEAGUE_BOUND_SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1";

    private static final Pattern TEAM_CODE = Pattern.compile("[A-Z0-9]{2,8}");
    private static final Pattern LEAGUE_ID = Pattern.compile("league_[0-9a-f]{64}");
    private static final Pattern SEASON_ID = Pattern.compile("season_[0-9a-f]{64}");

    private LeagueIdentity() {
    }

    public static String leagueId(String canonicalKey) {
        return "league_" + sha256("leagueIdSchema=AI_LEAGUE_ID_V1\nkey="
                + requireCanonicalKey(canonicalKey) + "\n");
    }

    public static String seasonId(String leagueId, String canonicalSeasonKey) {
        requireLeagueId(leagueId);
        return "season_" + sha256("seasonIdSchema=AI_LEAGUE_SEASON_ID_V1\nleagueId="
                + leagueId + "\nkey=" + requireCanonicalKey(canonicalSeasonKey) + "\n");
    }

    static String scheduleIdentity(
            String seasonId,
            LeagueSchedulePolicy policy,
            List<String> canonicalTeamCodes
    ) {
        requireSeasonId(seasonId);
        StringBuilder canonical = new StringBuilder(
                "scheduleSchema=AI_LEAGUE_SCHEDULE_IDENTITY_V1\n")
                .append("seasonId=").append(seasonId).append('\n')
                .append("policyId=").append(policy.policyId()).append('\n')
                .append("scheduleFormat=").append(policy.scheduleFormat()).append('\n')
                .append("seriesFormat=").append(policy.seriesFormat()).append('\n');
        canonicalTeamCodes.forEach(team -> canonical.append("team=").append(team).append('\n'));
        return sha256(canonical.toString());
    }

    static String fixtureId(
            String scheduleIdentity,
            int roundNumber,
            int legNumber,
            String pairId,
            String game1BlueTeamCode,
            String game1RedTeamCode,
            SeriesFormat format
    ) {
        LeagueSeasonFrozenSnapshot.requireSha256(scheduleIdentity, "scheduleIdentity");
        return "fixture_" + sha256("fixtureIdSchema=AI_LEAGUE_FIXTURE_ID_V1\n"
                + "scheduleIdentity=" + scheduleIdentity + '\n'
                + "roundNumber=" + roundNumber + '\n'
                + "legNumber=" + legNumber + '\n'
                + "pairId=" + pairId + '\n'
                + "game1BlueTeamCode=" + game1BlueTeamCode + '\n'
                + "game1RedTeamCode=" + game1RedTeamCode + '\n'
                + "seriesFormat=" + format + '\n');
    }

    static long fixtureRootSeed(
            String seasonId,
            long seasonRootSeed,
            String scheduleIdentity,
            String fixtureId,
            int roundNumber,
            int legNumber,
            String game1BlueTeamCode,
            String game1RedTeamCode
    ) {
        String canonical = "seedSchema=AI_LEAGUE_FIXTURE_ROOT_SEED_V1\n"
                + "algorithm=" + FIXTURE_ROOT_SEED_ALGORITHM + '\n'
                + "seasonId=" + seasonId + '\n'
                + "seasonRootSeed=" + seasonRootSeed + '\n'
                + "scheduleIdentity=" + scheduleIdentity + '\n'
                + "fixtureId=" + fixtureId + '\n'
                + "roundNumber=" + roundNumber + '\n'
                + "legNumber=" + legNumber + '\n'
                + "game1BlueTeamCode=" + game1BlueTeamCode + '\n'
                + "game1RedTeamCode=" + game1RedTeamCode + '\n';
        return ByteBuffer.wrap(digest(canonical)).getLong();
    }

    static String boundSeriesId(
            String fixtureId,
            long fixtureRootSeed,
            String firstTeamCode,
            String secondTeamCode,
            SeriesFormat format
    ) {
        return "series_" + sha256("createSchema=AI_LEAGUE_BOUND_SERIES_ID_V1\n"
                + "fixtureId=" + fixtureId + '\n'
                + "fixtureRootSeed=" + fixtureRootSeed + '\n'
                + "firstTeamCode=" + firstTeamCode + '\n'
                + "secondTeamCode=" + secondTeamCode + '\n'
                + "seriesFormat=" + format + '\n');
    }

    static long gameSeed(
            String boundSeriesId,
            long fixtureRootSeed,
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            String seedAnchorTeamCode,
            String historyBeforeHash
    ) {
        String canonical = "seedSchema=AI_LEAGUE_BOUND_SERIES_GAME_SEED_V1\n"
                + "algorithm=" + GAME_SEED_ALGORITHM + '\n'
                + "boundSeriesId=" + boundSeriesId + '\n'
                + "fixtureRootSeed=" + fixtureRootSeed + '\n'
                + "seriesGameNumber=" + gameNumber + '\n'
                + "blueTeamCode=" + blueTeamCode + '\n'
                + "redTeamCode=" + redTeamCode + '\n'
                + "seedAnchorTeamCode=" + seedAnchorTeamCode + '\n'
                + "seriesHistoryBeforeHash=" + historyBeforeHash + '\n';
        return ByteBuffer.wrap(digest(canonical)).getLong();
    }

    static String tieBreakCommonInputHash(long seasonRootSeed, List<String> tiedTeams) {
        StringBuilder canonical = new StringBuilder(
                "tieBreakSchema=AI_LEAGUE_STANDINGS_TIE_BREAK_COMMON_V1\n")
                .append("algorithm=").append(TIE_BREAK_DRAW_ALGORITHM).append('\n')
                .append("seasonRootSeed=").append(seasonRootSeed).append('\n');
        tiedTeams.forEach(team -> canonical.append("tiedTeam=").append(team).append('\n'));
        return sha256(canonical.toString());
    }

    static String tieBreakCandidateHash(
            long seasonRootSeed,
            List<String> tiedTeams,
            String candidateTeamCode
    ) {
        StringBuilder canonical = new StringBuilder(
                "tieBreakSchema=AI_LEAGUE_STANDINGS_TIE_BREAK_CANDIDATE_V1\n")
                .append("algorithm=").append(TIE_BREAK_DRAW_ALGORITHM).append('\n')
                .append("seasonRootSeed=").append(seasonRootSeed).append('\n');
        tiedTeams.forEach(team -> canonical.append("tiedTeam=").append(team).append('\n'));
        canonical.append("candidateTeam=").append(candidateTeamCode).append('\n');
        return sha256(canonical.toString());
    }

    public static String sha256(String canonical) {
        if (canonical == null || !canonical.endsWith("\n")) {
            throw new IllegalArgumentException("Canonical League identity requires trailing newline");
        }
        return HexFormat.of().formatHex(digest(canonical));
    }

    static String requireTeamCode(String teamCode) {
        if (teamCode == null || !TEAM_CODE.matcher(teamCode).matches()) {
            throw new IllegalArgumentException("Invalid canonical team code: " + teamCode);
        }
        return teamCode;
    }

    static void requireLeagueId(String value) {
        if (value == null || !LEAGUE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid League ID");
        }
    }

    static void requireSeasonId(String value) {
        if (value == null || !SEASON_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Season ID");
        }
    }

    private static String requireCanonicalKey(String value) {
        Objects.requireNonNull(value, "canonicalKey");
        if (value.isBlank() || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid canonical key");
        }
        return value;
    }

    private static byte[] digest(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}

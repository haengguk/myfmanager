package com.lolfm.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Raw-SHA-first loader for the LCK starters-only career, contract and honors resource. */
public final class PlayerCareerResourceLoader {
    public static final String RESOURCE =
            "/players/lck-player-career-contract-honors-2026-08-24-v1.json";
    public static final String VERSION =
            "lck-player-career-contract-honors-2026-08-24-v1";
    public static final String SNAPSHOT_AT = "2026-08-24";
    public static final String EXPECTED_SHA256 =
            "4e4f01fe72f68aca7dcb93afb72b43273201ce0daa7d63613f628597ff41ff19";
    public static final int TEAM_COUNT = 10;
    public static final int PLAYER_COUNT = 50;
    public static final int STARTERS_PER_TEAM = 5;
    public static final int TEAM_HISTORY_COUNT = 248;
    public static final int TEAM_ACHIEVEMENT_COUNT = 154;
    public static final int INDIVIDUAL_AWARD_COUNT = 21;
    public static final int SOURCE_COUNT = 248;
    public static final int PLAYERS_WITH_MAJOR_HONORS = 43;

    public static final String CONTRACT_SEMANTICS =
            "Only the publicly listed current contract expiration date is included; salary, start date, buyout, and inferred term are excluded.";
    public static final String CAREER_SEMANTICS =
            "Public team history with source date precision retained.";
    public static final String HONORS_SEMANTICS =
            "Major public team achievements and individual awards; not every minor tournament or weekly award.";
    public static final String PRIZE_MONEY_SEMANTICS =
            "Approximate public cumulative tournament winnings in USD; not salary, bonus, buyout, or market value.";
    public static final String AGE_SEMANTICS = "Completed age as of 2026-08-24.";
    private static final String IDENTITY_SOURCE_PATH =
            "lck-player-identities-2026-08-21-v1.json";

    private PlayerCareerResourceLoader() {
    }

    public static LoadedResource loadDefault() {
        return load(new ObjectMapper(),
                PlayerCareerResourceLoader.class.getResourceAsStream(RESOURCE));
    }

    public static LoadedResource load(ObjectMapper mapper, InputStream input) {
        return load(mapper, input, EXPECTED_SHA256);
    }

    static LoadedResource load(ObjectMapper mapper, InputStream input, String expectedSha256) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        byte[] bytes = readBytes(input);
        String sha256 = sha256(bytes);
        if (!expectedSha256.equals(sha256)) {
            throw new IllegalStateException("Player career resource SHA-256 mismatch: " + sha256);
        }

        RawResource raw;
        try {
            raw = mapper.readValue(bytes, RawResource.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load player career resource", error);
        }
        validateEnvelope(raw);

        LinkedHashMap<PlayerId, PlayerCareerResource> byId = new LinkedHashMap<>();
        Map<String, Set<Position>> positionsByTeam = new HashMap<>();
        int teamHistoryCount = 0;
        int teamAchievementCount = 0;
        int individualAwardCount = 0;
        int sourceCount = 0;
        int playersWithMajorHonors = 0;
        for (RawPlayer player : raw.players()) {
            PlayerCareerResource mapped = mapPlayer(player);
            if (byId.putIfAbsent(mapped.playerId(), mapped) != null) {
                throw new IllegalStateException("Duplicate career PlayerId: " + mapped.playerId());
            }
            positionsByTeam.computeIfAbsent(mapped.teamCode(), ignored -> new HashSet<>())
                    .add(mapped.position());
            teamHistoryCount += mapped.career().teamHistory().size();
            teamAchievementCount += mapped.honors().teamAchievements().size();
            individualAwardCount += mapped.honors().individualAwards().size();
            sourceCount += mapped.sources().size();
            if (!mapped.honors().teamAchievements().isEmpty()
                    || !mapped.honors().individualAwards().isEmpty()) {
                playersWithMajorHonors++;
            }
        }

        Counts counts = new Counts(byId.size(), positionsByTeam.size(), byId.keySet().size(),
                teamHistoryCount, teamAchievementCount, individualAwardCount, sourceCount,
                playersWithMajorHonors);
        validateMeasured(raw.scope(), positionsByTeam, counts);
        return new LoadedResource(raw.version(), raw.snapshotAt(), sha256,
                new Scope(raw.scope().league(), raw.scope().teams(), raw.scope().players(),
                        raw.scope().startersPerTeam(), raw.scope().startersOnly(),
                        raw.scope().salaryIncluded(), raw.scope().marketValueIncluded()),
                new Semantics(raw.semantics().contract(), raw.semantics().career(),
                        raw.semantics().honors(), raw.semantics().prizeMoney(),
                        raw.semantics().age()), counts, List.copyOf(byId.values()));
    }

    private static void validateEnvelope(RawResource raw) {
        if (raw == null) throw new IllegalStateException("Player career resource is empty");
        if (!VERSION.equals(raw.version())) {
            throw new IllegalStateException("Unsupported player career version: " + raw.version());
        }
        if (!SNAPSHOT_AT.equals(raw.snapshotAt())) {
            throw new IllegalStateException("Player career snapshotAt mismatch: " + raw.snapshotAt());
        }
        RawScope scope = raw.scope();
        if (scope == null || !"LCK".equals(scope.league()) || scope.teams() != TEAM_COUNT
                || scope.players() != PLAYER_COUNT
                || scope.startersPerTeam() != STARTERS_PER_TEAM || !scope.startersOnly()
                || scope.salaryIncluded() || scope.marketValueIncluded()) {
            throw new IllegalStateException("Player career scope mismatch");
        }
        RawSemantics semantics = raw.semantics();
        if (semantics == null || !CONTRACT_SEMANTICS.equals(semantics.contract())
                || !CAREER_SEMANTICS.equals(semantics.career())
                || !HONORS_SEMANTICS.equals(semantics.honors())
                || !PRIZE_MONEY_SEMANTICS.equals(semantics.prizeMoney())
                || !AGE_SEMANTICS.equals(semantics.age())) {
            throw new IllegalStateException("Player career semantics mismatch");
        }
        if (raw.players() == null || raw.players().size() != PLAYER_COUNT) {
            throw new IllegalStateException("Player career player count mismatch");
        }
    }

    private static PlayerCareerResource mapPlayer(RawPlayer raw) {
        if (raw == null || raw.position() == null) {
            throw new IllegalStateException("Incomplete player career identity");
        }
        PlayerId playerId;
        try {
            playerId = new PlayerId(required(raw.playerId(), "playerId"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Malformed career PlayerId", error);
        }
        String teamCode = required(raw.team(), "team");
        if (!teamCode.matches("[A-Z0-9]+")) {
            throw new IllegalStateException("Invalid current team code: " + teamCode);
        }
        if (!SNAPSHOT_AT.equals(raw.snapshotAt())) {
            throw new IllegalStateException("Career player snapshot mismatch: " + playerId);
        }
        validatePersonal(raw.personal(), playerId);
        validateContract(raw.contract(), playerId);
        validateCareer(raw.career(), playerId);
        validateHonors(raw.honors(), playerId);
        validatePrizeMoney(raw.careerPrizeMoney(), playerId);
        validateSources(raw.sources(), playerId);
        validateDataQuality(raw.dataQuality(), playerId);

        return new PlayerCareerResource(playerId, required(raw.nickname(), "nickname"),
                teamCode, raw.position(), raw.snapshotAt(),
                new PlayerCareerResource.Personal(raw.personal().legalName(),
                        raw.personal().birthDate(), raw.personal().ageAsOfSnapshot(),
                        raw.personal().nationality()),
                new PlayerCareerResource.Contract(raw.contract().endDate(),
                        raw.contract().daysRemainingAsOfSnapshot(), raw.contract().status(),
                        raw.contract().sourceType(), raw.contract().sourceSnapshotAt(),
                        raw.contract().checkedAt()),
                new PlayerCareerResource.Career(raw.career().debutDate(),
                        raw.career().yearsActiveAsOfSnapshot(),
                        raw.career().teamHistory().stream().map(value ->
                                new PlayerCareerResource.TeamHistory(value.team(), value.from(),
                                        value.to(), value.role(), value.datePrecision())).toList(),
                        raw.career().coverage()),
                new PlayerCareerResource.Honors(
                        raw.honors().teamAchievements().stream().map(value ->
                                new PlayerCareerResource.TeamAchievement(value.season(),
                                        value.competition(), value.team(), value.result(),
                                        value.sourceUrl())).toList(),
                        raw.honors().individualAwards().stream().map(value ->
                                new PlayerCareerResource.IndividualAward(value.season(),
                                        value.award(), value.competition(),
                                        value.sourceUrl())).toList(), raw.honors().coverage()),
                new PlayerCareerResource.CareerPrizeMoney(raw.careerPrizeMoney().amountUsd(),
                        raw.careerPrizeMoney().status(), raw.careerPrizeMoney().sourceType(),
                        raw.careerPrizeMoney().checkedAt()),
                raw.sources().stream().map(value -> new PlayerCareerResource.Source(
                        value.type(), value.path(), value.url(), value.checkedAt(),
                        value.sourceSnapshotAt())).toList(),
                new PlayerCareerResource.DataQuality(raw.dataQuality().personal(),
                        raw.dataQuality().contract(), raw.dataQuality().career(),
                        raw.dataQuality().honors(), raw.dataQuality().prizeMoney()));
    }

    private static void validatePersonal(RawPersonal value, PlayerId playerId) {
        if (value == null || value.nationality() == null || value.nationality().isEmpty()) {
            throw new IllegalStateException("Incomplete personal data: " + playerId);
        }
        LocalDate birthDate = date(value.birthDate(), "birthDate");
        int expectedAge = Period.between(birthDate, LocalDate.parse(SNAPSHOT_AT)).getYears();
        if (value.ageAsOfSnapshot() != expectedAge || value.ageAsOfSnapshot() < 0) {
            throw new IllegalStateException("Snapshot age mismatch: " + playerId);
        }
        required(value.legalName(), "legalName");
        value.nationality().forEach(item -> required(item, "nationality"));
    }

    private static void validateContract(RawContract value, PlayerId playerId) {
        if (value == null) throw new IllegalStateException("Missing contract: " + playerId);
        LocalDate end = date(value.endDate(), "contract.endDate");
        int expectedDays = Math.toIntExact(ChronoUnit.DAYS.between(
                LocalDate.parse(SNAPSHOT_AT), end));
        if (value.daysRemainingAsOfSnapshot() != expectedDays || expectedDays < 0) {
            throw new IllegalStateException("Snapshot contract days mismatch: " + playerId);
        }
        required(value.status(), "contract.status");
        required(value.sourceType(), "contract.sourceType");
        date(value.sourceSnapshotAt(), "contract.sourceSnapshotAt");
        date(value.checkedAt(), "contract.checkedAt");
    }

    private static void validateCareer(RawCareer value, PlayerId playerId) {
        if (value == null || value.yearsActiveAsOfSnapshot() == null
                || value.yearsActiveAsOfSnapshot().signum() < 0
                || value.teamHistory() == null || value.teamHistory().isEmpty()) {
            throw new IllegalStateException("Incomplete career: " + playerId);
        }
        date(value.debutDate(), "career.debutDate");
        required(value.coverage(), "career.coverage");
        for (RawTeamHistory item : value.teamHistory()) {
            if (item == null || item.role() == null) {
                throw new IllegalStateException("Incomplete team history: " + playerId);
            }
            required(item.team(), "teamHistory.team");
            date(item.from(), "teamHistory.from");
            if (item.to() != null) date(item.to(), "teamHistory.to");
            required(item.datePrecision(), "teamHistory.datePrecision");
        }
    }

    private static void validateHonors(RawHonors value, PlayerId playerId) {
        if (value == null || value.teamAchievements() == null
                || value.individualAwards() == null) {
            throw new IllegalStateException("Incomplete honors: " + playerId);
        }
        required(value.coverage(), "honors.coverage");
        for (RawTeamAchievement item : value.teamAchievements()) {
            if (item == null) throw new IllegalStateException("Null team achievement: " + playerId);
            required(item.season(), "achievement.season");
            required(item.competition(), "achievement.competition");
            required(item.team(), "achievement.team");
            required(item.result(), "achievement.result");
            optional(item.sourceUrl(), "achievement.sourceUrl");
        }
        for (RawIndividualAward item : value.individualAwards()) {
            if (item == null) throw new IllegalStateException("Null individual award: " + playerId);
            required(item.season(), "award.season");
            required(item.award(), "award.award");
            required(item.competition(), "award.competition");
            optional(item.sourceUrl(), "award.sourceUrl");
        }
    }

    private static void validatePrizeMoney(RawCareerPrizeMoney value, PlayerId playerId) {
        if (value == null || value.amountUsd() == null || value.amountUsd().signum() < 0) {
            throw new IllegalStateException("Incomplete prize money: " + playerId);
        }
        required(value.status(), "careerPrizeMoney.status");
        required(value.sourceType(), "careerPrizeMoney.sourceType");
        date(value.checkedAt(), "careerPrizeMoney.checkedAt");
    }

    private static void validateSources(List<RawSource> values, PlayerId playerId) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("Missing sources: " + playerId);
        }
        int identitySources = 0;
        for (RawSource value : values) {
            if (value == null) throw new IllegalStateException("Null source: " + playerId);
            required(value.type(), "source.type");
            date(value.checkedAt(), "source.checkedAt");
            if ((value.path() == null) == (value.url() == null)) {
                throw new IllegalStateException("Source must have exactly one path or URL: " + playerId);
            }
            optional(value.path(), "source.path");
            optional(value.url(), "source.url");
            if (value.sourceSnapshotAt() != null) {
                date(value.sourceSnapshotAt(), "source.sourceSnapshotAt");
            }
            if ("USER_PROVIDED_ROSTER_SNAPSHOT".equals(value.type())
                    && IDENTITY_SOURCE_PATH.equals(value.path())) {
                identitySources++;
            }
        }
        if (identitySources != 1) {
            throw new IllegalStateException("Career identity prerequisite mismatch: " + playerId);
        }
    }

    private static void validateDataQuality(RawDataQuality value, PlayerId playerId) {
        if (value == null) throw new IllegalStateException("Missing data quality: " + playerId);
        required(value.personal(), "dataQuality.personal");
        required(value.contract(), "dataQuality.contract");
        required(value.career(), "dataQuality.career");
        required(value.honors(), "dataQuality.honors");
        required(value.prizeMoney(), "dataQuality.prizeMoney");
    }

    private static void validateMeasured(RawScope scope,
                                         Map<String, Set<Position>> positionsByTeam,
                                         Counts counts) {
        if (counts.playerCount() != PLAYER_COUNT || counts.teamCount() != TEAM_COUNT
                || counts.uniquePlayerIdCount() != PLAYER_COUNT
                || counts.teamHistoryCount() != TEAM_HISTORY_COUNT
                || counts.teamAchievementCount() != TEAM_ACHIEVEMENT_COUNT
                || counts.individualAwardCount() != INDIVIDUAL_AWARD_COUNT
                || counts.sourceCount() != SOURCE_COUNT
                || counts.playersWithMajorHonors() != PLAYERS_WITH_MAJOR_HONORS) {
            throw new IllegalStateException("Measured player career counts mismatch: " + counts);
        }
        for (Map.Entry<String, Set<Position>> entry : positionsByTeam.entrySet()) {
            if (entry.getValue().size() != scope.startersPerTeam()
                    || !EnumSet.copyOf(entry.getValue()).equals(EnumSet.allOf(Position.class))) {
                throw new IllegalStateException(
                        "Career team does not have every position: " + entry.getKey());
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing " + field);
        }
        return value.trim();
    }

    private static void optional(String value, String field) {
        if (value != null) required(value, field);
    }

    private static LocalDate date(String value, String field) {
        try {
            return LocalDate.parse(required(value, field));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Invalid " + field, error);
        }
    }

    private static byte[] readBytes(InputStream input) {
        if (input == null) {
            throw new IllegalStateException("Player career resource not found: " + RESOURCE);
        }
        try (input) {
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read player career resource", error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public record LoadedResource(
            String version,
            String snapshotAt,
            String resourceSha256,
            Scope scope,
            Semantics semantics,
            Counts counts,
            List<PlayerCareerResource> players
    ) {
        public LoadedResource {
            players = List.copyOf(players);
        }
    }

    public record Scope(
            String league,
            int teams,
            int players,
            int startersPerTeam,
            boolean startersOnly,
            boolean salaryIncluded,
            boolean marketValueIncluded
    ) {
    }

    public record Semantics(
            String contract,
            String career,
            String honors,
            String prizeMoney,
            String age
    ) {
    }

    public record Counts(
            int playerCount,
            int teamCount,
            int uniquePlayerIdCount,
            int teamHistoryCount,
            int teamAchievementCount,
            int individualAwardCount,
            int sourceCount,
            int playersWithMajorHonors
    ) {
    }

    private record RawResource(String version, String snapshotAt, RawScope scope,
                               RawSemantics semantics, List<RawPlayer> players) {
    }

    private record RawScope(String league, int teams, int players, int startersPerTeam,
                            boolean startersOnly, boolean salaryIncluded,
                            boolean marketValueIncluded) {
    }

    private record RawSemantics(String contract, String career, String honors,
                                String prizeMoney, String age) {
    }

    private record RawPlayer(String playerId, String nickname, String team, Position position,
                             String snapshotAt, RawPersonal personal, RawContract contract,
                             RawCareer career, RawHonors honors,
                             RawCareerPrizeMoney careerPrizeMoney, List<RawSource> sources,
                             RawDataQuality dataQuality) {
    }

    private record RawPersonal(String legalName, String birthDate, int ageAsOfSnapshot,
                               List<String> nationality) {
    }

    private record RawContract(String endDate, int daysRemainingAsOfSnapshot, String status,
                               String sourceType, String sourceSnapshotAt, String checkedAt) {
    }

    private record RawCareer(String debutDate, BigDecimal yearsActiveAsOfSnapshot,
                             List<RawTeamHistory> teamHistory, String coverage) {
    }

    private record RawTeamHistory(String team, String from, String to, Position role,
                                  String datePrecision) {
    }

    private record RawHonors(List<RawTeamAchievement> teamAchievements,
                             List<RawIndividualAward> individualAwards, String coverage) {
    }

    private record RawTeamAchievement(String season, String competition, String team,
                                      String result, String sourceUrl) {
    }

    private record RawIndividualAward(String season, String award, String competition,
                                      String sourceUrl) {
    }

    private record RawCareerPrizeMoney(BigDecimal amountUsd, String status, String sourceType,
                                       String checkedAt) {
    }

    private record RawSource(String type, String path, String url, String checkedAt,
                             String sourceSnapshotAt) {
    }

    private record RawDataQuality(String personal, String contract, String career, String honors,
                                  String prizeMoney) {
    }
}

package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Raw-SHA-first loader and cross-catalog validator for the sparse LCK proficiency population. */
public final class ChampionProficiencyResourceLoader {
    public static final String RESOURCE = "/players/lck-champion-proficiency-2026-08-21-v1.json";
    public static final String VERSION = "lck-champion-proficiency-2026-08-21-v1";
    public static final String RESEARCH_AS_OF = "2026-08-21";
    public static final String IDENTITY_SEMANTICS =
            "PlayerRatingKey(teamCode, Position) x ChampionRoleKey(championId, same Position)";
    public static final String REQUIRED_CHAMPION_POOL_VERSION = "full-173-2026-08-v1";
    public static final int REQUIRED_LEGAL_ROLE_KEY_COUNT = 216;
    public static final String EXPECTED_SHA256 =
            "2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad";

    private ChampionProficiencyResourceLoader() { }

    public static LoadedResource loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
        ChampionCatalog champions = new ChampionCatalog(mapper);
        return load(mapper, ChampionProficiencyResourceLoader.class.getResourceAsStream(RESOURCE),
                ratings, champions);
    }

    public static LoadedResource load(ObjectMapper mapper, InputStream input,
                                      PlayerRatingCatalog ratings, ChampionCatalog champions) {
        return load(mapper, input, EXPECTED_SHA256, ratings, champions);
    }

    static LoadedResource load(ObjectMapper mapper, InputStream input, String expectedSha256,
                               PlayerRatingCatalog ratings, ChampionCatalog champions) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        Objects.requireNonNull(ratings, "ratings");
        Objects.requireNonNull(champions, "champions");
        byte[] bytes = readBytes(input);
        String sha256 = sha256(bytes);
        if (!expectedSha256.equals(sha256)) {
            throw new IllegalStateException("Champion proficiency resource SHA-256 mismatch: " + sha256);
        }

        RawResource raw;
        try {
            raw = mapper.readValue(bytes, RawResource.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load champion proficiency resource", error);
        }
        validateEnvelope(raw, ratings, champions);

        Map<PlayerRatingKey, RawPlayer> rawBySubject = indexSubjects(raw.players());
        validateSubjectSet(rawBySubject, ratings);

        Map<PlayerId, ChampionProficiencies> byPlayerId = new LinkedHashMap<>();
        List<ChampionProficiencyEntry> entries = new ArrayList<>();
        Map<Integer, Integer> distribution = new HashMap<>();
        int highCount = 0;
        int eliteCount = 0;
        int worldBenchmarkCount = 0;
        for (Map.Entry<PlayerRatingKey, RawPlayer> subject : rawBySubject.entrySet()) {
            PlayerRatingKey ratingKey = subject.getKey();
            RawPlayer rawPlayer = subject.getValue();
            PlayerRatingResource rating = ratings.get(ratingKey);
            PlayerIdentity identity = ratings.identities().get(ratingKey);
            if (!rating.nickname().equals(rawPlayer.nickname())
                    || !identity.nickname().equals(rawPlayer.nickname())) {
                throw new IllegalStateException("Proficiency subject nickname mismatch: " + ratingKey.stableId());
            }

            Map<ChampionRoleKey, Integer> values = new LinkedHashMap<>();
            if (rawPlayer.proficiencies() == null) {
                throw new IllegalStateException("Missing proficiency list: " + ratingKey.stableId());
            }
            for (RawProficiency rawEntry : rawPlayer.proficiencies()) {
                ChampionRoleKey roleKey = new ChampionRoleKey(
                        new ChampionId(rawEntry.championId()), rawEntry.position());
                validateEntry(ratingKey, roleKey, rawEntry.value(), champions);
                if (values.putIfAbsent(roleKey, rawEntry.value()) != null) {
                    throw new IllegalStateException("Duplicate proficiency subject-role key: "
                            + ratingKey.stableId() + "/" + roleKey.stableId());
                }
                entries.add(new ChampionProficiencyEntry(identity.playerId(), ratingKey, roleKey,
                        rawEntry.value()));
                distribution.merge(rawEntry.value(), 1, Integer::sum);
                if (rawEntry.value() >= raw.semantics().highProficiencyThreshold()) highCount++;
                if (rawEntry.value() >= 19) eliteCount++;
                if (rawEntry.value() == 20) worldBenchmarkCount++;
            }
            if (byPlayerId.putIfAbsent(identity.playerId(), new ChampionProficiencies(values)) != null) {
                throw new IllegalStateException("Duplicate proficiency PlayerId: " + identity.playerId());
            }
        }

        int potentialKeys = rawBySubject.keySet().stream()
                .mapToInt(key -> champions.forPosition(key.position()).size()).sum();
        int authoredCount = entries.size();
        int fallbackCount = potentialKeys - authoredCount;
        ChampionProficiencyPopulationMetrics metrics = new ChampionProficiencyPopulationMetrics(
                raw.scope().teams(), rawBySubject.size(), champions.legalRoleKeys().size(), potentialKeys,
                authoredCount, fallbackCount, distribution, highCount, eliteCount,
                worldBenchmarkCount, raw.authoringSummary().scopeInexpressibleEvidenceCount());
        validateMeasuredSummary(raw, metrics);

        return new LoadedResource(raw.version(), raw.researchAsOf(), sha256,
                raw.requiredPlayerRatingResourceVersion(), raw.requiredChampionPoolVersion(),
                raw.requiredLegalRoleKeyCount(), raw.semantics().highProficiencyThreshold(),
                metrics, Map.copyOf(byPlayerId), List.copyOf(entries));
    }

    private static void validateEnvelope(RawResource raw, PlayerRatingCatalog ratings,
                                         ChampionCatalog champions) {
        if (raw == null) throw new IllegalStateException("Champion proficiency resource is empty");
        if (!VERSION.equals(raw.version())) {
            throw new IllegalStateException("Unsupported champion proficiency version: " + raw.version());
        }
        if (!RESEARCH_AS_OF.equals(raw.researchAsOf())) {
            throw new IllegalStateException("Champion proficiency researchAsOf mismatch");
        }
        if (!ratings.version().equals(raw.requiredPlayerRatingResourceVersion())
                || !PlayerRatingResourceLoader.VERSION.equals(raw.requiredPlayerRatingResourceVersion())) {
            throw new IllegalStateException("Champion proficiency rating prerequisite mismatch");
        }
        if (!champions.championPoolVersion().equals(raw.requiredChampionPoolVersion())
                || !REQUIRED_CHAMPION_POOL_VERSION.equals(raw.requiredChampionPoolVersion())) {
            throw new IllegalStateException("Champion proficiency pool prerequisite mismatch");
        }
        int legalRoleCount = champions.legalRoleKeys().size();
        if (raw.requiredLegalRoleKeyCount() != legalRoleCount
                || legalRoleCount != REQUIRED_LEGAL_ROLE_KEY_COUNT) {
            throw new IllegalStateException("Champion proficiency legal-role prerequisite mismatch: "
                    + legalRoleCount);
        }
        if (raw.scale() == null || raw.scale().min() != 1 || raw.scale().max() != 20
                || raw.scale().neutralFallback() != ChampionProficiencies.NEUTRAL) {
            throw new IllegalStateException("Champion proficiency scale/neutral fallback mismatch");
        }
        RawSemantics semantics = raw.semantics();
        if (semantics == null || !IDENTITY_SEMANTICS.equals(semantics.identity())) {
            throw new IllegalStateException("Champion proficiency identity semantics mismatch");
        }
        if (!semantics.sparseOverridesOnly()
                || !"ChampionProficiencies.NEUTRAL_14".equals(semantics.omittedLegalRoleBehavior())
                || semantics.highProficiencyThreshold() != 17
                || !semantics.playerRatingsSeparate() || !semantics.draftMetaSeparate()
                || !semantics.absenceOfPlayDoesNotImplyLowProficiency()
                || semantics.belowNeutralOverridesAuthoredInV1()) {
            throw new IllegalStateException("Champion proficiency semantics envelope mismatch");
        }
        RawScope scope = raw.scope();
        if (scope == null || !"LCK".equals(scope.league()) || scope.teams() != 10
                || scope.players() != 50 || scope.substitutesIncluded()) {
            throw new IllegalStateException("Champion proficiency scope mismatch");
        }
        if (raw.players() == null || raw.players().size() != scope.players()) {
            throw new IllegalStateException("Champion proficiency player count mismatch");
        }
        if (raw.authoringSummary() == null) {
            throw new IllegalStateException("Champion proficiency authoring summary is required");
        }
    }

    private static Map<PlayerRatingKey, RawPlayer> indexSubjects(List<RawPlayer> players) {
        Map<PlayerRatingKey, RawPlayer> result = new LinkedHashMap<>();
        for (RawPlayer player : players) {
            if (player == null || player.team() == null || player.position() == null
                    || player.nickname() == null) {
                throw new IllegalStateException("Incomplete proficiency subject");
            }
            PlayerRatingKey key = new PlayerRatingKey(player.team(), player.position());
            if (result.putIfAbsent(key, player) != null) {
                throw new IllegalStateException("Duplicate proficiency PlayerRatingKey: " + key.stableId());
            }
        }
        return result;
    }

    private static void validateSubjectSet(Map<PlayerRatingKey, RawPlayer> rawBySubject,
                                           PlayerRatingCatalog ratings) {
        Set<PlayerRatingKey> expected = ratings.all().stream().map(PlayerRatingResource::playerKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<PlayerRatingKey> actual = rawBySubject.keySet();
        Set<PlayerRatingKey> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        Set<PlayerRatingKey> unknown = new HashSet<>(actual);
        unknown.removeAll(expected);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalStateException("Proficiency/rating subject mismatch; missing=" + missing
                    + " unknown=" + unknown);
        }
    }

    private static void validateEntry(PlayerRatingKey ratingKey, ChampionRoleKey roleKey, int value,
                                      ChampionCatalog champions) {
        if (ratingKey.position() != roleKey.position()) {
            throw new IllegalStateException("INVALID_SUBJECT_ROLE_BINDING: " + ratingKey.stableId()
                    + "/" + roleKey.stableId());
        }
        if (!champions.supports(roleKey)) {
            throw new IllegalStateException("Illegal authored ChampionRoleKey: " + roleKey.stableId());
        }
        if (value < 1 || value > 20) {
            throw new IllegalStateException("Authored proficiency outside 1..20: " + value);
        }
        if (value < 15 || value > 20) {
            throw new IllegalStateException("V1 authored proficiency outside 15..20: " + value);
        }
    }

    private static void validateMeasuredSummary(RawResource raw,
                                                ChampionProficiencyPopulationMetrics metrics) {
        RawScope scope = raw.scope();
        if (scope.potentialPlayerRoleKeysAtRosterPositions() != metrics.potentialPlayerRoleKeyCount()
                || scope.authoredOverrides() != metrics.authoredOverrideCount()
                || scope.neutralFallbackKeys() != metrics.neutralFallbackKeyCount()) {
            throw new IllegalStateException("Measured proficiency scope counts do not match resource envelope");
        }
        Map<Integer, Integer> declaredDistribution = new HashMap<>();
        raw.authoringSummary().scoreDistribution().forEach((key, value) -> {
            try {
                declaredDistribution.put(Integer.parseInt(key), value);
            } catch (NumberFormatException error) {
                throw new IllegalStateException("Invalid proficiency score distribution key: " + key, error);
            }
        });
        if (!declaredDistribution.equals(metrics.scoreDistribution())
                || raw.authoringSummary().highProficiency17PlusCount() != metrics.highProficiencyCount()
                || raw.authoringSummary().elite19PlusCount() != metrics.eliteProficiencyCount()
                || raw.authoringSummary().worldBenchmark20Count() != metrics.worldBenchmarkCount()) {
            throw new IllegalStateException("Measured proficiency distribution does not match resource envelope");
        }
    }

    private static byte[] readBytes(InputStream input) {
        if (input == null) {
            throw new IllegalStateException("Champion proficiency resource not found: " + RESOURCE);
        }
        try (input) {
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read champion proficiency resource", error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public record LoadedResource(
            String version,
            String researchAsOf,
            String resourceSha256,
            String requiredPlayerRatingResourceVersion,
            String requiredChampionPoolVersion,
            int requiredLegalRoleKeyCount,
            int highProficiencyThreshold,
            ChampionProficiencyPopulationMetrics metrics,
            Map<PlayerId, ChampionProficiencies> proficiencies,
            List<ChampionProficiencyEntry> authoredEntries
    ) {
        public LoadedResource {
            proficiencies = Map.copyOf(proficiencies);
            authoredEntries = List.copyOf(authoredEntries);
        }
    }

    private record RawResource(
            String version,
            String researchAsOf,
            String requiredPlayerRatingResourceVersion,
            String requiredChampionPoolVersion,
            int requiredLegalRoleKeyCount,
            RawScale scale,
            RawSemantics semantics,
            RawScope scope,
            RawAuthoringSummary authoringSummary,
            List<RawPlayer> players
    ) { }
    private record RawScale(int min, int max, int neutralFallback) { }
    private record RawSemantics(
            String identity,
            boolean sparseOverridesOnly,
            String omittedLegalRoleBehavior,
            int highProficiencyThreshold,
            boolean playerRatingsSeparate,
            boolean draftMetaSeparate,
            boolean absenceOfPlayDoesNotImplyLowProficiency,
            boolean belowNeutralOverridesAuthoredInV1
    ) { }
    private record RawScope(String league, int teams, int players, boolean substitutesIncluded,
                            int potentialPlayerRoleKeysAtRosterPositions, int authoredOverrides,
                            int neutralFallbackKeys) { }
    private record RawAuthoringSummary(Map<String, Integer> scoreDistribution,
                                       int highProficiency17PlusCount, int elite19PlusCount,
                                       int worldBenchmark20Count, int scopeInexpressibleEvidenceCount,
                                       String freshnessCaveat) { }
    private record RawPlayer(String team, String nickname, Position position,
                             List<RawProficiency> proficiencies) { }
    private record RawProficiency(String championId, Position position, int value) { }
}

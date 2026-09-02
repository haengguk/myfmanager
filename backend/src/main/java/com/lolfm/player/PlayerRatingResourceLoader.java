package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic loader and structural validator for the authored player-rating resource. */
public final class PlayerRatingResourceLoader {
    public static final String RESOURCE = "/players/lck-player-ratings-2026-08-19-v1.json";
    public static final String VERSION = "lck-player-ratings-2026-08-19-v1";
    public static final String SNAPSHOT_AT = "2026-08-19T02:57:00+09:00";
    public static final String DATA_CUTOFF = "2026-08-16";
    public static final String EXPECTED_SHA256 =
            "2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0";

    private static final Set<String> COMMON_JSON_ATTRIBUTES = Set.of(
            "mechanics", "decisionMaking", "mapAwareness", "positioning", "combatExecution", "consistency");
    private static final Map<String, PlayerSkill> JSON_TO_SKILL = jsonSkillMap();

    private PlayerRatingResourceLoader() { }

    public static LoadedResource loadDefault() {
        return load(new ObjectMapper(), PlayerRatingResourceLoader.class.getResourceAsStream(RESOURCE));
    }

    public static LoadedResource load(ObjectMapper mapper, InputStream input) {
        Objects.requireNonNull(mapper, "mapper");
        byte[] bytes = readBytes(input);
        String sha256 = sha256(bytes);
        if (!EXPECTED_SHA256.equals(sha256)) {
            throw new IllegalStateException("Player rating resource SHA-256 mismatch: " + sha256);
        }

        RawResource raw;
        try {
            raw = mapper.readValue(bytes, RawResource.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load player rating resource", error);
        }
        validateEnvelope(raw);

        Map<PlayerRatingKey, PlayerRatingResource> indexed = new LinkedHashMap<>();
        Map<String, Set<Position>> positionsByTeam = new HashMap<>();
        for (RawPlayer player : raw.players()) {
            PlayerRatingKey key = new PlayerRatingKey(player.team(), player.position());
            if (indexed.containsKey(key)) {
                throw new IllegalStateException("Duplicate structured player identity: " + key.stableId());
            }
            Set<Position> positions = positionsByTeam.computeIfAbsent(key.teamCode(), ignored -> new HashSet<>());
            if (!positions.add(key.position())) {
                throw new IllegalStateException("Duplicate team-position: " + key.stableId());
            }
            indexed.put(key, new PlayerRatingResource(key, player.nickname(), resolveRatings(player)));
        }

        validateRoster(raw.scope(), indexed, positionsByTeam);
        List<PlayerRatingResource> ordered = indexed.values().stream()
                .sorted(Comparator.comparing((PlayerRatingResource value) -> value.playerKey().teamCode())
                        .thenComparingInt(value -> value.position().ordinal()))
                .toList();
        return new LoadedResource(
                raw.version(), raw.snapshotAt(), raw.dataCutoff(), sha256,
                raw.scope().teams(), raw.scope().startersPerTeam(),
                raw.scope().players(), raw.scope().substitutesIncluded(),
                raw.semantics().commonAttributeCount(), raw.semantics().roleSpecificAttributeCount(),
                raw.semantics().activeAttributesPerPlayer(), List.copyOf(ordered));
    }

    public static String jsonName(PlayerSkill skill) {
        for (Map.Entry<String, PlayerSkill> entry : JSON_TO_SKILL.entrySet()) {
            if (entry.getValue() == skill) return entry.getKey();
        }
        throw new IllegalArgumentException("No authored JSON name for skill: " + skill);
    }

    private static PlayerRatings resolveRatings(RawPlayer player) {
        if (player.ratings() == null) throw new IllegalStateException("Missing ratings: " + player.nickname());
        Set<String> expected = JSON_TO_SKILL.entrySet().stream()
                .filter(entry -> entry.getValue().appliesTo(player.position()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> actual = player.ratings().keySet();
        if (!actual.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new HashSet<>(actual);
            extra.removeAll(expected);
            throw new IllegalStateException("Player " + player.nickname()
                    + " has invalid attributes; missing=" + missing + " extra=" + extra);
        }

        EnumMap<PlayerSkill, Integer> values = new EnumMap<>(PlayerSkill.class);
        for (String attribute : expected) {
            Integer value = player.ratings().get(attribute);
            if (value == null || value < PlayerRatings.MIN || value > PlayerRatings.MAX) {
                throw new IllegalStateException("Invalid player rating " + attribute + " for " + player.nickname());
            }
            values.put(JSON_TO_SKILL.get(attribute), value);
        }
        return new PlayerRatings(player.position(), values);
    }

    private static void validateEnvelope(RawResource raw) {
        if (raw == null) throw new IllegalStateException("Player rating resource is empty");
        if (!VERSION.equals(raw.version())) throw new IllegalStateException("Unsupported player rating version: " + raw.version());
        if (!SNAPSHOT_AT.equals(raw.snapshotAt()) || !DATA_CUTOFF.equals(raw.dataCutoff())) {
            throw new IllegalStateException("Player rating snapshot/data cutoff mismatch");
        }
        if (raw.scale() == null || raw.scale().min() != PlayerRatings.MIN || raw.scale().max() != PlayerRatings.MAX) {
            throw new IllegalStateException("Player rating scale must be exactly 1..20");
        }
        if (raw.scope() == null || !"LCK".equals(raw.scope().league()) || raw.scope().teams() != 10 || raw.scope().startersPerTeam() != 5
                || raw.scope().players() != 50 || raw.scope().substitutesIncluded()) {
            throw new IllegalStateException("Player rating scope must be 10 teams, 5 starters, 50 players, no substitutes");
        }
        if (raw.semantics() == null || raw.semantics().commonAttributeCount() != 6
                || raw.semantics().roleSpecificAttributeCount() != 6
                || raw.semantics().activeAttributesPerPlayer() != 12
                || !raw.semantics().championProficiencySeparate()
                || raw.semantics().displayCaIncludedInRuntime()) {
            throw new IllegalStateException("Player rating semantics envelope is invalid");
        }
        if (raw.players() == null || raw.players().size() != raw.scope().players()) {
            throw new IllegalStateException("Player rating player count mismatch");
        }
        if (raw.players().stream().anyMatch(value -> value == null || value.team() == null
                || value.nickname() == null || value.position() == null)) {
            throw new IllegalStateException("Player rating roster contains incomplete identity");
        }
    }

    private static void validateRoster(RawScope scope, Map<PlayerRatingKey, PlayerRatingResource> indexed,
                                       Map<String, Set<Position>> positionsByTeam) {
        if (indexed.size() != scope.players() || positionsByTeam.size() != scope.teams()) {
            throw new IllegalStateException("Player rating roster count mismatch");
        }
        for (Map.Entry<String, Set<Position>> entry : positionsByTeam.entrySet()) {
            if (entry.getValue().size() != scope.startersPerTeam()
                    || !EnumSet.copyOf(entry.getValue()).equals(EnumSet.allOf(Position.class))) {
                throw new IllegalStateException("Team does not have exactly one player in every position: " + entry.getKey());
            }
        }
    }

    private static Map<String, PlayerSkill> jsonSkillMap() {
        Map<String, PlayerSkill> result = new LinkedHashMap<>();
        result.put("mechanics", PlayerSkill.MECHANICS);
        result.put("decisionMaking", PlayerSkill.DECISION_MAKING);
        result.put("mapAwareness", PlayerSkill.MAP_AWARENESS);
        result.put("positioning", PlayerSkill.POSITIONING);
        result.put("combatExecution", PlayerSkill.COMBAT_EXECUTION);
        result.put("consistency", PlayerSkill.CONSISTENCY);
        result.put("csAcquisition", PlayerSkill.FARMING);
        result.put("trading", PlayerSkill.TRADING);
        result.put("waveManagement", PlayerSkill.WAVE_MANAGEMENT);
        result.put("lanePressure", PlayerSkill.LANE_PRESSURE);
        result.put("initiativeConversion", PlayerSkill.PRIORITY_CONVERSION);
        result.put("sideLaneManagement", PlayerSkill.SIDE_LANE);
        result.put("pathing", PlayerSkill.PATHING);
        result.put("jungleResourceManagement", PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT);
        result.put("enemyJungleTracking", PlayerSkill.ENEMY_JUNGLE_TRACKING);
        result.put("laneIntervention", PlayerSkill.LANE_INTERVENTION);
        result.put("objectiveDecision", PlayerSkill.OBJECTIVE_DECISION);
        result.put("objectiveSecuring", PlayerSkill.OBJECTIVE_SECURE);
        result.put("visionControl", PlayerSkill.VISION_CONTROL);
        result.put("laneSupport", PlayerSkill.LANE_SUPPORT);
        result.put("roamCoordination", PlayerSkill.ROTATION_PLANNING);
        result.put("engage", PlayerSkill.ENGAGE_EXECUTION);
        result.put("allyProtection", PlayerSkill.ALLY_PROTECTION);
        result.put("zoneSetup", PlayerSkill.AREA_SETUP);
        return Map.copyOf(result);
    }

    private static byte[] readBytes(InputStream input) {
        if (input == null) throw new IllegalStateException("Player rating resource not found: " + RESOURCE);
        try (input) {
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read player rating resource", error);
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
            String snapshotAt,
            String dataCutoff,
            String resourceSha256,
            int teamCount,
            int startersPerTeam,
            int playerCount,
            boolean substitutesIncluded,
            int commonAttributeCount,
            int roleSpecificAttributeCount,
            int activeAttributesPerPlayer,
            List<PlayerRatingResource> players
    ) {
        public LoadedResource {
            players = List.copyOf(players);
        }
    }

    private record RawResource(
            String version,
            String snapshotAt,
            String dataCutoff,
            RawScale scale,
            RawScope scope,
            RawSemantics semantics,
            List<RawPlayer> players
    ) { }

    private record RawScale(int min, int max) { }

    private record RawScope(String league, int teams, int startersPerTeam, int players,
                            boolean substitutesIncluded) { }

    private record RawSemantics(
            int commonAttributeCount,
            int roleSpecificAttributeCount,
            int activeAttributesPerPlayer,
            boolean championProficiencySeparate,
            boolean displayCaIncludedInRuntime
    ) { }

    private record RawPlayer(String team, String nickname, Position position, Map<String, Integer> ratings) { }
}

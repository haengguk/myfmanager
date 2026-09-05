package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Raw-SHA-pinned loader for the explicit stable-player identity mapping. */
public final class PlayerIdentityResourceLoader {
    public static final String RESOURCE = "/players/lck-player-identities-2026-08-21-v1.json";
    public static final String VERSION = "lck-player-identities-2026-08-21-v1";
    public static final String SNAPSHOT_AT = "2026-08-21";
    public static final String EXPECTED_SHA256 =
            "badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018";

    private PlayerIdentityResourceLoader() { }

    public static LoadedResource loadDefault() {
        return load(new ObjectMapper(), PlayerIdentityResourceLoader.class.getResourceAsStream(RESOURCE));
    }

    public static LoadedResource load(ObjectMapper mapper, InputStream input) {
        return load(mapper, input, EXPECTED_SHA256, PlayerRatingResourceLoader.loadDefault());
    }

    static LoadedResource load(ObjectMapper mapper, InputStream input, String expectedSha256,
                               PlayerRatingResourceLoader.LoadedResource ratings) {
        if (!PlayerRatingResourceLoader.VERSION.equals(ratings.version())) {
            throw new IllegalStateException("Player identity rating prerequisite mismatch");
        }
        return load(mapper, input, new PlayerResourceSpec(
                "LCK", 10, VERSION, SNAPSHOT_AT, expectedSha256, null), ratings);
    }

    public static LoadedResource load(ObjectMapper mapper, InputStream input, PlayerResourceSpec spec,
                                      PlayerRatingResourceLoader.LoadedResource ratings) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(ratings, "ratings");
        byte[] bytes = readBytes(input);
        String sha256 = sha256(bytes);
        if (!spec.sha256().equals(sha256)) {
            throw new IllegalStateException("Player identity resource SHA-256 mismatch: " + sha256);
        }

        RawResource raw;
        try {
            raw = mapper.readValue(bytes, RawResource.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load player identity resource", error);
        }
        validateEnvelope(raw, ratings, spec);

        Map<PlayerId, PlayerIdentity> byId = new LinkedHashMap<>();
        Map<PlayerRatingKey, PlayerIdentity> byRatingKey = new LinkedHashMap<>();
        Map<String, Set<Position>> positionsByTeam = new HashMap<>();
        for (RawPlayer player : raw.players()) {
            PlayerIdentity identity = new PlayerIdentity(
                    new PlayerId(player.playerId()),
                    new PlayerRatingKey(player.team(), player.position()),
                    player.nickname());
            if (byId.putIfAbsent(identity.playerId(), identity) != null) {
                throw new IllegalStateException("Duplicate PlayerId: " + identity.playerId());
            }
            if (byRatingKey.putIfAbsent(identity.ratingKey(), identity) != null) {
                throw new IllegalStateException("Duplicate PlayerRatingKey: " + identity.ratingKey().stableId());
            }
            positionsByTeam.computeIfAbsent(identity.ratingKey().teamCode(), ignored -> new HashSet<>())
                    .add(identity.ratingKey().position());
        }

        validateRoster(raw.scope(), byId, byRatingKey, positionsByTeam);
        validateRatingBindings(byRatingKey, ratings.players());
        List<PlayerIdentity> ordered = new ArrayList<>(byId.values());
        ordered.sort(Comparator.comparing((PlayerIdentity value) -> value.ratingKey().teamCode())
                .thenComparingInt(value -> value.ratingKey().position().ordinal()));
        return new LoadedResource(raw.version(), raw.snapshotAt(), sha256,
                raw.requiredPlayerRatingResourceVersion(),
                raw.scope().teams(), raw.scope().startersPerTeam(), raw.scope().players(),
                raw.scope().substitutesIncluded(), List.copyOf(ordered));
    }

    private static void validateEnvelope(RawResource raw,
                                         PlayerRatingResourceLoader.LoadedResource ratings, PlayerResourceSpec spec) {
        if (raw == null) throw new IllegalStateException("Player identity resource is empty");
        if (!spec.version().equals(raw.version())) {
            throw new IllegalStateException("Unsupported player identity version: " + raw.version());
        }
        if (raw.snapshotAt() == null || raw.snapshotAt().isBlank()
                || !spec.snapshotAt().equals(raw.snapshotAt())) {
            throw new IllegalStateException("Player identity snapshotAt mismatch: " + raw.snapshotAt());
        }
        if (!ratings.version().equals(raw.requiredPlayerRatingResourceVersion())) {
            throw new IllegalStateException("Player identity rating prerequisite mismatch");
        }
        RawScope scope = raw.scope();
        if (scope == null || !spec.leagueCode().equals(scope.league()) || scope.teams() != spec.teamCount()
                || scope.startersPerTeam() != 5 || scope.players() != spec.playerCount()
                || scope.substitutesIncluded()) {
            throw new IllegalStateException("Player identity scope does not match the selected starter dataset");
        }
        if (raw.players() == null || raw.players().size() != scope.players()) {
            throw new IllegalStateException("Player identity player count mismatch");
        }
        if (raw.players().stream().anyMatch(value -> value == null || value.playerId() == null
                || value.team() == null || value.position() == null || value.nickname() == null)) {
            throw new IllegalStateException("Player identity roster contains incomplete data");
        }
    }

    private static void validateRoster(RawScope scope, Map<PlayerId, PlayerIdentity> byId,
                                       Map<PlayerRatingKey, PlayerIdentity> byRatingKey,
                                       Map<String, Set<Position>> positionsByTeam) {
        if (byId.size() != scope.players() || byRatingKey.size() != scope.players()
                || positionsByTeam.size() != scope.teams()) {
            throw new IllegalStateException("Player identity roster count mismatch");
        }
        for (Map.Entry<String, Set<Position>> entry : positionsByTeam.entrySet()) {
            if (!EnumSet.copyOf(entry.getValue()).equals(EnumSet.allOf(Position.class))) {
                throw new IllegalStateException("Identity team does not have every position: " + entry.getKey());
            }
        }
    }

    private static void validateRatingBindings(Map<PlayerRatingKey, PlayerIdentity> identities,
                                               List<PlayerRatingResource> ratings) {
        Map<PlayerRatingKey, PlayerRatingResource> ratingIndex = ratings.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        PlayerRatingResource::playerKey, value -> value));
        Set<PlayerRatingKey> missing = new HashSet<>(ratingIndex.keySet());
        missing.removeAll(identities.keySet());
        Set<PlayerRatingKey> unknown = new HashSet<>(identities.keySet());
        unknown.removeAll(ratingIndex.keySet());
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalStateException("Identity/rating subject mismatch; missing=" + missing
                    + " unknown=" + unknown);
        }
        for (PlayerIdentity identity : identities.values()) {
            PlayerRatingResource rating = ratingIndex.get(identity.ratingKey());
            if (!identity.nickname().equals(rating.nickname())) {
                throw new IllegalStateException("Identity/rating nickname mismatch: "
                        + identity.ratingKey().stableId());
            }
        }
    }

    private static byte[] readBytes(InputStream input) {
        if (input == null) throw new IllegalStateException("Player identity resource not found: " + RESOURCE);
        try (input) {
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read player identity resource", error);
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
            String resourceSha256,
            String requiredPlayerRatingResourceVersion,
            int teamCount,
            int startersPerTeam,
            int playerCount,
            boolean substitutesIncluded,
            List<PlayerIdentity> players
    ) {
        public LoadedResource {
            players = List.copyOf(players);
        }
    }

    private record RawResource(String version, String snapshotAt,
                               String requiredPlayerRatingResourceVersion,
                               RawScope scope, List<RawPlayer> players) { }
    private record RawScope(String league, int teams, int startersPerTeam, int players,
                            boolean substitutesIncluded) { }
    private record RawPlayer(String playerId, String team, Position position, String nickname) { }
}

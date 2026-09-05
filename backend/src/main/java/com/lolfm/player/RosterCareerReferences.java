package com.lolfm.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Lossless authored reference data. Unknown values and source conflicts are not gameplay inputs. */
public final class RosterCareerReferences {
    private final PlayerResourceSpec source;
    private final ObjectNode metadata;
    private final Map<PlayerId, JsonNode> players;

    private RosterCareerReferences(PlayerResourceSpec source, ObjectNode metadata,
                                   Map<PlayerId, JsonNode> players) {
        this.source = source;
        this.metadata = metadata.deepCopy();
        this.players = Map.copyOf(players);
    }

    static RosterCareerReferences load(ObjectMapper mapper, PlayerResourceSpec source,
                                       PlayerIdentityCatalog identities) {
        try (InputStream input = RosterCareerReferences.class.getResourceAsStream(source.resource())) {
            if (input == null) throw invalid("Missing " + source.resource());
            return load(mapper, input.readAllBytes(), source, identities);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read career reference " + source.resource(), error);
        }
    }

    static RosterCareerReferences load(ObjectMapper mapper, byte[] bytes, PlayerResourceSpec source,
                                       PlayerIdentityCatalog identities) {
        if (!source.sha256().equals(digest(bytes))) throw invalid("Career resource SHA-256 mismatch");
        JsonNode root;
        try {
            root = mapper.readTree(bytes);
        } catch (IOException error) {
            throw new IllegalStateException("Invalid career JSON", error);
        }
        if (!(root instanceof ObjectNode object)
                || !source.version().equals(root.path("version").asText())
                || !source.snapshotAt().equals(root.path("snapshotAt").asText())) {
            throw invalid("Career version/snapshot mismatch");
        }
        JsonNode scope = root.path("scope");
        if (!source.leagueCode().equals(scope.path("league").asText())
                || scope.path("teams").asInt(-1) != source.teamCount()
                || scope.path("players").asInt(-1) != source.playerCount()
                || scope.path("startersPerTeam").asInt(-1) != 5
                || !scope.path("startersOnly").asBoolean()
                || !scope.path("salaryIncluded").isBoolean() || scope.path("salaryIncluded").asBoolean()
                || !scope.path("marketValueIncluded").isBoolean() || scope.path("marketValueIncluded").asBoolean()
                || !root.path("semantics").isObject()) throw invalid("Career scope/semantics mismatch");
        if (!"LCK".equals(source.leagueCode())
                && !identities.version().equals(root.path("rosterBasis").path("resourceVersion").asText())) {
            throw invalid("Career identity prerequisite mismatch");
        }
        if (!root.path("players").isArray() || root.path("players").size() != source.playerCount()) {
            throw invalid("Career player count mismatch");
        }
        Map<PlayerId, JsonNode> indexed = new HashMap<>();
        for (JsonNode player : root.path("players")) {
            PlayerId id = new PlayerId(player.path("playerId").asText());
            PlayerIdentity identity = identities.find(id).orElseThrow(() -> invalid("Unknown career PlayerId: " + id));
            if (!identity.ratingKey().teamCode().equals(player.path("team").asText())
                    || !identity.ratingKey().position().name().equals(player.path("position").asText())
                    || !identity.nickname().equals(player.path("nickname").asText())
                    || !source.snapshotAt().equals(player.path("snapshotAt").asText())) {
                throw invalid("Career identity binding mismatch: " + id);
            }
            for (String section : Set.of("personal", "contract", "career", "honors", "careerPrizeMoney", "dataQuality")) {
                if (!player.path(section).isObject()) throw invalid("Missing career section: " + section + "/" + id);
            }
            if (!player.path("sources").isArray() || player.path("sources").isEmpty()) {
                throw invalid("Missing career sources: " + id);
            }
            if (indexed.putIfAbsent(id, player.deepCopy()) != null) throw invalid("Duplicate career PlayerId: " + id);
        }
        Set<PlayerId> expected = identities.all().stream().map(PlayerIdentity::playerId).collect(Collectors.toSet());
        if (!indexed.keySet().equals(expected)) throw invalid("Career subject set mismatch");
        ObjectNode metadata = object.deepCopy();
        metadata.remove("players");
        return new RosterCareerReferences(source, metadata, indexed);
    }

    public PlayerResourceSpec source() { return source; }
    public JsonNode metadata() { return metadata.deepCopy(); }
    public JsonNode player(PlayerId id) {
        JsonNode value = players.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown career PlayerId: " + id);
        return value.deepCopy();
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static IllegalStateException invalid(String message) { return new IllegalStateException(message); }
}

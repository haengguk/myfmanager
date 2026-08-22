package com.lolfm.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.simulator.ObservedMatchSimulation;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Fast complete-timeline equality for deterministic integration tests.
 *
 * <p>The successful path compares canonical SHA-256 identities. Explicit public-contract
 * assertions remain visible at the call site, while a hash mismatch falls back to canonical
 * JSON equality so the failure still contains a useful structural diff.</p>
 */
public final class CompleteTimelineAssertions {
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .build();
    private static final List<String> PLAYER_CHAMPION_METADATA_FIELDS = List.of(
            "champion", "championId", "championNameKo", "championNameEn",
            "championPortraitUrl", "championPosition");

    private CompleteTimelineAssertions() {}

    public static void assertCompleteTimelineEquals(MatchTimeline actual, MatchTimeline expected) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(expected, "expected");

        assertThat(actual.getWinner()).as("timeline winner").isEqualTo(expected.getWinner());
        assertThat(actual.getDurationSeconds()).as("timeline duration")
                .isEqualTo(expected.getDurationSeconds());
        assertThat(actual.getEvents()).as("timeline event count")
                .hasSameSizeAs(expected.getEvents());
        assertThat(actual.getSnapshots()).as("timeline snapshot count")
                .hasSameSizeAs(expected.getSnapshots());

        assertCanonicalTimelineIdentity(actual, expected, "complete canonical timeline");
    }

    public static void assertCompleteObservedMatchEquals(
            ObservedMatchSimulation actual,
            ObservedMatchSimulation expected
    ) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(expected, "expected");
        assertCompleteTimelineEquals(actual.timeline(), expected.timeline());
        assertThat(actual.randomFingerprint()).as("complete seeded Random fingerprint")
                .isEqualTo(expected.randomFingerprint());
    }

    public static void assertTimelineEqualsIgnoringPlayerChampionMetadata(
            MatchTimeline actual,
            MatchTimeline expected
    ) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(expected, "expected");

        assertThat(actual.getWinner()).as("timeline winner").isEqualTo(expected.getWinner());
        assertThat(actual.getDurationSeconds()).as("timeline duration")
                .isEqualTo(expected.getDurationSeconds());
        assertThat(actual.getEvents()).as("timeline event count")
                .hasSameSizeAs(expected.getEvents());
        assertThat(actual.getSnapshots()).as("timeline snapshot count")
                .hasSameSizeAs(expected.getSnapshots());

        JsonNode actualTree = withoutPlayerChampionMetadata(canonicalTree(actual));
        JsonNode expectedTree = withoutPlayerChampionMetadata(canonicalTree(expected));
        assertCanonicalIdentity(actualTree, expectedTree,
                "canonical timeline excluding explicit player champion metadata");
    }

    public static String canonicalHash(MatchTimeline timeline) {
        return canonicalHashValue(Objects.requireNonNull(timeline, "timeline"));
    }

    public static String canonicalHash(ObjectMapper baseMapper, MatchTimeline timeline) {
        Objects.requireNonNull(baseMapper, "baseMapper");
        Objects.requireNonNull(timeline, "timeline");
        try {
            ObjectMapper canonicalMapper = baseMapper.copy()
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .disable(SerializationFeature.INDENT_OUTPUT);
            return sha256(canonicalMapper.writeValueAsBytes(timeline));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to canonicalize complete timeline", error);
        }
    }

    static JsonNode canonicalTree(MatchTimeline timeline) {
        return CANONICAL_MAPPER.valueToTree(Objects.requireNonNull(timeline, "timeline"));
    }

    private static JsonNode withoutPlayerChampionMetadata(JsonNode source) {
        JsonNode copy = source.deepCopy();
        JsonNode snapshots = copy.path("snapshots");
        if (!(snapshots instanceof ArrayNode snapshotArray)) return copy;
        for (JsonNode snapshot : snapshotArray) {
            JsonNode players = snapshot.path("playerSnapshots");
            if (!(players instanceof ArrayNode playerArray)) continue;
            for (JsonNode player : playerArray) {
                if (player instanceof ObjectNode object) {
                    object.remove(PLAYER_CHAMPION_METADATA_FIELDS);
                }
            }
        }
        return copy;
    }

    private static void assertCanonicalIdentity(JsonNode actual, JsonNode expected, String label) {
        String actualHash = canonicalHash(actual);
        String expectedHash = canonicalHash(expected);
        if (!actualHash.equals(expectedHash)) {
            assertThat(actual).as(label + " structural diff").isEqualTo(expected);
        }
        assertThat(actualHash).as(label + " SHA-256").isEqualTo(expectedHash);
    }

    private static void assertCanonicalTimelineIdentity(
            MatchTimeline actual,
            MatchTimeline expected,
            String label
    ) {
        String actualHash = canonicalHash(actual);
        String expectedHash = canonicalHash(expected);
        if (!actualHash.equals(expectedHash)) {
            assertThat(canonicalTree(actual)).as(label + " structural diff")
                    .isEqualTo(canonicalTree(expected));
        }
        assertThat(actualHash).as(label + " SHA-256").isEqualTo(expectedHash);
    }

    private static String canonicalHash(JsonNode value) {
        return canonicalHashValue(value);
    }

    private static String canonicalHashValue(Object value) {
        try {
            return sha256(CANONICAL_MAPPER.writeValueAsBytes(value));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to canonicalize complete timeline", error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}

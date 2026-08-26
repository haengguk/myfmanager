package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Synthetic, zero-gameplay proof for every signed V2 evidence shape. */
final class MatchEngineV9FreshSerializationPreflight {
    private final ObjectMapper canonical = new ObjectMapper().findAndRegisterModules().copy()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT);

    Artifact artifact(boolean reversedInput) throws Exception {
        List<String> multiple = reversedInput ? List.of("ACTION:Z", "ACTION:A", "ACTION:Z")
                : List.of("ACTION:A", "ACTION:Z", "ACTION:A");
        TreeMap<String, Object> matchup = new TreeMap<>();
        matchup.put("actionId", "COMBAT_AT:900");
        matchup.put("applicationPoint", "COMBAT_PROGRESSION_SCORE");
        matchup.put("participants", List.of("BLUE:MID", "RED:MID"));
        matchup.put("stateLineage", Map.of(
                "mutationIdentity", "LANE_PRESSURE:890:MID:89",
                "mutationVersion", 89,
                "consumerActionId", "COMBAT_AT:900"));
        TreeMap<String, Object> composition = new TreeMap<>();
        composition.put("attemptId", 17);
        composition.put("applicationKey", "TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE");
        composition.put("publicBindings", List.of(
                Map.of("eventOrdinal", 42, "actionId", "COMBAT_AT:900"),
                Map.of("eventOrdinal", 43, "actionId", "COMBAT_AT:900")));
        Payload payload = new Payload(
                "MATCH_ENGINE_V9_FRESH_SERIALIZATION_PREFLIGHT_PAYLOAD_V2",
                List.of(), List.of("ACTION:ONE"),
                MatchEngineV9FreshRequalificationRunner.canonicalActionIds(multiple),
                Map.of("matchup", matchup, "composition", composition),
                new SyntheticFixtureCheckpoint(
                        "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_FIXTURE_CHECKPOINT_V2",
                        "FIXTURE-000", "CALIBRATION", List.of("DRAFT:0", "DRAFT:1"),
                        List.of("ROW:0", "ROW:1", "ROW:2"),
                        List.of("PAIR:0", "PAIR:1"),
                        Map.of("contractHash", hex('a'), "sourceHash", hex('b'))),
                new SyntheticWorkerReceipt(
                        "MATCH_ENGINE_V9_FRESH_REQUALIFICATION_WORKER_RECEIPT_V2",
                        "CALIBRATION", 0, 4, "JVM:AUDIT",
                        List.of(hex('1'), hex('2'))));
        byte[] first = canonical.writeValueAsBytes(payload);
        Payload typed = canonical.readValue(first, Payload.class);
        byte[] typedBytes = canonical.writeValueAsBytes(typed);
        byte[] rawTreeBytes = canonical.writeValueAsBytes(canonical.readTree(first));
        assertThat(typedBytes).isEqualTo(first);
        assertThat(rawTreeBytes).isEqualTo(first);
        String digest = MatchEngineV9FreshRequalificationContract.sha256(first);
        return new Artifact(
                "MATCH_ENGINE_V9_FRESH_SERIALIZATION_PREFLIGHT_ARTIFACT_V2",
                payload, digest,
                MatchEngineV9FreshRequalificationContract.sha256(typedBytes),
                MatchEngineV9FreshRequalificationContract.sha256(rawTreeBytes),
                recursiveManifest(payload, digest));
    }

    byte[] bytes(boolean reversedInput) throws Exception {
        return canonical.writeValueAsBytes(artifact(reversedInput));
    }

    private static Map<String, String> recursiveManifest(Payload payload, String digest) {
        TreeMap<String, String> values = new TreeMap<>();
        values.put("payload.json", digest);
        values.put("fixture-checkpoint.identity",
                MatchEngineV9FreshRequalificationContract.sha256(
                        payload.fixtureCheckpoint().fixtureId()));
        values.put("worker-receipt.identity",
                MatchEngineV9FreshRequalificationContract.sha256(
                        payload.workerReceipt().workerJvmIdentity()));
        return values;
    }

    private static String hex(char value) {
        return String.valueOf(value).repeat(64);
    }

    record Payload(
            String schemaVersion,
            List<String> zeroActionIds,
            List<String> oneActionId,
            List<String> multipleActionIds,
            Map<String, Object> nestedProvenance,
            SyntheticFixtureCheckpoint fixtureCheckpoint,
            SyntheticWorkerReceipt workerReceipt
    ) {
        Payload {
            zeroActionIds = MatchEngineV9FreshRequalificationRunner
                    .canonicalActionIds(zeroActionIds);
            oneActionId = MatchEngineV9FreshRequalificationRunner
                    .canonicalActionIds(oneActionId);
            multipleActionIds = MatchEngineV9FreshRequalificationRunner
                    .canonicalActionIds(multipleActionIds);
            nestedProvenance = java.util.Collections.unmodifiableMap(
                    new TreeMap<>(nestedProvenance));
        }
    }

    record SyntheticFixtureCheckpoint(
            String schemaVersion,
            String fixtureId,
            String sampleLane,
            List<String> drafts,
            List<String> rows,
            List<String> pairs,
            Map<String, String> binding
    ) {
        SyntheticFixtureCheckpoint {
            drafts = List.copyOf(drafts);
            rows = List.copyOf(rows);
            pairs = List.copyOf(pairs);
            binding = java.util.Collections.unmodifiableMap(new TreeMap<>(binding));
        }
    }

    record SyntheticWorkerReceipt(
            String schemaVersion,
            String sampleLane,
            int shardIndex,
            int shardCount,
            String workerJvmIdentity,
            List<String> checkpointHashes
    ) {
        SyntheticWorkerReceipt {
            checkpointHashes = checkpointHashes.stream().sorted().toList();
        }
    }

    record Artifact(
            String schemaVersion,
            Payload payload,
            String canonicalPayloadSha256,
            String typedRoundTripSha256,
            String rawJsonTreeSha256,
            Map<String, String> recursiveManifest
    ) {
        Artifact {
            recursiveManifest = java.util.Collections.unmodifiableMap(
                    new TreeMap<>(recursiveManifest));
            if (!canonicalPayloadSha256.equals(typedRoundTripSha256)
                    || !canonicalPayloadSha256.equals(rawJsonTreeSha256)) {
                throw new IllegalArgumentException("Preflight digest paths differ");
            }
        }
    }
}

abstract class MatchEngineV9FreshSerializationPreflightWriter {
    final void write(String name, boolean reversed) throws Exception {
        byte[] bytes = new MatchEngineV9FreshSerializationPreflight().bytes(reversed);
        Path path = Path.of("build", "reports",
                "match-engine-v9-fresh-serialization-preflight-" + name + ".json");
        MatchEngineV9FreshRequalificationRunner.writeReplace(path, bytes);
    }
}

@Tag("diagnostic") @Tag("match-engine-v9-fresh-serialization-preflight")
class MatchEngineV9FreshSerializationPreflightATest
        extends MatchEngineV9FreshSerializationPreflightWriter {
    @Test void writesCanonicalTree() throws Exception { write("a", false); }
}

@Tag("diagnostic") @Tag("match-engine-v9-fresh-serialization-preflight")
class MatchEngineV9FreshSerializationPreflightBTest
        extends MatchEngineV9FreshSerializationPreflightWriter {
    @Test void writesCanonicalTreeFromReversedInput() throws Exception { write("b", true); }
}

@Tag("diagnostic") @Tag("match-engine-v9-fresh-serialization-preflight-verify")
class MatchEngineV9FreshSerializationPreflightVerificationTest {
    @Test void twoFreshJvmsProduceByteExactTrees() throws Exception {
        Path reports = Path.of("build", "reports");
        byte[] first = Files.readAllBytes(
                reports.resolve("match-engine-v9-fresh-serialization-preflight-a.json"));
        byte[] second = Files.readAllBytes(
                reports.resolve("match-engine-v9-fresh-serialization-preflight-b.json"));
        assertThat(first).isEqualTo(second);
        assertThat(new String(first, StandardCharsets.UTF_8))
                .contains("MATCH_ENGINE_V9_FRESH_SERIALIZATION_PREFLIGHT_ARTIFACT_V2");
    }
}

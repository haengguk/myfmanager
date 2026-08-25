package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Read-only verifier for the already-consumed predecessor evidence. */
public final class MatchupV9StructureAttributionEvidence {
    public static final Path PREDECESSOR = Path.of(
            "build", "reports", "match-engine-v9-matchup-composition-requalification-v1");

    private MatchupV9StructureAttributionEvidence() {
    }

    public static PredecessorAudit verify(Path backendRoot, ObjectMapper sourceMapper)
            throws Exception {
        Path root = backendRoot.resolve(PREDECESSOR);
        ObjectMapper mapper = sourceMapper.copy().findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        ManifestAudit manifest = verifyManifest(root);
        String contractHash = firstHash(root.resolve("contract.sha256"));
        if (!contractHash.equals(hash(root.resolve("contract.json")))) {
            throw new IllegalStateException("Predecessor contract SHA mismatch");
        }
        JsonNode identity = mapper.readTree(root.resolve(
                "source-resource-runtime-identity.json").toFile());
        var currentProduction = Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot);
        String predecessorProduction = identity.path("sourceIdentity")
                .path("productionSourceTree").path("hash").asText();
        if (!predecessorProduction.equals(currentProduction.hash())) {
            throw new IllegalStateException("Current production tree differs from predecessor binding");
        }
        if (!SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION.equals(
                identity.path("sourceIdentity").path("engineImplementationVersion").asText())) {
            throw new IllegalStateException("Engine implementation identity drift");
        }
        if (!MatchEngineV1Policy.authoritative().policyHash().equals(
                identity.path("productionPolicy").path("policyHash").asText())) {
            throw new IllegalStateException("Production policy identity drift");
        }
        if (!SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION.equals(
                identity.path("productionPolicy").path("activeGameplayRulesVersion").asText())) {
            throw new IllegalStateException("Active gameplay rules identity drift");
        }

        JsonNode consumed = mapper.readTree(root.resolve("holdout-consumed.json").toFile());
        String officialManifestHash = consumed.path("officialManifestHash").asText();
        if (!officialManifestHash.equals(hash(root.resolve("SHA256SUMS.txt")))
                || !consumed.path("candidateTreesByteEqual").asBoolean()
                || consumed.path("holdoutRowCount").asInt() != 1_200) {
            throw new IllegalStateException("Consumed holdout marker is not bound to official bytes");
        }
        JsonNode recommendation = mapper.readTree(
                root.resolve("production-profile-recommendation.json").toFile());
        if (!"RECOMMEND_BASELINE_V1".equals(recommendation.path("recommendation").asText())
                || recommendation.path("productionChanged").asBoolean()) {
            throw new IllegalStateException("Predecessor recommendation was changed");
        }
        JsonNode seedAudit = mapper.readTree(root.resolve("seed-overlap-audit.json").toFile());
        if (seedAudit.path("historicalOverlapCount").asInt() != 0
                || seedAudit.path("freshCollisionCount").asInt() != 0) {
            throw new IllegalStateException("Predecessor seed audit is not clean");
        }
        CheckpointAudit checkpoints = verifyCheckpointAndRawBinding(root, mapper, contractHash,
                identity.path("sourceIdentity").path("harnessSourceTree").path("hash").asText());
        return new PredecessorAudit(
                "MATCH_ENGINE_V9_REQUALIFICATION_PREDECESSOR_READ_ONLY_AUDIT_V1",
                true, contractHash, manifest.manifestHash(), manifest.verifiedFileCount(),
                officialManifestHash, consumed.path("freshJvmCandidateA").asText(),
                consumed.path("candidateTreesByteEqual").asBoolean(),
                currentProduction, predecessorProduction,
                identity.path("productionPolicy").path("policyHash").asText(),
                identity.path("productionPolicy").path("configurationHash").asText(),
                identity.path("profiles").path("MATCHUP_ONLY_CANDIDATE_V1")
                        .path("configurationHash").asText(),
                identity.path("resourceProvenance").path("resourceProvenanceHash").asText(),
                checkpoints, recommendation.path("recommendation").asText(),
                recommendation.path("matchupStatus").asText(), true);
    }

    private static ManifestAudit verifyManifest(Path root) throws IOException {
        Path manifestPath = root.resolve("SHA256SUMS.txt");
        int count = 0;
        for (String line : Files.readAllLines(manifestPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf("  ");
            if (separator != 64) throw new IllegalStateException("Malformed predecessor manifest");
            String expected = line.substring(0, separator);
            Path file = root.resolve(line.substring(separator + 2));
            if (!Files.isRegularFile(file) || !expected.equals(hash(file))) {
                throw new IllegalStateException("Predecessor manifest mismatch: " + file);
            }
            count++;
        }
        return new ManifestAudit(hash(manifestPath), count);
    }

    private static CheckpointAudit verifyCheckpointAndRawBinding(
            Path root, ObjectMapper mapper, String contractHash, String harnessHash)
            throws Exception {
        ArrayList<MatchEngineV9RequalificationRunner.FixtureCheckpoint> calibration =
                new ArrayList<>();
        ArrayList<MatchEngineV9RequalificationRunner.FixtureCheckpoint> holdout =
                new ArrayList<>();
        int sidecars = 0;
        for (String lane : List.of("calibration", "holdout")) {
            Path directory = root.resolve("checkpoints").resolve(lane);
            List<Path> payloads;
            try (var walk = Files.list(directory)) {
                payloads = walk.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted().toList();
            }
            if (payloads.size() != 100) {
                throw new IllegalStateException("Predecessor checkpoint coverage mismatch: " + lane);
            }
            for (Path payload : payloads) {
                Path sidecar = payload.resolveSibling(payload.getFileName() + ".sha256");
                if (!hash(payload).equals(firstHash(sidecar))) {
                    throw new IllegalStateException("Predecessor checkpoint sidecar mismatch");
                }
                sidecars++;
                var value = mapper.readValue(payload.toFile(),
                        MatchEngineV9RequalificationRunner.FixtureCheckpoint.class);
                if (!contractHash.equals(value.contractHash())
                        || !harnessHash.equals(value.harnessSourceHash())
                        || !value.replayExact()) {
                    throw new IllegalStateException("Predecessor checkpoint binding mismatch");
                }
                (lane.equals("calibration") ? calibration : holdout).add(value);
            }
        }
        List<MatchEngineV9RequalificationRunner.MatchRow> rows = new ArrayList<>(3_600);
        appendRows(rows, calibration);
        appendRows(rows, holdout);
        StringBuilder reconstructed = new StringBuilder();
        for (var row : rows) reconstructed.append(mapper.writeValueAsString(row)).append('\n');
        byte[] official = Files.readAllBytes(root.resolve("per-match-results.jsonl"));
        boolean rawExact = Arrays.equals(
                reconstructed.toString().getBytes(StandardCharsets.UTF_8), official);
        if (!rawExact || rows.size() != 3_600
                || rows.stream().filter(value -> value.sampleLane()
                == MatchEngineV9RequalificationContract.SampleLane.HOLDOUT).count() != 1_200) {
            throw new IllegalStateException("Predecessor raw rows do not bind to checkpoints");
        }
        return new CheckpointAudit(200, sidecars, 3_600, 1_200, rawExact,
                hash(root.resolve("per-match-results.jsonl")));
    }

    private static void appendRows(
            List<MatchEngineV9RequalificationRunner.MatchRow> target,
            List<MatchEngineV9RequalificationRunner.FixtureCheckpoint> checkpoints) {
        checkpoints.stream().sorted(Comparator.comparingInt(
                        MatchEngineV9RequalificationRunner.FixtureCheckpoint::fixtureIndex))
                .flatMap(value -> value.rows().stream())
                .sorted(Comparator.comparing(MatchEngineV9RequalificationRunner.MatchRow::fixtureId)
                        .thenComparingInt(MatchEngineV9RequalificationRunner.MatchRow::seedIndex)
                        .thenComparingInt(MatchEngineV9RequalificationRunner.MatchRow::profileIndex))
                .forEach(target::add);
    }

    static String hash(Path path) throws IOException {
        return MatchupV9StructureAttributionContract.sha256(Files.readAllBytes(path));
    }

    private static String firstHash(Path path) throws IOException {
        String value = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (value.length() < 64) throw new IllegalStateException("Missing SHA-256: " + path);
        return value.substring(0, 64);
    }

    public record ManifestAudit(String manifestHash, int verifiedFileCount) { }

    public record CheckpointAudit(
            int checkpointPayloadCount,
            int verifiedSidecarCount,
            int rawMatchRowCount,
            int consumedHoldoutRowCount,
            boolean rawRowsByteExactWithCheckpointProjection,
            String rawPerMatchSha256
    ) { }

    public record PredecessorAudit(
            String schemaVersion,
            boolean clean,
            String contractHash,
            String sha256SumsRawSha256,
            int manifestVerifiedFileCount,
            String holdoutConsumedOfficialManifestHash,
            String freshJvmCandidateIdentity,
            boolean freshJvmCandidateTreesByteEqual,
            Phase13GB1AuditArtifactWriter.SourceTreeIdentity currentProductionSourceTree,
            String predecessorProductionSourceHash,
            String productionPolicyHash,
            String baselineConfigurationHash,
            String matchupCandidateConfigurationHash,
            String resourceProvenanceHash,
            CheckpointAudit checkpointAudit,
            String preservedRecommendation,
            String preservedMatchupStatus,
            boolean consumedHoldoutRemainsReadOnly
    ) { }
}

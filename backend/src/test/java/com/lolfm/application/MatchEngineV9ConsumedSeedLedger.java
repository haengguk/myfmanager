package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.draft.AutoDraftVarietyV1Schedule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only inventory of every predecessor seed source required by the fresh audit. */
public final class MatchEngineV9ConsumedSeedLedger {
    public static final String SCHEMA = "MATCH_ENGINE_V9_CONSUMED_SEED_LEDGER_V1";
    private static final Set<String> SEED_FIELDS = Set.of(
            "seed", "seeds", "calibrationseeds", "holdoutseeds", "dryrunseed",
            "matchseed", "preparationseed", "seedvalue");

    private MatchEngineV9ConsumedSeedLedger() { }

    public static Ledger create(ObjectMapper mapper, Path backendRoot) throws IOException {
        Objects.requireNonNull(mapper);
        Path reports = backendRoot.resolve("build/reports");
        ArrayList<SourceSpec> specs = new ArrayList<>(List.of(
                spec("PHASE_13G_B1", "phase13g-b1/phase13g-b1-schedule.json", null),
                spec("PHASE_13G_B2", "phase13g-b1/phase13g-b1-schedule.json",
                        "REUSES_PHASE_13G_B1_CALIBRATION_SEEDS"),
                spec("PHASE_13G_B3", "phase13g-b1/phase13g-b1-schedule.json",
                        "REUSES_PHASE_13G_B1_HOLDOUT_SEEDS"),
                spec("MATCH_ENGINE_V9_REQUALIFICATION",
                        "match-engine-v9-matchup-composition-requalification-v1/frozen-schedule.json", null),
                spec("MATCH_ENGINE_V9_FRESH_FAILED_SERIALIZATION_PREFLIGHT",
                        "match-engine-v9-auto-draft-matchup-composition-fresh-requalification-failed-serialization-preflight-v1/frozen-schedule.json",
                        "ENTIRE_FROZEN_NAMESPACE_RETIRED_AFTER_FOUR_SHARDS_STARTED"),
                spec("MATCHUP_V9_STRUCTURE_ATTRIBUTION",
                        "matchup-v9-structure-effect-attribution-v1/frozen-attribution-schedule.json", null),
                spec("COMPOSITION_V9_FAILED_WORKER_ISOLATION",
                        "composition-v9-application-causality-hardening-failed-worker-isolation-v1/frozen-diagnostic-schedule.json", null),
                spec("COMPOSITION_V9_PROVENANCE_GAP_V2",
                        "composition-v9-application-causality-hardening-blocked-provenance-gap-v2/frozen-diagnostic-schedule.json", null),
                spec("COMPOSITION_V9_PROVENANCE_GAP_V3",
                        "composition-v9-application-causality-hardening-blocked-provenance-gap-v3/frozen-diagnostic-schedule.json", null),
                spec("COMPOSITION_V9_PROVENANCE_GAP_V4",
                        "composition-v9-application-causality-hardening-blocked-provenance-gap-v4/frozen-diagnostic-schedule.json", null),
                spec("COMPOSITION_V9_V5",
                        "composition-v9-application-causality-hardening-v5/frozen-diagnostic-schedule.json", null),
                spec("COMPOSITION_V9_V6",
                        "composition-v9-application-causality-hardening-v6/frozen-diagnostic-schedule.json",
                        "REUSES_COMPOSITION_V9_V5_SEEDS_NO_NEW_CONSUMPTION")
        ));
        ArrayList<LedgerSource> sources = new ArrayList<>();
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        boolean complete = true;
        for (SourceSpec spec : specs) {
            Path source = reports.resolve(spec.relativePath());
            if (!Files.isRegularFile(source)) {
                sources.add(new LedgerSource(spec.sourceId(), spec.relativePath(), "MISSING",
                        "MISSING", "UNKNOWN", List.of(), 0, 0, spec.relationship(), false));
                complete = false;
                continue;
            }
            JsonNode root = mapper.readTree(source.toFile());
            ArrayList<Long> values = new ArrayList<>();
            collectSeeds(root, null, values);
            LinkedHashSet<Long> sourceUnique = new LinkedHashSet<>(values);
            boolean reuseOnly = spec.relationship() != null && spec.relationship().startsWith("REUSES_");
            if (!reuseOnly) unique.addAll(sourceUnique);
            Path directory = source.getParent();
            Path manifest = directory.resolve("SHA256SUMS.txt");
            String manifestKind = Files.isRegularFile(manifest)
                    ? "RAW_SHA256_OF_SHA256SUMS" : "RAW_SHA256_OF_SOURCE_CONTRACT";
            String manifestHash = MatchEngineV9FreshRequalificationContract.sha256(
                    Files.readAllBytes(Files.isRegularFile(manifest) ? manifest : source));
            sources.add(new LedgerSource(spec.sourceId(), spec.relativePath(), manifestHash,
                    manifestKind, findText(root, "seedNamespace", "UNKNOWN"),
                    sampleLanes(root), values.size(), sourceUnique.size(), spec.relationship(), true));
        }
        List<Long> autoSeeds = AutoDraftVarietyV1Schedule.SEEDS;
        unique.addAll(autoSeeds);
        Path autoManifest = reports.resolve("auto-draft-variety-v1/SHA256SUMS.txt");
        boolean autoPresent = Files.isRegularFile(autoManifest);
        sources.add(new LedgerSource("AUTO_DRAFT_VARIETY_V1_FIXED_SEEDS",
                "src/test/java/com/lolfm/draft/AutoDraftVarietyV1Schedule.java",
                autoPresent ? MatchEngineV9FreshRequalificationContract.sha256(
                        Files.readAllBytes(autoManifest)) : "MISSING",
                "RAW_SHA256_OF_SHA256SUMS", "AUTO_DRAFT_VARIETY_V1_FIXED_SEEDS",
                List.of("STRUCTURAL_VARIETY"), autoSeeds.size(), new HashSet<>(autoSeeds).size(),
                null, autoPresent));
        complete &= autoPresent;
        List<Long> canonicalUnique = unique.stream().sorted().toList();
        return new Ledger(SCHEMA, complete, sources.size(), List.copyOf(sources),
                canonicalUnique.size(), canonicalUnique.stream().map(String::valueOf).toList(),
                "SIGNED_LONG_BIG_ENDIAN_FIRST_8_BYTES_OF_SHA256");
    }

    private static SourceSpec spec(String id, String path, String relationship) {
        return new SourceSpec(id, path, relationship);
    }

    private static void collectSeeds(JsonNode node, String fieldName, List<Long> target) {
        if (node == null) return;
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (SEED_FIELDS.contains(normalized)) {
            collectSeedValues(node, target);
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectSeeds(
                    entry.getValue(), entry.getKey(), target));
        } else if (node.isArray()) {
            node.forEach(value -> collectSeeds(value, fieldName, target));
        }
    }

    private static void collectSeedValues(JsonNode node, List<Long> target) {
        if (node.isIntegralNumber()) {
            target.add(node.longValue());
        } else if (node.isTextual()) {
            try {
                target.add(Long.parseLong(node.textValue()));
            } catch (NumberFormatException ignored) {
                // Namespace and descriptive seed fields are identities, not consumed values.
            }
        } else if (node.isArray()) {
            node.forEach(value -> collectSeedValues(value, target));
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectSeedValues(entry.getValue(), target));
        }
    }

    private static String findText(JsonNode root, String field, String fallback) {
        if (root.has(field) && root.path(field).isTextual()) return root.path(field).asText();
        if (root.isObject()) {
            var iterator = root.elements();
            while (iterator.hasNext()) {
                String found = findText(iterator.next(), field, null);
                if (found != null) return found;
            }
        } else if (root.isArray()) {
            for (JsonNode value : root) {
                String found = findText(value, field, null);
                if (found != null) return found;
            }
        }
        return fallback;
    }

    private static List<String> sampleLanes(JsonNode root) {
        Set<String> lanes = new HashSet<>();
        collectTexts(root, "sampleLane", lanes);
        collectTexts(root, "lane", lanes);
        return lanes.stream().sorted().toList();
    }

    private static void collectTexts(JsonNode node, String field, Set<String> target) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey().equals(field) && entry.getValue().isTextual()) {
                    target.add(entry.getValue().asText());
                }
                collectTexts(entry.getValue(), field, target);
            });
        } else if (node.isArray()) {
            node.forEach(value -> collectTexts(value, field, target));
        }
    }

    private record SourceSpec(String sourceId, String relativePath, String relationship) { }

    public record Ledger(
            String schemaVersion,
            boolean complete,
            int sourceCount,
            List<LedgerSource> sources,
            int historicalUniqueSeedCount,
            List<String> historicalUniqueSeeds,
            String signedLongDerivation
    ) {
        public Ledger {
            sources = List.copyOf(sources);
            historicalUniqueSeeds = List.copyOf(historicalUniqueSeeds);
        }

        public Set<Long> seedSet() {
            LinkedHashSet<Long> result = new LinkedHashSet<>();
            historicalUniqueSeeds.forEach(value -> result.add(Long.parseLong(value)));
            return Set.copyOf(result);
        }
    }

    public record LedgerSource(
            String sourceId,
            String artifactPathOrSourceContract,
            String manifestSha256,
            String manifestIdentityKind,
            String namespace,
            List<String> sampleLanes,
            int consumedSeedOccurrences,
            int uniqueSeedCount,
            String relationship,
            boolean present
    ) {
        public LedgerSource {
            sampleLanes = List.copyOf(sampleLanes);
        }
    }
}

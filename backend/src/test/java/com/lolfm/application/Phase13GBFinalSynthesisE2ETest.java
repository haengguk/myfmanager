package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase13GBFinalSynthesisE2ETest {
    private static final String ECONOMY = "ECONOMY_MINUS_FULL";
    private static final String TEMPO = "TEMPO_MINUS_ECONOMY";
    private static final String FULL = "FULL_SYSTEM_CANDIDATE_V1";
    private static final String ECONOMY_PROFILE =
            "FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1";
    private static final String TEMPO_PROFILE =
            "FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1";
    private static final List<String> TEAMS = List.of(
            "GEN", "T1", "HLE", "DK", "KT", "NS", "BRO", "BFX", "DNS", "KRX");

    @Test
    void syntheticFullWriteBindsRuntimeAndCreatesCanonicalDecision(@TempDir Path temp)
            throws Exception {
        SyntheticBundle bundle = createBundle(temp.resolve("bundle"));
        var runtime = syntheticRuntimeIdentity();
        Path first = temp.resolve("output-a");
        Path second = temp.resolve("output-b");

        var result = Phase13GBFinalSynthesis.writeVerifiedRuntimeIdentityForTest(
                bundle.b2(), bundle.b3(), runtime, runtime.runtimeIdentityHash(), first);
        Phase13GBFinalSynthesis.writeVerifiedRuntimeIdentityForTest(
                bundle.b2(), bundle.b3(), runtime, runtime.runtimeIdentityHash(), second);

        assertThat(result.evidenceStatus()).isEqualTo("FINAL_EVIDENCE_VALID");
        assertThat(result.productionDecision()).isEqualTo("KEEP_CURRENT_RUNTIME_DEFAULT");
        assertThat(result.retainedRuntimeProfileId()).isEqualTo("BASELINE_V1");
        assertThat(result.runtimeIdentityStatus()).isEqualTo("EXACT");
        assertThat(result.freezeReadiness()).isEqualTo("READY_FOR_MATCH_ENGINE_V1_FREEZE");
        assertThat(result.inputPairedRows()).isEqualTo(6_400);
        assertThat(Phase13GBFinalSynthesis.verifyManifest(first, 6).exact()).isTrue();
        assertByteIdentical(first, second);

        String binding = Files.readString(first.resolve(
                Phase13GBFinalSynthesis.EVIDENCE_BINDING_FILE));
        String runtimeJson = Files.readString(first.resolve(
                Phase13GBFinalSynthesis.RETAINED_RUNTIME_IDENTITY_FILE));
        String decision = Files.readString(first.resolve(
                Phase13GBFinalSynthesis.DECISION_FILE));
        String synthesis = Files.readString(first.resolve(
                Phase13GBFinalSynthesis.SYNTHESIS_FILE));
        String segments = Files.readString(first.resolve(
                Phase13GBFinalSynthesis.SEGMENTED_SENSITIVITY_FILE));

        assertThat(binding).contains("\"pairedEvidenceRowsRead\":6400")
                .contains("\"status\":\"EXACT\"");
        assertThat(runtimeJson).contains("\"retainedRuntimeProfileId\":\"BASELINE_V1\"")
                .contains("\"runtimeIdentityStatus\":\"EXACT\"")
                .contains("\"HTTP_MATCH_SIMULATE\"")
                .contains("\"LOW_LEVEL_SIMULATION_OPTIONS_PRODUCTION_DEFAULTS\"");
        assertThat(decision).contains("\"schemaVersion\":\"FINAL_13G_B_PRODUCTION_DECISION_V2\"")
                .contains("\"productionDecision\":\"KEEP_CURRENT_RUNTIME_DEFAULT\"")
                .contains("\"matchEngineV1FreezeReadiness\":\"READY_FOR_MATCH_ENGINE_V1_FREEZE\"")
                .contains("\"retainedRuntimeProfileId\":\"BASELINE_V1\"")
                .contains("\"commonBucketCount\":10")
                .contains("\"causalAttributionEstablished\":false")
                .contains("\"isolatedChampionEffect\":false")
                .contains("CHAMPION_BUCKET_SENSITIVITY_PATTERN_REPRODUCED_NOT_CAUSAL_EFFECT")
                .doesNotContain("champion dependence is real")
                .doesNotContain("SYSTEMATIC_CHAMPION_DEPENDENCE");
        assertThat(synthesis).contains("UNWEIGHTED_PEARSON_COMMON_CHAMPION_BUCKETS")
                .contains("\"knownConfounders\":[\"PLAYER\",\"TEAM\",\"FIXTURE\",\"MATCHUP\"]")
                .contains("\"preRegisteredDecisionGate\":false");
        assertThat(segments).contains(",PLAYER,").contains(",CHAMPION,")
                .contains(",PLAYER_CHAMPION,").contains(",FIXTURE,").contains(",TEAM,");
    }

    @Test
    void mutatedRuntimeIdentityFieldsCannotCreateReadyDecision(@TempDir Path temp)
            throws Exception {
        SyntheticBundle bundle = createBundle(temp.resolve("bundle"));
        var valid = syntheticRuntimeIdentity();
        for (Map.Entry<String, String> mutation : Map.of(
                "retainedRuntimeProfileId", "FULL_SYSTEM_CANDIDATE_V1",
                "retainedConfigurationHash", "1".repeat(64),
                "engineImplementationVersion", "MUTATED_ENGINE",
                "productionSourceTreeHash", "2".repeat(64),
                "resourceProvenanceHash", "3".repeat(64),
                "httpInjectedAutowiredSimulatorExact", "false").entrySet()) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>(valid.identityValues());
            values.put(mutation.getKey(), mutation.getValue());
            var mutated = Phase13GBFinalRuntimeIdentityEvidence.create(values);

            assertThatThrownBy(() ->
                    Phase13GBFinalSynthesis.writeVerifiedRuntimeIdentityForTest(
                            bundle.b2(), bundle.b3(), mutated,
                            valid.runtimeIdentityHash(),
                            temp.resolve("mutated-" + mutation.getKey())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("runtime identity contract mismatch");
        }
    }

    @Test
    void missingRuntimeIdentityIsExplicitlyUnboundAndBlocksFreeze(@TempDir Path temp)
            throws Exception {
        SyntheticBundle bundle = createBundle(temp.resolve("bundle"));
        Path output = temp.resolve("unbound");

        var result = Phase13GBFinalSynthesis.write(bundle.b2(), bundle.b3(), output);

        assertThat(result.productionDecision()).isEqualTo("KEEP_CURRENT_RUNTIME_DEFAULT");
        assertThat(result.runtimeIdentityStatus()).isEqualTo("UNBOUND");
        assertThat(result.freezeReadiness())
                .isEqualTo("BLOCK_MATCH_ENGINE_V1_FREEZE_RUNTIME_IDENTITY_UNBOUND");
        assertThat(Files.readString(output.resolve(Phase13GBFinalSynthesis.DECISION_FILE)))
                .contains("\"runtimeIdentityStatus\":\"UNBOUND\"")
                .contains("BLOCK_MATCH_ENGINE_V1_FREEZE_RUNTIME_IDENTITY_UNBOUND");
    }

    @Test
    void rawManifestAndPairedCardinalityMutationsAreRejected(@TempDir Path temp)
            throws Exception {
        var runtime = syntheticRuntimeIdentity();

        SyntheticBundle manifestTamper = createBundle(temp.resolve("manifest-tamper"));
        Files.writeString(manifestTamper.b2().resolve("phase13g-b2-review.json"),
                "tampered", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Phase13GBFinalSynthesis.writeVerifiedRuntimeIdentityForTest(
                manifestTamper.b2(), manifestTamper.b3(), runtime,
                runtime.runtimeIdentityHash(), temp.resolve("manifest-out")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA mismatch");

        SyntheticBundle missing = createBundle(temp.resolve("missing-row"));
        Path missingPaired = missing.b3().resolve("phase13g-b3-paired-marginals.csv");
        List<String> missingLines = new ArrayList<>(Files.readAllLines(missingPaired));
        missingLines.remove(missingLines.size() - 1);
        Files.write(missingPaired, missingLines, StandardCharsets.UTF_8);
        rewriteManifest(missing.b3());
        assertThatThrownBy(() -> Phase13GBFinalSynthesis.writeVerifiedRuntimeIdentityForTest(
                missing.b2(), missing.b3(), runtime, runtime.runtimeIdentityHash(),
                temp.resolve("missing-out")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 800 paired rows but found 799");

        SyntheticBundle duplicate = createBundle(temp.resolve("duplicate-row"));
        Path duplicatePaired = duplicate.b3().resolve("phase13g-b3-paired-marginals.csv");
        List<String> duplicateLines = new ArrayList<>(Files.readAllLines(duplicatePaired));
        duplicateLines.add(duplicateLines.get(duplicateLines.size() - 1));
        Files.write(duplicatePaired, duplicateLines, StandardCharsets.UTF_8);
        rewriteManifest(duplicate.b3());
        assertThatThrownBy(() -> Phase13GBFinalSynthesis.writeVerifiedRuntimeIdentityForTest(
                duplicate.b2(), duplicate.b3(), runtime, runtime.runtimeIdentityHash(),
                temp.resolve("duplicate-out")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 800 paired rows but found 801");
    }

    @Test
    void runtimeEvidenceRejectsRawAndSelfSignedWiringMutations(@TempDir Path temp)
            throws Exception {
        var valid = syntheticRuntimeIdentity();
        Path rawTamper = temp.resolve("runtime-raw-tamper");
        Phase13GBFinalRuntimeIdentityEvidence.writeBundle(rawTamper, valid);
        Files.writeString(rawTamper.resolve(
                        Phase13GBFinalRuntimeIdentityEvidence.EVIDENCE_FILE),
                valid.canonicalEvidence() + "tampered=true\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Phase13GBFinalRuntimeIdentityEvidence.readBundle(
                rawTamper, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raw SHA manifest mismatch");

        LinkedHashMap<String, String> changed = new LinkedHashMap<>(valid.identityValues());
        changed.put("httpInjectedAutowiredSimulatorExact", "false");
        var selfSigned = Phase13GBFinalRuntimeIdentityEvidence.create(changed);
        Path selfSignedBundle = temp.resolve("runtime-self-signed");
        Phase13GBFinalRuntimeIdentityEvidence.writeBundle(selfSignedBundle, selfSigned);
        assertThatThrownBy(() -> Phase13GBFinalRuntimeIdentityEvidence.readBundle(
                selfSignedBundle, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not frozen");
    }

    private static SyntheticBundle createBundle(Path root) throws Exception {
        Path b2 = root.resolve("b2");
        Path b3 = root.resolve("b3");
        Files.createDirectories(b2);
        Files.createDirectories(b3);
        writeB2Paired(b2.resolve("phase13g-b2-paired-marginals.csv"));
        writeJungle(b2.resolve("phase13g-b2-jungle-checkpoints.csv"), true);
        Files.writeString(b2.resolve("phase13g-b2-review.json"),
                "{\"calibrationMatchExecutionCount\":12000,"
                        + "\"status\":\"CALIBRATION_EVIDENCE_READY_FOR_REVIEW\"}\n",
                StandardCharsets.UTF_8);
        addFillers(b2, 13, "b2");
        rewriteManifest(b2);

        writeB3Paired(b3.resolve("phase13g-b3-paired-marginals.csv"));
        writeJungle(b3.resolve("phase13g-b3-jungle-observations.csv"), false);
        writeFixedDrafts(b3.resolve("phase13g-b3-fixed-drafts.csv"));
        Files.writeString(b3.resolve("phase13g-b3-frozen-gate-evaluation.json"),
                "{\"gateId\" : \"ECONOMY_ECONOMY_MINUS_FULL_PRIMARY_LEAGUE_G1_WINNER_FLIP_RATE\","
                        + "\"actual\" : 0.013888888888888888,"
                        + "\"lowerInclusive\" : 0.001,"
                        + "\"upperInclusive\" : 0.013794550001,"
                        + "\"passed\" : false}\n",
                StandardCharsets.UTF_8);
        String b2ReviewSha = Phase13GBFinalSynthesis.sha256(
                b2.resolve("phase13g-b2-review.json"));
        String b2ManifestSha = Phase13GBFinalSynthesis.sha256(
                b2.resolve("SHA256SUMS.txt"));
        Files.writeString(b3.resolve("phase13g-b3-final-review.json"),
                "{\"b2Evidence\":{\"reviewFileSha256\":\"" + b2ReviewSha
                        + "\",\"shaManifestSha256\":\"" + b2ManifestSha + "\"},"
                        + "\"calibrationMatchExecutionCount\":0,"
                        + "\"candidateFreezeIdentityHash\":\"" + "4".repeat(64) + "\","
                        + "\"economyCandidateVerdict\":\"FAIL\","
                        + "\"evidenceStatus\":\"HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW\","
                        + "\"frozenContractHash\":\"" + "5".repeat(64) + "\","
                        + "\"acceptanceGateIdentityHash\":\"" + "6".repeat(64) + "\","
                        + "\"holdoutMatchExecutionCount\":4000,"
                        + "\"productionDecision\":\"NOT_EVALUATED\","
                        + "\"tempoCandidateVerdict\":\"REVIEW_REQUIRED\"}\n",
                StandardCharsets.UTF_8);
        addFillers(b3, 13, "b3");
        rewriteManifest(b3);
        return new SyntheticBundle(b2, b3);
    }

    private static void writeB2Paired(Path output) throws Exception {
        StringBuilder rows = new StringBuilder(
                "comparisonId,fixtureId,fixtureLane,pairId,blueTeamCode,redTeamCode,seedIndex,seed,fromProfile,toProfile,fromWinner,toWinner,winnerFlipped,durationDelta,blueGoldEdgeDelta,totalKillsDelta,totalDragonsDelta,totalTowersDelta,blueJungleCsDelta,blueJungleGoldDelta,blueJungleExperienceDelta,blueJungleLevelDelta,redJungleCsDelta,redJungleGoldDelta,redJungleExperienceDelta,redJungleLevelDelta,jungleGankAttemptsDelta,counterGankAttemptsDelta\n");
        for (int fixture = 0; fixture < 100; fixture++) {
            for (int seed = 0; seed < 24; seed++) {
                appendPaired(rows, true, ECONOMY, fixture, seed, FULL, ECONOMY_PROFILE,
                        (fixture * 24 + seed) % 200 == 0);
                appendPaired(rows, true, TEMPO, fixture, seed, ECONOMY_PROFILE, TEMPO_PROFILE,
                        (fixture * 24 + seed) % 3 == 0);
            }
        }
        Files.writeString(output, rows, StandardCharsets.UTF_8);
    }

    private static void writeB3Paired(Path output) throws Exception {
        StringBuilder rows = new StringBuilder(
                "comparisonId,fixtureId,fixtureLane,pairId,seedIndex,seed,fromProfile,toProfile,fromWinner,toWinner,winnerFlipped,durationDelta,blueGoldEdgeDelta,blueJungleCsDelta,redJungleCsDelta,blueJungleExperienceDelta,redJungleExperienceDelta,jungleGankAttemptsDelta,counterGankAttemptsDelta\n");
        for (int fixture = 0; fixture < 100; fixture++) {
            for (int seed = 0; seed < 8; seed++) {
                appendPaired(rows, false, ECONOMY, fixture, seed, FULL, ECONOMY_PROFILE,
                        (fixture * 8 + seed) % 200 == 0);
                appendPaired(rows, false, TEMPO, fixture, seed, ECONOMY_PROFILE, TEMPO_PROFILE,
                        (fixture * 8 + seed) % 3 == 0);
            }
        }
        Files.writeString(output, rows, StandardCharsets.UTF_8);
    }

    private static void appendPaired(
            StringBuilder rows,
            boolean b2,
            String comparison,
            int fixture,
            int seed,
            String fromProfile,
            String toProfile,
            boolean flipped
    ) {
        String fixtureId = fixtureId(fixture);
        rows.append(comparison).append(',').append(fixtureId)
                .append(",PRIMARY_LEAGUE_G1,").append(comparison).append('-')
                .append(fixture).append('-').append(seed).append(',');
        if (b2) {
            rows.append(TEAMS.get(fixture % 10)).append(',')
                    .append(TEAMS.get((fixture + 1) % 10)).append(',');
        }
        rows.append(seed).append(',').append(100_000L + fixture * 100L + seed).append(',')
                .append(fromProfile).append(',').append(toProfile).append(",BLUE,")
                .append(flipped ? "RED,true" : "BLUE,false");
        int numericColumns = b2 ? 15 : 8;
        for (int index = 0; index < numericColumns; index++) rows.append(",0");
        rows.append('\n');
    }

    private static void writeJungle(Path output, boolean b2) throws Exception {
        StringBuilder rows = new StringBuilder(b2
                ? "jobId,fixtureId,fixtureLane,seedIndex,profileId,checkpointKind,requestedTimeSeconds,actualTimeSeconds,side,playerId,championId,kills,deaths,assists,cs,gold,totalExperience,level,itemStage,alive,canFarm\n"
                : "jobId,checkpointKind,requestedTimeSeconds,actualTimeSeconds,side,playerId,championId,kills,deaths,assists,cs,gold,totalExperience,level,itemStage,alive,canFarm\n");
        int seeds = b2 ? 24 : 8;
        String lane = b2 ? "CALIBRATION" : "HOLDOUT";
        for (int fixture = 0; fixture < 100; fixture++) {
            for (int seed = 0; seed < seeds; seed++) {
                for (String profile : List.of(FULL, ECONOMY_PROFILE)) {
                    String jobId = fixtureId(fixture) + '|' + lane + '|' + seed + '|' + profile;
                    for (String side : List.of("BLUE", "RED")) {
                        rows.append(jobId).append(',');
                        if (b2) {
                            rows.append(fixtureId(fixture)).append(",PRIMARY_LEAGUE_G1,")
                                    .append(seed).append(',').append(profile).append(',');
                        }
                        int championOffset = side.equals("BLUE") ? 0 : 5;
                        rows.append("FINAL,0,0,").append(side).append(',')
                                .append("player-").append(side.toLowerCase()).append('-')
                                .append(fixture % 10).append(',')
                                .append("champion-").append(championOffset + fixture % 5)
                                .append(",0,0,0,0,500,0,1,STARTING,true,true\n");
                    }
                }
            }
        }
        Files.writeString(output, rows, StandardCharsets.UTF_8);
    }

    private static void writeFixedDrafts(Path output) throws Exception {
        StringBuilder rows = new StringBuilder(
                "fixtureId,fixtureLane,pairId,blueTeamCode,redTeamCode,seriesGameNumber,productionOrchestrationCount,rosterIdentityHash,seriesHistoryBeforeHash,draftDecisionHash,finalDraftHash,finalAssignmentHash,canonicalAssignments\n");
        for (int fixture = 0; fixture < 100; fixture++) {
            rows.append(fixtureId(fixture)).append(",PRIMARY_LEAGUE_G1,PAIR-")
                    .append(fixture).append(',').append(TEAMS.get(fixture % 10)).append(',')
                    .append(TEAMS.get((fixture + 1) % 10))
                    .append(",1,1,").append("7".repeat(64)).append(',')
                    .append("8".repeat(64)).append(',').append("9".repeat(64)).append(',')
                    .append("a".repeat(64)).append(',').append("b".repeat(64))
                    .append(",SYNTHETIC_ASSIGNMENTS\n");
        }
        Files.writeString(output, rows, StandardCharsets.UTF_8);
    }

    private static void addFillers(Path directory, int count, String prefix) throws Exception {
        for (int index = 0; index < count; index++) {
            Files.writeString(directory.resolve(prefix + "-filler-" + index + ".txt"),
                    prefix + '-' + index + '\n', StandardCharsets.UTF_8);
        }
    }

    private static void rewriteManifest(Path directory) throws Exception {
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SHA256SUMS.txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) {
            manifest.append(Phase13GBFinalSynthesis.sha256(file)).append("  ")
                    .append(file.getFileName()).append('\n');
        }
        Files.writeString(directory.resolve("SHA256SUMS.txt"), manifest, StandardCharsets.UTF_8);
    }

    private static Phase13GBFinalRuntimeIdentityEvidence.Evidence syntheticRuntimeIdentity()
            throws Exception {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String key : Phase13GBFinalRuntimeIdentityEvidence.orderedIdentityKeysForTest()) {
            values.put(key, defaultRuntimeValue(key));
        }
        values.put("schemaVersion", Phase13GBFinalRuntimeIdentityEvidence.SCHEMA);
        values.put("evidenceStatus", "VERIFIED_FROM_PRODUCTION_REGISTRY_AND_WIRING");
        values.put("retainedRuntimeProfileId", "BASELINE_V1");
        values.put("configurationHashAlgorithm",
                "SHA256_UTF8_EXPLICIT_ORDERED_FIELD_LINES_TRAILING_NEWLINE_V1");
        values.put("gameplayConfigurationSchema", "EXPLICIT_SIMULATION_RUNTIME_CONFIGURATION_V1");
        values.put("championMatchupMode", "OFF");
        values.put("teamCompositionGameplayMode", "OFF");
        values.put("jungleClearContribution", "DISABLED_NOT_INTEGRATED");
        values.put("activeGameplayRulesVersion", "MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2");
        values.put("engineImplementationVersion", "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6");
        values.put("productionSourceTreeHashAlgorithm",
                "SHA256_UTF8_SORTED_LOGICAL_PATH_PIPE_RAW_OR_NORMALIZED_FILE_SHA256_LINES_V2");
        values.put("productionSourceTreeFileCount", "472");
        values.put("draftRuleSetIdentity", "PROFESSIONAL_5_BAN_5_PICK_HARD_FEARLESS_V1");
        values.put("realDraftDefaultResolvedProfileId", "BASELINE_V1");
        values.put("realDraftExplicitBaselineResolvedProfileId", "BASELINE_V1");
        values.put("springAutowiredResolvedProfileId", "BASELINE_V1");
        values.put("httpResolvedProfileId", "BASELINE_V1");
        values.put("httpInputRosterSource", "DUMMY_DATA_FACTORY");
        values.put("lowLevelProductionDefaultsIdentity",
                "LOW_LEVEL_SIMULATION_OPTIONS_PRODUCTION_DEFAULTS");
        values.put("lowLevelProductionDefaultsChampionMatchupMode", "GEOMETRIC_V2");
        values.put("lowLevelProductionDefaultsTeamCompositionGameplayMode", "PRODUCTION_V2");
        values.put("lowLevelProductionDefaultsJungleClearContribution",
                "DISABLED_NOT_INTEGRATED");
        values.put("jungleEconomyCandidateActivation", "false");
        values.put("jungleTempoCandidateActivation", "false");
        values.put("productionGameplayChanged", "false");
        values.put("automaticTuningPerformed", "false");
        values.put("holdoutRerunPerformed", "false");
        values.put("httpRealDraftTransitionPerformed", "false");
        values.put("realDraftExplicitBaselineAuthoritativeApplicationRuntimeDefault", "false");
        values.put("lowLevelProductionDefaultsAuthoritativeApplicationRuntimeDefault", "false");
        values.put("runtimeIdentityHashAlgorithm",
                Phase13GBFinalRuntimeIdentityEvidence.HASH_ALGORITHM);
        return Phase13GBFinalRuntimeIdentityEvidence.create(values);
    }

    private static String defaultRuntimeValue(String key) throws Exception {
        if (key.endsWith("Hash") || key.equals("retainedConfigurationHash")) {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(key.getBytes(StandardCharsets.UTF_8)));
        }
        if (key.endsWith("Enabled") || key.endsWith("Separated")
                || key.endsWith("AuthoritativeApplicationRuntimeDefault")
                || key.endsWith("ParityVerified") || key.endsWith("Exact")
                || key.endsWith("Performed") || key.endsWith("Activation")
                || key.endsWith("Changed")) {
            return "true";
        }
        return "SYNTHETIC_" + key.toUpperCase(java.util.Locale.ROOT);
    }

    private static void assertByteIdentical(Path first, Path second) throws Exception {
        List<String> names;
        try (var stream = Files.list(first)) {
            names = stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString()).sorted().toList();
        }
        assertThat(names).hasSize(7);
        for (String name : names) {
            assertThat(Files.readAllBytes(second.resolve(name)))
                    .as(name).isEqualTo(Files.readAllBytes(first.resolve(name)));
        }
    }

    private static String fixtureId(int fixture) {
        return String.format(java.util.Locale.ROOT, "G1_SYNTHETIC_%03d", fixture);
    }

    private record SyntheticBundle(Path b2, Path b3) {
    }
}

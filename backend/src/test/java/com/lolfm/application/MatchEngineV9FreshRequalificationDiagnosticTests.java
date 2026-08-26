package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

abstract class MatchEngineV9FreshTestSupport {
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;
    @Autowired RealDraftMatchPreflightValidator preflight;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;

    MatchEngineV9FreshRequalificationRunner runner() {
        return new MatchEngineV9FreshRequalificationRunner(mapper, champions, teams, preflight,
                inputs, simulators, identities, ratings, proficiencies);
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-fresh-freeze")
class MatchEngineV9FreshFreezeTest extends MatchEngineV9FreshTestSupport {
    @Test void freezesContractLedgerScheduleAndSourceIdentity() throws Exception {
        var result = runner().freeze(Path.of("."), MatchEngineV9FreshRequalificationRunner.OUTPUT);
        assertThat(result.contractHash()).matches("[0-9a-f]{64}");
        assertThat(result.seedOverlapAudit().clean()).isTrue();
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-fresh-smoke")
class MatchEngineV9FreshSmokeTest extends MatchEngineV9FreshTestSupport {
    @Test void provesDraftSharingReachabilityReplayAndInstrumentationWithoutOfficialSeeds()
            throws Exception {
        assertThat(runner().smoke(Path.of("."),
                MatchEngineV9FreshRequalificationRunner.OUTPUT).clean()).isTrue();
    }
}

abstract class MatchEngineV9FreshDraftProbeSupport extends MatchEngineV9FreshTestSupport {
    final void probe(String name) throws Exception {
        var harness = new FreshAutoDraftRealMatchHarness(mapper, champions, teams, preflight,
                inputs, simulators, identities, ratings, proficiencies);
        var schedule = MatchEngineV9FreshRequalificationContract.schedule().fixtures();
        var fixtures = new java.util.ArrayList<MatchEngineV9FreshRequalificationContract.Fixture>();
        fixtures.addAll(schedule.subList(0, 9));
        fixtures.add(schedule.get(90));
        long[] seeds = {0x13579bdf2468ace0L, -0x02468ace13579bdfL};
        java.util.ArrayList<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (var fixture : fixtures) {
            for (long seed : seeds) {
                var prepared = harness.prepare(fixture, seed);
                var draft = prepared.input().finalDraft();
                rows.add(new java.util.TreeMap<>(java.util.Map.ofEntries(
                        java.util.Map.entry("fixtureId", fixture.fixtureId()),
                        java.util.Map.entry("seed", Long.toString(seed)),
                        java.util.Map.entry("policyId", draft.draftSelectionPolicyId()),
                        java.util.Map.entry("policyHash", draft.draftSelectionPolicyHash()),
                        java.util.Map.entry("traceAlgorithm",
                                com.lolfm.draft.DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM),
                        java.util.Map.entry("traceHash", draft.draftSelectionTraceHash()),
                        java.util.Map.entry("traceCount", draft.selectionTraces().size()),
                        java.util.Map.entry("decisionHash", draft.draftDecisionHash()),
                        java.util.Map.entry("finalDraftHash", draft.finalDraftHash()),
                        java.util.Map.entry("finalAssignmentHash", draft.finalAssignmentHash()),
                        java.util.Map.entry("inputHash", prepared.input().inputHash()))));
            }
        }
        assertThat(rows).hasSize(20);
        assertThat(rows).allSatisfy(row -> assertThat(row.get("traceCount")).isEqualTo(20));
        var canonical = mapper.copy()
                .enable(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        MatchEngineV9FreshRequalificationRunner.writeReplace(
                Path.of("build", "reports", "match-engine-v9-fresh-draft-probe-" + name + ".json"),
                canonical.writeValueAsBytes(rows));
    }
}

@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-draft-probe")
class MatchEngineV9FreshDraftProbeATest extends MatchEngineV9FreshDraftProbeSupport {
    @Test void execute() throws Exception { probe("a"); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-draft-probe")
class MatchEngineV9FreshDraftProbeBTest extends MatchEngineV9FreshDraftProbeSupport {
    @Test void execute() throws Exception { probe("b"); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-fresh-draft-probe-verify")
class MatchEngineV9FreshDraftProbeVerificationTest {
    @Test void freshJvmDraftArtifactsAreByteExact() throws Exception {
        Path reports = Path.of("build", "reports");
        assertThat(Files.readAllBytes(reports.resolve("match-engine-v9-fresh-draft-probe-a.json")))
                .isEqualTo(Files.readAllBytes(
                        reports.resolve("match-engine-v9-fresh-draft-probe-b.json")));
    }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-fresh-full-receipt")
class MatchEngineV9FreshFullReceiptTest extends MatchEngineV9FreshTestSupport {
    @Test void authenticatesTheAlreadyCompletedDefaultFullRegression() throws Exception {
        Path results = Path.of("build", "test-results", "test");
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        double seconds = 0.0;
        long oldestResultMillis = Long.MAX_VALUE;
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var files = Files.list(results)) {
            for (Path path : files.filter(value -> value.getFileName().toString()
                    .startsWith("TEST-")).toList()) {
                oldestResultMillis = Math.min(oldestResultMillis,
                        Files.getLastModifiedTime(path).toMillis());
                var suite = factory.newDocumentBuilder().parse(path.toFile())
                        .getDocumentElement();
                tests += Integer.parseInt(suite.getAttribute("tests"));
                failures += Integer.parseInt(suite.getAttribute("failures"));
                errors += Integer.parseInt(suite.getAttribute("errors"));
                skipped += Integer.parseInt(suite.getAttribute("skipped"));
                seconds += Double.parseDouble(suite.getAttribute("time"));
            }
        }
        assertThat(oldestResultMillis).isGreaterThanOrEqualTo(Files.getLastModifiedTime(
                MatchEngineV9FreshRequalificationRunner.OUTPUT.resolve("contract.json"))
                .toMillis());
        var receipt = runner().recordFullRegressionReceipt(Path.of("."),
                MatchEngineV9FreshRequalificationRunner.OUTPUT, tests, failures, errors,
                skipped, Math.round(seconds * 1_000.0));
        assertThat(receipt.clean()).isTrue();
        assertThat(receipt.tests()).isPositive();
    }
}

abstract class MatchEngineV9FreshCalibrationShardSupport
        extends MatchEngineV9FreshTestSupport {
    final void executeShard(int shard) throws Exception {
        var result = runner().runShard(Path.of("."),
                MatchEngineV9FreshRequalificationRunner.OUTPUT,
                MatchEngineV9FreshRequalificationContract.SampleLane.CALIBRATION, shard);
        assertThat(result.fixtureCount()).isEqualTo(25);
        assertThat(result.draftCount()).isEqualTo(100);
        assertThat(result.rowCount()).isEqualTo(300);
        assertThat(result.replayChecks()).isEqualTo(75);
        assertThat(result.instrumentationChecks()).isEqualTo(75);
    }
}

@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-calibration-shard")
class MatchEngineV9FreshCalibrationShard0Test extends MatchEngineV9FreshCalibrationShardSupport {
    @Test void execute() throws Exception { executeShard(0); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-calibration-shard")
class MatchEngineV9FreshCalibrationShard1Test extends MatchEngineV9FreshCalibrationShardSupport {
    @Test void execute() throws Exception { executeShard(1); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-calibration-shard")
class MatchEngineV9FreshCalibrationShard2Test extends MatchEngineV9FreshCalibrationShardSupport {
    @Test void execute() throws Exception { executeShard(2); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-calibration-shard")
class MatchEngineV9FreshCalibrationShard3Test extends MatchEngineV9FreshCalibrationShardSupport {
    @Test void execute() throws Exception { executeShard(3); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-fresh-calibration-finalize")
class MatchEngineV9FreshCalibrationFinalizerTest extends MatchEngineV9FreshTestSupport {
    @Test void authorizesHoldoutOnlyWhenOperationalGateIsClean() throws Exception {
        var review = runner().finalizeCalibration(Path.of("."),
                MatchEngineV9FreshRequalificationRunner.OUTPUT);
        assertThat(review.matchRowCount()).isEqualTo(1_200);
        assertThat(review.operationalGateClean()).isTrue();
    }
}

abstract class MatchEngineV9FreshHoldoutShardSupport extends MatchEngineV9FreshTestSupport {
    final void executeShard(int shard) throws Exception {
        var result = runner().runShard(Path.of("."),
                MatchEngineV9FreshRequalificationRunner.OUTPUT,
                MatchEngineV9FreshRequalificationContract.SampleLane.HOLDOUT, shard);
        assertThat(result.fixtureCount()).isEqualTo(25);
        assertThat(result.draftCount()).isEqualTo(100);
        assertThat(result.rowCount()).isEqualTo(300);
        assertThat(result.replayChecks()).isZero();
        assertThat(result.instrumentationChecks()).isZero();
    }
}

@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-holdout-shard")
class MatchEngineV9FreshHoldoutShard0Test extends MatchEngineV9FreshHoldoutShardSupport {
    @Test void execute() throws Exception { executeShard(0); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-holdout-shard")
class MatchEngineV9FreshHoldoutShard1Test extends MatchEngineV9FreshHoldoutShardSupport {
    @Test void execute() throws Exception { executeShard(1); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-holdout-shard")
class MatchEngineV9FreshHoldoutShard2Test extends MatchEngineV9FreshHoldoutShardSupport {
    @Test void execute() throws Exception { executeShard(2); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-holdout-shard")
class MatchEngineV9FreshHoldoutShard3Test extends MatchEngineV9FreshHoldoutShardSupport {
    @Test void execute() throws Exception { executeShard(3); }
}

abstract class MatchEngineV9FreshJvmArtifactSupport extends MatchEngineV9FreshTestSupport {
    final void write(String candidate) throws Exception {
        var result = runner().writeFreshJvmCandidate(Path.of("."),
                MatchEngineV9FreshRequalificationRunner.OUTPUT,
                MatchEngineV9FreshRequalificationRunner.OUTPUT.resolve(candidate));
        assertThat(result.coreMatchRows()).isEqualTo(2_400);
        assertThat(result.officialSimulationCount()).isEqualTo(3_000);
    }
}

@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-jvm-artifact")
class MatchEngineV9AutoDraftFreshJvmArtifactATest extends MatchEngineV9FreshJvmArtifactSupport {
    @Test void writeCandidate() throws Exception { write("fresh-jvm-candidate-a"); }
}
@SpringBootTest @Tag("diagnostic") @Tag("match-engine-v9-fresh-jvm-artifact")
class MatchEngineV9AutoDraftFreshJvmArtifactBTest extends MatchEngineV9FreshJvmArtifactSupport {
    @Test void writeCandidate() throws Exception { write("fresh-jvm-candidate-b"); }
}

@SpringBootTest
@Tag("diagnostic")
@Tag("match-engine-v9-fresh-promote")
class MatchEngineV9FreshPromotionTest extends MatchEngineV9FreshTestSupport {
    @Test void promotesOnlyByteIdenticalFreshJvmArtifacts() throws Exception {
        var output = MatchEngineV9FreshRequalificationRunner.OUTPUT;
        var result = runner().promoteFreshJvmCandidates(Path.of("."), output,
                output.resolve("fresh-jvm-candidate-a"),
                output.resolve("fresh-jvm-candidate-b"));
        assertThat(result.coreMatchRows()).isEqualTo(2_400);
        assertThat(result.eligibleProfiles()).contains(
                com.lolfm.simulator.SimulationRuntimeProfileId.BASELINE_V1);
    }
}

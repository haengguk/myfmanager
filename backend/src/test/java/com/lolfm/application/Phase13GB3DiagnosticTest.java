package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("diagnostic")
class Phase13GB3DiagnosticTest {
    private static final Path OUTPUT = Path.of("build", "reports", "phase13g-b3");
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired ConfiguredMatchSimulatorFactory simulators;
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired PlayerIdentityCatalog identities;
    @Autowired PlayerRatingCatalog ratings;
    @Autowired ChampionProficiencyCatalog proficiencies;
    @TempDir Path temporary;

    @Test
    @Tag("phase13g-b3-smoke")
    void dryRunSmokeDoesNotOpenHoldoutAndDetectsPayloadMutations() throws Exception {
        var result = runner().runSmoke(Path.of("."), temporary);
        assertThat(result.rows()).hasSize(5);
        assertThat(result.rows().stream().map(
                Phase13GB3HoldoutModel.MatchRow::profileId))
                .containsExactlyElementsOf(Phase13GB3FrozenHoldoutContract.PROFILE_ORDER);
        assertThat(result.rows()).allSatisfy(row -> {
            assertThat(row.sampleLane()).isEqualTo(
                    Phase13GB1AuditSchedule.SampleLane.DRY_RUN);
            assertThat(row.finalPlayerStates()).hasSize(10);
            assertThat(row.blueSupportCs()).isZero();
            assertThat(row.redSupportCs()).isZero();
            assertThat(row.integrityClean()).isTrue();
        });
        assertThat(result.replay().exact()).isTrue();
        assertThat(result.holdoutMatchCount()).isZero();
        assertThat(result.calibrationMatchCount()).isZero();
        assertThat(result.artifacts().status()).isEqualTo("SYNTHETIC_VALIDATION_ONLY");
        assertThat(result.artifacts().officialHoldoutEvidence()).isFalse();

        var store = new Phase13GB3CheckpointStore(mapper);
        var row = result.rows().getFirst();
        var evidence = store.rowEvidence(row);

        ObjectNode outcome = mapper.valueToTree(row);
        outcome.put("blueGold", row.blueGold() + 1);
        var changedOutcome = mapper.treeToValue(
                outcome, Phase13GB3HoldoutModel.MatchRow.class);
        assertThatThrownBy(() -> store.validateRowEvidence(changedOutcome, evidence))
                .isInstanceOf(IllegalStateException.class);

        ObjectNode diagnostics = mapper.valueToTree(row);
        diagnostics.put("structuredDiagnosticsHash", "a".repeat(64));
        var changedDiagnostics = mapper.treeToValue(
                diagnostics, Phase13GB3HoldoutModel.MatchRow.class);
        assertThatThrownBy(() -> store.validateRowEvidence(changedDiagnostics, evidence))
                .isInstanceOf(IllegalStateException.class);

        ObjectNode observation = mapper.valueToTree(row);
        ((ObjectNode) observation.withArray("jungleObservations").get(0))
                .put("gold", row.jungleObservations().getFirst().gold() + 1);
        var changedObservation = mapper.treeToValue(
                observation, Phase13GB3HoldoutModel.MatchRow.class);
        assertThatThrownBy(() -> store.validateRowEvidence(changedObservation, evidence))
                .isInstanceOf(IllegalStateException.class);

        ObjectNode player = mapper.valueToTree(row);
        ((ObjectNode) player.withArray("finalPlayerStates").get(0))
                .put("cs", row.finalPlayerStates().getFirst().cs() + 1);
        var changedPlayer = mapper.treeToValue(
                player, Phase13GB3HoldoutModel.MatchRow.class);
        assertThatThrownBy(() -> store.validateRowEvidence(changedPlayer, evidence))
                .isInstanceOf(IllegalStateException.class);

        byte[] payload = mapper.writeValueAsBytes(result.rows());
        var receipt = new Phase13GB3HoldoutModel.CheckpointPayloadReceipt(
                0, result.fixtureId(), "smoke.json", 40,
                Phase13GB3CheckpointStore.sha256(payload));
        store.validatePayloadReceipt(payload, receipt);
        payload[payload.length - 1] ^= 1;
        assertThatThrownBy(() -> store.validatePayloadReceipt(payload, receipt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("raw-byte");
    }

    @Test
    @Tag("phase13g-b3-freeze")
    void freezesCandidateAndAcceptanceBeforeHoldout() throws Exception {
        var result = new Phase13GB3ContractFreezer(mapper).freeze(
                Path.of("."),
                Path.of("build", "reports", "phase13g-b1"),
                Path.of("build", "reports", "phase13g-b2"),
                OUTPUT);
        assertThat(result.frozenContractHash()).matches("[0-9a-f]{64}");
        assertThat(result.candidateFreezeIdentityHash()).matches("[0-9a-f]{64}");
        assertThat(result.acceptanceGateIdentityHash()).matches("[0-9a-f]{64}");
        assertThat(result.numericGateCount()).isGreaterThan(60);
        assertThat(result.exactBehaviorGateCount()).isEqualTo(7);
        assertThat(result.holdoutExecutionCountAtFreeze()).isZero();
        var contract = new Phase13GB3CheckpointStore(mapper).readFrozenContract(OUTPUT);
        assertThat(contract.b2Evidence().shaManifestExact()).isTrue();
        assertThat(contract.productionDecision()).isEqualTo("NOT_EVALUATED");
    }

    @Test
    @Tag("phase13g-b3-finalizer")
    void finalizesOnlyReceiptBoundOfficialEvidence() throws Exception {
        var result = runner().finalizeOfficial(Path.of("."), OUTPUT);
        assertThat(result.completedFixtureCount()).isEqualTo(100);
        assertThat(result.holdoutMatchCount()).isEqualTo(4_000);
        assertThat(result.calibrationMatchCount()).isZero();
        assertThat(result.artifacts().evidenceStatus())
                .isEqualTo("HOLDOUT_EVIDENCE_READY_FOR_FINAL_REVIEW");
        assertThat(result.artifacts().reviewSha256()).matches("[0-9a-f]{64}");
        assertThat(result.artifacts().shaManifestSha256()).matches("[0-9a-f]{64}");
    }

    private Phase13GB3FrozenHoldoutRunner runner() {
        return new Phase13GB3FrozenHoldoutRunner(
                orchestrator, simulators, mapper, champions, identities, ratings,
                proficiencies);
    }
}

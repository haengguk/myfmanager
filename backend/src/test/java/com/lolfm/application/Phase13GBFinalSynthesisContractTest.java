package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.application.Phase13GBFinalSynthesis.Dimension;
import com.lolfm.application.Phase13GBFinalSynthesis.PairedEvidence;
import com.lolfm.application.Phase13GBFinalSynthesis.Participant;
import com.lolfm.application.Phase13GBFinalSynthesis.SourcePopulation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Phase13GBFinalSynthesisContractTest {
    @Test
    void segmentedEvidenceAttributesPlayersChampionsAndDirections() {
        List<PairedEvidence> evidence = List.of(
                row(SourcePopulation.CALIBRATION_B2, true, "BLUE", "RED"),
                row(SourcePopulation.CALIBRATION_B2, false, "RED", "RED"),
                row(SourcePopulation.HOLDOUT_B3, true, "RED", "BLUE"));

        var segments = Phase13GBFinalSynthesis.segment(evidence);
        var combinedChampion = segments.stream()
                .filter(row -> row.population() == SourcePopulation.COMBINED)
                .filter(row -> row.dimension() == Dimension.CHAMPION)
                .filter(row -> row.key().equals("lee-sin"))
                .findFirst().orElseThrow();
        var combinedAll = segments.stream()
                .filter(row -> row.population() == SourcePopulation.COMBINED)
                .filter(row -> row.dimension() == Dimension.ALL)
                .findFirst().orElseThrow();

        assertThat(combinedChampion.exposureCount()).isEqualTo(3);
        assertThat(combinedChampion.winnerFlipCount()).isEqualTo(2);
        assertThat(combinedAll.blueToRedFlipCount()).isOne();
        assertThat(combinedAll.redToBlueFlipCount()).isOne();
    }

    @Test
    void correlationUsesOnlyCommonStructuredKeys() {
        double correlation = Phase13GBFinalSynthesis.correlation(
                Map.of("a", 0.1, "b", 0.2, "c", 0.3, "left-only", 1.0),
                Map.of("a", 0.2, "b", 0.4, "c", 0.6, "right-only", 0.0));

        assertThat(correlation).isCloseTo(1.0, within(1.0e-12));
    }

    @Test
    void frozenFailureAndReviewProduceConservativeProductionDecision() {
        var decision = Phase13GBFinalSynthesis.decisionPolicy(
                "FAIL", "REVIEW_REQUIRED", true, 0.013888888888888888,
                0.013794550001);

        assertThat(decision.productionDecision()).isEqualTo("KEEP_CURRENT_RUNTIME_DEFAULT");
        assertThat(decision.economyDisposition()).contains("NOT_APPROVED");
        assertThat(decision.tempoDisposition()).contains("DEFER_TO_V2");
        assertThat(decision.freezeReadiness()).contains("WITHOUT_JUNGLE_ECONOMY_OR_TEMPO");
    }

    @Test
    void invalidEvidenceCannotCreateProductionDecision() {
        var decision = Phase13GBFinalSynthesis.decisionPolicy(
                "FAIL", "REVIEW_REQUIRED", false, 0.02, 0.01);

        assertThat(decision.productionDecision()).isEqualTo("FINAL_EVIDENCE_INVALID");
        assertThat(decision.freezeReadiness()).isEqualTo("BLOCK_MATCH_ENGINE_V1_FREEZE");
    }

    @Test
    void shaManifestRejectsChangedRawBytes(@TempDir Path output) throws Exception {
        Path evidence = output.resolve("evidence.txt");
        Files.writeString(evidence, "original\n", StandardCharsets.UTF_8);
        Files.writeString(output.resolve(Phase13GBFinalSynthesis.SHA_FILE),
                Phase13GBFinalSynthesis.sha256(evidence) + "  evidence.txt\n",
                StandardCharsets.UTF_8);

        assertThat(Phase13GBFinalSynthesis.verifyManifest(output, 1).exact()).isTrue();
        Files.writeString(evidence, "changed\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Phase13GBFinalSynthesis.verifyManifest(output, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA mismatch");
    }

    @Test
    void csvAndCanonicalJsonAreStable() {
        assertThat(Phase13GBFinalSynthesis.parseCsvLine("a,\"b,c\",\"d\"\"e\""))
                .containsExactly("a", "b,c", "d\"e");
        LinkedHashMap<String, Object> first = new LinkedHashMap<>();
        first.put("z", 1);
        first.put("a", List.of("x", true));
        LinkedHashMap<String, Object> second = new LinkedHashMap<>();
        second.put("a", List.of("x", true));
        second.put("z", 1);

        assertThat(Phase13GBFinalSynthesis.canonicalJson(first))
                .isEqualTo(Phase13GBFinalSynthesis.canonicalJson(second))
                .isEqualTo("{\"a\":[\"x\",true],\"z\":1}");
    }

    private static PairedEvidence row(
            SourcePopulation population,
            boolean flipped,
            String fromWinner,
            String toWinner
    ) {
        return new PairedEvidence(
                population,
                "ECONOMY_MINUS_FULL",
                "G1_BLUE__RED",
                "PRIMARY_LEAGUE_G1",
                "PAIR",
                "BLUE_TEAM",
                "RED_TEAM",
                0,
                1L,
                "FULL_SYSTEM_CANDIDATE_V1",
                "FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1",
                fromWinner,
                toWinner,
                flipped,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                new Participant("BLUE", "blue-player", "lee-sin"),
                new Participant("RED", "red-player", "xin-zhao"));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}

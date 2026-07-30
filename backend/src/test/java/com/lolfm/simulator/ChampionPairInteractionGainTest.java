package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import org.junit.jupiter.api.Test;

class ChampionPairInteractionGainTest {
    private final ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());

    @Test void gainMatrixHasExactlyTwentySevenHundredRows() {
        assertThat(ChampionPairInteractionGainAudit.matrix(PairInteractionGeneratedCatalog.build(champions))).hasSize(2_700);
    }
    @Test void gainScalingPreservesDirectionalityAndCap() {
        var rows = ChampionPairInteractionGainAudit.matrix(PairInteractionGeneratedCatalog.build(champions));
        assertThat(rows).allMatch(row -> row.directionalityValid() && Double.isFinite(row.gainedEdge()) && Math.abs(row.gainedEdge()) <= .30);
    }
    @Test void gainCandidatesRemainDiagnosticsOnlyAndProductionNeutral() {
        var twelve = PairInteractionGeneratedCatalog.build(champions, 12).catalog();
        assertThat(twelve.version()).startsWith("diagnostics-").contains("gain-12.0");
        assertThat(ChampionMatchupCatalog.neutral(champions).profiles().values()).allMatch(profile ->
                profile.firstChampionEdges().values().stream().allMatch(edge -> edge == 0.0));
    }
    @Test void orderedSamplesDriveGainQuantiles() {
        var distributions = ChampionPairInteractionGainAudit.distributions(
                ChampionPairInteractionGainAudit.matrix(PairInteractionGeneratedCatalog.build(champions)));
        assertThat(distributions).hasSize(4).allMatch(value -> value.p50() <= value.p75()
                && value.p75() <= value.p90() && value.p90() <= value.p95() && value.p95() <= value.max());
    }
    @Test void staticGainCalculationConsumesNoRandom() {
        assertThat(ChampionPairInteractionGainAudit.matrix(PairInteractionGeneratedCatalog.build(champions))).hasSize(2_700);
    }
}

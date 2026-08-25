package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.FinalState;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.Health;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.LaneState;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.Severity;
import com.lolfm.application.MatchupV9StructureAttributionClassifier.TeamState;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchupV9StructureAttributionContractTest {
    @TempDir Path temporary;

    @Test void freezesBoundedDiagnosticScheduleWithNoConsumedSeedOverlap() {
        var schedule = MatchupV9StructureAttributionContract.requireFrozen(
                MatchupV9StructureAttributionContract.schedule());
        var overlap = MatchupV9StructureAttributionContract.requireNoSeedOverlap(schedule);

        assertThat(schedule.fixtures()).hasSize(100);
        assertThat(schedule.fixtures()).allSatisfy(value -> assertThat(value.seeds()).hasSize(4));
        assertThat(schedule.fixtures().stream().flatMap(value -> value.seeds().stream()).distinct())
                .hasSize(400);
        assertThat(MatchupV9StructureAttributionContract.PROFILES).containsExactly(
                com.lolfm.simulator.SimulationRuntimeProfileId.BASELINE_V1,
                com.lolfm.simulator.SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1);
        assertThat(overlap.clean()).isTrue();
        assertThat(overlap.consumptionStatus()).isEqualTo(
                "CONSUMED_AS_DIAGNOSTIC_NOT_HOLDOUT");
    }

    @Test void classifiesExactAndHpOnlyWithoutInventingProgression() {
        FinalState exact = state(100, true, true, true, true, 2, true);
        FinalState hp = state(90, true, true, true, true, 2, true);

        var equal = MatchupV9StructureAttributionClassifier.compare(exact, exact);
        var hpOnly = MatchupV9StructureAttributionClassifier.compare(exact, hp);

        assertThat(equal.primarySeverity()).isEqualTo(Severity.EXACT);
        assertThat(equal.severityLabels()).containsExactly(Severity.EXACT);
        assertThat(hpOnly.primarySeverity()).isEqualTo(Severity.HP_ONLY);
        assertThat(hpOnly.hpOnlyDifference()).isTrue();
        assertThat(hpOnly.severityLabels()).containsExactly(Severity.HP_ONLY);
    }

    @Test void classifiesLaneAndInhibitorProgressionComponents() {
        FinalState base = state(100, true, true, true, true, 2, true);
        FinalState lane = state(0, false, true, true, true, 2, true);
        FinalState inhibitor = state(0, false, false, false, false, 2, true);

        var laneResult = MatchupV9StructureAttributionClassifier.compare(base, lane);
        var inhibitorResult = MatchupV9StructureAttributionClassifier.compare(base, inhibitor);

        assertThat(laneResult.primarySeverity()).isEqualTo(Severity.LANE_TOWER_PROGRESSION);
        assertThat(laneResult.laneOuterTowerAliveDifference()).isTrue();
        assertThat(inhibitorResult.primarySeverity()).isEqualTo(Severity.INHIBITOR_PROGRESSION);
        assertThat(inhibitorResult.severityLabels()).contains(
                Severity.LANE_TOWER_PROGRESSION, Severity.INHIBITOR_PROGRESSION);
    }

    @Test void nexusAndEndingAlwaysWinHighestSeverityWithoutDuplicatePrimaryCount() {
        FinalState base = state(100, true, true, true, true, 2, true);
        FinalState turret = state(0, false, false, false, false, 1, true);
        FinalState ending = state(0, false, false, false, false, 0, false);

        var turretResult = MatchupV9StructureAttributionClassifier.compare(base, turret);
        var endingResult = MatchupV9StructureAttributionClassifier.compare(base, ending);

        assertThat(turretResult.primarySeverity()).isEqualTo(Severity.NEXUS_TURRET_PROGRESSION);
        assertThat(endingResult.primarySeverity()).isEqualTo(Severity.NEXUS_OR_ENDING);
        assertThat(endingResult.severityLabels()).doesNotHaveDuplicates();
        assertThat(endingResult.severityLabels()).contains(
                Severity.LANE_TOWER_PROGRESSION, Severity.INHIBITOR_PROGRESSION,
                Severity.NEXUS_TURRET_PROGRESSION, Severity.NEXUS_OR_ENDING);
    }

    @Tag("diagnostic")
    @Test void predecessorManifestSourceRawRowsAndCheckpointsRemainBoundReadOnly() throws Exception {
        var audit = MatchupV9StructureAttributionEvidence.verify(Path.of("."), new ObjectMapper());
        assertThat(audit.clean()).isTrue();
        assertThat(audit.checkpointAudit().checkpointPayloadCount()).isEqualTo(200);
        assertThat(audit.checkpointAudit().rawMatchRowCount()).isEqualTo(3_600);
        assertThat(audit.checkpointAudit().rawRowsByteExactWithCheckpointProjection()).isTrue();
        assertThat(audit.preservedRecommendation()).isEqualTo("RECOMMEND_BASELINE_V1");
    }

    @Test void canonicalManifestDetectsExactBytes() throws Exception {
        Files.writeString(temporary.resolve("one.json"), "{}\n", StandardCharsets.UTF_8);
        String hash = MatchupV9StructureAttributionContract.sha256(
                Files.readAllBytes(temporary.resolve("one.json")));
        Files.writeString(temporary.resolve("SHA256SUMS.txt"),
                hash + "  one.json\n", StandardCharsets.UTF_8);

        MatchupV9StructureAttributionArtifactWriter.verifyManifest(temporary);
        assertThat(MatchupV9StructureAttributionContract.sha256("stable"))
                .isEqualTo(MatchupV9StructureAttributionContract.sha256("stable"));
    }

    private static FinalState state(
            double outerHealth,
            boolean outerAlive,
            boolean innerAlive,
            boolean inhibitorTowerAlive,
            boolean inhibitorAlive,
            int nexusTurrets,
            boolean nexusAlive
    ) {
        EnumMap<TeamSide, TeamState> teams = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) {
            EnumMap<Lane, LaneState> lanes = new EnumMap<>(Lane.class);
            for (Lane lane : Lane.values()) {
                lanes.put(lane, new LaneState(
                        new Health(outerAlive ? outerHealth : 0, 100, outerAlive),
                        new Health(innerAlive ? 100 : 0, 100, innerAlive),
                        new Health(inhibitorTowerAlive ? 100 : 0, 100, inhibitorTowerAlive),
                        new Health(inhibitorAlive ? 100 : 0, 100, inhibitorAlive)));
            }
            teams.put(side, new TeamState(Map.copyOf(lanes),
                    nexusTurrets == 2 ? List.of(100.0, 100.0)
                            : nexusTurrets == 1 ? List.of(0.0, 100.0) : List.of(0.0, 0.0),
                    100, nexusAlive ? 100 : 0, 100, nexusTurrets, nexusAlive));
        }
        return new FinalState(Map.copyOf(teams));
    }
}

package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerMatchPerformanceSnapshotTest {

    @Test
    void snapshotRequiresExactRoleCoverageAndBoundedFiniteRatings() {
        EnumMap<PlayerSkill, Double> valid = ratings(Position.TOP);
        PlayerMatchPerformanceSnapshot snapshot = new PlayerMatchPerformanceSnapshot(
                new PlayerKey(TeamSide.BLUE, Position.TOP), valid, 14);
        assertThat(snapshot.realizedRatings()).hasSize(12);
        assertThatThrownBy(() -> snapshot.realizedRatings().put(PlayerSkill.MECHANICS, 1.0))
                .isInstanceOf(UnsupportedOperationException.class);

        EnumMap<PlayerSkill, Double> outOfRange = ratings(Position.TOP);
        outOfRange.put(PlayerSkill.MECHANICS, PlayerRatings.MAX + 0.01);
        assertThatThrownBy(() -> new PlayerMatchPerformanceSnapshot(
                new PlayerKey(TeamSide.BLUE, Position.TOP), outOfRange, 14))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredOutcomeRejectsDuplicatePlayerPerformanceKeys() {
        PlayerMatchPerformanceSnapshot performance = new PlayerMatchPerformanceSnapshot(
                new PlayerKey(TeamSide.BLUE, Position.TOP), ratings(Position.TOP), 14);
        MatchTimeline timeline = new MatchTimeline(0, null, List.of(), List.of());
        SimulationRandomFingerprint fingerprint = new SimulationRandomFingerprint(
                SimulationRandomFingerprint.SCHEMA, 0, "0".repeat(64),
                SimulationRandomFingerprint.TRACE_HASH_ALGORITHM);

        assertThatThrownBy(() -> new StructuredMatchSimulationOutcome(
                timeline, null, GameEndReason.SIMULATION_TIMEOUT, 0, fingerprint,
                List.of(performance, performance)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate match performance player key");
    }

    private EnumMap<PlayerSkill, Double> ratings(Position position) {
        EnumMap<PlayerSkill, Double> values = new EnumMap<>(PlayerSkill.class);
        PlayerRatings.neutral(position).asMap().forEach(
                (skill, value) -> values.put(skill, value.doubleValue()));
        return values;
    }
}

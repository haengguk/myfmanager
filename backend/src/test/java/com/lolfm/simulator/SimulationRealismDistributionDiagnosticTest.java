package com.lolfm.simulator;

import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("diagnostic")
@Tag("simulation-distribution")
class SimulationRealismDistributionDiagnosticTest {

    @Test
    void reportBoundedProfessionalMatchPlausibilitySample() {
        int sample = 240;
        long totalKills = 0, totalAbsKillGap = 0, totalAssists = 0;
        long totalSupportKills = 0, totalDuration = 0, totalTowers = 0;
        int gapAtLeastTen = 0, gapAtLeastTwenty = 0, maxGap = 0;
        int timeReversals = 0, postEndEvents = 0;
        TreeMap<String, Long> killsBySource = new TreeMap<>();
        EnumMap<Position, Long> csGap = new EnumMap<>(Position.class);
        for (Position position : Position.values()) csGap.put(position, 0L);

        MatchSimulator simulator = simulator();
        DummyDataFactory data = new DummyDataFactory();
        for (long seed = 1; seed <= sample; seed++) {
            MatchTimeline timeline = simulator.simulate(
                    data.createBlueTeam(), data.createRedTeam(), seed);
            var finalSnapshot = timeline.getSnapshots().getLast();
            int kills = finalSnapshot.getBlueKills() + finalSnapshot.getRedKills();
            int gap = Math.abs(finalSnapshot.getBlueKills() - finalSnapshot.getRedKills());
            totalKills += kills;
            totalAbsKillGap += gap;
            totalDuration += timeline.getDurationSeconds();
            totalTowers += finalSnapshot.getBlueTowersDestroyed()
                    + finalSnapshot.getRedTowersDestroyed();
            maxGap = Math.max(maxGap, gap);
            if (gap >= 10) gapAtLeastTen++;
            if (gap >= 20) gapAtLeastTwenty++;
            for (PlayerSnapshot player : finalSnapshot.getPlayerSnapshots()) {
                totalAssists += player.getAssists();
                if (player.getPosition() == Position.SUPPORT) {
                    totalSupportKills += player.getKills();
                }
            }
            for (Position position : Position.values()) {
                List<PlayerSnapshot> role = finalSnapshot.getPlayerSnapshots().stream()
                        .filter(player -> player.getPosition() == position)
                        .sorted(Comparator.comparing(PlayerSnapshot::getTeamSide)).toList();
                csGap.merge(position, (long) Math.abs(role.get(0).getCs() - role.get(1).getCs()), Long::sum);
            }
            int previous = -1;
            for (var event : timeline.getEvents()) {
                if (event.getType() == MatchEventType.KILL) {
                    killsBySource.merge(String.valueOf(event.getCombatSource()), 1L, Long::sum);
                }
                if (event.getTimeSeconds() < previous) timeReversals++;
                if (event.getTimeSeconds() > timeline.getDurationSeconds()) postEndEvents++;
                previous = event.getTimeSeconds();
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "REALISM_AUDIT sample=%d meanKills=%.2f meanAbsKillGap=%.2f gap10=%.3f gap20=%.3f maxGap=%d supportKillShare=%.3f assistsPerKill=%.2f meanDurationSeconds=%.1f meanTowers=%.2f meanCsGap=%s timeReversals=%d postEndEvents=%d%n",
                sample, totalKills / (double) sample, totalAbsKillGap / (double) sample,
                gapAtLeastTen / (double) sample, gapAtLeastTwenty / (double) sample,
                maxGap, totalSupportKills / (double) Math.max(1, totalKills),
                totalAssists / (double) Math.max(1, totalKills),
                totalDuration / (double) sample, totalTowers / (double) sample,
                meanCsGaps(csGap, sample) + ",sources=" + killsBySource,
                timeReversals, postEndEvents);
    }

    private String meanCsGaps(EnumMap<Position, Long> totals, int sample) {
        List<String> values = new ArrayList<>();
        for (Position position : Position.values()) {
            values.add(position + "=" + String.format(java.util.Locale.ROOT, "%.1f",
                    totals.get(position) / (double) sample));
        }
        return String.join(",", values);
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(), new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver());
    }
}

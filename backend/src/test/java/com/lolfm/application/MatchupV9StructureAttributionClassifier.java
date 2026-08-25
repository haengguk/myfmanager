package com.lolfm.application;

import com.lolfm.domain.StructureStateSnapshot;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure final-state projector and mutually-exclusive severity classifier. */
public final class MatchupV9StructureAttributionClassifier {
    private MatchupV9StructureAttributionClassifier() {
    }

    public static FinalState project(StructureStateSnapshot state) {
        EnumMap<TeamSide, TeamState> teams = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) {
            StructureStateSnapshot.TeamStructures source = state.teams().get(side);
            if (source == null) throw new IllegalArgumentException("Missing structure side: " + side);
            EnumMap<Lane, LaneState> lanes = new EnumMap<>(Lane.class);
            for (Lane lane : Lane.values()) {
                StructureStateSnapshot.LaneStructures value = source.lanes().get(lane);
                if (value == null) throw new IllegalArgumentException("Missing structure lane: " + side + ":" + lane);
                lanes.put(lane, new LaneState(
                        health(value.outerTower()), health(value.innerTower()),
                        health(value.inhibitorTower()), health(value.inhibitor())));
            }
            teams.put(side, new TeamState(
                    Collections.unmodifiableMap(lanes), List.copyOf(source.nexusTurretCurrentHealth()),
                    source.nexusTurretMaxHealth(), source.nexusCurrentHealth(),
                    source.nexusMaxHealth(), source.nexusTurretsRemaining(), source.nexusAlive()));
        }
        return new FinalState(Collections.unmodifiableMap(teams));
    }

    private static Health health(StructureStateSnapshot.Health value) {
        return new Health(value.current(), value.maximum(), value.alive());
    }

    public static Comparison compare(FinalState before, FinalState after) {
        boolean exact = before.equals(after);
        boolean outerAlive = false;
        boolean innerAlive = false;
        boolean inhibitorTowerAlive = false;
        boolean inhibitorAlive = false;
        boolean nexusTurretAlive = false;
        boolean nexusTurretHp = false;
        boolean nexusAlive = false;
        boolean hp = false;
        boolean maxHp = false;
        int towerCountBefore = 0;
        int towerCountAfter = 0;
        int inhibitorsBefore = 0;
        int inhibitorsAfter = 0;
        int nexusTurretsBefore = 0;
        int nexusTurretsAfter = 0;

        for (TeamSide side : TeamSide.values()) {
            TeamState a = before.teams().get(side);
            TeamState b = after.teams().get(side);
            nexusAlive |= a.nexusAlive() != b.nexusAlive();
            hp |= different(a.nexusCurrentHealth(), b.nexusCurrentHealth());
            maxHp |= different(a.nexusMaxHealth(), b.nexusMaxHealth());
            nexusTurretsBefore += a.nexusTurretsRemaining();
            nexusTurretsAfter += b.nexusTurretsRemaining();
            int count = Math.max(a.nexusTurretCurrentHealth().size(), b.nexusTurretCurrentHealth().size());
            for (int index = 0; index < count; index++) {
                double ah = index < a.nexusTurretCurrentHealth().size()
                        ? a.nexusTurretCurrentHealth().get(index) : Double.NaN;
                double bh = index < b.nexusTurretCurrentHealth().size()
                        ? b.nexusTurretCurrentHealth().get(index) : Double.NaN;
                nexusTurretAlive |= alive(ah) != alive(bh);
                nexusTurretHp |= different(ah, bh);
                hp |= different(ah, bh);
            }
            maxHp |= different(a.nexusTurretMaxHealth(), b.nexusTurretMaxHealth());
            for (Lane lane : Lane.values()) {
                LaneState al = a.lanes().get(lane);
                LaneState bl = b.lanes().get(lane);
                outerAlive |= al.outerTower().alive() != bl.outerTower().alive();
                innerAlive |= al.innerTower().alive() != bl.innerTower().alive();
                inhibitorTowerAlive |= al.inhibitorTower().alive() != bl.inhibitorTower().alive();
                inhibitorAlive |= al.inhibitor().alive() != bl.inhibitor().alive();
                towerCountBefore += aliveCount(al);
                towerCountAfter += aliveCount(bl);
                inhibitorsBefore += al.inhibitor().alive() ? 1 : 0;
                inhibitorsAfter += bl.inhibitor().alive() ? 1 : 0;
                for (HealthPair pair : List.of(
                        new HealthPair(al.outerTower(), bl.outerTower()),
                        new HealthPair(al.innerTower(), bl.innerTower()),
                        new HealthPair(al.inhibitorTower(), bl.inhibitorTower()),
                        new HealthPair(al.inhibitor(), bl.inhibitor()))) {
                    hp |= different(pair.before().current(), pair.after().current());
                    maxHp |= different(pair.before().maximum(), pair.after().maximum());
                }
            }
        }
        boolean laneTower = outerAlive || innerAlive;
        boolean inhibitor = inhibitorTowerAlive || inhibitorAlive;
        boolean nexusTurret = nexusTurretAlive
                || nexusTurretsBefore != nexusTurretsAfter;
        boolean progression = laneTower || inhibitor || nexusTurret || nexusAlive;
        boolean hpOnly = !exact && !progression;
        Severity primary = exact ? Severity.EXACT
                : nexusAlive ? Severity.NEXUS_OR_ENDING
                : nexusTurret ? Severity.NEXUS_TURRET_PROGRESSION
                : inhibitor ? Severity.INHIBITOR_PROGRESSION
                : laneTower ? Severity.LANE_TOWER_PROGRESSION
                : Severity.HP_ONLY;
        ArrayList<Severity> labels = new ArrayList<>();
        if (exact) labels.add(Severity.EXACT);
        if (hpOnly) labels.add(Severity.HP_ONLY);
        if (laneTower) labels.add(Severity.LANE_TOWER_PROGRESSION);
        if (inhibitor) labels.add(Severity.INHIBITOR_PROGRESSION);
        if (nexusTurret) labels.add(Severity.NEXUS_TURRET_PROGRESSION);
        if (nexusAlive) labels.add(Severity.NEXUS_OR_ENDING);
        return new Comparison(
                exact, hpOnly, outerAlive, innerAlive, inhibitorTowerAlive,
                inhibitorAlive, nexusTurretAlive, nexusTurretHp, nexusAlive,
                towerCountBefore != towerCountAfter,
                inhibitorsBefore != inhibitorsAfter,
                nexusTurretsBefore != nexusTurretsAfter,
                hp, maxHp, primary, List.copyOf(labels));
    }

    private static int aliveCount(LaneState value) {
        int result = 0;
        if (value.outerTower().alive()) result++;
        if (value.innerTower().alive()) result++;
        if (value.inhibitorTower().alive()) result++;
        return result;
    }

    private static boolean alive(double health) {
        return Double.isFinite(health) && health > 0.0;
    }

    private static boolean different(double first, double second) {
        return Double.doubleToLongBits(first) != Double.doubleToLongBits(second);
    }

    public enum Severity {
        EXACT,
        HP_ONLY,
        LANE_TOWER_PROGRESSION,
        INHIBITOR_PROGRESSION,
        NEXUS_TURRET_PROGRESSION,
        NEXUS_OR_ENDING
    }

    public record FinalState(Map<TeamSide, TeamState> teams) {
        public FinalState {
            EnumMap<TeamSide, TeamState> copy = new EnumMap<>(TeamSide.class);
            copy.putAll(teams);
            teams = Collections.unmodifiableMap(copy);
        }
    }

    public record TeamState(
            Map<Lane, LaneState> lanes,
            List<Double> nexusTurretCurrentHealth,
            double nexusTurretMaxHealth,
            double nexusCurrentHealth,
            double nexusMaxHealth,
            int nexusTurretsRemaining,
            boolean nexusAlive
    ) {
        public TeamState {
            EnumMap<Lane, LaneState> copy = new EnumMap<>(Lane.class);
            copy.putAll(lanes);
            lanes = Collections.unmodifiableMap(copy);
            nexusTurretCurrentHealth = List.copyOf(nexusTurretCurrentHealth);
        }
    }

    public record LaneState(
            Health outerTower,
            Health innerTower,
            Health inhibitorTower,
            Health inhibitor
    ) { }

    public record Health(double current, double maximum, boolean alive) { }

    private record HealthPair(Health before, Health after) { }

    public record Comparison(
            boolean finalStructureStateExactEquality,
            boolean hpOnlyDifference,
            boolean laneOuterTowerAliveDifference,
            boolean laneInnerTowerAliveDifference,
            boolean inhibitorTurretAliveDifference,
            boolean inhibitorAliveDifference,
            boolean individualNexusTurretAliveDifference,
            boolean individualNexusTurretHpDifference,
            boolean nexusAliveDifference,
            boolean towersDestroyedCountDifference,
            boolean inhibitorsRemainingDifference,
            boolean nexusTurretsRemainingDifference,
            boolean anyHealthDifference,
            boolean anyMaximumHealthDifference,
            Severity primarySeverity,
            List<Severity> severityLabels
    ) {
        public Comparison {
            severityLabels = List.copyOf(severityLabels);
        }
    }
}

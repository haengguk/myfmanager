package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.SiegeStopReason;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.TeamSide;
import java.util.EnumMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable structured durability and active-siege state for playback. */
public record StructureStateSnapshot(
        Map<TeamSide, TeamStructures> teams,
        Map<TeamSide, Siege> sieges
) {
    public StructureStateSnapshot {
        teams = immutableEnumMap(TeamSide.class, teams);
        sieges = immutableEnumMap(TeamSide.class, sieges);
    }

    public static StructureStateSnapshot empty() {
        return new StructureStateSnapshot(Map.of(), Map.of());
    }

    private static <K extends Enum<K>, V> Map<K, V> immutableEnumMap(
            Class<K> keyType, Map<K, V> source) {
        EnumMap<K, V> result = new EnumMap<>(keyType);
        result.putAll(source);
        return Collections.unmodifiableMap(result);
    }

    public record TeamStructures(
            TeamSide defendingSide,
            Map<Lane, LaneStructures> lanes,
            List<Double> nexusTurretCurrentHealth,
            double nexusTurretMaxHealth,
            double nexusCurrentHealth,
            double nexusMaxHealth,
            int nexusTurretsRemaining,
            boolean nexusAlive
    ) {
        public TeamStructures {
            lanes = immutableEnumMap(Lane.class, lanes);
            nexusTurretCurrentHealth = List.copyOf(nexusTurretCurrentHealth);
        }
    }

    public record LaneStructures(
            Lane lane,
            Health outerTower,
            Health innerTower,
            Health inhibitorTower,
            Health inhibitor
    ) { }

    public record Health(double current, double maximum, boolean alive) { }

    public record Siege(
            TeamSide attackingSide,
            boolean active,
            String actionId,
            Lane routeLane,
            String targetId,
            StructureActionSource source,
            Set<Position> participants,
            int nextAttackAtSeconds,
            int expiresAtSeconds,
            SiegeStopReason stopReason
    ) {
        public Siege {
            participants = DeterministicEnumSet.copyOfNullable(Position.class, participants);
        }
    }
}

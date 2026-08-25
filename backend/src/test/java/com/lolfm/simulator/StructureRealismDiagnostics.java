package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.StructureActionData;
import com.lolfm.domain.StructureActionPhase;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only, structured-event structure realism audit; excluded from normal tests. */
public final class StructureRealismDiagnostics {
    private StructureRealismDiagnostics() { }

    public static void main(String[] args) {
        int sample = args.length == 0 ? 200 : Integer.parseInt(args[0]);
        Aggregate aggregate = new Aggregate(sample);
        MatchSimulator simulator = simulator();
        DummyDataFactory data = new DummyDataFactory();
        for (long seed = 1; seed <= sample; seed++) {
            aggregate.add(seed, simulator.simulate(
                    data.createBlueTeam(), data.createRedTeam(), seed));
        }
        aggregate.print();
    }

    private static MatchSimulator simulator() {
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults());
    }

    private static final class Aggregate {
        private final int sample;
        private final List<Integer> durations = new ArrayList<>();
        private final List<Integer> firstDamageTimes = new ArrayList<>();
        private final List<Integer> firstTowerTimes = new ArrayList<>();
        private final List<Integer> baseOpenTimes = new ArrayList<>();
        private final List<Integer> clearToNexusAttackSeconds = new ArrayList<>();
        private long partialHits;
        private long towerDestructions;
        private long inhibitorDestructions;
        private long nexusTurretDestructions;
        private long nexusDestructions;
        private long backdoorHits;
        private long repelled;
        private long aborted;
        private long nexusTurretRespawns;
        private long baseClears;
        private long baseClearsFollowedByNexusAttack;
        private long noKillNineTowerClears;
        private long nineTowerRunsBetweenKills;
        private long timeouts;
        private long duplicateEventIds;
        private long malformedHealth;
        private long missingStructuredSource;
        private long destroyedNexusWithTurrets;
        private long postEndEvents;
        private int maximumDestructionsBetweenKills;
        private int maximumSameSideTowersBetweenKills;
        private long underTwentyThreeMinutes;
        private long underTwentyFiveMinutes;
        private long overFortyMinutes;
        private final List<String> earlyFinishDetails = new ArrayList<>();
        private final EnumMap<StructureActionSource, Long> hitsBySource = sourceCounts();
        private final EnumMap<StructureActionSource, Long> destructionsBySource = sourceCounts();
        private final EnumMap<StructureActionSource, Long> firstTowersBySource = sourceCounts();
        private final EnumMap<StructureActionSource, EnumMap<StructureKind, Long>>
                destructionKindsBySource = sourceKindCounts();

        private Aggregate(int sample) { this.sample = sample; }

        private void add(long seed, MatchTimeline timeline) {
            durations.add(timeline.getDurationSeconds());
            if (timeline.getDurationSeconds() < 1_380) underTwentyThreeMinutes++;
            if (timeline.getDurationSeconds() < 1_500) underTwentyFiveMinutes++;
            if (timeline.getDurationSeconds() >= 2_400) overFortyMinutes++;
            if (timeline.getWinner() == null) timeouts++;
            Set<String> eventIds = new HashSet<>();
            EnumMap<TeamSide, Integer> destroyedTowers = counts();
            EnumMap<TeamSide, Integer> downNexusTurrets = counts();
            EnumMap<TeamSide, Integer> pendingClearAt = times();
            EnumMap<TeamSide, Integer> towersSinceKill = counts();
            int killsSeen = 0;
            int destructionsSinceKill = 0;
            int firstDamage = -1;
            int firstTower = -1;
            int baseOpen = -1;
            StructureActionSource nexusSource = null;

            for (MatchEvent event : timeline.getEvents()) {
                if (event.getTimeSeconds() > timeline.getDurationSeconds()) postEndEvents++;
                if (event.getType() == MatchEventType.KILL) {
                    killsSeen++;
                    destructionsSinceKill = 0;
                    for (TeamSide side : TeamSide.values()) towersSinceKill.put(side, 0);
                }
                StructureActionData action = event.getStructureAction();
                if (action == null) continue;
                if (event.getActionId() != null && !eventIds.add(event.getActionId())) {
                    duplicateEventIds++;
                }
                if (action.maxHealth() <= 0 || action.healthBefore() < -0.000_001
                        || action.healthAfter() < -0.000_001
                        || action.healthAfter() > action.maxHealth() + 0.000_001
                        || action.damage() < -0.000_001) {
                    malformedHealth++;
                }
                if (action.source() == null && action.phase() != StructureActionPhase.RESPAWNED) {
                    missingStructuredSource++;
                }
                if (action.damage() > 0 && firstDamage < 0) firstDamage = event.getTimeSeconds();
                if (action.damage() > 0 && action.source() != null) {
                    hitsBySource.merge(action.source(), 1L, Long::sum);
                }
                if (action.phase() == StructureActionPhase.STARTED
                        || action.phase() == StructureActionPhase.DAMAGE) partialHits++;
                if (action.backdoorProtected()) backdoorHits++;
                if (action.phase() == StructureActionPhase.REPELLED) repelled++;
                if (action.phase() == StructureActionPhase.ABORTED) aborted++;

                if (action.phase() == StructureActionPhase.RESPAWNED) {
                    nexusTurretRespawns++;
                    downNexusTurrets.computeIfPresent(action.defendingSide(),
                            (side, count) -> Math.max(0, count - 1));
                    pendingClearAt.put(action.defendingSide(), -1);
                    continue;
                }
                if (action.structureKind() == StructureKind.NEXUS
                        && action.attackingSide() != null
                        && action.damage() > 0) {
                    TeamSide defending = action.defendingSide();
                    int clearAt = pendingClearAt.getOrDefault(defending, -1);
                    if (clearAt >= 0) {
                        baseClearsFollowedByNexusAttack++;
                        clearToNexusAttackSeconds.add(event.getTimeSeconds() - clearAt);
                        pendingClearAt.put(defending, -1);
                    }
                }
                if (action.phase() != StructureActionPhase.DESTROYED) continue;

                destructionsSinceKill++;
                maximumDestructionsBetweenKills = Math.max(
                        maximumDestructionsBetweenKills, destructionsSinceKill);
                switch (action.structureKind()) {
                    case TOWER -> {
                        towerDestructions++;
                        if (firstTower < 0) {
                            firstTower = event.getTimeSeconds();
                            firstTowersBySource.merge(action.source(), 1L, Long::sum);
                        }
                        int count = destroyedTowers.merge(action.attackingSide(), 1, Integer::sum);
                        int quietCount = towersSinceKill.merge(
                                action.attackingSide(), 1, Integer::sum);
                        maximumSameSideTowersBetweenKills = Math.max(
                                maximumSameSideTowersBetweenKills, quietCount);
                        if (quietCount == Lane.values().length * TowerTier.values().length) {
                            nineTowerRunsBetweenKills++;
                        }
                        if (count == Lane.values().length * TowerTier.values().length
                                && killsSeen == 0) noKillNineTowerClears++;
                    }
                    case INHIBITOR -> {
                        inhibitorDestructions++;
                        if (baseOpen < 0) baseOpen = event.getTimeSeconds();
                    }
                    case NEXUS_TURRET -> {
                        nexusTurretDestructions++;
                        int count = downNexusTurrets.merge(
                                action.defendingSide(), 1, Integer::sum);
                        if (count == 2) {
                            baseClears++;
                            pendingClearAt.put(action.defendingSide(), event.getTimeSeconds());
                        }
                    }
                    case NEXUS -> {
                        nexusDestructions++;
                        nexusSource = action.source();
                    }
                }
                destructionsBySource.merge(action.source(), 1L, Long::sum);
                destructionKindsBySource.get(action.source())
                        .merge(action.structureKind(), 1L, Long::sum);
            }
            if (firstDamage >= 0) firstDamageTimes.add(firstDamage);
            if (firstTower >= 0) firstTowerTimes.add(firstTower);
            if (baseOpen >= 0) baseOpenTimes.add(baseOpen);
            if (timeline.getDurationSeconds() < 1_380) {
                earlyFinishDetails.add(seed + ":duration=" + timeline.getDurationSeconds()
                        + ",firstTower=" + firstTower + ",baseOpen=" + baseOpen
                        + ",nexusSource=" + nexusSource + ",kills=" + killsSeen);
            }

            MatchSnapshot end = timeline.getSnapshots().getLast();
            if (!end.isBlueNexusAlive() && end.getBlueNexusTurretsRemaining() > 0
                    || !end.isRedNexusAlive() && end.getRedNexusTurretsRemaining() > 0) {
                destroyedNexusWithTurrets++;
            }
        }

        private void print() {
            System.out.printf(Locale.ROOT,
                    "STRUCTURE_REALISM sample=%d duration=%s firstDamage=%s firstTower=%s baseOpen=%s "
                            + "events={partial=%d,tower=%d,inhibitor=%d,nexusTurret=%d,nexus=%d,backdoor=%d,repelled=%d,aborted=%d,respawn=%d} "
                            + "finish={timeouts=%d,under23=%d,under25=%d,over40=%d,baseClears=%d,nexusFollow=%d,followRate=%.3f,clearToNexus=%s,early=%s} "
                            + "noCombat={nineTowerClears=%d,nineTowerRuns=%d,maxSameSideTowers=%d,maxDestructionsBetweenKills=%d} "
                            + "sources={hits=%s,destructions=%s,kinds=%s,firstTower=%s} "
                            + "integrity={duplicateEventIds=%d,malformedHealth=%d,missingSource=%d,nexusWithTurrets=%d,postEnd=%d}%n",
                    sample, distribution(durations), distribution(firstDamageTimes),
                    distribution(firstTowerTimes), distribution(baseOpenTimes), partialHits,
                    towerDestructions, inhibitorDestructions, nexusTurretDestructions,
                    nexusDestructions, backdoorHits, repelled, aborted, nexusTurretRespawns,
                    timeouts, underTwentyThreeMinutes, underTwentyFiveMinutes,
                    overFortyMinutes, baseClears, baseClearsFollowedByNexusAttack,
                    baseClears == 0 ? 0 : baseClearsFollowedByNexusAttack / (double) baseClears,
                    distribution(clearToNexusAttackSeconds), earlyFinishDetails,
                    noKillNineTowerClears, nineTowerRunsBetweenKills,
                    maximumSameSideTowersBetweenKills, maximumDestructionsBetweenKills,
                    hitsBySource, destructionsBySource,
                    destructionKindsBySource, firstTowersBySource,
                    duplicateEventIds, malformedHealth,
                    missingStructuredSource, destroyedNexusWithTurrets, postEndEvents);
        }

        private EnumMap<TeamSide, Integer> counts() {
            EnumMap<TeamSide, Integer> result = new EnumMap<>(TeamSide.class);
            for (TeamSide side : TeamSide.values()) result.put(side, 0);
            return result;
        }

        private EnumMap<TeamSide, Integer> times() {
            EnumMap<TeamSide, Integer> result = new EnumMap<>(TeamSide.class);
            for (TeamSide side : TeamSide.values()) result.put(side, -1);
            return result;
        }

        private EnumMap<StructureActionSource, Long> sourceCounts() {
            EnumMap<StructureActionSource, Long> result =
                    new EnumMap<>(StructureActionSource.class);
            for (StructureActionSource source : StructureActionSource.values()) {
                result.put(source, 0L);
            }
            return result;
        }

        private EnumMap<StructureActionSource, EnumMap<StructureKind, Long>>
        sourceKindCounts() {
            EnumMap<StructureActionSource, EnumMap<StructureKind, Long>> result =
                    new EnumMap<>(StructureActionSource.class);
            for (StructureActionSource source : StructureActionSource.values()) {
                EnumMap<StructureKind, Long> kinds = new EnumMap<>(StructureKind.class);
                for (StructureKind kind : StructureKind.values()) kinds.put(kind, 0L);
                result.put(source, kinds);
            }
            return result;
        }

        private String distribution(List<Integer> values) {
            if (values.isEmpty()) return "n=0";
            List<Integer> sorted = values.stream().sorted().toList();
            double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
            return String.format(Locale.ROOT, "n=%d,mean=%.1f,p10=%d,p50=%d,p90=%d,min=%d,max=%d",
                    values.size(), mean, percentile(sorted, .10), percentile(sorted, .50),
                    percentile(sorted, .90), sorted.getFirst(), sorted.getLast());
        }

        private int percentile(List<Integer> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
        }
    }
}

package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.LaneCombatData;
import com.lolfm.domain.LaneSnapshot;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structured FARM-recovery diagnostics. Event message/description is never read. */
public final class FarmRecoveryDiagnostics {
    private static final int CHECKPOINT = 840;

    public static void main(String[] args) {
        System.out.println("FARM_RECOVERY BASE ON/OFF (A-F seeds 1..1000, checkpoint=14m)");
        for (Scenario scenario : Scenario.base()) printScenario(scenario, 1_000);
        System.out.println("FARM_RECOVERY MIRROR ON/OFF (B-F mirror seeds 1..500, checkpoint=14m)");
        for (Scenario scenario : Scenario.mirrors()) printScenario(scenario, 500);
    }

    private static void printScenario(Scenario scenario, int runs) {
        Summary on = run(scenario, runs, true, true);
        Summary off = run(scenario, runs, false, false);
        printSummary(scenario.name(), "ON", on);
        printSummary(scenario.name(), "OFF", off);
    }

    private static Summary run(Scenario scenario, int runs, boolean recovery, boolean replay) {
        Summary summary = new Summary(runs);
        for (long seed = 1; seed <= runs; seed++) {
            MatchTimeline timeline = simulator(recovery).simulate(scenario.blueTeam(), scenario.redTeam(), seed);
            summary.collect(timeline, scenario);
            if (replay) {
                MatchTimeline again = simulator(recovery).simulate(scenario.blueTeam(), scenario.redTeam(), seed);
                if (!signature(timeline).equals(signature(again))) summary.replayMismatch++;
            }
        }
        return summary;
    }

    private static void printSummary(String scenario, String mode, Summary s) {
        System.out.printf("%s %s END win[B/R]=%.3f/%.3f duration[avg/median/p90/p95]=%.1f/%d/%d/%d over60=%.3f timeout=%d "
                        + "invalid[cs/gold/random/resume/passive/future/duplicate/replay]=%d/%d/%d/%d/%d/%d/%d/%d%n",
                scenario, mode, s.blueWinRate(), s.redWinRate(), s.averageDuration(), s.percentile(.50),
                s.percentile(.90), s.percentile(.95), s.over60Rate(), s.timeouts,
                s.lockedCsAwards, s.lockedFarmGoldAwards, s.randomConsumptionSuspicions,
                s.resumeFailures, s.passiveMissing, s.futureSnapshotExposure,
                s.duplicateResumeExtension, s.replayMismatch);
        for (Position position : Position.values()) {
            PositionStats p = s.positions.get(position);
            System.out.printf("%s %s POS %s deaths=%.3f cs[B/R]=%.2f/%.2f absGap=%.2f farmGold[B/R]=%.2f/%.2f "
                            + "totalGold[B/R]=%.2f/%.2f farmResume[B/R]=%.2f/%.2f returnDeaths=%d "
                            + "returnTicks/run=%.3f missedCs/run=%.3f missedFarmGold/run=%.2f deathTicks/run=%.3f totalMissedTicks/run=%.3f%n",
                    scenario, mode, position, p.deaths / (2.0 * s.runs), p.blueCs / (double) s.runs, p.redCs / (double) s.runs,
                    p.csGap / s.runs, p.blueFarmGold / s.runs, p.redFarmGold / s.runs,
                    p.blueTotalGold / s.runs, p.redTotalGold / s.runs,
                    p.blueFarmResume / s.runs, p.redFarmResume / s.runs, p.returnDeaths,
                    p.returnTicks / (double) s.runs, p.missedCs / s.runs, p.missedGold / s.runs,
                    p.deathTicks / (double) s.runs, (p.deathTicks + p.returnTicks) / (double) s.runs);
        }
        for (CombatSource source : CombatSource.values()) {
            SourceStats stats = s.sources.get(source);
            System.out.printf("%s %s SOURCE %s victims=%d avgDelay=%.2f returnTicks/run=%.3f missedCs/run=%.3f%n",
                    scenario, mode, source, stats.victims,
                    stats.victims == 0 ? 0.0 : stats.delaySeconds / (double) stats.victims,
                    stats.returnTicks / (double) s.runs, stats.missedCs / s.runs);
        }
        PositionStats support = s.positions.get(Position.SUPPORT);
        System.out.printf("%s %s CHECK locks[T/J/M/A]=%d/%d/%d/%d supportVictims=%d supportMissedCs=%.3f%n",
                scenario, mode, s.positions.get(Position.TOP).returnDeaths,
                s.positions.get(Position.JUNGLE).returnDeaths, s.positions.get(Position.MID).returnDeaths,
                s.positions.get(Position.ADC).returnDeaths, support.victims, support.missedCs);
    }

    private static MatchSimulator simulator(boolean recovery) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), true, recovery);
    }

    private static String signature(MatchTimeline timeline) {
        return timeline.getDurationSeconds() + ":" + timeline.getWinner() + ":"
                + timeline.getEvents().stream().map(event -> event.getTimeSeconds() + ":" + event.getType() + ":"
                + event.getCombatSource() + ":" + event.getVictim() + ":" + event.getLaneCombat()).toList()
                + ":" + timeline.getSnapshots().stream().map(snapshot -> snapshot.getTimeSeconds() + ":"
                + snapshot.getPlayerSnapshots().stream().map(player -> player.getPlayerName() + ":" + player.isAlive()
                + ":" + player.isCanFarm() + ":" + player.getRespawnAtSeconds() + ":" + player.getFarmResumeAtSeconds()
                + ":" + player.getCs() + ":" + player.getGold()).toList()).toList();
    }

    private static MatchSnapshot at(MatchTimeline timeline, int time) {
        MatchSnapshot result = timeline.getSnapshots().getFirst();
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            if (snapshot.getTimeSeconds() > time) break;
            result = snapshot;
        }
        return result;
    }

    private static PlayerSnapshot player(MatchSnapshot snapshot, String playerName) {
        return snapshot.getPlayerSnapshots().stream()
                .filter(player -> player.getPlayerName().equals(playerName)).findFirst().orElseThrow();
    }

    private static final class Summary {
        final int runs;
        final EnumMap<Position, PositionStats> positions = new EnumMap<>(Position.class);
        final EnumMap<CombatSource, SourceStats> sources = new EnumMap<>(CombatSource.class);
        final List<Integer> durations = new ArrayList<>();
        int blueWins, redWins, timeouts, over60;
        long durationTotal;
        int lockedCsAwards, lockedFarmGoldAwards, randomConsumptionSuspicions, resumeFailures;
        int passiveMissing, futureSnapshotExposure, duplicateResumeExtension, replayMismatch;

        Summary(int runs) {
            this.runs = runs;
            for (Position position : Position.values()) positions.put(position, new PositionStats());
            for (CombatSource source : CombatSource.values()) sources.put(source, new SourceStats());
        }

        void collect(MatchTimeline timeline, Scenario scenario) {
            MatchSnapshot checkpoint = at(timeline, CHECKPOINT);
            collectCheckpoint(checkpoint);
            List<Death> deaths = structuredDeaths(timeline);
            collectDeaths(timeline, scenario, deaths);
            collectInvariants(timeline, deaths);
            durationTotal += timeline.getDurationSeconds();
            durations.add(timeline.getDurationSeconds());
            if (timeline.getDurationSeconds() >= 3_600) over60++;
            if (timeline.getWinner() == null) timeouts++;
            else if (timeline.getWinner().equals("BLUE")) blueWins++;
            else redWins++;
        }

        void collectCheckpoint(MatchSnapshot checkpoint) {
            for (Position position : Position.values()) {
                PlayerSnapshot blue = checkpoint.getPlayerSnapshots().stream()
                        .filter(player -> player.getTeamName().equals("BLUE") && player.getPosition() == position)
                        .findFirst().orElseThrow();
                PlayerSnapshot red = checkpoint.getPlayerSnapshots().stream()
                        .filter(player -> player.getTeamName().equals("RED") && player.getPosition() == position)
                        .findFirst().orElseThrow();
                PositionStats stats = positions.get(position);
                stats.deaths += blue.getDeaths() + red.getDeaths();
                stats.blueCs += blue.getCs(); stats.redCs += red.getCs();
                stats.csGap += Math.abs(blue.getCs() - red.getCs());
                stats.blueFarmGold += blue.getCs() * PositionEconomyRuleConfig.CS_GOLD;
                stats.redFarmGold += red.getCs() * PositionEconomyRuleConfig.CS_GOLD;
                stats.blueTotalGold += blue.getGold(); stats.redTotalGold += red.getGold();
                stats.blueFarmResume += blue.getFarmResumeAtSeconds();
                stats.redFarmResume += red.getFarmResumeAtSeconds();
            }
        }

        void collectDeaths(MatchTimeline timeline, Scenario scenario, List<Death> deaths) {
            for (Death death : deaths) {
                PlayerSnapshot victim = player(at(timeline, death.time), death.victim);
                PositionStats position = positions.get(victim.getPosition());
                SourceStats source = sources.get(death.source);
                position.victims++;
                source.victims++;
                int respawn = victim.getRespawnAtSeconds();
                int resume = victim.getFarmResumeAtSeconds();
                int delay = Math.max(0, resume - respawn);
                source.delaySeconds += delay;
                if (delay > 0) position.returnDeaths++;
                for (int tick = death.time + 10; tick <= CHECKPOINT && tick < resume; tick += 10) {
                    double expected = expectedCs(timeline, scenario, victim, tick);
                    if (tick < respawn) {
                        position.deathTicks++;
                    } else {
                        position.returnTicks++;
                        position.missedCs += expected;
                        position.missedGold += expected * PositionEconomyRuleConfig.CS_GOLD;
                        source.returnTicks++;
                        source.missedCs += expected;
                    }
                }
            }
        }

        void collectInvariants(MatchTimeline timeline, List<Death> deaths) {
            Map<String, Set<Integer>> deathTimes = new HashMap<>();
            for (Death death : deaths) deathTimes.computeIfAbsent(death.victim, ignored -> new HashSet<>()).add(death.time);
            List<MatchSnapshot> snapshots = timeline.getSnapshots();
            for (int index = 1; index < snapshots.size(); index++) {
                MatchSnapshot previous = snapshots.get(index - 1), current = snapshots.get(index);
                if (current.getTimeSeconds() > CHECKPOINT) break;
                for (PlayerSnapshot now : current.getPlayerSnapshots()) {
                    PlayerSnapshot before = player(previous, now.getPlayerName());
                    int csIncrease = now.getCs() - before.getCs();
                    int goldIncrease = now.getGold() - before.getGold();
                    if (goldIncrease < PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK) passiveMissing++;
                    if (now.isAlive() && !now.isCanFarm() && csIncrease > 0) {
                        lockedCsAwards += csIncrease;
                        lockedFarmGoldAwards += csIncrease * PositionEconomyRuleConfig.CS_GOLD;
                        randomConsumptionSuspicions++;
                    }
                    int guaranteed = guaranteedWholeCs(now.getPosition());
                    if (!before.isCanFarm() && now.isCanFarm() && guaranteed > 0 && csIncrease < guaranteed) resumeFailures++;
                    boolean deathNow = deathTimes.getOrDefault(now.getPlayerName(), Set.of()).contains(current.getTimeSeconds());
                    if (now.getFarmResumeAtSeconds() != before.getFarmResumeAtSeconds() && !deathNow) duplicateResumeExtension++;
                    if (now.getFarmResumeAtSeconds() > 0 && deathTimes.getOrDefault(now.getPlayerName(), Set.of()).stream()
                            .noneMatch(time -> time <= current.getTimeSeconds())) futureSnapshotExposure++;
                }
            }
        }

        double blueWinRate() { return blueWins / (double) runs; }
        double redWinRate() { return redWins / (double) runs; }
        double averageDuration() { return durationTotal / (double) runs; }
        double over60Rate() { return over60 / (double) runs; }
        int percentile(double p) {
            durations.sort(Comparator.naturalOrder());
            return durations.get(Math.min(durations.size() - 1, (int) Math.ceil(p * durations.size()) - 1));
        }
    }

    private static List<Death> structuredDeaths(MatchTimeline timeline) {
        List<Death> deaths = new ArrayList<>();
        for (MatchEvent event : timeline.getEvents()) {
            if (event.getTimeSeconds() > CHECKPOINT) continue;
            if (event.getType() == MatchEventType.LANE_COMBAT) {
                LaneCombatData data = event.getLaneCombat();
                if (data != null && data.outcome() != LaneCombatOutcome.NO_KILL) {
                    deaths.add(new Death(event.getTimeSeconds(), data.victimPlayerId(), CombatSource.LANE_COMBAT));
                }
            } else if (event.getType() == MatchEventType.KILL) {
                deaths.add(new Death(event.getTimeSeconds(), event.getVictim(),
                        event.getCombatSource() == null ? CombatSource.OTHER : event.getCombatSource()));
            }
        }
        return deaths;
    }

    private static double expectedCs(MatchTimeline timeline, Scenario scenario, PlayerSnapshot victim, int tick) {
        double base = baseCs(victim.getPosition());
        if (base == 0) return 0;
        double farming = scenario.farming(victim.getTeamName().equals("BLUE") ? TeamSide.BLUE : TeamSide.RED,
                victim.getPosition());
        double lane = laneMultiplier(timeline, tick, victim.getPosition(), victim.getTeamName());
        return base * farming * lane * 10 / 60.0;
    }

    private static double laneMultiplier(MatchTimeline timeline, int tick, Position position, String team) {
        Lane lane = switch (position) {
            case TOP -> Lane.TOP;
            case MID -> Lane.MID;
            case ADC -> Lane.BOT;
            case JUNGLE, SUPPORT -> null;
        };
        if (lane == null) return 1.0;
        double pressure = timeline.getEvents().stream()
                .filter(event -> event.getTimeSeconds() == tick && event.getType() == MatchEventType.LANE_COMBAT)
                .map(MatchEvent::getLaneCombat).filter(data -> data != null && data.lane() == lane)
                .findFirst().map(LaneCombatData::pressureBefore)
                .orElseGet(() -> at(timeline, tick).getLaneSnapshots().stream()
                        .filter(value -> value.lane() == lane).findFirst().map(LaneSnapshot::pressure).orElse(0.0));
        double signed = Math.max(-LanePressureRuleConfig.MAX_LANE_CS_MODIFIER,
                Math.min(LanePressureRuleConfig.MAX_LANE_CS_MODIFIER,
                        pressure / 100.0 * LanePressureRuleConfig.MAX_LANE_CS_MODIFIER));
        return team.equals("BLUE") ? 1 + signed : 1 - signed;
    }

    private static double baseCs(Position position) {
        return switch (position) {
            case TOP -> PositionEconomyRuleConfig.TOP_BASE_CS_PER_MINUTE;
            case JUNGLE -> PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE;
            case MID -> PositionEconomyRuleConfig.MID_BASE_CS_PER_MINUTE;
            case ADC -> PositionEconomyRuleConfig.ADC_BASE_CS_PER_MINUTE;
            case SUPPORT -> PositionEconomyRuleConfig.SUPPORT_BASE_CS_PER_MINUTE;
        };
    }

    private static int guaranteedWholeCs(Position position) {
        return (int) Math.floor(baseCs(position)
                * (1.0 - LanePressureRuleConfig.MAX_LANE_CS_MODIFIER) / 6.0);
    }

    private record Death(int time, String victim, CombatSource source) {}

    private static final class PositionStats {
        long deaths, blueCs, redCs;
        double csGap, blueFarmGold, redFarmGold, blueTotalGold, redTotalGold, blueFarmResume, redFarmResume;
        int victims, returnDeaths, returnTicks, deathTicks;
        double missedCs, missedGold;
    }

    private static final class SourceStats {
        int victims, delaySeconds, returnTicks;
        double missedCs;
    }

    private record Scenario(String name, Map<Position, Values> blue, Map<Position, Values> red) {
        static List<Scenario> base() { return List.of(
                scenario("A", null), scenario("B", Position.TOP), scenario("C", Position.MID),
                scenario("D", Position.ADC), scenario("E", Position.SUPPORT), aggression("F", false)); }
        static List<Scenario> mirrors() { return List.of(
                mirror("B_M", Position.TOP), mirror("C_M", Position.MID), mirror("D_M", Position.ADC),
                mirror("E_M", Position.SUPPORT), aggression("F_M", true)); }
        static Scenario scenario(String name, Position changed) {
            return changed == null ? new Scenario(name, Map.of(), Map.of())
                    : new Scenario(name, Map.of(changed, new Values(18, 18, 14)),
                    Map.of(changed, new Values(10, 10, 14)));
        }
        static Scenario mirror(String name, Position changed) {
            return new Scenario(name, Map.of(changed, new Values(10, 10, 14)),
                    Map.of(changed, new Values(18, 18, 14)));
        }
        static Scenario aggression(String name, boolean mirror) {
            return new Scenario(name, Map.of(Position.TOP, new Values(14, mirror ? 10 : 18, 14)),
                    Map.of(Position.TOP, new Values(14, mirror ? 18 : 10, 14)));
        }
        Team blueTeam() { return team("BLUE", blue); }
        Team redTeam() { return team("RED", red); }
        double farming(TeamSide side, Position position) {
            int value = (side == TeamSide.BLUE ? blue : red).getOrDefault(position, Values.BASELINE).farming;
            return Math.max(PositionEconomyRuleConfig.MIN_FARMING_MULTIPLIER,
                    Math.min(PositionEconomyRuleConfig.MAX_FARMING_MULTIPLIER,
                            1 + (value - PositionEconomyRuleConfig.FARMING_BASELINE)
                                    * PositionEconomyRuleConfig.FARMING_MULTIPLIER_PER_POINT));
        }
        private static Team team(String name, Map<Position, Values> changes) {
            List<Player> players = new ArrayList<>();
            for (Position position : Position.values()) {
                Values value = changes.getOrDefault(position, Values.BASELINE);
                players.add(new Player(name + "-" + position, position,
                        new PlayerAttributes(value.mechanics, value.aggression, value.farming, 14)));
            }
            return new Team(name, players);
        }
    }

    private record Values(int mechanics, int aggression, int farming) {
        static final Values BASELINE = new Values(14, 14, 14);
    }
}

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Structured diagnostics only: no event message is read or parsed. */
public final class LaneCombatDiagnostics {
    private static final int CHECKPOINT_SECONDS = 840;

    public static void main(String[] args) {
        System.out.println("LANE_COMBAT ON/OFF (seeds 1..1000, checkpoint=14m)");
        for (Scenario scenario : Scenario.baseScenarios()) {
            Summary on = run(scenario, 1_000, true, false);
            Summary off = run(scenario, 1_000, false, false);
            printComparison(scenario.name(), on, off);
        }

        System.out.println("LANE_COMBAT A-F + MIRROR STRUCTURED AUDIT");
        for (Scenario scenario : Scenario.baseScenarios()) {
            printAudit(scenario.name(), run(scenario, 1_000, true, true));
        }
        for (Scenario scenario : Scenario.mirrorScenarios()) {
            printAudit(scenario.name(), run(scenario, 500, true, true));
        }
    }

    private static Summary run(Scenario scenario, int runs, boolean enabled, boolean verifyReplay) {
        Summary summary = new Summary(runs);
        for (long seed = 1; seed <= runs; seed++) {
            Team blue = scenario.blueTeam();
            Team red = scenario.redTeam();
            MatchTimeline timeline = simulator(enabled).simulate(blue, red, seed);
            summary.collect(timeline, blue, red, scenario);
            if (verifyReplay) {
                MatchTimeline replay = simulator(enabled).simulate(scenario.blueTeam(), scenario.redTeam(), seed);
                if (!signature(timeline).equals(signature(replay))) summary.replayMismatch++;
            }
        }
        return summary;
    }

    private static void printComparison(String scenario, Summary on, Summary off) {
        System.out.printf(
                "%s ON[attempt=%d laneKill=%d generic=%d teamfight=%d other=%d total=%d skipGeneric=%d skipTeamfightEligible=%d deaths=%s cs=%s absGap=%s goldGap=%.2f pressure=%s priority=%s win=%.3f/%.3f duration=%.1f/%d timeout=%.3f missedTicks=%.3f missedCs=%.3f] "
                        + "OFF[attempt=%d laneKill=%d generic=%d teamfight=%d other=%d total=%d skipGeneric=%d skipTeamfightEligible=%d deaths=%s cs=%s absGap=%s goldGap=%.2f pressure=%s priority=%s win=%.3f/%.3f duration=%.1f/%d timeout=%.3f]%n",
                scenario,
                on.attempts, on.laneKills, on.genericKills, on.teamfightKills, on.otherKills, on.totalKills(), on.attempts, 0,
                on.deaths(), on.cs(), on.csGaps(), on.averageGoldGap(), on.pressures(), on.priorities(),
                on.blueWinRate(), on.redWinRate(), on.averageDuration(), on.medianDuration(), on.timeoutRate(),
                on.averageMissedTicks(), on.averageMissedCs(),
                off.attempts, off.laneKills, off.genericKills, off.teamfightKills, off.otherKills, off.totalKills(), 0, 0,
                off.deaths(), off.cs(), off.csGaps(), off.averageGoldGap(), off.pressures(), off.priorities(),
                off.blueWinRate(), off.redWinRate(), off.averageDuration(), off.medianDuration(), off.timeoutRate()
        );
    }

    private static void printAudit(String scenario, Summary s) {
        System.out.printf(
                "%s attempts=%d initiator=%d/%d outcomes[N/A/R]=%d/%d/%d attackerWin=%.3f kills[B/R]=%d/%d laneKills[T/M/B]=%d/%d/%d "
                        + "botKiller[ADC/SUP]=%d/%d botVictim[ADC/SUP]=%d/%d assist[ok/missing]=%d/%d victims[T/M/A/S]=%d/%d/%d/%d "
                        + "missed[ticks/cs/gold]=%.3f/%.3f/%.2f generic=%d teamfight=%d other=%d multiDeath=%d deadParticipant=%d duplicateReward=%d multiCombatTick=%d supportCs=%d replayMismatch=%d%n",
                scenario, s.attempts, s.initiatorBlue, s.initiatorRed, s.noKill, s.attackerKill, s.reverseKill,
                s.attackerWinRate(), s.blueLaneKills, s.redLaneKills,
                s.laneKillsByLane.get(Lane.TOP), s.laneKillsByLane.get(Lane.MID), s.laneKillsByLane.get(Lane.BOT),
                s.botAdcKiller, s.botSupportKiller, s.botAdcVictim, s.botSupportVictim, s.assistOk, s.assistMissing,
                s.victims.get(Position.TOP), s.victims.get(Position.MID), s.victims.get(Position.ADC), s.victims.get(Position.SUPPORT),
                s.averageMissedTicks(), s.averageMissedCs(), s.averageMissedGold(), s.genericKills, s.teamfightKills, s.otherKills,
                s.multipleDeaths, s.deadParticipants, s.duplicateRewards, s.multipleCombatTicks, s.supportCsViolations, s.replayMismatch
        );
    }

    private static MatchSimulator simulator(boolean enabled) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), enabled);
    }

    private static MatchSnapshot at(MatchTimeline timeline, int seconds) {
        MatchSnapshot result = timeline.getSnapshots().getFirst();
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            if (snapshot.getTimeSeconds() > seconds) break;
            result = snapshot;
        }
        return result;
    }

    private static String signature(MatchTimeline timeline) {
        return timeline.getDurationSeconds() + ":" + timeline.getWinner() + ":"
                + timeline.getEvents().stream().map(event -> event.getTimeSeconds() + ":" + event.getType() + ":"
                + event.getCombatSource() + ":" + event.getLaneCombat() + ":" + event.getKiller() + ":" + event.getVictim()).toList()
                + ":" + timeline.getSnapshots().stream().map(snapshot -> snapshot.getTimeSeconds() + ":"
                + snapshot.getBlueGold() + ":" + snapshot.getRedGold() + ":" + snapshot.getLaneSnapshots()).toList();
    }

    private static Position positionOf(Team blue, Team red, String playerId) {
        for (Player player : blue.getPlayers()) if (player.getName().equals(playerId)) return player.getPosition();
        for (Player player : red.getPlayers()) if (player.getName().equals(playerId)) return player.getPosition();
        throw new IllegalArgumentException("Unknown player: " + playerId);
    }

    private static TeamSide sideOf(Team blue, Team red, String playerId) {
        if (blue.getPlayers().stream().anyMatch(player -> player.getName().equals(playerId))) return TeamSide.BLUE;
        if (red.getPlayers().stream().anyMatch(player -> player.getName().equals(playerId))) return TeamSide.RED;
        throw new IllegalArgumentException("Unknown player: " + playerId);
    }

    private static final class Summary {
        final int runs;
        int attempts, laneKills, genericKills, teamfightKills, otherKills;
        int initiatorBlue, initiatorRed, noKill, attackerKill, reverseKill, blueLaneKills, redLaneKills;
        int botAdcKiller, botSupportKiller, botAdcVictim, botSupportVictim, assistOk, assistMissing;
        int multipleDeaths, deadParticipants, duplicateRewards, multipleCombatTicks, supportCsViolations, replayMismatch;
        int blueWins, redWins, timeouts;
        long durationTotal;
        final List<Integer> durations = new ArrayList<>();
        final EnumMap<Lane, Integer> laneKillsByLane = integers(Lane.class);
        final EnumMap<Position, Integer> victims = integers(Position.class);
        final EnumMap<Position, Long> deaths = longs(Position.class);
        final EnumMap<Position, Long> blueCs = longs(Position.class), redCs = longs(Position.class);
        final EnumMap<Position, Double> csGap = doubles(Position.class);
        final EnumMap<Lane, Double> pressure = doubles(Lane.class);
        final EnumMap<Lane, Long> bluePriority = longs(Lane.class);
        final EnumMap<Lane, Long> neutralPriority = longs(Lane.class);
        final EnumMap<Lane, Long> redPriority = longs(Lane.class);
        double goldGap;
        long missedTicks;
        double missedExpectedCs, missedGold;

        Summary(int runs) { this.runs = runs; }

        void collect(MatchTimeline timeline, Team blue, Team red, Scenario scenario) {
            MatchSnapshot checkpoint = at(timeline, CHECKPOINT_SECONDS);
            collectCheckpoint(checkpoint);
            collectEvents(timeline, blue, red, scenario);
            durationTotal += timeline.getDurationSeconds(); durations.add(timeline.getDurationSeconds());
            if (timeline.getWinner() == null) timeouts++;
            else if (timeline.getWinner().equals(blue.getName())) blueWins++;
            else redWins++;
        }

        void collectCheckpoint(MatchSnapshot snapshot) {
            for (Position position : Position.values()) {
                PlayerSnapshot blue = snapshot.getPlayerSnapshots().stream().filter(p -> p.getTeamName().equals("BLUE") && p.getPosition() == position).findFirst().orElseThrow();
                PlayerSnapshot red = snapshot.getPlayerSnapshots().stream().filter(p -> p.getTeamName().equals("RED") && p.getPosition() == position).findFirst().orElseThrow();
                blueCs.merge(position, (long) blue.getCs(), Long::sum); redCs.merge(position, (long) red.getCs(), Long::sum);
                csGap.merge(position, (double) Math.abs(blue.getCs() - red.getCs()), Double::sum);
                deaths.merge(position, (long) blue.getDeaths() + red.getDeaths(), Long::sum);
                if (position == Position.SUPPORT && (blue.getCs() != 0 || red.getCs() != 0)) supportCsViolations++;
            }
            goldGap += Math.abs(snapshot.getBlueGold() - snapshot.getRedGold());
            for (LaneSnapshot lane : snapshot.getLaneSnapshots()) {
                pressure.merge(lane.lane(), lane.pressure(), Double::sum);
                if (lane.priority() == LanePriority.BLUE) bluePriority.merge(lane.lane(), 1L, Long::sum);
                else if (lane.priority() == LanePriority.RED) redPriority.merge(lane.lane(), 1L, Long::sum);
                else neutralPriority.merge(lane.lane(), 1L, Long::sum);
            }
        }

        void collectEvents(MatchTimeline timeline, Team blue, Team red, Scenario scenario) {
            Map<Integer, Integer> combatByTime = new java.util.HashMap<>();
            java.util.Set<String> rewardKeys = new java.util.HashSet<>();
            for (MatchEvent event : timeline.getEvents()) {
                if (event.getTimeSeconds() > CHECKPOINT_SECONDS) continue;
                if (event.getType() == MatchEventType.LANE_COMBAT) {
                    attempts++; combatByTime.merge(event.getTimeSeconds(), 1, Integer::sum);
                    LaneCombatData data = event.getLaneCombat();
                    if (data.initiatorSide() == TeamSide.BLUE) initiatorBlue++; else initiatorRed++;
                    if (data.outcome() == LaneCombatOutcome.NO_KILL) { noKill++; continue; }
                    laneKills++; laneKillsByLane.merge(data.lane(), 1, Integer::sum);
                    if (data.outcome() == LaneCombatOutcome.ATTACKER_KILL) attackerKill++; else reverseKill++;
                    if (data.winningSide() == TeamSide.BLUE) blueLaneKills++; else redLaneKills++;
                    Position killer = positionOf(blue, red, data.killerPlayerId());
                    Position victim = positionOf(blue, red, data.victimPlayerId());
                    victims.merge(victim, 1, Integer::sum);
                    if (data.lane() == Lane.BOT) {
                        if (killer == Position.ADC) botAdcKiller++; else botSupportKiller++;
                        if (victim == Position.ADC) botAdcVictim++; else botSupportVictim++;
                        if (data.assistantPlayerIds().size() == 1
                                && !data.assistantPlayerIds().getFirst().equals(data.killerPlayerId())
                                && !data.assistantPlayerIds().getFirst().equals(data.victimPlayerId())
                                && sideOf(blue, red, data.assistantPlayerIds().getFirst()) == data.winningSide()) assistOk++;
                        else assistMissing++;
                    }
                    String rewardKey = event.getTimeSeconds() + ":" + data.victimPlayerId();
                    if (!rewardKeys.add(rewardKey)) duplicateRewards++;
                    if (data.assistantPlayerIds().contains(data.victimPlayerId()) || data.assistantPlayerIds().contains(data.killerPlayerId())) multipleDeaths++;
                    collectMissedFarm(data, victim, event.getTimeSeconds(), scenario, timeline);
                    if (!aliveBefore(timeline, data.killerPlayerId(), event.getTimeSeconds())
                            || !aliveBefore(timeline, data.victimPlayerId(), event.getTimeSeconds())) deadParticipants++;
                } else if (event.getType() == MatchEventType.KILL) {
                    // A lane-combat KILL is the structured kill record for the LANE_COMBAT event
                    // already counted above; it is not a second combat on the same tick.
                    if (event.getCombatSource() == CombatSource.LANE_COMBAT) {
                        continue;
                    }
                    combatByTime.merge(event.getTimeSeconds(), 1, Integer::sum);
                    if (event.getCombatSource() == CombatSource.SKIRMISH) genericKills++;
                    else if (event.getCombatSource() == CombatSource.TEAMFIGHT) teamfightKills++;
                    else otherKills++;
                } else if (event.getType() == MatchEventType.TEAMFIGHT) {
                    combatByTime.merge(event.getTimeSeconds(), 1, Integer::sum);
                }
            }
            multipleCombatTicks += combatByTime.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
        }

        void collectMissedFarm(LaneCombatData data, Position victim, int deathTime, Scenario scenario, MatchTimeline timeline) {
            PlayerSnapshot victimSnapshot = timeline.getSnapshots().stream()
                    .filter(snapshot -> snapshot.getTimeSeconds() == deathTime)
                    .flatMap(snapshot -> snapshot.getPlayerSnapshots().stream())
                    .filter(player -> player.getPlayerName().equals(data.victimPlayerId()))
                    .findFirst().orElseThrow();
            int resume = victimSnapshot.getFarmResumeAtSeconds();
            int ticks = 0;
            double expectedTotal = 0.0;
            TeamSide victimSide = data.winningSide().opposite();
            double base = switch (victim) {
                case TOP -> PositionEconomyRuleConfig.TOP_BASE_CS_PER_MINUTE;
                case MID -> PositionEconomyRuleConfig.MID_BASE_CS_PER_MINUTE;
                case ADC -> PositionEconomyRuleConfig.ADC_BASE_CS_PER_MINUTE;
                case JUNGLE -> PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE;
                case SUPPORT -> 0;
            };
            for (int tick = deathTime + 10; tick < resume; tick += 10) {
                ticks++;
                if (base == 0) continue;
                double modifier = 1.0;
                if (victim != Position.JUNGLE) {
                    double pressure = at(timeline, tick).getLaneSnapshots().stream()
                            .filter(lane -> lane.lane() == data.lane()).findFirst().orElseThrow().pressure();
                    double signed = pressure / 100.0 * LanePressureRuleConfig.MAX_LANE_CS_MODIFIER;
                    modifier = victimSide == TeamSide.BLUE ? 1 + signed : 1 - signed;
                }
                expectedTotal += base * scenario.farming(victimSide, victim) * modifier * 10 / 60.0;
            }
            missedTicks += ticks;
            missedExpectedCs += expectedTotal;
            missedGold += expectedTotal * PositionEconomyRuleConfig.CS_GOLD;
        }

        boolean aliveBefore(MatchTimeline timeline, String player, int time) {
            MatchSnapshot before = at(timeline, Math.max(0, time - 10));
            int elapsed = time - before.getTimeSeconds();
            return before.getPlayerSnapshots().stream().filter(p -> p.getPlayerName().equals(player)).findFirst()
                    .map(p -> p.isAlive() || p.getRespawnRemainingSeconds() <= elapsed).orElse(false);
        }

        int totalKills() { return laneKills + genericKills + teamfightKills + otherKills; }
        double attackerWinRate() { return laneKills == 0 ? 0 : attackerKill / (double) laneKills; }
        double averageGoldGap() { return goldGap / runs; }
        double averageDuration() { return durationTotal / (double) runs; }
        int medianDuration() { durations.sort(Integer::compareTo); return durations.get(durations.size()/2); }
        double timeoutRate() { return timeouts/(double)runs; }
        double blueWinRate() { return blueWins/(double)runs; }
        double redWinRate() { return redWins/(double)runs; }
        double averageMissedTicks() { return laneKills == 0 ? 0 : missedTicks/(double)laneKills; }
        double averageMissedCs() { return laneKills == 0 ? 0 : missedExpectedCs/laneKills; }
        double averageMissedGold() { return laneKills == 0 ? 0 : missedGold/laneKills; }
        String deaths() { return positions(deaths, 2.0*runs); }
        String cs() { return paired(blueCs, redCs, runs); }
        String csGaps() { return positions(csGap, runs); }
        String pressures() { return lanes(pressure, runs); }
        String priorities() { return String.format("T=%.3f/%.3f/%.3f M=%.3f/%.3f/%.3f B=%.3f/%.3f/%.3f(B/N/R)",
                bluePriority.get(Lane.TOP)/(double)runs, neutralPriority.get(Lane.TOP)/(double)runs, redPriority.get(Lane.TOP)/(double)runs,
                bluePriority.get(Lane.MID)/(double)runs, neutralPriority.get(Lane.MID)/(double)runs, redPriority.get(Lane.MID)/(double)runs,
                bluePriority.get(Lane.BOT)/(double)runs, neutralPriority.get(Lane.BOT)/(double)runs, redPriority.get(Lane.BOT)/(double)runs); }
    }

    private record Scenario(String name, Map<Position, Values> blue, Map<Position, Values> red) {
        static List<Scenario> baseScenarios() { return List.of(
                scenario("A", null), scenario("B", Position.TOP), scenario("C", Position.MID),
                scenario("D", Position.ADC), scenario("E", Position.SUPPORT), aggressionOnly("F", false)); }
        static List<Scenario> mirrorScenarios() { return List.of(
                scenario("A_M", null), mirror("B_M", Position.TOP), mirror("C_M", Position.MID), mirror("D_M", Position.ADC),
                mirror("E_M", Position.SUPPORT), aggressionOnly("F_M", true)); }
        static Scenario scenario(String name, Position changed) { return changed == null ? new Scenario(name, Map.of(), Map.of())
                : new Scenario(name, Map.of(changed,new Values(18,18,14)), Map.of(changed,new Values(10,10,14))); }
        static Scenario mirror(String name, Position changed) { return new Scenario(name,
                Map.of(changed,new Values(10,10,14)), Map.of(changed,new Values(18,18,14))); }
        static Scenario aggressionOnly(String name, boolean mirror) { return new Scenario(name,
                Map.of(Position.TOP,new Values(14,mirror?10:18,14)), Map.of(Position.TOP,new Values(14,mirror?18:10,14))); }
        Team blueTeam() { return team("BLUE", blue); } Team redTeam() { return team("RED", red); }
        double farming(TeamSide side, Position position) { int value=(side==TeamSide.BLUE?blue:red).getOrDefault(position,Values.BASELINE).farming; return Math.max(.74,Math.min(1.12,1+(value-14)*.02)); }
        private static Team team(String name, Map<Position,Values> changes) { List<Player> players=new ArrayList<>();for(Position p:Position.values()){Values v=changes.getOrDefault(p,Values.BASELINE);players.add(new Player(name+"-"+p,p,new PlayerAttributes(v.mechanics,v.aggression,v.farming,14)));}return new Team(name,players); }
    }
    private record Values(int mechanics,int aggression,int farming) { static final Values BASELINE=new Values(14,14,14); }

    private static <E extends Enum<E>> EnumMap<E,Integer> integers(Class<E> type){EnumMap<E,Integer> map=new EnumMap<>(type);for(E e:type.getEnumConstants())map.put(e,0);return map;}
    private static <E extends Enum<E>> EnumMap<E,Long> longs(Class<E> type){EnumMap<E,Long> map=new EnumMap<>(type);for(E e:type.getEnumConstants())map.put(e,0L);return map;}
    private static <E extends Enum<E>> EnumMap<E,Double> doubles(Class<E> type){EnumMap<E,Double> map=new EnumMap<>(type);for(E e:type.getEnumConstants())map.put(e,0.0);return map;}
    private static String positions(Map<Position,? extends Number> values,double divisor){return String.format("T/J/M/A/S=%.2f/%.2f/%.2f/%.2f/%.2f",values.get(Position.TOP).doubleValue()/divisor,values.get(Position.JUNGLE).doubleValue()/divisor,values.get(Position.MID).doubleValue()/divisor,values.get(Position.ADC).doubleValue()/divisor,values.get(Position.SUPPORT).doubleValue()/divisor);}
    private static String paired(Map<Position,Long> blue,Map<Position,Long> red,double divisor){return String.format("T=%.2f/%.2f M=%.2f/%.2f A=%.2f/%.2f",blue.get(Position.TOP)/divisor,red.get(Position.TOP)/divisor,blue.get(Position.MID)/divisor,red.get(Position.MID)/divisor,blue.get(Position.ADC)/divisor,red.get(Position.ADC)/divisor);}
    private static String lanes(Map<Lane,Double> values,double divisor){return String.format("T/M/B=%.2f/%.2f/%.2f",values.get(Lane.TOP)/divisor,values.get(Lane.MID)/divisor,values.get(Lane.BOT)/divisor);}
}

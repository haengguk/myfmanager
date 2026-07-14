package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.RoamData;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Large-sample, observational roam diagnostic. It intentionally is not a unit test. */
public final class RoamDiagnostics {
    private RoamDiagnostics() { }

    public static void main(String[] args) {
        auditScenarioInputs();
        List<Scenario> scenarios = List.of(Scenario.A, Scenario.B, Scenario.C, Scenario.D, Scenario.E, Scenario.F, Scenario.G);
        auditFirstBAndCDifference();
        for (Scenario scenario : scenarios) {
            print(run(scenario, false, true, 1_000));
            print(run(scenario, false, false, 1_000));
        }
        for (Scenario scenario : List.of(Scenario.B, Scenario.C, Scenario.D, Scenario.E, Scenario.F, Scenario.G)) {
            print(run(scenario, true, true, 500));
            print(run(scenario, true, false, 500));
        }
    }

    private static Report run(Scenario scenario, boolean mirror, boolean roamEnabled, int seeds) {
        MatchSimulator simulator = simulator(SimulationOptions.productionDefaults().withRoamEnabled(roamEnabled));
        Report report = new Report(scenario.name() + (mirror ? "-MIRROR" : "") + (roamEnabled ? "-ON" : "-OFF"), seeds);
        Team blue = team("BLUE", scenario, mirror, TeamSide.BLUE);
        Team red = team("RED", scenario, mirror, TeamSide.RED);
        for (long seed = 1; seed <= seeds; seed++) report.add(simulator.simulateWithDiagnostics(blue, red, seed), seed);
        return report;
    }

    private static MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private static Team team(String name, Scenario scenario, boolean mirror, TeamSide side) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            PlayerAttributes attributes = scenario.attributes(mirror ? side.opposite() : side, position);
            players.add(new Player(name + "-" + position, position, attributes));
        }
        return new Team(name, players);
    }
    private static void auditScenarioInputs() {
        Team bBlue = team("B-BLUE", Scenario.B, false, TeamSide.BLUE), bRed = team("B-RED", Scenario.B, false, TeamSide.RED);
        Team cBlue = team("C-BLUE", Scenario.C, false, TeamSide.BLUE), cRed = team("C-RED", Scenario.C, false, TeamSide.RED);
        System.out.println("ROAM_INPUT_AUDIT B blueMid=" + attributes(bBlue, Position.MID) + " redMid=" + attributes(bRed, Position.MID)
                + " blueSupport=" + attributes(bBlue, Position.SUPPORT) + " redSupport=" + attributes(bRed, Position.SUPPORT));
        System.out.println("ROAM_INPUT_AUDIT C blueMid=" + attributes(cBlue, Position.MID) + " redMid=" + attributes(cRed, Position.MID)
                + " blueSupport=" + attributes(cBlue, Position.SUPPORT) + " redSupport=" + attributes(cRed, Position.SUPPORT));
        System.out.println("ROAM_INPUT_IDENTITY teamsShared=" + (bBlue == cBlue || bRed == cRed)
                + " midAttributesShared=" + (attributesObject(bBlue, Position.MID) == attributesObject(cBlue, Position.MID)
                || attributesObject(bRed, Position.MID) == attributesObject(cRed, Position.MID))
                + " bBlue=" + System.identityHashCode(bBlue) + " cBlue=" + System.identityHashCode(cBlue));
    }

    private static String attributes(Team team, Position position) {
        PlayerAttributes a = attributesObject(team, position);
        return a.getMechanics() + "/" + a.getAggression() + "/" + a.getFarming() + "/" + a.getTeamfighting();
    }

    private static PlayerAttributes attributesObject(Team team, Position position) {
        return team.getPlayers().stream().filter(player -> player.getPosition() == position).findFirst().orElseThrow().getAttributes();
    }

    private static void auditFirstBAndCDifference() {
        MatchSimulator simulator = simulator(SimulationOptions.productionDefaults());
        Team bBlue = team("BLUE", Scenario.B, false, TeamSide.BLUE), bRed = team("RED", Scenario.B, false, TeamSide.RED);
        Team cBlue = team("BLUE", Scenario.C, false, TeamSide.BLUE), cRed = team("RED", Scenario.C, false, TeamSide.RED);
        for (long seed = 1; seed <= 1_000; seed++) {
            RoamData b = simulator.simulateWithDiagnostics(bBlue, bRed, seed).timeline().getEvents().stream().filter(event -> event.getType() == MatchEventType.ROAM).findFirst().map(MatchEvent::getRoam).orElse(null);
            RoamData c = simulator.simulateWithDiagnostics(cBlue, cRed, seed).timeline().getEvents().stream().filter(event -> event.getType() == MatchEventType.ROAM).findFirst().map(MatchEvent::getRoam).orElse(null);
            if (b != null && c != null && (Double.compare(b.combatEdge(), c.combatEdge()) != 0 || b.roamerMechanics() != c.roamerMechanics() || b.roamerTeamfighting() != c.roamerTeamfighting())) {
                System.out.println("ROAM_BC_FIRST_DIFFERENCE seed=" + seed + " B=" + structuredRoam(b) + " C=" + structuredRoam(c));
                return;
            }
        }
        throw new IllegalStateException("B/C did not produce a structured roam difference in seeds 1..1000");
    }

    private static String structuredRoam(RoamData d) {
        return "side=" + d.roamingSide() + ",position=" + d.roamerPosition() + ",target=" + d.targetLane() + ",attr=" + d.roamerMechanics() + "/" + d.roamerAggression() + "/" + d.roamerFarming() + "/" + d.roamerTeamfighting() + ",groups=" + d.attackerMechanics() + "/" + d.attackerAggression() + "/" + d.attackerTeamfighting() + ":" + d.defenderMechanics() + "/" + d.defenderAggression() + "/" + d.defenderTeamfighting() + ",edge=" + d.mechanicsEdge() + "/" + d.aggressionEdge() + "/" + d.teamfightingEdge() + "/" + d.goldEdge() + "/" + d.vulnerabilityEdge() + "/" + d.combatEdge() + ",decisive=" + d.decisiveChance() + ",success=" + d.roamSuccessChance() + ",outcome=" + d.outcome();
    }
    private static void print(Report r) {
        System.out.println("ROAM_DIAGNOSTIC " + r.label + " seeds=" + r.seeds);
        System.out.println(" evaluation mid(B/R)=" + r.midBlue + "/" + r.midRed
                + " support(B/R)=" + r.supportBlue + "/" + r.supportRed
                + " trigger(B/R)=" + r.triggerBlue + "/" + r.triggerRed
                + " multi=" + r.multi + " no-attempt-evaluation=" + (r.evaluations - r.attempts));
        System.out.println(" attempts B/R=" + r.attemptBlue + "/" + r.attemptRed + " total=" + r.attempts
                + " mid/support=" + r.midAttempts + "/" + r.supportAttempts + " unselected=" + r.unselected
                + " targets top/bot/mid=" + r.midTop + "/" + r.midBot + "/" + r.supportMid
                + " repeat/penalty=" + r.repeat + "/" + r.repeatPenalty);
        System.out.println(" outcomes no-kill/roam/reverse=" + r.noKill + "/" + r.roamKill + "/" + r.reverseKill
                + " kills B/R=" + r.killBlue + "/" + r.killRed + " edge/decisive/success="
                + r.average(r.edge) + "/" + r.average(r.decisive) + "/" + r.average(r.success));
        System.out.println(" participants top=" + r.participants.get(Lane.TOP) + " bot=" + r.participants.get(Lane.BOT)
                + " mid=" + r.participants.get(Lane.MID) + " missing-assist=" + r.missingAssist
                + " duplicate-assist=" + r.duplicateAssist + " wrong-side-assist=" + r.wrongSideAssist
                + " multi-death=" + r.multiDeath);
        System.out.println(" activity created/returned/death-clear=" + r.activityCreated + "/" + r.activityReturned + "/" + r.activityDeathClear
                + " stale-origin/target=" + r.staleOrigin + "/" + r.staleTarget + " combat-while-active=" + r.combatWhileActive
                + " same-tick-context=" + r.sameTickContext);
        System.out.println(" economy mid-blocked=" + r.midBlockedTicks + " mid-cs-10/14=" + r.midCs10 + "/" + r.midCs14
                + " mid-farm-gold-10/14=" + (r.midCs10 * PositionEconomyRuleConfig.CS_GOLD) + "/" + (r.midCs14 * PositionEconomyRuleConfig.CS_GOLD)
                + " support-cs=" + r.supportCs + " support-farm-gold=" + (r.supportCs * PositionEconomyRuleConfig.CS_GOLD)
                + " support-passive=" + r.supportPassiveGold + " duplicate-loss=" + r.duplicateFarmLoss);
        System.out.println(" pressure mid-origin/support-origin=" + r.midOriginCost + "/" + r.supportOriginCost
                + " sign-errors=" + r.pressureSignErrors + " no-kill-cost-missing=" + r.noKillCostMissing
                + " target-shock/reverse=" + r.targetShock + "/" + r.reverseShock);
        System.out.println(" sources=" + r.sources + " priority gank-skip/fallthrough/lane-block/generic-block="
                + r.gankSkip + "/" + r.roamFallthrough + "/" + r.laneBlocked + "/" + r.genericBlocked
                + " same-tick-major=" + r.sameTickMajor + " summary-kill-double-count=" + r.summaryKillDoubleCount);
        System.out.println(" finish blue/red=" + r.blueWins + "/" + r.redWins + " duration avg/median/p90/p95="
                + r.average(r.durations) + "/" + r.percentile(50) + "/" + r.percentile(90) + "/" + r.percentile(95)
                + " >=40/60=" + r.over40 + "/" + r.over60 + " timeout=" + r.timeout
                + " replay-mismatch=" + r.replayMismatch + " diagnostic-mismatch=" + r.diagnosticMismatch);
    }

    private enum Scenario {
        A, B, C, D, E, F, G;
        PlayerAttributes attributes(TeamSide side, Position position) {
            if (this == B && side == TeamSide.BLUE && position == Position.MID) return new PlayerAttributes(18, 18, 14, 18);
            if (this == B && side == TeamSide.RED && position == Position.MID) return new PlayerAttributes(10, 10, 14, 10);
            if (this == C && side == TeamSide.BLUE && position == Position.MID) return new PlayerAttributes(14, 18, 14, 14);
            if (this == C && side == TeamSide.RED && position == Position.MID) return new PlayerAttributes(14, 10, 14, 14);
            int value = value(side, position);
            return new PlayerAttributes(value, value, 14, value);
        }

        int value(TeamSide side, Position position) {
            return switch (this) {
                case A -> 14;
                case B -> side == TeamSide.BLUE && position == Position.MID ? 18 : side == TeamSide.RED && position == Position.MID ? 10 : 14;
                case D -> side == TeamSide.BLUE && position == Position.TOP ? 10 : side == TeamSide.RED && position == Position.TOP ? 18 : 14;
                case C -> 14;
                case E -> side == TeamSide.BLUE && (position == Position.ADC || position == Position.SUPPORT) ? 10
                        : side == TeamSide.RED && (position == Position.ADC || position == Position.SUPPORT) ? 18 : 14;
                case F -> side == TeamSide.BLUE && position == Position.SUPPORT ? 18 : side == TeamSide.RED && position == Position.SUPPORT ? 10 : 14;
                case G -> side == TeamSide.BLUE && (position == Position.SUPPORT || position == Position.MID) ? (position == Position.SUPPORT ? 18 : 10)
                        : side == TeamSide.RED && (position == Position.SUPPORT || position == Position.MID) ? (position == Position.SUPPORT ? 10 : 18) : 14;
            };
        }
    }

    private static final class Report {
        final String label; final int seeds; final List<Integer> durations = new ArrayList<>();
        final EnumMap<Lane, Integer> participants = new EnumMap<>(Lane.class);
        final EnumMap<CombatSource, Integer> sources = new EnumMap<>(CombatSource.class);
        final List<Double> edge = new ArrayList<>(), decisive = new ArrayList<>(), success = new ArrayList<>();
        int evaluations, midBlue, midRed, supportBlue, supportRed, triggerBlue, triggerRed, multi, attempts, attemptBlue, attemptRed, midAttempts, supportAttempts, unselected, midTop, midBot, supportMid, repeat, repeatPenalty, noKill, roamKill, reverseKill, killBlue, killRed, missingAssist, duplicateAssist, wrongSideAssist, multiDeath, activityCreated, activityReturned, activityDeathClear, staleOrigin, staleTarget, combatWhileActive, sameTickContext, midBlockedTicks, midCs10, midCs14, supportCs, supportPassiveGold, duplicateFarmLoss, midOriginCost, supportOriginCost, pressureSignErrors, noKillCostMissing, targetShock, reverseShock, gankSkip, roamFallthrough, laneBlocked, genericBlocked, sameTickMajor, summaryKillDoubleCount, blueWins, redWins, over40, over60, timeout, replayMismatch, diagnosticMismatch;

        Report(String label, int seeds) {
            this.label = label; this.seeds = seeds;
            for (Lane lane : Lane.values()) participants.put(lane, 0);
            for (CombatSource source : CombatSource.values()) sources.put(source, 0);
        }

        void add(MatchSimulator.SimulationResult result, long seed) {
            MatchTimeline timeline = result.timeline();
            RoamExecutionStatsSnapshot s = result.roamExecutionStats();
            evaluations += s.roamResolverEvaluations(); midBlue += s.midCandidateEvaluationsBlue(); midRed += s.midCandidateEvaluationsRed();
            supportBlue += s.supportCandidateEvaluationsBlue(); supportRed += s.supportCandidateEvaluationsRed(); triggerBlue += s.roamTriggersBlue(); triggerRed += s.roamTriggersRed();
            multi += s.multipleRoamTriggers(); attempts += s.actualRoamAttempts(); midAttempts += s.actualMidRoams(); supportAttempts += s.actualSupportRoams(); unselected += s.unselectedTriggeredCandidates();
            noKill += s.roamNoKill(); roamKill += s.roamingSideKills(); reverseKill += s.defendingSideKills(); activityCreated += s.activityCreated(); activityReturned += s.activityReturned(); activityDeathClear += s.activityClearedByDeath();
            gankSkip += s.roamSkippedByHigherPriorityActualCombat(); roamFallthrough += s.roamEvaluationFallthroughToLaneCombat(); laneBlocked += s.roamBlockedLaneCombat(); genericBlocked += s.roamBlockedGeneric();
            durations.add(timeline.getDurationSeconds()); if (timeline.getDurationSeconds() >= 2400) over40++; if (timeline.getDurationSeconds() >= 3600) over60++; if (result.endReason() == GameEndReason.SIMULATION_TIMEOUT) timeout++;
            if ("BLUE".equals(timeline.getWinner())) blueWins++; else if ("RED".equals(timeline.getWinner())) redWins++;
            Map<Integer, Integer> majorByTime = new HashMap<>();
            for (MatchEvent event : timeline.getEvents()) {
                if (event.getCombatSource() != null) sources.merge(event.getCombatSource(), 1, Integer::sum);
                if (event.getType() == MatchEventType.ROAM) addRoam(event.getRoam());
                if (isMajorSummary(event)) majorByTime.merge(event.getTimeSeconds(), 1, Integer::sum);
            }
            sameTickMajor += majorByTime.values().stream().filter(v -> v > 1).count();
            MatchSnapshot finalSnapshot = timeline.getSnapshots().getLast();
            for (PlayerSnapshot player : finalSnapshot.getPlayerSnapshots()) {
                if (player.getPosition() == Position.SUPPORT) { supportCs += player.getCs(); supportPassiveGold += Math.max(0, player.getGold() - 500); }
                if (player.getPosition() == Position.MID) {
                    midCs14 += player.getCs(); if (finalSnapshot.getTimeSeconds() >= 600) midCs10 += player.getCs();
                }
                if (player.getActivityType() == PlayerActivityType.ROAMING && player.getActivityOriginLane() == null) staleOrigin++;
                if (player.getActivityType() == PlayerActivityType.ROAMING && player.getActivityTargetLane() == null) staleTarget++;
            }
        }

        void addRoam(RoamData data) {
            if (data == null) return;
            if (data.roamingSide() == TeamSide.BLUE) attemptBlue++; else attemptRed++;
            if (data.roamerPosition() == Position.MID && data.targetLane() == Lane.TOP) midTop++;
            if (data.roamerPosition() == Position.MID && data.targetLane() == Lane.BOT) midBot++;
            if (data.roamerPosition() == Position.SUPPORT && data.targetLane() == Lane.MID) supportMid++;
            if (data.repeatTarget()) repeat++; if (data.repeatPenaltyApplied()) repeatPenalty++;
            edge.add(data.combatEdge()); decisive.add(data.decisiveChance()); success.add(data.roamSuccessChance());
            if (data.outcome() == RoamOutcome.NO_KILL) {
                if (data.originPressureBefore() == data.originPressureAfter()) noKillCostMissing++;
                if (data.targetPressureBefore() != data.targetPressureAfter()) pressureSignErrors++;
            } else {
            if (data.roamerPosition() == Position.MID) midBlockedTicks += 2;
                if (data.winningSide() == TeamSide.BLUE) killBlue++; else killRed++;
                if (data.winningSide() == data.roamingSide()) targetShock++; else reverseShock++;
            }
            if (data.roamerPosition() == Position.MID) midOriginCost += Math.round(Math.abs(data.originPressureAfter() - data.originPressureBefore()));
            else supportOriginCost += Math.round(Math.abs(data.originPressureAfter() - data.originPressureBefore()));
            participants.merge(data.targetLane(), data.assistantPlayerIds().size() + 2, Integer::sum);
            if (data.outcome() != RoamOutcome.NO_KILL && data.assistantPlayerIds().isEmpty() && data.targetLane() == Lane.BOT) missingAssist++;
            if (data.assistantPlayerIds().size() != data.assistantPlayerIds().stream().distinct().count()) duplicateAssist++;
        }

        boolean isMajorSummary(MatchEvent e) { return e.getType() == MatchEventType.ROAM || e.getType() == MatchEventType.JUNGLE_GANK || e.getType() == MatchEventType.COUNTER_GANK || e.getType() == MatchEventType.LANE_COMBAT || e.getType() == MatchEventType.TEAMFIGHT || e.getCombatSource() == CombatSource.SKIRMISH; }
        String average(List<? extends Number> values) { return values.isEmpty() ? "0" : String.format("%.2f", values.stream().mapToDouble(Number::doubleValue).average().orElse(0)); }
        int percentile(int p) { if (durations.isEmpty()) return 0; List<Integer> sorted = durations.stream().sorted(Comparator.naturalOrder()).toList(); return sorted.get(Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * p / 100.0) - 1))); }
    }
}

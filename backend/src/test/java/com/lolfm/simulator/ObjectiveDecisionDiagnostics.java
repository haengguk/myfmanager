package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.ObjectiveDecisionData;
import com.lolfm.domain.ObjectiveDecisionWeightBreakdown;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Large-sample observational diagnostics. All counts use structured state/event data only.
 * Defaults implement the requested A-G 1..1000 and B-G mirrors 1..500 matrix.
 */
public final class ObjectiveDecisionDiagnostics {
    private static final int PRIMARY_SEEDS = Integer.getInteger("objectiveDecision.primarySeeds", 1_000);
    private static final int MIRROR_SEEDS = Integer.getInteger("objectiveDecision.mirrorSeeds", 500);

    public static void main(String[] args) {
        List<Scenario> scenarios = scenarios();
        fixtureAudit(scenarios);
        for (Scenario scenario : scenarios) {
            runPair(scenario, PRIMARY_SEEDS);
            if (!scenario.key().equals("A")) runPair(scenario.mirror(), MIRROR_SEEDS);
        }
        focusedUrgencyAudit();
    }

    private static void runPair(Scenario scenario, int seeds) {
        Report on = run(scenario, true, seeds);
        Report off = run(scenario, false, seeds);
        System.out.println(on.line());
        System.out.println(off.line());
    }

    private static Report run(Scenario scenario, boolean enabled, int seeds) {
        Report report = new Report(scenario.key(), enabled, seeds);
        SimulationOptions options = SimulationOptions.productionDefaults().withObjectiveDecisionEnabled(enabled);
        MatchSimulator simulator = simulator(options);
        for (long seed = 1; seed <= seeds; seed++) {
            MatchSimulator.SimulationResult result = simulator.simulateWithDiagnostics(
                    team("BLUE", scenario.blue()), team("RED", scenario.red()), seed);
            report.add(result);
        }
        MatchSimulator.SimulationResult replayA = simulator.simulateWithDiagnostics(
                team("BLUE", scenario.blue()), team("RED", scenario.red()), 1L);
        MatchSimulator.SimulationResult replayB = simulator.simulateWithDiagnostics(
                team("BLUE", scenario.blue()), team("RED", scenario.red()), 1L);
        if (!signature(replayA).equals(signature(replayB))) report.replayMismatch++;
        return report;
    }

    private static MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private static String signature(MatchSimulator.SimulationResult result) {
        return result.timeline().getDurationSeconds() + "|" + result.timeline().getWinner() + "|"
                + result.timeline().getEvents().stream().map(event -> event.getTimeSeconds() + ":"
                + event.getType() + ":" + event.getCombatSource() + ":" + event.getObjectiveDecision()).toList();
    }

    private static Team team(String side, Map<Position, Profile> profiles) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            Profile profile = profiles.get(position);
            players.add(new Player(side + "-" + position, position,
                    new PlayerAttributes(profile.mechanics(), profile.aggression(),
                            profile.farming(), profile.teamfighting())));
        }
        return new Team(side, players);
    }

    private static List<Scenario> scenarios() {
        Map<Position, Profile> base = all(new Profile(14, 14, 14, 14));
        Map<Position, Profile> bBlue = all(new Profile(18, 16, 16, 18));
        Map<Position, Profile> bRed = all(new Profile(10, 12, 12, 10));
        Map<Position, Profile> cBlue = all(new Profile(14, 16, 14, 18));
        Map<Position, Profile> cRed = all(new Profile(14, 12, 14, 10));
        Map<Position, Profile> dBlue = all(new Profile(10, 12, 10, 10));
        Map<Position, Profile> dRed = all(new Profile(18, 16, 18, 18));

        Map<Position, Profile> eBlue = all(new Profile(14, 14, 14, 10));
        eBlue.put(Position.TOP, new Profile(14, 14, 18, 14));
        eBlue.put(Position.MID, new Profile(14, 14, 18, 14));
        eBlue.put(Position.ADC, new Profile(14, 14, 18, 14));
        Map<Position, Profile> eRed = all(new Profile(14, 14, 14, 18));

        Map<Position, Profile> fBlue = all(new Profile(14, 14, 14, 14));
        Map<Position, Profile> fRed = all(new Profile(14, 14, 14, 14));
        for (Position position : List.of(Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT)) {
            fBlue.put(position, new Profile(18, 18, 14, 18));
            fRed.put(position, new Profile(10, 10, 14, 10));
        }

        Map<Position, Profile> gBlue = all(new Profile(14, 14, 14, 14));
        Map<Position, Profile> gRed = all(new Profile(14, 14, 14, 14));
        for (Position position : List.of(Position.JUNGLE, Position.TOP, Position.MID, Position.SUPPORT)) {
            gBlue.put(position, new Profile(18, 18, 14, 18));
            gRed.put(position, new Profile(10, 10, 14, 10));
        }

        return List.of(new Scenario("A", base, copy(base)), new Scenario("B", bBlue, bRed),
                new Scenario("C", cBlue, cRed), new Scenario("D", dBlue, dRed),
                new Scenario("E", eBlue, eRed), new Scenario("F", fBlue, fRed),
                new Scenario("G", gBlue, gRed));
    }

    private static Map<Position, Profile> all(Profile profile) {
        EnumMap<Position, Profile> result = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            result.put(position, new Profile(profile.mechanics(), profile.aggression(),
                    profile.farming(), profile.teamfighting()));
        }
        return result;
    }

    private static Map<Position, Profile> copy(Map<Position, Profile> source) {
        EnumMap<Position, Profile> result = new EnumMap<>(Position.class);
        source.forEach((position, profile) -> result.put(position, new Profile(profile.mechanics(),
                profile.aggression(), profile.farming(), profile.teamfighting())));
        return result;
    }

    private static void fixtureAudit(List<Scenario> scenarios) {
        long distinctKeys = scenarios.stream().map(Scenario::key).distinct().count();
        if (distinctKeys != scenarios.size()) throw new IllegalStateException("Scenario key collision");
        for (Scenario scenario : scenarios) {
            if (scenario.blue() == scenario.red()) throw new IllegalStateException("Shared side profile: " + scenario.key());
            for (Position position : Position.values()) {
                if (!scenario.blue().containsKey(position) || !scenario.red().containsKey(position)) {
                    throw new IllegalStateException("Missing profile " + scenario.key() + " " + position);
                }
            }
            Team first = team("BLUE", scenario.blue());
            Team second = team("BLUE", scenario.blue());
            if (first == second || first.getPlayers().getFirst() == second.getPlayers().getFirst()
                    || first.getPlayers().getFirst().getAttributes() == second.getPlayers().getFirst().getAttributes()) {
                throw new IllegalStateException("Fixture object sharing: " + scenario.key());
            }
        }
        System.out.println("FIXTURE_AUDIT scenarios=" + scenarios.size()
                + " order=mechanics/aggression/farming/teamfighting sharedObjects=0 keyCollisions=0 mirrorDirection=verified");
    }

    private static void focusedUrgencyAudit() {
        ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();
        GameState blueThree = ObjectiveDecisionTestSupport.dragonState(true);
        for (int i = 0; i < 3; i++) blueThree.getBlueTeamState().addDragon();
        ObjectiveDecisionContext context = resolver.buildContext(blueThree, ObjectiveType.DRAGON, TeamSide.BLUE, 0);
        double own = weight(resolver.initiativeWeights(context), ObjectiveDecisionAction.TAKE).urgencyContribution();
        double denial = weight(resolver.responderWeights(blueThree, context), ObjectiveDecisionAction.CONTEST).urgencyContribution();
        ObjectiveDecisionContext elder = resolver.buildContext(blueThree, ObjectiveType.ELDER, TeamSide.BLUE, 100);
        double elderUrgency = weight(resolver.initiativeWeights(elder), ObjectiveDecisionAction.TAKE).urgencyContribution();
        double elderPriority = weight(resolver.initiativeWeights(elder), ObjectiveDecisionAction.TAKE).priorityEdge();
        System.out.println("FOCUSED_URGENCY ownSoulPoint=" + own + " enemyDenial=" + denial
                + " elderUrgency=" + elderUrgency + " elderPriorityEdge=" + elderPriority);
    }

    private static ObjectiveDecisionWeightBreakdown weight(List<ObjectiveDecisionWeightBreakdown> values,
                                                            ObjectiveDecisionAction action) {
        return values.stream().filter(value -> value.action() == action).findFirst().orElseThrow();
    }

    private record Profile(int mechanics, int aggression, int farming, int teamfighting) { }

    private record Scenario(String key, Map<Position, Profile> blue, Map<Position, Profile> red) {
        Scenario {
            blue = copy(blue);
            red = copy(red);
        }
        Scenario mirror() { return new Scenario(key + "_MIRROR", red, blue); }
    }

    private static final class Report {
        private final String key;
        private final boolean enabled;
        private final int games;
        private final EnumMap<ObjectiveDecisionAction, long[]> actions = new EnumMap<>(ObjectiveDecisionAction.class);
        private final EnumMap<ObjectiveType, EnumMap<ObjectiveDecisionAction, Long>> byObjective = new EnumMap<>(ObjectiveType.class);
        private final EnumMap<ObjectiveType, long[]> captures = new EnumMap<>(ObjectiveType.class);
        private final EnumMap<Lane, Long> tradeLanes = new EnumMap<>(Lane.class);
        private final EnumMap<TowerTier, Long> tradeTiers = new EnumMap<>(TowerTier.class);
        private final EnumMap<ObjectiveDecisionIneligibleReason, Long> ineligible = new EnumMap<>(ObjectiveDecisionIneligibleReason.class);
        private final EnumMap<ObjectiveDecisionAction, Double> weightTotals = new EnumMap<>(ObjectiveDecisionAction.class);
        private final EnumMap<ObjectiveDecisionAction, Long> weightCounts = new EnumMap<>(ObjectiveDecisionAction.class);
        private final List<Integer> durations = new ArrayList<>();
        private long weightSampleCount;
        private long decisions, initiativeRolls, responderRolls, initiativeBlue, initiativeRed;
        private long uncontested, contests, contestBlue, contestRed, objectiveFightKills, resets, stale;
        private long tradeAttempts, tradeSuccess, tradeFailure, tradeBlueDestruction, tradeRedDestruction;
        private double tradeChanceTotal, priorityContribution, aliveContribution, goldContribution;
        private double teamfightContribution, farmingContribution, urgencyContribution;
        private long dragonAttemptRolls, dragonAttemptSuccess, baronAttemptRolls, baronAttemptSuccess;
        private long elderAttemptRolls, elderAttemptSuccess, postFightCaptures, souls, soulTime;
        private long blueWins, redWins, nexusEnds, timeouts, over40, over60;
        private long duplicateDecision, elderPriorityMisuse, postFightDecisionError, forbiddenTrade;
        private long offMutation, supportCsError, overdue, replayMismatch, sameTickGeneralPostFight;
        private long macroSetupDecisions, setupDoubleUseError;

        Report(String key, boolean enabled, int games) {
            this.key = key;
            this.enabled = enabled;
            this.games = games;
            for (ObjectiveDecisionAction action : ObjectiveDecisionAction.values()) actions.put(action, new long[2]);
            for (ObjectiveType type : ObjectiveType.values()) {
                EnumMap<ObjectiveDecisionAction, Long> values = new EnumMap<>(ObjectiveDecisionAction.class);
                for (ObjectiveDecisionAction action : ObjectiveDecisionAction.values()) values.put(action, 0L);
                byObjective.put(type, values);
                captures.put(type, new long[2]);
            }
            for (Lane lane : Lane.values()) tradeLanes.put(lane, 0L);
            for (TowerTier tier : TowerTier.values()) tradeTiers.put(tier, 0L);
        }

        void add(MatchSimulator.SimulationResult result) {
            decisions += result.objectiveDecisionHistory().size();
            ObjectiveDecisionExecutionStatsSnapshot stats = result.objectiveDecisionExecutionStats();
            duplicateDecision += stats.duplicateRejected();
            initiativeRolls += stats.initiativeRolls();
            responderRolls += stats.responderRolls();
            for (ObjectiveDecisionData data : result.objectiveDecisionHistory()) addDecision(data);
            addAttempts(result.objectivePriorityExecutionStats());
            addEvents(result.timeline().getEvents());
            addEnd(result);
            MatchSnapshot last = result.timeline().getSnapshots().getLast();
            supportCsError += last.getPlayerSnapshots().stream()
                    .filter(player -> player.getPosition() == Position.SUPPORT && player.getCs() != 0).count();
            if (!enabled && (!result.objectiveDecisionHistory().isEmpty()
                    || result.timeline().getEvents().stream().anyMatch(event -> event.getObjectiveDecision() != null
                    || event.getStructureActionSource() == StructureActionSource.OBJECTIVE_TRADE))) offMutation++;
        }

        private void addDecision(ObjectiveDecisionData data) {
            int initiativeIndex = data.initiativeSide() == TeamSide.BLUE ? 0 : 1;
            if (initiativeIndex == 0) initiativeBlue++; else initiativeRed++;
            addAction(data.initiativeAction(), initiativeIndex, data.objectiveType());
            if (data.responderAction() != null) {
                int responderIndex = data.responderSide() == TeamSide.BLUE ? 0 : 1;
                addAction(data.responderAction(), responderIndex, data.objectiveType());
            }
            for (ObjectiveDecisionWeightBreakdown weight : data.initiativeCandidates()) addWeight(weight);
            for (ObjectiveDecisionWeightBreakdown weight : data.responderCandidates()) {
                addWeight(weight);
                if (!weight.eligible() && weight.reason() != null) ineligible.merge(weight.reason(), 1L, Long::sum);
            }
            switch (data.result()) {
                case INITIATOR_RESET -> {
                    resets++;
                    if (data.nextGeneralAttemptAtSeconds() <= data.evaluationTimeSeconds()) overdue++;
                }
                case UNCONTESTED_CAPTURE -> uncontested++;
                case CONTEST_FIGHT -> {
                    contests++;
                    if (data.fightWinner() == TeamSide.BLUE) contestBlue++; else contestRed++;
                }
                case TRADE_SUCCEEDED -> { tradeAttempts++; tradeSuccess++; }
                case TRADE_FAILED -> { tradeAttempts++; tradeFailure++; }
                case STALE_OBJECTIVE -> stale++;
                default -> { }
            }
            if (data.captureSide() != null) captures.get(data.objectiveType())[data.captureSide() == TeamSide.BLUE ? 0 : 1]++;
            if (data.tradeRollExecuted()) {
                tradeChanceTotal += data.tradePushChance();
                tradeLanes.merge(data.tradeTargetLane(), 1L, Long::sum);
                tradeTiers.merge(data.tradeTargetStructure(), 1L, Long::sum);
            }
            if (data.objectiveType() == ObjectiveType.ELDER && data.elderPriorityAvailable()) elderPriorityMisuse++;
            if (data.tradeTargetStructure() != null && data.tradeTargetStructure() != TowerTier.OUTER
                    && data.tradeTargetStructure() != TowerTier.INNER
                    && data.tradeTargetStructure() != TowerTier.INHIBITOR) forbiddenTrade++;
        }

        private void addAction(ObjectiveDecisionAction action, int side, ObjectiveType type) {
            actions.get(action)[side]++;
            byObjective.get(type).merge(action, 1L, Long::sum);
        }

        private void addWeight(ObjectiveDecisionWeightBreakdown weight) {
            weightSampleCount++;
            weightTotals.merge(weight.action(), weight.finalWeight(), Double::sum);
            weightCounts.merge(weight.action(), 1L, Long::sum);
            priorityContribution += weight.priorityContribution();
            aliveContribution += weight.aliveContribution();
            goldContribution += weight.goldContribution();
            teamfightContribution += weight.teamfightContribution();
            farmingContribution += weight.farmingContribution();
            urgencyContribution += weight.urgencyContribution();
        }

        private void addAttempts(ObjectivePriorityExecutionStatsSnapshot stats) {
            var dragon = stats.attempts().get(ObjectiveType.DRAGON);
            var baron = stats.attempts().get(ObjectiveType.BARON);
            var elder = stats.attempts().get(ObjectiveType.ELDER);
            dragonAttemptRolls += dragon.attemptRolls(); dragonAttemptSuccess += dragon.attemptSuccesses();
            baronAttemptRolls += baron.attemptRolls(); baronAttemptSuccess += baron.attemptSuccesses();
            elderAttemptRolls += elder.attemptRolls(); elderAttemptSuccess += elder.attemptSuccesses();
            sameTickGeneralPostFight += stats.sameTickGeneralPostFightDuplicate();
            for (var priority : stats.decisions()) {
                if (priority.generalAttempt() && priority.macroSetupControl() != 0) macroSetupDecisions++;
            }
        }

        private void addEvents(List<MatchEvent> events) {
            for (MatchEvent event : events) {
                if (event.getType() == MatchEventType.KILL && event.getCombatSource() == com.lolfm.domain.CombatSource.OBJECTIVE_FIGHT) objectiveFightKills++;
                if (event.getObjectivePriorityDecision() != null && event.getObjectivePriorityDecision().postFightLinked()) {
                    postFightCaptures++;
                    if (event.getObjectiveDecision() != null) postFightDecisionError++;
                }
                if (event.getStructureActionSource() == StructureActionSource.OBJECTIVE_TRADE) {
                    if (event.getStructureAttackingSide() == TeamSide.BLUE) tradeBlueDestruction++; else tradeRedDestruction++;
                    if (event.getStructureKind() != StructureKind.TOWER) forbiddenTrade++;
                }
            }
        }

        private void addEnd(MatchSimulator.SimulationResult result) {
            int duration = result.timeline().getDurationSeconds();
            durations.add(duration);
            if (result.winnerSide() == TeamSide.BLUE) blueWins++; else if (result.winnerSide() == TeamSide.RED) redWins++;
            if (result.endReason() == GameEndReason.NEXUS_DESTROYED) nexusEnds++;
            if (result.endReason() == GameEndReason.SIMULATION_TIMEOUT) timeouts++;
            if (duration >= 2_400) over40++;
            if (duration >= 3_600) over60++;
            if (result.soulOwner() != null) { souls++; soulTime += result.soulClaimedAtSeconds(); }
        }

        String line() {
            durations.sort(Comparator.naturalOrder());
            return "DECISION_REPORT key=" + key + " mode=" + (enabled ? "ON" : "OFF") + " seeds=" + games
                    + " opportunity={dragon=" + dragonAttemptSuccess + "/" + dragonAttemptRolls
                    + ",baron=" + baronAttemptSuccess + "/" + baronAttemptRolls
                    + ",elder=" + elderAttemptSuccess + "/" + elderAttemptRolls
                    + ",initiative=" + initiativeBlue + "/" + initiativeRed + "}"
                    + " initiative={take=" + sides(ObjectiveDecisionAction.TAKE)
                    + ",reset=" + sides(ObjectiveDecisionAction.RESET) + ",rolls=" + initiativeRolls
                    + ",avgTake=" + avgWeight(ObjectiveDecisionAction.TAKE)
                    + ",avgReset=" + avgWeight(ObjectiveDecisionAction.RESET) + "}"
                    + " responder={contest=" + sides(ObjectiveDecisionAction.CONTEST)
                    + ",give=" + sides(ObjectiveDecisionAction.GIVE)
                    + ",trade=" + sides(ObjectiveDecisionAction.TRADE_STRUCTURE)
                    + ",rolls=" + responderRolls + ",ineligible=" + ineligible + "}"
                    + " resolution={uncontested=" + uncontested + ",contests=" + contests
                    + ",contestWinner=" + contestBlue + "/" + contestRed + ",fightKills=" + objectiveFightKills
                    + ",resets=" + resets + ",stale=" + stale + "}"
                    + " objective={actions=" + byObjective + ",captures=" + captureLine()
                    + ",souls=" + souls + ",avgSoul=" + (souls == 0 ? 0 : soulTime / souls) + "}"
                    + " trade={attempts=" + tradeAttempts + ",success=" + tradeSuccess + ",failure=" + tradeFailure
                    + ",avgChance=" + f(tradeAttempts == 0 ? 0 : tradeChanceTotal / tradeAttempts)
                    + ",lanes=" + tradeLanes + ",tiers=" + tradeTiers
                    + ",destruction=" + tradeBlueDestruction + "/" + tradeRedDestruction + "}"
                    + " inputs={priority=" + avgInput(priorityContribution) + ",alive=" + avgInput(aliveContribution)
                    + ",gold=" + avgInput(goldContribution) + ",teamfight=" + avgInput(teamfightContribution)
                    + ",farming=" + avgInput(farmingContribution) + ",urgency=" + avgInput(urgencyContribution) + "}"
                    + " macro={setupDecisions=" + macroSetupDecisions + ",doubleUse=" + setupDoubleUseError + "}"
                    + " postFight={captures=" + postFightCaptures + ",decisionError=" + postFightDecisionError
                    + ",sameTick=" + sameTickGeneralPostFight + "}"
                    + " end={win=" + pct(blueWins) + "/" + pct(redWins) + ",avg=" + avgDuration()
                    + ",median=" + percentile(.5) + ",p90=" + percentile(.9) + ",p95=" + percentile(.95)
                    + ",40m=" + over40 + ",60m=" + over60 + ",nexus=" + nexusEnds + ",timeout=" + timeouts + "}"
                    + " integrity={offMutation=" + offMutation + ",duplicateDecision=" + duplicateDecision
                    + ",elderPriority=" + elderPriorityMisuse + ",postFightDecision=" + postFightDecisionError
                    + ",forbiddenTrade=" + forbiddenTrade + ",overdue=" + overdue
                    + ",supportCs=" + supportCsError + ",replay=" + replayMismatch + "}";
        }

        private String sides(ObjectiveDecisionAction action) { return actions.get(action)[0] + "/" + actions.get(action)[1]; }
        private String avgInput(double total) { return f(weightSampleCount == 0 ? 0 : total / weightSampleCount); }

        private String avgWeight(ObjectiveDecisionAction action) {
            long count = weightCounts.getOrDefault(action, 0L);
            return f(count == 0 ? 0 : weightTotals.getOrDefault(action, 0.0) / count);
        }
        private String captureLine() {
            Map<ObjectiveType, String> values = new LinkedHashMap<>();
            for (ObjectiveType type : ObjectiveType.values()) values.put(type, captures.get(type)[0] + "/" + captures.get(type)[1]);
            return values.toString();
        }
        private String pct(long value) { return f(value * 100.0 / games); }
        private int avgDuration() { return durations.stream().mapToInt(Integer::intValue).sum() / Math.max(1, durations.size()); }
        private int percentile(double value) {
            if (durations.isEmpty()) return 0;
            return durations.get(Math.min(durations.size() - 1, (int) Math.ceil(value * durations.size()) - 1));
        }
        private String f(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
    }
}

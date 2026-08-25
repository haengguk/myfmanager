package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.*;
import com.lolfm.factory.DummyDataFactory;
import java.util.*;
import org.junit.jupiter.api.Test;

class MidGameMacroDeterministicAuditTest {
    @Test
    void planEligibilityAndWeightComponentsAreDeterministic() {
        GameState state = stateAt(870);
        state.getBlueTeamState().addGold(2_500);
        state.getBlueTeamState().addDragon();
        state.getBlueTeamState().addDragon();
        state.getBlueTeamState().addDragon();
        List<MacroPlanWeightBreakdown> values = resolver().inspectCandidates(state, TeamSide.BLUE);
        MacroPlanWeightBreakdown group = weight(values, TeamMacroPlan.GROUP_MID);
        assertEquals(MidGameMacroRuleConfig.GROUP_MID_BASE_WEIGHT, group.baseWeight());
        assertEquals(group.goldEdge() * MidGameMacroRuleConfig.GOLD_EDGE_WEIGHT, group.goldContribution(), 1e-9);
        assertEquals(group.attributeEdge() * MidGameMacroRuleConfig.TEAMFIGHT_EDGE_WEIGHT,
                group.attributeContribution(), 1e-9);
        MacroPlanWeightBreakdown top = weight(values, TeamMacroPlan.SIDE_LANE_TOP);
        assertEquals(top.attributeEdge() * MidGameMacroRuleConfig.SIDE_FARMING_EDGE_WEIGHT,
                top.attributeContribution(), 1e-9);
        MacroPlanWeightBreakdown dragon = weight(values, TeamMacroPlan.OBJECTIVE_SETUP_DRAGON);
        assertEquals(MidGameMacroRuleConfig.DRAGON_SOUL_POINT_WEIGHT_BONUS, dragon.soulPointBonus());
        assertEquals(dragon.objectivePriorityEdge() * MidGameMacroRuleConfig.OBJECTIVE_PRIORITY_WEIGHT,
                dragon.objectiveContribution(), 1e-9);
        assertTrue(weight(values, TeamMacroPlan.RESET_AND_FARM).eligible());

        killAll(state.getBlueTeamState(), 870);
        values = resolver().inspectCandidates(state, TeamSide.BLUE);
        assertFalse(weight(values, TeamMacroPlan.GROUP_MID).eligible());
        assertEquals("INSUFFICIENT_PARTICIPANTS", weight(values, TeamMacroPlan.GROUP_MID).ineligibleReason());
        assertFalse(weight(values, TeamMacroPlan.SIDE_LANE_TOP).eligible());
        assertEquals(0.0, weight(values, TeamMacroPlan.SIDE_LANE_TOP).finalWeight());
        assertTrue(weight(values, TeamMacroPlan.RESET_AND_FARM).eligible());
    }

    @Test
    void repeatMultiplierAndBlueRedSymmetryHold() {
        GameState equal = stateAt(870);
        List<MacroPlanWeightBreakdown> blue = resolver().inspectCandidates(equal, TeamSide.BLUE);
        List<MacroPlanWeightBreakdown> red = resolver().inspectCandidates(equal, TeamSide.RED);
        for (TeamMacroPlan plan : TeamMacroPlan.values()) {
            assertEquals(weight(blue, plan).eligible(), weight(red, plan).eligible(), plan.name());
            assertEquals(weight(blue, plan).finalWeight(), weight(red, plan).finalWeight(), 1e-9, plan.name());
        }
        TeamMacroTeamState team = equal.getMidGameMacroState().teamState(TeamSide.BLUE);
        team.beginPlan(TeamMacroPlan.SIDE_LANE_TOP, Lane.TOP, null, EnumSet.of(Position.TOP), 870);
        List<MacroPlanWeightBreakdown> repeated = resolver().inspectCandidates(equal, TeamSide.BLUE);
        assertEquals(MidGameMacroRuleConfig.SAME_PLAN_REPEAT_MULTIPLIER,
                weight(repeated, TeamMacroPlan.SIDE_LANE_TOP).repeatMultiplier());
        assertEquals(1.0, weight(repeated, TeamMacroPlan.SIDE_LANE_BOT).repeatMultiplier());
        assertEquals(1.0, weight(repeated, TeamMacroPlan.RESET_AND_FARM).repeatMultiplier());
    }

    @Test
    void blueAndRedSelectionRandomOrderIsStable() {
        GameState reset = stateAt(870);
        killAll(reset.getBlueTeamState(), 870);
        killAll(reset.getRedTeamState(), 870);
        SequenceRandom none = new SequenceRandom();
        resolver().resolveDueEvaluation(reset, none, new ArrayList<>(), new StructureResolver());
        assertEquals(0, none.calls);
        assertEquals(0, reset.getMidGameMacroState().getEvaluationHistory().getFirst()
                .selectionRandomConsumptionCount());

        GameState normal = stateAt(870);
        SequenceRandom ordered = new SequenceRandom(0.11, 0.89, 1.0, 1.0);
        resolver().resolveDueEvaluation(normal, ordered, new ArrayList<>(), new StructureResolver());
        var record = normal.getMidGameMacroState().getEvaluationHistory().getFirst();
        assertEquals(0.11, record.blueDecision().selectionRoll());
        assertEquals(0.89, record.redDecision().selectionRoll());
        assertEquals(2, record.selectionRandomConsumptionCount());
        assertEquals(List.of(0.11, 0.89), ordered.used.subList(0, 2));
    }

    @Test
    void bothSidesSelectPlansFromSamePreActionSnapshot() {
        GameState state = stateAt(870);
        MidGameMacroResolver resolver = resolver();
        List<MacroPlanWeightBreakdown> blue = resolver.inspectCandidates(state, TeamSide.BLUE);
        List<MacroPlanWeightBreakdown> red = resolver.inspectCandidates(state, TeamSide.RED);
        List<MatchEvent> events = new ArrayList<>();
        resolver.resolveDueEvaluation(state, new SequenceRandom(
                rollFor(blue, TeamMacroPlan.GROUP_MID), rollFor(red, TeamMacroPlan.GROUP_MID), 0, 0),
                events, new StructureResolver());
        var audit = state.getMidGameMacroState().getEvaluationHistory().getFirst();
        assertEquals(blue, audit.blueDecision().candidates());
        assertEquals(red, audit.redDecision().candidates());
        assertEquals(TeamMacroPlan.GROUP_MID, audit.blueDecision().selectedPlan());
        assertEquals(TeamMacroPlan.GROUP_MID, audit.redDecision().selectedPlan());
        assertTrue(state.getMidGameMacroState().teamState(TeamSide.BLUE).isActiveAt(870));
        assertTrue(state.getMidGameMacroState().teamState(TeamSide.RED).isActiveAt(870));
    }

    @Test
    void blueActionDoesNotChangeRedCandidateWeights() {
        GameState state = stateAt(870);
        MidGameMacroResolver resolver = resolver();
        List<MacroPlanWeightBreakdown> blue = resolver.inspectCandidates(state, TeamSide.BLUE);
        List<MacroPlanWeightBreakdown> redBeforeBlueAction = resolver.inspectCandidates(state, TeamSide.RED);
        resolver.resolveDueEvaluation(state, new SequenceRandom(
                rollFor(blue, TeamMacroPlan.GROUP_MID),
                rollFor(redBeforeBlueAction, TeamMacroPlan.GROUP_MID), 0, 0),
                new ArrayList<>(), new StructureResolver());
        assertEquals(redBeforeBlueAction, state.getMidGameMacroState().getEvaluationHistory().getFirst()
                .redDecision().candidates());
    }

    @Test
    void bothSidesCanHoldActivePlansAtTheSameTime() {
        GameState state = stateAt(870);
        MidGameMacroResolver resolver = resolver();
        List<MacroPlanWeightBreakdown> blue = resolver.inspectCandidates(state, TeamSide.BLUE);
        List<MacroPlanWeightBreakdown> red = resolver.inspectCandidates(state, TeamSide.RED);
        resolver.resolveDueEvaluation(state, new SequenceRandom(
                rollFor(blue, TeamMacroPlan.GROUP_MID), rollFor(red, TeamMacroPlan.SIDE_LANE_TOP), 1, 1),
                new ArrayList<>(), new StructureResolver());
        assertTrue(state.getMidGameMacroState().teamState(TeamSide.BLUE).isActiveAt(870));
        assertTrue(state.getMidGameMacroState().teamState(TeamSide.RED).isActiveAt(870));
    }

    @Test
    void structureActionLimitIsPerTeamSideNotGlobal() {
        GameState both = stateAt(870);
        List<MacroPlanWeightBreakdown> blue = resolver().inspectCandidates(both, TeamSide.BLUE);
        List<MacroPlanWeightBreakdown> red = resolver().inspectCandidates(both, TeamSide.RED);
        List<MatchEvent> events = new ArrayList<>();
        resolver().resolveDueEvaluation(both, new SequenceRandom(
                rollFor(blue, TeamMacroPlan.GROUP_MID), rollFor(red, TeamMacroPlan.GROUP_MID), 0, 0),
                events, new StructureResolver());
        List<MatchEvent> towers = events.stream()
                .filter(e -> e.getStructureActionSource() == StructureActionSource.MID_GAME_MACRO).toList();
        assertEquals(2, towers.size());
        assertEquals(1, towers.stream().filter(e -> e.getStructureAttackingSide() == TeamSide.BLUE).count());
        assertEquals(1, towers.stream().filter(e -> e.getStructureAttackingSide() == TeamSide.RED).count());

        GameState blocked = stateAt(870);
        blocked.markStructureActionPerformed(TeamSide.BLUE);
        List<MatchEvent> blockedEvents = new ArrayList<>();
        resolver().resolveDueEvaluation(blocked, new SequenceRandom(0, 0, 0, 0),
                blockedEvents, new StructureResolver());
        assertTrue(blockedEvents.stream().noneMatch(event -> event.getStructureAttackingSide() == TeamSide.BLUE));
        assertEquals(1, blocked.getMidGameMacroState().getExecutionStats().snapshot()
                .existingStructureActionBlocked());
    }

    @Test
    void positionAssignmentsExcludeDeadAndCombatParticipants() {
        assertEquals(EnumSet.of(Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT),
                decisionFor(TeamMacroPlan.GROUP_MID, 870).assignedPositions());
        assertEquals(EnumSet.of(Position.TOP), decisionFor(TeamMacroPlan.SIDE_LANE_TOP, 870).assignedPositions());
        assertEquals(EnumSet.of(Position.ADC), decisionFor(TeamMacroPlan.SIDE_LANE_BOT, 870).assignedPositions());
        assertEquals(EnumSet.of(Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT),
                decisionFor(TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, 870).assignedPositions());
        assertEquals(EnumSet.of(Position.JUNGLE, Position.TOP, Position.MID, Position.SUPPORT),
                decisionFor(TeamMacroPlan.OBJECTIVE_SETUP_BARON, 1200).assignedPositions());

        GameState state = stateAt(870);
        state.markMajorCombatParticipant(state.getBlueTeamState().playerAt(Position.MID));
        MidGameMacroDecisionData decision = resolveSelected(
                state, TeamSide.BLUE, TeamMacroPlan.GROUP_MID, 1).decision();
        assertFalse(decision.assignedPositions().contains(Position.MID));
        assertTrue(decision.assignedPositions().size() >= 3);
    }

    @Test
    void towerOrderIsOuterInnerInhibitorTowerAndBaseTargetsAreForbidden() {
        assertEquals(TowerTier.OUTER, destroyedTier(0));
        assertEquals(TowerTier.INNER, destroyedTier(1));
        assertEquals(TowerTier.INHIBITOR, destroyedTier(2));
        GameState state = stateAt(870);
        LaneStructureState lane = state.getMapState().getLaneState(TeamSide.RED, Lane.MID);
        lane.destroy(TowerTier.OUTER);
        lane.destroy(TowerTier.INNER);
        lane.destroy(TowerTier.INHIBITOR);
        state.clearStructureActionRegistryThisTick();
        assertFalse(weight(resolver().inspectCandidates(state, TeamSide.BLUE), TeamMacroPlan.GROUP_MID).eligible());
        assertTrue(new StructureResolver().destroyNextTower(
                state, TeamSide.BLUE, Lane.MID, PushReason.MID_GAME_MACRO).isEmpty());
        assertTrue(lane.isInhibitorAlive());
        assertEquals(2, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());
        assertTrue(state.getMapState().getBaseState(TeamSide.RED).isNexusAlive());
    }

    @Test
    void pushSuccessFailureAndRewardPathAreSingle() {
        GameState success = stateAt(870);
        int teamGold = success.getBlueTeamState().getGold();
        List<Integer> playerGold = success.getBlueTeamState().getPlayers().stream()
                .map(PlayerState::getGold).toList();
        Resolution win = resolveSelected(success, TeamSide.BLUE, TeamMacroPlan.GROUP_MID, 0);
        assertEquals(MacroActionResult.STRUCTURE_DAMAGED, win.action().result());
        assertEquals(teamGold + 240, success.getBlueTeamState().getGold());
        for (int i = 0; i < playerGold.size(); i++) {
            Position position = success.getBlueTeamState().getPlayers().get(i).getPosition();
            int expected = playerGold.get(i) + (position == Position.TOP ? 0 : 60);
            assertEquals(expected, success.getBlueTeamState().getPlayers().get(i).getGold());
        }
        assertEquals(1, win.events().stream()
                .filter(e -> e.getStructureActionSource() == StructureActionSource.MID_GAME_MACRO).count());
        GameState failure = stateAt(870);
        Resolution lose = resolveSelected(failure, TeamSide.BLUE, TeamMacroPlan.GROUP_MID, .999);
        assertNull(lose.action());
        assertEquals(MacroActionResult.PUSH_FAILED, failure.getMidGameMacroState()
                .teamState(TeamSide.BLUE).getLastActionResult());
        assertTrue(lose.events().stream().noneMatch(
                e -> e.getStructureActionSource() == StructureActionSource.MID_GAME_MACRO));
    }

    @Test
    void setupSignsNetZeroExpiryCaptureElderAndPostFightIsolationHold() {
        GameState state = stateAt(870);
        TeamMacroTeamState blue = state.getMidGameMacroState().teamState(TeamSide.BLUE);
        TeamMacroTeamState red = state.getMidGameMacroState().teamState(TeamSide.RED);
        blue.beginPlan(TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, null, ObjectiveType.DRAGON,
                EnumSet.of(Position.JUNGLE, Position.MID, Position.ADC), 870);
        ObjectivePriorityResolver priority = new ObjectivePriorityResolver();
        assertEquals(12, priority.dragonMacroSetupControl(state));
        assertEquals(12, priority.dragonSignedPriority(state)
                - priority.dragonSignedPriorityWithoutMacro(state), 1e-9);
        red.beginPlan(TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, null, ObjectiveType.DRAGON,
                EnumSet.of(Position.JUNGLE, Position.MID, Position.ADC), 870);
        assertEquals(0, priority.dragonMacroSetupControl(state));
        assertEquals(0, priority.snapshot(state).dragonMacroSetupControl());
        resolver().cancelSetupForObjective(state, ObjectiveType.DRAGON);
        assertEquals(MacroPlanStatus.CANCELLED, blue.getStatus());
        assertEquals(MacroPlanEndReason.OBJECTIVE_CAPTURED, blue.getEndReason());

        GameState expiry = stateAt(870);
        expiry.getMidGameMacroState().teamState(TeamSide.BLUE).beginPlan(
                TeamMacroPlan.OBJECTIVE_SETUP_BARON, null, ObjectiveType.BARON,
                EnumSet.of(Position.JUNGLE, Position.TOP, Position.MID), 870);
        expiry.advanceTimeSeconds(60);
        resolver().expirePlans(expiry);
        assertEquals(MacroPlanStatus.EXPIRED,
                expiry.getMidGameMacroState().teamState(TeamSide.BLUE).getStatus());
        assertEquals(0, priority.baronMacroSetupControl(expiry));

        GameState elder = stateAt(870);
        elder.getObjectiveState().claimSoul(TeamSide.BLUE, 870);
        elder.getMidGameMacroState().teamState(TeamSide.BLUE).beginPlan(
                TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, null, ObjectiveType.DRAGON,
                EnumSet.of(Position.JUNGLE, Position.MID, Position.ADC), 870);
        assertEquals(0, priority.dragonMacroSetupControl(elder));
    }

    @Test
    void farmBlockUsesMaxNoSubtractionNoCatchUpNoBlockedRandomAndSupportZero() {
        GameState state = stateAt(870);
        PlayerState adc = state.getBlueTeamState().playerAt(Position.ADC);
        adc.blockFarmUntil(900);
        resolveSelected(state, TeamSide.BLUE, TeamMacroPlan.GROUP_MID, 1);
        assertEquals(900, adc.getFarmResumeAtSeconds());
        assertEquals(0, state.getBlueTeamState().playerAt(Position.TOP).getFarmResumeAtSeconds());
        assertEquals(0, state.getBlueTeamState().playerAt(Position.SUPPORT).getFarmResumeAtSeconds());

        TeamState one = new TeamState("one", List.of(new PlayerState(
                "adc", Position.ADC, new PlayerAttributes(14, 14, 14, 14), 500)));
        TeamState enemy = new TeamState("enemy", List.of(new PlayerState(
                "enemy", Position.ADC, new PlayerAttributes(14, 14, 14, 14), 500)));
        GameState economy = new GameState(one, enemy, true, true, true, true);
        PlayerState player = one.playerAt(Position.ADC);
        player.addCs(100);
        player.blockFarmUntil(20);
        economy.getMidGameMacroState().registerFarmBlock(TeamSide.BLUE, Position.ADC, 20);
        SequenceRandom random = new SequenceRandom(0);
        new PositionEconomyResolver().resolve(economy, one, TeamSide.BLUE, 10, 10, random);
        assertEquals(100, player.getCs());
        assertEquals(0, random.calls);
        new PositionEconomyResolver().resolve(economy, one, TeamSide.BLUE, 20, 10, random);
        assertTrue(player.getCs() > 100);
        assertEquals(1, random.calls);

        TeamState support = new TeamState("support", List.of(new PlayerState(
                "support", Position.SUPPORT, new PlayerAttributes(14, 14, 14, 14), 500)));
        SequenceRandom supportRandom = new SequenceRandom(0);
        new PositionEconomyResolver().resolve(support, 10, 10, supportRandom);
        assertEquals(0, support.playerAt(Position.SUPPORT).getCs());
        assertEquals(0, supportRandom.calls);
    }

    @Test
    void snapshotFeatureOffDiagnosticsIsolationAndCompleteTimelineReproducibilityHold() {
        GameState state = stateAt(870);
        resolveSelected(state, TeamSide.BLUE, TeamMacroPlan.GROUP_MID, 1);
        MatchSnapshot snapshot = new SnapshotFactory().create(state);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getMidGameMacro().evaluationHistory().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getMidGameMacro().blueTeam().assignedPositions().add(Position.TOP));

        DummyDataFactory factory = new DummyDataFactory();
        MatchTimeline on = simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true))
                .simulate(factory.createBlueTeam(), factory.createRedTeam(), 777L);
        MatchTimeline offDiagnostics = simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(false))
                .simulate(factory.createBlueTeam(), factory.createRedTeam(), 777L);
        MatchTimeline replay = simulator(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true))
                .simulate(factory.createBlueTeam(), factory.createRedTeam(), 777L);
        assertEquals(signature(on), signature(offDiagnostics));
        assertEquals(signature(on), signature(replay));
        MatchTimeline featureOff = simulator(SimulationOptions.productionDefaults().withMidGameMacroEnabled(false))
                .simulate(factory.createBlueTeam(), factory.createRedTeam(), 777L);
        assertFalse(featureOff.getSnapshots().getLast().getMidGameMacro().enabled());
        assertTrue(featureOff.getEvents().stream().noneMatch(e -> e.getType() == MatchEventType.MACRO_ACTION));
        assertTrue(on.getSnapshots().getLast().getPlayerSnapshots().stream()
                .filter(p -> p.getPosition() == Position.SUPPORT).allMatch(p -> p.getCs() == 0));
    }

    @Test
    void laneSiegeAlsoHonorsPerTeamStructureLimitBeforeRandom() {
        GameState state = new GameState(team("BLUE"), team("RED"), true, true, true, true);
        state.advanceTimeSeconds(600);
        state.laneState(Lane.TOP).setPressure(60);
        state.laneState(Lane.MID).setPressure(60);
        state.getMapState().getLaneState(TeamSide.RED, Lane.TOP).applyOuterDamage(99.9);
        state.getMapState().getLaneState(TeamSide.RED, Lane.MID).applyOuterDamage(99.9);
        SequenceRandom random = new SequenceRandom(0, 0);
        List<StructureOutcome> destroyed = new LanePhaseResolver().resolveOuterSieges(
                state, 600, random, new StructureResolver());
        assertEquals(1, destroyed.size());
        assertEquals(TeamSide.BLUE, destroyed.getFirst().attackingSide());
        assertEquals(1, random.calls);
    }

    private MidGameMacroDecisionData decisionFor(TeamMacroPlan plan, int time) {
        return resolveSelected(stateAt(time), TeamSide.BLUE, plan, 1).decision();
    }

    private TowerTier destroyedTier(int preDestroyed) {
        GameState state = stateAt(870);
        LaneStructureState lane = state.getMapState().getLaneState(TeamSide.RED, Lane.MID);
        if (preDestroyed >= 1) lane.destroy(TowerTier.OUTER);
        if (preDestroyed >= 2) lane.destroy(TowerTier.INNER);
        state.clearStructureActionRegistryThisTick();
        return resolveSelected(state, TeamSide.BLUE, TeamMacroPlan.GROUP_MID, 0).events().stream()
                .filter(e -> e.getStructureActionSource() == StructureActionSource.MID_GAME_MACRO)
                .findFirst().orElseThrow().getStructureTowerTier();
    }

    private Resolution resolveSelected(GameState state, TeamSide side, TeamMacroPlan plan, double actionRoll) {
        killAll(state.getTeamState(side.opposite()), state.getCurrentTimeSeconds());
        MidGameMacroResolver resolver = resolver();
        double selection = rollFor(resolver.inspectCandidates(state, side), plan);
        List<MatchEvent> events = new ArrayList<>();
        resolver.resolveDueEvaluation(state, new SequenceRandom(selection, actionRoll), events, new StructureResolver());
        var audit = state.getMidGameMacroState().getEvaluationHistory().getLast();
        MidGameMacroDecisionData decision = side == TeamSide.BLUE ? audit.blueDecision() : audit.redDecision();
        MidGameMacroActionData action = events.stream().map(MatchEvent::getMidGameMacroAction)
                .filter(a -> a != null && a.teamSide() == side).findFirst().orElse(null);
        return new Resolution(decision, action, List.copyOf(events));
    }

    private double rollFor(List<MacroPlanWeightBreakdown> candidates, TeamMacroPlan target) {
        double total = candidates.stream().filter(MacroPlanWeightBreakdown::eligible)
                .mapToDouble(MacroPlanWeightBreakdown::finalWeight).sum();
        double before = 0;
        for (MacroPlanWeightBreakdown value : candidates) {
            if (!value.eligible()) continue;
            if (value.plan() == target) return (before + value.finalWeight() / 2) / total;
            before += value.finalWeight();
        }
        throw new IllegalArgumentException("ineligible target " + target);
    }

    private MacroPlanWeightBreakdown weight(List<MacroPlanWeightBreakdown> values, TeamMacroPlan plan) {
        return values.stream().filter(v -> v.plan() == plan).findFirst().orElseThrow();
    }

    private MidGameMacroResolver resolver() { return new MidGameMacroResolver(); }

    private GameState stateAt(int time) {
        GameState state = new GameState(team("BLUE"), team("RED"), true, true, true, true);
        state.advanceTimeSeconds(840);
        new LanePhaseResolver().transitionIfDue(state).orElseThrow();
        resolver().onPhaseTransition(state);
        state.advanceTimeSeconds(time - 840);
        state.getObjectiveState().updateSpawnState(time);
        return state;
    }

    private TeamState team(String name) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(new PlayerState(
                name + "-" + position, position, new PlayerAttributes(14, 14, 14, 14), 500));
        return new TeamState(name, players);
    }

    private void killAll(TeamState team, int time) {
        for (PlayerState player : team.getPlayers()) player.markDead(time, 300);
    }

    private MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private String signature(MatchTimeline timeline) {
        StringBuilder value = new StringBuilder().append(timeline.getDurationSeconds()).append('|')
                .append(timeline.getWinner()).append('|');
        for (MatchEvent event : timeline.getEvents()) value.append(event.getTimeSeconds()).append(':')
                .append(event.getType()).append(':').append(event.getCombatSource()).append(':')
                .append(event.getStructureActionSource()).append(':').append(event.getStructureKind()).append(':')
                .append(event.getStructureTowerTier()).append(':').append(event.getStructureLane()).append(':')
                .append(event.getStructureAttackingSide()).append(':').append(event.getMidGameMacroDecision()).append(':')
                .append(event.getMidGameMacroAction()).append(':').append(event.getStructureAction()).append(';');
        for (MatchSnapshot snapshot : timeline.getSnapshots()) {
            value.append(snapshot.getTimeSeconds()).append(':').append(snapshot.getBlueKills()).append(':')
                    .append(snapshot.getRedKills()).append(':').append(snapshot.getBlueGold()).append(':')
                    .append(snapshot.getRedGold()).append(':').append(snapshot.getBlueDragons()).append(':')
                    .append(snapshot.getRedDragons()).append(':').append(snapshot.getMidGameMacro()).append(':')
                    .append(snapshot.getStructureState()).append(':')
                    .append(snapshot.getObjectivePriority()).append(':');
            snapshot.getPlayerSnapshots().forEach(p -> value.append(p.getTeamSide()).append('/')
                    .append(p.getPosition()).append('/').append(p.getKills()).append('/').append(p.getDeaths())
                    .append('/').append(p.getAssists()).append('/').append(p.getCs()).append('/')
                    .append(p.getGold()).append('/').append(p.isAlive()).append(','));
            value.append(';');
        }
        return value.toString();
    }

    private record Resolution(MidGameMacroDecisionData decision, MidGameMacroActionData action,
                              List<MatchEvent> events) { }

    private static final class SequenceRandom extends Random {
        private final List<Double> values;
        private final List<Double> used = new ArrayList<>();
        private int index;
        private int calls;
        SequenceRandom(double... values) { this.values = Arrays.stream(values).boxed().toList(); }
        @Override public double nextDouble() {
            calls++;
            double value = index < values.size() ? values.get(index++) : .5;
            used.add(value);
            return value;
        }
        @Override public boolean nextBoolean() { return false; }
    }
}

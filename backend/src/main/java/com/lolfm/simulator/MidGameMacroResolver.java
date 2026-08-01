package com.lolfm.simulator;

import com.lolfm.domain.MacroPlanWeightBreakdown;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MidGameMacroActionData;
import com.lolfm.domain.MidGameMacroDecisionData;
import com.lolfm.domain.MidGameMacroEvaluationData;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Stateless evaluator and executor for the match-owned mid-game macro state. */
public final class MidGameMacroResolver {
    private static final List<Position> GROUP_POSITIONS = List.of(Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
    private static final List<Position> DRAGON_SUPPORT_POSITIONS = List.of(Position.MID, Position.ADC, Position.SUPPORT);
    private static final List<Position> BARON_SUPPORT_POSITIONS = List.of(Position.TOP, Position.MID, Position.SUPPORT);

    public void expirePlans(GameState state) {
        if (!state.isMidGameMacroEnabled()) return;
        MidGameMacroExecutionStats stats = state.getMidGameMacroState().getExecutionStats();
        for (TeamSide side : TeamSide.values()) {
            TeamMacroTeamState team = state.getMidGameMacroState().teamState(side);
            boolean setup = team.getCurrentPlan() == TeamMacroPlan.OBJECTIVE_SETUP_DRAGON
                    || team.getCurrentPlan() == TeamMacroPlan.OBJECTIVE_SETUP_BARON;
            if (setup && team.getActiveUntilSeconds() >= 0
                    && state.getCurrentTimeSeconds() >= team.getActiveUntilSeconds()) stats.recordSetupExpiry();
        }
        state.getMidGameMacroState().expirePlansIfNeeded(state.getCurrentTimeSeconds());
    }

    public void onPhaseTransition(GameState state) {
        if (state.getLanePhaseState().getMatchPhase() == MatchPhase.MID_GAME) {
            state.getMidGameMacroState().onMidGameStarted(state.getLanePhaseState().getMidGameStartedAtSeconds());
        }
    }

    public void onLateGameTransition(GameState state) {
        state.getMidGameMacroState().onLateGameTransition(state.getCurrentTimeSeconds());
    }

    public void resolveDueEvaluation(GameState state, Random random, List<MatchEvent> events,
                                     StructureResolver structureResolver) {
        MidGameMacroState macro = state.getMidGameMacroState();
        MidGameMacroExecutionStats stats = macro.getExecutionStats();
        if (!macro.isEnabled()) {
            stats.recordFeatureDisabled();
            return;
        }
        if (state.isFinished() || state.getLanePhaseState().getMatchPhase() != MatchPhase.MID_GAME) {
            stats.recordPhaseIneligible();
            return;
        }

        int time = state.getCurrentTimeSeconds();
        boolean blueDue = macro.dueAt(TeamSide.BLUE, time);
        boolean redDue = macro.dueAt(TeamSide.RED, time);
        if (!blueDue && !redDue) {
            stats.recordNotDue();
            return;
        }

        TeamMacroTeamState blueState = macro.teamState(TeamSide.BLUE);
        TeamMacroTeamState redState = macro.teamState(TeamSide.RED);
        int blueDueAt = blueDue ? blueState.getNextEvaluationAtSeconds() : -1;
        int redDueAt = redDue ? redState.getNextEvaluationAtSeconds() : -1;
        TeamMacroPlan bluePrevious = blueState.getPreviousPlan();
        TeamMacroPlan redPrevious = redState.getPreviousPlan();
        MacroPlanEndReason bluePreviousEndReason = blueState.getEndReason();
        MacroPlanEndReason redPreviousEndReason = redState.getEndReason();

        // Both candidate sets and both selections are completed against the same pre-action state.
        Evaluation blue = blueDue ? evaluate(state, TeamSide.BLUE) : null;
        Evaluation red = redDue ? evaluate(state, TeamSide.RED) : null;
        Selection blueSelection = blue == null ? null : select(state, blue, random);
        Selection redSelection = red == null ? null : select(state, red, random);

        if (blueSelection != null) blueState.advanceScheduleAfterEvaluation(time, blueSelection.rollExecuted() ? 1 : 0);
        if (redSelection != null) redState.advanceScheduleAfterEvaluation(time, redSelection.rollExecuted() ? 1 : 0);
        Decision blueDecision = blueSelection == null ? null : applySelection(state, blueSelection, time);
        Decision redDecision = redSelection == null ? null : applySelection(state, redSelection, time);
        macro.markEvaluationAt(time);
        if (blue != null) stats.recordEvaluation(TeamSide.BLUE);
        if (red != null) stats.recordEvaluation(TeamSide.RED);
        int dueAt = blueDueAt < 0 ? redDueAt : redDueAt < 0 ? blueDueAt : Math.min(blueDueAt, redDueAt);
        int selectionRandomCount = (blueSelection != null && blueSelection.rollExecuted() ? 1 : 0)
                + (redSelection != null && redSelection.rollExecuted() ? 1 : 0);
        macro.recordEvaluation(new MidGameMacroEvaluationData(
                dueAt, time,
                blueDecision == null ? null : blueDecision.decision(),
                redDecision == null ? null : redDecision.decision(),
                bluePrevious, redPrevious, bluePreviousEndReason, redPreviousEndReason,
                blueState.getNextEvaluationAtSeconds(), redState.getNextEvaluationAtSeconds(),
                null, selectionRandomCount));

        if (blueDecision != null) execute(state, blueDecision, events, structureResolver, random);
        if (redDecision != null) execute(state, redDecision, events, structureResolver, random);
    }

    public void onMatchFinished(GameState state) {
        state.getMidGameMacroState().finishMatch(state.getCurrentTimeSeconds());
    }

    public void cancelSetupForObjective(GameState state, ObjectiveType objectiveType) {
        if (!state.isMidGameMacroEnabled()) return;
        for (TeamSide side : TeamSide.values()) {
            TeamMacroTeamState team = state.getMidGameMacroState().teamState(side);
            if (team.isActiveAt(state.getCurrentTimeSeconds()) && team.getTargetObjective() == objectiveType) {
                team.cancel(MacroPlanEndReason.OBJECTIVE_CAPTURED, state.getCurrentTimeSeconds());
                state.getMidGameMacroState().getExecutionStats().recordSetupCaptureCancellation();
            }
        }
    }

    private Evaluation evaluate(GameState state, TeamSide side) {
        List<Candidate> candidates = new ArrayList<>();
        for (TeamMacroPlan plan : TeamMacroPlan.values()) candidates.add(candidate(state, side, plan, true));
        return new Evaluation(side, List.copyOf(candidates));
    }

    List<MacroPlanWeightBreakdown> inspectCandidates(GameState state, TeamSide side) {
        List<MacroPlanWeightBreakdown> result = new ArrayList<>();
        for (TeamMacroPlan plan : TeamMacroPlan.values()) result.add(candidate(state, side, plan, false).breakdown());
        return List.copyOf(result);
    }

    private Candidate candidate(GameState state, TeamSide side, TeamMacroPlan plan, boolean recordDiagnostics) {
        Eligibility eligibility = eligibility(state, side, plan);
        MacroPlanWeightBreakdown breakdown = weight(state, side, plan, eligibility);
        if (recordDiagnostics) state.getMidGameMacroState().getExecutionStats().recordCandidate(plan, eligibility.eligible());
        return new Candidate(plan, eligibility, breakdown);
    }

    private Eligibility eligibility(GameState state, TeamSide side, TeamMacroPlan plan) {
        return switch (plan) {
            case GROUP_MID -> {
                Set<Position> positions = availablePositions(state, side, GROUP_POSITIONS);
                yield positions.size() >= 3 && hasTowerTarget(state, side, Lane.MID)
                        ? Eligibility.ok(positions, Lane.MID, null)
                        : Eligibility.no(positions.size() < 3 ? "INSUFFICIENT_PARTICIPANTS" : "NO_STRUCTURE_TARGET", Lane.MID, null);
            }
            case SIDE_LANE_TOP -> singleLaneEligibility(state, side, Position.TOP, Lane.TOP);
            case SIDE_LANE_BOT -> singleLaneEligibility(state, side, Position.ADC, Lane.BOT);
            case OBJECTIVE_SETUP_DRAGON -> objectiveEligibility(state, side, ObjectiveType.DRAGON,
                    Position.JUNGLE, DRAGON_SUPPORT_POSITIONS);
            case OBJECTIVE_SETUP_BARON -> objectiveEligibility(state, side, ObjectiveType.BARON,
                    Position.JUNGLE, BARON_SUPPORT_POSITIONS);
            case RESET_AND_FARM -> Eligibility.ok(Set.of(), null, null);
        };
    }

    private Eligibility singleLaneEligibility(GameState state, TeamSide side, Position position, Lane lane) {
        Set<Position> positions = availablePositions(state, side, List.of(position));
        return positions.size() == 1 && hasTowerTarget(state, side, lane)
                ? Eligibility.ok(positions, lane, null)
                : Eligibility.no(positions.isEmpty() ? "REQUIRED_PLAYER_UNAVAILABLE" : "NO_STRUCTURE_TARGET", lane, null);
    }

    private Eligibility objectiveEligibility(GameState state, TeamSide side, ObjectiveType objective,
                                             Position required, List<Position> optional) {
        int time = state.getCurrentTimeSeconds();
        boolean available = objective == ObjectiveType.DRAGON
                ? state.getObjectiveState().isElementalDragonPhase() && objectiveInWindow(
                        state.getObjectiveState().isDragonAlive(), state.getObjectiveState().getNextDragonSpawnSeconds(), time)
                : objectiveInWindow(state.getObjectiveState().isBaronAlive(),
                        state.getObjectiveState().getNextBaronSpawnSeconds(), time);
        Set<Position> positions = EnumSet.noneOf(Position.class);
        if (availablePosition(state, side, required)) positions.add(required);
        for (Position position : optional) if (availablePosition(state, side, position)) positions.add(position);
        int optionalCount = positions.size() - (positions.contains(required) ? 1 : 0);
        if (!available) return Eligibility.no("OBJECTIVE_UNAVAILABLE", null, objective);
        if (!positions.contains(required)) return Eligibility.no("REQUIRED_PLAYER_UNAVAILABLE", null, objective);
        if (optionalCount < 2) return Eligibility.no("INSUFFICIENT_PARTICIPANTS", null, objective);
        return Eligibility.ok(positions, null, objective);
    }

    private boolean objectiveInWindow(boolean alive, int nextSpawn, int time) {
        return alive || (nextSpawn >= time && nextSpawn - time <= MidGameMacroRuleConfig.OBJECTIVE_SETUP_WINDOW_SECONDS);
    }

    private Set<Position> availablePositions(GameState state, TeamSide side, List<Position> positions) {
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (Position position : positions) if (availablePosition(state, side, position)) result.add(position);
        return result;
    }

    private boolean availablePosition(GameState state, TeamSide side, Position position) {
        Optional<PlayerState> player = state.getTeamState(side).findPlayerAt(position);
        return player.isPresent() && player.get().canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())
                && !state.wasMajorCombatParticipantThisTick(player.get());
    }

    private boolean hasTowerTarget(GameState state, TeamSide side, Lane lane) {
        return state.getMapState().getLaneState(side.opposite(), lane).nextAliveTower().isPresent();
    }

    private MacroPlanWeightBreakdown weight(GameState state, TeamSide side, TeamMacroPlan plan,
                                            Eligibility eligibility) {
        double goldEdge = edge(state.getTeamState(side).getGold(), state.getTeamState(side.opposite()).getGold(),
                MidGameMacroRuleConfig.GOLD_EDGE_NORMALIZER);
        double attributeEdge = 0;
        double objectiveEdge = 0;
        double base = baseWeight(plan);
        double goldContribution = goldEdge * MidGameMacroRuleConfig.GOLD_EDGE_WEIGHT;
        double attributeContribution = 0;
        double objectiveContribution = 0;
        double soulBonus = 0;
        double behind = 0;
        double missing = 0;
        if (plan == TeamMacroPlan.GROUP_MID) {
            attributeEdge = averageAttribute(state.getTeamState(side), GROUP_POSITIONS, true)
                    - averageAttribute(state.getTeamState(side.opposite()), GROUP_POSITIONS, true);
            attributeEdge = clamp(attributeEdge / MidGameMacroRuleConfig.ATTRIBUTE_EDGE_NORMALIZER, -1, 1);
            attributeContribution = attributeEdge * MidGameMacroRuleConfig.TEAMFIGHT_EDGE_WEIGHT;
        } else if (plan == TeamMacroPlan.SIDE_LANE_TOP) {
            attributeEdge = farmingEdge(state, side, Position.TOP);
            attributeContribution = attributeEdge * MidGameMacroRuleConfig.SIDE_FARMING_EDGE_WEIGHT;
        } else if (plan == TeamMacroPlan.SIDE_LANE_BOT) {
            attributeEdge = farmingEdge(state, side, Position.ADC);
            attributeContribution = attributeEdge * MidGameMacroRuleConfig.SIDE_FARMING_EDGE_WEIGHT;
        } else if (plan == TeamMacroPlan.OBJECTIVE_SETUP_DRAGON) {
            double signed = new ObjectivePriorityResolver().dragonSignedPriorityWithoutMacro(state);
            objectiveEdge = side == TeamSide.BLUE ? signed / 100.0 : -signed / 100.0;
            objectiveEdge = clamp(objectiveEdge, -1, 1);
            objectiveContribution = objectiveEdge * MidGameMacroRuleConfig.OBJECTIVE_PRIORITY_WEIGHT;
            soulBonus = state.getTeamState(side).getDragons() >= 3
                    ? MidGameMacroRuleConfig.DRAGON_SOUL_POINT_WEIGHT_BONUS : 0;
        } else if (plan == TeamMacroPlan.OBJECTIVE_SETUP_BARON) {
            double signed = new ObjectivePriorityResolver().baronSignedPriorityWithoutMacro(state);
            objectiveEdge = side == TeamSide.BLUE ? signed / 100.0 : -signed / 100.0;
            objectiveEdge = clamp(objectiveEdge, -1, 1);
            objectiveContribution = objectiveEdge * MidGameMacroRuleConfig.OBJECTIVE_PRIORITY_WEIGHT;
        } else if (plan == TeamMacroPlan.RESET_AND_FARM) {
            behind = Math.max(0, -goldEdge) * MidGameMacroRuleConfig.RESET_BEHIND_GOLD_WEIGHT;
            int missingPlayers = Position.values().length - countAlive(state.getTeamState(side), state.getCurrentTimeSeconds());
            missing = missingPlayers * MidGameMacroRuleConfig.RESET_MISSING_PLAYER_WEIGHT;
        }
        TeamMacroTeamState macroTeam = state.getMidGameMacroState().teamState(side);
        boolean repeat = plan != TeamMacroPlan.RESET_AND_FARM
                && (plan == macroTeam.getCurrentPlan() || plan == macroTeam.getPreviousPlan());
        double repeatMultiplier = repeat ? MidGameMacroRuleConfig.SAME_PLAN_REPEAT_MULTIPLIER : 1;
        double finalWeight = eligibility.eligible()
                ? clamp((base + goldContribution + attributeContribution + objectiveContribution + soulBonus + behind + missing)
                        * repeatMultiplier, MidGameMacroRuleConfig.MIN_PLAN_WEIGHT, MidGameMacroRuleConfig.MAX_PLAN_WEIGHT)
                : 0;
        return new MacroPlanWeightBreakdown(plan, eligibility.eligible(), base, goldEdge, goldContribution,
                attributeEdge, attributeContribution, objectiveEdge, objectiveContribution, soulBonus, behind, missing,
                repeatMultiplier, finalWeight, eligibility.reason());
    }

    private Selection select(GameState state, Evaluation evaluation, Random random) {
        List<Candidate> eligible = evaluation.candidates().stream()
                .filter(candidate -> candidate.breakdown().eligible()).toList();
        Candidate selected;
        boolean rollExecuted = eligible.size() > 1;
        double roll = 0;
        if (eligible.size() == 1) {
            selected = eligible.getFirst();
        } else {
            double total = eligible.stream().mapToDouble(candidate -> candidate.breakdown().finalWeight()).sum();
            roll = random.nextDouble();
            double cursor = roll * total;
            selected = eligible.getLast();
            for (Candidate candidate : eligible) {
                cursor -= candidate.breakdown().finalWeight();
                if (cursor < 0) {
                    selected = candidate;
                    break;
                }
            }
        }
        state.getMidGameMacroState().getExecutionStats().recordSelection(
                evaluation.side(), selected.plan(), rollExecuted,
                selected.breakdown().repeatMultiplier() != 1);
        return new Selection(evaluation.side(), evaluation.candidates(), selected, rollExecuted, roll);
    }

    private Decision applySelection(GameState state, Selection selection, int time) {
        Candidate candidate = selection.selected();
        TeamMacroTeamState team = state.getMidGameMacroState().teamState(selection.side());
        team.beginPlan(candidate.plan(), candidate.eligibility().targetLane(), candidate.eligibility().targetObjective(),
                candidate.eligibility().positions(), time);
        for (Position position : candidate.eligibility().positions()) {
            PlayerState player = state.getTeamState(selection.side()).playerAt(position);
            state.getMidGameMacroState().getExecutionStats().recordAssignmentValidation(
                    !player.isAlive(time), state.wasMajorCombatParticipantThisTick(player));
            state.getMidGameMacroState().getExecutionStats().recordAssignment(position);
        }
        MidGameMacroDecisionData decision = new MidGameMacroDecisionData(time, selection.side(), true,
                selection.candidates().stream().map(Candidate::breakdown).toList(), candidate.plan(),
                team.getTargetLane(), team.getTargetObjective(), team.getAssignedPositions(), selection.rollExecuted(),
                selection.rollExecuted() ? selection.roll() : null,
                team.getStartedAtSeconds(), team.getActiveUntilSeconds());
        return new Decision(selection.side(), decision, candidate);
    }

    private void execute(GameState state, Decision decision, List<MatchEvent> events,
                         StructureResolver structures, Random random) {
        TeamMacroPlan plan = decision.candidate().plan();
        if (plan == TeamMacroPlan.RESET_AND_FARM) return;
        if (plan == TeamMacroPlan.OBJECTIVE_SETUP_DRAGON || plan == TeamMacroPlan.OBJECTIVE_SETUP_BARON) {
            executeObjectiveSetup(state, decision, events);
            return;
        }
        executeStructurePush(state, decision, events, structures, random);
    }

    private void executeStructurePush(GameState state, Decision decision, List<MatchEvent> events,
                                      StructureResolver structures, Random random) {
        TeamMacroTeamState team = state.getMidGameMacroState().teamState(decision.side());
        Lane lane = team.getTargetLane();
        Set<Position> participants = team.getAssignedPositions();
        if (!participantsAvailable(state, decision.side(), participants)
                || lane == null || !hasTowerTarget(state, decision.side(), lane)) {
            team.setLastActionResult(MacroActionResult.INELIGIBLE);
            if (state.getMapState().getLaneState(decision.side().opposite(), lane == null ? Lane.MID : lane)
                    .nextAliveTower().isEmpty()) {
                state.getMidGameMacroState().getExecutionStats().recordTargetMissingAfterSelection();
            }
            state.getMidGameMacroState().getExecutionStats().recordActionIneligible();
            return;
        }
        if (state.wasStructureActionPerformedThisTick(decision.side())) {
            state.recordLaterStructureResolverBlockedByAttempt();
            team.setLastActionResult(MacroActionResult.INELIGIBLE);
            state.getMidGameMacroState().getExecutionStats().recordExistingStructureActionBlocked();
            state.getMidGameMacroState().getExecutionStats().recordActionIneligible();
            return;
        }
        state.getMidGameMacroState().getExecutionStats().recordActionAttempt();
        state.recordPushAttempt();
        state.getCompositionRuntimeState().recordActualAttempt(
                com.lolfm.composition.CompositionActionType.SIEGE, decision.side(), decision.side(), decision.side().opposite(),
                com.lolfm.composition.FightScale.NONE, null, false, null, lane, state.getCurrentTimeSeconds(),
                com.lolfm.composition.CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        double goldEdge = edge(state.getTeamState(decision.side()).getGold(),
                state.getTeamState(decision.side().opposite()).getGold(), MidGameMacroRuleConfig.GOLD_EDGE_NORMALIZER);
        double aliveBonus = (countAlive(state.getTeamState(decision.side()), state.getCurrentTimeSeconds())
                - countAlive(state.getTeamState(decision.side().opposite()), state.getCurrentTimeSeconds()))
                * MidGameMacroRuleConfig.PUSH_ALIVE_EDGE_BONUS_PER_PLAYER;
        double goldBonus = goldEdge * MidGameMacroRuleConfig.PUSH_GOLD_EDGE_BONUS_MAX;
        double attributeEdge = planAttributeEdge(state, decision.side(), teamPlan(decision));
        double attributeBonus = teamPlan(decision) == TeamMacroPlan.GROUP_MID
                ? attributeEdge * MidGameMacroRuleConfig.GROUP_TEAMFIGHT_EDGE_BONUS
                : attributeEdge * MidGameMacroRuleConfig.SIDE_FARMING_EDGE_BONUS;
        double baronBonus = state.getTeamState(decision.side()).hasActiveBaronBuff(state.getCurrentTimeSeconds())
                ? MidGameMacroRuleConfig.BARON_PUSH_CHANCE_BONUS : 0;
        double base = teamPlan(decision) == TeamMacroPlan.GROUP_MID
                ? MidGameMacroRuleConfig.GROUP_MID_PUSH_BASE_CHANCE
                : MidGameMacroRuleConfig.SIDE_LANE_PUSH_BASE_CHANCE;
        double chance = clamp(base + goldBonus + aliveBonus + attributeBonus + baronBonus,
                MidGameMacroRuleConfig.MIN_MACRO_PUSH_CHANCE, MidGameMacroRuleConfig.MAX_MACRO_PUSH_CHANCE);
        state.markStructureActionAttempted(decision.side());
        double roll = random.nextDouble();
        executeStructurePushWithRoll(state, decision, events, structures, roll, base,
                goldBonus, aliveBonus, attributeBonus, baronBonus, chance);
    }

    private void executeStructurePushWithRoll(GameState state, Decision decision, List<MatchEvent> events,
                                              StructureResolver structures, double roll, double base,
                                              double goldBonus, double aliveBonus, double attributeBonus,
                                              double baronBonus, double chance) {
        TeamMacroTeamState team = state.getMidGameMacroState().teamState(decision.side());
        state.getMidGameMacroState().getExecutionStats().recordPushRoll();
        boolean success = roll < chance;
        int farmSeconds = applyFarmBlock(state, decision.side(), team.getAssignedPositions(), teamPlan(decision));
        StructureOutcome outcome = success
                ? structures.destroyNextTower(state, decision.side(), team.getTargetLane(), PushReason.MID_GAME_MACRO).orElse(null)
                : null;
        MacroActionResult result = outcome == null
                ? MacroActionResult.PUSH_FAILED : MacroActionResult.STRUCTURE_DESTROYED;
        team.setLastActionResult(result);
        if (outcome != null) {
            state.recordPushSuccess();
            state.getMidGameMacroState().getExecutionStats().recordPushSuccess();
            team.recordStructure(outcome);
        } else {
            state.recordPushFailure(success
                    ? PushFailureReason.TARGET_UNAVAILABLE : PushFailureReason.CHANCE_ROLL_FAILED);
            state.getMidGameMacroState().getExecutionStats().recordPushFailure();
        }
        MidGameMacroActionData action = new MidGameMacroActionData(
                decision.side(), teamPlan(decision), MacroActionType.STRUCTURE_PUSH,
                result, team.getTargetLane(), null, team.getAssignedPositions(),
                outcome == null ? null : outcome.towerTier(), base, goldBonus, aliveBonus,
                attributeBonus, baronBonus, chance, true, success,
                outcome == null ? null : outcome.structureKind(), 0, -1, farmSeconds);
        events.add(macroEvent(state, decision.decision(), action));
        if (outcome != null) events.add(structures.createStructureEvent(state, outcome));
    }

    private void executeObjectiveSetup(GameState state, Decision decision, List<MatchEvent> events) {
        TeamMacroTeamState team = state.getMidGameMacroState().teamState(decision.side());
        ObjectiveType objective = team.getTargetObjective();
        boolean eligible = objective == ObjectiveType.DRAGON
                ? state.getObjectiveState().isElementalDragonPhase()
                    && objectiveInWindow(state.getObjectiveState().isDragonAlive(),
                            state.getObjectiveState().getNextDragonSpawnSeconds(), state.getCurrentTimeSeconds())
                : objective == ObjectiveType.BARON
                    && objectiveInWindow(state.getObjectiveState().isBaronAlive(),
                            state.getObjectiveState().getNextBaronSpawnSeconds(), state.getCurrentTimeSeconds());
        eligible = eligible && participantsAvailable(state, decision.side(), team.getAssignedPositions());
        if (!eligible) {
            team.setLastActionResult(MacroActionResult.INELIGIBLE);
            state.getMidGameMacroState().getExecutionStats().recordActionIneligible();
            return;
        }
        state.getMidGameMacroState().getExecutionStats().recordActionAttempt();
        int farmSeconds = applyFarmBlock(state, decision.side(), team.getAssignedPositions(), teamPlan(decision));
        team.setLastActionResult(MacroActionResult.SETUP_STARTED);
        state.getMidGameMacroState().getExecutionStats().recordSetupStart();
        double control = new ObjectivePriorityResolver().snapshot(state).enabled()
                ? (decision.side() == TeamSide.BLUE
                    ? MidGameMacroRuleConfig.MACRO_SETUP_CONTROL : -MidGameMacroRuleConfig.MACRO_SETUP_CONTROL)
                : 0;
        MidGameMacroActionData action = new MidGameMacroActionData(
                decision.side(), teamPlan(decision), MacroActionType.OBJECTIVE_SETUP,
                MacroActionResult.SETUP_STARTED, null, objective, team.getAssignedPositions(), null,
                0, 0, 0, 0, 0, 0, false, false, null, control,
                team.getActiveUntilSeconds(), farmSeconds);
        events.add(macroEvent(state, decision.decision(), action));
    }

    private MatchEvent macroEvent(GameState state, MidGameMacroDecisionData decision,
                                  MidGameMacroActionData action) {
        MatchEvent event = new MatchEvent(state.getCurrentTimeSeconds(), MatchEventType.MACRO_ACTION,
                "미드게임 팀 운영 액션", null, null, List.of());
        event.setMidGameMacroDecision(decision);
        event.setMidGameMacroAction(action);
        return event;
    }

    private int applyFarmBlock(GameState state, TeamSide side, Set<Position> positions,
                               TeamMacroPlan plan) {
        int seconds = plan == TeamMacroPlan.GROUP_MID
                ? MidGameMacroRuleConfig.GROUP_MID_FARM_BLOCK_SECONDS
                : plan == TeamMacroPlan.OBJECTIVE_SETUP_DRAGON
                    || plan == TeamMacroPlan.OBJECTIVE_SETUP_BARON
                ? MidGameMacroRuleConfig.OBJECTIVE_SETUP_FARM_BLOCK_SECONDS : 0;
        if (seconds == 0) return 0;
        for (Position position : positions) {
            boolean blocked = plan == TeamMacroPlan.GROUP_MID
                    ? position == Position.JUNGLE || position == Position.MID || position == Position.ADC
                    : position != Position.SUPPORT;
            if (blocked) {
                int until = state.getCurrentTimeSeconds() + seconds;
                state.getTeamState(side).playerAt(position).blockFarmUntil(until);
                state.getMidGameMacroState().registerFarmBlock(side, position, until);
            }
        }
        return seconds;
    }

    private boolean participantsAvailable(GameState state, TeamSide side, Set<Position> positions) {
        for (Position position : positions) {
            if (!availablePosition(state, side, position)) return false;
        }
        return true;
    }

    private TeamMacroPlan teamPlan(Decision decision) { return decision.candidate().plan(); }

    private double planAttributeEdge(GameState state, TeamSide side, TeamMacroPlan plan) {
        if (plan == TeamMacroPlan.GROUP_MID) {
            return averageAttribute(state.getTeamState(side), GROUP_POSITIONS, true)
                    - averageAttribute(state.getTeamState(side.opposite()), GROUP_POSITIONS, true);
        }
        return plan == TeamMacroPlan.SIDE_LANE_TOP
                ? farmingEdge(state, side, Position.TOP) : farmingEdge(state, side, Position.ADC);
    }

    private double farmingEdge(GameState state, TeamSide side, Position position) {
        return clamp((state.getTeamState(side).playerAt(position).getFarming()
                - state.getTeamState(side.opposite()).playerAt(position).getFarming())
                / MidGameMacroRuleConfig.ATTRIBUTE_EDGE_NORMALIZER, -1, 1);
    }

    private double averageAttribute(TeamState team, List<Position> positions, boolean teamfighting) {
        double total = 0;
        for (Position position : positions) {
            PlayerState player = team.playerAt(position);
            total += teamfighting ? player.getTeamfighting() : player.getFarming();
        }
        return total / positions.size();
    }

    private int countAlive(TeamState team, int time) {
        int count = 0;
        for (PlayerState player : team.getPlayers()) if (player.isAlive(time)) count++;
        return count;
    }

    private double baseWeight(TeamMacroPlan plan) {
        return switch (plan) {
            case GROUP_MID -> MidGameMacroRuleConfig.GROUP_MID_BASE_WEIGHT;
            case SIDE_LANE_TOP -> MidGameMacroRuleConfig.SIDE_LANE_TOP_BASE_WEIGHT;
            case SIDE_LANE_BOT -> MidGameMacroRuleConfig.SIDE_LANE_BOT_BASE_WEIGHT;
            case OBJECTIVE_SETUP_DRAGON -> MidGameMacroRuleConfig.DRAGON_SETUP_BASE_WEIGHT;
            case OBJECTIVE_SETUP_BARON -> MidGameMacroRuleConfig.BARON_SETUP_BASE_WEIGHT;
            case RESET_AND_FARM -> MidGameMacroRuleConfig.RESET_AND_FARM_BASE_WEIGHT;
        };
    }

    private double edge(double own, double enemy, double normalizer) {
        return clamp((own - enemy) / normalizer, -1, 1);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Eligibility(boolean eligible, Set<Position> positions, Lane targetLane,
                               ObjectiveType targetObjective, String reason) {
        static Eligibility ok(Set<Position> positions, Lane lane, ObjectiveType objective) {
            return new Eligibility(true, Set.copyOf(positions), lane, objective, null);
        }
        static Eligibility no(String reason, Lane lane, ObjectiveType objective) {
            return new Eligibility(false, Set.of(), lane, objective, reason);
        }
    }
    private record Candidate(TeamMacroPlan plan, Eligibility eligibility,
                             MacroPlanWeightBreakdown breakdown) { }
    private record Evaluation(TeamSide side, List<Candidate> candidates) { }
    private record Selection(TeamSide side, List<Candidate> candidates, Candidate selected,
                             boolean rollExecuted, double roll) { }
    private record Decision(TeamSide side, MidGameMacroDecisionData decision, Candidate candidate) { }
}

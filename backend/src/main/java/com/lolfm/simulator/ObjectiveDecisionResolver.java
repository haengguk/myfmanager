package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.FightScale;
import com.lolfm.domain.ObjectiveDecisionData;
import com.lolfm.domain.ObjectiveDecisionWeightBreakdown;
import com.lolfm.domain.ObjectiveFightSkillImpactData;
import com.lolfm.domain.ObjectivePriorityDecisionData;
import com.lolfm.domain.ObjectiveSecureDecisionData;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Stateless decision and resolution layer entered only after the legacy initiative side is selected. */
public final class ObjectiveDecisionResolver {
    private final ObjectiveFightResolver objectiveFights = new ObjectiveFightResolver();
    private final ObjectiveSecureResolver objectiveSecures = new ObjectiveSecureResolver();
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();

    public Optional<MatchEvent> resolve(
            GameState state,
            ObjectiveType type,
            TeamSide initiative,
            double signedPriority,
            Random random,
            ObjectiveResolver objectives,
            StructureResolver structures,
            List<MatchEvent> events,
            ObjectivePriorityDecisionData priorityDecision
    ) {
        int time = state.getCurrentTimeSeconds();
        ObjectiveDecisionContext context = buildContext(state, type, initiative, signedPriority);
        int spawnedAt = spawnedAt(state.getObjectiveState(), type);
        ObjectiveDecisionKey key = new ObjectiveDecisionKey(type, spawnedAt, time, initiative);
        if (!state.getObjectiveDecisionState().reserve(key)) return Optional.empty();
        String decisionActionId = "OBJECTIVE_DECISION:" + type + ":" + spawnedAt + ":"
                + time + ":" + initiative;
        String fightActionId = decisionActionId + ":FIGHT";

        List<ObjectiveDecisionWeightBreakdown> initiativeWeights = initiativeWeights(state, context);
        Selection initiativeSelection = select(initiativeWeights, random);
        ObjectiveDecisionAction initiativeAction = initiativeSelection.action();
        if (initiativeAction == ObjectiveDecisionAction.RESET) {
            ObjectiveDecisionData data = data(state, context, initiativeWeights, initiativeSelection,
                    List.of(), null, null, 0, false, false, false, null, null,
                    ObjectiveDecisionResult.INITIATOR_RESET, false, false, null, null);
            state.getObjectiveDecisionState().record(data);
            return Optional.empty();
        }

        List<ObjectiveDecisionWeightBreakdown> responderWeights = responderWeights(state, context);
        Selection responderSelection = select(responderWeights, random);
        ObjectiveDecisionAction responderAction = responderSelection.action();
        if (responderAction == ObjectiveDecisionAction.CONTEST) {
            state.getCompositionRuntimeState().recordActualAttempt(
                    CompositionActionType.OBJECTIVE_SETUP, initiative, initiative, initiative.opposite(),
                    FightScale.FORMAL, type, true, null, null, time,
                    CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        }
        TeamSide captureSide = initiative;
        TeamSide fightWinner = null;
        boolean contested = false;
        boolean tradeRoll = false;
        boolean tradeSuccess = false;
        double tradeChance = 0;
        boolean majorConsumed = false;
        boolean structureConsumed = false;
        ObjectiveFightSkillImpactData fightSkillImpact = null;
        ObjectiveSecureDecisionData secureDecision = null;
        ObjectiveDecisionContext.TradeTarget tradeTarget = context.tradeTarget(context.responderSide());
        ObjectiveDecisionResult result;

        if (responderAction == ObjectiveDecisionAction.CONTEST) {
            ObjectiveFightOutcome fight = objectiveFights.resolve(
                    state, random, events, fightActionId);
            fightWinner = fight.winningSide();
            fightSkillImpact = fight.skillImpact();
            secureDecision = objectiveSecures.resolve(state, type, fightWinner, random);
            captureSide = secureDecision.selectedCaptureSide();
            contested = true;
            majorConsumed = true;
            result = ObjectiveDecisionResult.CONTEST_FIGHT;
        } else if (responderAction == ObjectiveDecisionAction.TRADE_STRUCTURE) {
            tradeChance = tradeChance(state, context, tradeTarget);
            result = ObjectiveDecisionResult.TRADE_FAILED;
        } else {
            result = ObjectiveDecisionResult.UNCONTESTED_CAPTURE;
        }

        Optional<MatchEvent> capture = capture(state, type, captureSide, random, objectives,
                secureDecision != null && secureDecision.secureWon());
        if (capture.isEmpty()) {
            if (secureDecision != null) secureDecision = secureDecision.withCaptureResult(false);
            ObjectiveDecisionData data = data(state, context, initiativeWeights, initiativeSelection,
                    responderWeights, responderSelection, tradeTarget, tradeChance, tradeRoll, false,
                    contested, fightWinner, null, ObjectiveDecisionResult.STALE_OBJECTIVE,
                    majorConsumed, false, fightSkillImpact, secureDecision);
            state.getObjectiveDecisionState().record(data);
            return Optional.empty();
        }

        events.add(capture.get());
        if (secureDecision != null) secureDecision = secureDecision.withCaptureResult(true);
        capture.get().setActionId(decisionActionId);
        if (contested) capture.get().setParentActionId(fightActionId);

        if (responderAction == ObjectiveDecisionAction.TRADE_STRUCTURE) {
            Optional<StructureAttackRequest> tradeRequest = tradeRequest(
                    context.responderSide(), tradeTarget, decisionActionId);
            if (tradeRequest.isPresent() && structures.canAttemptSiege(state, tradeRequest.get())) {
                tradeRoll = true;
                if (random.nextDouble() < tradeChance) {
                    Optional<StructureAttackResult> attack = structures.attemptSiege(
                            state, tradeRequest.get());
                    structureConsumed = attack.isPresent();
                    tradeSuccess = attack.isPresent();
                    attack.ifPresent(value -> structures.addAttackEvents(state, value, events));
                }
            }
            result = tradeSuccess
                    ? ObjectiveDecisionResult.TRADE_SUCCEEDED
                    : ObjectiveDecisionResult.TRADE_FAILED;
        }

        ObjectiveDecisionData data = data(state, context, initiativeWeights, initiativeSelection,
                responderWeights, responderSelection, tradeTarget, tradeChance, tradeRoll, tradeSuccess,
                contested, fightWinner, captureSide, result, majorConsumed, structureConsumed,
                fightSkillImpact, secureDecision);
        state.getObjectiveDecisionState().record(data);
        capture.get().setObjectivePriorityDecision(priorityDecision);
        capture.get().setObjectiveDecision(data);
        return capture;
    }

    ObjectiveDecisionContext buildContext(GameState state, ObjectiveType type, TeamSide initiative, double signedPriority) {
        int time = state.getCurrentTimeSeconds();
        TeamSide responder = initiative.opposite();
        ObjectiveDecisionContext.TradeTarget blueTrade = findTradeTarget(state, type, TeamSide.BLUE);
        ObjectiveDecisionContext.TradeTarget redTrade = findTradeTarget(state, type, TeamSide.RED);
        return new ObjectiveDecisionContext(time, type, initiative, responder, objectiveAvailable(state, type),
                alive(state.getBlueTeamState(), time), alive(state.getRedTeamState(), time),
                state.getBlueTeamState().getGold(), state.getRedTeamState().getGold(),
                state.getBlueTeamState().getKills(), state.getRedTeamState().getKills(),
                averageTeamfighting(state.getBlueTeamState(), time), averageTeamfighting(state.getRedTeamState(), time),
                relevantFarming(blueTrade), relevantFarming(redTrade),
                state.getBlueTeamState().getDragons(), state.getRedTeamState().getDragons(),
                state.getObjectiveState().getSoulOwner(),
                state.getBlueTeamState().hasActiveBaronBuff(time), state.getRedTeamState().hasActiveBaronBuff(time),
                type == ObjectiveType.ELDER ? 0 : signedPriority, type != ObjectiveType.ELDER,
                !state.wasMajorCombatAttemptedThisTick(),
                !state.wasStructureActionPerformedThisTick(TeamSide.BLUE)
                        && !state.getBaseSiegeState(TeamSide.BLUE).isActive(),
                !state.wasStructureActionPerformedThisTick(TeamSide.RED)
                        && !state.getBaseSiegeState(TeamSide.RED).isActive(),
                blueTrade, redTrade);
    }

    List<ObjectiveDecisionWeightBreakdown> initiativeWeights(ObjectiveDecisionContext context) {
        return initiativeWeights(null, context);
    }

    List<ObjectiveDecisionWeightBreakdown> initiativeWeights(
            GameState state, ObjectiveDecisionContext context) {
        TeamSide side = context.initiativeSide();
        Edges edge = edges(context, side);
        DecisionSkill decisionSkill = objectiveDecisionSkill(state, side, edge);
        double urgency = urgency(context, side);
        int minimumParticipants = minimumAlive(context.objectiveType());
        int availableParticipants = state == null ? context.alive(side)
                : participatingCount(state.getTeamState(side), context.evaluationTimeSeconds());
        boolean takeEligible = context.objectiveAvailable()
                && availableParticipants >= minimumParticipants;
        ObjectiveDecisionIneligibleReason takeReason = !context.objectiveAvailable()
                ? ObjectiveDecisionIneligibleReason.OBJECTIVE_UNAVAILABLE
                : context.alive(side) < minimumParticipants
                ? ObjectiveDecisionIneligibleReason.INSUFFICIENT_ALIVE
                : takeEligible ? null : ObjectiveDecisionIneligibleReason.INSUFFICIENT_COMBAT_PARTICIPANTS;
        double take = ObjectiveDecisionRuleConfig.TAKE_BASE_WEIGHT
                + edge.priority() * ObjectiveDecisionRuleConfig.TAKE_PRIORITY_EDGE_WEIGHT
                + edge.alive() * ObjectiveDecisionRuleConfig.TAKE_ALIVE_EDGE_WEIGHT
                + edge.gold() * ObjectiveDecisionRuleConfig.TAKE_GOLD_EDGE_WEIGHT
                + edge.teamfight() * ObjectiveDecisionRuleConfig.TAKE_TEAMFIGHT_EDGE_WEIGHT
                + urgency + decisionSkill.favorableContribution();
        int missing = 5 - context.alive(side);
        double resetPriority = Math.max(0, -edge.priority()) * ObjectiveDecisionRuleConfig.RESET_BEHIND_PRIORITY_WEIGHT;
        double resetAlive = Math.max(0, -edge.alive()) * ObjectiveDecisionRuleConfig.RESET_BEHIND_ALIVE_WEIGHT;
        double resetGold = Math.max(0, -edge.gold()) * ObjectiveDecisionRuleConfig.RESET_BEHIND_GOLD_WEIGHT;
        double resetMissing = missing * ObjectiveDecisionRuleConfig.RESET_MISSING_PLAYER_WEIGHT;
        double resetDecision = -decisionSkill.favorableContribution();
        double reset = ObjectiveDecisionRuleConfig.RESET_BASE_WEIGHT + resetPriority + resetAlive
                + resetGold + resetMissing + resetDecision;
        return List.of(
                breakdown(ObjectiveDecisionAction.TAKE, ObjectiveDecisionRole.INITIATOR, takeEligible, takeReason,
                        ObjectiveDecisionRuleConfig.TAKE_BASE_WEIGHT, edge,
                        edge.priority() * ObjectiveDecisionRuleConfig.TAKE_PRIORITY_EDGE_WEIGHT,
                        edge.alive() * ObjectiveDecisionRuleConfig.TAKE_ALIVE_EDGE_WEIGHT,
                        edge.gold() * ObjectiveDecisionRuleConfig.TAKE_GOLD_EDGE_WEIGHT,
                        edge.teamfight() * ObjectiveDecisionRuleConfig.TAKE_TEAMFIGHT_EDGE_WEIGHT,
                        0, urgency, 0, 0, take, decisionSkill,
                        decisionSkill.favorableContribution()),
                breakdown(ObjectiveDecisionAction.RESET, ObjectiveDecisionRole.INITIATOR, true, null,
                        ObjectiveDecisionRuleConfig.RESET_BASE_WEIGHT, edge, resetPriority, resetAlive, resetGold,
                        0, 0, 0, resetMissing, 0, reset, decisionSkill, resetDecision)
        );
    }

    List<ObjectiveDecisionWeightBreakdown> responderWeights(GameState state, ObjectiveDecisionContext context) {
        TeamSide side = context.responderSide();
        Edges edge = edges(context, side);
        DecisionSkill decisionSkill = objectiveDecisionSkill(state, side, edge);
        double urgency = urgency(context, side);
        int missing = 5 - context.alive(side);
        int minimumParticipants = minimumAlive(context.objectiveType());
        int ownParticipants = participatingCount(
                state.getTeamState(side), context.evaluationTimeSeconds());
        int enemyParticipants = participatingCount(
                state.getTeamState(side.opposite()), context.evaluationTimeSeconds());
        boolean contest = context.majorCombatAvailable() && context.objectiveAvailable()
                && context.alive(side) >= minimumParticipants
                && ownParticipants >= minimumParticipants
                && enemyParticipants >= minimumParticipants
                && !state.isFinished();
        ObjectiveDecisionIneligibleReason contestReason = !context.majorCombatAvailable()
                ? ObjectiveDecisionIneligibleReason.MAJOR_COMBAT_ALREADY_USED
                : !context.objectiveAvailable() ? ObjectiveDecisionIneligibleReason.OBJECTIVE_UNAVAILABLE
                : context.alive(side) < minimumParticipants
                ? ObjectiveDecisionIneligibleReason.INSUFFICIENT_ALIVE
                : state.isFinished() ? ObjectiveDecisionIneligibleReason.GAME_FINISHED
                : ownParticipants < minimumParticipants || enemyParticipants < minimumParticipants
                ? ObjectiveDecisionIneligibleReason.INSUFFICIENT_COMBAT_PARTICIPANTS : contest ? null
                : ObjectiveDecisionIneligibleReason.INSUFFICIENT_ALIVE;
        double contestWeight = ObjectiveDecisionRuleConfig.CONTEST_BASE_WEIGHT
                + edge.priority() * ObjectiveDecisionRuleConfig.CONTEST_PRIORITY_EDGE_WEIGHT
                + edge.alive() * ObjectiveDecisionRuleConfig.CONTEST_ALIVE_EDGE_WEIGHT
                + edge.gold() * ObjectiveDecisionRuleConfig.CONTEST_GOLD_EDGE_WEIGHT
                + edge.teamfight() * ObjectiveDecisionRuleConfig.CONTEST_TEAMFIGHT_EDGE_WEIGHT
                + urgency + decisionSkill.favorableContribution();
        double givePriority = Math.max(0, -edge.priority()) * ObjectiveDecisionRuleConfig.GIVE_BEHIND_PRIORITY_WEIGHT;
        double giveAlive = Math.max(0, -edge.alive()) * ObjectiveDecisionRuleConfig.GIVE_BEHIND_ALIVE_WEIGHT;
        double giveGold = Math.max(0, -edge.gold()) * ObjectiveDecisionRuleConfig.GIVE_BEHIND_GOLD_WEIGHT;
        double giveMissing = missing * ObjectiveDecisionRuleConfig.GIVE_MISSING_PLAYER_WEIGHT;
        double giveDecision = -decisionSkill.favorableContribution();
        double giveWeight = ObjectiveDecisionRuleConfig.GIVE_BASE_WEIGHT + givePriority + giveAlive
                + giveGold + giveMissing + giveDecision;
        ObjectiveDecisionContext.TradeTarget target = context.tradeTarget(side);
        boolean trade = context.structureAvailable(side) && target != null && !state.isFinished();
        ObjectiveDecisionIneligibleReason tradeReason = !context.structureAvailable(side)
                ? ObjectiveDecisionIneligibleReason.STRUCTURE_ACTION_ALREADY_USED
                : state.isFinished() ? ObjectiveDecisionIneligibleReason.GAME_FINISHED
                : target == null ? tradeTargetIneligibleReason(
                        state, context.objectiveType(), side) : null;
        double farmEdge = farmingEdge(state, side, target);
        double availability = trade ? ObjectiveDecisionRuleConfig.TRADE_STRUCTURE_AVAILABLE_BONUS : 0;
        double tradePriority = Math.max(0, -edge.priority()) * ObjectiveDecisionRuleConfig.TRADE_BEHIND_PRIORITY_WEIGHT;
        double tradeFarm = farmEdge * ObjectiveDecisionRuleConfig.TRADE_FARMING_EDGE_WEIGHT;
        double tradeWeight = ObjectiveDecisionRuleConfig.TRADE_STRUCTURE_BASE_WEIGHT + availability + tradeFarm + tradePriority;
        return List.of(
                breakdown(ObjectiveDecisionAction.CONTEST, ObjectiveDecisionRole.RESPONDER, contest, contestReason,
                        ObjectiveDecisionRuleConfig.CONTEST_BASE_WEIGHT, edge,
                        edge.priority() * ObjectiveDecisionRuleConfig.CONTEST_PRIORITY_EDGE_WEIGHT,
                        edge.alive() * ObjectiveDecisionRuleConfig.CONTEST_ALIVE_EDGE_WEIGHT,
                        edge.gold() * ObjectiveDecisionRuleConfig.CONTEST_GOLD_EDGE_WEIGHT,
                        edge.teamfight() * ObjectiveDecisionRuleConfig.CONTEST_TEAMFIGHT_EDGE_WEIGHT,
                        0, urgency, 0, 0, contestWeight, decisionSkill,
                        decisionSkill.favorableContribution()),
                breakdown(ObjectiveDecisionAction.GIVE, ObjectiveDecisionRole.RESPONDER, true, null,
                        ObjectiveDecisionRuleConfig.GIVE_BASE_WEIGHT, edge, givePriority, giveAlive, giveGold,
                        0, 0, 0, giveMissing, 0, giveWeight, decisionSkill, giveDecision),
                breakdown(ObjectiveDecisionAction.TRADE_STRUCTURE, ObjectiveDecisionRole.RESPONDER, trade, tradeReason,
                        ObjectiveDecisionRuleConfig.TRADE_STRUCTURE_BASE_WEIGHT, new Edges(edge.priority(), edge.alive(), edge.gold(), edge.teamfight(), farmEdge), tradePriority, 0, 0,
                        0, tradeFarm, 0, 0, availability, tradeWeight, decisionSkill, 0)
        );
    }

    private ObjectiveDecisionWeightBreakdown breakdown(
            ObjectiveDecisionAction action, ObjectiveDecisionRole role, boolean eligible,
            ObjectiveDecisionIneligibleReason reason, double base, Edges edge,
            double priorityContribution, double aliveContribution, double goldContribution,
            double teamfightContribution, double farmingContribution, double urgency,
            double missing, double tradeAvailability, double weight,
            DecisionSkill decisionSkill, double decisionContribution
    ) {
        return new ObjectiveDecisionWeightBreakdown(action, role, eligible, reason, base,
                edge.priority(), priorityContribution, edge.alive(), aliveContribution,
                edge.gold(), goldContribution, edge.teamfight(), teamfightContribution,
                edge.farming(), farmingContribution, urgency, missing, tradeAvailability,
                clamp(weight, ObjectiveDecisionRuleConfig.MIN_DECISION_WEIGHT,
                        ObjectiveDecisionRuleConfig.MAX_DECISION_WEIGHT),
                decisionSkill.score(), decisionSkill.favorability(), decisionContribution);
    }

    private Selection select(List<ObjectiveDecisionWeightBreakdown> candidates, Random random) {
        List<ObjectiveDecisionWeightBreakdown> eligible = candidates.stream().filter(ObjectiveDecisionWeightBreakdown::eligible).toList();
        if (eligible.size() == 1) return new Selection(eligible.getFirst().action(), false, null);
        double total = eligible.stream().mapToDouble(ObjectiveDecisionWeightBreakdown::finalWeight).sum();
        double roll = random.nextDouble();
        double cursor = 0;
        for (ObjectiveDecisionWeightBreakdown candidate : eligible) {
            cursor += candidate.finalWeight() / total;
            if (roll <= cursor) return new Selection(candidate.action(), true, roll);
        }
        return new Selection(eligible.getLast().action(), true, roll);
    }

    private Optional<MatchEvent> capture(GameState state, ObjectiveType type, TeamSide side, Random random,
                                         ObjectiveResolver resolver, boolean stolen) {
        boolean firstMessage = random.nextBoolean();
        String message = stolen
                ? firstMessage ? "교전 중 상대의 마무리를 가로채 확보합니다."
                        : "정교한 목표물 마무리로 상대에게서 빼앗습니다."
                : firstMessage ? "시야와 인원 우위를 바탕으로 확보합니다."
                        : "지역 주도권을 바탕으로 처치합니다.";
        return switch (type) {
            case DRAGON -> resolver.captureDragon(state, side, state.getCurrentTimeSeconds(), DragonCaptureSource.GENERAL, message);
            case BARON -> resolver.captureBaron(state, side, state.getCurrentTimeSeconds(), message);
            case ELDER -> resolver.captureElder(state, side, state.getCurrentTimeSeconds(), message).map(ElderCaptureOutcome::event);
        };
    }

    private ObjectiveDecisionData data(
            GameState state, ObjectiveDecisionContext context,
            List<ObjectiveDecisionWeightBreakdown> initiativeWeights, Selection initiativeSelection,
            List<ObjectiveDecisionWeightBreakdown> responderWeights, Selection responderSelection,
            ObjectiveDecisionContext.TradeTarget tradeTarget, double tradeChance, boolean tradeRoll,
            boolean tradeSuccess, boolean contested, TeamSide fightWinner, TeamSide captureSide,
            ObjectiveDecisionResult result, boolean majorConsumed, boolean structureConsumed,
            ObjectiveFightSkillImpactData fightSkillImpact,
            ObjectiveSecureDecisionData secureDecision
    ) {
        return new ObjectiveDecisionData(state.getObjectiveDecisionState().nextSequence(),
                context.evaluationTimeSeconds(), context.objectiveType(), true,
                context.initiativeSide(), context.responderSide(), initiativeWeights, initiativeSelection.action(),
                initiativeSelection.rollExecuted(), initiativeSelection.roll(), responderWeights,
                responderSelection == null ? null : responderSelection.action(),
                responderSelection != null && responderSelection.rollExecuted(),
                responderSelection == null ? null : responderSelection.roll(),
                tradeTarget == null ? null : tradeTarget.lane(),
                tradeTarget == null ? null : tradeTarget.towerTier(),
                tradeChance, tradeRoll, tradeSuccess, contested, fightWinner, captureSide, result,
                majorConsumed, structureConsumed, nextAttempt(state.getObjectiveState(), context.objectiveType()),
                false, context.priorityAvailable(), fightSkillImpact, secureDecision);
    }

    private ObjectiveDecisionContext.TradeTarget findTradeTarget(GameState state, ObjectiveType type, TeamSide side) {
        if (state.wasStructureActionPerformedThisTick(side) || state.isFinished()
                || state.getBaseSiegeState(side).isActive()) return null;
        Lane[] order = type == ObjectiveType.BARON
                ? new Lane[]{Lane.BOT, Lane.MID, Lane.TOP}
                : new Lane[]{Lane.TOP, Lane.MID, Lane.BOT};
        int time = state.getCurrentTimeSeconds();
        for (Lane lane : order) {
            Position position = switch (lane) { case TOP -> Position.TOP; case MID -> Position.MID; case BOT -> Position.ADC; };
            Optional<PlayerState> pusher = state.getTeamState(side).findPlayerAt(position);
            Optional<TowerTier> target = state.getMapState().getLaneState(side.opposite(), lane).nextAliveTower();
            LaneWaveState wave = state.getMapState().getWaveState(side, lane);
            if (target.isPresent() && pusher.isPresent() && pusher.get().isAlive(time)
                    && pusher.get().canParticipateInMajorCombatAt(time)
                    && !state.wasMajorCombatParticipantThisTick(pusher.get())
                    && (wave.hasActiveWaveAt(time) || wave.canPrepareAt(time))) {
                return new ObjectiveDecisionContext.TradeTarget(lane, target.get(), pusher.get());
            }
        }
        return null;
    }

    private ObjectiveDecisionIneligibleReason tradeTargetIneligibleReason(
            GameState state, ObjectiveType type, TeamSide side) {
        if (state.wasStructureActionPerformedThisTick(side)
                || state.getBaseSiegeState(side).isActive()) {
            return ObjectiveDecisionIneligibleReason.STRUCTURE_ACTION_ALREADY_USED;
        }
        Lane[] order = type == ObjectiveType.BARON
                ? new Lane[]{Lane.BOT, Lane.MID, Lane.TOP}
                : new Lane[]{Lane.TOP, Lane.MID, Lane.BOT};
        int time = state.getCurrentTimeSeconds();
        boolean dead = false;
        boolean activityUnavailable = false;
        boolean combatParticipant = false;
        for (Lane lane : order) {
            if (state.getMapState().getLaneState(side.opposite(), lane).nextAliveTower().isEmpty()) {
                continue;
            }
            Position position = switch (lane) {
                case TOP -> Position.TOP;
                case MID -> Position.MID;
                case BOT -> Position.ADC;
            };
            Optional<PlayerState> pusher = state.getTeamState(side).findPlayerAt(position);
            if (pusher.isEmpty()) continue;
            if (!pusher.get().isAlive(time)) dead = true;
            else if (!pusher.get().canParticipateInMajorCombatAt(time)) activityUnavailable = true;
            else if (state.wasMajorCombatParticipantThisTick(pusher.get())) combatParticipant = true;
            else {
                LaneWaveState wave = state.getMapState().getWaveState(side, lane);
                if (wave.hasActiveWaveAt(time) || wave.canPrepareAt(time)) return null;
            }
        }
        if (activityUnavailable) return ObjectiveDecisionIneligibleReason.PUSHER_ACTIVITY_UNAVAILABLE;
        if (combatParticipant) return ObjectiveDecisionIneligibleReason.PUSHER_COMBAT_PARTICIPANT;
        if (dead) return ObjectiveDecisionIneligibleReason.PUSHER_DEAD;
        return ObjectiveDecisionIneligibleReason.NO_TRADE_TARGET;
    }

    private Optional<StructureAttackRequest> tradeRequest(
            TeamSide side, ObjectiveDecisionContext.TradeTarget target,
            String parentActionId) {
        if (target == null) return Optional.empty();
        LateGameStructureTarget requested = switch (target.towerTier()) {
            case OUTER -> LateGameStructureTarget.OUTER;
            case INNER -> LateGameStructureTarget.INNER;
            case INHIBITOR -> LateGameStructureTarget.INHIBITOR_TOWER;
        };
        return Optional.of(StructureAttackRequest.siege(
                side, target.lane(), requested, PushReason.OBJECTIVE_TRADE,
                java.util.Set.of(target.primaryPusher().getPosition()), parentActionId));
    }

    private double tradeChance(GameState state, ObjectiveDecisionContext context, ObjectiveDecisionContext.TradeTarget target) {
        TeamSide side = context.responderSide();
        int aliveRaw = context.alive(side) - context.alive(side.opposite());
        double chance = ObjectiveDecisionRuleConfig.TRADE_STRUCTURE_PUSH_BASE_CHANCE
                + goldEdge(context, side) * ObjectiveDecisionRuleConfig.TRADE_GOLD_EDGE_BONUS_MAX
                + aliveRaw * ObjectiveDecisionRuleConfig.TRADE_ALIVE_EDGE_BONUS_PER_PLAYER
                + farmingEdge(state, side, target) * ObjectiveDecisionRuleConfig.TRADE_FARMING_EDGE_BONUS_MAX
                + (context.hasBaron(side) ? ObjectiveDecisionRuleConfig.TRADE_BARON_BUFF_BONUS : 0);
        return clamp(chance, ObjectiveDecisionRuleConfig.MIN_TRADE_STRUCTURE_CHANCE,
                ObjectiveDecisionRuleConfig.MAX_TRADE_STRUCTURE_CHANCE);
    }

    private Edges edges(ObjectiveDecisionContext context, TeamSide side) {
        double priority = context.priorityAvailable()
                ? clamp((side == TeamSide.BLUE ? context.signedObjectivePriority() : -context.signedObjectivePriority()) / 100.0, -1, 1)
                : 0;
        return new Edges(priority,
                clamp((context.alive(side) - context.alive(side.opposite())) / ObjectiveDecisionRuleConfig.ALIVE_EDGE_NORMALIZER, -1, 1),
                goldEdge(context, side),
                clamp((context.teamfighting(side) - context.teamfighting(side.opposite()))
                        / ObjectiveDecisionRuleConfig.ATTRIBUTE_EDGE_NORMALIZER, -1, 1), 0);
    }

    private double goldEdge(ObjectiveDecisionContext context, TeamSide side) {
        return clamp((context.gold(side) - context.gold(side.opposite()))
                / ObjectiveDecisionRuleConfig.GOLD_EDGE_NORMALIZER, -1, 1);
    }

    private double urgency(ObjectiveDecisionContext context, TeamSide side) {
        return switch (context.objectiveType()) {
            case DRAGON -> (context.dragonStacks(side) == 3 ? ObjectiveDecisionRuleConfig.OWN_SOUL_POINT_URGENCY_BONUS : 0)
                    + (context.dragonStacks(side.opposite()) == 3 ? ObjectiveDecisionRuleConfig.ENEMY_SOUL_POINT_DENIAL_BONUS : 0);
            case BARON -> context.evaluationTimeSeconds() >= ObjectiveDecisionRuleConfig.BARON_LATE_GAME_START_SECONDS
                    ? ObjectiveDecisionRuleConfig.BARON_LATE_GAME_URGENCY_BONUS : 0;
            case ELDER -> ObjectiveDecisionRuleConfig.ELDER_URGENCY_BONUS;
        };
    }

    private double farmingEdge(GameState state, TeamSide side, ObjectiveDecisionContext.TradeTarget target) {
        if (target == null) return 0;
        Position position = target.primaryPusher().getPosition();
        PlayerState ownPlayer = state.getTeamState(side).playerAt(position);
        PlayerState enemyPlayer = state.getTeamState(side.opposite()).playerAt(position);
        double own = ownPlayer.hasMatchPerformance() ? playerSkills.sideLane(ownPlayer) : ownPlayer.getFarming();
        double enemy = enemyPlayer.hasMatchPerformance() ? playerSkills.sideLane(enemyPlayer) : enemyPlayer.getFarming();
        return clamp((own - enemy) / ObjectiveDecisionRuleConfig.ATTRIBUTE_EDGE_NORMALIZER, -1, 1);
    }

    private double farmingEdgeFromContext(ObjectiveDecisionContext context, TeamSide side,
                                          ObjectiveDecisionContext.TradeTarget target) {
        if (target == null) return 0;
        return clamp((context.farming(side) - context.farming(side.opposite()))
                / ObjectiveDecisionRuleConfig.ATTRIBUTE_EDGE_NORMALIZER, -1, 1);
    }

    private double relevantFarming(ObjectiveDecisionContext.TradeTarget target) {
        return target == null ? 0 : target.primaryPusher().getFarming();
    }

    private double averageTeamfighting(TeamState team, int time) {
        double sum = 0; int count = 0;
        for (PlayerState player : team.getPlayers()) {
            if (player.canParticipateInMajorCombatAt(time)) {
                sum += player.getTeamfighting();
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private int participatingCount(TeamState team, int time) {
        return (int) team.getPlayers().stream()
                .filter(player -> player.canParticipateInMajorCombatAt(time)).count();
    }

    private int alive(TeamState team, int time) {
        return (int) team.getPlayers().stream().filter(player -> player.isAlive(time)).count();
    }

    private int minimumAlive(ObjectiveType type) { return type == ObjectiveType.DRAGON ? 3 : 4; }
    private boolean objectiveAvailable(GameState state, ObjectiveType type) {
        return !state.isFinished() && switch (type) {
            case DRAGON -> state.getObjectiveState().isElementalDragonPhase() && state.getObjectiveState().isDragonAlive();
            case BARON -> state.getObjectiveState().isBaronAlive();
            case ELDER -> state.getObjectiveState().isElderPhase() && state.getObjectiveState().isElderAlive();
        };
    }
    private int spawnedAt(ObjectiveState state, ObjectiveType type) {
        return switch (type) { case DRAGON -> state.getDragonSpawnedAtSeconds(); case BARON -> state.getBaronSpawnedAtSeconds(); case ELDER -> state.getElderSpawnedAtSeconds(); };
    }
    private int nextAttempt(ObjectiveState state, ObjectiveType type) {
        return switch (type) { case DRAGON -> state.getNextDragonAttemptSeconds(); case BARON -> state.getNextBaronAttemptSeconds(); case ELDER -> state.getNextElderAttemptSeconds(); };
    }
    private DecisionSkill objectiveDecisionSkill(GameState state, TeamSide side, Edges edge) {
        double favorability = clamp(
                edge.priority() * ObjectivePlayerSkillRuleConfig.DECISION_PRIORITY_FAVORABILITY_WEIGHT
                        + edge.alive() * ObjectivePlayerSkillRuleConfig.DECISION_ALIVE_FAVORABILITY_WEIGHT
                        + edge.gold() * ObjectivePlayerSkillRuleConfig.DECISION_GOLD_FAVORABILITY_WEIGHT
                        + edge.teamfight() * ObjectivePlayerSkillRuleConfig.DECISION_TEAMFIGHT_FAVORABILITY_WEIGHT,
                -1, 1);
        if (state == null) {
            return new DecisionSkill(ObjectivePlayerSkillRuleConfig.BASELINE_SKILL, favorability, 0);
        }
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        if (!jungler.hasMatchPerformance()
                || !jungler.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())) {
            return new DecisionSkill(ObjectivePlayerSkillRuleConfig.BASELINE_SKILL, favorability, 0);
        }
        double score = playerSkills.objectiveDecision(jungler);
        double contribution = (score - ObjectivePlayerSkillRuleConfig.BASELINE_SKILL)
                * favorability * ObjectivePlayerSkillRuleConfig.DECISION_WEIGHT_PER_SKILL_POINT;
        return new DecisionSkill(score, favorability, contribution);
    }

    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private record Edges(double priority, double alive, double gold, double teamfight, double farming) { }
    private record DecisionSkill(double score, double favorability, double favorableContribution) { }
    private record Selection(ObjectiveDecisionAction action, boolean rollExecuted, Double roll) { }
}

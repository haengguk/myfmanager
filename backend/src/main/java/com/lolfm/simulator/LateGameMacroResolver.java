package com.lolfm.simulator;

import com.lolfm.domain.BaseThreatSnapshot;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.LateGameDecisionData;
import com.lolfm.domain.LateGameDefenseWeightBreakdown;
import com.lolfm.domain.LateGameInitiativeCandidate;
import com.lolfm.domain.LateGamePlanWeightBreakdown;
import com.lolfm.domain.LateGameRespawnSummary;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Stateless late-game evaluator/executor.
 *
 * <p>Targets, participants, waves and structure slots are prepared before
 * initiative selection. Random order for an eligible action is initiative,
 * plan, response, fight trigger, fight, structure outcome and cross-map outcome.
 */
public final class LateGameMacroResolver {
    private final BaseThreatEvaluator threats = new BaseThreatEvaluator();
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();

    public void expirePlans(GameState state) {
        state.getLateGameState().expire(state.getCurrentTimeSeconds());
    }

    public Optional<MatchEvent> transitionIfDue(GameState state,
                                                MidGameMacroResolver midGame) {
        Optional<MatchEvent> event = new LanePhaseResolver().transitionToLateGameIfDue(state);
        if (event.isPresent()) {
            LateGameTransitionReason reason = state.getLanePhaseState()
                    .getLateGameTransitionReason();
            state.getLateGameState().start(state.getCurrentTimeSeconds(), reason);
            midGame.onLateGameTransition(state);
        }
        return event;
    }

    public void onMatchFinished(GameState state) {
        state.getLateGameState().finish();
    }

    public void resolveDue(
            GameState state,
            Team blue,
            Team red,
            Random random,
            List<MatchEvent> events,
            StructureResolver structures,
            TeamfightResolver fights
    ) {
        LateGameState lateGame = state.getLateGameState();
        int time = state.getCurrentTimeSeconds();
        if (!lateGame.isEnabled() || state.isFinished()
                || state.getLanePhaseState().getMatchPhase() != MatchPhase.LATE_GAME
                || !lateGame.due(time)) {
            return;
        }

        int due = lateGame.beginEvaluation(time);
        Context context = context(state, time);
        EnumMap<TeamSide, PreparedSide> prepared = new EnumMap<>(TeamSide.class);
        List<LateGameInitiativeCandidate> initiatives = new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            PreparedSide sidePlans = preparePlans(context, side, structures);
            prepared.put(side, sidePlans);
            initiatives.add(initiative(context, sidePlans));
        }
        List<LateGameInitiativeCandidate> eligibleInitiatives = initiatives.stream()
                .filter(LateGameInitiativeCandidate::eligible)
                .toList();
        if (eligibleInitiatives.isEmpty()) {
            lateGame.getStats().noInitiative();
            LateGameDecisionData decision = emptyDecision(
                    lateGame, due, time, initiatives, context,
                    LateGameActionResult.NO_INITIATIVE);
            lateGame.record(decision);
            return;
        }

        boolean initiativeRoll = eligibleInitiatives.size() > 1;
        TeamSide attacker = initiativeRoll
                ? weightedInitiative(eligibleInitiatives, random)
                : eligibleInitiatives.getFirst().side();
        if (initiativeRoll) lateGame.getStats().initiativeRoll();
        TeamSide defender = attacker.opposite();

        List<PlanCandidate> planCandidates = prepared.get(attacker).plans();
        List<PlanCandidate> eligiblePlans = planCandidates.stream()
                .filter(candidate -> candidate.weight().eligible())
                .toList();
        boolean planRoll = eligiblePlans.size() > 1;
        PlanCandidate selected = planRoll
                ? weightedPlan(eligiblePlans, random)
                : eligiblePlans.getFirst();
        if (planRoll) lateGame.getStats().planRoll();

        if (selected.plan() == LateGameAttackPlan.RESET_AND_REGROUP) {
            lateGame.teamPlan(attacker).beginAttack(
                    selected.plan(), null, null, Set.of(), time);
            lateGame.teamPlan(attacker).setResult(LateGameActionResult.ATTACKER_RESET);
            LateGameDecisionData decision = decision(
                    lateGame, due, time, initiatives, attacker, initiativeRoll,
                    planCandidates, selected, planRoll, List.of(), null, false,
                    context, null, 0, false, null, null,
                    0, false, false, null, null,
                    0, false, false, LateGameActionResult.ATTACKER_RESET,
                    false, false, false);
            lateGame.record(decision);
            return;
        }

        CrossTarget cross = crossTarget(
                context, defender, selected.lane(), structures);
        Set<Position> defenders = availablePositions(
                state, defender, List.of(Position.TOP, Position.JUNGLE,
                        Position.MID, Position.ADC, Position.SUPPORT));
        List<ResponseCandidate> responses = new ArrayList<>();
        for (LateGameDefenseResponse response : LateGameDefenseResponse.values()) {
            responses.add(response(
                    context, attacker, selected, response, cross, defenders));
        }
        List<ResponseCandidate> eligibleResponses = responses.stream()
                .filter(candidate -> candidate.weight().eligible())
                .toList();
        boolean responseRoll = eligibleResponses.size() > 1;
        ResponseCandidate response = responseRoll
                ? weightedResponse(eligibleResponses, random)
                : eligibleResponses.getFirst();
        if (responseRoll) lateGame.getStats().responseRoll();

        lateGame.teamPlan(attacker).beginAttack(
                selected.plan(), selected.lane(), selected.target(),
                selected.assigned(), time);
        lateGame.teamPlan(defender).beginDefense(
                response.response(), selected.lane(), selected.target(),
                defenders, time);

        String actionId = "LATE_GAME:" + lateGame.getEvaluationSequence();
        int eventStart = events.size();
        Resolution resolution = resolve(
                state, blue, red, random, events, structures, fights, context,
                attacker, selected, response, cross, actionId);
        lateGame.teamPlan(attacker).setResult(resolution.result());
        lateGame.teamPlan(defender).setResult(resolution.result());

        LateGameDecisionData decision = decision(
                lateGame, due, time, initiatives, attacker, initiativeRoll,
                planCandidates, selected, planRoll, responses, response, responseRoll,
                context, respawn(context, defender), resolution.fightChance(),
                resolution.fightTriggered(), resolution.grade(),
                resolution.fightWinner(), resolution.structureChance(),
                resolution.structureRoll(), resolution.structureSuccess(),
                resolution.crossLane(), resolution.crossTarget(),
                resolution.crossChance(), resolution.crossRoll(),
                resolution.crossSuccess(), resolution.result(),
                resolution.majorCombat(), resolution.attackerStructure(),
                resolution.defenderStructure());
        lateGame.record(decision);

        if (resolution.attempted()) {
            for (int index = eventStart; index < events.size(); index++) {
                MatchEvent child = events.get(index);
                if (child.getParentActionId() == null) child.setParentActionId(actionId);
            }
            MatchEvent event = new MatchEvent(
                    time, MatchEventType.LATE_GAME_ACTION,
                    lateGameMessage(state, decision), null, null, List.of());
            event.setActionId(actionId);
            event.setLateGameDecision(decision);
            events.add(event);
        }
    }

    Resolution resolveSelected(
            GameState state,
            Team blue,
            Team red,
            Random random,
            List<MatchEvent> events,
            StructureResolver structures,
            TeamfightResolver fights,
            TeamSide attacker,
            LateGameAttackPlan attackPlan,
            LateGameDefenseResponse defenseResponse
    ) {
        Context context = context(state, state.getCurrentTimeSeconds());
        PlanCandidate selected = plan(context, attacker, attackPlan, structures);
        if (!selected.weight().eligible()
                || selected.plan() == LateGameAttackPlan.RESET_AND_REGROUP) {
            return Resolution.ineligible();
        }
        Set<Position> defenders = availablePositions(
                state, attacker.opposite(), List.of(Position.TOP, Position.JUNGLE,
                        Position.MID, Position.ADC, Position.SUPPORT));
        CrossTarget cross = crossTarget(
                context, attacker.opposite(), selected.lane(), structures);
        ResponseCandidate response = response(
                context, attacker, selected, defenseResponse, cross, defenders);
        if (!response.weight().eligible()) return Resolution.ineligible();
        String parentActionId = "LATE_GAME_SELECTED:"
                + state.getCurrentTimeSeconds() + ":" + attacker + ":" + attackPlan;
        return resolve(state, blue, red, random, events, structures, fights,
                context, attacker, selected, response, cross, parentActionId);
    }

    private Resolution resolve(
            GameState state,
            Team blue,
            Team red,
            Random random,
            List<MatchEvent> events,
            StructureResolver structures,
            TeamfightResolver fights,
            Context context,
            TeamSide attacker,
            PlanCandidate plan,
            ResponseCandidate response,
            CrossTarget cross,
            String parentActionId
    ) {
        if (!currentTargetMatches(state, attacker, plan)
                || !canAttemptPlan(state, attacker, plan, structures, parentActionId)) {
            return Resolution.ineligible();
        }

        recordSiegeAttempt(state, attacker, plan.lane());
        boolean nexus = plan.plan() == LateGameAttackPlan.NEXUS_FINISH;
        double fightChance = 0;
        boolean fightTriggered = false;
        TeamfightOutcome fight = null;

        if (response.response() == LateGameDefenseResponse.DEFEND
                && !state.wasMajorCombatAttemptedThisTick()
                && (!nexus || state.getLateGameState().canAttemptBaseDefenseCombat(
                        attacker, state.getCurrentTimeSeconds()))) {
            if (nexus) {
                fightTriggered = true;
            } else {
                fightChance = siegeFightChance(context, attacker);
                fightTriggered = random.nextDouble() < fightChance;
            }
            if (fightTriggered) {
                state.getLateGameState().getStats().fight();
                fight = fights.resolveForcedTeamfight(
                        state, blue, red, random, events,
                        nexus ? CombatSource.BASE_DEFENSE : CombatSource.LATE_GAME_SIEGE,
                        attacker).orElse(null);
                if (fight != null && nexus) {
                    state.getLateGameState().recordBaseDefenseCombat(
                            attacker, state.getCurrentTimeSeconds());
                }
                if (fight != null && fight.winningSide() != attacker) {
                    markFailedStructureAttempt(state, attacker);
                    state.blockStructurePushUntil(
                            attacker, state.getCurrentTimeSeconds()
                                    + LateGameRuleConfig.POST_REPEL_MACRO_PUSH_BLOCK_SECONDS);
                    return new Resolution(
                            true, false, fightChance, true, fight.grade(),
                            fight.winningSide(), 0, false, false,
                            null, null, 0, false, false,
                            LateGameActionResult.SIEGE_FIGHT_DEFENDER_WIN,
                            true, false, false);
                }
            }
        }

        Set<Position> survivingAttackers = survivingAssigned(
                state, attacker, plan.assigned());
        if (survivingAttackers.size() < LateGameRuleConfig.MIN_SIEGE_ATTACKERS) {
            markFailedStructureAttempt(state, attacker);
            return new Resolution(
                    true, false, fightChance, fightTriggered,
                    fight == null ? null : fight.grade(),
                    fight == null ? null : fight.winningSide(),
                    0, false, false, null, null,
                    0, false, false, LateGameActionResult.SIEGE_REPELLED,
                    fight != null, false, false);
        }

        if (nexus) {
            return resolveNexusFinish(
                    state, random, events, structures, context, attacker, plan,
                    survivingAttackers, parentActionId, fightChance,
                    fightTriggered, fight);
        }

        double structureChance = 1.0;
        boolean structureRoll = false;
        boolean attackAllowed = true;
        if (response.response() == LateGameDefenseResponse.DEFEND && !fightTriggered) {
            structureChance = nonCombatChance(context, attacker);
            structureRoll = true;
            attackAllowed = random.nextDouble() < structureChance;
        }

        StructureAttackResult attack = null;
        if (attackAllowed) {
            StructureAttackRequest request = requestForPlan(
                    attacker, plan, survivingAttackers, parentActionId,
                    PushReason.LATE_GAME_SIEGE);
            attack = structures.attemptSiege(state, request).orElse(null);
            if (attack != null) {
                structures.addAttackEvents(state, attack, events);
                state.getLateGameState().getStats().structure();
            }
        }
        if (attack == null) markFailedStructureAttempt(state, attacker);

        CrossResolution crossResolution = resolveCrossMap(
                state, random, events, structures, context, attacker, response,
                cross, parentActionId);
        LateGameActionResult result;
        if (attack == null) {
            result = LateGameActionResult.SIEGE_REPELLED;
        } else if (fight != null) {
            result = LateGameActionResult.SIEGE_FIGHT_ATTACKER_WIN;
        } else if (crossResolution.success()) {
            result = LateGameActionResult.CROSS_MAP_SUCCEEDED;
        } else if (attack.destroyed()) {
            result = LateGameActionResult.STRUCTURE_DESTROYED;
        } else {
            result = LateGameActionResult.STRUCTURE_DAMAGED;
        }
        return new Resolution(
                true, attack != null || crossResolution.success(), fightChance,
                fightTriggered, fight == null ? null : fight.grade(),
                fight == null ? null : fight.winningSide(), structureChance,
                structureRoll, attack != null, crossResolution.lane(),
                crossResolution.target(), crossResolution.chance(),
                crossResolution.roll(), crossResolution.success(), result,
                fight != null, attack != null, crossResolution.success());
    }

    private Resolution resolveNexusFinish(
            GameState state,
            Random random,
            List<MatchEvent> events,
            StructureResolver structures,
            Context context,
            TeamSide attacker,
            PlanCandidate plan,
            Set<Position> participants,
            String parentActionId,
            double fightChance,
            boolean fightTriggered,
            TeamfightOutcome fight
    ) {
        state.getLateGameState().getStats().finishAttempt();
        double chance = finishChance(context, attacker);
        boolean success = random.nextDouble() < chance;
        StructureAttackResult attack = null;
        if (success) {
            StructureAttackRequest request = requestForPlan(
                    attacker, plan, participants, parentActionId,
                    PushReason.NEXUS_FINISH);
            attack = structures.attemptSiege(state, request).orElse(null);
            if (attack != null) {
                structures.addAttackEvents(state, attack, events);
                state.getLateGameState().getStats().structure();
                if (attack.destruction() != null
                        && attack.destruction().structureKind() == StructureKind.NEXUS) {
                    state.getLateGameState().getStats().nexusDestroyed();
                }
            }
        }
        if (attack == null) markFailedStructureAttempt(state, attacker);
        LateGameActionResult result = attack == null
                ? LateGameActionResult.SIEGE_REPELLED
                : attack.destruction() != null && attack.destruction().gameEnded()
                ? LateGameActionResult.NEXUS_DESTROYED
                : LateGameActionResult.NEXUS_FINISH_ADVANCED;
        return new Resolution(
                true, attack != null, fightChance, fightTriggered,
                fight == null ? null : fight.grade(),
                fight == null ? null : fight.winningSide(), chance, true,
                attack != null, null, null, 0, false, false, result,
                fight != null, attack != null, false);
    }

    private CrossResolution resolveCrossMap(
            GameState state,
            Random random,
            List<MatchEvent> events,
            StructureResolver structures,
            Context context,
            TeamSide attacker,
            ResponseCandidate response,
            CrossTarget cross,
            String parentActionId
    ) {
        if (response.response() != LateGameDefenseResponse.CROSS_MAP_PUSH
                || cross == null) {
            return CrossResolution.none();
        }
        TeamSide crossSide = attacker.opposite();
        Set<Position> participants = Set.of(cross.pusher());
        StructureAttackRequest request = crossMapRequest(
                state, crossSide, cross.lane(), cross.target(), participants, parentActionId);
        if (!structures.canAttemptSiege(state, request)) return CrossResolution.none();

        recordSiegeAttempt(state, crossSide, cross.lane());
        double chance = crossMapChance(context, crossSide, cross.lane());
        boolean successRoll = random.nextDouble() < chance;
        StructureAttackResult attack = successRoll
                ? structures.attemptSiege(state, request).orElse(null)
                : null;
        boolean success = attack != null;
        if (attack != null) {
            structures.addAttackEvents(state, attack, events);
            state.getLateGameState().getStats().structure();
        } else {
            markFailedStructureAttempt(state, crossSide);
        }
        state.getLateGameState().getStats().crossMap(success);
        return new CrossResolution(
                cross.lane(), cross.target(), chance, true, success);
    }

    private PreparedSide preparePlans(Context context, TeamSide side,
                                      StructureResolver structures) {
        List<PlanCandidate> plans = new ArrayList<>();
        boolean attackEligible = false;
        for (LateGameAttackPlan attackPlan : LateGameAttackPlan.values()) {
            PlanCandidate candidate = plan(context, side, attackPlan, structures);
            plans.add(candidate);
            if (attackPlan != LateGameAttackPlan.RESET_AND_REGROUP
                    && candidate.weight().eligible()) {
                attackEligible = true;
            }
        }
        return new PreparedSide(side, List.copyOf(plans), attackEligible);
    }

    private Context context(GameState state, int time) {
        EnumMap<TeamSide, Integer> gold = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Integer> alive = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Double> teamfight = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Double> aggression = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Boolean> baron = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Boolean> elder = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Boolean> bigWin = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, Boolean> ace = new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, BaseThreatSnapshot> baseThreat = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) {
            TeamState team = state.getTeamState(side);
            gold.put(side, team.getGold());
            alive.put(side, countAlive(team, time));
            teamfight.put(side, average(team, PlayerState::getTeamfighting));
            aggression.put(side, average(team, PlayerState::getAggression));
            baron.put(side, team.hasActiveBaronBuff(time));
            elder.put(side, team.getPlayers().stream()
                    .anyMatch(player -> player.hasActiveElderBuff(time)));
            bigWin.put(side, state.hasRecentBigWin(
                    side, LateGameRuleConfig.RECENT_FIGHT_WINDOW_SECONDS));
            ace.put(side, state.hasRecentAce(
                    side, LateGameRuleConfig.RECENT_FIGHT_WINDOW_SECONDS));
            baseThreat.put(side, threats.evaluate(state, side));
        }
        return new Context(state, time, gold, alive, teamfight, aggression,
                baron, elder, bigWin, ace, baseThreat);
    }

    private LateGameInitiativeCandidate initiative(Context context,
                                                    PreparedSide prepared) {
        TeamSide side = prepared.side();
        if (context.state().wasStructureActionAttemptedThisTick(side)) {
            context.state().recordLaterStructureResolverBlockedByAttempt();
        }
        boolean eligible = prepared.hasAttackPlan();
        String reason;
        if (eligible) reason = null;
        else if (context.alive().get(side) < LateGameRuleConfig.MIN_SIEGE_ATTACKERS) {
            reason = "INSUFFICIENT_ATTACKERS";
        } else if (context.state().wasStructureActionPerformedThisTick(side)) {
            reason = "STRUCTURE_SLOT_USED";
        } else if (context.state().getBaseSiegeState(side).isActive()) {
            reason = "SIEGE_ALREADY_ACTIVE";
        } else {
            reason = "NO_ELIGIBLE_TARGET_OR_WAVE";
        }
        double goldEdge = edge(
                context.gold().get(side), context.gold().get(side.opposite()),
                LateGameRuleConfig.GOLD_EDGE_NORMALIZER);
        double aliveEdge = edge(
                context.alive().get(side), context.alive().get(side.opposite()),
                LateGameRuleConfig.ALIVE_EDGE_NORMALIZER);
        double teamfightEdge = edge(
                context.averageTeamfight().get(side),
                context.averageTeamfight().get(side.opposite()),
                LateGameRuleConfig.ATTRIBUTE_EDGE_NORMALIZER);
        double goldContribution = Math.max(0, goldEdge)
                * LateGameRuleConfig.INITIATIVE_GOLD_EDGE_WEIGHT;
        double aliveContribution = Math.max(0, aliveEdge)
                * LateGameRuleConfig.INITIATIVE_ALIVE_EDGE_WEIGHT;
        double teamfightContribution = Math.max(0, teamfightEdge)
                * LateGameRuleConfig.INITIATIVE_TEAMFIGHT_EDGE_WEIGHT;
        double baronContribution = context.baron().get(side)
                ? LateGameRuleConfig.INITIATIVE_BARON_BONUS : 0;
        double elderContribution = context.elder().get(side)
                ? LateGameRuleConfig.INITIATIVE_ELDER_BONUS : 0;
        double recentContribution = context.ace().get(side)
                ? LateGameRuleConfig.INITIATIVE_RECENT_ACE_BONUS
                : context.bigWin().get(side)
                ? LateGameRuleConfig.INITIATIVE_RECENT_BIG_WIN_BONUS : 0;
        double exposureContribution = threatValue(
                context.threat(side.opposite()).overallLevel())
                / LateGameRuleConfig.MAX_THREAT_LEVEL_VALUE
                * LateGameRuleConfig.INITIATIVE_BASE_EXPOSURE_WEIGHT;
        double finalWeight = eligible ? clamp(
                LateGameRuleConfig.INITIATIVE_BASE_WEIGHT + goldContribution
                        + aliveContribution + teamfightContribution
                        + baronContribution + elderContribution
                        + recentContribution + exposureContribution,
                LateGameRuleConfig.MIN_PLAN_WEIGHT,
                LateGameRuleConfig.MAX_PLAN_WEIGHT) : 0;
        return new LateGameInitiativeCandidate(
                side, eligible, reason, goldEdge, aliveEdge, teamfightEdge,
                goldContribution, aliveContribution, teamfightContribution,
                baronContribution, elderContribution, recentContribution,
                exposureContribution, finalWeight);
    }

    private PlanCandidate plan(Context context, TeamSide side,
                               LateGameAttackPlan attackPlan,
                               StructureResolver structures) {
        boolean reset = attackPlan == LateGameAttackPlan.RESET_AND_REGROUP;
        boolean nexus = attackPlan == LateGameAttackPlan.NEXUS_FINISH;
        Lane lane = switch (attackPlan) {
            case SIEGE_TOP -> Lane.TOP;
            case SIEGE_MID -> Lane.MID;
            case SIEGE_BOT -> Lane.BOT;
            case NEXUS_FINISH -> routeLaneForBase(context.state(), side.opposite());
            case RESET_AND_REGROUP -> null;
        };
        LateGameStructureTarget target = nexus
                ? threats.nextTarget(context.state(), side, null)
                : reset ? null : threats.nextTarget(context.state(), side, lane);
        Set<Position> assigned = reset ? Set.of() : assign(
                context.state(), side, attackPlan);

        boolean targetEligible = nexus
                ? target == LateGameStructureTarget.NEXUS
                        || target == LateGameStructureTarget.NEXUS_TURRET
                : isLaneSiegeTarget(target);
        boolean eligible = reset;
        if (!reset && assigned.size() >= LateGameRuleConfig.MIN_SIEGE_ATTACKERS
                && targetEligible
                && !context.state().wasStructureActionPerformedThisTick(side)
                && !context.state().getBaseSiegeState(side).isActive()
                && (!nexus || !context.state().wasMajorCombatAttemptedThisTick())) {
            StructureAttackRequest request = StructureAttackRequest.siege(
                    side, lane, target,
                    nexus ? PushReason.NEXUS_FINISH : PushReason.LATE_GAME_SIEGE,
                    assigned, null);
            eligible = structures.canAttemptSiege(context.state(), request);
        }
        String reason = eligible ? null : assigned.size() < LateGameRuleConfig.MIN_SIEGE_ATTACKERS
                ? "INSUFFICIENT_PARTICIPANTS"
                : !targetEligible ? "NO_ELIGIBLE_TARGET"
                : context.state().getBaseSiegeState(side).isActive()
                ? "SIEGE_ALREADY_ACTIVE" : "TARGET_WAVE_OR_SLOT_INELIGIBLE";

        double goldEdge = edge(
                context.gold().get(side), context.gold().get(side.opposite()),
                LateGameRuleConfig.GOLD_EDGE_NORMALIZER);
        double aliveEdge = edge(
                context.alive().get(side), context.alive().get(side.opposite()),
                LateGameRuleConfig.ALIVE_EDGE_NORMALIZER);
        double teamfightEdge = edge(
                context.averageTeamfight().get(side),
                context.averageTeamfight().get(side.opposite()),
                LateGameRuleConfig.ATTRIBUTE_EDGE_NORMALIZER);

        double base;
        double goldContribution;
        double aliveContribution;
        double teamfightContribution;
        double depthContribution = 0;
        double baronContribution = 0;
        double elderContribution = 0;
        double recentContribution = 0;
        double respawnContribution = 0;
        if (reset) {
            base = LateGameRuleConfig.RESET_AND_REGROUP_BASE_WEIGHT;
            goldContribution = Math.max(0, -goldEdge)
                    * LateGameRuleConfig.RESET_BEHIND_GOLD_WEIGHT;
            aliveContribution = Math.max(0, -aliveEdge)
                    * LateGameRuleConfig.RESET_BEHIND_ALIVE_WEIGHT;
            teamfightContribution = 0;
            respawnContribution = (Position.values().length - context.alive().get(side))
                    * LateGameRuleConfig.RESET_MISSING_ATTACKER_WEIGHT
                    + respawn(context, side.opposite()).respawningSoonCount()
                    * LateGameRuleConfig.RESET_ENEMY_RESPAWN_SOON_WEIGHT;
        } else if (nexus) {
            base = LateGameRuleConfig.NEXUS_FINISH_BASE_WEIGHT;
            goldContribution = goldEdge
                    * LateGameRuleConfig.NEXUS_FINISH_GOLD_EDGE_WEIGHT;
            aliveContribution = aliveEdge
                    * LateGameRuleConfig.NEXUS_FINISH_ALIVE_EDGE_WEIGHT;
            teamfightContribution = 0;
            depthContribution = context.threat(side.opposite()).remainingNexusTurrets() > 0
                    ? LateGameRuleConfig.NEXUS_TURRET_EXPOSED_BONUS
                    : LateGameRuleConfig.NEXUS_EXPOSED_BONUS;
            LateGameRespawnSummary respawn = respawn(context, side.opposite());
            respawnContribution = respawn.deadCount()
                    * LateGameRuleConfig.NEXUS_FINISH_DEAD_DEFENDER_BONUS_PER_PLAYER
                    + respawn.longRespawnCount()
                    * LateGameRuleConfig.NEXUS_FINISH_LONG_RESPAWN_BONUS_PER_PLAYER;
            recentContribution = context.ace().get(side)
                    ? LateGameRuleConfig.NEXUS_FINISH_RECENT_ACE_BONUS : 0;
            baronContribution = context.baron().get(side)
                    ? LateGameRuleConfig.NEXUS_FINISH_BARON_BONUS : 0;
            elderContribution = context.elder().get(side)
                    ? LateGameRuleConfig.NEXUS_FINISH_ELDER_BONUS : 0;
        } else {
            base = LateGameRuleConfig.SIEGE_BASE_WEIGHT;
            goldContribution = goldEdge * LateGameRuleConfig.SIEGE_GOLD_EDGE_WEIGHT;
            aliveContribution = aliveEdge * LateGameRuleConfig.SIEGE_ALIVE_EDGE_WEIGHT;
            teamfightContribution = teamfightEdge
                    * LateGameRuleConfig.SIEGE_TEAMFIGHT_EDGE_WEIGHT;
            depthContribution = depth(target);
            baronContribution = context.baron().get(side)
                    ? LateGameRuleConfig.SIEGE_BARON_BONUS : 0;
            elderContribution = context.elder().get(side)
                    ? LateGameRuleConfig.SIEGE_ELDER_BONUS : 0;
            if (lane != null && !context.state().getMapState()
                    .getLaneState(side.opposite(), lane).isInhibitorAlive()) {
                recentContribution = LateGameRuleConfig.SIEGE_INHIBITOR_PRESSURE_BONUS;
            }
        }
        double finalWeight = eligible ? clamp(
                base + goldContribution + aliveContribution + teamfightContribution
                        + depthContribution + baronContribution + elderContribution
                        + recentContribution + respawnContribution,
                LateGameRuleConfig.MIN_PLAN_WEIGHT,
                LateGameRuleConfig.MAX_PLAN_WEIGHT) : 0;
        LateGamePlanWeightBreakdown weight = new LateGamePlanWeightBreakdown(
                attackPlan, eligible, reason, base, goldEdge, goldContribution,
                aliveEdge, aliveContribution, teamfightEdge, teamfightContribution,
                depthContribution, baronContribution, elderContribution,
                recentContribution, 0, respawnContribution, finalWeight);
        return new PlanCandidate(attackPlan, lane, target, assigned, weight);
    }

    private ResponseCandidate response(
            Context context,
            TeamSide attacker,
            PlanCandidate plan,
            LateGameDefenseResponse response,
            CrossTarget cross,
            Set<Position> defenders
    ) {
        TeamSide side = attacker.opposite();
        int minimumDefenders = plan.plan() == LateGameAttackPlan.NEXUS_FINISH
                ? LateGameRuleConfig.MIN_NEXUS_DEFENDERS
                : LateGameRuleConfig.MIN_STANDARD_DEFENDERS;
        boolean eligible = switch (response) {
            case DEFEND -> defenders.size() >= minimumDefenders;
            case GIVE_STRUCTURE -> true;
            case CROSS_MAP_PUSH -> plan.plan() != LateGameAttackPlan.NEXUS_FINISH
                    && context.threat(side).overallLevel().ordinal()
                    < BaseThreatLevel.NEXUS_TURRET_THREAT.ordinal()
                    && cross != null
                    && !context.state().wasStructureActionPerformedThisTick(side)
                    && !context.state().getBaseSiegeState(side).isActive();
        };
        String reason = eligible ? null : "INELIGIBLE";
        double aliveEdge = edge(
                context.alive().get(side), context.alive().get(attacker),
                LateGameRuleConfig.ALIVE_EDGE_NORMALIZER);
        double teamfightEdge = edge(
                context.averageTeamfight().get(side),
                context.averageTeamfight().get(attacker),
                LateGameRuleConfig.ATTRIBUTE_EDGE_NORMALIZER);
        double goldEdge = edge(
                context.gold().get(side), context.gold().get(attacker),
                LateGameRuleConfig.GOLD_EDGE_NORMALIZER);
        double base;
        double threatContribution = 0;
        double aliveContribution = 0;
        double teamfightContribution = 0;
        double missingContribution = 0;
        double targetContribution = 0;
        double farmingContribution = 0;
        double baronContribution = 0;
        if (response == LateGameDefenseResponse.DEFEND) {
            base = LateGameRuleConfig.DEFEND_BASE_WEIGHT;
            threatContribution = threatValue(context.threat(side).overallLevel())
                    / LateGameRuleConfig.MAX_THREAT_LEVEL_VALUE
                    * LateGameRuleConfig.DEFEND_THREAT_LEVEL_WEIGHT;
            aliveContribution = aliveEdge * LateGameRuleConfig.DEFEND_ALIVE_EDGE_WEIGHT;
            teamfightContribution = teamfightEdge
                    * LateGameRuleConfig.DEFEND_TEAMFIGHT_EDGE_WEIGHT;
            if (respawn(context, side).respawningSoonCount() > 0) {
                missingContribution = LateGameRuleConfig.DEFEND_RESPAWN_SOON_BONUS;
            }
            if (plan.plan() == LateGameAttackPlan.NEXUS_FINISH) {
                threatContribution += LateGameRuleConfig.DEFEND_NEXUS_URGENCY_BONUS;
            }
        } else if (response == LateGameDefenseResponse.GIVE_STRUCTURE) {
            base = LateGameRuleConfig.GIVE_STRUCTURE_BASE_WEIGHT;
            missingContribution = (Position.values().length - context.alive().get(side))
                    * LateGameRuleConfig.GIVE_MISSING_PLAYER_WEIGHT;
            aliveContribution = Math.max(0, -aliveEdge)
                    * LateGameRuleConfig.GIVE_BEHIND_ALIVE_WEIGHT;
            threatContribution = Math.max(0, -goldEdge)
                    * LateGameRuleConfig.GIVE_BEHIND_GOLD_WEIGHT;
        } else {
            base = LateGameRuleConfig.CROSS_MAP_BASE_WEIGHT;
            targetContribution = LateGameRuleConfig.CROSS_MAP_TARGET_AVAILABLE_BONUS;
            farmingContribution = cross == null ? 0
                    : farmingEdge(context.state(), side, cross.lane())
                    * LateGameRuleConfig.CROSS_MAP_FARMING_EDGE_WEIGHT;
            baronContribution = context.baron().get(side)
                    ? LateGameRuleConfig.CROSS_MAP_BARON_BONUS : 0;
        }
        double finalWeight = eligible ? clamp(
                base + threatContribution + aliveContribution
                        + teamfightContribution + missingContribution
                        + targetContribution + farmingContribution + baronContribution,
                LateGameRuleConfig.MIN_PLAN_WEIGHT,
                LateGameRuleConfig.MAX_PLAN_WEIGHT) : 0;
        return new ResponseCandidate(response, new LateGameDefenseWeightBreakdown(
                response, eligible, reason, base, threatContribution,
                aliveContribution, teamfightContribution, missingContribution,
                targetContribution, farmingContribution, baronContribution,
                finalWeight));
    }

    private CrossTarget crossTarget(Context context, TeamSide side, Lane attacked,
                                    StructureResolver structures) {
        if (attacked == null) return null;
        List<Lane> order = switch (attacked) {
            case TOP -> List.of(Lane.BOT, Lane.MID, Lane.TOP);
            case MID -> List.of(Lane.TOP, Lane.BOT, Lane.MID);
            case BOT -> List.of(Lane.TOP, Lane.MID, Lane.BOT);
        };
        for (Lane lane : order) {
            LateGameStructureTarget target = threats.nextTarget(
                    context.state(), side, lane);
            Position pusher = primary(lane);
            PlayerState player = context.state().getTeamState(side).playerAt(pusher);
            if (!isCrossMapTarget(target)
                    || !player.canParticipateInMajorCombatAt(context.time())
                    || context.state().wasMajorCombatParticipantThisTick(player)) {
                continue;
            }
            StructureAttackRequest request = crossMapRequest(
                    context.state(), side, lane, target, Set.of(pusher), null);
            if (structures.canAttemptSiege(context.state(), request)) {
                return new CrossTarget(lane, target, pusher);
            }
        }
        return null;
    }

    private Set<Position> assign(GameState state, TeamSide side,
                                 LateGameAttackPlan plan) {
        List<Position> order = switch (plan) {
            case SIEGE_TOP -> List.of(Position.TOP, Position.JUNGLE,
                    Position.SUPPORT, Position.MID, Position.ADC);
            case SIEGE_MID -> List.of(Position.JUNGLE, Position.MID,
                    Position.ADC, Position.SUPPORT);
            case SIEGE_BOT -> List.of(Position.JUNGLE, Position.ADC,
                    Position.SUPPORT, Position.TOP, Position.MID);
            case NEXUS_FINISH -> List.of(Position.TOP, Position.JUNGLE,
                    Position.MID, Position.ADC, Position.SUPPORT);
            case RESET_AND_REGROUP -> List.of();
        };
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (Position position : order) {
            PlayerState player = state.getTeamState(side).playerAt(position);
            if (player.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())
                    && !state.wasMajorCombatParticipantThisTick(player)) {
                result.add(position);
            }
            if ((plan == LateGameAttackPlan.SIEGE_TOP
                    || plan == LateGameAttackPlan.SIEGE_BOT)
                    && result.size() == LateGameRuleConfig.MAX_SIDE_LANE_ATTACKERS) {
                break;
            }
        }
        return Set.copyOf(result);
    }

    private Set<Position> availablePositions(GameState state, TeamSide side,
                                             List<Position> order) {
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (Position position : order) {
            PlayerState player = state.getTeamState(side).playerAt(position);
            if (player.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())
                    && !state.wasMajorCombatParticipantThisTick(player)) {
                result.add(position);
            }
        }
        return Set.copyOf(result);
    }

    private Set<Position> survivingAssigned(GameState state, TeamSide side,
                                            Set<Position> assigned) {
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (Position position : assigned) {
            if (state.getTeamState(side).playerAt(position)
                    .isAlive(state.getCurrentTimeSeconds())) {
                result.add(position);
            }
        }
        return Set.copyOf(result);
    }

    private boolean canAttemptPlan(GameState state, TeamSide attacker,
                                   PlanCandidate plan,
                                   StructureResolver structures,
                                   String parentActionId) {
        return structures.canAttemptSiege(state, requestForPlan(
                attacker, plan, plan.assigned(), parentActionId,
                plan.plan() == LateGameAttackPlan.NEXUS_FINISH
                        ? PushReason.NEXUS_FINISH : PushReason.LATE_GAME_SIEGE));
    }

    private StructureAttackRequest requestForPlan(
            TeamSide side,
            PlanCandidate plan,
            Set<Position> participants,
            String parentActionId,
            PushReason reason
    ) {
        return StructureAttackRequest.siege(
                side, plan.lane(), plan.target(), reason, participants, parentActionId);
    }

    private StructureAttackRequest crossMapRequest(
            GameState state,
            TeamSide side,
            Lane lane,
            LateGameStructureTarget target,
            Set<Position> participants,
            String parentActionId
    ) {
        LaneWaveState wave = state.getMapState().getWaveState(side, lane);
        if (wave.hasActiveWaveAt(state.getCurrentTimeSeconds())
                || wave.canPrepareAt(state.getCurrentTimeSeconds())) {
            return StructureAttackRequest.siege(
                    side, lane, target, PushReason.LATE_GAME_CROSS_MAP,
                    participants, parentActionId);
        }
        return StructureAttackRequest.backdoor(
                side, lane, target, PushReason.LATE_GAME_CROSS_MAP,
                participants, parentActionId);
    }

    private boolean currentTargetMatches(GameState state, TeamSide attacker,
                                         PlanCandidate plan) {
        Lane targetLane = plan.plan() == LateGameAttackPlan.NEXUS_FINISH
                ? null : plan.lane();
        return threats.nextTarget(state, attacker, targetLane) == plan.target();
    }

    private void recordSiegeAttempt(GameState state, TeamSide side, Lane lane) {
        state.getCompositionRuntimeState().recordActualAttempt(
                com.lolfm.composition.CompositionActionType.SIEGE,
                side, side, side.opposite(),
                com.lolfm.composition.FightScale.NONE,
                null, false, null, lane, state.getCurrentTimeSeconds(),
                com.lolfm.composition.CompositionBaselineScoreDomain.NOT_AVAILABLE,
                null, null);
        state.getLateGameState().getStats().attempt();
        state.recordPushAttempt();
    }

    private void markFailedStructureAttempt(GameState state, TeamSide side) {
        if (!state.wasStructureActionAttemptedThisTick(side)) {
            state.markStructureActionAttempted(side);
        }
        state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
    }

    private double siegeFightChance(Context context, TeamSide attacker) {
        return clamp(
                LateGameRuleConfig.SIEGE_FIGHT_TRIGGER_BASE_CHANCE
                        + edge(context.averageAggression().get(attacker),
                        context.averageAggression().get(attacker.opposite()),
                        LateGameRuleConfig.ATTRIBUTE_EDGE_NORMALIZER)
                        * LateGameRuleConfig.SIEGE_FIGHT_AGGRESSION_EDGE_BONUS_MAX
                        + threatValue(context.threat(attacker.opposite()).overallLevel())
                        / LateGameRuleConfig.MAX_THREAT_LEVEL_VALUE
                        * LateGameRuleConfig.SIEGE_FIGHT_THREAT_LEVEL_BONUS_MAX,
                LateGameRuleConfig.MIN_SIEGE_FIGHT_TRIGGER_CHANCE,
                LateGameRuleConfig.MAX_SIEGE_FIGHT_TRIGGER_CHANCE);
    }

    private double finishChance(Context context, TeamSide side) {
        LateGameRespawnSummary respawn = respawn(context, side.opposite());
        return clamp(
                LateGameRuleConfig.NEXUS_FINISH_BASE_CHANCE
                        + edge(context.gold().get(side), context.gold().get(side.opposite()),
                        LateGameRuleConfig.GOLD_EDGE_NORMALIZER)
                        * LateGameRuleConfig.NEXUS_FINISH_GOLD_BONUS_MAX
                        + (context.alive().get(side) - context.alive().get(side.opposite()))
                        * LateGameRuleConfig.NEXUS_FINISH_ALIVE_BONUS_PER_PLAYER
                        + respawn.deadCount()
                        * LateGameRuleConfig.NEXUS_FINISH_DEAD_DEFENDER_CHANCE_PER_PLAYER
                        + respawn.longRespawnCount()
                        * LateGameRuleConfig.NEXUS_FINISH_LONG_RESPAWN_CHANCE_PER_PLAYER
                        + (context.baron().get(side)
                        ? LateGameRuleConfig.NEXUS_FINISH_BARON_CHANCE_BONUS : 0)
                        + (context.elder().get(side)
                        ? LateGameRuleConfig.NEXUS_FINISH_ELDER_CHANCE_BONUS : 0)
                        + (context.ace().get(side)
                        ? LateGameRuleConfig.NEXUS_FINISH_RECENT_ACE_CHANCE_BONUS : 0),
                LateGameRuleConfig.MIN_STRUCTURE_ACTION_CHANCE,
                LateGameRuleConfig.MAX_STRUCTURE_ACTION_CHANCE);
    }

    private double nonCombatChance(Context context, TeamSide side) {
        return clamp(
                LateGameRuleConfig.NON_COMBAT_SIEGE_BASE_CHANCE
                        + edge(context.gold().get(side), context.gold().get(side.opposite()),
                        LateGameRuleConfig.GOLD_EDGE_NORMALIZER)
                        * LateGameRuleConfig.NON_COMBAT_SIEGE_GOLD_BONUS_MAX
                        + (context.alive().get(side) - context.alive().get(side.opposite()))
                        * LateGameRuleConfig.NON_COMBAT_SIEGE_ALIVE_BONUS_PER_PLAYER
                        + edge(context.averageTeamfight().get(side),
                        context.averageTeamfight().get(side.opposite()),
                        LateGameRuleConfig.ATTRIBUTE_EDGE_NORMALIZER)
                        * LateGameRuleConfig.NON_COMBAT_SIEGE_TEAMFIGHT_BONUS_MAX
                        + (context.baron().get(side)
                        ? LateGameRuleConfig.NON_COMBAT_SIEGE_BARON_BONUS : 0)
                        + (context.elder().get(side)
                        ? LateGameRuleConfig.NON_COMBAT_SIEGE_ELDER_BONUS : 0),
                LateGameRuleConfig.MIN_STRUCTURE_ACTION_CHANCE,
                LateGameRuleConfig.MAX_STRUCTURE_ACTION_CHANCE);
    }

    private double crossMapChance(Context context, TeamSide side, Lane lane) {
        return clamp(
                LateGameRuleConfig.CROSS_MAP_PUSH_BASE_CHANCE
                        + edge(context.gold().get(side), context.gold().get(side.opposite()),
                        LateGameRuleConfig.GOLD_EDGE_NORMALIZER)
                        * LateGameRuleConfig.CROSS_MAP_GOLD_BONUS_MAX
                        + (context.alive().get(side) - context.alive().get(side.opposite()))
                        * LateGameRuleConfig.CROSS_MAP_ALIVE_BONUS_PER_PLAYER
                        + farmingEdge(context.state(), side, lane)
                        * LateGameRuleConfig.CROSS_MAP_FARMING_BONUS_MAX
                        + (context.baron().get(side)
                        ? LateGameRuleConfig.CROSS_MAP_BARON_CHANCE_BONUS : 0),
                LateGameRuleConfig.MIN_STRUCTURE_ACTION_CHANCE,
                LateGameRuleConfig.MAX_STRUCTURE_ACTION_CHANCE);
    }

    private LateGameRespawnSummary respawn(Context context, TeamSide side) {
        int dead = 0;
        int soon = 0;
        int longCount = 0;
        int longest = 0;
        for (PlayerState player : context.state().getTeamState(side).getPlayers()) {
            if (player.isAlive(context.time())) continue;
            dead++;
            int seconds = Math.max(
                    0, player.getRespawnAtSeconds() - context.time());
            if (seconds <= LateGameRuleConfig.RESPAWN_SOON_SECONDS) soon++;
            if (seconds >= LateGameRuleConfig.RESPAWN_LONG_SECONDS) longCount++;
            longest = Math.max(longest, seconds);
        }
        return new LateGameRespawnSummary(dead, soon, longCount, longest);
    }

    private TeamSide weightedInitiative(
            List<LateGameInitiativeCandidate> candidates, Random random) {
        double total = candidates.stream()
                .mapToDouble(LateGameInitiativeCandidate::finalWeight).sum();
        double roll = random.nextDouble() * total;
        for (LateGameInitiativeCandidate candidate : candidates) {
            roll -= candidate.finalWeight();
            if (roll < 0) return candidate.side();
        }
        return candidates.getLast().side();
    }

    private PlanCandidate weightedPlan(List<PlanCandidate> candidates, Random random) {
        double total = candidates.stream()
                .mapToDouble(candidate -> candidate.weight().finalWeight()).sum();
        double roll = random.nextDouble() * total;
        for (PlanCandidate candidate : candidates) {
            roll -= candidate.weight().finalWeight();
            if (roll < 0) return candidate;
        }
        return candidates.getLast();
    }

    private ResponseCandidate weightedResponse(
            List<ResponseCandidate> candidates, Random random) {
        double total = candidates.stream()
                .mapToDouble(candidate -> candidate.weight().finalWeight()).sum();
        double roll = random.nextDouble() * total;
        for (ResponseCandidate candidate : candidates) {
            roll -= candidate.weight().finalWeight();
            if (roll < 0) return candidate;
        }
        return candidates.getLast();
    }

    private LateGameDecisionData emptyDecision(
            LateGameState state,
            int due,
            int time,
            List<LateGameInitiativeCandidate> initiatives,
            Context context,
            LateGameActionResult result
    ) {
        return new LateGameDecisionData(
                state.getEvaluationSequence(), due, time, initiatives,
                null, false, List.of(), null, false, null, null, Set.of(),
                List.of(), null, false, context.threat(TeamSide.BLUE),
                context.threat(TeamSide.RED), 0, 0,
                new LateGameRespawnSummary(0, 0, 0, 0),
                0, false, null, null, 0, false, false,
                null, null, 0, false, false, result,
                false, false, false, state.getNextEvaluationAtSeconds());
    }

    private LateGameDecisionData decision(
            LateGameState state,
            int due,
            int time,
            List<LateGameInitiativeCandidate> initiatives,
            TeamSide attacker,
            boolean initiativeRoll,
            List<PlanCandidate> plans,
            PlanCandidate selected,
            boolean planRoll,
            List<ResponseCandidate> defenses,
            ResponseCandidate response,
            boolean defenseRoll,
            Context context,
            LateGameRespawnSummary defenderRespawn,
            double fightChance,
            boolean fightTriggered,
            FightGrade grade,
            TeamSide winner,
            double structureChance,
            boolean structureRoll,
            boolean structureSuccess,
            Lane crossLane,
            LateGameStructureTarget crossTarget,
            double crossChance,
            boolean crossRoll,
            boolean crossSuccess,
            LateGameActionResult result,
            boolean majorCombat,
            boolean attackerStructure,
            boolean defenderStructure
    ) {
        return new LateGameDecisionData(
                state.getEvaluationSequence(), due, time, initiatives,
                attacker, initiativeRoll,
                plans.stream().map(PlanCandidate::weight).toList(),
                selected.plan(), planRoll, selected.lane(), selected.target(),
                selected.assigned(),
                defenses.stream().map(ResponseCandidate::weight).toList(),
                response == null ? null : response.response(), defenseRoll,
                context.threat(TeamSide.BLUE), context.threat(TeamSide.RED),
                context.alive().get(attacker), context.alive().get(attacker.opposite()),
                defenderRespawn, fightChance, fightTriggered, grade, winner,
                structureChance, structureRoll, structureSuccess,
                crossLane, crossTarget, crossChance, crossRoll, crossSuccess,
                result, majorCombat, attackerStructure, defenderStructure,
                state.getNextEvaluationAtSeconds());
    }

    private Lane routeLaneForBase(GameState state, TeamSide defending) {
        for (Lane lane : Lane.values()) {
            if (!state.getMapState().getLaneState(defending, lane).isInhibitorAlive()) {
                return lane;
            }
        }
        Lane result = Lane.MID;
        int maximum = -1;
        for (Lane lane : Lane.values()) {
            int progress = state.getMapState().calculateLaneProgress(defending, lane);
            if (progress > maximum) {
                maximum = progress;
                result = lane;
            }
        }
        return result;
    }

    private boolean isLaneSiegeTarget(LateGameStructureTarget target) {
        return target == LateGameStructureTarget.OUTER
                || target == LateGameStructureTarget.INNER
                || target == LateGameStructureTarget.INHIBITOR_TOWER
                || target == LateGameStructureTarget.INHIBITOR;
    }

    private boolean isCrossMapTarget(LateGameStructureTarget target) {
        return target == LateGameStructureTarget.OUTER
                || target == LateGameStructureTarget.INNER
                || target == LateGameStructureTarget.INHIBITOR_TOWER;
    }

    private int threatValue(BaseThreatLevel level) {
        return switch (level) {
            case NONE -> 0;
            case INHIBITOR_TOWER_THREAT -> 1;
            case INHIBITOR_THREAT -> 2;
            case NEXUS_TURRET_THREAT -> 3;
            case NEXUS_THREAT, MATCH_ENDED -> 4;
        };
    }

    private double depth(LateGameStructureTarget target) {
        if (target == null) return 0;
        return switch (target) {
            case OUTER -> LateGameRuleConfig.OUTER_TARGET_DEPTH_BONUS;
            case INNER -> LateGameRuleConfig.INNER_TARGET_DEPTH_BONUS;
            case INHIBITOR_TOWER -> LateGameRuleConfig.INHIBITOR_TOWER_TARGET_DEPTH_BONUS;
            case INHIBITOR -> LateGameRuleConfig.INHIBITOR_TARGET_DEPTH_BONUS;
            default -> 0;
        };
    }

    double farmingEdge(GameState state, TeamSide side, Lane lane) {
        Position position = primary(lane);
        PlayerState own = state.getTeamState(side).playerAt(position);
        PlayerState enemy = state.getTeamState(side.opposite()).playerAt(position);
        double ownScore = own.hasMatchPerformance()
                ? playerSkills.sideLane(own) : own.getFarming();
        double enemyScore = enemy.hasMatchPerformance()
                ? playerSkills.sideLane(enemy) : enemy.getFarming();
        return edge(ownScore, enemyScore, LateGameRuleConfig.ATTRIBUTE_EDGE_NORMALIZER);
    }

    private Position primary(Lane lane) {
        return switch (lane) {
            case TOP -> Position.TOP;
            case MID -> Position.MID;
            case BOT -> Position.ADC;
        };
    }

    private int countAlive(TeamState team, int time) {
        return (int) team.getPlayers().stream()
                .filter(player -> player.isAlive(time)).count();
    }

    private double average(TeamState team, ToIntFunction<PlayerState> getter) {
        return team.getPlayers().stream().mapToInt(getter).average()
                .orElse(LateGameRuleConfig.BASELINE_ATTRIBUTE_SCORE);
    }

    private double edge(double first, double second, double normalizer) {
        return clamp((first - second) / normalizer, -1, 1);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String lateGameMessage(GameState state, LateGameDecisionData decision) {
        String team = state.getTeamState(decision.initiativeSide()).getTeamName();
        return switch (decision.result()) {
            case SIEGE_FIGHT_DEFENDER_WIN -> team
                    + "의 후반 공성이 수비 교전에서 저지됐습니다.";
            case SIEGE_FIGHT_ATTACKER_WIN -> team
                    + "가 후반 공성 교전에서 승리해 압박을 이어갑니다.";
            case STRUCTURE_DAMAGED -> team
                    + "가 후반 운영으로 구조물에 피해를 누적했습니다.";
            case STRUCTURE_DESTROYED -> team
                    + "가 후반 운영으로 구조물을 파괴했습니다.";
            case NEXUS_FINISH_ADVANCED -> team
                    + "가 넥서스 공략을 이어갑니다.";
            case NEXUS_DESTROYED -> team
                    + "가 넥서스를 파괴해 경기를 끝냈습니다.";
            case SIEGE_REPELLED -> team
                    + "의 후반 공성이 구조물 피해 없이 저지됐습니다.";
            case CROSS_MAP_SUCCEEDED -> team
                    + "의 공성과 상대의 교차 맵 운영이 동시에 성과를 냈습니다.";
            case ATTACKER_RESET -> team + "가 공격을 멈추고 재정비합니다.";
            case NO_INITIATIVE -> "양 팀 모두 후반 공격 기회를 만들지 못했습니다.";
            case INELIGIBLE -> team + "의 후반 공격 조건이 충족되지 않았습니다.";
            default -> team + "의 후반 운영이 종료됐습니다.";
        };
    }

    private record Context(
            GameState state,
            int time,
            EnumMap<TeamSide, Integer> gold,
            EnumMap<TeamSide, Integer> alive,
            EnumMap<TeamSide, Double> averageTeamfight,
            EnumMap<TeamSide, Double> averageAggression,
            EnumMap<TeamSide, Boolean> baron,
            EnumMap<TeamSide, Boolean> elder,
            EnumMap<TeamSide, Boolean> bigWin,
            EnumMap<TeamSide, Boolean> ace,
            EnumMap<TeamSide, BaseThreatSnapshot> threats
    ) {
        BaseThreatSnapshot threat(TeamSide side) {
            return threats.get(side);
        }
    }

    private record PreparedSide(
            TeamSide side,
            List<PlanCandidate> plans,
            boolean hasAttackPlan
    ) { }

    private record PlanCandidate(
            LateGameAttackPlan plan,
            Lane lane,
            LateGameStructureTarget target,
            Set<Position> assigned,
            LateGamePlanWeightBreakdown weight
    ) { }

    private record ResponseCandidate(
            LateGameDefenseResponse response,
            LateGameDefenseWeightBreakdown weight
    ) { }

    private record CrossTarget(
            Lane lane,
            LateGameStructureTarget target,
            Position pusher
    ) { }

    private record CrossResolution(
            Lane lane,
            LateGameStructureTarget target,
            double chance,
            boolean roll,
            boolean success
    ) {
        static CrossResolution none() {
            return new CrossResolution(null, null, 0, false, false);
        }
    }

    record Resolution(
            boolean attempted,
            boolean anySuccess,
            double fightChance,
            boolean fightTriggered,
            FightGrade grade,
            TeamSide fightWinner,
            double structureChance,
            boolean structureRoll,
            boolean structureSuccess,
            Lane crossLane,
            LateGameStructureTarget crossTarget,
            double crossChance,
            boolean crossRoll,
            boolean crossSuccess,
            LateGameActionResult result,
            boolean majorCombat,
            boolean attackerStructure,
            boolean defenderStructure
    ) {
        static Resolution ineligible() {
            return new Resolution(
                    false, false, 0, false, null, null,
                    0, false, false, null, null, 0,
                    false, false, LateGameActionResult.INELIGIBLE,
                    false, false, false);
        }
    }
}

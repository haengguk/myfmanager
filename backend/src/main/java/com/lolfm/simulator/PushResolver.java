package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PushResolver {

    public Optional<StructureOutcome> maybeResolvePostFightPush(
            GameState state, Optional<TeamfightOutcome> outcome, Random random,
            StructureResolver structures) {
        return maybeResolvePostFightPush(state, outcome, random, structures, null);
    }

    public Optional<StructureOutcome> maybeResolvePostFightPush(
            GameState state, Optional<TeamfightOutcome> outcome, Random random,
            StructureResolver structures, List<MatchEvent> events) {
        if (outcome.isEmpty() || state.isFinished()) return Optional.empty();
        TeamfightOutcome fight = outcome.get();
        TeamSide side = fight.winningSide();
        Set<Position> participants = availablePostFightParticipants(state, side);
        Optional<StructureAttackRequest> request = requestForCurrentTarget(
                state, side, participants, PushReason.POST_FIGHT,
                fight.actionId(), true, random, structures);
        request = request.map(value -> applyPostFightSiegeWindow(state, side, value));
        if (request.isEmpty()) {
            recordNoRequestFailure(state, side, participants.size());
            return Optional.empty();
        }
        if (!structures.canAttemptSiege(state, request.get())) {
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
            return Optional.empty();
        }
        if (random.nextDouble() >= postFightChance(state, fight)) {
            state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
            return Optional.empty();
        }
        Optional<StructureAttackResult> attack = structures.attemptSiege(state, request.get());
        if (attack.isEmpty()) {
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
            return Optional.empty();
        }
        state.recordPushAttempt();
        recordCompositionAttempt(state, side, request.get().routeLane());
        state.recordPushSuccess();
        if (events != null) structures.addAttackEvents(state, attack.get(), events);
        return Optional.ofNullable(attack.get().destruction());
    }

    public List<StructureOutcome> resolvePostFightWindow(
            GameState state,
            Optional<TeamfightOutcome> outcome,
            Optional<MatchEvent> postFightObjective,
            Random random,
            StructureResolver structures
    ) {
        return resolvePostFightWindow(state, outcome, postFightObjective, random, structures, null);
    }

    public List<StructureOutcome> resolvePostFightWindow(
            GameState state,
            Optional<TeamfightOutcome> outcome,
            Optional<MatchEvent> postFightObjective,
            Random random,
            StructureResolver structures,
            List<MatchEvent> events
    ) {
        if (outcome.isEmpty() || state.isFinished()) return List.of();
        TeamfightOutcome fight = outcome.get();
        TeamSide attacking = fight.winningSide();
        if (postFightObjective.map(this::objectiveTimeCostSeconds).orElse(0) > 0) {
            // The team chose the objective instead of beginning a structure action.
            return List.of();
        }
        Set<Position> participants = availablePostFightParticipants(state, attacking);
        Optional<StructureAttackRequest> request = requestForCurrentTarget(
                state, attacking, participants, reason(state, attacking, PushReason.POST_FIGHT),
                fight.actionId(), true, random, structures);
        request = request.map(value -> applyPostFightSiegeWindow(state, attacking, value));
        if (request.isEmpty()) {
            recordNoRequestFailure(state, attacking, participants.size());
            return List.of();
        }
        if (!structures.canAttemptSiege(state, request.get())) {
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
            return List.of();
        }

        if (random.nextDouble() >= postFightChance(state, fight)) {
            state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
            return List.of();
        }
        Optional<StructureAttackResult> attack = structures.attemptSiege(state, request.get());
        if (attack.isEmpty()) {
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
            return List.of();
        }
        state.recordPushAttempt();
        recordCompositionAttempt(state, attacking, request.get().routeLane());
        state.recordPushSuccess();
        if (events != null) structures.addAttackEvents(state, attack.get(), events);
        if (attack.get().destruction() == null) return List.of();
        StructureOutcome destruction = attack.get().destruction();
        state.recordPushWindow(1,
                fight.grade() == FightGrade.ACE && destruction.structureKind() == StructureKind.NEXUS);
        state.recordPostFightStructureWindow(1);
        return List.of(destruction);
    }

    public Optional<StructureOutcome> maybeResolveMacroPush(
            GameState state, Random random, StructureResolver structures) {
        return maybeResolveMacroPush(state, random, structures, null);
    }

    public Optional<StructureOutcome> maybeResolveMacroPush(
            GameState state, Random random, StructureResolver structures,
            List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        if (time < PushRuleConfig.MACRO_START_SECONDS || state.isFinished()) return Optional.empty();
        if ((time - PushRuleConfig.MACRO_START_SECONDS)
                % PushRuleConfig.MACRO_EVALUATION_INTERVAL_SECONDS != 0) return Optional.empty();

        List<TeamSide> eligibleSides = new ArrayList<>();
        EnumMap<TeamSide, EnumMap<Lane, Set<Position>>> participantsByLane =
                new EnumMap<>(TeamSide.class);
        EnumMap<TeamSide, List<Lane>> lanesBySide = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) {
            if (!state.getMapState().isPushAttemptDue(side, time)) continue;
            if (state.wasStructureActionPerformedThisTick(side)) {
                state.recordLaterStructureResolverBlockedByAttempt();
                continue;
            }
            if (state.getBaseSiegeState(side).isActive()) continue;
            if (state.isStructurePushBlocked(side, time)) {
                state.recordPushFailure(PushFailureReason.RECENTLY_REPELLED);
                continue;
            }
            Set<Position> participants = availableMacroPushers(state, side, time);
            if (participants.size() < StructureRuleConfig.MIN_LANE_SIEGE_ATTACKERS) {
                state.recordPushFailure(PushFailureReason.COMBAT_PARTICIPANTS_UNAVAILABLE);
                continue;
            }
            PushReason pushReason = reason(state, side, PushReason.MACRO_PLAY);
            EnumMap<Lane, Set<Position>> eligibleRequests = eligibleMacroRequests(
                    state, side, participants, structures, pushReason);
            List<Lane> eligibleLanes = List.copyOf(eligibleRequests.keySet());
            if (eligibleLanes.isEmpty()) {
                if (baseTarget(state, side).isPresent()
                        && participants.size() < StructureRuleConfig.MIN_BASE_SIEGE_ATTACKERS) {
                    state.recordPushFailure(PushFailureReason.INSUFFICIENT_ATTACKERS);
                } else {
                    state.recordPushFailure(hasAnyTarget(state, side)
                            ? PushFailureReason.TARGET_UNAVAILABLE : PushFailureReason.NO_TARGET);
                }
                continue;
            }
            eligibleSides.add(side);
            participantsByLane.put(side, eligibleRequests);
            lanesBySide.put(side, eligibleLanes);
        }

        if (eligibleSides.size() == 2 && random.nextBoolean()) {
            TeamSide first = eligibleSides.getFirst();
            eligibleSides.set(0, eligibleSides.getLast());
            eligibleSides.set(1, first);
        }

        for (TeamSide side : eligibleSides) {
            List<Lane> lanes = lanesBySide.get(side);
            Lane lane = baseTarget(state, side).isPresent()
                    ? lanes.getFirst()
                    : chooseLane(state, side.opposite(), lanes, false, random);
            LateGameStructureTarget requestedTarget = baseTarget(state, side).orElse(null);
            PushReason pushReason = reason(state, side, PushReason.MACRO_PLAY);
            StructureAttackRequest request = StructureAttackRequest.siege(
                    side, lane, requestedTarget, pushReason,
                    participantsByLane.get(side).get(lane), null);
            if (random.nextDouble() >= macroPushChance(state, side, lane)) {
                state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
                continue;
            }
            Optional<StructureAttackResult> attack = structures.attemptSiege(state, request);
            if (attack.isEmpty()) {
                state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
                continue;
            }
            state.getMapState().markPushAttempted(side, time, attemptInterval(state, side, time));
            state.recordPushAttempt();
            recordCompositionAttempt(state, side, lane);
            state.recordPushSuccess();
            if (events != null) structures.addAttackEvents(state, attack.get(), events);
            if (attack.get().destruction() != null) return Optional.of(attack.get().destruction());
        }
        return Optional.empty();
    }

    double postFightChance(GameState state, TeamfightOutcome outcome) {
        int time = state.getCurrentTimeSeconds();
        TeamState attackers = state.getTeamState(outcome.winningSide());
        TeamState defenders = state.getTeamState(outcome.winningSide().opposite());
        double chance = switch (outcome.grade()) {
            case SMALL_WIN -> PushRuleConfig.SMALL_WIN_PUSH_CHANCE;
            case NORMAL_WIN -> PushRuleConfig.NORMAL_WIN_PUSH_CHANCE;
            case BIG_WIN -> PushRuleConfig.BIG_WIN_PUSH_CHANCE;
            case ACE -> PushRuleConfig.ACE_PUSH_CHANCE;
        };
        if (countAlive(attackers, time) >= 4) chance += PushRuleConfig.FOUR_ALIVE_PUSH_BONUS;
        if (countAlive(defenders, time) <= 1) chance += PushRuleConfig.ONE_OR_FEWER_DEFENDER_BONUS;
        chance += Math.min(PushRuleConfig.RESPAWN_PUSH_BONUS_CAP,
                averageRespawn(defenders, time) / PushRuleConfig.RESPAWN_PUSH_BONUS_DIVISOR);
        if (attackers.hasActiveBaronBuff(time)) chance += PushRuleConfig.BARON_PUSH_BONUS;
        if (state.getMapState().hasActiveBasePressure(outcome.winningSide(), time)) {
            chance += PushRuleConfig.BASE_PRESSURE_PUSH_BONUS;
        }
        if (state.getObjectiveState().isSoulOwner(outcome.winningSide())) {
            chance += DragonSoulRuleConfig.SOUL_PUSH_CHANCE_BONUS;
        }
        if (hasActiveElder(attackers, time)) chance += ElderRuleConfig.PUSH_CHANCE_BONUS;
        return Math.min(PushRuleConfig.MAX_POST_FIGHT_PUSH_CHANCE, chance);
    }

    double macroPushChance(GameState state, TeamSide side) {
        List<Lane> lanes = state.getMapState().getPressureLanes(side.opposite());
        return macroPushChance(state, side, lanes.isEmpty() ? Lane.MID : lanes.getFirst());
    }

    double macroPushChance(GameState state, TeamSide side, Lane lane) {
        int time = state.getCurrentTimeSeconds();
        TeamState attackers = state.getTeamState(side);
        TeamState defenders = state.getTeamState(side.opposite());
        int lead = Math.max(0, attackers.getGold() - defenders.getGold());
        double chance = PushRuleConfig.MACRO_BASE_CHANCE;
        if (lead >= PushRuleConfig.SMALL_GOLD_LEAD) chance += PushRuleConfig.SMALL_GOLD_LEAD_BONUS;
        if (lead >= PushRuleConfig.LARGE_GOLD_LEAD) chance += PushRuleConfig.LARGE_GOLD_LEAD_BONUS;
        if (countAlive(attackers, time) > countAlive(defenders, time)) {
            chance += PushRuleConfig.ALIVE_LEAD_BONUS;
        }
        if (isDeepestLane(state, side.opposite(), lane)) chance += PushRuleConfig.DEEPEST_LANE_BONUS;
        int depth = state.getMapState().calculateLaneProgress(side.opposite(), lane);
        if (depth == 0) chance += PushRuleConfig.OUTER_TARGET_PUSH_CHANCE_BONUS;
        else chance -= depth * PushRuleConfig.STRUCTURE_DEPTH_PUSH_CHANCE_PENALTY;
        if (attackers.hasActiveBaronBuff(time)) chance += PushRuleConfig.MACRO_BARON_BONUS;
        if (state.getMapState().hasActiveBasePressure(side, time)) {
            chance += PushRuleConfig.MACRO_BASE_PRESSURE_BONUS;
        }
        if (state.getObjectiveState().isSoulOwner(side)) chance += DragonSoulRuleConfig.SOUL_PUSH_CHANCE_BONUS;
        if (hasActiveElder(attackers, time)) chance += ElderRuleConfig.PUSH_CHANCE_BONUS;
        return Math.min(PushRuleConfig.MAX_MACRO_PUSH_CHANCE, chance);
    }

    int attemptInterval(GameState state, TeamSide side, int time) {
        if (state.getMapState().hasActiveBasePressure(side, time)) {
            return PushRuleConfig.BASE_PRESSURE_ATTEMPT_INTERVAL_SECONDS;
        }
        if (state.getTeamState(side).hasActiveBaronBuff(time)) {
            return PushRuleConfig.BARON_ATTEMPT_INTERVAL_SECONDS;
        }
        return PushRuleConfig.MACRO_ATTEMPT_INTERVAL_SECONDS;
    }

    boolean isDeepestLane(GameState state, TeamSide defending, Lane lane) {
        int progress = state.getMapState().calculateLaneProgress(defending, lane);
        for (Lane other : state.getMapState().getPressureLanes(defending)) {
            if (state.getMapState().calculateLaneProgress(defending, other) > progress) return false;
        }
        return true;
    }

    private Optional<StructureAttackRequest> requestForCurrentTarget(
            GameState state, TeamSide side, Set<Position> participants, PushReason reason,
            String parentActionId, boolean priority, Random random,
            StructureResolver structures) {
        List<Lane> eligibleLanes = eligibleRequestLanes(
                state, side, participants, structures, reason);
        if (eligibleLanes.isEmpty()) return Optional.empty();
        Optional<LateGameStructureTarget> base = baseTarget(state, side);
        if (base.isPresent()) {
            return Optional.of(StructureAttackRequest.siege(
                    side, eligibleLanes.getFirst(), base.get(), reason, participants, parentActionId));
        }
        Lane lane = chooseLane(state, side.opposite(), eligibleLanes, priority, random);
        return Optional.of(StructureAttackRequest.siege(
                side, lane, null, reason, participants, parentActionId));
    }

    private List<Lane> eligibleRequestLanes(
            GameState state, TeamSide side, Set<Position> participants,
            StructureResolver structures, PushReason reason) {
        Optional<LateGameStructureTarget> base = baseTarget(state, side);
        if (base.isPresent()) {
            if (participants.size() < StructureRuleConfig.MIN_BASE_SIEGE_ATTACKERS
                    || !canTargetBase(state, side, base.get())) return List.of();
            Lane route = routeLaneForBase(state, side.opposite());
            StructureAttackRequest request = StructureAttackRequest.siege(
                    side, route, base.get(), reason, participants, null);
            return structures.canAttemptSiege(state, request) ? List.of(route) : List.of();
        }
        List<Lane> result = new ArrayList<>();
        for (Lane lane : state.getMapState().getPressureLanes(side.opposite())) {
            StructureAttackRequest request = StructureAttackRequest.siege(
                    side, lane, null, reason, participants, null);
            if (structures.canAttemptSiege(state, request)) result.add(lane);
        }
        return List.copyOf(result);
    }

    private EnumMap<Lane, Set<Position>> eligibleMacroRequests(
            GameState state, TeamSide side, Set<Position> available,
            StructureResolver structures, PushReason reason) {
        EnumMap<Lane, Set<Position>> result = new EnumMap<>(Lane.class);
        Optional<LateGameStructureTarget> base = baseTarget(state, side);
        if (base.isPresent()) {
            if (available.size() < StructureRuleConfig.MIN_BASE_SIEGE_ATTACKERS
                    || !canTargetBase(state, side, base.get())) return result;
            Lane route = routeLaneForBase(state, side.opposite());
            StructureAttackRequest request = StructureAttackRequest.siege(
                    side, route, base.get(), reason, available, null);
            if (structures.canAttemptSiege(state, request)) result.put(route, available);
            return result;
        }
        boolean baronEmpowered = state.getTeamState(side)
                .hasActiveBaronBuff(state.getCurrentTimeSeconds());
        for (Lane lane : state.getMapState().getPressureLanes(side.opposite())) {
            Set<Position> participants = baronEmpowered
                    ? available : laneMacroParticipants(available, lane);
            if (participants.size() < StructureRuleConfig.MIN_LANE_SIEGE_ATTACKERS) continue;
            StructureAttackRequest request = StructureAttackRequest.siege(
                    side, lane, null, reason, participants, null);
            if (structures.canAttemptSiege(state, request)) result.put(lane, participants);
        }
        return result;
    }

    private Set<Position> laneMacroParticipants(Set<Position> available, Lane lane) {
        List<Position> order = switch (lane) {
            case TOP -> List.of(Position.TOP, Position.JUNGLE);
            case MID -> List.of(Position.MID, Position.JUNGLE, Position.SUPPORT);
            case BOT -> List.of(Position.ADC, Position.SUPPORT, Position.JUNGLE);
        };
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (Position position : order) if (available.contains(position)) result.add(position);
        return Set.copyOf(result);
    }

    private Optional<LateGameStructureTarget> baseTarget(GameState state, TeamSide side) {
        TeamSide defending = side.opposite();
        if (state.getMapState().isNexusVulnerable(defending)) {
            return Optional.of(LateGameStructureTarget.NEXUS);
        }
        if (state.getMapState().areNexusTurretsVulnerable(defending)) {
            return Optional.of(LateGameStructureTarget.NEXUS_TURRET);
        }
        return Optional.empty();
    }

    private boolean canTargetBase(GameState state, TeamSide side,
                                  LateGameStructureTarget target) {
        int time = state.getCurrentTimeSeconds();
        int defenders = countAlive(state.getTeamState(side.opposite()), time);
        if (target == LateGameStructureTarget.NEXUS_TURRET) {
            return defenders <= 2
                    || state.hasRecentBigWin(side, PushRuleConfig.RECENT_FIGHT_BASE_WINDOW_SECONDS)
                    || state.hasRecentAce(side, PushRuleConfig.RECENT_FIGHT_BASE_WINDOW_SECONDS)
                    || state.getTeamState(side).hasActiveBaronBuff(time)
                    || state.getMapState().hasActiveBasePressure(side, time);
        }
        return defenders <= 2
                || state.hasRecentAce(side, PushRuleConfig.RECENT_FIGHT_BASE_WINDOW_SECONDS)
                || state.getTeamState(side).hasActiveBaronBuff(time) && defenders <= 3
                || state.getMapState().hasActiveBasePressure(side, time) && defenders <= 2;
    }

    private Lane routeLaneForBase(GameState state, TeamSide defending) {
        for (Lane lane : Lane.values()) {
            if (!state.getMapState().getLaneState(defending, lane).isInhibitorAlive()) return lane;
        }
        Lane result = Lane.MID;
        int max = -1;
        for (Lane lane : Lane.values()) {
            int progress = state.getMapState().calculateLaneProgress(defending, lane);
            if (progress > max) {
                max = progress;
                result = lane;
            }
        }
        return result;
    }

    private Lane chooseLane(GameState state, TeamSide defending, List<Lane> lanes,
                            boolean priority, Random random) {
        if (lanes.isEmpty()) throw new IllegalArgumentException("No eligible lane");
        int max = lanes.stream()
                .mapToInt(lane -> state.getMapState().calculateLaneProgress(defending, lane))
                .max().orElse(0);
        List<Lane> deepest = lanes.stream()
                .filter(lane -> state.getMapState().calculateLaneProgress(defending, lane) == max)
                .toList();
        if (priority) return deepest.size() == 1
                ? deepest.getFirst() : deepest.get(random.nextInt(deepest.size()));
        int totalWeight = 0;
        for (Lane lane : lanes) {
            totalWeight += 1 + state.getMapState().calculateLaneProgress(defending, lane) * 2;
        }
        int roll = random.nextInt(totalWeight);
        for (Lane lane : lanes) {
            roll -= 1 + state.getMapState().calculateLaneProgress(defending, lane) * 2;
            if (roll < 0) return lane;
        }
        return lanes.getLast();
    }

    private Set<Position> availableMacroPushers(GameState state, TeamSide side, int time) {
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (PlayerState player : state.getTeamState(side).getPlayers()) {
            if (player.canParticipateInMajorCombatAt(time)
                    && !state.wasMajorCombatParticipantThisTick(player)) result.add(player.getPosition());
        }
        return result;
    }

    private Set<Position> availablePostFightParticipants(GameState state, TeamSide side) {
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        int time = state.getCurrentTimeSeconds();
        for (PlayerState player : state.getTeamState(side).getPlayers()) {
            if (player.isAlive(time)) result.add(player.getPosition());
        }
        return result;
    }

    private StructureAttackRequest applyPostFightSiegeWindow(
            GameState state, TeamSide attackingSide, StructureAttackRequest request) {
        int time = state.getCurrentTimeSeconds();
        List<Integer> defenderReturnTimes = state.getTeamState(attackingSide.opposite())
                .getPlayers().stream()
                .map(player -> Math.max(0, player.getRespawnAtSeconds() - time))
                .sorted()
                .toList();
        int returnThreshold = defenderReturnTimes.get(
                Math.min(StructureRuleConfig.BASE_DEFENSE_RETURN_COUNT - 1,
                        defenderReturnTimes.size() - 1));
        int opportunities = 1 + Math.max(0, returnThreshold - 1)
                / StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS;
        if (state.getTeamState(attackingSide).hasActiveBaronBuff(time)) {
            opportunities = Math.max(opportunities, StructureRuleConfig.BARON_WAVE_ATTACKS);
        }
        opportunities = Math.min(StructureRuleConfig.POST_FIGHT_WAVE_ATTACKS,
                Math.max(1, opportunities));
        int duration = Math.min(StructureRuleConfig.POST_FIGHT_SIEGE_DURATION_SECONDS,
                (opportunities + 1) * StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        return request.withSiegeWindow(opportunities, duration);
    }

    private void recordNoRequestFailure(GameState state, TeamSide side, int participantCount) {
        if (baseTarget(state, side).isPresent()
                && participantCount < StructureRuleConfig.MIN_BASE_SIEGE_ATTACKERS) {
            state.recordPushFailure(PushFailureReason.INSUFFICIENT_ATTACKERS);
        } else if (!hasAnyTarget(state, side)) {
            state.recordPushFailure(PushFailureReason.NO_TARGET);
        } else {
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
        }
    }

    private void recordCompositionAttempt(GameState state, TeamSide side, Lane lane) {
        state.getCompositionRuntimeState().recordActualAttempt(
                com.lolfm.composition.CompositionActionType.SIEGE, side, side, side.opposite(),
                com.lolfm.composition.FightScale.NONE, null, false, null, lane,
                state.getCurrentTimeSeconds(),
                com.lolfm.composition.CompositionBaselineScoreDomain.NOT_AVAILABLE,
                null, null);
    }

    private int objectiveTimeCostSeconds(MatchEvent event) {
        return switch (event.getType()) {
            case DRAGON -> PushRuleConfig.DRAGON_OBJECTIVE_TIME_SECONDS;
            case BARON -> PushRuleConfig.BARON_OBJECTIVE_TIME_SECONDS;
            default -> 0;
        };
    }

    private PushReason reason(GameState state, TeamSide side, PushReason fallbackReason) {
        return state.getTeamState(side).hasActiveBaronBuff(state.getCurrentTimeSeconds())
                ? PushReason.BARON_PRESSURE : fallbackReason;
    }

    private boolean hasAnyTarget(GameState state, TeamSide side) {
        TeamSide defending = side.opposite();
        return !state.getMapState().getPressureLanes(defending).isEmpty()
                || state.getMapState().areNexusTurretsVulnerable(defending)
                || state.getMapState().isNexusVulnerable(defending);
    }

    private boolean hasActiveElder(TeamState team, int time) {
        for (PlayerState player : team.getPlayers()) if (player.hasActiveElderBuff(time)) return true;
        return false;
    }

    private int countAlive(TeamState team, int time) {
        int count = 0;
        for (PlayerState player : team.getPlayers()) if (player.isAlive(time)) count++;
        return count;
    }

    private double averageRespawn(TeamState team, int time) {
        int total = 0;
        for (PlayerState player : team.getPlayers()) {
            total += Math.max(0, player.getRespawnAtSeconds() - time);
        }
        return total / (double) team.getPlayers().size();
    }
}

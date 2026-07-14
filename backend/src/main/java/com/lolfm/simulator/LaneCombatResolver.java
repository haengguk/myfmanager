package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.LaneCombatData;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Stateless early lane combat; GameState and LaneState own all mutable timing. */
public final class LaneCombatResolver {
    private final KillRewardResolver rewards = new KillRewardResolver();

    public boolean resolve(GameState state, Random random, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        state.getCombatExecutionStats().recordLaneCombatResolverCall(time);
        if (!state.shouldResolveLaneCombatAt(time)) return false;
        state.markLaneCombatResolvedAt(time);

        List<Lane> triggered = new ArrayList<>();
        List<Double> chances = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            if (!eligible(state, lane, time)) continue;
            double chance = attemptChance(state, lane);
            if (random.nextDouble() < chance) {
                triggered.add(lane);
                chances.add(chance);
            }
        }
        state.getCombatExecutionStats().recordLaneCombatTriggeredLanes(triggered.size());
        if (triggered.isEmpty()) return false;

        Lane lane = pickLane(triggered, chances, random);
        state.getCombatExecutionStats().recordLaneCombatAttempt();
        state.laneState(lane).markCombatAttemptAt(time);
        TeamSide initiator = chooseInitiator(state, lane, random);
        double combatEdge = combatEdge(state, lane, initiator);
        LaneCombatOutcome outcome;
        if (random.nextDouble() >= decisiveChance(state, lane, initiator)) {
            outcome = LaneCombatOutcome.NO_KILL;
        } else {
            outcome = random.nextDouble() < attackerWinChance(combatEdge)
                    ? LaneCombatOutcome.ATTACKER_KILL
                    : LaneCombatOutcome.DEFENDER_REVERSE_KILL;
        }

        double pressureBefore = state.laneState(lane).getPressure();
        if (outcome == LaneCombatOutcome.NO_KILL) {
            events.add(laneEvent(time, lane, initiator, outcome, null, null, null, List.of(), pressureBefore, pressureBefore));
            return true;
        }

        TeamSide winningSide = outcome == LaneCombatOutcome.ATTACKER_KILL ? initiator : initiator.opposite();
        TeamState winners = state.getTeamState(winningSide);
        TeamState losers = state.getTeamState(winningSide.opposite());
        PlayerState killer = pickPlayer(winners, lane, true, random);
        PlayerState victim = pickPlayer(losers, lane, false, random);
        List<PlayerState> assistants = lane == Lane.BOT ? List.of(otherBotPlayer(winners, killer)) : List.of();
        int eventStart = events.size();
        rewards.award(time, winners, killer, losers, victim, assistants, respawnDelaySeconds(time), false, null, events);
        for (int i = eventStart; i < events.size(); i++) events.get(i).setCombatSource(CombatSource.LANE_COMBAT);
        MatchEvent kill = new MatchEvent(time, MatchEventType.KILL, "Lane combat kill",
                killer.getPlayerName(), victim.getPlayerName(), assistants.stream().map(PlayerState::getPlayerName).toList());
        kill.setCombatSource(CombatSource.LANE_COMBAT);
        events.add(kill);
        state.getCombatExecutionStats().recordLaneCombatKill();

        double shock = lane == Lane.BOT
                ? LaneCombatRuleConfig.BOT_KILL_PRESSURE_SHOCK
                : LaneCombatRuleConfig.SOLO_KILL_PRESSURE_SHOCK;
        double pressureAfter = clamp(pressureBefore + (winningSide == TeamSide.BLUE ? shock : -shock), -100, 100);
        state.laneState(lane).setPressure(pressureAfter);
        new ObjectivePriorityResolver().applyLaneCombatKill(state, time, lane, winningSide);
        events.add(laneEvent(time, lane, initiator, outcome, winningSide, killer, victim, assistants, pressureBefore, pressureAfter));
        return true;
    }

    public double attemptChance(GameState state, Lane lane) {
        double averageAggression = (laneAggression(state, lane, TeamSide.BLUE)
                + laneAggression(state, lane, TeamSide.RED)) / 2.0;
        double aggressionModifier = clamp(
                (averageAggression - 14) * LaneCombatRuleConfig.AGGRESSION_ATTEMPT_FACTOR,
                LaneCombatRuleConfig.AGGRESSION_ATTEMPT_MIN,
                LaneCombatRuleConfig.AGGRESSION_ATTEMPT_MAX
        );
        double pressureModifier = Math.abs(state.laneState(lane).getPressure()) / 100.0
                * LaneCombatRuleConfig.PRESSURE_ATTEMPT_MAX_BONUS;
        return clamp(LaneCombatRuleConfig.BASE_ATTEMPT_CHANCE + aggressionModifier + pressureModifier,
                LaneCombatRuleConfig.MIN_ATTEMPT_CHANCE, LaneCombatRuleConfig.MAX_ATTEMPT_CHANCE);
    }

    boolean eligible(GameState state, Lane lane, int time) {
        int lastAttempt = state.laneState(lane).getLastCombatAttemptAtSeconds();
        if (lastAttempt >= 0 && time - lastAttempt < LaneCombatRuleConfig.LANE_COMBAT_COOLDOWN_SECONDS) return false;
        for (TeamSide side : TeamSide.values()) {
            for (PlayerState player : participants(state.getTeamState(side), lane)) {
                if (!player.canParticipateInMajorCombatAt(time)) return false;
            }
        }
        return true;
    }

    double initiativeWeight(GameState state, Lane lane, TeamSide side) {
        double signedPressure = side == TeamSide.BLUE
                ? Math.max(state.laneState(lane).getPressure(), 0)
                : Math.max(-state.laneState(lane).getPressure(), 0);
        return Math.max(LaneCombatRuleConfig.MIN_INITIATIVE_WEIGHT,
                1 + (laneAggression(state, lane, side) - 14) * LaneCombatRuleConfig.INITIATIVE_AGGRESSION_FACTOR
                        + signedPressure / LaneCombatRuleConfig.INITIATIVE_PRESSURE_DIVISOR);
    }

    double combatEdge(GameState state, Lane lane, TeamSide attacker) {
        TeamSide defender = attacker.opposite();
        double attackerPressure = attacker == TeamSide.BLUE
                ? state.laneState(lane).getPressure() : -state.laneState(lane).getPressure();
        return (laneMechanics(state, lane, attacker) - laneMechanics(state, lane, defender))
                * LaneCombatRuleConfig.MECHANICS_EDGE_FACTOR
                + (laneAggression(state, lane, attacker) - laneAggression(state, lane, defender))
                * LaneCombatRuleConfig.AGGRESSION_EDGE_FACTOR
                + clamp((laneGold(state, lane, attacker) - laneGold(state, lane, defender))
                        / LaneCombatRuleConfig.COMBAT_GOLD_DIVISOR,
                        LaneCombatRuleConfig.COMBAT_GOLD_EDGE_MIN, LaneCombatRuleConfig.COMBAT_GOLD_EDGE_MAX)
                + clamp(attackerPressure / LaneCombatRuleConfig.COMBAT_PRESSURE_DIVISOR,
                        LaneCombatRuleConfig.COMBAT_PRESSURE_EDGE_MIN, LaneCombatRuleConfig.COMBAT_PRESSURE_EDGE_MAX);
    }

    double decisiveChance(GameState state, Lane lane, TeamSide attacker) {
        double edge = combatEdge(state, lane, attacker);
        return clamp(LaneCombatRuleConfig.BASE_DECISIVE_CHANCE
                        + (laneAggression(state, lane, attacker) - 14) * LaneCombatRuleConfig.DECISIVE_AGGRESSION_FACTOR
                        + Math.abs(edge) * LaneCombatRuleConfig.DECISIVE_EDGE_FACTOR,
                LaneCombatRuleConfig.MIN_DECISIVE_CHANCE, LaneCombatRuleConfig.MAX_DECISIVE_CHANCE);
    }

    double attackerWinChance(double edge) {
        return clamp(0.50 + edge * LaneCombatRuleConfig.ATTACKER_WIN_EDGE_FACTOR,
                LaneCombatRuleConfig.MIN_ATTACKER_WIN_CHANCE, LaneCombatRuleConfig.MAX_ATTACKER_WIN_CHANCE);
    }

    private TeamSide chooseInitiator(GameState state, Lane lane, Random random) {
        double blue = initiativeWeight(state, lane, TeamSide.BLUE);
        double red = initiativeWeight(state, lane, TeamSide.RED);
        return random.nextDouble() < blue / (blue + red) ? TeamSide.BLUE : TeamSide.RED;
    }

    private MatchEvent laneEvent(int time, Lane lane, TeamSide initiator, LaneCombatOutcome outcome,
                                 TeamSide winningSide, PlayerState killer, PlayerState victim,
                                 List<PlayerState> assistants, double before, double after) {
        List<String> assistantIds = assistants.stream().map(PlayerState::getPlayerName).toList();
        MatchEvent event = new MatchEvent(time, MatchEventType.LANE_COMBAT, "Lane combat",
                killer == null ? null : killer.getPlayerName(), victim == null ? null : victim.getPlayerName(), assistantIds);
        event.setCombatSource(CombatSource.LANE_COMBAT);
        event.setLaneCombat(new LaneCombatData(lane, initiator, outcome, winningSide,
                killer == null ? null : killer.getPlayerName(), victim == null ? null : victim.getPlayerName(),
                assistantIds, before, after));
        return event;
    }

    private double laneAggression(GameState state, Lane lane, TeamSide side) {
        List<PlayerState> players = participants(state.getTeamState(side), lane);
        return lane == Lane.BOT
                ? players.get(0).getAggression() * LaneCombatRuleConfig.BOT_ADC_AGGRESSION_CONTRIBUTION
                    + players.get(1).getAggression() * LaneCombatRuleConfig.BOT_SUPPORT_AGGRESSION_CONTRIBUTION
                : players.getFirst().getAggression();
    }

    private double laneMechanics(GameState state, Lane lane, TeamSide side) {
        List<PlayerState> players = participants(state.getTeamState(side), lane);
        return lane == Lane.BOT
                ? players.get(0).getMechanics() * LaneCombatRuleConfig.BOT_ADC_MECHANICS_CONTRIBUTION
                    + players.get(1).getMechanics() * LaneCombatRuleConfig.BOT_SUPPORT_MECHANICS_CONTRIBUTION
                : players.getFirst().getMechanics();
    }

    private double laneGold(GameState state, Lane lane, TeamSide side) {
        List<PlayerState> players = participants(state.getTeamState(side), lane);
        return lane == Lane.BOT ? (players.get(0).getGold() + players.get(1).getGold()) / 2.0 : players.getFirst().getGold();
    }

    private List<PlayerState> participants(TeamState team, Lane lane) {
        return switch (lane) {
            case TOP -> List.of(team.playerAt(Position.TOP));
            case MID -> List.of(team.playerAt(Position.MID));
            case BOT -> List.of(team.playerAt(Position.ADC), team.playerAt(Position.SUPPORT));
        };
    }

    private PlayerState pickPlayer(TeamState team, Lane lane, boolean killer, Random random) {
        List<PlayerState> players = participants(team, lane);
        if (lane != Lane.BOT) return players.getFirst();
        double adcWeight = killer ? LaneCombatRuleConfig.BOT_ADC_KILLER_BASE_WEIGHT
                : LaneCombatRuleConfig.BOT_ADC_VICTIM_BASE_WEIGHT;
        return random.nextDouble() < adcWeight ? players.get(0) : players.get(1);
    }

    private PlayerState otherBotPlayer(TeamState team, PlayerState selected) {
        return participants(team, Lane.BOT).stream().filter(player -> player != selected).findFirst().orElseThrow();
    }

    private Lane pickLane(List<Lane> lanes, List<Double> weights, Random random) {
        double roll = random.nextDouble() * weights.stream().mapToDouble(Double::doubleValue).sum();
        for (int i = 0; i < lanes.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) return lanes.get(i);
        }
        return lanes.getLast();
    }

    private int respawnDelaySeconds(int time) {
        return time < 600 ? RespawnRuleConfig.BEFORE_10_MINUTES_SECONDS
                : RespawnRuleConfig.FROM_10_TO_20_MINUTES_SECONDS;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

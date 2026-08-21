package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.CounterGankData;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Resolves a defender response after a real gank side and lane have already been selected. */
public final class CounterGankResolver {
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();
    private final KillRewardResolver rewards = new KillRewardResolver();

    public ResponseDecision tryResolve(GameState state, TeamSide attackingSide, Lane lane,
                                       boolean defenderInitiallyTriggered, double overextension,
                                       Random random, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        CounterGankIneligibility reason = ineligibility(state, attackingSide, lane, time);
        if (reason != CounterGankIneligibility.NONE) {
            return new ResponseDecision(false, reason, defenderInitiallyTriggered, false, 0.0, false);
        }
        double responseChance = responseChance(state, attackingSide, defenderInitiallyTriggered, overextension);
        if (random.nextDouble() >= responseChance) {
            return new ResponseDecision(true, CounterGankIneligibility.NONE, defenderInitiallyTriggered,
                    true, responseChance, false);
        }

        TeamSide defendingSide = attackingSide.opposite();
        state.getCombatExecutionStats().recordCounterGankAttempt();
        JungleActionState attackingAction = state.jungleActionState(attackingSide);
        JungleActionState defendingAction = state.jungleActionState(defendingSide);
        attackingAction.recordGankAttempt(time, lane);
        defendingAction.recordCounterGankAttempt(time, lane);

        double attackingMechanics = groupMechanics(state, attackingSide, lane);
        double defendingMechanics = groupMechanics(state, defendingSide, lane);
        double attackingGold = groupGold(state, attackingSide, lane);
        double defendingGold = groupGold(state, defendingSide, lane);
        double edge = combatEdge(state, attackingSide, lane, overextension);
        double decisive = decisiveChance(state, attackingSide, lane, edge);
        double attackingWin = attackingSideWinChance(edge);
        CounterGankOutcome outcome = random.nextDouble() >= decisive
                ? CounterGankOutcome.NO_KILL
                : random.nextDouble() < attackingWin
                ? CounterGankOutcome.ATTACKING_SIDE_KILL
                : CounterGankOutcome.DEFENDING_SIDE_KILL;

        double before = state.laneState(lane).getPressure();
        double after = before;
        TeamSide winningSide = null;
        PlayerState killer = null;
        PlayerState victim = null;
        List<PlayerState> assistants = List.of();

        if (outcome != CounterGankOutcome.NO_KILL) {
            winningSide = outcome == CounterGankOutcome.ATTACKING_SIDE_KILL ? attackingSide : defendingSide;
            CombatParticipants participants = participants(state, winningSide, lane, random);
            killer = participants.killer();
            victim = participants.victim();
            assistants = participants.assistants();
            int eventStart = events.size();
            rewards.award(time, state.getTeamState(winningSide), killer, state.getTeamState(winningSide.opposite()),
                    victim, assistants, respawnDelaySeconds(time), false, null, events);
            for (int i = eventStart; i < events.size(); i++) events.get(i).setCombatSource(CombatSource.COUNTER_GANK);
            MatchEvent kill = new MatchEvent(time, MatchEventType.KILL, "Counter-gank kill",
                    killer.getPlayerName(), victim.getPlayerName(), names(assistants));
            kill.setParticipantPlayerIds(killer.getStructuredPlayerId(), victim.getStructuredPlayerId(),
                    ids(assistants));
            kill.setCombatSource(CombatSource.COUNTER_GANK);
            events.add(kill);
            double shock = lane == Lane.BOT ? CounterGankRuleConfig.BOT_COUNTER_GANK_PRESSURE_SHOCK
                    : CounterGankRuleConfig.SOLO_COUNTER_GANK_PRESSURE_SHOCK;
            after = clamp(before + (winningSide == TeamSide.BLUE ? shock : -shock), -100, 100);
            state.laneState(lane).setPressure(after);
            new ObjectivePriorityResolver().applyCounterGankKill(state, time, lane, winningSide);
        }

        MatchEvent event = new MatchEvent(time, MatchEventType.COUNTER_GANK, "Counter gank",
                killer == null ? null : killer.getPlayerName(),
                victim == null ? null : victim.getPlayerName(), names(assistants));
        event.setParticipantPlayerIds(
                killer == null ? null : killer.getStructuredPlayerId(),
                victim == null ? null : victim.getStructuredPlayerId(), ids(assistants));
        event.setCombatSource(CombatSource.COUNTER_GANK);
        event.setCounterGank(new CounterGankData(
                attackingSide, defendingSide,
                state.getTeamState(attackingSide).playerAt(Position.JUNGLE).getStructuredPlayerId(),
                state.getTeamState(defendingSide).playerAt(Position.JUNGLE).getStructuredPlayerId(),
                lane, defenderInitiallyTriggered, responseChance, outcome, winningSide,
                killer == null ? null : killer.getStructuredPlayerId(),
                victim == null ? null : victim.getStructuredPlayerId(), ids(assistants),
                before, after, overextension,
                attackingAction.getJungleFarmBlockedUntilSeconds(),
                defendingAction.getJungleFarmBlockedUntilSeconds(),
                edge, decisive, attackingWin,
                attackingMechanics, defendingMechanics, attackingGold, defendingGold));
        events.add(event);
        return new ResponseDecision(true, CounterGankIneligibility.NONE, defenderInitiallyTriggered,
                true, responseChance, true);
    }

    CounterGankIneligibility ineligibility(GameState state, TeamSide attackingSide, Lane lane, int time) {
        if (time < CounterGankRuleConfig.COUNTER_GANK_START_SECONDS
                || time > CounterGankRuleConfig.COUNTER_GANK_END_SECONDS) {
            return CounterGankIneligibility.OUTSIDE_WINDOW;
        }
        TeamSide defendingSide = attackingSide.opposite();
        if (!state.getTeamState(defendingSide).playerAt(Position.JUNGLE).canParticipateInMajorCombatAt(time)) {
            return CounterGankIneligibility.DEFENDING_JUNGLER_DEAD;
        }
        int last = state.jungleActionState(defendingSide).getLastJungleActionAtSeconds();
        if (last >= 0 && time - last < CounterGankRuleConfig.COUNTER_GANK_ACTION_COOLDOWN_SECONDS) {
            return CounterGankIneligibility.DEFENDING_JUNGLER_COOLDOWN;
        }
        for (TeamSide side : TeamSide.values()) {
            for (PlayerState player : lanePlayers(state.getTeamState(side), lane)) {
                if (!player.canParticipateInMajorCombatAt(time)) return CounterGankIneligibility.LANE_PARTICIPANT_DEAD;
            }
        }
        return CounterGankIneligibility.NONE;
    }

    double responseChance(GameState state, TeamSide attackingSide, boolean defenderInitiallyTriggered,
                          double overextension) {
        PlayerState defender = state.getTeamState(attackingSide.opposite()).playerAt(Position.JUNGLE);
        double tracking = defender.hasMatchPerformance() ? playerSkills.jungleTracking(defender) : defender.getAggression();
        double aggression = clamp((tracking - 14)
                        * CounterGankRuleConfig.DEFENDER_AGGRESSION_RESPONSE_FACTOR,
                CounterGankRuleConfig.DEFENDER_AGGRESSION_RESPONSE_MIN,
                CounterGankRuleConfig.DEFENDER_AGGRESSION_RESPONSE_MAX);
        return clamp(CounterGankRuleConfig.BASE_RESPONSE_CHANCE
                        + (defenderInitiallyTriggered ? CounterGankRuleConfig.DEFENDER_INITIAL_TRIGGER_BONUS : 0.0)
                        + aggression
                        + clamp(overextension, 0, 100) / 100.0
                        * CounterGankRuleConfig.OVEREXTENSION_RESPONSE_MAX_BONUS,
                CounterGankRuleConfig.MIN_RESPONSE_CHANCE, CounterGankRuleConfig.MAX_RESPONSE_CHANCE);
    }

    double groupMechanics(GameState state, TeamSide side, Lane lane) {
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        return (jungler.hasMatchPerformance() ? playerSkills.laneIntervention(jungler) : jungler.getMechanics())
                * CounterGankRuleConfig.JUNGLER_MECHANICS_CONTRIBUTION
                + laneMechanics(state, side, lane) * CounterGankRuleConfig.LANE_MECHANICS_CONTRIBUTION;
    }

    double groupAggression(GameState state, TeamSide side, Lane lane) {
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        return combatTendency(jungler)
                * CounterGankRuleConfig.JUNGLER_AGGRESSION_CONTRIBUTION
                + laneAggression(state, side, lane) * CounterGankRuleConfig.LANE_AGGRESSION_CONTRIBUTION;
    }

    double groupTeamfighting(GameState state, TeamSide side, Lane lane) {
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        return (jungler.hasMatchPerformance() ? playerSkills.combatExecution(jungler) : jungler.getTeamfighting())
                * CounterGankRuleConfig.JUNGLER_TEAMFIGHTING_CONTRIBUTION
                + laneTeamfighting(state, side, lane) * CounterGankRuleConfig.LANE_TEAMFIGHTING_CONTRIBUTION;
    }

    double groupGold(GameState state, TeamSide side, Lane lane) {
        List<PlayerState> players = new ArrayList<>();
        players.add(state.getTeamState(side).playerAt(Position.JUNGLE));
        players.addAll(lanePlayers(state.getTeamState(side), lane));
        return players.stream().mapToInt(PlayerState::getGold).average().orElse(0.0);
    }

    double combatEdge(GameState state, TeamSide attackingSide, Lane lane, double overextension) {
        TeamSide defendingSide = attackingSide.opposite();
        double goldEdge = clamp((groupGold(state, attackingSide, lane) - groupGold(state, defendingSide, lane))
                        / CounterGankRuleConfig.COUNTER_GANK_GOLD_DIVISOR,
                CounterGankRuleConfig.COUNTER_GANK_GOLD_EDGE_MIN,
                CounterGankRuleConfig.COUNTER_GANK_GOLD_EDGE_MAX);
        double overextensionEdge = clamp(overextension / CounterGankRuleConfig.OVEREXTENSION_EDGE_DIVISOR,
                0, CounterGankRuleConfig.OVEREXTENSION_EDGE_MAX);
        double existing=(groupMechanics(state,attackingSide,lane)-groupMechanics(state,defendingSide,lane))*CounterGankRuleConfig.MECHANICS_EDGE_FACTOR+(groupAggression(state,attackingSide,lane)-groupAggression(state,defendingSide,lane))*CounterGankRuleConfig.AGGRESSION_EDGE_FACTOR+(groupTeamfighting(state,attackingSide,lane)-groupTeamfighting(state,defendingSide,lane))*CounterGankRuleConfig.TEAMFIGHTING_EDGE_FACTOR+goldEdge+overextensionEdge-CounterGankRuleConfig.COUNTER_PREPARATION_EDGE;
        return existing+new CombatProgressionEvaluator().contribution(state,ProgressionCombatContext.COUNTER_GANK,combatGroup(state,attackingSide,lane),combatGroup(state,defendingSide,lane),existing,goldEdge);
    }

    private List<PlayerState> combatGroup(GameState state, TeamSide side, Lane lane) {List<PlayerState> result=new ArrayList<>();result.add(state.getTeamState(side).playerAt(Position.JUNGLE));result.addAll(lanePlayers(state.getTeamState(side),lane));return result;}

    double decisiveChance(GameState state, TeamSide attackingSide, Lane lane, double edge) {
        TeamSide defendingSide = attackingSide.opposite();
        double averageAggression = (groupAggression(state, attackingSide, lane)
                + groupAggression(state, defendingSide, lane)) / 2.0;
        return clamp(CounterGankRuleConfig.BASE_DECISIVE_CHANCE
                        + (averageAggression - 14) * CounterGankRuleConfig.AVERAGE_AGGRESSION_DECISIVE_FACTOR
                        + Math.abs(edge) * CounterGankRuleConfig.DECISIVE_EDGE_FACTOR
                        + (lane == Lane.BOT ? CounterGankRuleConfig.BOT_DECISIVE_BONUS : 0.0),
                CounterGankRuleConfig.MIN_DECISIVE_CHANCE, CounterGankRuleConfig.MAX_DECISIVE_CHANCE);
    }

    double attackingSideWinChance(double edge) {
        return clamp(CounterGankRuleConfig.BASE_ATTACKING_SIDE_WIN_CHANCE
                        + edge * CounterGankRuleConfig.ATTACKING_SIDE_WIN_EDGE_FACTOR,
                CounterGankRuleConfig.MIN_ATTACKING_SIDE_WIN_CHANCE,
                CounterGankRuleConfig.MAX_ATTACKING_SIDE_WIN_CHANCE);
    }

    private CombatParticipants participants(GameState state, TeamSide winningSide, Lane lane, Random random) {
        List<PlayerState> winners = participants(state.getTeamState(winningSide), lane);
        List<PlayerState> losers = participants(state.getTeamState(winningSide.opposite()), lane);
        PlayerState killer;
        PlayerState victim;
        if (lane == Lane.BOT) {
            killer = weightedPlayer(winners, List.of(
                    CounterGankRuleConfig.BOT_WINNER_JUNGLER_KILLER_WEIGHT,
                    CounterGankRuleConfig.BOT_WINNER_ADC_KILLER_WEIGHT,
                    CounterGankRuleConfig.BOT_WINNER_SUPPORT_KILLER_WEIGHT), random);
            victim = weightedPlayer(losers, List.of(
                    CounterGankRuleConfig.BOT_LOSER_JUNGLER_VICTIM_WEIGHT,
                    CounterGankRuleConfig.BOT_LOSER_ADC_VICTIM_WEIGHT,
                    CounterGankRuleConfig.BOT_LOSER_SUPPORT_VICTIM_WEIGHT), random);
        } else {
            killer = weightedPlayer(winners, List.of(
                    CounterGankRuleConfig.SOLO_WINNER_JUNGLER_KILLER_WEIGHT,
                    CounterGankRuleConfig.SOLO_WINNER_LANER_KILLER_WEIGHT), random);
            victim = weightedPlayer(losers, List.of(
                    CounterGankRuleConfig.SOLO_LOSER_JUNGLER_VICTIM_WEIGHT,
                    CounterGankRuleConfig.SOLO_LOSER_LANER_VICTIM_WEIGHT), random);
        }
        return new CombatParticipants(killer, victim, winners.stream().filter(player -> player != killer).toList());
    }

    private List<PlayerState> participants(TeamState team, Lane lane) {
        List<PlayerState> result = new ArrayList<>();
        result.add(team.playerAt(Position.JUNGLE));
        result.addAll(lanePlayers(team, lane));
        return result;
    }

    private List<PlayerState> lanePlayers(TeamState team, Lane lane) {
        return switch (lane) {
            case TOP -> List.of(team.playerAt(Position.TOP));
            case MID -> List.of(team.playerAt(Position.MID));
            case BOT -> List.of(team.playerAt(Position.ADC), team.playerAt(Position.SUPPORT));
        };
    }

    private double laneMechanics(GameState state, TeamSide side, Lane lane) {
        List<PlayerState> players = lanePlayers(state.getTeamState(side), lane);
        return lane == Lane.BOT
                ? players.get(0).getMechanics() * CounterGankRuleConfig.BOT_ADC_MECHANICS_CONTRIBUTION
                + players.get(1).getMechanics() * CounterGankRuleConfig.BOT_SUPPORT_MECHANICS_CONTRIBUTION
                : players.getFirst().getMechanics();
    }

    private double laneAggression(GameState state, TeamSide side, Lane lane) {
        List<PlayerState> players = lanePlayers(state.getTeamState(side), lane);
        return lane == Lane.BOT
                ? combatTendency(players.get(0)) * CounterGankRuleConfig.BOT_ADC_AGGRESSION_CONTRIBUTION
                + combatTendency(players.get(1)) * CounterGankRuleConfig.BOT_SUPPORT_AGGRESSION_CONTRIBUTION
                : combatTendency(players.getFirst());
    }

    private double combatTendency(PlayerState player) {
        return player.hasMatchPerformance() ? PlayerImpactRuleConfig.BASELINE_ATTRIBUTE : player.getAggression();
    }

    private double laneTeamfighting(GameState state, TeamSide side, Lane lane) {
        List<PlayerState> players = lanePlayers(state.getTeamState(side), lane);
        return lane == Lane.BOT
                ? players.get(0).getTeamfighting() * CounterGankRuleConfig.BOT_ADC_TEAMFIGHTING_CONTRIBUTION
                + players.get(1).getTeamfighting() * CounterGankRuleConfig.BOT_SUPPORT_TEAMFIGHTING_CONTRIBUTION
                : players.getFirst().getTeamfighting();
    }

    private PlayerState weightedPlayer(List<PlayerState> players, List<Double> weights, Random random) {
        double roll = random.nextDouble() * weights.stream().mapToDouble(Double::doubleValue).sum();
        for (int i = 0; i < players.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) return players.get(i);
        }
        return players.getLast();
    }

    private List<String> ids(List<PlayerState> players) {
        return players == null ? List.of()
                : players.stream().map(PlayerState::getStructuredPlayerId).toList();
    }

    private List<String> names(List<PlayerState> players) {
        return players == null ? List.of() : players.stream().map(PlayerState::getPlayerName).toList();
    }

    private int respawnDelaySeconds(int time) {
        return time < 600 ? RespawnRuleConfig.BEFORE_10_MINUTES_SECONDS
                : RespawnRuleConfig.FROM_10_TO_20_MINUTES_SECONDS;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record ResponseDecision(boolean eligible, CounterGankIneligibility ineligibility,
                            boolean defenderInitiallyTriggered, boolean responseRolled,
                            double responseChance, boolean responseSucceeded) {
        static ResponseDecision disabled(boolean defenderInitiallyTriggered) {
            return new ResponseDecision(false, CounterGankIneligibility.NONE, defenderInitiallyTriggered,
                    false, 0.0, false);
        }
    }

    private record CombatParticipants(PlayerState killer, PlayerState victim,
                                      List<PlayerState> assistants) { }
}

package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.JungleGankData;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Stateless jungle-gank resolver. All mutable clocks live in GameState. */
public final class JungleGankResolver {
    private final KillRewardResolver rewards = new KillRewardResolver();
    private final CounterGankResolver counterGankResolver = new CounterGankResolver();
    private final boolean counterGankEnabled;

    public JungleGankResolver() { this(true); }

    JungleGankResolver(boolean counterGankEnabled) {
        this.counterGankEnabled = counterGankEnabled;
    }

    public boolean resolve(GameState state, Random random, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        if (!state.shouldResolveJungleGankAt(time)) return false;
        state.markJungleGankResolvedAt(time);
        state.getCombatExecutionStats().recordJungleGankEvaluation();

        EnumMap<TeamSide, Double> triggered = new EnumMap<>(TeamSide.class);
        for (TeamSide side : List.of(TeamSide.BLUE, TeamSide.RED)) {
            if (!junglerEligible(state, side, time)) continue;
            double chance = attemptChance(state, side);
            if (random.nextDouble() < chance) triggered.put(side, chance);
        }
        if (triggered.isEmpty()) {
            state.getCombatExecutionStats().recordJungleGankAllTriggersFailed();
            return false;
        }
        state.getCombatExecutionStats().recordJungleGankAttempt();
        TeamSide side = triggered.size() == 1 ? triggered.keySet().iterator().next()
                : weightedSide(triggered, random);
        Lane lane = chooseTargetLane(state, side, time, random);
        double selectedWeight = targetWeight(state, side, lane, time);
        double attemptChance = attemptChance(state, side);
        double overextension = enemyOverextension(state, side, lane);
        JungleActionState action = state.jungleActionState(side);
        boolean defenderInitiallyTriggered = triggered.containsKey(side.opposite());
        CounterGankResolver.ResponseDecision counterDecision = counterGankEnabled
                ? counterGankResolver.tryResolve(state, side, lane, defenderInitiallyTriggered,
                        overextension, random, events)
                : CounterGankResolver.ResponseDecision.disabled(defenderInitiallyTriggered);
        if (counterDecision.responseSucceeded()) return true;
        action.recordGankAttempt(time, lane);

        double edge = combatEdge(state, side, lane);
        double decisive = decisiveChance(state, side, lane);
        double success = gankSuccessChance(edge);
        JungleGankOutcome outcome = random.nextDouble() >= decisive ? JungleGankOutcome.NO_KILL
                : random.nextDouble() < success ? JungleGankOutcome.GANK_SUCCESS
                : JungleGankOutcome.DEFENDER_REVERSE_KILL;
        double pressureBefore = state.laneState(lane).getPressure();
        if (outcome == JungleGankOutcome.NO_KILL) {
            events.add(gankEvent(time, side, state.getTeamState(side).playerAt(Position.JUNGLE).getPlayerName(), lane, outcome, null, null, null, List.of(),
                    pressureBefore, pressureBefore, overextension, action.getJungleFarmBlockedUntilSeconds(),
                    attemptChance, selectedWeight, edge, decisive, success,
                    triggered.containsKey(TeamSide.BLUE), triggered.containsKey(TeamSide.RED), counterDecision));
            return true;
        }

        CombatParticipants participants = outcome == JungleGankOutcome.GANK_SUCCESS
                ? successParticipants(state, side, lane, random)
                : reverseParticipants(state, side, lane, random);
        int eventStart = events.size();
        rewards.award(time, participants.winners(), participants.killer(), participants.losers(), participants.victim(),
                participants.assistants(), respawnDelaySeconds(time), false, null, events);
        for (int i = eventStart; i < events.size(); i++) events.get(i).setCombatSource(CombatSource.JUNGLE_GANK);
        MatchEvent kill = new MatchEvent(time, MatchEventType.KILL, "Jungle gank kill",
                participants.killer().getPlayerName(), participants.victim().getPlayerName(),
                ids(participants.assistants()));
        kill.setCombatSource(CombatSource.JUNGLE_GANK);
        events.add(kill);

        TeamSide winningSide = outcome == JungleGankOutcome.GANK_SUCCESS ? side : side.opposite();
        double shock = lane == Lane.BOT ? JungleGankRuleConfig.BOT_GANK_PRESSURE_SHOCK
                : JungleGankRuleConfig.SOLO_GANK_PRESSURE_SHOCK;
        double pressureAfter = clamp(pressureBefore + (winningSide == TeamSide.BLUE ? shock : -shock), -100, 100);
        state.laneState(lane).setPressure(pressureAfter);
        new ObjectivePriorityResolver().applyJungleGankKill(state, time, lane, winningSide);
        events.add(gankEvent(time, side, state.getTeamState(side).playerAt(Position.JUNGLE).getPlayerName(), lane, outcome, winningSide, participants.killer(), participants.victim(),
                participants.assistants(), pressureBefore, pressureAfter, overextension,
                action.getJungleFarmBlockedUntilSeconds(), attemptChance, selectedWeight, edge, decisive, success,
                triggered.containsKey(TeamSide.BLUE), triggered.containsKey(TeamSide.RED), counterDecision));
        return true;
    }

    boolean junglerEligible(GameState state, TeamSide side, int time) {
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        if (!jungler.canParticipateInMajorCombatAt(time)) return false;
        int last = state.jungleActionState(side).getLastJungleActionAtSeconds();
        if (last >= 0 && time - last < JungleGankRuleConfig.JUNGLER_GANK_COOLDOWN_SECONDS) return false;
        return Lane.values().length > 0 && java.util.Arrays.stream(Lane.values()).anyMatch(lane -> laneEligible(state, lane, time));
    }

    boolean laneEligible(GameState state, Lane lane, int time) {
        for (TeamSide side : TeamSide.values()) {
            for (PlayerState player : lanePlayers(state.getTeamState(side), lane)) {
                if (!player.canParticipateInMajorCombatAt(time)) return false;
            }
        }
        return true;
    }

    double relativePressure(GameState state, TeamSide side, Lane lane) {
        return side == TeamSide.BLUE ? state.laneState(lane).getPressure() : -state.laneState(lane).getPressure();
    }

    double enemyOverextension(GameState state, TeamSide side, Lane lane) {
        return Math.max(-relativePressure(state, side, lane), 0.0);
    }

    double attemptChance(GameState state, TeamSide side) {
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        double best = java.util.Arrays.stream(Lane.values())
                .filter(lane -> laneEligible(state, lane, state.getCurrentTimeSeconds()))
                .mapToDouble(lane -> enemyOverextension(state, side, lane)).max().orElse(0.0);
        double aggression = clamp((jungler.getAggression() - 14) * JungleGankRuleConfig.JUNGLE_AGGRESSION_ATTEMPT_FACTOR,
                JungleGankRuleConfig.JUNGLE_AGGRESSION_ATTEMPT_MIN, JungleGankRuleConfig.JUNGLE_AGGRESSION_ATTEMPT_MAX);
        return clamp(JungleGankRuleConfig.BASE_GANK_ATTEMPT_CHANCE + aggression
                        + best / 100.0 * JungleGankRuleConfig.OVEREXTENSION_ATTEMPT_MAX_BONUS,
                JungleGankRuleConfig.MIN_GANK_ATTEMPT_CHANCE, JungleGankRuleConfig.MAX_GANK_ATTEMPT_CHANCE);
    }

    double targetWeight(GameState state, TeamSide side, Lane lane, int time) {
        double followup = clamp((allyFollowupPower(state, side, lane) - 14)
                        * JungleGankRuleConfig.FOLLOWUP_TARGET_FACTOR,
                JungleGankRuleConfig.FOLLOWUP_TARGET_MIN, JungleGankRuleConfig.FOLLOWUP_TARGET_MAX);
        double gold = clamp((laneGold(state, side.opposite(), lane) - laneGold(state, side, lane))
                        / JungleGankRuleConfig.TARGET_GOLD_DIVISOR,
                JungleGankRuleConfig.TARGET_GOLD_MIN, JungleGankRuleConfig.TARGET_GOLD_MAX);
        double weight = Math.max(JungleGankRuleConfig.MIN_TARGET_WEIGHT,
                JungleGankRuleConfig.BASE_TARGET_WEIGHT
                        + enemyOverextension(state, side, lane) / 100.0
                        * JungleGankRuleConfig.OVEREXTENSION_TARGET_MAX_BONUS + followup + gold);
        int last = state.jungleActionState(side).getLastGankAttemptAtSeconds(lane);
        if (last >= 0 && time - last < JungleGankRuleConfig.SAME_LANE_REPEAT_COOLDOWN_SECONDS) {
            weight *= JungleGankRuleConfig.REPEAT_GANK_WEIGHT_MULTIPLIER;
        }
        if (lane == Lane.BOT) weight *= JungleGankRuleConfig.BOT_TARGET_WEIGHT_MULTIPLIER;
        return weight;
    }

    Lane chooseTargetLane(GameState state, TeamSide side, int time, Random random) {
        List<Lane> lanes = java.util.Arrays.stream(Lane.values()).filter(lane -> laneEligible(state, lane, time)).toList();
        List<Double> weights = lanes.stream().map(lane -> targetWeight(state, side, lane, time)).toList();
        double roll = random.nextDouble() * weights.stream().mapToDouble(Double::doubleValue).sum();
        for (int i = 0; i < lanes.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) return lanes.get(i);
        }
        return lanes.getLast();
    }

    double attackerMechanics(GameState state, TeamSide side, Lane lane) {
        return state.getTeamState(side).playerAt(Position.JUNGLE).getMechanics()
                * JungleGankRuleConfig.JUNGLER_MECHANICS_CONTRIBUTION
                + laneMechanics(state, side, lane) * JungleGankRuleConfig.LANE_MECHANICS_CONTRIBUTION;
    }

    double attackerAggression(GameState state, TeamSide side, Lane lane) {
        return state.getTeamState(side).playerAt(Position.JUNGLE).getAggression()
                * JungleGankRuleConfig.JUNGLER_AGGRESSION_CONTRIBUTION
                + laneAggression(state, side, lane) * JungleGankRuleConfig.LANE_AGGRESSION_CONTRIBUTION;
    }

    double goldEdge(GameState state, TeamSide side, Lane lane) {
        TeamState attackers = state.getTeamState(side), defenders = state.getTeamState(side.opposite());
        List<PlayerState> attack = new ArrayList<>();
        attack.add(attackers.playerAt(Position.JUNGLE));
        attack.addAll(lanePlayers(attackers, lane));
        return clamp((averageGold(attack) - averageGold(lanePlayers(defenders, lane)))
                        / JungleGankRuleConfig.GANK_GOLD_DIVISOR,
                JungleGankRuleConfig.GANK_GOLD_EDGE_MIN, JungleGankRuleConfig.GANK_GOLD_EDGE_MAX);
    }

    double combatEdge(GameState state, TeamSide side, Lane lane) {
        double mechanicsEdge = attackerMechanics(state, side, lane) - laneMechanics(state, side.opposite(), lane);
        double aggressionEdge = attackerAggression(state, side, lane) - laneAggression(state, side.opposite(), lane);
        double vulnerability = clamp(enemyOverextension(state, side, lane)
                        / JungleGankRuleConfig.GANK_VULNERABILITY_DIVISOR,
                0, JungleGankRuleConfig.GANK_VULNERABILITY_EDGE_MAX);
        return (lane == Lane.BOT ? JungleGankRuleConfig.BOT_GANK_NUMBERS_EDGE
                : JungleGankRuleConfig.SOLO_GANK_NUMBERS_EDGE)
                + mechanicsEdge * JungleGankRuleConfig.GANK_MECHANICS_EDGE_FACTOR
                + aggressionEdge * JungleGankRuleConfig.GANK_AGGRESSION_EDGE_FACTOR
                + goldEdge(state, side, lane) + vulnerability;
    }

    double decisiveChance(GameState state, TeamSide side, Lane lane) {
        PlayerState jungler = state.getTeamState(side).playerAt(Position.JUNGLE);
        return clamp(JungleGankRuleConfig.BASE_GANK_DECISIVE_CHANCE
                        + (jungler.getAggression() - 14) * JungleGankRuleConfig.GANK_DECISIVE_AGGRESSION_FACTOR
                        + Math.abs(combatEdge(state, side, lane)) * JungleGankRuleConfig.GANK_DECISIVE_EDGE_FACTOR
                        + enemyOverextension(state, side, lane) / 100.0
                        * JungleGankRuleConfig.GANK_DECISIVE_OVEREXTENSION_MAX_BONUS,
                JungleGankRuleConfig.MIN_GANK_DECISIVE_CHANCE, JungleGankRuleConfig.MAX_GANK_DECISIVE_CHANCE);
    }

    double gankSuccessChance(double combatEdge) {
        return clamp(JungleGankRuleConfig.BASE_GANK_SUCCESS_CHANCE
                        + combatEdge * JungleGankRuleConfig.GANK_SUCCESS_EDGE_FACTOR,
                JungleGankRuleConfig.MIN_GANK_SUCCESS_CHANCE, JungleGankRuleConfig.MAX_GANK_SUCCESS_CHANCE);
    }

    private CombatParticipants successParticipants(GameState state, TeamSide side, Lane lane, Random random) {
        TeamState winners = state.getTeamState(side), losers = state.getTeamState(side.opposite());
        PlayerState jungler = winners.playerAt(Position.JUNGLE);
        List<PlayerState> laners = lanePlayers(winners, lane);
        PlayerState killer;
        PlayerState victim;
        List<PlayerState> assists;
        if (lane != Lane.BOT) {
            killer = random.nextDouble() < JungleGankRuleConfig.SOLO_SUCCESS_JUNGLER_KILLER_WEIGHT ? jungler : laners.getFirst();
            assists = List.of(killer == jungler ? laners.getFirst() : jungler);
            victim = lanePlayers(losers, lane).getFirst();
        } else {
            killer = weightedPlayer(List.of(jungler, laners.get(0), laners.get(1)),
                    List.of(JungleGankRuleConfig.BOT_SUCCESS_JUNGLER_KILLER_WEIGHT,
                            JungleGankRuleConfig.BOT_SUCCESS_ADC_KILLER_WEIGHT,
                            JungleGankRuleConfig.BOT_SUCCESS_SUPPORT_KILLER_WEIGHT), random);
            assists = List.of(jungler, laners.get(0), laners.get(1)).stream().filter(p -> p != killer).toList();
            victim = random.nextDouble() < JungleGankRuleConfig.BOT_SUCCESS_ADC_VICTIM_WEIGHT
                    ? losers.playerAt(Position.ADC) : losers.playerAt(Position.SUPPORT);
        }
        return new CombatParticipants(winners, losers, killer, victim, assists);
    }

    private CombatParticipants reverseParticipants(GameState state, TeamSide side, Lane lane, Random random) {
        TeamState winners = state.getTeamState(side.opposite()), losers = state.getTeamState(side);
        PlayerState jungler = losers.playerAt(Position.JUNGLE);
        List<PlayerState> attackers = lanePlayers(losers, lane);
        if (lane != Lane.BOT) {
            PlayerState victim = random.nextDouble() < JungleGankRuleConfig.SOLO_REVERSE_JUNGLER_VICTIM_WEIGHT
                    ? jungler : attackers.getFirst();
            return new CombatParticipants(winners, losers, lanePlayers(winners, lane).getFirst(), victim, List.of());
        }
        PlayerState killer = random.nextDouble() < JungleGankRuleConfig.BOT_REVERSE_ADC_KILLER_WEIGHT
                ? winners.playerAt(Position.ADC) : winners.playerAt(Position.SUPPORT);
        PlayerState assistant = killer.getPosition() == Position.ADC
                ? winners.playerAt(Position.SUPPORT) : winners.playerAt(Position.ADC);
        PlayerState victim = weightedPlayer(List.of(jungler, attackers.get(0), attackers.get(1)),
                List.of(JungleGankRuleConfig.BOT_REVERSE_JUNGLER_VICTIM_WEIGHT,
                        JungleGankRuleConfig.BOT_REVERSE_ADC_VICTIM_WEIGHT,
                        JungleGankRuleConfig.BOT_REVERSE_SUPPORT_VICTIM_WEIGHT), random);
        return new CombatParticipants(winners, losers, killer, victim, List.of(assistant));
    }

    private MatchEvent gankEvent(int time, TeamSide side, String junglerPlayerId, Lane lane, JungleGankOutcome outcome,
                                 TeamSide winning, PlayerState killer, PlayerState victim, List<PlayerState> assists,
                                 double before, double after, double overextension, int blockedUntil,
                                 double attemptChance, double targetWeight, double edge, double decisive, double success,
                                 boolean blueTriggered, boolean redTriggered,
                                 CounterGankResolver.ResponseDecision counterDecision) {
        MatchEvent event = new MatchEvent(time, MatchEventType.JUNGLE_GANK, "Jungle gank",
                killer == null ? null : killer.getPlayerName(), victim == null ? null : victim.getPlayerName(), ids(assists));
        event.setCombatSource(CombatSource.JUNGLE_GANK);
        event.setJungleGank(new JungleGankData(side,
                junglerPlayerId, lane, outcome, winning,
                killer == null ? null : killer.getPlayerName(), victim == null ? null : victim.getPlayerName(), ids(assists),
                before, after, overextension, blockedUntil, attemptChance, targetWeight, edge, decisive, success,
                blueTriggered, redTriggered,
                counterDecision.eligible(), counterDecision.ineligibility(),
                counterDecision.defenderInitiallyTriggered(), counterDecision.responseRolled(),
                counterDecision.responseChance(), counterDecision.responseSucceeded()));
        return event;
    }

    private TeamSide weightedSide(Map<TeamSide, Double> weights, Random random) {
        double blue = weights.get(TeamSide.BLUE), red = weights.get(TeamSide.RED);
        return random.nextDouble() < blue / (blue + red) ? TeamSide.BLUE : TeamSide.RED;
    }

    private double allyFollowupPower(GameState state, TeamSide side, Lane lane) {
        return laneMechanics(state, side, lane) * .55 + laneAggression(state, side, lane) * .45;
    }

    private double laneMechanics(GameState state, TeamSide side, Lane lane) {
        List<PlayerState> players = lanePlayers(state.getTeamState(side), lane);
        return lane == Lane.BOT ? players.get(0).getMechanics() * JungleGankRuleConfig.BOT_ADC_MECHANICS_CONTRIBUTION
                + players.get(1).getMechanics() * JungleGankRuleConfig.BOT_SUPPORT_MECHANICS_CONTRIBUTION
                : players.getFirst().getMechanics();
    }

    private double laneAggression(GameState state, TeamSide side, Lane lane) {
        List<PlayerState> players = lanePlayers(state.getTeamState(side), lane);
        return lane == Lane.BOT ? players.get(0).getAggression() * JungleGankRuleConfig.BOT_ADC_AGGRESSION_CONTRIBUTION
                + players.get(1).getAggression() * JungleGankRuleConfig.BOT_SUPPORT_AGGRESSION_CONTRIBUTION
                : players.getFirst().getAggression();
    }

    private double laneGold(GameState state, TeamSide side, Lane lane) {
        return averageGold(lanePlayers(state.getTeamState(side), lane));
    }

    private List<PlayerState> lanePlayers(TeamState team, Lane lane) {
        return switch (lane) {
            case TOP -> List.of(team.playerAt(Position.TOP));
            case MID -> List.of(team.playerAt(Position.MID));
            case BOT -> List.of(team.playerAt(Position.ADC), team.playerAt(Position.SUPPORT));
        };
    }

    private PlayerState weightedPlayer(List<PlayerState> players, List<Double> weights, Random random) {
        double roll = random.nextDouble() * weights.stream().mapToDouble(Double::doubleValue).sum();
        for (int i = 0; i < players.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) return players.get(i);
        }
        return players.getLast();
    }

    private double averageGold(List<PlayerState> players) {
        return players.stream().mapToInt(PlayerState::getGold).average().orElse(0.0);
    }

    private List<String> ids(List<PlayerState> players) { return players.stream().map(PlayerState::getPlayerName).toList(); }
    private int respawnDelaySeconds(int time) { return time < 600 ? RespawnRuleConfig.BEFORE_10_MINUTES_SECONDS : RespawnRuleConfig.FROM_10_TO_20_MINUTES_SECONDS; }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private record CombatParticipants(TeamState winners, TeamState losers, PlayerState killer,
                                      PlayerState victim, List<PlayerState> assistants) { }
}

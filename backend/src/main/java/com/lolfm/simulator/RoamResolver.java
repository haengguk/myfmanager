package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import com.lolfm.domain.RoamData;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Stateless resolver for one possible MID or SUPPORT roam per evaluation tick. */
public final class RoamResolver {
    private final KillRewardResolver rewards = new KillRewardResolver();

    public boolean resolve(GameState state, Random random, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        if (!state.shouldResolveRoamAt(time)) return false;
        state.markRoamEvaluatedAt(time);
        RoamExecutionStats stats = state.getRoamExecutionStats();
        stats.recordEvaluation();
        List<Candidate> triggered = new ArrayList<>();
        for (Candidate candidate : candidates(state, time)) {
            stats.recordCandidateEvaluation(candidate.position(), candidate.side());
            RoamIneligibility ineligibility = ineligibility(state, candidate, time);
            if (ineligibility != RoamIneligibility.NONE) {
                stats.recordIneligible(ineligibility);
                continue;
            }
            double chance = attemptChance(state, candidate);
            stats.recordTriggerRoll();
            if (random.nextDouble() < chance) {
                triggered.add(candidate.withAttemptChance(chance));
                stats.recordTrigger(candidate.side());
            }
        }
        if (triggered.isEmpty()) return false;
        if (triggered.size() > 1) stats.recordMultipleTriggers();
        Candidate selected = weightedCandidate(triggered, random);
        stats.recordUnselectedTriggers(triggered.size() - 1);
        Lane target = selected.position() == Position.SUPPORT ? Lane.MID : weightedTarget(state, selected, time, random);
        double targetWeight = targetWeight(state, selected, target, time);
        PlayerState roamer = player(state, selected);
        state.markMajorCombatParticipant(roamer);
        for (TeamSide participantSide : TeamSide.values()) {
            for (PlayerState participant : lanePlayers(state.getTeamState(participantSide), target)) state.markMajorCombatParticipant(participant);
        }
        int lastTargetAttempt = roamer.getRoamActionState().getLastRoamAttemptAtSeconds(target);
        boolean repeatTarget = lastTargetAttempt >= 0
                && time < lastTargetAttempt + RoamRuleConfig.SAME_TARGET_REPEAT_WINDOW_SECONDS;
        int blockSeconds = selected.position() == Position.MID
                ? RoamRuleConfig.MID_ROAM_FARM_BLOCK_SECONDS : RoamRuleConfig.SUPPORT_ROAM_FARM_BLOCK_SECONDS;
        roamer.getRoamActionState().recordAttempt(time, target, blockSeconds);
        roamer.beginRoamActivity(origin(selected.position()), target, time);
        stats.recordAttempt(selected.position(), target);
        stats.recordActivityCreated();

        double originBefore = state.laneState(origin(selected.position())).getPressure();
        double originAfter = clamp(originBefore + (selected.side() == TeamSide.BLUE ? -originCost(selected.position()) : originCost(selected.position())), -100, 100);
        state.laneState(origin(selected.position())).setPressure(originAfter);
        double targetBefore = state.laneState(target).getPressure();
        double overextension = enemyOverextension(state, selected.side(), target);
        RoamCombatEdgeBreakdown edgeBreakdown = combatEdgeBreakdown(state, selected, target, overextension);
        double edge = edgeBreakdown.combatEdge();
        double decisive = decisiveChance(roamer, edge, overextension);
        RoamOutcome outcome = random.nextDouble() >= decisive ? RoamOutcome.NO_KILL
                : random.nextDouble() < successChance(edge) ? RoamOutcome.ROAMING_SIDE_KILL : RoamOutcome.DEFENDING_SIDE_KILL;
        stats.recordOutcome(outcome);
        TeamSide winning = outcome == RoamOutcome.NO_KILL ? null
                : outcome == RoamOutcome.ROAMING_SIDE_KILL ? selected.side() : selected.side().opposite();
        PlayerState killer = null, victim = null;
        List<PlayerState> assistants = List.of();
        double targetAfter = targetBefore;
        if (outcome != RoamOutcome.NO_KILL) {
            Participants participants = participants(state, selected, target, outcome, random);
            killer = participants.killer(); victim = participants.victim(); assistants = participants.assistants();
            if (victim.getActivityState().getActivityType() == PlayerActivityType.ROAMING) {
                stats.recordActivityClearedByDeath();
            }
            int start = events.size();
            rewards.award(time, state.getTeamState(winning), killer, state.getTeamState(winning.opposite()), victim,
                    assistants, respawnDelay(time), false, null, events);
            for (int i = start; i < events.size(); i++) events.get(i).setCombatSource(CombatSource.ROAM);
            MatchEvent kill = new MatchEvent(time, MatchEventType.KILL, "Roam kill", killer.getPlayerName(), victim.getPlayerName(), ids(assistants));
            kill.setCombatSource(CombatSource.ROAM); events.add(kill);
            double shock = target == Lane.BOT ? RoamRuleConfig.BOT_TARGET_PRESSURE_SHOCK : RoamRuleConfig.SOLO_TARGET_PRESSURE_SHOCK;
            targetAfter = clamp(targetBefore + (winning == TeamSide.BLUE ? shock : -shock), -100, 100);
            state.laneState(target).setPressure(targetAfter);
            new ObjectivePriorityResolver().applyRoamKill(state, time, target, winning);
        }
        MatchEvent event = new MatchEvent(time, MatchEventType.ROAM, "Roam", killer == null ? null : killer.getPlayerName(), victim == null ? null : victim.getPlayerName(), ids(assistants));
        event.setCombatSource(CombatSource.ROAM);
        event.setRoam(new RoamData(selected.side(), roamer.getPlayerName(), selected.position(), origin(selected.position()), target,
                outcome, winning, killer == null ? null : killer.getPlayerName(), victim == null ? null : victim.getPlayerName(), ids(assistants),
                originBefore, originAfter, targetBefore, targetAfter, originPriority(state, selected), overextension,
                roamer.getActivityState().getActivityUntilSeconds(), roamer.getRoamActionState().getRoamFarmBlockedUntilSeconds(),
                selected.attemptChance(), targetWeight, edge, decisive, successChance(edge), repeatTarget, repeatTarget,
                roamer.getMechanics(), roamer.getAggression(), roamer.getFarming(), roamer.getTeamfighting(),
                edgeBreakdown.attackerMechanics(), edgeBreakdown.defenderMechanics(), edgeBreakdown.attackerAggression(),
                edgeBreakdown.defenderAggression(), edgeBreakdown.attackerTeamfighting(), edgeBreakdown.defenderTeamfighting(),
                edgeBreakdown.mechanicsEdge(), edgeBreakdown.aggressionEdge(), edgeBreakdown.teamfightingEdge(),
                edgeBreakdown.goldEdge(), edgeBreakdown.vulnerabilityEdge(), edgeBreakdown.numbersEdge()));
        events.add(event);
        return true;
    }

    private List<Candidate> candidates(GameState state, int time) {
        List<Candidate> result = new ArrayList<>();
        if (time >= RoamRuleConfig.MID_ROAM_START_SECONDS) { result.add(new Candidate(TeamSide.BLUE, Position.MID, 0)); result.add(new Candidate(TeamSide.RED, Position.MID, 0)); }
        if (time >= RoamRuleConfig.SUPPORT_ROAM_START_SECONDS) { result.add(new Candidate(TeamSide.BLUE, Position.SUPPORT, 0)); result.add(new Candidate(TeamSide.RED, Position.SUPPORT, 0)); }
        return result;
    }

    boolean eligible(GameState state, Candidate candidate, int time) {
        return ineligibility(state, candidate, time) == RoamIneligibility.NONE;
    }
    RoamIneligibility ineligibility(GameState state, Candidate candidate, int time) {
        if (state.isLanePhaseEnabled() && !state.isLaneLaning(origin(candidate.position()))) {
            state.getLanePhaseExecutionStats().recordRoamOriginExcluded();
            return RoamIneligibility.NO_TARGET;
        }
        PlayerState roamer = player(state, candidate);
        if (!roamer.isAlive(time)) return RoamIneligibility.DEAD;
        if (roamer.getActivityState().getActivityType() != PlayerActivityType.DEFAULT_ROLE) return RoamIneligibility.ACTIVITY;
        int last = roamer.getRoamActionState().getLastRoamAttemptAtSeconds();
        if (last >= 0 && time < last + RoamRuleConfig.ROAM_ACTION_COOLDOWN_SECONDS) return RoamIneligibility.COOLDOWN;
        return targets(candidate.position()).stream().anyMatch(lane -> targetEligible(state, candidate.side(), lane, time))
                ? RoamIneligibility.NONE : RoamIneligibility.NO_TARGET;
    }
    boolean targetEligible(GameState state, TeamSide side, Lane lane, int time) {
        if (state.isLanePhaseEnabled() && !state.isLaneLaning(lane)) {
            state.getLanePhaseExecutionStats().recordRoamTargetExcluded();
            return false;
        }
        for (TeamSide participantSide : TeamSide.values()) for (PlayerState p : lanePlayers(state.getTeamState(participantSide), lane))
            if (!p.canParticipateInMajorCombatAt(time)) return false;
        return true;
    }
    double attemptChance(GameState state, Candidate c) {
        PlayerState p = player(state, c);
        double best = targets(c.position()).stream().filter(l -> targetEligible(state,c.side(),l,state.getCurrentTimeSeconds())).mapToDouble(l -> enemyOverextension(state,c.side(),l)).max().orElse(0);
        double base = c.position() == Position.MID ? RoamRuleConfig.BASE_MID_ROAM_ATTEMPT_CHANCE : RoamRuleConfig.BASE_SUPPORT_ROAM_ATTEMPT_CHANCE;
        return clamp(base + clamp((p.getAggression()-14)*RoamRuleConfig.ROAMER_AGGRESSION_ATTEMPT_FACTOR, RoamRuleConfig.ROAMER_AGGRESSION_ATTEMPT_MIN, RoamRuleConfig.ROAMER_AGGRESSION_ATTEMPT_MAX)
                + originPriority(state,c)/100*RoamRuleConfig.ORIGIN_PRIORITY_ATTEMPT_MAX_BONUS + best/100*RoamRuleConfig.TARGET_OVEREXTENSION_ATTEMPT_MAX_BONUS,
                RoamRuleConfig.MIN_ROAM_ATTEMPT_CHANCE, RoamRuleConfig.MAX_ROAM_ATTEMPT_CHANCE);
    }
    double targetWeight(GameState state, Candidate c, Lane lane, int time) {
        if (c.position() == Position.SUPPORT) return RoamRuleConfig.BASE_TARGET_WEIGHT;
        double followup = clamp((group(state,c.side(),lane, PlayerState::getMechanics)*.55 + group(state,c.side(),lane, PlayerState::getAggression)*.45 - 14)*RoamRuleConfig.FOLLOWUP_TARGET_FACTOR, RoamRuleConfig.FOLLOWUP_TARGET_MIN, RoamRuleConfig.FOLLOWUP_TARGET_MAX);
        double gold = clamp((group(state,c.side().opposite(),lane,PlayerState::getGold)-group(state,c.side(),lane,PlayerState::getGold))/RoamRuleConfig.TARGET_GOLD_DIVISOR, RoamRuleConfig.TARGET_GOLD_MIN, RoamRuleConfig.TARGET_GOLD_MAX);
        double weight = Math.max(RoamRuleConfig.MIN_TARGET_WEIGHT, RoamRuleConfig.BASE_TARGET_WEIGHT + enemyOverextension(state,c.side(),lane)/100*RoamRuleConfig.TARGET_OVEREXTENSION_WEIGHT_MAX_BONUS + followup + gold);
        int last = player(state,c).getRoamActionState().getLastRoamAttemptAtSeconds(lane);
        if (last >= 0 && time < last + RoamRuleConfig.SAME_TARGET_REPEAT_WINDOW_SECONDS) weight *= RoamRuleConfig.REPEAT_TARGET_WEIGHT_MULTIPLIER;
        return lane == Lane.BOT ? weight * RoamRuleConfig.BOT_TARGET_WEIGHT_MULTIPLIER : weight;
    }
    private Lane weightedTarget(GameState state, Candidate c, int time, Random random) { List<Lane> lanes=targets(c.position()).stream().filter(l->targetEligible(state,c.side(),l,time)).toList(); double total=lanes.stream().mapToDouble(l->targetWeight(state,c,l,time)).sum(); double roll=random.nextDouble()*total; for(Lane lane:lanes){roll-=targetWeight(state,c,lane,time);if(roll<=0)return lane;} return lanes.getLast(); }
    private Participants participants(GameState state, Candidate c, Lane lane, RoamOutcome outcome, Random random) {
        List<PlayerState> attackers = new ArrayList<>(); attackers.add(player(state,c)); attackers.addAll(lanePlayers(state.getTeamState(c.side()),lane));
        List<PlayerState> defenders = lanePlayers(state.getTeamState(c.side().opposite()),lane);
        if (outcome == RoamOutcome.ROAMING_SIDE_KILL) { PlayerState killer=weighted(attackers, successWeights(c.position(),lane),random); PlayerState victim=lane==Lane.BOT?weighted(defenders,List.of(.65,.35),random):defenders.getFirst(); return new Participants(killer,victim,attackers.stream().filter(p->p!=killer).toList()); }
        PlayerState killer=lane==Lane.BOT?weighted(defenders,List.of(RoamRuleConfig.BOT_REVERSE_ADC_KILLER_WEIGHT,RoamRuleConfig.BOT_REVERSE_SUPPORT_KILLER_WEIGHT),random):defenders.getFirst();
        PlayerState victim=weighted(attackers, reverseWeights(c.position(),lane),random); return new Participants(killer,victim,lane==Lane.BOT?defenders.stream().filter(p->p!=killer).toList():List.of());
    }
    private List<Double> successWeights(Position p,Lane lane){ if(p==Position.SUPPORT)return List.of(RoamRuleConfig.SUPPORT_TO_MID_ROAMER_KILLER_WEIGHT,RoamRuleConfig.SUPPORT_TO_MID_MID_KILLER_WEIGHT); if(lane==Lane.TOP)return List.of(RoamRuleConfig.MID_TO_TOP_ROAMER_KILLER_WEIGHT,RoamRuleConfig.MID_TO_TOP_LANER_KILLER_WEIGHT); return List.of(RoamRuleConfig.MID_TO_BOT_ROAMER_KILLER_WEIGHT,RoamRuleConfig.MID_TO_BOT_ADC_KILLER_WEIGHT,RoamRuleConfig.MID_TO_BOT_SUPPORT_KILLER_WEIGHT); }
    private List<Double> reverseWeights(Position p,Lane lane){ if(lane==Lane.BOT)return List.of(RoamRuleConfig.BOT_REVERSE_ROAMER_VICTIM_WEIGHT,RoamRuleConfig.BOT_REVERSE_ADC_VICTIM_WEIGHT,RoamRuleConfig.BOT_REVERSE_SUPPORT_VICTIM_WEIGHT); return List.of(RoamRuleConfig.SOLO_REVERSE_ROAMER_VICTIM_WEIGHT,RoamRuleConfig.SOLO_REVERSE_TARGET_LANER_VICTIM_WEIGHT); }
    double combatEdge(GameState s, Candidate c, Lane l, double over) { return combatEdgeBreakdown(s, c, l, over).combatEdge(); }
    RoamCombatEdgeBreakdown combatEdgeBreakdown(GameState s, Candidate c, Lane l, double over) {
        PlayerState roamer = player(s, c); TeamSide defender = c.side().opposite();
        double attackerMechanics = roamer.getMechanics() * .45 + group(s, c.side(), l, PlayerState::getMechanics) * .55;
        double attackerAggression = roamer.getAggression() * .55 + group(s, c.side(), l, PlayerState::getAggression) * .45;
        double attackerTeamfighting = roamer.getTeamfighting() * .40 + group(s, c.side(), l, PlayerState::getTeamfighting) * .60;
        double defenderMechanics = group(s, defender, l, PlayerState::getMechanics), defenderAggression = group(s, defender, l, PlayerState::getAggression), defenderTeamfighting = group(s, defender, l, PlayerState::getTeamfighting);
        double mechanicsEdge = (attackerMechanics - defenderMechanics) * RoamRuleConfig.ROAM_MECHANICS_EDGE_FACTOR;
        double aggressionEdge = (attackerAggression - defenderAggression) * RoamRuleConfig.ROAM_AGGRESSION_EDGE_FACTOR;
        double teamfightingEdge = (attackerTeamfighting - defenderTeamfighting) * RoamRuleConfig.ROAM_TEAMFIGHTING_EDGE_FACTOR;
        double goldEdge = clamp((averageGold(roamer, lanePlayers(s.getTeamState(c.side()), l)) - averageGold(null, lanePlayers(s.getTeamState(defender), l))) / RoamRuleConfig.ROAM_GOLD_DIVISOR, RoamRuleConfig.ROAM_GOLD_EDGE_MIN, RoamRuleConfig.ROAM_GOLD_EDGE_MAX);
        double vulnerabilityEdge = clamp(over / RoamRuleConfig.ROAM_VULNERABILITY_DIVISOR, 0, RoamRuleConfig.ROAM_VULNERABILITY_EDGE_MAX);
        double numbersEdge = l == Lane.BOT ? RoamRuleConfig.BOT_ROAM_NUMBERS_EDGE : RoamRuleConfig.SOLO_ROAM_NUMBERS_EDGE;
        List<PlayerState> own=new ArrayList<>();own.add(roamer);own.addAll(lanePlayers(s.getTeamState(c.side()),l));
        double existing=numbersEdge+mechanicsEdge+aggressionEdge+teamfightingEdge+goldEdge+vulnerabilityEdge;
        double progression=new CombatProgressionEvaluator().contribution(s,ProgressionCombatContext.ROAM,own,lanePlayers(s.getTeamState(defender),l),existing,goldEdge);
        return new RoamCombatEdgeBreakdown(attackerMechanics,defenderMechanics,attackerAggression,defenderAggression,attackerTeamfighting,defenderTeamfighting,mechanicsEdge,aggressionEdge,teamfightingEdge,goldEdge,vulnerabilityEdge,numbersEdge,existing+progression);
    }
    double decisiveChance(PlayerState p,double edge,double over){return clamp(RoamRuleConfig.BASE_ROAM_DECISIVE_CHANCE+(p.getAggression()-14)*RoamRuleConfig.ROAMER_AGGRESSION_DECISIVE_FACTOR+Math.abs(edge)*RoamRuleConfig.ROAM_DECISIVE_EDGE_FACTOR+over/100*RoamRuleConfig.ROAM_DECISIVE_OVEREXTENSION_MAX_BONUS,RoamRuleConfig.MIN_ROAM_DECISIVE_CHANCE,RoamRuleConfig.MAX_ROAM_DECISIVE_CHANCE);}
    double successChance(double edge){return clamp(RoamRuleConfig.BASE_ROAM_SUCCESS_CHANCE+edge*RoamRuleConfig.ROAM_SUCCESS_EDGE_FACTOR,RoamRuleConfig.MIN_ROAM_SUCCESS_CHANCE,RoamRuleConfig.MAX_ROAM_SUCCESS_CHANCE);}
    private double group(GameState s,TeamSide side,Lane l,java.util.function.ToIntFunction<PlayerState> value){List<PlayerState> ps=lanePlayers(s.getTeamState(side),l);return l==Lane.BOT?value.applyAsInt(ps.get(0))*.60+value.applyAsInt(ps.get(1))*.40:value.applyAsInt(ps.getFirst());}
    private double averageGold(PlayerState roamer,List<PlayerState> ps){double sum=roamer==null?0:roamer.getGold();for(PlayerState p:ps)sum+=p.getGold();return sum/(ps.size()+(roamer==null?0:1));}
    double originPriority(GameState s,Candidate c){return Math.max(relativePressure(s,c.side(),origin(c.position())),0);}
    double enemyOverextension(GameState s,TeamSide side,Lane lane){return Math.max(-relativePressure(s,side,lane),0);}
    private double relativePressure(GameState s,TeamSide side,Lane lane){return side==TeamSide.BLUE?s.laneState(lane).getPressure():-s.laneState(lane).getPressure();}
    private Candidate weightedCandidate(List<Candidate> cs,Random r){double total=cs.stream().mapToDouble(Candidate::attemptChance).sum(),roll=r.nextDouble()*total;for(Candidate c:cs){roll-=c.attemptChance();if(roll<=0)return c;}return cs.getLast();}
    private PlayerState weighted(List<PlayerState> ps,List<Double>w,Random r){double roll=r.nextDouble()*w.stream().mapToDouble(Double::doubleValue).sum();for(int i=0;i<ps.size();i++){roll-=w.get(i);if(roll<=0)return ps.get(i);}return ps.getLast();}
    private PlayerState player(GameState s,Candidate c){return s.getTeamState(c.side()).playerAt(c.position());}
    private List<Lane> targets(Position p){return p==Position.MID?List.of(Lane.TOP,Lane.BOT):List.of(Lane.MID);}
    private Lane origin(Position p){return p==Position.MID?Lane.MID:Lane.BOT;}
    private double originCost(Position p){return p==Position.MID?RoamRuleConfig.MID_ORIGIN_PRESSURE_COST:RoamRuleConfig.SUPPORT_ORIGIN_PRESSURE_COST;}
    private List<PlayerState> lanePlayers(TeamState t,Lane l){return switch(l){case TOP->List.of(t.playerAt(Position.TOP));case MID->List.of(t.playerAt(Position.MID));case BOT->List.of(t.playerAt(Position.ADC),t.playerAt(Position.SUPPORT));};}
    private List<String> ids(List<PlayerState> ps){return ps.stream().map(PlayerState::getPlayerName).toList();}
    private int respawnDelay(int time){return time<600?RespawnRuleConfig.BEFORE_10_MINUTES_SECONDS:RespawnRuleConfig.FROM_10_TO_20_MINUTES_SECONDS;}
    private double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    record Candidate(TeamSide side,Position position,double attemptChance){Candidate withAttemptChance(double chance){return new Candidate(side,position,chance);}}
    private record Participants(PlayerState killer,PlayerState victim,List<PlayerState> assistants){}
}

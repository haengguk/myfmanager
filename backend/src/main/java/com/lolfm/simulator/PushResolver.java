package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class PushResolver {
    public Optional<StructureOutcome> maybeResolvePostFightPush(GameState state, Optional<TeamfightOutcome> outcome, Random random, StructureResolver resolver) {
        if(outcome.isEmpty()||state.isFinished()) return Optional.empty();
        TeamfightOutcome fight=outcome.get(); TeamSide side=fight.winningSide(); int time=state.getCurrentTimeSeconds();
        if (countAlive(state.getTeamState(side), time) < 2) {
            state.recordPushFailure(PushFailureReason.INSUFFICIENT_ATTACKERS);
            return Optional.empty();
        }
        if (!hasAnyTarget(state, side)) {
            state.recordPushFailure(PushFailureReason.NO_TARGET);
            return Optional.empty();
        }
        state.recordPushAttempt();
        state.getCompositionRuntimeState().recordActualAttempt(
                com.lolfm.composition.CompositionActionType.SIEGE, side, side, side.opposite(),
                com.lolfm.composition.FightScale.NONE, null, false, null, null, state.getCurrentTimeSeconds(),
                com.lolfm.composition.CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        state.markStructureActionAttempted(side);
        if (random.nextDouble() >= postFightChance(state, fight)) {
            state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
            return Optional.empty();
        }
        Optional<StructureOutcome> result = destroyChosen(
                state, side, isPriorityPush(state, fight), random, resolver, PushReason.POST_FIGHT
        );
        if (result.isPresent()) state.recordPushSuccess();
        else state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
        return result;
    }
    public List<StructureOutcome> resolvePostFightWindow(
            GameState state,
            Optional<TeamfightOutcome> outcome,
            Optional<com.lolfm.domain.MatchEvent> postFightObjective,
            Random random,
            StructureResolver resolver
    ) {
        if (outcome.isEmpty() || state.isFinished()) return List.of();
        TeamfightOutcome fight = outcome.get();
        TeamSide attacking = fight.winningSide();
        int currentTime = state.getCurrentTimeSeconds();
        if (countAlive(state.getTeamState(attacking), currentTime) < 2 || !hasAnyTarget(state, attacking)) return List.of();
        state.recordPushAttempt();
        state.getCompositionRuntimeState().recordActualAttempt(
                com.lolfm.composition.CompositionActionType.SIEGE, attacking, attacking, attacking.opposite(),
                com.lolfm.composition.FightScale.NONE, null, false, null, null, currentTime,
                com.lolfm.composition.CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        state.markStructureActionAttempted(attacking);
        if (random.nextDouble() >= postFightChance(state, fight)) {
            state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
            return List.of();
        }

        int objectiveTime = postFightObjective.map(this::objectiveTimeCostSeconds).orElse(0);
        if (objectiveTime > 0) {
            // Taking a major objective consumes this simulation tick's conversion window.
            // A later macro tick may begin a separate structure attempt.
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
            return List.of();
        }
        int startTime = Math.max(currentTime, fight.endedAtSeconds()) + objectiveTime;
        int availableUntil = Math.min(
                defendersReachThreeAliveAt(state, attacking.opposite(), startTime),
                MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS
        );
        TeamState attackers = state.getTeamState(attacking);
        boolean largePressure = fight.grade() == FightGrade.BIG_WIN || fight.grade() == FightGrade.ACE
                || attackers.hasActiveBaronBuff(startTime)
                || state.getMapState().hasActiveBasePressure(attacking, startTime);
        int maximumStructures = maximumWindowStructures(fight.grade(), largePressure, startTime, availableUntil, random);
        if (maximumStructures == 0 || startTime >= availableUntil) return List.of();

        Lane lane = chooseWindowLane(state, attacking, random);
        PushWindow window = new PushWindow(
                attacking,
                attacking.opposite(),
                lane,
                startTime,
                availableUntil,
                startTime + structureAttackSeconds(attackers, startTime),
                reason(state, attacking, PushReason.POST_FIGHT),
                maximumStructures
        );
        List<StructureOutcome> results = new ArrayList<>();
        int nextAttack = window.nextStructureAttackSeconds();
        while (!state.isFinished() && results.size() < window.maximumStructures() && nextAttack < window.availableUntilSeconds()) {
            if (countAlive(attackers, nextAttack) < 2) break;
            Optional<StructureOutcome> result = destroyWindowTarget(state, window, resolver);
            if (result.isEmpty()) break;
            StructureOutcome structure = result.get();
            results.add(structure);
            state.recordPushSuccess();
            if (structure.gameEnded()) break;
            nextAttack += structureAttackSeconds(attackers, nextAttack);
        }
        if (results.isEmpty()) state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
        else {
            state.recordPushWindow(results.size(), fight.grade() == FightGrade.ACE && results.getLast().structureKind() == StructureKind.NEXUS);
            state.recordPostFightStructureWindow(results.size());
        }
        return List.copyOf(results);
    }

    private int objectiveTimeCostSeconds(com.lolfm.domain.MatchEvent event) {
        return switch (event.getType()) {
            case DRAGON -> PushRuleConfig.DRAGON_OBJECTIVE_TIME_SECONDS;
            case BARON -> PushRuleConfig.BARON_OBJECTIVE_TIME_SECONDS;
            default -> 0;
        };
    }

    private int maximumWindowStructures(FightGrade grade, boolean largePressure, int start, int availableUntil, Random random) {
        int available = availableUntil - start;
        if (available <= 0) return 0;
        // The simulator resolves one map mutation per ten-second tick. A later tick may
        // continue the push, but one combat result cannot instantly remove an entire base.
        return 1;
    }

    private Lane chooseWindowLane(GameState state, TeamSide attacking, Random random) {
        TeamSide defending = attacking.opposite();
        if (state.getMapState().isNexusVulnerable(defending) || state.getMapState().areNexusTurretsVulnerable(defending)) {
            return Lane.MID;
        }
        return chooseLane(state, defending, true, random);
    }

    private Optional<StructureOutcome> destroyWindowTarget(GameState state, PushWindow window, StructureResolver resolver) {
        TeamSide attacking = window.attackingSide();
        TeamSide defending = window.defendingSide();
        int aliveAttackers = countAlive(state.getTeamState(attacking), state.getCurrentTimeSeconds());
        if (state.getMapState().isNexusVulnerable(defending) && aliveAttackers >= 3) {
            return resolver.destroyNextStructure(state, attacking, Lane.MID, window.reason());
        }
        if (state.getMapState().areNexusTurretsVulnerable(defending) && aliveAttackers >= 3) {
            return resolver.destroyNextStructure(state, attacking, Lane.MID, window.reason());
        }
        return resolver.destroyNextStructure(state, attacking, window.selectedLane(), window.reason());
    }

    private int defendersReachThreeAliveAt(GameState state, TeamSide defending, int fromTime) {
        TeamState team = state.getTeamState(defending);
        int alive = countAlive(team, fromTime);
        if (alive >= 3) return fromTime;
        List<Integer> respawns = new ArrayList<>();
        for (PlayerState player : team.getPlayers()) {
            if (!player.isAlive(fromTime)) respawns.add(player.getRespawnAtSeconds());
        }
        respawns.sort(Integer::compareTo);
        for (int respawnAt : respawns) {
            alive++;
            if (alive >= 3) return respawnAt;
        }
        return MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS;
    }

    private int structureAttackSeconds(TeamState attackers, int time) {
        return attackers.hasActiveBaronBuff(time)
                ? PushRuleConfig.BARON_STRUCTURE_ATTACK_SECONDS
                : PushRuleConfig.STANDARD_STRUCTURE_ATTACK_SECONDS;
    }

    public Optional<StructureOutcome> maybeResolveMacroPush(GameState state,Random random,StructureResolver resolver){
        int time=state.getCurrentTimeSeconds();if(time<480||state.isFinished())return Optional.empty();
        List<TeamSide>sides=new ArrayList<>();for(TeamSide side:TeamSide.values())if(state.getMapState().isPushAttemptDue(side,time))sides.add(side);
        if(sides.size()==2&&random.nextBoolean()){TeamSide first=sides.get(0);sides.set(0,sides.get(1));sides.set(1,first);}
        for (TeamSide side : sides) {
            if (state.wasStructureActionPerformedThisTick(side)) {
                state.recordLaterStructureResolverBlockedByAttempt();
                continue;
            }
            TeamState team = state.getTeamState(side);
            int interval = attemptInterval(state, side, time);
            state.getMapState().markPushAttempted(side, time, interval);
            if (countAlive(team, time) < 2) {
                state.recordPushFailure(PushFailureReason.INSUFFICIENT_ATTACKERS);
                continue;
            }
            if (!hasAnyTarget(state, side)) {
                state.recordPushFailure(PushFailureReason.NO_TARGET);
                continue;
            }
            Lane lane = chooseLane(state, side.opposite(), false, random);
            state.recordPushAttempt();
            state.getCompositionRuntimeState().recordActualAttempt(
                    com.lolfm.composition.CompositionActionType.SIEGE, side, side, side.opposite(),
                    com.lolfm.composition.FightScale.NONE, null, false, null, null, time,
                    com.lolfm.composition.CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
            state.markStructureActionAttempted(side);
            if (random.nextDouble() >= macroPushChance(state, side, lane)) {
                state.recordPushFailure(PushFailureReason.CHANCE_ROLL_FAILED);
                continue;
            }
            Optional<StructureOutcome> result = destroyChosen(
                    state, side, false, lane, random, resolver, PushReason.MACRO_PLAY
            );
            if (result.isPresent()) {
                state.recordPushSuccess();
                return result;
            }
            state.recordPushFailure(PushFailureReason.TARGET_UNAVAILABLE);
        }
        return Optional.empty();
    }
    double postFightChance(GameState s,TeamfightOutcome o){int t=s.getCurrentTimeSeconds();TeamState a=s.getTeamState(o.winningSide()),d=s.getTeamState(o.winningSide().opposite());double c=switch(o.grade()){case SMALL_WIN->PushRuleConfig.SMALL_WIN_PUSH_CHANCE;case NORMAL_WIN->PushRuleConfig.NORMAL_WIN_PUSH_CHANCE;case BIG_WIN->PushRuleConfig.BIG_WIN_PUSH_CHANCE;case ACE->PushRuleConfig.ACE_PUSH_CHANCE;};if(countAlive(a,t)>=4)c+=.04;if(countAlive(d,t)<=1)c+=.06;c+=Math.min(.10,averageRespawn(d,t)/400.0);if(a.hasActiveBaronBuff(t))c+=PushRuleConfig.BARON_PUSH_BONUS;if(s.getMapState().hasActiveBasePressure(o.winningSide(),t))c+=PushRuleConfig.BASE_PRESSURE_PUSH_BONUS;if(s.getObjectiveState().isSoulOwner(o.winningSide()))c+=DragonSoulRuleConfig.SOUL_PUSH_CHANCE_BONUS;if(hasActiveElder(s.getTeamState(o.winningSide()),t))c+=ElderRuleConfig.PUSH_CHANCE_BONUS;return Math.min(.98,c);}
    double macroPushChance(GameState s, TeamSide side) {
        List<Lane> lanes = s.getMapState().getPressureLanes(side.opposite());
        return macroPushChance(s, side, lanes.isEmpty() ? Lane.MID : lanes.getFirst());
    }
    double macroPushChance(GameState s,TeamSide side,Lane lane){int t=s.getCurrentTimeSeconds();TeamState a=s.getTeamState(side),d=s.getTeamState(side.opposite());int lead=Math.max(0,a.getGold()-d.getGold());double c=PushRuleConfig.MACRO_BASE_CHANCE;if(lead>=3000)c+=.04;if(lead>=6000)c+=.06;if(countAlive(a,t)>countAlive(d,t))c+=.05;if(isDeepestLane(s,side.opposite(),lane))c+=.05;if(a.hasActiveBaronBuff(t))c+=.20;if(s.getMapState().hasActiveBasePressure(side,t))c+=.15;if(s.getObjectiveState().isSoulOwner(side))c+=DragonSoulRuleConfig.SOUL_PUSH_CHANCE_BONUS;if(hasActiveElder(a,t))c+=ElderRuleConfig.PUSH_CHANCE_BONUS;return Math.min(.45,c);}
    private Optional<StructureOutcome> destroyChosen(GameState s, TeamSide side, boolean priority, Random r, StructureResolver resolver, PushReason fallbackReason) {
        TeamSide defending = side.opposite();
        if (canTargetNexus(s, side) && countAlive(s.getTeamState(side), s.getCurrentTimeSeconds()) >= 3) {
            return resolver.destroyNextStructure(s, side, Lane.MID, reason(s, side, fallbackReason));
        }
        if (canTargetNexusTurret(s, side) && countAlive(s.getTeamState(side), s.getCurrentTimeSeconds()) >= 3) {
            return resolver.destroyNextStructure(s, side, Lane.MID, reason(s, side, fallbackReason));
        }
        List<Lane> lanes = s.getMapState().getPressureLanes(defending);
        if (lanes.isEmpty()) {
            return Optional.empty();
        }
        return resolver.destroyNextStructure(s, side, chooseLane(s, defending, priority, r), reason(s, side, fallbackReason));
    }

    private Optional<StructureOutcome> destroyChosen(GameState s, TeamSide side, boolean priority, Lane lane, Random r, StructureResolver resolver, PushReason fallbackReason) {
        TeamSide defending = side.opposite();
        if (canTargetNexus(s, side) && countAlive(s.getTeamState(side), s.getCurrentTimeSeconds()) >= 3) {
            return resolver.destroyNextStructure(s, side, Lane.MID, reason(s, side, fallbackReason));
        }
        if (canTargetNexusTurret(s, side) && countAlive(s.getTeamState(side), s.getCurrentTimeSeconds()) >= 3) {
            return resolver.destroyNextStructure(s, side, Lane.MID, reason(s, side, fallbackReason));
        }
        if (s.getMapState().getPressureLanes(defending).isEmpty()) {
            return Optional.empty();
        }
        return resolver.destroyNextStructure(s, side, lane, reason(s, side, fallbackReason));
    }
    private boolean canTargetNexusTurret(GameState s,TeamSide side){int t=s.getCurrentTimeSeconds();TeamSide d=side.opposite();if(!s.getMapState().areNexusTurretsVulnerable(d))return false;return countAlive(s.getTeamState(d),t)<=2||s.hasRecentBigWin(s.getTeamState(side).getTeamName(),120)||s.hasRecentAce(s.getTeamState(side).getTeamName(),120)||s.getTeamState(side).hasActiveBaronBuff(t)||s.getMapState().hasActiveBasePressure(side,t);}
    private boolean canTargetNexus(GameState s,TeamSide side){int t=s.getCurrentTimeSeconds();TeamSide d=side.opposite();if(!s.getMapState().isNexusVulnerable(d))return false;int defenders=countAlive(s.getTeamState(d),t);return defenders<=2||s.hasRecentAce(s.getTeamState(side).getTeamName(),120)||(s.getTeamState(side).hasActiveBaronBuff(t)&&defenders<=3)||(s.getMapState().hasActiveBasePressure(side,t)&&defenders<=2);}
    int attemptInterval(GameState s,TeamSide side,int t){if(s.getMapState().hasActiveBasePressure(side,t))return PushRuleConfig.BASE_PRESSURE_ATTEMPT_INTERVAL_SECONDS;if(s.getTeamState(side).hasActiveBaronBuff(t))return PushRuleConfig.BARON_ATTEMPT_INTERVAL_SECONDS;return PushRuleConfig.MACRO_ATTEMPT_INTERVAL_SECONDS;}
    private Lane chooseLane(GameState s, TeamSide defending, boolean priority, Random r) {
        List<Lane> lanes = s.getMapState().getPressureLanes(defending);
        // The caller normally filters this case. MID is a harmless fallback while only base targets remain.
        if (lanes.isEmpty()) {
            return Lane.MID;
        }
        int max = lanes.stream().mapToInt(lane -> s.getMapState().calculateLaneProgress(defending, lane)).max().orElse(0);
        List<Lane> deepest = lanes.stream()
                .filter(lane -> s.getMapState().calculateLaneProgress(defending, lane) == max)
                .toList();
        if (priority) {
            return deepest.get(r.nextInt(deepest.size()));
        }
        int totalWeight = 0;
        for (Lane lane : lanes) {
            totalWeight += 1 + s.getMapState().calculateLaneProgress(defending, lane) * 2;
        }
        int roll = r.nextInt(totalWeight);
        for (Lane lane : lanes) {
            roll -= 1 + s.getMapState().calculateLaneProgress(defending, lane) * 2;
            if (roll < 0) {
                return lane;
            }
        }
        return lanes.getLast();
    }
    boolean isDeepestLane(GameState s,TeamSide defending,Lane lane){int p=s.getMapState().calculateLaneProgress(defending,lane);for(Lane other:s.getMapState().getPressureLanes(defending))if(s.getMapState().calculateLaneProgress(defending,other)>p)return false;return true;}
    private boolean hasActiveElder(TeamState team, int time) { for (PlayerState player : team.getPlayers()) if (player.hasActiveElderBuff(time)) return true; return false; }
    private boolean isPriorityPush(GameState s,TeamfightOutcome o){return o.grade()==FightGrade.BIG_WIN||o.grade()==FightGrade.ACE||s.getTeamState(o.winningSide()).hasActiveBaronBuff(s.getCurrentTimeSeconds());}
    private boolean hasAnyTarget(GameState s,TeamSide side){TeamSide d=side.opposite();return !s.getMapState().getPressureLanes(d).isEmpty()||s.getMapState().areNexusTurretsVulnerable(d)||s.getMapState().isNexusVulnerable(d);}
    private PushReason reason(GameState s, TeamSide side, PushReason fallbackReason) {
        return s.getTeamState(side).hasActiveBaronBuff(s.getCurrentTimeSeconds())
                ? PushReason.BARON_PRESSURE
                : fallbackReason;
    }
    private int countAlive(TeamState team,int t){int n=0;for(PlayerState p:team.getPlayers())if(p.isAlive(t))n++;return n;}
    private double averageRespawn(TeamState team,int t){int total=0;for(PlayerState p:team.getPlayers())total+=Math.max(0,p.getRespawnAtSeconds()-t);return total/(double)team.getPlayers().size();}
}

package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.List;
import java.util.Random;

/** Abstract return travel and local defense; mutable presence belongs to PlayerState. */
public final class BaseDefenseResolver {
    private final PlayerSkillEvaluator skills = new PlayerSkillEvaluator();
    private final KillRewardResolver rewards = new KillRewardResolver();

    public void updateReturns(GameState state, List<MatchEvent> events) {
        if (!state.isRealismEnabled() || state.isFinished()) return;
        int time = state.getCurrentTimeSeconds();
        for (TeamSide side : TeamSide.values()) {
            BaseSiegeState enemy = state.getBaseSiegeState(side.opposite());
            BaseSiegeState own = state.getBaseSiegeState(side);
            boolean threatened = enemy.isActive() && isBaseApproach(enemy.getCurrentTarget());
            boolean race = own.isActive() && own.getCurrentTarget().kind() == StructureKind.NEXUS
                    && canCommit(state, own);
            for (PlayerState player : state.getTeamState(side).getPlayers()) {
                PlayerActivityState activity = player.getActivityState();
                if (!player.isAlive(time)) {
                    if (isDefending(activity)) activity.clear();
                    continue;
                }
                if (!threatened || race) {
                    if (isDefending(activity)) activity.clear();
                    continue;
                }
                if (isDefending(activity)) continue;
                boolean respawnedHere = player.getLastDeathAtSeconds() >= 0
                        && time - player.getRespawnAtSeconds()
                            <= MatchRealismRuleConfig.RESPAWN_BASE_PRESENCE_SECONDS;
                int travel = respawnedHere ? 0
                        : activity.getActivityType() == PlayerActivityType.SIEGING
                            ? MatchRealismRuleConfig.BASE_RETURN_SECONDS
                            : MatchRealismRuleConfig.NEAR_BASE_RETURN_SECONDS;
                if (travel == 0) activity.defendBase(time);
                else activity.beginBaseReturn(time, travel);
                player.blockFarmUntil(time + travel);
                MatchEvent event = event(time, MatchEventType.BASE_RETURN, side,
                        "본진 위협에 대응해 수비 위치로 복귀합니다.");
                event.setActorPlayerId(player.getStructuredPlayerId());
                event.setBaseDefense(new BaseDefenseData(side, "RETURN", List.of(player.getStructuredPlayerId()),
                        List.of(), time + travel, 0, 0, false));
                events.add(event);
            }
        }
    }

    public List<PlayerState> defenders(GameState state, TeamSide side) {
        int time = state.getCurrentTimeSeconds();
        return state.getTeamState(side).getPlayers().stream()
                .filter(p -> p.isAlive(time)
                        && p.getActivityState().getActivityType() == PlayerActivityType.DEFENDING_BASE)
                .toList();
    }

    private List<PlayerState> attackers(GameState state, BaseSiegeState siege) {
        int time = state.getCurrentTimeSeconds();
        return siege.getParticipants().stream().sorted()
                .map(p -> state.getTeamState(siege.getAttackingSide()).playerAt(p))
                .filter(p -> p.isAlive(time) && p.getActivityState().isSiegingAction(siege.getActionId()))
                .toList();
    }

    public boolean canCommit(GameState state, BaseSiegeState siege) {
        List<PlayerState> attacking = attackers(state, siege);
        if (attacking.isEmpty()) return false;
        int time = state.getCurrentTimeSeconds();
        if (siege.getMode() == StructureAttackMode.WITH_WAVE
                && !state.getMapState().getWaveState(siege.getAttackingSide(), siege.getRouteLane()).hasActiveWaveAt(time)) return false;
        List<PlayerState> defending = defenders(state, siege.getAttackingSide().opposite());
        double localPower = power(state, defending);
        if (localPower == 0 || power(state, attacking) >= localPower * MatchRealismRuleConfig.SIEGE_POWER_ADVANTAGE) return true;
        if (siege.getCurrentTarget().kind() != StructureKind.NEXUS) return false;
        double effectiveDamage = attacking.size() * StructureRuleConfig.EFFECTIVE_DAMAGE_PER_ATTACKER
                * StructureRuleConfig.LATE_GAME_DAMAGE_MULTIPLIER
                * Math.max(StructureRuleConfig.MIN_LOCAL_DEFENSE_DAMAGE_MULTIPLIER,
                    1 - defending.size() * StructureRuleConfig.LOCAL_DEFENDER_DAMAGE_REDUCTION_PER_PLAYER);
        if (state.getTeamState(siege.getAttackingSide()).hasActiveBaronBuff(time)) {
            effectiveDamage *= StructureRuleConfig.BARON_DAMAGE_MULTIPLIER;
        }
        if (siege.getMode() == StructureAttackMode.BACKDOOR) effectiveDamage *= StructureRuleConfig.BACKDOOR_DAMAGE_MULTIPLIER;
        return state.getMapState().getBaseState(siege.getAttackingSide().opposite()).getNexusCurrentHealth()
                <= effectiveDamage * MatchRealismRuleConfig.NEXUS_FINISH_ATTACKS;
    }

    /** First slot in the new central priority flow, before any structure damage. */
    public boolean resolveCombat(GameState state, Random random, List<MatchEvent> events) {
        if (!state.isRealismEnabled() || state.isFinished() || state.wasMajorCombatAttemptedThisTick()) return false;
        int time = state.getCurrentTimeSeconds();
        if (!state.beginBaseDefenseEvaluation(time)) return false;
        for (TeamSide side : TeamSide.values()) {
            BaseSiegeState siege = state.getBaseSiegeState(side.opposite());
            if (!siege.isActive() || !isBaseApproach(siege.getCurrentTarget())) continue;
            List<PlayerState> defending = defenders(state, side);
            List<PlayerState> attacking = attackers(state, siege);
            if (defending.isEmpty() || attacking.isEmpty()) continue;
            if (random.nextDouble() >= MatchRealismRuleConfig.DEFENSE_ATTEMPT_CHANCE) continue;
            defending.forEach(state::markMajorCombatParticipant);
            attacking.forEach(state::markMajorCombatParticipant);
            boolean kill = random.nextDouble() < MatchRealismRuleConfig.DEFENSE_KILL_CHANCE;
            MatchEvent summary = event(time, MatchEventType.BASE_DEFENSE, side,
                    kill ? "본진 수비 교전에서 처치가 발생했습니다." : "본진 수비 교전 후 양측이 거리를 벌립니다.");
            summary.setCombatSource(CombatSource.BASE_DEFENSE);
            summary.setBaseDefense(new BaseDefenseData(side, kill ? "KILL" : "NO_KILL",
                    defending.stream().map(PlayerState::getStructuredPlayerId).toList(),
                    attacking.stream().map(PlayerState::getStructuredPlayerId).toList(),
                    time, power(state, defending), power(state, attacking), true));
            events.add(summary);
            if (kill) {
                boolean defenseWins = random.nextDouble() < power(state, defending) / (power(state, defending) + power(state, attacking));
                List<PlayerState> winners = defenseWins ? defending : attacking;
                List<PlayerState> losers = defenseWins ? attacking : defending;
                PlayerState killer = winners.get(random.nextInt(winners.size()));
                PlayerState victim = losers.get(random.nextInt(losers.size()));
                TeamSide winningSide = defenseWins ? side : side.opposite();
                int start = events.size();
                rewards.award(time, state.getTeamState(winningSide), killer,
                        state.getTeamState(winningSide.opposite()), victim,
                        winners.stream().filter(p -> p != killer).toList(),
                        new TeamfightResolver().calculateRespawnDelaySeconds(time), true, null, events);
                for (int i = start; i < events.size(); i++) {
                    events.get(i).setCombatSource(CombatSource.BASE_DEFENSE);
                    events.get(i).setActionId(summary.getActionId());
                }
            }
            return true;
        }
        return false;
    }

    double power(GameState state, List<PlayerState> players) {
        var progression = new PlayerProgressionPowerEvaluator();
        double base = players.stream().mapToDouble(p -> skills.combatExecution(p)
                + progression.evaluate(p, ProgressionCombatContext.TEAMFIGHT,
                        state.isProgressionEnabled()).clampedTotalPower()).sum();
        var champion = new com.lolfm.champion.CombatChampionPowerEvaluator().evaluatePure(
                state, players, List.of(), ProgressionCombatContext.TEAMFIGHT,
                ProgressionApplicationStage.COMBAT_SCORE);
        return base + (champion.championPowerEnabled()
                ? champion.ownAverageChampionPower() * players.size() : 0);
    }
    private boolean isDefending(PlayerActivityState activity) {
        return activity.getActivityType() == PlayerActivityType.RETURNING_TO_BASE
                || activity.getActivityType() == PlayerActivityType.DEFENDING_BASE;
    }
    static boolean isBaseApproach(StructureTargetId target) {
        return target.kind() != StructureKind.TOWER || target.towerTier() == TowerTier.INHIBITOR;
    }
    private MatchEvent event(int time, MatchEventType type, TeamSide side, String text) {
        MatchEvent event = new MatchEvent(time, type, text, null, null, List.of());
        event.setActionId(type + ":" + time + ":" + side);
        return event;
    }
}

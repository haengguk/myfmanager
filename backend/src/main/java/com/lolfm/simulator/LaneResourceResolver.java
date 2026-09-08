package com.lolfm.simulator;

import com.lolfm.domain.Position;
import java.util.List;

/** XP represents proximity, CS represents last hits; both consume a bounded lane opportunity. */
public final class LaneResourceResolver {
    private final ProgressionRewardResolver rewards = new ProgressionRewardResolver();
    public Lane lane(PlayerState player) {
        return switch (player.getPosition()) {
            case TOP -> Lane.TOP;
            case MID -> Lane.MID;
            case ADC, SUPPORT -> Lane.BOT;
            case JUNGLE -> null;
        };
    }
    public boolean present(PlayerState player, int time) {
        Lane lane = lane(player);
        if (lane == null || time < MatchRealismRuleConfig.contactAt(lane) || !player.isAlive(time)
                || player.getActivityState().getActivityType() != PlayerActivityType.DEFAULT_ROLE) return false;
        // A living lane combatant can receive proximity XP while last-hitting is blocked.
        // Post-respawn travel still removes proximity until the existing farm-return boundary.
        return time >= player.getLaneReturnAtSeconds();
    }
    public void resolveExperience(GameState state, int time) {
        if (!state.getLaneResourceState().beginXp(time)) return;
        for (TeamSide side : TeamSide.values()) {
            for (Lane lane : Lane.values()) {
                List<PlayerState> recipients = state.getTeamState(side).getPlayers().stream()
                        .filter(p -> lane(p) == lane && present(p, time))
                        .sorted(java.util.Comparator.comparing(PlayerState::getPosition)).toList();
                if (recipients.isEmpty()) continue;
                int pool = recipients.size() == 1 ? ProgressionRuleConfig.SOLO_LANE_XP_PER_TICK
                        : 2 * ProgressionRuleConfig.BOT_SHARED_XP_PER_PLAYER_PER_TICK;
                for (int i = 0; i < recipients.size(); i++) {
                    PlayerState player = recipients.get(i);
                    int xp = pool / recipients.size() + (i < pool % recipients.size() ? 1 : 0);
                    if (player.getPosition() == Position.TOP && time >= PositionEconomyRuleConfig.ROLE_QUEST_ACTIVATION_SECONDS) {
                        xp = xp * ProgressionRuleConfig.TOP_QUEST_XP_PER_TICK / ProgressionRuleConfig.SOLO_LANE_XP_PER_TICK;
                    }
                    rewards.awardExperience(player, lane == Lane.BOT
                            ? recipients.size() > 1 ? ExperienceSource.BOT_SHARED_ECONOMY : ExperienceSource.BOT_SOLO_ECONOMY
                            : ExperienceSource.LANE_ECONOMY, xp, time);
                }
            }
        }
    }
    public void rotateSupport(GameState state) {
        if (!state.isRealismEnabled()) return;
        int time = state.getCurrentTimeSeconds();
        if (time < MatchRealismRuleConfig.SUPPORT_ROTATION_START_SECONDS
                || time % MatchRealismRuleConfig.SUPPORT_ROTATION_INTERVAL_SECONDS != 0) return;
        for (TeamSide side : TeamSide.values()) {
            PlayerState support = state.getTeamState(side).playerAt(Position.SUPPORT);
            if (!support.canParticipateInMajorCombatAt(time) || !support.canFarmAt(time)) continue;
            double pressure = state.laneState(Lane.BOT).getPressure() * (side == TeamSide.BLUE ? 1 : -1);
            boolean objective = state.getObjectiveState().isDragonAlive() || state.getObjectiveState().isBaronAlive();
            if (pressure >= 0 || objective || !state.isLaneLaning(Lane.BOT)) {
                support.getActivityState().beginRoam(Lane.BOT, Lane.MID, time,
                        time + MatchRealismRuleConfig.SUPPORT_ROTATION_SECONDS);
                support.blockFarmUntil(time + MatchRealismRuleConfig.SUPPORT_ROTATION_SECONDS);
            }
        }
    }
}

package com.lolfm.simulator;

import com.lolfm.domain.Position;

/** Awards non-unified lane XP and the legacy Jungle XP path when Jungle Economy is OFF. */
public final class ProgressionEconomyResolver {
    private final ProgressionRewardResolver rewards = new ProgressionRewardResolver();

    public void resolve(GameState state, int timeSeconds) {
        if (!state.isProgressionEnabled() || state.isFinished()) return;
        for (TeamSide side : TeamSide.values()) resolveTeam(state, side, timeSeconds);
    }

    private void resolveTeam(GameState state, TeamSide side, int timeSeconds) {
        TeamState team = state.getTeamState(side);
        PlayerState top = team.playerAt(Position.TOP);
        PlayerState mid = team.playerAt(Position.MID);
        PlayerState jungle = team.playerAt(Position.JUNGLE);
        PlayerState adc = team.playerAt(Position.ADC);
        PlayerState support = team.playerAt(Position.SUPPORT);

        if (eligible(state, side, top, timeSeconds, false)) {
            rewards.awardExperience(top, ExperienceSource.LANE_ECONOMY,
                    ProgressionRuleConfig.SOLO_LANE_XP_PER_TICK, timeSeconds);
        }
        if (eligible(state, side, mid, timeSeconds, false)) {
            rewards.awardExperience(mid, ExperienceSource.LANE_ECONOMY,
                    ProgressionRuleConfig.SOLO_LANE_XP_PER_TICK, timeSeconds);
        }
        if (!state.isJungleEconomyEnabled()
                && eligible(state, side, jungle, timeSeconds, true)) {
            rewards.awardExperience(jungle, ExperienceSource.JUNGLE_ECONOMY,
                    ProgressionRuleConfig.JUNGLE_XP_PER_TICK, timeSeconds);
            state.getProgressionExecutionStats().jungle();
        }

        boolean adcEligible = eligible(state, side, adc, timeSeconds, false);
        boolean supportEligible = eligible(state, side, support, timeSeconds, false);
        if (adcEligible && supportEligible) {
            rewards.awardExperience(adc, ExperienceSource.BOT_SHARED_ECONOMY,
                    ProgressionRuleConfig.BOT_SHARED_XP_PER_PLAYER_PER_TICK, timeSeconds);
            rewards.awardExperience(support, ExperienceSource.BOT_SHARED_ECONOMY,
                    ProgressionRuleConfig.BOT_SHARED_XP_PER_PLAYER_PER_TICK, timeSeconds);
            state.getProgressionExecutionStats().shared();
        } else if (adcEligible) {
            rewards.awardExperience(adc, ExperienceSource.BOT_SOLO_ECONOMY,
                    ProgressionRuleConfig.BOT_SOLO_XP_PER_TICK, timeSeconds);
            state.getProgressionExecutionStats().adcSolo();
        } else if (supportEligible) {
            rewards.awardExperience(support, ExperienceSource.BOT_SOLO_ECONOMY,
                    ProgressionRuleConfig.BOT_SOLO_XP_PER_TICK, timeSeconds);
            state.getProgressionExecutionStats().supportSolo();
        } else {
            state.getProgressionExecutionStats().absent();
        }
    }

    boolean eligible(
            GameState state,
            TeamSide side,
            PlayerState player,
            int timeSeconds,
            boolean jungle
    ) {
        if (!player.isAlive(timeSeconds)) {
            state.getProgressionExecutionStats().dead();
            return false;
        }
        if (!player.canFarmAt(timeSeconds)) {
            state.getProgressionExecutionStats().recovery();
            return false;
        }
        if (player.getActivityState().getActivityType() != PlayerActivityType.DEFAULT_ROLE) {
            state.getProgressionExecutionStats().activity();
            return false;
        }
        if (player.getPosition() == Position.MID
                && timeSeconds < player.getRoamActionState().getRoamFarmBlockedUntilSeconds()) {
            state.getProgressionExecutionStats().activity();
            return false;
        }
        if (jungle && timeSeconds
                < state.jungleActionState(side).getJungleFarmBlockedUntilSeconds()) {
            state.getProgressionExecutionStats().activity();
            return false;
        }
        return true;
    }
}

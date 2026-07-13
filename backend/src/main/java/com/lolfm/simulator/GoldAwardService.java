package com.lolfm.simulator;

/** Centralises player and team gold accounting and bounty-progress sources. */
public final class GoldAwardService {

    public void awardGold(TeamState team, PlayerState player, int amount, GoldSource source, boolean deferCombatProgress) {
        if (amount <= 0) return;
        player.addGold(amount);
        team.addGold(amount);

        double progress = switch (source) {
            case KILL, ASSIST -> amount * BountyRuleConfig.KILL_ASSIST_BOUNTY_PROGRESS_RATE;
            case FARM -> amount * BountyRuleConfig.FARM_BOUNTY_PROGRESS_RATE;
            default -> 0.0;
        };
        if (progress <= 0.0) return;
        if (deferCombatProgress) player.addPendingCombatBountyProgress(progress);
        else player.addImmediateBountyProgress(progress);
    }
}

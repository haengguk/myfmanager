package com.lolfm.simulator;

/** Calculates visible personal shutdown bounties from current game state. */
public final class BountyService {

    private BountyService() {
    }

    public static double calculateSuppressionFactor(TeamState ownTeam, TeamState enemyTeam, int currentTimeSeconds) {
        if (currentTimeSeconds < BountyRuleConfig.SUPPRESSION_START_SECONDS) return 1.0;
        double leadRatio = (ownTeam.getGold() - enemyTeam.getGold()) / (double) Math.max(1, enemyTeam.getGold());
        if (leadRatio <= BountyRuleConfig.FULL_SUPPRESSION_MAX_LEAD_RATIO) return 0.0;
        if (leadRatio >= BountyRuleConfig.FULL_BOUNTY_MIN_LEAD_RATIO) return 1.0;
        double range = BountyRuleConfig.FULL_BOUNTY_MIN_LEAD_RATIO
                - BountyRuleConfig.FULL_SUPPRESSION_MAX_LEAD_RATIO;
        return Math.max(0.0, Math.min(1.0,
                (leadRatio - BountyRuleConfig.FULL_SUPPRESSION_MAX_LEAD_RATIO) / range));
    }

    public static int displayedShutdownGold(PlayerState player, TeamState ownTeam, TeamState enemyTeam, int currentTimeSeconds) {
        double effectivePositive = player.getRawPositiveBounty()
                * calculateSuppressionFactor(ownTeam, enemyTeam, currentTimeSeconds);
        int stepped = (int) Math.floor(effectivePositive / BountyRuleConfig.BOUNTY_DISPLAY_STEP)
                * BountyRuleConfig.BOUNTY_DISPLAY_STEP;
        if (stepped < BountyRuleConfig.MIN_VISIBLE_SHUTDOWN_GOLD) return 0;
        return Math.min(stepped, BountyRuleConfig.MAX_SHUTDOWN_PAYOUT);
    }
}

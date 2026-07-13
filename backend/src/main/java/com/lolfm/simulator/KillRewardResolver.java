package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import java.util.List;

/** Single reward path for both skirmishes and teamfights. */
public final class KillRewardResolver {

    static final int BASE_KILL_GOLD = 300;
    static final int BASE_ASSIST_GOLD = 150;
    private final GoldAwardService goldAwards = new GoldAwardService();

    public void award(
            int timeSeconds,
            TeamState attackingTeam,
            PlayerState killer,
            TeamState defendingTeam,
            PlayerState victim,
            List<PlayerState> assistants,
            int respawnDelaySeconds,
            boolean teamfight,
            Integer frozenShutdownGold,
            List<MatchEvent> events
    ) {
        int shutdownGold = frozenShutdownGold == null
                ? BountyService.displayedShutdownGold(victim, defendingTeam, attackingTeam, timeSeconds)
                : frozenShutdownGold;
        double victimRawBounty = victim.getRawPositiveBounty();

        killer.addKill();
        attackingTeam.addKill();
        goldAwards.awardGold(attackingTeam, killer, BASE_KILL_GOLD, GoldSource.KILL, teamfight);
        for (PlayerState assistant : assistants) {
            assistant.addAssist();
            goldAwards.awardGold(attackingTeam, assistant, BASE_ASSIST_GOLD, GoldSource.ASSIST, teamfight);
        }
        if (shutdownGold >= BountyRuleConfig.MIN_VISIBLE_SHUTDOWN_GOLD) {
            goldAwards.awardGold(attackingTeam, killer, shutdownGold, GoldSource.SHUTDOWN, false);
            killer.addShutdownGoldEarned(shutdownGold);
            victim.addShutdownGoldGiven(shutdownGold);
            events.add(new MatchEvent(
                    timeSeconds, MatchEventType.SHUTDOWN,
                    killer.getPlayerName() + " shut down " + victim.getPlayerName() + " for +" + shutdownGold + "G.",
                    killer.getPlayerName(), victim.getPlayerName(), List.of(), shutdownGold, victimRawBounty
            ));
        }

        victim.markDead(timeSeconds, respawnDelaySeconds);
        victim.consumeShutdownBounty();
        int deathGoldGiven = BASE_KILL_GOLD + assistants.size() * BASE_ASSIST_GOLD;
        victim.reduceBountyProgress(deathGoldGiven * BountyRuleConfig.DEATH_PROGRESS_REDUCTION_RATE);
    }
}

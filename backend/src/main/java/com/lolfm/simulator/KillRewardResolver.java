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
        if (!victim.isAlive(timeSeconds)) return;
        int shutdownGold = frozenShutdownGold == null
                ? BountyService.displayedShutdownGold(victim, defendingTeam, attackingTeam, timeSeconds)
                : frozenShutdownGold;
        double victimRawBounty = victim.getRawPositiveBounty();

        killer.addKill();
        attackingTeam.addKill();
        goldAwards.awardGold(attackingTeam, killer, BASE_KILL_GOLD, GoldSource.KILL, teamfight, timeSeconds);
        for (PlayerState assistant : assistants) {
            assistant.addAssist();
            goldAwards.awardGold(attackingTeam, assistant, BASE_ASSIST_GOLD, GoldSource.ASSIST, teamfight, timeSeconds);
        }
        new ProgressionRewardResolver().awardKillExperience(killer, assistants, timeSeconds);
        if (shutdownGold >= BountyRuleConfig.MIN_VISIBLE_SHUTDOWN_GOLD) {
            goldAwards.awardGold(attackingTeam, killer, shutdownGold, GoldSource.SHUTDOWN, false, timeSeconds);
            killer.addShutdownGoldEarned(shutdownGold);
            victim.addShutdownGoldGiven(shutdownGold);
            MatchEvent shutdownEvent = new MatchEvent(
                    timeSeconds, MatchEventType.SHUTDOWN,
                    killer.getPlayerName() + " shut down " + victim.getPlayerName() + " for +" + shutdownGold + "G.",
                    killer.getPlayerName(), victim.getPlayerName(), List.of(), shutdownGold, victimRawBounty
            );
            shutdownEvent.setParticipantPlayerIds(killer.getStructuredPlayerId(),
                    victim.getStructuredPlayerId(), List.of());
            events.add(shutdownEvent);
        }

        victim.markDead(timeSeconds, respawnDelaySeconds);
        victim.consumeShutdownBounty();
        int deathGoldGiven = BASE_KILL_GOLD + assistants.size() * BASE_ASSIST_GOLD;
        victim.reduceBountyProgress(deathGoldGiven * BountyRuleConfig.DEATH_PROGRESS_REDUCTION_RATE);
    }
}

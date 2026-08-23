package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.AssistEventData;
import com.lolfm.domain.KillEventData;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Single reward path for both skirmishes and teamfights. */
public final class KillRewardResolver {

    static final int BASE_KILL_GOLD = KillRewardRuleConfig.BASE_KILL_GOLD;
    static final int BASE_ASSIST_GOLD = KillRewardRuleConfig.BASE_ASSIST_GOLD;
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
        boolean firstBlood = attackingTeam.getKills() + defendingTeam.getKills() == 0;
        int firstBloodBonus = firstBlood ? KillRewardRuleConfig.FIRST_BLOOD_BONUS_GOLD : 0;
        int killGold = BASE_KILL_GOLD + firstBloodBonus;
        Set<PlayerState> seenAssistants = Collections.newSetFromMap(new IdentityHashMap<>());
        List<PlayerState> creditedAssistants = assistants == null ? List.of() : assistants.stream()
                .filter(assistant -> assistant != null && assistant != killer)
                .filter(assistant -> assistant.isAlive(timeSeconds))
                .filter(seenAssistants::add)
                .toList();

        killer.addKill();
        attackingTeam.addKill();
        goldAwards.awardGold(attackingTeam, killer, BASE_KILL_GOLD, GoldSource.KILL, teamfight, timeSeconds);
        if (firstBloodBonus > 0) {
            goldAwards.awardGold(attackingTeam, killer, firstBloodBonus, GoldSource.KILL, teamfight, timeSeconds);
        }
        for (PlayerState assistant : creditedAssistants) {
            assistant.addAssist();
            goldAwards.awardGold(attackingTeam, assistant, BASE_ASSIST_GOLD, GoldSource.ASSIST, teamfight, timeSeconds);
        }
        new ProgressionRewardResolver().awardKillExperience(killer, creditedAssistants, timeSeconds);

        MatchEvent killEvent = new MatchEvent(
                timeSeconds, MatchEventType.KILL,
                killMessage(killer, victim, creditedAssistants, firstBlood, killGold),
                killer.getPlayerName(), victim.getPlayerName(),
                creditedAssistants.stream().map(PlayerState::getPlayerName).toList(),
                killGold, victimRawBounty
        );
        killEvent.setActorPlayerId(killer.getStructuredPlayerId());
        killEvent.setParticipantPlayerIds(killer.getStructuredPlayerId(),
                victim.getStructuredPlayerId(),
                creditedAssistants.stream().map(PlayerState::getStructuredPlayerId).toList());
        killEvent.setKillEvent(new KillEventData(
                firstBlood, BASE_KILL_GOLD, firstBloodBonus, shutdownGold,
                killGold + shutdownGold, BASE_ASSIST_GOLD,
                creditedAssistants.size() * BASE_ASSIST_GOLD));
        events.add(killEvent);

        for (PlayerState assistant : creditedAssistants) {
            MatchEvent assistEvent = new MatchEvent(
                    timeSeconds, MatchEventType.ASSIST,
                    assistant.getPlayerName() + "가 " + killer.getPlayerName() + "의 "
                            + victim.getPlayerName() + " 처치를 도왔습니다. +" + BASE_ASSIST_GOLD + "G",
                    killer.getPlayerName(), victim.getPlayerName(),
                    List.of(assistant.getPlayerName()), BASE_ASSIST_GOLD
            );
            assistEvent.setActorPlayerId(assistant.getStructuredPlayerId());
            assistEvent.setParticipantPlayerIds(killer.getStructuredPlayerId(),
                    victim.getStructuredPlayerId(), List.of(assistant.getStructuredPlayerId()));
            assistEvent.setAssistEvent(new AssistEventData(
                    assistant.getStructuredPlayerId(), killer.getStructuredPlayerId(),
                    victim.getStructuredPlayerId(), BASE_ASSIST_GOLD));
            events.add(assistEvent);
        }

        if (shutdownGold >= BountyRuleConfig.MIN_VISIBLE_SHUTDOWN_GOLD) {
            goldAwards.awardGold(attackingTeam, killer, shutdownGold, GoldSource.SHUTDOWN, false, timeSeconds);
            killer.addShutdownGoldEarned(shutdownGold);
            victim.addShutdownGoldGiven(shutdownGold);
            MatchEvent shutdownEvent = new MatchEvent(
                    timeSeconds, MatchEventType.SHUTDOWN,
                    killer.getPlayerName() + "가 " + victim.getPlayerName() + "의 연속 처치를 끝냈습니다. +"
                            + shutdownGold + "G",
                    killer.getPlayerName(), victim.getPlayerName(), List.of(), shutdownGold, victimRawBounty
            );
            shutdownEvent.setActorPlayerId(killer.getStructuredPlayerId());
            shutdownEvent.setParticipantPlayerIds(killer.getStructuredPlayerId(),
                    victim.getStructuredPlayerId(), List.of());
            events.add(shutdownEvent);
        }

        victim.markDead(timeSeconds, respawnDelaySeconds);
        victim.consumeShutdownBounty();
        int deathGoldGiven = killGold + creditedAssistants.size() * BASE_ASSIST_GOLD;
        victim.reduceBountyProgress(deathGoldGiven * BountyRuleConfig.DEATH_PROGRESS_REDUCTION_RATE);
    }

    private String killMessage(PlayerState killer, PlayerState victim, List<PlayerState> assistants,
                               boolean firstBlood, int killGold) {
        String prefix = firstBlood ? "퍼스트 블러드! " : "";
        String assistText = assistants.isEmpty() ? "" : " 합류: "
                + String.join(", ", assistants.stream().map(PlayerState::getPlayerName).toList());
        return prefix + killer.getPlayerName() + "가 " + victim.getPlayerName()
                + "를 처치했습니다. +" + killGold + "G" + assistText;
    }
}

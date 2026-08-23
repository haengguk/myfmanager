package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KillRewardEventContractTest {

    @Test
    void firstBloodKillAndEachAssistHaveAuditableRewardsAndDuplicateIsIdempotent() {
        GameState state = LateGameTestSupport.state();
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        PlayerState killer = blue.playerAt(Position.MID);
        PlayerState assistant = blue.playerAt(Position.JUNGLE);
        PlayerState victim = red.playerAt(Position.MID);
        List<MatchEvent> events = new ArrayList<>();
        KillRewardResolver resolver = new KillRewardResolver();

        resolver.award(100, blue, killer, red, victim, List.of(assistant, assistant, killer),
                20, false, 0, events);

        MatchEvent kill = events.stream()
                .filter(event -> event.getType() == MatchEventType.KILL)
                .findFirst().orElseThrow();
        MatchEvent assist = events.stream()
                .filter(event -> event.getType() == MatchEventType.ASSIST)
                .findFirst().orElseThrow();
        assertThat(kill.getGoldAmount()).isEqualTo(400);
        assertThat(kill.getActorPlayerId()).isEqualTo(killer.getStructuredPlayerId());
        assertThat(kill.getKillEvent().firstBlood()).isTrue();
        assertThat(kill.getKillEvent().baseKillGold()).isEqualTo(300);
        assertThat(kill.getKillEvent().firstBloodBonusGold()).isEqualTo(100);
        assertThat(assist.getGoldAmount()).isEqualTo(150);
        assertThat(assist.getActorPlayerId()).isEqualTo(assistant.getStructuredPlayerId());
        assertThat(assist.getAssistEvent().killerPlayerId())
                .isEqualTo(killer.getStructuredPlayerId());
        assertThat(killer.getKills()).isOne();
        assertThat(assistant.getAssists()).isOne();
        assertThat(victim.getDeaths()).isOne();

        int killerGold = killer.getGold();
        int assistantGold = assistant.getGold();
        resolver.award(100, blue, killer, red, victim, List.of(assistant),
                20, false, 0, events);

        assertThat(events).hasSize(2);
        assertThat(killer.getGold()).isEqualTo(killerGold);
        assertThat(assistant.getGold()).isEqualTo(assistantGold);
        assertThat(killer.getKills()).isOne();
        assertThat(assistant.getAssists()).isOne();
        assertThat(victim.getDeaths()).isOne();
    }

    @Test
    void laterKillUsesBaseGoldAndIsNotMarkedFirstBlood() {
        GameState state = LateGameTestSupport.state();
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        KillRewardResolver resolver = new KillRewardResolver();
        List<MatchEvent> events = new ArrayList<>();

        resolver.award(100, blue, blue.playerAt(Position.MID), red,
                red.playerAt(Position.MID), List.of(), 20, false, 0, events);
        resolver.award(101, blue, blue.playerAt(Position.TOP), red,
                red.playerAt(Position.TOP), List.of(), 20, false, 0, events);

        List<MatchEvent> kills = events.stream()
                .filter(event -> event.getType() == MatchEventType.KILL).toList();
        assertThat(kills).hasSize(2);
        assertThat(kills.get(1).getGoldAmount()).isEqualTo(300);
        assertThat(kills.get(1).getKillEvent().firstBlood()).isFalse();
        assertThat(kills.get(1).getKillEvent().firstBloodBonusGold()).isZero();
    }
}

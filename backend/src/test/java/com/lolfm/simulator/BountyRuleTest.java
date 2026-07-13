package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BountyRuleTest {

    @Test
    void initialStateHasNoVisibleBounty() {
        PlayerState player = player("victim");
        assertEquals(0.0, player.getBountyProgress());
        assertEquals(0, player.getRawPositiveBounty());
        assertEquals(0, BountyService.displayedShutdownGold(player, team("own", player), team("enemy", player("enemy")), 0));
    }

    @Test
    void displayedBountyUsesBufferStepAndMaximum() {
        PlayerState player = player("victim");
        TeamState own = team("own", player);
        TeamState enemy = team("enemy", player("enemy"));
        player.addImmediateBountyProgress(249); // raw 149
        assertEquals(0, BountyService.displayedShutdownGold(player, own, enemy, 0));
        player.addImmediateBountyProgress(1);
        assertEquals(150, BountyService.displayedShutdownGold(player, own, enemy, 0));
        player.addImmediateBountyProgress(49);
        assertEquals(150, BountyService.displayedShutdownGold(player, own, enemy, 0));
        player.addImmediateBountyProgress(1);
        assertEquals(200, BountyService.displayedShutdownGold(player, own, enemy, 0));
        player.addImmediateBountyProgress(1_000);
        assertEquals(700, BountyService.displayedShutdownGold(player, own, enemy, 0));
    }

    @Test
    void suppressionUsesCurrentGoldLeadAfterSixMinutes() {
        PlayerState ownPlayer = player("own");
        ownPlayer.addImmediateBountyProgress(400);
        TeamState own = team("own", ownPlayer);
        TeamState enemy = team("enemy", player("enemy"));
        assertEquals(1.0, BountyService.calculateSuppressionFactor(own, enemy, 359));
        assertEquals(0.0, BountyService.calculateSuppressionFactor(own, enemy, 360));
        own.addGold(300); // 300 / 500 = 60%, a full lead
        assertEquals(1.0, BountyService.calculateSuppressionFactor(own, enemy, 360));
    }

    @Test
    void onlyFarmKillAndAssistCreateProgressAndCombatCanBeDeferred() {
        PlayerState player = player("player");
        TeamState state = team("team", player);
        GoldAwardService awards = new GoldAwardService();
        awards.awardGold(state, player, 100, GoldSource.FARM, false);
        awards.awardGold(state, player, 300, GoldSource.KILL, true);
        awards.awardGold(state, player, 150, GoldSource.ASSIST, true);
        awards.awardGold(state, player, 700, GoldSource.SHUTDOWN, false);
        assertEquals(5.0, player.getBountyProgress());
        assertEquals(131.625, player.getPendingCombatBountyProgress(), 0.000001);
        player.commitPendingCombatBountyProgress();
        assertEquals(136.625, player.getBountyProgress(), 0.000001);
    }

    @Test
    void shutdownPaysOnlyKillerAndCarriesRawAmountAboveCap() {
        PlayerState killer = player("killer");
        PlayerState assistant = player("assistant");
        PlayerState victim = player("victim");
        victim.addImmediateBountyProgress(1_050); // raw 950
        TeamState attackers = team("attackers", killer, assistant);
        TeamState defenders = team("defenders", victim);
        List<MatchEvent> events = new ArrayList<>();

        new KillRewardResolver().award(100, attackers, killer, defenders, victim, List.of(assistant), 10, false, 700, events);

        assertEquals(1_500, killer.getGold()); // 500 + 300 + 700
        assertEquals(650, assistant.getGold());
        assertEquals(700, killer.getTotalShutdownGoldEarned());
        assertEquals(700, victim.getTotalShutdownGoldGiven());
        assertEquals(237.5, victim.getBountyProgress(), 0.000001);
        assertTrue(events.stream().anyMatch(event -> event.getType() == MatchEventType.SHUTDOWN));
        assertFalse(assistant.getTotalShutdownGoldEarned() > 0);
    }

    @Test
    void snapshotHidesBountyForDeadPlayersAndRestoresCarryOverAfterRespawn() {
        PlayerState bluePlayer = player("blue");
        PlayerState redPlayer = player("red");
        bluePlayer.addImmediateBountyProgress(1_050);
        TeamState blue = team("blue", bluePlayer);
        TeamState red = team("red", redPlayer);
        blue.addGold(1_000);
        GameState state = new GameState(blue, red);
        state.advanceTimeSeconds(400);
        SnapshotFactory snapshots = new SnapshotFactory();
        assertEquals(700, snapshots.create(state).getPlayerSnapshots().getFirst().getShutdownBountyGold());

        bluePlayer.markDead(400, 35);
        assertEquals(0, snapshots.create(state).getPlayerSnapshots().getFirst().getShutdownBountyGold());
        assertFalse(snapshots.create(state).getPlayerSnapshots().getFirst().isHasShutdownBounty());
        assertEquals(1_050.0, bluePlayer.getBountyProgress(), 0.000001);

        state.advanceTimeSeconds(35);
        assertEquals(700, snapshots.create(state).getPlayerSnapshots().getFirst().getShutdownBountyGold());
    }

    @Test
    void suppressionCoversTwoToEightPercentInterpolation() {
        TeamState own = team("own", player("own")); TeamState enemy = team("enemy", player("enemy"));
        own.addGold(10); assertEquals(0.0, BountyService.calculateSuppressionFactor(own, enemy, 360));
        own.addGold(20); assertEquals(2.0 / 3.0, BountyService.calculateSuppressionFactor(own, enemy, 360), 0.000001);
        own.addGold(10); assertEquals(1.0, BountyService.calculateSuppressionFactor(own, enemy, 360));
    }

    @Test
    void payoutBelowCapClearsProgressAndCannotPayTwice() {
        PlayerState killer = player("killer"), victim = player("victim"); victim.addImmediateBountyProgress(400);
        TeamState attackers = team("attackers", killer), defenders = team("defenders", victim); List<MatchEvent> events = new ArrayList<>();
        KillRewardResolver resolver = new KillRewardResolver(); resolver.award(10, attackers, killer, defenders, victim, List.of(), 10, false, 300, events);
        assertEquals(0.0, victim.getBountyProgress()); assertEquals(300, killer.getTotalShutdownGoldEarned());
        resolver.award(10, attackers, killer, defenders, victim, List.of(), 10, false, 0, events);
        assertEquals(300, killer.getTotalShutdownGoldEarned());
    }

    private PlayerState player(String name) {
        return new PlayerState(name, Position.MID, 500);
    }

    private TeamState team(String name, PlayerState... players) {
        return new TeamState(name, List.of(players));
    }
}

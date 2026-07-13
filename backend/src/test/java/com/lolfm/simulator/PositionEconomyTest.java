package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PositionEconomyTest {

    @Test
    void playerStateStoresExplicitPosition() {
        assertEquals(Position.JUNGLE, player("J", Position.JUNGLE, 14).getPosition());
    }

    @Test
    void completeLineupValidatesAndSupportsPositionLookup() {
        TeamState team = completeTeam(14);
        team.validateCompleteLineup();
        assertEquals("TOP", team.playerAt(Position.TOP).getPlayerName());
        assertEquals("ADC", team.findPlayerAt(Position.ADC).orElseThrow().getPlayerName());
    }

    @Test
    void missingOrDuplicatePositionFailsCompleteLineupValidation() {
        assertThrows(IllegalStateException.class, () -> new TeamState("missing", List.of(
                player("top", Position.TOP, 14), player("mid", Position.MID, 14))).validateCompleteLineup());
        assertThrows(IllegalStateException.class, () -> new TeamState("duplicate", List.of(
                player("top1", Position.TOP, 14), player("top2", Position.TOP, 14),
                player("jungle", Position.JUNGLE, 14), player("mid", Position.MID, 14),
                player("adc", Position.ADC, 14))).validateCompleteLineup());
    }

    @Test
    void laneAndJungleFarmAwardCsGoldAndBountyProgressButSupportDoesNot() {
        TeamState team = completeTeam(14);
        new PositionEconomyResolver().resolve(team, 600, 600, new Random(1));
        assertEquals(70, team.playerAt(Position.TOP).getCs());
        assertEquals(71, team.playerAt(Position.MID).getCs());
        assertEquals(72, team.playerAt(Position.ADC).getCs());
        assertEquals(58, team.playerAt(Position.JUNGLE).getCs());
        PlayerState top = team.playerAt(Position.TOP);
        assertEquals(500 + top.getCs() * PositionEconomyRuleConfig.CS_GOLD, top.getGold());
        assertEquals(top.getCs(), top.getBountyProgress(), 0.000001);
        assertEquals(0, team.playerAt(Position.SUPPORT).getCs());
        assertEquals(500, team.playerAt(Position.SUPPORT).getGold());
        assertEquals(0.0, team.playerAt(Position.SUPPORT).getBountyProgress());
    }

    @Test
    void higherFarmingHasHigherMultiplierAndExpectedCs() {
        PositionEconomyResolver resolver = new PositionEconomyResolver();
        PlayerState low = player("low", Position.TOP, 10);
        PlayerState high = player("high", Position.TOP, 18);
        assertTrue(resolver.farmingMultiplier(high) > resolver.farmingMultiplier(low));
        TeamState lowTeam = new TeamState("low", List.of(low));
        TeamState highTeam = new TeamState("high", List.of(high));
        resolver.resolve(lowTeam, 600, 600, new Random(2));
        resolver.resolve(highTeam, 600, 600, new Random(2));
        assertTrue(high.getCs() > low.getCs());
    }

    @Test
    void deadPlayersDoNotReceiveFarmAndRejoinAfterRespawn() {
        PlayerState top = player("top", Position.TOP, 14);
        TeamState team = new TeamState("team", List.of(top));
        top.markDead(10, 50);
        PositionEconomyResolver resolver = new PositionEconomyResolver();
        resolver.resolve(team, 20, 10, new Random(3));
        assertEquals(0, top.getCs());
        resolver.resolve(team, 60, 10, new Random(3));
        assertTrue(top.getCs() > 0);
    }

    @Test
    void duplicateResolutionAtSameTimeDoesNotAwardTwice() {
        PlayerState top = player("top", Position.TOP, 14);
        TeamState team = new TeamState("team", List.of(top));
        PositionEconomyResolver resolver = new PositionEconomyResolver();
        resolver.resolve(team, 60, 60, new Random(4));
        int cs = top.getCs();
        int gold = top.getGold();
        resolver.resolve(team, 60, 60, new Random(99));
        assertEquals(cs, top.getCs());
        assertEquals(gold, top.getGold());
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(team, 50, 10, new Random(4)));
    }

    private TeamState completeTeam(int farming) {
        return new TeamState("team", List.of(
                player("TOP", Position.TOP, farming), player("JUNGLE", Position.JUNGLE, farming),
                player("MID", Position.MID, farming), player("ADC", Position.ADC, farming),
                player("SUPPORT", Position.SUPPORT, farming)));
    }

    private PlayerState player(String name, Position position, int farming) {
        return new PlayerState(name, position, new PlayerAttributes(14, 14, farming, 14), 500);
    }
}

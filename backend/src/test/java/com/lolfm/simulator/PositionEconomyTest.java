package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(81, team.playerAt(Position.TOP).getCs());
        assertEquals(86, team.playerAt(Position.MID).getCs());
        assertEquals(92, team.playerAt(Position.ADC).getCs());
        assertEquals(66, team.playerAt(Position.JUNGLE).getCs());
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
        resolver.resolve(team, 89, 10, new Random(3));
        assertEquals(0, top.getCs());
        resolver.resolve(team, 90, 10, new Random(3));
        assertTrue(top.getCs() > 0);
    }

    @Test
    void passiveAndSupportQuestGoldStartWithTheEconomyClock() {
        for (int farming : List.of(1, 10, 14, 18, 20)) {
            for (Position position : Position.values()) {
                PlayerState player = player(position.name(), position, farming);
                TeamState team = new TeamState("team-" + position + farming, List.of(player));
                simulator().applyTickEconomy(new Random(1), team, 10, 70);
                assertEquals(PositionEconomyRuleConfig.passiveGoldPerTick(position),
                        player.getGold() - 500 - player.getCs() * PositionEconomyRuleConfig.CS_GOLD);
                assertEquals(player.getCs(), player.getBountyProgress(), 0.000001);
            }
        }
    }

    @Test
    void deadPlayersReceivePassiveButNoFarmCsOrFarmGold() {
        PlayerState top = player("top", Position.TOP, 14);
        TeamState team = new TeamState("team", List.of(top));
        top.markDead(0, 60);
        simulator().applyTickEconomy(new Random(1), team, 10, 70);
        assertEquals(0, top.getCs());
        assertEquals(520, top.getGold());
        assertEquals(0.0, top.getBountyProgress());
    }

    @Test
    void wholeEconomyTickDoesNotDuplicateAndAdvancesAtNextTime() {
        PlayerState top = player("top", Position.TOP, 14);
        TeamState team = new TeamState("team", List.of(top));
        MatchSimulator simulator = simulator();
        simulator.applyTickEconomy(new Random(4), team, 10, 70);
        int cs = top.getCs(), gold = top.getGold();
        simulator.applyTickEconomy(new Random(99), team, 10, 70);
        assertEquals(cs, top.getCs());
        assertEquals(gold, top.getGold());
        assertEquals(1, team.getDuplicateEconomyResolutionCount());
        simulator.applyTickEconomy(new Random(5), team, 10, 80);
        assertTrue(top.getGold() >= gold + PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK);
        assertEquals(80, team.getLastEconomyResolvedAtSeconds());
    }

    @Test
    void newSameNamedTeamsAndStatelessResolverDoNotShareEconomyClock() {
        MatchSimulator simulator = simulator();
        TeamState first = new TeamState("same", List.of(player("first", Position.TOP, 14)));
        TeamState second = new TeamState("same", List.of(player("second", Position.TOP, 14)));
        assertEquals(-1, first.getLastEconomyResolvedAtSeconds());
        assertEquals(-1, second.getLastEconomyResolvedAtSeconds());
        simulator.applyTickEconomy(new Random(1), first, 10, 70);
        simulator.applyTickEconomy(new Random(1), second, 10, 70);
        assertEquals(520 + first.getPlayers().getFirst().getCs() * PositionEconomyRuleConfig.CS_GOLD, first.getPlayers().getFirst().getGold());
        assertEquals(520 + second.getPlayers().getFirst().getCs() * PositionEconomyRuleConfig.CS_GOLD, second.getPlayers().getFirst().getGold());
        assertThrows(IllegalArgumentException.class, () -> simulator.applyTickEconomy(new Random(1), first, 10, 5));
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
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

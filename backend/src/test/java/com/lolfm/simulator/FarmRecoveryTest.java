package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FarmRecoveryTest {

    @Test
    void newPlayerCanFarmImmediatelyWithZeroResumeTime() {
        PlayerState player = player("TOP", Position.TOP, true);
        assertEquals(0, player.getFarmResumeAtSeconds());
        assertTrue(player.isAlive(0));
        assertTrue(player.canFarmAt(0));
    }

    @Test
    void preFourteenMinuteDeathUsesPositionReturnDelay() {
        assertDelay(Position.TOP, 20);
        assertDelay(Position.JUNGLE, 10);
        assertDelay(Position.MID, 10);
        assertDelay(Position.ADC, 20);
        assertDelay(Position.SUPPORT, 0);
    }

    @Test
    void returnDelayAppliesAt840ButNot841() {
        PlayerState atBoundary = player("TOP", Position.TOP, true);
        atBoundary.markDead(840, 20);
        assertEquals(880, atBoundary.getFarmResumeAtSeconds());
        PlayerState afterBoundary = player("TOP", Position.TOP, true);
        afterBoundary.markDead(841, 20);
        assertEquals(861, afterBoundary.getFarmResumeAtSeconds());
    }

    @Test
    void deadAndReturningPlayersReceivePassiveButNoFarm() {
        PlayerState top = player("TOP", Position.TOP, true);
        TeamState team = new TeamState("BLUE", List.of(top));
        top.markDead(180, 10);
        MatchSimulator simulator = simulator(true);
        CountingRandom random = new CountingRandom(0);
        simulator.applyTickEconomy(random, team, 10, 180);
        assertFalse(top.isAlive(180));
        assertFalse(top.canFarmAt(180));
        assertEquals(0, top.getCs());
        assertEquals(514, top.getGold());
        assertEquals(0, random.calls);
        simulator.applyTickEconomy(random, team, 10, 190);
        assertTrue(top.isAlive(190));
        assertFalse(top.canFarmAt(190));
        assertEquals(0, top.getCs());
        assertEquals(528, top.getGold());
        assertEquals(0.0, top.getBountyProgress());
        assertEquals(0, random.calls);
    }

    @Test
    void exactFarmResumeBoundaryRestartsWithoutCatchUp() {
        PlayerState top = player("TOP", Position.TOP, true);
        TeamState team = new TeamState("BLUE", List.of(top));
        top.markDead(180, 10);
        assertFalse(top.canFarmAt(209));
        new PositionEconomyResolver().resolve(team, 200, 10, new Random(1));
        assertEquals(0, top.getCs());
        assertTrue(top.canFarmAt(210));
        new PositionEconomyResolver().resolve(team, 210, 10, new FixedRandom(0));
        assertEquals(2, top.getCs());
    }

    @Test
    void returningSkipsFarmRandomGoldCsAndBountyThenResumes() {
        PlayerState adc = player("ADC", Position.ADC, true);
        TeamState team = new TeamState("BLUE", List.of(adc));
        adc.markDead(180, 10);
        CountingRandom random = new CountingRandom(0);
        new PositionEconomyResolver().resolve(team, 200, 10, random);
        assertEquals(0, random.calls);
        assertEquals(0, adc.getCs());
        assertEquals(500, adc.getGold());
        assertEquals(0.0, adc.getBountyProgress());
        new PositionEconomyResolver().resolve(team, 210, 10, random);
        assertEquals(1, random.calls);
        assertTrue(adc.getCs() > 0);
        assertEquals(500 + adc.getCs() * PositionEconomyRuleConfig.CS_GOLD, adc.getGold());
    }

    @Test
    void passiveIsIndependentDuringDeathAndReturn() {
        PlayerState mid = player("MID", Position.MID, true);
        TeamState team = new TeamState("BLUE", List.of(mid));
        mid.markDead(180, 10);
        MatchSimulator simulator = simulator(true);
        simulator.applyTickEconomy(new FixedRandom(1), team, 10, 180);
        simulator.applyTickEconomy(new FixedRandom(1), team, 10, 190);
        assertEquals(528, mid.getGold());
        assertEquals(0, mid.getCs());
        assertEquals(0.0, mid.getBountyProgress());
    }

    @Test
    void laneSkirmishAndTeamfightUseCommonFarmResumeRule() {
        GameState laneState = completeState(true);
        laneState.advanceTimeSeconds(180);
        List<MatchEvent> laneEvents = new ArrayList<>();
        new LaneCombatResolver().resolve(laneState, new SequenceRandom(0, 1, 1, .5, 0, 0, 0), laneEvents);
        PlayerState laneVictim = laneState.getRedTeamState().playerAt(Position.TOP);
        assertEquals(laneVictim.getRespawnAtSeconds() + 20, laneVictim.getFarmResumeAtSeconds());

        assertCombatSourceUsesCommonRule(false);
        assertCombatSourceUsesCommonRule(true);
    }

    @Test
    void repeatedDeathWhileReturningMovesResumeForwardAndDuplicateDoesNotExtend() {
        PlayerState top = player("TOP", Position.TOP, true);
        top.markDead(180, 10);
        assertEquals(210, top.getFarmResumeAtSeconds());
        top.markDead(180, 10);
        assertEquals(1, top.getDeaths());
        assertEquals(210, top.getFarmResumeAtSeconds());
        top.markDead(190, 10);
        assertEquals(2, top.getDeaths());
        assertEquals(220, top.getFarmResumeAtSeconds());
    }

    @Test
    void supportNeverReceivesFarmButKeepsPassive() {
        PlayerState support = player("SUPPORT", Position.SUPPORT, true);
        TeamState team = new TeamState("BLUE", List.of(support));
        support.markDead(180, 10);
        MatchSimulator simulator = simulator(true);
        simulator.applyTickEconomy(new FixedRandom(0), team, 10, 180);
        simulator.applyTickEconomy(new FixedRandom(0), team, 10, 190);
        assertEquals(0, support.getCs());
        assertEquals(528, support.getGold());
        assertEquals(0.0, support.getBountyProgress());
    }

    @Test
    void snapshotsSeparateDeadReturningAndFarmReadyAndStayImmutable() {
        GameState state = completeState(true);
        PlayerState top = state.getBlueTeamState().playerAt(Position.TOP);
        top.markDead(180, 10);
        state.advanceTimeSeconds(180);
        SnapshotFactory factory = new SnapshotFactory();
        MatchSnapshot dead = factory.create(state);
        PlayerSnapshot deadTop = snapshot(dead, "BLUE-TOP");
        assertFalse(deadTop.isAlive());
        assertFalse(deadTop.isCanFarm());
        assertEquals(190, deadTop.getRespawnAtSeconds());
        assertEquals(210, deadTop.getFarmResumeAtSeconds());
        assertEquals(30, deadTop.getFarmReturnSecondsRemaining());
        state.advanceTimeSeconds(10);
        PlayerSnapshot returning = snapshot(factory.create(state), "BLUE-TOP");
        assertTrue(returning.isAlive());
        assertFalse(returning.isCanFarm());
        assertEquals(20, returning.getFarmReturnSecondsRemaining());
        state.advanceTimeSeconds(20);
        PlayerSnapshot ready = snapshot(factory.create(state), "BLUE-TOP");
        assertTrue(ready.isCanFarm());
        assertEquals(0, ready.getFarmReturnSecondsRemaining());
        assertFalse(deadTop.isAlive());
        assertEquals(30, deadTop.getFarmReturnSecondsRemaining());
    }

    @Test
    void returningAlivePlayerRemainsEligibleForCombat() {
        PlayerState attacker = player("BLUE-TOP", Position.TOP, true);
        attacker.markDead(180, 10);
        assertTrue(attacker.isAlive(190));
        assertFalse(attacker.canFarmAt(190));
        Team blue = new Team("BLUE", List.of(domainPlayer("BLUE-TOP", Position.TOP)));
        Team red = new Team("RED", List.of(domainPlayer("RED-TOP", Position.TOP)));
        TeamState blueState = new TeamState("BLUE", List.of(attacker));
        TeamState redState = new TeamState("RED", List.of(player("RED-TOP", Position.TOP, true)));
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(new TeamfightResolver().resolveKill(190, new FixedRandom(0), blue, blueState, red, redState,
                events, false, new HashSet<>()));
    }

    @Test
    void disabledRecoveryResumesFarmAtRespawn() {
        PlayerState top = player("TOP", Position.TOP, false);
        top.markDead(180, 10);
        assertEquals(190, top.getRespawnAtSeconds());
        assertEquals(190, top.getFarmResumeAtSeconds());
        assertTrue(top.canFarmAt(190));
    }

    @Test
    void sameSeedReproducesFarmStateEconomyAndTimeline() {
        MatchTimeline first = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 77);
        MatchTimeline second = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 77);
        assertEquals(signature(first), signature(second));
    }

    private void assertDelay(Position position, int delay) {
        PlayerState player = player(position.name(), position, true);
        player.markDead(180, 10);
        assertEquals(190, player.getRespawnAtSeconds());
        assertEquals(190 + delay, player.getFarmResumeAtSeconds());
    }

    private void assertCombatSourceUsesCommonRule(boolean teamfight) {
        Team blue = domainTeam("BLUE"), red = domainTeam("RED");
        TeamState blueState = completeTeamState("BLUE", true), redState = completeTeamState("RED", true);
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(new TeamfightResolver().resolveKill(180, new FixedRandom(0), blue, blueState, red, redState,
                events, teamfight, new HashSet<>()));
        MatchEvent kill = events.stream().filter(event -> event.getType() == MatchEventType.KILL).findFirst().orElseThrow();
        PlayerState victim = redState.getPlayers().stream()
                .filter(player -> player.getStructuredPlayerId().equals(kill.getVictimPlayerId()))
                .findFirst().orElseThrow();
        int expectedDelay = FarmRecoveryRuleConfig.returnDelaySeconds(victim.getPosition(), 180);
        assertEquals(victim.getRespawnAtSeconds() + expectedDelay, victim.getFarmResumeAtSeconds());
    }

    private String signature(MatchTimeline timeline) {
        return timeline.getDurationSeconds() + ":" + timeline.getWinner() + ":"
                + timeline.getEvents().stream().map(e -> e.getTimeSeconds() + ":" + e.getType() + ":" + e.getCombatSource() + ":" + e.getVictim()).toList()
                + ":" + timeline.getSnapshots().stream().map(s -> s.getTimeSeconds() + ":" + s.getPlayerSnapshots().stream()
                .map(p -> p.getPlayerName() + ":" + p.isAlive() + ":" + p.isCanFarm() + ":" + p.getRespawnAtSeconds() + ":"
                        + p.getFarmResumeAtSeconds() + ":" + p.getCs() + ":" + p.getGold()).toList()).toList();
    }

    private PlayerSnapshot snapshot(MatchSnapshot snapshot, String name) {
        return snapshot.getPlayerSnapshots().stream().filter(player -> player.getPlayerName().equals(name)).findFirst().orElseThrow();
    }

    private GameState completeState(boolean recovery) {
        return new GameState(completeTeamState("BLUE", recovery), completeTeamState("RED", recovery));
    }

    private TeamState completeTeamState(String side, boolean recovery) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(player(side + "-" + position, position, recovery));
        return new TeamState(side, players);
    }

    private PlayerState player(String name, Position position, boolean recovery) {
        return new PlayerState(name, position, new PlayerAttributes(14, 14, 14, 14), 500, recovery);
    }

    private Team domainTeam(String side) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(domainPlayer(side + "-" + position, position));
        return new Team(side, players);
    }

    private Player domainPlayer(String name, Position position) {
        return new Player(name, position, new PlayerAttributes(14, 14, 14, 14));
    }

    private MatchSimulator simulator(boolean recovery) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), true, recovery);
    }

    private static class FixedRandom extends Random {
        private final double value;
        FixedRandom(double value) { this.value = value; }
        @Override public double nextDouble() { return value; }
    }

    private static final class CountingRandom extends FixedRandom {
        int calls;
        CountingRandom(double value) { super(value); }
        @Override public double nextDouble() { calls++; return super.nextDouble(); }
    }

    private static final class SequenceRandom extends Random {
        private final double[] values;
        private int index;
        SequenceRandom(double... values) { this.values = values; }
        @Override public double nextDouble() { return index < values.length ? values[index++] : 1.0; }
    }
}

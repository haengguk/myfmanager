package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.CounterGankData;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CounterGankTest {
    private final CounterGankResolver resolver = new CounterGankResolver();

    @Test void responseWindowAllows180And840ButRejectsOutside() {
        GameState state = state(14, 14, 14, 14);
        assertEquals(CounterGankIneligibility.OUTSIDE_WINDOW, resolver.ineligibility(state, TeamSide.BLUE, Lane.TOP, 179));
        assertEquals(CounterGankIneligibility.NONE, resolver.ineligibility(state, TeamSide.BLUE, Lane.TOP, 180));
        assertEquals(CounterGankIneligibility.NONE, resolver.ineligibility(state, TeamSide.BLUE, Lane.TOP, 840));
        assertEquals(CounterGankIneligibility.OUTSIDE_WINDOW, resolver.ineligibility(state, TeamSide.BLUE, Lane.TOP, 841));
    }

    @Test void responseRollOccursOnlyWhenEligibleAndCounterIsNotAutomatic() {
        GameState state = at180(state(14, 14, 14, 14));
        CountingRandom fail = new CountingRandom(.99);
        var decision = resolver.tryResolve(state, TeamSide.BLUE, Lane.TOP, false, 0, fail, new ArrayList<>());
        assertTrue(decision.eligible()); assertTrue(decision.responseRolled()); assertFalse(decision.responseSucceeded());
        assertEquals(1, fail.calls); assertEquals(.10, decision.responseChance(), 1e-9);
        state.getRedTeamState().playerAt(Position.JUNGLE).markDead(180, 20);
        CountingRandom unused = new CountingRandom(0);
        var ineligible = resolver.tryResolve(state, TeamSide.BLUE, Lane.TOP, false, 0, unused, new ArrayList<>());
        assertFalse(ineligible.eligible()); assertEquals(0, unused.calls);
    }

    @Test void initialTriggerAggressionAndOverextensionRaiseResponseChanceWithinClamp() {
        GameState base = state(14, 14, 14, 14);
        GameState aggressive = state(14, 14, 14, 20);
        double low = resolver.responseChance(base, TeamSide.BLUE, false, 0);
        assertTrue(resolver.responseChance(base, TeamSide.BLUE, true, 0) > low);
        assertTrue(resolver.responseChance(aggressive, TeamSide.BLUE, false, 0) > low);
        assertTrue(resolver.responseChance(base, TeamSide.BLUE, false, 100) > low);
        assertTrue(resolver.responseChance(aggressive, TeamSide.BLUE, true, 100)
                <= CounterGankRuleConfig.MAX_RESPONSE_CHANCE);
    }

    @Test void deadDefenderJunglerAndAnyDeadLaneParticipantPreventResponse() {
        GameState deadJungle = state(14, 14, 14, 14);
        deadJungle.getRedTeamState().playerAt(Position.JUNGLE).markDead(180, 20);
        assertEquals(CounterGankIneligibility.DEFENDING_JUNGLER_DEAD,
                resolver.ineligibility(deadJungle, TeamSide.BLUE, Lane.TOP, 180));
        for (Position position : List.of(Position.TOP, Position.MID, Position.ADC, Position.SUPPORT)) {
            GameState state = state(14, 14, 14, 14);
            state.getBlueTeamState().playerAt(position).markDead(180, 20);
            Lane lane = position == Position.TOP ? Lane.TOP : position == Position.MID ? Lane.MID : Lane.BOT;
            assertEquals(CounterGankIneligibility.LANE_PARTICIPANT_DEAD,
                    resolver.ineligibility(state, TeamSide.BLUE, lane, 180));
        }
    }

    @Test void responseFailureFallsBackToExactlyOneNormalGankOutcome() {
        GameState state = at180(state(14, 14, 14, 14)); List<MatchEvent> events = new ArrayList<>();
        assertTrue(new JungleGankResolver(true).resolve(state,
                new SequenceRandom(0, .99, 0, .99, .99), events));
        assertEquals(1, events.stream().filter(e -> e.getType() == MatchEventType.JUNGLE_GANK).count());
        assertEquals(0, events.stream().filter(e -> e.getType() == MatchEventType.COUNTER_GANK).count());
        assertTrue(events.stream().filter(e -> e.getType() == MatchEventType.JUNGLE_GANK)
                .findFirst().orElseThrow().getJungleGank().counterResponseRolled());
        assertEquals(-1, state.jungleActionState(TeamSide.RED).getLastJungleActionAtSeconds());
    }

    @Test void successfulCounterReplacesNormalGankAndNoKillStillConsumesMajorCombat() {
        GameState state = at180(state(14, 14, 14, 14)); List<MatchEvent> events = new ArrayList<>();
        assertTrue(new JungleGankResolver(true).resolve(state,
                new SequenceRandom(0, .99, 0, 0, .99), events));
        assertEquals(1, events.stream().filter(e -> e.getType() == MatchEventType.COUNTER_GANK).count());
        assertEquals(0, events.stream().filter(e -> e.getType() == MatchEventType.JUNGLE_GANK).count());
        CounterGankData data = events.stream().filter(e -> e.getType() == MatchEventType.COUNTER_GANK)
                .findFirst().orElseThrow().getCounterGank();
        assertEquals(CounterGankOutcome.NO_KILL, data.outcome());
        assertEquals(0, events.stream().filter(e -> e.getType() == MatchEventType.KILL).count());
    }

    @Test void successfulCounterChargesBothJunglersFarmAndActionCost() {
        Result result = direct(Lane.TOP, new SequenceRandom(0, .99));
        for (TeamSide side : TeamSide.values()) {
            JungleActionState action = result.state.jungleActionState(side);
            assertEquals(180, action.getLastJungleActionAtSeconds());
            assertEquals(210, action.getJungleFarmBlockedUntilSeconds());
        }
        assertEquals(180, result.state.jungleActionState(TeamSide.BLUE).getLastGankAttemptAtSeconds());
        assertEquals(180, result.state.jungleActionState(TeamSide.RED).getLastCounterGankAttemptAtSeconds());
    }

    @Test void bothJunglersLoseFarmAt190And200AndResumeAt210() {
        Result result = direct(Lane.TOP, new SequenceRandom(0, .99));
        PositionEconomyResolver economy = new PositionEconomyResolver();
        for (int time : List.of(190, 200)) for (TeamSide side : TeamSide.values())
            economy.resolve(result.state, result.state.getTeamState(side), side, time, 10, new Random(time + side.ordinal()));
        assertEquals(0, result.state.getBlueTeamState().playerAt(Position.JUNGLE).getCs());
        assertEquals(0, result.state.getRedTeamState().playerAt(Position.JUNGLE).getCs());
        for (TeamSide side : TeamSide.values())
            economy.resolve(result.state, result.state.getTeamState(side), side, 210, 10, new Random(9 + side.ordinal()));
        assertTrue(result.state.getBlueTeamState().playerAt(Position.JUNGLE).getCs() > 0);
        assertTrue(result.state.getRedTeamState().playerAt(Position.JUNGLE).getCs() > 0);
    }

    @Test void counterParticipantCannotGankOrCounterAgainBeforeShared120SecondCooldown() {
        Result result = direct(Lane.TOP, new SequenceRandom(0, .99));
        JungleGankResolver ganks = new JungleGankResolver(true);
        assertFalse(ganks.junglerEligible(result.state, TeamSide.BLUE, 240));
        assertFalse(ganks.junglerEligible(result.state, TeamSide.RED, 240));
        assertEquals(CounterGankIneligibility.DEFENDING_JUNGLER_COOLDOWN,
                resolver.ineligibility(result.state, TeamSide.BLUE, Lane.MID, 240));
        assertTrue(ganks.junglerEligible(result.state, TeamSide.BLUE, 300));
        assertEquals(CounterGankIneligibility.NONE,
                resolver.ineligibility(result.state, TeamSide.BLUE, Lane.MID, 300));
    }

    @Test void mechanicsAggressionTeamfightingGoldAndPreparationBuildCombatEdge() {
        GameState strong = state(18, 18, 10, 10);
        GameState weak = state(10, 10, 18, 18);
        assertTrue(resolver.groupMechanics(strong, TeamSide.BLUE, Lane.TOP)
                > resolver.groupMechanics(weak, TeamSide.BLUE, Lane.TOP));
        assertTrue(resolver.combatEdge(strong, TeamSide.BLUE, Lane.TOP, 0)
                > resolver.combatEdge(weak, TeamSide.BLUE, Lane.TOP, 0));
        assertTrue(resolver.combatEdge(strong, TeamSide.BLUE, Lane.TOP, 100)
                > resolver.combatEdge(strong, TeamSide.BLUE, Lane.TOP, 0));
    }

    @Test void decisiveAndWinProbabilitiesUseConfiguredClampsWithoutForcingEitherWinner() {
        GameState state = state(14, 14, 14, 14);
        double edge = resolver.combatEdge(state, TeamSide.BLUE, Lane.TOP, 0);
        double decisive = resolver.decisiveChance(state, TeamSide.BLUE, Lane.TOP, edge);
        double win = resolver.attackingSideWinChance(edge);
        assertTrue(decisive >= .30 && decisive <= .80);
        assertTrue(win >= .20 && win <= .80);
        assertEquals(.20, resolver.attackingSideWinChance(-100), 1e-9);
        assertEquals(.80, resolver.attackingSideWinChance(100), 1e-9);
    }

    @Test void bothAttackingAndDefendingTeamsCanWinWithIdenticalInputs() {
        Result attack = direct(Lane.TOP, new SequenceRandom(0, 0, 0, 0, 0));
        Result defend = direct(Lane.TOP, new SequenceRandom(0, 0, .99, 0, 0));
        assertEquals(CounterGankOutcome.ATTACKING_SIDE_KILL, attack.data.outcome());
        assertEquals(TeamSide.BLUE, attack.data.winningSide());
        assertEquals(CounterGankOutcome.DEFENDING_SIDE_KILL, defend.data.outcome());
        assertEquals(TeamSide.RED, defend.data.winningSide());
    }

    @Test void soloCounterHasOneKillerOneVictimAndExactlyOneAlliedAssistant() {
        Result result = direct(Lane.TOP, new SequenceRandom(0, 0, 0, 0, 0));
        assertEquals(1, result.data.assistantPlayerIds().size());
        assertTrue(result.data.killerPlayerId().startsWith("player-fixture-blue-"));
        assertTrue(result.data.victimPlayerId().startsWith("player-fixture-red-"));
        assertTrue(result.data.assistantPlayerIds().getFirst().startsWith("player-fixture-blue-"));
        assertEquals(1, result.state.getRedTeamState().getPlayers().stream().mapToInt(PlayerState::getDeaths).sum());
    }

    @Test void botCounterHasTwoAlliedAssistantsAndJunglerAdcSupportWeightedParticipants() {
        Result result = direct(Lane.BOT, new SequenceRandom(0, 0, 0, .9, .9));
        assertEquals(2, result.data.assistantPlayerIds().size());
        assertEquals(2, result.data.assistantPlayerIds().stream().distinct().count());
        assertTrue(result.data.assistantPlayerIds().stream().allMatch(id -> id.startsWith("player-fixture-blue-")));
        assertTrue(result.data.killerPlayerId().endsWith("-support"));
        assertTrue(result.data.victimPlayerId().endsWith("-support"));
    }

    @Test void noKillHasNoDeathRewardOrPressureShock() {
        Result result = direct(Lane.TOP, new SequenceRandom(0, .99));
        assertEquals(CounterGankOutcome.NO_KILL, result.data.outcome());
        assertNull(result.data.winningSide()); assertNull(result.data.killerPlayerId());
        assertEquals(result.data.pressureBefore(), result.data.pressureAfter());
        assertEquals(0, result.state.getBlueTeamState().getKills() + result.state.getRedTeamState().getKills());
    }

    @Test void actualWinnerAppliesPressureShockAndKillRewardsOnlyOnce() {
        Result result = direct(Lane.TOP, new SequenceRandom(0, 0, .99, 0, 0));
        assertTrue(result.data.pressureAfter() < result.data.pressureBefore());
        assertEquals(1, result.state.getRedTeamState().getKills());
        assertEquals(1, result.state.getBlueTeamState().getPlayers().stream().mapToInt(PlayerState::getDeaths).sum());
        assertEquals(1, result.events.stream().filter(e -> e.getType() == MatchEventType.KILL).count());
        assertTrue(result.events.stream().filter(e -> e.getType() == MatchEventType.KILL)
                .allMatch(e -> e.getCombatSource() == CombatSource.COUNTER_GANK));
    }

    @Test void structuredEventContainsSidesJunglersLaneOutcomeParticipantsPressureAndFormulaInputs() {
        Result result = direct(Lane.BOT, new SequenceRandom(0, 0, 0, 0, 0));
        CounterGankData data = result.data;
        assertAll(
                () -> assertEquals(TeamSide.BLUE, data.attackingSide()),
                () -> assertEquals(TeamSide.RED, data.defendingSide()),
                () -> assertEquals("player-fixture-blue-jungle", data.attackingJunglerPlayerId()),
                () -> assertEquals("player-fixture-red-jungle", data.defendingJunglerPlayerId()),
                () -> assertEquals(Lane.BOT, data.targetLane()),
                () -> assertNotNull(data.outcome()),
                () -> assertNotNull(data.killerPlayerId()),
                () -> assertNotNull(data.victimPlayerId()),
                () -> assertEquals(2, data.assistantPlayerIds().size()),
                () -> assertTrue(data.pressureBefore() != data.pressureAfter()),
                () -> assertTrue(data.decisiveChance() >= .30),
                () -> assertTrue(data.attackingSideWinChance() >= .20),
                () -> assertEquals(500.0, data.attackingGroupGold(), 1e-9),
                () -> assertEquals(500.0, data.defendingGroupGold(), 1e-9));
    }

    @Test void counterMajorCombatNeverSharesTickWithNormalGankLaneCombatSkirmishOrTeamfight() {
        MatchTimeline timeline = findTimelineWithCounter();
        for (MatchEvent counter : timeline.getEvents()) {
            if (counter.getType() != MatchEventType.COUNTER_GANK) continue;
            long majors = timeline.getEvents().stream().filter(e -> e.getTimeSeconds() == counter.getTimeSeconds())
                    .filter(e -> e.getType() == MatchEventType.COUNTER_GANK
                            || e.getType() == MatchEventType.JUNGLE_GANK
                            || e.getType() == MatchEventType.LANE_COMBAT
                            || e.getType() == MatchEventType.TEAMFIGHT
                            || e.getCombatSource() == CombatSource.SKIRMISH).count();
            assertEquals(1, majors);
        }
    }

    @Test void supportCsRemainsZeroWithCounterGanks() {
        MatchTimeline timeline = findTimelineWithCounter();
        assertTrue(timeline.getSnapshots().getLast().getPlayerSnapshots().stream()
                .filter(player -> player.getPosition() == Position.SUPPORT).allMatch(player -> player.getCs() == 0));
    }

    @Test void sameSeedReproducesCounterEventsParticipantsPressureAndTimeline() {
        MatchSimulator simulator = simulator(true);
        MatchTimeline first = simulator.simulate(domainTeam("BLUE"), domainTeam("RED"), 31);
        MatchTimeline second = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 31);
        assertEquals(signature(first), signature(second));
    }

    @Test void responseSuccessConsumesNoNormalGankOutcomeRandom() {
        GameState state = at180(state(14,14,14,14)); SequenceRandom random = new SequenceRandom(0,.99,0,0,.99,.12,.34);
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(new JungleGankResolver(true).resolve(state, random, events));
        assertEquals(5, random.index);
        assertTrue(events.stream().anyMatch(e -> e.getType() == MatchEventType.COUNTER_GANK));
    }

    @Test void canFarmFalseAloneDoesNotBlockEligibleLivingParticipant() {
        GameState state=state(14,14,14,14);
        PlayerState top=state.getBlueTeamState().playerAt(Position.TOP);
        top.markDead(150,30);
        assertTrue(top.isAlive(180)); assertFalse(top.canFarmAt(180));
        assertEquals(CounterGankIneligibility.NONE,resolver.ineligibility(state,TeamSide.BLUE,Lane.TOP,180));
    }

    @Test void unselectedTriggeredSideReceivesExactInitialTriggerBonus() {
        GameState state=at180(state(14,14,14,14));List<MatchEvent>events=new ArrayList<>();
        assertTrue(new JungleGankResolver(true).resolve(state,new SequenceRandom(0,0,0,0,0,.99),events));
        CounterGankData data=events.stream().filter(e->e.getType()==MatchEventType.COUNTER_GANK).findFirst().orElseThrow().getCounterGank();
        assertTrue(data.defenderInitiallyTriggered());
        assertEquals(.22,data.responseChance(),1e-9);
    }

    @Test void ordinaryGankUsesSharedClockAndPreventsDefenderCounterUntilBoundary() {
        GameState state=state(14,14,14,14);
        state.jungleActionState(TeamSide.RED).recordGankAttempt(180,Lane.TOP);
        assertEquals(CounterGankIneligibility.DEFENDING_JUNGLER_COOLDOWN,resolver.ineligibility(state,TeamSide.BLUE,Lane.MID,299));
        assertEquals(CounterGankIneligibility.NONE,resolver.ineligibility(state,TeamSide.BLUE,Lane.MID,300));
    }

    @Test void successfulCounterRecordsSameLaneTimestampForBothJunglers() {
        Result result=direct(Lane.MID,new SequenceRandom(0,.99));
        assertEquals(180,result.state.jungleActionState(TeamSide.BLUE).getLastGankAttemptAtSeconds(Lane.MID));
        assertEquals(180,result.state.jungleActionState(TeamSide.RED).getLastGankAttemptAtSeconds(Lane.MID));
    }

    @Test void groupGoldUsesOnlyJunglerAndTargetLaneParticipants() {
        GameState state=state(14,14,14,14);
        double base=resolver.groupGold(state,TeamSide.BLUE,Lane.TOP);
        state.getBlueTeamState().playerAt(Position.SUPPORT).addGold(10_000);
        assertEquals(base,resolver.groupGold(state,TeamSide.BLUE,Lane.TOP),1e-9);
        state.getBlueTeamState().playerAt(Position.TOP).addGold(1_000);
        assertTrue(resolver.groupGold(state,TeamSide.BLUE,Lane.TOP)>base);
    }

    @Test void configuredGroupPowerWeightsAreAppliedExactly() {
        GameState state=state(18,18,10,10);
        assertEquals(16.0,resolver.groupMechanics(state,TeamSide.BLUE,Lane.TOP),1e-9);
        assertEquals(15.6,resolver.groupAggression(state,TeamSide.BLUE,Lane.TOP),1e-9);
        assertEquals(15.4,resolver.groupTeamfighting(state,TeamSide.BLUE,Lane.TOP),1e-9);
    }

    @Test void pressureShockUsesExactSoloAndBotValuesForBothDirections() {
        Result topBlue=direct(Lane.TOP,new SequenceRandom(0,0,0,0,0));
        assertEquals(18.0,topBlue.data.pressureAfter()-topBlue.data.pressureBefore(),1e-9);
        Result botRed=direct(Lane.BOT,new SequenceRandom(0,0,.99,0,0));
        assertEquals(-15.0,botRed.data.pressureAfter()-botRed.data.pressureBefore(),1e-9);
    }

    @Test void counterOffModePreservesNormalJungleGankResolution() {
        GameState state=at180(state(14,14,14,14));List<MatchEvent>events=new ArrayList<>();
        assertTrue(new JungleGankResolver(false).resolve(state,new SequenceRandom(0,.99,0,.99),events));
        assertTrue(events.stream().anyMatch(e->e.getType()==MatchEventType.JUNGLE_GANK));
        assertTrue(events.stream().noneMatch(e->e.getType()==MatchEventType.COUNTER_GANK));
    }

    @Test void deathRecoveryAndCounterFarmBlockUseTheLaterResumeBoundary() {
        Result result=direct(Lane.TOP,new SequenceRandom(0,.99));
        PlayerState jungle=result.state.getBlueTeamState().playerAt(Position.JUNGLE);
        jungle.markDead(180,30);
        assertTrue(jungle.getFarmResumeAtSeconds()>result.state.jungleActionState(TeamSide.BLUE).getJungleFarmBlockedUntilSeconds());
        assertFalse(jungle.canFarmAt(210));assertTrue(jungle.canFarmAt(jungle.getFarmResumeAtSeconds()));
    }

    private Result direct(Lane lane, Random random) {
        GameState state = at180(state(14, 14, 14, 14)); List<MatchEvent> events = new ArrayList<>();
        var decision = resolver.tryResolve(state, TeamSide.BLUE, lane, false, 0, random, events);
        assertTrue(decision.responseSucceeded());
        MatchEvent event = events.stream().filter(e -> e.getType() == MatchEventType.COUNTER_GANK)
                .findFirst().orElseThrow();
        return new Result(state, events, event.getCounterGank());
    }

    private MatchTimeline findTimelineWithCounter() {
        for (long seed = 1; seed <= 250; seed++) {
            MatchTimeline timeline = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), seed);
            if (timeline.getEvents().stream().anyMatch(e -> e.getType() == MatchEventType.COUNTER_GANK)) return timeline;
        }
        throw new AssertionError("No counter-gank found");
    }

    private MatchSimulator simulator(boolean counter) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), true, true, true, counter);
    }

    private GameState at180(GameState state) { state.advanceTimeSeconds(180); return state; }

    private GameState state(int blueJungleMechanics, int blueJungleAggression,
                            int redJungleMechanics, int redJungleAggression) {
        return new GameState(team("BLUE", blueJungleMechanics, blueJungleAggression),
                team("RED", redJungleMechanics, redJungleAggression));
    }

    private TeamState team(String side, int jungleMechanics, int jungleAggression) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(PlayerStateTestFixture.player(side, position,
                new PlayerAttributes(position == Position.JUNGLE ? jungleMechanics : 14,
                        position == Position.JUNGLE ? jungleAggression : 14, 14,
                        position == Position.JUNGLE ? jungleMechanics : 14), 500));
        return new TeamState(side, players);
    }

    private Team domainTeam(String side) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values())
            players.add(new Player(side + "-" + position, position, new PlayerAttributes(14, 14, 14, 14)));
        return new Team(side, players);
    }

    private String signature(MatchTimeline timeline) {
        return timeline.getDurationSeconds() + ":" + timeline.getWinner() + ":"
                + timeline.getEvents().stream().map(event -> event.getTimeSeconds() + ":" + event.getType()
                + ":" + event.getCombatSource() + ":" + event.getKiller() + ":" + event.getVictim() + ":"
                + (event.getCounterGank() == null ? "" : event.getCounterGank())).toList();
    }

    private record Result(GameState state, List<MatchEvent> events, CounterGankData data) { }
    static final class CountingRandom extends Random {
        final double value; int calls;
        CountingRandom(double value) { this.value = value; }
        @Override public double nextDouble() { calls++; return value; }
    }
    static final class SequenceRandom extends Random {
        final double[] values; int index;
        SequenceRandom(double... values) { this.values = values; }
        @Override public double nextDouble() {
            return index < values.length ? values[index++] : values[values.length - 1];
        }
    }
}

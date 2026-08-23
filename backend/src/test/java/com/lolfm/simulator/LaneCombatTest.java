package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.LaneCombatData;
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
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class LaneCombatTest {
    private final LaneCombatResolver resolver = new LaneCombatResolver();

    @Test
    void resolvesOnlyFrom180Through840AtSixtySecondIntervals() {
        GameState state = state(14, 14, 14, 14);
        List<MatchEvent> events = new ArrayList<>();
        setTime(state, 179); resolver.resolve(state, ones(), events); assertEquals(-1, state.getLastLaneCombatResolvedAtSeconds());
        setTime(state, 180); resolver.resolve(state, ones(), events); assertEquals(180, state.getLastLaneCombatResolvedAtSeconds());
        setTime(state, 239); resolver.resolve(state, ones(), events); assertEquals(180, state.getLastLaneCombatResolvedAtSeconds());
        setTime(state, 240); resolver.resolve(state, ones(), events); assertEquals(240, state.getLastLaneCombatResolvedAtSeconds());
        setTime(state, 840); resolver.resolve(state, ones(), events); assertEquals(840, state.getLastLaneCombatResolvedAtSeconds());
        setTime(state, 841); resolver.resolve(state, ones(), events); assertEquals(840, state.getLastLaneCombatResolvedAtSeconds());
    }

    @Test
    void duplicateTimeDoesNotChangeResultOrConsumeRandom() {
        GameState state = at180(state(14, 14, 14, 14));
        CountingRandom random = new CountingRandom(1, 1, 1);
        resolver.resolve(state, random, new ArrayList<>()); int calls = random.calls;
        resolver.resolve(state, random, new ArrayList<>());
        assertEquals(calls, random.calls);
    }

    @Test
    void deadSoloOrBotParticipantMakesThatLaneIneligible() {
        GameState state = at180(state(14, 14, 14, 14));
        state.getBlueTeamState().playerAt(Position.TOP).markDead(100, 200);
        state.getRedTeamState().playerAt(Position.MID).markDead(100, 200);
        state.getBlueTeamState().playerAt(Position.SUPPORT).markDead(100, 200);
        assertFalse(resolver.eligible(state, Lane.TOP, 180));
        assertFalse(resolver.eligible(state, Lane.MID, 180));
        assertFalse(resolver.eligible(state, Lane.BOT, 180));
    }

    @Test
    void mechanicsAndAggressionAffectTheirIntendedProbabilities() {
        GameState mechanics = at180(state(18, 10, 14, 14));
        GameState equal = at180(state(14, 14, 14, 14));
        assertTrue(resolver.combatEdge(mechanics, Lane.TOP, TeamSide.BLUE) > resolver.combatEdge(equal, Lane.TOP, TeamSide.BLUE));
        assertTrue(resolver.attackerWinChance(resolver.combatEdge(mechanics, Lane.TOP, TeamSide.BLUE))
                > resolver.attackerWinChance(resolver.combatEdge(equal, Lane.TOP, TeamSide.BLUE)));
        GameState aggressive = at180(state(14, 14, 18, 18));
        assertTrue(resolver.attemptChance(aggressive, Lane.TOP) > resolver.attemptChance(equal, Lane.TOP));
        GameState asymmetricAggression = at180(state(14, 14, 18, 10));
        assertTrue(resolver.decisiveChance(asymmetricAggression, Lane.TOP, TeamSide.BLUE)
                > resolver.decisiveChance(equal, Lane.TOP, TeamSide.BLUE));
        assertTrue(resolver.attackerWinChance(-100) >= LaneCombatRuleConfig.MIN_ATTACKER_WIN_CHANCE);
    }

    @Test
    void noKillChangesNothingButBlocksOtherCombatAtThatTick() {
        GameState state = at180(state(14, 14, 14, 14)); List<MatchEvent> events = new ArrayList<>();
        int gold = state.getBlueTeamState().getGold();
        assertTrue(resolver.resolve(state, sequence(0, 1, 1, .5, 0, .99), events));
        LaneCombatData data = events.getFirst().getLaneCombat();
        assertEquals(LaneCombatOutcome.NO_KILL, data.outcome()); assertEquals(data.pressureBefore(), data.pressureAfter());
        assertEquals(gold, state.getBlueTeamState().getGold()); assertEquals(0, state.getBlueTeamState().getKills());
        MatchTimeline timeline = findTimelineWithNoKill();
        for (MatchEvent event : timeline.getEvents()) if (event.getType() == MatchEventType.LANE_COMBAT && event.getLaneCombat().outcome() == LaneCombatOutcome.NO_KILL) {
            int time = event.getTimeSeconds();
            assertFalse(timeline.getEvents().stream().anyMatch(other -> other.getTimeSeconds() == time && (other.getType() == MatchEventType.KILL || other.getType() == MatchEventType.TEAMFIGHT)));
            return;
        }
        fail("Expected a no-kill lane attempt");
    }

    @Test
    void topAttackerKillAndMidReverseKillUseActualWinnerForPressure() {
        GameState top = at180(state(14, 14, 14, 14)); List<MatchEvent> topEvents = new ArrayList<>();
        resolver.resolve(top, sequence(0,1,1,.5,0,0,0), topEvents);
        LaneCombatData attack = topEvents.getLast().getLaneCombat();
        assertEquals(Lane.TOP, attack.lane()); assertEquals(LaneCombatOutcome.ATTACKER_KILL, attack.outcome());
        assertEquals(TeamSide.BLUE, attack.winningSide()); assertTrue(attack.pressureAfter() > attack.pressureBefore()); assertTrue(attack.assistantPlayerIds().isEmpty());

        GameState mid = at180(state(14,14,14,14)); List<MatchEvent> midEvents = new ArrayList<>();
        resolver.resolve(mid, sequence(1,0,1,.5,0,0,.99), midEvents);
        LaneCombatData reverse = midEvents.getLast().getLaneCombat();
        assertEquals(Lane.MID, reverse.lane()); assertEquals(LaneCombatOutcome.DEFENDER_REVERSE_KILL, reverse.outcome());
        assertEquals(TeamSide.RED, reverse.winningSide()); assertTrue(reverse.pressureAfter() < reverse.pressureBefore()); assertTrue(reverse.assistantPlayerIds().isEmpty());
    }

    @Test
    void botAssignsKillerVictimAndSameTeamAssistantWithOneDeath() {
        GameState adcKill = at180(state(14,14,14,14)); List<MatchEvent> events = new ArrayList<>();
        resolver.resolve(adcKill, sequence(1,1,0,.5,0,0,0,0,0), events);
        LaneCombatData data = events.getLast().getLaneCombat();
        assertEquals("player-fixture-blue-adc", data.killerPlayerId()); assertEquals(List.of("player-fixture-blue-support"), data.assistantPlayerIds());
        assertEquals("player-fixture-red-adc", data.victimPlayerId()); assertEquals(1, adcKill.getRedTeamState().getPlayers().stream().filter(p -> !p.isAlive(180)).count());

        GameState supportKill = at180(state(14,14,14,14)); events = new ArrayList<>();
        resolver.resolve(supportKill, sequence(1,1,0,.5,0,0,0,.99,.99), events);
        data = events.getLast().getLaneCombat();
        assertEquals("player-fixture-blue-support", data.killerPlayerId()); assertEquals(List.of("player-fixture-blue-adc"), data.assistantPlayerIds());
        assertEquals("player-fixture-red-support", data.victimPlayerId());
    }

    @Test
    void killRewardsAssistShutdownAndDuplicateResolutionAreCorrect() {
        GameState state = at180(state(14,14,14,14));
        PlayerState victim = state.getRedTeamState().playerAt(Position.ADC); victim.addImmediateBountyProgress(400);
        List<MatchEvent> events = new ArrayList<>(); resolver.resolve(state, sequence(1,1,0,.5,0,0,0,0,0), events);
        PlayerState killer = state.getBlueTeamState().playerAt(Position.ADC), assistant = state.getBlueTeamState().playerAt(Position.SUPPORT);
        assertEquals(500 + KillRewardResolver.BASE_KILL_GOLD + 300, killer.getGold());
        assertEquals(500 + KillRewardResolver.BASE_ASSIST_GOLD, assistant.getGold());
        int gold = killer.getGold(); resolver.resolve(state, ones(), events); assertEquals(gold, killer.getGold());
    }

    @Test
    void laneDeathSkipsNextFarmButKeepsPassiveAndResumesAfterRespawn() {
        GameState state = state(14,14,14,14); setTime(state,600);
        MatchSimulator simulator = simulator(true); simulator.applyTickEconomy(new Random(1), state, state.getRedTeamState(), TeamSide.RED, 10, 600);
        PlayerState victim = state.getRedTeamState().playerAt(Position.TOP); int before = victim.getCs();
        resolver.resolve(state, sequence(0,1,1,.5,0,0,0), new ArrayList<>()); assertEquals(before, victim.getCs());
        int goldAfterDeath = victim.getGold();
        setTime(state,610); simulator.applyTickEconomy(new Random(2), state, state.getRedTeamState(), TeamSide.RED,10,610);
        assertEquals(before, victim.getCs()); assertEquals(goldAfterDeath + PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK, victim.getGold());
        setTime(state,620); simulator.applyTickEconomy(new Random(3), state, state.getRedTeamState(), TeamSide.RED,10,620);
        assertEquals(before, victim.getCs());
        setTime(state,650); simulator.applyTickEconomy(new Random(3), state, state.getRedTeamState(), TeamSide.RED,10,650);
        assertTrue(victim.getCs() > before);
    }

    @Test
    void structuredEventContainsAllFieldsAndCombatSource() {
        GameState state=at180(state(14,14,14,14)); List<MatchEvent> events=new ArrayList<>();
        resolver.resolve(state, sequence(0,1,1,.5,0,0,0), events); MatchEvent event=events.getLast(); LaneCombatData data=event.getLaneCombat();
        assertEquals(CombatSource.LANE_COMBAT,event.getCombatSource());
        assertTrue(events.stream().filter(e -> e.getType() == MatchEventType.KILL)
                .allMatch(e -> e.getCombatSource() == CombatSource.LANE_COMBAT));
        assertNotNull(data.lane()); assertNotNull(data.initiatorSide()); assertNotNull(data.outcome()); assertNotNull(data.winningSide()); assertNotNull(data.killerPlayerId()); assertNotNull(data.victimPlayerId()); assertNotNull(data.assistantPlayerIds()); assertNotEquals(data.pressureBefore(),data.pressureAfter());
    }

    @Test
    void killEventsCarrySkirmishOrTeamfightCombatSource() {
        Team blue = domainTeam("BLUE",14,14), red = domainTeam("RED",14,14);
        TeamState blueState = teamState("BLUE",14,14), redState = teamState("RED",14,14);
        TeamfightResolver fights = new TeamfightResolver();
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(fights.resolveKill(900, sequence(0,0,1,1,1,1), blue, blueState, red, redState,
                events, false, new HashSet<>()));
        assertEquals(CombatSource.SKIRMISH, events.stream().filter(e -> e.getType() == MatchEventType.KILL).findFirst().orElseThrow().getCombatSource());

        blueState = teamState("BLUE",14,14); redState = teamState("RED",14,14); events = new ArrayList<>();
        assertTrue(fights.resolveKill(900, sequence(0,0,1,1,1,1), blue, blueState, red, redState,
                events, true, new HashSet<>()));
        assertEquals(CombatSource.TEAMFIGHT, events.stream().filter(e -> e.getType() == MatchEventType.KILL).findFirst().orElseThrow().getCombatSource());
    }

    @Test
    void sameSeedProducesIdenticalStructuredLaneCombatTimeline() {
        MatchTimeline first=simulator(true).simulate(domainTeam("BLUE",14,14),domainTeam("RED",14,14),77);
        MatchTimeline second=simulator(true).simulate(domainTeam("BLUE",14,14),domainTeam("RED",14,14),77);
        assertEquals(signatures(first),signatures(second));
    }

    @Test
    void failedJungleGankEvaluationFallsThroughToLaneCombatResolver() {
        GameState state = at180(state(14,14,14,14));
        assertFalse(new JungleGankResolver(false).resolve(state, ones(), new ArrayList<>()));
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(resolver.resolve(state, sequence(0,1,1,.5,0,.99), events));
        assertEquals(1, state.getCombatExecutionStats().snapshot().jungleGankAllTriggersFailed());
        assertEquals(1, state.getCombatExecutionStats().snapshot().laneCombatResolverCalls());
        assertEquals(1, state.getCombatExecutionStats().snapshot().laneCombatAttempts());
    }

    @Test
    void actualJungleGankAttemptIsTheOnlyCaseThatBlocksLaneCombatAtThatTick() {
        GameState state = at180(state(14,14,14,14));
        boolean attempt = new JungleGankResolver(false).resolve(state, sequence(0,1,0,.99), new ArrayList<>());
        assertTrue(attempt);
        if (!attempt) resolver.resolve(state, sequence(0,1,1,.5,0,.99), new ArrayList<>());
        assertEquals(0, state.getCombatExecutionStats().snapshot().laneCombatResolverCalls());
        assertEquals(1, state.getCombatExecutionStats().snapshot().jungleGankAttempts());
    }

    @Test
    void merelyEvaluatingJungleGankDoesNotConsumeMajorCombat() {
        GameState state = at180(state(14,14,14,14));
        assertFalse(new JungleGankResolver(false).resolve(state, ones(), new ArrayList<>()));
        assertEquals(0, state.getCombatExecutionStats().snapshot().jungleGankAttempts());
        assertEquals(0, state.getCombatExecutionStats().snapshot().counterGankAttempts());
    }

    @Test
    void counterOffRetainsFourthStageLaneCombatFallthrough() {
        GameState state = at180(state(14,14,14,14));
        JungleGankResolver ganks = new JungleGankResolver(false);
        assertFalse(ganks.resolve(state, ones(), new ArrayList<>()));
        assertTrue(resolver.resolve(state, sequence(0,1,1,.5,0,.99), new ArrayList<>()));
    }

    @Test
    void matchSimulatorCanEmitLaneCombatEventAndLaneCombatKillSourceBeforeFourteenMinutes() {
        for (long seed = 1; seed <= 300; seed++) {
            MatchTimeline timeline = simulator(true).simulate(domainTeam("BLUE",14,14), domainTeam("RED",14,14), seed);
            boolean laneEvent = timeline.getEvents().stream().anyMatch(e -> e.getTimeSeconds() <= 840
                    && e.getType() == MatchEventType.LANE_COMBAT);
            boolean laneKill = timeline.getEvents().stream().anyMatch(e -> e.getTimeSeconds() <= 840
                    && e.getType() == MatchEventType.KILL && e.getCombatSource() == CombatSource.LANE_COMBAT);
            if (laneEvent && laneKill) return;
        }
        fail("Expected a pre-14-minute LaneCombat event and LANE_COMBAT kill source");
    }

    private MatchTimeline findTimelineWithNoKill(){for(long seed=1;seed<100;seed++){MatchTimeline t=simulator(true).simulate(domainTeam("BLUE",14,14),domainTeam("RED",14,14),seed);if(t.getEvents().stream().anyMatch(e->e.getType()==MatchEventType.LANE_COMBAT&&e.getLaneCombat().outcome()==LaneCombatOutcome.NO_KILL))return t;}throw new AssertionError();}
    private List<String> signatures(MatchTimeline t){return t.getEvents().stream().filter(e->e.getType()==MatchEventType.LANE_COMBAT).map(e->e.getTimeSeconds()+":"+e.getLaneCombat()).toList();}
    private GameState state(int blueMechanics,int redMechanics,int blueAggression,int redAggression){return new GameState(teamState("BLUE",blueMechanics,blueAggression),teamState("RED",redMechanics,redAggression));}
    private TeamState teamState(String side,int mechanics,int aggression){return new TeamState(side,List.of(ps(side,"TOP",Position.TOP,mechanics,aggression),ps(side,"JUNGLE",Position.JUNGLE,14,14),ps(side,"MID",Position.MID,mechanics,aggression),ps(side,"ADC",Position.ADC,mechanics,aggression),ps(side,"SUPPORT",Position.SUPPORT,mechanics,aggression)));}
    private PlayerState ps(String side,String name,Position p,int m,int a){return PlayerStateTestFixture.player(side, p, new PlayerAttributes(m, a, 14, 14), 500);}
    private Team domainTeam(String side,int m,int a){return new Team(side,List.of(dp(side,"TOP",Position.TOP,m,a),dp(side,"JUNGLE",Position.JUNGLE,14,14),dp(side,"MID",Position.MID,m,a),dp(side,"ADC",Position.ADC,m,a),dp(side,"SUPPORT",Position.SUPPORT,m,a)));}
    private Player dp(String side,String n,Position p,int m,int a){return new Player(side+"-"+n,p,new PlayerAttributes(m,a,14,14));}
    private GameState at180(GameState state){setTime(state,180);return state;} private void setTime(GameState state,int target){state.advanceTimeSeconds(target-state.getCurrentTimeSeconds());}
    private MatchSimulator simulator(boolean enabled){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),enabled);}
    private Random sequence(double...values){return new CountingRandom(values);} private Random ones(){return new CountingRandom(1,1,1,1,1,1,1,1,1,1);}
    private static final class CountingRandom extends Random {private final double[] values;private int index;int calls;CountingRandom(double...values){this.values=values;}@Override public double nextDouble(){calls++;return index<values.length?values[index++]:1.0;}}
}

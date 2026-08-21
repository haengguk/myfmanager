package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.JungleGankData;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class JungleGankTest {
    private final JungleGankResolver resolver = new JungleGankResolver(false);

    @Test void evaluationTimingIs180Through840Every60Seconds() {
        GameState state = state(14, 14, 14, 14);
        assertFalse(state.shouldResolveJungleGankAt(179));
        for (int t = 180; t <= 840; t += 60) assertTrue(state.shouldResolveJungleGankAt(t));
        assertFalse(state.shouldResolveJungleGankAt(181));
        assertFalse(state.shouldResolveJungleGankAt(841));
    }

    @Test void duplicateEvaluationDoesNotChangeStateOrConsumeRandom() {
        GameState state = at180(state(14, 14, 14, 14));
        CountingRandom random = new CountingRandom(.99);
        assertFalse(resolver.resolve(state, random, new ArrayList<>()));
        int calls = random.calls;
        assertFalse(resolver.resolve(state, random, new ArrayList<>()));
        assertEquals(calls, random.calls);
        assertEquals(180, state.getLastJungleGankResolvedAtSeconds());
    }

    @Test void backwardEvaluationThrows() {
        GameState state = state(14, 14, 14, 14);
        state.markJungleGankResolvedAt(180);
        assertThrows(IllegalArgumentException.class, () -> state.shouldResolveJungleGankAt(179));
    }

    @Test void eachGameOwnsIndependentJungleActionState() {
        GameState first = state(14, 14, 14, 14), second = state(14, 14, 14, 14);
        first.jungleActionState(TeamSide.BLUE).recordAttempt(180, Lane.TOP);
        assertEquals(-1, second.jungleActionState(TeamSide.BLUE).getLastGankAttemptAtSeconds());
        assertNotSame(first.jungleActionState(TeamSide.BLUE), second.jungleActionState(TeamSide.BLUE));
    }

    @Test void deadJunglerAndCooldownPreventEligibilityButFarmReturnDoesNot() {
        GameState state = state(14, 14, 14, 14);
        PlayerState jungle = state.getBlueTeamState().playerAt(Position.JUNGLE);
        jungle.markDead(170, 20);
        assertFalse(resolver.junglerEligible(state, TeamSide.BLUE, 180));
        assertTrue(resolver.junglerEligible(state, TeamSide.BLUE, 190));
        state.jungleActionState(TeamSide.BLUE).recordAttempt(180, Lane.TOP);
        assertFalse(resolver.junglerEligible(state, TeamSide.BLUE, 240));
        assertTrue(resolver.junglerEligible(state, TeamSide.BLUE, 300));
    }

    @Test void soloAndBotLaneEligibilityRequiresEveryLaneParticipantAlive() {
        GameState solo = state(14, 14, 14, 14);
        solo.getRedTeamState().playerAt(Position.TOP).markDead(170, 20);
        assertFalse(resolver.laneEligible(solo, Lane.TOP, 180));
        assertTrue(resolver.laneEligible(solo, Lane.MID, 180));
        GameState bot = state(14, 14, 14, 14);
        bot.getBlueTeamState().playerAt(Position.SUPPORT).markDead(170, 20);
        assertFalse(resolver.laneEligible(bot, Lane.BOT, 180));
    }

    @Test void pressureSignProducesCorrectEnemyOverextensionForBothSides() {
        GameState state = state(14, 14, 14, 14);
        state.laneState(Lane.TOP).setPressure(-60);
        assertEquals(60, resolver.enemyOverextension(state, TeamSide.BLUE, Lane.TOP));
        assertEquals(0, resolver.enemyOverextension(state, TeamSide.RED, Lane.TOP));
        state.laneState(Lane.TOP).setPressure(60);
        assertEquals(0, resolver.enemyOverextension(state, TeamSide.BLUE, Lane.TOP));
        assertEquals(60, resolver.enemyOverextension(state, TeamSide.RED, Lane.TOP));
    }

    @Test void aggressionAndOverextensionIncreaseAttemptChanceWithinClamp() {
        GameState low = state(14, 10, 14, 14), high = state(14, 18, 14, 14);
        high.laneState(Lane.TOP).setPressure(-100);
        assertTrue(resolver.attemptChance(high, TeamSide.BLUE) > resolver.attemptChance(low, TeamSide.BLUE));
        assertEquals(.03, resolver.attemptChance(state(14, 1, 14, 14), TeamSide.BLUE), 1e-9);
        GameState max = state(14, 20, 14, 14); max.laneState(Lane.TOP).setPressure(-100);
        assertTrue(resolver.attemptChance(max, TeamSide.BLUE) <= .22);
    }

    @Test void simultaneousTriggersSelectOnlyOneSideAndChargeOnlyWinner() {
        GameState state = at180(state(14, 14, 14, 14));
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(resolver.resolve(state, new SequenceRandom(0, 0, 0, 0, .99), events));
        int attempts = 0;
        for (TeamSide side : TeamSide.values()) if (state.jungleActionState(side).getLastGankAttemptAtSeconds() == 180) attempts++;
        assertEquals(1, attempts);
    }

    @Test void targetWeightIncludesOverextensionFollowupGoldBotAndRepeatFactors() {
        GameState state = state(14, 14, 14, 14);
        double base = resolver.targetWeight(state, TeamSide.BLUE, Lane.TOP, 180);
        state.laneState(Lane.TOP).setPressure(-100);
        assertTrue(resolver.targetWeight(state, TeamSide.BLUE, Lane.TOP, 180) > base);
        double bot = resolver.targetWeight(state, TeamSide.BLUE, Lane.BOT, 180);
        assertTrue(bot > 0);
        state.jungleActionState(TeamSide.BLUE).recordAttempt(120, Lane.TOP);
        assertEquals((base + 2) * JungleGankRuleConfig.REPEAT_GANK_WEIGHT_MULTIPLIER,
                resolver.targetWeight(state, TeamSide.BLUE, Lane.TOP, 180), 1e-9);
    }

    @Test void weightedTargetSelectionIsSeededAndPositive() {
        GameState state = state(14, 14, 14, 14);
        assertEquals(resolver.chooseTargetLane(state, TeamSide.BLUE, 180, new Random(7)),
                resolver.chooseTargetLane(state, TeamSide.BLUE, 180, new Random(7)));
        for (Lane lane : Lane.values()) assertTrue(resolver.targetWeight(state, TeamSide.BLUE, lane, 180) > 0);
    }

    @Test void mechanicsAggressionGoldVulnerabilityAndNumbersBuildCombatEdge() {
        GameState weak = state(10, 10, 18, 18), strong = state(18, 18, 10, 10);
        assertTrue(resolver.attackerMechanics(strong, TeamSide.BLUE, Lane.TOP)
                > resolver.attackerMechanics(weak, TeamSide.BLUE, Lane.TOP));
        assertTrue(resolver.combatEdge(strong, TeamSide.BLUE, Lane.TOP)
                > resolver.combatEdge(weak, TeamSide.BLUE, Lane.TOP));
        strong.laneState(Lane.TOP).setPressure(-100);
        assertTrue(resolver.combatEdge(strong, TeamSide.BLUE, Lane.TOP)
                > resolver.combatEdge(state(18, 18, 10, 10), TeamSide.BLUE, Lane.TOP));
    }

    @Test void decisiveAndSuccessChancesUseConfiguredClamps() {
        GameState state = state(20, 20, 1, 1); state.laneState(Lane.TOP).setPressure(-100);
        double decisive = resolver.decisiveChance(state, TeamSide.BLUE, Lane.TOP);
        double success = resolver.gankSuccessChance(resolver.combatEdge(state, TeamSide.BLUE, Lane.TOP));
        assertTrue(decisive >= .25 && decisive <= .70);
        assertTrue(success >= .25 && success <= .88);
        assertEquals(.25, resolver.gankSuccessChance(-100), 1e-9);
        assertEquals(.88, resolver.gankSuccessChance(100), 1e-9);
    }

    @Test void noKillStillRecordsCooldownFarmCostAndStructuredAttemptWithoutShock() {
        Result result = resolveBlue(new SequenceRandom(0, .99, 0, .99));
        assertEquals(JungleGankOutcome.NO_KILL, result.data.outcome());
        assertEquals(result.data.pressureBefore(), result.data.pressureAfter());
        assertEquals(210, result.state.jungleActionState(TeamSide.BLUE).getJungleFarmBlockedUntilSeconds());
        assertEquals(180, result.state.jungleActionState(TeamSide.BLUE).getLastGankAttemptAtSeconds());
    }

    @Test void soloSuccessHasOneVictimJunglerOrLanerKillerAndOtherAssistant() {
        Result result = resolveBlue(new SequenceRandom(0, .99, 0, 0, 0, 0));
        assertEquals(JungleGankOutcome.GANK_SUCCESS, result.data.outcome());
        assertEquals(1, result.data.assistantPlayerIds().size());
        assertTrue(result.data.killerPlayerId().startsWith("player-fixture-blue-"));
        assertTrue(result.data.victimPlayerId().startsWith("player-fixture-red-"));
    }

    @Test void soloReverseHasDefenderKillerNoAssistAndAttackerVictim() {
        Result result = resolveBlue(new SequenceRandom(0, .99, 0, 0, .99, 0));
        assertEquals(JungleGankOutcome.DEFENDER_REVERSE_KILL, result.data.outcome());
        assertTrue(result.data.killerPlayerId().startsWith("player-fixture-red-"));
        assertTrue(result.data.assistantPlayerIds().isEmpty());
        assertTrue(result.data.victimPlayerId().startsWith("player-fixture-blue-"));
    }

    @Test void botSuccessStoresTwoDistinctAlliedAssistantsAndEnemyAdcOrSupportVictim() {
        Result result = resolveBlue(new SequenceRandom(0, .99, .99, 0, 0, .4, 0));
        assertEquals(Lane.BOT, result.data.targetLane());
        assertEquals(2, result.data.assistantPlayerIds().size());
        assertEquals(2, result.data.assistantPlayerIds().stream().distinct().count());
        assertTrue(result.data.assistantPlayerIds().stream().allMatch(id -> id.startsWith("player-fixture-blue-")));
        assertTrue(result.data.victimPlayerId().endsWith("-adc") || result.data.victimPlayerId().endsWith("-support"));
    }

    @Test void botReverseStoresDefenderAssistAndOneAttackingVictim() {
        Result result = resolveBlue(new SequenceRandom(0, .99, .99, 0, .99, 0, 0));
        assertEquals(Lane.BOT, result.data.targetLane());
        assertEquals(JungleGankOutcome.DEFENDER_REVERSE_KILL, result.data.outcome());
        assertEquals(1, result.data.assistantPlayerIds().size());
        assertTrue(result.data.killerPlayerId().startsWith("player-fixture-red-"));
        assertTrue(result.data.assistantPlayerIds().getFirst().startsWith("player-fixture-red-"));
        assertTrue(result.data.victimPlayerId().startsWith("player-fixture-blue-"));
    }

    @Test void killRewardResolverPaysKillAssistShutdownAndRejectsDuplicateDeath() {
        TeamState blue = team("BLUE", 14, 14), red = team("RED", 14, 14);
        PlayerState killer = blue.playerAt(Position.JUNGLE), assist = blue.playerAt(Position.TOP), victim = red.playerAt(Position.TOP);
        KillRewardResolver rewards = new KillRewardResolver();
        rewards.award(180, blue, killer, red, victim, List.of(assist), 10, false, null, new ArrayList<>());
        assertEquals(800, killer.getGold()); assertEquals(650, assist.getGold()); assertEquals(1, victim.getDeaths());
        rewards.award(180, blue, killer, red, victim, List.of(), 10, false, null, new ArrayList<>());
        assertEquals(1, victim.getDeaths());
    }

    @Test void pressureShockUsesActualWinningSideAndClamps() {
        Result success = resolveBlue(new SequenceRandom(0, .99, 0, 0, 0, 0));
        assertTrue(success.data.pressureAfter() > success.data.pressureBefore());
        Result reverse = resolveBlue(new SequenceRandom(0, .99, 0, 0, .99, 0));
        assertTrue(reverse.data.pressureAfter() < reverse.data.pressureBefore());
    }

    @Test void jungleFarmBlockSkips190And200WithoutRandomThenResumesAt210() {
        GameState state = state(14, 14, 14, 14);
        state.jungleActionState(TeamSide.BLUE).recordAttempt(180, Lane.TOP);
        TeamState blue = state.getBlueTeamState(); PlayerState jungle = blue.playerAt(Position.JUNGLE);
        CountingRandom random = new CountingRandom(0);
        PositionEconomyResolver economy = new PositionEconomyResolver();
        economy.resolve(state, blue, TeamSide.BLUE, 190, 10, random);
        economy.resolve(state, blue, TeamSide.BLUE, 200, 10, random);
        assertEquals(0, jungle.getCs()); assertEquals(6, random.calls); // three non-support non-junglers over two blocked ticks
        economy.resolve(state, blue, TeamSide.BLUE, 210, 10, random);
        assertTrue(jungle.getCs() > 0);
    }

    @Test void farmBlockAndDeathRecoveryOverlapByMaximumWithoutCsCatchup() {
        GameState state = state(14, 14, 14, 14); PlayerState jungle = state.getBlueTeamState().playerAt(Position.JUNGLE);
        state.jungleActionState(TeamSide.BLUE).recordAttempt(180, Lane.TOP); jungle.markDead(180, 10);
        assertFalse(jungle.canFarmAt(190)); assertTrue(jungle.canFarmAt(200));
        assertTrue(200 < state.jungleActionState(TeamSide.BLUE).getJungleFarmBlockedUntilSeconds());
        new PositionEconomyResolver().resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 200, 10, new Random(1));
        assertEquals(0, jungle.getCs());
    }

    @Test void gankAttemptBlocksLaneCombatSkirmishAndTeamfightOnSameTick() {
        MatchTimeline timeline = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 2);
        for (MatchEvent gank : timeline.getEvents()) {
            if (gank.getType() != MatchEventType.JUNGLE_GANK) continue;
            long majors = timeline.getEvents().stream().filter(e -> e.getTimeSeconds() == gank.getTimeSeconds())
                    .filter(e -> e.getType() == MatchEventType.JUNGLE_GANK || e.getType() == MatchEventType.LANE_COMBAT
                            || e.getCombatSource() == CombatSource.SKIRMISH || e.getType() == MatchEventType.TEAMFIGHT).count();
            assertEquals(1, majors);
        }
    }

    @Test void structuredEventContainsAllRequiredFieldsAndKillSource() {
        Result result = resolveBlue(new SequenceRandom(0, .99, 0, 0, 0, 0)); JungleGankData data = result.data;
        assertAll(() -> assertEquals(TeamSide.BLUE, data.gankingSide()), () -> assertNotNull(data.junglerPlayerId()),
                () -> assertNotNull(data.targetLane()), () -> assertNotNull(data.outcome()), () -> assertNotNull(data.winningSide()),
                () -> assertNotNull(data.killerPlayerId()), () -> assertNotNull(data.victimPlayerId()),
                () -> assertNotNull(data.assistantPlayerIds()), () -> assertTrue(data.pressureAfter() != data.pressureBefore()));
        assertTrue(result.events.stream().filter(e -> e.getType() == MatchEventType.KILL)
                .allMatch(e -> e.getCombatSource() == CombatSource.JUNGLE_GANK));
    }

    @Test void supportCsAlwaysZeroAndJungleCsCanDifferAfterGankCost() {
        MatchTimeline timeline = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 2);
        var last = timeline.getSnapshots().getLast();
        assertTrue(last.getPlayerSnapshots().stream().filter(p -> p.getPosition() == Position.SUPPORT).allMatch(p -> p.getCs() == 0));
    }

    @Test void sameSeedReproducesGankFieldsParticipantsPressureAndTimeline() {
        MatchTimeline a = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 77);
        MatchTimeline b = simulator(true).simulate(domainTeam("BLUE"), domainTeam("RED"), 77);
        assertEquals(signature(a), signature(b));
    }

    @Test void explicitOffModeHasNoGankButKeepsOtherCombatSystems() {
        MatchTimeline off = simulator(false).simulate(domainTeam("BLUE"), domainTeam("RED"), 7);
        assertTrue(off.getEvents().stream().noneMatch(e -> e.getType() == MatchEventType.JUNGLE_GANK));
        assertTrue(off.getEvents().stream().anyMatch(e -> e.getType() == MatchEventType.LANE_COMBAT
                || e.getCombatSource() == CombatSource.SKIRMISH || e.getType() == MatchEventType.TEAMFIGHT));
    }

    private Result resolveBlue(Random random) {
        GameState state = at180(state(14, 14, 14, 14)); List<MatchEvent> events = new ArrayList<>();
        assertTrue(resolver.resolve(state, random, events));
        MatchEvent event = events.stream().filter(e -> e.getType() == MatchEventType.JUNGLE_GANK).findFirst().orElseThrow();
        return new Result(state, events, event.getJungleGank());
    }
    private GameState at180(GameState state) { state.advanceTimeSeconds(180); return state; }
    private GameState state(int blueJungleMechanics, int blueJungleAggression, int redJungleMechanics, int redJungleAggression) {
        return new GameState(team("BLUE", blueJungleMechanics, blueJungleAggression), team("RED", redJungleMechanics, redJungleAggression));
    }
    private TeamState team(String side, int jungleMechanics, int jungleAggression) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(PlayerStateTestFixture.player(side, position,
                new PlayerAttributes(position == Position.JUNGLE ? jungleMechanics : 14,
                        position == Position.JUNGLE ? jungleAggression : 14, 14, 14), 500));
        return new TeamState(side, players);
    }
    private MatchSimulator simulator(boolean gank) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), true, true, gank, false);
    }
    private Team domainTeam(String side) {
        List<Player> players = new ArrayList<>();
        for (Position p : Position.values()) players.add(new Player(side + "-" + p, p, new PlayerAttributes(14, 14, 14, 14)));
        return new Team(side, players);
    }
    private String signature(MatchTimeline t) {
        return t.getDurationSeconds() + ":" + t.getWinner() + ":" + t.getEvents().stream().map(e ->
                e.getTimeSeconds() + ":" + e.getType() + ":" + e.getCombatSource() + ":" + e.getKiller() + ":" + e.getVictim()
                        + ":" + (e.getJungleGank() == null ? "" : e.getJungleGank().toString())).toList();
    }
    private record Result(GameState state, List<MatchEvent> events, JungleGankData data) { }
    static final class CountingRandom extends Random { final double value; int calls; CountingRandom(double value){this.value=value;} @Override public double nextDouble(){calls++;return value;} }
    static final class SequenceRandom extends Random { final double[] values; int index; SequenceRandom(double... values){this.values=values;} @Override public double nextDouble(){return index < values.length ? values[index++] : values[values.length-1];} }
}

package com.lolfm.simulator;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.CounterGankData;
import com.lolfm.domain.JungleGankData;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JungleV1FocusedHardeningIntegrationTest {
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();
    private static final long GANK_SEED = 2026082201L;
    private static final long COUNTER_GANK_SEED = 2026082203L;

    @Test
    void runtimeGateKeepsEligibilityPriorityConsumptionRewardsAndReplayConsistent() {
        DummyDataFactory teams = new DummyDataFactory();
        MatchChampionAssignments assignments = new ChampionSelectionValidator(
                RESOURCES.catalog()).resolve(null);
        MatchSimulator simulator = tempoSimulator();

        MatchSimulator.SimulationResult first = simulator.simulateWithDiagnostics(
                teams.createBlueTeam(), teams.createRedTeam(), GANK_SEED, assignments);
        MatchSimulator.SimulationResult replay = simulator.simulateWithDiagnostics(
                teams.createBlueTeam(), teams.createRedTeam(), GANK_SEED, assignments);
        MatchSimulator.SimulationResult counter = simulator.simulateWithDiagnostics(
                teams.createBlueTeam(), teams.createRedTeam(), COUNTER_GANK_SEED, assignments);

        assertCompleteTimelineEquals(first.timeline(), replay.timeline());
        assertThat(replay.combatExecutionStats()).isEqualTo(first.combatExecutionStats());
        assertThat(replay.jungleEconomyExecutionStats())
                .isEqualTo(first.jungleEconomyExecutionStats());
        assertThat(replay.jungleTempoExecutionStats())
                .isEqualTo(first.jungleTempoExecutionStats());

        assertRuntimeAlgebra(first);
        assertRuntimeAlgebra(counter);
        assertOneMajorCombatPerTick(first.timeline().getEvents());
        assertOneMajorCombatPerTick(counter.timeline().getEvents());
        assertStructuredJungleRewards(first.timeline().getEvents());
        assertStructuredJungleRewards(counter.timeline().getEvents());
        assertThat(first.combatExecutionStats().jungleGankAttempts()).isPositive();
        assertThat(counter.combatExecutionStats().counterGankAttempts()).isPositive();
    }

    @Test
    void actualGankKeepsCurrentTickEconomyAndMissesOnlyFutureBlockedTicks() {
        GameState state = tempoEconomyState();
        MatchSimulator simulator = tempoSimulator();
        SideOrientationRandomTraceObserver economyRandom = traceRandom();
        primeBlueEconomyThrough180(state, simulator, economyRandom);
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);
        int csAtAttempt = jungler.getCs();
        int goldAtAttempt = jungler.getGold();
        int xpAtAttempt = jungler.getProgressionState().getTotalExperience();
        double bountyAtAttempt = jungler.getBountyProgress();

        List<MatchEvent> events = new ArrayList<>();
        assertThat(new JungleGankResolver(false).resolve(
                state, new SequenceRandom(0.0, 0.0, 0.99), events)).isTrue();
        assertThat(events).anyMatch(event -> event.getType() == MatchEventType.JUNGLE_GANK);
        assertThat(jungler.getCs()).isEqualTo(csAtAttempt);
        assertThat(jungler.getGold()).isEqualTo(goldAtAttempt);
        assertThat(jungler.getProgressionState().getTotalExperience()).isEqualTo(xpAtAttempt);
        assertThat(state.jungleActionState(TeamSide.BLUE)
                .getJungleFarmBlockedUntilSeconds()).isEqualTo(210);

        applyBlueEconomyAt(state, simulator, economyRandom, 190);
        applyBlueEconomyAt(state, simulator, economyRandom, 200);

        assertThat(jungler.getCs()).isEqualTo(csAtAttempt);
        assertThat(jungler.getGold()).isEqualTo(goldAtAttempt
                + 2 * PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK);
        assertThat(jungler.getProgressionState().getTotalExperience()).isEqualTo(xpAtAttempt);
        assertThat(jungler.getBountyProgress()).isEqualTo(bountyAtAttempt);
        assertThat(jungleDrawsAt(economyRandom, 190, 200)).isEmpty();
        assertThat(state.getJungleEconomyExecutionStats().snapshot().skippedByReason())
                .containsEntry(JungleEconomySkipReason.JUNGLE_ACTION_FARM_BLOCK, 2);

        applyBlueEconomyAt(state, simulator, economyRandom, 210);

        assertThat(jungler.getCs()).isEqualTo(csAtAttempt + 1);
        assertThat(jungler.getGold()).isEqualTo(goldAtAttempt
                + 3 * PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK
                + JungleEconomyRuleConfig.GOLD_PER_CS);
        assertThat(jungler.getProgressionState().getTotalExperience())
                .isEqualTo(xpAtAttempt + 63);
        assertThat(jungleDrawsAt(economyRandom, 210)).isNotEmpty();
    }

    @Test
    void deathRecoveryExtendsGankOpportunityCostWithoutCatchUpOrJungleRandom() {
        GameState state = tempoEconomyState();
        MatchSimulator simulator = tempoSimulator();
        SideOrientationRandomTraceObserver economyRandom = traceRandom();
        primeBlueEconomyThrough180(state, simulator, economyRandom);
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);

        assertThat(new JungleGankResolver(false).resolve(
                state, new SequenceRandom(0.0, 0.0, 0.99), new ArrayList<>())).isTrue();
        jungler.markDead(180, 30);
        int csBeforeBlock = jungler.getCs();
        int goldBeforeBlock = jungler.getGold();
        int xpBeforeBlock = jungler.getProgressionState().getTotalExperience();

        applyBlueEconomyAt(state, simulator, economyRandom, 190);
        applyBlueEconomyAt(state, simulator, economyRandom, 200);
        applyBlueEconomyAt(state, simulator, economyRandom, 210);

        assertThat(state.jungleActionState(TeamSide.BLUE)
                .getJungleFarmBlockedUntilSeconds()).isEqualTo(210);
        assertThat(jungler.getFarmResumeAtSeconds()).isEqualTo(220);
        assertThat(jungler.getCs()).isEqualTo(csBeforeBlock);
        assertThat(jungler.getGold()).isEqualTo(goldBeforeBlock
                + 3 * PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK);
        assertThat(jungler.getProgressionState().getTotalExperience())
                .isEqualTo(xpBeforeBlock);
        assertThat(jungleDrawsAt(economyRandom, 190, 200, 210)).isEmpty();
        assertThat(state.getJungleEconomyExecutionStats().snapshot().skippedByReason())
                .containsEntry(JungleEconomySkipReason.DEAD, 2)
                .containsEntry(JungleEconomySkipReason.FARM_RECOVERY, 1);

        applyBlueEconomyAt(state, simulator, economyRandom, 220);

        assertThat(jungler.getCs()).isEqualTo(csBeforeBlock + 1);
        assertThat(jungler.getGold()).isEqualTo(goldBeforeBlock
                + 4 * PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK
                + JungleEconomyRuleConfig.GOLD_PER_CS);
        assertThat(jungler.getProgressionState().getTotalExperience())
                .isEqualTo(xpBeforeBlock + 63);
    }

    @Test
    void macroFarmBlockPreservesPassiveButSkipsCsGoldXpTempoAndRandom() {
        GameState state = tempoEconomyState();
        MatchSimulator simulator = tempoSimulator();
        SideOrientationRandomTraceObserver random = traceRandom();
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);
        jungler.blockFarmUntil(330);
        state.getMidGameMacroState().registerFarmBlock(
                TeamSide.BLUE, Position.JUNGLE, 330);

        applyBlueEconomyAt(state, simulator, random, 300);
        applyBlueEconomyAt(state, simulator, random, 310);
        applyBlueEconomyAt(state, simulator, random, 320);

        assertThat(jungler.getCs()).isZero();
        assertThat(jungler.getGold()).isEqualTo(500
                + 3 * PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK);
        assertThat(jungler.getProgressionState().getTotalExperience()).isZero();
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().creditSeconds()).isZero();
        assertThat(jungleDrawsAt(random, 300, 310, 320)).isEmpty();
        assertThat(state.getJungleEconomyExecutionStats().snapshot().skippedByReason())
                .containsEntry(JungleEconomySkipReason.MACRO_FARM_BLOCK, 3);

        applyBlueEconomyAt(state, simulator, random, 330);

        assertThat(jungler.getCs()).isOne();
        assertThat(jungler.getGold()).isEqualTo(500
                + 4 * PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK
                + JungleEconomyRuleConfig.GOLD_PER_CS);
        assertThat(jungler.getProgressionState().getTotalExperience()).isEqualTo(63);
        assertThat(state.jungleTempoState(TeamSide.BLUE).snapshot().creditSeconds())
                .isPositive();
        assertThat(jungleDrawsAt(random, 330)).isNotEmpty();
    }

    private void assertRuntimeAlgebra(MatchSimulator.SimulationResult result) {
        CombatExecutionStatsSnapshot combat = result.combatExecutionStats();
        JungleTempoExecutionStatsSnapshot tempo = result.jungleTempoExecutionStats();

        assertThat(sum(combat.jungleGankEligibilityByReason()))
                .isEqualTo(combat.jungleGankEvaluations() * TeamSide.values().length);
        assertThat(combat.jungleGankEligibilityByReason().get(
                JungleGankIneligibility.NONE)).isEqualTo(combat.jungleGankTriggerRolls());
        assertThat(combat.jungleGankAttempts() + combat.jungleGankFallthroughs())
                .isEqualTo(combat.jungleGankEvaluations());
        assertThat(combat.jungleGankAllTriggersFailed()
                + combat.jungleGankNoEligibleSides())
                .isEqualTo(combat.jungleGankFallthroughs());
        assertThat(combat.jungleGankTriggerSuccesses())
                .isEqualTo(combat.jungleGankAttempts()
                        + combat.jungleGankUnselectedTriggerSuccesses());
        assertThat(sum(combat.counterGankEligibilityByReason()))
                .isEqualTo(combat.jungleGankAttempts());
        assertThat(tempo.actualConsumptions().get(JungleTempoActionType.GANK))
                .isEqualTo(combat.jungleGankAttempts());
        assertThat(tempo.actualConsumptions().get(JungleTempoActionType.COUNTER_GANK))
                .isEqualTo(combat.counterGankAttempts());
        assertThat(result.jungleEconomyExecutionStats().duplicateCalls()).isZero();
    }

    private void assertOneMajorCombatPerTick(List<MatchEvent> events) {
        Set<Integer> objectiveFightTimes = events.stream()
                .filter(event -> event.getCombatSource() == CombatSource.OBJECTIVE_FIGHT
                        || event.getObjectiveDecision() != null
                        && event.getObjectiveDecision().majorCombatConsumed())
                .map(MatchEvent::getTimeSeconds)
                .collect(java.util.stream.Collectors.toSet());
        Set<Integer> lateGameFightTimes = events.stream()
                .filter(event -> event.getCombatSource() == CombatSource.LATE_GAME_SIEGE
                        || event.getCombatSource() == CombatSource.BASE_DEFENSE
                        || event.getLateGameDecision() != null
                        && event.getLateGameDecision().majorCombatConsumed())
                .map(MatchEvent::getTimeSeconds)
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, Set<MajorCombatKind>> byTime = new HashMap<>();
        for (MatchEvent event : events) {
            MajorCombatKind kind = majorCombatKind(
                    event, objectiveFightTimes, lateGameFightTimes);
            if (kind != null) {
                byTime.computeIfAbsent(event.getTimeSeconds(), ignored ->
                        EnumSet.noneOf(MajorCombatKind.class)).add(kind);
            }
        }
        assertThat(byTime).allSatisfy((time, attempts) ->
                assertThat(attempts)
                        .as("major combat kinds at %s", time)
                        .hasSizeLessThanOrEqualTo(1));
    }

    private MajorCombatKind majorCombatKind(
            MatchEvent event,
            Set<Integer> objectiveFightTimes,
            Set<Integer> lateGameFightTimes
    ) {
        if (event.getType() == MatchEventType.JUNGLE_GANK
                || event.getType() == MatchEventType.COUNTER_GANK
                || event.getCombatSource() == CombatSource.JUNGLE_GANK
                || event.getCombatSource() == CombatSource.COUNTER_GANK) {
            return MajorCombatKind.JUNGLE;
        }
        if (event.getType() == MatchEventType.ROAM
                || event.getCombatSource() == CombatSource.ROAM) return MajorCombatKind.ROAM;
        if (event.getType() == MatchEventType.LANE_COMBAT
                || event.getCombatSource() == CombatSource.LANE_COMBAT) return MajorCombatKind.LANE;
        if (event.getCombatSource() == CombatSource.SKIRMISH) return MajorCombatKind.SKIRMISH;
        if (objectiveFightTimes.contains(event.getTimeSeconds())
                && (event.getType() == MatchEventType.TEAMFIGHT
                || event.getType() == MatchEventType.TEAMFIGHT_RESULT
                || event.getCombatSource() == CombatSource.OBJECTIVE_FIGHT)) {
            return MajorCombatKind.OBJECTIVE;
        }
        if (lateGameFightTimes.contains(event.getTimeSeconds())
                && (event.getType() == MatchEventType.TEAMFIGHT
                || event.getType() == MatchEventType.TEAMFIGHT_RESULT
                || event.getCombatSource() == CombatSource.LATE_GAME_SIEGE
                || event.getCombatSource() == CombatSource.BASE_DEFENSE)) {
            return MajorCombatKind.LATE_GAME;
        }
        if (event.getType() == MatchEventType.TEAMFIGHT
                || event.getType() == MatchEventType.TEAMFIGHT_RESULT
                || event.getCombatSource() == CombatSource.TEAMFIGHT) return MajorCombatKind.TEAMFIGHT;
        if (event.getCombatSource() == CombatSource.OBJECTIVE_FIGHT) {
            return MajorCombatKind.OBJECTIVE;
        }
        return null;
    }

    private void assertStructuredJungleRewards(List<MatchEvent> events) {
        for (MatchEvent summary : events) {
            if (summary.getType() == MatchEventType.JUNGLE_GANK) {
                JungleGankData data = summary.getJungleGank();
                List<MatchEvent> kills = associatedKills(
                        events, summary, CombatSource.JUNGLE_GANK);
                if (data.outcome() == JungleGankOutcome.NO_KILL) {
                    assertThat(kills).isEmpty();
                } else {
                    assertThat(kills).singleElement().satisfies(kill -> {
                        assertThat(kill.getKillerPlayerId()).isEqualTo(data.killerPlayerId());
                        assertThat(kill.getVictimPlayerId()).isEqualTo(data.victimPlayerId());
                        assertThat(kill.getAssistPlayerIds())
                                .containsExactlyElementsOf(data.assistantPlayerIds());
                    });
                }
            }
            if (summary.getType() == MatchEventType.COUNTER_GANK) {
                CounterGankData data = summary.getCounterGank();
                List<MatchEvent> kills = associatedKills(
                        events, summary, CombatSource.COUNTER_GANK);
                if (data.outcome() == CounterGankOutcome.NO_KILL) {
                    assertThat(kills).isEmpty();
                } else {
                    assertThat(kills).singleElement().satisfies(kill -> {
                        assertThat(kill.getKillerPlayerId()).isEqualTo(data.killerPlayerId());
                        assertThat(kill.getVictimPlayerId()).isEqualTo(data.victimPlayerId());
                        assertThat(kill.getAssistPlayerIds())
                                .containsExactlyElementsOf(data.assistantPlayerIds());
                    });
                }
            }
        }
    }

    private List<MatchEvent> associatedKills(
            List<MatchEvent> events,
            MatchEvent summary,
            CombatSource source
    ) {
        return events.stream()
                .filter(event -> event.getTimeSeconds() == summary.getTimeSeconds())
                .filter(event -> event.getType() == MatchEventType.KILL)
                .filter(event -> event.getCombatSource() == source)
                .toList();
    }

    private void primeBlueEconomyThrough180(
            GameState state,
            MatchSimulator simulator,
            SideOrientationRandomTraceObserver random
    ) {
        for (int time = 10; time <= 180; time += 10) {
            applyBlueEconomyAt(state, simulator, random, time);
        }
        assertThat(state.jungleTempoState(TeamSide.BLUE).readinessAt(180).ready()).isTrue();
        assertThat(state.jungleTempoState(TeamSide.RED).readinessAt(180).ready()).isFalse();
    }

    private void applyBlueEconomyAt(
            GameState state,
            MatchSimulator simulator,
            SideOrientationRandomTraceObserver random,
            int time
    ) {
        int delta = time - state.getCurrentTimeSeconds();
        if (delta > 0) state.advanceTimeSeconds(delta);
        random.context(SideOrientationRandomTraceObserver.Source.ECONOMY,
                TeamSide.BLUE, time);
        simulator.applyTickEconomy(
                random, state, state.getBlueTeamState(), TeamSide.BLUE, 10, time);
    }

    private List<SideOrientationRandomTraceObserver.Draw> jungleDrawsAt(
            SideOrientationRandomTraceObserver random,
            int... times
    ) {
        Set<Integer> expected = java.util.Arrays.stream(times).boxed()
                .collect(java.util.stream.Collectors.toSet());
        return random.trace().stream()
                .filter(draw -> draw.resolverSource()
                        == SideOrientationRandomTraceObserver.Source.JUNGLE_ECONOMY)
                .filter(draw -> expected.contains(draw.tickSeconds()))
                .toList();
    }

    private GameState tempoEconomyState() {
        MatchChampionAssignments assignments = belvethBlueJungleAssignments();
        GameState state = new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"),
                true, true, true, true, true, true, assignments);
        state.configureJungleEconomy(
                RESOURCES.jungleClear(),
                JungleClearContribution.ECONOMY_AND_GANK_TEMPO_V1);
        return state;
    }

    private MatchChampionAssignments belvethBlueJungleAssignments() {
        MatchChampionAssignments defaults = new ChampionSelectionValidator(
                RESOURCES.catalog()).resolve(null);
        List<ChampionAssignment> assignments = defaults.asMap().values().stream()
                .map(assignment -> assignment.playerKey().equals(
                        new PlayerKey(TeamSide.BLUE, Position.JUNGLE))
                        ? new ChampionAssignment(assignment.playerKey(),
                        new ChampionId("belveth"), Position.JUNGLE)
                        : assignment)
                .toList();
        return new MatchChampionAssignments(assignments, ChampionSelectionMode.EXPLICIT);
    }

    private MatchSimulator tempoSimulator() {
        SimulationOptions options = SimulationRuntimeProfiles.resolve(
                        SimulationRuntimeProfileId
                                .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1)
                .gameplayConfiguration().toSimulationOptions(
                        SimulationInstrumentation.enabled());
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                options, RESOURCES.matchup());
    }

    private SideOrientationRandomTraceObserver traceRandom() {
        return new SideOrientationRandomTraceObserver(
                73L, "HARDENING", "BLUE", "RED", true);
    }

    private int sum(Map<?, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    private enum MajorCombatKind {
        JUNGLE,
        ROAM,
        LANE,
        SKIRMISH,
        TEAMFIGHT,
        OBJECTIVE,
        LATE_GAME
    }

    private static final class SequenceRandom extends Random {
        private final double[] values;
        private int index;

        private SequenceRandom(double... values) {
            this.values = values;
        }

        @Override
        public double nextDouble() {
            return index < values.length ? values[index++] : values[values.length - 1];
        }
    }
}

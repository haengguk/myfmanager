package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionJungleClearProfileCatalog;
import com.lolfm.champion.ChampionResourceManifest;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class JungleEconomyResolverTest {
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();

    @Test
    void championClearAndResourceManagementProduceOneUnifiedCsGoldXpOutcome() {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(600);
        CountingRandom random = new CountingRandom(0.99);
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);
        int teamGoldBefore = state.getBlueTeamState().getGold();

        JungleEconomyOutcome outcome = resolver().resolve(
                state, TeamSide.BLUE, 600, 10, random).orElseThrow();

        assertThat(outcome.championRoleKey())
                .isEqualTo(new ChampionRoleKey(new ChampionId("belveth"), Position.JUNGLE));
        assertThat(outcome.clearProfileVersion())
                .isEqualTo("full-173-jungle-clear-economy-2026-08-v1");
        assertThat(outcome.championClearMultiplier()).isEqualTo(1.05);
        assertThat(outcome.resourceManagementMultiplier()).isEqualTo(1.0);
        assertThat(outcome.combinedEfficiency()).isEqualTo(1.05);
        assertThat(outcome.expectedCs()).isEqualTo(1.015);
        assertThat(outcome.awardedCs()).isEqualTo(1);
        assertThat(outcome.awardedGold()).isEqualTo(20);
        assertThat(outcome.awardedExperience()).isEqualTo(63);
        assertThat(jungler.getCs()).isEqualTo(1);
        assertThat(jungler.getGold()).isEqualTo(520);
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(teamGoldBefore + 20);
        assertThat(jungler.getProgressionState().getTotalExperience()).isEqualTo(63);
        assertThat(jungler.getBountyProgress()).isGreaterThan(0.0);
        assertThat(random.calls).isEqualTo(1);
        assertThat(state.getJungleEconomyExecutionStats().snapshot())
                .satisfies(stats -> {
                    assertThat(stats.evaluations()).isEqualTo(1);
                    assertThat(stats.eligibleOutcomes()).isEqualTo(1);
                    assertThat(stats.awardedCs()).isEqualTo(1);
                    assertThat(stats.awardedGold()).isEqualTo(20);
                    assertThat(stats.awardedExperience()).isEqualTo(63);
                    assertThat(stats.latestOutcomeBySide()).containsEntry(TeamSide.BLUE, outcome);
                });
    }

    @Test
    void phaseBoundariesUseEarlyThenMidThenLateWithoutRandomPhaseSelection() {
        assertPhase(899, 1.05, 63);
        assertPhase(900, 1.08, 65);
        assertPhase(1_799, 1.08, 65);
        assertPhase(1_800, 1.10, 66);
    }

    @Test
    void realizedPureJungleResourceManagementMultipliesChampionClear() {
        PlayerRatings ratings = PlayerRatings.neutral(Position.JUNGLE)
                .with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, 20)
                .with(PlayerSkill.PATHING, 5)
                .with(PlayerSkill.CONSISTENCY, 20);
        PlayerState eliteResourceJungler = realizedJungler(
                "ELITE-JUNGLE", ratings, 73L);
        GameState state = enabledState("belveth", eliteResourceJungler);
        state.advanceTimeSeconds(900);

        JungleEconomyOutcome outcome = resolver().resolve(
                state, TeamSide.BLUE, 900, 10, new CountingRandom(0.99)).orElseThrow();

        assertThat(outcome.championClearMultiplier()).isEqualTo(1.08);
        assertThat(outcome.resourceManagementMultiplier()).isEqualTo(1.12);
        assertThat(outcome.combinedEfficiency()).isCloseTo(1.2096, within(1.0e-12));
        assertThat(outcome.awardedExperience()).isEqualTo(73);
    }

    @Test
    void pathingChangesLegacyJungleFarmingBlendButNotCandidatePureJrm() {
        PlayerRatings lowPathingRatings = PlayerRatings.neutral(Position.JUNGLE)
                .with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, 17)
                .with(PlayerSkill.PATHING, 5)
                .with(PlayerSkill.CONSISTENCY, 20);
        PlayerRatings highPathingRatings = lowPathingRatings.with(PlayerSkill.PATHING, 20);
        PlayerState lowPathing = realizedJungler("LOW-PATHING", lowPathingRatings, 73L);
        PlayerState highPathing = realizedJungler("HIGH-PATHING", highPathingRatings, 73L);
        PositionEconomyResolver economy = new PositionEconomyResolver();

        assertThat(economy.farmingMultiplier(lowPathing, 900))
                .isLessThan(economy.farmingMultiplier(highPathing, 900));
        assertThat(economy.jungleResourceManagementMultiplier(lowPathing, 900))
                .isEqualTo(economy.jungleResourceManagementMultiplier(highPathing, 900))
                .isEqualTo(1.06);

        GameState lowState = enabledState("belveth", lowPathing);
        GameState highState = enabledState("belveth", highPathing);
        lowState.advanceTimeSeconds(900);
        highState.advanceTimeSeconds(900);
        JungleEconomyOutcome lowOutcome = resolver().resolve(
                lowState, TeamSide.BLUE, 900, 10, new CountingRandom(0.99)).orElseThrow();
        JungleEconomyOutcome highOutcome = resolver().resolve(
                highState, TeamSide.BLUE, 900, 10, new CountingRandom(0.99)).orElseThrow();

        assertThat(lowOutcome.resourceManagementMultiplier())
                .isEqualTo(highOutcome.resourceManagementMultiplier());
        assertThat(lowOutcome.combinedEfficiency()).isEqualTo(highOutcome.combinedEfficiency());
        assertThat(lowOutcome.awardedCs()).isEqualTo(highOutcome.awardedCs());
        assertThat(lowOutcome.awardedGold()).isEqualTo(highOutcome.awardedGold());
        assertThat(lowOutcome.awardedExperience()).isEqualTo(highOutcome.awardedExperience());
    }

    @Test
    void duplicateCallIsIdempotentAndConsumesNoAdditionalRandom() {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(10);
        CountingRandom random = new CountingRandom(0.0);
        JungleEconomyResolver resolver = resolver();

        JungleEconomyOutcome first = resolver.resolve(
                state, TeamSide.BLUE, 10, 10, random).orElseThrow();
        int cs = state.getBlueTeamState().playerAt(Position.JUNGLE).getCs();
        int gold = state.getBlueTeamState().getGold();
        int xp = state.getBlueTeamState().playerAt(Position.JUNGLE)
                .getProgressionState().getTotalExperience();
        Optional<JungleEconomyOutcome> duplicate = resolver.resolve(
                state, TeamSide.BLUE, 10, 10, random);

        assertThat(duplicate).isEmpty();
        assertThat(state.getBlueTeamState().playerAt(Position.JUNGLE).getCs()).isEqualTo(cs);
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(gold);
        assertThat(state.getBlueTeamState().playerAt(Position.JUNGLE)
                .getProgressionState().getTotalExperience()).isEqualTo(xp);
        assertThat(state.jungleEconomyState(TeamSide.BLUE).latestOutcome())
                .contains(first);
        assertThat(state.jungleEconomyState(TeamSide.BLUE).getDuplicateResolutionCount())
                .isEqualTo(1);
        assertThat(state.getJungleEconomyExecutionStats().snapshot().duplicateCalls())
                .isEqualTo(1);
        assertThat(random.calls).isEqualTo(1);
    }

    @Test
    void deadAndGankBlockedTicksAwardNothingAndConsumeNoRandom() {
        GameState dead = enabledState("belveth");
        dead.advanceTimeSeconds(300);
        dead.getBlueTeamState().playerAt(Position.JUNGLE).markDead(300, 20);
        assertIneligible(dead, 300, JungleEconomySkipReason.DEAD);

        GameState gankBlocked = enabledState("belveth");
        gankBlocked.advanceTimeSeconds(300);
        gankBlocked.jungleActionState(TeamSide.BLUE).recordGankAttempt(300, Lane.TOP);
        assertIneligible(gankBlocked, 300,
                JungleEconomySkipReason.JUNGLE_ACTION_FARM_BLOCK);
    }

    @Test
    void remainingIneligibleStatesAwardNothingAndConsumeNoRandom() {
        GameState recovery = enabledState("belveth");
        recovery.advanceTimeSeconds(300);
        recovery.getBlueTeamState().playerAt(Position.JUNGLE).blockFarmUntil(330);
        assertIneligible(recovery, 300, JungleEconomySkipReason.FARM_RECOVERY);

        GameState macro = enabledState("belveth");
        macro.advanceTimeSeconds(300);
        macro.getBlueTeamState().playerAt(Position.JUNGLE).blockFarmUntil(330);
        macro.getMidGameMacroState().registerFarmBlock(
                TeamSide.BLUE, Position.JUNGLE, 330);
        assertIneligible(macro, 300, JungleEconomySkipReason.MACRO_FARM_BLOCK);

        GameState activity = enabledState("belveth");
        activity.advanceTimeSeconds(300);
        activity.getBlueTeamState().playerAt(Position.JUNGLE)
                .beginRoamActivity(Lane.TOP, Lane.MID, 300);
        assertIneligible(activity, 300, JungleEconomySkipReason.NON_DEFAULT_ACTIVITY);

        GameState counterGank = enabledState("belveth");
        counterGank.advanceTimeSeconds(300);
        counterGank.jungleActionState(TeamSide.BLUE)
                .recordCounterGankAttempt(300, Lane.TOP);
        assertIneligible(counterGank, 300,
                JungleEconomySkipReason.JUNGLE_ACTION_FARM_BLOCK);

        GameState finished = enabledState("belveth");
        finished.advanceTimeSeconds(300);
        finished.finish(TeamSide.BLUE, GameEndReason.NEXUS_DESTROYED);
        assertIneligible(finished, 300, JungleEconomySkipReason.MATCH_FINISHED);
    }

    @Test
    void progressionOffStillAwardsUnifiedCsAndGoldButNoExperience() {
        GameState state = enabledState("belveth");
        state.configureProgression(false, false);
        state.advanceTimeSeconds(900);
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);
        int teamGoldBefore = state.getBlueTeamState().getGold();
        CountingRandom random = new CountingRandom(0.99);

        JungleEconomyOutcome outcome = resolver().resolve(
                state, TeamSide.BLUE, 900, 10, random).orElseThrow();

        assertThat(outcome.awardedCs()).isPositive();
        assertThat(outcome.awardedGold())
                .isEqualTo(outcome.awardedCs() * JungleEconomyRuleConfig.GOLD_PER_CS);
        assertThat(outcome.awardedExperience()).isZero();
        assertThat(jungler.getCs()).isEqualTo(outcome.awardedCs());
        assertThat(jungler.getGold()).isEqualTo(500 + outcome.awardedGold());
        assertThat(state.getBlueTeamState().getGold())
                .isEqualTo(teamGoldBefore + outcome.awardedGold());
        assertThat(jungler.getProgressionState().getTotalExperience()).isZero();
        assertThat(random.calls).isEqualTo(1);
    }

    @Test
    void gankBlockedFarmStillReceivesPassiveGoldWithoutFarmBountyOrJungleRandom() {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(300);
        state.jungleActionState(TeamSide.BLUE).recordGankAttempt(300, Lane.TOP);
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);
        CountingRandom random = new CountingRandom(0.0);

        candidateSimulator().applyTickEconomy(
                random, state, state.getBlueTeamState(), TeamSide.BLUE, 10, 300);

        assertThat(jungler.getGold()).isEqualTo(500
                + PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK);
        assertThat(jungler.getCs()).isZero();
        assertThat(jungler.getBountyProgress()).isZero();
        assertThat(jungler.getProgressionState().getTotalExperience()).isZero();
        assertThat(random.calls).isEqualTo(3);
        assertThat(state.getJungleEconomyExecutionStats().snapshot().skippedByReason())
                .containsEntry(JungleEconomySkipReason.JUNGLE_ACTION_FARM_BLOCK, 1);
    }

    @Test
    void economyContributionRejectsSelectedDisabledProfilesAtMatchConfiguration() {
        ChampionJungleClearProfileCatalog historical =
                ChampionJungleClearProfileCatalog.load(
                        new ObjectMapper(), RESOURCES.catalog(),
                        ChampionResourceManifest.open(
                                "/champions/champion-jungle-clear-full-173-v1.json"));
        MatchChampionAssignments assignments = assignments("belveth");
        GameState state = new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"),
                true, true, true, true, true, true, assignments);

        assertThat(historical.profiles().values())
                .allMatch(profile -> !profile.gameplayEnabled());
        assertThatThrownBy(() -> state.configureJungleEconomy(
                historical, JungleClearContribution.ECONOMY_V1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gameplay-enabled clear profile");
    }

    @Test
    void backwardsTimeThrowsBeforeRandomOrEconomyMutation() {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(20);
        CountingRandom random = new CountingRandom(0.99);
        JungleEconomyResolver resolver = resolver();
        resolver.resolve(state, TeamSide.BLUE, 20, 10, random);
        int cs = state.getBlueTeamState().playerAt(Position.JUNGLE).getCs();
        int gold = state.getBlueTeamState().getGold();

        assertThatThrownBy(() -> resolver.resolve(
                state, TeamSide.BLUE, 10, 10, random))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot move backwards");
        assertThat(random.calls).isEqualTo(1);
        assertThat(state.getBlueTeamState().playerAt(Position.JUNGLE).getCs()).isEqualTo(cs);
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(gold);
    }

    @Test
    void disabledContributionKeepsLegacyPositionEconomyAndRandomOrderExactly() {
        MatchChampionAssignments assignments = assignments("belveth");
        GameState disabled = state(assignments, JungleClearContribution.DISABLED_NOT_INTEGRATED);
        TeamState legacy = LateGameTestSupport.team("LEGACY");
        CountingRandom disabledRandom = new CountingRandom(0.42);
        CountingRandom legacyRandom = new CountingRandom(0.42);

        new PositionEconomyResolver().resolve(disabled, disabled.getBlueTeamState(),
                TeamSide.BLUE, 600, 10, disabledRandom);
        new PositionEconomyResolver().resolve(legacy, 600, 10, legacyRandom);

        for (Position position : Position.values()) {
            assertThat(disabled.getBlueTeamState().playerAt(position).getCs())
                    .isEqualTo(legacy.playerAt(position).getCs());
            assertThat(disabled.getBlueTeamState().playerAt(position).getGold())
                    .isEqualTo(legacy.playerAt(position).getGold());
        }
        assertThat(disabled.getBlueTeamState().getGold()).isEqualTo(legacy.getGold());
        assertThat(disabledRandom.calls).isEqualTo(legacyRandom.calls);
        assertThat(disabled.getJungleEconomyExecutionStats().snapshot().evaluations()).isZero();
    }

    @Test
    void enabledPathKeepsPlayerIterationOrderAndLabelsOnlyTheJungleDraw() {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(600);
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                73L, "TEST", "BLUE", "RED", true);
        random.context(SideOrientationRandomTraceObserver.Source.ECONOMY,
                TeamSide.BLUE, 600);

        new PositionEconomyResolver().resolve(state, state.getBlueTeamState(),
                TeamSide.BLUE, 600, 10, random);

        assertThat(random.trace())
                .extracting(SideOrientationRandomTraceObserver.Draw::resolverSource)
                .containsExactly(
                        SideOrientationRandomTraceObserver.Source.ECONOMY,
                        SideOrientationRandomTraceObserver.Source.ECONOMY,
                        SideOrientationRandomTraceObserver.Source.JUNGLE_ECONOMY,
                        SideOrientationRandomTraceObserver.Source.JUNGLE_ECONOMY,
                        SideOrientationRandomTraceObserver.Source.ECONOMY,
                        SideOrientationRandomTraceObserver.Source.ECONOMY,
                        SideOrientationRandomTraceObserver.Source.ECONOMY,
                        SideOrientationRandomTraceObserver.Source.ECONOMY);
    }

    @Test
    void diagnosticMapsKeepCanonicalEnumOrderAndRemainImmutable() {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(10);
        JungleEconomyResolver resolver = resolver();

        resolver.resolve(state, TeamSide.RED, 10, 10, new CountingRandom(0.99));
        resolver.resolve(state, TeamSide.BLUE, 10, 10, new CountingRandom(0.99));
        JungleEconomyExecutionStatsSnapshot snapshot =
                state.getJungleEconomyExecutionStats().snapshot();

        assertThat(snapshot.skippedByReason().keySet())
                .containsExactly(JungleEconomySkipReason.values());
        assertThat(snapshot.latestOutcomeBySide().keySet())
                .containsExactly(TeamSide.BLUE, TeamSide.RED);
        assertThat(state.getJungleEconomyStates().keySet())
                .containsExactly(TeamSide.BLUE, TeamSide.RED);
        assertThatThrownBy(() -> snapshot.skippedByReason().put(
                JungleEconomySkipReason.DEAD, 99))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.getJungleEconomyStates().put(
                TeamSide.BLUE, new JungleEconomyState()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertPhase(int timeSeconds, double multiplier, int experience) {
        GameState state = enabledState("belveth");
        state.advanceTimeSeconds(timeSeconds);
        CountingRandom random = new CountingRandom(0.99);
        JungleEconomyOutcome outcome = resolver().resolve(
                state, TeamSide.BLUE, timeSeconds, 10, random).orElseThrow();
        assertThat(outcome.championClearMultiplier()).isEqualTo(multiplier);
        assertThat(outcome.awardedExperience()).isEqualTo(experience);
        assertThat(random.calls).isEqualTo(1);
    }

    private void assertIneligible(
            GameState state,
            int timeSeconds,
            JungleEconomySkipReason reason
    ) {
        CountingRandom random = new CountingRandom(0.0);
        PlayerState jungler = state.getBlueTeamState().playerAt(Position.JUNGLE);
        int teamGold = state.getBlueTeamState().getGold();

        assertThat(resolver().resolve(
                state, TeamSide.BLUE, timeSeconds, 10, random)).isEmpty();

        assertThat(jungler.getCs()).isZero();
        assertThat(jungler.getGold()).isEqualTo(500);
        assertThat(jungler.getProgressionState().getTotalExperience()).isZero();
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(teamGold);
        assertThat(random.calls).isZero();
        assertThat(state.getJungleEconomyExecutionStats().snapshot().skippedByReason())
                .containsEntry(reason, 1);
    }

    private JungleEconomyResolver resolver() {
        return new JungleEconomyResolver(new PositionEconomyResolver());
    }

    private PlayerState realizedJungler(
            String name,
            PlayerRatings ratings,
            long matchSeed
    ) {
        PlayerMatchPerformance performance = PlayerMatchPerformance.realize(
                ratings, 14, matchSeed, TeamSide.BLUE);
        return new PlayerState(name, Position.JUNGLE,
                new PlayerAttributes(14, 14, 14, 14), performance, 500, true);
    }

    private MatchSimulator candidateSimulator() {
        SimulationOptions options = SimulationRuntimeProfiles.resolve(
                        SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1)
                .gameplayConfiguration().toSimulationOptions(SimulationInstrumentation.enabled());
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                options, RESOURCES.matchup());
    }

    private GameState enabledState(String blueJungle) {
        return state(assignments(blueJungle), JungleClearContribution.ECONOMY_V1);
    }

    private GameState enabledState(String blueJungle, PlayerState blueJungler) {
        MatchChampionAssignments assignments = assignments(blueJungle);
        TeamState blue = new TeamState("BLUE", List.of(
                LateGameTestSupport.player("BLUE-TOP", Position.TOP),
                blueJungler,
                LateGameTestSupport.player("BLUE-MID", Position.MID),
                LateGameTestSupport.player("BLUE-ADC", Position.ADC),
                LateGameTestSupport.player("BLUE-SUPPORT", Position.SUPPORT)));
        GameState state = new GameState(
                blue, LateGameTestSupport.team("RED"), true, true, true,
                true, true, true, assignments);
        state.configureJungleEconomy(
                RESOURCES.jungleClear(), JungleClearContribution.ECONOMY_V1);
        return state;
    }

    private GameState state(
            MatchChampionAssignments assignments,
            JungleClearContribution contribution
    ) {
        GameState state = new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"),
                true, true, true, true, true, true, assignments);
        state.configureJungleEconomy(RESOURCES.jungleClear(), contribution);
        return state;
    }

    private MatchChampionAssignments assignments(String blueJungle) {
        MatchChampionAssignments defaults = new ChampionSelectionValidator(
                RESOURCES.catalog()).resolve(null);
        List<ChampionAssignment> values = new ArrayList<>();
        for (ChampionAssignment assignment : defaults.asMap().values()) {
            if (assignment.playerKey().equals(new PlayerKey(TeamSide.BLUE, Position.JUNGLE))) {
                values.add(new ChampionAssignment(assignment.playerKey(),
                        new ChampionId(blueJungle), Position.JUNGLE));
            } else {
                values.add(assignment);
            }
        }
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    private static final class CountingRandom extends Random {
        private final double value;
        private int calls;

        private CountingRandom(double value) {
            this.value = value;
        }

        @Override
        public double nextDouble() {
            calls++;
            return value;
        }
    }
}

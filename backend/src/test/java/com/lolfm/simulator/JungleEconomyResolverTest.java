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
    void realizedJungleResourceManagementMultipliesChampionClear() {
        PlayerRatings ratings = PlayerRatings.neutral(Position.JUNGLE)
                .with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, 20)
                .with(PlayerSkill.PATHING, 20)
                .with(PlayerSkill.CONSISTENCY, 20);
        PlayerMatchPerformance performance = PlayerMatchPerformance.realize(
                ratings, 14, 73L, TeamSide.BLUE);
        PlayerState eliteResourceJungler = new PlayerState(
                "ELITE-JUNGLE", Position.JUNGLE,
                new PlayerAttributes(14, 14, 14, 14), performance, 500, true);
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

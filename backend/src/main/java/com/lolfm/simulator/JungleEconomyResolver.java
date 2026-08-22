package com.lolfm.simulator;

import com.lolfm.champion.ChampionJungleClearEvaluator;
import com.lolfm.champion.ChampionJungleClearProfile;
import com.lolfm.champion.ChampionJungleClearProfileCatalog;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/** Stateless unified resolver for Champion Clear x pure player JRM economy only. */
public final class JungleEconomyResolver {
    private final PositionEconomyResolver playerEconomy;
    private final ChampionJungleClearEvaluator championClear = new ChampionJungleClearEvaluator();
    private final GoldAwardService goldAwards = new GoldAwardService();
    private final ProgressionRewardResolver progressionRewards = new ProgressionRewardResolver();

    JungleEconomyResolver(PositionEconomyResolver playerEconomy) {
        this.playerEconomy = Objects.requireNonNull(playerEconomy, "playerEconomy");
    }

    public Optional<JungleEconomyOutcome> resolve(
            GameState gameState,
            TeamSide side,
            int timeSeconds,
            int elapsedSeconds,
            Random random
    ) {
        Objects.requireNonNull(gameState, "gameState");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(random, "random");
        if (!gameState.isJungleEconomyEnabled()) {
            throw new IllegalStateException("Jungle economy resolver requires ECONOMY_V1");
        }
        if (elapsedSeconds <= 0) throw new IllegalArgumentException("elapsedSeconds must be positive");

        JungleEconomyExecutionStats stats = gameState.getJungleEconomyExecutionStats();
        stats.recordEvaluation();
        JungleEconomyState economyState = gameState.jungleEconomyState(side);
        if (!economyState.shouldResolveAt(timeSeconds)) {
            stats.recordDuplicate();
            return Optional.empty();
        }

        PlayerState jungler = gameState.getTeamState(side).playerAt(Position.JUNGLE);
        JungleEconomySkipReason skipReason = skipReason(gameState, side, jungler, timeSeconds);
        if (skipReason != null) {
            economyState.markResolvedAt(timeSeconds, null);
            stats.recordSkipped(skipReason);
            recordProgressionSkip(gameState, skipReason);
            return Optional.empty();
        }

        PlayerKey playerKey = new PlayerKey(side, Position.JUNGLE);
        ChampionRoleKey championRoleKey = new ChampionRoleKey(
                gameState.getChampionAssignments().orElseThrow()
                        .get(playerKey).championId(), Position.JUNGLE);
        ChampionJungleClearProfileCatalog catalog = gameState.getJungleClearProfileCatalog()
                .orElseThrow(() -> new IllegalStateException("Missing jungle clear catalog"));
        ChampionJungleClearProfile profile = catalog.get(championRoleKey);
        if (!profile.gameplayEnabled()) {
            throw new IllegalStateException("Selected jungle clear profile is not gameplay-enabled: "
                    + championRoleKey);
        }

        double clearMultiplier = championClear.evaluate(profile, timeSeconds);
        double resourceMultiplier = playerEconomy.jungleResourceManagementMultiplier(
                jungler, timeSeconds);
        double combinedEfficiency = clearMultiplier * resourceMultiplier;
        double expectedCs = JungleEconomyRuleConfig.BASE_CS_PER_MINUTE
                * combinedEfficiency * elapsedSeconds / 60.0;
        int awardedCs = stochasticRound(expectedCs, random);
        int awardedGold = awardedCs * JungleEconomyRuleConfig.GOLD_PER_CS;
        int awardedExperience = gameState.isProgressionEnabled()
                ? (int) Math.round(JungleEconomyRuleConfig.BASE_XP_PER_STANDARD_TICK
                        * combinedEfficiency * elapsedSeconds
                        / JungleEconomyRuleConfig.STANDARD_TICK_SECONDS)
                : 0;

        JungleEconomyOutcome outcome = new JungleEconomyOutcome(
                side, playerKey, championRoleKey, catalog.profileVersion(), timeSeconds,
                elapsedSeconds, clearMultiplier, resourceMultiplier, combinedEfficiency,
                expectedCs, awardedCs, awardedGold, awardedExperience);
        if (awardedCs > 0) jungler.addCs(awardedCs);
        if (awardedGold > 0) {
            goldAwards.awardGold(gameState.getTeamState(side), jungler, awardedGold,
                    GoldSource.FARM, false, timeSeconds);
        }
        if (awardedExperience > 0) {
            progressionRewards.awardExperience(jungler, ExperienceSource.JUNGLE_ECONOMY,
                    awardedExperience, timeSeconds);
            gameState.getProgressionExecutionStats().jungle();
        }
        economyState.markResolvedAt(timeSeconds, outcome);
        stats.recordOutcome(outcome);
        if (gameState.isJungleGankTempoEnabled()) {
            JungleTempoState.CreditUpdate update = gameState.jungleTempoState(side)
                    .recordEconomyOutcome(outcome);
            gameState.getJungleTempoExecutionStats().recordEconomyUpdate(update);
        }
        return Optional.of(outcome);
    }

    private JungleEconomySkipReason skipReason(
            GameState state,
            TeamSide side,
            PlayerState jungler,
            int timeSeconds
    ) {
        if (state.isFinished()) return JungleEconomySkipReason.MATCH_FINISHED;
        if (!jungler.isAlive(timeSeconds)) return JungleEconomySkipReason.DEAD;
        if (!jungler.canFarmAt(timeSeconds)) {
            if (state.getMidGameMacroState().isFarmBlockedByMacro(
                    side, Position.JUNGLE, timeSeconds)) {
                state.getMidGameMacroState().getExecutionStats()
                        .recordFarmBlockedTick(Position.JUNGLE);
                return JungleEconomySkipReason.MACRO_FARM_BLOCK;
            }
            return JungleEconomySkipReason.FARM_RECOVERY;
        }
        if (jungler.getActivityState().getActivityType() != PlayerActivityType.DEFAULT_ROLE) {
            return JungleEconomySkipReason.NON_DEFAULT_ACTIVITY;
        }
        if (timeSeconds < state.jungleActionState(side).getJungleFarmBlockedUntilSeconds()) {
            return JungleEconomySkipReason.JUNGLE_ACTION_FARM_BLOCK;
        }
        return null;
    }

    private void recordProgressionSkip(GameState state, JungleEconomySkipReason reason) {
        if (!state.isProgressionEnabled()) return;
        switch (reason) {
            case DEAD -> state.getProgressionExecutionStats().dead();
            case MACRO_FARM_BLOCK, FARM_RECOVERY ->
                    state.getProgressionExecutionStats().recovery();
            case NON_DEFAULT_ACTIVITY, JUNGLE_ACTION_FARM_BLOCK ->
                    state.getProgressionExecutionStats().activity();
            case MATCH_FINISHED -> { }
        }
    }

    private int stochasticRound(double expectedCs, Random random) {
        if (expectedCs <= 0.0) return 0;
        int wholeCs = (int) Math.floor(expectedCs);
        return wholeCs + (random.nextDouble() < expectedCs - wholeCs ? 1 : 0);
    }
}

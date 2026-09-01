package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.SeriesFormat;
import com.lolfm.champion.ChampionId;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Stateless synchronous kernel that runs exactly one frozen FULL_AUTO fixture. */
@Component
public final class LeagueAutomatedSeriesRunner {
    private final LeagueFrozenProductionIdentityProvider production;
    private final LeagueAutomatedSeriesGameExecutor games;

    @Autowired
    LeagueAutomatedSeriesRunner(
            LeagueFrozenProductionIdentityProvider production,
            LeagueAutomatedSeriesGameExecutor games
    ) {
        this.production = Objects.requireNonNull(production, "production");
        this.games = Objects.requireNonNull(games, "games");
    }

    public LeagueAutomatedSeriesRunResult run(LeagueAutomatedSeriesRunnerInput input) {
        return run(input, SimulationInstrumentation.enabled());
    }

    public LeagueAutomatedSeriesRunResult run(
            LeagueAutomatedSeriesRunnerInput input,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(instrumentation, "instrumentation");
        int executions = 0;
        try {
            LeagueSeasonAggregate season = input.season();
            LeagueFixture fixture = input.fixture();
            validateFrozenInput(input);
            LeagueSeasonFrozenSnapshot current = production.currentSnapshot(
                    season.schedule().teamCodes().stream().collect(
                            java.util.stream.Collectors.toUnmodifiableSet()));
            if (!current.equals(season.frozenSnapshot())) {
                return LeagueAutomatedSeriesRunResult.blocked(
                        "FROZEN_PRODUCTION_IDENTITY_MISMATCH", 0);
            }

            SeriesDraftHistory history = new SeriesDraftHistory();
            Map<String, Integer> score = new HashMap<>();
            score.put(fixture.firstTeamCode(), 0);
            score.put(fixture.secondTeamCode(), 0);
            ArrayList<LeagueFixtureGameReceiptV1> receipts = new ArrayList<>();
            while (!clinched(fixture.seriesFormat(), score)) {
                if (!games.canComplete(history)) {
                    return LeagueAutomatedSeriesRunResult.blocked(
                            "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED", executions);
                }
                int gameNumber = receipts.size() + 1;
                String historyBeforeHash = history.identityHash();
                String blue = fixture.blueTeamCode(gameNumber);
                String red = fixture.redTeamCode(gameNumber);
                long gameSeed = fixture.gameSeed(gameNumber, historyBeforeHash);
                String matchIdentity = "LEAGUE_FIXTURE:" + fixture.fixtureId()
                        + ":SERIES:" + fixture.boundSeriesId()
                        + ":GAME:" + gameNumber;
                executions++;
                LeagueAutomatedSeriesGameExecutor.Execution execution = games.execute(
                        new LeagueAutomatedSeriesGameExecutor.Request(
                                fixture, gameNumber, blue, red, gameSeed, matchIdentity,
                                history, instrumentation));
                LeagueFixtureGameReceiptV1 game = execution.gameReceipt();
                validateGameEvidence(fixture, current, game, gameNumber,
                        historyBeforeHash, blue, red, gameSeed, matchIdentity);
                if (game.winnerTeamCode() == null) {
                    return LeagueAutomatedSeriesRunResult.blocked(
                            "NO_DECISIVE_MATCH_ENGINE_RESULT", executions);
                }
                history.commitCompleted(execution.completedDraft());
                if (!history.identityHash().equals(game.historyAfterHash())
                        || !history.consumedPicks().equals(
                        java.util.Set.copyOf(game.historyAfterPicks()))) {
                    return LeagueAutomatedSeriesRunResult.blocked(
                            "HARD_FEARLESS_COMMIT_INTEGRITY_FAILED", executions);
                }
                score.compute(game.winnerTeamCode(),
                        (ignored, value) -> Objects.requireNonNull(value) + 1);
                receipts.add(game);
            }

            String winner = score.get(fixture.firstTeamCode())
                    > score.get(fixture.secondTeamCode())
                    ? fixture.firstTeamCode() : fixture.secondTeamCode();
            String loser = winner.equals(fixture.firstTeamCode())
                    ? fixture.secondTeamCode() : fixture.firstTeamCode();
            LeagueFixtureCompletionReceiptV1 receipt = new LeagueFixtureCompletionReceiptV1(
                    LeagueFixtureCompletionReceiptV1.SCHEMA,
                    LeagueFixtureCompletionReceiptV1.HASH_ALGORITHM,
                    season.seasonId(), fixture.fixtureId(), fixture.boundSeriesId(),
                    fixture.executionMode(), fixture.firstTeamCode(), fixture.secondTeamCode(),
                    fixture.game1BlueTeamCode(), fixture.game1RedTeamCode(),
                    fixture.seriesFormat(), fixture.fixtureRootSeed(),
                    LeagueIdentity.FIXTURE_ROOT_SEED_ALGORITHM,
                    LeagueIdentity.GAME_SEED_ALGORITHM, season.schedule().scheduleIdentity(),
                    input.frozenProductDecisionHash(), current.snapshotIdentity(),
                    current.teamSnapshotIdentity(fixture.firstTeamCode()),
                    current.teamSnapshotIdentity(fixture.secondTeamCode()),
                    current.playerResourceIdentity(), current.championDraftResourceIdentity(),
                    current.matchupCompositionResourceIdentity(),
                    current.productionRuntimeIdentity(),
                    production.currentResourceProvenanceHash(), receipts,
                    score.get(fixture.firstTeamCode()),
                    score.get(fixture.secondTeamCode()), winner, loser, receipts.size(), null);
            LeagueFixtureCompletionReceiptV2 unifiedReceipt =
                    new LeagueFixtureCompletionReceiptV2(
                            LeagueFixtureCompletionReceiptV2.SCHEMA,
                            LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                            season.leagueId(), null, receipt,
                            receipts.stream().map(game ->
                                    LeagueFixtureDraftAuthorityReceiptV1.fullAuto(
                                            game.gameNumber())).toList(), null);
            VerifiedLeagueFixtureCompletion completion =
                    VerifiedLeagueFixtureCompletion.verifyAutomated(
                            input, current, production.currentResourceProvenanceHash(),
                            receipts, unifiedReceipt);
            return LeagueAutomatedSeriesRunResult.completed(
                    executions, receipt, unifiedReceipt, completion);
        } catch (RuntimeException error) {
            String reason = error.getMessage();
            return LeagueAutomatedSeriesRunResult.blocked(
                    reason == null || reason.isBlank()
                            ? "AUTOMATED_SERIES_EXECUTION_FAILED" : reason,
                    executions);
        }
    }

    private void validateFrozenInput(LeagueAutomatedSeriesRunnerInput input) {
        LeagueSeasonAggregate season = input.season();
        LeagueFixture fixture = input.fixture();
        if (fixture.executionMode() != LeagueFixtureExecutionMode.FULL_AUTO) {
            throw new IllegalArgumentException("PLAYER_CONTROLLED_FIXTURE_REJECTED");
        }
        LeagueFixture scheduled = season.schedule().fixture(fixture.fixtureId());
        if (!scheduled.equals(fixture)) {
            throw new IllegalArgumentException("FROZEN_FIXTURE_BINDING_MISMATCH");
        }
        if (!season.seasonId().equals(season.schedule().seasonId())
                || !season.productDecisionHash().equals(input.frozenProductDecisionHash())
                || !LeagueV1ProductDecisions.productDecisionHash().equals(
                input.frozenProductDecisionHash())) {
            throw new IllegalArgumentException("FROZEN_SEASON_PRODUCT_IDENTITY_MISMATCH");
        }
        if (fixture.seriesFormat() != SeriesFormat.BO3
                && fixture.seriesFormat() != SeriesFormat.BO5) {
            throw new IllegalArgumentException("UNSUPPORTED_SERIES_FORMAT");
        }
    }

    private void validateGameEvidence(
            LeagueFixture fixture,
            LeagueSeasonFrozenSnapshot current,
            LeagueFixtureGameReceiptV1 game,
            int gameNumber,
            String historyBeforeHash,
            String blue,
            String red,
            long gameSeed,
            String matchIdentity
    ) {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean valid = game.gameNumber() == gameNumber
                && game.matchIdentity().equals(matchIdentity)
                && game.blueTeamCode().equals(blue)
                && game.redTeamCode().equals(red)
                && game.gameSeed() == gameSeed
                && game.historyBeforeHash().equals(historyBeforeHash)
                && game.policyId().equals(policy.policyId())
                && game.policyHash().equals(policy.policyHash())
                && game.runtimeProfileId().equals(policy.retainedRuntimeProfileId().name())
                && game.configurationHash().equals(policy.configurationHash())
                && game.engineImplementationVersion().equals(
                policy.engineImplementationVersion())
                && game.activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())
                && game.resourceProvenanceHash().equals(
                production.currentResourceProvenanceHash())
                && fixture.teamCodes().contains(game.blueTeamCode())
                && fixture.teamCodes().contains(game.redTeamCode())
                && current.teamSnapshotIdentities().keySet().containsAll(
                fixture.teamCodes());
        if (!valid) throw new IllegalArgumentException(
                "LEAGUE_GAME_RECEIPT_BINDING_MISMATCH");
    }

    private static boolean clinched(SeriesFormat format, Map<String, Integer> score) {
        return score.values().stream().anyMatch(value -> value >= format.winsRequired());
    }
}

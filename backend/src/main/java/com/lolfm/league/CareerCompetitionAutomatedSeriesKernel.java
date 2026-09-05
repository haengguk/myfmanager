package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.career.CareerCompetitionSeriesBindingV1;
import com.lolfm.champion.ChampionId;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Thin Career adapter over the existing Production Auto Draft and V9 game kernel. */
@Component
public final class CareerCompetitionAutomatedSeriesKernel {
    private final LeagueProductionSnapshotProvider production;
    private final LeagueAutomatedSeriesGameExecutor games;

    CareerCompetitionAutomatedSeriesKernel(
            LeagueProductionSnapshotProvider production,
            LeagueAutomatedSeriesGameExecutor games
    ) {
        this.production = Objects.requireNonNull(production, "production");
        this.games = Objects.requireNonNull(games, "games");
    }

    public CompletedSeriesEvidence run(CareerCompetitionSeriesBindingV1 binding) {
        Objects.requireNonNull(binding, "binding");
        if (!"FULL_AUTO".equals(binding.executionMode())) {
            throw new IllegalArgumentException("PLAYER_CONTROLLED_FIXTURE_REJECTED");
        }
        String canonicalFirst = binding.seedAnchorTeamCode();
        String canonicalSecond = canonicalFirst.equals(binding.firstTeamCode())
                ? binding.secondTeamCode() : binding.firstTeamCode();
        LeagueFixture fixture = new LeagueFixture(binding.fixtureId(),
                "pair_" + canonicalFirst + '_' + canonicalSecond, 1, 1,
                canonicalFirst, canonicalSecond, binding.game1BlueTeamCode(),
                binding.game1RedTeamCode(), binding.seriesFormat(),
                LeagueFixtureExecutionMode.FULL_AUTO, binding.fixtureRootSeed(),
                binding.boundSeriesId(), canonicalFirst);
        LeagueSeasonFrozenSnapshot snapshot = production.currentSnapshot(
                production.currentTeamCodes());
        binding.requireProductionAuthority(snapshot,
                production.currentResourceProvenanceHash());
        SeriesDraftHistory history = new SeriesDraftHistory();
        HashMap<String, Integer> score = new HashMap<>();
        score.put(binding.firstTeamCode(), 0);
        score.put(binding.secondTeamCode(), 0);
        ArrayList<LeagueFixtureGameReceiptV1> receipts = new ArrayList<>();
        while (score.values().stream().noneMatch(value ->
                value >= binding.seriesFormat().winsRequired())) {
            if (!games.canComplete(history)) {
                throw new IllegalStateException("HARD_FEARLESS_LEGAL_POOL_EXHAUSTED");
            }
            int gameNumber = receipts.size() + 1;
            String blue = fixture.blueTeamCode(gameNumber);
            String red = fixture.redTeamCode(gameNumber);
            String historyHash = history.identityHash();
            long gameSeed = fixture.gameSeed(gameNumber, historyHash);
            String matchIdentity = "CAREER_COMPETITION:" + binding.careerId()
                    + ":" + binding.seasonYear() + ':' + binding.competitionId()
                    + ':' + binding.matchId() + ":SERIES:" + binding.boundSeriesId()
                    + ":GAME:" + gameNumber;
            LeagueAutomatedSeriesGameExecutor.Execution execution = games.execute(
                    new LeagueAutomatedSeriesGameExecutor.Request(fixture, gameNumber,
                            blue, red, gameSeed, matchIdentity, history,
                            SimulationInstrumentation.enabled()));
            LeagueFixtureGameReceiptV1 receipt = execution.gameReceipt();
            verifyGame(binding, snapshot, receipt, gameNumber, blue, red,
                    gameSeed, historyHash, matchIdentity);
            if (receipt.winnerTeamCode() == null) {
                throw new IllegalStateException("NO_DECISIVE_MATCH_ENGINE_RESULT");
            }
            HashSet<ChampionId> expected = new HashSet<>(history.consumedPicks());
            expected.addAll(execution.completedDraft().bluePicks());
            expected.addAll(execution.completedDraft().redPicks());
            history.commitCompleted(execution.completedDraft());
            if (!history.consumedPicks().equals(expected)
                    || !history.identityHash().equals(receipt.historyAfterHash())) {
                throw new IllegalStateException("HARD_FEARLESS_COMMIT_INTEGRITY_FAILED");
            }
            score.compute(receipt.winnerTeamCode(),
                    (ignored, value) -> Objects.requireNonNull(value) + 1);
            receipts.add(receipt);
        }
        String winner = score.get(binding.firstTeamCode())
                > score.get(binding.secondTeamCode())
                ? binding.firstTeamCode() : binding.secondTeamCode();
        String loser = winner.equals(binding.firstTeamCode())
                ? binding.secondTeamCode() : binding.firstTeamCode();
        return new CompletedSeriesEvidence(binding.bindingHash(),
                Map.copyOf(score), winner, loser, receipts);
    }

    private void verifyGame(
            CareerCompetitionSeriesBindingV1 binding,
            LeagueSeasonFrozenSnapshot snapshot,
            LeagueFixtureGameReceiptV1 receipt,
            int gameNumber, String blue, String red, long gameSeed,
            String historyHash, String matchIdentity
    ) {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean valid = receipt.gameNumber() == gameNumber
                && receipt.matchIdentity().equals(matchIdentity)
                && receipt.blueTeamCode().equals(blue)
                && receipt.redTeamCode().equals(red)
                && receipt.gameSeed() == gameSeed
                && receipt.historyBeforeHash().equals(historyHash)
                && receipt.policyId().equals(policy.policyId())
                && receipt.policyHash().equals(policy.policyHash())
                && receipt.runtimeProfileId().equals(
                policy.retainedRuntimeProfileId().name())
                && receipt.configurationHash().equals(policy.configurationHash())
                && receipt.engineImplementationVersion().equals(
                policy.engineImplementationVersion())
                && receipt.activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())
                && receipt.resourceProvenanceHash().equals(
                binding.resourceProvenanceHash())
                && snapshot.teamSnapshotIdentities().keySet().containsAll(
                Set.of(binding.firstTeamCode(), binding.secondTeamCode()));
        if (!valid) throw new IllegalStateException(
                "COMPETITION_PRODUCTION_GAME_BINDING_MISMATCH");
    }

    public record CompletedSeriesEvidence(
            String bindingHash,
            Map<String, Integer> score,
            String winnerTeamCode,
            String loserTeamCode,
            List<LeagueFixtureGameReceiptV1> orderedGames
    ) {
        public CompletedSeriesEvidence {
            score = Map.copyOf(score);
            orderedGames = List.copyOf(orderedGames);
        }
    }
}

package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Policy;
import java.util.List;
import java.util.Objects;

/** Opaque standings token. No public constructor or caller-authored verified flag exists. */
public final class VerifiedLeagueFixtureCompletion {
    private final String fixtureId;
    private final String canonicalFixtureReceiptHash;
    private final String winnerTeamCode;
    private final String loserTeamCode;
    private final int winnerGameWins;
    private final int loserGameWins;

    private VerifiedLeagueFixtureCompletion(
            String fixtureId,
            String canonicalFixtureReceiptHash,
            String winnerTeamCode,
            String loserTeamCode,
            int winnerGameWins,
            int loserGameWins
    ) {
        this.fixtureId = fixtureId;
        this.canonicalFixtureReceiptHash = canonicalFixtureReceiptHash;
        this.winnerTeamCode = winnerTeamCode;
        this.loserTeamCode = loserTeamCode;
        this.winnerGameWins = winnerGameWins;
        this.loserGameWins = loserGameWins;
    }

    static VerifiedLeagueFixtureCompletion verifyAutomated(
            LeagueAutomatedSeriesRunnerInput input,
            LeagueSeasonFrozenSnapshot currentSnapshot,
            String currentResourceProvenanceHash,
            List<LeagueFixtureGameReceiptV1> actualOrderedGames,
            LeagueFixtureCompletionReceiptV1 receipt
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(receipt, "receipt");
        actualOrderedGames = List.copyOf(actualOrderedGames);
        LeagueSeasonAggregate season = input.season();
        LeagueFixture fixture = input.fixture();
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean valid = fixture.executionMode() == LeagueFixtureExecutionMode.FULL_AUTO
                && season.schedule().fixture(fixture.fixtureId()).equals(fixture)
                && season.frozenSnapshot().equals(currentSnapshot)
                && receipt.schemaVersion().equals(LeagueFixtureCompletionReceiptV1.SCHEMA)
                && receipt.canonicalHashAlgorithm().equals(
                LeagueFixtureCompletionReceiptV1.HASH_ALGORITHM)
                && receipt.seasonId().equals(season.seasonId())
                && receipt.fixtureId().equals(fixture.fixtureId())
                && receipt.boundSeriesId().equals(fixture.boundSeriesId())
                && receipt.executionMode() == fixture.executionMode()
                && receipt.firstTeamCode().equals(fixture.firstTeamCode())
                && receipt.secondTeamCode().equals(fixture.secondTeamCode())
                && receipt.game1BlueTeamCode().equals(fixture.game1BlueTeamCode())
                && receipt.game1RedTeamCode().equals(fixture.game1RedTeamCode())
                && receipt.seriesFormat() == fixture.seriesFormat()
                && receipt.fixtureRootSeed() == fixture.fixtureRootSeed()
                && receipt.fixtureRootSeedAlgorithm().equals(
                LeagueIdentity.FIXTURE_ROOT_SEED_ALGORITHM)
                && receipt.gameSeedAlgorithm().equals(LeagueIdentity.GAME_SEED_ALGORITHM)
                && receipt.scheduleIdentity().equals(season.schedule().scheduleIdentity())
                && receipt.productDecisionHash().equals(input.frozenProductDecisionHash())
                && receipt.productDecisionHash().equals(
                LeagueV1ProductDecisions.productDecisionHash())
                && receipt.frozenSnapshotIdentity().equals(currentSnapshot.snapshotIdentity())
                && receipt.firstTeamSnapshotIdentity().equals(
                currentSnapshot.teamSnapshotIdentity(fixture.firstTeamCode()))
                && receipt.secondTeamSnapshotIdentity().equals(
                currentSnapshot.teamSnapshotIdentity(fixture.secondTeamCode()))
                && receipt.playerResourceIdentity().equals(
                currentSnapshot.playerResourceIdentity())
                && receipt.championDraftResourceIdentity().equals(
                currentSnapshot.championDraftResourceIdentity())
                && receipt.matchupCompositionResourceIdentity().equals(
                currentSnapshot.matchupCompositionResourceIdentity())
                && receipt.productionRuntimeIdentity().equals(
                currentSnapshot.productionRuntimeIdentity())
                && receipt.resourceProvenanceHash().equals(currentResourceProvenanceHash)
                && receipt.orderedGameReceipts().equals(actualOrderedGames)
                && receipt.actualGameCount() == actualOrderedGames.size();
        if (!valid) {
            throw new IllegalArgumentException("LEAGUE_FIXTURE_RECEIPT_PROOF_MISMATCH");
        }
        String historyHash = com.lolfm.draft.SeriesDraftHistory.identityHash(0, java.util.Set.of());
        for (int index = 0; index < actualOrderedGames.size(); index++) {
            LeagueFixtureGameReceiptV1 game = actualOrderedGames.get(index);
            int number = index + 1;
            boolean gameValid = game.gameNumber() == number
                    && game.blueTeamCode().equals(fixture.blueTeamCode(number))
                    && game.redTeamCode().equals(fixture.redTeamCode(number))
                    && game.gameSeed() == fixture.gameSeed(number, historyHash)
                    && game.historyBeforeHash().equals(historyHash)
                    && game.policyId().equals(policy.policyId())
                    && game.policyHash().equals(policy.policyHash())
                    && game.runtimeProfileId().equals(policy.retainedRuntimeProfileId().name())
                    && game.configurationHash().equals(policy.configurationHash())
                    && game.engineImplementationVersion().equals(
                    policy.engineImplementationVersion())
                    && game.activeGameplayRulesVersion().equals(
                    policy.activeGameplayRulesVersion())
                    && game.resourceProvenanceHash().equals(currentResourceProvenanceHash)
                    && game.winnerTeamCode() != null;
            if (!gameValid) {
                throw new IllegalArgumentException("LEAGUE_GAME_RECEIPT_PROOF_MISMATCH");
            }
            historyHash = game.historyAfterHash();
        }
        int winnerWins = receipt.winnerTeamCode().equals(fixture.firstTeamCode())
                ? receipt.firstTeamGameWins() : receipt.secondTeamGameWins();
        int loserWins = receipt.loserTeamCode().equals(fixture.firstTeamCode())
                ? receipt.firstTeamGameWins() : receipt.secondTeamGameWins();
        return new VerifiedLeagueFixtureCompletion(
                receipt.fixtureId(), receipt.canonicalFixtureReceiptHash(),
                receipt.winnerTeamCode(), receipt.loserTeamCode(), winnerWins, loserWins);
    }

    public String fixtureId() { return fixtureId; }
    public String canonicalFixtureReceiptHash() { return canonicalFixtureReceiptHash; }
    public String winnerTeamCode() { return winnerTeamCode; }
    public String loserTeamCode() { return loserTeamCode; }
    public int winnerGameWins() { return winnerGameWins; }
    public int loserGameWins() { return loserGameWins; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VerifiedLeagueFixtureCompletion value)) return false;
        return winnerGameWins == value.winnerGameWins
                && loserGameWins == value.loserGameWins
                && fixtureId.equals(value.fixtureId)
                && canonicalFixtureReceiptHash.equals(value.canonicalFixtureReceiptHash)
                && winnerTeamCode.equals(value.winnerTeamCode)
                && loserTeamCode.equals(value.loserTeamCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fixtureId, canonicalFixtureReceiptHash, winnerTeamCode,
                loserTeamCode, winnerGameWins, loserGameWins);
    }
}

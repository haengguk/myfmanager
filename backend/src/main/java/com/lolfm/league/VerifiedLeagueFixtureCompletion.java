package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.simulator.TeamSide;
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
            LeagueFixtureCompletionReceiptV2 unifiedReceipt
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(unifiedReceipt, "unifiedReceipt");
        LeagueFixtureCompletionReceiptV1 receipt = unifiedReceipt.fixtureReceipt();
        actualOrderedGames = List.copyOf(actualOrderedGames);
        LeagueSeasonAggregate season = input.season();
        LeagueFixture fixture = input.fixture();
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean valid = fixture.executionMode() == LeagueFixtureExecutionMode.FULL_AUTO
                && season.schedule().fixture(fixture.fixtureId()).equals(fixture)
                && season.frozenSnapshot().equals(currentSnapshot)
                && unifiedReceipt.schemaVersion().equals(
                LeagueFixtureCompletionReceiptV2.SCHEMA)
                && unifiedReceipt.canonicalHashAlgorithm().equals(
                LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM)
                && unifiedReceipt.leagueId().equals(season.leagueId())
                && unifiedReceipt.playerSeriesBindingHash() == null
                && unifiedReceipt.orderedDraftAuthorityReceipts().equals(
                actualOrderedGames.stream().map(game ->
                        LeagueFixtureDraftAuthorityReceiptV1.fullAuto(
                                game.gameNumber())).toList())
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
                receipt.fixtureId(), unifiedReceipt.canonicalFixtureReceiptHash(),
                receipt.winnerTeamCode(), receipt.loserTeamCode(), winnerWins, loserWins);
    }

    static VerifiedLeagueFixtureCompletion verifyPlayer(
            LeagueSeasonAggregate season,
            LeagueFixture fixture,
            LeagueFixtureSeriesBindingV1 binding,
            LeagueSeasonFrozenSnapshot currentSnapshot,
            String currentResourceProvenanceHash,
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence actualSeries,
            List<LeagueFixtureGameReceiptV1> actualOrderedGames,
            List<LeagueFixtureDraftAuthorityReceiptV1> actualAuthorities,
            LeagueFixtureCompletionReceiptV2 unifiedReceipt
    ) {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(fixture, "fixture");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(currentSnapshot, "currentSnapshot");
        Objects.requireNonNull(actualSeries, "actualSeries");
        actualOrderedGames = List.copyOf(actualOrderedGames);
        actualAuthorities = List.copyOf(actualAuthorities);
        Objects.requireNonNull(unifiedReceipt, "unifiedReceipt");
        LeagueFixtureCompletionReceiptV1 receipt = unifiedReceipt.fixtureReceipt();
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean bindingValid = fixture.executionMode()
                == LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                && season.seasonMode() == LeagueSeasonMode.HYBRID_MANAGER
                && season.schedule().fixture(fixture.fixtureId()).equals(fixture)
                && season.frozenSnapshot().equals(currentSnapshot)
                && binding.leagueId().equals(season.leagueId())
                && binding.seasonId().equals(season.seasonId())
                && binding.fixtureId().equals(fixture.fixtureId())
                && binding.boundSeriesId().equals(fixture.boundSeriesId())
                && binding.executionMode() == fixture.executionMode()
                && binding.seriesFormat() == fixture.seriesFormat()
                && binding.managedTeamCode().equals(season.managedTeamCode())
                && binding.firstTeamCode().equals(fixture.firstTeamCode())
                && binding.secondTeamCode().equals(fixture.secondTeamCode())
                && binding.game1BlueTeamCode().equals(fixture.game1BlueTeamCode())
                && binding.game1RedTeamCode().equals(fixture.game1RedTeamCode())
                && binding.fixtureRootSeed() == fixture.fixtureRootSeed()
                && binding.seedAnchorTeamCode().equals(fixture.seedAnchorTeamCode())
                && binding.initialHistoryHash().equals(
                com.lolfm.draft.SeriesDraftHistory.identityHash(
                        0, java.util.Set.of()))
                && binding.scheduleIdentity().equals(season.schedule().scheduleIdentity())
                && binding.productDecisionHash().equals(season.productDecisionHash())
                && binding.productDecisionHash().equals(
                LeagueV1ProductDecisions.productDecisionHash())
                && binding.frozenSnapshotIdentity().equals(
                currentSnapshot.snapshotIdentity())
                && binding.firstTeamSnapshotIdentity().equals(
                currentSnapshot.teamSnapshotIdentity(fixture.firstTeamCode()))
                && binding.secondTeamSnapshotIdentity().equals(
                currentSnapshot.teamSnapshotIdentity(fixture.secondTeamCode()))
                && binding.playerResourceIdentity().equals(
                currentSnapshot.playerResourceIdentity())
                && binding.championDraftResourceIdentity().equals(
                currentSnapshot.championDraftResourceIdentity())
                && binding.matchupCompositionResourceIdentity().equals(
                currentSnapshot.matchupCompositionResourceIdentity())
                && binding.productionRuntimeIdentity().equals(
                currentSnapshot.productionRuntimeIdentity())
                && binding.resourceProvenanceHash().equals(
                currentResourceProvenanceHash)
                && binding.policyId().equals(policy.policyId())
                && binding.policyHash().equals(policy.policyHash())
                && binding.runtimeProfileId().equals(
                policy.retainedRuntimeProfileId().name())
                && binding.configurationHash().equals(policy.configurationHash())
                && binding.activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())
                && binding.engineImplementationVersion().equals(
                policy.engineImplementationVersion());
        boolean seriesValid = actualSeries.seriesId().equals(binding.boundSeriesId())
                && actualSeries.bindingHash().equals(binding.bindingHash())
                && actualSeries.format() == fixture.seriesFormat()
                && actualSeries.firstTeamCode().equals(fixture.firstTeamCode())
                && actualSeries.secondTeamCode().equals(fixture.secondTeamCode())
                && actualSeries.managedTeamCode().equals(season.managedTeamCode())
                && actualSeries.rootSeed() == fixture.fixtureRootSeed()
                && actualSeries.winnerTeamCode().equals(receipt.winnerTeamCode())
                && actualSeries.score().get(fixture.firstTeamCode())
                == receipt.firstTeamGameWins()
                && actualSeries.score().get(fixture.secondTeamCode())
                == receipt.secondTeamGameWins()
                && actualSeries.orderedGames().size() == receipt.actualGameCount();
        boolean receiptValid = unifiedReceipt.schemaVersion().equals(
                LeagueFixtureCompletionReceiptV2.SCHEMA)
                && unifiedReceipt.canonicalHashAlgorithm().equals(
                LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM)
                && unifiedReceipt.leagueId().equals(season.leagueId())
                && unifiedReceipt.playerSeriesBindingHash().equals(binding.bindingHash())
                && unifiedReceipt.orderedDraftAuthorityReceipts().equals(actualAuthorities)
                && receipt.schemaVersion().equals(LeagueFixtureCompletionReceiptV1.SCHEMA)
                && receipt.canonicalHashAlgorithm().equals(
                LeagueFixtureCompletionReceiptV1.HASH_ALGORITHM)
                && receipt.executionMode() == LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                && receipt.seasonId().equals(season.seasonId())
                && receipt.fixtureId().equals(fixture.fixtureId())
                && receipt.boundSeriesId().equals(fixture.boundSeriesId())
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
                && receipt.productDecisionHash().equals(season.productDecisionHash())
                && receipt.productDecisionHash().equals(
                LeagueV1ProductDecisions.productDecisionHash())
                && receipt.frozenSnapshotIdentity().equals(
                currentSnapshot.snapshotIdentity())
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
                && receipt.orderedGameReceipts().equals(actualOrderedGames)
                && receipt.actualGameCount() == actualOrderedGames.size()
                && receipt.resourceProvenanceHash().equals(currentResourceProvenanceHash);
        if (!bindingValid || !seriesValid || !receiptValid) {
            throw new IllegalArgumentException("LEAGUE_PLAYER_FIXTURE_RECEIPT_PROOF_MISMATCH");
        }
        String historyHash = com.lolfm.draft.SeriesDraftHistory.identityHash(
                0, java.util.Set.of());
        for (int index = 0; index < actualOrderedGames.size(); index++) {
            int number = index + 1;
            LeagueFixtureGameReceiptV1 game = actualOrderedGames.get(index);
            LeagueFixtureDraftAuthorityReceiptV1 authority = actualAuthorities.get(index);
            LeaguePlayerSeriesKernelPort.CompletedGameEvidence evidence =
                    actualSeries.orderedGames().get(index);
            TeamSide expectedControlled = fixture.blueTeamCode(number)
                    .equals(binding.managedTeamCode()) ? TeamSide.BLUE : TeamSide.RED;
            boolean valid = game.gameNumber() == number
                    && evidence.gameNumber() == number
                    && game.blueTeamCode().equals(fixture.blueTeamCode(number))
                    && game.redTeamCode().equals(fixture.redTeamCode(number))
                    && evidence.blueTeamCode().equals(game.blueTeamCode())
                    && evidence.redTeamCode().equals(game.redTeamCode())
                    && evidence.controlledSide() == expectedControlled
                    && authority.controlledSide() == expectedControlled
                    && game.gameSeed() == fixture.gameSeed(number, historyHash)
                    && evidence.matchSeed() == game.gameSeed()
                    && game.historyBeforeHash().equals(historyHash)
                    && evidence.historyBeforeHash().equals(historyHash)
                    && game.policyId().equals(policy.policyId())
                    && game.policyHash().equals(policy.policyHash())
                    && game.runtimeProfileId().equals(
                    policy.retainedRuntimeProfileId().name())
                    && game.configurationHash().equals(policy.configurationHash())
                    && game.activeGameplayRulesVersion().equals(
                    policy.activeGameplayRulesVersion())
                    && game.engineImplementationVersion().equals(
                    policy.engineImplementationVersion())
                    && game.resourceProvenanceHash().equals(
                    currentResourceProvenanceHash)
                    && game.winnerTeamCode() != null
                    && game.outputHash().equals(evidence.storedReceipt().outputHash())
                    && game.replayProvenanceHash().equals(
                    evidence.storedReceipt().replayProvenanceHash())
                    && game.simulatorTimelineHash().equals(
                    evidence.storedReceipt().simulatorTimelineHash())
                    && game.structuredTimelineHash().equals(
                    evidence.storedReceipt().structuredTimelineHash())
                    && game.randomTraceHash().equals(
                    evidence.storedReceipt().randomTraceHash());
            if (!valid) {
                throw new IllegalArgumentException(
                        "LEAGUE_PLAYER_GAME_RECEIPT_PROOF_MISMATCH");
            }
            historyHash = game.historyAfterHash();
        }
        List<com.lolfm.champion.ChampionId> finalHistory = actualOrderedGames.isEmpty()
                ? List.of()
                : actualOrderedGames.getLast().historyAfterPicks();
        if (!historyHash.equals(actualSeries.historyHash())
                || !java.util.Set.copyOf(finalHistory).equals(
                java.util.Set.copyOf(actualSeries.consumedPicks()))) {
            throw new IllegalArgumentException("LEAGUE_PLAYER_HISTORY_PROOF_MISMATCH");
        }
        int winnerWins = receipt.winnerTeamCode().equals(fixture.firstTeamCode())
                ? receipt.firstTeamGameWins() : receipt.secondTeamGameWins();
        int loserWins = receipt.loserTeamCode().equals(fixture.firstTeamCode())
                ? receipt.firstTeamGameWins() : receipt.secondTeamGameWins();
        return new VerifiedLeagueFixtureCompletion(receipt.fixtureId(),
                unifiedReceipt.canonicalFixtureReceiptHash(), receipt.winnerTeamCode(),
                receipt.loserTeamCode(), winnerWins, loserWins);
    }

    static VerifiedLeagueFixtureCompletion restoreVerified(
            LeagueFixtureCompletionReceiptV2 receipt
    ) {
        Objects.requireNonNull(receipt, "receipt");
        LeagueFixtureCompletionReceiptV1 core = receipt.fixtureReceipt();
        int winnerWins = core.winnerTeamCode().equals(core.firstTeamCode())
                ? core.firstTeamGameWins() : core.secondTeamGameWins();
        int loserWins = core.loserTeamCode().equals(core.firstTeamCode())
                ? core.firstTeamGameWins() : core.secondTeamGameWins();
        return new VerifiedLeagueFixtureCompletion(core.fixtureId(),
                receipt.canonicalFixtureReceiptHash(), core.winnerTeamCode(),
                core.loserTeamCode(), winnerWins, loserWins);
    }

    /**
     * Revalidates durable evidence against the frozen Season authority. This deliberately
     * does not consult mutable live resources: a previously verified receipt remains usable
     * after a process restart or later authored-resource drift.
     */
    static VerifiedLeagueFixtureCompletion verifyPersisted(
            LeagueSeasonAggregate season,
            LeagueFixtureCompletionReceiptV2 receipt,
            LeagueFixtureSeriesBindingV1 playerBinding
    ) {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(receipt, "receipt");
        LeagueFixture fixture = season.schedule().fixture(receipt.fixtureId());
        LeagueFixtureCompletionReceiptV1 core = receipt.fixtureReceipt();
        LeagueSeasonFrozenSnapshot snapshot = season.frozenSnapshot();
        boolean common = receipt.leagueId().equals(season.leagueId())
                && core.seasonId().equals(season.seasonId())
                && core.fixtureId().equals(fixture.fixtureId())
                && core.boundSeriesId().equals(fixture.boundSeriesId())
                && core.executionMode() == fixture.executionMode()
                && core.firstTeamCode().equals(fixture.firstTeamCode())
                && core.secondTeamCode().equals(fixture.secondTeamCode())
                && core.game1BlueTeamCode().equals(fixture.game1BlueTeamCode())
                && core.game1RedTeamCode().equals(fixture.game1RedTeamCode())
                && core.seriesFormat() == fixture.seriesFormat()
                && core.fixtureRootSeed() == fixture.fixtureRootSeed()
                && core.scheduleIdentity().equals(season.schedule().scheduleIdentity())
                && core.productDecisionHash().equals(season.productDecisionHash())
                && core.frozenSnapshotIdentity().equals(snapshot.snapshotIdentity())
                && core.firstTeamSnapshotIdentity().equals(
                snapshot.teamSnapshotIdentity(fixture.firstTeamCode()))
                && core.secondTeamSnapshotIdentity().equals(
                snapshot.teamSnapshotIdentity(fixture.secondTeamCode()))
                && core.playerResourceIdentity().equals(snapshot.playerResourceIdentity())
                && core.championDraftResourceIdentity().equals(
                snapshot.championDraftResourceIdentity())
                && core.matchupCompositionResourceIdentity().equals(
                snapshot.matchupCompositionResourceIdentity())
                && core.productionRuntimeIdentity().equals(
                snapshot.productionRuntimeIdentity())
                && core.actualGameCount() == core.orderedGameReceipts().size()
                && core.actualGameCount() == receipt.orderedDraftAuthorityReceipts().size();
        boolean mode = fixture.executionMode() == LeagueFixtureExecutionMode.FULL_AUTO
                ? receipt.playerSeriesBindingHash() == null && playerBinding == null
                : playerBinding != null
                && receipt.playerSeriesBindingHash().equals(playerBinding.bindingHash())
                && playerBinding.fixtureId().equals(fixture.fixtureId())
                && playerBinding.boundSeriesId().equals(fixture.boundSeriesId())
                && playerBinding.scheduleIdentity().equals(season.schedule().scheduleIdentity())
                && playerBinding.frozenSnapshotIdentity().equals(snapshot.snapshotIdentity());
        if (!common || !mode) {
            throw new IllegalArgumentException("DURABLE_LEAGUE_RECEIPT_BINDING_MISMATCH");
        }
        String history = com.lolfm.draft.SeriesDraftHistory.identityHash(0, java.util.Set.of());
        for (int index = 0; index < core.orderedGameReceipts().size(); index++) {
            int number = index + 1;
            LeagueFixtureGameReceiptV1 game = core.orderedGameReceipts().get(index);
            LeagueFixtureDraftAuthorityReceiptV1 authority =
                    receipt.orderedDraftAuthorityReceipts().get(index);
            if (game.gameNumber() != number || authority.gameNumber() != number
                    || authority.executionMode() != fixture.executionMode()
                    || !game.blueTeamCode().equals(fixture.blueTeamCode(number))
                    || !game.redTeamCode().equals(fixture.redTeamCode(number))
                    || game.gameSeed() != fixture.gameSeed(number, history)
                    || !game.historyBeforeHash().equals(history)
                    || !game.resourceProvenanceHash().equals(
                    core.resourceProvenanceHash())) {
                throw new IllegalArgumentException("DURABLE_LEAGUE_GAME_PROOF_MISMATCH");
            }
            history = game.historyAfterHash();
        }
        return restoreVerified(receipt);
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

package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.SimulationProvenanceService;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.draft.AutoDraftSelectionPolicy;
import com.lolfm.draft.DraftDecision;
import com.lolfm.draft.DraftPlanArchetype;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeagueAutomatedSeriesRunnerTest {
    private static final String RESOURCE_HASH = LeagueDomainTestFixtures.hash(
            "production-resource-provenance");

    @Test
    void fullAutoBo3CompletesTwoZeroAndStopsBeforeThirdGame() {
        LeagueSeasonAggregate season = season(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        FakeGameExecutor executor = new FakeGameExecutor(List.of("GEN", "GEN"));
        LeagueAutomatedSeriesRunner runner = runner(season.frozenSnapshot(), executor);

        LeagueAutomatedSeriesRunResult result = runner.run(input(season, fixture));

        assertThat(result.status()).isEqualTo(
                LeagueAutomatedSeriesRunResult.Status.COMPLETED);
        assertThat(result.gameExecutionCount()).isEqualTo(2);
        assertThat(executor.observations).hasSize(2);
        assertThat(result.receipt().winnerTeamCode()).isEqualTo("GEN");
        assertThat(result.receipt().firstTeamGameWins()
                + result.receipt().secondTeamGameWins()).isEqualTo(2);
        assertThat(result.receipt().orderedGameReceipts())
                .extracting(LeagueFixtureGameReceiptV1::gameNumber)
                .containsExactly(1, 2);
        assertSideSeedAndHistory(fixture, result.receipt().orderedGameReceipts());
        assertThat(result.receipt().orderedGameReceipts().get(0).historyBeforePicks())
                .isEmpty();
        assertThat(result.receipt().orderedGameReceipts().get(0).historyAfterPicks())
                .hasSize(10);
        assertThat(result.receipt().orderedGameReceipts().get(1).historyBeforePicks())
                .hasSize(10);
        assertThat(result.receipt().orderedGameReceipts().get(1).historyAfterPicks())
                .hasSize(20);
    }

    @Test
    void fullAutoBo3CompletesTwoOneAndVerifiedCompletionAppliesExactlyOnce() {
        LeagueSeasonAggregate season = season(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        LeagueAutomatedSeriesRunResult result = runner(season.frozenSnapshot(),
                new FakeGameExecutor(List.of("GEN", "T1", "GEN")))
                .run(input(season, fixture));

        assertThat(result.status()).isEqualTo(
                LeagueAutomatedSeriesRunResult.Status.COMPLETED);
        assertThat(result.gameExecutionCount()).isEqualTo(3);
        assertThat(result.receipt().actualGameCount()).isEqualTo(3);
        assertThat(result.receipt().orderedGameReceipts().get(2).historyBeforePicks())
                .hasSize(20);
        assertThat(result.receipt().orderedGameReceipts().get(2).historyAfterPicks())
                .hasSize(30);

        LeagueSeasonAggregate applied = season.applyVerifiedCompletion(
                result.verifiedCompletion());
        LeagueSeasonAggregate replayed = applied.applyVerifiedCompletion(
                result.verifiedCompletion());
        assertThat(applied.revision()).isEqualTo(1);
        assertThat(replayed).isSameAs(applied);
        assertThat(applied.standings().appliedFixtureCount()).isEqualTo(1);
    }

    @Test
    void playerControlledPoolBlockedAndNoDecisivePathsPublishNoCompletion() {
        LeagueSeasonAggregate hybrid = season(LeagueSeasonMode.HYBRID_MANAGER, "GEN");
        LeagueFixture playerFixture = LeagueDomainTestFixtures.fixture(
                hybrid.schedule(), "GEN", "T1");
        FakeGameExecutor playerExecutor = new FakeGameExecutor(List.of("GEN"));
        LeagueAutomatedSeriesRunResult playerResult = runner(
                hybrid.frozenSnapshot(), playerExecutor).run(input(hybrid, playerFixture));
        assertThat(playerResult.status()).isEqualTo(
                LeagueAutomatedSeriesRunResult.Status.BLOCKED);
        assertThat(playerResult.failureReason()).isEqualTo(
                "PLAYER_CONTROLLED_FIXTURE_REJECTED");
        assertThat(playerResult.gameExecutionCount()).isZero();
        assertThat(playerExecutor.canCompleteCalls).isZero();
        assertThat(playerExecutor.observations).isEmpty();

        LeagueSeasonAggregate spectator = season(
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture fullAuto = LeagueDomainTestFixtures.fixture(
                spectator.schedule(), "GEN", "T1");
        FakeGameExecutor exhausted = new FakeGameExecutor(List.of("GEN"));
        exhausted.poolAvailable = false;
        LeagueAutomatedSeriesRunResult poolResult = runner(
                spectator.frozenSnapshot(), exhausted).run(input(spectator, fullAuto));
        assertThat(poolResult.failureReason()).isEqualTo(
                "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED");
        assertThat(poolResult.gameExecutionCount()).isZero();
        assertThat(exhausted.observations).isEmpty();

        FakeGameExecutor noDecisive = new FakeGameExecutor(java.util.Arrays.asList(
                (String) null));
        LeagueAutomatedSeriesRunResult noResult = runner(
                spectator.frozenSnapshot(), noDecisive).run(input(spectator, fullAuto));
        assertThat(noResult.failureReason()).isEqualTo(
                "NO_DECISIVE_MATCH_ENGINE_RESULT");
        assertThat(noResult.gameExecutionCount()).isEqualTo(1);
        assertThat(noResult.receipt()).isNull();
        assertThat(noResult.verifiedCompletion()).isNull();
        assertThat(spectator.revision()).isZero();
        assertThat(spectator.standings().appliedFixtureCount()).isZero();
        assertThat(noDecisive.observations.getFirst().historyBefore()).isEmpty();
    }

    @Test
    void sameFixtureDiagnosticsAndFixtureExecutionOrderAreIsolatedAndExact() {
        LeagueSeasonAggregate season = season(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture genT1 = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        LeagueFixture dkHle = LeagueDomainTestFixtures.fixture(
                season.schedule(), "DK", "HLE");

        LeagueFixtureCompletionReceiptV1 enabled = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(
                input(season, genT1), SimulationInstrumentation.enabled()).receipt();
        LeagueFixtureCompletionReceiptV1 disabled = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(
                input(season, genT1), SimulationInstrumentation.disabled()).receipt();
        assertThat(disabled.canonicalBytes()).isEqualTo(enabled.canonicalBytes());

        LeagueFixtureCompletionReceiptV1 firstA = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(input(season, genT1)).receipt();
        LeagueFixtureCompletionReceiptV1 secondA = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(input(season, dkHle)).receipt();
        LeagueFixtureCompletionReceiptV1 secondB = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(input(season, dkHle)).receipt();
        LeagueFixtureCompletionReceiptV1 firstB = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(input(season, genT1)).receipt();
        assertThat(firstA.canonicalBytes()).isEqualTo(firstB.canonicalBytes());
        assertThat(secondA.canonicalBytes()).isEqualTo(secondB.canonicalBytes());
        assertThat(firstA.orderedGameReceipts().getFirst().historyBeforePicks()).isEmpty();
        assertThat(secondA.orderedGameReceipts().getFirst().historyBeforePicks()).isEmpty();
    }

    @Test
    void frozenIdentityMismatchBlocksBeforePoolOrDraftExecution() {
        LeagueSeasonAggregate season = season(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        FakeGameExecutor executor = new FakeGameExecutor(List.of("GEN", "GEN"));
        LeagueFrozenProductionIdentityProvider drifted = new FixedIdentityProvider(
                LeagueDomainTestFixtures.snapshot(), RESOURCE_HASH) {
            @Override
            public LeagueSeasonFrozenSnapshot currentSnapshot(Set<String> ignored) {
                return new LeagueSeasonFrozenSnapshot(
                        LeagueDomainTestFixtures.snapshot().teamSnapshotIdentities(),
                        LeagueDomainTestFixtures.hash("drifted-player"),
                        LeagueDomainTestFixtures.hash("drifted-draft"),
                        LeagueDomainTestFixtures.hash("drifted-matchup"),
                        LeagueDomainTestFixtures.hash("drifted-runtime"));
            }
        };

        LeagueAutomatedSeriesRunResult result = new LeagueAutomatedSeriesRunner(
                drifted, executor).run(input(season, fixture));

        assertThat(result.failureReason()).isEqualTo(
                "FROZEN_PRODUCTION_IDENTITY_MISMATCH");
        assertThat(result.gameExecutionCount()).isZero();
        assertThat(executor.canCompleteCalls).isZero();
        assertThat(executor.observations).isEmpty();
    }

    @Test
    void verifiedCompletionHasNoPublicConstructorAndFakeReceiptHashIsRejected() {
        assertThat(List.of(VerifiedLeagueFixtureCompletion.class.getDeclaredConstructors()))
                .allSatisfy(constructor -> assertThat(
                        Modifier.isPublic(constructor.getModifiers())).isFalse());

        LeagueSeasonAggregate season = season(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        LeagueFixtureCompletionReceiptV1 valid = runner(season.frozenSnapshot(),
                new FakeGameExecutor(List.of("GEN", "GEN")))
                .run(input(season, fixture)).receipt();
        Object[] fields = recordValues(valid);
        fields[fields.length - 1] = "a".repeat(64);

        assertThatThrownBy(() -> constructRecord(
                LeagueFixtureCompletionReceiptV1.class, fields))
                .hasRootCauseMessage("Canonical fixture receipt hash mismatch");
    }

    @Test
    void verifierRejectsFixtureDraftRuntimeOutputAndCrossBoundaryTampering() {
        LeagueSeasonAggregate season = season(LeagueSeasonMode.SPECTATOR_FULL_AUTO, null);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        LeagueAutomatedSeriesRunnerInput input = input(season, fixture);
        LeagueFixtureCompletionReceiptV1 valid = runner(season.frozenSnapshot(),
                new FakeGameExecutor(List.of("GEN", "GEN"))).run(input).receipt();
        List<LeagueFixtureGameReceiptV1> actual = valid.orderedGameReceipts();

        List<LeagueFixtureCompletionReceiptV1> topLevelTampering = List.of(
                mutateReceipt(valid, Map.of("seasonId", LeagueIdentity.seasonId(
                        LeagueDomainTestFixtures.leagueId(), "tampered-season"))),
                mutateReceipt(valid, Map.of("fixtureId", "fixture_" + hash("other-fixture"))),
                mutateReceipt(valid, Map.of("boundSeriesId", "series_" + hash("other-series"))),
                mutateReceipt(valid, Map.of("executionMode",
                        LeagueFixtureExecutionMode.PLAYER_CONTROLLED)),
                mutateReceipt(valid, Map.of("game1BlueTeamCode", valid.game1RedTeamCode(),
                        "game1RedTeamCode", valid.game1BlueTeamCode())),
                mutateReceipt(valid, Map.of("fixtureRootSeed", valid.fixtureRootSeed() + 1)),
                mutateReceipt(valid, Map.of("scheduleIdentity", hash("other-schedule"))),
                mutateReceipt(valid, Map.of("productDecisionHash", hash("other-product"))),
                mutateReceipt(valid, Map.of("frozenSnapshotIdentity", hash("other-snapshot"))),
                mutateReceipt(valid, Map.of("firstTeamSnapshotIdentity", hash("other-team"))),
                mutateReceipt(valid, Map.of("playerResourceIdentity", hash("other-player"))),
                mutateReceipt(valid, Map.of("championDraftResourceIdentity", hash("other-draft"))),
                mutateReceipt(valid, Map.of("matchupCompositionResourceIdentity",
                        hash("other-matchup"))),
                mutateReceipt(valid, Map.of("productionRuntimeIdentity", hash("other-runtime"))),
                mutateReceipt(valid, Map.of("resourceProvenanceHash", hash("other-resource"))));
        topLevelTampering.forEach(receipt -> assertVerificationRejected(
                input, season.frozenSnapshot(), actual, receipt));

        LeagueFixtureGameReceiptV1 game = actual.getFirst();
        assertThatThrownBy(() -> mutateGame(game, Map.of(
                "draftDecisionHash", hash("other-decision"))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mutateGame(game, Map.of(
                "blueTeamCode", game.redTeamCode(),
                "redTeamCode", game.blueTeamCode())))
                .isInstanceOf(RuntimeException.class);
        ArrayList<ChampionId> invalidHistory = new ArrayList<>(game.historyAfterPicks());
        invalidHistory.set(0, new ChampionId("tampered-history-pick"));
        assertThatThrownBy(() -> mutateGame(game, Map.of(
                "historyAfterPicks", List.copyOf(invalidHistory),
                "historyAfterHash", SeriesDraftHistory.identityHash(
                        game.gameNumber(), Set.copyOf(invalidHistory)))))
                .isInstanceOf(RuntimeException.class);
        List<LeagueFixtureGameReceiptV1> gameTampering = List.of(
                mutateGame(game, Map.of("gameSeed", game.gameSeed() + 1)),
                mutateGame(game, Map.of("finalDraftHash", hash("other-final-draft"))),
                mutateGame(game, Map.of("finalAssignmentHash", hash("other-assignment"))),
                mutateGame(game, Map.of("rosterIdentityHash", hash("other-roster"))),
                mutateGame(game, Map.of("policyHash", hash("other-policy"))),
                mutateGame(game, Map.of("runtimeProfileId", "OTHER_PROFILE")),
                mutateGame(game, Map.of("configurationHash", hash("other-configuration"))),
                mutateGame(game, Map.of("engineImplementationVersion", "OTHER_ENGINE")),
                mutateGame(game, Map.of("activeGameplayRulesVersion", "OTHER_RULES")),
                mutateGame(game, Map.of("resourceProvenanceHash", hash("other-game-resource"))),
                mutateGame(game, Map.of("inputHash", hash("other-input"))),
                mutateGame(game, Map.of("replayProvenanceHash", hash("other-replay"))),
                mutateGame(game, Map.of("simulatorTimelineHash", hash("other-timeline"))),
                mutateGame(game, Map.of("structuredTimelineHash", hash("other-structured"))),
                mutateGame(game, Map.of("outputHash", hash("other-output"))),
                mutateGame(game, Map.of("randomTraceHash", hash("other-random"))));
        gameTampering.forEach(tamperedGame -> {
            ArrayList<LeagueFixtureGameReceiptV1> tamperedGames = new ArrayList<>(actual);
            tamperedGames.set(0, tamperedGame);
            LeagueFixtureCompletionReceiptV1 tamperedReceipt = mutateReceipt(
                    valid, Map.of("orderedGameReceipts", List.copyOf(tamperedGames)));
            assertVerificationRejected(input, season.frozenSnapshot(), actual,
                    tamperedReceipt);
        });

        assertThatThrownBy(() -> mutateReceipt(valid, Map.of(
                "orderedGameReceipts", List.of(actual.getFirst()))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mutateReceipt(valid, Map.of(
                "orderedGameReceipts", List.of(actual.getFirst(), actual.getFirst()))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mutateReceipt(valid, Map.of(
                "orderedGameReceipts", actual.reversed())))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mutateReceipt(valid, Map.of(
                "firstTeamGameWins", valid.firstTeamGameWins() + 1)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mutateReceipt(valid, Map.of(
                "winnerTeamCode", valid.loserTeamCode(),
                "loserTeamCode", valid.winnerTeamCode())))
                .isInstanceOf(RuntimeException.class);

        LeagueFixture otherFixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "DK", "HLE");
        LeagueAutomatedSeriesRunResult other = runner(season.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(input(season, otherFixture));
        assertVerificationRejected(input, season.frozenSnapshot(),
                other.receipt().orderedGameReceipts(), other.receipt());

        LeagueSeasonAggregate otherSeason = LeagueSeasonAggregate.create(
                LeagueIdentity.seasonId(LeagueDomainTestFixtures.leagueId(), "cross-season"),
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, null,
                season.frozenSnapshot(), LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
        LeagueFixture otherSeasonFixture = LeagueDomainTestFixtures.fixture(
                otherSeason.schedule(), "GEN", "T1");
        LeagueAutomatedSeriesRunResult crossSeason = runner(otherSeason.frozenSnapshot(),
                new FixedFirstTeamWinnerExecutor()).run(
                input(otherSeason, otherSeasonFixture));
        assertVerificationRejected(input, season.frozenSnapshot(),
                crossSeason.receipt().orderedGameReceipts(), crossSeason.receipt());
    }

    private static void assertSideSeedAndHistory(
            LeagueFixture fixture,
            List<LeagueFixtureGameReceiptV1> games
    ) {
        String historyHash = SeriesDraftHistory.identityHash(0, Set.of());
        for (int index = 0; index < games.size(); index++) {
            int number = index + 1;
            LeagueFixtureGameReceiptV1 game = games.get(index);
            assertThat(game.blueTeamCode()).isEqualTo(fixture.blueTeamCode(number));
            assertThat(game.redTeamCode()).isEqualTo(fixture.redTeamCode(number));
            assertThat(game.historyBeforeHash()).isEqualTo(historyHash);
            assertThat(game.gameSeed()).isEqualTo(fixture.gameSeed(number, historyHash));
            historyHash = game.historyAfterHash();
        }
    }

    private static LeagueSeasonAggregate season(
            LeagueSeasonMode mode,
            String managedTeam
    ) {
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        return LeagueSeasonAggregate.create(LeagueDomainTestFixtures.seasonId(), mode,
                managedTeam,
                managedTeam == null ? null : snapshot.teamSnapshotIdentity(managedTeam),
                snapshot, LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }

    private static LeagueAutomatedSeriesRunnerInput input(
            LeagueSeasonAggregate season,
            LeagueFixture fixture
    ) {
        return new LeagueAutomatedSeriesRunnerInput(season, fixture,
                LeagueV1ProductDecisions.productDecisionHash());
    }

    private static LeagueAutomatedSeriesRunner runner(
            LeagueSeasonFrozenSnapshot snapshot,
            LeagueAutomatedSeriesGameExecutor executor
    ) {
        return new LeagueAutomatedSeriesRunner(
                new FixedIdentityProvider(snapshot, RESOURCE_HASH), executor);
    }

    private static class FixedIdentityProvider
            implements LeagueFrozenProductionIdentityProvider {
        private final LeagueSeasonFrozenSnapshot snapshot;
        private final String resourceHash;

        FixedIdentityProvider(LeagueSeasonFrozenSnapshot snapshot, String resourceHash) {
            this.snapshot = snapshot;
            this.resourceHash = resourceHash;
        }

        @Override
        public LeagueSeasonFrozenSnapshot currentSnapshot(Set<String> expectedTeamCodes) {
            assertThat(expectedTeamCodes).isEqualTo(snapshot.teamSnapshotIdentities().keySet());
            return snapshot;
        }

        @Override
        public String currentResourceProvenanceHash() { return resourceHash; }
    }

    private static class FixedFirstTeamWinnerExecutor extends FakeGameExecutor {
        FixedFirstTeamWinnerExecutor() { super(List.of()); }

        @Override
        String winner(LeagueAutomatedSeriesGameExecutor.Request request) {
            return request.fixture().firstTeamCode();
        }
    }

    private static class FakeGameExecutor implements LeagueAutomatedSeriesGameExecutor {
        private final List<String> winners;
        private final List<Observation> observations = new ArrayList<>();
        private boolean poolAvailable = true;
        private int canCompleteCalls;

        FakeGameExecutor(List<String> winners) { this.winners = winners; }

        @Override
        public boolean canComplete(SeriesDraftHistory history) {
            canCompleteCalls++;
            return poolAvailable;
        }

        @Override
        public Execution execute(Request request) {
            observations.add(new Observation(request.gameNumber(), request.blueTeamCode(),
                    request.redTeamCode(), request.gameSeed(), request.history().identityHash(),
                    Set.copyOf(request.history().consumedPicks()),
                    request.instrumentation().diagnosticsEnabled()));
            String winner = winner(request);
            FinalDraftResult draft = draft(request.gameNumber(),
                    request.history().consumedPicks());
            HashSet<ChampionId> after = new HashSet<>(request.history().consumedPicks());
            after.addAll(draft.bluePicks());
            after.addAll(draft.redPicks());
            LeagueFixtureGameReceiptV1 receipt = gameReceipt(request, draft,
                    after.stream().sorted(java.util.Comparator.comparing(ChampionId::value))
                            .toList(), winner);
            return new Execution(draft, receipt);
        }

        String winner(Request request) {
            return winners.get(request.gameNumber() - 1);
        }
    }

    private static FinalDraftResult draft(int game, Set<ChampionId> historyBefore) {
        DraftRuleSet rules = DraftRuleSet.professional();
        List<ChampionId> bluePicks = champions("g" + game + "-blue-pick", 5);
        List<ChampionId> redPicks = champions("g" + game + "-red-pick", 5);
        List<ChampionId> blueBans = champions("g" + game + "-blue-ban", 5);
        List<ChampionId> redBans = champions("g" + game + "-red-ban", 5);
        int blueBan = 0;
        int redBan = 0;
        int bluePick = 0;
        int redPick = 0;
        List<DraftDecision> decisions = new ArrayList<>();
        for (int index = 0; index < rules.turns().size(); index++) {
            var turn = rules.turns().get(index);
            ChampionId selected;
            if (turn.actionType() == com.lolfm.draft.DraftActionType.BAN) {
                selected = turn.side() == TeamSide.BLUE
                        ? blueBans.get(blueBan++) : redBans.get(redBan++);
            } else {
                selected = turn.side() == TeamSide.BLUE
                        ? bluePicks.get(bluePick++) : redPicks.get(redPick++);
            }
            decisions.add(new DraftDecision(turn.number(), turn.side(), turn.actionType(),
                    selected,
                    0, 0, 0, Map.of(), DraftPlanArchetype.FRONT_TO_BACK,
                    0, List.of()));
        }
        return new FinalDraftResult(rules, blueBans, redBans, bluePicks, redPicks,
                decisions, Map.of(), Map.of(), null, null, null, null, null,
                historyBefore, AutoDraftSelectionPolicy.POLICY_ID,
                AutoDraftSelectionPolicy.APPROVED_POLICY_SHA256, List.of(),
                "test-meta-v1", hash("legal-role"), hash("legal-role"));
    }

    private static LeagueFixtureGameReceiptV1 gameReceipt(
            LeagueAutomatedSeriesGameExecutor.Request request,
            FinalDraftResult draft,
            List<ChampionId> historyAfter,
            String winnerTeam
    ) {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        TeamSide winnerSide = winnerTeam == null ? null
                : winnerTeam.equals(request.blueTeamCode()) ? TeamSide.BLUE : TeamSide.RED;
        List<LeagueFixtureGameReceiptV1.DraftTurnEvidence> decisions =
                draft.decisions().stream().map(value ->
                        new LeagueFixtureGameReceiptV1.DraftTurnEvidence(
                                value.turn(), value.side(), value.actionType(),
                                value.selectedChampionId())).toList();
        ArrayList<LeagueFixtureGameReceiptV1.FinalAssignmentEvidence> assignments =
                new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            for (Position position : Position.values()) {
                int ordinal = side.ordinal() * Position.values().length + position.ordinal();
                ChampionId champion = side == TeamSide.BLUE
                        ? draft.bluePicks().get(position.ordinal())
                        : draft.redPicks().get(position.ordinal());
                assignments.add(new LeagueFixtureGameReceiptV1.FinalAssignmentEvidence(
                        side, position, new PlayerId("player-test-" + (ordinal + 1)),
                        champion));
            }
        }
        String gameHash = hash("game=" + request.fixture().fixtureId()
                + ":" + request.gameNumber());
        return new LeagueFixtureGameReceiptV1(
                LeagueFixtureGameReceiptV1.SCHEMA, request.matchIdentity(),
                request.gameNumber(), request.blueTeamCode(), request.redTeamCode(),
                request.gameSeed(), request.history().identityHash(),
                SeriesDraftHistory.identityHash(request.gameNumber(), Set.copyOf(historyAfter)),
                request.history().consumedPicks().stream()
                        .sorted(java.util.Comparator.comparing(ChampionId::value)).toList(),
                historyAfter, rulesIdentity(), hash("draft-rules"),
                hash("draft-scoring"), AutoDraftSelectionPolicy.POLICY_ID,
                AutoDraftSelectionPolicy.APPROVED_POLICY_SHA256,
                hash("draft-trace-" + request.gameNumber()), draft.draftIdentity(),
                decisions, draft.blueBans(), draft.redBans(), draft.bluePicks(),
                draft.redPicks(), draft.draftMetaVersion(),
                draft.requiredLegalRoleKeyHash(), draft.actualLegalRoleKeyHash(),
                hash("final-draft-" + request.gameNumber()),
                hash("assignment-" + request.gameNumber()), assignments,
                hash("roster=" + request.blueTeamCode() + ":" + request.redTeamCode()),
                policy.policyId(), policy.policyHash(),
                policy.retainedRuntimeProfileId().name(), policy.configurationHash(),
                policy.engineImplementationVersion(), policy.activeGameplayRulesVersion(),
                RESOURCE_HASH, hash("input-" + gameHash),
                hash("replay-" + gameHash),
                SimulationProvenanceService.MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM,
                hash("simulator-timeline-" + gameHash),
                hash("structured-timeline-" + gameHash),
                MatchEngineV1Canonicalizer.HASH_ALGORITHM, hash("output-" + gameHash),
                MatchEngineV1Canonicalizer.HASH_ALGORITHM,
                MatchEngineV1Output.OUTPUT_HASH_SCOPE,
                SimulationRandomFingerprint.SCHEMA, 100 + request.gameNumber(),
                hash("random-" + gameHash),
                SimulationRandomFingerprint.TRACE_HASH_ALGORITHM,
                winnerSide, winnerTeam, 1_800, winnerTeam == null
                ? GameEndReason.SIMULATION_TIMEOUT : GameEndReason.NEXUS_DESTROYED);
    }

    private static List<ChampionId> champions(String prefix, int count) {
        ArrayList<ChampionId> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new ChampionId(prefix + "-" + (index + 1)));
        }
        return List.copyOf(result);
    }

    private static String rulesIdentity() {
        return DraftRuleSet.professional().identity();
    }

    private static String hash(String value) {
        return LeagueDomainTestFixtures.hash(value);
    }

    private static Object[] recordValues(Object record) {
        try {
            var components = record.getClass().getRecordComponents();
            Object[] values = new Object[components.length];
            for (int index = 0; index < components.length; index++) {
                values[index] = components[index].getAccessor().invoke(record);
            }
            return values;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static LeagueFixtureCompletionReceiptV1 mutateReceipt(
            LeagueFixtureCompletionReceiptV1 receipt,
            Map<String, Object> replacements
    ) {
        LinkedHashMap<String, Object> values = recordMap(receipt);
        values.putAll(replacements);
        values.put("canonicalFixtureReceiptHash", null);
        return constructRecord(LeagueFixtureCompletionReceiptV1.class,
                values.values().toArray());
    }

    private static LeagueFixtureGameReceiptV1 mutateGame(
            LeagueFixtureGameReceiptV1 receipt,
            Map<String, Object> replacements
    ) {
        LinkedHashMap<String, Object> values = recordMap(receipt);
        values.putAll(replacements);
        return constructRecord(LeagueFixtureGameReceiptV1.class,
                values.values().toArray());
    }

    private static LinkedHashMap<String, Object> recordMap(Object record) {
        Object[] values = recordValues(record);
        var components = record.getClass().getRecordComponents();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < components.length; index++) {
            result.put(components[index].getName(), values[index]);
        }
        return result;
    }

    private static void assertVerificationRejected(
            LeagueAutomatedSeriesRunnerInput input,
            LeagueSeasonFrozenSnapshot snapshot,
            List<LeagueFixtureGameReceiptV1> actual,
            LeagueFixtureCompletionReceiptV1 receipt
    ) {
        assertThatThrownBy(() -> VerifiedLeagueFixtureCompletion.verifyAutomated(
                input, snapshot, RESOURCE_HASH, actual, receipt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T constructRecord(Class<T> type, Object[] values) {
        try {
            Class<?>[] types = java.util.Arrays.stream(type.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
            return (T) type.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }

    private record Observation(
            int gameNumber,
            String blue,
            String red,
            long seed,
            String historyBeforeHash,
            Set<ChampionId> historyBefore,
            boolean diagnosticsEnabled
    ) { }
}

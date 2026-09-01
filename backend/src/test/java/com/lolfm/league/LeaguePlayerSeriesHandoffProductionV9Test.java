package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.PlayerDraftSessionStatus;
import com.lolfm.application.SeriesApiV1Facade;
import com.lolfm.application.SeriesGameStatus;
import com.lolfm.application.SeriesStatus;
import com.lolfm.champion.ChampionId;
import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class LeaguePlayerSeriesHandoffProductionV9Test {
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired LeaguePlayerSeriesHandoffService handoff;
    @Autowired LeaguePlayerSeriesKernelPort kernel;
    @Autowired SeriesApiV1Facade series;
    @Autowired LeagueAutomatedSeriesRunner automated;

    @Test
    void managedFixtureCompletesThroughPlayerDraftV9AndUnifiedReceiptExactlyOnce() {
        LeagueSeasonAggregate season = productionHybridSeason(snapshots);
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "T1");
        var started = handoff.startOrResume(
                new LeaguePlayerSeriesHandoffService.StartCommand(
                        season.leagueId(), season, fixture.fixtureId(), season.revision(),
                        "production-player-start"));

        assertThat(started.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.STARTED);
        LeagueFixtureSeriesBindingV1 binding = started.bindingState().binding();
        var view = series.get(binding.boundSeriesId());
        assertThat(view.seriesId()).isEqualTo(fixture.boundSeriesId());
        assertThat(view.rootSeed()).isEqualTo(Long.toString(fixture.fixtureRootSeed()));
        assertThat(view.seedDerivationAlgorithm()).isEqualTo(LeagueIdentity.GAME_SEED_ALGORITHM);
        assertThat(view.allowedCommands()).doesNotContain("CANCEL_SERIES");

        int action = 0;
        int committed = 0;
        while (view.status() == SeriesStatus.ACTIVE) {
            int gameNumber = view.currentGameNumber();
            assertThat(Long.parseLong(view.currentGameSeed())).isEqualTo(
                    fixture.gameSeed(gameNumber, view.seriesHistoryBeforeHash()));
            var draft = series.createDraft(view.seriesId(),
                    new SeriesApiV1Dtos.DraftCreateRequest(
                            SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,
                            view.revision(), "league-draft-" + gameNumber));
            view = draft.series();
            var child = draft.draftSession().session();
            while (child.status() == PlayerDraftSessionStatus.ACTIVE) {
                String champion = child.selectableChampions().getFirst()
                        .champion().championId();
                var selected = series.draftAction(view.seriesId(), gameNumber,
                        new SeriesApiV1Dtos.DraftActionRequest(
                                SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA,
                                view.revision(), child.revision(),
                                "league-action-" + action++, champion));
                view = selected.series();
                child = selected.draftSession().session();
            }
            assertThat(child.decisions()).hasSize(20);
            assertThat(child.completedDraft().finalAssignments()).hasSize(10);
            var simulated = series.simulate(view.seriesId(), gameNumber,
                    new SeriesApiV1Dtos.SimulateRequest(
                            SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                            view.revision(), child.revision(),
                            "league-simulate-" + gameNumber));
            assertThat(simulated.accepted()).isFalse();
            assertThat(simulated.response().game().status())
                    .isEqualTo(SeriesGameStatus.COMMITTED);
            assertThat(simulated.response().match().integrity().runtimeProfileId())
                    .isEqualTo("PRODUCTION_MATCHUP_COMPOSITION_V1");
            assertThat(simulated.response().match().integrity().engineImplementationVersion())
                    .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
            view = simulated.response().series();
            committed++;
            assertThat(view.excludedChampionIds()).hasSize(committed * 10);
            if (view.status() == SeriesStatus.ACTIVE) {
                assertThat(view.currentGameNumber()).isEqualTo(committed + 1);
            }
        }
        assertThat(view.status()).isEqualTo(SeriesStatus.COMPLETED);
        assertThat(view.score().get(view.winnerTeamCode())).isEqualTo(2);
        assertThat(committed).isBetween(2, 3);

        var enabledEvidence = kernel.completedEvidence(
                binding, SimulationInstrumentation.enabled());
        var disabledEvidence = kernel.completedEvidence(
                binding, SimulationInstrumentation.disabled());
        assertThat(gameCanonical(disabledEvidence)).isEqualTo(
                gameCanonical(enabledEvidence));

        LeagueFixture unrelated = LeagueDomainTestFixtures.fixture(
                season.schedule(), "DK", "HLE");
        LeagueAutomatedSeriesRunResult unrelatedResult = automated.run(
                new LeagueAutomatedSeriesRunnerInput(season, unrelated,
                        LeagueV1ProductDecisions.productDecisionHash()),
                SimulationInstrumentation.disabled());
        LeagueSeasonAggregate completionSeason = season.applyVerifiedCompletion(
                unrelatedResult.verifiedCompletion());
        assertThat(completionSeason.revision()).isOne();
        assertThat(completionSeason.standings().appliedFixtureCount()).isOne();

        var completed = handoff.complete(
                new LeaguePlayerSeriesHandoffService.CompletionCommand(
                        completionSeason.leagueId(), completionSeason, fixture.fixtureId(),
                        binding.bindingHash()), SimulationInstrumentation.enabled());
        assertThat(completed.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.CompletionStatus.VERIFIED);
        assertThat(completed.replayed()).isFalse();
        assertThat(completed.gameEngineExecutionCount()).isEqualTo(committed);
        assertThat(completed.receipt().leagueId()).isEqualTo(completionSeason.leagueId());
        assertThat(completed.receipt().playerSeriesBindingHash())
                .isEqualTo(binding.bindingHash());
        assertThat(completed.receipt().executionMode())
                .isEqualTo(LeagueFixtureExecutionMode.PLAYER_CONTROLLED);
        assertThat(completed.receipt().orderedGameReceipts()).hasSize(committed)
                .allSatisfy(game -> {
                    assertThat(game.orderedDraftDecisions()).hasSize(20);
                    assertThat(game.orderedFinalAssignments()).hasSize(10);
                    assertThat(game.policyId()).isEqualTo(MatchEngineV1Policy.POLICY_ID);
                    assertThat(game.runtimeProfileId())
                            .isEqualTo("PRODUCTION_MATCHUP_COMPOSITION_V1");
                    assertThat(game.engineImplementationVersion())
                            .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
                });
        assertThat(completed.receipt().orderedDraftAuthorityReceipts())
                .allSatisfy(authority -> {
                    assertThat(authority.executionMode())
                            .isEqualTo(LeagueFixtureExecutionMode.PLAYER_CONTROLLED);
                    assertThat(authority.controlledSide()).isNotNull();
                    assertThat(authority.controlEvidenceHash()).matches("[0-9a-f]{64}");
                });

        LeagueFixtureCompletionReceiptV2 wrongBinding =
                new LeagueFixtureCompletionReceiptV2(
                        LeagueFixtureCompletionReceiptV2.SCHEMA,
                        LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                        completionSeason.leagueId(), "0".repeat(64),
                        completed.receipt().fixtureReceipt(),
                        completed.receipt().orderedDraftAuthorityReceipts(), null);
        assertPlayerVerificationRejected(completionSeason, fixture, binding, enabledEvidence,
                wrongBinding);
        List<Map<String, Object>> coreIdentityTampering = List.of(
                Map.of("seasonId", LeagueIdentity.seasonId(
                        LeagueIdentity.leagueId("cross-league"), "cross-season")),
                Map.of("fixtureId", "fixture_" + "1".repeat(64)),
                Map.of("boundSeriesId", "series_" + "1".repeat(64)),
                Map.of("game1BlueTeamCode", fixture.game1RedTeamCode(),
                        "game1RedTeamCode", fixture.game1BlueTeamCode()),
                Map.of("fixtureRootSeed", fixture.fixtureRootSeed() + 1),
                Map.of("gameSeedAlgorithm", "TAMPERED_GAME_SEED_ALGORITHM"),
                Map.of("scheduleIdentity", "3".repeat(64)),
                Map.of("productDecisionHash", "3".repeat(64)),
                Map.of("frozenSnapshotIdentity", "3".repeat(64)),
                Map.of("firstTeamSnapshotIdentity", "3".repeat(64)),
                Map.of("playerResourceIdentity", "3".repeat(64)),
                Map.of("championDraftResourceIdentity", "3".repeat(64)),
                Map.of("matchupCompositionResourceIdentity", "3".repeat(64)),
                Map.of("productionRuntimeIdentity", "3".repeat(64)),
                Map.of("resourceProvenanceHash", "3".repeat(64)));
        coreIdentityTampering.forEach(replacements -> {
            LinkedHashMap<String, Object> changedFields = new LinkedHashMap<>(replacements);
            changedFields.put("canonicalFixtureReceiptHash", null);
            LeagueFixtureCompletionReceiptV1 changedCore = mutateRecord(
                    completed.receipt().fixtureReceipt(), changedFields);
            LeagueFixtureCompletionReceiptV2 changedReceipt =
                    new LeagueFixtureCompletionReceiptV2(
                            LeagueFixtureCompletionReceiptV2.SCHEMA,
                            LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                            completionSeason.leagueId(), binding.bindingHash(), changedCore,
                            completed.receipt().orderedDraftAuthorityReceipts(), null);
            assertPlayerVerificationRejected(completionSeason, fixture, binding,
                    enabledEvidence,
                    changedReceipt);
        });
        LeagueFixtureDraftAuthorityReceiptV1 firstAuthority =
                completed.receipt().orderedDraftAuthorityReceipts().getFirst();
        ArrayList<LeagueFixtureDraftAuthorityReceiptV1> changedAuthorities =
                new ArrayList<>(completed.receipt().orderedDraftAuthorityReceipts());
        changedAuthorities.set(0, LeagueFixtureDraftAuthorityReceiptV1.player(
                firstAuthority.gameNumber(), firstAuthority.controlledSide().opposite(),
                firstAuthority.controlPolicyId(), firstAuthority.controlPolicyHash(),
                firstAuthority.controlEvidenceHash()));
        LeagueFixtureCompletionReceiptV2 authorityTampered =
                new LeagueFixtureCompletionReceiptV2(
                        LeagueFixtureCompletionReceiptV2.SCHEMA,
                        LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                        completionSeason.leagueId(), binding.bindingHash(),
                        completed.receipt().fixtureReceipt(), changedAuthorities, null);
        assertPlayerVerificationRejected(completionSeason, fixture, binding,
                enabledEvidence,
                authorityTampered);
        LeagueFixtureCompletionReceiptV2 crossLeague =
                new LeagueFixtureCompletionReceiptV2(
                LeagueFixtureCompletionReceiptV2.SCHEMA,
                LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                LeagueIdentity.leagueId("cross-league"), binding.bindingHash(),
                completed.receipt().fixtureReceipt(),
                completed.receipt().orderedDraftAuthorityReceipts(), null);
        assertPlayerVerificationRejected(completionSeason, fixture, binding,
                enabledEvidence,
                crossLeague);
        LeagueFixtureGameReceiptV1 outputTamperedGame = mutateRecord(
                completed.receipt().orderedGameReceipts().getFirst(),
                Map.of("outputHash", "1".repeat(64)));
        ArrayList<LeagueFixtureGameReceiptV1> outputTamperedGames = new ArrayList<>(
                completed.receipt().orderedGameReceipts());
        outputTamperedGames.set(0, outputTamperedGame);
        LinkedHashMap<String, Object> outputTamperedFields = new LinkedHashMap<>();
        outputTamperedFields.put("orderedGameReceipts", List.copyOf(outputTamperedGames));
        outputTamperedFields.put("canonicalFixtureReceiptHash", null);
        LeagueFixtureCompletionReceiptV1 outputTamperedCore = mutateRecord(
                completed.receipt().fixtureReceipt(), outputTamperedFields);
        LeagueFixtureCompletionReceiptV2 outputTampered =
                new LeagueFixtureCompletionReceiptV2(
                        LeagueFixtureCompletionReceiptV2.SCHEMA,
                        LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                        completionSeason.leagueId(), binding.bindingHash(),
                        outputTamperedCore,
                        completed.receipt().orderedDraftAuthorityReceipts(), null);
        assertPlayerVerificationRejected(completionSeason, fixture, binding,
                enabledEvidence,
                outputTampered);
        Map<String, Object> gameTamper = new LinkedHashMap<>();
        gameTamper.put("gameSeed", completed.receipt().orderedGameReceipts()
                .getFirst().gameSeed() + 1);
        gameTamper.put("historyBeforeHash", "2".repeat(64));
        gameTamper.put("draftDecisionHash", "2".repeat(64));
        gameTamper.put("finalAssignmentHash", "2".repeat(64));
        gameTamper.put("inputHash", "2".repeat(64));
        gameTamper.put("replayProvenanceHash", "2".repeat(64));
        gameTamper.put("simulatorTimelineHash", "2".repeat(64));
        gameTamper.put("structuredTimelineHash", "2".repeat(64));
        gameTamper.put("randomTraceHash", "2".repeat(64));
        gameTamper.forEach((field, replacement) -> assertThatThrownBy(() -> {
            LeagueFixtureGameReceiptV1 changed = mutateRecord(
                    completed.receipt().orderedGameReceipts().getFirst(),
                    Map.of(field, replacement));
            ArrayList<LeagueFixtureGameReceiptV1> changedGames = new ArrayList<>(
                    completed.receipt().orderedGameReceipts());
            changedGames.set(0, changed);
            LinkedHashMap<String, Object> coreFields = new LinkedHashMap<>();
            coreFields.put("orderedGameReceipts", List.copyOf(changedGames));
            coreFields.put("canonicalFixtureReceiptHash", null);
            LeagueFixtureCompletionReceiptV1 changedCore = mutateRecord(
                    completed.receipt().fixtureReceipt(), coreFields);
            LeagueFixtureCompletionReceiptV2 changedReceipt =
                    new LeagueFixtureCompletionReceiptV2(
                            LeagueFixtureCompletionReceiptV2.SCHEMA,
                            LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                            completionSeason.leagueId(), binding.bindingHash(), changedCore,
                            completed.receipt().orderedDraftAuthorityReceipts(), null);
            VerifiedLeagueFixtureCompletion.verifyPlayer(
                    completionSeason, fixture, binding,
                    completionSeason.frozenSnapshot(),
                    snapshots.currentResourceProvenanceHash(), enabledEvidence,
                    gameReceipts(enabledEvidence), authorityReceipts(enabledEvidence),
                    changedReceipt);
        }).as(field).isInstanceOf(RuntimeException.class));
        assertThatThrownBy(() -> {
            LinkedHashMap<String, Object> reordered = new LinkedHashMap<>();
            reordered.put("orderedGameReceipts",
                    completed.receipt().orderedGameReceipts().reversed());
            reordered.put("canonicalFixtureReceiptHash", null);
            mutateRecord(completed.receipt().fixtureReceipt(), reordered);
        })
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> {
            ArrayList<LeagueFixtureGameReceiptV1> omitted = new ArrayList<>(
                    completed.receipt().orderedGameReceipts());
            omitted.removeLast();
            LinkedHashMap<String, Object> changed = new LinkedHashMap<>();
            changed.put("orderedGameReceipts", omitted);
            changed.put("actualGameCount", omitted.size());
            changed.put("canonicalFixtureReceiptHash", null);
            mutateRecord(completed.receipt().fixtureReceipt(), changed);
        }).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> {
            ArrayList<LeagueFixtureGameReceiptV1> duplicated = new ArrayList<>(
                    completed.receipt().orderedGameReceipts());
            duplicated.add(completed.receipt().orderedGameReceipts().getLast());
            LinkedHashMap<String, Object> changed = new LinkedHashMap<>();
            changed.put("orderedGameReceipts", duplicated);
            changed.put("actualGameCount", duplicated.size());
            changed.put("canonicalFixtureReceiptHash", null);
            mutateRecord(completed.receipt().fixtureReceipt(), changed);
        }).isInstanceOf(RuntimeException.class);

        LeagueSeasonAggregate applied = completionSeason.applyVerifiedCompletion(
                completed.verifiedCompletion());
        LeagueSeasonAggregate replayedStandings = applied.applyVerifiedCompletion(
                completed.verifiedCompletion());
        assertThat(applied.revision()).isEqualTo(2);
        assertThat(replayedStandings).isSameAs(applied);
        assertThat(applied.standings().appliedFixtureCount()).isEqualTo(2);

        var duplicate = handoff.complete(
                new LeaguePlayerSeriesHandoffService.CompletionCommand(
                        applied.leagueId(), applied, fixture.fixtureId(),
                        binding.bindingHash()), SimulationInstrumentation.disabled());
        assertThat(duplicate.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.CompletionStatus.VERIFIED);
        assertThat(duplicate.replayed()).isTrue();
        assertThat(duplicate.gameEngineExecutionCount()).isZero();
        assertThat(duplicate.receipt().canonicalBytes())
                .isEqualTo(completed.receipt().canonicalBytes());
    }

    @Test
    void leagueBoundDraftCancelRequiresRestartAndNeverChangesStandingsOrHistory() {
        LeagueSeasonAggregate season = productionHybridSeason(snapshots, "cancel");
        LeagueFixture fixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "GEN", "DK");
        var started = handoff.startOrResume(
                new LeaguePlayerSeriesHandoffService.StartCommand(
                        season.leagueId(), season, fixture.fixtureId(), season.revision(),
                        "cancel-player-start"));
        var draft = series.createDraft(fixture.boundSeriesId(),
                new SeriesApiV1Dtos.DraftCreateRequest(
                        SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA,
                        started.seriesReference().revision(), "cancel-player-draft"));

        series.cancelDraft(fixture.boundSeriesId(), 1,
                new SeriesApiV1Dtos.DraftCancelRequest(
                        SeriesApiV1Dtos.DRAFT_CANCEL_REQUEST_SCHEMA,
                        draft.series().revision(), "cancel-player-child"));
        var blocked = series.get(fixture.boundSeriesId());
        var resumed = handoff.startOrResume(
                new LeaguePlayerSeriesHandoffService.StartCommand(
                        season.leagueId(), season, fixture.fixtureId(), season.revision(),
                        "cancel-player-resume"));

        assertThat(blocked.status()).isEqualTo(SeriesStatus.BLOCKED);
        assertThat(blocked.terminalReason()).isEqualTo("PLAYER_SERIES_RESTART_REQUIRED");
        assertThat(blocked.score()).containsValues(0, 0);
        assertThat(blocked.excludedChampionIds()).isEmpty();
        assertThat(resumed.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.PLAYER_SERIES_RESTART_REQUIRED);
        assertThat(season.revision()).isZero();
        assertThat(season.standings().appliedFixtureCount()).isZero();
    }

    static LeagueSeasonAggregate productionHybridSeason(
            LeagueProductionSnapshotProvider snapshots
    ) {
        return productionHybridSeason(snapshots, "main");
    }

    private static LeagueSeasonAggregate productionHybridSeason(
            LeagueProductionSnapshotProvider snapshots,
            String key
    ) {
        LeagueSeasonFrozenSnapshot snapshot = snapshots.currentSnapshot(
                Set.copyOf(LeagueDomainTestFixtures.TEAM_CODES));
        String leagueId = LeagueIdentity.leagueId(
                "production-player-handoff-test-league-" + key);
        String seasonId = LeagueIdentity.seasonId(
                leagueId, "production-player-handoff-test-season-" + key);
        return LeagueSeasonAggregate.create(leagueId, seasonId,
                LeagueSeasonMode.HYBRID_MANAGER, "GEN",
                snapshot.teamSnapshotIdentity("GEN"), snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }

    private static List<String> gameCanonical(
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence
    ) {
        LinkedHashSet<ChampionId> history = new LinkedHashSet<>();
        ArrayList<String> values = new ArrayList<>();
        evidence.orderedGames().forEach(game -> {
            history.addAll(game.completedDraft().bluePicks());
            history.addAll(game.completedDraft().redPicks());
            List<ChampionId> after = history.stream()
                    .sorted(java.util.Comparator.comparing(ChampionId::value)).toList();
            values.add(LeagueFixtureGameReceiptV1.from(
                    game.verifiedInput(), game.verifiedOutput(), after).canonicalText());
        });
        return values;
    }

    private void assertPlayerVerificationRejected(
            LeagueSeasonAggregate season,
            LeagueFixture fixture,
            LeagueFixtureSeriesBindingV1 binding,
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence,
            LeagueFixtureCompletionReceiptV2 receipt
    ) {
        assertThatThrownBy(() -> VerifiedLeagueFixtureCompletion.verifyPlayer(
                season, fixture, binding, season.frozenSnapshot(),
                snapshots.currentResourceProvenanceHash(), evidence,
                gameReceipts(evidence), authorityReceipts(evidence), receipt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<LeagueFixtureGameReceiptV1> gameReceipts(
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence
    ) {
        LinkedHashSet<ChampionId> history = new LinkedHashSet<>();
        ArrayList<LeagueFixtureGameReceiptV1> values = new ArrayList<>();
        evidence.orderedGames().forEach(game -> {
            history.addAll(game.completedDraft().bluePicks());
            history.addAll(game.completedDraft().redPicks());
            values.add(LeagueFixtureGameReceiptV1.from(game.verifiedInput(),
                    game.verifiedOutput(), history.stream().sorted(
                            java.util.Comparator.comparing(ChampionId::value)).toList()));
        });
        return List.copyOf(values);
    }

    private static List<LeagueFixtureDraftAuthorityReceiptV1> authorityReceipts(
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence
    ) {
        return evidence.orderedGames().stream().map(game -> {
            var control = game.verifiedOutput().finalDraft().controlEvidence();
            return LeagueFixtureDraftAuthorityReceiptV1.player(game.gameNumber(),
                    game.controlledSide(), control.policyId(), control.policyHash(),
                    control.controlEvidenceHash());
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> T mutateRecord(T value, Map<String, Object> replacements) {
        try {
            var components = value.getClass().getRecordComponents();
            LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
            for (var component : components) {
                fields.put(component.getName(), component.getAccessor().invoke(value));
            }
            fields.putAll(replacements);
            Class<?>[] types = java.util.Arrays.stream(components)
                    .map(java.lang.reflect.RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            return (T) value.getClass().getDeclaredConstructor(types)
                    .newInstance(fields.values().toArray());
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error.getCause() == null ? error : error.getCause());
        }
    }
}

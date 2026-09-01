package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.application.SeriesStatus;
import com.lolfm.simulator.SimulationInstrumentation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeaguePlayerSeriesHandoffServiceTest {
    private static final String RESOURCE_HASH = LeagueDomainTestFixtures.hash(
            "player-handoff-resource-provenance");

    @Test
    void managedBlueAndRedFixturesStartFromFrozenBindingAndLeagueSeed() {
        LeagueSeasonAggregate season = hybridSeason();
        List<LeagueFixture> managed = season.schedule().fixtures().stream()
                .filter(fixture -> fixture.containsTeam("GEN"))
                .toList();
        LeagueFixture blue = managed.stream()
                .filter(fixture -> fixture.game1BlueTeamCode().equals("GEN"))
                .findFirst().orElseThrow();
        LeagueFixture red = managed.stream()
                .filter(fixture -> fixture.game1RedTeamCode().equals("GEN"))
                .findFirst().orElseThrow();
        FakeKernel blueKernel = new FakeKernel();
        FakeKernel redKernel = new FakeKernel();

        var blueResult = service(season, blueKernel).startOrResume(command(
                season, blue, "start-blue"));
        var redResult = service(season, redKernel).startOrResume(command(
                season, red, "start-red"));

        assertThat(blueResult.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.STARTED);
        assertThat(redResult.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.STARTED);
        assertBinding(season, blue, blueResult.bindingState().binding());
        assertBinding(season, red, redResult.bindingState().binding());
        assertThat(blueResult.bindingState().binding().game1BlueTeamCode()).isEqualTo("GEN");
        assertThat(redResult.bindingState().binding().game1RedTeamCode()).isEqualTo("GEN");
        assertThat(blueKernel.starts).hasSize(1);
        assertThat(redKernel.starts).hasSize(1);
    }

    @Test
    void exactStartReplayResumesSameBindingAndCommandPayloadConflictMutatesNothing() {
        LeagueSeasonAggregate season = hybridSeason();
        LeagueFixture first = managedFixture(season, "T1");
        LeagueFixture second = managedFixture(season, "DK");
        FakeKernel kernel = new FakeKernel();
        InMemoryLeaguePlayerSeriesBindingAdapter adapter =
                new InMemoryLeaguePlayerSeriesBindingAdapter();
        LeaguePlayerSeriesHandoffService service = service(season, kernel, adapter);

        var started = service.startOrResume(command(season, first, "same-command"));
        var replayed = service.startOrResume(command(season, first, "same-command"));
        var conflict = service.startOrResume(command(season, second, "same-command"));

        assertThat(started.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.STARTED);
        assertThat(replayed.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.RESUMED);
        assertThat(replayed.bindingState().binding()).isEqualTo(
                started.bindingState().binding());
        assertThat(replayed.bindingState().revision()).isEqualTo(
                started.bindingState().revision());
        assertThat(conflict.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.BLOCKED);
        assertThat(conflict.reason()).isEqualTo(
                "PLAYER_SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
        assertThat(kernel.starts).hasSize(1);
        assertThat(kernel.resumes).hasSize(1);
        assertThat(adapter.findByFixture(season.seasonId(), second.fixtureId())).isEmpty();
    }

    @Test
    void spectatorFullAutoNonManagedStaleAndSnapshotDriftRejectBeforeKernel() {
        LeagueSeasonAggregate hybrid = hybridSeason();
        LeagueSeasonAggregate spectator = spectatorSeason();
        FakeKernel kernel = new FakeKernel();
        LeaguePlayerSeriesHandoffService service = service(hybrid, kernel);
        LeagueFixture fullAuto = LeagueDomainTestFixtures.fixture(
                hybrid.schedule(), "DK", "HLE");
        LeagueFixture managed = managedFixture(hybrid, "T1");

        var fullAutoResult = service.startOrResume(command(
                hybrid, fullAuto, "full-auto"));
        var stale = service.startOrResume(new LeaguePlayerSeriesHandoffService.StartCommand(
                hybrid.leagueId(), hybrid, managed.fixtureId(), 1, "stale"));
        var spectatorResult = service(spectator, kernel).startOrResume(
                new LeaguePlayerSeriesHandoffService.StartCommand(
                        spectator.leagueId(), spectator,
                        LeagueDomainTestFixtures.fixture(
                                spectator.schedule(), "GEN", "T1").fixtureId(),
                        spectator.revision(), "spectator"));
        LeagueSeasonFrozenSnapshot drift = new LeagueSeasonFrozenSnapshot(
                hybrid.frozenSnapshot().teamSnapshotIdentities(),
                LeagueDomainTestFixtures.hash("drift-player"),
                hybrid.frozenSnapshot().championDraftResourceIdentity(),
                hybrid.frozenSnapshot().matchupCompositionResourceIdentity(),
                hybrid.frozenSnapshot().productionRuntimeIdentity());
        var driftResult = new LeaguePlayerSeriesHandoffService(
                new FixedProduction(drift), new InMemoryLeaguePlayerSeriesBindingAdapter(),
                kernel).startOrResume(command(hybrid, managed, "drift"));

        assertThat(fullAutoResult.reason()).isEqualTo(
                "PLAYER_SERIES_FIXTURE_NOT_MANAGED");
        assertThat(stale.reason()).isEqualTo("PLAYER_SERIES_STALE_SEASON_REVISION");
        assertThat(spectatorResult.reason()).isEqualTo(
                "PLAYER_SERIES_REQUIRES_HYBRID_MANAGER");
        assertThat(driftResult.reason()).isEqualTo(
                "PLAYER_SERIES_FROZEN_IDENTITY_MISMATCH");
        assertThat(kernel.starts).isEmpty();
        assertThat(kernel.resumes).isEmpty();
        assertThat(kernel.completedReads).isZero();
    }

    @Test
    void bindingIsOpaqueAndStartCommandHasNoCallerSelectableFixtureContext() {
        assertThat(List.of(LeagueFixtureSeriesBindingV1.class.getDeclaredConstructors()))
                .allSatisfy(constructor -> assertThat(
                        Modifier.isPrivate(constructor.getModifiers())).isTrue());
        assertThat(List.of(LeaguePlayerSeriesHandoffService.StartCommand.class
                        .getRecordComponents()))
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("leagueId", "season", "fixtureId",
                        "expectedSeasonRevision", "commandId")
                .doesNotContain("opponent", "format", "game1BlueTeamCode", "rootSeed",
                        "gameSeed", "managedTeamCode", "runtimeProfile", "history");
        assertThat(List.of(com.lolfm.dto.SeriesApiV1Dtos.CreateRequest.class
                        .getRecordComponents()))
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("origin", "leagueBindingHash", "fixtureId", "leagueId");
    }

    @Test
    void incompleteCompletionAndPoolExhaustionProduceNoReceiptOrKernelMutation() {
        LeagueSeasonAggregate season = hybridSeason();
        LeagueFixture fixture = managedFixture(season, "T1");
        FakeKernel kernel = new FakeKernel();
        InMemoryLeaguePlayerSeriesBindingAdapter adapter =
                new InMemoryLeaguePlayerSeriesBindingAdapter();
        LeaguePlayerSeriesHandoffService service = service(season, kernel, adapter);
        var started = service.startOrResume(command(season, fixture, "start"));

        var incomplete = service.complete(
                new LeaguePlayerSeriesHandoffService.CompletionCommand(
                        season.leagueId(), season, fixture.fixtureId(),
                        started.bindingState().binding().bindingHash()));

        assertThat(incomplete.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.CompletionStatus.NOT_COMPLETED);
        assertThat(incomplete.receipt()).isNull();
        assertThat(incomplete.verifiedCompletion()).isNull();
        assertThat(incomplete.gameEngineExecutionCount()).isZero();
        assertThat(kernel.completedReads).isOne();
        assertThat(adapter.findByBindingHash(
                started.bindingState().binding().bindingHash()).orElseThrow().status())
                .isEqualTo(LeaguePlayerSeriesBindingPort.Status.ACTIVE);
        assertThat(season.revision()).isZero();
        assertThat(season.standings().appliedFixtureCount()).isZero();

        FakeKernel exhausted = new FakeKernel();
        exhausted.canComplete = false;
        var blocked = service(season, exhausted,
                new InMemoryLeaguePlayerSeriesBindingAdapter()).startOrResume(
                command(season, managedFixture(season, "DK"), "pool"));
        assertThat(blocked.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.StartStatus.BLOCKED);
        assertThat(blocked.reason()).isEqualTo("HARD_FEARLESS_LEGAL_POOL_EXHAUSTED");
        assertThat(exhausted.starts).isEmpty();
        assertThat(exhausted.completedReads).isZero();
    }

    @Test
    void invalidCompletedEvidenceClosesPendingBindingAsBlockedWithoutStandingsMutation() {
        LeagueSeasonAggregate season = hybridSeason();
        LeagueFixture fixture = managedFixture(season, "T1");
        FakeKernel kernel = new FakeKernel();
        InMemoryLeaguePlayerSeriesBindingAdapter adapter =
                new InMemoryLeaguePlayerSeriesBindingAdapter();
        LeaguePlayerSeriesHandoffService service = service(season, kernel, adapter);
        var started = service.startOrResume(command(season, fixture, "invalid-start"));
        kernel.invalidCompletedEvidence = true;

        var result = service.complete(
                new LeaguePlayerSeriesHandoffService.CompletionCommand(
                        season.leagueId(), season, fixture.fixtureId(),
                        started.bindingState().binding().bindingHash()));

        assertThat(result.status()).isEqualTo(
                LeaguePlayerSeriesHandoffService.CompletionStatus.BLOCKED);
        assertThat(result.receipt()).isNull();
        assertThat(adapter.findByBindingHash(
                started.bindingState().binding().bindingHash()).orElseThrow().status())
                .isEqualTo(LeaguePlayerSeriesBindingPort.Status.BLOCKED);
        assertThat(season.revision()).isZero();
        assertThat(season.standings().appliedFixtureCount()).isZero();
    }

    private static void assertBinding(
            LeagueSeasonAggregate season,
            LeagueFixture fixture,
            LeagueFixtureSeriesBindingV1 binding
    ) {
        assertThat(binding.leagueId()).isEqualTo(season.leagueId());
        assertThat(binding.seasonId()).isEqualTo(season.seasonId());
        assertThat(binding.fixtureId()).isEqualTo(fixture.fixtureId());
        assertThat(binding.boundSeriesId()).isEqualTo(fixture.boundSeriesId());
        assertThat(binding.fixtureRootSeed()).isEqualTo(fixture.fixtureRootSeed());
        assertThat(binding.gameSeedAlgorithm()).isEqualTo(LeagueIdentity.GAME_SEED_ALGORITHM);
        assertThat(binding.initialHistoryHash()).isEqualTo(
                com.lolfm.draft.SeriesDraftHistory.identityHash(0, Set.of()));
        assertThat(binding.canonicalBytes()).isNotEmpty()
                .hasSizeLessThanOrEqualTo(LeagueFixtureSeriesBindingV1.MAX_CANONICAL_BYTES);
        assertThat(binding.bindingHash()).isEqualTo(LeagueIdentity.sha256(
                binding.canonicalText().substring(0,
                        binding.canonicalText().lastIndexOf("bindingHash="))));
        assertThat(fixture.gameSeed(1, binding.initialHistoryHash())).isNotEqualTo(0L);
    }

    private static LeaguePlayerSeriesHandoffService.StartCommand command(
            LeagueSeasonAggregate season,
            LeagueFixture fixture,
            String commandId
    ) {
        return new LeaguePlayerSeriesHandoffService.StartCommand(
                season.leagueId(), season, fixture.fixtureId(), season.revision(), commandId);
    }

    private static LeagueFixture managedFixture(
            LeagueSeasonAggregate season,
            String opponent
    ) {
        return LeagueDomainTestFixtures.fixture(season.schedule(), "GEN", opponent);
    }

    private static LeagueSeasonAggregate hybridSeason() {
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        return LeagueSeasonAggregate.create(LeagueDomainTestFixtures.leagueId(),
                LeagueDomainTestFixtures.seasonId(), LeagueSeasonMode.HYBRID_MANAGER,
                "GEN", snapshot.teamSnapshotIdentity("GEN"), snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }

    private static LeagueSeasonAggregate spectatorSeason() {
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        return LeagueSeasonAggregate.create(LeagueDomainTestFixtures.leagueId(),
                LeagueDomainTestFixtures.seasonId(),
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, null, snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }

    private static LeaguePlayerSeriesHandoffService service(
            LeagueSeasonAggregate season,
            FakeKernel kernel
    ) {
        return service(season, kernel, new InMemoryLeaguePlayerSeriesBindingAdapter());
    }

    private static LeaguePlayerSeriesHandoffService service(
            LeagueSeasonAggregate season,
            FakeKernel kernel,
            InMemoryLeaguePlayerSeriesBindingAdapter adapter
    ) {
        return new LeaguePlayerSeriesHandoffService(
                new FixedProduction(season.frozenSnapshot()), adapter, kernel);
    }

    private static final class FixedProduction
            implements LeagueFrozenProductionIdentityProvider {
        private final LeagueSeasonFrozenSnapshot snapshot;

        private FixedProduction(LeagueSeasonFrozenSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public LeagueSeasonFrozenSnapshot currentSnapshot(Set<String> expectedTeamCodes) {
            return snapshot;
        }

        @Override
        public String currentResourceProvenanceHash() {
            return RESOURCE_HASH;
        }
    }

    private static final class FakeKernel implements LeaguePlayerSeriesKernelPort {
        private final List<LeagueFixtureSeriesBindingV1> starts = new ArrayList<>();
        private final List<LeagueFixtureSeriesBindingV1> resumes = new ArrayList<>();
        private int completedReads;
        private boolean canComplete = true;
        private boolean invalidCompletedEvidence;

        @Override
        public boolean canCompleteInitialDraft(LeagueFixtureSeriesBindingV1 binding) {
            return canComplete;
        }

        @Override
        public SeriesReference start(LeagueFixtureSeriesBindingV1 binding) {
            starts.add(binding);
            return reference(binding, false);
        }

        @Override
        public SeriesReference resume(LeagueFixtureSeriesBindingV1 binding) {
            resumes.add(binding);
            return reference(binding, true);
        }

        @Override
        public CompletedSeriesEvidence completedEvidence(
                LeagueFixtureSeriesBindingV1 binding,
                SimulationInstrumentation instrumentation
        ) {
            completedReads++;
            if (invalidCompletedEvidence) {
                return new CompletedSeriesEvidence(
                        binding.boundSeriesId(), binding.bindingHash(), 1,
                        binding.seriesFormat(), binding.firstTeamCode(),
                        binding.secondTeamCode(), binding.managedTeamCode(),
                        binding.fixtureRootSeed(), Map.of(
                        binding.firstTeamCode(), 0, binding.secondTeamCode(), 0),
                        List.of(), binding.initialHistoryHash(),
                        binding.firstTeamCode(), List.of());
            }
            throw new IllegalStateException("PLAYER_SERIES_NOT_COMPLETED");
        }

        private static SeriesReference reference(
                LeagueFixtureSeriesBindingV1 binding,
                boolean replayed
        ) {
            return new SeriesReference(binding.boundSeriesId(), binding.bindingHash(),
                    0, SeriesStatus.ACTIVE, 1, replayed);
        }
    }
}

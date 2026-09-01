package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class LeagueRelationalPersistenceAndJobTest {
    @TempDir Path temporary;

    @Test
    void migratesEmptyAndPreviousSchemaThenRestartsFromSameFile() {
        String url = fileUrl("migration-restart");
        try (HikariDataSource dataSource = dataSource(url)) {
            var first = Flyway.configure().dataSource(dataSource).target("1").load().migrate();
            assertThat(first.migrationsExecuted).isOne();
            var upgraded = Flyway.configure().dataSource(dataSource).load().migrate();
            assertThat(upgraded.migrationsExecuted).isOne();
            var repeated = Flyway.configure().dataSource(dataSource).load().migrate();
            assertThat(repeated.migrationsExecuted).isZero();

            StoreBundle bundle = store(dataSource, Clock.systemUTC());
            LeagueSeasonAggregate frozen = season("restart", LeagueSeasonMode.HYBRID_MANAGER);
            bundle.store().freeze(frozen);
            LeagueFixture player = frozen.schedule().fixtures().stream()
                    .filter(value -> value.executionMode()
                            == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)
                    .findFirst().orElseThrow();
            LeagueFixtureSeriesBindingV1 binding = LeagueFixtureSeriesBindingV1.create(
                    frozen, player, LeagueDomainTestFixtures.hash("resource-provenance"));
            JdbcLeaguePlayerSeriesBindingAdapter bindings =
                    new JdbcLeaguePlayerSeriesBindingAdapter(bundle.store());
            var executor = Executors.newFixedThreadPool(12);
            try {
                ArrayList<Future<LeaguePlayerSeriesBindingPort.Registration>> starts =
                        new ArrayList<>();
                for (int index = 0; index < 20; index++) {
                    int command = index;
                    starts.add(executor.submit(() -> bindings.createOrLoad(
                            command < 10 ? "durable-start" : "durable-start-" + command,
                            LeagueDomainTestFixtures.hash("payload"), binding)));
                }
                List<LeaguePlayerSeriesBindingPort.Registration> registrations =
                        starts.stream().map(value -> {
                            try { return value.get(); }
                            catch (Exception error) { throw new AssertionError(error); }
                        }).toList();
                assertThat(registrations).filteredOn(
                        LeaguePlayerSeriesBindingPort.Registration::startOwner)
                        .hasSize(1);
                assertThat(registrations).allSatisfy(value -> assertThat(
                        value.state().binding()).isEqualTo(binding));
                assertThat(bundle.jdbc().queryForObject(
                        "SELECT COUNT(*) FROM league_player_binding", Integer.class))
                        .isOne();
            } finally {
                executor.shutdownNow();
            }
            assertThatThrownBy(() -> bindings.createOrLoad("durable-start",
                    LeagueDomainTestFixtures.hash("different-payload"), binding))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("PLAYER_SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
        }

        try (HikariDataSource reopened = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(reopened).load().migrate()
                    .migrationsExecuted).isZero();
            StoreBundle bundle = store(reopened, Clock.systemUTC());
            LeagueSeasonAggregate loaded = bundle.store().loadSeason(
                    season("restart", LeagueSeasonMode.HYBRID_MANAGER).seasonId());
            assertThat(loaded.schedule().scheduleIdentity()).isEqualTo(
                    season("restart", LeagueSeasonMode.HYBRID_MANAGER)
                            .schedule().scheduleIdentity());
            assertThat(loaded.schedule().fixtures()).hasSize(90);
            JdbcLeaguePlayerSeriesBindingAdapter bindings =
                    new JdbcLeaguePlayerSeriesBindingAdapter(bundle.store());
            LeaguePlayerSeriesBindingPort.State state = bindings.findByFixture(
                    loaded.seasonId(), loaded.schedule().fixtures().stream()
                            .filter(value -> value.executionMode()
                                    == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)
                            .findFirst().orElseThrow().fixtureId()).orElseThrow();
            assertThat(state.binding().canonicalText().getBytes()).isEqualTo(
                    state.binding().canonicalBytes());
            assertThat(state.status()).isEqualTo(
                    LeaguePlayerSeriesBindingPort.Status.CREATED);
            LeagueJobCoordinator recovery = new LeagueJobCoordinator(
                    bundle.store(), mock(LeagueAutomatedSeriesRunner.class),
                    Clock.systemUTC(), new LeagueSeasonApplicationService(bundle.store()));
            assertThat(recovery.recover().playerStartsRequiringRestart()).isOne();
            assertThat(bindings.findByBindingHash(state.binding().bindingHash())
                    .orElseThrow().status()).isEqualTo(
                    LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED);
            assertThat(bundle.jdbc().queryForObject("""
                    SELECT schema_token FROM league_schema_version
                    WHERE schema_name = 'AI_LEAGUE_V1'
                    """, String.class)).isEqualTo("AI_LEAGUE_PERSISTENCE_AND_JOBS_V1");

            bundle.jdbc().update("""
                    UPDATE league_player_binding SET binding_canonical =
                      binding_canonical || 'tampered=true' WHERE binding_hash = ?
                    """, state.binding().bindingHash());
            assertThatThrownBy(() -> bindings.findByBindingHash(
                    state.binding().bindingHash()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void leaseFencingRetryParallelLimitAndRetentionAreDurableWithoutGameplay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-01T00:00:00Z"));
        LeagueAutomatedSeriesRunner runner = mock(LeagueAutomatedSeriesRunner.class);
        try (HikariDataSource dataSource = dataSource(fileUrl("jobs"))) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            StoreBundle bundle = store(dataSource, clock);
            LeagueSeasonApplicationService lifecycle =
                    new LeagueSeasonApplicationService(bundle.store());
            LeagueJobCoordinator jobs = new LeagueJobCoordinator(
                    bundle.store(), runner, clock, lifecycle);
            LeagueSeasonAggregate spectator = season(
                    "job-spectator", LeagueSeasonMode.SPECTATOR_FULL_AUTO);
            bundle.store().freeze(spectator);
            assertThat(lifecycle.ready(spectator.seasonId(), 0).status())
                    .isEqualTo(LeaguePersistenceState.SeasonStatus.READY);
            spectator.schedule().fixtures().stream().limit(5).forEach(fixture ->
                    jobs.dispatchFullAutoFixture(spectator.seasonId(), fixture.fixtureId()));

            var beforePause = lifecycle.view(spectator.seasonId());
            assertThat(lifecycle.pause(spectator.seasonId(),
                    beforePause.lifecycleRevision()).status())
                    .isEqualTo(LeaguePersistenceState.SeasonStatus.PAUSED);
            assertThat(jobs.leaseNext("worker-paused")).isEmpty();
            var paused = lifecycle.view(spectator.seasonId());
            assertThat(lifecycle.resume(spectator.seasonId(),
                    paused.lifecycleRevision()).status())
                    .isEqualTo(LeaguePersistenceState.SeasonStatus.RUNNING);

            ArrayList<LeagueSimulationApplicationPort.Lease> leases = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                leases.add(jobs.leaseNext("worker-a").orElseThrow());
            }
            assertThat(jobs.leaseNext("worker-a")).isEmpty();
            assertThat(leases).allSatisfy(lease -> {
                assertThat(lease.attemptNumber()).isOne();
                assertThat(lease.expiresAt()).isEqualTo(
                        Instant.parse("2026-08-01T00:15:00Z").atOffset(ZoneOffset.UTC));
            });
            assertThat(jobs.heartbeat(leases.getFirst())).isTrue();

            clock.advanceSeconds(15 * 60);
            assertThat(jobs.heartbeat(leases.getFirst())).isFalse();
            LeagueSimulationApplicationPort.RecoveryResult recovered = jobs.recover();
            assertThat(recovered.autoJobsRetried()).isEqualTo(4);
            assertThat(jobs.execute(leases.getFirst(),
                    com.lolfm.simulator.SimulationInstrumentation.disabled()).status())
                    .isEqualTo(LeagueSimulationApplicationPort.Status.STALE_RESULT_REJECTED);
            verifyNoInteractions(runner);

            LeagueSimulationApplicationPort.Lease second =
                    jobs.leaseNext("worker-b").orElseThrow();
            assertThat(second.attemptNumber()).isEqualTo(2);
            assertThat(second.frozenInputHash()).isEqualTo(
                    jobs.findJob(second.seasonId(), second.fixtureId())
                            .orElseThrow().frozenInputHash());
            clock.advanceSeconds(15 * 60);
            LeagueSimulationApplicationPort.RecoveryResult exhausted = jobs.recover();
            assertThat(exhausted.autoJobsBlocked()).isOne();
            assertThat(jobs.findJob(second.seasonId(), second.fixtureId())
                    .orElseThrow().status()).isEqualTo("BLOCKED");

            LeagueSeasonAggregate hybrid = season("job-hybrid", LeagueSeasonMode.HYBRID_MANAGER);
            bundle.store().freeze(hybrid);
            lifecycle.ready(hybrid.seasonId(), 0);
            LeagueFixture player = hybrid.schedule().fixtures().stream()
                    .filter(value -> value.executionMode()
                            == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)
                    .findFirst().orElseThrow();
            assertThatThrownBy(() -> jobs.dispatchFullAutoFixture(
                    hybrid.seasonId(), player.fixtureId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("PLAYER_FIXTURE_EXCLUDED_FROM_AUTO_DISPATCH");
            assertThat(jobs.findJob(hybrid.seasonId(), player.fixtureId())).isEmpty();
            var hybridReady = lifecycle.view(hybrid.seasonId());
            assertThat(lifecycle.cancel(hybrid.seasonId(),
                    hybridReady.lifecycleRevision()).status())
                    .isEqualTo(LeaguePersistenceState.SeasonStatus.CANCELLED);
            assertThatThrownBy(() -> jobs.dispatchFullAutoFixture(hybrid.seasonId(),
                    hybrid.schedule().fixtures().stream()
                            .filter(value -> value.executionMode()
                                    == LeagueFixtureExecutionMode.FULL_AUTO)
                            .findFirst().orElseThrow().fixtureId()))
                    .hasMessage("LEAGUE_SEASON_NOT_DISPATCHABLE");

            bundle.jdbc().update("""
                    UPDATE league_job_attempt SET finished_at = ?
                    WHERE lifecycle_status = 'BLOCKED'
                    """, clock.instant().minus(java.time.Duration.ofDays(31))
                    .atOffset(ZoneOffset.UTC));
            assertThat(jobs.purgeExpiredAttemptLogs()).isOne();
        }
    }

    private LeagueSeasonAggregate season(String key, LeagueSeasonMode mode) {
        String leagueId = LeagueIdentity.leagueId("persistence-test-league-" + key);
        String seasonId = LeagueIdentity.seasonId(leagueId,
                "persistence-test-season-" + key);
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        String managed = mode == LeagueSeasonMode.HYBRID_MANAGER ? "GEN" : null;
        return LeagueSeasonAggregate.create(leagueId, seasonId, mode, managed,
                managed == null ? null : snapshot.teamSnapshotIdentity(managed), snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
    }

    private StoreBundle store(HikariDataSource dataSource, Clock clock) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        LeagueJsonCodec json = new LeagueJsonCodec(
                new ObjectMapper().findAndRegisterModules());
        return new StoreBundle(new LeagueRelationalStore(jdbc,
                new DataSourceTransactionManager(dataSource), json, clock), jdbc);
    }

    private String fileUrl(String name) {
        return "jdbc:h2:file:" + temporary.resolve(name).toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
    }

    private static HikariDataSource dataSource(String url) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(url);
        configuration.setUsername("sa");
        configuration.setPassword("");
        configuration.setMaximumPoolSize(4);
        return new HikariDataSource(configuration);
    }

    private record StoreBundle(LeagueRelationalStore store, JdbcTemplate jdbc) {}

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

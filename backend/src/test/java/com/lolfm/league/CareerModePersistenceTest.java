package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CareerApplicationService;
import com.lolfm.career.CareerCalendarApplicationService;
import com.lolfm.career.CareerCalendarRelationalStore;
import com.lolfm.career.CareerCalendarTemplate;
import com.lolfm.career.CareerException;
import com.lolfm.career.CareerIdentity;
import com.lolfm.career.CareerRelationalStore;
import com.lolfm.dto.CareerApiV1Dtos;
import com.lolfm.reference.TeamPlayerInformationCatalog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Clob;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class CareerModePersistenceTest {
    @TempDir Path temporary;

    @Test
    void atomicProvisionReplayPlayerResumeAndFileRestartReuseExistingAuthority() {
        String url = "jdbc:h2:file:" + temporary.resolve("career-restart").toAbsolutePath()
                + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String commandId = UUID.randomUUID().toString();
        String careerId;
        String seasonId;
        String playerFixtureId;
        String boundSeriesId;
        DatabaseState durableState;

        try (HikariDataSource dataSource = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(dataSource).load().migrate()
                    .migrationsExecuted).isEqualTo(5);
            Harness harness = harness(dataSource);

            String rolledBackCommand = UUID.randomUUID().toString();
            String rolledBackCareer = CareerIdentity.careerId(rolledBackCommand);
            String rolledBackLeague = CareerIdentity.leagueId(rolledBackCareer);
            String rolledBackSeason = CareerIdentity.seasonId(
                    rolledBackCareer, rolledBackLeague);
            assertThatThrownBy(() -> harness.careerStore().createOrReplay(
                    rolledBackCommand, LeagueDomainTestFixtures.hash("rollback-payload"),
                    () -> {
                        harness.provisioning().provision(rolledBackLeague,
                                rolledBackSeason, "GEN",
                                CareerIdentity.rootSeed(rolledBackCareer));
                        throw new IllegalStateException("forced-after-provisioning");
                    })).isInstanceOf(IllegalStateException.class)
                    .hasMessage("forced-after-provisioning");
            assertThat(count(harness.jdbc(), "career_save")).isZero();
            assertThat(count(harness.jdbc(), "career_create_command")).isZero();
            assertThat(count(harness.jdbc(), "league_registry")).isZero();
            assertThat(count(harness.jdbc(), "league_season")).isZero();
            assertThat(count(harness.jdbc(), "league_fixture")).isZero();

            CareerApplicationService.CreateResult created = harness.careers().create(
                    request(commandId));
            assertThat(created.replayed()).isFalse();
            careerId = created.career().career().careerId();
            seasonId = created.career().career().seasonId();
            assertThat(created.career().linkedSeason().seasonMode())
                    .isEqualTo("HYBRID_MANAGER");
            assertThat(created.career().linkedSeason().resume().kind())
                    .isEqualTo("LEAGUE_DASHBOARD");
            assertThat(created.career().linkedSeason().resume().allowedCommands())
                    .containsExactly("VIEW_STANDINGS",
                            "RUN_CURRENT_ROUND_AUTO_FIXTURES", "CANCEL_SEASON");
            assertThat(count(harness.jdbc(), "career_save")).isOne();
            assertThat(count(harness.jdbc(), "career_create_command")).isOne();
            assertThat(count(harness.jdbc(), "league_season")).isOne();
            assertThat(count(harness.jdbc(), "league_round")).isEqualTo(18);
            assertThat(count(harness.jdbc(), "league_fixture")).isEqualTo(90);
            assertThat(count(harness.jdbc(), "league_standing")).isEqualTo(10);
            assertThat(harness.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM league_fixture
                    WHERE execution_mode = 'PLAYER_CONTROLLED'
                    """, Integer.class)).isEqualTo(18);
            assertThat(harness.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM league_fixture
                    WHERE execution_mode = 'FULL_AUTO'
                    """, Integer.class)).isEqualTo(72);
            assertThat(count(harness.jdbc(), "league_job")).isZero();

            CareerApplicationService.CreateResult replay = harness.careers().create(
                    request(commandId));
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.career().career().careerId()).isEqualTo(careerId);
            assertThat(replay.career().career().seasonId()).isEqualTo(seasonId);
            assertThat(count(harness.jdbc(), "league_fixture")).isEqualTo(90);

            assertThatThrownBy(() -> harness.careers().create(
                    new CareerApiV1Dtos.CreateRequest(
                            CareerApiV1Dtos.CREATE_REQUEST_SCHEMA, "changed", "manager",
                            "GEN", commandId)))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type.COMMAND_CONFLICT);
            assertThat(count(harness.jdbc(), "career_save")).isOne();
            assertThat(count(harness.jdbc(), "league_season")).isOne();

            LeagueSeasonAggregate season = harness.leagueStore().loadSeason(seasonId);
            LeagueFixture player = season.schedule().fixtures().stream()
                    .filter(fixture -> fixture.executionMode()
                            == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)
                    .findFirst().orElseThrow();
            playerFixtureId = player.fixtureId();
            LeagueFixtureSeriesBindingV1 binding = LeagueFixtureSeriesBindingV1.create(
                    season, player, LeagueDomainTestFixtures.hash("career-resume-resource"));
            boundSeriesId = binding.boundSeriesId();
            LeaguePlayerSeriesBindingPort.Registration registration = harness.bindings()
                    .createOrLoad("career-player-series-start",
                            LeagueDomainTestFixtures.hash("career-start-payload"), binding);
            assertThat(registration.state().status())
                    .isEqualTo(LeaguePlayerSeriesBindingPort.Status.CREATED);

            CareerApplicationService.CareerViewState resumed = harness.careers().get(careerId);
            assertThat(resumed.linkedSeason().resume().kind()).isEqualTo("PLAYER_SERIES");
            assertThat(resumed.linkedSeason().resume().fixtureId())
                    .isEqualTo(playerFixtureId);
            assertThat(resumed.linkedSeason().resume().seriesId())
                    .isEqualTo(boundSeriesId);
            assertThat(resumed.linkedSeason().resume().allowedCommands())
                    .containsExactly("RESUME_PLAYER_SERIES");

            harness.bindings().transition(binding.bindingHash(), 0,
                    LeaguePlayerSeriesBindingPort.Status.CREATED,
                    LeaguePlayerSeriesBindingPort.Status.ACTIVE, null, null);
            harness.jdbc().update("""
                    INSERT INTO league_player_series_checkpoint(
                      binding_hash, series_id, checkpoint_schema, checkpoint_json,
                      checkpoint_hash, series_revision, series_status, updated_at)
                    VALUES (?, ?, ?, ?, ?, 7, 'ACTIVE', CURRENT_TIMESTAMP)
                    """, binding.bindingHash(), boundSeriesId,
                    "TEST_EXPIRED_RESERVATION_V1",
                    "{\"reservation\":{\"leaseExpiresAt\":\"2000-01-01T00:00:00Z\"}}",
                    LeagueDomainTestFixtures.hash("expired-checkpoint"));

            resumed = harness.careers().get(careerId);
            assertThat(resumed.linkedSeason().resume().kind()).isEqualTo("PLAYER_SERIES");
            assertThat(resumed.linkedSeason().resume().allowedCommands())
                    .containsExactly("RESUME_PLAYER_SERIES");
            verifyNoInteractions(harness.playerSeries());

            harness.jdbc().update("""
                    UPDATE league_season SET lifecycle_status = 'PAUSED',
                      lifecycle_revision = lifecycle_revision + 1
                    WHERE season_id = ?
                    """, seasonId);
            CareerApplicationService.ResumeState paused = harness.careers().get(careerId)
                    .linkedSeason().resume();
            assertThat(paused.kind()).isEqualTo("LEAGUE_DASHBOARD");
            assertThat(paused.allowedCommands()).containsExactly(
                    "VIEW_STANDINGS", "RESUME_SEASON", "CANCEL_SEASON");

            harness.jdbc().update("""
                    UPDATE league_season SET lifecycle_status = 'READY',
                      lifecycle_revision = lifecycle_revision + 1
                    WHERE season_id = ?
                    """, seasonId);
            harness.jdbc().update("""
                    UPDATE league_player_binding SET lifecycle_status = 'VERIFIED'
                    WHERE binding_hash = ?
                    """, binding.bindingHash());
            harness.jdbc().update("""
                    UPDATE league_player_series_checkpoint SET series_status = 'COMPLETED'
                    WHERE binding_hash = ?
                    """, binding.bindingHash());
            CareerApplicationService.ResumeState verified = harness.careers().get(careerId)
                    .linkedSeason().resume();
            assertThat(verified.kind()).isEqualTo("LEAGUE_DASHBOARD");
            assertThat(verified.allowedCommands()).doesNotContain(
                    "RESUME_PLAYER_SERIES", "RECONCILE_PLAYER_SERIES_COMPLETION");

            harness.jdbc().update("""
                    UPDATE league_player_binding SET lifecycle_status = 'ACTIVE'
                    WHERE binding_hash = ?
                    """, binding.bindingHash());
            harness.jdbc().update("""
                    UPDATE league_player_series_checkpoint SET series_status = 'ACTIVE'
                    WHERE binding_hash = ?
                    """, binding.bindingHash());
            durableState = state(harness.jdbc());
            harness.careers().get(careerId);
            harness.careers().list();
            verifyNoInteractions(harness.playerSeries());
            assertThat(state(harness.jdbc())).isEqualTo(durableState);
        }

        try (HikariDataSource reopened = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(reopened).load().migrate()
                    .migrationsExecuted).isZero();
            Harness harness = harness(reopened);
            CareerApplicationService.CareerViewState loaded = harness.careers().get(careerId);
            assertThat(loaded.career().careerId()).isEqualTo(careerId);
            assertThat(loaded.career().seasonId()).isEqualTo(seasonId);
            assertThat(loaded.linkedSeason().resume().kind()).isEqualTo("PLAYER_SERIES");
            assertThat(loaded.linkedSeason().resume().fixtureId())
                    .isEqualTo(playerFixtureId);
            assertThat(loaded.linkedSeason().resume().seriesId()).isEqualTo(boundSeriesId);
            verifyNoInteractions(harness.playerSeries());
            assertThat(state(harness.jdbc())).isEqualTo(durableState);

            CareerApplicationService.CreateResult replay = harness.careers().create(
                    request(commandId));
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.career().career().careerId()).isEqualTo(careerId);
            assertThat(count(harness.jdbc(), "career_save")).isOne();
            assertThat(count(harness.jdbc(), "league_season")).isOne();
            assertThat(count(harness.jdbc(), "league_fixture")).isEqualTo(90);

            assertThat(harness.jdbc().queryForObject("""
                    SELECT schema_token FROM career_schema_version
                    WHERE schema_name = 'CAREER_MODE_V1'
                    """, String.class)).isEqualTo("CAREER_MODE_FOUNDATION_V1");
            harness.jdbc().update("""
                    UPDATE career_save SET league_product_decision_hash = ?
                    WHERE career_id = ?
                    """, "f".repeat(64), careerId);
            assertThatThrownBy(() -> harness.careers().get(careerId))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type.LINKED_SEASON_INTEGRITY_FAILURE);
            assertThat(count(harness.jdbc(), "league_season")).isOne();
            assertThat(count(harness.jdbc(), "league_fixture")).isEqualTo(90);
        }
    }

    @Test
    void capacityRejectsNewCommandsButExactReplayRemainsAvailableWithoutMutation() {
        String url = "jdbc:h2:mem:career-capacity-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource, 1);
            String firstCommand = UUID.randomUUID().toString();
            CareerApplicationService.CreateResult first = harness.careers().create(
                    request(firstCommand));
            DatabaseState full = state(harness.jdbc());

            CareerApplicationService.CreateResult replay = harness.careers().create(
                    request(firstCommand));
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.career().career().careerId())
                    .isEqualTo(first.career().career().careerId());
            assertThat(state(harness.jdbc())).isEqualTo(full);

            assertThatThrownBy(() -> harness.careers().create(
                    request(UUID.randomUUID().toString())))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type.CAPACITY_REACHED);
            assertThat(state(harness.jdbc())).isEqualTo(full);
            assertThat(harness.careers().list().currentCount()).isOne();
            assertThat(harness.careers().list().maximumCount()).isOne();
            assertThat(harness.careers().list().remainingCount()).isZero();
        }
    }

    @Test
    void calendarProjectionAdvanceReplayAndStaleRevisionRemainDeterministic() {
        String url = "jdbc:h2:file:" + temporary.resolve("career-calendar-restart")
                .toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String careerId;
        String firstCommand = UUID.randomUUID().toString();
        DatabaseState afterFirst;
        DatabaseState afterSecond;
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerApplicationService.CreateResult created = harness.careers().create(
                    request(UUID.randomUUID().toString()));
            CareerRelationalStore.CareerRow career = created.career().career();
            careerId = career.careerId();

            CareerCalendarTemplate.ProjectedCalendar projected2027 =
                    harness.calendarTemplate().project(2027);
            CareerCalendarTemplate.ProjectedCalendar projected2028 =
                    harness.calendarTemplate().project(2028);
            assertThat(projected2028.projectionStatus())
                    .isEqualTo("GAME_PROJECTED_FROM_2026_TEMPLATE");
            assertThat(projected2028.events()).hasSameSizeAs(projected2027.events());
            for (int index = 0; index < projected2027.events().size(); index++) {
                CareerCalendarTemplate.ProjectedEvent event2027 =
                        projected2027.events().get(index);
                CareerCalendarTemplate.ProjectedEvent event2028 =
                        projected2028.events().get(index);
                assertThat(event2028.templateId()).isEqualTo(event2027.templateId());
                assertThat(event2028.eventId()).isNotEqualTo(event2027.eventId());
                assertThat(event2028.startDate().getMonth())
                        .isEqualTo(event2027.startDate().getMonth());
                assertThat(event2028.startDate().getDayOfMonth())
                        .isEqualTo(event2027.startDate().getDayOfMonth());
                assertThat(event2028.endDate().getMonth())
                        .isEqualTo(event2027.endDate().getMonth());
                assertThat(event2028.endDate().getDayOfMonth())
                        .isEqualTo(event2027.endDate().getDayOfMonth());
                assertThat(event2028.stages()).zipSatisfy(event2027.stages(),
                        (next, previous) -> {
                            assertThat(next.startDate()).isEqualTo(previous.startDate()
                                    == null ? null : previous.startDate().plusYears(1));
                            assertThat(next.endDate()).isEqualTo(previous.endDate()
                                    == null ? null : previous.endDate().plusYears(1));
                        });
            }

            CareerCalendarApplicationService.CalendarView initial =
                    harness.calendar().view(career);
            assertThat(initial.state().currentDate()).isEqualTo(LocalDate.of(2026, 8, 24));
            assertThat(initial.state().seasonYear()).isEqualTo(2027);
            assertThat(initial.upcomingEvents()).hasSize(8);
            assertThat(initial.fixtureOverlay().fixtures()).hasSize(90);
            assertThat(initial.counts().sources()).isEqualTo(15);
            assertThat(initial.counts().calendarDefinitions()).isEqualTo(11);
            assertThat(initial.upcomingEvents())
                    .extracting(com.lolfm.career.CareerCalendarTemplate.ProjectedEvent
                            ::templateId)
                    .doesNotContain("KESPA_CUP");
            assertThat(initial.sourceDataNotes()).containsExactly(
                    new CareerCalendarApplicationService.SourceDataNote(
                            "KESPA_CUP", "SOURCE_DATA_NOT_PRESENT"));

            CareerCalendarApplicationService.AdvanceResult first =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            firstCommand);
            assertThat(first.pending()).isFalse();
            assertThat(first.calendar().state().currentDate())
                    .isEqualTo(LocalDate.of(2027, 1, 14));
            assertThat(first.calendar().state().calendarRevision()).isEqualTo(1);
            afterFirst = state(harness.jdbc());

            CareerCalendarApplicationService.AdvanceResult replay =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            firstCommand);
            assertThat(replay.replayed()).isTrue();
            assertThat(state(harness.jdbc())).isEqualTo(afterFirst);

            assertThatThrownBy(() -> harness.calendar().advance(career,
                    CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                    CareerCalendarApplicationService.ADVANCE_ONE_DAY,
                    UUID.randomUUID().toString()))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type.CALENDAR_STALE_REVISION);
            assertThat(state(harness.jdbc())).isEqualTo(afterFirst);

            CareerCalendarApplicationService.AdvanceResult second =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 1,
                            CareerCalendarApplicationService.ADVANCE_ONE_DAY,
                            UUID.randomUUID().toString());
            assertThat(second.calendar().state().currentDate())
                    .isEqualTo(LocalDate.of(2027, 1, 15));
            assertThat(second.calendar().state().calendarRevision()).isEqualTo(2);
            afterSecond = state(harness.jdbc());
        }

        try (HikariDataSource reopened = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(reopened).load().migrate()
                    .migrationsExecuted).isZero();
            Harness harness = harness(reopened);
            CareerRelationalStore.CareerRow career = harness.careerStore()
                    .find(careerId).orElseThrow();
            CareerCalendarApplicationService.CalendarView restored =
                    harness.calendar().view(career);
            assertThat(restored.state().currentDate()).isEqualTo(LocalDate.of(2027, 1, 15));
            assertThat(restored.state().calendarRevision()).isEqualTo(2);

            CareerCalendarApplicationService.AdvanceResult replayAfterRestart =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            firstCommand);
            assertThat(replayAfterRestart.replayed()).isTrue();
            assertThat(replayAfterRestart.calendar().state().currentDate())
                    .isEqualTo(LocalDate.of(2027, 1, 14));
            assertThat(replayAfterRestart.calendar().state().calendarRevision()).isEqualTo(1);
            assertThat(state(harness.jdbc())).isEqualTo(afterSecond);
        }
    }

    @Test
    void v4CareerMigratesToFrozenV5CalendarWithoutBackdatingFoundationBinding() {
        String url = "jdbc:h2:file:" + temporary.resolve("career-v4-calendar-migration")
                .toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String commandId = UUID.randomUUID().toString();
        String careerId = CareerIdentity.careerId(commandId);
        try (HikariDataSource dataSource = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(dataSource)
                    .target(MigrationVersion.fromVersion("4")).load().migrate()
                    .migrationsExecuted).isEqualTo(4);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DataSourceTransactionManager transactions =
                    new DataSourceTransactionManager(dataSource);
            LeagueRelationalStore leagueStore = new LeagueRelationalStore(jdbc,
                    transactions, new LeagueJsonCodec(
                    new ObjectMapper().findAndRegisterModules()), Clock.systemUTC());
            LeagueSeasonApplicationService seasons = new LeagueSeasonApplicationService(
                    leagueStore);
            LeagueProductionSnapshotProvider snapshots = mock(
                    LeagueProductionSnapshotProvider.class);
            when(snapshots.currentTeamCodes()).thenReturn(
                    Set.copyOf(LeagueDomainTestFixtures.TEAM_CODES));
            when(snapshots.currentSnapshot(any())).thenReturn(
                    LeagueDomainTestFixtures.snapshot());
            LeagueCareerSeasonProvisioningService provisioning =
                    new LeagueCareerSeasonProvisioningService(snapshots, seasons,
                            leagueStore);
            CareerRelationalStore store = new CareerRelationalStore(jdbc, transactions,
                    Clock.systemUTC(), CareerRelationalStore.MAX_CAREERS);
            TeamPlayerInformationCatalog catalog =
                    TeamPlayerInformationCatalog.loadDefault();
            String leagueId = CareerIdentity.leagueId(careerId);
            String seasonId = CareerIdentity.seasonId(careerId, leagueId);
            long seed = CareerIdentity.rootSeed(careerId);
            java.time.LocalDate start = java.time.LocalDate.of(2026, 8, 24);
            store.createOrReplay(commandId,
                    CareerIdentity.createPayloadHash(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                            "GEN 장기 저장", "김 감독", "GEN"), () -> {
                        CareerApplicationService.ProvisionedSeason provisioned =
                                provisioning.provision(leagueId, seasonId, "GEN", seed);
                        String binding = CareerIdentity.bindingHash(careerId, "GEN", start,
                                start, leagueId, seasonId, seed,
                                provisioned.frozenSnapshotIdentity(),
                                provisioned.productDecisionIdentity(),
                                catalog.provenance().catalogVersion(),
                                catalog.provenance().catalogHash());
                        return new CareerRelationalStore.NewCareer(careerId,
                                "GEN 장기 저장", "김 감독", "GEN", start, start,
                                leagueId, seasonId, seed, CareerIdentity.SEED_ALGORITHM,
                                provisioned.frozenSnapshotIdentity(),
                                provisioned.productDecisionIdentity(),
                                catalog.provenance().catalogVersion(),
                                catalog.provenance().catalogHash(),
                                CareerIdentity.BINDING_SCHEMA, binding,
                                CareerIdentity.CAREER_SCHEMA, "ACTIVE", 0);
                    });
            assertThat(count(jdbc, "career_save")).isOne();
        }

        try (HikariDataSource dataSource = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(dataSource).load().migrate()
                    .migrationsExecuted).isOne();
            Harness harness = harness(dataSource);
            CareerApplicationService.CareerViewState loaded =
                    harness.careers().get(careerId);
            assertThat(loaded.career().currentDate()).isEqualTo(LocalDate.of(2026, 8, 24));
            assertThat(loaded.currentGameDate()).isEqualTo(LocalDate.of(2026, 8, 24));
            assertThat(harness.jdbc().queryForObject("""
                    SELECT lifecycle_status FROM career_calendar_state
                    WHERE career_id = ?
                    """, String.class, careerId)).isEqualTo("ACTIVE");
            assertThat(harness.jdbc().queryForObject("""
                    SELECT active_calendar_season_year FROM career_calendar_state
                    WHERE career_id = ?
                    """, Integer.class, careerId)).isEqualTo(2027);
            assertThat(harness.jdbc().queryForObject("""
                    SELECT calendar_state_hash FROM career_calendar_state
                    WHERE career_id = ?
                    """, String.class, careerId)).matches("[0-9a-f]{64}")
                    .isNotEqualTo("0".repeat(64));
            assertThat(harness.calendar().view(loaded.career()).upcomingEvents()
                    .getFirst().startDate()).isEqualTo(LocalDate.of(2027, 1, 14));
        }
    }

    @Test
    void calendarStopsAtManagedFixtureAndOnlyDispatchesSameDateAutoFixtures() {
        String url = "jdbc:h2:mem:career-calendar-gate-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            List<Map<String, Object>> identitiesBefore = harness.jdbc().queryForList("""
                    SELECT fixture_id, fixture_root_seed FROM league_fixture
                    WHERE season_id = ? ORDER BY fixture_id
                    """, career.seasonId());

            long revision = 0;
            CareerCalendarApplicationService.AdvanceResult result = null;
            for (int index = 0; index < 3; index++) {
                result = harness.calendar().advance(career,
                        CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, revision,
                        CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                        UUID.randomUUID().toString());
                revision = result.calendar().state().calendarRevision();
            }
            assertThat(result).isNotNull();
            assertThat(result.stopReason()).isEqualTo("MANAGED_FIXTURE_REQUIRED");
            assertThat(result.pending()).isFalse();
            assertThat(result.calendar().state().currentDate())
                    .isEqualTo(LocalDate.of(2027, 4, 1));
            assertThat(result.calendar().state().blockingReason())
                    .isEqualTo("MANAGED_FIXTURE_REQUIRED");
            assertThat(result.calendar().nextManagedFixture()).isNotNull();
            assertThat(result.calendar().nextManagedFixture().executionMode())
                    .isEqualTo("PLAYER_CONTROLLED");
            verify(harness.calendarJobs(), times(4)).dispatchFullAutoFixture(
                    org.mockito.ArgumentMatchers.eq(career.seasonId()), any());
            assertThat(harness.jdbc().queryForList("""
                    SELECT fixture_id, fixture_root_seed FROM league_fixture
                    WHERE season_id = ? ORDER BY fixture_id
                    """, career.seasonId())).isEqualTo(identitiesBefore);
            assertThat(count(harness.jdbc(), "league_job")).isZero();
        }
    }

    private Harness harness(HikariDataSource dataSource) {
        return harness(dataSource, CareerRelationalStore.MAX_CAREERS);
    }

    private Harness harness(HikariDataSource dataSource, int maximumCareers) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        LeagueRelationalStore leagueStore = new LeagueRelationalStore(jdbc,
                transactionManager, new LeagueJsonCodec(
                new ObjectMapper().findAndRegisterModules()), java.time.Clock.systemUTC());
        LeagueSeasonApplicationService seasons = new LeagueSeasonApplicationService(
                leagueStore);
        LeagueProductionSnapshotProvider snapshots = mock(
                LeagueProductionSnapshotProvider.class);
        when(snapshots.currentTeamCodes()).thenReturn(
                Set.copyOf(LeagueDomainTestFixtures.TEAM_CODES));
        when(snapshots.currentSnapshot(any())).thenReturn(
                LeagueDomainTestFixtures.snapshot());
        LeagueCareerSeasonProvisioningService provisioning =
                new LeagueCareerSeasonProvisioningService(snapshots, seasons, leagueStore);
        JdbcLeaguePlayerSeriesBindingAdapter bindings =
                new JdbcLeaguePlayerSeriesBindingAdapter(leagueStore);
        LeaguePlayerSeriesKernelPort playerSeries = mock(LeaguePlayerSeriesKernelPort.class);
        when(playerSeries.inspect(any())).thenThrow(
                new AssertionError("Career reads must not inspect mutable Series state"));
        LeagueCareerSeasonReadService read = new LeagueCareerSeasonReadService(leagueStore);
        CareerRelationalStore careerStore = new CareerRelationalStore(
                jdbc, transactionManager, Clock.systemUTC(), maximumCareers);
        CareerCalendarTemplate calendarTemplate = new CareerCalendarTemplate(
                new ObjectMapper().findAndRegisterModules());
        CareerCalendarRelationalStore calendarStore =
                new CareerCalendarRelationalStore(jdbc, transactionManager,
                        calendarTemplate);
        LeagueSimulationApplicationPort calendarJobs = mock(
                LeagueSimulationApplicationPort.class);
        LeagueCareerCalendarService calendarLeague = new LeagueCareerCalendarService(
                leagueStore, calendarJobs, ignored -> true);
        CareerCalendarApplicationService calendar =
                new CareerCalendarApplicationService(calendarStore, calendarTemplate,
                        calendarLeague);
        CareerApplicationService careers = new CareerApplicationService(careerStore,
                provisioning, read, TeamPlayerInformationCatalog.loadDefault(), calendar);
        return new Harness(jdbc, leagueStore, careerStore, provisioning, bindings,
                playerSeries, careers, calendar, calendarTemplate, calendarJobs);
    }

    private static CareerApiV1Dtos.CreateRequest request(String commandId) {
        return new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "GEN 장기 저장", "김 감독", "GEN", commandId);
    }

    private static DatabaseState state(JdbcTemplate jdbc) {
        LinkedHashMap<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        tables.put("career_save", rows(jdbc, "career_save", "career_id"));
        tables.put("career_create_command", rows(jdbc, "career_create_command",
                "client_command_id"));
        tables.put("career_calendar_state", rows(jdbc, "career_calendar_state",
                "career_id"));
        tables.put("career_calendar_advance_command", rows(jdbc,
                "career_calendar_advance_command", "client_command_id"));
        tables.put("league_registry", rows(jdbc, "league_registry", "league_id"));
        tables.put("league_season", rows(jdbc, "league_season", "season_id"));
        tables.put("league_round", rows(jdbc, "league_round",
                "season_id, round_number"));
        tables.put("league_standing", rows(jdbc, "league_standing",
                "season_id, team_code"));
        tables.put("league_fixture", rows(jdbc, "league_fixture",
                "season_id, fixture_id"));
        tables.put("league_player_binding", rows(jdbc, "league_player_binding",
                "binding_hash"));
        tables.put("league_player_binding_command", rows(jdbc,
                "league_player_binding_command", "season_id, fixture_id, command_id"));
        tables.put("league_player_series_checkpoint", rows(jdbc,
                "league_player_series_checkpoint", "binding_hash"));
        tables.put("league_api_command", rows(jdbc, "league_api_command",
                "client_command_id"));
        tables.put("league_job", rows(jdbc, "league_job", "job_id"));
        tables.put("league_job_attempt", rows(jdbc, "league_job_attempt",
                "season_id, fixture_id, attempt_number"));
        tables.put("league_completion_receipt", rows(jdbc,
                "league_completion_receipt", "receipt_hash"));
        tables.put("league_outbox", rows(jdbc, "league_outbox", "event_id"));
        tables.put("league_standings_application", rows(jdbc,
                "league_standings_application", "season_id, fixture_id, receipt_hash"));
        return new DatabaseState(Map.copyOf(tables));
    }

    private static List<Map<String, Object>> rows(
            JdbcTemplate jdbc,
            String table,
            String orderBy
    ) {
        return jdbc.query("SELECT * FROM " + table + " ORDER BY " + orderBy,
                result -> {
                    ResultSetMetaData metadata = result.getMetaData();
                    java.util.ArrayList<Map<String, Object>> records =
                            new java.util.ArrayList<>();
                    while (result.next()) {
                        LinkedHashMap<String, Object> record = new LinkedHashMap<>();
                        for (int index = 1; index <= metadata.getColumnCount(); index++) {
                            Object value = result.getObject(index);
                            if (value instanceof Clob clob) {
                                value = clob.getSubString(1, Math.toIntExact(clob.length()));
                            }
                            record.put(metadata.getColumnLabel(index), value);
                        }
                        records.add(java.util.Collections.unmodifiableMap(record));
                    }
                    return List.copyOf(records);
                });
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static HikariDataSource dataSource(String url) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(url);
        configuration.setUsername("sa");
        configuration.setPassword("");
        configuration.setMaximumPoolSize(4);
        return new HikariDataSource(configuration);
    }

    private record Harness(
            JdbcTemplate jdbc,
            LeagueRelationalStore leagueStore,
            CareerRelationalStore careerStore,
            LeagueCareerSeasonProvisioningService provisioning,
            JdbcLeaguePlayerSeriesBindingAdapter bindings,
            LeaguePlayerSeriesKernelPort playerSeries,
            CareerApplicationService careers,
            CareerCalendarApplicationService calendar,
            CareerCalendarTemplate calendarTemplate,
            LeagueSimulationApplicationPort calendarJobs
    ) {}

    private record DatabaseState(
            Map<String, List<Map<String, Object>>> tables
    ) {}
}

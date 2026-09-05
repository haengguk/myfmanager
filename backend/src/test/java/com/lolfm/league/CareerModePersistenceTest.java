package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CareerApplicationService;
import com.lolfm.career.CareerCalendarApplicationService;
import com.lolfm.career.CareerCalendarRelationalStore;
import com.lolfm.career.CareerCalendarTemplate;
import com.lolfm.career.CareerCompetitionAggregate;
import com.lolfm.career.CareerCompetitionApplicationService;
import com.lolfm.career.CareerCompetitionRelationalStore;
import com.lolfm.career.CareerCompetitionRules;
import com.lolfm.career.CareerCompetitionTestSupport;
import com.lolfm.career.CareerException;
import com.lolfm.career.CareerIdentity;
import com.lolfm.career.CareerRelationalStore;
import com.lolfm.dto.CareerApiV1Dtos;
import com.lolfm.reference.TeamPlayerInformationCatalog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Clob;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                    .migrationsExecuted).isEqualTo(9);
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
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
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
            assertThat(initial.fixtureOverlay().schemaVersion())
                    .isEqualTo(CareerCalendarTemplate.FIXTURE_OVERLAY_SCHEMA_V1);
            assertThat(initial.fixtureOverlay().provenanceV2().schemaVersion())
                    .isEqualTo(CareerCalendarTemplate.FIXTURE_OVERLAY_SCHEMA_V2);
            assertThat(initial.fixtureOverlay().provenanceV2().scheduleIdentity())
                    .matches("[0-9a-f]{64}");
            assertThat(initial.fixtureOverlay().provenanceV2().overlayHash())
                    .isNotEqualTo(initial.fixtureOverlay().overlayHash());
            assertThat(initial.counts().sources()).isEqualTo(15);
            assertThat(initial.counts().calendarDefinitions()).isEqualTo(11);
            assertThat(initial.upcomingEvents())
                    .extracting(com.lolfm.career.CareerCalendarTemplate.ProjectedEvent
                            ::templateId)
                    .doesNotContain("KESPA_CUP");
            assertThat(initial.sourceDataNotes()).containsExactly(
                    new CareerCalendarApplicationService.SourceDataNote(
                            "KESPA_CUP",
                            "REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE",
                            2025, "KESPA_CUP_REFERENCE_TEMPLATE_2025",
                            List.of("KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE",
                                    "EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING")));
            String tamperedFixture = initial.fixtureOverlay().fixtures().getFirst()
                    .fixtureId();
            long originalSeed = harness.jdbc().queryForObject("""
                    SELECT fixture_root_seed FROM league_fixture
                    WHERE season_id = ? AND fixture_id = ?
                    """, Long.class, career.seasonId(), tamperedFixture);
            harness.jdbc().update("""
                    UPDATE league_fixture SET fixture_root_seed = ?
                    WHERE season_id = ? AND fixture_id = ?
                    """, originalSeed + 1, career.seasonId(), tamperedFixture);
            assertThatThrownBy(() -> harness.calendar().view(career))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type.CALENDAR_INTEGRITY_FAILURE);
            harness.jdbc().update("""
                    UPDATE league_fixture SET fixture_root_seed = ?
                    WHERE season_id = ? AND fixture_id = ?
                    """, originalSeed, career.seasonId(), tamperedFixture);

            CareerCalendarApplicationService.AdvanceResult first =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            firstCommand);
            assertThat(first.pending()).isFalse();
            assertThat(first.calendar().state().currentDate())
                    .isEqualTo(LocalDate.of(2027, 1, 14));
            assertThat(first.calendar().state().calendarRevision()).isEqualTo(1);
            assertThat(first.commandResult().receipt().mode())
                    .isEqualTo(CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT);
            assertThat(first.commandResult().receipt().expectedRevision()).isZero();
            assertThat(first.commandResult().receipt().resultingRevision()).isEqualTo(1);
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
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
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
                    .isEqualTo(LocalDate.of(2027, 1, 15));
            assertThat(replayAfterRestart.calendar().state().calendarRevision())
                    .isEqualTo(2);
            assertThat(replayAfterRestart.commandResult().receipt().resultingDate())
                    .isEqualTo(LocalDate.of(2027, 1, 14));
            assertThat(replayAfterRestart.commandResult().receipt().resultingRevision())
                    .isEqualTo(1);
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
                    .migrationsExecuted).isEqualTo(5);
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
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
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

    @Test
    void pendingAdvanceIsServerRecoverablePerCareerAcrossRestart() {
        String url = "jdbc:h2:file:" + temporary.resolve("calendar-pending-recovery")
                .toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String careerA;
        String careerB;
        String commandA = UUID.randomUUID().toString();
        String commandB = UUID.randomUUID().toString();
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow first = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            CareerRelationalStore.CareerRow second = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
            careerA = first.careerId();
            careerB = second.careerId();

            for (CareerRelationalStore.CareerRow career : List.of(first, second)) {
                long revision = 0;
                for (int index = 0; index < 2; index++) {
                    CareerCalendarApplicationService.AdvanceResult result =
                            harness.calendar().advance(career,
                                    CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, revision,
                                    CareerCalendarApplicationService
                                            .ADVANCE_TO_NEXT_EVENT,
                                    UUID.randomUUID().toString());
                    revision = result.calendar().state().calendarRevision();
                }
                harness.jdbc().update("""
                        UPDATE league_fixture SET lifecycle_status = 'COMPLETED'
                        WHERE season_id = ? AND round_number = 1
                          AND execution_mode = 'PLAYER_CONTROLLED'
                        """, career.seasonId());
            }

            CareerCalendarApplicationService.AdvanceResult pendingA =
                    harness.calendar().advance(first,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 2,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            commandA);
            CareerCalendarApplicationService.AdvanceResult pendingB =
                    harness.calendar().advance(second,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 2,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            commandB);
            assertThat(pendingA.pending()).isTrue();
            assertThat(pendingB.pending()).isTrue();
            assertThat(harness.calendar().view(first).activePendingAdvance()
                    .clientCommandId()).isEqualTo(commandA);
            assertThat(harness.calendar().view(second).activePendingAdvance()
                    .clientCommandId()).isEqualTo(commandB);
            assertThat(harness.calendar().view(first).activePendingAdvance()
                    .mode()).isEqualTo(
                    CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT);
            assertThat(harness.calendar().view(first).activePendingAdvance()
                    .expectedRevision()).isEqualTo(2);
            assertThatThrownBy(() -> harness.calendar().advance(first,
                    CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 3,
                    CareerCalendarApplicationService.ADVANCE_ONE_DAY,
                    UUID.randomUUID().toString()))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type.CALENDAR_ADVANCE_ALREADY_PENDING);
            assertThat(harness.jdbc().queryForObject("""
                    SELECT COUNT(*) FROM career_calendar_advance_command
                    WHERE career_id IN (?, ?) AND command_status = 'PENDING'
                    """, Integer.class, careerA, careerB)).isEqualTo(2);
        }

        try (HikariDataSource reopened = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(reopened).load().migrate()
                    .migrationsExecuted).isZero();
            Harness harness = harness(reopened);
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
            CareerRelationalStore.CareerRow first = harness.careerStore().find(careerA)
                    .orElseThrow();
            CareerRelationalStore.CareerRow second = harness.careerStore().find(careerB)
                    .orElseThrow();
            assertThat(harness.calendar().view(second).activePendingAdvance()
                    .clientCommandId()).isEqualTo(commandB);
            assertThat(harness.calendar().view(first).activePendingAdvance()
                    .clientCommandId()).isEqualTo(commandA);
            CareerCalendarApplicationService.AdvanceResult replay =
                    harness.calendar().advance(first,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 2,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            commandA);
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.pending()).isTrue();
            assertThat(replay.commandResult().receipt().clientCommandId())
                    .isEqualTo(commandA);
        }
    }

    @Test
    void calendarSeasonLifecycleBlocksDispatchAndDateMovementExplicitly() {
        String url = "jdbc:h2:mem:career-calendar-lifecycle-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
            Map<String, String> blocked = new LinkedHashMap<>();
            blocked.put("PAUSED", "SEASON_PAUSED");
            blocked.put("BLOCKED", "ATTENTION_REQUIRED");
            blocked.put("CANCELLED", "SEASON_CANCELLED");
            blocked.put("COMPLETED", "COMPETITION_TRANSITION_REQUIRED");
            blocked.put("DRAFT", "SEASON_NOT_READY");
            blocked.put("FROZEN", "SEASON_NOT_READY");
            for (Map.Entry<String, String> entry : blocked.entrySet()) {
                harness.jdbc().update("""
                        UPDATE league_season SET lifecycle_status = ?
                        WHERE season_id = ?
                        """, entry.getKey(), career.seasonId());
                CareerCalendarApplicationService.CalendarView before =
                        harness.calendar().view(career);
                assertThat(before.blockingReason()).isEqualTo(entry.getValue());
                assertThat(before.allowedAdvanceModes()).isEmpty();
                CareerCalendarApplicationService.AdvanceResult result =
                        harness.calendar().advance(career,
                                CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,
                                before.state().calendarRevision(),
                                CareerCalendarApplicationService.ADVANCE_ONE_DAY,
                                UUID.randomUUID().toString());
                assertThat(result.stopReason()).isEqualTo(entry.getValue());
                assertThat(result.calendar().state().currentDate())
                        .isEqualTo(before.state().currentDate());
                assertThat(result.calendar().allowedAdvanceModes()).isEmpty();
            }
            verifyNoInteractions(harness.calendarJobs());
            assertThat(count(harness.jdbc(), "league_job")).isZero();
        }
    }

    @Test
    void calendarAutoJobCompletionAndReplayApplyStandingsExactlyOnce() {
        String url = "jdbc:h2:mem:career-calendar-job-integration-"
                + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            allowLeagueCalendarCoveragePastCompetitionGate(harness.competitions());
            long revision = 0;
            for (int index = 0; index < 2; index++) {
                CareerCalendarApplicationService.AdvanceResult result =
                        harness.calendar().advance(career,
                                CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, revision,
                                CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                                UUID.randomUUID().toString());
                revision = result.calendar().state().calendarRevision();
            }

            LeagueSeasonAggregate season = harness.leagueStore()
                    .loadSeason(career.seasonId());
            LeagueFixture target = season.schedule().fixtures().stream()
                    .filter(value -> value.roundNumber() == 1)
                    .filter(value -> value.executionMode()
                            == LeagueFixtureExecutionMode.FULL_AUTO)
                    .findFirst().orElseThrow();
            harness.jdbc().update("""
                    UPDATE league_fixture SET lifecycle_status = 'COMPLETED'
                    WHERE season_id = ? AND round_number = 1 AND fixture_id <> ?
                    """, career.seasonId(), target.fixtureId());

            LeagueAutomatedSeriesRunResult completion =
                    LeagueAutomatedSeriesRunnerTest.runner(season.frozenSnapshot(),
                            new LeagueAutomatedSeriesRunnerTest.FakeGameExecutor(
                                    List.of(target.firstTeamCode(),
                                            target.firstTeamCode())))
                            .run(new LeagueAutomatedSeriesRunnerInput(season, target,
                                    season.productDecisionHash()));
            LeagueAutomatedSeriesRunner controlledRunner = mock(
                    LeagueAutomatedSeriesRunner.class);
            when(controlledRunner.run(any(), any())).thenReturn(completion);
            LeagueSeasonApplicationService lifecycle =
                    new LeagueSeasonApplicationService(harness.leagueStore());
            LeagueJobCoordinator jobs = new LeagueJobCoordinator(harness.leagueStore(),
                    controlledRunner, Clock.systemUTC(), lifecycle);
            LeagueCareerCalendarService leagueCalendar =
                    new LeagueCareerCalendarService(harness.leagueStore(), jobs,
                            ignored -> true);
            CareerCalendarRelationalStore calendarStore =
                    new CareerCalendarRelationalStore(harness.jdbc(),
                            new DataSourceTransactionManager(dataSource),
                            harness.calendarTemplate());
            CareerCompetitionRules competitionRules = new CareerCompetitionRules(
                    new ObjectMapper().findAndRegisterModules());
            CareerCompetitionApplicationService competitions = spy(
                    new CareerCompetitionApplicationService(
                            new CareerCompetitionRelationalStore(harness.jdbc(),
                                    new DataSourceTransactionManager(dataSource),
                                    Clock.systemUTC(), competitionRules),
                            competitionRules));
            allowLeagueCalendarCoveragePastCompetitionGate(competitions);
            CareerCalendarApplicationService calendar =
                    new CareerCalendarApplicationService(calendarStore,
                            harness.calendarTemplate(), leagueCalendar, competitions);
            String commandId = UUID.randomUUID().toString();
            CareerCalendarApplicationService.AdvanceResult pending = calendar.advance(
                    career, CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, revision,
                    CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT, commandId);
            assertThat(pending.pending()).isTrue();
            assertThat(count(harness.jdbc(), "league_job")).isOne();

            LeagueSimulationApplicationPort.Lease lease = jobs.leaseNext(
                    "career-calendar-controlled-worker").orElseThrow();
            assertThat(jobs.execute(lease,
                    com.lolfm.simulator.SimulationInstrumentation.disabled()).status())
                    .isEqualTo(LeagueSimulationApplicationPort.Status.COMPLETED);
            verify(controlledRunner, times(1)).run(any(), any());
            assertThat(count(harness.jdbc(), "league_completion_receipt")).isOne();
            assertThat(count(harness.jdbc(), "league_outbox")).isOne();
            assertThat(harness.leagueStore().drainOutbox(10)).isOne();
            assertThat(count(harness.jdbc(), "league_standings_application")).isOne();

            CareerCalendarApplicationService.AdvanceResult completed = calendar.advance(
                    career, CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, revision,
                    CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT, commandId);
            assertThat(completed.pending()).isFalse();
            DatabaseState beforeReplay = state(harness.jdbc());
            CareerCalendarApplicationService.AdvanceResult replay = calendar.advance(
                    career, CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, revision,
                    CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT, commandId);
            assertThat(replay.replayed()).isTrue();
            assertThat(state(harness.jdbc())).isEqualTo(beforeReplay);
            assertThat(count(harness.jdbc(), "league_job")).isOne();
            assertThat(count(harness.jdbc(), "league_standings_application")).isOne();
            verify(controlledRunner, times(1)).run(any(), any());
        }
    }

    @Test
    void completedR1R2SealsCompetitionInputOnceAndReceiptTransitionSurvivesRestart() {
        String url = "jdbc:h2:file:" + temporary.resolve("competition-restart")
                .toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        String careerId;
        String matchId = "M1";
        String seriesId;
        String first;
        String second;
        String winner;
        String receiptHash = "d".repeat(64);
        String stateHash;

        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            careerId = career.careerId();
            harness.jdbc().update("""
                    UPDATE league_fixture SET lifecycle_status = 'COMPLETED'
                    WHERE season_id = ?
                    """, career.seasonId());
            harness.jdbc().update("""
                    UPDATE league_season SET lifecycle_status = 'COMPLETED'
                    WHERE season_id = ?
                    """, career.seasonId());

            CareerCalendarApplicationService.CalendarView calendar =
                    harness.calendar().view(career);
            assertThat(calendar.competition().nextCompetition().competitionId())
                    .isEqualTo("LCK_CUP");
            assertThat(calendar.competition().nextCompetition().blockingReason())
                    .isNull();
            assertThat(calendar.allowedAdvanceModes()).containsExactly(
                    CareerCalendarApplicationService.ADVANCE_ONE_DAY,
                    CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT);
            assertThat(count(harness.jdbc(), "career_competition_seed")).isEqualTo(10);
            assertThat(count(harness.jdbc(), "career_competition_fixture")).isEqualTo(40);

            CareerCalendarApplicationService.AdvanceResult advanced =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            UUID.randomUUID().toString());
            assertThat(advanced.stopReason())
                    .isEqualTo("MANAGED_COMPETITION_FIXTURE_REQUIRED");
            assertThat(count(harness.jdbc(), "career_competition_seed")).isEqualTo(30);
            assertThat(count(harness.jdbc(), "career_competition_fixture")).isEqualTo(85);

            var road = harness.competitionStore().loadAggregate(careerId, 2027,
                    "LCK_ROAD_TO_MSI");
            var fixture = road.fixtures().stream().filter(value ->
                    matchId.equals(value.matchId())).findFirst().orElseThrow();
            seriesId = fixture.seriesId();
            first = fixture.firstTeamCode();
            second = fixture.secondTeamCode();
            winner = first;
            var applied = CareerCompetitionTestSupport.applyCompletion(
                    harness.competitionStore(), careerId, 2027,
                    "LCK_ROAD_TO_MSI", matchId, seriesId, first, second, winner,
                    receiptHash);
            assertThat(applied.replayed()).isFalse();
            assertThat(applied.aggregate().revision()).isOne();
            assertThat(count(harness.jdbc(), "career_competition_application")).isOne();
            assertThat(applied.aggregate().fixtures().stream().filter(value ->
                    "M2".equals(value.matchId())).findFirst().orElseThrow()
                    .secondTeamCode()).isEqualTo(winner);
            stateHash = applied.aggregate().stateHash();
        }

        try (HikariDataSource dataSource = dataSource(url)) {
            assertThat(Flyway.configure().dataSource(dataSource).load().migrate()
                    .migrationsExecuted).isZero();
            Harness harness = harness(dataSource);
            var restored = harness.competitionStore().loadAggregate(careerId, 2027,
                    "LCK_ROAD_TO_MSI");
            assertThat(restored.stateHash()).isEqualTo(stateHash);
            var replay = CareerCompetitionTestSupport.applyCompletion(
                    harness.competitionStore(), careerId, 2027,
                    "LCK_ROAD_TO_MSI", matchId, seriesId, first, second, winner,
                    receiptHash);
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.aggregate().stateHash()).isEqualTo(stateHash);
            assertThat(count(harness.jdbc(), "career_competition_application")).isOne();
            assertThatThrownBy(() -> CareerCompetitionTestSupport.applyCompletion(
                    harness.competitionStore(), careerId, 2027,
                    "LCK_REGULAR_R3_R4", matchId, seriesId,
                    first, second, winner, receiptHash))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("COMPETITION_RECEIPT_SCOPE_MISMATCH");
        }
    }

    @Test
    void cupVerifiedResultsSealGroupsResolveFivePlusTenGraphAndOutputOnce() {
        String url = "jdbc:h2:mem:career-cup-transition-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            Map<String, Integer> strength = new LinkedHashMap<>();
            harness.jdbc().query("""
                    SELECT seed_scope, seed_number, team_code
                    FROM career_competition_seed
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP'
                    ORDER BY seed_number, seed_scope
                    """, result -> {
                int base = 12 - result.getInt(2) * 2;
                strength.put(result.getString(3), base
                        + ("CUP_GROUP_BARON".equals(result.getString(1)) ? 1 : 0));
            }, career.careerId());
            assertThat(strength).hasSize(10);

            com.lolfm.career.CareerCompetitionSeriesBindingV1 lastBinding = null;
            String lastWinner = null;
            for (int completed = 0; completed < 40; completed++) {
                CareerCompetitionRelationalStore.CycleView cycle =
                        harness.competitionStore().load(career.careerId(), 2027);
                CareerCompetitionRelationalStore.FixtureRow fixture = cycle.fixtures()
                        .stream().filter(value -> "LCK_CUP".equals(
                                value.competitionId()))
                        .filter(value -> !"COMPLETED".equals(value.lifecycleStatus()))
                        .min(java.util.Comparator.comparingInt(
                                CareerCompetitionRelationalStore.FixtureRow::matchOrder))
                        .orElseThrow();
                assertThat(fixture.lifecycleStatus()).isEqualTo("READY");
                lastBinding = harness.competitionStore().bindFixture(
                        career.careerId(), 2027, fixture.matchId(),
                        LeagueDomainTestFixtures.snapshot(), "c".repeat(64));
                lastWinner = completed < 25
                        ? (strength.get(fixture.firstTeamCode())
                        > strength.get(fixture.secondTeamCode())
                        ? fixture.firstTeamCode() : fixture.secondTeamCode())
                        : fixture.firstTeamCode();
                CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(
                        harness.competitionStore(), lastBinding, lastWinner);
                if (completed == 24) {
                    assertThat(harness.competitionStore().cupStandings(
                            career.careerId(), 2027)).hasSize(10);
                    assertThat(harness.competitionStore().currentSeeds(
                            career.careerId(), 2027))
                            .filteredOn(value -> "CUP_PLAY_IN_SEED".equals(
                                    value.seedScope())).hasSize(6);
                    assertThat(harness.competitionStore().currentSeeds(
                            career.careerId(), 2027))
                            .filteredOn(value -> "CUP_PLAYOFF_SEED".equals(
                                    value.seedScope())).hasSize(3);
                }
            }

            CareerCompetitionRelationalStore.CycleView completedCycle =
                    harness.competitionStore().load(career.careerId(), 2027);
            assertThat(completedCycle.competitions()).filteredOn(value ->
                    "LCK_CUP".equals(value.competitionId()))
                    .singleElement().extracting(
                            CareerCompetitionRelationalStore.InstanceRow::lifecycleStatus)
                    .isEqualTo("COMPLETED");
            assertThat(completedCycle.outputs()).filteredOn(value ->
                    "LCK_CUP".equals(value.competitionId()))
                    .extracting(CareerCompetitionRelationalStore.OutputRow::outputId)
                    .contains("FIRST_STAND_LCK_SEED_1",
                            "FIRST_STAND_LCK_SEED_2");
            assertThat(completedCycle.outputs()).filteredOn(value ->
                    value.outputId().startsWith("FIRST_STAND_LCK_SEED_"))
                    .hasSize(2);
            assertThat(count(harness.jdbc(), "career_competition_application"))
                    .isEqualTo(40);
            assertThat(count(harness.jdbc(), "career_competition_result_detail"))
                    .isEqualTo(40);
            assertThat(count(harness.jdbc(), "career_competition_opponent_choice"))
                    .isEqualTo(3);

            var replay = CareerCompetitionTestSupport
                    .applySyntheticVerifiedCompletion(harness.competitionStore(),
                            Objects.requireNonNull(lastBinding),
                            Objects.requireNonNull(lastWinner));
            assertThat(replay.replayed()).isTrue();
            assertThat(count(harness.jdbc(), "career_competition_application"))
                    .isEqualTo(40);
            assertThat(count(harness.jdbc(), "career_competition_result_detail"))
                    .isEqualTo(40);
        }
    }

    @Test
    void competitionV2CertifiesMaterializedGraphAndSelectorOrigin() {
        String url = "jdbc:h2:mem:career-competition-v2-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            harness.jdbc().update("""
                    UPDATE league_fixture SET lifecycle_status = 'COMPLETED'
                    WHERE season_id = ?
                    """, career.seasonId());
            harness.jdbc().update("""
                    UPDATE league_season SET lifecycle_status = 'COMPLETED'
                    WHERE season_id = ?
                    """, career.seasonId());
            CareerCalendarApplicationService.AdvanceResult blocked =
                    harness.calendar().advance(career,
                            CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0,
                            CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT,
                            UUID.randomUUID().toString());
            assertThat(blocked.stopReason())
                    .isEqualTo("MANAGED_COMPETITION_FIXTURE_REQUIRED");
            assertThat(harness.competitions().gate(career, 2027,
                    LocalDate.of(2027, 4, 1), "LCK_REGULAR_R1_R2", null).stopReason())
                    .isEqualTo("MANAGED_COMPETITION_FIXTURE_REQUIRED");

            assertThat(harness.jdbc().queryForList("""
                    SELECT DISTINCT first_selector_type FROM career_competition_fixture
                    WHERE career_id = ? AND competition_id = 'LCK_REGULAR_R3_R4'
                    """, String.class, career.careerId()))
                    .containsExactly("R1_R2_RANK");
            assertThat(harness.jdbc().queryForList("""
                    SELECT DISTINCT second_selector_type FROM career_competition_fixture
                    WHERE career_id = ? AND competition_id = 'LCK_REGULAR_R3_R4'
                    """, String.class, career.careerId()))
                    .containsExactly("R1_R2_RANK");
            assertThat(harness.jdbc().queryForObject("""
                    SELECT hash_algorithm FROM career_competition_cycle
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, String.class, career.careerId()))
                    .isEqualTo(CareerCompetitionRelationalStore.CYCLE_HASH_ALGORITHM);

            LocalDate originalDate = harness.jdbc().queryForObject("""
                    SELECT scheduled_date FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, LocalDate.class, career.careerId());
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET scheduled_date = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, originalDate.plusDays(1), career.careerId());
            assertThatThrownBy(() -> harness.competitionStore().load(
                    career.careerId(), 2027)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET scheduled_date = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, originalDate, career.careerId());

            String originalSelector = harness.jdbc().queryForObject("""
                    SELECT first_selector_value FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, String.class, career.careerId());
            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_fixture SET first_selector_value = 'BARON:9'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET first_selector_value = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, originalSelector, career.careerId());

            String originalTeam = harness.jdbc().queryForObject("""
                    SELECT first_team_code FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, String.class, career.careerId());
            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_fixture SET first_team_code = 'TAMPER'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET first_team_code = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, originalTeam, career.careerId());

            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_fixture SET match_order = 2
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET match_order = 1
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, career.careerId());

            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_fixture SET series_format = 'BO5'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET series_format = 'BO3'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, career.careerId());

            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_fixture SET hard_fearless = FALSE
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET hard_fearless = TRUE
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, career.careerId());

            var road = harness.competitionStore().loadAggregate(career.careerId(), 2027,
                    "LCK_ROAD_TO_MSI");
            var match = road.fixtures().getFirst();
            String cycleBefore = harness.competitionStore().load(career.careerId(), 2027)
                    .stateHash();
            CareerCompetitionTestSupport.applyCompletion(harness.competitionStore(),
                    career.careerId(), 2027,
                    "LCK_ROAD_TO_MSI", match.matchId(), match.seriesId(),
                    match.firstTeamCode(), match.secondTeamCode(), match.firstTeamCode(),
                    "d".repeat(64));
            String cycleAfter = harness.competitionStore().load(career.careerId(), 2027)
                    .stateHash();
            assertThat(cycleAfter).isNotEqualTo(cycleBefore);

            String originalWinner = harness.jdbc().queryForObject("""
                    SELECT winner_team_code FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, String.class, career.careerId());
            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_fixture SET winner_team_code = 'TAMPER'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET winner_team_code = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, originalWinner, career.careerId());

            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET completion_receipt_hash = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, "e".repeat(64), career.careerId());
            assertThatThrownBy(() -> harness.competitionStore().load(
                    career.careerId(), 2027)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
            harness.jdbc().update("""
                    UPDATE career_competition_fixture SET completion_receipt_hash = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI' AND match_id = 'M1'
                    """, "d".repeat(64), career.careerId());

            var directQualifier = harness.competitionStore().loadAggregate(
                    career.careerId(), 2027, "LCK_ROAD_TO_MSI").fixtures().stream()
                    .filter(value -> "M3".equals(value.matchId())).findFirst().orElseThrow();
            CareerCompetitionTestSupport.applyCompletion(harness.competitionStore(),
                    career.careerId(), 2027,
                    "LCK_ROAD_TO_MSI", directQualifier.matchId(),
                    directQualifier.seriesId(), directQualifier.firstTeamCode(),
                    directQualifier.secondTeamCode(), directQualifier.firstTeamCode(),
                    "f".repeat(64));
            String outputTeam = harness.jdbc().queryForObject("""
                    SELECT team_code FROM career_competition_output
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND output_id = 'MSI_LCK_SEED_1'
                    """, String.class, career.careerId());
            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_output SET team_code = 'TAMPER'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND output_id = 'MSI_LCK_SEED_1'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_output SET team_code = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND output_id = 'MSI_LCK_SEED_1'
                    """, outputTeam, career.careerId());

            String roadHash = harness.jdbc().queryForObject("""
                    SELECT state_hash FROM career_competition_instance
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI'
                    """, String.class, career.careerId());
            assertCompetitionTamperRejected(harness, career.careerId(), """
                    UPDATE career_competition_instance SET state_hash =
                      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI'
                    """);
            harness.jdbc().update("""
                    UPDATE career_competition_instance SET state_hash = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_ROAD_TO_MSI'
                    """, roadHash, career.careerId());

            String currentCycleHash = harness.competitionStore().load(
                    career.careerId(), 2027).stateHash();
            harness.jdbc().update("""
                    UPDATE career_competition_cycle SET state_hash = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, cycleBefore, career.careerId());
            assertThatThrownBy(() -> harness.competitionStore().load(
                    career.careerId(), 2027)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("CAREER_COMPETITION_CYCLE_INTEGRITY_FAILURE");
            harness.jdbc().update("""
                    UPDATE career_competition_cycle SET state_hash = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, currentCycleHash, career.careerId());

            harness.jdbc().update("""
                    INSERT INTO career_competition_fixture(
                      career_id, calendar_season_year, competition_id, match_id,
                      fixture_id, series_id, scheduled_date, schedule_status,
                      series_format, hard_fearless, first_selector_type,
                      first_selector_value, second_selector_type, second_selector_value,
                      first_team_code, second_team_code, execution_mode, fixture_root_seed,
                      seed_algorithm, lifecycle_status, winner_output_ids, loser_output_ids,
                      winner_team_code, loser_team_code, completion_receipt_hash, revision,
                      stage_id, match_order, group_id, group_point_value,
                      selection_right_owner, opponent_choice_policy, side_selection_policy)
                    SELECT career_id, calendar_season_year, competition_id,
                      match_id || '_DUP', fixture_id || '_DUP', series_id || '_DUP',
                      scheduled_date, schedule_status, series_format, hard_fearless,
                      first_selector_type, first_selector_value, second_selector_type,
                      second_selector_value, first_team_code, second_team_code,
                      execution_mode, fixture_root_seed, seed_algorithm, lifecycle_status,
                      winner_output_ids, loser_output_ids, winner_team_code,
                      loser_team_code, completion_receipt_hash, revision, stage_id,
                      match_order, group_id, group_point_value, selection_right_owner,
                      opponent_choice_policy, side_selection_policy
                    FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, career.careerId());
            assertThatThrownBy(() -> harness.competitionStore().load(
                    career.careerId(), 2027)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
            harness.jdbc().update("""
                    DELETE FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2_DUP'
                    """, career.careerId());
            assertThat(harness.competitionStore().load(career.careerId(), 2027)
                    .stateHash()).isEqualTo(currentCycleHash);

            harness.jdbc().update("""
                    DELETE FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP' AND match_id = 'GB_B1_E2'
                    """, career.careerId());
            assertThatThrownBy(() -> harness.competitionStore().load(
                    career.careerId(), 2027)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
        }
    }

    @Test
    void futureCupPersistsOnlySealedPriorGameRankingProvenance() {
        String url = "jdbc:h2:mem:career-future-cup-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            List<CareerCompetitionAggregate.SeededTeam> ranking =
                    java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(seed ->
                            new CareerCompetitionAggregate.SeededTeam(seed,
                                    "T%02d".formatted(seed), 20 - seed, seed,
                                    40 - seed, seed)).toList();
            CareerCompetitionRules.PriorLckRanking prior =
                    new CareerCompetitionRules.PriorLckRanking(career.careerId(),
                            2027, "SEALED", "9".repeat(64), ranking);

            assertThatThrownBy(() -> harness.competitionStore().initializeFuture(
                    career.careerId(), 2028, 2, prior))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("UNVERIFIED_PRIOR_LCK_RANKING_REJECTED");
            String sourceSeasonId = career.seasonId();
            String stateHash = CareerCompetitionRelationalStore.finalRankingStateHash(
                    career.careerId(), 2027, 1, sourceSeasonId, ranking);
            harness.jdbc().update("""
                    INSERT INTO career_lck_final_ranking_snapshot(
                      career_id, calendar_season_year, season_ordinal,
                      source_season_id, lifecycle_status, state_hash, created_at)
                    VALUES (?, 2027, 1, ?, 'SEALED', ?, CURRENT_TIMESTAMP)
                    """, career.careerId(), sourceSeasonId, stateHash);
            ranking.forEach(value -> harness.jdbc().update("""
                    INSERT INTO career_lck_final_ranking_row(
                      career_id, calendar_season_year, rank_number, team_code,
                      series_wins, series_losses, game_wins, game_losses)
                    VALUES (?, 2027, ?, ?, ?, ?, ?, ?)
                    """, career.careerId(), value.seed(), value.teamCode(),
                    value.seriesWins(), value.seriesLosses(), value.gameWins(),
                    value.gameLosses()));

            CareerCompetitionRelationalStore.CycleView created =
                    harness.competitionStore().initializeFuture(career.careerId(),
                            2028);
            assertThat(created.seasonOrdinal()).isEqualTo(2);
            assertThat(created.initializationPolicyId())
                    .isEqualTo(CareerCompetitionRules.FUTURE_CUP_POLICY);
            assertThat(created.initializationInputHash()).matches("[0-9a-f]{64}");
            assertThat(harness.jdbc().queryForList("""
                    SELECT team_code FROM career_competition_seed
                    WHERE career_id = ? AND calendar_season_year = 2028
                      AND competition_id = 'LCK_CUP'
                    ORDER BY seed_scope, seed_number
                    """, String.class, career.careerId()))
                    .containsExactly("T01", "T03", "T06", "T07", "T10",
                            "T02", "T04", "T05", "T08", "T09");

            CareerCompetitionRelationalStore.CycleView restored = harness(dataSource)
                    .competitionStore().load(career.careerId(), 2028);
            assertThat(restored.stateHash()).isEqualTo(created.stateHash());
            assertThat(restored.initializationInputHash())
                    .isEqualTo(created.initializationInputHash());
            assertThatThrownBy(() -> harness.competitionStore().initialize(
                    career.careerId(), 2028))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("CAREER_COMPETITION_INITIALIZATION_CONFLICT");
        }
    }

    @Test
    void provenLegacyCompetitionUpgradesButTamperedPolicyIdentityStaysBlocked() {
        String url = "jdbc:h2:mem:career-legacy-competition-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            harness.jdbc().update("""
                    DELETE FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP'
                    """, career.careerId());
            harness.jdbc().update("""
                    DELETE FROM career_competition_seed
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'LCK_CUP'
                    """, career.careerId());
            harness.jdbc().update("""
                    DELETE FROM career_competition_instance
                    WHERE career_id = ? AND calendar_season_year = 2027
                      AND competition_id = 'KESPA_CUP'
                    """, career.careerId());

            for (Map<String, Object> instance : harness.jdbc().queryForList("""
                    SELECT competition_id, lifecycle_status, blocking_reason,
                      source_input_hash, revision
                    FROM career_competition_instance
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, career.careerId())) {
                String competitionId = (String) instance.get("COMPETITION_ID");
                String lifecycle = (String) instance.get("LIFECYCLE_STATUS");
                String blocker = (String) instance.get("BLOCKING_REASON");
                String sourceInput = (String) instance.get("SOURCE_INPUT_HASH");
                long revision = ((Number) instance.get("REVISION")).longValue();
                harness.jdbc().update("""
                        UPDATE career_competition_instance
                        SET hash_algorithm = ?, state_hash = ?
                        WHERE career_id = ? AND calendar_season_year = 2027
                          AND competition_id = ?
                        """, CareerCompetitionRelationalStore
                                .LEGACY_INSTANCE_HASH_ALGORITHM,
                        legacyCompetitionInstanceHash(career.careerId(), 2027,
                                competitionId, lifecycle, blocker, sourceInput, revision),
                        career.careerId(), competitionId);
            }
            String legacyCycleHash = legacyCompetitionCycleHash(career.careerId(),
                    2027, 0, null, null);
            harness.jdbc().update("""
                    UPDATE career_competition_cycle
                    SET cycle_schema = 'CAREER_COMPETITION_CYCLE_V1',
                      rule_version = 'tampered-rule-version',
                      rule_resource_hash =
                        '64acfab316162ca7f17c898c434b7ecce496f085370ff45012a83332d445b770',
                      game_policy_version = 'CAREER_COMPETITION_GAME_POLICY_V1',
                      hash_algorithm = ?, state_hash = ?
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, CareerCompetitionRelationalStore.LEGACY_CYCLE_HASH_ALGORITHM,
                    legacyCycleHash, career.careerId());

            harness.competitionStore().recoverLegacyCompetitions();
            assertThat(harness.jdbc().queryForObject("""
                    SELECT hash_algorithm FROM career_competition_cycle
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, String.class, career.careerId()))
                    .isEqualTo(CareerCompetitionRelationalStore
                            .LEGACY_CYCLE_HASH_ALGORITHM);
            assertThatThrownBy(() -> harness.competitionStore().load(
                    career.careerId(), 2027)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("COMPETITION_STATE_MIGRATION_REQUIRED");

            harness.jdbc().update("""
                    UPDATE career_competition_cycle
                    SET rule_version = 'lck-career-competition-rules-2026-v1'
                    WHERE career_id = ? AND calendar_season_year = 2027
                    """, career.careerId());
            harness.competitionStore().recoverLegacyCompetitions();
            CareerCompetitionRelationalStore.CycleView upgraded =
                    harness.competitionStore().load(career.careerId(), 2027);
            assertThat(upgraded.hashAlgorithm())
                    .isEqualTo(CareerCompetitionRelationalStore.CYCLE_HASH_ALGORITHM);
            assertThat(upgraded.competitions()).hasSize(12);
            assertThat(upgraded.fixtures()).filteredOn(value ->
                    "LCK_CUP".equals(value.competitionId())).hasSize(40);
        }
    }

    private static void assertCompetitionTamperRejected(
            Harness harness, String careerId, String sql
    ) {
        harness.jdbc().update(sql, careerId);
        assertThatThrownBy(() -> harness.competitionStore().load(careerId, 2027))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
    }

    private static String legacyCompetitionInstanceHash(
            String careerId, int year, String competitionId, String lifecycle,
            String blocker, String sourceInputHash, long revision
    ) {
        return plainSha256("schema=CAREER_COMPETITION_INSTANCE_V1\ncareerId="
                + careerId + "\nseasonYear=" + year + "\ncompetitionId="
                + competitionId + "\nlifecycleStatus=" + lifecycle
                + "\nblockingReason=" + Objects.toString(blocker, "")
                + "\nsourceInputHash=" + Objects.toString(sourceInputHash, "")
                + "\nrevision=" + revision + '\n');
    }

    private static String legacyCompetitionCycleHash(
            String careerId, int year, long revision, String importHash,
            Long standingsRevision
    ) {
        return plainSha256("schema=CAREER_COMPETITION_CYCLE_V1\ncareerId="
                + careerId + "\nseasonYear=" + year
                + "\nruleVersion=lck-career-competition-rules-2026-v1"
                + "\nruleResourceHash="
                + "64acfab316162ca7f17c898c434b7ecce496f085370ff45012a83332d445b770"
                + "\ngamePolicyVersion=CAREER_COMPETITION_GAME_POLICY_V1"
                + "\nprojectionPolicy=SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1"
                + "\nr3r4AllocationPolicy="
                + "LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1"
                + "\nrevision=" + revision + "\nr1r2ImportHash="
                + Objects.toString(importHash, "") + "\nr1r2StandingsRevision="
                + Objects.toString(standingsRevision, "") + '\n');
    }

    private static String plainSha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Test
    void calendarGetIsReadOnlyAndLegacyPendingOpensWithExplicitRecoveryBlocker() {
        String url = "jdbc:h2:mem:career-calendar-read-only-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        try (HikariDataSource dataSource = dataSource(url)) {
            Flyway.configure().dataSource(dataSource).load().migrate();
            Harness harness = harness(dataSource);
            CareerRelationalStore.CareerRow career = harness.careers().create(
                    request(UUID.randomUUID().toString())).career().career();
            DatabaseState before = state(harness.jdbc());
            harness.calendar().view(career);
            harness.calendar().view(career);
            assertThat(state(harness.jdbc())).isEqualTo(before);
            assertThat(count(harness.jdbc(), "league_job")).isZero();

            String commandId = UUID.randomUUID().toString();
            String mode = CareerCalendarApplicationService.ADVANCE_TO_NEXT_EVENT;
            String payload = harness.calendarTemplate().advancePayloadHash(
                    career.careerId(), 0, mode);
            harness.jdbc().update("""
                    INSERT INTO career_calendar_advance_command(
                      client_command_id, career_id, command_schema, payload_hash,
                      command_status, request_mode, request_expected_revision,
                      background_required, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PENDING', NULL, NULL, FALSE,
                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, commandId, career.careerId(),
                    CareerCalendarTemplate.ADVANCE_COMMAND_SCHEMA, payload);
            DatabaseState pendingBefore = state(harness.jdbc());
            CareerCalendarApplicationService.CalendarView view =
                    harness.calendar().view(career);
            assertThat(view.activePendingAdvance()).isNull();
            assertThat(view.advanceRecoveryStatus())
                    .isEqualTo("LEGACY_PENDING_RECONCILIATION_REQUIRED");
            assertThat(view.blockingReason())
                    .isEqualTo("LEGACY_PENDING_RECONCILIATION_REQUIRED");
            assertThat(view.allowedAdvanceModes()).isEmpty();
            assertThat(state(harness.jdbc())).isEqualTo(pendingBefore);
            assertThatThrownBy(() -> harness.calendar().advance(career,
                    CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA, 0, mode, commandId))
                    .isInstanceOf(CareerException.class)
                    .extracting(error -> ((CareerException) error).type())
                    .isEqualTo(CareerException.Type
                            .CALENDAR_LEGACY_PENDING_RECONCILIATION_REQUIRED);
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
        CareerCompetitionRules competitionRules = new CareerCompetitionRules(
                new ObjectMapper().findAndRegisterModules());
        CareerCompetitionRelationalStore competitionStore =
                new CareerCompetitionRelationalStore(jdbc, transactionManager,
                        Clock.systemUTC(), competitionRules);
        calendarStore.recoverLegacyStates();
        competitionStore.recoverLegacyCompetitions();
        CareerCompetitionApplicationService competitions = spy(
                new CareerCompetitionApplicationService(
                        competitionStore,
                        competitionRules));
        LeagueSimulationApplicationPort calendarJobs = mock(
                LeagueSimulationApplicationPort.class);
        LeagueCareerCalendarService calendarLeague = new LeagueCareerCalendarService(
                leagueStore, calendarJobs, ignored -> true);
        CareerCalendarApplicationService calendar =
                new CareerCalendarApplicationService(calendarStore, calendarTemplate,
                        calendarLeague, competitions);
        CareerApplicationService careers = new CareerApplicationService(careerStore,
                provisioning, read, TeamPlayerInformationCatalog.loadDefault(), calendar);
        return new Harness(jdbc, leagueStore, careerStore, provisioning, bindings,
                playerSeries, careers, calendar, calendarTemplate, competitionStore,
                competitions, calendarJobs);
    }

    /** Keeps legacy R1-R2 Calendar tests scoped to their original League behavior. */
    private static void allowLeagueCalendarCoveragePastCompetitionGate(
            CareerCompetitionApplicationService competitions
    ) {
        doReturn(CareerCompetitionApplicationService.CompetitionGate.clear())
                .when(competitions).gate(any(), anyInt(), any(), any(), any());
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
        tables.put("career_competition_cycle", rows(jdbc,
                "career_competition_cycle", "career_id, calendar_season_year"));
        tables.put("career_competition_instance", rows(jdbc,
                "career_competition_instance",
                "career_id, calendar_season_year, competition_id"));
        tables.put("career_competition_seed", rows(jdbc,
                "career_competition_seed",
                "career_id, calendar_season_year, competition_id, seed_scope, seed_number"));
        tables.put("career_competition_fixture", rows(jdbc,
                "career_competition_fixture",
                "career_id, calendar_season_year, competition_id, match_id"));
        tables.put("career_competition_output", rows(jdbc,
                "career_competition_output", "career_id, calendar_season_year, output_id"));
        tables.put("career_competition_application", rows(jdbc,
                "career_competition_application", "receipt_hash"));
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
            CareerCompetitionRelationalStore competitionStore,
            CareerCompetitionApplicationService competitions,
            LeagueSimulationApplicationPort calendarJobs
    ) {}

    private record DatabaseState(
            Map<String, List<Map<String, Object>>> tables
    ) {}
}

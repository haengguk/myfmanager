package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CareerApplicationService;
import com.lolfm.career.CareerException;
import com.lolfm.career.CareerIdentity;
import com.lolfm.career.CareerRelationalStore;
import com.lolfm.dto.CareerApiV1Dtos;
import com.lolfm.reference.TeamPlayerInformationCatalog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
                    .migrationsExecuted).isEqualTo(4);
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
            durableState = state(harness.jdbc(), seasonId);
            harness.careers().get(careerId);
            harness.careers().list();
            assertThat(state(harness.jdbc(), seasonId)).isEqualTo(durableState);
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
            assertThat(state(harness.jdbc(), seasonId)).isEqualTo(durableState);

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

    private Harness harness(HikariDataSource dataSource) {
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
        LeagueApiV1ResponseMapper leagueMapper = new LeagueApiV1ResponseMapper(
                leagueStore, seasons, bindings, playerSeries);
        LeagueCareerSeasonReadService read = new LeagueCareerSeasonReadService(leagueMapper);
        CareerRelationalStore careerStore = new CareerRelationalStore(
                jdbc, transactionManager);
        CareerApplicationService careers = new CareerApplicationService(careerStore,
                provisioning, read, TeamPlayerInformationCatalog.loadDefault());
        return new Harness(jdbc, leagueStore, careerStore, provisioning, bindings, careers);
    }

    private static CareerApiV1Dtos.CreateRequest request(String commandId) {
        return new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "GEN 장기 저장", "김 감독", "GEN", commandId);
    }

    private static DatabaseState state(JdbcTemplate jdbc, String seasonId) {
        return new DatabaseState(
                jdbc.queryForMap("""
                        SELECT lifecycle_status, lifecycle_revision, revision
                        FROM league_season WHERE season_id = ?
                        """, seasonId),
                jdbc.queryForList("""
                        SELECT team_code, series_wins, series_losses, game_wins, game_losses
                        FROM league_standing WHERE season_id = ? ORDER BY team_code
                        """, seasonId),
                jdbc.queryForList("""
                        SELECT fixture_id, lifecycle_status, revision
                        FROM league_fixture WHERE season_id = ? ORDER BY fixture_id
                        """, seasonId),
                jdbc.queryForList("""
                        SELECT binding_hash, lifecycle_status, revision
                        FROM league_player_binding WHERE season_id = ? ORDER BY binding_hash
                        """, seasonId));
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
            CareerApplicationService careers
    ) {}

    private record DatabaseState(
            Map<String, Object> season,
            List<Map<String, Object>> standings,
            List<Map<String, Object>> fixtures,
            List<Map<String, Object>> bindings
    ) {}
}

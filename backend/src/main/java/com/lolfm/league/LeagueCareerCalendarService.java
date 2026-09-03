package com.lolfm.league;

import com.lolfm.career.CareerCalendarLeaguePort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Calendar integration over structured League rows; it never interprets display text. */
@Service
public final class LeagueCareerCalendarService implements CareerCalendarLeaguePort {
    private static final Set<String> AUTO_PENDING = Set.of(
            "SCHEDULED", "QUEUED", "LEASED", "RUNNING", "RETRY_PENDING",
            "COMPLETION_PENDING_VERIFICATION");
    private final LeagueRelationalStore store;
    private final LeagueSimulationApplicationPort jobs;
    private final LeagueBackgroundExecutionPort background;

    LeagueCareerCalendarService(
            LeagueRelationalStore store,
            LeagueSimulationApplicationPort jobs,
            LeagueBackgroundExecutionPort background
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.background = Objects.requireNonNull(background, "background");
    }

    @Override
    public SeasonProjection load(String leagueId, String seasonId) {
        List<String> lifecycle = store.jdbc().query("""
                SELECT lifecycle_status FROM league_season
                WHERE league_id = ? AND season_id = ?
                """, (result, ignored) -> result.getString(1), leagueId, seasonId);
        if (lifecycle.size() != 1) {
            throw new IllegalStateException("CAREER_CALENDAR_LEAGUE_SEASON_NOT_FOUND");
        }
        List<FixtureProjection> fixtures = store.jdbc().query("""
                SELECT f.fixture_id, f.round_number, f.execution_mode,
                       f.lifecycle_status, f.first_team_code, f.second_team_code,
                       f.fixture_root_seed, f.bound_series_id, j.lifecycle_status,
                       CASE WHEN EXISTS (
                         SELECT 1 FROM league_outbox o
                         WHERE o.season_id = f.season_id
                           AND o.fixture_id = f.fixture_id
                           AND o.lifecycle_status = 'PENDING'
                       ) THEN TRUE ELSE FALSE END,
                       b.lifecycle_status, c.series_status
                FROM league_fixture f
                LEFT JOIN league_job j
                  ON j.season_id = f.season_id AND j.fixture_id = f.fixture_id
                LEFT JOIN league_player_binding b
                  ON b.season_id = f.season_id AND b.fixture_id = f.fixture_id
                LEFT JOIN league_player_series_checkpoint c
                  ON c.binding_hash = b.binding_hash
                WHERE f.season_id = ?
                ORDER BY f.round_number, f.fixture_id
                """, (result, ignored) -> new FixtureProjection(
                result.getString(1), result.getInt(2), result.getString(3),
                result.getString(4), result.getString(5), result.getString(6),
                result.getLong(7), result.getString(8), result.getString(9),
                result.getBoolean(10), result.getString(11), result.getString(12)),
                seasonId);
        if (fixtures.size() != LeagueV1OperationalConfiguration.defaults()
                .doubleRoundRobinFixtureCount()) {
            throw new IllegalStateException("CAREER_CALENDAR_R1_R2_FIXTURE_COUNT_MISMATCH");
        }
        return new SeasonProjection(leagueId, seasonId, lifecycle.getFirst(), fixtures);
    }

    @Override
    public GateResult gateAndDispatch(String seasonId, List<String> fixtureIds) {
        if (fixtureIds.isEmpty()) return GateResult.clear();
        SeasonProjection season = loadBySeasonId(seasonId);
        Map<String, FixtureProjection> indexed = new HashMap<>();
        season.fixtures().forEach(fixture -> indexed.put(fixture.fixtureId(), fixture));
        ArrayList<FixtureProjection> selected = new ArrayList<>();
        for (String fixtureId : fixtureIds) {
            FixtureProjection fixture = indexed.get(fixtureId);
            if (fixture == null || !selected.add(fixture)) {
                throw new IllegalStateException("CAREER_CALENDAR_FIXTURE_IDENTITY_MISMATCH");
            }
        }

        FixtureProjection attention = selected.stream()
                .filter(LeagueCareerCalendarService::needsAttention).findFirst().orElse(null);
        if (attention != null) {
            return new GateResult("ATTENTION_REQUIRED", false, false,
                    attention.fixtureId(), attention.boundSeriesId());
        }

        boolean backgroundRequired = false;
        for (FixtureProjection fixture : selected) {
            if (!"FULL_AUTO".equals(fixture.executionMode())
                    || terminal(fixture.fixtureStatus())) {
                continue;
            }
            if (fixture.jobStatus() == null) {
                jobs.dispatchFullAutoFixture(seasonId, fixture.fixtureId());
            }
            backgroundRequired = true;
        }
        backgroundRequired = backgroundRequired || selected.stream().anyMatch(value ->
                "FULL_AUTO".equals(value.executionMode())
                        && (AUTO_PENDING.contains(value.fixtureStatus())
                        || value.pendingOutbox()
                        || value.jobStatus() != null
                        && !"COMPLETED".equals(value.jobStatus())));

        FixtureProjection managed = selected.stream().filter(value ->
                "PLAYER_CONTROLLED".equals(value.executionMode())
                        && !"COMPLETED".equals(value.fixtureStatus())).findFirst().orElse(null);
        if (managed != null) {
            return new GateResult("MANAGED_FIXTURE_REQUIRED", false,
                    backgroundRequired, managed.fixtureId(), managed.boundSeriesId());
        }
        if (backgroundRequired || selected.stream().anyMatch(value ->
                "FULL_AUTO".equals(value.executionMode())
                        && !"COMPLETED".equals(value.fixtureStatus()))) {
            return new GateResult("AUTO_FIXTURES_PENDING", true, true, null, null);
        }
        return GateResult.clear();
    }

    @Override
    public boolean wakeBackground(String commandId) {
        return background.submit("career-calendar:" + commandId);
    }

    private SeasonProjection loadBySeasonId(String seasonId) {
        List<String> leagueIds = store.jdbc().query(
                "SELECT league_id FROM league_season WHERE season_id = ?",
                (result, ignored) -> result.getString(1), seasonId);
        if (leagueIds.size() != 1) {
            throw new IllegalStateException("CAREER_CALENDAR_LEAGUE_SEASON_NOT_FOUND");
        }
        return load(leagueIds.getFirst(), seasonId);
    }

    private static boolean terminal(String status) {
        return "COMPLETED".equals(status);
    }

    private static boolean needsAttention(FixtureProjection fixture) {
        return "BLOCKED".equals(fixture.fixtureStatus())
                || "CANCELLED".equals(fixture.fixtureStatus())
                || "PLAYER_SERIES_RESTART_REQUIRED".equals(fixture.fixtureStatus())
                || "BLOCKED".equals(fixture.jobStatus())
                || "CANCELLED".equals(fixture.jobStatus())
                || "BLOCKED".equals(fixture.bindingStatus())
                || "PLAYER_SERIES_RESTART_REQUIRED".equals(fixture.bindingStatus());
    }
}

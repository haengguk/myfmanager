package com.lolfm.league;

import com.lolfm.controller.LeagueApiV1Exception;
import com.lolfm.dto.LeagueApiV1Dtos;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Public API application boundary. Controllers never touch domain repositories or JDBC. */
@Service
public final class LeagueApiV1Facade {
    private final LeagueProductionSnapshotProvider snapshots;
    private final LeagueSeasonApplicationService seasons;
    private final LeagueSimulationApplicationPort jobs;
    private final LeaguePlayerSeriesHandoffService playerSeries;
    private final LeaguePlayerSeriesBindingPort bindings;
    private final LeagueRelationalStore store;
    private final LeagueApiCommandStore commands;
    private final LeagueApiV1ResponseMapper mapper;
    private final LeagueBackgroundExecutionPort background;

    LeagueApiV1Facade(
            LeagueProductionSnapshotProvider snapshots,
            LeagueSeasonApplicationService seasons,
            LeagueSimulationApplicationPort jobs,
            LeaguePlayerSeriesHandoffService playerSeries,
            LeaguePlayerSeriesBindingPort bindings,
            LeagueRelationalStore store,
            LeagueApiCommandStore commands,
            LeagueApiV1ResponseMapper mapper,
            LeagueBackgroundExecutionPort background
    ) {
        this.snapshots = snapshots;
        this.seasons = seasons;
        this.jobs = jobs;
        this.playerSeries = playerSeries;
        this.bindings = bindings;
        this.store = store;
        this.commands = commands;
        this.mapper = mapper;
        this.background = background;
    }

    public HttpResult<LeagueApiV1Dtos.SeasonResponse> create(
            LeagueApiV1Dtos.CreateRequest request
    ) {
        return guard(() -> {
            LeagueSeasonMode mode = parseMode(request.seasonMode());
            var teamCodes = snapshots.currentTeamCodes();
            if (mode == LeagueSeasonMode.HYBRID_MANAGER) {
                if (request.managedTeamCode() == null
                        || !teamCodes.contains(request.managedTeamCode())) {
                    throw invalid("LEAGUE_INVALID_MANAGED_TEAM",
                            "Hybrid Season에는 현재 10팀 중 관리 팀이 필요합니다.");
                }
            } else if (request.managedTeamCode() != null) {
                throw invalid("LEAGUE_SPECTATOR_MANAGED_TEAM_FORBIDDEN",
                        "Spectator Season에는 관리 팀을 지정할 수 없습니다.");
            }
            String leagueId = LeagueIdentity.leagueId(request.leagueKey());
            String seasonId = LeagueIdentity.seasonId(leagueId, request.seasonKey());
            long rootSeed = Long.parseLong(request.seasonRootSeed());
            String payload = hash("CREATE", request.schemaVersion(), leagueId, seasonId,
                    request.seasonMode(), value(request.managedTeamCode()),
                    request.seasonRootSeed());
            LeagueApiCommandStore.Result command = commands.execute(
                    request.clientCommandId(), "CREATE_SEASON", payload,
                    leagueId, seasonId, null, () -> {
                        if (store.findSeason(seasonId).isPresent()) {
                            throw LeagueApiV1Exception.conflict(
                                    "LEAGUE_STABLE_KEY_CONFLICT",
                                    "이미 사용된 League 또는 Season key입니다.",
                                    seasons.view(seasonId).lifecycleRevision(),
                                    seasons.view(seasonId).status().name());
                        }
                        LeagueSeasonFrozenSnapshot snapshot = snapshots.currentSnapshot(teamCodes);
                        LeagueSeasonAggregate season = LeagueSeasonAggregate.create(
                                leagueId, seasonId, mode, request.managedTeamCode(),
                                request.managedTeamCode() == null ? null
                                        : snapshot.teamSnapshotIdentity(
                                                request.managedTeamCode()),
                                snapshot, rootSeed, LeagueSchedulePolicy.productionDefault());
                        seasons.createFrozen(season);
                        seasons.ready(seasonId, 0);
                        return HttpStatus.CREATED.value();
                    });
            int status = command.replayed() ? HttpStatus.OK.value() : command.httpStatus();
            return new HttpResult<>(status, new LeagueApiV1Dtos.SeasonResponse(
                    LeagueApiV1Dtos.SEASON_SCHEMA, command.replayed(),
                    mapper.season(leagueId, seasonId)));
        });
    }

    public LeagueApiV1Dtos.SeasonResponse season(String leagueId, String seasonId) {
        return guard(() -> new LeagueApiV1Dtos.SeasonResponse(
                LeagueApiV1Dtos.SEASON_SCHEMA, false, mapper.season(leagueId, seasonId)));
    }

    public LeagueApiV1Dtos.StandingsResponse standings(
            String leagueId,
            String seasonId
    ) {
        return guard(() -> mapper.standings(leagueId, seasonId));
    }

    public LeagueApiV1Dtos.FixturesResponse fixtures(
            String leagueId,
            String seasonId
    ) {
        return guard(() -> mapper.fixtures(leagueId, seasonId));
    }

    public LeagueApiV1Dtos.FixtureResponse fixture(
            String leagueId,
            String seasonId,
            String fixtureId
    ) {
        return guard(() -> new LeagueApiV1Dtos.FixtureResponse(
                LeagueApiV1Dtos.FIXTURE_SCHEMA,
                mapper.fixture(leagueId, seasonId, fixtureId)));
    }

    public HttpResult<LeagueApiV1Dtos.RunResponse> runCurrentRound(
            String leagueId,
            String seasonId,
            LeagueApiV1Dtos.RunCurrentRoundRequest request
    ) {
        return guard(() -> {
            AtomicReference<LeagueSimulationApplicationPort.DispatchBatch> dispatched =
                    new AtomicReference<>();
            AtomicInteger round = new AtomicInteger();
            String payload = hash("RUN_CURRENT_ROUND", request.schemaVersion(), leagueId,
                    seasonId, Long.toString(request.expectedLifecycleRevision()));
            LeagueApiCommandStore.Result command = commands.execute(
                    request.clientCommandId(), "RUN_CURRENT_ROUND", payload,
                    leagueId, seasonId, null, () -> {
                        requireExpected(leagueId, seasonId,
                                request.expectedLifecycleRevision());
                        int currentRound = mapper.season(leagueId, seasonId).currentRound();
                        round.set(currentRound);
                        dispatched.set(jobs.dispatchRound(seasonId, currentRound));
                        return HttpStatus.ACCEPTED.value();
                    });
            LeagueApiV1Dtos.SeasonView season = mapper.season(leagueId, seasonId);
            int currentRound = round.get() == 0 ? season.currentRound() : round.get();
            List<LeagueApiV1Dtos.JobView> views = mapper.currentRoundJobs(
                    leagueId, seasonId, currentRound);
            LeagueSimulationApplicationPort.DispatchBatch batch = dispatched.get();
            if (!command.replayed()) {
                background.submit("api-run:" + LeagueIdentity.sha256(
                        request.clientCommandId() + '\n').substring(0, 24));
            }
            return new HttpResult<>(command.httpStatus(), new LeagueApiV1Dtos.RunResponse(
                    LeagueApiV1Dtos.RUN_RESPONSE_SCHEMA, command.replayed(),
                    batch == null ? 0 : batch.queued(),
                    batch == null ? views.size() : batch.replayed(),
                    batch == null ? playerFixtureCount(leagueId, seasonId, currentRound)
                            : batch.playerFixturesExcluded(), season, views));
        });
    }

    public HttpResult<LeagueApiV1Dtos.SeasonResponse> pause(
            String leagueId, String seasonId,
            LeagueApiV1Dtos.LifecycleCommandRequest request
    ) {
        return lifecycle("PAUSE_SEASON", leagueId, seasonId, request,
                () -> seasons.pause(seasonId, request.expectedLifecycleRevision()));
    }

    public HttpResult<LeagueApiV1Dtos.SeasonResponse> resume(
            String leagueId, String seasonId,
            LeagueApiV1Dtos.LifecycleCommandRequest request
    ) {
        return lifecycle("RESUME_SEASON", leagueId, seasonId, request,
                () -> seasons.resume(seasonId, request.expectedLifecycleRevision()));
    }

    public HttpResult<Void> cancel(
            String leagueId, String seasonId,
            LeagueApiV1Dtos.LifecycleCommandRequest request
    ) {
        return guard(() -> {
            String payload = hash("CANCEL_SEASON", request.schemaVersion(), leagueId,
                    seasonId, Long.toString(request.expectedLifecycleRevision()));
            LeagueApiCommandStore.Result command = commands.execute(
                    request.clientCommandId(), "CANCEL_SEASON", payload,
                    leagueId, seasonId, null, () -> {
                        requireSeasonScope(leagueId, seasonId);
                        seasons.cancel(seasonId, request.expectedLifecycleRevision());
                        return HttpStatus.NO_CONTENT.value();
                    });
            return new HttpResult<>(command.httpStatus(), null);
        });
    }

    public LeagueApiV1Dtos.JobResponse job(
            String leagueId,
            String seasonId,
            String jobId
    ) {
        return guard(() -> new LeagueApiV1Dtos.JobResponse(
                LeagueApiV1Dtos.JOB_SCHEMA, mapper.job(leagueId, seasonId, jobId)));
    }

    public HttpResult<LeagueApiV1Dtos.PlayerSeriesResponse> startPlayerSeries(
            String leagueId,
            String seasonId,
            String fixtureId,
            LeagueApiV1Dtos.PlayerSeriesCommandRequest request
    ) {
        return guard(() -> {
            AtomicInteger outcome = new AtomicInteger(HttpStatus.OK.value());
            String payload = hash("START_PLAYER_SERIES", request.schemaVersion(), leagueId,
                    seasonId, fixtureId,
                    Long.toString(request.expectedLifecycleRevision()));
            LeagueApiCommandStore.Result command = commands.execute(
                    request.clientCommandId(), "START_PLAYER_SERIES", payload,
                    leagueId, seasonId, fixtureId, () -> {
                        requireExpected(leagueId, seasonId,
                                request.expectedLifecycleRevision());
                        LeagueSeasonAggregate season = mapper.requireSeason(
                                leagueId, seasonId);
                        LeagueFixture fixture = mapper.requireFixture(
                                leagueId, seasonId, fixtureId);
                        if (fixture.executionMode()
                                != LeagueFixtureExecutionMode.PLAYER_CONTROLLED) {
                            throw invalid("PLAYER_SERIES_REQUIRES_PLAYER_FIXTURE",
                                    "Player Series는 관리 팀 fixture에서만 시작할 수 있습니다.");
                        }
                        var result = playerSeries.startOrResume(
                                new LeaguePlayerSeriesHandoffService.StartCommand(
                                        leagueId, season, fixtureId, season.revision(),
                                        request.clientCommandId()));
                        if (result.status()
                                == LeaguePlayerSeriesHandoffService.StartStatus.BLOCKED) {
                            throw conflict("PLAYER_SERIES_START_REJECTED", seasonId);
                        }
                        outcome.set(result.status()
                                == LeaguePlayerSeriesHandoffService.StartStatus.STARTED
                                ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
                        return outcome.get();
                    });
            int status = command.replayed() ? HttpStatus.OK.value() : command.httpStatus();
            return new HttpResult<>(status, new LeagueApiV1Dtos.PlayerSeriesResponse(
                    LeagueApiV1Dtos.PLAYER_SERIES_SCHEMA, command.replayed(),
                    mapper.playerSeries(leagueId, seasonId, fixtureId)));
        });
    }

    public LeagueApiV1Dtos.PlayerSeriesResponse playerSeries(
            String leagueId,
            String seasonId,
            String fixtureId
    ) {
        return guard(() -> new LeagueApiV1Dtos.PlayerSeriesResponse(
                LeagueApiV1Dtos.PLAYER_SERIES_SCHEMA, false,
                mapper.playerSeries(leagueId, seasonId, fixtureId)));
    }

    public HttpResult<LeagueApiV1Dtos.CompletionStatusResponse> completePlayerSeries(
            String leagueId,
            String seasonId,
            String fixtureId,
            LeagueApiV1Dtos.PlayerCompletionCommandRequest request
    ) {
        return guard(() -> {
            String payload = hash("COMPLETE_PLAYER_SERIES", request.schemaVersion(),
                    leagueId, seasonId, fixtureId,
                    Long.toString(request.expectedLifecycleRevision()),
                    request.bindingHash());
            LeagueApiCommandStore.Result command = commands.execute(
                    request.clientCommandId(), "COMPLETE_PLAYER_SERIES", payload,
                    leagueId, seasonId, fixtureId, () -> {
                        requireExpected(leagueId, seasonId,
                                request.expectedLifecycleRevision());
                        LeagueSeasonAggregate season = mapper.requireSeason(
                                leagueId, seasonId);
                        LeaguePlayerSeriesBindingPort.State binding = bindings
                                .findByFixture(seasonId, fixtureId)
                                .orElseThrow(() -> new LeagueApiV1ResponseMapper.Missing(
                                        "LEAGUE_PLAYER_SERIES_NOT_FOUND"));
                        if (!binding.binding().bindingHash().equals(request.bindingHash())) {
                            throw invalid("PLAYER_SERIES_BINDING_SCOPE_MISMATCH",
                                    "fixture에 결속된 binding이 아닙니다.");
                        }
                        var completion = playerSeries.complete(
                                new LeaguePlayerSeriesHandoffService.CompletionCommand(
                                        leagueId, season, fixtureId, request.bindingHash()),
                                SimulationInstrumentation.disabled());
                        return switch (completion.status()) {
                            case VERIFIED -> {
                                store.drainOutbox(100);
                                yield HttpStatus.OK.value();
                            }
                            case PENDING -> HttpStatus.ACCEPTED.value();
                            case NOT_COMPLETED -> throw conflict(
                                    "PLAYER_SERIES_NOT_COMPLETED", seasonId);
                            case BLOCKED -> throw conflict(
                                    "PLAYER_SERIES_COMPLETION_REJECTED", seasonId);
                        };
                    });
            return new HttpResult<>(command.httpStatus(),
                    new LeagueApiV1Dtos.CompletionStatusResponse(
                            LeagueApiV1Dtos.COMPLETION_STATUS_SCHEMA,
                            command.replayed(), mapper.completionStatus(
                                    leagueId, seasonId, fixtureId)));
        });
    }

    public LeagueApiV1Dtos.CompletionStatusResponse completionStatus(
            String leagueId,
            String seasonId,
            String fixtureId
    ) {
        return guard(() -> new LeagueApiV1Dtos.CompletionStatusResponse(
                LeagueApiV1Dtos.COMPLETION_STATUS_SCHEMA, false,
                mapper.completionStatus(leagueId, seasonId, fixtureId)));
    }

    private HttpResult<LeagueApiV1Dtos.SeasonResponse> lifecycle(
            String type,
            String leagueId,
            String seasonId,
            LeagueApiV1Dtos.LifecycleCommandRequest request,
            Supplier<LeagueSeasonApplicationService.SeasonView> action
    ) {
        return guard(() -> {
            String payload = hash(type, request.schemaVersion(), leagueId, seasonId,
                    Long.toString(request.expectedLifecycleRevision()));
            LeagueApiCommandStore.Result command = commands.execute(
                    request.clientCommandId(), type, payload, leagueId, seasonId, null,
                    () -> {
                        requireSeasonScope(leagueId, seasonId);
                        action.get();
                        return HttpStatus.OK.value();
                    });
            return new HttpResult<>(command.httpStatus(),
                    new LeagueApiV1Dtos.SeasonResponse(
                            LeagueApiV1Dtos.SEASON_SCHEMA, command.replayed(),
                            mapper.season(leagueId, seasonId)));
        });
    }

    private void requireExpected(String leagueId, String seasonId, long expected) {
        requireSeasonScope(leagueId, seasonId);
        LeagueSeasonApplicationService.SeasonView current = seasons.view(seasonId);
        if (current.lifecycleRevision() != expected) {
            throw LeagueApiV1Exception.conflict("LEAGUE_STALE_LIFECYCLE_REVISION",
                    "Season lifecycle revision이 오래되었습니다.",
                    current.lifecycleRevision(), current.status().name());
        }
    }

    private void requireSeasonScope(String leagueId, String seasonId) {
        mapper.requireSeason(leagueId, seasonId);
    }

    private int playerFixtureCount(String leagueId, String seasonId, int round) {
        return (int) mapper.fixtures(leagueId, seasonId).fixtures().stream()
                .filter(value -> value.roundNumber() == round)
                .filter(value -> "PLAYER_CONTROLLED".equals(value.executionMode())).count();
    }

    private LeagueApiV1Exception conflict(String code, String seasonId) {
        LeagueSeasonApplicationService.SeasonView current = seasons.view(seasonId);
        return LeagueApiV1Exception.conflict(code,
                "현재 Season 상태에서는 요청을 수행할 수 없습니다.",
                current.lifecycleRevision(), current.status().name());
    }

    private static LeagueSeasonMode parseMode(String value) {
        try {
            return LeagueSeasonMode.valueOf(value);
        } catch (RuntimeException invalid) {
            throw invalid("LEAGUE_INVALID_SEASON_MODE",
                    "seasonMode는 HYBRID_MANAGER 또는 SPECTATOR_FULL_AUTO여야 합니다.");
        }
    }

    private static LeagueApiV1Exception invalid(String code, String message) {
        return LeagueApiV1Exception.of(HttpStatus.UNPROCESSABLE_ENTITY,
                code, null, message);
    }

    private static String hash(String type, String... values) {
        StringBuilder canonical = new StringBuilder(
                "commandSchema=AI_LEAGUE_API_COMMAND_PAYLOAD_V1\n")
                .append("commandType=").append(type).append('\n');
        for (int index = 0; index < values.length; index++) {
            canonical.append("value").append(index).append('=')
                    .append(values[index]).append('\n');
        }
        return LeagueIdentity.sha256(canonical.toString());
    }

    private static String value(String value) {
        return value == null ? "<null>" : value;
    }

    private static <T> T guard(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        try {
            return action.get();
        } catch (LeagueApiV1Exception known) {
            throw known;
        } catch (LeagueApiV1ResponseMapper.Missing missing) {
            throw LeagueApiV1Exception.of(HttpStatus.NOT_FOUND, missing.getMessage(),
                    null, "요청한 League resource를 찾을 수 없습니다.");
        } catch (LeagueApiV1ResponseMapper.Invalid invalid) {
            throw LeagueApiV1Exception.of(HttpStatus.UNPROCESSABLE_ENTITY,
                    invalid.getMessage(), null, "League 요청이 domain 계약에 맞지 않습니다.");
        } catch (LeagueApiCommandStore.CommandConflict conflict) {
            throw LeagueApiV1Exception.conflict(conflict.getMessage(),
                    "clientCommandId가 기존 요청과 충돌합니다.", null, null);
        } catch (DuplicateKeyException duplicate) {
            throw LeagueApiV1Exception.conflict("LEAGUE_STABLE_KEY_CONFLICT",
                    "이미 사용된 League 또는 Season key입니다.", null, null);
        } catch (TransientDataAccessException unavailable) {
            throw LeagueApiV1Exception.retryable("LEAGUE_TEMPORARILY_UNAVAILABLE",
                    "League 저장소를 일시적으로 사용할 수 없습니다.");
        } catch (IllegalStateException illegal) {
            String code = illegal.getMessage();
            if (code != null && (code.contains("STALE")
                    || code.contains("NOT_DISPATCHABLE")
                    || code.contains("ILLEGAL_TRANSITION"))) {
                throw LeagueApiV1Exception.conflict("LEAGUE_ILLEGAL_LIFECYCLE_TRANSITION",
                        "현재 League lifecycle에서는 요청을 수행할 수 없습니다.",
                        null, null);
            }
            throw illegal;
        } catch (IllegalArgumentException invalid) {
            throw LeagueApiV1Exception.of(HttpStatus.UNPROCESSABLE_ENTITY,
                    "LEAGUE_INVALID_DOMAIN_INPUT", null,
                    "League 요청이 frozen domain 계약에 맞지 않습니다.");
        }
    }

    public record HttpResult<T>(int httpStatus, T body) {}
}

package com.lolfm.league;

import com.lolfm.career.CareerApplicationService;
import com.lolfm.dto.LeagueApiV1Dtos;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only adapter from durable League authority to the Career resume projection. */
@Service
public final class LeagueCareerSeasonReadService
        implements CareerApplicationService.SeasonReadPort {
    private static final List<String> ACTIVE_PLAYER_BINDINGS = List.of(
            "CREATED", "ACTIVE", "COMPLETION_PENDING_VERIFICATION", "VERIFIED");
    private static final List<String> ATTENTION_FIXTURES = List.of(
            "BLOCKED", "PLAYER_SERIES_RESTART_REQUIRED");
    private static final List<String> ATTENTION_BINDINGS = List.of(
            "BLOCKED", "PLAYER_SERIES_RESTART_REQUIRED");

    private final LeagueApiV1ResponseMapper mapper;

    LeagueCareerSeasonReadService(LeagueApiV1ResponseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CareerApplicationService.LinkedSeason load(
            String leagueId,
            String seasonId
    ) {
        LeagueApiV1Dtos.SeasonView season = mapper.season(leagueId, seasonId);
        List<LeagueApiV1Dtos.FixtureView> fixtures = mapper.fixtures(
                leagueId, seasonId).fixtures();
        CareerApplicationService.ResumeState resume = resume(season, fixtures);
        return new CareerApplicationService.LinkedSeason(season.leagueId(),
                season.seasonId(), season.seasonMode(), season.managedTeamCode(),
                Long.parseLong(season.seasonRootSeed()),
                season.frozenSnapshotIdentity(), season.productDecisionHash(), resume);
    }

    private static CareerApplicationService.ResumeState resume(
            LeagueApiV1Dtos.SeasonView season,
            List<LeagueApiV1Dtos.FixtureView> fixtures
    ) {
        LeagueApiV1Dtos.FixtureView context = fixtures.stream()
                .filter(LeagueCareerSeasonReadService::needsAttention)
                .min(fixtureOrder()).orElse(null);
        String kind;
        if ("COMPLETED".equals(season.lifecycleStatus())) {
            kind = "SEASON_COMPLETE";
            context = null;
        } else if ("BLOCKED".equals(season.lifecycleStatus())
                || "CANCELLED".equals(season.lifecycleStatus()) || context != null) {
            kind = "ATTENTION_REQUIRED";
        } else {
            context = fixtures.stream()
                    .filter(LeagueCareerSeasonReadService::activePlayerSeries)
                    .min(fixtureOrder()).orElse(null);
            kind = context == null ? "LEAGUE_DASHBOARD" : "PLAYER_SERIES";
        }
        return new CareerApplicationService.ResumeState(kind, season.leagueId(),
                season.seasonId(), context == null ? null : context.fixtureId(),
                context == null ? null : context.boundSeriesId(),
                season.lifecycleStatus(), season.currentRound(),
                season.lifecycleRevision(), season.standingsRevision());
    }

    private static boolean activePlayerSeries(LeagueApiV1Dtos.FixtureView fixture) {
        return !terminal(fixture.lifecycleStatus())
                && fixture.playerSeriesStatus() != null
                && ACTIVE_PLAYER_BINDINGS.contains(fixture.playerSeriesStatus());
    }

    private static boolean needsAttention(LeagueApiV1Dtos.FixtureView fixture) {
        return ATTENTION_FIXTURES.contains(fixture.lifecycleStatus())
                || fixture.playerSeriesStatus() != null
                && ATTENTION_BINDINGS.contains(fixture.playerSeriesStatus());
    }

    private static Comparator<LeagueApiV1Dtos.FixtureView> fixtureOrder() {
        return Comparator.comparingInt(LeagueApiV1Dtos.FixtureView::roundNumber)
                .thenComparing(LeagueApiV1Dtos.FixtureView::fixtureId);
    }

    private static boolean terminal(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status);
    }
}

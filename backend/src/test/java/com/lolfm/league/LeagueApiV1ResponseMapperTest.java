package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.application.SeriesStatus;
import org.junit.jupiter.api.Test;

class LeagueApiV1ResponseMapperTest {
    @Test
    void playerCommandsFollowBindingAndAuthoritativeChildCompletionState() {
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.CREATED, null))
                .containsExactly("RESUME_PLAYER_SERIES");
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.ACTIVE, SeriesStatus.ACTIVE))
                .containsExactly("RESUME_PLAYER_SERIES");
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.ACTIVE, SeriesStatus.COMPLETED))
                .containsExactly("RECONCILE_PLAYER_SERIES_COMPLETION");
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.COMPLETION_PENDING_VERIFICATION,
                SeriesStatus.COMPLETED))
                .containsExactly("RECONCILE_PLAYER_SERIES_COMPLETION");
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.VERIFIED, SeriesStatus.COMPLETED))
                .isEmpty();
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.BLOCKED, SeriesStatus.BLOCKED))
                .isEmpty();
        assertThat(LeagueApiV1ResponseMapper.playerAllowed(
                LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED,
                SeriesStatus.ACTIVE))
                .isEmpty();
    }
}

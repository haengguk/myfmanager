package com.lolfm.application;

import com.lolfm.league.LeagueFixtureSeriesBindingV1;
import com.lolfm.league.LeaguePlayerSeriesKernelPort;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Process-local adapter that reuses the existing Series, Player Draft and V9 kernel. */
@Component
public final class ProductionLeaguePlayerSeriesKernel
        implements LeaguePlayerSeriesKernelPort {
    private final SeriesLifecycleService lifecycle;

    public ProductionLeaguePlayerSeriesKernel(SeriesLifecycleService lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    @Override
    public boolean canCompleteInitialDraft(LeagueFixtureSeriesBindingV1 binding) {
        Objects.requireNonNull(binding, "binding");
        return lifecycle.canCompleteLeagueBoundInitialDraft();
    }

    @Override
    public SeriesReference start(LeagueFixtureSeriesBindingV1 binding) {
        SeriesRepository.CreateResult result = lifecycle.createLeagueBound(binding);
        return reference(result.aggregate(), result.replayed());
    }

    @Override
    public SeriesReference resume(LeagueFixtureSeriesBindingV1 binding) {
        return reference(lifecycle.resumeLeagueBound(binding), true);
    }

    @Override
    public CompletedSeriesEvidence completedEvidence(
            LeagueFixtureSeriesBindingV1 binding,
            SimulationInstrumentation instrumentation
    ) {
        return lifecycle.completedLeagueEvidence(binding, instrumentation);
    }

    private static SeriesReference reference(SeriesAggregate value, boolean replayed) {
        return new SeriesReference(value.seriesId(), value.leagueBindingHash(),
                value.revision(), value.status(), value.currentGame().gameNumber(), replayed);
    }
}

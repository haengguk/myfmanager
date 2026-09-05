package com.lolfm.application;

import com.lolfm.career.CareerCompetitionSeriesBindingV1;
import com.lolfm.league.LeaguePlayerSeriesKernelPort;
import com.lolfm.league.LeagueProductionSnapshotProvider;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Reuses the existing Player Draft, BO3/BO5, Hard Fearless and V9 lifecycle. */
@Component
public final class CareerCompetitionPlayerSeriesKernel {
    private final SeriesLifecycleService lifecycle;
    private final LeagueProductionSnapshotProvider production;

    public CareerCompetitionPlayerSeriesKernel(
            SeriesLifecycleService lifecycle,
            LeagueProductionSnapshotProvider production
    ) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.production = Objects.requireNonNull(production, "production");
    }

    public Reference start(CareerCompetitionSeriesBindingV1 binding) {
        requireProductionAuthority(binding);
        SeriesRepository.CreateResult result = lifecycle.createCompetitionBound(binding);
        return reference(result.aggregate(), result.replayed());
    }

    public Reference resume(CareerCompetitionSeriesBindingV1 binding) {
        requireProductionAuthority(binding);
        return reference(lifecycle.resumeCompetitionBound(binding), true);
    }

    public LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence completedEvidence(
            CareerCompetitionSeriesBindingV1 binding
    ) {
        requireProductionAuthority(binding);
        return lifecycle.completedCompetitionEvidence(binding,
                SimulationInstrumentation.enabled());
    }

    private void requireProductionAuthority(
            CareerCompetitionSeriesBindingV1 binding
    ) {
        binding.requireProductionAuthority(production.currentSnapshot(
                        production.currentTeamCodes()),
                production.currentResourceProvenanceHash());
    }

    private static Reference reference(SeriesAggregate aggregate, boolean replayed) {
        return new Reference(aggregate.seriesId(), aggregate.leagueBindingHash(),
                aggregate.revision(), aggregate.status(),
                aggregate.currentGame().gameNumber(), replayed);
    }

    public record Reference(
            String seriesId, String bindingHash, long revision,
            SeriesStatus status, int currentGameNumber, boolean replayed
    ) {}
}

package com.lolfm.application;

import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Test-substitutable execution boundary; production implementation exposes no profile selector. */
interface SeriesMatchExecutor {
    default PlayerDraftCompletionBinding bind(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerControlledDraftResult completedDraft
    ) {
        return null;
    }

    Execution execute(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            PlayerControlledDraftResult completedDraft
    );

    default Execution execute(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            PlayerControlledDraftResult completedDraft,
            SimulationInstrumentation instrumentation
    ) {
        return execute(binding, completedDraft);
    }

    default Execution executeTrusted(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerDraftCompletionBinding completionBinding,
            PlayerControlledDraftResult completedDraft
    ) {
        return execute(parent, completedDraft);
    }

    default Execution executeTrusted(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerDraftCompletionBinding completionBinding,
            PlayerControlledDraftResult completedDraft,
            SimulationInstrumentation instrumentation
    ) {
        return executeTrusted(parent, childId, generation, completionRevision,
                completionBinding, completedDraft);
    }

    record Execution(MatchEngineV1Input input, MatchEngineV1Output output,
                     SeriesGameReceipt receipt) {
        public Execution {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(receipt, "receipt");
        }
    }
}

@Component
final class ProductionSeriesMatchExecutor implements SeriesMatchExecutor {
    private final PlayerControlledDraftMatchInputBoundary inputs;
    private final MatchEngineV1 matches;
    private final MatchEngineV1Canonicalizer canonicalizer;

    ProductionSeriesMatchExecutor(
            PlayerControlledDraftMatchInputBoundary inputs,
            MatchEngineV1 matches,
            MatchEngineV1Canonicalizer canonicalizer
    ) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.matches = Objects.requireNonNull(matches, "matches");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    @Override
    public PlayerDraftCompletionBinding bind(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerControlledDraftResult completedDraft
    ) {
        return inputs.bindSeries(parent, childId, generation, completionRevision,
                completedDraft);
    }

    @Override
    public Execution execute(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            PlayerControlledDraftResult completedDraft
    ) {
        return execute(binding, completedDraft, SimulationInstrumentation.enabled());
    }

    @Override
    public Execution execute(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            PlayerControlledDraftResult completedDraft,
            SimulationInstrumentation instrumentation
    ) {
        MatchEngineV1Input input = inputs.validateAndCreateSeriesInput(binding, completedDraft);
        MatchEngineV1Output output = matches.execute(input, instrumentation);
        validate(binding, input, output);
        return new Execution(input, output, SeriesGameReceipt.from(output));
    }

    @Override
    public Execution executeTrusted(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerDraftCompletionBinding completionBinding,
            PlayerControlledDraftResult completedDraft
    ) {
        return executeTrusted(parent, childId, generation, completionRevision,
                completionBinding, completedDraft, SimulationInstrumentation.enabled());
    }

    @Override
    public Execution executeTrusted(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerDraftCompletionBinding completionBinding,
            PlayerControlledDraftResult completedDraft,
            SimulationInstrumentation instrumentation
    ) {
        MatchEngineV1Input input = inputs.validateAndCreateTrustedSeriesInput(parent,
                childId, generation, completionRevision, completionBinding, completedDraft);
        MatchEngineV1Output output = matches.execute(input, instrumentation);
        validate(parent, input, output);
        return new Execution(input, output, SeriesGameReceipt.from(output));
    }

    private void validate(
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            MatchEngineV1Input input,
            MatchEngineV1Output output
    ) {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        SimulationExecutionProvenance execution = output.executionProvenance();
        boolean valid = output.productionPolicy().equals(policy)
                && output.configurationHash().equals(policy.configurationHash())
                && execution.runtimeProfileId() == policy.retainedRuntimeProfileId()
                && execution.configurationHash().equals(policy.configurationHash())
                && execution.engineImplementationVersion().equals(
                policy.engineImplementationVersion())
                && execution.activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())
                && execution.blueTeamCode().equals(binding.blueTeamCode())
                && execution.redTeamCode().equals(binding.redTeamCode())
                && execution.matchSeed() == binding.matchSeed()
                && execution.seriesGameNumber() == binding.gameNumber()
                && execution.seriesHistoryBeforeHash().equals(binding.historyBeforeHash())
                && output.finalDraft().seriesGameNumber() == binding.gameNumber()
                && output.finalDraft().hardFearlessExclusions().equals(
                binding.hardFearlessExclusions().stream().sorted(
                        java.util.Comparator.comparing(
                                com.lolfm.champion.ChampionId::value)).toList())
                && output.inputHash().equals(input.inputHash())
                && output.resultSummary().finalDraftHash().equals(
                output.finalDraft().finalDraftHash())
                && output.resultSummary().finalAssignmentHash().equals(
                output.finalDraft().finalAssignmentHash())
                && output.hasValidOutputHash(canonicalizer);
        if (!valid) throw new SeriesMatchIntegrityException(
                "SERIES_ENGINE_OUTPUT_INTEGRITY_FAILED");
    }
}

final class SeriesMatchIntegrityException extends RuntimeException {
    SeriesMatchIntegrityException(String code) { super(code); }
}

package com.lolfm.simulator;

public record StructureActionExecutionStatsSnapshot(
        int structureAttempted,
        int structureMutationPerformed,
        int laterResolverBlockedByAttempt,
        int sameSideMultipleAttemptError,
        int sameSideMultipleMutationError,
        int postFightMultiStructureActions,
        int postFightMultiStructureMutationCount,
        int postFightInternalBlockError
) {
}

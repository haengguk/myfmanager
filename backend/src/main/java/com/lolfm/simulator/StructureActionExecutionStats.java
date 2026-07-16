package com.lolfm.simulator;

public final class StructureActionExecutionStats {
    private int structureAttempted;
    private int structureMutationPerformed;
    private int laterResolverBlockedByAttempt;
    private int sameSideMultipleAttemptError;
    private int sameSideMultipleMutationError;
    private int postFightMultiStructureActions;
    private int postFightMultiStructureMutationCount;
    private int postFightInternalBlockError;
    void recordAttempt() { structureAttempted++; }
    void recordMutation() { structureMutationPerformed++; }
    void recordLaterResolverBlockedByAttempt() { laterResolverBlockedByAttempt++; }
    void recordSameSideMultipleAttemptError() { sameSideMultipleAttemptError++; }
    void recordSameSideMultipleMutationError() { sameSideMultipleMutationError++; }
    void recordPostFightWindow(int mutations) {
        if (mutations > 1) {
            postFightMultiStructureActions++;
            postFightMultiStructureMutationCount += mutations;
        }
    }
    void recordPostFightInternalBlockError() { postFightInternalBlockError++; }
    public StructureActionExecutionStatsSnapshot snapshot() {
        return new StructureActionExecutionStatsSnapshot(structureAttempted, structureMutationPerformed,
                laterResolverBlockedByAttempt, sameSideMultipleAttemptError, sameSideMultipleMutationError,
                postFightMultiStructureActions, postFightMultiStructureMutationCount, postFightInternalBlockError);
    }
}

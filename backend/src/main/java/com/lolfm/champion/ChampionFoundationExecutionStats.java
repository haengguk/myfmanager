package com.lolfm.champion;

import java.util.Map;

public record ChampionFoundationExecutionStats(
        int catalogSize, Map<String, Integer> countByPosition, int duplicateChampionId,
        int duplicateRiotAssetId, int missingDisplayName, int missingPortrait,
        int invalidSupportedPosition, int assignmentCount, int missingAssignment,
        int duplicateAssignment, int crossTeamDuplicate, int positionMismatch,
        int unknownChampion, int displayNameIdentityLookup, int arrayIndexIdentityLookup,
        int championRandomCalls, int championGameplayContributionNonZero,
        int championMultiplierNonNeutral, int championSpikeNonZero,
        int championContextModifierNonZero, int snapshotAssignmentMismatch,
        int replayAssignmentMismatch, int futureSnapshotLeak, int catalogMutationError,
        int matchStateLeak, int diagnosticsMismatch
) {
    public ChampionFoundationExecutionStats { countByPosition = Map.copyOf(countByPosition); }
}

package com.lolfm.champion;

public final class ChampionMatchupExecutionStats {
    private final java.util.List<Double> applicationEdges = new java.util.ArrayList<>();
    private final java.util.List<ChampionMatchupApplicationProvenance> applicationProvenance =
            new java.util.ArrayList<>();
    private final java.util.List<ChampionMatchupStateConsumerProvenance> stateConsumerProvenance =
            new java.util.ArrayList<>();
    private final java.util.Map<ConsumedApplicationSlot, ConsumedApplicationPayload>
            consumedApplications = new java.util.HashMap<>();
    private final java.util.Set<StateConsumerSlot> consumedStateVersions = new java.util.HashSet<>();
    private long nextApplicationSequence = 1;
    private int evaluations;
    private int enabledEvaluations;
    private int disabledEvaluations;
    private int eligiblePairEvaluations;
    private int noEligiblePairEvaluations;
    private int totalPairApplications;
    private int zeroContributionApplications;
    private int nonZeroContributionApplications;
    private int missingAssignmentErrors;
    private int deadParticipantErrors;
    private int nonParticipantErrors;
    private int sameTeamPairErrors;
    private int crossPositionErrors;
    private int duplicateApplicationErrors;
    private int staleStateErrors;
    private int directRandomCalls;
    private int exactZeroApplications;
    private int neutralOnMismatch;
    private int featureOffMismatch;
    private int mirrorMismatch;
    private int deadParticipantSkipped;
    private int nonParticipantSkipped;
    private int crossPositionSkipped;
    private int sameTeamSkipped;
    private double generatedBaseEdgeSum;
    private double overrideAdjustmentSum;
    private double finalMatchupEdgeSum;
    private long eligiblePairCountTotal;
    private long nonZeroPairCountTotal;
    private double dilutionRatioSum;
    private int dilutionSamples;
    private double coverageRatioSum;
    private double netDirectionalRetentionSum;
    private int prototypeCoverageDilutionCount;
    private int signCancellationCount;
    private int unexpectedAggregationDilutionCount;
    private int consumedApplicationCount;
    private int nonZeroConsumedApplicationCount;
    private int idempotentDuplicateConsumedApplicationCount;
    private int duplicateConsumedApplicationErrors;
    private int applicationBindingErrors;
    private int staleAssignmentParticipantErrors;

    public void recordDisabledEvaluation() {
        evaluations++;
        disabledEvaluations++;
    }

    public void recordEnabledEvaluation(ChampionMatchupResult result) {
        evaluations++;
        enabledEvaluations++;
        if (result.eligiblePairCount() == 0) noEligiblePairEvaluations++;
        else eligiblePairEvaluations++;
        totalPairApplications += result.eligiblePairCount();
        for (ChampionMatchupPairContribution value : result.pairContributions()) {
            applicationEdges.add(value.edge());
            if (value.edge() == 0.0) {
                zeroContributionApplications++;
                exactZeroApplications++;
            } else {
                nonZeroContributionApplications++;
            }
        }
        missingAssignmentErrors += result.missingAssignmentCount();
        deadParticipantSkipped += result.deadParticipantSkipped();
        nonParticipantSkipped += result.nonParticipantSkipped();
        crossPositionSkipped += result.crossPositionSkipped();
        sameTeamSkipped += result.sameTeamSkipped();
        duplicateApplicationErrors += result.duplicateApplicationCount();
        generatedBaseEdgeSum += result.generatedMatchupBaseEdge();
        overrideAdjustmentSum += result.matchupOverrideAdjustment();
        finalMatchupEdgeSum += result.finalChampionMatchupEdge();
        eligiblePairCountTotal += result.matchupEligiblePairCount();
        nonZeroPairCountTotal += result.matchupNonZeroPairCount();
        if (result.matchupNonZeroPairCount() > 0) {
            ChampionMatchupDilutionMetrics metrics = result.matchupDilutionMetrics();
            dilutionRatioSum += result.matchupDilutionRatio();
            coverageRatioSum += metrics.coverageRatio();
            netDirectionalRetentionSum += metrics.netDirectionalRetention();
            dilutionSamples++;
            switch (metrics.classification()) {
                case PROTOTYPE_COVERAGE_DILUTION -> prototypeCoverageDilutionCount++;
                case SIGN_CANCELLATION -> signCancellationCount++;
                case UNEXPECTED_AGGREGATION_DILUTION -> unexpectedAggregationDilutionCount++;
                default -> { }
            }
        }
    }

    public void deadParticipantAppliedError() { deadParticipantErrors++; }
    public void nonParticipantAppliedError() { nonParticipantErrors++; }
    public void sameTeamPairAppliedError() { sameTeamPairErrors++; }
    public void crossPositionAppliedError() { crossPositionErrors++; }
    public void staleStateError() { staleStateErrors++; }
    public void directRandomCall() { directRandomCalls++; }
    public void neutralOnMismatch() { neutralOnMismatch++; }
    public void featureOffMismatch() { featureOffMismatch++; }
    public void mirrorMismatch() { mirrorMismatch++; }

    public void recordConsumedApplication(
            com.lolfm.simulator.GameState state,
            ChampionMatchupResult result,
            com.lolfm.simulator.ProgressionCombatContext context,
            com.lolfm.simulator.ProgressionApplicationStage stage,
            ChampionMatchupApplicationPoint applicationPoint,
            ChampionMatchupLaneScope laneScope,
            double scoreBefore,
            double scoreAfter,
            String structuredActionId
    ) {
        recordConsumedApplication(state, result, context, stage, applicationPoint, laneScope,
                scoreBefore, scoreAfter, structuredActionId, null);
    }

    public void recordConsumedApplication(
            com.lolfm.simulator.GameState state,
            ChampionMatchupResult result,
            com.lolfm.simulator.ProgressionCombatContext context,
            com.lolfm.simulator.ProgressionApplicationStage stage,
            ChampionMatchupApplicationPoint applicationPoint,
            ChampionMatchupLaneScope laneScope,
            double scoreBefore,
            double scoreAfter,
            String structuredActionId,
            ChampionMatchupStateMutationLineage stateMutationLineage
    ) {
        if (state.getChampionMatchupMode() == ChampionMatchupMode.OFF || !result.enabled()
                || result.pairContributions().isEmpty()) return;
        com.lolfm.simulator.TeamSide perspective = result.pairContributions().getFirst()
                .source().side();
        java.util.List<ChampionMatchupPairApplication> pairs = new java.util.ArrayList<>();
        try {
            for (ChampionMatchupPairContribution contribution : result.pairContributions()) {
                pairs.add(new ChampionMatchupPairApplication(
                        binding(state, contribution.source()),
                        binding(state, contribution.opponent()), contribution.edge()));
            }
        } catch (IllegalStateException error) {
            staleAssignmentParticipantErrors++;
            return;
        } catch (IllegalArgumentException error) {
            applicationBindingErrors++;
            return;
        }
        double delta = scoreAfter - scoreBefore;
        String mutationIdentity = stateMutationLineage == null
                ? null : stateMutationLineage.mutationIdentity();
        boolean combatConsumer = applicationPoint == ChampionMatchupApplicationPoint
                .COMBAT_PROGRESSION_SCORE;
        if (combatConsumer ? structuredActionId == null || structuredActionId.isBlank()
                : stateMutationLineage == null || structuredActionId != null) {
            applicationBindingErrors++;
            return;
        }
        java.util.List<com.lolfm.simulator.PlayerKey> participants = pairs.stream()
                .flatMap(pair -> java.util.stream.Stream.of(
                        pair.source().playerKey(), pair.opponent().playerKey()))
                .distinct()
                .sorted(java.util.Comparator.comparing(
                        (com.lolfm.simulator.PlayerKey key) -> key.side().ordinal())
                        .thenComparing(key -> key.position().ordinal()))
                .toList();
        ConsumedApplicationSlot slot = new ConsumedApplicationSlot(
                state.getCurrentTimeSeconds(), context, stage, applicationPoint, perspective,
                laneScope, participants, structuredActionId, mutationIdentity);
        ConsumedApplicationPayload payload = new ConsumedApplicationPayload(
                java.util.List.copyOf(pairs), Double.doubleToLongBits(result.matchupEdge()),
                Double.doubleToLongBits(scoreBefore), Double.doubleToLongBits(scoreAfter),
                stateMutationLineage);
        ConsumedApplicationPayload existing = consumedApplications.putIfAbsent(slot, payload);
        if (existing != null) {
            if (existing.equals(payload)) idempotentDuplicateConsumedApplicationCount++;
            else duplicateConsumedApplicationErrors++;
            return;
        }
        long sequence = nextApplicationSequence++;
        applicationProvenance.add(new ChampionMatchupApplicationProvenance(
                ChampionMatchupApplicationProvenance.SCHEMA_VERSION, sequence,
                "MATCHUP_APPLICATION:" + sequence, state.getCurrentTimeSeconds(),
                state.getChampionMatchupMode(), context, stage, applicationPoint, perspective,
                laneScope, pairs, result.matchupEdge(), scoreBefore, scoreAfter, delta,
                true, delta != 0.0, structuredActionId, stateMutationLineage));
        consumedApplicationCount++;
        if (delta != 0.0) nonZeroConsumedApplicationCount++;
    }

    private static ChampionMatchupParticipantBinding binding(
            com.lolfm.simulator.GameState state, com.lolfm.simulator.PlayerKey key) {
        com.lolfm.simulator.PlayerState player = state.getTeamState(key.side()).playerAt(key.position());
        ChampionAssignment assignment = state.getChampionAssignments().orElseThrow().get(key);
        return new ChampionMatchupParticipantBinding(key, key.position(), player.requirePlayerId(),
                assignment.championId());
    }

    public void recordStateConsumer(
            ChampionMatchupStateMutationLineage lineage,
            int consumerTimeSeconds,
            com.lolfm.simulator.ProgressionCombatContext context,
            com.lolfm.simulator.ProgressionApplicationStage stage,
            String structuredActionId
    ) {
        if (lineage == null || lineage.matchupPressureDelta() == 0.0) return;
        if (structuredActionId == null || structuredActionId.isBlank()) {
            applicationBindingErrors++;
            return;
        }
        StateConsumerSlot slot = new StateConsumerSlot(lineage.mutationIdentity(),
                lineage.mutationVersion(), structuredActionId, context, stage);
        if (!consumedStateVersions.add(slot)) return;
        stateConsumerProvenance.add(new ChampionMatchupStateConsumerProvenance(
                lineage.mutationIdentity(), lineage.mutationVersion(),
                lineage.simulationTimeSeconds(), laneScope(lineage.lane()),
                lineage.matchupPressureDelta(), consumerTimeSeconds, context, stage,
                structuredActionId));
    }

    private static ChampionMatchupLaneScope laneScope(com.lolfm.simulator.Lane lane) {
        return switch (lane) {
            case TOP -> ChampionMatchupLaneScope.TOP;
            case MID -> ChampionMatchupLaneScope.MID;
            case BOT -> ChampionMatchupLaneScope.BOT;
        };
    }

    /** Logical consumer slot; payload values are deliberately excluded. */
    private record ConsumedApplicationSlot(
            int simulationTimeSeconds,
            com.lolfm.simulator.ProgressionCombatContext context,
            com.lolfm.simulator.ProgressionApplicationStage applicationStage,
            ChampionMatchupApplicationPoint applicationPoint,
            com.lolfm.simulator.TeamSide perspective,
            ChampionMatchupLaneScope laneScope,
            java.util.List<com.lolfm.simulator.PlayerKey> participants,
            String structuredActionId,
            String stateMutationIdentity
    ) {
        private ConsumedApplicationSlot {
            participants = java.util.List.copyOf(participants);
        }
    }

    /** Semantic payload bound to exactly one logical consumer slot. */
    private record ConsumedApplicationPayload(
            java.util.List<ChampionMatchupPairApplication> pairApplications,
            long aggregateEdgeBits,
            long consumerScoreBeforeBits,
            long consumerScoreAfterBits,
            ChampionMatchupStateMutationLineage stateMutationLineage
    ) {
        private ConsumedApplicationPayload {
            pairApplications = java.util.List.copyOf(pairApplications);
        }
    }

    private record StateConsumerSlot(
            String mutationIdentity,
            long mutationVersion,
            String structuredActionId,
            com.lolfm.simulator.ProgressionCombatContext context,
            com.lolfm.simulator.ProgressionApplicationStage stage
    ) { }

    public ChampionMatchupExecutionStatsSnapshot snapshot() {
        return new ChampionMatchupExecutionStatsSnapshot(
                evaluations, enabledEvaluations, disabledEvaluations,
                eligiblePairEvaluations, noEligiblePairEvaluations, totalPairApplications,
                zeroContributionApplications, nonZeroContributionApplications,
                missingAssignmentErrors, deadParticipantErrors, nonParticipantErrors,
                sameTeamPairErrors, crossPositionErrors, duplicateApplicationErrors,
                staleStateErrors, directRandomCalls, exactZeroApplications,
                neutralOnMismatch, featureOffMismatch, mirrorMismatch,
                deadParticipantSkipped, nonParticipantSkipped, crossPositionSkipped,
                sameTeamSkipped, generatedBaseEdgeSum, overrideAdjustmentSum,
                finalMatchupEdgeSum, eligiblePairCountTotal, nonZeroPairCountTotal,
                dilutionRatioSum, dilutionSamples, coverageRatioSum,
                netDirectionalRetentionSum, prototypeCoverageDilutionCount,
                signCancellationCount, unexpectedAggregationDilutionCount,
                java.util.List.copyOf(applicationEdges), consumedApplicationCount,
                nonZeroConsumedApplicationCount, idempotentDuplicateConsumedApplicationCount,
                duplicateConsumedApplicationErrors, applicationBindingErrors,
                staleAssignmentParticipantErrors, java.util.List.copyOf(applicationProvenance),
                java.util.List.copyOf(stateConsumerProvenance));
    }

}

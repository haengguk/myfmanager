package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Objects;

/** Match-scoped, observation-only snapshot of the canonical runtime winner decision. */
public record CompositionWinnerDecisionProvenance(
        long matchSeed,
        int caseIndex,
        GameplayAttemptId attemptId,
        String applicationKey,
        TeamCompositionContext context,
        CompositionActionType actionType,
        CompositionBaselineScoreDomain scoreDomain,
        int timeSeconds,
        TeamSide perspectiveSide,
        CompositionScoreOrientation scoreOrientation,
        TeamSide attackingSide,
        TeamSide defendingSide,
        CompositionCombatRole perspectiveRole,
        CompositionRuntimeDecisionKind decisionKind,
        CompositionRuntimeComparisonOperator comparisonOperator,
        double baselineScore,
        double compositionEdge,
        double selectedGain,
        double compositionModifier,
        double candidateScore,
        double baselineScoreBeforeClamp,
        double candidateScoreBeforeClamp,
        double existingNonScalarCompositionComponent,
        double baselineClampDelta,
        double candidateClampDelta,
        double clampEffect,
        double baselineProbability,
        double candidateProbability,
        double randomSample,
        long randomDrawOrdinal,
        double runtimeThreshold,
        TeamSide baselineCounterfactualWinner,
        TeamSide runtimeWinner,
        int blueGold,
        int redGold,
        int blueKills,
        int redKills,
        int blueAliveCount,
        int redAliveCount,
        double blueBaseTeamPower,
        double redBaseTeamPower,
        double goldContribution,
        double killContribution,
        double levelContribution,
        double itemContribution,
        double progressionContribution,
        double championPowerContribution,
        double matchupContribution,
        boolean blueDragonSoul,
        boolean redDragonSoul,
        boolean blueBaronBuff,
        boolean redBaronBuff,
        boolean blueElderBuff,
        boolean redElderBuff,
        int blueTowersDestroyed,
        int redTowersDestroyed,
        int blueInhibitorsRemaining,
        int redInhibitorsRemaining,
        int blueNexusTurretsRemaining,
        int redNexusTurretsRemaining,
        boolean blueNexusAlive,
        boolean redNexusAlive,
        CompositionFactorAvailability economyAvailability,
        CompositionFactorAvailability progressionAvailability,
        CompositionFactorAvailability championPowerAvailability,
        CompositionFactorAvailability matchupAvailability,
        List<CompositionDecisionScoreStage> scoreStages
) {
    public CompositionWinnerDecisionProvenance {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(applicationKey, "applicationKey");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        Objects.requireNonNull(perspectiveSide, "perspectiveSide");
        Objects.requireNonNull(scoreOrientation, "scoreOrientation");
        Objects.requireNonNull(perspectiveRole, "perspectiveRole");
        Objects.requireNonNull(decisionKind, "decisionKind");
        Objects.requireNonNull(comparisonOperator, "comparisonOperator");
        Objects.requireNonNull(baselineCounterfactualWinner, "baselineCounterfactualWinner");
        Objects.requireNonNull(runtimeWinner, "runtimeWinner");
        Objects.requireNonNull(economyAvailability, "economyAvailability");
        Objects.requireNonNull(progressionAvailability, "progressionAvailability");
        Objects.requireNonNull(championPowerAvailability, "championPowerAvailability");
        Objects.requireNonNull(matchupAvailability, "matchupAvailability");
        scoreStages = List.copyOf(scoreStages);
        requireFinite(baselineScore, "baselineScore");
        requireFinite(candidateScore, "candidateScore");
        requireFinite(baselineScoreBeforeClamp, "baselineScoreBeforeClamp");
        requireFinite(candidateScoreBeforeClamp, "candidateScoreBeforeClamp");
        requireFinite(existingNonScalarCompositionComponent,
                "existingNonScalarCompositionComponent");
        requireFinite(baselineClampDelta, "baselineClampDelta");
        requireFinite(candidateClampDelta, "candidateClampDelta");
        requireFinite(clampEffect, "clampEffect");
        requireClose(candidateScoreBeforeClamp - baselineScoreBeforeClamp,
                compositionModifier + existingNonScalarCompositionComponent,
                "pre-clamp score decomposition");
        requireClose(baselineScore, baselineScoreBeforeClamp + baselineClampDelta,
                "baseline clamp decomposition");
        requireClose(candidateScore, candidateScoreBeforeClamp + candidateClampDelta,
                "candidate clamp decomposition");
        requireClose(clampEffect, candidateClampDelta - baselineClampDelta,
                "differential clamp effect");
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    private static void requireClose(double actual, double expected, String field) {
        if (Math.abs(actual - expected) > 1e-12) {
            throw new IllegalArgumentException(field + " mismatch");
        }
    }
}

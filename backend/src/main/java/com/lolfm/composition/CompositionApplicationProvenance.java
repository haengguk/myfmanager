package com.lolfm.composition;

import com.lolfm.domain.MatchEventType;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/**
 * Match-scoped, observation-only evidence that one frozen composition modifier reached an
 * existing gameplay decision. Nullable values mean that the structured attempt did not expose
 * that domain value; display text is never used as a fallback.
 */
public record CompositionApplicationProvenance(
        String schemaVersion,
        long matchSeed,
        int simulationTimeSeconds,
        GameplayAttemptId attemptId,
        String resolverIdentity,
        CompositionActionType actionType,
        TeamCompositionContext context,
        CompositionApplicationPoint applicationPoint,
        CompositionBaselineScoreDomain scoreDomain,
        TeamSide attemptOwnerSide,
        TeamSide initiatingSide,
        TeamSide attackingSide,
        TeamSide defendingSide,
        TeamSide perspectiveSide,
        TeamSide opponentSide,
        Lane lane,
        ObjectiveType objectiveType,
        StructureKind structureTargetType,
        FightScale fightScale,
        TeamCompositionGameplayMode runtimeMode,
        String frozenApplicationKey,
        String approvalStatus,
        boolean contextMapped,
        CompositionApplicationEligibility routingEligibility,
        String eligibilityReason,
        double rawCompositionEdge,
        double frozenGain,
        double modifier,
        double existingNonScalarCompositionDelta,
        double totalCompositionInputDelta,
        boolean existingNonScalarEffectConsumed,
        Double perspectiveScoreBefore,
        Double opponentScoreBefore,
        Double perspectiveScoreAfter,
        Double opponentScoreAfter,
        Double baselineGap,
        Double adjustedGap,
        Double baselineProbability,
        Double adjustedProbability,
        String gameplayConsumerIdentity,
        String gameplayEffectStatus,
        boolean modifierCalculated,
        boolean applicationApplied,
        boolean nonZeroModifier,
        boolean modifierConsumed,
        boolean localDecisionChanged,
        Long randomDrawOrdinal,
        Double randomSample,
        String baselineLocalResult,
        String finalLocalResult,
        String publicActionId,
        String publicParentActionId,
        MatchEventType publicEventType,
        String publicCombatSource,
        Lane publicCombatLane,
        String publicBindingStatus
) {
    public static final String SCHEMA_VERSION = "COMPOSITION_APPLICATION_CAUSAL_PROVENANCE_V4";

    public CompositionApplicationProvenance {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(resolverIdentity, "resolverIdentity");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(applicationPoint, "applicationPoint");
        Objects.requireNonNull(scoreDomain, "scoreDomain");
        Objects.requireNonNull(fightScale, "fightScale");
        Objects.requireNonNull(runtimeMode, "runtimeMode");
        Objects.requireNonNull(frozenApplicationKey, "frozenApplicationKey");
        Objects.requireNonNull(approvalStatus, "approvalStatus");
        Objects.requireNonNull(routingEligibility, "routingEligibility");
        Objects.requireNonNull(eligibilityReason, "eligibilityReason");
        Objects.requireNonNull(gameplayConsumerIdentity, "gameplayConsumerIdentity");
        Objects.requireNonNull(gameplayEffectStatus, "gameplayEffectStatus");
        Objects.requireNonNull(baselineLocalResult, "baselineLocalResult");
        Objects.requireNonNull(finalLocalResult, "finalLocalResult");
        Objects.requireNonNull(publicBindingStatus, "publicBindingStatus");
        if (!SCHEMA_VERSION.equals(schemaVersion) || simulationTimeSeconds < 0) {
            throw new IllegalArgumentException("Invalid composition application provenance identity");
        }
        requireFinite(rawCompositionEdge, "rawCompositionEdge");
        requireFinite(frozenGain, "frozenGain");
        requireFinite(modifier, "modifier");
        requireFinite(existingNonScalarCompositionDelta, "existingNonScalarCompositionDelta");
        requireFinite(totalCompositionInputDelta, "totalCompositionInputDelta");
        requireNullableFinite(perspectiveScoreBefore, "perspectiveScoreBefore");
        requireNullableFinite(opponentScoreBefore, "opponentScoreBefore");
        requireNullableFinite(perspectiveScoreAfter, "perspectiveScoreAfter");
        requireNullableFinite(opponentScoreAfter, "opponentScoreAfter");
        requireNullableFinite(baselineGap, "baselineGap");
        requireNullableFinite(adjustedGap, "adjustedGap");
        requireNullableFinite(baselineProbability, "baselineProbability");
        requireNullableFinite(adjustedProbability, "adjustedProbability");
        requireNullableFinite(randomSample, "randomSample");
        if (modifierConsumed && (!applicationApplied || !modifierCalculated)) {
            throw new IllegalArgumentException("Consumed modifier must be calculated and applied");
        }
        if (applicationApplied && !modifierConsumed && !existingNonScalarEffectConsumed) {
            throw new IllegalArgumentException("Applied composition effect must identify its consumer channel");
        }
        if (nonZeroModifier != (modifier != 0.0)) {
            throw new IllegalArgumentException("nonZeroModifier does not match modifier");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    private static void requireNullableFinite(Double value, String field) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite when present");
        }
    }
}

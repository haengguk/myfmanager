package com.lolfm.composition;

import com.lolfm.simulator.TeamSide;
import java.util.Objects;

/** Stateless structured-action router. It never reads event text or display names. */
public final class CompositionContextRouter {
    public CompositionContextRouting route(CompositionAttemptDescriptor attempt) {
        Objects.requireNonNull(attempt, "attempt");
        return switch (attempt.actionType()) {
            case SKIRMISH, JUNGLE_GANK, COUNTER_GANK, LANE_COMBAT, ROAM ->
                    combat(attempt, TeamCompositionContext.SKIRMISH,
                            CompositionApplicationPoint.SKIRMISH_COMBAT,
                            CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE);
            case TEAMFIGHT -> combat(attempt, TeamCompositionContext.TEAMFIGHT,
                    CompositionApplicationPoint.TEAMFIGHT_COMBAT,
                    CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE);
            case OBJECTIVE_SETUP -> objective(attempt);
            case SIEGE, SIEGE_COMBAT, STRUCTURE_PUSH -> structure(attempt);
            case BASE_DEFENSE -> baseDefense(attempt);
            case SIDE_LANE -> sideLane(attempt);
            case OBJECTIVE_CAPTURE -> CompositionContextRouting.unmapped(
                    "UNMAPPED_OBJECTIVE_CAPTURE_IS_RESULT_NOT_SETUP", attempt.baselineScoreDomain());
        };
    }

    private CompositionContextRouting combat(CompositionAttemptDescriptor attempt,
                                              TeamCompositionContext context,
                                              CompositionApplicationPoint point,
                                              CompositionBaselineScoreDomain expectedDomain) {
        TeamSide perspective = first(attempt.initiatingSide(), attempt.attemptOwnerSide());
        if (perspective == null) return CompositionContextRouting.unmapped(
                "AMBIGUOUS_CONTEXT_ROUTING_MISSING_INITIATOR_OR_OWNER", expectedDomain);
        return mapped(attempt, context, perspective, point, expectedDomain,
                "STRUCTURED_ACTUAL_COMBAT_ATTEMPT");
    }

    private CompositionContextRouting objective(CompositionAttemptDescriptor attempt) {
        if (attempt.objectiveType() == null) {
            return CompositionContextRouting.unmapped("UNMAPPED_OBJECTIVE_SETUP_MISSING_OBJECTIVE_TYPE",
                    CompositionBaselineScoreDomain.OBJECTIVE_SETUP_SCORE);
        }
        if (!attempt.objectiveContested()) {
            return CompositionContextRouting.unmapped("UNMAPPED_OBJECTIVE_SETUP_NOT_CONTESTED",
                    CompositionBaselineScoreDomain.OBJECTIVE_SETUP_SCORE);
        }
        TeamSide perspective = first(attempt.initiatingSide(), attempt.attemptOwnerSide());
        if (perspective == null) return CompositionContextRouting.unmapped(
                "AMBIGUOUS_CONTEXT_ROUTING_MISSING_OBJECTIVE_INITIATOR", CompositionBaselineScoreDomain.OBJECTIVE_SETUP_SCORE);
        return mapped(attempt, TeamCompositionContext.OBJECTIVE_SETUP, perspective,
                CompositionApplicationPoint.OBJECTIVE_SETUP, CompositionBaselineScoreDomain.OBJECTIVE_SETUP_SCORE,
                "STRUCTURED_CONTESTED_OBJECTIVE_ATTEMPT");
    }

    private CompositionContextRouting structure(CompositionAttemptDescriptor attempt) {
        if (attempt.attemptOwnerSide() == null) return CompositionContextRouting.unmapped(
                "AMBIGUOUS_SIEGE_MISSING_ATTACKING_SIDE", CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE);
        return mapped(attempt, TeamCompositionContext.SIEGE, attempt.attemptOwnerSide(),
                CompositionApplicationPoint.SIEGE_PUSH, CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE,
                "STRUCTURED_ATTACKING_STRUCTURE_ATTEMPT");
    }

    private CompositionContextRouting baseDefense(CompositionAttemptDescriptor attempt) {
        TeamSide defending = attempt.defendingSide();
        if (defending == null) return CompositionContextRouting.unmapped(
                "UNMAPPED_BASE_DEFENSE_MISSING_DEFENDING_SIDE", CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE);
        return mapped(attempt, TeamCompositionContext.BASE_DEFENSE, defending,
                CompositionApplicationPoint.BASE_DEFENSE, CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,
                "STRUCTURED_BASE_DEFENSE_ATTEMPT");
    }

    private CompositionContextRouting sideLane(CompositionAttemptDescriptor attempt) {
        TeamSide perspective = first(attempt.initiatingSide(), attempt.attemptOwnerSide());
        if (perspective == null) return CompositionContextRouting.unmapped(
                "AMBIGUOUS_SIDE_LANE_MISSING_PRESSURE_SIDE", CompositionBaselineScoreDomain.SIDE_LANE_SCORE);
        return mapped(attempt, TeamCompositionContext.SIDE_LANE, perspective,
                CompositionApplicationPoint.SIDE_LANE, CompositionBaselineScoreDomain.SIDE_LANE_SCORE,
                "STRUCTURED_SIDE_LANE_ATTEMPT");
    }

    private CompositionContextRouting mapped(CompositionAttemptDescriptor attempt, TeamCompositionContext context,
                                             TeamSide perspective, CompositionApplicationPoint point,
                                             CompositionBaselineScoreDomain expectedDomain, String reason) {
        if (attempt.baselineScoreDomain() != expectedDomain) {
            return new CompositionContextRouting(true, context, perspective,
                    reason + "_SCORE_DOMAIN_UNAVAILABLE", point, CompositionBaselineScoreDomain.NOT_AVAILABLE,
                    false, null, null, ineligibleFor(attempt.actionType()),
                    ineligibleReason(attempt.actionType()), "NOT_AVAILABLE", scoreEvidence(attempt.actionType(), false));
        }
        boolean available = attempt.ownerBaselineScore() != null && attempt.opponentBaselineScore() != null;
        Double perspectiveScore = perspective == attempt.attemptOwnerSide()
                ? attempt.ownerBaselineScore() : attempt.opponentBaselineScore();
        Double opponentScore = perspective == attempt.attemptOwnerSide()
                ? attempt.opponentBaselineScore() : attempt.ownerBaselineScore();
        return new CompositionContextRouting(true, context, perspective, reason, point,
                expectedDomain, available, perspectiveScore, opponentScore,
                available ? CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN : ineligibleFor(attempt.actionType()),
                available ? "EXISTING_DETERMINISTIC_SCORE_AT_APPLICATION_POINT" : ineligibleReason(attempt.actionType()),
                available ? point.name() : "NOT_AVAILABLE", scoreEvidence(attempt.actionType(), available));
    }

    private CompositionApplicationEligibility ineligibleFor(CompositionActionType actionType) {
        return switch (actionType) {
            case SIEGE, STRUCTURE_PUSH -> CompositionApplicationEligibility.INELIGIBLE_NOT_SCORE_PRODUCING_ATTEMPT;
            case SIEGE_COMBAT -> CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN;
            case SIDE_LANE -> CompositionApplicationEligibility.DEFERRED_NO_STRUCTURED_ACTION;
            default -> CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN;
        };
    }

    private String ineligibleReason(CompositionActionType actionType) {
        return switch (actionType) {
            case JUNGLE_GANK -> "DEFERRED_NO_APPROVED_GANK_SCORE_DOMAIN";
            case COUNTER_GANK -> "DEFERRED_NO_APPROVED_COUNTER_GANK_SCORE_DOMAIN";
            case LANE_COMBAT -> "DEFERRED_NO_APPROVED_LANE_COMBAT_SCORE_DOMAIN";
            case ROAM -> "DEFERRED_NO_APPROVED_ROAM_SCORE_DOMAIN";
            case OBJECTIVE_SETUP -> "DEFERRED_NO_APPROVED_OBJECTIVE_SETUP_SCORE_DOMAIN";
            case SIEGE, STRUCTURE_PUSH -> "OBSERVATION_ONLY_STRUCTURE_ATTEMPT_WITHOUT_SCORE";
            case SIEGE_COMBAT -> "DEFERRED_NO_APPROVED_SIEGE_COMBAT_SCORE_DOMAIN";
            case SIDE_LANE -> "DEFERRED_NO_STRUCTURED_SIDE_LANE_ACTION";
            default -> "DEFERRED_NO_EXISTING_SCORE_DOMAIN";
        };
    }

    private String scoreEvidence(CompositionActionType actionType, boolean available) {
        if (available) return switch (actionType) {
            case SKIRMISH -> "MatchSimulator.skirmishInitiative values captured after team selection and before kill resolution";
            case TEAMFIGHT, BASE_DEFENSE, SIEGE_COMBAT -> "TeamfightResolver.teamfightScore values captured before combat outcome Random draws";
            default -> "Existing paired deterministic score values supplied by the score-producing resolver";
        };
        return switch (actionType) {
            case OBJECTIVE_SETUP -> "Objective decision uses weight lists and Random selection; no approved paired scalar application score";
            case JUNGLE_GANK -> "Resolver has signed combatEdge and probabilities but no existing paired scalar score domain";
            case LANE_COMBAT -> "Resolver has signed combatEdge and probabilities but no existing paired scalar score domain";
            case ROAM -> "Resolver has a signed edge breakdown but no existing paired scalar score domain";
            case SIEGE, STRUCTURE_PUSH -> "Structure attempt uses chance or sequence bookkeeping without paired scalar score";
            case SIEGE_COMBAT -> "No paired score supplied for a siege combat attempt";
            case SIDE_LANE -> "No existing structured side-lane action";
            default -> "No existing paired deterministic score at this application point";
        };
    }

    private TeamSide first(TeamSide primary, TeamSide fallback) { return primary != null ? primary : fallback; }

}

package com.lolfm.composition;

import java.util.Objects;

public record CompositionContextInteractionExplanation(
        TeamCompositionContext context,
        double teamAToTeamBPressure,
        double teamBToTeamAPressure,
        double teamASignedEdge
) {
    public CompositionContextInteractionExplanation {
        Objects.requireNonNull(context, "context");
        if (!Double.isFinite(teamAToTeamBPressure) || !Double.isFinite(teamBToTeamAPressure) || !Double.isFinite(teamASignedEdge)) {
            throw new IllegalArgumentException("Invalid context explanation number");
        }
        teamAToTeamBPressure = normalizeZero(teamAToTeamBPressure);
        teamBToTeamAPressure = normalizeZero(teamBToTeamAPressure);
        teamASignedEdge = normalizeZero(teamASignedEdge);
    }

    private static double normalizeZero(double value) { return value == 0.0 ? 0.0 : value; }
}

package com.lolfm.composition;

import java.util.Objects;

public record CompositionContextInteraction(
        TeamCompositionContext context,
        DirectedCompositionPressure teamAToTeamB,
        DirectedCompositionPressure teamBToTeamA,
        double teamASignedEdge
) {
    public CompositionContextInteraction {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(teamAToTeamB, "teamAToTeamB");
        Objects.requireNonNull(teamBToTeamA, "teamBToTeamA");
        if (teamAToTeamB.context() != context || teamBToTeamA.context() != context) throw new IllegalArgumentException("Context mismatch");
        if (!Double.isFinite(teamASignedEdge) || teamASignedEdge < -1.0 || teamASignedEdge > 1.0) throw new IllegalArgumentException("Invalid signed edge");
        teamASignedEdge = teamASignedEdge == 0.0 ? 0.0 : teamASignedEdge;
    }

    public double teamBSignedEdge() {
        return teamASignedEdge == 0.0 ? 0.0 : -teamASignedEdge;
    }
}

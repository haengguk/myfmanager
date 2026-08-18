package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.util.Comparator;
import java.util.List;

/** Deterministic robust final resolver over the small legal role-permutation space. */
public final class FinalRoleAssignmentResolver {
    private final RoleAssignmentSolver assignments;
    private final DraftMatchupEvaluator matchup;
    private final DraftCompositionEvaluator composition;

    public FinalRoleAssignmentResolver(RoleAssignmentSolver assignments, DraftMatchupEvaluator matchup,
                                       DraftCompositionEvaluator composition) {
        this.assignments = assignments; this.matchup = matchup; this.composition = composition;
    }

    public ResolvedPair resolve(List<ChampionId> bluePicks, List<ChampionId> redPicks,
                                DraftTeamContext blue, DraftTeamContext red) {
        List<RoleAssignmentSolver.RoleAssignment> blueAssignments = assignments.feasibleAssignments(bluePicks);
        List<RoleAssignmentSolver.RoleAssignment> redAssignments = assignments.feasibleAssignments(redPicks);
        RoleAssignmentSolver.RoleAssignment blueChoice = robustChoice(blueAssignments, redAssignments, blue);
        RoleAssignmentSolver.RoleAssignment redChoice = robustChoice(redAssignments, blueAssignments, red);
        return new ResolvedPair(blueChoice, redChoice);
    }

    private RoleAssignmentSolver.RoleAssignment robustChoice(
            List<RoleAssignmentSolver.RoleAssignment> own,
            List<RoleAssignmentSolver.RoleAssignment> opponents,
            DraftTeamContext team) {
        return own.stream().max(Comparator.comparingDouble((RoleAssignmentSolver.RoleAssignment candidate) ->
                        opponents.stream().mapToDouble(opponent -> utility(candidate, opponent, team)).min().orElse(0.0))
                .thenComparing(RoleAssignmentSolver.RoleAssignment::stableId, Comparator.reverseOrder()))
                .orElseThrow(() -> new IllegalArgumentException("No legal final role assignment"));
    }

    public double utility(RoleAssignmentSolver.RoleAssignment own,
                          RoleAssignmentSolver.RoleAssignment opponent,
                          DraftTeamContext team) {
        double proficiency = assignments.proficiencyScore(own, team);
        double matchupScore = DraftMatchupEvaluator.normalize(matchup.assignmentEdge(own, opponent));
        double compositionScore = composition.assignmentQuality(own);
        return proficiency * 0.45 + matchupScore * 0.35 + compositionScore * 0.20;
    }

    public record ResolvedPair(RoleAssignmentSolver.RoleAssignment blue,
                               RoleAssignmentSolver.RoleAssignment red) { }
}

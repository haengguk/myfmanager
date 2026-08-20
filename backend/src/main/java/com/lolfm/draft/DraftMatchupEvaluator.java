package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionMatchupEvaluator;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.List;

/** Draft adapter; all pair edges come directly from the frozen production GEOMETRIC_V2 evaluator. */
public final class DraftMatchupEvaluator {
    private static final double DRAFT_FULL_SCALE_EDGE = 0.30;
    private final RoleAssignmentSolver assignments;
    private final ChampionMatchupEvaluator production;

    public DraftMatchupEvaluator(RoleAssignmentSolver assignments, ChampionRoleMatchupProfileCatalog profiles) {
        this.assignments = assignments;
        this.production = new ChampionMatchupEvaluator(profiles);
    }

    public double roleEdge(ChampionRoleKey source, ChampionRoleKey opponent) {
        return production.evaluate(source, opponent, ProgressionCombatContext.LANE_COMBAT,
                ChampionMatchupMode.GEOMETRIC_V2).finalEdge();
    }

    public double robustScore(List<ChampionId> ownPicks, List<ChampionId> enemyPicks) {
        List<RoleAssignmentSolver.RoleAssignment> own = assignments.feasibleAssignments(ownPicks);
        List<RoleAssignmentSolver.RoleAssignment> enemy = assignments.feasibleAssignments(enemyPicks);
        if (own.isEmpty()) return 0.0;
        if (enemy.isEmpty()) return 10.0;
        double robustEdge = own.stream().mapToDouble(ours -> enemy.stream()
                .mapToDouble(theirs -> assignmentEdge(ours, theirs)).min().orElse(0.0)).max().orElse(0.0);
        return normalize(robustEdge);
    }

    public double assignmentEdge(RoleAssignmentSolver.RoleAssignment own,
                                 RoleAssignmentSolver.RoleAssignment enemy) {
        double total = 0.0;
        for (Position position : Position.values()) {
            ChampionId source = championAt(own, position);
            ChampionId opponent = championAt(enemy, position);
            if (source != null && opponent != null) {
                total += roleEdge(new ChampionRoleKey(source, position), new ChampionRoleKey(opponent, position));
            }
        }
        return total / Position.values().length;
    }

    public static double normalize(double edge) {
        return Math.max(0.0, Math.min(20.0, 10.0 + edge / DRAFT_FULL_SCALE_EDGE * 10.0));
    }

    public static double positiveThreatScore(double edge) {
        if (edge <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(20.0, edge / DRAFT_FULL_SCALE_EDGE * 20.0));
    }
    private static ChampionId championAt(RoleAssignmentSolver.RoleAssignment assignment, Position position) {
        return assignment.positions().entrySet().stream().filter(entry -> entry.getValue() == position)
                .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }
}

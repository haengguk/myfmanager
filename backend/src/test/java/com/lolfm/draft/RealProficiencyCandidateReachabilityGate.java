package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Objects;

/**
 * Reusable test-side contract for the post-player-proficiency reachability audit.
 * It deliberately depends on Champion Proficiency-bearing draft context only;
 * Player Ratings are not an input to candidate membership.
 */
final class RealProficiencyCandidateReachabilityGate {
    static final int DEFAULT_HIGH_PROFICIENCY_THRESHOLD = 17;
    static final String PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE =
            "PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE";

    private final ChampionCatalog champions;
    private final RoleAssignmentSolver assignments;
    private final DraftAvailability availability;
    private final DraftCandidateGenerator productionCandidateGenerator;

    RealProficiencyCandidateReachabilityGate(DraftResourceSet resources) {
        Objects.requireNonNull(resources, "resources");
        champions = resources.champions().catalog();
        assignments = new RoleAssignmentSolver(champions);
        availability = new DraftAvailability(champions, assignments);
        DraftCompositionEvaluator composition = new DraftCompositionEvaluator(
                champions, resources.champions().composition(), assignments);
        productionCandidateGenerator = new DraftCandidateGenerator(
                champions, resources.meta(), assignments, composition, availability,
                DraftScoringPolicy.standard());
    }

    DraftCandidateGenerator productionCandidateGenerator() {
        return productionCandidateGenerator;
    }

    Result evaluate(ProficiencySubjectKey playerKey, ChampionRoleKey championRole,
                    int proficiency, List<Scenario> scenarios) {
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(championRole, "championRole");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        if (!champions.supports(championRole)) {
            throw new IllegalArgumentException("Champion role is not legal: " + championRole.stableId());
        }
        if (proficiency < 1 || proficiency > 20) {
            throw new IllegalArgumentException("Champion proficiency must be 1..20: " + proficiency);
        }

        int legalScenarioCount = 0;
        int candidateAppearanceCount = 0;
        List<ScenarioResult> scenarioResults = new java.util.ArrayList<>();
        for (Scenario scenario : scenarios) {
            boolean legal = isLegalScenario(scenario, championRole);
            boolean present = false;
            if (legal) {
                legalScenarioCount++;
                List<ChampionId> candidates = productionCandidateGenerator.generate(
                        scenario.state(), scenario.own(), scenario.enemy(),
                        scenario.ownPortfolio(), scenario.enemyPortfolio());
                present = candidates.contains(championRole.championId());
                if (present) candidateAppearanceCount++;
            }
            scenarioResults.add(new ScenarioResult(scenario.id(), legal, present));
        }

        boolean high = proficiency >= DEFAULT_HIGH_PROFICIENCY_THRESHOLD;
        boolean reachable = high && legalScenarioCount > 0 && candidateAppearanceCount > 0;
        String reason = !high
                ? "BELOW_HIGH_PROFICIENCY_THRESHOLD"
                : legalScenarioCount == 0
                        ? "NO_LEGAL_SCENARIOS"
                        : candidateAppearanceCount == 0
                                ? "CANDIDATE_ABSENT_FROM_ALL_LEGAL_SHORTLISTS"
                                : "CANDIDATE_APPEARS_IN_LEGAL_SHORTLIST";
        return new Result(playerKey.stableId(), championRole.championId(), championRole.position(), proficiency,
                scenarios.size(), legalScenarioCount, candidateAppearanceCount, candidateAppearanceCount > 0,
                reachable, reason, scenarioResults);
    }

    private boolean isLegalScenario(Scenario scenario, ChampionRoleKey championRole) {
        if (scenario == null || scenario.state() == null || scenario.state().complete()
                || scenario.state().currentTurn().actionType() != DraftActionType.PICK
                || scenario.state().currentTurn().side() != scenario.side()) return false;
        if (!scenario.state().unavailableChampions().contains(championRole.championId())
                && assignments.isFeasible(append(scenario.state().picks(scenario.side()), championRole.championId()))
                && availability.canComplete(scenario.state(), scenario.side(), championRole.championId())) {
            return scenario.own().proficiency(championRole) >= DEFAULT_HIGH_PROFICIENCY_THRESHOLD;
        }
        return false;
    }

    private static List<ChampionId> append(List<ChampionId> values, ChampionId candidate) {
        java.util.ArrayList<ChampionId> result = new java.util.ArrayList<>(values);
        result.add(candidate);
        return result;
    }

    record ProficiencySubjectKey(String teamCode, Position position) {
        ProficiencySubjectKey {
            if (teamCode == null || teamCode.isBlank()) throw new IllegalArgumentException("teamCode is required");
            Objects.requireNonNull(position, "position");
        }

        String stableId() { return teamCode + ":" + position.name(); }
    }

    record Scenario(
            String id,
            TeamSide side,
            DraftState state,
            DraftTeamContext own,
            DraftTeamContext enemy,
            DraftPlanPortfolio ownPortfolio,
            DraftPlanPortfolio enemyPortfolio
    ) {
        Scenario {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("scenario id is required");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(own, "own");
            Objects.requireNonNull(enemy, "enemy");
            Objects.requireNonNull(ownPortfolio, "ownPortfolio");
            Objects.requireNonNull(enemyPortfolio, "enemyPortfolio");
        }
    }

    record ScenarioResult(String id, boolean legal, boolean candidatePresent) { }

    record Result(
            String playerKey,
            ChampionId championId,
            Position position,
            int proficiency,
            int scenarioCount,
            int legalScenarioCount,
            int candidateAppearanceCount,
            boolean candidateScenarioPresence,
            boolean reachable,
            String reason,
            List<ScenarioResult> scenarios
    ) {
        Result {
            scenarios = List.copyOf(scenarios);
        }
    }
}

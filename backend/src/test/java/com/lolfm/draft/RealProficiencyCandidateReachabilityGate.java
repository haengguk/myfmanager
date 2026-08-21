package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingKey;
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
    private final PlayerRatingCatalog ratings;

    RealProficiencyCandidateReachabilityGate(DraftResourceSet resources) {
        this(resources, PlayerRatingCatalog.loadDefault());
    }

    RealProficiencyCandidateReachabilityGate(DraftResourceSet resources,
                                              PlayerRatingCatalog ratings) {
        Objects.requireNonNull(resources, "resources");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
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

    Result evaluate(PlayerRatingKey playerKey, ChampionRoleKey championRole,
                    List<Scenario> scenarios) {
        return evaluate(null, playerKey, championRole, scenarios);
    }

    Result evaluate(PlayerId playerId, PlayerRatingKey playerKey, ChampionRoleKey championRole,
                    List<Scenario> scenarios) {
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(championRole, "championRole");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        if (!subjectRoleMatches(playerKey, championRole)) {
            throw new IllegalArgumentException("INVALID_SUBJECT_ROLE_BINDING: subject position "
                    + playerKey.position() + " does not match champion role position "
                    + championRole.position());
        }
        if (!champions.supports(championRole)) {
            throw new IllegalArgumentException("Champion role is not legal: " + championRole.stableId());
        }
        if (playerId != null) {
            validatePlayerRatingBinding(playerId, playerKey);
            validatePlayerIdentity(playerId, championRole, scenarios);
        }
        int proficiency = boundProficiency(scenarios, championRole);

        int championLevelLegalScenarioCount = 0;
        int roleSpecificLegalScenarioCount = 0;
        int championCandidateAppearanceCount = 0;
        int roleKeyReachableScenarioCount = 0;
        int championPresentButTargetRoleInfeasibleCount = 0;
        int championPresentButRoleCompletionImpossibleCount = 0;
        List<ScenarioResult> scenarioResults = new java.util.ArrayList<>();
        for (Scenario scenario : scenarios) {
            ScenarioFeasibility feasibility = feasibility(scenario, championRole);
            boolean championCandidatePresent = false;
            if (feasibility.championLevelLegal()) {
                championLevelLegalScenarioCount++;
                List<ChampionId> candidates = productionCandidateGenerator.generate(
                        scenario.state(), scenario.own(), scenario.enemy(),
                        scenario.ownPortfolio(), scenario.enemyPortfolio());
                championCandidatePresent = candidates.contains(championRole.championId());
                if (championCandidatePresent) championCandidateAppearanceCount++;
            }
            if (feasibility.roleSpecificCompletionFeasible()) roleSpecificLegalScenarioCount++;
            boolean roleKeyReachable = championCandidatePresent
                    && feasibility.roleSpecificCompletionFeasible();
            if (roleKeyReachable) roleKeyReachableScenarioCount++;
            if (championCandidatePresent && !feasibility.targetRoleFeasible()) {
                championPresentButTargetRoleInfeasibleCount++;
            }
            if (championCandidatePresent && feasibility.targetRoleFeasible()
                    && !feasibility.roleSpecificCompletionFeasible()) {
                championPresentButRoleCompletionImpossibleCount++;
            }
            scenarioResults.add(new ScenarioResult(scenario.id(), feasibility.championLevelLegal(),
                    championCandidatePresent, feasibility.targetRoleFeasible(),
                    feasibility.roleSpecificCompletionFeasible(), roleKeyReachable));
        }

        boolean high = proficiency >= DEFAULT_HIGH_PROFICIENCY_THRESHOLD;
        boolean championCandidateScenarioPresence = championCandidateAppearanceCount > 0;
        boolean roleKeyScenarioPresence = roleKeyReachableScenarioCount > 0;
        boolean roleKeyReachable = high && roleKeyScenarioPresence;
        String reason = reason(high, championLevelLegalScenarioCount, roleSpecificLegalScenarioCount,
                championCandidateAppearanceCount, roleKeyReachableScenarioCount,
                championPresentButTargetRoleInfeasibleCount,
                championPresentButRoleCompletionImpossibleCount);
        return new Result(playerKey, playerId, championRole.championId(), championRole.position(), proficiency,
                scenarios.size(), championLevelLegalScenarioCount, roleSpecificLegalScenarioCount,
                championCandidateAppearanceCount, roleKeyReachableScenarioCount,
                championPresentButTargetRoleInfeasibleCount,
                championPresentButRoleCompletionImpossibleCount,
                championCandidateScenarioPresence, roleKeyScenarioPresence, roleKeyReachable,
                reason, true, true, scenarioResults);
    }

    static boolean subjectRoleMatches(PlayerRatingKey playerKey, ChampionRoleKey championRole) {
        return playerKey != null && championRole != null
                && playerKey.position() == championRole.position();
    }

    private void validatePlayerRatingBinding(PlayerId playerId, PlayerRatingKey playerKey) {
        PlayerId expected;
        try {
            expected = ratings.playerId(playerKey);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("PLAYER_ID_RATING_KEY_MISMATCH: " + playerId
                    + "/" + playerKey.stableId(), error);
        }
        if (!expected.equals(playerId)) {
            throw new IllegalArgumentException("PLAYER_ID_RATING_KEY_MISMATCH: " + playerId
                    + "/" + playerKey.stableId());
        }
    }

    private static void validatePlayerIdentity(PlayerId playerId, ChampionRoleKey championRole,
                                               List<Scenario> scenarios) {
        if (scenarios.isEmpty()) throw new IllegalArgumentException("At least one scenario is required");
        for (Scenario scenario : scenarios) {
            PlayerId bound = scenario.own().playerId(championRole.position())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "PROFICIENCY_BINDING_MISMATCH: real context has no PlayerId for "
                                    + championRole.position()));
            if (!playerId.equals(bound)) {
                throw new IllegalArgumentException("PROFICIENCY_BINDING_MISMATCH: subject="
                        + playerId + ", context=" + bound);
            }
        }
    }

    private static int boundProficiency(List<Scenario> scenarios, ChampionRoleKey championRole) {
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("At least one scenario is required");
        }
        int expected = scenarios.getFirst().own().proficiency(championRole);
        for (Scenario scenario : scenarios) {
            int observed = scenario.own().proficiency(championRole);
            if (observed != expected) {
                throw new IllegalArgumentException("PROFICIENCY_BINDING_MISMATCH: scenario contexts disagree for "
                        + championRole.stableId() + ": expected=" + expected + ", observed=" + observed);
            }
        }
        return expected;
    }

    private ScenarioFeasibility feasibility(Scenario scenario, ChampionRoleKey championRole) {
        if (scenario == null || scenario.state() == null || scenario.state().complete()
                || scenario.state().currentTurn().actionType() != DraftActionType.PICK
                || scenario.state().currentTurn().side() != scenario.side()) {
            return new ScenarioFeasibility(false, false, false);
        }
        ChampionId candidate = championRole.championId();
        if (scenario.state().unavailableChampions().contains(candidate)) {
            return new ScenarioFeasibility(false, false, false);
        }
        List<ChampionId> nextPicks = append(scenario.state().picks(scenario.side()), candidate);
        boolean championLevelLegal = assignments.isFeasible(nextPicks)
                && availability.canComplete(scenario.state(), scenario.side(), candidate);
        if (!championLevelLegal) return new ScenarioFeasibility(false, false, false);
        boolean targetRoleFeasible = assignments.feasibleCandidatePositions(
                scenario.state().picks(scenario.side()), candidate).contains(championRole.position());
        boolean roleSpecificCompletionFeasible = targetRoleFeasible
                && availability.canCompleteWithCandidateAtRole(
                        scenario.state(), scenario.side(), candidate, championRole.position());
        return new ScenarioFeasibility(true, targetRoleFeasible, roleSpecificCompletionFeasible);
    }

    private static String reason(boolean high, int championLevelLegalScenarioCount,
                                 int roleSpecificLegalScenarioCount,
                                 int championCandidateAppearanceCount,
                                 int roleKeyReachableScenarioCount,
                                 int championPresentButTargetRoleInfeasibleCount,
                                 int championPresentButRoleCompletionImpossibleCount) {
        if (!high) return "BELOW_HIGH_PROFICIENCY_THRESHOLD";
        if (championLevelLegalScenarioCount == 0) return "NO_CHAMPION_LEVEL_LEGAL_SCENARIO";
        if (roleKeyReachableScenarioCount > 0) {
            return "CHAMPION_ROLE_KEY_APPEARS_IN_LEGAL_SHORTLIST";
        }
        if (roleSpecificLegalScenarioCount == 0) {
            if (championPresentButTargetRoleInfeasibleCount > 0) {
                return "CHAMPION_PRESENT_BUT_TARGET_ROLE_INFEASIBLE";
            }
            if (championPresentButRoleCompletionImpossibleCount > 0) {
                return "CHAMPION_PRESENT_BUT_ROLE_COMPLETION_IMPOSSIBLE";
            }
            return "NO_ROLE_SPECIFIC_LEGAL_SCENARIO";
        }
        if (championCandidateAppearanceCount == 0) {
            return "CHAMPION_ABSENT_FROM_ALL_LEGAL_SHORTLISTS";
        }
        if (championPresentButTargetRoleInfeasibleCount > 0) {
            return "CHAMPION_PRESENT_BUT_TARGET_ROLE_INFEASIBLE";
        }
        if (championPresentButRoleCompletionImpossibleCount > 0) {
            return "CHAMPION_PRESENT_BUT_ROLE_COMPLETION_IMPOSSIBLE";
        }
        return "CHAMPION_ABSENT_FROM_ALL_LEGAL_SHORTLISTS";
    }

    private static List<ChampionId> append(List<ChampionId> values, ChampionId candidate) {
        java.util.ArrayList<ChampionId> result = new java.util.ArrayList<>(values);
        result.add(candidate);
        return result;
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

    private record ScenarioFeasibility(boolean championLevelLegal, boolean targetRoleFeasible,
                                       boolean roleSpecificCompletionFeasible) { }

    record ScenarioResult(
            String scenarioId,
            boolean championLevelLegal,
            boolean championCandidatePresent,
            boolean targetRoleFeasible,
            boolean roleSpecificCompletionFeasible,
            boolean roleKeyReachable
    ) {
        String id() { return scenarioId; }
        boolean legal() { return championLevelLegal; }
        boolean candidatePresent() { return championCandidatePresent; }
    }

    record Result(
            PlayerRatingKey playerKey,
            PlayerId playerId,
            ChampionId championId,
            Position position,
            int proficiency,
            int scenarioCount,
            int championLevelLegalScenarioCount,
            int roleSpecificLegalScenarioCount,
            int championCandidateAppearanceCount,
            int roleKeyReachableScenarioCount,
            int championPresentButTargetRoleInfeasibleCount,
            int championPresentButRoleCompletionImpossibleCount,
            boolean championCandidateScenarioPresence,
            boolean roleKeyScenarioPresence,
            boolean roleKeyReachable,
            String reason,
            boolean bindingValidated,
            boolean subjectRoleMatched,
            List<ScenarioResult> scenarios
    ) {
        Result {
            scenarios = List.copyOf(scenarios);
        }

        int legalScenarioCount() { return championLevelLegalScenarioCount; }
        int candidateAppearanceCount() { return championCandidateAppearanceCount; }
        boolean candidateScenarioPresence() { return championCandidateScenarioPresence; }
        boolean reachable() { return roleKeyReachable; }
    }
}

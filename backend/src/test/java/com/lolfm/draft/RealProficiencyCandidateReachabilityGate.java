package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
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
        if (playerId != null) validatePlayerIdentity(playerId, championRole, scenarios);
        int proficiency = boundProficiency(scenarios, championRole);

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
        return new Result(playerKey, playerId, championRole.championId(), championRole.position(), proficiency,
                scenarios.size(), legalScenarioCount, candidateAppearanceCount, candidateAppearanceCount > 0,
                reachable, reason, true, true, scenarioResults);
    }

    static boolean subjectRoleMatches(PlayerRatingKey playerKey, ChampionRoleKey championRole) {
        return playerKey != null && championRole != null
                && playerKey.position() == championRole.position();
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

    private boolean isLegalScenario(Scenario scenario, ChampionRoleKey championRole) {
        if (scenario == null || scenario.state() == null || scenario.state().complete()
                || scenario.state().currentTurn().actionType() != DraftActionType.PICK
                || scenario.state().currentTurn().side() != scenario.side()) return false;
        if (!scenario.state().unavailableChampions().contains(championRole.championId())
                && assignments.isFeasible(append(scenario.state().picks(scenario.side()), championRole.championId()))
                && availability.canComplete(scenario.state(), scenario.side(), championRole.championId())) {
            return true;
        }
        return false;
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

    record ScenarioResult(String id, boolean legal, boolean candidatePresent) { }

    record Result(
            PlayerRatingKey playerKey,
            PlayerId playerId,
            ChampionId championId,
            Position position,
            int proficiency,
            int scenarioCount,
            int legalScenarioCount,
            int candidateAppearanceCount,
            boolean candidateScenarioPresence,
            boolean reachable,
            String reason,
            boolean bindingValidated,
            boolean subjectRoleMatched,
            List<ScenarioResult> scenarios
    ) {
        Result {
            scenarios = List.copyOf(scenarios);
        }
    }
}

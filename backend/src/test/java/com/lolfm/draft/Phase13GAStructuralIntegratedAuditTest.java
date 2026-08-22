package com.lolfm.draft;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.simulator.EndGameEvaluator;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.ObjectiveAttemptResolver;
import com.lolfm.simulator.ObjectiveResolver;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.PostFightResolver;
import com.lolfm.simulator.PushResolver;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SnapshotFactory;
import com.lolfm.simulator.StructureResolver;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TeamfightResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase13GAStructuralIntegratedAuditTest {
    private DraftResourceSet resources;
    private List<Phase13GASyntheticContextFactory.SyntheticContext> contexts;
    private Phase13GAAuditSchedule.Schedule schedule;
    private DraftEngine engine;
    private FinalDraftResult neutralDraft;

    @BeforeAll
    void setUp() {
        resources = DraftResourceSet.loadDefault();
        contexts = Phase13GASyntheticContextFactory.create(resources);
        schedule = Phase13GAAuditSchedule.freeze(contexts);
        engine = new DraftEngine(resources);
        neutralDraft = engine.draft(context("synthetic-neutral"), context("synthetic-neutral"), new SeriesDraftHistory());
    }

    @Test
    void syntheticContextGenerationIsExactAndDeterministic() {
        List<Phase13GASyntheticContextFactory.SyntheticContext> replay =
                Phase13GASyntheticContextFactory.create(resources);
        assertThat(contexts).hasSize(24);
        assertThat(contexts.stream().map(value -> value.id()).toList())
                .containsExactlyElementsOf(replay.stream().map(value -> value.id()).toList());
        assertThat(contexts.stream().map(Phase13GASyntheticContextFactory.SyntheticContext::canonicalProficiency).toList())
                .containsExactlyElementsOf(replay.stream().map(Phase13GASyntheticContextFactory.SyntheticContext::canonicalProficiency).toList());
        assertThat(contexts).allSatisfy(value -> assertThat(value.proficiencyByRole()).hasSize(216));
        assertThat(contexts.getFirst().kind()).isEqualTo("NEUTRAL");
        assertThat(contexts.getFirst().proficiencyByRole().values()).containsOnly(10);
    }

    @Test
    void syntheticContextUsesAll216LegalRoleKeys() {
        Set<ChampionRoleKey> legal = resources.champions().catalog().legalRoleKeys();
        assertThat(contexts).allSatisfy(value -> assertThat(value.proficiencyByRole().keySet()).containsExactlyInAnyOrderElementsOf(legal));
    }

    @Test
    void syntheticContextContainsNoRealPlayerIdentity() {
        assertThat(contexts.stream().map(Phase13GASyntheticContextFactory.SyntheticContext::id).toList())
                .allMatch(value -> !value.toLowerCase().matches(".*(chovy|canyon|faker|lck|player).*"));
    }

    @Test
    void auditScheduleIsFrozenAndDeterministic() {
        assertThat(schedule.gameOneCases()).hasSizeGreaterThanOrEqualTo(96)
                .isEqualTo(Phase13GAAuditSchedule.freeze(contexts).gameOneCases());
        assertThat(schedule.fearlessSeries()).isEqualTo(Phase13GAAuditSchedule.freeze(contexts).fearlessSeries());
        assertThat(schedule.gameOneCases()).isSortedAccordingTo(Comparator.comparing(
                Phase13GAAuditSchedule.GameOneCase::caseId));
    }

    @Test
    void mirroredScheduleContainsBothSideOrientations() {
        boolean mirrored = schedule.gameOneCases().stream().anyMatch(value -> schedule.gameOneCases().stream()
                .anyMatch(reverse -> value.blueContextId().equals(reverse.redContextId())
                        && value.redContextId().equals(reverse.blueContextId())
                        && !value.caseId().equals(reverse.caseId())));
        assertThat(mirrored).isTrue();
    }

    @Test
    void everyGameOneDraftCompletesTwentyActions() {
        assertThat(neutralDraft.decisions()).hasSize(20);
        assertThat(neutralDraft.blueBans()).hasSize(5);
        assertThat(neutralDraft.redBans()).hasSize(5);
        assertThat(neutralDraft.bluePicks()).hasSize(5);
        assertThat(neutralDraft.redPicks()).hasSize(5);
        assertThat(neutralDraft.decisions()).allSatisfy(value -> assertThat(value.topAlternatives()).isNotEmpty());
    }

    @Test
    void everyFinalDraftHasFiveUniqueLegalRolesPerTeam() {
        assertThat(neutralDraft.blueFinalRoleAssignments().values()).containsExactlyInAnyOrder(Position.values());
        assertThat(neutralDraft.redFinalRoleAssignments().values()).containsExactlyInAnyOrder(Position.values());
        for (Map.Entry<ChampionId, Position> entry : neutralDraft.blueFinalRoleAssignments().entrySet()) {
            assertThat(resources.champions().catalog().supports(new ChampionRoleKey(entry.getKey(), entry.getValue()))).isTrue();
        }
        for (Map.Entry<ChampionId, Position> entry : neutralDraft.redFinalRoleAssignments().entrySet()) {
            assertThat(resources.champions().catalog().supports(new ChampionRoleKey(entry.getKey(), entry.getValue()))).isTrue();
        }
    }

    @Test
    void candidatePoolNeverBecomesEmpty() {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        Phase13GASyntheticContextFactory.SyntheticContext neutral = contextValue("synthetic-neutral");
        RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
        DraftAvailability availability = new DraftAvailability(resources.champions().catalog(), roles);
        DraftCompositionEvaluator composition = new DraftCompositionEvaluator(resources.champions().catalog(), resources.champions().composition(), roles);
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        DraftCandidateGenerator generator = new DraftCandidateGenerator(resources.champions().catalog(), resources.meta(), roles, composition, availability, policy);
        PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(), resources.champions().composition(), roles);
        for (DraftDecision ignored : neutralDraft.decisions()) {
            TeamSide side = state.currentTurn().side();
            DraftTeamContext own = neutral.draftContext();
            DraftPlanPortfolio ownPlan = planner.replan(own, own, side, state);
            assertThat(generator.generate(state, own, own, ownPlan, ownPlan)).isNotEmpty();
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), ignored.selectedChampionId()));
        }
        assertThat(state.complete()).isTrue();
    }

    @Test
    void finalMatchAssignmentsAreExplicitAndStructured() {
        assertThat(neutralDraft.matchChampionAssignments().selectionMode()).isEqualTo(ChampionSelectionMode.EXPLICIT);
        assertThat(neutralDraft.matchChampionAssignments().asMap()).hasSize(10);
        for (TeamSide side : TeamSide.values()) for (Position position : Position.values()) {
            ChampionAssignment value = neutralDraft.matchChampionAssignments().get(new PlayerKey(side, position));
            assertThat(value.playerKey().position()).isEqualTo(position);
            assertThat(value.selectedPosition()).isEqualTo(position);
            assertThat(resources.champions().catalog().supports(new ChampionRoleKey(value.championId(), position))).isTrue();
        }
    }

    @Test
    void exactDraftReplayIsDeterministic() {
        FinalDraftResult replay = engine.draft(context("synthetic-neutral"), context("synthetic-neutral"), new SeriesDraftHistory());
        assertThat(replay.decisions()).isEqualTo(neutralDraft.decisions());
        assertThat(replay.blueFinalRoleAssignments()).isEqualTo(neutralDraft.blueFinalRoleAssignments());
        assertThat(replay.redFinalRoleAssignments()).isEqualTo(neutralDraft.redFinalRoleAssignments());
        assertThat(replay.matchChampionAssignments().asMap()).isEqualTo(neutralDraft.matchChampionAssignments().asMap());
    }

    @Test
    void hardFearlessNeverReusesPriorCompletedPick() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        Set<ChampionId> prior = new HashSet<>();
        for (int game = 0; game < 5; game++) {
            FinalDraftResult result = engine.draft(context("synthetic-neutral"), context("synthetic-high-baseline"), history);
            Set<ChampionId> picks = new HashSet<>(result.bluePicks());
            picks.addAll(result.redPicks());
            assertThat(picks).hasSize(10);
            assertThat(result.hardFearlessExclusions()).containsExactlyInAnyOrderElementsOf(prior);
            history.commitCompleted(result);
            prior.addAll(picks);
        }
        assertThat(history.committedGameCount()).isEqualTo(5);
    }

    @Test
    void hardFearlessBanDoesNotConsumeChampion() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        FinalDraftResult gameOne = neutralDraft;
        history.commitCompleted(gameOne);
        assertThat(history.consumedPicks()).doesNotContainAnyElementsOf(gameOne.blueBans());
        assertThat(history.consumedPicks()).doesNotContainAnyElementsOf(gameOne.redBans());
        assertThat(engine.draft(context("synthetic-neutral"), context("synthetic-neutral"), history)
                .hardFearlessExclusions()).containsExactlyInAnyOrderElementsOf(history.consumedPicks());
    }

    @Test
    void freshSeriesHasNoPriorFearlessHistory() {
        assertThat(new SeriesDraftHistory().consumedPicks()).isEmpty();
        FinalDraftResult fresh = engine.draft(context("synthetic-neutral"), context("synthetic-neutral"), new SeriesDraftHistory());
        assertThat(fresh.hardFearlessExclusions()).isEmpty();
    }

    @Test
    void allFiveFearlessGamesRemainCompletable() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        for (int game = 0; game < 5; game++) {
            FinalDraftResult result = engine.draft(context("synthetic-flex-wide"), context("synthetic-flex-narrow"), history);
            assertThat(result.decisions()).hasSize(20);
            history.commitCompleted(result);
        }
    }

    @Test
    void componentDistributionContainsNoNaNOrInfinity() {
        assertThat(neutralDraft.decisions()).allSatisfy(decision ->
                assertThat(decision.componentBreakdown().values()).allMatch(Double::isFinite));
    }

    @Test
    void protectionControlledCaseIsPositiveOnlyForSameRoleThreat() {
        RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
        DraftAvailability availability = new DraftAvailability(resources.champions().catalog(), roles);
        DraftCompositionEvaluator composition = new DraftCompositionEvaluator(resources.champions().catalog(), resources.champions().composition(), roles);
        DraftMatchupEvaluator matchup = new DraftMatchupEvaluator(roles, resources.champions().matchup());
        ChampionId carry = new ChampionId("caitlyn");
        DraftState state = new DraftState(DraftRuleSet.professional(), 13, List.of(carry), List.of(), List.of(), List.of(), Set.of());
        ChampionId threat = resources.champions().catalog().forPosition(Position.ADC).stream().map(value -> value.id())
                .filter(value -> !value.equals(carry)).max(Comparator.comparingDouble(value -> matchup.roleEdge(
                        new ChampionRoleKey(value, Position.ADC), new ChampionRoleKey(carry, Position.ADC)))).orElseThrow();
        DraftPlanPortfolio portfolio = new DraftPlanPortfolio(List.of(new DraftPlan(DraftPlanArchetype.FRONT_TO_BACK,
                DraftPlanArchetype.FRONT_TO_BACK.desired(), DraftPlanArchetype.FRONT_TO_BACK.vulnerabilities(), List.of(), Map.of(), 10.0)));
        BanEvaluation positive = new BanEvaluator(resources.champions().catalog(), resources.meta(), resources.champions().composition(), roles,
                availability, composition, matchup, DraftScoringPolicy.standard()).evaluate(state, TeamSide.BLUE, threat,
                context("synthetic-neutral"), context("synthetic-neutral"), portfolio, portfolio);
        assertThat(positive.components().get(BanScoreComponent.PROTECTION_VALUE)).isPositive();
        assertThat(new BanEvaluator(resources.champions().catalog(), resources.meta(), resources.champions().composition(), roles,
                availability, composition, matchup, DraftScoringPolicy.standard()).evaluate(state, TeamSide.BLUE, new ChampionId("fiora"),
                context("synthetic-neutral"), context("synthetic-neutral"), portfolio, portfolio)
                .components().get(BanScoreComponent.PROTECTION_VALUE)).isZero();
    }

    @Test
    void currentImpossibleFlexRoleDoesNotAffectIntegratedDecision() {
        ChampionId taliyah = new ChampionId("taliyah");
        assertThat(resources.champions().catalog().supports(new ChampionRoleKey(taliyah, Position.ADC))).isTrue();
        DraftState state = new DraftState(DraftRuleSet.professional(), 19,
                List.of(), List.of(new ChampionId("fiora"), new ChampionId("orianna"), new ChampionId("caitlyn"), new ChampionId("soraka")),
                List.of(), List.of(), Set.of());
        RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
        assertThat(roles.feasibleCandidatePositions(state.picks(TeamSide.RED), taliyah)).containsExactly(Position.JUNGLE);
        Map<ChampionRoleKey, Integer> impossibleValues = new java.util.HashMap<>(contextValue("synthetic-neutral").proficiencyByRole());
        impossibleValues.put(new ChampionRoleKey(taliyah, Position.MID), 20);
        impossibleValues.put(new ChampionRoleKey(taliyah, Position.ADC), 20);
        DraftTeamContext impossibleHigh = context(impossibleValues);
        assertThat(roles.practicalFlexValue(state.picks(TeamSide.RED), taliyah, context("synthetic-neutral")))
                .isEqualTo(roles.practicalFlexValue(state.picks(TeamSide.RED), taliyah, impossibleHigh));
    }

    @Test
    void planPortfolioCanPivotUnderLegalBans() {
        RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
        PreDraftPlanner planner = new PreDraftPlanner(resources.champions().catalog(), resources.meta(), resources.champions().composition(), roles);
        DraftState state = stateAfter(List.of("camille", "vi", "poppy", "nautilus", "fiora", "jax", "syndra", "varus", "ryze", "ezreal", "bard", "kaisa"));
        DraftTeamContext blue = context("synthetic-meta-contrarian"), red = context("synthetic-neutral");
        DraftPlanArchetype before = planner.replan(blue, red, TeamSide.BLUE, state).preferred().archetype();
        DraftState after = state.apply(new DraftAction(state.currentTurn().number(), state.currentTurn().side(), state.currentTurn().actionType(), new ChampionId("anivia")));
        assertThat(planner.replan(blue, red, TeamSide.BLUE, after).preferred().archetype()).isNotEqualTo(before);
    }

    @Test
    void multiRoleChampionCanResolveToMultipleLegalRolesAcrossControlledContexts() {
        RoleAssignmentSolver roles = new RoleAssignmentSolver(resources.champions().catalog());
        List<ChampionId> picks = List.of(new ChampionId("taliyah"), new ChampionId("varus"), new ChampionId("poppy"), new ChampionId("gnar"), new ChampionId("graves"));
        assertThat(roles.isFeasible(picks)).isTrue();
        Position wide = roles.bestAssignment(picks, context("synthetic-flex-wide")).positionOf(new ChampionId("taliyah"));
        Position narrow = roles.bestAssignment(picks, context("synthetic-flex-narrow")).positionOf(new ChampionId("taliyah"));
        assertThat(resources.champions().catalog().supports(new ChampionRoleKey(new ChampionId("taliyah"), wide))).isTrue();
        assertThat(resources.champions().catalog().supports(new ChampionRoleKey(new ChampionId("taliyah"), narrow))).isTrue();
    }

    @Test
    void endToEndDraftAssignmentsReachMatchSimulator() {
        MatchSimulator simulator = new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(resources.champions().catalog()),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults(), resources.champions().matchup());
        MatchTimeline result = simulator.simulate(team(TeamSide.BLUE, "synthetic-neutral"), team(TeamSide.RED, "synthetic-high-baseline"), 1301L,
                neutralDraft.matchChampionAssignments());
        assertThat(result.getWinner()).isNotBlank();
        assertThat(result.getDurationSeconds()).isPositive();
        assertThat(result.getEvents()).isNotEmpty();
        assertThat(result.getSnapshots()).isNotEmpty();
    }

    @Test
    void sameDraftSameSeedTimelineReplayIsExact() {
        MatchSimulator simulator = new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(resources.champions().catalog()),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults(), resources.champions().matchup());
        MatchTimeline first = simulator.simulate(team(TeamSide.BLUE, "synthetic-neutral"), team(TeamSide.RED, "synthetic-high-baseline"), 1302L,
                neutralDraft.matchChampionAssignments());
        MatchTimeline second = simulator.simulate(team(TeamSide.BLUE, "synthetic-neutral"), team(TeamSide.RED, "synthetic-high-baseline"), 1302L,
                neutralDraft.matchChampionAssignments());
        assertThat(first.getWinner()).isEqualTo(second.getWinner());
        assertThat(first.getDurationSeconds()).isEqualTo(second.getDurationSeconds());
        assertCompleteTimelineEquals(first, second);
    }

    @Test
    void frozenResourceHashesRemainExact() {
        assertThat(resources.meta().requiredLegalRoleKeyCount()).isEqualTo(216);
        assertThat(resources.meta().actualLegalRoleKeyHash()).isEqualTo(Phase13GAStructuralIntegratedAudit.LEGAL_ROLE_HASH);
        assertThat(resources.champions().composition().profileHash()).isEqualTo(Phase13GAStructuralIntegratedAudit.COMPOSITION_HASH);
    }

    private DraftTeamContext context(String id) { return contextValue(id).draftContext(); }
    private DraftTeamContext context(Map<ChampionRoleKey, Integer> values) {
        EnumMap<Position, ChampionProficiencies> byPosition = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<ChampionRoleKey, Integer> selected = new java.util.HashMap<>();
            values.forEach((key, value) -> {
                if (key.position() == position) selected.put(key, value);
            });
            byPosition.put(position, new ChampionProficiencies(selected));
        }
        return new DraftTeamContext(byPosition);
    }

    private Phase13GASyntheticContextFactory.SyntheticContext contextValue(String id) {
        return contexts.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private DraftState stateAfter(List<String> values) {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        for (String value : values) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), new ChampionId(value)));
        }
        return state;
    }

    private Team team(TeamSide side, String contextId) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new Player("synthetic-" + side.name().toLowerCase() + "-" + position.name().toLowerCase(), position,
                    PlayerRatings.neutral(position), contextValue(contextId).draftContext().proficiencies().get(position)));
        }
        return new Team("synthetic-" + side.name().toLowerCase(), players);
    }
}

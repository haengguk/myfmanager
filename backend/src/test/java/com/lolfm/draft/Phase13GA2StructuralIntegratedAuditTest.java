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
import java.nio.file.Files;
import java.nio.file.Path;
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
class Phase13GA2StructuralIntegratedAuditTest {
    private DraftResourceSet resources;
    private List<Phase13GASyntheticContextFactory.SyntheticContext> contexts;
    private Phase13GA2AuditSchedule.Schedule schedule;
    private DraftEngine engine;
    private FinalDraftResult neutralDraft;
    private Phase13GA2StructuralIntegratedAudit audit;
    private Phase13GA2StructuralIntegratedAudit.DraftAudit neutralAudit;

    @BeforeAll
    void setUp() {
        resources = DraftResourceSet.loadDefault();
        contexts = Phase13GASyntheticContextFactory.create(resources);
        schedule = Phase13GA2AuditSchedule.freeze(contexts);
        engine = new DraftEngine(resources);
        neutralDraft = engine.draft(context("synthetic-neutral"), context("synthetic-neutral"), new SeriesDraftHistory());
        audit = new Phase13GA2StructuralIntegratedAudit();
        neutralAudit = audit.auditSingle("focused-neutral", "synthetic-neutral", "synthetic-neutral", new SeriesDraftHistory());
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
        Phase13GA2AuditSchedule.Schedule replay = Phase13GA2AuditSchedule.freeze(contexts);
        assertThat(schedule.gameOneCases()).hasSize(120).isEqualTo(replay.gameOneCases());
        assertThat(schedule.unorderedPairs()).hasSize(60).isEqualTo(replay.unorderedPairs());
        assertThat(schedule.fearlessSeries()).hasSize(12).isEqualTo(replay.fearlessSeries());
        assertThat(schedule.scheduleHash()).isEqualTo(replay.scheduleHash());
        assertThat(schedule.permutation()).containsExactlyElementsOf(replay.permutation());
    }

    @Test
    void v2GameOneScheduleHasExactPairAndOrientationCardinality() {
        assertThat(schedule.gameOneCases()).hasSize(120);
        assertThat(schedule.unorderedPairs()).hasSize(60);
        assertThat(schedule.gameOneCases()).allSatisfy(value -> assertThat(value.blueContextId()).isNotEqualTo(value.redContextId()));
        assertThat(schedule.gameOneCases().stream().map(Phase13GA2AuditSchedule.GameOneCase::orderedKey).distinct()).hasSize(120);
        assertThat(schedule.gameOneCases().stream().map(value -> normalizedPair(value.blueContextId(), value.redContextId())).distinct()).hasSize(60);
    }

    @Test
    void v2GameOneScheduleContainsEveryReverseOrientationAndBalancesContexts() {
        for (Phase13GA2AuditSchedule.GameOneCase value : schedule.gameOneCases()) {
            assertThat(schedule.gameOneCases().stream().filter(reverse ->
                    reverse.blueContextId().equals(value.redContextId())
                            && reverse.redContextId().equals(value.blueContextId()))).hasSize(1);
        }
        Map<String, Long> total = schedule.gameOneCases().stream()
                .flatMap(value -> java.util.stream.Stream.of(value.blueContextId(), value.redContextId()))
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        Map<String, Long> blue = schedule.gameOneCases().stream().collect(Collectors.groupingBy(
                Phase13GA2AuditSchedule.GameOneCase::blueContextId, Collectors.counting()));
        Map<String, Long> red = schedule.gameOneCases().stream().collect(Collectors.groupingBy(
                Phase13GA2AuditSchedule.GameOneCase::redContextId, Collectors.counting()));
        assertThat(total.values()).containsOnly(10L);
        assertThat(blue.values()).containsOnly(5L);
        assertThat(red.values()).containsOnly(5L);
    }

    @Test
    void v2FearlessScheduleUsesEveryContextExactlyOnceAndHasTwelvePairs() {
        assertThat(schedule.fearlessSeries()).hasSize(12);
        assertThat(schedule.fearlessSeries().stream().flatMap(value -> java.util.stream.Stream.of(
                value.blueContextId(), value.redContextId()))).containsExactlyInAnyOrderElementsOf(
                contexts.stream().map(Phase13GASyntheticContextFactory.SyntheticContext::id).toList());
        assertThat(schedule.fearlessSeries().stream().map(Phase13GA2AuditSchedule.FearlessSeriesCase::unorderedKey).distinct()).hasSize(12);
        assertThat(schedule.fearlessSeries()).allSatisfy(value -> assertThat(value.blueContextId()).isNotEqualTo(value.redContextId()));
    }

    @Test
    void controlledProbesContainNeutralMirrorsAndFlexMirrors() {
        assertThat(schedule.controlledProbes().stream().map(Phase13GA2AuditSchedule.ControlledProbeCase::probeId))
                .containsExactly("neutral-vs-neutral", "meta-aligned-blue-vs-contrarian-red",
                        "meta-contrarian-blue-vs-aligned-red", "flex-wide-blue-vs-narrow-red",
                        "flex-narrow-blue-vs-wide-red");
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
        assertThat(resources.meta().actualLegalRoleKeyHash()).isEqualTo(Phase13GA2StructuralIntegratedAudit.LEGAL_ROLE_HASH);
        assertThat(resources.champions().composition().profileHash()).isEqualTo(Phase13GA2StructuralIntegratedAudit.COMPOSITION_HASH);
    }

    
    @Test
    void rawLegalCandidateCountIsNeverBelowGeneratedShortlist() {
        assertThat(neutralAudit.success()).isTrue();
        assertThat(neutralAudit.candidateTrace()).allSatisfy(trace ->
                assertThat(trace.rawLegalActionCandidateCount()).isGreaterThanOrEqualTo(trace.generatedShortlistCount()));
    }

    
    @Test
    void generatedShortlistNeverExceedsTwelve() {
        assertThat(neutralAudit.candidateTrace()).allSatisfy(trace ->
                assertThat(trace.generatedShortlistCount()).isBetween(1, 12));
    }

    
    @Test
    void generatedShortlistNeverEmptyOnValidAuditDraft() {
        assertThat(neutralAudit.success()).isTrue();
        assertThat(neutralAudit.candidateTrace()).allSatisfy(trace -> assertThat(trace.candidates()).isNotEmpty());
    }

    
    @Test
    void selectedChampionAlwaysBelongsToGeneratedShortlist() {
        assertThat(neutralAudit.candidateTrace()).allSatisfy(trace -> assertThat(trace.selectedInsideGeneratedShortlist()).isTrue());
    }

    
    @Test
    void rawLegalPoolAndGeneratedShortlistAreSeparateMetrics() {
        assertThat(neutralAudit.candidateTrace()).allSatisfy(trace -> {
            assertThat(trace.rawLegalActionCandidateCount()).isNotEqualTo(trace.generatedShortlistCount());
            assertThat(trace.rawAvailableChampionCount()).isGreaterThanOrEqualTo(trace.generatedShortlistCount());
            assertThat(trace.rawAvailableLegalRoleKeyCount()).isPositive();
        });
    }

    
    @Test
    void candidateCoverageContainsExactly173Champions() {
        assertThat(audit.candidateCoverageFor(List.of(neutralAudit))).hasSize(173)
                .allSatisfy(row -> assertThat(row).containsKeys("championId", "pickOccurrences", "banOccurrences",
                        "candidateAppearanceCount", "highProficiencyContextCount", "roleAssignmentKeys"));
    }

    
    @Test
    void neverSelectedChampionDoesNotAutomaticallyMeanSystemicStarvation() {
        assertThat(Phase13GA2StructuralIntegratedAudit.candidateStarvationEligible(Map.of(
                "highProficiencyContextCount", 0, "candidateAppearanceCount", 0))).isFalse();
        assertThat(Phase13GA2StructuralIntegratedAudit.candidateStarvationEligible(Map.of(
                "highProficiencyContextCount", 2, "candidateAppearanceCount", 3))).isFalse();
    }

    
    @Test
    void highProficiencyZeroCandidateAppearanceIsReviewable() {
        assertThat(Phase13GA2StructuralIntegratedAudit.candidateStarvationEligible(Map.of(
                "highProficiencyContextCount", 1, "candidateAppearanceCount", 0))).isTrue();
    }

    
    @Test
    void componentDistributionContainsGameOneScope() {
        assertThat(audit.componentDistributionFor("GAME1", List.of(neutralAudit))).isNotEmpty()
                .allSatisfy(value -> assertThat(value.scope()).isEqualTo("GAME1"));
    }

    
    @Test
    void componentDistributionContainsLaterFearlessScope() {
        assertThat(audit.componentDistributionFor("LATER_FEARLESS", List.of(neutralAudit))).isNotEmpty()
                .allSatisfy(value -> assertThat(value.scope()).isEqualTo("LATER_FEARLESS"));
    }

    
    @Test
    void laterFearlessComponentScopeContainsExactly48Drafts() {
        List<Phase13GA2StructuralIntegratedAudit.DraftAudit> later =
                Phase13GA2StructuralIntegratedAudit.laterFearlessDraftsForAudit(fearlessFixture());
        assertThat(later).hasSize(48);
    }

    
    @Test
    void laterFearlessComponentScopeContainsGamesTwoThroughFiveOnly() {
        List<Phase13GA2StructuralIntegratedAudit.DraftAudit> later =
                Phase13GA2StructuralIntegratedAudit.laterFearlessDraftsForAudit(fearlessFixture());
        assertThat(later).allMatch(value -> value.caseId().matches(".*-game-[2345]"));
        assertThat(later).noneMatch(value -> value.caseId().endsWith("-game-1"));
    }

    
    @Test
    void laterFearlessLatencyContainsExactly48Drafts() {
        assertThat(Phase13GA2StructuralIntegratedAudit.laterFearlessDraftsForAudit(fearlessFixture())).hasSize(48);
    }

    
    @Test
    void engineAndValidationLatencyAreReportedSeparately() {
        assertThat(neutralAudit.engineDraftMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(neutralAudit.validationMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(neutralAudit.totalAuditCaseMillis()).isEqualTo(
                neutralAudit.engineDraftMillis() + neutralAudit.validationMillis());
    }

    
    @Test
    void integrationSelectsExactlyTwentyDrafts() {
        List<Phase13GA2StructuralIntegratedAudit.IntegrationSelection> selections = audit.plannedIntegrationSelections();
        assertThat(selections).hasSize(20);
        assertThat(selections.stream().map(Phase13GA2StructuralIntegratedAudit.IntegrationSelection::draftId).distinct()).hasSize(20);
    }

    
    @Test
    void gameOneIntegrationCoversAll24SyntheticContexts() {
        Set<String> selected = audit.plannedIntegrationSelections().stream()
                .filter(value -> value.source().equals("GAME1"))
                .flatMap(value -> java.util.stream.Stream.of(value.blueContextId(), value.redContextId()))
                .collect(Collectors.toSet());
        assertThat(selected).containsExactlyInAnyOrderElementsOf(contexts.stream()
                .map(Phase13GASyntheticContextFactory.SyntheticContext::id).toList());
    }

    
    @Test
    void laterIntegrationIncludesGamesTwoThreeFourFive() {
        List<Integer> numbers = audit.plannedIntegrationSelections().stream()
                .filter(value -> value.source().equals("LATER_FEARLESS"))
                .map(Phase13GA2StructuralIntegratedAudit.IntegrationSelection::gameNumber).toList();
        assertThat(numbers).containsExactly(2, 3, 4, 5, 2, 3, 4, 5);
    }

    
    @Test
    void integrationSelectionIsOutcomeIndependent() {
        assertThat(audit.plannedIntegrationSelections())
                .isEqualTo(new Phase13GA2StructuralIntegratedAudit().plannedIntegrationSelections());
    }

    
    @Test
    void allFortyIntegrationRunsAreScheduledForTwoSeeds() {
        assertThat((long) audit.plannedIntegrationSelections().size()
                * Phase13GA2StructuralIntegratedAudit.INTEGRATION_SEEDS.size()).isEqualTo(40L);
    }

    
    @Test
    void allTwelveSeriesAreScheduledForFiveGames() {
        assertThat(schedule.fearlessSeries()).hasSize(12);
        assertThat(Phase13GA2StructuralIntegratedAudit.LATER_FEARLESS_DRAFT_COUNT).isEqualTo(48);
    }

    
    @Test
    void v1ArtifactDirectoryIsNotOverwritten() {
        Path v1 = Path.of("build/reports/phase13g-a");
        assertThat(v1.resolve("phase13g-a-structural-integrated-audit-summary.json")).exists();
        assertThat(v1).isNotEqualTo(Path.of(Phase13GA2StructuralIntegratedAudit.OUTPUT_DIRECTORY));
    }

    
    @Test
    void v2ArtifactsUseDedicatedDirectory() {
        assertThat(Phase13GA2StructuralIntegratedAudit.OUTPUT_DIRECTORY).isEqualTo("build/reports/phase13g-a-v2");
        assertThat(Phase13GA2AuditArtifactWriter.finalArtifactNames()).allMatch(value -> value.startsWith("phase13g-a-v2-"));
    }

    
    @Test
    void finalShaManifestMatchesEveryArtifact() throws Exception {
        Path temp = Files.createTempDirectory("phase13g-a2-manifest");
        for (String name : Phase13GA2AuditArtifactWriter.finalArtifactNames()) Files.writeString(temp.resolve(name), name);
        Phase13GA2AuditArtifactWriter.writeShaManifest(temp);
        assertThat(Phase13GA2AuditArtifactWriter.verifyShaManifest(temp)).isTrue();
    }

    
    @Test
    void v2VerdictIsComputedNotHardcoded() {
        assertThat(Phase13GA2StructuralIntegratedAudit.computedVerdict(List.of(), List.of()))
                .isEqualTo("PHASE_13G_A_V2_STRUCTURAL_BASELINE_COMPLETE");
        assertThat(Phase13GA2StructuralIntegratedAudit.computedVerdict(List.of(), List.of("REVIEW_X")))
                .isEqualTo("PHASE_13G_A_V2_STRUCTURAL_BASELINE_COMPLETE_WITH_REVIEWS");
        assertThat(Phase13GA2StructuralIntegratedAudit.computedVerdict(List.of("BLOCKER"), List.of()))
                .isEqualTo("PHASE_13G_A_V2_STRUCTURAL_BASELINE_BLOCKED");
    }

    
    @Test
    void frozenProductionSearchBoundsRemainExact() {
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        assertThat(policy.candidateLimit()).isEqualTo(12);
        assertThat(policy.structuralRepairSlots()).isEqualTo(4);
        assertThat(policy.searchDepth()).isEqualTo(3);
        assertThat(policy.beamWidth()).isEqualTo(2);
    }

    private List<Phase13GA2StructuralIntegratedAudit.FearlessSeriesAudit> fearlessFixture() {
        List<Phase13GA2StructuralIntegratedAudit.FearlessSeriesAudit> result = new ArrayList<>();
        for (int series = 1; series <= 12; series++) {
            List<Phase13GA2StructuralIntegratedAudit.DraftAudit> games = new ArrayList<>();
            for (int game = 1; game <= 5; game++) {
                games.add(new Phase13GA2StructuralIntegratedAudit.DraftAudit(
                        "fixture-series-" + series + "-game-" + game, "blue", "red", null,
                        List.of(), List.of(), 0L, 0L, 0L, "", Map.of(), Map.of(), Map.of(), Map.of()));
            }
            result.add(new Phase13GA2StructuralIntegratedAudit.FearlessSeriesAudit(
                    "fixture-series-" + series, "blue-" + series, "red-" + series,
                    games, true, 0, 0, "fixture"));
        }
        return result;
    }

    private static String normalizedPair(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
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

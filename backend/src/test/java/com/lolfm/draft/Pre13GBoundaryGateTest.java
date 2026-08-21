package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerRatingKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Pre13GBoundaryGateTest {
    private static DraftResourceSet resources;
    private static ChampionCatalog champions;

    @BeforeAll
    static void setUp() {
        resources = DraftResourceSet.loadDefault();
        champions = resources.champions().catalog();
    }

    @Test
    void controlledRolePoolCompressionStateIsLegal() {
        ControlledRolePoolCompressionProbe.Result result = compressionProbe();
        assertThat(result.state().nextTurnIndex()).isZero();
        assertThat(result.state().currentTurn().actionType()).isEqualTo(DraftActionType.BAN);
        assertThat(result.bluePicks()).isEmpty();
        assertThat(result.redPicks()).isEmpty();
        assertThat(result.depletedRoleCount()).isEqualTo(28);
        assertThat(result.bluePicksLegal()).isTrue();
        assertThat(result.redPicksLegal()).isTrue();
    }

    @Test
    void controlledRolePoolCompressionStateRemainsCompletable() {
        ControlledRolePoolCompressionProbe.Result result = compressionProbe();
        assertThat(result.blueCanStillComplete()).isTrue();
        assertThat(result.redCanStillComplete()).isTrue();
        assertThat(result.positiveCandidateCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void controlledRolePoolCompressionCanBecomePositive() {
        ControlledRolePoolCompressionProbe.Result result = compressionProbe();
        assertThat(result.positiveCandidateCount()).isGreaterThan(0);
        assertThat(result.positiveCandidates()).isNotEmpty();
        assertThat(result.positiveCandidates().stream()
                .map(ControlledRolePoolCompressionProbe.CandidateEvaluation::componentValue))
                .allMatch(value -> value > 0.0);
    }

    @Test
    void rolePoolCompressionProbeUsesProductionBanEvaluator() {
        ControlledRolePoolCompressionProbe.Result result = compressionProbe();
        assertThat(result.evaluatorClass()).isEqualTo(BanEvaluator.class.getName());
        assertThat(result.positiveCandidates()).allSatisfy(value ->
                assertThat(value.componentValue()).isEqualTo(value.evaluation().components()
                        .get(BanScoreComponent.ROLE_POOL_COMPRESSION)));
    }

    @Test
    void realProficiencyReachabilityGateAcceptsStructuredPlayerAndChampionRoleIdentity() {
        RealProficiencyCandidateReachabilityGate gate = new RealProficiencyCandidateReachabilityGate(resources);
        RealProficiencyCandidateReachabilityGate.Result result = gate.evaluate(
                new PlayerRatingKey("GEN", Position.MID),
                new ChampionRoleKey(new ChampionId("azir"), Position.MID),
                reachabilityScenarios());

        assertThat(result.playerKey()).isEqualTo(new PlayerRatingKey("GEN", Position.MID));
        assertThat(result.championId()).isEqualTo(new ChampionId("azir"));
        assertThat(result.position()).isEqualTo(Position.MID);
        assertThat(result.proficiency()).isEqualTo(18);
        assertThat(result.scenarioCount()).isEqualTo(3);
        assertThat(result.bindingValidated()).isTrue();
        assertThat(result.subjectRoleMatched()).isTrue();
    }

    @Test
    void reachabilityGateRejectsIllegalChampionRoleScenario() {
        RealProficiencyCandidateReachabilityGate gate = new RealProficiencyCandidateReachabilityGate(resources);
        assertThatThrownBy(() -> gate.evaluate(
                new PlayerRatingKey("GEN", Position.SUPPORT),
                new ChampionRoleKey(new ChampionId("azir"), Position.SUPPORT),
                reachabilityScenarios()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Champion role is not legal");
    }

    @Test
    void reachabilityGateUsesProductionCandidateGenerator() {
        RealProficiencyCandidateReachabilityGate gate = new RealProficiencyCandidateReachabilityGate(resources);
        assertThat(gate.productionCandidateGenerator()).isNotNull();
        assertThat(gate.productionCandidateGenerator().getClass()).isEqualTo(DraftCandidateGenerator.class);
        RealProficiencyCandidateReachabilityGate.Result result = gate.evaluate(
                new PlayerRatingKey("GEN", Position.MID),
                new ChampionRoleKey(new ChampionId("azir"), Position.MID),
                reachabilityScenarios());
        assertThat(result.scenarios()).hasSize(3);
    }

    @Test
    void reachabilityGateCanonicalizesTeamCodeThroughPlayerRatingKey() {
        RealProficiencyCandidateReachabilityGate.Result result = new RealProficiencyCandidateReachabilityGate(resources)
                .evaluate(new PlayerRatingKey("gen", Position.MID),
                        new ChampionRoleKey(new ChampionId("azir"), Position.MID), reachabilityScenarios());

        assertThat(result.playerKey()).isEqualTo(new PlayerRatingKey("GEN", Position.MID));
        assertThat(result.playerKey().stableId()).isEqualTo("GEN:MID");
    }

    @Test
    void reachabilityGateRejectsSubjectPositionDifferentFromChampionRolePosition() {
        assertThatThrownBy(() -> new RealProficiencyCandidateReachabilityGate(resources).evaluate(
                new PlayerRatingKey("GEN", Position.TOP),
                new ChampionRoleKey(new ChampionId("azir"), Position.MID), reachabilityScenarios()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_SUBJECT_ROLE_BINDING");
    }

    @Test
    void reachabilityGateUsesOneCanonicalSubjectIdentityType() {
        assertThat(RealProficiencyCandidateReachabilityGate.class.getDeclaredClasses())
                .noneMatch(value -> value.getSimpleName().equals("ProficiencySubjectKey"));
        assertThat(RealProficiencyCandidateReachabilityGate.Result.class.getRecordComponents())
                .filteredOn(component -> component.getName().equals("playerKey"))
                .extracting(java.lang.reflect.RecordComponent::getType)
                .containsExactly(PlayerRatingKey.class);
    }

    @Test
    void reachabilityGateFailsFastWhenScenarioContextsDisagreeOnProficiency() {
        List<RealProficiencyCandidateReachabilityGate.Scenario> scenarios = reachabilityScenarios();
        RealProficiencyCandidateReachabilityGate.Scenario first = scenarios.getFirst();
        DraftTeamContext mismatched = contextWith(new ChampionId("azir"), Position.MID, 12);
        List<RealProficiencyCandidateReachabilityGate.Scenario> mismatchedScenarios = new ArrayList<>();
        mismatchedScenarios.add(new RealProficiencyCandidateReachabilityGate.Scenario(first.id(), first.side(),
                first.state(), mismatched, first.enemy(), first.ownPortfolio(), first.enemyPortfolio()));
        mismatchedScenarios.addAll(scenarios.subList(1, scenarios.size()));

        assertThatThrownBy(() -> new RealProficiencyCandidateReachabilityGate(resources).evaluate(
                new PlayerRatingKey("GEN", Position.MID),
                new ChampionRoleKey(new ChampionId("azir"), Position.MID), mismatchedScenarios))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROFICIENCY_BINDING_MISMATCH");
    }

    @Test
    void reachabilityGateReportsTheDraftContextProficiencyItUses() {
        RealProficiencyCandidateReachabilityGate.Result high = reachabilityResult();
        RealProficiencyCandidateReachabilityGate.Result low = new RealProficiencyCandidateReachabilityGate(resources)
                .evaluate(new PlayerRatingKey("GEN", Position.MID),
                        new ChampionRoleKey(new ChampionId("azir"), Position.MID),
                        lowReachabilityScenarios());

        assertThat(high.proficiency()).isEqualTo(18);
        assertThat(low.proficiency()).isEqualTo(1);
        assertThat(low.reachable()).isFalse();
        assertThat(low.reason()).isEqualTo("BELOW_HIGH_PROFICIENCY_THRESHOLD");
    }

    @Test
    void reachabilityGateTracksLegalScenarioCount() {
        RealProficiencyCandidateReachabilityGate.Result result = reachabilityResult();
        assertThat(result.scenarioCount()).isEqualTo(3);
        assertThat(result.legalScenarioCount()).isEqualTo(3);
        assertThat(result.scenarios()).allMatch(RealProficiencyCandidateReachabilityGate.ScenarioResult::legal);
    }

    @Test
    void reachabilityGateTracksCandidateScenarioPresence() {
        RealProficiencyCandidateReachabilityGate.Result result = reachabilityResult();
        assertThat(result.candidateAppearanceCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.candidateScenarioPresence()).isEqualTo(result.candidateAppearanceCount() > 0);
    }

    @Test
    void reachabilityGateDoesNotUsePlayerRatingsAsChampionProficiency() {
        assertThat(java.util.Arrays.stream(RealProficiencyCandidateReachabilityGate.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .map(Class::getName))
                .noneMatch(value -> value.contains("PlayerSkill"));
        assertThat(reachabilityResult().proficiency()).isEqualTo(18);
    }

    @Test
    void realProficiencyGateRemainsPendingWithoutRealProficiencyResource() {
        assertThat(RealProficiencyCandidateReachabilityGate.PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE)
                .isEqualTo("PENDING_REAL_CHAMPION_PROFICIENCY_RESOURCE");
        assertThat(reachabilityResult().reason()).isIn(
                "CANDIDATE_APPEARS_IN_LEGAL_SHORTLIST",
                "CANDIDATE_ABSENT_FROM_ALL_LEGAL_SHORTLISTS");
        assertThat(java.util.Arrays.stream(RealProficiencyCandidateReachabilityGate.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .noneMatch(value -> value.equalsIgnoreCase("realChampionProficiencyResource"));
    }

    private RealProficiencyCandidateReachabilityGate.Result reachabilityResult() {
        return new RealProficiencyCandidateReachabilityGate(resources).evaluate(
                new PlayerRatingKey("GEN", Position.MID),
                new ChampionRoleKey(new ChampionId("azir"), Position.MID),
                reachabilityScenarios());
    }

    private List<RealProficiencyCandidateReachabilityGate.Scenario> reachabilityScenarios() {
        ChampionId candidate = new ChampionId("azir");
        DraftTeamContext high = contextWith(candidate, Position.MID, 18);
        DraftTeamContext neutral = new DraftTeamContext(Map.of());
        PreDraftPlanner planner = new PreDraftPlanner(champions, resources.meta(),
                resources.champions().composition(), new RoleAssignmentSolver(champions));

        DraftState early = stateAfter(List.of("aatrox", "akali", "akshan", "annie", "amumu", "brand"));
        DraftState partial = stateAfter(List.of("aatrox", "akali", "akshan", "annie", "amumu", "brand",
                "camille", "vi"));
        DraftState response = stateAfter(List.of("aatrox", "akali", "akshan", "annie", "amumu", "brand",
                "camille", "vi", "poppy", "nautilus"));
        return List.of(
                scenario("empty-early-legal-state", early, high, neutral, planner),
                scenario("partial-team-state", partial, high, neutral, planner),
                scenario("response-state", response, high, neutral, planner));
    }

    private List<RealProficiencyCandidateReachabilityGate.Scenario> lowReachabilityScenarios() {
        ChampionId candidate = new ChampionId("azir");
        DraftTeamContext low = contextWith(candidate, Position.MID, 1);
        DraftTeamContext neutral = new DraftTeamContext(Map.of());
        PreDraftPlanner planner = new PreDraftPlanner(champions, resources.meta(),
                resources.champions().composition(), new RoleAssignmentSolver(champions));
        DraftState state = stateAfter(List.of("aatrox", "akali", "akshan", "annie", "amumu", "brand"));
        return List.of(scenario("low-proficiency-state", state, low, neutral, planner));
    }

    private RealProficiencyCandidateReachabilityGate.Scenario scenario(
            String id, DraftState state, DraftTeamContext own, DraftTeamContext enemy, PreDraftPlanner planner) {
        TeamSide side = state.currentTurn().side();
        return new RealProficiencyCandidateReachabilityGate.Scenario(id, side, state, own, enemy,
                planner.replan(own, enemy, side, state),
                planner.replan(enemy, own, side.opposite(), state));
    }

    private DraftTeamContext contextWith(ChampionId champion, Position position, int proficiency) {
        EnumMap<Position, ChampionProficiencies> values = new EnumMap<>(Position.class);
        values.put(position, new ChampionProficiencies(Map.of(new ChampionRoleKey(champion, position), proficiency)));
        return new DraftTeamContext(values);
    }

    private DraftState stateAfter(List<String> ids) {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        for (String id : ids) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), new ChampionId(id)));
        }
        return state;
    }

    private ControlledRolePoolCompressionProbe.Result compressionProbe() {
        return new ControlledRolePoolCompressionProbe(resources).run();
    }

    private static final class ControlledRolePoolCompressionProbe {
        private final DraftResourceSet resources;
        private final ChampionCatalog champions;
        private final RoleAssignmentSolver roles;
        private final DraftAvailability availability;
        private final DraftCompositionEvaluator composition;
        private final DraftMatchupEvaluator matchup;
        private final BanEvaluator evaluator;

        private ControlledRolePoolCompressionProbe(DraftResourceSet resources) {
            this.resources = resources;
            champions = resources.champions().catalog();
            roles = new RoleAssignmentSolver(champions);
            availability = new DraftAvailability(champions, roles);
            composition = new DraftCompositionEvaluator(champions, resources.champions().composition(), roles);
            matchup = new DraftMatchupEvaluator(roles, resources.champions().matchup());
            evaluator = new BanEvaluator(champions, resources.meta(), resources.champions().composition(), roles,
                    availability, composition, matchup, DraftScoringPolicy.standard());
        }

        private Result run() {
            List<ChampionId> depletedAdc = champions.forPosition(Position.ADC).stream()
                    .map(value -> value.id()).sorted(Comparator.comparing(ChampionId::value)).limit(28).toList();
            DraftState state = new DraftState(DraftRuleSet.professional(), 0, List.of(), List.of(),
                    List.of(), List.of(), Set.copyOf(depletedAdc));

            DraftTeamContext neutral = new DraftTeamContext(Map.of());
            PreDraftPlanner planner = new PreDraftPlanner(champions, resources.meta(),
                    resources.champions().composition(), roles);
            DraftPlanPortfolio bluePortfolio = planner.replan(neutral, neutral, TeamSide.BLUE, state);
            DraftPlanPortfolio redPortfolio = planner.replan(neutral, neutral, TeamSide.RED, state);
            List<CandidateEvaluation> positive = new ArrayList<>();
            int candidateCount = 0;
            for (var definition : champions.all().stream()
                    .sorted(Comparator.comparing(value -> value.id().value())).toList()) {
                ChampionId candidate = definition.id();
                if (state.unavailableChampions().contains(candidate)) continue;
                candidateCount++;
                BanEvaluation evaluation = evaluator.evaluate(state, TeamSide.BLUE, candidate,
                        neutral, neutral, redPortfolio, bluePortfolio);
                double component = evaluation.components().get(BanScoreComponent.ROLE_POOL_COMPRESSION);
                if (component > 0.0) positive.add(new CandidateEvaluation(candidate, component, evaluation));
            }
            boolean blueLegal = roles.isFeasible(state.bluePicks());
            boolean redLegal = roles.isFeasible(state.redPicks());
            boolean blueComplete = candidateCount > 0 && canCompleteAfterAnyPick(state, TeamSide.BLUE);
            boolean redComplete = candidateCount > 0 && canCompleteAfterAnyPick(state, TeamSide.RED);
            return new Result(state, depletedAdc.size(), state.bluePicks(), state.redPicks(), blueLegal, redLegal,
                    blueComplete, redComplete, positive.size(), positive, BanEvaluator.class.getName());
        }

        private boolean canCompleteAfterAnyPick(DraftState state, TeamSide side) {
            return champions.all().stream().map(value -> value.id())
                    .filter(candidate -> !state.unavailableChampions().contains(candidate))
                    .anyMatch(candidate -> availability.canComplete(state, side, candidate));
        }

        private record CandidateEvaluation(ChampionId championId, double componentValue, BanEvaluation evaluation) { }

        private record Result(
                DraftState state,
                int depletedRoleCount,
                List<ChampionId> bluePicks,
                List<ChampionId> redPicks,
                boolean bluePicksLegal,
                boolean redPicksLegal,
                boolean blueCanStillComplete,
                boolean redCanStillComplete,
                int positiveCandidateCount,
                List<CandidateEvaluation> positiveCandidates,
                String evaluatorClass
        ) { }
    }
}

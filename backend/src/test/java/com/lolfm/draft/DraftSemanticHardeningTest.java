package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.TeamSide;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class DraftSemanticHardeningTest {
    private final DraftHardeningFixture f = new DraftHardeningFixture();

    @Test
    void productionMetaArtifactRetainsTheAuthoritativeByteHash() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(DraftMetaCatalog.RESOURCE)) {
            assertThat(input).isNotNull();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
            assertThat(hash).isEqualTo("dd1173aadfad92d4ec231f097653ac840809c60812a4920d32b3d9606fa7fe99");
        }
    }

    @Test
    void realCurrentGameBansRemoveCoreResourcesAndPivotThePortfolio() {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        DraftPlanPortfolio initial = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, state.fearlessExclusions());
        DraftPlan original = initial.preferred();
        for (ChampionId banned : original.coreCandidates().subList(0, 6)) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), banned));
        }
        DraftPlanPortfolio replanned = f.planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, state);
        Set<ChampionId> unavailable = state.unavailableChampions();
        assertThat(replanned.plans()).allSatisfy(plan ->
                assertThat(plan.coreCandidates()).doesNotContainAnyElementsOf(unavailable));
        double originalAfter = replanned.plans().stream().filter(plan -> plan.archetype() == original.archetype())
                .mapToDouble(DraftPlan::viability).findFirst().orElse(Double.NEGATIVE_INFINITY);
        assertThat(originalAfter).isLessThan(original.viability());
        assertThat(replanned.preferred().archetype()).isNotEqualTo(original.archetype());
    }

    @Test
    void opponentProficiencyChangesPlanExposureWithoutChampionScripts() {
        DraftPlanPortfolio neutral = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, Set.of());
        Map<ChampionRoleKey, Integer> highValues = new java.util.HashMap<>();
        f.resources.champions().catalog().legalRoleKeys().forEach(key -> highValues.put(key, 20));
        DraftTeamContext dangerousOpponent = context(highValues);
        DraftPlanPortfolio adjusted = f.planner.plan(DraftTestSupport.NEUTRAL, dangerousOpponent,
                TeamSide.BLUE, Set.of());
        assertThat(adjusted).isNotEqualTo(neutral);
    }

    @Test
    void banPruningUsesOpponentContextEvenWhenOwnProficiencyIsLow() {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        DraftPlanPortfolio enemyNeutral = f.planner.plan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.RED, Set.of());
        ChampionId opponentCore = enemyNeutral.preferred().coreCandidates().getFirst();
        Map<ChampionRoleKey, Integer> ownLow = new java.util.HashMap<>(), enemyHigh = new java.util.HashMap<>();
        f.resources.champions().catalog().get(opponentCore).supportedPositions().forEach(position -> {
            ChampionRoleKey key = new ChampionRoleKey(opponentCore, position);
            ownLow.put(key, 1); enemyHigh.put(key, 20);
        });
        DraftTeamContext own = context(ownLow), enemy = context(enemyHigh);
        DraftPlanPortfolio ownPlan = f.planner.plan(own, enemy, TeamSide.BLUE, Set.of());
        DraftPlanPortfolio enemyPlan = f.planner.plan(enemy, own, TeamSide.RED, Set.of());
        assertThat(f.candidates.generate(state, own, enemy, ownPlan, enemyPlan)).contains(opponentCore);
    }

    @Test
    void structuralRepairSlotsAreActuallyReservedOutsideTheCoarseTopTwelve() {
        DraftState state = DraftTestSupport.stateAfter(List.of(
                "rumble", "vi", "syndra", "varus", "nautilus", "poppy",
                "fiora", "jax", "graves", "lee-sin", "orianna", "zed",
                "ryze", "ezreal", "bard", "gnar", "caitlyn", "kaisa", "rell"));
        DraftPlanPortfolio own = f.planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.RED, state);
        DraftPlanPortfolio enemy = f.planner.replan(DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                TeamSide.BLUE, state);
        List<ChampionId> reserved = f.candidates.generate(state, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemy);
        DraftScoringPolicy noRepairPolicy = new DraftScoringPolicy(12, 0, 3, 2,
                f.policy.pickWeights(), f.policy.banWeights());
        DraftCandidateGenerator noRepair = new DraftCandidateGenerator(f.resources.champions().catalog(),
                f.resources.meta(), f.roles, f.composition, f.availability, noRepairPolicy);
        List<ChampionId> coarseOnly = noRepair.generate(state, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemy);
        assertThat(reserved).hasSize(12);
        assertThat(reserved).anyMatch(candidate -> !coarseOnly.contains(candidate));
    }

    @Test
    void futureCompletionRequiresAnActuallyAvailableChampionForEveryRemainingRole() {
        Set<ChampionId> allSupports = supporting(Position.SUPPORT);
        ChampionId remainingSupport = f.id("nautilus");
        Set<ChampionId> viableExclusions = new HashSet<>(allSupports);
        viableExclusions.remove(remainingSupport);
        DraftState viable = syntheticPickTurn(viableExclusions);
        DraftState impossible = syntheticPickTurn(allSupports);
        assertThat(f.availability.canComplete(viable, TeamSide.BLUE, f.id("fiora"))).isTrue();
        assertThat(f.availability.canComplete(impossible, TeamSide.BLUE, f.id("fiora"))).isFalse();
    }

    @Test
    void laterFearlessPressureProducesRealRolePoolCompression() {
        Set<ChampionId> exclusions = supporting(Position.ADC);
        exclusions.removeAll(Set.of(f.id("aphelios"), f.id("ashe"), f.id("caitlyn")));
        DraftState state = new DraftState(DraftRuleSet.professional(), 0, List.of(), List.of(),
                List.of(), List.of(), exclusions);
        double thinAdc = f.availability.rolePoolCompression(state, TeamSide.RED, f.id("aphelios"));
        double wideTop = f.availability.rolePoolCompression(state, TeamSide.RED, f.id("fiora"));
        assertThat(thinAdc).isGreaterThan(wideTop);
    }

    @Test
    void denialIsAttributedToEnemyPortfolioNotOwnPortfolio() {
        DraftState state = DraftTestSupport.stateAfter(List.of("rumble", "vi", "syndra", "varus", "nautilus", "poppy"));
        ChampionId candidate = f.id("yasuo");
        DraftPlanPortfolio ownA = portfolio(DraftPlanArchetype.POKE_SIEGE, List.of(candidate), 20);
        DraftPlanPortfolio ownB = portfolio(DraftPlanArchetype.DIVE, List.of(), 2);
        DraftPlanPortfolio enemyLow = portfolio(DraftPlanArchetype.FRONT_TO_BACK, List.of(), 2);
        DraftPlanPortfolio enemyHigh = portfolio(DraftPlanArchetype.FRONT_TO_BACK, List.of(candidate), 20);
        double low = denial(state, candidate, ownA, enemyLow);
        double high = denial(state, candidate, ownA, enemyHigh);
        double ownChanged = denial(state, candidate, ownB, enemyHigh);
        assertThat(high).isGreaterThan(low);
        assertThat(ownChanged).isEqualTo(high);
    }

    @Test
    void productionRosterFactoryRequiresExactlyFiveStructuredRoles() {
        DummyDataFactory factory = new DummyDataFactory();
        assertThat(DraftTeamContext.from(factory.createBlueTeam())).isNotNull();
        List<Player> valid = factory.createBlueTeam().getPlayers();
        assertThatThrownBy(() -> DraftTeamContext.from(new Team("missing", valid.subList(0, 4))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly");
        List<Player> duplicate = new ArrayList<>(valid.subList(0, 4));
        duplicate.add(new Player("duplicate", Position.TOP, new PlayerAttributes(10, 10, 10, 10)));
        assertThatThrownBy(() -> DraftTeamContext.from(new Team("duplicate", duplicate)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    private double denial(DraftState state, ChampionId candidate, DraftPlanPortfolio own,
                          DraftPlanPortfolio enemy) {
        return f.picks.evaluate(state, TeamSide.BLUE, candidate, DraftTestSupport.NEUTRAL,
                DraftTestSupport.NEUTRAL, own, enemy).components().get(PickScoreComponent.DENIAL);
    }
    private DraftPlanPortfolio portfolio(DraftPlanArchetype type, List<ChampionId> core, double viability) {
        return new DraftPlanPortfolio(List.of(new DraftPlan(type, type.desired(), type.vulnerabilities(),
                core, Map.of(), viability)));
    }
    private DraftTeamContext context(Map<ChampionRoleKey, Integer> values) {
        EnumMap<Position, ChampionProficiencies> byPosition = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<ChampionRoleKey, Integer> selected = values.entrySet().stream()
                    .filter(entry -> entry.getKey().position() == position)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            byPosition.put(position, new ChampionProficiencies(selected));
        }
        return new DraftTeamContext(byPosition);
    }
    private Set<ChampionId> supporting(Position position) {
        Set<ChampionId> result = new HashSet<>();
        f.resources.champions().catalog().forPosition(position).forEach(value -> result.add(value.id()));
        return result;
    }
    private DraftState syntheticPickTurn(Set<ChampionId> exclusions) {
        return new DraftState(DraftRuleSet.professional(), 6, List.of(), List.of(),
                List.of(f.id("rumble"), f.id("orianna"), f.id("zed")),
                List.of(f.id("vi"), f.id("lee-sin"), f.id("graves")), exclusions);
    }
}

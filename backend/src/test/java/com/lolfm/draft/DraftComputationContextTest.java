package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DraftComputationContextTest {
    private final DraftResourceSet resources = DraftTestSupport.RESOURCES;
    private final RoleAssignmentSolver assignments =
            new RoleAssignmentSolver(resources.champions().catalog());

    @Test
    void canonicalChampionCombinationReusesExactImmutableAssignments() {
        ChampionId yasuo = id("yasuo");
        ChampionId poppy = id("poppy");
        DraftComputationContext context = DraftComputationContext.cached();

        List<RoleAssignmentSolver.RoleAssignment> first =
                assignments.feasibleAssignments(List.of(yasuo, poppy), context);
        List<RoleAssignmentSolver.RoleAssignment> reordered =
                assignments.feasibleAssignments(List.of(poppy, yasuo), context);

        assertThat(reordered).isSameAs(first);
        assertThat(reordered).extracting(RoleAssignmentSolver.RoleAssignment::stableId)
                .containsExactlyElementsOf(first.stream()
                        .map(RoleAssignmentSolver.RoleAssignment::stableId).toList());
        assertThatThrownBy(() -> first.add(first.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.getFirst().positions().put(yasuo, Position.TOP))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(context.snapshot().roleAssignmentRequests()).isEqualTo(2);
        assertThat(context.snapshot().roleAssignmentPhysicalComputations()).isOne();
        assertThat(context.snapshot().roleAssignmentHits()).isOne();
    }

    @Test
    void emptyDuplicateAndOneToFiveChampionBoundariesKeepUncachedMeaning() {
        List<List<ChampionId>> cases = List.of(
                List.of(),
                List.of(id("yasuo")),
                List.of(id("yasuo"), id("poppy")),
                List.of(id("varus"), id("neeko"), id("rumble")),
                List.of(id("varus"), id("neeko"), id("rumble"), id("syndra")),
                List.of(id("varus"), id("neeko"), id("rumble"), id("syndra"),
                        id("naafiri")),
                List.of(id("yasuo"), id("yasuo")));
        DraftComputationContext context = DraftComputationContext.cached();

        for (List<ChampionId> champions : cases) {
            assertThat(assignments.feasibleAssignments(champions, context))
                    .isEqualTo(assignments.feasibleAssignments(champions));
        }
        assertThat(assignments.feasibleAssignments(
                List.of(id("yasuo"), id("yasuo")), context)).isEmpty();
    }

    @Test
    void availabilityAndPoolHealthCachePreserveExactBitsAndOrdering() {
        DraftState state = DraftState.fresh(
                DraftRuleSet.professional(), new SeriesDraftHistory());
        ChampionId candidate = id("poppy");
        DraftAvailability availability = new DraftAvailability(
                resources.champions().catalog(), assignments);
        DraftComputationContext context = DraftComputationContext.cached();

        boolean expectedComplete = availability.canComplete(
                state, TeamSide.BLUE, candidate);
        double expectedHealth = availability.poolHealth(
                state, TeamSide.BLUE, candidate);
        boolean firstComplete = availability.canComplete(
                state, TeamSide.BLUE, candidate, context);
        boolean replayComplete = availability.canComplete(
                state, TeamSide.BLUE, candidate, context);
        double firstHealth = availability.poolHealth(
                state, TeamSide.BLUE, candidate, context);
        double replayHealth = availability.poolHealth(
                state, TeamSide.BLUE, candidate, context);

        assertThat(firstComplete).isEqualTo(expectedComplete).isEqualTo(replayComplete);
        assertThat(Double.doubleToLongBits(firstHealth))
                .isEqualTo(Double.doubleToLongBits(expectedHealth))
                .isEqualTo(Double.doubleToLongBits(replayHealth));
        assertThat(context.snapshot().completionHits()).isOne();
        assertThat(context.snapshot().poolHealthHits()).isOne();
        assertThat(assignments.feasibleCandidatePositions(
                List.of(), candidate, context)).containsExactlyInAnyOrder(
                assignments.feasibleCandidatePositions(List.of(), candidate)
                        .toArray(Position[]::new));
        assertThatThrownBy(() -> assignments.feasibleCandidatePositions(
                List.of(), candidate, context).add(Position.TOP))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cachedAndUncachedFullDraftsAreBitExactWithLessPhysicalWork() {
        DraftEngine engine = new DraftEngine(resources);
        AutoDraftObservationHarnessV1 observer =
                new AutoDraftObservationHarnessV1(engine);

        AutoDraftObservationHarnessV1.Observation uncached = observer.observeUncached(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                new SeriesDraftHistory());
        AutoDraftObservationHarnessV1.Observation cached = observer.observe(
                DraftTestSupport.NEUTRAL, DraftTestSupport.NEUTRAL,
                new SeriesDraftHistory());

        assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                uncached.result(), cached.result())).isTrue();
        assertThat(cached.counters()).isEqualTo(uncached.counters());
        assertThat(cached.turns().stream().map(
                AutoDraftObservationHarnessV1.TurnObservation::rootCandidateScores).toList())
                .isEqualTo(uncached.turns().stream().map(
                        AutoDraftObservationHarnessV1.TurnObservation::rootCandidateScores)
                        .toList());
        assertThat(cached.computation().roleAssignmentPhysicalComputations())
                .isLessThan(uncached.computation().roleAssignmentPhysicalComputations());
        assertThat(cached.computation().completionPhysicalComputations())
                .isLessThan(uncached.computation().completionPhysicalComputations());
        assertThat(cached.computation().poolHealthPhysicalComputations())
                .isLessThan(uncached.computation().poolHealthPhysicalComputations());
        assertThat(cached.computation().roleAssignmentHits()).isPositive();
        assertThat(cached.computation().completionHits()).isPositive();
        assertThat(cached.computation().poolHealthHits()).isPositive();
        assertThat(cached.computation().plannerCandidatePhysicalComputations()).isPositive();
        assertThat(cached.computation().plannerCandidatePhysicalComputations())
                .isLessThan(uncached.computation().plannerCandidatePhysicalComputations());
        assertThat(cached.computation().plannerCandidateLocalReuses()).isPositive();
    }

    @Test
    void freshContextsDoNotAccumulateAcrossOneHundredSequentialLifecyclesOrFailure() {
        ArrayList<DraftComputationContext.Snapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            DraftComputationContext context = DraftComputationContext.cached();
            assignments.feasibleAssignments(List.of(id("yasuo"), id("poppy")), context);
            assignments.feasibleAssignments(List.of(id("poppy"), id("yasuo")), context);
            snapshots.add(context.snapshot());
        }
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.roleAssignmentEntries()).isOne();
            assertThat(snapshot.roleAssignmentHits()).isOne();
            assertThat(snapshot.roleAssignmentPhysicalComputations()).isOne();
        });

        DraftComputationContext failed = DraftComputationContext.cached();
        assertThatThrownBy(() -> failed.roleAssignments(List.of(id("yasuo")),
                () -> { throw new IllegalStateException("expected failure"); }))
                .isInstanceOf(IllegalStateException.class);
        DraftComputationContext fresh = DraftComputationContext.cached();
        assignments.feasibleAssignments(List.of(id("yasuo")), fresh);
        assertThat(fresh.snapshot().roleAssignmentEntries()).isOne();
        assertThat(fresh.snapshot().roleAssignmentHits()).isZero();
    }

    @Test
    void singletonEngineConcurrentSameAndDifferentFixturesMatchSequentialResults()
            throws Exception {
        DraftEngine engine = new DraftEngine(resources);
        DraftTeamContext neutral = DraftTestSupport.NEUTRAL;
        DraftTeamContext fitted = DraftTestSupport.context(Position.JUNGLE,
                Map.of(new com.lolfm.champion.ChampionRoleKey(
                        id("poppy"), Position.JUNGLE), 20));
        FinalDraftResult expectedSame = engine.draftDeterministicBest(
                neutral, neutral, new SeriesDraftHistory());
        FinalDraftResult expectedDifferent = engine.draftDeterministicBest(
                fitted, neutral, new SeriesDraftHistory());

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<FinalDraftResult>> same = List.of(
                    () -> engine.draftDeterministicBest(neutral, neutral, new SeriesDraftHistory()),
                    () -> engine.draftDeterministicBest(neutral, neutral, new SeriesDraftHistory()));
            for (var future : executor.invokeAll(same)) {
                assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                        expectedSame, future.get())).isTrue();
            }
            List<Callable<FinalDraftResult>> different = List.of(
                    () -> engine.draftDeterministicBest(neutral, neutral, new SeriesDraftHistory()),
                    () -> engine.draftDeterministicBest(fitted, neutral, new SeriesDraftHistory()));
            List<java.util.concurrent.Future<FinalDraftResult>> results =
                    executor.invokeAll(different);
            assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                    expectedSame, results.get(0).get())).isTrue();
            assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                    expectedDifferent, results.get(1).get())).isTrue();
        }
    }

    @Test
    void optimizationStateExistsOnlyInTheDraftScopedContext() {
        assertThat(List.of(RoleAssignmentSolver.class, DraftAvailability.class,
                PreDraftPlanner.class, DraftCandidateGenerator.class, DraftEngine.class))
                .allSatisfy(type -> assertThat(List.of(type.getDeclaredFields()))
                        .noneMatch(field -> Modifier.isStatic(field.getModifiers())
                                && (Map.class.isAssignableFrom(field.getType())
                                || Set.class.isAssignableFrom(field.getType()))));
        assertThat(List.of(DraftComputationContext.class.getDeclaredFields()))
                .filteredOn(field -> Map.class.isAssignableFrom(field.getType()))
                .allSatisfy(field -> assertThat(Modifier.isStatic(field.getModifiers()))
                        .isFalse());
    }

    private static ChampionId id(String value) {
        return DraftTestSupport.id(value);
    }
}

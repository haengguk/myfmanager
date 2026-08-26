package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.RealDraftSelectionContextFactory;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.LckTeamAssembler;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoDraftVarietyV1ProductionIntegrationTest {
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;

    private DraftEngine engine;

    @BeforeAll
    void createEngine() {
        engine = new DraftEngine(DraftResourceSet.loadDefault(mapper, champions));
    }

    @Test
    void seededProductionDraftCompletesLegallyAndReplaysAllSelectionEvidenceExactly() {
        FinalDraftResult first = draft("GEN", "T1", 73L, new SeriesDraftHistory());
        FinalDraftResult replay = draft("GEN", "T1", 73L, new SeriesDraftHistory());

        assertDraftExact(first, replay);
        assertThat(first.draftSelectionPolicyId())
                .isEqualTo(MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID);
        assertThat(first.draftSelectionPolicyHash())
                .isEqualTo(MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256);
        assertThat(first.selectionTraceHash()).matches("[0-9a-f]{64}");
        assertThat(first.selectionTraces()).hasSize(20);
        assertThat(first.blueBans()).hasSize(5);
        assertThat(first.redBans()).hasSize(5);
        assertThat(first.bluePicks()).hasSize(5);
        assertThat(first.redPicks()).hasSize(5);
        assertThat(new HashSet<>(allDraftChampions(first))).hasSize(20);
        assertThat(first.blueFinalRoleAssignments().values())
                .containsExactlyInAnyOrder(Position.values());
        assertThat(first.redFinalRoleAssignments().values())
                .containsExactlyInAnyOrder(Position.values());
        for (int index = 0; index < first.decisions().size(); index++) {
            DraftDecision decision = first.decisions().get(index);
            DraftSelectionTrace trace = first.selectionTraces().get(index);
            assertThat(trace.turn()).isEqualTo(decision.turn());
            assertThat(trace.side()).isEqualTo(decision.side());
            assertThat(trace.actionType()).isEqualTo(decision.actionType());
            assertThat(trace.selectedChampionId()).isEqualTo(decision.selectedChampionId());
            assertThat(trace.selectedRank()).isBetween(1, 3);
            assertThat(trace.selectedCanonicalScoreLoss()).isBetween(0L, 2_000_000L);
            assertThat(trace.eligiblePool()).extracting(DraftSelectionPoolEntry::championId)
                    .contains(decision.selectedChampionId());
        }
    }

    @Test
    void fixedSeedSetReachesMoreThanOneHighQualityDraftWithoutChangingSearchBounds() {
        Set<String> identities = new HashSet<>();
        for (long seed : AutoDraftVarietyV1Schedule.SEEDS) {
            FinalDraftResult result = draft("GEN", "T1", seed, new SeriesDraftHistory());
            identities.add(result.draftIdentity());
            assertThat(result.selectionTraces()).allSatisfy(trace -> {
                assertThat(trace.selectedRank()).isLessThanOrEqualTo(3);
                assertThat(trace.selectedCanonicalScoreLoss()).isLessThanOrEqualTo(2_000_000L);
            });
        }
        assertThat(identities).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void hardFearlessHistoryAndGameNumberCreateFreshSelectionIdentityAndCommitOnce() {
        SeriesDraftHistory history = new SeriesDraftHistory();
        FinalDraftResult gameOne = draft("GEN", "T1", 73L, history);
        history.commitCompleted(gameOne);
        history.commitCompleted(gameOne);
        Set<ChampionId> gameOnePicks = Set.copyOf(
                java.util.stream.Stream.concat(
                        gameOne.bluePicks().stream(), gameOne.redPicks().stream()).toList());

        FinalDraftResult gameTwo = draft("GEN", "T1", 73L, history);

        assertThat(history.committedGameCount()).isOne();
        assertThat(gameTwo.hardFearlessExclusions()).containsExactlyInAnyOrderElementsOf(
                gameOnePicks);
        assertThat(gameTwo.bluePicks()).doesNotContainAnyElementsOf(gameOnePicks);
        assertThat(gameTwo.redPicks()).doesNotContainAnyElementsOf(gameOnePicks);
        assertThat(gameTwo.selectionTraces().getFirst().selectionContextHash())
                .isNotEqualTo(gameOne.selectionTraces().getFirst().selectionContextHash());
        assertThatThrownBy(() -> engine.draft(
                DraftTeamContext.from(teams.assemble("GEN")),
                DraftTeamContext.from(teams.assemble("T1")), history,
                context("GEN", "T1", 73L, new SeriesDraftHistory())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("series game number mismatch");
        assertThat(history.committedGameCount()).isOne();
    }

    @Test
    void selectorOwnsNoRandomOrCrossMatchMutableStateAndConcurrentRunsAreIsolated()
            throws Exception {
        assertThat(List.of(AutoDraftSelector.class.getDeclaredFields()))
                .noneMatch(field -> Random.class.isAssignableFrom(field.getType())
                        || Modifier.isStatic(field.getModifiers())
                        && (Map.class.isAssignableFrom(field.getType())
                        || Set.class.isAssignableFrom(field.getType())));
        FinalDraftResult expectedGenT1 = draft("GEN", "T1", 73L,
                new SeriesDraftHistory());
        FinalDraftResult expectedHleKrx = draft("HLE", "KRX", -73L,
                new SeriesDraftHistory());
        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Callable<FinalDraftResult>> calls = List.of(
                    () -> draft("GEN", "T1", 73L, new SeriesDraftHistory()),
                    () -> draft("GEN", "T1", 73L, new SeriesDraftHistory()),
                    () -> draft("HLE", "KRX", -73L, new SeriesDraftHistory()));
            var results = executor.invokeAll(calls);
            assertDraftExact(expectedGenT1, results.get(0).get());
            assertDraftExact(expectedGenT1, results.get(1).get());
            assertDraftExact(expectedHleKrx, results.get(2).get());
        }
    }

    @Test
    void unseededBestOnlyBoundaryIsExplicitlyNamedAndProductionDraftRequiresContext() {
        assertThat(List.of(DraftEngine.class.getDeclaredMethods()))
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .filteredOn(method -> method.getName().equals("draft"))
                .allSatisfy(method -> assertThat(method.getParameterTypes())
                        .contains(DraftSelectionContext.class));
        assertThat(DraftEngine.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("draftDeterministicBest"));
    }

    private FinalDraftResult draft(String blueCode, String redCode, long seed,
                                   SeriesDraftHistory history) {
        Team blue = teams.assemble(blueCode);
        Team red = teams.assemble(redCode);
        return engine.draft(DraftTeamContext.from(blue), DraftTeamContext.from(red), history,
                RealDraftSelectionContextFactory.create(seed, blueCode, blue, redCode, red,
                        history.committedGameCount() + 1, history.consumedPicks()));
    }

    private DraftSelectionContext context(String blueCode, String redCode, long seed,
                                          SeriesDraftHistory history) {
        Team blue = teams.assemble(blueCode);
        Team red = teams.assemble(redCode);
        return RealDraftSelectionContextFactory.create(seed, blueCode, blue, redCode, red,
                history.committedGameCount() + 1, history.consumedPicks());
    }

    private static List<ChampionId> allDraftChampions(FinalDraftResult result) {
        return java.util.stream.Stream.of(result.blueBans(), result.redBans(),
                        result.bluePicks(), result.redPicks())
                .flatMap(List::stream).toList();
    }

    private static void assertDraftExact(FinalDraftResult expected, FinalDraftResult actual) {
        assertThat(actual.draftIdentity()).isEqualTo(expected.draftIdentity());
        assertThat(actual.decisions()).isEqualTo(expected.decisions());
        assertThat(actual.selectionTraces()).isEqualTo(expected.selectionTraces());
        assertThat(actual.selectionTraceHash()).isEqualTo(expected.selectionTraceHash());
        assertThat(actual.blueFinalRoleAssignments())
                .containsExactlyInAnyOrderEntriesOf(expected.blueFinalRoleAssignments());
        assertThat(actual.redFinalRoleAssignments())
                .containsExactlyInAnyOrderEntriesOf(expected.redFinalRoleAssignments());
        assertThat(actual.matchChampionAssignments().asMap())
                .containsExactlyInAnyOrderEntriesOf(expected.matchChampionAssignments().asMap());
    }
}

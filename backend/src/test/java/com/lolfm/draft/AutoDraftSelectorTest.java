package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoDraftSelectorTest {
    private static final String ROSTER_HASH = "a".repeat(64);
    private static final String HISTORY_HASH = "b".repeat(64);
    private final AutoDraftSelectionPolicy policy = AutoDraftSelectionPolicy.production();
    private final AutoDraftSelector selector = new AutoDraftSelector(policy);

    @Test
    void productionPolicyHasExactRequestedBoundsWeightsAndAlgorithms() {
        assertThat(policy.policyId()).isEqualTo("AUTO_DRAFT_VARIETY_V1");
        assertThat(policy.mode()).isEqualTo("SEEDED_BOUNDED_RANK_WEIGHTED_V1");
        assertThat(policy.maximumSelectableCandidates()).isEqualTo(3);
        assertThat(policy.maximumScoreLossFixed()).isEqualTo(2_000_000L);
        assertThat(policy.rankWeight(DraftActionType.BAN, 1)).isEqualTo(55);
        assertThat(policy.rankWeight(DraftActionType.BAN, 2)).isEqualTo(30);
        assertThat(policy.rankWeight(DraftActionType.BAN, 3)).isEqualTo(15);
        assertThat(policy.rankWeight(DraftActionType.PICK, 1)).isEqualTo(70);
        assertThat(policy.rankWeight(DraftActionType.PICK, 2)).isEqualTo(22);
        assertThat(policy.rankWeight(DraftActionType.PICK, 3)).isEqualTo(8);
        assertThat(policy.policyHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void exactTwoPointBoundaryIsIncludedAndOneMicroPointBeyondIsExcluded() {
        AutoDraftSelector.Selection selection = select(fresh(), context(73L), List.of(
                candidate("aatrox", 10.0), candidate("ahri", 8.0),
                candidate("akali", 7.999999)));

        assertThat(selection.trace().eligiblePool()).extracting(
                entry -> entry.championId().value()).containsExactly("aatrox", "ahri");
        assertThat(selection.trace().eligiblePool().get(1).canonicalScoreLoss())
                .isEqualTo(2_000_000L);
    }

    @Test
    void poolIsCappedAtThreeAndRankFourCanNeverBeSelected() {
        AutoDraftSelector.Selection selection = select(fresh(), context(91L), List.of(
                candidate("aatrox", 10.0), candidate("ahri", 9.9),
                candidate("akali", 9.8), candidate("alistar", 9.7)));

        assertThat(selection.trace().eligiblePool()).hasSize(3);
        assertThat(selection.trace().selectedRank()).isBetween(1, 3);
        assertThat(selection.trace().eligiblePool()).extracting(
                entry -> entry.championId().value()).doesNotContain("alistar");
    }

    @Test
    void singletonPoolSelectsExactBestWithoutAWeightedDraw() {
        AutoDraftSelector.Selection selection = select(fresh(), context(-1L), List.of(
                candidate("aatrox", 10.0), candidate("ahri", 7.0)));

        assertThat(selection.selectedCandidate().championId().value()).isEqualTo("aatrox");
        assertThat(selection.trace().selectedRank()).isOne();
        assertThat(selection.trace().drawBucket()).isNull();
        assertThat(selection.trace().reason())
                .isEqualTo(DraftSelectionReason.ONLY_ONE_WITHIN_WINDOW);
    }

    @Test
    void pickUsesPickWeightsAndSameCanonicalInputReplaysExactBucketAndTrace() {
        DraftState pickState = advance(fresh(), 6);
        List<DraftSearchCandidate> candidates = List.of(
                candidate("aatrox", 10.0), candidate("ahri", 9.5),
                candidate("akali", 9.0));

        AutoDraftSelector.Selection first = select(pickState, context(Long.MIN_VALUE), candidates);
        AutoDraftSelector.Selection replay = select(pickState, context(Long.MIN_VALUE), candidates);

        assertThat(first).isEqualTo(replay);
        assertThat(first.trace().eligiblePool()).extracting(
                DraftSelectionPoolEntry::rankWeight).containsExactly(70, 22, 8);
        assertThat(first.trace().totalEligibleWeight()).isEqualTo(100);
        assertThat(first.trace().drawBucket()).isBetween(0, 99);
    }

    @Test
    void seedTurnSideActionHistoryAndRosterAreDomainSeparated() {
        DraftState blueBan = fresh();
        DraftState redBan = advance(fresh(), 1);
        DraftState bluePick = advance(fresh(), 6);
        List<DraftSearchCandidate> candidates = List.of(
                candidate("aatrox", 10.0), candidate("ahri", 9.0));
        String base = select(blueBan, context(73L), candidates).trace().selectionContextHash();

        assertThat(Set.of(
                base,
                select(blueBan, context(74L), candidates).trace().selectionContextHash(),
                select(redBan, context(73L), candidates).trace().selectionContextHash(),
                select(bluePick, context(73L), candidates).trace().selectionContextHash(),
                select(blueBan, new DraftSelectionContext(73L, "BLUE", "RED",
                        "c".repeat(64), 1, HISTORY_HASH), candidates)
                        .trace().selectionContextHash(),
                select(blueBan, new DraftSelectionContext(73L, "BLUE", "RED",
                        ROSTER_HASH, 2, "d".repeat(64)), candidates)
                        .trace().selectionContextHash())).hasSize(6);
    }

    @Test
    void candidateAndFearlessInsertionOrderCannotChangeSelection() {
        Set<ChampionId> firstExclusions = new LinkedHashSet<>(List.of(
                id("zed"), id("zoe")));
        Set<ChampionId> reversedExclusions = new LinkedHashSet<>(List.of(
                id("zoe"), id("zed")));
        DraftState firstState = state(firstExclusions);
        DraftState reversedState = state(reversedExclusions);
        List<DraftSearchCandidate> firstCandidates = List.of(
                candidate("akali", 9.0), candidate("aatrox", 10.0), candidate("ahri", 9.5));
        List<DraftSearchCandidate> reversedCandidates = List.of(
                candidate("ahri", 9.5), candidate("aatrox", 10.0), candidate("akali", 9.0));

        assertThat(select(firstState, context(73L), firstCandidates))
                .isEqualTo(select(reversedState, context(73L), reversedCandidates));
    }

    @Test
    void fixedPointTieUsesStableChampionIdAndInvalidCandidateSetsAreRejected() {
        AutoDraftSelector.Selection tied = select(fresh(), context(73L), List.of(
                candidate("zed", 10.0000004), candidate("aatrox", 10.0000001)));
        assertThat(tied.trace().bestCandidateId().value()).isEqualTo("aatrox");
        assertThatThrownBy(() -> new DraftSearchCandidate(
                id("ahri"), 1.0, 0.0, Double.NaN, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> select(fresh(), context(73L), List.of(
                candidate("ahri", 10.0), candidate("ahri", 9.0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate Draft selection candidate");
    }

    @Test
    void alternativesExcludeActualSelectionForRankOneTwoAndThree() {
        List<DraftSearchCandidate> ranked = List.of(
                candidate("aatrox", 10.0), candidate("ahri", 9.5),
                candidate("akali", 9.0), candidate("alistar", 8.5));
        ShallowDraftSearch.SearchResult result =
                new ShallowDraftSearch.SearchResult(ranked, null);

        for (int selectedIndex = 0; selectedIndex < 3; selectedIndex++) {
            ChampionId selected = ranked.get(selectedIndex).championId();
            ShallowDraftSearch.SearchChoice choice =
                    ShallowDraftSearch.SearchChoice.fromSelection(
                            result, ranked.get(selectedIndex));
            assertThat(choice.alternatives()).extracting(DraftAlternative::championId)
                    .doesNotContain(selected)
                    .containsExactlyElementsOf(ranked.stream()
                            .map(DraftSearchCandidate::championId)
                            .filter(value -> !value.equals(selected)).limit(3).toList());
        }
    }

    @Test
    void traceHashV2IgnoresRawDoubleButBindsFixedPointEvidence() {
        DraftSelectionTrace first = select(fresh(), context(73L), List.of(
                candidate("aatrox", 10.0), candidate("ahri", 9.5))).trace();
        List<DraftSelectionPoolEntry> rawChanged = first.eligiblePool().stream()
                .map(value -> new DraftSelectionPoolEntry(value.championId(),
                        value.canonicalRank(), value.rawFinalSearchScore() + 1e-12,
                        value.canonicalFinalScore(), value.canonicalScoreLoss(),
                        value.rankWeight())).toList();
        DraftSelectionTrace observationallyDifferent = copy(first, rawChanged,
                first.selectionContextHash(), first.drawBucket(), first.totalEligibleWeight());
        DraftSelectionPoolEntry changedFixed = new DraftSelectionPoolEntry(
                rawChanged.getFirst().championId(), 1, rawChanged.getFirst().rawFinalSearchScore(),
                rawChanged.getFirst().canonicalFinalScore() + 1,
                rawChanged.getFirst().canonicalScoreLoss(), rawChanged.getFirst().rankWeight());
        List<DraftSelectionPoolEntry> fixedChanged = new java.util.ArrayList<>(rawChanged);
        fixedChanged.set(0, changedFixed);
        DraftSelectionTrace structurallyDifferent = new DraftSelectionTrace(
                first.policyId(), first.policyMode(), first.policyHash(),
                first.selectionContextHash(), first.turn(), first.side(), first.actionType(),
                first.bestCandidateId(), first.bestCanonicalScore() + 1, fixedChanged,
                first.selectedChampionId(), first.selectedRank(),
                first.selectedCanonicalScoreLoss(), first.drawBucket(),
                first.totalEligibleWeight(), first.reason());

        assertThat(DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM).endsWith("_V2");
        assertThat(DraftSelectionTraceHasher.traceHash(observationallyDifferent))
                .isEqualTo(DraftSelectionTraceHasher.traceHash(first));
        assertThat(DraftSelectionTraceHasher.traceHash(structurallyDifferent))
                .isNotEqualTo(DraftSelectionTraceHasher.traceHash(first));
    }

    private static DraftSelectionTrace copy(
            DraftSelectionTrace source, List<DraftSelectionPoolEntry> pool,
            String contextHash, Integer drawBucket, int totalWeight) {
        return new DraftSelectionTrace(source.policyId(), source.policyMode(), source.policyHash(),
                contextHash, source.turn(), source.side(), source.actionType(),
                source.bestCandidateId(), source.bestCanonicalScore(), pool,
                source.selectedChampionId(), source.selectedRank(),
                source.selectedCanonicalScoreLoss(), drawBucket, totalWeight, source.reason());
    }

    private AutoDraftSelector.Selection select(DraftState state,
                                               DraftSelectionContext context,
                                               List<DraftSearchCandidate> candidates) {
        return selector.select(state, new ShallowDraftSearch.SearchResult(candidates, null), context);
    }

    private static DraftSearchCandidate candidate(String championId, double finalScore) {
        return new DraftSearchCandidate(id(championId), finalScore, 0.0, finalScore,
                Map.of("TEST", finalScore));
    }

    private static DraftSelectionContext context(long seed) {
        return new DraftSelectionContext(
                seed, "BLUE", "RED", ROSTER_HASH, 1, HISTORY_HASH);
    }

    private static DraftState fresh() {
        return state(Set.of());
    }

    private static DraftState state(Set<ChampionId> exclusions) {
        return new DraftState(DraftRuleSet.professional(), 0, List.of(), List.of(),
                List.of(), List.of(), exclusions);
    }

    private static DraftState advance(DraftState state, int turns) {
        DraftState value = state;
        for (int index = 0; index < turns; index++) {
            DraftTurn turn = value.currentTurn();
            value = value.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(),
                    id("test-champion-" + (index + 1))));
        }
        return value;
    }

    private static ChampionId id(String value) {
        return new ChampionId(value);
    }
}

package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class DraftSelectionEvidenceValidatorTest {
    private static final String ROSTER = "a".repeat(64);
    private static final String HISTORY = "b".repeat(64);
    private final AutoDraftSelectionPolicy policy = AutoDraftSelectionPolicy.production();
    private final DraftSelectionEvidenceValidator validator =
            new DraftSelectionEvidenceValidator(policy);

    @Test
    void validatesCompleteAuthoritativeEvidenceAndReconstructedDraft() {
        Evidence evidence = evidence(73L, Set.of());
        var result = validator.validate(DraftRuleSet.professional(), Set.of(),
                evidence.context(), evidence.decisions(), evidence.traces(), evidence.traceHash());

        assertThat(result.blueBans()).hasSize(5);
        assertThat(result.redBans()).hasSize(5);
        assertThat(result.bluePicks()).hasSize(5);
        assertThat(result.redPicks()).hasSize(5);
    }

    @Test
    void rejectsWeightSumBucketRankPoolScorePolicyAndTraceRelabeling() {
        Evidence value = evidence(73L, Set.of());
        assertTraceMutationRejected(value, 0, trace -> {
            List<DraftSelectionPoolEntry> pool = new ArrayList<>(trace.eligiblePool());
            DraftSelectionPoolEntry first = pool.getFirst();
            pool.set(0, entry(first, first.canonicalRank(), first.championId(),
                    first.canonicalFinalScore(), first.canonicalScoreLoss(),
                    first.rankWeight() + 1));
            return copy(trace, pool, trace.selectionContextHash(), trace.selectedRank(),
                    trace.drawBucket(), trace.totalEligibleWeight(), trace.policyId(),
                    trace.policyMode(), trace.policyHash());
        });
        assertTraceMutationRejected(value, 0, trace -> copy(trace, trace.eligiblePool(),
                trace.selectionContextHash(), trace.selectedRank(), trace.drawBucket(),
                trace.totalEligibleWeight() + 1, trace.policyId(), trace.policyMode(),
                trace.policyHash()));
        int weighted = firstWeighted(value.traces());
        assertTraceMutationRejected(value, weighted, trace -> copy(trace, trace.eligiblePool(),
                trace.selectionContextHash(), trace.selectedRank(),
                (trace.drawBucket() + 1) % trace.totalEligibleWeight(),
                trace.totalEligibleWeight(), trace.policyId(), trace.policyMode(),
                trace.policyHash()));
        assertTraceMutationRejected(value, weighted, trace -> copy(trace, trace.eligiblePool(),
                trace.selectionContextHash(), trace.selectedRank() == 1 ? 2 : 1,
                trace.drawBucket(), trace.totalEligibleWeight(), trace.policyId(),
                trace.policyMode(), trace.policyHash()));
        assertTraceMutationRejected(value, 0, trace -> {
            List<DraftSelectionPoolEntry> pool = new ArrayList<>(trace.eligiblePool());
            DraftSelectionPoolEntry first = pool.getFirst();
            pool.set(0, entry(first, 1, id("tampered-champion"),
                    first.canonicalFinalScore(), first.canonicalScoreLoss(), first.rankWeight()));
            return new DraftSelectionTrace(trace.policyId(), trace.policyMode(), trace.policyHash(),
                    trace.selectionContextHash(), trace.turn(), trace.side(), trace.actionType(),
                    id("tampered-champion"), trace.bestCanonicalScore(), pool,
                    trace.selectedRank() == 1 ? id("tampered-champion") : trace.selectedChampionId(),
                    trace.selectedRank(), trace.selectedCanonicalScoreLoss(), trace.drawBucket(),
                    trace.totalEligibleWeight(), trace.reason());
        });
        assertTraceMutationRejected(value, 0, trace -> {
            List<DraftSelectionPoolEntry> pool = new ArrayList<>(trace.eligiblePool());
            DraftSelectionPoolEntry first = pool.getFirst();
            pool.set(0, entry(first, 1, first.championId(),
                    first.canonicalFinalScore() + 1, first.canonicalScoreLoss(), first.rankWeight()));
            return new DraftSelectionTrace(trace.policyId(), trace.policyMode(), trace.policyHash(),
                    trace.selectionContextHash(), trace.turn(), trace.side(), trace.actionType(),
                    trace.bestCandidateId(), trace.bestCanonicalScore() + 1, pool,
                    trace.selectedChampionId(), trace.selectedRank(),
                    trace.selectedCanonicalScoreLoss(), trace.drawBucket(),
                    trace.totalEligibleWeight(), trace.reason());
        });
        assertTraceMutationRejected(value, 0, trace -> copy(trace, trace.eligiblePool(),
                trace.selectionContextHash(), trace.selectedRank(), trace.drawBucket(),
                trace.totalEligibleWeight(), "RELABELLED_POLICY", trace.policyMode(),
                trace.policyHash()));
        List<DraftSelectionTrace> reordered = new ArrayList<>(value.traces());
        java.util.Collections.swap(reordered, 0, 1);
        assertThatThrownBy(() -> validator.validate(DraftRuleSet.professional(), Set.of(),
                value.context(), value.decisions(), reordered,
                DraftSelectionTraceHasher.hash(reordered)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSeedTeamRosterGameHistoryPriorStateAndExclusionMutation() {
        Evidence value = evidence(73L, Set.of());
        List<DraftSelectionContext> contexts = List.of(
                new DraftSelectionContext(74L, "BLUE", "RED", ROSTER, 1, HISTORY),
                new DraftSelectionContext(73L, "OTHER_BLUE", "RED", ROSTER, 1, HISTORY),
                new DraftSelectionContext(73L, "BLUE", "OTHER_RED", ROSTER, 1, HISTORY),
                new DraftSelectionContext(73L, "BLUE", "RED", "c".repeat(64), 1, HISTORY),
                new DraftSelectionContext(73L, "BLUE", "RED", ROSTER, 2, HISTORY),
                new DraftSelectionContext(73L, "BLUE", "RED", ROSTER, 1, "d".repeat(64)));
        contexts.forEach(context -> assertThatThrownBy(() -> validator.validate(
                DraftRuleSet.professional(), Set.of(), context, value.decisions(),
                value.traces(), value.traceHash())).isInstanceOf(IllegalArgumentException.class));

        List<DraftAction> decisions = new ArrayList<>(value.decisions());
        DraftAction second = decisions.get(1);
        decisions.set(1, new DraftAction(second.turn(), second.side(), second.actionType(),
                id("prior-state-tamper")));
        assertThatThrownBy(() -> validator.validate(DraftRuleSet.professional(), Set.of(),
                value.context(), decisions, value.traces(), value.traceHash()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(DraftRuleSet.professional(),
                Set.of(id("fearless-tamper")), value.context(), value.decisions(),
                value.traces(), value.traceHash())).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertTraceMutationRejected(Evidence evidence, int index,
                                              UnaryOperator<DraftSelectionTrace> mutation) {
        List<DraftSelectionTrace> traces = new ArrayList<>(evidence.traces());
        traces.set(index, mutation.apply(traces.get(index)));
        assertThatThrownBy(() -> validator.validate(DraftRuleSet.professional(), Set.of(),
                evidence.context(), evidence.decisions(), traces,
                DraftSelectionTraceHasher.hash(traces)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Evidence evidence(long seed, Set<ChampionId> exclusions) {
        DraftSelectionContext context = new DraftSelectionContext(
                seed, "BLUE", "RED", ROSTER, 1, HISTORY);
        DraftState state = new DraftState(DraftRuleSet.professional(), 0,
                List.of(), List.of(), List.of(), List.of(), exclusions);
        AutoDraftSelector selector = new AutoDraftSelector(policy);
        List<DraftAction> decisions = new ArrayList<>();
        List<DraftSelectionTrace> traces = new ArrayList<>();
        for (int turnIndex = 0; turnIndex < 20; turnIndex++) {
            List<DraftSearchCandidate> candidates = List.of(
                    candidate("candidate-" + turnIndex + "-a", 10.0),
                    candidate("candidate-" + turnIndex + "-b", 9.5),
                    candidate("candidate-" + turnIndex + "-c", 9.0));
            AutoDraftSelector.Selection selection = selector.select(state,
                    new ShallowDraftSearch.SearchResult(candidates, null), context);
            DraftTurn turn = state.currentTurn();
            DraftAction action = new DraftAction(turn.number(), turn.side(), turn.actionType(),
                    selection.selectedCandidate().championId());
            decisions.add(action);
            traces.add(selection.trace());
            state = state.apply(action);
        }
        return new Evidence(context, List.copyOf(decisions), List.copyOf(traces),
                DraftSelectionTraceHasher.hash(traces));
    }

    private static int firstWeighted(List<DraftSelectionTrace> traces) {
        for (int index = 0; index < traces.size(); index++) {
            if (traces.get(index).drawBucket() != null) return index;
        }
        throw new AssertionError("Expected weighted trace");
    }

    private static DraftSelectionPoolEntry entry(DraftSelectionPoolEntry source, int rank,
                                                  ChampionId id, long score, long loss, int weight) {
        return new DraftSelectionPoolEntry(id, rank, source.rawFinalSearchScore(), score, loss, weight);
    }

    private static DraftSelectionTrace copy(
            DraftSelectionTrace source, List<DraftSelectionPoolEntry> pool, String contextHash,
            int selectedRank, Integer bucket, int totalWeight,
            String policyId, String policyMode, String policyHash) {
        DraftSelectionPoolEntry selected = pool.get(selectedRank - 1);
        return new DraftSelectionTrace(policyId, policyMode, policyHash, contextHash,
                source.turn(), source.side(), source.actionType(), pool.getFirst().championId(),
                pool.getFirst().canonicalFinalScore(), pool, selected.championId(), selectedRank,
                selected.canonicalScoreLoss(), bucket, totalWeight, source.reason());
    }

    private static DraftSearchCandidate candidate(String value, double score) {
        return new DraftSearchCandidate(id(value), score, 0.0, score, Map.of("TEST", score));
    }

    private static ChampionId id(String value) {
        return new ChampionId(value);
    }

    private record Evidence(DraftSelectionContext context, List<DraftAction> decisions,
                            List<DraftSelectionTrace> traces, String traceHash) {
    }
}

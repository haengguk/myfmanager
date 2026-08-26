package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Stateless authoritative validator for structured Auto Draft selection evidence. */
public final class DraftSelectionEvidenceValidator {
    private final AutoDraftSelectionPolicy policy;
    private final AutoDraftSelector selector;

    public DraftSelectionEvidenceValidator(AutoDraftSelectionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        selector = new AutoDraftSelector(policy);
    }

    public ValidatedDraft validate(
            DraftRuleSet rules,
            Set<ChampionId> hardFearlessExclusions,
            DraftSelectionContext context,
            List<DraftAction> decisions,
            List<DraftSelectionTrace> traces,
            String traceSetHash
    ) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(hardFearlessExclusions, "hardFearlessExclusions");
        Objects.requireNonNull(context, "context");
        decisions = List.copyOf(decisions);
        traces = List.copyOf(traces);
        if (decisions.size() != rules.turns().size() || traces.size() != decisions.size()) {
            throw invalid("DRAFT_SELECTION_TRACE_CARDINALITY");
        }
        if (!DraftSelectionTraceHasher.hash(traces).equals(traceSetHash)) {
            throw invalid("DRAFT_SELECTION_TRACE_SET_HASH");
        }

        DraftState state = new DraftState(rules, 0, List.of(), List.of(), List.of(),
                List.of(), Set.copyOf(hardFearlessExclusions));
        for (int index = 0; index < decisions.size(); index++) {
            DraftAction decision = decisions.get(index);
            DraftSelectionTrace trace = traces.get(index);
            validateTrace(state, context, decision, trace);
            state = state.apply(decision);
        }
        if (!state.complete()) throw invalid("DRAFT_SELECTION_FINAL_STATE_INCOMPLETE");
        return new ValidatedDraft(state.blueBans(), state.redBans(),
                state.bluePicks(), state.redPicks());
    }

    private void validateTrace(DraftState state, DraftSelectionContext context,
                               DraftAction decision, DraftSelectionTrace trace) {
        DraftTurn turn = state.currentTurn();
        if (!policy.policyId().equals(trace.policyId())
                || !policy.mode().equals(trace.policyMode())
                || !policy.policyHash().equals(trace.policyHash())) {
            throw invalid("DRAFT_SELECTION_POLICY_IDENTITY");
        }
        if (decision.turn() != turn.number() || decision.side() != turn.side()
                || decision.actionType() != turn.actionType()
                || trace.turn() != turn.number() || trace.side() != turn.side()
                || trace.actionType() != turn.actionType()
                || !trace.selectedChampionId().equals(decision.championId())) {
            throw invalid("DRAFT_SELECTION_DECISION_BINDING");
        }

        List<DraftSelectionPoolEntry> pool = trace.eligiblePool();
        if (pool.isEmpty() || pool.size() > policy.maximumSelectableCandidates()) {
            throw invalid("DRAFT_SELECTION_POOL_CARDINALITY");
        }
        Set<ChampionId> champions = new HashSet<>();
        long best = pool.getFirst().canonicalFinalScore();
        int totalWeight = 0;
        for (int index = 0; index < pool.size(); index++) {
            DraftSelectionPoolEntry entry = pool.get(index);
            int rank = index + 1;
            if (entry.canonicalRank() != rank || !champions.add(entry.championId())) {
                throw invalid("DRAFT_SELECTION_POOL_ORDER_OR_IDENTITY");
            }
            long expectedLoss;
            try {
                expectedLoss = Math.subtractExact(best, entry.canonicalFinalScore());
                totalWeight = Math.addExact(totalWeight,
                        policy.rankWeight(turn.actionType(), rank));
            } catch (ArithmeticException error) {
                throw invalid("DRAFT_SELECTION_EVIDENCE_ARITHMETIC", error);
            }
            if (expectedLoss < 0 || expectedLoss != entry.canonicalScoreLoss()
                    || expectedLoss > policy.maximumScoreLossFixed()
                    || entry.rankWeight() != policy.rankWeight(turn.actionType(), rank)) {
                throw invalid("DRAFT_SELECTION_SCORE_OR_WEIGHT");
            }
        }
        if (!trace.bestCandidateId().equals(pool.getFirst().championId())
                || trace.bestCanonicalScore() != best
                || trace.totalEligibleWeight() != totalWeight
                || trace.selectedRank() < 1 || trace.selectedRank() > pool.size()) {
            throw invalid("DRAFT_SELECTION_TRACE_AGGREGATE");
        }
        DraftSelectionPoolEntry selected = pool.get(trace.selectedRank() - 1);
        if (!selected.championId().equals(trace.selectedChampionId())
                || selected.canonicalScoreLoss() != trace.selectedCanonicalScoreLoss()) {
            throw invalid("DRAFT_SELECTION_SELECTED_ENTRY");
        }

        if (pool.size() == 1) {
            if (trace.reason() != DraftSelectionReason.ONLY_ONE_WITHIN_WINDOW
                    || trace.drawBucket() != null || trace.selectedRank() != 1) {
                throw invalid("DRAFT_SELECTION_SINGLETON_SEMANTICS");
            }
        } else {
            int expectedBucket = new BigInteger(trace.selectionContextHash(), 16)
                    .mod(BigInteger.valueOf(totalWeight)).intValueExact();
            if (trace.reason() != DraftSelectionReason.SEEDED_WEIGHTED_SELECTION
                    || trace.drawBucket() == null || trace.drawBucket() < 0
                    || trace.drawBucket() >= totalWeight
                    || trace.drawBucket() != expectedBucket
                    || mappedRank(pool, trace.drawBucket()) != trace.selectedRank()) {
                throw invalid("DRAFT_SELECTION_WEIGHTED_BUCKET");
            }
        }
        if (!selector.selectionContextHash(state, context, pool)
                .equals(trace.selectionContextHash())) {
            throw invalid("DRAFT_SELECTION_CONTEXT_HASH");
        }
    }

    private static int mappedRank(List<DraftSelectionPoolEntry> pool, int bucket) {
        int cumulative = 0;
        for (DraftSelectionPoolEntry entry : pool) {
            cumulative += entry.rankWeight();
            if (bucket < cumulative) return entry.canonicalRank();
        }
        throw invalid("DRAFT_SELECTION_BUCKET_UNMAPPED");
    }

    private static IllegalArgumentException invalid(String code) {
        return new IllegalArgumentException(code);
    }

    private static IllegalArgumentException invalid(String code, Throwable cause) {
        return new IllegalArgumentException(code, cause);
    }

    public record ValidatedDraft(
            List<ChampionId> blueBans,
            List<ChampionId> redBans,
            List<ChampionId> bluePicks,
            List<ChampionId> redPicks
    ) {
        public ValidatedDraft {
            blueBans = List.copyOf(blueBans);
            redBans = List.copyOf(redBans);
            bluePicks = List.copyOf(bluePicks);
            redPicks = List.copyOf(redPicks);
        }
    }
}

package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Stateless bounded selector. It consumes evaluated candidates without invoking Draft scoring. */
public final class AutoDraftSelector {
    private final AutoDraftSelectionPolicy policy;

    public AutoDraftSelector(AutoDraftSelectionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Selection select(DraftState state,
                            ShallowDraftSearch.SearchResult searchResult,
                            DraftSelectionContext context) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(searchResult, "searchResult");
        Objects.requireNonNull(context, "context");
        DraftTurn turn = state.currentTurn();
        List<CanonicalCandidate> ranked = canonicalRank(searchResult.rankedCandidates());
        long bestScore = ranked.getFirst().canonicalScore();
        ArrayList<DraftSelectionPoolEntry> pool = new ArrayList<>();
        for (int index = 0; index < ranked.size()
                && pool.size() < policy.maximumSelectableCandidates(); index++) {
            CanonicalCandidate candidate = ranked.get(index);
            long loss;
            try {
                loss = Math.subtractExact(bestScore, candidate.canonicalScore());
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Draft candidate score-loss overflow", error);
            }
            if (loss > policy.maximumScoreLossFixed()) break;
            int rank = index + 1;
            pool.add(new DraftSelectionPoolEntry(candidate.candidate().championId(), rank,
                    candidate.candidate().finalSearchScore(), candidate.canonicalScore(), loss,
                    policy.rankWeight(turn.actionType(), rank)));
        }
        if (pool.isEmpty()) throw new IllegalStateException("Canonical Draft pool is empty");

        byte[] contextDigest = selectionContextDigest(state, context, pool);
        String contextHash = HexFormat.of().formatHex(contextDigest);
        int totalWeight = pool.stream().mapToInt(DraftSelectionPoolEntry::rankWeight).sum();
        DraftSelectionPoolEntry selected;
        Integer bucket;
        DraftSelectionReason reason;
        if (pool.size() == 1) {
            selected = pool.getFirst();
            bucket = null;
            reason = DraftSelectionReason.ONLY_ONE_WITHIN_WINDOW;
        } else {
            bucket = new BigInteger(1, contextDigest)
                    .mod(BigInteger.valueOf(totalWeight)).intValueExact();
            int cumulative = 0;
            selected = null;
            for (DraftSelectionPoolEntry entry : pool) {
                cumulative += entry.rankWeight();
                if (bucket < cumulative) {
                    selected = entry;
                    break;
                }
            }
            if (selected == null) throw new IllegalStateException("Draft weighted bucket unmapped");
            reason = DraftSelectionReason.SEEDED_WEIGHTED_SELECTION;
        }

        DraftSearchCandidate selectedCandidate = ranked.get(
                selected.canonicalRank() - 1).candidate();
        DraftSelectionTrace trace = new DraftSelectionTrace(
                policy.policyId(), policy.mode(), policy.policyHash(), contextHash,
                turn.number(), turn.side(), turn.actionType(), pool.getFirst().championId(),
                pool.getFirst().canonicalFinalScore(), pool, selected.championId(),
                selected.canonicalRank(), selected.canonicalScoreLoss(), bucket,
                totalWeight, reason);
        return new Selection(selectedCandidate, trace);
    }

    static long canonicalScore(double rawScore) {
        if (!Double.isFinite(rawScore)) {
            throw new IllegalArgumentException("Draft candidate score must be finite");
        }
        try {
            return BigDecimal.valueOf(rawScore).movePointRight(6)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Draft candidate score is outside fixed-point range", error);
        }
    }

    private static List<CanonicalCandidate> canonicalRank(List<DraftSearchCandidate> candidates) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("At least one candidate is required");
        Set<ChampionId> identities = new HashSet<>();
        ArrayList<CanonicalCandidate> values = new ArrayList<>(candidates.size());
        for (DraftSearchCandidate candidate : candidates) {
            if (!identities.add(candidate.championId())) {
                throw new IllegalArgumentException("Duplicate Draft selection candidate: "
                        + candidate.championId());
            }
            values.add(new CanonicalCandidate(candidate,
                    canonicalScore(candidate.finalSearchScore())));
        }
        values.sort(Comparator.comparingLong(CanonicalCandidate::canonicalScore).reversed()
                .thenComparing(value -> value.candidate().championId().value()));
        return List.copyOf(values);
    }

    private byte[] selectionContextDigest(DraftState state,
                                          DraftSelectionContext context,
                                          List<DraftSelectionPoolEntry> pool) {
        DraftTurn turn = state.currentTurn();
        StringBuilder canonical = new StringBuilder("schema=")
                .append(AutoDraftSelectionPolicy.POLICY_ID).append('\n')
                .append("matchSeed=").append(context.matchSeed()).append('\n')
                .append("blueTeamIdentity=").append(context.blueTeamIdentity()).append('\n')
                .append("redTeamIdentity=").append(context.redTeamIdentity()).append('\n')
                .append("rosterIdentityHash=").append(context.rosterIdentityHash()).append('\n')
                .append("seriesGameNumber=").append(context.seriesGameNumber()).append('\n')
                .append("seriesHistoryBeforeHash=")
                .append(context.seriesHistoryBeforeHash()).append('\n')
                .append("turn=").append(turn.number()).append('\n')
                .append("side=").append(turn.side().name()).append('\n')
                .append("actionType=").append(turn.actionType().name()).append('\n');
        appendChampions(canonical, "blueBan", state.blueBans());
        appendChampions(canonical, "redBan", state.redBans());
        appendChampions(canonical, "bluePick", state.bluePicks());
        appendChampions(canonical, "redPick", state.redPicks());
        state.fearlessExclusions().stream().map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("hardFearlessExclusion=")
                        .append(value).append('\n'));
        canonical.append("selectionPolicyHash=").append(policy.policyHash()).append('\n');
        pool.forEach(entry -> canonical.append("eligibleCandidate=")
                .append(entry.canonicalRank()).append('|').append(entry.championId().value())
                .append('\n'));
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void appendChampions(StringBuilder canonical, String field,
                                        List<ChampionId> champions) {
        for (int index = 0; index < champions.size(); index++) {
            canonical.append(field).append('=').append(index).append('|')
                    .append(champions.get(index).value()).append('\n');
        }
    }

    public record Selection(DraftSearchCandidate selectedCandidate,
                            DraftSelectionTrace trace) {
        public Selection {
            Objects.requireNonNull(selectedCandidate, "selectedCandidate");
            Objects.requireNonNull(trace, "trace");
            if (!selectedCandidate.championId().equals(trace.selectedChampionId())) {
                throw new IllegalArgumentException("Selected candidate and trace differ");
            }
        }
    }

    private record CanonicalCandidate(DraftSearchCandidate candidate, long canonicalScore) {
    }
}

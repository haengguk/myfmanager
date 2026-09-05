package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/** Mutable state deliberately scoped to one series instance. */
public final class SeriesDraftHistory {
    private final LinkedHashSet<ChampionId> consumedPicks = new LinkedHashSet<>();
    private final Set<String> committedDrafts = new HashSet<>();

    public SeriesDraftHistory() {}

    /** Frozen exclusions from a parent competition; this child owns fresh commits. */
    public SeriesDraftHistory(Set<ChampionId> inheritedExclusions) {
        consumedPicks.addAll(Set.copyOf(inheritedExclusions));
    }

    public Set<ChampionId> consumedPicks() { return Set.copyOf(consumedPicks); }
    public int committedGameCount() { return committedDrafts.size(); }
    public String identityHash() {
        return identityHash(committedGameCount(), consumedPicks());
    }

    /** Canonical immutable identity shared by Series and League-bound execution. */
    public static String identityHash(int committedGames, Set<ChampionId> exclusions) {
        if (committedGames < 0) {
            throw new IllegalArgumentException("committedGames must not be negative");
        }
        StringBuilder canonical = new StringBuilder(
                "seriesHistorySchema=HARD_FEARLESS_HISTORY_V1\n")
                .append("committedGameCount=").append(committedGames).append('\n');
        java.util.Objects.requireNonNull(exclusions, "exclusions").stream()
                .map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("consumedPick=")
                        .append(value).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public void commitCompleted(FinalDraftResult result) {
        if (result.decisions().size() != result.ruleSet().turns().size()) throw new IllegalArgumentException("Only completed drafts may be committed");
        String identity = result.draftIdentity();
        if (!committedDrafts.add(identity)) return;
        consumedPicks.addAll(result.bluePicks());
        consumedPicks.addAll(result.redPicks());
    }
}

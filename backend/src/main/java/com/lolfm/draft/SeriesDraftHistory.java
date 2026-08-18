package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Mutable state deliberately scoped to one series instance. */
public final class SeriesDraftHistory {
    private final LinkedHashSet<ChampionId> consumedPicks = new LinkedHashSet<>();
    private final Set<String> committedDrafts = new HashSet<>();

    public Set<ChampionId> consumedPicks() { return Set.copyOf(consumedPicks); }
    public int committedGameCount() { return committedDrafts.size(); }

    public void commitCompleted(FinalDraftResult result) {
        if (result.decisions().size() != result.ruleSet().turns().size()) throw new IllegalArgumentException("Only completed drafts may be committed");
        String identity = result.draftIdentity();
        if (!committedDrafts.add(identity)) return;
        consumedPicks.addAll(result.bluePicks());
        consumedPicks.addAll(result.redPicks());
    }
}

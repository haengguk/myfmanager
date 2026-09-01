package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.draft.FinalDraftResult;
import java.util.Objects;
import java.util.Set;

/**
 * Opaque server-created Production Auto Draft/V9 result before Series history commit.
 * This lets a caller verify decisiveness and integrity before changing its fixture state.
 */
public final class PreparedAutoDraftMatch {
    private final MatchEngineV1Input input;
    private final MatchEngineV1Output output;
    private final FinalDraftResult completedDraft;
    private final int gameNumber;
    private final Set<ChampionId> historyBefore;

    PreparedAutoDraftMatch(
            MatchEngineV1Input input,
            MatchEngineV1Output output,
            FinalDraftResult completedDraft,
            int gameNumber,
            Set<ChampionId> historyBefore
    ) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.completedDraft = Objects.requireNonNull(completedDraft, "completedDraft");
        if (gameNumber < 1) throw new IllegalArgumentException("gameNumber");
        this.gameNumber = gameNumber;
        this.historyBefore = Set.copyOf(historyBefore);
    }

    public MatchEngineV1Input input() { return input; }
    public MatchEngineV1Output output() { return output; }
    public FinalDraftResult completedDraft() { return completedDraft; }
    public int gameNumber() { return gameNumber; }
    public Set<ChampionId> historyBefore() { return historyBefore; }
}

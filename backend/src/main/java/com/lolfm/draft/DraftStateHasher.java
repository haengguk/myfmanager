package com.lolfm.draft;

import com.lolfm.champion.ChampionId;

/** Canonical display-independent identity for a partial Draft state. */
public final class DraftStateHasher {
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_ORDERED_DRAFT_STATE_LINES_V1";

    private DraftStateHasher() {
    }

    public static String hash(DraftState state) {
        StringBuilder canonical = new StringBuilder("draftStateSchema=DRAFT_STATE_V1\n")
                .append("ruleSetIdentity=").append(state.ruleSet().identity()).append('\n')
                .append("nextTurnIndex=").append(state.nextTurnIndex()).append('\n');
        append(canonical, "bluePick", state.bluePicks());
        append(canonical, "redPick", state.redPicks());
        append(canonical, "blueBan", state.blueBans());
        append(canonical, "redBan", state.redBans());
        state.fearlessExclusions().stream().map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("fearlessExclusion=")
                        .append(value).append('\n'));
        return PlayerDraftControlPolicy.hash(canonical.toString());
    }

    private static void append(
            StringBuilder canonical, String field, java.util.List<ChampionId> champions
    ) {
        for (int index = 0; index < champions.size(); index++) {
            canonical.append(field).append('=').append(index).append('|')
                    .append(champions.get(index).value()).append('\n');
        }
    }
}

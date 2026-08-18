package com.lolfm.draft;

import com.lolfm.simulator.TeamSide;
import java.util.List;

public record DraftRuleSet(String identity, List<DraftTurn> turns) {
    public static final String PROFESSIONAL_HARD_FEARLESS_5X5 = "PROFESSIONAL_5_BAN_5_PICK_HARD_FEARLESS_V1";
    public DraftRuleSet {
        if (identity == null || identity.isBlank()) throw new IllegalArgumentException("identity is required");
        turns = List.copyOf(turns);
        for (int i = 0; i < turns.size(); i++) if (turns.get(i).number() != i + 1) throw new IllegalArgumentException("Non-contiguous DraftTurn sequence");
    }
    public static DraftRuleSet professional() {
        return new DraftRuleSet(PROFESSIONAL_HARD_FEARLESS_5X5, List.of(
                t(1, TeamSide.BLUE, DraftActionType.BAN), t(2, TeamSide.RED, DraftActionType.BAN),
                t(3, TeamSide.BLUE, DraftActionType.BAN), t(4, TeamSide.RED, DraftActionType.BAN),
                t(5, TeamSide.BLUE, DraftActionType.BAN), t(6, TeamSide.RED, DraftActionType.BAN),
                t(7, TeamSide.BLUE, DraftActionType.PICK), t(8, TeamSide.RED, DraftActionType.PICK),
                t(9, TeamSide.RED, DraftActionType.PICK), t(10, TeamSide.BLUE, DraftActionType.PICK),
                t(11, TeamSide.BLUE, DraftActionType.PICK), t(12, TeamSide.RED, DraftActionType.PICK),
                t(13, TeamSide.RED, DraftActionType.BAN), t(14, TeamSide.BLUE, DraftActionType.BAN),
                t(15, TeamSide.RED, DraftActionType.BAN), t(16, TeamSide.BLUE, DraftActionType.BAN),
                t(17, TeamSide.RED, DraftActionType.PICK), t(18, TeamSide.BLUE, DraftActionType.PICK),
                t(19, TeamSide.BLUE, DraftActionType.PICK), t(20, TeamSide.RED, DraftActionType.PICK)));
    }
    private static DraftTurn t(int n, TeamSide side, DraftActionType type) { return new DraftTurn(n, side, type); }
}

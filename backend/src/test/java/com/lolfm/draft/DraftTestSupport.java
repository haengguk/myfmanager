package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class DraftTestSupport {
    static final DraftResourceSet RESOURCES = DraftResourceSet.loadDefault();
    static final DraftTeamContext NEUTRAL = new DraftTeamContext(Map.of());
    private DraftTestSupport() { }

    static ChampionId id(String value) { return new ChampionId(value); }
    static DraftTeamContext context(Position position, Map<com.lolfm.champion.ChampionRoleKey, Integer> values) {
        EnumMap<Position, ChampionProficiencies> result = new EnumMap<>(Position.class);
        result.put(position, new ChampionProficiencies(values));
        return new DraftTeamContext(result);
    }
    static DraftState stateAfter(List<String> championIds) {
        DraftState state = DraftState.fresh(DraftRuleSet.professional(), new SeriesDraftHistory());
        for (String value : championIds) {
            DraftTurn turn = state.currentTurn();
            state = state.apply(new DraftAction(turn.number(), turn.side(), turn.actionType(), id(value)));
        }
        return state;
    }
}

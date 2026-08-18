package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record DraftState(
        DraftRuleSet ruleSet,
        int nextTurnIndex,
        List<ChampionId> bluePicks,
        List<ChampionId> redPicks,
        List<ChampionId> blueBans,
        List<ChampionId> redBans,
        Set<ChampionId> fearlessExclusions
) {
    public DraftState {
        if (ruleSet == null || nextTurnIndex < 0 || nextTurnIndex > ruleSet.turns().size()) throw new IllegalArgumentException("Invalid DraftState turn");
        bluePicks = List.copyOf(bluePicks); redPicks = List.copyOf(redPicks);
        blueBans = List.copyOf(blueBans); redBans = List.copyOf(redBans);
        fearlessExclusions = Set.copyOf(fearlessExclusions);
        LinkedHashSet<ChampionId> current = new LinkedHashSet<>();
        int total = bluePicks.size() + redPicks.size() + blueBans.size() + redBans.size();
        current.addAll(bluePicks); current.addAll(redPicks); current.addAll(blueBans); current.addAll(redBans);
        if (current.size() != total) throw new IllegalArgumentException("Champion repeated in current draft");
        if (current.stream().anyMatch(fearlessExclusions::contains)) throw new IllegalArgumentException("Hard Fearless champion used in current draft");
    }
    public static DraftState fresh(DraftRuleSet rules, SeriesDraftHistory history) {
        return new DraftState(rules, 0, List.of(), List.of(), List.of(), List.of(), history.consumedPicks());
    }
    public boolean complete() { return nextTurnIndex == ruleSet.turns().size(); }
    public DraftTurn currentTurn() {
        if (complete()) throw new IllegalStateException("Draft is complete");
        return ruleSet.turns().get(nextTurnIndex);
    }
    public Set<ChampionId> unavailableChampions() {
        LinkedHashSet<ChampionId> unavailable = new LinkedHashSet<>(fearlessExclusions);
        unavailable.addAll(bluePicks); unavailable.addAll(redPicks); unavailable.addAll(blueBans); unavailable.addAll(redBans);
        return Set.copyOf(unavailable);
    }
    public List<ChampionId> picks(TeamSide side) { return side == TeamSide.BLUE ? bluePicks : redPicks; }
    public List<ChampionId> bans(TeamSide side) { return side == TeamSide.BLUE ? blueBans : redBans; }
    public DraftState apply(DraftAction action) {
        DraftTurn expected = currentTurn();
        if (action.turn() != expected.number() || action.side() != expected.side() || action.actionType() != expected.actionType()) {
            throw new IllegalArgumentException("DraftAction does not match current turn");
        }
        if (unavailableChampions().contains(action.championId())) throw new IllegalArgumentException("Unavailable champion: " + action.championId());
        List<ChampionId> bp = new ArrayList<>(bluePicks), rp = new ArrayList<>(redPicks);
        List<ChampionId> bb = new ArrayList<>(blueBans), rb = new ArrayList<>(redBans);
        List<ChampionId> target = action.actionType() == DraftActionType.PICK
                ? (action.side() == TeamSide.BLUE ? bp : rp) : (action.side() == TeamSide.BLUE ? bb : rb);
        target.add(action.championId());
        return new DraftState(ruleSet, nextTurnIndex + 1, bp, rp, bb, rb, fearlessExclusions);
    }
}

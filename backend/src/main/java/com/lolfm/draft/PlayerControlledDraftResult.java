package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Completed mixed-authority Draft owned by the Draft domain. */
public record PlayerControlledDraftResult(
        DraftRuleSet ruleSet,
        TeamSide controlledSide,
        List<ChampionId> blueBans,
        List<ChampionId> redBans,
        List<ChampionId> bluePicks,
        List<ChampionId> redPicks,
        List<DraftTurnControlEvidence> turnEvidence,
        Map<ChampionId, Position> blueFinalRoleAssignments,
        Map<ChampionId, Position> redFinalRoleAssignments,
        MatchChampionAssignments matchChampionAssignments,
        Set<ChampionId> hardFearlessExclusions,
        String draftMetaVersion,
        String requiredLegalRoleKeyHash,
        String actualLegalRoleKeyHash
) {
    public PlayerControlledDraftResult {
        blueBans = List.copyOf(blueBans);
        redBans = List.copyOf(redBans);
        bluePicks = List.copyOf(bluePicks);
        redPicks = List.copyOf(redPicks);
        turnEvidence = List.copyOf(turnEvidence);
        blueFinalRoleAssignments = Map.copyOf(blueFinalRoleAssignments);
        redFinalRoleAssignments = Map.copyOf(redFinalRoleAssignments);
        hardFearlessExclusions = Set.copyOf(hardFearlessExclusions);
        if (turnEvidence.size() != ruleSet.turns().size()) {
            throw new IllegalArgumentException("Completed Player Draft requires all turns");
        }
    }

    public DraftControlEvidence controlEvidence() {
        return DraftControlEvidence.create(controlledSide, turnEvidence);
    }

    public List<DraftAction> decisions() {
        return turnEvidence.stream().map(value -> new DraftAction(
                value.turn(), value.side(), value.actionType(), value.championId())).toList();
    }

    public String draftIdentity() {
        StringBuilder canonical = new StringBuilder();
        decisions().forEach(value -> canonical.append(value.turn()).append(':')
                .append(value.side()).append(':').append(value.actionType()).append(':')
                .append(value.championId().value()).append('\n'));
        return PlayerDraftControlPolicy.hash(canonical.toString());
    }
}

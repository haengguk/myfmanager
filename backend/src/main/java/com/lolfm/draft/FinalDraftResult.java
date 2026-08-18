package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record FinalDraftResult(
        DraftRuleSet ruleSet,
        List<ChampionId> blueBans,
        List<ChampionId> redBans,
        List<ChampionId> bluePicks,
        List<ChampionId> redPicks,
        List<DraftDecision> decisions,
        Map<ChampionId, Position> blueFinalRoleAssignments,
        Map<ChampionId, Position> redFinalRoleAssignments,
        MatchChampionAssignments matchChampionAssignments,
        DraftPlanPortfolio blueInitialPortfolio,
        DraftPlanPortfolio redInitialPortfolio,
        DraftPlanPortfolio blueFinalPortfolio,
        DraftPlanPortfolio redFinalPortfolio,
        Set<ChampionId> hardFearlessExclusions,
        String draftMetaVersion,
        String requiredLegalRoleKeyHash,
        String actualLegalRoleKeyHash
) {
    public FinalDraftResult {
        blueBans = List.copyOf(blueBans); redBans = List.copyOf(redBans);
        bluePicks = List.copyOf(bluePicks); redPicks = List.copyOf(redPicks);
        decisions = List.copyOf(decisions);
        blueFinalRoleAssignments = Map.copyOf(blueFinalRoleAssignments);
        redFinalRoleAssignments = Map.copyOf(redFinalRoleAssignments);
        hardFearlessExclusions = Set.copyOf(hardFearlessExclusions);
    }
    public String draftIdentity() {
        String canonical = decisions.stream().map(value -> value.turn() + ":" + value.side() + ":"
                + value.actionType() + ":" + value.selectedChampionId().value())
                .collect(java.util.stream.Collectors.joining("\n")) + "\n";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}

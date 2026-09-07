package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.career.CompetitionRosterSnapshot;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import java.util.Map;

/** CA is a rounded display of current ratings. Market decisions retain their full precision. */
public final class PlayerAbilityPolicy {
    public static final String VERSION = "EQUAL_TWELVE_DISPLAY_CA_V1";
    public static final String PA_POLICY = "AUTHORED_LIFETIME_CEILING_DISPLAY_ONLY_V1";
    private PlayerAbilityPolicy() {}

    public static int strength(Map<PlayerSkill, Integer> ratings) {
        if (ratings.size() != 12) throw new IllegalArgumentException("TWELVE_RATINGS_REQUIRED");
        return ratings.values().stream().mapToInt(Integer::intValue).sum();
    }
    public static int currentAbility(Map<PlayerSkill, Integer> ratings) {
        return (int) Math.round(1 + (strength(ratings) - 12) * 199.0 / 228);
    }
    public static ExpandedPlayerCatalog.Definition apply(ObjectMapper mapper, ExpandedPlayerCatalog.Definition p,
            Map<PlayerSkill, Integer> ratings, Integer potential, long revision, String source) {
        new PlayerRatings(p.position(), ratings);
        if (potential != null && (potential < 1 || potential > 200)) throw new IllegalArgumentException("PA_RANGE");
        try {
            var details = (ObjectNode) mapper.readTree(p.detailsJson());
            var metadata = details.putObject("abilityMetadata");
            metadata.put("caPolicy", VERSION).put("paPolicy", PA_POLICY).put("dataRevision", revision).put("sourceVersion", source);
            if (potential == null) metadata.putNull("potentialAbility"); else metadata.put("potentialAbility", potential);
            // Preserve an authored PA below current CA; growth policy may resolve the conflict later.
            var gameplay = new CompetitionRosterSnapshot.Starter(p.playerId(), p.nickname(), p.position(), ratings, p.gameplay().proficiencies());
            return new ExpandedPlayerCatalog.Definition(p.playerId(), p.nickname(), p.position(), gameplay, p.provisional(),
                    p.initialOrganizationId(), p.initialOwnerTeam(), p.initialSquad(), p.eligibilityReason(), mapper.writeValueAsString(details));
        } catch (java.io.IOException e) { throw new IllegalStateException("PLAYER_ABILITY_DETAILS", e); }
    }
}

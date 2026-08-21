package com.lolfm.simulator;

import com.lolfm.domain.Player;
import com.lolfm.domain.Team;
import com.lolfm.player.PlayerId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Stateless preflight for stable person identity uniqueness across one whole match. */
public final class MatchLineupIdentityValidator {
    private MatchLineupIdentityValidator() { }

    public static void validate(Team blueTeam, Team redTeam) {
        Objects.requireNonNull(blueTeam, "blueTeam");
        Objects.requireNonNull(redTeam, "redTeam");
        Map<PlayerId, String> firstSlotById = new LinkedHashMap<>();
        validateTeam(TeamSide.BLUE, blueTeam, firstSlotById);
        validateTeam(TeamSide.RED, redTeam, firstSlotById);
    }

    private static void validateTeam(TeamSide side, Team team, Map<PlayerId, String> firstSlotById) {
        for (Player player : team.getPlayers()) {
            if (!player.hasStablePlayerId()) continue;
            PlayerId playerId = player.requirePlayerId();
            String slot = side.name() + ":" + player.getPosition().name();
            String firstSlot = firstSlotById.putIfAbsent(playerId, slot);
            if (firstSlot != null) {
                throw new IllegalArgumentException("DUPLICATE_MATCH_PLAYER_ID: " + playerId
                        + " first=" + firstSlot + " duplicate=" + slot);
            }
        }
    }
}

package com.lolfm.simulator;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import java.util.Locale;

/** Explicit structured identity for isolated resolver fixtures. */
final class PlayerStateTestFixture {
    private PlayerStateTestFixture() { }

    static PlayerState player(String side, Position position,
                              PlayerAttributes attributes, int startingGold) {
        TeamSide teamSide = TeamSide.valueOf(side);
        PlayerId playerId = new PlayerId("player-fixture-"
                + side.toLowerCase(Locale.ROOT) + "-"
                + position.name().toLowerCase(Locale.ROOT));
        return new PlayerState(new PlayerKey(teamSide, position), playerId,
                side + "-" + position, position, attributes, null, startingGold, true);
    }
}

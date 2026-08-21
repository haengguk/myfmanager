package com.lolfm.player;

import java.util.Objects;

/** One stable person identity bound to a slot in the current authored roster snapshot. */
public record PlayerIdentity(PlayerId playerId, PlayerRatingKey ratingKey, String nickname) {
    public PlayerIdentity {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ratingKey, "ratingKey");
        nickname = Objects.requireNonNull(nickname, "nickname").trim();
        if (nickname.isBlank()) throw new IllegalArgumentException("nickname is required");
    }
}

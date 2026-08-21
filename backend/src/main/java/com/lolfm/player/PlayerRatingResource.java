package com.lolfm.player;

import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.Position;
import java.util.Objects;

/** Runtime player-rating data. Review-only metrics such as Display CA are not represented. */
public record PlayerRatingResource(
        PlayerRatingKey playerKey,
        String nickname,
        PlayerRatings ratings
) {
    public PlayerRatingResource {
        Objects.requireNonNull(playerKey, "playerKey");
        nickname = Objects.requireNonNull(nickname, "nickname").trim();
        if (nickname.isBlank()) throw new IllegalArgumentException("nickname is required");
        Objects.requireNonNull(ratings, "ratings");
        if (ratings.position() != playerKey.position()) {
            throw new IllegalArgumentException("Rating position does not match player key");
        }
    }

    public String teamCode() { return playerKey.teamCode(); }
    public Position position() { return playerKey.position(); }
}

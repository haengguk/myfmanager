package com.lolfm.player;

import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministic production-capable assembly of the current five-player LCK rosters. */
@Component
public final class LckTeamAssembler {
    private final PlayerRatingCatalog ratings;
    private final ChampionProficiencyCatalog proficiencies;

    public LckTeamAssembler(PlayerRatingCatalog ratings,
                            ChampionProficiencyCatalog proficiencies) {
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.proficiencies = Objects.requireNonNull(proficiencies, "proficiencies");
    }

    public static LckTeamAssembler loadDefault() {
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
        ChampionProficiencyCatalog proficiencies = ChampionProficiencyCatalog.loadDefault();
        return new LckTeamAssembler(ratings, proficiencies);
    }

    public Team assemble(String teamCode) {
        String normalized = Objects.requireNonNull(teamCode, "teamCode").trim()
                .toUpperCase(Locale.ROOT);
        if (!ratings.teamCodes().contains(normalized)) {
            throw new IllegalArgumentException("Unknown LCK team code: " + normalized);
        }
        List<Player> players = new ArrayList<>();
        Set<PlayerId> playerIds = new HashSet<>();
        EnumSet<Position> positions = EnumSet.noneOf(Position.class);
        for (Position position : Position.values()) {
            PlayerRatingKey ratingKey = new PlayerRatingKey(normalized, position);
            PlayerId playerId = ratings.playerId(ratingKey);
            Player player = assemblePlayer(playerId, ratingKey, playerId);
            if (!playerIds.add(player.requirePlayerId())) {
                throw new IllegalStateException("Duplicate PlayerId in assembled team: " + playerId);
            }
            if (!positions.add(player.getPosition())) {
                throw new IllegalStateException("Duplicate position in assembled team: " + position);
            }
            players.add(player);
        }
        if (players.size() != Position.values().length
                || !positions.equals(EnumSet.allOf(Position.class))) {
            throw new IllegalStateException("Assembled LCK team is incomplete: " + normalized);
        }
        return new Team(normalized, players);
    }

    /** Explicit provider arguments make mixed identity/profile probes pass through production objects. */
    public Player assemblePlayer(PlayerId runtimePlayerId, PlayerRatingKey ratingKey,
                                 PlayerId proficiencyOwnerId) {
        Objects.requireNonNull(runtimePlayerId, "runtimePlayerId");
        Objects.requireNonNull(ratingKey, "ratingKey");
        PlayerIdentity identity = ratings.identities().get(ratingKey);
        if (!identity.playerId().equals(runtimePlayerId)) {
            throw new IllegalArgumentException("PLAYER_ID_RATING_KEY_MISMATCH: " + runtimePlayerId
                    + "/" + ratingKey.stableId());
        }
        return ratings.createPlayer(runtimePlayerId,
                proficiencies.bind(runtimePlayerId, ratingKey, proficiencyOwnerId));
    }

    public List<Team> assembleAll() {
        return ratings.teamCodes().stream().sorted().map(this::assemble).toList();
    }

    public Set<String> teamCodes() { return ratings.teamCodes(); }
}

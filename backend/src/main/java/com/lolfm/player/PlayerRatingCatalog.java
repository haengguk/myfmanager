package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.Position;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Immutable production lookup for the approved authored player-rating population. */
@Component
public final class PlayerRatingCatalog {
    private final String version;
    private final String resourceSha256;
    private final int teamCount;
    private final int startersPerTeam;
    private final int playerCount;
    private final boolean substitutesIncluded;
    private final int commonAttributeCount;
    private final int roleSpecificAttributeCount;
    private final int activeAttributesPerPlayer;
    private final List<PlayerRatingResource> players;
    private final Map<PlayerRatingKey, PlayerRatingResource> byKey;

    @org.springframework.beans.factory.annotation.Autowired
    public PlayerRatingCatalog(ObjectMapper mapper) {
        this(PlayerRatingResourceLoader.load(mapper,
                PlayerRatingResourceLoader.class.getResourceAsStream(PlayerRatingResourceLoader.RESOURCE)));
    }

    public PlayerRatingCatalog(PlayerRatingResourceLoader.LoadedResource loaded) {
        Objects.requireNonNull(loaded, "loaded");
        version = loaded.version();
        resourceSha256 = loaded.resourceSha256();
        teamCount = loaded.teamCount();
        startersPerTeam = loaded.startersPerTeam();
        playerCount = loaded.playerCount();
        substitutesIncluded = loaded.substitutesIncluded();
        commonAttributeCount = loaded.commonAttributeCount();
        roleSpecificAttributeCount = loaded.roleSpecificAttributeCount();
        activeAttributesPerPlayer = loaded.activeAttributesPerPlayer();
        players = List.copyOf(loaded.players());
        byKey = players.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                PlayerRatingResource::playerKey, value -> value));
    }

    public static PlayerRatingCatalog loadDefault() {
        return new PlayerRatingCatalog(PlayerRatingResourceLoader.loadDefault());
    }

    public String version() { return version; }
    public String resourceSha256() { return resourceSha256; }
    public int teamCount() { return teamCount; }
    public int startersPerTeam() { return startersPerTeam; }
    public int playerCount() { return playerCount; }
    public boolean substitutesIncluded() { return substitutesIncluded; }
    public int commonAttributeCount() { return commonAttributeCount; }
    public int roleSpecificAttributeCount() { return roleSpecificAttributeCount; }
    public int activeAttributesPerPlayer() { return activeAttributesPerPlayer; }
    public List<PlayerRatingResource> all() { return players; }
    public Set<String> teamCodes() {
        return java.util.Collections.unmodifiableSet(new TreeSet<>(
                players.stream().map(PlayerRatingResource::teamCode).collect(java.util.stream.Collectors.toSet())));
    }

    public List<PlayerRatingResource> forTeam(String teamCode) {
        String normalized = new PlayerRatingKey(teamCode, Position.TOP).teamCode();
        return players.stream().filter(value -> value.teamCode().equals(normalized)).toList();
    }

    public Optional<PlayerRatingResource> find(PlayerRatingKey key) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    public PlayerRatingResource get(PlayerRatingKey key) {
        return find(key).orElseThrow(() -> new IllegalArgumentException("Unknown player rating key: " + key.stableId()));
    }

    public PlayerRatings ratings(PlayerRatingKey key) { return get(key).ratings(); }

    /** Creates a domain Player only with an explicit, independently-authored proficiency profile. */
    public Player createPlayer(PlayerRatingKey key, ChampionProficiencies championProficiencies) {
        Objects.requireNonNull(championProficiencies, "championProficiencies");
        PlayerRatingResource resource = get(key);
        return new Player(resource.nickname(), resource.position(), resource.ratings(), championProficiencies);
    }
}

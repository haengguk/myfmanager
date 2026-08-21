package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** One immutable identity record set exposed through person and current-roster indexes. */
@Component
public final class PlayerIdentityCatalog {
    private final String version;
    private final String resourceSha256;
    private final String requiredPlayerRatingResourceVersion;
    private final List<PlayerIdentity> identities;
    private final Map<PlayerId, PlayerIdentity> byId;
    private final Map<PlayerRatingKey, PlayerIdentity> byRatingKey;

    @org.springframework.beans.factory.annotation.Autowired
    public PlayerIdentityCatalog(ObjectMapper mapper) {
        this(PlayerIdentityResourceLoader.load(mapper,
                PlayerIdentityResourceLoader.class.getResourceAsStream(PlayerIdentityResourceLoader.RESOURCE)));
    }

    public PlayerIdentityCatalog(PlayerIdentityResourceLoader.LoadedResource loaded) {
        Objects.requireNonNull(loaded, "loaded");
        version = loaded.version();
        resourceSha256 = loaded.resourceSha256();
        requiredPlayerRatingResourceVersion = loaded.requiredPlayerRatingResourceVersion();
        identities = List.copyOf(loaded.players());
        byId = identities.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                PlayerIdentity::playerId, value -> value));
        byRatingKey = identities.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                PlayerIdentity::ratingKey, value -> value));
    }

    public static PlayerIdentityCatalog loadDefault() {
        return new PlayerIdentityCatalog(PlayerIdentityResourceLoader.loadDefault());
    }

    public String version() { return version; }
    public String resourceSha256() { return resourceSha256; }
    public String requiredPlayerRatingResourceVersion() { return requiredPlayerRatingResourceVersion; }
    public List<PlayerIdentity> all() { return identities; }
    public Set<String> teamCodes() {
        return java.util.Collections.unmodifiableSet(new TreeSet<>(identities.stream()
                .map(value -> value.ratingKey().teamCode()).collect(java.util.stream.Collectors.toSet())));
    }
    public Optional<PlayerIdentity> find(PlayerId playerId) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(playerId, "playerId")));
    }
    public Optional<PlayerIdentity> find(PlayerRatingKey ratingKey) {
        return Optional.ofNullable(byRatingKey.get(Objects.requireNonNull(ratingKey, "ratingKey")));
    }
    public PlayerIdentity get(PlayerId playerId) {
        return find(playerId).orElseThrow(() -> new IllegalArgumentException("Unknown PlayerId: " + playerId));
    }
    public PlayerIdentity get(PlayerRatingKey ratingKey) {
        return find(ratingKey).orElseThrow(() -> new IllegalArgumentException(
                "Unknown PlayerRatingKey: " + ratingKey.stableId()));
    }
    public PlayerId findByRatingKey(PlayerRatingKey ratingKey) { return get(ratingKey).playerId(); }
    public PlayerRatingKey currentRatingKey(PlayerId playerId) { return get(playerId).ratingKey(); }
}

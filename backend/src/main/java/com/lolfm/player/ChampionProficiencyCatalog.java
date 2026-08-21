package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Immutable stable-person ownership boundary for the sparse proficiency population. */
@Component
public final class ChampionProficiencyCatalog {
    private final ChampionProficiencyResourceLoader.LoadedResource loaded;
    private final PlayerRatingCatalog ratings;
    private final ChampionCatalog champions;
    private final Map<PlayerId, ChampionProficiencies> byPlayerId;

    @org.springframework.beans.factory.annotation.Autowired
    public ChampionProficiencyCatalog(ObjectMapper mapper, PlayerRatingCatalog ratings,
                                      ChampionCatalog champions) {
        this(ChampionProficiencyResourceLoader.load(mapper,
                ChampionProficiencyResourceLoader.class.getResourceAsStream(
                        ChampionProficiencyResourceLoader.RESOURCE), ratings, champions), ratings, champions);
    }

    public ChampionProficiencyCatalog(ChampionProficiencyResourceLoader.LoadedResource loaded,
                                      PlayerRatingCatalog ratings, ChampionCatalog champions) {
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.champions = Objects.requireNonNull(champions, "champions");
        byPlayerId = Map.copyOf(loaded.proficiencies());
        validatePrerequisites();
    }

    public static ChampionProficiencyCatalog loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
        ChampionCatalog champions = new ChampionCatalog(mapper);
        return loadDefault(mapper, ratings, champions);
    }

    public static ChampionProficiencyCatalog loadDefault(PlayerRatingCatalog ratings,
                                                          ChampionCatalog champions) {
        return loadDefault(new ObjectMapper(), ratings, champions);
    }

    static ChampionProficiencyCatalog loadDefault(ObjectMapper mapper, PlayerRatingCatalog ratings,
                                                   ChampionCatalog champions) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(ratings, "ratings");
        Objects.requireNonNull(champions, "champions");
        return new ChampionProficiencyCatalog(
                ChampionProficiencyResourceLoader.load(mapper,
                        ChampionProficiencyResourceLoader.class.getResourceAsStream(
                                ChampionProficiencyResourceLoader.RESOURCE), ratings, champions),
                ratings, champions);
    }

    public String version() { return loaded.version(); }
    public String researchAsOf() { return loaded.researchAsOf(); }
    public String resourceSha256() { return loaded.resourceSha256(); }
    public String requiredPlayerRatingResourceVersion() {
        return loaded.requiredPlayerRatingResourceVersion();
    }
    public String requiredChampionPoolVersion() { return loaded.requiredChampionPoolVersion(); }
    public int requiredLegalRoleKeyCount() { return loaded.requiredLegalRoleKeyCount(); }
    public int highProficiencyThreshold() { return loaded.highProficiencyThreshold(); }
    public ChampionProficiencyPopulationMetrics metrics() { return loaded.metrics(); }
    public List<ChampionProficiencyEntry> authoredEntries() { return loaded.authoredEntries(); }
    public Map<PlayerId, ChampionProficiencies> all() { return byPlayerId; }
    PlayerRatingCatalog ratingsCatalog() { return ratings; }
    ChampionCatalog championCatalog() { return champions; }

    public Optional<ChampionProficiencies> find(PlayerId playerId) {
        return Optional.ofNullable(byPlayerId.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public ChampionProficiencies get(PlayerId playerId) {
        return find(playerId).orElseThrow(() -> new IllegalArgumentException("Unknown PlayerId: " + playerId));
    }

    public ChampionProficiencies get(PlayerRatingKey ratingKey) {
        return get(ratings.playerId(Objects.requireNonNull(ratingKey, "ratingKey")));
    }

    public int value(PlayerId playerId, ChampionRoleKey roleKey) {
        validateSubjectRole(playerId, roleKey);
        return get(playerId).get(roleKey);
    }

    public int value(PlayerRatingKey ratingKey, ChampionRoleKey roleKey) {
        Objects.requireNonNull(ratingKey, "ratingKey");
        if (ratingKey.position() != roleKey.position()) {
            throw new IllegalArgumentException("INVALID_SUBJECT_ROLE_BINDING: " + ratingKey.stableId()
                    + "/" + roleKey.stableId());
        }
        return value(ratings.playerId(ratingKey), roleKey);
    }

    /** Production preflight path that keeps rating subject, person, and profile provider explicit. */
    public ChampionProficiencies bind(PlayerId playerId, PlayerRatingKey ratingKey,
                                      PlayerId proficiencyOwnerId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ratingKey, "ratingKey");
        Objects.requireNonNull(proficiencyOwnerId, "proficiencyOwnerId");
        PlayerId expected = ratings.playerId(ratingKey);
        if (!expected.equals(playerId)) {
            throw new IllegalArgumentException("PLAYER_ID_RATING_KEY_MISMATCH: " + playerId
                    + "/" + ratingKey.stableId());
        }
        if (!playerId.equals(proficiencyOwnerId)) {
            throw new IllegalArgumentException("PROFICIENCY_BINDING_MISMATCH: subject=" + playerId
                    + " provider=" + proficiencyOwnerId);
        }
        return get(proficiencyOwnerId);
    }

    private void validateSubjectRole(PlayerId playerId, ChampionRoleKey roleKey) {
        Objects.requireNonNull(roleKey, "roleKey");
        PlayerRatingKey ratingKey = ratings.currentRatingKey(playerId);
        if (ratingKey.position() != roleKey.position()) {
            throw new IllegalArgumentException("INVALID_SUBJECT_ROLE_BINDING: " + ratingKey.stableId()
                    + "/" + roleKey.stableId());
        }
        if (!champions.supports(roleKey)) {
            throw new IllegalArgumentException("Illegal ChampionRoleKey: " + roleKey.stableId());
        }
    }

    private void validatePrerequisites() {
        if (!ratings.version().equals(loaded.requiredPlayerRatingResourceVersion())) {
            throw new IllegalStateException("Champion proficiency rating prerequisite mismatch");
        }
        if (!champions.championPoolVersion().equals(loaded.requiredChampionPoolVersion())) {
            throw new IllegalStateException("Champion proficiency pool prerequisite mismatch");
        }
        if (champions.legalRoleKeys().size() != loaded.requiredLegalRoleKeyCount()) {
            throw new IllegalStateException("Champion proficiency legal-role prerequisite mismatch");
        }
        Set<PlayerId> expectedSubjects = ratings.identities().all().stream()
                .map(PlayerIdentity::playerId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!byPlayerId.keySet().equals(expectedSubjects)) {
            Set<PlayerId> missing = new java.util.HashSet<>(expectedSubjects);
            missing.removeAll(byPlayerId.keySet());
            Set<PlayerId> unknown = new java.util.HashSet<>(byPlayerId.keySet());
            unknown.removeAll(expectedSubjects);
            throw new IllegalStateException("Champion proficiency PlayerId subject mismatch; missing="
                    + missing + " unknown=" + unknown);
        }
    }
}

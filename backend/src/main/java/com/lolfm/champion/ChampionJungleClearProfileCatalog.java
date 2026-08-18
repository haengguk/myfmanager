package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** JUNGLE-only clear profiles, separate from power, ganking, pathing and player ratings. */
public final class ChampionJungleClearProfileCatalog {
    private final Map<ChampionRoleKey, ChampionJungleClearProfile> profiles;

    private ChampionJungleClearProfileCatalog(Map<ChampionRoleKey, ChampionJungleClearProfile> profiles) {
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    public static ChampionJungleClearProfileCatalog loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
        ChampionCatalog champions = new ChampionCatalog(
                mapper, requiredResource(manifest.catalog(), "catalog"));
        return load(mapper, champions, requiredResource(manifest.jungleClear(), "jungleClear"));
    }

    public static ChampionJungleClearProfileCatalog load(
            ObjectMapper mapper, ChampionCatalog champions, InputStream input
    ) {
        RawCatalog raw = read(mapper, input);
        if (!Objects.equals(raw.requiredChampionPoolVersion, champions.championPoolVersion())) {
            throw new IllegalStateException("Jungle clear pool version mismatch");
        }
        if (raw.profiles == null) throw new IllegalStateException("Missing jungle clear profiles");

        LinkedHashMap<ChampionRoleKey, ChampionJungleClearProfile> materialized = new LinkedHashMap<>();
        for (RawProfile authored : raw.profiles) {
            ChampionRoleKey key = new ChampionRoleKey(
                    new ChampionId(authored.championId), authored.position);
            if (authored.position != Position.JUNGLE || !champions.supports(key)) {
                throw new IllegalStateException("Invalid jungle clear role " + key);
            }
            ChampionJungleClearProfile profile = new ChampionJungleClearProfile(
                    key, authored.early, authored.mid, authored.late, authored.gameplayEnabled);
            if (materialized.put(key, profile) != null) {
                throw new IllegalStateException("Duplicate jungle clear role " + key);
            }
        }
        Set<ChampionRoleKey> expected = champions.legalRoleKeys().stream()
                .filter(key -> key.position() == Position.JUNGLE)
                .collect(java.util.stream.Collectors.toSet());
        if (!materialized.keySet().equals(expected)) {
            throw new IllegalStateException("Jungle clear coverage must match legal JUNGLE roles");
        }
        return new ChampionJungleClearProfileCatalog(materialized);
    }

    public Map<ChampionRoleKey, ChampionJungleClearProfile> profiles() { return profiles; }

    public ChampionJungleClearProfile get(ChampionRoleKey key) {
        ChampionJungleClearProfile profile = profiles.get(key);
        if (profile == null) throw new IllegalArgumentException("Missing jungle clear profile " + key);
        return profile;
    }

    private static RawCatalog read(ObjectMapper mapper, InputStream input) {
        try (input) {
            if (input == null) throw new IllegalStateException("Missing jungle clear resource");
            return mapper.readValue(input, RawCatalog.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load jungle clear resource", error);
        }
    }

    private static InputStream requiredResource(String path, String role) {
        InputStream input = ChampionResourceManifest.open(path);
        if (input == null) throw new IllegalStateException("Missing " + role + " resource: " + path);
        return input;
    }

    public static final class RawCatalog {
        public String profileVersion;
        public String requiredChampionPoolVersion;
        public List<RawProfile> profiles;
    }

    public static final class RawProfile {
        public String championId;
        public Position position;
        public double early;
        public double mid;
        public double late;
        public boolean gameplayEnabled;
    }
}

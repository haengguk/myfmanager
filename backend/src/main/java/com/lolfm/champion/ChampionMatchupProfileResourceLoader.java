package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads authoring profiles and materializes complete ChampionRoleKey profiles. */
public final class ChampionMatchupProfileResourceLoader {
    private ChampionMatchupProfileResourceLoader() {}

    public static ChampionRoleMatchupProfileCatalog loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
        ChampionCatalog champions = new ChampionCatalog(
                mapper, requiredResource(manifest.catalog(), "catalog"));
        return load(mapper, champions, requiredResource(manifest.matchup(), "matchup"), false);
    }

    public static ChampionRoleMatchupProfileCatalog load(
            ObjectMapper mapper, ChampionCatalog champions, InputStream input, boolean prototypeOnly
    ) {
        RawCatalog raw = read(mapper, input);
        required(raw.profileVersion, "profileVersion");
        if (!Objects.equals(raw.requiredChampionPoolVersion, champions.championPoolVersion())) {
            throw new IllegalStateException("Matchup pool version mismatch");
        }
        if (raw.championProfiles == null) throw new IllegalStateException("Missing championProfiles");

        LinkedHashMap<ChampionRoleKey, ChampionRoleMatchupProfile> materialized = new LinkedHashMap<>();
        Set<ChampionId> authoredChampions = new HashSet<>();
        for (RawProfile authored : raw.championProfiles) {
            materializeChampion(raw.profileVersion, champions, authored, authoredChampions, materialized);
        }

        Set<ChampionId> expectedChampions = champions.all().stream()
                .map(ChampionDefinition::id).collect(java.util.stream.Collectors.toSet());
        if (!authoredChampions.equals(expectedChampions)
                || !materialized.keySet().equals(champions.legalRoleKeys())) {
            throw new IllegalStateException("Matchup coverage must exactly match legal roles");
        }
        return ChampionRoleMatchupProfileCatalog.materialized(
                raw.profileVersion, prototypeOnly, List.copyOf(materialized.values()));
    }

    private static void materializeChampion(
            String version, ChampionCatalog champions, RawProfile authored, Set<ChampionId> authoredChampions,
            Map<ChampionRoleKey, ChampionRoleMatchupProfile> materialized
    ) {
        ChampionId championId = new ChampionId(authored.championId);
        ChampionDefinition champion = champions.find(championId)
                .orElseThrow(() -> new IllegalStateException("Unknown matchup champion " + championId));
        if (!authoredChampions.add(championId)) {
            throw new IllegalStateException("Duplicate matchup champion " + championId);
        }
        EnumMap<ChampionMatchupTrait, Integer> base = completeTraits(
                authored.baseTraits, championId.toString());
        Map<Position, RawOverride> overrides = indexOverrides(authored.roleOverrides, champion);

        for (Position position : champion.supportedPositions().stream().sorted().toList()) {
            EnumMap<ChampionMatchupTrait, Integer> resolved = new EnumMap<>(base);
            RawOverride override = overrides.get(position);
            if (override != null && override.traits != null) resolved.putAll(override.traits);
            validateComplete(resolved, championId + ":" + position);
            ChampionRoleKey key = new ChampionRoleKey(championId, position);
            if (materialized.put(key, new ChampionRoleMatchupProfile(key, version, resolved)) != null) {
                throw new IllegalStateException("Duplicate matchup role " + key);
            }
        }
    }

    private static Map<Position, RawOverride> indexOverrides(
            List<RawOverride> values, ChampionDefinition champion
    ) {
        EnumMap<Position, RawOverride> indexed = new EnumMap<>(Position.class);
        if (values == null) return indexed;
        for (RawOverride override : values) {
            if (override.position == null || !champion.supportedPositions().contains(override.position)) {
                throw new IllegalStateException(
                        "Unsupported matchup override " + champion.id() + ":" + override.position);
            }
            if (indexed.put(override.position, override) != null) {
                throw new IllegalStateException(
                        "Duplicate matchup override " + champion.id() + ":" + override.position);
            }
        }
        return indexed;
    }

    private static EnumMap<ChampionMatchupTrait, Integer> completeTraits(
            Map<ChampionMatchupTrait, Integer> values, String owner
    ) {
        if (values == null) throw new IllegalStateException("Missing matchup traits " + owner);
        EnumMap<ChampionMatchupTrait, Integer> result = new EnumMap<>(ChampionMatchupTrait.class);
        result.putAll(values);
        validateComplete(result, owner);
        return result;
    }

    private static void validateComplete(Map<ChampionMatchupTrait, Integer> values, String owner) {
        if (values.size() != ChampionMatchupTrait.values().length) {
            throw new IllegalStateException("All 15 matchup traits required " + owner);
        }
        for (ChampionMatchupTrait trait : ChampionMatchupTrait.values()) {
            Integer value = values.get(trait);
            if (value == null || value < 1 || value > 20) {
                throw new IllegalStateException("Invalid matchup trait " + trait + " " + owner);
            }
        }
    }

    private static RawCatalog read(ObjectMapper mapper, InputStream input) {
        try (input) {
            if (input == null) throw new IllegalStateException("Missing matchup resource");
            return mapper.readValue(input, RawCatalog.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load matchup resource", error);
        }
    }

    private static InputStream requiredResource(String path, String role) {
        InputStream input = ChampionResourceManifest.open(path);
        if (input == null) throw new IllegalStateException("Missing " + role + " resource: " + path);
        return input;
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
    }

    public static final class RawCatalog {
        public String profileVersion;
        public String requiredChampionPoolVersion;
        public List<RawProfile> championProfiles;
    }

    public static final class RawProfile {
        public String championId;
        public Map<ChampionMatchupTrait, Integer> baseTraits;
        public List<RawOverride> roleOverrides;
        public List<ChampionMatchupTrait> primaryStrengthTraits;
        public List<ChampionMatchupTrait> primaryWeaknessTraits;
        public String kitInteractionSummary;
        public String profileSource;
    }

    public static final class RawOverride {
        public Position position;
        public Map<ChampionMatchupTrait, Integer> traits;
    }
}

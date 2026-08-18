package com.lolfm.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionResourceManifest;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fully materialized composition profiles loaded from the active manifest. */
public final class ChampionCompositionProfileCatalog {
    private final String version;
    private final Map<ChampionRoleKey, ChampionCompositionProfile> profiles;

    private ChampionCompositionProfileCatalog(
            String version, Map<ChampionRoleKey, ChampionCompositionProfile> profiles
    ) {
        this.version = version;
        this.profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    public static ChampionCompositionProfileCatalog loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
        ChampionCatalog champions = new ChampionCatalog(
                mapper, requiredResource(manifest.catalog(), "catalog"));
        return load(mapper, champions, requiredResource(manifest.composition(), "composition"));
    }

    public static ChampionCompositionProfileCatalog load(
            ObjectMapper mapper, ChampionCatalog champions, InputStream input
    ) {
        RawCatalog raw = read(mapper, input);
        if (raw.profileVersion == null
                || !Objects.equals(raw.requiredChampionPoolVersion, champions.championPoolVersion())) {
            throw new IllegalStateException("Composition resource identity mismatch");
        }
        if (raw.championProfiles == null) throw new IllegalStateException("Missing championProfiles");

        LinkedHashMap<ChampionRoleKey, ChampionCompositionProfile> materialized = new LinkedHashMap<>();
        Set<ChampionId> authoredChampions = new HashSet<>();
        for (RawProfile authored : raw.championProfiles) {
            materializeChampion(raw.profileVersion, champions, authored, authoredChampions, materialized);
        }
        Set<ChampionId> expectedChampions = champions.all().stream()
                .map(ChampionDefinition::id).collect(java.util.stream.Collectors.toSet());
        if (!authoredChampions.equals(expectedChampions)
                || !materialized.keySet().equals(champions.legalRoleKeys())) {
            throw new IllegalStateException("Composition coverage must exactly match legal roles");
        }
        return new ChampionCompositionProfileCatalog(raw.profileVersion, materialized);
    }

    private static void materializeChampion(
            String version, ChampionCatalog champions, RawProfile authored, Set<ChampionId> authoredChampions,
            Map<ChampionRoleKey, ChampionCompositionProfile> materialized
    ) {
        ChampionId championId = new ChampionId(authored.championId);
        ChampionDefinition champion = champions.find(championId)
                .orElseThrow(() -> new IllegalStateException("Unknown composition champion " + championId));
        if (!authoredChampions.add(championId)) {
            throw new IllegalStateException("Duplicate composition champion " + championId);
        }
        EnumMap<CompositionCapability, Integer> base = completeCapabilities(
                authored.baseCapabilities, championId.toString());
        if (authored.damageProfile == null) {
            throw new IllegalStateException("Missing damage profile " + championId);
        }
        Map<Position, RawOverride> overrides = indexOverrides(authored.roleOverrides, champion);

        for (Position position : champion.supportedPositions().stream().sorted().toList()) {
            EnumMap<CompositionCapability, Integer> resolved = new EnumMap<>(base);
            RawOverride override = overrides.get(position);
            if (override != null && override.capabilities != null) resolved.putAll(override.capabilities);
            validateCapabilities(resolved, championId + ":" + position);
            DamageChannelProfile damage = override != null && override.damageProfile != null
                    ? override.damageProfile : authored.damageProfile;
            ChampionRoleKey key = new ChampionRoleKey(championId, position);
            if (materialized.put(key, new ChampionCompositionProfile(key, resolved, damage)) != null) {
                throw new IllegalStateException("Duplicate composition role " + key);
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
                        "Unsupported composition override " + champion.id() + ":" + override.position);
            }
            if (indexed.put(override.position, override) != null) {
                throw new IllegalStateException(
                        "Duplicate composition override " + champion.id() + ":" + override.position);
            }
        }
        return indexed;
    }

    private static EnumMap<CompositionCapability, Integer> completeCapabilities(
            Map<CompositionCapability, Integer> values, String owner
    ) {
        if (values == null) throw new IllegalStateException("Missing composition capabilities " + owner);
        EnumMap<CompositionCapability, Integer> result = new EnumMap<>(CompositionCapability.class);
        result.putAll(values);
        validateCapabilities(result, owner);
        return result;
    }

    private static void validateCapabilities(Map<CompositionCapability, Integer> values, String owner) {
        if (values.size() != CompositionCapability.values().length) {
            throw new IllegalStateException("All 15 composition capabilities required " + owner);
        }
        for (CompositionCapability capability : CompositionCapability.values()) {
            Integer value = values.get(capability);
            if (value == null || value < 1 || value > 20) {
                throw new IllegalStateException("Invalid capability " + capability + " " + owner);
            }
        }
    }

    public String version() { return version; }
    public Map<ChampionRoleKey, ChampionCompositionProfile> profiles() { return profiles; }

    public String canonicalSerialization() {
        StringBuilder out = new StringBuilder(version).append('\n');
        for (Position position : Position.values()) {
            profiles.entrySet().stream()
                    .filter(entry -> entry.getKey().position() == position)
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ChampionRoleKey::stableId)))
                    .forEach(entry -> appendCanonical(out, entry));
        }
        return out.toString();
    }

    private static void appendCanonical(
            StringBuilder out, Map.Entry<ChampionRoleKey, ChampionCompositionProfile> entry
    ) {
        out.append(entry.getKey().stableId());
        for (CompositionCapability capability : CompositionCapability.values()) {
            out.append('|').append(entry.getValue().capability(capability));
        }
        DamageChannelProfile damage = entry.getValue().damageProfile();
        out.append('|').append(damage.physicalThreat())
                .append('|').append(damage.magicThreat())
                .append('|').append(damage.trueDamageThreat()).append('\n');
    }

    public String profileHash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalSerialization().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static RawCatalog read(ObjectMapper mapper, InputStream input) {
        try (input) {
            if (input == null) throw new IllegalStateException("Missing composition resource");
            return mapper.readValue(input, RawCatalog.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load composition resource", error);
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
        public List<RawProfile> championProfiles;
    }

    public static final class RawProfile {
        public String championId;
        public Map<CompositionCapability, Integer> baseCapabilities;
        public DamageChannelProfile damageProfile;
        public List<RawOverride> roleOverrides;
    }

    public static final class RawOverride {
        public Position position;
        public Map<CompositionCapability, Integer> capabilities;
        public DamageChannelProfile damageProfile;
    }
}

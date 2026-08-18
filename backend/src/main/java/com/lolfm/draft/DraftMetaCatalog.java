package com.lolfm.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DraftMetaCatalog {
    public static final String RESOURCE = "draft/draft-meta-full-173-216-role-2026-08-18-v3.json";
    public static final String VERSION = "draft-meta-full-173-216-role-2026-08-18-v3";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_SORTED_CHAMPION_ID_COLON_POSITION_LINES_TRAILING_NEWLINE_V1";

    private final String metaVersion;
    private final String requiredChampionPoolVersion;
    private final int requiredLegalRoleKeyCount;
    private final String requiredLegalRoleKeyHash;
    private final String actualLegalRoleKeyHash;
    private final String asOfDate;
    private final boolean hardFearlessContext;
    private final Map<ChampionRoleKey, DraftMetaProfile> profiles;

    private DraftMetaCatalog(RawCatalog raw, ChampionCatalog champions) {
        metaVersion = required(raw.metaVersion, "metaVersion");
        requiredChampionPoolVersion = required(raw.requiredChampionPoolVersion, "requiredChampionPoolVersion");
        requiredLegalRoleKeyCount = raw.requiredLegalRoleKeyCount;
        requiredLegalRoleKeyHash = required(raw.requiredLegalRoleKeyHash, "requiredLegalRoleKeyHash");
        asOfDate = required(raw.asOfDate, "asOfDate");
        hardFearlessContext = raw.hardFearlessContext;
        if (!VERSION.equals(metaVersion)) throw new IllegalStateException("Unsupported Draft Meta version: " + metaVersion);
        if (!HASH_ALGORITHM.equals(raw.legalRoleKeyHashAlgorithm)) throw new IllegalStateException("Unsupported legal-role hash algorithm");
        if (!"1-20".equals(raw.priorityScale)) throw new IllegalStateException("Draft Meta priority scale must be 1-20");
        if (!hardFearlessContext) throw new IllegalStateException("Draft Meta must be authored for Hard Fearless");
        if (!Objects.equals(champions.championPoolVersion(), requiredChampionPoolVersion)) {
            throw new IllegalStateException("Draft Meta champion pool version mismatch");
        }

        Set<ChampionRoleKey> legal = champions.legalRoleKeys();
        actualLegalRoleKeyHash = legalRoleKeyHash(legal);
        if (requiredLegalRoleKeyCount != legal.size()) {
            throw new IllegalStateException("Draft Meta legal-role count mismatch: required="
                    + requiredLegalRoleKeyCount + " actual=" + legal.size());
        }
        if (!requiredLegalRoleKeyHash.equals(actualLegalRoleKeyHash)) {
            throw new IllegalStateException("Draft Meta legal-role hash mismatch: required="
                    + requiredLegalRoleKeyHash + " actual=" + actualLegalRoleKeyHash);
        }
        if (raw.profiles == null) throw new IllegalStateException("Missing Draft Meta profiles");
        LinkedHashMap<ChampionRoleKey, DraftMetaProfile> indexed = new LinkedHashMap<>();
        for (RawProfile item : raw.profiles) {
            ChampionRoleKey key = new ChampionRoleKey(new ChampionId(item.championId), item.position);
            if (!champions.supports(key)) throw new IllegalStateException("Unsupported Draft Meta role: " + key.stableId());
            DraftMetaProfile profile = new DraftMetaProfile(key, item.priority);
            if (indexed.put(key, profile) != null) throw new IllegalStateException("Duplicate Draft Meta role: " + key.stableId());
        }
        if (!indexed.keySet().equals(legal)) {
            Set<ChampionRoleKey> missing = legal.stream().filter(key -> !indexed.containsKey(key)).collect(Collectors.toSet());
            Set<ChampionRoleKey> extra = indexed.keySet().stream().filter(key -> !legal.contains(key)).collect(Collectors.toSet());
            throw new IllegalStateException("Draft Meta coverage mismatch: missing=" + missing + " extra=" + extra);
        }
        profiles = Collections.unmodifiableMap(indexed);
    }

    public static DraftMetaCatalog loadDefault(ObjectMapper mapper, ChampionCatalog champions) {
        InputStream input = DraftMetaCatalog.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Draft Meta resource not found: " + RESOURCE);
        return load(mapper, champions, input);
    }

    public static DraftMetaCatalog load(ObjectMapper mapper, ChampionCatalog champions, InputStream input) {
        try (input) {
            if (input == null) throw new IllegalStateException("Draft Meta input is required");
            return new DraftMetaCatalog(mapper.readValue(input, RawCatalog.class), champions);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load Draft Meta", error);
        }
    }

    public static String legalRoleKeyHash(Set<ChampionRoleKey> keys) {
        List<String> lines = keys.stream().map(ChampionRoleKey::stableId).sorted().toList();
        String canonical = String.join("\n", lines) + "\n";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public DraftMetaProfile get(ChampionRoleKey key) {
        DraftMetaProfile profile = profiles.get(key);
        if (profile == null) throw new IllegalArgumentException("No Draft Meta profile: " + key.stableId());
        return profile;
    }
    public int priority(ChampionRoleKey key) { return get(key).priority(); }
    public List<DraftMetaProfile> forChampion(ChampionId id) {
        List<DraftMetaProfile> result = new ArrayList<>();
        profiles.values().stream().filter(value -> value.roleKey().championId().equals(id))
                .sorted(java.util.Comparator.comparing(value -> value.roleKey().position())).forEach(result::add);
        return List.copyOf(result);
    }
    public String metaVersion() { return metaVersion; }
    public String requiredChampionPoolVersion() { return requiredChampionPoolVersion; }
    public int requiredLegalRoleKeyCount() { return requiredLegalRoleKeyCount; }
    public String requiredLegalRoleKeyHash() { return requiredLegalRoleKeyHash; }
    public String actualLegalRoleKeyHash() { return actualLegalRoleKeyHash; }
    public String asOfDate() { return asOfDate; }
    public boolean hardFearlessContext() { return hardFearlessContext; }
    public Map<ChampionRoleKey, DraftMetaProfile> profiles() { return profiles; }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing Draft Meta " + name);
        return value;
    }
    public static final class RawCatalog {
        public String metaVersion;
        public String requiredChampionPoolVersion;
        public int requiredLegalRoleKeyCount;
        public String requiredLegalRoleKeyHash;
        public String legalRoleKeyHashAlgorithm;
        public String asOfDate;
        public String priorityScale;
        public boolean hardFearlessContext;
        public String authoringMethod;
        public List<RawProfile> profiles;
    }
    public static final class RawProfile {
        public String championId;
        public Position position;
        public int priority;
    }
}

package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.ProgressionCombatContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ChampionPowerProfileCatalog {
    private final String profileVersion;
    private final String requiredChampionPoolVersion;
    private final Map<String, LevelPowerCurve> levelCurves;
    private final Map<String, Map<ItemProgressStage, Double>> itemCurves;
    private final Map<ChampionId, ChampionPowerProfile> profiles;
    private final List<String> warnings;

    @org.springframework.beans.factory.annotation.Autowired
    public ChampionPowerProfileCatalog(ObjectMapper mapper, ChampionCatalog champions) {
        this(mapper, champions, activePowerResource(mapper));
    }
    ChampionPowerProfileCatalog(ObjectMapper mapper, ChampionCatalog champions, InputStream input) {
        RawCatalog raw;
        try (input) {
            if (input == null) throw new IllegalStateException("Champion power resource not found");
            raw = mapper.readValue(input, RawCatalog.class);
        } catch (IOException error) { throw new IllegalStateException("Failed to load champion power profiles", error); }
        profileVersion = required(raw.profileVersion(), "profileVersion");
        requiredChampionPoolVersion = required(raw.requiredChampionPoolVersion(), "requiredChampionPoolVersion");
        if (!requiredChampionPoolVersion.equals(champions.championPoolVersion())) throw new IllegalStateException("Champion power pool version mismatch");
        LinkedHashMap<String, LevelPowerCurve> levels = new LinkedHashMap<>();
        raw.levelCurves().forEach((id, values) -> {
            LinkedHashMap<Integer, Double> anchors = new LinkedHashMap<>();
            values.forEach((level, value) -> anchors.put(Integer.parseInt(level), value));
            if (levels.put(id, new LevelPowerCurve(id, anchors)) != null) throw new IllegalStateException("Duplicate level curve: " + id);
        });
        levelCurves = Map.copyOf(levels);
        LinkedHashMap<String, Map<ItemProgressStage, Double>> items = new LinkedHashMap<>();
        raw.itemCurves().forEach((id, values) -> {
            if (values.size() != ItemProgressStage.values().length) throw new IllegalStateException("Incomplete item curve: " + id);
            values.values().forEach(ChampionPowerProfileCatalog::validateValue);
            if (items.put(id, Map.copyOf(values)) != null) throw new IllegalStateException("Duplicate item curve: " + id);
        });
        itemCurves = Map.copyOf(items);
        LinkedHashMap<ChampionId, ChampionPowerProfile> indexed = new LinkedHashMap<>();
        for (RawProfile item : raw.championProfiles()) {
            ChampionId id = new ChampionId(item.championId());
            if (champions.find(id).isEmpty()) throw new IllegalStateException("Unknown champion power profile: " + id);
            LevelPowerCurve templateLevel = Optional.ofNullable(levelCurves.get(item.levelCurveId())).orElseThrow(() -> new IllegalStateException("Unknown level curve: " + item.levelCurveId()));
            LinkedHashMap<Integer, Double> resolvedLevels = new LinkedHashMap<>(templateLevel.anchors());
            if (item.levelAnchors() != null) item.levelAnchors().forEach((anchor, value) -> resolvedLevels.put(Integer.parseInt(anchor), value));
            LevelPowerCurve level = new LevelPowerCurve(item.levelCurveId(), resolvedLevels);
            Map<ItemProgressStage, Double> templateItems = Optional.ofNullable(itemCurves.get(item.itemCurveId())).orElseThrow(() -> new IllegalStateException("Unknown item curve: " + item.itemCurveId()));
            LinkedHashMap<ItemProgressStage, Double> resolvedItems = new LinkedHashMap<>(templateItems);
            if (item.itemAnchors() != null) resolvedItems.putAll(item.itemAnchors());
            Map<ItemProgressStage, Double> curve = Map.copyOf(resolvedItems);
            ChampionPowerProfile profile = new ChampionPowerProfile(id, item.levelCurveId(), item.itemCurveId(), level, curve, item.contexts(), item.tags(), profileVersion);
            if (indexed.put(id, profile) != null) throw new IllegalStateException("Duplicate champion power profile: " + id);
        }
        Set<ChampionId> expected = champions.all().stream().map(ChampionDefinition::id).collect(java.util.stream.Collectors.toSet());
        if (!indexed.keySet().equals(expected)) throw new IllegalStateException("Champion power profiles must match catalog champions 1:1");
        profiles = Map.copyOf(indexed);
        warnings = List.copyOf(computeWarnings());
    }
    private static InputStream activePowerResource(ObjectMapper mapper) {
        ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
        InputStream input = ChampionResourceManifest.open(manifest.power());
        if (input == null) throw new IllegalStateException("Champion power resource not found: " + manifest.power());
        return input;
    }

    public static ChampionPowerProfileCatalog loadDefault() { ChampionCatalog catalog = new ChampionCatalog(new ObjectMapper()); return new ChampionPowerProfileCatalog(new ObjectMapper(), catalog); }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name); return value; }
    private static void validateValue(double value) { if (!Double.isFinite(value) || value < ChampionPowerRuleConfig.PROFILE_VALUE_MIN || value > ChampionPowerRuleConfig.PROFILE_VALUE_MAX) throw new IllegalStateException("Profile curve value out of range: " + value); }
    private List<String> computeWarnings() {
        List<String> result = new ArrayList<>(); Set<String> resolved = new HashSet<>();
        for (ChampionPowerProfile profile : profiles.values()) {
            String signature = profile.levelCurve().anchors() + "|" + profile.itemModifiers() + "|" + profile.contextModifiers();
            if (!resolved.add(signature)) result.add("EXACT_DUPLICATE:" + profile.championId());
            boolean allNonNegative = profile.contextModifiers().values().stream().allMatch(v -> v >= 0);
            boolean allNonPositive = profile.contextModifiers().values().stream().allMatch(v -> v <= 0);
            if (allNonNegative) result.add("ALL_NON_NEGATIVE_CONTEXT:" + profile.championId());
            if (allNonPositive) result.add("ALL_NON_POSITIVE_CONTEXT:" + profile.championId());
            if (profile.contextModifiers().values().stream().noneMatch(v -> v > 0)) result.add("NO_STRENGTH:" + profile.championId());
            if (profile.contextModifiers().values().stream().noneMatch(v -> v < 0)) result.add("NO_WEAKNESS:" + profile.championId());
        }
        return result;
    }
    public String profileVersion() { return profileVersion; }
    public String requiredChampionPoolVersion() { return requiredChampionPoolVersion; }
    public Map<String, LevelPowerCurve> levelCurves() { return levelCurves; }
    public Map<String, Map<ItemProgressStage, Double>> itemCurves() { return itemCurves; }
    public List<ChampionPowerProfile> all() { return List.copyOf(profiles.values()); }
    public ChampionPowerProfile get(ChampionId id) { ChampionPowerProfile value = profiles.get(id); if (value == null) throw new IllegalArgumentException("Missing champion power profile: " + id); return value; }
    public List<String> warnings() { return warnings; }

    private record RawCatalog(String profileVersion, String requiredChampionPoolVersion, Map<String, Map<String, Double>> levelCurves,
            Map<String, Map<ItemProgressStage, Double>> itemCurves, List<RawProfile> championProfiles) { }
    private record RawProfile(String championId, String levelCurveId, String itemCurveId,
            Map<ProgressionCombatContext, Double> contexts, Set<ChampionTag> tags, Map<String, Double> levelAnchors,
            Map<ItemProgressStage, Double> itemAnchors) { }
}

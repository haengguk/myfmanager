package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ChampionCatalog {
    private final String championPoolVersion;
    private final String championBalanceVersion;
    private final String riotDataVersion;
    private final List<ChampionDefinition> champions;
    private final Map<ChampionId, ChampionDefinition> byId;
    private final Map<Position, List<ChampionDefinition>> byPosition;
    private final ChampionSelectionRequest defaultSelection;

    @org.springframework.beans.factory.annotation.Autowired
    public ChampionCatalog(ObjectMapper objectMapper) {
        this(objectMapper, activeCatalogResource(objectMapper));
    }

    public ChampionCatalog(ObjectMapper objectMapper, InputStream input) {
        RawCatalog raw;
        try (input) {
            if (input == null) throw new IllegalStateException("Champion catalog resource not found");
            raw = objectMapper.readValue(input, RawCatalog.class);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load champion catalog", error);
        }
        championPoolVersion = required(raw.championPoolVersion(), "championPoolVersion");
        championBalanceVersion = required(raw.championBalanceVersion(), "championBalanceVersion");
        riotDataVersion = required(raw.riotDataVersion(), "riotDataVersion");
        defaultSelection = raw.defaultSelection();

        List<ChampionDefinition> ordered = new ArrayList<>();
        LinkedHashMap<ChampionId, ChampionDefinition> indexed = new LinkedHashMap<>();
        EnumMap<Position, List<ChampionDefinition>> grouped = new EnumMap<>(Position.class);
        for (Position position : Position.values()) grouped.put(position, new ArrayList<>());
        Set<String> riotIds = new HashSet<>();
        if (raw.champions() == null || raw.champions().isEmpty()) throw new IllegalStateException("Champion catalog must not be empty");
        for (RawChampion item : raw.champions()) {
            ChampionId id = new ChampionId(item.id());
            if (item.supportedPositions() == null || item.supportedPositions().isEmpty()) throw new IllegalStateException("supportedPositions must not be empty: " + id);
            if (new HashSet<>(item.supportedPositions()).size() != item.supportedPositions().size()) throw new IllegalStateException("Duplicate supported position: " + id);
            String portrait = "https://ddragon.leagueoflegends.com/cdn/" + riotDataVersion
                    + "/img/champion/" + item.riotAssetId() + ".png";
            ChampionDefinition definition = new ChampionDefinition(id, item.displayNameKo(), item.displayNameEn(),
                    item.riotAssetId(), item.primaryPosition(), Set.copyOf(item.supportedPositions()), portrait,
                    championPoolVersion, riotDataVersion);
            if (indexed.put(id, definition) != null) throw new IllegalStateException("Duplicate ChampionId: " + id);
            if (!riotIds.add(definition.riotAssetId())) throw new IllegalStateException("Duplicate riotAssetId: " + definition.riotAssetId());
            if (!definition.supportedPositions().contains(definition.primaryPosition())) throw new IllegalStateException("primaryPosition must be supported: " + id);
            ordered.add(definition);
            definition.supportedPositions().forEach(position -> grouped.get(position).add(definition));
        }
        for (Position position : Position.values()) {
            grouped.put(position, List.copyOf(grouped.get(position)));
        }
        champions = List.copyOf(ordered);
        byId = Map.copyOf(indexed);
        byPosition = Map.copyOf(grouped);
        if (defaultSelection != null) new ChampionSelectionValidator(this).validate(defaultSelection, ChampionSelectionMode.DEFAULT_FIXED);
    }

    private static InputStream activeCatalogResource(ObjectMapper mapper) {
        ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
        InputStream input = ChampionResourceManifest.open(manifest.catalog());
        if (input == null) throw new IllegalStateException("Champion catalog resource not found: " + manifest.catalog());
        return input;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + field);
        return value;
    }
    public String championPoolVersion() { return championPoolVersion; }
    public String championBalanceVersion() { return championBalanceVersion; }
    public String riotDataVersion() { return riotDataVersion; }
    public List<ChampionDefinition> all() { return champions; }
    public List<ChampionDefinition> forPosition(Position position) { return byPosition.getOrDefault(position, List.of()); }
    public Optional<ChampionDefinition> find(ChampionId id) { return Optional.ofNullable(byId.get(id)); }
    public ChampionDefinition get(ChampionId id) { return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown ChampionId: " + id)); }
    public ChampionSelectionRequest defaultSelection() { return defaultSelection; }
    public Set<ChampionRoleKey> legalRoleKeys() {
        java.util.LinkedHashSet<ChampionRoleKey> keys = new java.util.LinkedHashSet<>();
        for (ChampionDefinition champion : champions) champion.supportedPositions().stream().sorted().forEach(position -> keys.add(new ChampionRoleKey(champion.id(), position)));
        return java.util.Collections.unmodifiableSet(keys);
    }
    public boolean supports(ChampionRoleKey key) { ChampionDefinition champion = byId.get(key.championId()); return champion != null && champion.supportedPositions().contains(key.position()); }

    private record RawCatalog(String championPoolVersion, String championBalanceVersion, String riotDataVersion,
                              ChampionSelectionRequest defaultSelection, List<RawChampion> champions) { }
    private record RawChampion(String id, String displayNameKo, String displayNameEn, String riotAssetId,
                               Position primaryPosition, List<Position> supportedPositions) { }
}

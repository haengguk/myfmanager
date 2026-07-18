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
    private static final String RESOURCE = "/champions/champion-pool-initial-30-v1.json";
    private final String championPoolVersion;
    private final String championBalanceVersion;
    private final String riotDataVersion;
    private final List<ChampionDefinition> champions;
    private final Map<ChampionId, ChampionDefinition> byId;
    private final Map<Position, List<ChampionDefinition>> byPosition;
    private final ChampionSelectionRequest defaultSelection;

    public ChampionCatalog(ObjectMapper objectMapper) {
        RawCatalog raw;
        try (InputStream input = ChampionCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Champion catalog resource not found: " + RESOURCE);
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
        for (RawChampion item : raw.champions()) {
            ChampionId id = new ChampionId(item.id());
            String portrait = "https://ddragon.leagueoflegends.com/cdn/" + riotDataVersion
                    + "/img/champion/" + item.riotAssetId() + ".png";
            ChampionDefinition definition = new ChampionDefinition(id, item.displayNameKo(), item.displayNameEn(),
                    item.riotAssetId(), item.primaryPosition(), item.supportedPositions(), portrait,
                    championPoolVersion, riotDataVersion);
            if (indexed.put(id, definition) != null) throw new IllegalStateException("Duplicate ChampionId: " + id);
            if (!riotIds.add(definition.riotAssetId())) throw new IllegalStateException("Duplicate riotAssetId: " + definition.riotAssetId());
            if (!definition.supportedPositions().contains(definition.primaryPosition()) || definition.supportedPositions().size() != 1) {
                throw new IllegalStateException("13A requires exactly the primary supported position: " + id);
            }
            ordered.add(definition);
            grouped.get(definition.primaryPosition()).add(definition);
        }
        if (ordered.size() != 30) throw new IllegalStateException("Expected exactly 30 champions, found " + ordered.size());
        for (Position position : Position.values()) {
            if (grouped.get(position).size() != 6) throw new IllegalStateException("Expected 6 " + position + " champions");
            grouped.put(position, List.copyOf(grouped.get(position)));
        }
        champions = List.copyOf(ordered);
        byId = Map.copyOf(indexed);
        byPosition = Map.copyOf(grouped);
        new ChampionSelectionValidator(this).validate(defaultSelection, ChampionSelectionMode.DEFAULT_FIXED);
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

    private record RawCatalog(String championPoolVersion, String championBalanceVersion, String riotDataVersion,
                              ChampionSelectionRequest defaultSelection, List<RawChampion> champions) { }
    private record RawChampion(String id, String displayNameKo, String displayNameEn, String riotAssetId,
                               Position primaryPosition, Set<Position> supportedPositions) { }
}

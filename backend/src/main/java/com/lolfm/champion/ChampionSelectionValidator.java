package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChampionSelectionValidator {
    private final ChampionCatalog catalog;

    public ChampionSelectionValidator(ChampionCatalog catalog) { this.catalog = catalog; }

    public MatchChampionAssignments resolve(ChampionSelectionRequest request) {
        return request == null
                ? validate(catalog.defaultSelection(), ChampionSelectionMode.DEFAULT_FIXED)
                : validate(request, ChampionSelectionMode.EXPLICIT);
    }

    public MatchChampionAssignments validate(ChampionSelectionRequest request, ChampionSelectionMode mode) {
        if (request == null || request.blue() == null || request.red() == null) {
            throw failure("CHAMPION_SELECTION_MISSING", request == null || request.blue() == null ? "championSelection.blue" : "championSelection.red", null,
                    "Both blue and red champion lineups are required");
        }
        List<ChampionAssignment> assignments = new ArrayList<>(10);
        Set<ChampionId> selected = new HashSet<>();
        addLineup(TeamSide.BLUE, request.blue(), assignments, selected);
        addLineup(TeamSide.RED, request.red(), assignments, selected);
        return new MatchChampionAssignments(assignments, mode);
    }

    private void addLineup(TeamSide side, ChampionLineupRequest lineup, List<ChampionAssignment> assignments,
                           Set<ChampionId> selected) {
        add(side, Position.TOP, "top", lineup.top(), assignments, selected);
        add(side, Position.JUNGLE, "jgl", lineup.jgl(), assignments, selected);
        add(side, Position.MID, "mid", lineup.mid(), assignments, selected);
        add(side, Position.ADC, "adc", lineup.adc(), assignments, selected);
        add(side, Position.SUPPORT, "sup", lineup.sup(), assignments, selected);
    }

    private void add(TeamSide side, Position position, String requestField, String rawId,
                     List<ChampionAssignment> assignments, Set<ChampionId> selected) {
        String field = "championSelection." + side.name().toLowerCase() + "." + requestField;
        if (rawId == null || rawId.isBlank()) {
            throw failure("CHAMPION_POSITION_MISSING", field, rawId, "A champion is required for " + side + " " + position);
        }
        ChampionId id;
        try { id = new ChampionId(rawId); }
        catch (IllegalArgumentException error) { throw failure("INVALID_CHAMPION_SELECTION", field, rawId, error.getMessage()); }
        ChampionDefinition definition = catalog.find(id).orElseThrow(() ->
                failure("UNKNOWN_CHAMPION", field, id.value(), "Unknown champion: " + id));
        if (!selected.add(id)) {
            throw failure("DUPLICATE_CHAMPION", field, id.value(), id + " is already selected in this match");
        }
        if (!definition.supportedPositions().contains(position)) {
            throw failure("CHAMPION_POSITION_MISMATCH", field, id.value(), id + " cannot be selected for " + position);
        }
        PlayerKey key = new PlayerKey(side, position);
        assignments.add(new ChampionAssignment(key, id, position));
    }

    private ChampionSelectionException failure(String code, String field, String championId, String message) {
        return new ChampionSelectionException(code, field, championId, message);
    }
}

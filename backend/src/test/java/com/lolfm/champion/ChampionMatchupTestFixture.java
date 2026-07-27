package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.simulator.GameState;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TeamState;
import java.util.ArrayList;
import java.util.List;

public final class ChampionMatchupTestFixture {
    final ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
    final TeamState blue = team("BLUE", TeamSide.BLUE);
    final TeamState red = team("RED", TeamSide.RED);
    final GameState state;

    public ChampionMatchupTestFixture(ChampionMatchupMode mode, boolean testCatalog) {
        ChampionSelectionRequest request = new ChampionSelectionRequest(
                new ChampionLineupRequest(
                        "renekton", "lee-sin", "leblanc", "lucian", "nautilus"),
                new ChampionLineupRequest(
                        "jax", "viego", "viktor", "jinx", "lulu"));
        state = new GameState(
                blue, red, true, true, true, true, true, true,
                new ChampionSelectionValidator(champions).resolve(request));
        state.configureChampionPower(
                new ChampionPowerProfileCatalog(new ObjectMapper(), champions), true);
        state.configureChampionMatchup(
                testCatalog
                        ? ChampionMatchupTestCatalogFactory.focused(champions)
                        : ChampionMatchupCatalog.neutral(champions),
                mode);
        state.configureProgression(true, true);
    }

    public PlayerState blue(Position position) { return blue.playerAt(position); }
    public PlayerState red(Position position) { return red.playerAt(position); }
    public TeamState blueTeam() { return blue; }
    public TeamState redTeam() { return red; }
    public GameState state() { return state; }
    public ChampionCatalog champions() { return champions; }

    private static TeamState team(String name, TeamSide side) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new PlayerState(
                    side + "-" + position,
                    position,
                    new PlayerAttributes(15, 15, 15, 15),
                    500));
        }
        return new TeamState(name, players);
    }
}

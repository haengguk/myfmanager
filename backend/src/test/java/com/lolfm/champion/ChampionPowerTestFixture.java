package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.simulator.*;
import java.util.ArrayList;
import java.util.List;

final class ChampionPowerTestFixture {
    final ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
    final ChampionPowerProfileCatalog profiles = new ChampionPowerProfileCatalog(new ObjectMapper(), champions);
    final TeamState blue = team("BLUE", TeamSide.BLUE);
    final TeamState red = team("RED", TeamSide.RED);
    final GameState state;
    ChampionPowerTestFixture(boolean enabled) {
        var request = new ChampionSelectionRequest(
                new ChampionLineupRequest("renekton","sejuani","azir","lucian","nautilus"),
                new ChampionLineupRequest("jax","lee-sin","ahri","jinx","rakan"));
        state = new GameState(blue, red, true, true, true, true, true, true,
                new ChampionSelectionValidator(champions).resolve(request));
        state.configureChampionPower(profiles, enabled); state.configureProgression(true, true);
    }
    static TeamState team(String name, TeamSide side) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(new PlayerState(side + "-" + position, position,
                new PlayerAttributes(15,15,15,15), 500));
        return new TeamState(name, players);
    }
    static void grow(PlayerState player, int xp, int gold) {
        player.getProgressionState().awardExperience(ExperienceSource.KILL, xp, 1);
        player.getProgressionState().awardEarnedGold(gold, GoldSource.FARM, 1);
    }
}

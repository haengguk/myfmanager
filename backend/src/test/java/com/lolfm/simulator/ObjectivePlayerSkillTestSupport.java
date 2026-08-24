package com.lolfm.simulator;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import java.util.List;

final class ObjectivePlayerSkillTestSupport {
    private ObjectivePlayerSkillTestSupport() { }

    static GameState detailedDragonState() {
        return dragonState(
                team("BLUE", TeamSide.BLUE, true, 14, 14, true, 14, 14),
                team("RED", TeamSide.RED, true, 14, 14, true, 14, 14));
    }

    static GameState dragonState(TeamState blue, TeamState red) {
        GameState state = new GameState(blue, red, true, true, true, true, true);
        state.advanceTimeSeconds(300);
        new ObjectiveResolver().updateSpawnState(state);
        state.advanceTimeSeconds(40);
        return state;
    }

    static TeamState team(String name, TeamSide side,
                          boolean detailedJungler, int objectiveDecision, int objectiveSecure,
                          boolean detailedSupport, int areaSetup, int visionControl) {
        return new TeamState(name, List.of(
                legacyPlayer(name + "-TOP", Position.TOP),
                detailedJungler
                        ? jungler(name + "-JUNGLE", side, objectiveDecision, objectiveSecure)
                        : legacyPlayer(name + "-JUNGLE", Position.JUNGLE),
                legacyPlayer(name + "-MID", Position.MID),
                legacyPlayer(name + "-ADC", Position.ADC),
                detailedSupport
                        ? support(name + "-SUPPORT", side, areaSetup, visionControl)
                        : legacyPlayer(name + "-SUPPORT", Position.SUPPORT)
        ));
    }

    private static PlayerState jungler(String name, TeamSide side,
                                       int objectiveDecision, int objectiveSecure) {
        PlayerRatings ratings = PlayerRatings.neutral(Position.JUNGLE)
                .with(PlayerSkill.CONSISTENCY, 20)
                .with(PlayerSkill.OBJECTIVE_DECISION, objectiveDecision)
                .with(PlayerSkill.OBJECTIVE_SECURE, objectiveSecure);
        return detailedPlayer(name, Position.JUNGLE, side, ratings);
    }

    private static PlayerState support(String name, TeamSide side,
                                       int areaSetup, int visionControl) {
        PlayerRatings ratings = PlayerRatings.neutral(Position.SUPPORT)
                .with(PlayerSkill.CONSISTENCY, 20)
                .with(PlayerSkill.AREA_SETUP, areaSetup)
                .with(PlayerSkill.VISION_CONTROL, visionControl);
        return detailedPlayer(name, Position.SUPPORT, side, ratings);
    }

    private static PlayerState detailedPlayer(String name, Position position, TeamSide side,
                                               PlayerRatings ratings) {
        PlayerMatchPerformance performance = PlayerMatchPerformance.realize(ratings, 14, 71L, side);
        return new PlayerState(new PlayerKey(side, position),
                new PlayerId("player-objective-" + side.name().toLowerCase() + "-"
                        + position.name().toLowerCase()),
                name, position, new PlayerAttributes(14, 14, 14, 14),
                performance, 500, true);
    }

    private static PlayerState legacyPlayer(String name, Position position) {
        return new PlayerState(name, position, new PlayerAttributes(14, 14, 14, 14), 500);
    }
}

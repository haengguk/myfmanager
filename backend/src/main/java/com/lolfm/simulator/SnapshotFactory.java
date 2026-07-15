package com.lolfm.simulator;

import com.lolfm.domain.LanePhaseLaneSnapshot;
import com.lolfm.domain.LanePhaseSnapshot;
import com.lolfm.domain.LaneSnapshot;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MidGameMacroSnapshot;
import com.lolfm.domain.OuterTurretSnapshot;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.TeamMacroSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SnapshotFactory {

    public MatchSnapshot create(GameState gameState) {
        int currentTime = gameState.getCurrentTimeSeconds();
        TeamState blue = gameState.getBlueTeamState();
        TeamState red = gameState.getRedTeamState();
        List<PlayerSnapshot> playerSnapshots = new ArrayList<>();
        int blueAlivePlayers = addTeamSnapshots(blue, red, TeamSide.BLUE, currentTime, playerSnapshots);
        int redAlivePlayers = addTeamSnapshots(red, blue, TeamSide.RED, currentTime, playerSnapshots);

        return new MatchSnapshot(
                currentTime,
                blue.getKills(),
                red.getKills(),
                blue.getGold(),
                red.getGold(),
                blue.getDragons(),
                red.getDragons(),
                gameState.getObjectiveState().isSoulOwner(TeamSide.BLUE),
                gameState.getObjectiveState().isSoulOwner(TeamSide.RED),
                blue.hasActiveBaronBuff(currentTime),
                red.hasActiveBaronBuff(currentTime),
                gameState.getObjectiveState().isElderAlive(),
                hasElder(blue, currentTime),
                hasElder(red, currentTime),
                elderRemaining(blue, currentTime),
                elderRemaining(red, currentTime),
                blue.getTowersDestroyed(),
                red.getTowersDestroyed(),
                gameState.getMapState().getAliveInhibitorCount(TeamSide.BLUE),
                gameState.getMapState().getAliveInhibitorCount(TeamSide.RED),
                gameState.getMapState().getBaseState(TeamSide.BLUE).getNexusTurretsRemaining(),
                gameState.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining(),
                gameState.getMapState().getBaseState(TeamSide.BLUE).isNexusAlive(),
                gameState.getMapState().getBaseState(TeamSide.RED).isNexusAlive(),
                blueAlivePlayers,
                redAlivePlayers,
                playerSnapshots,
                laneSnapshots(gameState),
                new ObjectivePriorityResolver().snapshot(gameState),
                lanePhaseSnapshot(gameState),
                midGameMacroSnapshot(gameState)
        );
    }

    private LanePhaseSnapshot lanePhaseSnapshot(GameState gameState) {
        LanePhaseState phases = gameState.getLanePhaseState();
        List<LanePhaseLaneSnapshot> lanes = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            LaneStructureState blue = gameState.getMapState().getLaneState(TeamSide.BLUE, lane);
            LaneStructureState red = gameState.getMapState().getLaneState(TeamSide.RED, lane);
            lanes.add(new LanePhaseLaneSnapshot(lane, phases.getLanePhase(lane), gameState.laneState(lane).getPressure(),
                    phases.isLaning(lane),
                    new OuterTurretSnapshot(blue.isOuterTowerAlive(), blue.getOuterRemainingIntegrity(), blue.getOuterDestroyedAtSeconds()),
                    new OuterTurretSnapshot(red.isOuterTowerAlive(), red.getOuterRemainingIntegrity(), red.getOuterDestroyedAtSeconds())));
        }
        return new LanePhaseSnapshot(phases.isEnabled(), phases.getMatchPhase(), phases.getMidGameStartedAtSeconds(),
                phases.getTransitionReason(), lanes);
    }

    private MidGameMacroSnapshot midGameMacroSnapshot(GameState gameState) {
        MidGameMacroState macro = gameState.getMidGameMacroState();
        if (!macro.isEnabled()) {
            return MidGameMacroSnapshot.disabled(gameState.getCurrentTimeSeconds(),
                    gameState.getLanePhaseState().getMatchPhase());
        }
        ObjectivePriorityResolver priority = new ObjectivePriorityResolver();
        return new MidGameMacroSnapshot(true, gameState.getLanePhaseState().getMatchPhase(),
                gameState.getCurrentTimeSeconds(), teamMacroSnapshot(macro.teamState(TeamSide.BLUE)),
                teamMacroSnapshot(macro.teamState(TeamSide.RED)), priority.dragonMacroSetupControl(gameState),
                priority.baronMacroSetupControl(gameState), macro.getEvaluationHistory(), macro.isMatchEnded());
    }

    private TeamMacroSnapshot teamMacroSnapshot(TeamMacroTeamState state) {
        return new TeamMacroSnapshot(state.getCurrentPlan(), state.getTargetLane(), state.getTargetObjective(),
                state.getStartedAtSeconds(), state.getActiveUntilSeconds(), state.getNextEvaluationAtSeconds(),
                state.getAssignedPositions(), state.getLastActionResult(), state.getLastDestroyedStructure(),
                state.getLastDestroyedTowerTier(), state.getLastStructureLane(), state.getLastSelectedPlan(),
                state.getStatus(), state.getEndReason(), state.getLastEvaluationDueAtSeconds(),
                state.getLastEvaluationAtSeconds(), state.getLastEvaluationSkippedReason(),
                state.getLastSelectionRandomConsumptionCount());
    }

    private List<LaneSnapshot> laneSnapshots(GameState gameState) {
        List<LaneSnapshot> snapshots = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            LaneState state = gameState.laneState(lane);
            snapshots.add(new LaneSnapshot(lane, state.getPressure(), state.getPriority()));
        }
        return snapshots;
    }

    private boolean hasElder(TeamState team, int time) { return elderRemaining(team, time) > 0; }

    private int elderRemaining(TeamState team, int time) {
        int max = 0;
        for (PlayerState player : team.getPlayers()) {
            max = Math.max(max, player.getElderBuffRemainingSeconds(time));
        }
        return max;
    }

    private int addTeamSnapshots(TeamState teamState, TeamState opposingTeam, TeamSide teamSide,
                                 int currentTime, List<PlayerSnapshot> playerSnapshots) {
        int alivePlayers = 0;
        for (PlayerState playerState : teamState.getPlayers()) {
            boolean alive = playerState.isAlive(currentTime);
            int respawnRemainingSeconds = alive ? 0 : Math.max(0, playerState.getRespawnAtSeconds() - currentTime);
            if (alive) alivePlayers++;
            int shutdownBountyGold = alive
                    ? BountyService.displayedShutdownGold(playerState, teamState, opposingTeam, currentTime) : 0;
            playerState.setLastVisibleShutdownGold(shutdownBountyGold);
            playerSnapshots.add(new PlayerSnapshot(
                    playerState.getPlayerName(), teamState.getTeamName(), teamSide, playerState.getPosition(),
                    playerState.getKills(), playerState.getDeaths(), playerState.getAssists(), playerState.getCs(),
                    playerState.getGold(), alive, playerState.getRespawnAtSeconds(), respawnRemainingSeconds,
                    playerState.canFarmAt(currentTime) && currentTime >= playerState.getRoamActionState().getRoamFarmBlockedUntilSeconds(),
                    playerState.getFarmResumeAtSeconds(), playerState.farmReturnSecondsRemaining(currentTime),
                    playerState.hasActiveElderBuff(currentTime), playerState.getElderBuffRemainingSeconds(currentTime),
                    shutdownBountyGold, shutdownBountyGold >= BountyRuleConfig.MIN_VISIBLE_SHUTDOWN_GOLD,
                    playerState.getTotalShutdownGoldEarned(), playerState.getTotalShutdownGoldGiven(),
                    playerState.getBountyProgress(), playerState.getActivityState().getActivityType(),
                    playerState.getActivityState().getOriginLane(), playerState.getActivityState().getTargetLane(),
                    playerState.getActivityState().getActivityUntilSeconds(),
                    Math.max(0, playerState.getActivityState().getActivityUntilSeconds() - currentTime),
                    playerState.getRoamActionState().getRoamFarmBlockedUntilSeconds()
            ));
        }
        return alivePlayers;
    }
}

package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ObjectiveResolver {

    public void updateSpawnState(GameState gameState) {
        gameState.getObjectiveState().updateSpawnState(gameState.getCurrentTimeSeconds());
    }

    public Optional<MatchEvent> captureDragon(GameState gameState, TeamSide side, int currentTimeSeconds) {
        return captureDragon(gameState, side, currentTimeSeconds, DragonCaptureSource.POST_FIGHT, "");
    }

    public Optional<MatchEvent> captureDragon(
            GameState gameState, TeamSide side, int currentTimeSeconds, String ignoredResultMessage
    ) {
        return captureDragon(gameState, side, currentTimeSeconds, DragonCaptureSource.GENERAL, ignoredResultMessage);
    }

    public Optional<MatchEvent> captureDragon(
            GameState gameState,
            TeamSide side,
            int currentTimeSeconds,
            DragonCaptureSource source,
            String ignoredResultMessage
    ) {
        ObjectiveState objectives = gameState.getObjectiveState();
        int spawnedAtSeconds = objectives.getDragonSpawnedAtSeconds();
        if (!objectives.captureDragon(side, currentTimeSeconds)) return Optional.empty();
        gameState.recordDragonCapture(side, source, currentTimeSeconds, currentTimeSeconds - spawnedAtSeconds);

        TeamState capturingTeam = gameState.getTeamState(side);
        TeamState opposingTeam = gameState.getTeamState(side.opposite());
        capturingTeam.addDragon();
        awardTeamGold(capturingTeam, ObjectiveRuleConfig.DRAGON_GOLD_PER_PLAYER);

        int dragonCount = capturingTeam.getDragons();
        boolean soulClaimed = dragonCount == 4;
        if (soulClaimed) {
            objectives.claimSoul(side, currentTimeSeconds);
            capturingTeam.setHasDragonSoul(true);
            opposingTeam.setHasDragonSoul(false);
        } else {
            objectives.scheduleNextDragonSpawn(currentTimeSeconds);
        }

        return Optional.of(new MatchEvent(
                currentTimeSeconds,
                MatchEventType.DRAGON,
                dragonMessage(capturingTeam.getTeamName(), dragonCount),
                null,
                null,
                List.of()
        ));
    }

    public Optional<MatchEvent> captureBaron(GameState gameState, TeamSide side, int currentTimeSeconds) {
        return captureBaron(gameState, side, currentTimeSeconds, "한타 대승 이후 바론을 확보합니다.");
    }

    public Optional<MatchEvent> captureBaron(
            GameState gameState, TeamSide side, int currentTimeSeconds, String resultMessage
    ) {
        if (!gameState.getObjectiveState().captureBaron(side, currentTimeSeconds)) return Optional.empty();
        TeamState winningTeam = gameState.getTeamState(side);
        gameState.getTeamState(side.opposite()).setHasBaronBuff(false);
        winningTeam.grantBaronBuff(currentTimeSeconds, 180);
        awardTeamGold(winningTeam, ObjectiveRuleConfig.BARON_GOLD_PER_PLAYER);
        return Optional.of(new MatchEvent(
                currentTimeSeconds, MatchEventType.BARON, winningTeam.getTeamName() + "가 " + resultMessage,
                null, null, List.of()
        ));
    }

    public Optional<ElderCaptureOutcome> captureElder(GameState gameState, TeamSide side, int currentTimeSeconds, String message) {
        ObjectiveState objectives = gameState.getObjectiveState();
        if (gameState.isFinished() || !objectives.captureElder(side, currentTimeSeconds)) return Optional.empty();
        TeamState team = gameState.getTeamState(side);
        java.util.List<String> buffed = new java.util.ArrayList<>();
        for (PlayerState player : team.getPlayers()) {
            if (player.isAlive(currentTimeSeconds)) { player.grantElderBuff(currentTimeSeconds, ElderRuleConfig.ELDER_BUFF_DURATION_SECONDS); buffed.add(player.getPlayerName()); }
        }
        MatchEvent event = new MatchEvent(currentTimeSeconds, MatchEventType.ELDER, team.getTeamName() + "가 " + message, null, null, java.util.List.of());
        return Optional.of(new ElderCaptureOutcome(side, currentTimeSeconds, objectives.getNextElderSpawnSeconds(), java.util.List.copyOf(buffed), event));
    }

    private String dragonMessage(String teamName, int dragonCount) {
        return switch (dragonCount) {
            case 1 -> teamName + "가 드래곤을 확보합니다. 현재 드래곤 1스택입니다.";
            case 2 -> teamName + "가 두 번째 드래곤을 처치합니다.";
            case 3 -> teamName + "가 드래곤 3스택을 완성합니다.";
            case 4 -> teamName + "가 네 번째 드래곤을 처치하고 드래곤의 영혼을 획득합니다.";
            default -> throw new IllegalStateException("Elemental dragon count exceeded: " + dragonCount);
        };
    }

    private void awardTeamGold(TeamState teamState, int goldPerPlayer) {
        for (PlayerState playerState : teamState.getPlayers()) playerState.addGold(goldPerPlayer);
        teamState.addGold(goldPerPlayer * teamState.getPlayers().size());
    }
}

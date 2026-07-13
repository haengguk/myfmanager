package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class ObjectiveAttemptResolver {

    public Optional<MatchEvent> maybeAttemptObjective(
            GameState gameState, Random random, ObjectiveResolver objectiveResolver
    ) {
        ObjectiveState objectives = gameState.getObjectiveState();
        int currentTime = gameState.getCurrentTimeSeconds();
        if (objectives.isElderAttemptDue(currentTime)) {
            objectives.markElderAttempted(currentTime);
            Optional<MatchEvent> elder = maybeAttemptElder(gameState, random, objectiveResolver);
            if (elder.isPresent()) return elder;
        }
        if (objectives.isElementalDragonPhase() && objectives.isDragonAttemptDue(currentTime)) {
            objectives.markDragonAttempted(currentTime);
            gameState.recordGeneralDragonAttempt();
            Optional<MatchEvent> dragon = maybeAttemptDragon(gameState, random, objectiveResolver);
            if (dragon.isPresent()) return dragon;
        }
        if (objectives.isBaronAttemptDue(currentTime)) {
            objectives.markBaronAttempted(currentTime);
            return maybeAttemptBaron(gameState, random, objectiveResolver);
        }
        return Optional.empty();
    }

    private Optional<MatchEvent> maybeAttemptElder(GameState state, Random random, ObjectiveResolver resolver) {
        int time = state.getCurrentTimeSeconds();
        List<TeamSide> eligible = eligibleSides(state, time, 4);
        if (eligible.isEmpty()) return Optional.empty();
        int aliveFor = Math.max(0, time - state.getObjectiveState().getElderSpawnedAtSeconds());
        double chance = ElderRuleConfig.GENERAL_CAPTURE_BASE_CHANCE + (aliveFor >= 60 ? .08 : 0) + (aliveFor >= 120 ? .12 : 0) + (aliveFor >= 180 ? .15 : 0);
        if (random.nextDouble() >= chance) return Optional.empty();
        TeamSide winner = chooseWinner(state, eligible, random);
        return resolver.captureElder(state, winner, time, random.nextBoolean() ? "장로 드래곤을 확보합니다." : "시야 주도권을 바탕으로 장로 드래곤을 처치합니다.").map(ElderCaptureOutcome::event);
    }

    private Optional<MatchEvent> maybeAttemptDragon(GameState state, Random random, ObjectiveResolver resolver) {
        int currentTime = state.getCurrentTimeSeconds();
        List<TeamSide> eligible = eligibleSides(state, currentTime, 3);
        if (eligible.isEmpty()) return Optional.empty();
        ObjectiveState objectives = state.getObjectiveState();
        int aliveFor = Math.max(0, currentTime - objectives.getDragonSpawnedAtSeconds());
        double chance = ObjectiveRuleConfig.DRAGON_GENERAL_BASE_CAPTURE_CHANCE;
        if (aliveFor >= 180) chance += ObjectiveRuleConfig.DRAGON_CAPTURE_CHANCE_AFTER_180_SECONDS;
        if (aliveFor >= 300) chance += ObjectiveRuleConfig.DRAGON_CAPTURE_CHANCE_AFTER_300_SECONDS;
        if (random.nextDouble() >= chance) return Optional.empty();
        TeamSide winner = chooseWinner(state, eligible, random);
        String message = random.nextBoolean()
                ? "시야와 인원 우위를 바탕으로 드래곤을 확보합니다."
                : "상대보다 먼저 드래곤 지역을 장악합니다.";
        return resolver.captureDragon(state, winner, currentTime, DragonCaptureSource.GENERAL, message);
    }

    private Optional<MatchEvent> maybeAttemptBaron(GameState state, Random random, ObjectiveResolver resolver) {
        int currentTime = state.getCurrentTimeSeconds();
        List<TeamSide> eligible = eligibleSides(state, currentTime, 4);
        if (eligible.isEmpty()) return Optional.empty();
        ObjectiveState objectives = state.getObjectiveState();
        int aliveFor = Math.max(0, currentTime - objectives.getBaronSpawnedAtSeconds());
        double chance = currentTime >= 2_100 ? 0.14 : currentTime >= 1_800 ? 0.08 : 0.04;
        chance += Math.min(0.06, Math.max(0, aliveFor - 240) / 2_000.0);
        if (random.nextDouble() >= chance) return Optional.empty();
        TeamSide winner = chooseWinner(state, eligible, random);
        String message = random.nextBoolean()
                ? "상대의 빈틈을 노려 바론을 확보합니다."
                : "시야 주도권을 바탕으로 바론을 처치합니다.";
        return resolver.captureBaron(state, winner, currentTime, message);
    }

    private List<TeamSide> eligibleSides(GameState state, int currentTime, int minimumAlivePlayers) {
        List<TeamSide> eligible = new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            if (countAlivePlayers(state.getTeamState(side), currentTime) >= minimumAlivePlayers) eligible.add(side);
        }
        return eligible;
    }

    private TeamSide chooseWinner(GameState state, List<TeamSide> eligible, Random random) {
        if (eligible.size() == 1) return eligible.get(0);
        double blueWeight = objectiveWeight(state, TeamSide.BLUE);
        double redWeight = objectiveWeight(state, TeamSide.RED);
        return random.nextDouble() < blueWeight / (blueWeight + redWeight) ? TeamSide.BLUE : TeamSide.RED;
    }

    private double objectiveWeight(GameState state, TeamSide side) {
        TeamState team = state.getTeamState(side);
        int alive = countAlivePlayers(team, state.getCurrentTimeSeconds());
        String teamName = team.getTeamName();
        double weight = alive * 160.0 + team.getGold() / 90.0 + team.getKills() * 65.0;
        if (state.hasRecentBigWin(teamName, 120)) weight += 450.0;
        if (state.hasRecentAce(teamName, 120)) weight += 800.0;
        return weight;
    }

    private int countAlivePlayers(TeamState team, int currentTime) {
        int count = 0;
        for (PlayerState player : team.getPlayers()) if (player.isAlive(currentTime)) count++;
        return count;
    }
}

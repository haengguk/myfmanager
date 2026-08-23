package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.ObjectiveDecisionData;
import com.lolfm.domain.ObjectivePriorityDecisionData;
import com.lolfm.domain.ObjectiveSelectionWeightBreakdown;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class PostFightResolver {

    public Optional<MatchEvent> resolve(
            GameState gameState,
            TeamfightOutcome outcome,
            Random random,
            ObjectiveResolver objectiveResolver
    ) {
        if (outcome.grade() == FightGrade.SMALL_WIN) {
            return Optional.empty();
        }

        int currentTime = outcome.endedAtSeconds();
        TeamState winningTeam = gameState.getTeamState(outcome.winningSide());
        TeamState losingTeam = gameState.getTeamState(outcome.winningSide().opposite());
        int winningSurvivors = countAlivePlayers(winningTeam, currentTime);
        if (winningSurvivors < 2) {
            return Optional.empty();
        }

        ObjectiveState objectives = gameState.getObjectiveState();
        boolean elderAvailable = objectives.isElderAlive();
        boolean dragonAvailable = objectives.isElementalDragonPhase() && objectives.isDragonAlive();
        boolean baronAvailable = currentTime >= ObjectiveRuleConfig.FIRST_BARON_SPAWN_SECONDS && objectives.isBaronAlive();
        if (!elderAvailable && !dragonAvailable && !baronAvailable) {
            return Optional.empty();
        }

        int longestEnemyRespawn = longestRemainingRespawn(losingTeam, currentTime);
        double captureChance = captureChance(outcome.grade(), winningSurvivors, longestEnemyRespawn);
        if (random.nextDouble() >= captureChance) {
            return Optional.empty();
        }

        if (elderAvailable && (outcome.grade() == FightGrade.ACE || outcome.grade() == FightGrade.BIG_WIN) && random.nextDouble() < 0.78) {
            return objectiveResolver.captureElder(gameState, outcome.winningSide(), currentTime, "한타 대승 이후 장로 드래곤을 확보합니다.")
                    .map(ElderCaptureOutcome::event)
                    .map(event -> attachDecision(gameState, event, ObjectiveType.ELDER, currentTime, outcome));
        }
        if (baronAvailable && shouldChooseBaron(outcome.grade(), dragonAvailable, winningTeam.getDragons(), random)) {
            if (elderAvailable) return objectiveResolver.captureElder(gameState, outcome.winningSide(), currentTime, "상대의 긴 부활 시간을 활용해 장로 드래곤을 처치합니다.")
                    .map(ElderCaptureOutcome::event)
                    .map(event -> attachDecision(gameState, event, ObjectiveType.ELDER, currentTime, outcome));
            return objectiveResolver.captureBaron(gameState, outcome.winningSide(), currentTime)
                    .map(event -> attachDecision(gameState, event, ObjectiveType.BARON, currentTime, outcome));
        }
        if (dragonAvailable) {
            return objectiveResolver.captureDragon(gameState, outcome.winningSide(), currentTime, DragonCaptureSource.POST_FIGHT, "")
                    .map(event -> attachDecision(gameState, event, ObjectiveType.DRAGON, currentTime, outcome));
        }
        if (elderAvailable) return objectiveResolver.captureElder(gameState, outcome.winningSide(), currentTime, "상대의 긴 부활 시간을 활용해 장로 드래곤을 처치합니다.")
                .map(ElderCaptureOutcome::event)
                .map(event -> attachDecision(gameState, event, ObjectiveType.ELDER, currentTime, outcome));
        return objectiveResolver.captureBaron(gameState, outcome.winningSide(), currentTime)
                .map(event -> attachDecision(gameState, event, ObjectiveType.BARON, currentTime, outcome));
    }

    private MatchEvent attachDecision(GameState state, MatchEvent event, ObjectiveType type,
                                      int time, TeamfightOutcome outcome) {
        TeamSide selectedSide = outcome.winningSide();
        ObjectivePriorityResolver priority = new ObjectivePriorityResolver();
        double lane = type == ObjectiveType.DRAGON ? priority.dragonLanePressureScore(state)
                : type == ObjectiveType.BARON ? priority.baronLanePressureScore(state) : 0;
        double recent = type == ObjectiveType.DRAGON ? state.getObjectivePriorityState().getDragonRecentControl()
                : type == ObjectiveType.BARON ? state.getObjectivePriorityState().getBaronRecentControl() : 0;
        double signed = type == ObjectiveType.DRAGON ? priority.dragonSignedPriorityWithoutMacro(state)
                : type == ObjectiveType.BARON ? priority.baronSignedPriorityWithoutMacro(state) : 0;
        double blue = type == ObjectiveType.ELDER ? 50 : priority.blueDisplayPriority(signed);
        event.setObjectivePriorityDecision(new ObjectivePriorityDecisionData(type, time,
                state.getObjectivePriorityState().isEnabled(), false, true, false,
                lane, recent, signed, blue, 100 - blue,
                0, 0, 0, false, false, false, false,
                ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(),
                1, 1, 0, 0, false, selectedSide));
        event.setActionId("POST_FIGHT_OBJECTIVE:" + time + ":" + type);
        event.setParentActionId(outcome.actionId() == null ? "COMBAT_AT:" + time : outcome.actionId());
        if (state.isObjectiveDecisionEnabled()) {
            ObjectiveDecisionState decisions = state.getObjectiveDecisionState();
            ObjectiveDecisionKey key = new ObjectiveDecisionKey(type, time, time, selectedSide);
            if (decisions.reserve(key)) {
                ObjectiveDecisionData data = new ObjectiveDecisionData(
                        decisions.nextSequence(), time, type, true, selectedSide,
                        selectedSide.opposite(), List.of(), ObjectiveDecisionAction.TAKE,
                        false, null, List.of(), ObjectiveDecisionAction.GIVE,
                        false, null, null, null, 0, false, false,
                        true, selectedSide, selectedSide, ObjectiveDecisionResult.CONTEST_FIGHT,
                        true, false, nextAttempt(state.getObjectiveState(), type), true,
                        type == ObjectiveType.ELDER || state.getObjectiveState().isElderAlive());
                decisions.record(data);
                event.setObjectiveDecision(data);
            }
        }
        return event;
    }

    private int nextAttempt(ObjectiveState state, ObjectiveType type) {
        return switch (type) {
            case DRAGON -> state.getNextDragonAttemptSeconds();
            case BARON -> state.getNextBaronAttemptSeconds();
            case ELDER -> state.getNextElderAttemptSeconds();
        };
    }

    private int countAlivePlayers(TeamState teamState, int currentTimeSeconds) {
        int count = 0;
        for (PlayerState player : teamState.getPlayers()) {
            if (player.isAlive(currentTimeSeconds)) {
                count++;
            }
        }
        return count;
    }

    private int longestRemainingRespawn(TeamState teamState, int currentTimeSeconds) {
        int longest = 0;
        for (PlayerState player : teamState.getPlayers()) {
            longest = Math.max(longest, Math.max(0, player.getRespawnAtSeconds() - currentTimeSeconds));
        }
        return longest;
    }

    private double captureChance(FightGrade grade, int winningSurvivors, int longestEnemyRespawn) {
        double chance = switch (grade) {
            case NORMAL_WIN -> 0.18;
            case BIG_WIN -> 0.68;
            case ACE -> 0.92;
            case SMALL_WIN -> 0.0;
        };
        chance += Math.min(0.12, longestEnemyRespawn / 400.0);
        chance += Math.min(0.08, Math.max(0, winningSurvivors - 2) * 0.03);
        return Math.min(0.98, chance);
    }

    private boolean shouldChooseBaron(FightGrade grade, boolean dragonAvailable, int dragonStacks, Random random) {
        if (!dragonAvailable) {
            return true;
        }
        double baronPreference = switch (grade) {
            case ACE -> 0.82;
            case BIG_WIN -> 0.68;
            case NORMAL_WIN -> 0.35;
            case SMALL_WIN -> 0.0;
        };
        // Dragon stacks make the next elemental dragon strategically more valuable without forcing it.
        if (dragonStacks >= 3) {
            baronPreference -= 0.45;
        } else if (dragonStacks >= 2) {
            baronPreference -= 0.25;
        }
        return random.nextDouble() < Math.max(0.10, baronPreference);
    }
}

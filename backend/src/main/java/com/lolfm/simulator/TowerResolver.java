package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TowerResolver {

    private static final int TOWER_GOLD_PER_PLAYER = 125;

    public Optional<PushOutcome> destroyNextTower(
            GameState state, TeamSide attackingSide, Lane lane, PushReason reason
    ) {
        TeamSide defendingSide = attackingSide.opposite();
        LaneStructureState laneState = state.getMapState().getLaneState(defendingSide, lane);
        Optional<TowerTier> nextTower = laneState.nextAliveTower();
        if (nextTower.isEmpty()) return Optional.empty();

        TowerTier tier = nextTower.get();
        return new StructureResolver().destroyNextStructure(state, attackingSide, lane, reason)
                .map(ignored -> new PushOutcome(
                        attackingSide, defendingSide, lane, tier, state.getCurrentTimeSeconds(), reason
                ));
    }

    public MatchEvent createTowerEvent(GameState state, PushOutcome outcome) {
        String teamName = state.getTeamState(outcome.attackingSide()).getTeamName();
        return new MatchEvent(
                outcome.occurredAtSeconds(),
                MatchEventType.TOWER,
                buildMessage(teamName, outcome),
                null,
                null,
                List.of()
        );
    }

    private void awardTowerGold(TeamState teamState) {
        for (PlayerState player : teamState.getPlayers()) player.addGold(TOWER_GOLD_PER_PLAYER);
        teamState.addGold(TOWER_GOLD_PER_PLAYER * teamState.getPlayers().size());
    }

    private String buildMessage(String teamName, PushOutcome outcome) {
        String lane = switch (outcome.lane()) {
            case TOP -> "탑";
            case MID -> "미드";
            case BOT -> "바텀";
        };
        String tier = switch (outcome.destroyedTowerTier()) {
            case OUTER -> "외곽 포탑";
            case INNER -> "내부 포탑";
            case INHIBITOR -> "억제기 포탑";
        };
        return switch (outcome.reason()) {
            case BARON_PRESSURE -> teamName + "가 바론 버프를 앞세워 " + lane + " " + tier + "을 무너뜨립니다.";
            case POST_FIGHT -> teamName + "가 한타 승리 이후 " + lane + " " + tier + "까지 진격합니다.";
            case MACRO_PLAY -> teamName + "가 운영 압박으로 " + lane + " " + tier + "을 파괴합니다.";
        };
    }
}

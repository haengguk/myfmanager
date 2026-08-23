package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.OuterTurretSiegeData;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StructureResolver {
    private static final int TOWER_GOLD_PER_PLAYER = 125;

    public Optional<StructureOutcome> destroyNextStructure(GameState state, TeamSide attackingSide, Lane lane, PushReason reason) {
        if (state.isFinished()) return Optional.empty();
        TeamSide defendingSide = attackingSide.opposite();
        LaneStructureState laneState = state.getMapState().getLaneState(defendingSide, lane);
        Optional<TowerTier> tower = laneState.nextAliveTower();
        if (tower.isPresent()) {
            return destroyTower(state, attackingSide, defendingSide, lane, tower.get(), reason, source(reason), null);
        }
        if (laneState.destroyInhibitor(state.getCurrentTimeSeconds())) {
            state.markStructureMutationPerformed(attackingSide);
            state.getMapState().activateBasePressure(attackingSide, state.getCurrentTimeSeconds());
            return Optional.of(new StructureOutcome(attackingSide, defendingSide, StructureKind.INHIBITOR, lane, null,
                    state.getCurrentTimeSeconds(), reason, false));
        }
        BaseState base = state.getMapState().getBaseState(defendingSide);
        if (state.getMapState().areNexusTurretsVulnerable(defendingSide)
                && base.destroyOneNexusTurret(state.getCurrentTimeSeconds())) {
            state.markStructureMutationPerformed(attackingSide);
            return Optional.of(new StructureOutcome(attackingSide, defendingSide, StructureKind.NEXUS_TURRET, null, null,
                    state.getCurrentTimeSeconds(), reason, false));
        }
        if (state.getMapState().isNexusVulnerable(defendingSide) && base.destroyNexus(state.getCurrentTimeSeconds())) {
            state.markStructureMutationPerformed(attackingSide);
            state.finish(attackingSide, GameEndReason.NEXUS_DESTROYED);
            return Optional.of(new StructureOutcome(attackingSide, defendingSide, StructureKind.NEXUS, null, null,
                    state.getCurrentTimeSeconds(), reason, true));
        }
        return Optional.empty();
    }

    public Optional<StructureOutcome> destroyTarget(GameState state, TeamSide attackingSide, Lane lane, LateGameStructureTarget target, PushReason reason) {
        if (target == null || state.wasStructureMutationPerformedThisTick(attackingSide)) return Optional.empty();
        LateGameStructureTarget current = new BaseThreatEvaluator().nextTarget(state, attackingSide, lane);
        if (current != target) return Optional.empty();
        TeamSide defendingSide = attackingSide.opposite();
        BaseState base = state.getMapState().getBaseState(defendingSide);
        if (target == LateGameStructureTarget.NEXUS_TURRET) {
            if (!state.getMapState().areNexusTurretsVulnerable(defendingSide)
                    || !base.destroyOneNexusTurret(state.getCurrentTimeSeconds())) {
                return Optional.empty();
            }
            state.markStructureMutationPerformed(attackingSide);
            return Optional.of(new StructureOutcome(attackingSide, defendingSide,
                    StructureKind.NEXUS_TURRET, null, null,
                    state.getCurrentTimeSeconds(), reason, false));
        }
        if (target == LateGameStructureTarget.NEXUS) {
            if (!state.getMapState().isNexusVulnerable(defendingSide)
                    || !base.destroyNexus(state.getCurrentTimeSeconds())) {
                return Optional.empty();
            }
            state.markStructureMutationPerformed(attackingSide);
            state.finish(attackingSide, GameEndReason.NEXUS_DESTROYED);
            return Optional.of(new StructureOutcome(attackingSide, defendingSide,
                    StructureKind.NEXUS, null, null,
                    state.getCurrentTimeSeconds(), reason, true));
        }
        return destroyNextStructure(state, attackingSide, lane == null ? Lane.MID : lane, reason);
    }

    /** Macro-only tower path; it deliberately cannot fall through to inhibitors or the nexus. */
    public Optional<StructureOutcome> destroyNextTower(GameState state, TeamSide attackingSide, Lane lane,
                                                        PushReason reason) {
        if (state.isFinished()) return Optional.empty();
        TeamSide defendingSide = attackingSide.opposite();
        LaneStructureState laneState = state.getMapState().getLaneState(defendingSide, lane);
        Optional<TowerTier> tower = laneState.nextAliveTower();
        if (tower.isEmpty()) return Optional.empty();
        return destroyTower(state, attackingSide, defendingSide, lane, tower.get(), reason, source(reason), null);
    }

    public Optional<StructureOutcome> destroyOuterFromLanePressure(GameState state, TeamSide attackingSide, Lane lane,
                                                                    OuterTurretSiegeData siege) {
        if (state.isFinished()) return Optional.empty();
        TeamSide defendingSide = attackingSide.opposite();
        LaneStructureState target = state.getMapState().getLaneState(defendingSide, lane);
        if (!target.isOuterTowerAlive() || target.getOuterRemainingIntegrity() > 0) return Optional.empty();
        return destroyTower(state, attackingSide, defendingSide, lane, TowerTier.OUTER, PushReason.MACRO_PLAY,
                StructureActionSource.LANE_PRESSURE, siege);
    }

    private Optional<StructureOutcome> destroyTower(GameState state, TeamSide attacking, TeamSide defending, Lane lane,
                                                     TowerTier tier, PushReason reason, StructureActionSource source,
                                                     OuterTurretSiegeData siege) {
        LaneStructureState laneState = state.getMapState().getLaneState(defending, lane);
        if (!laneState.canDestroy(tier)) return Optional.empty();
        laneState.destroy(tier, state.getCurrentTimeSeconds(), attacking, source);
        TeamState attackers = state.getTeamState(attacking);
        state.markStructureMutationPerformed(attacking);
        attackers.addTowerDestroyed();
        awardTowerGold(attackers, state.getCurrentTimeSeconds());
        if (tier == TowerTier.OUTER) {
            state.getLanePhaseExecutionStats().recordDestruction(defending, lane, source);
            state.getLanePhaseState().openLane(lane, state.getCurrentTimeSeconds());
        }
        return Optional.of(new StructureOutcome(attacking, defending, StructureKind.TOWER, lane, tier,
                state.getCurrentTimeSeconds(), reason, false, source, siege));
    }

    public MatchEvent createStructureEvent(GameState state, StructureOutcome outcome) {
        String team = state.getTeamState(outcome.attackingSide()).getTeamName();
        MatchEvent event = new MatchEvent(outcome.occurredAtSeconds(), MatchEventType.TOWER,
                message(team, outcome, state), null, null, List.of());
        event.setStructureActionSource(outcome.source());
        event.setStructureKind(outcome.structureKind());
        event.setStructureTowerTier(outcome.towerTier());
        event.setStructureLane(outcome.lane());
        event.setStructureAttackingSide(outcome.attackingSide());
        event.setStructureDefendingSide(outcome.defendingSide());
        event.setOuterTurretSiege(outcome.outerTurretSiege());
        event.setActionId("STRUCTURE:" + outcome.occurredAtSeconds() + ":"
                + outcome.attackingSide() + ":" + outcome.source());
        return event;
    }

    private StructureActionSource source(PushReason reason) {
        return switch (reason) {
            case POST_FIGHT -> StructureActionSource.POST_FIGHT;
            case BARON_PRESSURE -> StructureActionSource.BARON_PRESSURE;
            case MACRO_PLAY -> StructureActionSource.MACRO_PLAY;
            case MID_GAME_MACRO -> StructureActionSource.MID_GAME_MACRO;
            case OBJECTIVE_TRADE -> StructureActionSource.OBJECTIVE_TRADE;
            case LATE_GAME_SIEGE -> StructureActionSource.LATE_GAME_SIEGE;
            case LATE_GAME_CROSS_MAP -> StructureActionSource.LATE_GAME_CROSS_MAP;
            case NEXUS_FINISH -> StructureActionSource.NEXUS_FINISH;
        };
    }

    private void awardTowerGold(TeamState team, int timeSeconds) {
        for (PlayerState player : team.getPlayers()) player.addGold(TOWER_GOLD_PER_PLAYER, GoldSource.STRUCTURE, timeSeconds);
        team.addGold(TOWER_GOLD_PER_PLAYER * team.getPlayers().size());
    }

    private String message(String team, StructureOutcome outcome, GameState state) {
        if (outcome.source() == StructureActionSource.LANE_PRESSURE) {
            return team + "가 라인 압박으로 " + laneName(outcome.lane()) + " 외곽 포탑을 파괴합니다.";
        }
        if (outcome.structureKind() == StructureKind.NEXUS) return team + "가 적 넥서스를 파괴합니다.";
        if (outcome.structureKind() == StructureKind.NEXUS_TURRET) {
            int remaining = state.getMapState().getBaseState(outcome.defendingSide()).getNexusTurretsRemaining();
            return team + (outcome.reason() == PushReason.BARON_PRESSURE ? "가 바론 버프를 앞세워 " : "가 적 본진에 진입해 ")
                    + "넥서스 포탑을 파괴합니다. 남은 포탑: " + remaining;
        }
        String lane = laneName(outcome.lane());
        if (outcome.structureKind() == StructureKind.INHIBITOR) {
            return outcome.reason() == PushReason.POST_FIGHT
                    ? team + "가 한타 승리를 바탕으로 " + lane + " 억제기까지 무너뜨립니다."
                    : team + "가 " + lane + " 억제기를 파괴합니다.";
        }
        String tier = switch (outcome.towerTier()) {
            case OUTER -> "외곽 포탑";
            case INNER -> "내부 포탑";
            case INHIBITOR -> "억제기 포탑";
        };
        return switch (outcome.reason()) {
            case BARON_PRESSURE -> team + "가 바론 버프를 앞세워 " + lane + " " + tier + "을 무너뜨립니다.";
            case POST_FIGHT -> team + "가 한타 승리 이후 " + lane + " " + tier + "까지 진격합니다.";
            case MACRO_PLAY -> team + "가 운영 압박으로 " + lane + " " + tier + "을 파괴합니다.";
            case MID_GAME_MACRO -> team + "가 미드게임 팀 운영으로 " + lane + " " + tier + "을 파괴합니다.";
            case OBJECTIVE_TRADE -> team + "가 오브젝트를 양보하는 대신 " + lane + " " + tier + "을 파괴합니다.";
            case LATE_GAME_SIEGE -> team + "가 후반 공성으로 " + lane + " " + tier + "을 파괴합니다.";
            case LATE_GAME_CROSS_MAP -> team + "가 교차 맵 운영으로 " + lane + " " + tier + "을 파괴합니다.";
            case NEXUS_FINISH -> team + "가 넥서스 마무리 과정에서 " + lane + " " + tier + "을 파괴합니다.";
        };
    }

    private String laneName(Lane lane) {
        return switch (lane) {
            case TOP -> "탑";
            case MID -> "미드";
            case BOT -> "바텀";
        };
    }
}

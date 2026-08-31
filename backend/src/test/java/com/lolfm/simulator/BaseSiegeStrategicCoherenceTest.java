package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureActionData;
import com.lolfm.domain.StructureActionPhase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BaseSiegeStrategicCoherenceTest {
    private final StructureResolver structures = new StructureResolver();

    @Test
    void previousTickNexusEmergencyStopsActiveLowerTierSiegeBeforeMutation() {
        GameState state = stateAt(1_000);
        state.getBlueTeamState().grantBaronBuff(state.getCurrentTimeSeconds(), 180);
        structures.attemptSiege(state, StructureAttackRequest.siege(
                TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.OUTER,
                PushReason.LATE_GAME_CROSS_MAP, Set.of(Position.TOP), "LOWER_TIER_SIEGE"))
                .orElseThrow();

        exposeNexus(state, TeamSide.BLUE, Lane.BOT);
        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();

        LaneStructureState target = state.getMapState().getLaneState(TeamSide.RED, Lane.TOP);
        BaseSiegeState siege = state.getBaseSiegeState(TeamSide.BLUE);
        LaneWaveState wave = state.getMapState().getWaveState(TeamSide.BLUE, Lane.TOP);
        double healthBefore = target.getTowerCurrentHealth(TowerTier.OUTER);
        int goldBefore = state.getBlueTeamState().getGold();
        int sequenceBefore = siege.getAttackSequence();
        int waveAttacksBefore = wave.getAttacksRemaining();
        int processedBefore = state.getProcessedStructureActionCount();
        int farmResumeBefore = state.getBlueTeamState().playerAt(Position.TOP)
                .getFarmResumeAtSeconds();
        List<MatchEvent> events = new ArrayList<>();

        structures.resolveActiveSieges(state, events);

        assertThat(siege.isActive()).isFalse();
        assertThat(target.getTowerCurrentHealth(TowerTier.OUTER)).isEqualTo(healthBefore);
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(goldBefore);
        assertThat(siege.getAttackSequence()).isEqualTo(sequenceBefore);
        assertThat(wave.getAttacksRemaining()).isEqualTo(waveAttacksBefore);
        assertThat(state.getProcessedStructureActionCount()).isEqualTo(processedBefore);
        assertThat(state.wasStructureActionAttemptedThisTick(TeamSide.BLUE)).isFalse();
        assertThat(state.getBlueTeamState().playerAt(Position.TOP)
                .getActivityState().getActivityType()).isEqualTo(PlayerActivityType.DEFAULT_ROLE);
        assertThat(state.getBlueTeamState().playerAt(Position.TOP).getFarmResumeAtSeconds())
                .isEqualTo(farmResumeBefore);
        assertThat(events).singleElement().satisfies(event -> {
            StructureActionData action = event.getStructureAction();
            assertThat(action.phase()).isEqualTo(StructureActionPhase.ABORTED);
            assertThat(action.stopReason()).isEqualTo(SiegeStopReason.OWN_BASE_EMERGENCY);
            assertThat(action.ownBaseThreatLevelAtDecision())
                    .isEqualTo(BaseThreatLevel.NEXUS_THREAT);
            assertThat(action.strategicContinuationDecision()).isEqualTo(
                    SiegeContinuationDecisionReason.LOWER_VALUE_SIEGE_ABORTED_FOR_BASE_DEFENSE);
            assertThat(action.strategicallyAllowed()).isFalse();
            assertThat(action.damage()).isZero();
        });

        List<MatchEvent> duplicateEvents = new ArrayList<>();
        structures.resolveActiveSieges(state, duplicateEvents);
        assertThat(duplicateEvents).isEmpty();
        assertThat(target.getTowerCurrentHealth(TowerTier.OUTER)).isEqualTo(healthBefore);
        assertThat(state.getProcessedStructureActionCount()).isEqualTo(processedBefore);
    }

    @Test
    void emergencyAbortIsSymmetricWhenBlueAndRedAreMirrored() {
        for (TeamSide attackingSide : TeamSide.values()) {
            EmergencyRun result = runEmergencyAbort(attackingSide, true);

            assertThat(result.stopReason()).isEqualTo(SiegeStopReason.OWN_BASE_EMERGENCY);
            assertThat(result.decisionReason()).isEqualTo(
                    SiegeContinuationDecisionReason.LOWER_VALUE_SIEGE_ABORTED_FOR_BASE_DEFENSE);
            assertThat(result.threatLevel()).isEqualTo(BaseThreatLevel.NEXUS_THREAT);
            assertThat(result.damage()).isZero();
            assertThat(result.activity()).isEqualTo(PlayerActivityType.DEFAULT_ROLE);
        }
    }

    @Test
    void everyLowerValueLaneTargetIsRejectedRatherThanMisclassifiedAsBaseRace() {
        for (LateGameStructureTarget target : List.of(
                LateGameStructureTarget.OUTER,
                LateGameStructureTarget.INNER,
                LateGameStructureTarget.INHIBITOR_TOWER,
                LateGameStructureTarget.INHIBITOR)) {
            GameState state = stateAt(1_000);
            prepareLaneTarget(state, TeamSide.RED, Lane.TOP, target);
            state.getBlueTeamState().grantBaronBuff(state.getCurrentTimeSeconds(), 180);
            structures.attemptSiege(state, StructureAttackRequest.siege(
                    TeamSide.BLUE, Lane.TOP, target, PushReason.LATE_GAME_CROSS_MAP,
                    Set.of(Position.TOP), "LOWER_TARGET:" + target)).orElseThrow();
            exposeNexus(state, TeamSide.BLUE, Lane.BOT);
            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            List<MatchEvent> events = new ArrayList<>();

            structures.resolveActiveSieges(state, events);

            assertThat(events).singleElement().satisfies(event -> {
                StructureActionData action = event.getStructureAction();
                assertThat(action.stopReason()).isEqualTo(SiegeStopReason.OWN_BASE_EMERGENCY);
                assertThat(action.strategicContinuationDecision()).isEqualTo(
                        SiegeContinuationDecisionReason
                                .LOWER_VALUE_SIEGE_ABORTED_FOR_BASE_DEFENSE);
                assertThat(action.strategicContinuationDecision()).isNotEqualTo(
                        SiegeContinuationDecisionReason.BASE_RACE_REJECTED_FAIL_CLOSED);
                assertThat(action.damage()).isZero();
            });
        }
    }

    @Test
    void sameTickNewEmergencyDoesNotRetroactivelyCancelEitherSideAndAppliesNextTick() {
        for (TeamSide threateningSide : TeamSide.values()) {
            GameState state = sameTickThreatFixture(threateningSide);
            TeamSide lowerSiegeSide = threateningSide.opposite();
            Lane lowerLane = lowerSiegeSide == TeamSide.BLUE ? Lane.TOP : Lane.BOT;
            TeamSide lowerTargetSide = lowerSiegeSide.opposite();
            LaneStructureState lowerTarget = state.getMapState()
                    .getLaneState(lowerTargetSide, lowerLane);

            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            double beforeThreatTick = lowerTarget.getTowerCurrentHealth(TowerTier.OUTER);
            List<MatchEvent> threatTickEvents = new ArrayList<>();

            structures.resolveActiveSieges(state, threatTickEvents);

            assertThat(state.getMapState().isNexusVulnerable(lowerSiegeSide)).isTrue();
            assertThat(lowerTarget.getTowerCurrentHealth(TowerTier.OUTER))
                    .isLessThan(beforeThreatTick);
            MatchEvent allowed = continuationEvent(threatTickEvents, lowerSiegeSide);
            assertThat(allowed.getStructureAction().ownBaseThreatLevelAtDecision())
                    .isEqualTo(BaseThreatLevel.NEXUS_TURRET_THREAT);
            assertThat(allowed.getStructureAction().strategicContinuationDecision())
                    .isEqualTo(SiegeContinuationDecisionReason.CONTINUATION_ALLOWED);
            assertThat(allowed.getStructureAction().strategicallyAllowed()).isTrue();

            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            double beforeNextTick = lowerTarget.getTowerCurrentHealth(TowerTier.OUTER);
            List<MatchEvent> nextTickEvents = new ArrayList<>();

            structures.resolveActiveSieges(state, nextTickEvents);

            assertThat(lowerTarget.getTowerCurrentHealth(TowerTier.OUTER))
                    .isEqualTo(beforeNextTick);
            MatchEvent stopped = continuationEvent(nextTickEvents, lowerSiegeSide);
            assertThat(stopped.getStructureAction().stopReason())
                    .isEqualTo(SiegeStopReason.OWN_BASE_EMERGENCY);
            assertThat(stopped.getStructureAction().ownBaseThreatLevelAtDecision())
                    .isEqualTo(BaseThreatLevel.NEXUS_THREAT);
        }
    }

    @Test
    void nexusContinuationIsRejectedFailClosedWithoutSafeBaseRaceContract() {
        GameState state = stateAt(2_700);
        exposeNexus(state, TeamSide.BLUE, Lane.BOT);
        exposeNexus(state, TeamSide.RED, Lane.TOP);
        state.getBlueTeamState().grantBaronBuff(state.getCurrentTimeSeconds(), 180);
        structures.attemptSiege(state, StructureAttackRequest.siege(
                TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.NEXUS,
                PushReason.NEXUS_FINISH, allPositions(), "BASE_RACE_FAIL_CLOSED")
                .withSiegeWindow(4, 60)).orElseThrow();

        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();
        BaseState redBase = state.getMapState().getBaseState(TeamSide.RED);
        double healthBefore = redBase.getNexusCurrentHealth();
        List<MatchEvent> events = new ArrayList<>();

        structures.resolveActiveSieges(state, events);

        assertThat(redBase.getNexusCurrentHealth()).isEqualTo(healthBefore);
        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isFalse();
        assertThat(events).singleElement().satisfies(event -> {
            StructureActionData action = event.getStructureAction();
            assertThat(action.stopReason()).isEqualTo(SiegeStopReason.OWN_BASE_EMERGENCY);
            assertThat(action.strategicContinuationDecision())
                    .isEqualTo(SiegeContinuationDecisionReason.BASE_RACE_REJECTED_FAIL_CLOSED);
            assertThat(action.strategicallyAllowed()).isFalse();
        });
    }

    @Test
    void safeNoKillBaronSiegeCanStillFinishTheNexus() {
        GameState state = stateAt(2_700);
        exposeNexus(state, TeamSide.RED, Lane.TOP);
        state.getBlueTeamState().grantBaronBuff(state.getCurrentTimeSeconds(), 180);
        structures.attemptSiege(state, StructureAttackRequest.siege(
                TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.NEXUS,
                PushReason.BARON_PRESSURE, allPositions(), "NO_KILL_BARON_FINISH")
                .withSiegeWindow(4, 60)).orElseThrow();
        List<MatchEvent> events = new ArrayList<>();

        for (int tick = 0; tick < 3 && !state.isFinished(); tick++) {
            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            structures.resolveActiveSieges(state, events);
        }

        assertThat(state.isFinished()).isTrue();
        assertThat(state.getWinnerSide()).isEqualTo(TeamSide.BLUE);
        assertThat(state.getBlueTeamState().getKills()).isZero();
        assertThat(state.getRedTeamState().getKills()).isZero();
        assertThat(events.stream()
                .filter(event -> event.getStructureAction() != null)
                .filter(event -> event.getStructureAction().strategicContinuationDecision() != null)
                .allMatch(event -> event.getStructureAction().strategicallyAllowed())).isTrue();
    }

    @Test
    void tickStartEmergencyAbortIsRecordedBeforeOpposingNexusFinishForEitherSide() {
        for (TeamSide finishingSide : TeamSide.values()) {
            GameState state = nexusFinishWithOpposingLowerSiege(finishingSide);
            TeamSide lowerSiegeSide = finishingSide.opposite();
            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            List<MatchEvent> events = new ArrayList<>();

            structures.resolveActiveSieges(state, events);

            assertThat(state.isFinished()).isTrue();
            assertThat(state.getWinnerSide()).isEqualTo(finishingSide);
            assertThat(events.getFirst().getStructureAction().attackingSide())
                    .isEqualTo(lowerSiegeSide);
            assertThat(events.getFirst().getStructureAction().stopReason())
                    .isEqualTo(SiegeStopReason.OWN_BASE_EMERGENCY);
            assertThat(events.stream()
                    .dropWhile(event -> event.getStructureAction().stopReason()
                            == SiegeStopReason.OWN_BASE_EMERGENCY)
                    .allMatch(event -> event.getStructureAction().attackingSide()
                            == finishingSide)).isTrue();

            List<MatchEvent> afterFinish = new ArrayList<>();
            structures.resolveActiveSieges(state, afterFinish);
            assertThat(afterFinish).isEmpty();
        }
    }

    @Test
    void diagnosticsToggleAndDeterministicReplayPreserveEmergencyDecisionExactly() {
        EmergencyRun diagnosticsOn = runEmergencyAbort(TeamSide.BLUE, true);
        EmergencyRun diagnosticsOff = runEmergencyAbort(TeamSide.BLUE, false);
        EmergencyRun replay = runEmergencyAbort(TeamSide.BLUE, true);

        assertThat(diagnosticsOn).isEqualTo(diagnosticsOff).isEqualTo(replay);
    }

    private GameState stateAt(int timeSeconds) {
        return stateAt(timeSeconds, true);
    }

    private GameState stateAt(int timeSeconds, boolean diagnosticsEnabled) {
        GameState state = new GameState(
                LateGameTestSupport.team("BLUE"), LateGameTestSupport.team("RED"),
                diagnosticsEnabled, true, true, true, true, true);
        state.advanceTimeSeconds(timeSeconds);
        return state;
    }

    private EmergencyRun runEmergencyAbort(TeamSide attackingSide, boolean diagnosticsEnabled) {
        GameState state = stateAt(1_000, diagnosticsEnabled);
        Lane route = attackingSide == TeamSide.BLUE ? Lane.TOP : Lane.BOT;
        Lane emergencyLane = attackingSide == TeamSide.BLUE ? Lane.BOT : Lane.TOP;
        state.getTeamState(attackingSide).grantBaronBuff(state.getCurrentTimeSeconds(), 180);
        structures.attemptSiege(state, StructureAttackRequest.siege(
                attackingSide, route, LateGameStructureTarget.OUTER,
                PushReason.LATE_GAME_CROSS_MAP, Set.of(Position.TOP), "MIRROR_EMERGENCY")
                .withSiegeWindow(4, 60)).orElseThrow();
        exposeNexus(state, attackingSide, emergencyLane);
        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();
        LaneStructureState target = state.getMapState()
                .getLaneState(attackingSide.opposite(), route);
        double healthBefore = target.getTowerCurrentHealth(TowerTier.OUTER);
        List<MatchEvent> events = new ArrayList<>();

        structures.resolveActiveSieges(state, events);

        StructureActionData action = events.getFirst().getStructureAction();
        return new EmergencyRun(
                action.stopReason(), action.strategicContinuationDecision(),
                action.ownBaseThreatLevelAtDecision(), healthBefore - action.healthAfter(),
                state.getTeamState(attackingSide).playerAt(Position.TOP)
                        .getActivityState().getActivityType(),
                state.getTeamState(attackingSide).playerAt(Position.TOP).getFarmResumeAtSeconds(),
                state.getProcessedStructureActionCount(), action);
    }

    private GameState sameTickThreatFixture(TeamSide threateningSide) {
        GameState state = stateAt(1_000);
        TeamSide defendingSide = threateningSide.opposite();
        Lane baseLane = threateningSide == TeamSide.BLUE ? Lane.TOP : Lane.BOT;
        exposeBaseWithOneNexusTurret(state, defendingSide, baseLane);
        BaseState base = state.getMapState().getBaseState(defendingSide);
        int lastTurret = base.nextAliveNexusTurretIndex();
        base.applyNexusTurretDamage(lastTurret, 800, state.getCurrentTimeSeconds());
        state.getTeamState(threateningSide)
                .grantBaronBuff(state.getCurrentTimeSeconds(), 180);
        structures.attemptSiege(state, StructureAttackRequest.siege(
                threateningSide, baseLane, LateGameStructureTarget.NEXUS_TURRET,
                PushReason.NEXUS_FINISH,
                EnumSet.of(Position.TOP, Position.JUNGLE, Position.MID), "LAST_TURRET_THREAT")
                .withSiegeWindow(5, 70)).orElseThrow();

        TeamSide lowerSiegeSide = defendingSide;
        Lane lowerLane = lowerSiegeSide == TeamSide.BLUE ? Lane.TOP : Lane.BOT;
        structures.attemptSiege(state, StructureAttackRequest.siege(
                lowerSiegeSide, lowerLane, LateGameStructureTarget.OUTER,
                PushReason.LATE_GAME_CROSS_MAP, Set.of(Position.TOP), "LOWER_SAME_TICK")
                .withSiegeWindow(4, 60)).orElseThrow();
        return state;
    }

    private GameState nexusFinishWithOpposingLowerSiege(TeamSide finishingSide) {
        GameState state = stateAt(2_700);
        TeamSide defendingSide = finishingSide.opposite();
        Lane finishLane = finishingSide == TeamSide.BLUE ? Lane.TOP : Lane.BOT;
        Lane lowerLane = finishingSide == TeamSide.BLUE ? Lane.BOT : Lane.TOP;
        structures.attemptSiege(state, StructureAttackRequest.siege(
                defendingSide, lowerLane, LateGameStructureTarget.OUTER,
                PushReason.LATE_GAME_CROSS_MAP, Set.of(Position.TOP), "TERMINAL_LOWER")
                .withSiegeWindow(4, 60)).orElseThrow();
        exposeNexus(state, defendingSide, finishLane);
        state.getTeamState(finishingSide).grantBaronBuff(state.getCurrentTimeSeconds(), 180);
        structures.attemptSiege(state, StructureAttackRequest.siege(
                finishingSide, finishLane, LateGameStructureTarget.NEXUS,
                PushReason.NEXUS_FINISH, allPositions(), "TERMINAL_FINISH")
                .withSiegeWindow(4, 60)).orElseThrow();
        BaseState targetBase = state.getMapState().getBaseState(defendingSide);
        targetBase.applyNexusDamage(targetBase.getNexusCurrentHealth() - 1);
        return state;
    }

    private void prepareLaneTarget(
            GameState state, TeamSide defendingSide, Lane lane,
            LateGameStructureTarget target) {
        LaneStructureState structures = state.getMapState().getLaneState(defendingSide, lane);
        if (target != LateGameStructureTarget.OUTER) {
            structures.destroy(TowerTier.OUTER, state.getCurrentTimeSeconds(),
                    defendingSide.opposite(), StructureActionSource.MACRO_PLAY);
        }
        if (target == LateGameStructureTarget.INHIBITOR_TOWER
                || target == LateGameStructureTarget.INHIBITOR) {
            structures.destroy(TowerTier.INNER, state.getCurrentTimeSeconds(),
                    defendingSide.opposite(), StructureActionSource.MACRO_PLAY);
        }
        if (target == LateGameStructureTarget.INHIBITOR) {
            structures.destroy(TowerTier.INHIBITOR, state.getCurrentTimeSeconds(),
                    defendingSide.opposite(), StructureActionSource.MACRO_PLAY);
        }
    }

    private MatchEvent continuationEvent(List<MatchEvent> events, TeamSide attackingSide) {
        return events.stream()
                .filter(event -> event.getStructureAction() != null)
                .filter(event -> event.getStructureAction().attackingSide() == attackingSide)
                .findFirst()
                .orElseThrow();
    }

    private void exposeNexus(GameState state, TeamSide defending, Lane lane) {
        exposeBaseWithOneNexusTurret(state, defending, lane);
        BaseState base = state.getMapState().getBaseState(defending);
        while (base.hasNexusTurrets()) {
            base.destroyOneNexusTurret(state.getCurrentTimeSeconds());
        }
    }

    private void exposeBaseWithOneNexusTurret(
            GameState state, TeamSide defending, Lane lane) {
        LateGameTestSupport.destroyThroughInhibitorTower(state, defending, lane);
        state.getMapState().getLaneState(defending, lane)
                .destroyInhibitor(state.getCurrentTimeSeconds());
        BaseState base = state.getMapState().getBaseState(defending);
        while (base.getNexusTurretsRemaining() > 1) {
            base.destroyOneNexusTurret(state.getCurrentTimeSeconds());
        }
    }

    private Set<Position> allPositions() {
        return EnumSet.allOf(Position.class);
    }

    private record EmergencyRun(
            SiegeStopReason stopReason,
            SiegeContinuationDecisionReason decisionReason,
            BaseThreatLevel threatLevel,
            double damage,
            PlayerActivityType activity,
            int farmResumeAtSeconds,
            int processedActions,
            StructureActionData action
    ) {
    }
}

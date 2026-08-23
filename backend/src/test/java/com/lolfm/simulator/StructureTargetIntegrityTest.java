package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StructureTargetIntegrityTest {

    @Test
    void nexusTurretTargetDoesNotFallThroughToUnrelatedMidLaneStructure() {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(1_800);
        exposeNexusTurretsThroughTop(state, TeamSide.RED);

        StructureOutcome outcome = new StructureResolver().destroyTarget(
                state, TeamSide.BLUE, null, LateGameStructureTarget.NEXUS_TURRET,
                PushReason.NEXUS_FINISH).orElseThrow();

        assertThat(outcome.structureKind()).isEqualTo(StructureKind.NEXUS_TURRET);
        assertThat(outcome.lane()).isNull();
        assertThat(state.getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretsRemaining()).isOne();
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.MID)
                .destroyedTowerCount()).isZero();
    }

    @Test
    void nexusTargetDoesNotFallThroughToUnrelatedMidLaneStructure() {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(1_800);
        exposeNexusTurretsThroughTop(state, TeamSide.RED);
        BaseState base = state.getMapState().getBaseState(TeamSide.RED);
        base.destroyOneNexusTurret(state.getCurrentTimeSeconds());
        base.destroyOneNexusTurret(state.getCurrentTimeSeconds());

        StructureOutcome outcome = new StructureResolver().destroyTarget(
                state, TeamSide.BLUE, null, LateGameStructureTarget.NEXUS,
                PushReason.NEXUS_FINISH).orElseThrow();

        assertThat(outcome.structureKind()).isEqualTo(StructureKind.NEXUS);
        assertThat(outcome.gameEnded()).isTrue();
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.MID)
                .destroyedTowerCount()).isZero();
        assertThat(state.getWinnerSide()).isEqualTo(TeamSide.BLUE);
    }

    private void exposeNexusTurretsThroughTop(GameState state, TeamSide defendingSide) {
        LateGameTestSupport.destroyThroughInhibitorTower(state, defendingSide, Lane.TOP);
        assertThat(state.getMapState().getLaneState(defendingSide, Lane.TOP)
                .destroyInhibitor(state.getCurrentTimeSeconds())).isTrue();
        assertThat(state.getMapState().areNexusTurretsVulnerable(defendingSide)).isTrue();
    }
}

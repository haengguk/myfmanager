package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureActionPhase;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructureEngineRedesignTest {
    private final StructureResolver structures = new StructureResolver();
    private final PushResolver pushes = new PushResolver();

    @Test
    void exposedTopBaseTargetsNexusTurretWithoutTouchingMidOuter() {
        GameState state = stateAt(2_700);
        openBase(state, TeamSide.RED, Lane.TOP);
        killAll(state.getRedTeamState(), state.getCurrentTimeSeconds(), 300);
        LaneStructureState mid = state.getMapState().getLaneState(TeamSide.RED, Lane.MID);
        double midBefore = mid.getTowerCurrentHealth(TowerTier.OUTER);
        List<MatchEvent> events = new ArrayList<>();
        TeamfightOutcome fight = new TeamfightOutcome(
                TeamSide.BLUE, FightGrade.ACE, 5, 0,
                state.getCurrentTimeSeconds(), List.of(), "FIGHT:TOP_BASE");

        List<StructureOutcome> outcomes = pushes.resolvePostFightWindow(
                state, Optional.of(fight), Optional.empty(), new ZeroRandom(), structures, events);

        assertThat(outcomes).singleElement().satisfies(outcome ->
                assertThat(outcome.structureKind()).isEqualTo(StructureKind.NEXUS_TURRET));
        assertThat(mid.isOuterTowerAlive()).isTrue();
        assertThat(mid.getTowerCurrentHealth(TowerTier.OUTER)).isEqualTo(midBefore);
        assertThat(state.getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretsRemaining()).isOne();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStructureActionSource()).isEqualTo(StructureActionSource.POST_FIGHT);
            assertThat(event.getStructureAction().structureKind())
                    .isEqualTo(StructureKind.NEXUS_TURRET);
            assertThat(event.getParentActionId()).isEqualTo("FIGHT:TOP_BASE");
        });
    }

    @Test
    void twoPostFightAttackersCannotFallThroughIntoBaseAndConsumeNoRandom() {
        GameState state = stateAt(2_700);
        openBase(state, TeamSide.RED, Lane.TOP);
        killAll(state.getRedTeamState(), state.getCurrentTimeSeconds(), 300);
        for (Position position : List.of(Position.TOP, Position.JUNGLE, Position.MID)) {
            state.getBlueTeamState().playerAt(position)
                    .markDead(state.getCurrentTimeSeconds(), 300);
        }
        BaseState base = state.getMapState().getBaseState(TeamSide.RED);
        List<Double> before = base.getNexusTurretCurrentHealths();
        CountingRandom random = new CountingRandom(0);
        TeamfightOutcome fight = new TeamfightOutcome(
                TeamSide.BLUE, FightGrade.BIG_WIN, 2, 0,
                state.getCurrentTimeSeconds(), List.of());

        List<StructureOutcome> outcomes = pushes.resolvePostFightWindow(
                state, Optional.of(fight), Optional.empty(), random, structures);

        assertThat(outcomes).isEmpty();
        assertThat(random.calls()).isZero();
        assertThat(base.getNexusTurretCurrentHealths()).isEqualTo(before);
        assertThat(state.wasStructureActionAttemptedThisTick(TeamSide.BLUE)).isFalse();
        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isFalse();
    }

    @Test
    void duplicateDynamicCallCannotRetargetAndPayAgain() {
        GameState state = stateAt(1_000);
        LaneStructureState lane = state.getMapState().getLaneState(TeamSide.RED, Lane.TOP);

        StructureOutcome first = structures.destroyNextStructure(
                state, TeamSide.BLUE, Lane.TOP, PushReason.MACRO_PLAY).orElseThrow();
        int goldAfterFirst = state.getBlueTeamState().getGold();
        double innerAfterFirst = lane.getTowerCurrentHealth(TowerTier.INNER);
        Optional<StructureOutcome> duplicate = structures.destroyNextStructure(
                state, TeamSide.BLUE, Lane.TOP, PushReason.MACRO_PLAY);

        assertThat(first.towerTier()).isEqualTo(TowerTier.OUTER);
        assertThat(duplicate).isEmpty();
        assertThat(lane.destroyedTowerCount()).isOne();
        assertThat(lane.getTowerCurrentHealth(TowerTier.INNER)).isEqualTo(innerAfterFirst);
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(goldAfterFirst);
        assertThat(state.getProcessedStructureActionCount()).isOne();
        assertThat(state.getStructureActionExecutionStats().snapshot()
                .sameSideMultipleMutationError()).isZero();
    }

    @Test
    void finishedMatchRejectsOppositeNexusMutation() {
        GameState state = stateAt(1_000);
        openBase(state, TeamSide.RED, Lane.TOP);
        openBase(state, TeamSide.BLUE, Lane.BOT);
        destroyAllNexusTurrets(state, TeamSide.RED);
        destroyAllNexusTurrets(state, TeamSide.BLUE);
        BaseState blueBase = state.getMapState().getBaseState(TeamSide.BLUE);
        double blueNexusBefore = blueBase.getNexusCurrentHealth();

        StructureOutcome finish = structures.destroyTarget(
                state, TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.NEXUS,
                PushReason.NEXUS_FINISH).orElseThrow();
        Optional<StructureOutcome> impossibleReply = structures.destroyTarget(
                state, TeamSide.RED, Lane.BOT, LateGameStructureTarget.NEXUS,
                PushReason.NEXUS_FINISH);

        assertThat(finish.gameEnded()).isTrue();
        assertThat(state.getWinnerSide()).isEqualTo(TeamSide.BLUE);
        assertThat(impossibleReply).isEmpty();
        assertThat(blueBase.isNexusAlive()).isTrue();
        assertThat(blueBase.getNexusCurrentHealth()).isEqualTo(blueNexusBefore);
    }

    @Test
    void sameDisplayNamesCannotBorrowOpponentsRecentAce() {
        GameState state = new GameState(
                LateGameTestSupport.team("SAME"), LateGameTestSupport.team("SAME"),
                true, true, true, true, true, true);
        state.advanceTimeSeconds(1_000);
        openBase(state, TeamSide.RED, Lane.TOP);
        state.recordAce(TeamSide.RED);
        state.getMapState().markPushAttempted(
                TeamSide.RED, state.getCurrentTimeSeconds(), 10_000);
        CountingRandom random = new CountingRandom(0);
        List<Double> before = state.getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretCurrentHealths();

        pushes.maybeResolveMacroPush(state, random, structures);

        assertThat(state.hasRecentAce(TeamSide.BLUE,
                PushRuleConfig.RECENT_FIGHT_BASE_WINDOW_SECONDS)).isFalse();
        assertThat(state.hasRecentAce(TeamSide.RED,
                PushRuleConfig.RECENT_FIGHT_BASE_WINDOW_SECONDS)).isTrue();
        assertThat(random.calls()).isZero();
        assertThat(state.getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretCurrentHealths()).isEqualTo(before);
        assertThat(state.wasStructureActionAttemptedThisTick(TeamSide.BLUE)).isFalse();
    }

    @Test
    void lateGameWithNoEligibleParticipantsConsumesNoRandom() {
        GameState state = LateGameTestSupport.state();
        LateGameTestSupport.midGameAt(state, LateGameRuleConfig.LATE_GAME_START_SECONDS);
        LateGameMacroResolver late = new LateGameMacroResolver();
        late.transitionIfDue(state, new MidGameMacroResolver()).orElseThrow();
        state.advanceTimeSeconds(LateGameRuleConfig.FIRST_EVALUATION_DELAY_SECONDS);
        for (TeamSide side : TeamSide.values()) {
            for (Position position : List.of(Position.TOP, Position.JUNGLE, Position.MID)) {
                state.getTeamState(side).playerAt(position)
                        .markDead(state.getCurrentTimeSeconds(), 300);
            }
        }
        CountingRandom random = new CountingRandom(0);

        late.resolveDue(state, domainTeam("BLUE"), domainTeam("RED"), random,
                new ArrayList<>(), structures, new TeamfightResolver());

        assertThat(random.calls()).isZero();
        assertThat(state.getLateGameState().getLatestDecision().result())
                .isEqualTo(LateGameActionResult.NO_INITIATIVE);
        assertThat(state.wasAnyStructureActionPerformedThisTick()).isFalse();
    }

    @Test
    void durabilityUsesPartialDamageLocalDefenseBackdoorAndNewPlateThresholds() {
        GameState defendedState = stateAt(1_000);
        StructureAttackResult defended = structures.attemptSiege(
                defendedState, StructureAttackRequest.siege(
                        TeamSide.BLUE, Lane.TOP, null, PushReason.MACRO_PLAY,
                        Set.of(Position.TOP), "DEFENDED")).orElseThrow();

        GameState openState = stateAt(1_000);
        openState.getRedTeamState().playerAt(Position.TOP)
                .markDead(openState.getCurrentTimeSeconds(), 300);
        StructureAttackResult open = structures.attemptSiege(
                openState, StructureAttackRequest.siege(
                        TeamSide.BLUE, Lane.TOP, null, PushReason.MACRO_PLAY,
                        Set.of(Position.TOP), "OPEN")).orElseThrow();

        GameState backdoorState = stateAt(1_000);
        backdoorState.getRedTeamState().playerAt(Position.TOP)
                .markDead(backdoorState.getCurrentTimeSeconds(), 300);
        StructureAttackResult backdoor = structures.attemptSiege(
                backdoorState, StructureAttackRequest.backdoor(
                        TeamSide.BLUE, Lane.TOP, null, PushReason.LATE_GAME_CROSS_MAP,
                        Set.of(Position.TOP), "BACKDOOR")).orElseThrow();
        List<MatchEvent> events = new ArrayList<>();
        structures.addAttackEvents(backdoorState, backdoor, events);

        assertThat(defended.destroyed()).isFalse();
        assertThat(defended.damage()).isLessThan(open.damage());
        assertThat(backdoor.damage()).isCloseTo(
                StructureRuleConfig.EFFECTIVE_DAMAGE_PER_ATTACKER
                        * StructureRuleConfig.LATE_GAME_DAMAGE_MULTIPLIER
                        * StructureRuleConfig.BACKDOOR_DAMAGE_MULTIPLIER,
                offset(0.000_001));
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStructureAction().backdoorProtected()).isTrue();
            assertThat(event.getStructureAction().wavePresent()).isFalse();
            assertThat(event.getStructureAction().phase())
                    .isEqualTo(StructureActionPhase.STARTED);
        });

        MatchSnapshot snapshot = new SnapshotFactory().create(openState);
        assertThat(snapshot.getStructureState().teams().get(TeamSide.RED)
                .lanes().get(Lane.TOP).outerTower().current())
                .isEqualTo(open.healthAfter());
        assertThat(snapshot.getStructureState().sieges().get(TeamSide.BLUE).active()).isTrue();
        assertThat(openState.getBlueTeamState().playerAt(Position.TOP)
                .getActivityState().getActivityType()).isEqualTo(PlayerActivityType.SIEGING);
        assertThat(openState.getBlueTeamState().playerAt(Position.TOP)
                .canFarmAt(openState.getCurrentTimeSeconds())).isFalse();
    }

    @Test
    void plateGoldIsIncrementalAndAPartialHitCannotDeleteFullTower() {
        GameState state = stateAt(1_000);
        int teamGoldBefore = state.getBlueTeamState().getGold();
        int topGoldBefore = state.getBlueTeamState().playerAt(Position.TOP).getGold();

        StructureAttackResult first = structures.attemptSiege(state, StructureAttackRequest.fixed(
                TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.OUTER,
                PushReason.MACRO_PLAY, Set.of(Position.TOP), 1_000,
                StructureActionSource.MACRO_PLAY, "PLATE:1")).orElseThrow();

        assertThat(first.destroyed()).isFalse();
        assertThat(first.platesClaimed()).isOne();
        assertThat(state.getBlueTeamState().getGold())
                .isEqualTo(teamGoldBefore + StructureRuleConfig.TURRET_PLATE_LOCAL_GOLD);
        assertThat(state.getBlueTeamState().playerAt(Position.TOP).getGold())
                .isEqualTo(topGoldBefore + StructureRuleConfig.TURRET_PLATE_LOCAL_GOLD);

        state.advanceTimeSeconds(10);
        state.clearStructureActionRegistryThisTick();
        int goldAfterFirst = state.getBlueTeamState().getGold();
        StructureAttackResult second = structures.attemptSiege(state, StructureAttackRequest.fixed(
                TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.OUTER,
                PushReason.MACRO_PLAY, Set.of(Position.TOP), 100,
                StructureActionSource.MACRO_PLAY, "PLATE:2")).orElseThrow();

        assertThat(second.platesClaimed()).isZero();
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(goldAfterFirst);
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .isOuterTowerAlive()).isTrue();
    }

    @Test
    void nexusTurretRespawnsAtExactlyThreeMinutesWithFortyPercentHealthAndEvent() {
        GameState state = stateAt(100);
        BaseState base = state.getMapState().getBaseState(TeamSide.RED);
        base.applyNexusTurretDamage(
                0, StructureRuleConfig.NEXUS_TURRET_MAX_HEALTH,
                state.getCurrentTimeSeconds());
        assertThat(base.getNexusTurretsRemaining()).isOne();

        state.advanceTimeSeconds(StructureRuleConfig.NEXUS_TURRET_RESPAWN_SECONDS - 1);
        assertThat(base.getNexusTurretCurrentHealth(0)).isZero();
        assertThat(base.getNexusTurretsRemaining()).isOne();
        state.advanceTimeSeconds(1);

        assertThat(base.getNexusTurretsRemaining()).isEqualTo(2);
        assertThat(base.getNexusTurretCurrentHealth(0)).isEqualTo(
                StructureRuleConfig.NEXUS_TURRET_MAX_HEALTH
                        * StructureRuleConfig.NEXUS_TURRET_RESPAWN_HEALTH_RATIO);
        List<MatchEvent> events = new ArrayList<>();
        structures.addLifecycleEvents(state, events);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStructureAction().phase())
                    .isEqualTo(StructureActionPhase.RESPAWNED);
            assertThat(event.getStructureAction().healthAfter()).isEqualTo(1_400);
            assertThat(event.getStructureAction().maxHealth()).isEqualTo(3_500);
        });
    }

    @Test
    void returningDefendersStopPersistentSiegeAndEmitRepelledEvent() {
        GameState state = stateAt(1_000);
        openBase(state, TeamSide.RED, Lane.TOP);
        killAll(state.getRedTeamState(), state.getCurrentTimeSeconds(), 5);
        StructureAttackResult first = structures.attemptSiege(
                state, StructureAttackRequest.siege(
                        TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.NEXUS_TURRET,
                        PushReason.MACRO_PLAY, allPositions(), "RETURN_TEST"))
                .orElseThrow();
        assertThat(first.siegeContinues()).isTrue();

        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();
        List<MatchEvent> events = new ArrayList<>();
        structures.resolveActiveSieges(state, events);

        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isFalse();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStructureAction().phase())
                    .isEqualTo(StructureActionPhase.REPELLED);
            assertThat(event.getStructureAction().stopReason())
                    .isEqualTo(SiegeStopReason.DEFENDERS_RETURNED);
            assertThat(event.getParentActionId()).isEqualTo("RETURN_TEST");
        });
    }

    @Test
    void ordinarySiegeEndsWhenItsWaveIsSpentInsteadOfWaitingForAnotherWave() {
        GameState state = stateAt(1_000);
        state.getRedTeamState().playerAt(Position.TOP)
                .markDead(state.getCurrentTimeSeconds(), 300);
        StructureAttackResult first = structures.attemptSiege(
                state, StructureAttackRequest.siege(
                        TeamSide.BLUE, Lane.TOP, LateGameStructureTarget.OUTER,
                        PushReason.MACRO_PLAY, Set.of(Position.TOP), "ONE_WAVE"))
                .orElseThrow();
        assertThat(first.siegeContinues()).isTrue();

        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();
        structures.resolveActiveSieges(state, new ArrayList<>());
        double healthAfterLastWaveHit = state.getMapState()
                .getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER);

        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();
        List<MatchEvent> events = new ArrayList<>();
        structures.resolveActiveSieges(state, events);

        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isFalse();
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER)).isEqualTo(healthAfterLastWaveHit);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getStructureAction().phase())
                    .isEqualTo(StructureActionPhase.ABORTED);
            assertThat(event.getStructureAction().stopReason())
                    .isEqualTo(SiegeStopReason.WAVE_LOST);
        });
        assertThat(state.getBlueTeamState().playerAt(Position.TOP)
                .getActivityState().getActivityType()).isEqualTo(PlayerActivityType.DEFAULT_ROLE);
    }

    @Test
    void earlyAceWindowEndsWhenThirdDefenderRespawnsInsteadOfCrossingTheMap() {
        GameState state = stateAt(900);
        killAll(state.getRedTeamState(), state.getCurrentTimeSeconds(), 20);
        TeamfightOutcome fight = new TeamfightOutcome(
                TeamSide.BLUE, FightGrade.ACE, 5, 0,
                state.getCurrentTimeSeconds(), List.of(), "EARLY_ACE");

        pushes.resolvePostFightWindow(state, Optional.of(fight), Optional.empty(),
                new ZeroRandom(), structures, new ArrayList<>());

        assertThat(state.getBaseSiegeState(TeamSide.BLUE).getAttackOpportunityLimit())
                .isEqualTo(2);
        for (int tick = 0; tick < 2; tick++) {
            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            structures.resolveActiveSieges(state, new ArrayList<>());
        }

        LaneStructureState top = state.getMapState().getLaneState(TeamSide.RED, Lane.TOP);
        assertThat(state.getBaseSiegeState(TeamSide.BLUE).isActive()).isFalse();
        assertThat(top.isOuterTowerAlive()).isFalse();
        assertThat(top.getTowerCurrentHealth(TowerTier.INNER))
                .isEqualTo(StructureRuleConfig.INNER_TURRET_MAX_HEALTH);
        assertThat(top.isInhibitorAlive()).isTrue();
        assertThat(state.getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretsRemaining()).isEqualTo(2);
    }

    @Test
    void aceSiegeCanConvertExposedInhibitorThroughTwinsIntoNexus() {
        GameState state = stateAt(2_700);
        LateGameTestSupport.destroyThroughInhibitorTower(state, TeamSide.RED, Lane.TOP);
        killAll(state.getRedTeamState(), state.getCurrentTimeSeconds(), 300);
        List<MatchEvent> events = new ArrayList<>();

        StructureAttackResult first = structures.attemptSiege(
                state, StructureAttackRequest.siege(
                        TeamSide.BLUE, Lane.TOP, null, PushReason.POST_FIGHT,
                        allPositions(), "ACE_CONVERSION")).orElseThrow();
        structures.addAttackEvents(state, first, events);
        for (int attempt = 0; attempt < 6 && !state.isFinished(); attempt++) {
            state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
            state.clearStructureActionRegistryThisTick();
            structures.resolveActiveSieges(state, events);
        }

        assertThat(state.isFinished()).isTrue();
        assertThat(state.getWinnerSide()).isEqualTo(TeamSide.BLUE);
        assertThat(state.getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretsRemaining()).isZero();
        assertThat(events.stream()
                .filter(event -> event.getStructureAction() != null)
                .map(event -> event.getStructureAction().structureKind()))
                .containsSequence(
                        StructureKind.INHIBITOR,
                        StructureKind.NEXUS_TURRET,
                        StructureKind.NEXUS_TURRET,
                        StructureKind.NEXUS,
                        StructureKind.NEXUS);
        assertThat(events.stream()
                .filter(event -> event.getStructureAction() != null)
                .allMatch(event -> event.getStructureAction().source()
                        == StructureActionSource.POST_FIGHT)).isTrue();
    }

    @Test
    void attackerDeathAndBackdoorBudgetStopContinuationsWithoutExtraDamage() {
        GameState deathState = stateAt(1_000);
        deathState.getRedTeamState().playerAt(Position.TOP)
                .markDead(deathState.getCurrentTimeSeconds(), 300);
        structures.attemptSiege(deathState, StructureAttackRequest.siege(
                TeamSide.BLUE, Lane.TOP, null, PushReason.MACRO_PLAY,
                Set.of(Position.TOP), "ATTACKER_DEATH")).orElseThrow();
        deathState.getBlueTeamState().playerAt(Position.TOP)
                .markDead(deathState.getCurrentTimeSeconds(), 300);
        deathState.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        deathState.clearStructureActionRegistryThisTick();
        List<MatchEvent> deathEvents = new ArrayList<>();
        structures.resolveActiveSieges(deathState, deathEvents);

        assertThat(deathState.getBaseSiegeState(TeamSide.BLUE).getStopReason())
                .isEqualTo(SiegeStopReason.ATTACKER_KILLED);

        GameState backdoorState = stateAt(1_000);
        backdoorState.getRedTeamState().playerAt(Position.TOP)
                .markDead(backdoorState.getCurrentTimeSeconds(), 300);
        structures.attemptSiege(backdoorState, StructureAttackRequest.backdoor(
                TeamSide.BLUE, Lane.TOP, null, PushReason.LATE_GAME_CROSS_MAP,
                Set.of(Position.TOP), "BACKDOOR_BUDGET")).orElseThrow();
        double healthAfterFirst = backdoorState.getMapState()
                .getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER);
        backdoorState.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        backdoorState.clearStructureActionRegistryThisTick();
        List<MatchEvent> backdoorEvents = new ArrayList<>();
        structures.resolveActiveSieges(backdoorState, backdoorEvents);

        assertThat(backdoorState.getBaseSiegeState(TeamSide.BLUE).getStopReason())
                .isEqualTo(SiegeStopReason.ATTACK_WINDOW_COMPLETE);
        assertThat(backdoorState.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER)).isEqualTo(healthAfterFirst);
        assertThat(backdoorEvents).singleElement().satisfies(event ->
                assertThat(event.getStructureAction().stopReason())
                        .isEqualTo(SiegeStopReason.ATTACK_WINDOW_COMPLETE));
    }

    private GameState stateAt(int timeSeconds) {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(timeSeconds);
        return state;
    }

    private void openBase(GameState state, TeamSide defending, Lane lane) {
        LateGameTestSupport.destroyThroughInhibitorTower(state, defending, lane);
        state.getMapState().getLaneState(defending, lane)
                .destroyInhibitor(state.getCurrentTimeSeconds());
    }

    private void destroyAllNexusTurrets(GameState state, TeamSide defending) {
        BaseState base = state.getMapState().getBaseState(defending);
        while (base.hasNexusTurrets()) {
            base.destroyOneNexusTurret(state.getCurrentTimeSeconds());
        }
    }

    private void killAll(TeamState team, int time, int duration) {
        for (PlayerState player : team.getPlayers()) player.markDead(time, duration);
    }

    private Set<Position> allPositions() {
        return EnumSet.allOf(Position.class);
    }

    private Team domainTeam(String name) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new Player(name + "-" + position, position,
                    new PlayerAttributes(14, 14, 14, 14)));
        }
        return new Team(name, players);
    }

    private static final class ZeroRandom extends Random {
        @Override public double nextDouble() { return 0; }
        @Override public boolean nextBoolean() { return false; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static final class CountingRandom extends Random {
        private final double value;
        private int calls;

        private CountingRandom(double value) { this.value = value; }
        int calls() { return calls; }

        @Override public double nextDouble() { calls++; return value; }
        @Override public boolean nextBoolean() { calls++; return false; }
        @Override public int nextInt(int bound) { calls++; return 0; }
    }
}

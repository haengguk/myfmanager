package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LateGameStructureBranchTest {
    private final LateGameMacroResolver late = new LateGameMacroResolver();
    private final StructureResolver structures = new StructureResolver();
    private final TeamfightResolver fights = new TeamfightResolver();

    @Test
    void deterministicGiveStructureRecordsAttemptAndMutation() {
        Fixture fixture = fixture();

        LateGameMacroResolver.Resolution result = resolve(
                fixture, TeamSide.BLUE, LateGameAttackPlan.SIEGE_TOP,
                LateGameDefenseResponse.GIVE_STRUCTURE, new ZeroRandom());

        StructureActionExecutionStatsSnapshot stats = fixture.state()
                .getStructureActionExecutionStats().snapshot();
        assertThat(result.attackerStructure()).isTrue();
        assertThat(fixture.state().wasStructureActionAttemptedThisTick(TeamSide.BLUE)).isTrue();
        assertThat(fixture.state().wasStructureMutationPerformedThisTick(TeamSide.BLUE)).isTrue();
        assertThat(stats.structureAttempted()).isOne();
        assertThat(stats.structureMutationPerformed()).isOne();
    }

    @Test
    void siegeFightWinRecordsOneAttemptAndOneMutation() {
        Fixture fixture = fixture();
        fixture.state().getBlueTeamState().addGold(100_000);

        LateGameMacroResolver.Resolution result = resolve(
                fixture, TeamSide.BLUE, LateGameAttackPlan.SIEGE_MID,
                LateGameDefenseResponse.DEFEND,
                new SequenceRandom(0, .5, 0, 0, 0, 0, 0, 0, 0, 0));

        StructureActionExecutionStatsSnapshot stats = fixture.state()
                .getStructureActionExecutionStats().snapshot();
        assertThat(result.fightWinner()).isEqualTo(TeamSide.BLUE);
        assertThat(result.attackerStructure()).isTrue();
        assertThat(stats.structureAttempted()).isOne();
        assertThat(stats.structureMutationPerformed()).isOne();
        assertThat(stats.sameSideMultipleAttemptError()).isZero();
        assertThat(stats.sameSideMultipleMutationError()).isZero();
    }

    @Test
    void siegeGiveAppliesPartialDamageAndKeepsAContinuousSiege() {
        Fixture fixture = fixture();
        LaneStructureState lane = fixture.state().getMapState()
                .getLaneState(TeamSide.RED, Lane.TOP);
        double before = lane.getTowerCurrentHealth(TowerTier.OUTER);

        LateGameMacroResolver.Resolution result = resolve(
                fixture, TeamSide.BLUE, LateGameAttackPlan.SIEGE_TOP,
                LateGameDefenseResponse.GIVE_STRUCTURE, new ZeroRandom());

        assertThat(result.result()).isEqualTo(LateGameActionResult.STRUCTURE_DAMAGED);
        assertThat(result.attackerStructure()).isTrue();
        assertThat(lane.destroyedTowerCount()).isZero();
        assertThat(lane.getTowerCurrentHealth(TowerTier.OUTER)).isBetween(0.0, before);
        assertThat(fixture.state().getBaseSiegeState(TeamSide.BLUE).isActive()).isTrue();
        assertThat(fixture.state().getStructureActionExecutionStats().snapshot()
                .structureMutationPerformed()).isOne();
    }

    @Test
    void defendAttackerWinAndDefenderWinAreDeterministic() {
        Fixture attackerWin = fixture();
        attackerWin.state().getBlueTeamState().addGold(100_000);
        LateGameMacroResolver.Resolution won = resolve(
                attackerWin, TeamSide.BLUE, LateGameAttackPlan.SIEGE_MID,
                LateGameDefenseResponse.DEFEND,
                new SequenceRandom(0, .5, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThat(won.fightWinner()).isEqualTo(TeamSide.BLUE);
        assertThat(won.attackerStructure()).isTrue();

        Fixture defenderWin = fixture();
        defenderWin.state().getRedTeamState().addGold(100_000);
        LateGameMacroResolver.Resolution lost = resolve(
                defenderWin, TeamSide.BLUE, LateGameAttackPlan.SIEGE_MID,
                LateGameDefenseResponse.DEFEND, new ZeroRandom());
        assertThat(lost.fightWinner()).isEqualTo(TeamSide.RED);
        assertThat(lost.attackerStructure()).isFalse();
        assertThat(defenderWin.state().wasStructureActionAttemptedThisTick(TeamSide.BLUE)).isTrue();
    }

    @Test
    void crossMapAllowsAtMostOneActionPerSide() {
        Fixture fixture = fixture();

        LateGameMacroResolver.Resolution result = resolve(
                fixture, TeamSide.BLUE, LateGameAttackPlan.SIEGE_TOP,
                LateGameDefenseResponse.CROSS_MAP_PUSH, new ZeroRandom());

        assertThat(result.attackerStructure()).isTrue();
        assertThat(result.defenderStructure()).isTrue();
        StructureActionExecutionStatsSnapshot stats = fixture.state()
                .getStructureActionExecutionStats().snapshot();
        assertThat(stats.structureAttempted()).isEqualTo(2);
        assertThat(stats.structureMutationPerformed()).isEqualTo(2);
    }

    @Test
    void nexusFinishDamagesTurretThenContinuesThroughBothTurretsAndNexus() {
        Fixture fixture = nexus(2);
        killAll(fixture.state().getRedTeamState(), fixture.state().getCurrentTimeSeconds());

        LateGameMacroResolver.Resolution result = resolve(
                fixture, TeamSide.BLUE, LateGameAttackPlan.NEXUS_FINISH,
                LateGameDefenseResponse.GIVE_STRUCTURE, new ZeroRandom());

        assertThat(result.result()).isEqualTo(LateGameActionResult.NEXUS_FINISH_ADVANCED);
        assertThat(fixture.state().getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretsRemaining()).isOne();
        assertThat(fixture.state().isFinished()).isFalse();
        assertThat(fixture.state().getBaseSiegeState(TeamSide.BLUE).isActive()).isTrue();

        resolveSiegeAtNextTick(fixture.state());
        assertThat(fixture.state().getMapState().getBaseState(TeamSide.RED)
                .getNexusTurretsRemaining()).isZero();
        resolveSiegeAtNextTick(fixture.state()); // waits for the next wave
        resolveSiegeAtNextTick(fixture.state());
        assertThat(fixture.state().getMapState().getBaseState(TeamSide.RED)
                .getNexusCurrentHealth()).isLessThan(StructureRuleConfig.NEXUS_MAX_HEALTH);
        resolveSiegeAtNextTick(fixture.state());

        assertThat(fixture.state().isFinished()).isTrue();
        assertThat(fixture.state().getEndReason()).isEqualTo(GameEndReason.NEXUS_DESTROYED);
    }

    @Test
    void exposedNexusRequiresAccumulatedDamageBeforeGameEnds() {
        Fixture fixture = nexus(0);
        killAll(fixture.state().getRedTeamState(), fixture.state().getCurrentTimeSeconds());

        LateGameMacroResolver.Resolution result = resolve(
                fixture, TeamSide.BLUE, LateGameAttackPlan.NEXUS_FINISH,
                LateGameDefenseResponse.GIVE_STRUCTURE, new ZeroRandom());

        assertThat(result.result()).isEqualTo(LateGameActionResult.NEXUS_FINISH_ADVANCED);
        assertThat(fixture.state().isFinished()).isFalse();
        assertThat(fixture.state().getMapState().getBaseState(TeamSide.RED)
                .getNexusCurrentHealth()).isBetween(0.0, StructureRuleConfig.NEXUS_MAX_HEALTH);

        resolveSiegeAtNextTick(fixture.state());

        assertThat(fixture.state().isFinished()).isTrue();
        assertThat(fixture.state().getEndReason()).isEqualTo(GameEndReason.NEXUS_DESTROYED);
    }

    @Test
    void seed7OrdersLaningMidGameLateGamePhaseEvents() {
        com.lolfm.factory.DummyDataFactory factory = new com.lolfm.factory.DummyDataFactory();
        MatchSimulator simulator = new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults());

        List<MatchPhase> phases = simulator.simulate(
                        factory.createBlueTeam(), factory.createRedTeam(), 7L)
                .getEvents().stream()
                .filter(event -> event.getType() == MatchEventType.MATCH_PHASE_CHANGE)
                .map(event -> event.getMatchPhaseChange().newPhase())
                .toList();

        assertThat(phases).containsExactly(MatchPhase.MID_GAME, MatchPhase.LATE_GAME);
    }

    private LateGameMacroResolver.Resolution resolve(
            Fixture fixture, TeamSide attacker, LateGameAttackPlan plan,
            LateGameDefenseResponse response, Random random) {
        return late.resolveSelected(
                fixture.state(), fixture.blue(), fixture.red(), random,
                new ArrayList<>(), structures, fights, attacker, plan, response);
    }

    private void resolveSiegeAtNextTick(GameState state) {
        state.advanceTimeSeconds(StructureRuleConfig.STRUCTURE_ATTACK_INTERVAL_SECONDS);
        state.clearStructureActionRegistryThisTick();
        structures.resolveActiveSieges(state, new ArrayList<MatchEvent>());
    }

    private Fixture nexus(int remainingTurrets) {
        Fixture fixture = fixture();
        LateGameTestSupport.destroyThroughInhibitorTower(
                fixture.state(), TeamSide.RED, Lane.MID);
        fixture.state().getMapState().getLaneState(TeamSide.RED, Lane.MID)
                .destroyInhibitor(fixture.state().getCurrentTimeSeconds());
        BaseState base = fixture.state().getMapState().getBaseState(TeamSide.RED);
        while (base.getNexusTurretsRemaining() > remainingTurrets) {
            base.destroyOneNexusTurret();
        }
        return fixture;
    }

    private Fixture fixture() {
        Team blue = team("BLUE");
        Team red = team("RED");
        GameState state = new GameState(
                state(blue), state(red), true, true, true, true, true, true);
        state.advanceTimeSeconds(1_800);
        return new Fixture(blue, red, state);
    }

    private Team team(String name) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) {
            players.add(new Player(name + "-" + position, position,
                    new PlayerAttributes(14, 14, 14, 14)));
        }
        return new Team(name, players);
    }

    private TeamState state(Team team) {
        return new TeamState(team.getName(), team.getPlayers().stream()
                .map(player -> new PlayerState(
                        player.getName(), player.getPosition(), player.getAttributes(), 500))
                .toList());
    }

    private void killAll(TeamState team, int time) {
        for (PlayerState player : team.getPlayers()) player.markDead(time, 300);
    }

    private record Fixture(Team blue, Team red, GameState state) { }

    private static final class ZeroRandom extends Random {
        @Override public double nextDouble() { return 0; }
        @Override public boolean nextBoolean() { return false; }
        @Override public int nextInt(int bound) { return 0; }
    }

    private static final class SequenceRandom extends Random {
        private final double[] values;
        private int index;

        private SequenceRandom(double... values) { this.values = values; }

        @Override public double nextDouble() {
            return index < values.length ? values[index++] : 0;
        }

        @Override public boolean nextBoolean() { return nextDouble() < .5; }
        @Override public int nextInt(int bound) { return 0; }
    }
}

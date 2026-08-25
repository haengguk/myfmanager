package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SimulationRealismInvariantTest {

    @Test
    void inhibitorRespawnsAtFiveMinuteBoundary() {
        GameState state = LateGameTestSupport.state();
        LateGameTestSupport.destroyThroughInhibitorTower(state, TeamSide.RED, Lane.MID);
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.MID)
                .destroyInhibitor(900)).isTrue();

        state.advanceTimeSeconds(1_199);
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.MID)
                .isInhibitorAlive()).isFalse();
        state.advanceTimeSeconds(1);
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.MID)
                .isInhibitorAlive()).isTrue();
        assertThat(state.getMapState().areNexusTurretsVulnerable(TeamSide.RED)).isFalse();
    }

    @Test
    void destroyedNexusTurretRespawnsAtThreeMinuteBoundary() {
        BaseState base = new BaseState();
        assertThat(base.destroyOneNexusTurret(1_000)).isTrue();
        assertThat(base.getNexusTurretsRemaining()).isOne();
        assertThat(base.refreshAt(1_179)).isZero();
        assertThat(base.refreshAt(1_180)).isOne();
        assertThat(base.getNexusTurretsRemaining()).isEqualTo(2);
        assertThat(base.getNexusTurretCurrentHealth(0)).isEqualTo(
                StructureRuleConfig.NEXUS_TURRET_MAX_HEALTH
                        * StructureRuleConfig.NEXUS_TURRET_RESPAWN_HEALTH_RATIO);
    }

    @Test
    void postFightPartialSiegeDoesNotAdvanceTheSimulationClock() {
        GameState state = LateGameTestSupport.state();
        state.advanceTimeSeconds(2_100);
        for (PlayerState player : state.getRedTeamState().getPlayers()) {
            player.markDead(2_100, 60);
        }
        TeamfightOutcome fight = new TeamfightOutcome(
                TeamSide.BLUE, FightGrade.ACE, 5, 0, 2_100, List.of());
        double before = state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER);
        List<StructureOutcome> outcomes = new PushResolver().resolvePostFightWindow(
                state, Optional.of(fight), Optional.empty(), new ZeroRandom(),
                new StructureResolver());

        assertThat(outcomes).isEmpty();
        assertThat(state.getCurrentTimeSeconds()).isEqualTo(2_100);
        assertThat(state.getMapState().getLaneState(TeamSide.RED, Lane.TOP)
                .getTowerCurrentHealth(TowerTier.OUTER)).isLessThan(before);
        assertThat(state.getBaseSiegeState(TeamSide.BLUE).getStartedAtSeconds())
                .isEqualTo(2_100);
    }

    @Test
    void topQuestAllowsLevelTwentyButOtherRolesRemainCappedAtEighteen() {
        PlayerState top = LateGameTestSupport.player("TOP", Position.TOP);
        PlayerState mid = LateGameTestSupport.player("MID", Position.MID);
        top.getProgressionState().awardExperience(ExperienceSource.LANE_ECONOMY, 30_000, 2_000);
        mid.getProgressionState().awardExperience(ExperienceSource.LANE_ECONOMY, 30_000, 2_000);

        assertThat(top.getProgressionState().getLevel()).isEqualTo(20);
        assertThat(mid.getProgressionState().getLevel()).isEqualTo(18);
    }

    @Test
    void genericSkirmishUsesOnlyStructuredLocalParticipants() {
        DummyDataFactory data = new DummyDataFactory();
        Team blue = data.createBlueTeam();
        Team red = data.createRedTeam();
        TeamState blueState = teamState(blue, TeamSide.BLUE);
        TeamState redState = teamState(red, TeamSide.RED);
        java.util.ArrayList<MatchEvent> events = new java.util.ArrayList<>();

        assertThat(new TeamfightResolver().resolveLocalizedSkirmishKill(
                600, Lane.MID, new ZeroRandom(), blue, blueState, red, redState,
                events, new java.util.HashSet<>())).isTrue();
        MatchEvent kill = events.getLast();
        java.util.Set<String> participantIds = new java.util.HashSet<>(kill.getAssistPlayerIds());
        participantIds.add(kill.getKillerPlayerId());
        participantIds.add(kill.getVictimPlayerId());

        assertThat(kill.getCombatLane()).isEqualTo(Lane.MID);
        assertThat(kill.getAssists()).isNotEmpty();
        assertThat(java.util.stream.Stream.concat(blue.getPlayers().stream(), red.getPlayers().stream())
                .filter(player -> participantIds.contains(player.requirePlayerId().value()))
                .map(Player::getPosition).toList())
                .allMatch(java.util.Set.of(Position.JUNGLE, Position.MID, Position.SUPPORT)::contains);
    }

    @Test
    void baseDefenseCombatCooldownIsMatchScopedAndHasExactBoundary() {
        LateGameState first = new LateGameState(true);
        LateGameState second = new LateGameState(true);
        first.recordBaseDefenseCombat(TeamSide.BLUE, 1_800);

        assertThat(first.canAttemptBaseDefenseCombat(TeamSide.BLUE, 2_039)).isFalse();
        assertThat(first.canAttemptBaseDefenseCombat(TeamSide.BLUE, 2_040)).isTrue();
        assertThat(first.canAttemptBaseDefenseCombat(TeamSide.RED, 1_800)).isTrue();
        assertThat(second.canAttemptBaseDefenseCombat(TeamSide.BLUE, 1_800)).isTrue();
    }

    @Test
    void standardTeamfightWithTooFewPlayersIsIneligibleWithoutRandomConsumption() {
        DummyDataFactory data = new DummyDataFactory();
        Team blue = data.createBlueTeam();
        Team red = data.createRedTeam();
        GameState state = new GameState(teamState(blue, TeamSide.BLUE),
                teamState(red, TeamSide.RED));
        state.advanceTimeSeconds(900);
        for (int index = 0; index < 3; index++) {
            state.getRedTeamState().getPlayers().get(index).markDead(900, 60);
        }
        CountingRandom random = new CountingRandom();

        assertThat(new TeamfightResolver().maybeResolveTeamfight(
                state, blue, red, random, new java.util.ArrayList<>())).isEmpty();
        assertThat(random.calls).isZero();
    }

    @Test
    void aliveRoamerCannotSatisfyStandardTeamfightMinimumOrConsumeRandom() {
        DummyDataFactory data = new DummyDataFactory();
        Team blue = data.createBlueTeam();
        Team red = data.createRedTeam();
        GameState state = new GameState(teamState(blue, TeamSide.BLUE),
                teamState(red, TeamSide.RED));
        state.advanceTimeSeconds(900);
        List<PlayerState> redPlayers = state.getRedTeamState().getPlayers();
        redPlayers.get(3).markDead(900, 60);
        redPlayers.get(4).markDead(900, 60);
        redPlayers.getFirst().beginRoamActivity(Lane.TOP, Lane.MID, 900);
        CountingRandom random = new CountingRandom();
        java.util.ArrayList<MatchEvent> events = new java.util.ArrayList<>();

        assertThat(new TeamfightResolver().maybeResolveTeamfight(
                state, blue, red, random, events)).isEmpty();
        assertThat(random.calls).isZero();
        assertThat(events).isEmpty();
        assertThat(state.getCompositionRuntimeState().lastActualAttemptId()).isNull();
    }

    @Test
    void forcedTeamfightWithOnlyRoamingSurvivorCreatesNoZeroKillAttempt() {
        DummyDataFactory data = new DummyDataFactory();
        Team blue = data.createBlueTeam();
        Team red = data.createRedTeam();
        GameState state = new GameState(teamState(blue, TeamSide.BLUE),
                teamState(red, TeamSide.RED));
        state.advanceTimeSeconds(1_800);
        List<PlayerState> bluePlayers = state.getBlueTeamState().getPlayers();
        for (int index = 1; index < bluePlayers.size(); index++) {
            bluePlayers.get(index).markDead(1_800, 60);
        }
        bluePlayers.getFirst().beginRoamActivity(Lane.TOP, Lane.MID, 1_800);
        CountingRandom random = new CountingRandom();
        java.util.ArrayList<MatchEvent> events = new java.util.ArrayList<>();

        assertThat(new TeamfightResolver().resolveForcedTeamfight(
                state, blue, red, random, events, CombatSource.LATE_GAME_SIEGE)).isEmpty();
        assertThat(random.calls).isZero();
        assertThat(events).isEmpty();
        assertThat(state.getCompositionRuntimeState().lastActualAttemptId()).isNull();
    }

    @Test
    void genericSkirmishWithoutAnyEligibleLaneConsumesNothing() {
        DummyDataFactory data = new DummyDataFactory();
        Team blue = data.createBlueTeam();
        Team red = data.createRedTeam();
        GameState state = new GameState(teamState(blue, TeamSide.BLUE),
                teamState(red, TeamSide.RED));
        state.advanceTimeSeconds(600);
        for (PlayerState player : state.getBlueTeamState().getPlayers()) {
            player.beginRoamActivity(Lane.TOP, Lane.MID, 600);
        }
        MatchSimulator simulator = simulator();
        CountingRandom random = new CountingRandom();
        java.util.ArrayList<MatchEvent> events = new java.util.ArrayList<>();

        assertThat(simulator.eligibleLocalizedSkirmishLanes(state)).isEmpty();
        assertThat(simulator.maybeCreateKillEvent(random, blue, red, state, events)).isFalse();
        assertThat(random.calls).isZero();
        assertThat(events).isEmpty();
        assertThat(state.getCompositionRuntimeState().lastActualAttemptId()).isNull();
    }

    @Test
    void genericSkirmishSelectsOnlyEligibleLaneWithoutRejectedLaneDraws() {
        DummyDataFactory data = new DummyDataFactory();
        Team blue = data.createBlueTeam();
        Team red = data.createRedTeam();
        GameState state = new GameState(teamState(blue, TeamSide.BLUE),
                teamState(red, TeamSide.RED));
        state.advanceTimeSeconds(600);
        for (TeamSide side : TeamSide.values()) {
            for (PlayerState player : state.getTeamState(side).getPlayers()) {
                if (player.getPosition() != Position.TOP) {
                    player.beginRoamActivity(Lane.TOP, Lane.MID, 600);
                }
            }
        }
        MatchSimulator simulator = simulator();
        CountingRandom random = new CountingRandom();
        java.util.ArrayList<MatchEvent> events = new java.util.ArrayList<>();

        assertThat(simulator.eligibleLocalizedSkirmishLanes(state)).containsExactly(Lane.TOP);
        assertThat(simulator.maybeCreateKillEvent(random, blue, red, state, events)).isTrue();
        assertThat(events).filteredOn(event -> event.getType() == com.lolfm.domain.MatchEventType.KILL)
                .singleElement().satisfies(event -> assertThat(event.getCombatLane()).isEqualTo(Lane.TOP));
        assertThat(random.calls).isEqualTo(5);
    }

    @Test
    void completedTimelinesAreMonotonicAndContainNoPostGameEvents() {
        DummyDataFactory data = new DummyDataFactory();
        MatchSimulator simulator = new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
        for (long seed = 1; seed <= 12; seed++) {
            MatchTimeline timeline = simulator.simulate(
                    data.createBlueTeam(), data.createRedTeam(), seed);
            int previous = -1;
            for (MatchEvent event : timeline.getEvents()) {
                assertThat(event.getTimeSeconds()).isBetween(previous, timeline.getDurationSeconds());
                previous = event.getTimeSeconds();
            }
        }
    }

    private static final class ZeroRandom extends Random {
        @Override public double nextDouble() { return 0; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public boolean nextBoolean() { return false; }
    }

    private static final class CountingRandom extends Random {
        private int calls;
        @Override public double nextDouble() { calls++; return 0; }
        @Override public int nextInt(int bound) { calls++; return 0; }
        @Override public boolean nextBoolean() { calls++; return false; }
    }

    private TeamState teamState(Team team, TeamSide side) {
        return new TeamState(team.getName(), team.getPlayers().stream()
                .map(player -> new PlayerState(new PlayerKey(side, player.getPosition()),
                        player.requirePlayerId(), player.getName(), player.getPosition(),
                        player.getAttributes(), null, 500, true))
                .toList());
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
    }
}

package com.lolfm.simulator;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LckRealTeamMatchIntegrationTest {
    private static final long SEED = 73L;

    @Test
    void realGenAndT1ReachMatchSimulatorAndReplayExactly() {
        LckTeamAssembler assembler = LckTeamAssembler.loadDefault();
        Team gen = assembler.assemble("GEN");
        Team t1 = assembler.assemble("T1");
        MatchChampionAssignments assignments = assignments();
        MatchSimulator simulator = simulator();

        MatchTimeline first = simulator.simulate(gen, t1, SEED, assignments);
        MatchTimeline replay = simulator.simulate(gen, t1, SEED, assignments);

        assertThat(first.getDurationSeconds()).isPositive()
                .isLessThanOrEqualTo(MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS);
        assertThat(first.getWinner()).isIn("GEN", "T1");
        assertThat(first.getEvents()).isNotEmpty();
        assertThat(first.getSnapshots()).isNotEmpty();
        assertCompleteTimelineEquals(first, replay);

        Set<String> playerIds = new HashSet<>();
        Set<String> displayNames = new HashSet<>();
        for (Player player : concat(gen, t1)) {
            playerIds.add(player.requirePlayerId().value());
            displayNames.add(player.getName());
            assertThat(player.getRatings()).isNotNull();
            assertThat(player.getChampionProficiencies()).isNotNull();
        }
        assertThat(playerIds).hasSize(10);
        List<MatchEvent> kills = first.getEvents().stream()
                .filter(event -> event.getType() == MatchEventType.KILL).toList();
        assertThat(kills).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getKillerPlayerId()).isIn(playerIds);
            assertThat(event.getVictimPlayerId()).isIn(playerIds);
            assertThat(event.getAssistPlayerIds()).allMatch(playerIds::contains);
            assertThat(event.getKiller()).isIn(displayNames);
            assertThat(event.getVictim()).isIn(displayNames);
            assertThat(event.getKillerPlayerId()).isNotEqualTo(event.getKiller());
            assertThat(event.getVictimPlayerId()).isNotEqualTo(event.getVictim());
        });
    }

    @Test
    void matchStateUsesPlayerKeyAndStablePersonIdentitySeparately() {
        PlayerKey key = new PlayerKey(TeamSide.BLUE, Position.MID);
        PlayerId playerId = new PlayerId("player-chovy");
        PlayerState state = new PlayerState(key, playerId, "Chovy", Position.MID,
                new com.lolfm.domain.PlayerAttributes(14, 14, 14, 14), null, 500, true);
        TeamState team = new TeamState("GEN", List.of(state));

        assertThat(team.player(key)).isSameAs(state);
        assertThat(state.getPlayerKey()).isEqualTo(key);
        assertThat(state.requirePlayerId()).isEqualTo(playerId);
        assertThat(state.getStructuredPlayerId()).isEqualTo("player-chovy");
        assertThat(java.util.Arrays.stream(TeamState.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)).doesNotContain("getPlayerState");
    }

    @Test
    void legacyDummyBoundaryUsesExplicitFixtureIds() {
        DummyDataFactory factory = new DummyDataFactory();
        assertThat(concat(factory.createBlueTeam(), factory.createRedTeam()))
                .allSatisfy(player -> {
                    assertThat(player.isLegacyProfile()).isTrue();
                    assertThat(player.hasStablePlayerId()).isTrue();
                    assertThat(player.requirePlayerId().value()).startsWith("player-fixture-");
                });
    }

    private MatchChampionAssignments assignments() {
        EnumMap<Position, String> blue = lineup("renekton", "sejuani", "azir", "jinx", "nautilus");
        EnumMap<Position, String> red = lineup("jax", "lee-sin", "ahri", "kaisa", "rakan");
        List<ChampionAssignment> values = new ArrayList<>();
        for (Position position : Position.values()) {
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.BLUE, position),
                    new ChampionId(blue.get(position)), position));
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.RED, position),
                    new ChampionId(red.get(position)), position));
        }
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    private EnumMap<Position, String> lineup(String top, String jungle, String mid,
                                             String adc, String support) {
        EnumMap<Position, String> values = new EnumMap<>(Position.class);
        values.put(Position.TOP, top);
        values.put(Position.JUNGLE, jungle);
        values.put(Position.MID, mid);
        values.put(Position.ADC, adc);
        values.put(Position.SUPPORT, support);
        return values;
    }

    private List<Player> concat(Team first, Team second) {
        ArrayList<Player> players = new ArrayList<>(first.getPlayers());
        players.addAll(second.getPlayers());
        return players;
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
    }
}

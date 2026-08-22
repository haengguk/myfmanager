package com.lolfm.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.CombatSource;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.EndGameEvaluator;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.ObjectiveAttemptResolver;
import com.lolfm.simulator.ObjectiveResolver;
import com.lolfm.simulator.PostFightResolver;
import com.lolfm.simulator.PushResolver;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SnapshotFactory;
import com.lolfm.simulator.StructureResolver;
import com.lolfm.simulator.TeamfightResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CompleteTimelineAssertionsTest {
    private static MatchTimeline baseline;

    @BeforeAll
    static void createCompleteTimelineFixture() {
        DummyDataFactory teams = new DummyDataFactory();
        baseline = simulator().simulate(teams.createBlueTeam(), teams.createRedTeam(), 73L);
    }

    @Test
    void canonicalHashDetectsTopLevelValueAndCollectionOrderMutations() {
        assertHashChanges(copyTimeline(
                baseline.getDurationSeconds() + 1, baseline.getWinner(),
                baseline.getEvents(), baseline.getSnapshots()));
        assertHashChanges(copyTimeline(
                baseline.getDurationSeconds(), baseline.getWinner() + "-mutated",
                baseline.getEvents(), baseline.getSnapshots()));

        List<MatchEvent> removedEvent = new ArrayList<>(baseline.getEvents());
        removedEvent.removeLast();
        assertHashChanges(copyTimeline(baseline.getDurationSeconds(), baseline.getWinner(),
                removedEvent, baseline.getSnapshots()));

        List<MatchEvent> reorderedEvents = new ArrayList<>(baseline.getEvents());
        MatchEvent firstEvent = reorderedEvents.removeFirst();
        reorderedEvents.add(1, firstEvent);
        assertHashChanges(copyTimeline(baseline.getDurationSeconds(), baseline.getWinner(),
                reorderedEvents, baseline.getSnapshots()));

        List<MatchSnapshot> reorderedSnapshots = new ArrayList<>(baseline.getSnapshots());
        MatchSnapshot firstSnapshot = reorderedSnapshots.removeFirst();
        reorderedSnapshots.add(1, firstSnapshot);
        assertHashChanges(copyTimeline(baseline.getDurationSeconds(), baseline.getWinner(),
                baseline.getEvents(), reorderedSnapshots));
    }

    @Test
    void canonicalHashDetectsStructuredEventAndStableParticipantIdentityMutations() {
        MatchEvent original = killEvent("player-blue-top");
        MatchEvent changedPlayerId = killEvent("player-blue-jungle");
        MatchEvent changedCombatSource = killEvent("player-blue-top");
        changedCombatSource.setCombatSource(CombatSource.TEAMFIGHT);

        MatchTimeline source = new MatchTimeline(120, "BLUE", List.of(original), List.of());
        assertThat(CompleteTimelineAssertions.canonicalHash(source))
                .isNotEqualTo(CompleteTimelineAssertions.canonicalHash(
                        new MatchTimeline(120, "BLUE", List.of(changedPlayerId), List.of())))
                .isNotEqualTo(CompleteTimelineAssertions.canonicalHash(
                        new MatchTimeline(120, "BLUE", List.of(changedCombatSource), List.of())));

        JsonNode event = CompleteTimelineAssertions.canonicalTree(source).at("/events/0");
        assertThat(event.has("killerPlayerId")).isTrue();
        assertThat(event.has("victimPlayerId")).isTrue();
        assertThat(event.has("assistPlayerIds")).isTrue();
        assertThat(event.has("combatSource")).isTrue();
    }

    @Test
    void canonicalHashDetectsPlayerEconomyObjectiveAndStructureMutations() {
        MatchSnapshot source = baseline.getSnapshots().getFirst();
        MatchSnapshot teamGold = copySnapshot(source, source.getBlueGold() + 1,
                source.getBlueDragons(), source.getBlueTowersDestroyed(),
                source.getPlayerSnapshots());
        MatchSnapshot objective = copySnapshot(source, source.getBlueGold(),
                source.getBlueDragons() + 1, source.getBlueTowersDestroyed(),
                source.getPlayerSnapshots());
        MatchSnapshot structure = copySnapshot(source, source.getBlueGold(),
                source.getBlueDragons(), source.getBlueTowersDestroyed() + 1,
                source.getPlayerSnapshots());

        List<PlayerSnapshot> changedPlayers = new ArrayList<>(source.getPlayerSnapshots());
        changedPlayers.set(0, copyPlayerWithGold(
                changedPlayers.getFirst(), changedPlayers.getFirst().getGold() + 1));
        MatchSnapshot playerGold = copySnapshot(source, source.getBlueGold(),
                source.getBlueDragons(), source.getBlueTowersDestroyed(), changedPlayers);

        String sourceHash = snapshotHash(source);
        assertThat(snapshotHash(teamGold)).isNotEqualTo(sourceHash);
        assertThat(snapshotHash(objective)).isNotEqualTo(sourceHash);
        assertThat(snapshotHash(structure)).isNotEqualTo(sourceHash);
        assertThat(snapshotHash(playerGold)).isNotEqualTo(sourceHash);

        JsonNode snapshot = CompleteTimelineAssertions.canonicalTree(
                new MatchTimeline(source.getTimeSeconds(), "BLUE", List.of(), List.of(source)))
                .at("/snapshots/0");
        assertThat(snapshot.has("blueGold")).isTrue();
        assertThat(snapshot.has("blueDragons")).isTrue();
        assertThat(snapshot.has("blueTowersDestroyed")).isTrue();
        assertThat(snapshot.at("/playerSnapshots/0").has("gold")).isTrue();
        assertThat(snapshot.at("/playerSnapshots/0").has("progression")).isTrue();
    }

    private static void assertHashChanges(MatchTimeline mutated) {
        assertThat(CompleteTimelineAssertions.canonicalHash(mutated))
                .isNotEqualTo(CompleteTimelineAssertions.canonicalHash(baseline));
    }

    private static MatchTimeline copyTimeline(
            int duration,
            String winner,
            List<MatchEvent> events,
            List<MatchSnapshot> snapshots
    ) {
        return new MatchTimeline(duration, winner, events, snapshots);
    }

    private static MatchEvent killEvent(String killerPlayerId) {
        MatchEvent event = new MatchEvent(120, MatchEventType.KILL, "structured kill",
                "Blue Top", "Red Top", List.of("Blue Jungle"), 300, 25.0);
        event.setParticipantPlayerIds(killerPlayerId, "player-red-top",
                List.of("player-blue-jungle"));
        event.setCombatSource(CombatSource.LANE_COMBAT);
        return event;
    }

    private static String snapshotHash(MatchSnapshot snapshot) {
        return CompleteTimelineAssertions.canonicalHash(
                new MatchTimeline(snapshot.getTimeSeconds(), "BLUE", List.of(), List.of(snapshot)));
    }

    private static MatchSnapshot copySnapshot(
            MatchSnapshot source,
            int blueGold,
            int blueDragons,
            int blueTowers,
            List<PlayerSnapshot> players
    ) {
        MatchSnapshot copy = new MatchSnapshot(
                source.getTimeSeconds(), source.getBlueKills(), source.getRedKills(),
                blueGold, source.getRedGold(), blueDragons, source.getRedDragons(),
                source.isBlueHasDragonSoul(), source.isRedHasDragonSoul(),
                source.isBlueHasBaronBuff(), source.isRedHasBaronBuff(), source.isElderAlive(),
                source.isBlueHasElderBuff(), source.isRedHasElderBuff(),
                source.getBlueElderBuffRemainingSeconds(), source.getRedElderBuffRemainingSeconds(),
                blueTowers, source.getRedTowersDestroyed(), source.getBlueInhibitorsRemaining(),
                source.getRedInhibitorsRemaining(), source.getBlueNexusTurretsRemaining(),
                source.getRedNexusTurretsRemaining(), source.isBlueNexusAlive(),
                source.isRedNexusAlive(), source.getBlueAlivePlayers(), source.getRedAlivePlayers(),
                players, source.getLaneSnapshots(), source.getObjectivePriority(),
                source.getLanePhase(), source.getMidGameMacro(), source.getObjectiveDecision(),
                source.getLateGame());
        copy.setProgression(source.getProgression());
        return copy;
    }

    private static PlayerSnapshot copyPlayerWithGold(PlayerSnapshot source, int gold) {
        return new PlayerSnapshot(
                source.getPlayerName(), source.getTeamName(), source.getTeamSide(),
                source.getPosition(), source.getKills(), source.getDeaths(), source.getAssists(),
                source.getCs(), gold, source.isAlive(), source.getRespawnAtSeconds(),
                source.getRespawnRemainingSeconds(), source.isCanFarm(),
                source.getFarmResumeAtSeconds(), source.getFarmReturnSecondsRemaining(),
                source.isHasElderBuff(), source.getElderBuffRemainingSeconds(),
                source.getShutdownBountyGold(), source.isHasShutdownBounty(),
                source.getTotalShutdownGoldEarned(), source.getTotalShutdownGoldGiven(),
                source.getBountyProgress(), source.getActivityType(), source.getActivityOriginLane(),
                source.getActivityTargetLane(), source.getActivityUntilSeconds(),
                source.getActivitySecondsRemaining(), source.getRoamFarmBlockedUntilSeconds(),
                source.getProgression(), source.getChampion());
    }

    private static MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
                new SnapshotFactory(), new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults());
    }
}

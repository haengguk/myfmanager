package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

class MatchSimulatorSmokeTest {

    private final MatchSimulator matchSimulator = new MatchSimulator(
            new TeamfightResolver(),
            new EndGameEvaluator(),
            new SnapshotFactory(),
            new ObjectiveResolver(),
            new PostFightResolver(),
            new ObjectiveAttemptResolver(),
            new StructureResolver(),
            new PushResolver()
    );
    private final DummyDataFactory dummyDataFactory = new DummyDataFactory();

    @Test
    void simulateCreatesTimelineWithStableGameEnd() {
        MatchTimeline timeline = simulate(12_345L);

        assertTrue(timeline.getDurationSeconds() > 0);
        assertTrue(timeline.getDurationSeconds() <= MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS);
        assertFalse(timeline.getSnapshots().isEmpty());
        assertFalse(timeline.getEvents().isEmpty());
        List<MatchEvent> events = timeline.getEvents();
        MatchEvent lastEvent = events.get(events.size() - 1);
        assertEquals(MatchEventType.GAME_END, lastEvent.getType());
        if (timeline.getWinner() == null) {
            assertTrue(lastEvent.getMessage().contains("안전 제한"));
        } else {
            assertFalse(timeline.getWinner().isBlank());
            assertTrue(lastEvent.getMessage().contains(timeline.getWinner()));
        }
    }

    @Test
    void teamfightVictimDoesNotRepeatWithinSingleFight() {
        MatchTimeline timeline = findTimelineWithTeamfight();
        Set<String> deadPlayers = new HashSet<>();
        boolean inTeamfight = false;

        for (MatchEvent event : timeline.getEvents()) {
            MatchEventType type = event.getType();

            if (type == MatchEventType.TEAMFIGHT) {
                inTeamfight = true;
                deadPlayers.clear();
                continue;
            }

            if (!inTeamfight) {
                continue;
            }

            if (type == MatchEventType.KILL) {
                assertNotNull(event.getKiller());
                assertNotNull(event.getVictim());
                assertNotEquals(event.getKiller(), event.getVictim());
                assertTrue(
                        deadPlayers.add(event.getVictim()),
                        () -> "Duplicated victim inside one teamfight: " + event.getVictim()
                );
            }

            if (type == MatchEventType.TEAMFIGHT_RESULT || type == MatchEventType.ACE || type == MatchEventType.GAME_END) {
                inTeamfight = false;
                deadPlayers.clear();
            }
        }
    }

    @Test
    @Tag("diagnostic")
    @Tag("simulation-distribution")
    void sampledMatchesCanFinishBeforeFortyMinutesWithoutEndingTooEarly() {
        boolean foundEarlyFinish = false;

        for (long seed = 1; seed <= 120; seed++) {
            MatchTimeline timeline = simulate(seed);
            assertTrue(timeline.getDurationSeconds() >= 900, "Match ended before 15 minutes for seed " + seed);
            if (timeline.getDurationSeconds() < 2_400) {
                foundEarlyFinish = true;
            }
        }

        assertTrue(foundEarlyFinish, "Expected at least one sampled match to finish before 40 minutes.");
    }

    @Test
    void playerStateUsesConfiguredRespawnWindows() {
        PlayerState playerState = new PlayerState("Atlas", com.lolfm.domain.Position.TOP, 500);

        playerState.markDead(599, 10);
        assertFalse(playerState.isAlive(608));
        assertTrue(playerState.isAlive(609));

        playerState = new PlayerState("Atlas", com.lolfm.domain.Position.TOP, 500);
        playerState.markDead(600, 20);
        assertFalse(playerState.isAlive(619));
        assertTrue(playerState.isAlive(620));

        playerState = new PlayerState("Atlas", com.lolfm.domain.Position.TOP, 500);
        playerState.markDead(1_200, 35);
        assertFalse(playerState.isAlive(1_234));
        assertTrue(playerState.isAlive(1_235));

        playerState = new PlayerState("Atlas", com.lolfm.domain.Position.TOP, 500);
        playerState.markDead(1_800, 50);
        assertFalse(playerState.isAlive(1_849));
        assertTrue(playerState.isAlive(1_850));
    }

    @Test
    void deadPlayerIsExcludedUntilRespawnAndCanParticipateAfterward() {
        Team blueTeam = dummyDataFactory.createBlueTeam();
        Team redTeam = dummyDataFactory.createRedTeam();
        TeamState blueState = createTeamState(blueTeam);
        TeamState redState = createTeamState(redTeam);
        Player returningPlayer = redTeam.getPlayers().get(0);
        Player blueTarget = blueTeam.getPlayers().get(0);
        List<MatchEvent> events = new ArrayList<>();
        TeamfightResolver resolver = new TeamfightResolver();

        markAllButAlive(blueState, blueTarget.getName(), 1_200, 9_999);
        markAllButAlive(redState, returningPlayer.getName(), 1_200, 9_999);
        PlayerState returningState = redState.playerAt(returningPlayer.getPosition());
        returningState.markDead(1_200, 35);

        assertFalse(resolver.resolveKill(
                1_234, new Random(1L), redTeam, redState, blueTeam, blueState, events, false, new HashSet<>()
        ));
        assertFalse(resolver.resolveKill(
                1_234, new Random(2L), blueTeam, blueState, redTeam, redState, events, false, new HashSet<>()
        ));

        assertTrue(resolver.resolveKill(
                1_235, new Random(3L), redTeam, redState, blueTeam, blueState, events, false, new HashSet<>()
        ));
        MatchEvent respawnedPlayerKill = events.get(events.size() - 1);
        assertEquals(returningPlayer.getName(), respawnedPlayerKill.getKiller());
        assertFalse(respawnedPlayerKill.getAssists().contains(returningPlayer.getName()));
    }

    @Test
    void allGeneratedKillParticipantsRespectRespawnWindows() {
        for (long seed = 1; seed <= 120; seed++) {
            MatchTimeline timeline = simulate(seed);
            Map<String, Integer> respawnAtByPlayer = new HashMap<>();

            for (MatchEvent event : timeline.getEvents()) {
                if (event.getType() != MatchEventType.KILL) {
                    continue;
                }

                assertAvailable(event.getKiller(), event.getTimeSeconds(), respawnAtByPlayer, "killer");
                for (String assist : event.getAssists()) {
                    assertAvailable(assist, event.getTimeSeconds(), respawnAtByPlayer, "assist");
                }
                assertAvailable(event.getVictim(), event.getTimeSeconds(), respawnAtByPlayer, "victim");
                respawnAtByPlayer.put(event.getVictim(), event.getTimeSeconds() + respawnDelayAt(event.getTimeSeconds()));
            }
        }
    }

    @Test
    void sameSeedProducesIdenticalTimeline() {
        MatchTimeline first = simulate(98_765L);
        MatchTimeline second = simulate(98_765L);

        assertEquals(first.getDurationSeconds(), second.getDurationSeconds());
        assertEquals(first.getWinner(), second.getWinner());
        assertEquals(first.getSnapshots().size(), second.getSnapshots().size());
        assertEquals(eventSignatures(first), eventSignatures(second));
        assertEquals(snapshotSignatures(first), snapshotSignatures(second));
    }


    @Test
    @Tag("diagnostic")
    @Tag("simulation-distribution")
    void nexusEndStatisticsAcrossOneThousandSeeds() {
        int nexusEnds = 0;
        int timeouts = 0;
        int totalDuration = 0;
        int before30 = 0;
        int from30To40 = 0;
        int from40To50 = 0;
        int from50To60 = 0;
        int after60 = 0;
        int after70 = 0;
        int totalWindowStructures = 0;
        int totalWindows = 0;
        int aceMatches = 0;
        int aceWindowNexusEnds = 0;
        int totalDestroyedInhibitors = 0;
        int totalDestroyedNexusTurrets = 0;
        List<Integer> durations = new ArrayList<>();
        String firstTimeoutDiagnostic = null;

        for (long seed = 1; seed <= 1_000; seed++) {
            MatchSimulator.SimulationResult result = simulateWithDiagnostics(seed);
            MatchTimeline timeline = result.timeline();
            com.lolfm.domain.MatchSnapshot last = timeline.getSnapshots().get(timeline.getSnapshots().size() - 1);
            int duration = timeline.getDurationSeconds();
            durations.add(duration);
            totalDuration += duration;
            totalDestroyedInhibitors += 6 - last.getBlueInhibitorsRemaining() - last.getRedInhibitorsRemaining();
            totalWindowStructures += result.pushWindowStructureCount();
            totalWindows += result.pushWindowCount();
            aceWindowNexusEnds += result.aceWindowNexusEndCount();
            if (timeline.getEvents().stream().anyMatch(event -> event.getType() == MatchEventType.ACE)) aceMatches++;
            totalDestroyedNexusTurrets += 4 - last.getBlueNexusTurretsRemaining() - last.getRedNexusTurretsRemaining();

            if (result.endReason() == GameEndReason.NEXUS_DESTROYED) nexusEnds++;
            else {
                timeouts++;
                if (firstTimeoutDiagnostic == null) {
                    firstTimeoutDiagnostic = "seed=" + seed
                            + " blueInhibitors=" + last.getBlueInhibitorsRemaining()
                            + " redInhibitors=" + last.getRedInhibitorsRemaining()
                            + " blueNexusTurrets=" + last.getBlueNexusTurretsRemaining()
                            + " redNexusTurrets=" + last.getRedNexusTurretsRemaining()
                            + " blueNexusAlive=" + last.isBlueNexusAlive()
                            + " redNexusAlive=" + last.isRedNexusAlive()
                            + " pushAttempts=" + result.pushAttempts()
                            + " pushSuccesses=" + result.pushSuccesses()
                            + " pushFailures=" + result.pushFailureCounts();
                }
            }
            if (duration < 1_800) before30++;
            else if (duration < 2_400) from30To40++;
            else if (duration < 3_000) from40To50++;
            else if (duration < 3_600) from50To60++;
            else {
                after60++;
                if (duration >= 4_200) after70++;
            }
        }

        durations.sort(Integer::compareTo);
        System.out.printf(
                "END_STATS nexusRate=%.3f timeoutRate=%.3f averageDuration=%.1f medianDuration=%d p90Duration=%d p95Duration=%d before30=%.3f from30To40=%.3f from40To50=%.3f from50To60=%.3f after60=%.3f after70=%.3f maxDuration=%d averageWindowStructuresPerGame=%.2f averageWindowStructures=%.2f aceNexusEndRate=%.3f averageInhibitors=%.2f averageNexusTurrets=%.2f timeoutDiagnostic=%s%n",
                nexusEnds / 1_000.0, timeouts / 1_000.0, totalDuration / 1_000.0,
                percentile(durations, 0.50), percentile(durations, 0.90), percentile(durations, 0.95),
                before30 / 1_000.0, from30To40 / 1_000.0, from40To50 / 1_000.0,
                from50To60 / 1_000.0, after60 / 1_000.0, after70 / 1_000.0, durations.getLast(),
                totalWindowStructures / 1_000.0, totalWindows == 0 ? 0.0 : totalWindowStructures / (double) totalWindows,
                aceMatches == 0 ? 0.0 : aceWindowNexusEnds / (double) aceMatches,
                totalDestroyedInhibitors / 1_000.0, totalDestroyedNexusTurrets / 1_000.0,
                firstTimeoutDiagnostic == null ? "none" : firstTimeoutDiagnostic
        );
        assertEquals(1_000, nexusEnds + timeouts);
    }

    @Test
    void inhibitorAndBaseStructuresFollowTheRequiredOrder() {
        GameState state = createGameState();
        StructureResolver resolver = new StructureResolver();
        assertTrue(resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.MACRO_PLAY).isPresent());
        assertTrue(resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.MACRO_PLAY).isPresent());
        assertTrue(resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.MACRO_PLAY).isPresent());
        assertTrue(state.getMapState().getLaneState(TeamSide.RED, Lane.MID).isInhibitorAlive());
        StructureOutcome inhibitor = resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT).orElseThrow();
        assertEquals(StructureKind.INHIBITOR, inhibitor.structureKind());
        assertTrue(state.getMapState().hasDestroyedInhibitor(TeamSide.RED));
        assertEquals(2, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());

        StructureOutcome firstTurret = resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT).orElseThrow();
        assertEquals(StructureKind.NEXUS_TURRET, firstTurret.structureKind());
        assertEquals(1, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());
        StructureOutcome secondTurret = resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT).orElseThrow();
        assertEquals(StructureKind.NEXUS_TURRET, secondTurret.structureKind());
        assertEquals(0, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());
        StructureOutcome nexus = resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT).orElseThrow();
        assertEquals(StructureKind.NEXUS, nexus.structureKind());
        assertTrue(nexus.gameEnded());
        assertTrue(state.isFinished());
        assertEquals(TeamSide.BLUE, state.getWinnerSide());
        assertFalse(state.getMapState().getBaseState(TeamSide.RED).isNexusAlive());
        assertTrue(resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT).isEmpty());
    }

    @Test
    void nexusTurretsAndNexusCannotBeDestroyedBeforeInhibitorProgress() {
        GameState state = createGameState();
        StructureResolver resolver = new StructureResolver();
        assertEquals(2, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());
        assertFalse(state.getMapState().areNexusTurretsVulnerable(TeamSide.RED));
        assertFalse(state.getMapState().isNexusVulnerable(TeamSide.RED));
        assertEquals(2, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());
        assertTrue(state.getMapState().getBaseState(TeamSide.RED).isNexusAlive());
        assertTrue(resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.TOP, PushReason.MACRO_PLAY).isPresent());
        assertEquals(2, state.getMapState().getBaseState(TeamSide.RED).getNexusTurretsRemaining());
    }

    @Test
    void fortyMinutesDoNotFinishAMatchWhileBothNexusesAreAlive() {
        GameState state = createGameState();
        state.advanceTimeSeconds(2_400);

        EndGameEvaluator.EndGameDecision decision = new EndGameEvaluator().evaluateAfterTick(state);

        assertFalse(decision.isFinished());
        assertFalse(state.isFinished());
        assertTrue(state.getMapState().getBaseState(TeamSide.BLUE).isNexusAlive());
        assertTrue(state.getMapState().getBaseState(TeamSide.RED).isNexusAlive());
    }

    @Test
    void matchContinuesPastFortyMinutesUntilANexusIsDestroyed() {
        MatchTimeline timeline = findTimelinePastFortyMinutes();

        assertTrue(timeline.getDurationSeconds() > 2_400);
        assertTrue(timeline.getSnapshots().stream().anyMatch(snapshot -> snapshot.getTimeSeconds() > 2_400));
        assertTrue(timeline.getEvents().stream().anyMatch(event -> event.getTimeSeconds() > 2_400));
        assertEquals(MatchEventType.GAME_END, timeline.getEvents().getLast().getType());
        assertTrue(timeline.getEvents().getLast().getMessage().contains(timeline.getWinner()));
    }

    @Test
    void nexusDestructionAfterFortyMinutesEndsImmediately() {
        GameState state = createGameState();
        state.advanceTimeSeconds(2_410);
        StructureResolver structures = new StructureResolver();
        for (int index = 0; index < 7; index++) {
            structures.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT);
        }

        EndGameEvaluator.EndGameDecision decision = new EndGameEvaluator().evaluateAfterTick(state);
        assertTrue(decision.isFinished());
        assertEquals(GameEndReason.NEXUS_DESTROYED, decision.getReason());
        assertEquals(2_410, state.getEndedAtSeconds());
    }

    @Test
    void safetyTimeoutDoesNotAssignWinnerOrNexusWin() {
        GameState state = createGameState();
        state.advanceTimeSeconds(MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS);

        EndGameEvaluator.EndGameDecision decision = new EndGameEvaluator().evaluateAfterTick(state);

        assertTrue(decision.isFinished());
        assertEquals(GameEndReason.SIMULATION_TIMEOUT, decision.getReason());
        assertEquals(null, decision.getWinner());
        assertTrue(state.getMapState().getBaseState(TeamSide.BLUE).isNexusAlive());
        assertTrue(state.getMapState().getBaseState(TeamSide.RED).isNexusAlive());
    }

    @Test
    void gameEndIsTheLastEventAfterNexusDestruction() {
        MatchTimeline timeline = simulate(1L);
        assertEquals(MatchEventType.GAME_END, timeline.getEvents().getLast().getType());
        assertTrue(timeline.getEvents().get(timeline.getEvents().size() - 2).getTimeSeconds()
                <= timeline.getEvents().getLast().getTimeSeconds());
    }

    @Test
    void thirtyKillsDoNotEndGameWhileNexusIsAlive() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        for (int index = 0; index < 31; index++) state.getBlueTeamState().addKill();
        EndGameEvaluator.EndGameDecision decision = new EndGameEvaluator().evaluateAfterTick(state);
        assertFalse(decision.isFinished());
        assertTrue(state.getMapState().getBaseState(TeamSide.BLUE).isNexusAlive());
        assertTrue(state.getMapState().getBaseState(TeamSide.RED).isNexusAlive());
    }

    @Test
    void nexusDestructionWinnerCannotBeOverturnedByScore() {
        GameState state = createGameState();
        for (int index = 0; index < 50; index++) state.getRedTeamState().addKill();
        StructureResolver resolver = new StructureResolver();
        for (int index = 0; index < 7; index++) resolver.destroyNextStructure(state, TeamSide.BLUE, Lane.TOP, PushReason.POST_FIGHT);
        EndGameEvaluator.EndGameDecision decision = new EndGameEvaluator().evaluateAfterTick(state);
        assertTrue(decision.isFinished());
        assertEquals(state.getBlueTeamState().getTeamName(), decision.getWinner());
        assertEquals(GameEndReason.NEXUS_DESTROYED, decision.getReason());
    }

    @Test
    void finalSnapshotReflectsDestroyedNexus() {
        GameState state = createGameState();
        StructureResolver resolver = new StructureResolver();
        for (int index = 0; index < 7; index++) resolver.destroyNextStructure(state, TeamSide.RED, Lane.BOT, PushReason.POST_FIGHT);
        com.lolfm.domain.MatchSnapshot snapshot = new SnapshotFactory().create(state);
        assertEquals(2, snapshot.getBlueInhibitorsRemaining());
        assertEquals(0, snapshot.getBlueNexusTurretsRemaining());
        assertFalse(snapshot.isBlueNexusAlive());
    }

    @Test
    void laneStructuresEnforceOuterInnerInhibitorOrder() {
        LaneStructureState structures = new LaneStructureState();
        assertFalse(structures.canDestroy(TowerTier.INNER));
        assertFalse(structures.canDestroy(TowerTier.INHIBITOR));
        structures.destroy(TowerTier.OUTER);
        assertFalse(structures.canDestroy(TowerTier.INHIBITOR));
        structures.destroy(TowerTier.INNER);
        assertTrue(structures.canDestroy(TowerTier.INHIBITOR));
        structures.destroy(TowerTier.INHIBITOR);
        assertTrue(structures.nextAliveTower().isEmpty());
        assertEquals(3, structures.destroyedTowerCount());
    }

    @Test
    void towerResolverUpdatesMapAndAttackingTeamExactlyOnce() {
        GameState state = createGameState();
        TowerResolver resolver = new TowerResolver();
        PushOutcome first = resolver.destroyNextTower(state, TeamSide.BLUE, Lane.MID, PushReason.MACRO_PLAY).orElseThrow();
        assertEquals(TowerTier.OUTER, first.destroyedTowerTier());
        assertEquals(1, state.getBlueTeamState().getTowersDestroyed());
        assertEquals(1, state.getMapState().getDestroyedTowerCountByAttackingSide(TeamSide.BLUE));
        assertTrue(resolver.destroyNextTower(state, TeamSide.BLUE, Lane.MID, PushReason.MACRO_PLAY).isPresent());
        assertEquals(2, state.getBlueTeamState().getTowersDestroyed());
    }

    @Test
    void activeBaronBuffExpiresAfterThreeMinutes() {
        GameState state = createGameState();
        TeamState blue = state.getBlueTeamState();
        blue.grantBaronBuff(1_200, 180);
        assertTrue(blue.hasActiveBaronBuff(1_379));
        state.advanceTimeSeconds(1_380);
        state.expireBaronBuffsIfNeeded();
        assertFalse(blue.hasActiveBaronBuff(state.getCurrentTimeSeconds()));
        assertFalse(blue.hasBaronBuff());
    }

    @Test
    void postFightPushRequiresTwoLivingAttackers() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        for (int index = 1; index < state.getBlueTeamState().getPlayers().size(); index++) {
            state.getBlueTeamState().getPlayers().get(index).markDead(1_200, 35);
        }
        TeamfightOutcome outcome = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, 1_200, List.of());
        assertTrue(new PushResolver().maybeResolvePostFightPush(
                state, java.util.Optional.of(outcome), forceSuccessfulRandom(1L), new StructureResolver()
        ).isEmpty());
    }

    @Test
    void bigWinAndAceHaveHigherPushChanceThanSmallWinAndBaronRaisesMacroChance() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        PushResolver resolver = new PushResolver();
        TeamfightOutcome small = new TeamfightOutcome(TeamSide.BLUE, FightGrade.SMALL_WIN, 1, 0, 1_200, List.of());
        TeamfightOutcome big = new TeamfightOutcome(TeamSide.BLUE, FightGrade.BIG_WIN, 4, 0, 1_200, List.of());
        TeamfightOutcome ace = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, 1_200, List.of());
        assertTrue(resolver.postFightChance(state, big) > resolver.postFightChance(state, small));
        assertTrue(resolver.postFightChance(state, ace) > resolver.postFightChance(state, big));
        double withoutBaron = resolver.macroPushChance(state, TeamSide.BLUE);
        state.getBlueTeamState().grantBaronBuff(1_200, 180);
        assertTrue(resolver.macroPushChance(state, TeamSide.BLUE) > withoutBaron);
    }

    @Test
    void acePushDestroysExactlyOneStructureWhenConditionsAreMet() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        TeamfightOutcome ace = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, 1_200, List.of());

        StructureOutcome outcome = new PushResolver().maybeResolvePostFightPush(
                state, java.util.Optional.of(ace), forceSuccessfulRandom(9L), new StructureResolver()
        ).orElseThrow();

        assertEquals(StructureKind.TOWER, outcome.structureKind());
        assertEquals(TowerTier.OUTER, outcome.towerTier());
        assertEquals(1, state.getMapState().getDestroyedTowerCountByAttackingSide(TeamSide.BLUE));
        assertTrue(state.getMapState().getLaneState(TeamSide.RED, outcome.lane()).isInnerTowerAlive());
    }

    @Test
    void lateGameRespawnDelaysIncreaseThroughFiftyMinutes() {
        TeamfightResolver resolver = new TeamfightResolver();
        assertEquals(50, resolver.calculateRespawnDelaySeconds(1_800));
        assertEquals(55, resolver.calculateRespawnDelaySeconds(2_100));
        assertEquals(60, resolver.calculateRespawnDelaySeconds(2_400));
        assertEquals(65, resolver.calculateRespawnDelaySeconds(2_700));
        assertEquals(70, resolver.calculateRespawnDelaySeconds(3_000));
    }

    @Test
    void bigWinPushWindowMutatesOnlyOneStructureInTheCurrentTick() {
        GameState state = createGameState();
        state.advanceTimeSeconds(2_700);
        StructureResolver structures = new StructureResolver();
        structures.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT);
        markAllPlayersDead(state.getRedTeamState(), 2_700, 65);
        TeamfightOutcome bigWin = new TeamfightOutcome(TeamSide.BLUE, FightGrade.BIG_WIN, 4, 0, 2_700, List.of());

        List<StructureOutcome> outcomes = new PushResolver().resolvePostFightWindow(
                state, java.util.Optional.of(bigWin), java.util.Optional.empty(), forceSuccessfulRandom(1L), structures
        );

        assertEquals(1, outcomes.size());
        assertEquals(Lane.MID, outcomes.get(0).lane());
        assertEquals(2_700, state.getCurrentTimeSeconds());
    }

    @Test
    void acePushWindowCannotInstantlyRemoveAnEntireBase() {
        GameState state = createGameState();
        state.advanceTimeSeconds(2_700);
        StructureResolver structures = new StructureResolver();
        for (int index = 0; index < 3; index++) {
            structures.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.POST_FIGHT);
        }
        markAllPlayersDead(state.getRedTeamState(), 2_700, 70);
        TeamfightOutcome ace = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, 2_700, List.of());

        List<StructureOutcome> outcomes = new PushResolver().resolvePostFightWindow(
                state, java.util.Optional.of(ace), java.util.Optional.empty(), forceSuccessfulRandom(2L), structures
        );

        assertEquals(List.of(StructureKind.INHIBITOR),
                outcomes.stream().map(StructureOutcome::structureKind).toList());
        assertFalse(state.isFinished());
        assertEquals(2_700, state.getCurrentTimeSeconds());
    }

    @Test
    void pushWindowStopsWhenThreeDefendersRespawnAndDeductsBaronTime() {
        GameState state = createGameState();
        state.advanceTimeSeconds(2_700);
        StructureResolver structures = new StructureResolver();
        markAllPlayersDead(state.getRedTeamState(), 2_700, 50);
        TeamfightOutcome ace = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, 2_700, List.of());
        MatchEvent baron = new MatchEvent(2_700, MatchEventType.BARON, "바론 확보", null, null, List.of());

        List<StructureOutcome> outcomes = new PushResolver().resolvePostFightWindow(
                state, java.util.Optional.of(ace), java.util.Optional.of(baron), forceSuccessfulRandom(3L), structures
        );

        assertTrue(outcomes.isEmpty());
        assertEquals(2_700, state.getCurrentTimeSeconds());
    }

    @Test
    void deeperLanesArePreferredAndInhibitorTargetsRemainPressureCandidates() {
        GameState state = createGameState();
        StructureResolver structures = new StructureResolver();
        for (int index = 0; index < 3; index++) {
            structures.destroyNextStructure(state, TeamSide.BLUE, Lane.MID, PushReason.MACRO_PLAY);
        }

        MapState map = state.getMapState();
        assertEquals(3, map.calculateLaneProgress(TeamSide.RED, Lane.MID));
        assertTrue(map.getPressureLanes(TeamSide.RED).contains(Lane.MID));
        assertTrue(map.getLaneState(TeamSide.RED, Lane.MID).isInhibitorVulnerable());
        assertTrue(new PushResolver().isDeepestLane(state, TeamSide.RED, Lane.MID));
        assertFalse(new PushResolver().isDeepestLane(state, TeamSide.RED, Lane.TOP));
    }

    @Test
    void inhibitorDestructionActivatesBasePressureAndShortensMacroInterval() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        StructureResolver structures = new StructureResolver();
        for (int index = 0; index < 4; index++) {
            structures.destroyNextStructure(state, TeamSide.BLUE, Lane.BOT, PushReason.POST_FIGHT);
        }

        PushResolver pushes = new PushResolver();
        assertTrue(state.getMapState().hasActiveBasePressure(TeamSide.BLUE, 1_200));
        assertEquals(PushRuleConfig.BASE_PRESSURE_ATTEMPT_INTERVAL_SECONDS, pushes.attemptInterval(state, TeamSide.BLUE, 1_200));
        assertFalse(state.getMapState().hasActiveBasePressure(TeamSide.BLUE, 1_320));
        assertEquals(PushRuleConfig.MACRO_ATTEMPT_INTERVAL_SECONDS, pushes.attemptInterval(state, TeamSide.BLUE, 1_320));
    }

    @Test
    void snapshotTowerCountMatchesMapProgress() {
        GameState state = createGameState();
        TowerResolver resolver = new TowerResolver();
        resolver.destroyNextTower(state, TeamSide.RED, Lane.TOP, PushReason.MACRO_PLAY);
        resolver.destroyNextTower(state, TeamSide.RED, Lane.TOP, PushReason.MACRO_PLAY);
        assertEquals(2, new SnapshotFactory().create(state).getRedTowersDestroyed());
    }

    @Test
    void simulationCreatesAtMostOneTowerEventPerTeamSidePerTick() {
        for (long seed = 1; seed <= 120; seed++) {
            Map<String, Integer> towersByTimeAndSide = new HashMap<>();
            List<MatchEvent> simulatedEvents = simulate(seed).getEvents();
            for (MatchEvent event : simulatedEvents) {
                if (event.getType() == MatchEventType.TOWER) {
                    String key = event.getTimeSeconds() + ":" + event.getStructureAttackingSide();
                    towersByTimeAndSide.merge(key, 1, Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> entry : towersByTimeAndSide.entrySet()) {
                String target = entry.getKey();
                assertTrue(entry.getValue() <= 1, "seed=" + seed + " key=" + entry
                        + " events=" + simulatedEvents.stream()
                        .filter(event -> event.getType() == MatchEventType.TOWER)
                        .filter(event -> (event.getTimeSeconds() + ":"
                                + event.getStructureAttackingSide()).equals(target))
                        .map(event -> event.getStructureKind() + ":"
                                + event.getStructureActionSource() + ":" + event.getStructureLane())
                        .toList());
            }
        }
    }

    @Test
    void snapshotReflectsDragonAndBaronStateAfterCapture() {
        GameState state = createGameState();
        SnapshotFactory snapshotFactory = new SnapshotFactory();
        ObjectiveResolver objectiveResolver = new ObjectiveResolver();

        state.advanceTimeSeconds(300);
        objectiveResolver.updateSpawnState(state);
        objectiveResolver.captureDragon(state, TeamSide.BLUE, 300);
        assertEquals(1, snapshotFactory.create(state).getBlueDragons());
        assertEquals(0, snapshotFactory.create(state).getRedDragons());

        state.advanceTimeSeconds(900);
        objectiveResolver.updateSpawnState(state);
        objectiveResolver.captureBaron(state, TeamSide.RED, 1_200);
        assertTrue(snapshotFactory.create(state).isRedHasBaronBuff());
        assertFalse(snapshotFactory.create(state).isBlueHasBaronBuff());
    }

    @Test
    void snapshotReflectsDeathAndRespawnStateAtCurrentTimeOnly() {
        GameState state = createGameState();
        SnapshotFactory snapshotFactory = new SnapshotFactory();
        state.advanceTimeSeconds(1_200);
        PlayerState player = state.getBlueTeamState().getPlayers().get(0);
        player.markDead(1_200, 35);

        com.lolfm.domain.PlayerSnapshot deadSnapshot = snapshotFactory.create(state).getPlayerSnapshots().get(0);
        assertFalse(deadSnapshot.isAlive());
        assertEquals(35, deadSnapshot.getRespawnRemainingSeconds());
        assertEquals(4, snapshotFactory.create(state).getBlueAlivePlayers());

        state.advanceTimeSeconds(35);
        com.lolfm.domain.PlayerSnapshot respawnedSnapshot = snapshotFactory.create(state).getPlayerSnapshots().get(0);
        assertTrue(respawnedSnapshot.isAlive());
        assertEquals(0, respawnedSnapshot.getRespawnRemainingSeconds());
        assertEquals(5, snapshotFactory.create(state).getBlueAlivePlayers());
    }

    @Test
    @Tag("diagnostic")
    @Tag("simulation-distribution")
    void objectiveAttemptStatisticsAcrossTwoHundredSeeds() {
        int matchesWithDragon = 0;
        int matchesWithBaron = 0;
        int totalDragons = 0;
        int firstDragonTotalSeconds = 0;
        int firstDragonCount = 0;

        for (long seed = 1; seed <= 200; seed++) {
            MatchTimeline timeline = simulate(seed);
            List<MatchEvent> dragons = timeline.getEvents().stream()
                    .filter(event -> event.getType() == MatchEventType.DRAGON)
                    .toList();
            boolean hasBaron = timeline.getEvents().stream()
                    .anyMatch(event -> event.getType() == MatchEventType.BARON);
            if (!dragons.isEmpty()) {
                matchesWithDragon++;
                totalDragons += dragons.size();
                firstDragonTotalSeconds += dragons.get(0).getTimeSeconds();
                firstDragonCount++;
            }
            if (hasBaron) matchesWithBaron++;
        }

        double dragonMatchRate = matchesWithDragon / 200.0;
        double averageDragons = totalDragons / 200.0;
        int averageFirstDragonTime = firstDragonCount == 0 ? -1 : firstDragonTotalSeconds / firstDragonCount;
        System.out.printf(
                "OBJECTIVE_STATS dragonRate=%.3f averageDragons=%.3f averageFirstDragon=%d baronRate=%.3f%n",
                dragonMatchRate, averageDragons, averageFirstDragonTime, matchesWithBaron / 200.0
        );
        assertTrue(dragonMatchRate >= 0.70, "Expected dragons in most sampled matches.");
        assertTrue(averageFirstDragonTime >= 360 && averageFirstDragonTime <= 900);
    }

    @Test
    void generalObjectiveAttemptCanCaptureDragonBeforeTeamfightsAreEnabled() {
        boolean foundEarlyDragon = false;
        for (long seed = 1; seed <= 200; seed++) {
            MatchTimeline timeline = simulate(seed);
            boolean earlyDragon = timeline.getEvents().stream()
                    .anyMatch(event -> event.getType() == MatchEventType.DRAGON && event.getTimeSeconds() < 900);
            if (earlyDragon) {
                foundEarlyDragon = true;
                break;
            }
        }
        assertTrue(foundEarlyDragon, "Expected a general dragon capture before the 15-minute teamfight window.");
    }

    @Test
    void generalAttemptDoesNotCaptureAnObjectiveThatIsNotAlive() {
        GameState state = createGameState();
        ObjectiveAttemptResolver attempts = new ObjectiveAttemptResolver();
        ObjectiveResolver objectives = new ObjectiveResolver();
        state.advanceTimeSeconds(360);

        assertTrue(attempts.maybeAttemptObjective(state, forceSuccessfulRandom(1L), objectives).isEmpty());
        assertFalse(state.getObjectiveState().isDragonAlive());
        assertFalse(state.getObjectiveState().isBaronAlive());
    }

    @Test
    void generalAttemptCannotCaptureBaronBeforeTwentyMinutes() {
        GameState state = createGameState();
        ObjectiveAttemptResolver attempts = new ObjectiveAttemptResolver();
        ObjectiveResolver objectives = new ObjectiveResolver();
        state.advanceTimeSeconds(1_190);
        objectives.updateSpawnState(state);

        java.util.Optional<MatchEvent> capture = attempts.maybeAttemptObjective(state, forceSuccessfulRandom(2L), objectives);
        assertTrue(capture.isEmpty() || capture.get().getType() != MatchEventType.BARON);
        assertFalse(state.getObjectiveState().isBaronAlive());
    }

    @Test
    void eachTimelineTickContainsAtMostOneMajorObjectiveCapture() {
        for (long seed = 1; seed <= 120; seed++) {
            Map<Integer, Integer> capturesByTime = new HashMap<>();
            for (MatchEvent event : simulate(seed).getEvents()) {
                if (event.getType() == MatchEventType.DRAGON || event.getType() == MatchEventType.BARON) {
                    capturesByTime.merge(event.getTimeSeconds(), 1, Integer::sum);
                }
            }
            for (Map.Entry<Integer, Integer> entry : capturesByTime.entrySet()) {
                assertTrue(entry.getValue() <= 1, "Multiple major objectives at " + entry.getKey());
            }
        }
    }

    @Test
    void eventTimesAlwaysHaveAValidSecondsComponent() {
        for (MatchEvent event : simulate(20L).getEvents()) {
            int secondsComponent = event.getTimeSeconds() % 60;
            assertTrue(secondsComponent >= 0 && secondsComponent <= 59);
        }
    }

    @Test
    void dragonSpawnRespawnAndDuplicateCaptureRulesAreApplied() {
        GameState state = createGameState();
        ObjectiveResolver resolver = new ObjectiveResolver();
        state.advanceTimeSeconds(299);
        resolver.updateSpawnState(state);
        assertFalse(state.getObjectiveState().isDragonAlive());
        state.advanceTimeSeconds(1);
        resolver.updateSpawnState(state);
        assertTrue(state.getObjectiveState().isDragonAlive());
        assertTrue(resolver.captureDragon(state, TeamSide.BLUE, 300).isPresent());
        assertFalse(state.getObjectiveState().isDragonAlive());
        assertEquals(600, state.getObjectiveState().getNextDragonSpawnSeconds());
        assertEquals(1, state.getBlueTeamState().getDragons());
        assertEquals(TeamSide.BLUE, state.getObjectiveState().getLastDragonSide());
        assertEquals(300, state.getObjectiveState().getLastDragonTimeSeconds());
        assertTrue(resolver.captureDragon(state, TeamSide.BLUE, 300).isEmpty());
        state.advanceTimeSeconds(299);
        resolver.updateSpawnState(state);
        assertFalse(state.getObjectiveState().isDragonAlive());
        state.advanceTimeSeconds(1);
        resolver.updateSpawnState(state);
        assertTrue(state.getObjectiveState().isDragonAlive());
    }

    @Test
    void baronSpawnRespawnAndTeamStateRulesAreApplied() {
        GameState state = createGameState();
        ObjectiveResolver resolver = new ObjectiveResolver();
        state.advanceTimeSeconds(1_199);
        resolver.updateSpawnState(state);
        assertFalse(state.getObjectiveState().isBaronAlive());
        state.advanceTimeSeconds(1);
        resolver.updateSpawnState(state);
        assertTrue(state.getObjectiveState().isBaronAlive());
        assertTrue(resolver.captureBaron(state, TeamSide.RED, 1_200).isPresent());
        assertFalse(state.getObjectiveState().isBaronAlive());
        assertEquals(1_560, state.getObjectiveState().getNextBaronSpawnSeconds());
        assertTrue(state.getRedTeamState().hasBaronBuff());
        assertFalse(state.getBlueTeamState().hasBaronBuff());
        assertEquals(TeamSide.RED, state.getObjectiveState().getLastBaronSide());
        assertEquals(1_200, state.getObjectiveState().getLastBaronTimeSeconds());
        assertTrue(resolver.captureBaron(state, TeamSide.RED, 1_200).isEmpty());
        state.advanceTimeSeconds(359);
        resolver.updateSpawnState(state);
        assertFalse(state.getObjectiveState().isBaronAlive());
        state.advanceTimeSeconds(1);
        resolver.updateSpawnState(state);
        assertTrue(state.getObjectiveState().isBaronAlive());
    }

    @Test
    void teamfightOutcomeMatchesItsGeneratedKillEvents() {
        Team blueTeam = dummyDataFactory.createBlueTeam();
        Team redTeam = dummyDataFactory.createRedTeam();
        GameState state = createGameState(blueTeam, redTeam);
        state.advanceTimeSeconds(900);
        List<MatchEvent> events = new ArrayList<>();
        TeamfightOutcome outcome = new TeamfightResolver().maybeResolveTeamfight(
                state, blueTeam, redTeam, forceTeamfightRandom(1L), events
        ).orElseThrow();

        Set<String> winners = namesFor(outcome.winningSide() == TeamSide.BLUE ? blueTeam : redTeam);
        int winningKills = 0;
        int losingKills = 0;
        for (MatchEvent event : events) {
            if (event.getType() == MatchEventType.KILL) {
                if (winners.contains(event.getKiller())) winningKills++; else losingKills++;
            }
        }
        assertEquals(winningKills, outcome.winningTeamKills());
        assertEquals(losingKills, outcome.losingTeamKills());
        assertEquals(expectedGrade(winningKills, losingKills), outcome.grade());
    }

    @Test
    void postFightCapturesAtMostOneMajorObjective() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        ObjectiveResolver resolver = new ObjectiveResolver();
        resolver.updateSpawnState(state);
        for (PlayerState player : state.getRedTeamState().getPlayers()) player.markDead(1_200, 35);
        TeamfightOutcome outcome = new TeamfightOutcome(
                TeamSide.BLUE, FightGrade.ACE, 5, 0, 1_200,
                state.getRedTeamState().getPlayers().stream().map(PlayerState::getPlayerName).toList()
        );
        java.util.Optional<MatchEvent> capture = new PostFightResolver().resolve(state, outcome, new Random(1L), resolver);
        assertTrue(capture.isPresent());
        assertTrue(capture.get().getType() == MatchEventType.DRAGON || capture.get().getType() == MatchEventType.BARON);
        assertTrue(state.getObjectiveState().isDragonAlive() || state.getObjectiveState().isBaronAlive());
        assertFalse(state.getObjectiveState().isDragonAlive() && state.getObjectiveState().isBaronAlive());
    }

    @Test
    void fewerThanTwoSurvivorsCannotSecureAnObjectiveAfterFight() {
        GameState state = createGameState();
        state.advanceTimeSeconds(1_200);
        ObjectiveResolver resolver = new ObjectiveResolver();
        resolver.updateSpawnState(state);
        for (int index = 1; index < state.getBlueTeamState().getPlayers().size(); index++) {
            state.getBlueTeamState().getPlayers().get(index).markDead(1_200, 35);
        }
        for (PlayerState player : state.getRedTeamState().getPlayers()) player.markDead(1_200, 35);
        TeamfightOutcome outcome = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, 1_200, List.of());
        assertTrue(new PostFightResolver().resolve(state, outcome, new Random(1L), resolver).isEmpty());
        assertTrue(state.getObjectiveState().isDragonAlive());
        assertTrue(state.getObjectiveState().isBaronAlive());
    }

    @Test
    void threeDragonsDoNotClaimSoulButFourthDragonDoes() {
        GameState state = createGameState();
        ObjectiveResolver resolver = new ObjectiveResolver();
        captureDragons(state, resolver, TeamSide.BLUE, 3);
        assertEquals(3, state.getBlueTeamState().getDragons());
        assertFalse(state.getBlueTeamState().hasDragonSoul());
        assertEquals(DragonPhase.ELEMENTAL, state.getObjectiveState().getDragonPhase());

        MatchEvent fourthDragon = captureNextDragon(state, resolver, TeamSide.BLUE);
        ObjectiveState objectives = state.getObjectiveState();
        assertEquals(4, state.getBlueTeamState().getDragons());
        assertTrue(state.getBlueTeamState().hasDragonSoul());
        assertFalse(state.getRedTeamState().hasDragonSoul());
        assertEquals(TeamSide.BLUE, objectives.getSoulOwner());
        assertEquals(DragonPhase.ELDER_PENDING, objectives.getDragonPhase());
        assertEquals(fourthDragon.getTimeSeconds(), objectives.getSoulClaimedAtSeconds());
        assertTrue(fourthDragon.getMessage().contains("영혼"));
    }

    @Test
    void dragonSoulHasSingleOwnerAndStopsFurtherDragonSpawns() {
        GameState state = createGameState();
        ObjectiveResolver resolver = new ObjectiveResolver();
        captureDragons(state, resolver, TeamSide.BLUE, 4);
        ObjectiveState objectives = state.getObjectiveState();
        objectives.claimSoul(TeamSide.RED, state.getCurrentTimeSeconds() + 10);
        state.advanceTimeSeconds(600);
        resolver.updateSpawnState(state);

        assertEquals(TeamSide.BLUE, objectives.getSoulOwner());
        assertTrue(state.getBlueTeamState().hasDragonSoul());
        assertFalse(state.getRedTeamState().hasDragonSoul());
        assertFalse(objectives.isDragonAlive());
        assertTrue(resolver.captureDragon(state, TeamSide.RED, state.getCurrentTimeSeconds()).isEmpty());
        assertEquals(4, state.getBlueTeamState().getDragons());
    }

    @Test
    void soulClaimStopsGeneralAndPostFightDragonSelection() {
        GameState state = createGameState();
        ObjectiveResolver resolver = new ObjectiveResolver();
        captureDragons(state, resolver, TeamSide.BLUE, 4);
        int dragonsBefore = state.getBlueTeamState().getDragons();
        java.util.Optional<MatchEvent> generalAttempt = new ObjectiveAttemptResolver().maybeAttemptObjective(state, forceSuccessfulRandom(1L), resolver);
        TeamfightOutcome outcome = new TeamfightOutcome(TeamSide.BLUE, FightGrade.ACE, 5, 0, state.getCurrentTimeSeconds(), List.of());
        java.util.Optional<MatchEvent> postFightAttempt = new PostFightResolver().resolve(state, outcome, forceSuccessfulRandom(2L), resolver);

        assertTrue(generalAttempt.isEmpty() || generalAttempt.get().getType() == MatchEventType.BARON);
        assertTrue(postFightAttempt.isEmpty() || postFightAttempt.get().getType() == MatchEventType.BARON);
        assertEquals(dragonsBefore, state.getBlueTeamState().getDragons());
    }

    @Test
    void soulAddsConfiguredTeamfightAndPushBonusesWithoutSkippingRules() {
        Team blueTeam = dummyDataFactory.createBlueTeam();
        Team redTeam = dummyDataFactory.createRedTeam();
        GameState state = createGameState(blueTeam, redTeam);
        TeamfightResolver fights = new TeamfightResolver();
        PushResolver pushes = new PushResolver();
        TeamfightOutcome normalWin = new TeamfightOutcome(TeamSide.BLUE, FightGrade.NORMAL_WIN, 2, 0, 0, List.of());
        double teamfightWithoutSoul = fights.teamfightScore(state, TeamSide.BLUE, blueTeam);
        double pushWithoutSoul = pushes.postFightChance(state, normalWin);
        state.getObjectiveState().claimSoul(TeamSide.BLUE, 0);
        state.getBlueTeamState().setHasDragonSoul(true);

        assertEquals(DragonSoulRuleConfig.SOUL_TEAMFIGHT_SCORE_BONUS, fights.teamfightScore(state, TeamSide.BLUE, blueTeam) - teamfightWithoutSoul, 0.0001);
        assertEquals(DragonSoulRuleConfig.SOUL_PUSH_CHANCE_BONUS, pushes.postFightChance(state, normalWin) - pushWithoutSoul, 0.0001);
        assertFalse(state.getMapState().areNexusTurretsVulnerable(TeamSide.RED));
    }

    @Test
    void snapshotReflectsOnlyTheSoulOwner() {
        GameState state = createGameState();
        SnapshotFactory snapshots = new SnapshotFactory();
        assertFalse(snapshots.create(state).isBlueHasDragonSoul());
        assertFalse(snapshots.create(state).isRedHasDragonSoul());
        captureDragons(state, new ObjectiveResolver(), TeamSide.RED, 4);
        com.lolfm.domain.MatchSnapshot snapshot = snapshots.create(state);
        assertEquals(4, snapshot.getRedDragons());
        assertFalse(snapshot.isBlueHasDragonSoul());
        assertTrue(snapshot.isRedHasDragonSoul());
    }

    @Test
    @Tag("diagnostic")
    @Tag("simulation-distribution")
    void dragonSoulStatisticsAcrossOneThousandSeeds() {
        int matchesWithSoul = 0, blueSouls = 0, redSouls = 0, soulWinnerMatches = 0, matchesWithoutSoul = 0;
        int before25 = 0, from25To30 = 0, from30To35 = 0, from35To40 = 0, after40 = 0;
        int endedBeforeSoul = 0, totalGeneralAttempts = 0, totalGeneralCaptures = 0, totalPostFightCaptures = 0;
        int totalDuration = 0, timeouts = 0;
        List<Integer> soulTimes = new ArrayList<>();
        List<Integer> dragonAliveTimes = new ArrayList<>();
        List<Integer> first = new ArrayList<>(), second = new ArrayList<>(), third = new ArrayList<>(), fourth = new ArrayList<>();
        List<Integer> globalFirst = new ArrayList<>(), globalSecond = new ArrayList<>(), globalThird = new ArrayList<>(), globalFourth = new ArrayList<>();
        for (long seed = 1; seed <= 1_000; seed++) {
            MatchSimulator.SimulationResult result = simulateWithDiagnostics(seed);
            totalDuration += result.timeline().getDurationSeconds();
            if (result.endReason() == GameEndReason.SIMULATION_TIMEOUT) timeouts++;
            List<DragonCaptureRecord> chronologicalCaptures = new ArrayList<>(result.dragonCaptures());
            chronologicalCaptures.sort(java.util.Comparator.comparingInt(DragonCaptureRecord::captureTimeSeconds));
            addDragonCaptureAtRecords(chronologicalCaptures, 0, globalFirst);
            addDragonCaptureAtRecords(chronologicalCaptures, 1, globalSecond);
            addDragonCaptureAtRecords(chronologicalCaptures, 2, globalThird);
            addDragonCaptureAtRecords(chronologicalCaptures, 3, globalFourth);
            int blueDragonStacks = 0;
            int redDragonStacks = 0;
            for (DragonCaptureRecord capture : result.dragonCaptures()) {
                int stack = capture.capturingSide() == TeamSide.BLUE ? ++blueDragonStacks : ++redDragonStacks;
                if (stack == 1) first.add(capture.captureTimeSeconds());
                else if (stack == 2) second.add(capture.captureTimeSeconds());
                else if (stack == 3) third.add(capture.captureTimeSeconds());
                else if (stack == 4) fourth.add(capture.captureTimeSeconds());
            }
            dragonAliveTimes.addAll(result.dragonSpawnAliveSeconds());
            totalGeneralAttempts += result.generalDragonAttemptCount();
            totalGeneralCaptures += result.generalDragonCaptureCount();
            totalPostFightCaptures += result.postFightDragonCaptureCount();
            if (result.soulOwner() == null) { matchesWithoutSoul++; endedBeforeSoul++; continue; }
            matchesWithSoul++;
            soulTimes.add(result.soulClaimedAtSeconds());
            if (result.soulOwner() == TeamSide.BLUE) blueSouls++; else redSouls++;
            if (result.timeline().getWinner() != null && result.timeline().getWinner().equals(stateTeamName(result.soulOwner()))) soulWinnerMatches++;
            if (result.soulClaimedAtSeconds() < 1_500) before25++;
            else if (result.soulClaimedAtSeconds() < 1_800) from25To30++;
            else if (result.soulClaimedAtSeconds() < 2_100) from30To35++;
            else if (result.soulClaimedAtSeconds() < 2_400) from35To40++;
            else after40++;
        }
        soulTimes.sort(Integer::compareTo);
        double soulWinnerRate = matchesWithSoul == 0 ? 0.0 : soulWinnerMatches / (double) matchesWithSoul;
        int totalDragonCaptures = totalGeneralCaptures + totalPostFightCaptures;
        System.out.printf("DRAGON_TIMING_STATS soulRate=%.3f blueSoulRate=%.3f redSoulRate=%.3f averageSoulTime=%.1f medianSoulTime=%d before25=%.3f from25To30=%.3f from30To35=%.3f from35To40=%.3f after40=%.3f soulOwnerWinRate=%.3f noSoulRate=%.3f globalFirstAvg=%.1f globalFirstMedian=%d globalSecondAvg=%.1f globalSecondMedian=%d globalThirdAvg=%.1f globalThirdMedian=%d globalFourthAvg=%.1f globalFourthMedian=%d stackFirstAvg=%.1f stackSecondAvg=%.1f stackThirdAvg=%.1f stackFourthAvg=%.1f avgAlive=%.1f generalAttempts=%.2f generalSuccessRate=%.3f postFightCaptureRate=%.3f generalCaptureRate=%.3f endedBeforeSoulRate=%.3f averageGameDuration=%.1f timeoutRate=%.3f%n",
                matchesWithSoul / 1_000.0, blueSouls / 1_000.0, redSouls / 1_000.0,
                average(soulTimes), median(soulTimes), before25 / 1_000.0, from25To30 / 1_000.0, from30To35 / 1_000.0, from35To40 / 1_000.0, after40 / 1_000.0,
                soulWinnerRate, matchesWithoutSoul / 1_000.0,
                average(globalFirst), median(globalFirst), average(globalSecond), median(globalSecond), average(globalThird), median(globalThird), average(globalFourth), median(globalFourth),
                average(first), average(second), average(third), average(fourth), average(dragonAliveTimes),
                totalGeneralAttempts / 1_000.0, totalGeneralAttempts == 0 ? 0.0 : totalGeneralCaptures / (double) totalGeneralAttempts,
                totalDragonCaptures == 0 ? 0.0 : totalPostFightCaptures / (double) totalDragonCaptures,
                totalDragonCaptures == 0 ? 0.0 : totalGeneralCaptures / (double) totalDragonCaptures,
                endedBeforeSoul / 1_000.0, totalDuration / 1_000.0, timeouts / 1_000.0);
        assertEquals(1_000, matchesWithSoul + matchesWithoutSoul);
    }


    @Test
    @Tag("diagnostic")
    @Tag("simulation-distribution")
    void playerAttributesProduceBalancedMirrorsAndClearStrongTeamAdvantage() {
        ScenarioMetrics equal = runAttributeScenario(14, 14, 500);
        ScenarioMetrics strongVsWeak = runAttributeScenario(18, 10, 500);
        ScenarioMetrics strongVsStrong = runAttributeScenario(18, 18, 500);

        System.out.printf(
                "PLAYER_IMPACT_STATS equal=%s strongVsWeak=%s strongVsStrong=%s%n",
                equal, strongVsWeak, strongVsStrong
        );

        assertTrue(equal.blueWinRate() <= 0.60 && equal.redWinRate() <= 0.60,
                "Equal teams must remain close to a 50/50 result.");
        assertTrue(strongVsWeak.blueWinRate() >= 0.70,
                "The all-18 blue team should earn a clear, but not forced, advantage.");
        assertTrue(strongVsWeak.averageKillDifference() > 0.0);
        assertTrue(strongVsWeak.averageGoldDifference() > 0.0);
        assertTrue(strongVsWeak.averageTowerDifference() > 0.0);
        assertTrue(strongVsWeak.averageDurationSeconds() < equal.averageDurationSeconds(),
                "A sustained ability advantage should usually convert into a shorter game.");
        assertTrue(strongVsStrong.blueWinRate() <= 0.60 && strongVsStrong.redWinRate() <= 0.60);
    }

    private ScenarioMetrics runAttributeScenario(int blueAttribute, int redAttribute, int seeds) {
        Team blue = createUniformTeam("Blue " + blueAttribute, blueAttribute);
        Team red = createUniformTeam("Red " + redAttribute, redAttribute);
        int blueWins = 0;
        int redWins = 0;
        int totalDuration = 0;
        int totalKillDifference = 0;
        int totalGoldDifference = 0;
        int totalDragonDifference = 0;
        int totalTowerDifference = 0;
        int bigWins = 0;
        int aces = 0;
        int beforeThirtyMinutes = 0;
        int atLeastFortyMinutes = 0;
        int atLeastSixtyMinutes = 0;
        List<Integer> durations = new ArrayList<>();

        for (long seed = 1; seed <= seeds; seed++) {
            MatchTimeline timeline = matchSimulator.simulate(blue, red, seed);
            com.lolfm.domain.MatchSnapshot last = timeline.getSnapshots().getLast();
            int duration = timeline.getDurationSeconds();
            durations.add(duration);
            totalDuration += duration;
            totalKillDifference += last.getBlueKills() - last.getRedKills();
            totalGoldDifference += last.getBlueGold() - last.getRedGold();
            totalDragonDifference += last.getBlueDragons() - last.getRedDragons();
            totalTowerDifference += last.getBlueTowersDestroyed() - last.getRedTowersDestroyed();
            if (blue.getName().equals(timeline.getWinner())) blueWins++;
            if (red.getName().equals(timeline.getWinner())) redWins++;
            if (duration < 1_800) beforeThirtyMinutes++;
            if (duration >= 2_400) atLeastFortyMinutes++;
            if (duration >= 3_600) atLeastSixtyMinutes++;
            for (MatchEvent event : timeline.getEvents()) {
                if (event.getType() == MatchEventType.TEAMFIGHT_RESULT
                        && event.getMessage().contains("대승")) bigWins++;
                if (event.getType() == MatchEventType.ACE) aces++;
            }
        }
        durations.sort(Integer::compareTo);
        return new ScenarioMetrics(
                blueWins / (double) seeds, redWins / (double) seeds,
                totalDuration / (double) seeds, percentile(durations, 0.50),
                totalKillDifference / (double) seeds, totalGoldDifference / (double) seeds,
                totalDragonDifference / (double) seeds, totalTowerDifference / (double) seeds,
                bigWins, aces, beforeThirtyMinutes / (double) seeds,
                atLeastFortyMinutes / (double) seeds, atLeastSixtyMinutes / (double) seeds
        );
    }

    private Team createUniformTeam(String name, int attribute) {
        List<Player> players = new ArrayList<>();
        for (com.lolfm.domain.Position position : com.lolfm.domain.Position.values()) {
            players.add(new Player(
                    name + " " + position.name(), position,
                    new com.lolfm.domain.PlayerAttributes(attribute, attribute, attribute, attribute)
            ));
        }
        return new Team(name, players);
    }

    private record ScenarioMetrics(
            double blueWinRate,
            double redWinRate,
            double averageDurationSeconds,
            int medianDurationSeconds,
            double averageKillDifference,
            double averageGoldDifference,
            double averageDragonDifference,
            double averageTowerDifference,
            int bigWinCount,
            int aceCount,
            double beforeThirtyMinutesRate,
            double atLeastFortyMinutesRate,
            double atLeastSixtyMinutesRate
    ) {
    }

    private void addDragonCaptureAtRecords(List<DragonCaptureRecord> captures, int index, List<Integer> target) {
        if (captures.size() > index) target.add(captures.get(index).captureTimeSeconds());
    }

    private double average(List<Integer> values) {
        return values.isEmpty() ? -1.0 : values.stream().mapToInt(Integer::intValue).average().orElse(-1.0);
    }

    private int median(List<Integer> values) {
        if (values.isEmpty()) return -1;
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        return percentile(sorted, 0.50);
    }

    private Random forceSuccessfulRandom(long seed) {
        return new Random(seed) {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }

    private Random forceTeamfightRandom(long seed) {
        return new Random(seed) {
            private boolean firstDouble = true;

            @Override
            public double nextDouble() {
                if (firstDouble) {
                    firstDouble = false;
                    return 0.0;
                }
                return super.nextDouble();
            }
        };
    }

    private GameState createGameState() {
        return createGameState(dummyDataFactory.createBlueTeam(), dummyDataFactory.createRedTeam());
    }

    private GameState createGameState(Team blueTeam, Team redTeam) {
        return new GameState(createTeamState(blueTeam), createTeamState(redTeam));
    }

    private Set<String> namesFor(Team team) {
        Set<String> names = new HashSet<>();
        for (Player player : team.getPlayers()) names.add(player.getName());
        return names;
    }

    private FightGrade expectedGrade(int winningKills, int losingKills) {
        if (winningKills == 5) return FightGrade.ACE;
        if (winningKills >= 4 || winningKills == 3 && losingKills == 0) return FightGrade.BIG_WIN;
        if (winningKills >= 2) return FightGrade.NORMAL_WIN;
        return FightGrade.SMALL_WIN;
    }

    private void markAllPlayersDead(TeamState teamState, int currentTime, int delay) {
        for (PlayerState playerState : teamState.getPlayers()) {
            playerState.markDead(currentTime, delay);
        }
    }

    private void markAllButAlive(TeamState teamState, String playerName, int currentTime, int delay) {
        for (PlayerState playerState : teamState.getPlayers()) {
            if (!playerState.getPlayerName().equals(playerName)) {
                playerState.markDead(currentTime, delay);
            }
        }
    }

    private void assertAvailable(
            String playerName,
            int eventTimeSeconds,
            Map<String, Integer> respawnAtByPlayer,
            String role
    ) {
        Integer respawnAt = respawnAtByPlayer.get(playerName);
        assertTrue(
                respawnAt == null || eventTimeSeconds >= respawnAt,
                () -> playerName + " participated as " + role + " at " + eventTimeSeconds
                        + " before respawn at " + respawnAt
        );
    }

    private int respawnDelayAt(int timeSeconds) {
        if (timeSeconds < 600) {
            return 10;
        }
        if (timeSeconds < 1_200) {
            return 20;
        }
        if (timeSeconds < 1_800) {
            return 35;
        }
        return 50;
    }

    private List<String> snapshotSignatures(MatchTimeline timeline) {
        return timeline.getSnapshots().stream()
                .map(snapshot -> snapshot.getTimeSeconds() + "|" + snapshot.getBlueKills() + "|"
                        + snapshot.getRedKills() + "|" + snapshot.getBlueGold() + "|" + snapshot.getRedGold()
                        + "|" + snapshot.getBlueTowersDestroyed() + "|" + snapshot.getRedTowersDestroyed()
                        + "|" + snapshot.getBlueInhibitorsRemaining() + "|" + snapshot.getRedInhibitorsRemaining()
                        + "|" + snapshot.getBlueNexusTurretsRemaining() + "|" + snapshot.getRedNexusTurretsRemaining()
                        + "|" + snapshot.isBlueHasDragonSoul() + "|" + snapshot.isRedHasDragonSoul()
                        + "|" + snapshot.isBlueNexusAlive() + "|" + snapshot.isRedNexusAlive()
                        + "|" + snapshot.getPlayerSnapshots().stream()
                                .map(player -> player.getPlayerName() + ":" + player.getKills() + ":"
                                        + player.getDeaths() + ":" + player.getAssists() + ":"
                                        + player.getCs() + ":" + player.getGold())
                                .toList())
                .toList();
    }

    private List<String> eventSignatures(MatchTimeline timeline) {
        return timeline.getEvents().stream()
                .map(event -> event.getTimeSeconds() + "|" + event.getType() + "|" + event.getMessage()
                        + "|" + event.getKiller() + "|" + event.getVictim() + "|" + event.getAssists())
                .toList();
    }

    private int percentile(List<Integer> sortedDurations, double percentile) {
        int index = (int) Math.ceil(percentile * sortedDurations.size()) - 1;
        return sortedDurations.get(Math.max(0, index));
    }

    private MatchTimeline findTimelinePastFortyMinutes() {
        for (long seed = 1; seed <= 100; seed++) {
            MatchTimeline timeline = simulate(seed);
            if (timeline.getDurationSeconds() > 2_400 && timeline.getWinner() != null) {
                return timeline;
            }
        }
        fail("Unable to find a normal match that extends beyond 40 minutes.");
        throw new IllegalStateException("Unreachable");
    }

    private void captureDragons(GameState state, ObjectiveResolver resolver, TeamSide side, int count) {
        for (int index = 0; index < count; index++) captureNextDragon(state, resolver, side);
    }

    private MatchEvent captureNextDragon(GameState state, ObjectiveResolver resolver, TeamSide side) {
        int nextSpawn = state.getObjectiveState().getNextDragonSpawnSeconds();
        state.advanceTimeSeconds(nextSpawn - state.getCurrentTimeSeconds());
        resolver.updateSpawnState(state);
        return resolver.captureDragon(state, side, state.getCurrentTimeSeconds()).orElseThrow();
    }

    private String stateTeamName(TeamSide side) {
        return side == TeamSide.BLUE ? dummyDataFactory.createBlueTeam().getName() : dummyDataFactory.createRedTeam().getName();
    }

    private MatchTimeline findTimelineWithTeamfight() {
        for (long seed = 1; seed <= 50; seed++) {
            MatchTimeline timeline = simulate(seed);
            boolean hasTeamfight = timeline.getEvents().stream()
                    .anyMatch(event -> event.getType() == MatchEventType.TEAMFIGHT);
            if (hasTeamfight) {
                return timeline;
            }
        }

        fail("Unable to find a teamfight timeline in the sampled seeds.");
        throw new IllegalStateException("Unreachable");
    }

    private TeamState createTeamState(Team team) {
        List<PlayerState> states = new ArrayList<>();
        for (Player player : team.getPlayers()) {
            states.add(new PlayerState(player.getName(), player.getPosition(), player.getAttributes(), 500));
        }
        return new TeamState(team.getName(), states);
    }

    private MatchSimulator.SimulationResult simulateWithDiagnostics(long seed) {
        return matchSimulator.simulateWithDiagnostics(
                dummyDataFactory.createBlueTeam(),
                dummyDataFactory.createRedTeam(),
                seed
        );
    }

    private MatchTimeline simulate(long seed) {
        return matchSimulator.simulate(
                dummyDataFactory.createBlueTeam(),
                dummyDataFactory.createRedTeam(),
                seed
        );
    }
}

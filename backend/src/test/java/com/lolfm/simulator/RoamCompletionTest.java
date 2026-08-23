package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.RoamData;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RoamCompletionTest {
    private final RoamResolver resolver = new RoamResolver();

    @Test void midAndSupportStartBoundariesAreExact() {
        GameState state = state();
        assertFalse(state.shouldResolveRoamAt(239));
        assertTrue(state.shouldResolveRoamAt(240));
        state.advanceTimeSeconds(240);
        resolver.resolve(state, new CountingRandom(.99), new ArrayList<>());
        assertEquals(2, state.getRoamExecutionStats().snapshot().midCandidateEvaluationsBlue()
                + state.getRoamExecutionStats().snapshot().midCandidateEvaluationsRed());
        GameState support = at(state(), 300);
        resolver.resolve(support, new CountingRandom(.99), new ArrayList<>());
        assertEquals(2, support.getRoamExecutionStats().snapshot().supportCandidateEvaluationsBlue()
                + support.getRoamExecutionStats().snapshot().supportCandidateEvaluationsRed());
    }

    @Test void endBoundaryAllows840AndRejectsAfterward() {
        GameState state = state();
        assertTrue(state.shouldResolveRoamAt(840));
        assertFalse(state.shouldResolveRoamAt(841));
        assertFalse(state.shouldResolveRoamAt(900));
    }

    @Test void duplicateTimeConsumesNoRandomAndBackwardTimeThrows() {
        GameState state = at(state(), 240);
        CountingRandom random = new CountingRandom(.99);
        resolver.resolve(state, random, new ArrayList<>());
        int calls = random.calls;
        assertFalse(resolver.resolve(state, random, new ArrayList<>()));
        assertEquals(calls, random.calls);
        assertThrows(IllegalArgumentException.class, () -> state.shouldResolveRoamAt(239));
    }

    @Test void deadCooldownAndAlreadyRoamingCandidatesAreIneligible() {
        RoamResolver.Candidate blueMid = candidate(TeamSide.BLUE, Position.MID);
        GameState dead = state();
        dead.getBlueTeamState().playerAt(Position.MID).markDead(230, 20);
        assertEquals(RoamIneligibility.DEAD, resolver.ineligibility(dead, blueMid, 240));
        GameState cooldown = state();
        cooldown.getBlueTeamState().playerAt(Position.MID).getRoamActionState().recordAttempt(120, Lane.TOP, 30);
        assertEquals(RoamIneligibility.COOLDOWN, resolver.ineligibility(cooldown, blueMid, 240));
        GameState active = state();
        active.getBlueTeamState().playerAt(Position.MID).beginRoamActivity(Lane.MID, Lane.TOP, 230);
        assertEquals(RoamIneligibility.ACTIVITY, resolver.ineligibility(active, blueMid, 240));
    }

    @Test void deadOrRoamingTargetParticipantExcludesTarget() {
        GameState dead = state();
        dead.getRedTeamState().playerAt(Position.TOP).markDead(230, 20);
        assertFalse(resolver.targetEligible(dead, TeamSide.BLUE, Lane.TOP, 240));
        GameState active = state();
        active.getBlueTeamState().playerAt(Position.SUPPORT).beginRoamActivity(Lane.BOT, Lane.MID, 230);
        assertFalse(resolver.targetEligible(active, TeamSide.RED, Lane.BOT, 240));
    }

    @Test void pressureSignIsSymmetricForBlueAndRed() {
        GameState state = state();
        state.laneState(Lane.TOP).setPressure(-60);
        assertEquals(60, resolver.enemyOverextension(state, TeamSide.BLUE, Lane.TOP));
        assertEquals(0, resolver.enemyOverextension(state, TeamSide.RED, Lane.TOP));
        state.laneState(Lane.TOP).setPressure(60);
        assertEquals(0, resolver.enemyOverextension(state, TeamSide.BLUE, Lane.TOP));
        assertEquals(60, resolver.enemyOverextension(state, TeamSide.RED, Lane.TOP));
    }

    @Test void attemptChanceUsesAggressionOriginPriorityAndOverextensionWithClamps() {
        GameState baseline = state();
        double base = resolver.attemptChance(baseline, candidate(TeamSide.BLUE, Position.MID));
        GameState boosted = attributedState(14, 18, 14, 14, 14, 14);
        boosted.laneState(Lane.MID).setPressure(80);
        boosted.laneState(Lane.TOP).setPressure(-100);
        assertTrue(resolver.attemptChance(boosted, candidate(TeamSide.BLUE, Position.MID)) > base);
        assertTrue(resolver.attemptChance(boosted, candidate(TeamSide.BLUE, Position.MID)) <= RoamRuleConfig.MAX_ROAM_ATTEMPT_CHANCE);
    }

    @Test void multipleTriggersProduceOneAttemptAndDoNotMutateUnselectedCandidate() {
        GameState state = at(state(), 240);
        assertTrue(resolver.resolve(state, new SequenceRandom(0, 0, 0, 0, .99), new ArrayList<>()));
        int attempts = 0;
        for (TeamSide side : TeamSide.values()) {
            if (state.getTeamState(side).playerAt(Position.MID).getRoamActionState().getLastRoamAttemptAtSeconds() == 240) attempts++;
        }
        assertEquals(1, attempts);
        RoamExecutionStatsSnapshot stats = state.getRoamExecutionStats().snapshot();
        assertEquals(1, stats.multipleRoamTriggers());
        assertEquals(1, stats.unselectedTriggeredCandidates());
    }

    @Test void midTargetWeightIncludesBotMultiplierAndRepeatPenalty() {
        GameState state = state();
        RoamResolver.Candidate mid = candidate(TeamSide.BLUE, Position.MID);
        double top = resolver.targetWeight(state, mid, Lane.TOP, 240);
        double bot = resolver.targetWeight(state, mid, Lane.BOT, 240);
        assertEquals(top * RoamRuleConfig.BOT_TARGET_WEIGHT_MULTIPLIER, bot, 1e-9);
        state.getBlueTeamState().playerAt(Position.MID).getRoamActionState().recordAttempt(120, Lane.TOP, 30);
        assertEquals(top * RoamRuleConfig.REPEAT_TARGET_WEIGHT_MULTIPLIER,
                resolver.targetWeight(state, mid, Lane.TOP, 240), 1e-9);
    }

    @Test void supportTargetsMidWithoutTargetChoiceRandom() {
        ResolveResult result = resolveSupport(new SequenceRandom(.99, .99, 0, .99, 0, .99));
        assertEquals(Lane.MID, result.data.targetLane());
        assertEquals(6, result.random.index);
    }

    @Test void activityStartsExpiresAndClearsStaleLanes() {
        ResolveResult result = resolveBlueTopNoKill();
        PlayerState mid = result.state.getBlueTeamState().playerAt(Position.MID);
        assertEquals(PlayerActivityType.ROAMING, mid.getActivityState().getActivityType());
        assertEquals(Lane.MID, mid.getActivityState().getOriginLane());
        assertEquals(Lane.TOP, mid.getActivityState().getTargetLane());
        result.state.advanceTimeSeconds(30);
        result.state.expireBaronBuffsIfNeeded();
        assertEquals(PlayerActivityType.DEFAULT_ROLE, mid.getActivityState().getActivityType());
        assertNull(mid.getActivityState().getOriginLane());
        assertNull(mid.getActivityState().getTargetLane());
    }

    @Test void deathClearsActivityButPreservesCooldownAndFarmCost() {
        ResolveResult result = resolveBlueTopNoKill();
        PlayerState mid = result.state.getBlueTeamState().playerAt(Position.MID);
        mid.markDead(240, 10);
        assertEquals(PlayerActivityType.DEFAULT_ROLE, mid.getActivityState().getActivityType());
        assertEquals(240, mid.getRoamActionState().getLastRoamAttemptAtSeconds());
        assertEquals(270, mid.getRoamActionState().getRoamFarmBlockedUntilSeconds());
    }

    @Test void midFarmIsBlockedAt250And260AndResumesAt270() {
        GameState state = state();
        PlayerState mid = state.getBlueTeamState().playerAt(Position.MID);
        mid.getRoamActionState().recordAttempt(240, Lane.TOP, RoamRuleConfig.MID_ROAM_FARM_BLOCK_SECONDS);
        PositionEconomyResolver economy = new PositionEconomyResolver();
        CountingRandom random = new CountingRandom(0);
        economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 250, 10, random);
        economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 260, 10, random);
        assertEquals(0, mid.getCs());
        int calls = random.calls;
        economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 270, 10, random);
        assertTrue(mid.getCs() > 0);
        assertEquals(calls + 4, random.calls);
    }

    @Test void farmBlockConsumesNoMidRandomCsGoldOrBounty() {
        GameState state = state();
        PlayerState mid = state.getBlueTeamState().playerAt(Position.MID);
        mid.getRoamActionState().recordAttempt(240, Lane.TOP, 30);
        CountingRandom random = new CountingRandom(0);
        new PositionEconomyResolver().resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 250, 10, random);
        assertAll(() -> assertEquals(0, mid.getCs()), () -> assertEquals(500, mid.getGold()),
                () -> assertEquals(0, mid.getBountyProgress()), () -> assertEquals(3, random.calls));
    }

    @Test void deathRecoveryAndRoamBlockOverlapWithoutDoubleLoss() {
        GameState state = state();
        PlayerState mid = state.getBlueTeamState().playerAt(Position.MID);
        mid.getRoamActionState().recordAttempt(240, Lane.TOP, 30);
        mid.markDead(240, 10);
        PositionEconomyResolver economy = new PositionEconomyResolver();
        economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 250, 10, new CountingRandom(0));
        economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 260, 10, new CountingRandom(0));
        assertEquals(0, mid.getCs());
        economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 270, 10, new CountingRandom(0));
        assertTrue(mid.canFarmAt(270) || mid.getFarmResumeAtSeconds() > 270);
    }

    @Test void supportHasZeroFarmCsWhilePassiveAndQuestIncomeContinue() {
        MatchSimulator simulator = simulator(SimulationOptions.productionDefaults());
        GameState state = state();
        PlayerState support = state.getBlueTeamState().playerAt(Position.SUPPORT);
        state.advanceTimeSeconds(70);
        simulator.applyTickEconomy(new CountingRandom(0), state, state.getBlueTeamState(), TeamSide.BLUE, 10, 70);
        assertEquals(0, support.getCs());
        assertEquals(500 + PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK
                + PositionEconomyRuleConfig.SUPPORT_QUEST_GOLD_PER_TICK, support.getGold());
    }

    @Test void midOriginPressureCostsAreBlueMinusEightAndRedPlusEight() {
        ResolveResult blue = resolveBlueTopNoKill();
        assertEquals(-RoamRuleConfig.MID_ORIGIN_PRESSURE_COST, blue.data.originPressureAfter(), 1e-9);
        ResolveResult red = resolveAt(240, new SequenceRandom(.99, 0, 0, 0, .99));
        assertEquals(RoamRuleConfig.MID_ORIGIN_PRESSURE_COST, red.data.originPressureAfter(), 1e-9);
    }

    @Test void supportOriginPressureCostsAreBlueMinusTenAndRedPlusTen() {
        ResolveResult blue = resolveSupport(new SequenceRandom(.99, .99, 0, .99, 0, .99));
        assertEquals(-RoamRuleConfig.SUPPORT_ORIGIN_PRESSURE_COST, blue.data.originPressureAfter(), 1e-9);
        ResolveResult red = resolveAt(300, new SequenceRandom(.99, .99, .99, 0, 0, .99));
        assertEquals(RoamRuleConfig.SUPPORT_ORIGIN_PRESSURE_COST, red.data.originPressureAfter(), 1e-9);
    }

    @Test void noKillStillPaysOriginPressureAndNeverShocksTarget() {
        ResolveResult result = resolveBlueTopNoKill();
        assertNotEquals(result.data.originPressureBefore(), result.data.originPressureAfter());
        assertEquals(result.data.targetPressureBefore(), result.data.targetPressureAfter());
        assertEquals(RoamOutcome.NO_KILL, result.data.outcome());
    }

    @Test void combatEdgeIncludesAttributesGoldVulnerabilityAndConfiguredClampComponents() {
        GameState weak = attributedState(10, 10, 10, 18, 18, 18);
        GameState strong = attributedState(18, 18, 18, 10, 10, 10);
        RoamResolver.Candidate blue = candidate(TeamSide.BLUE, Position.MID);
        double weakEdge = resolver.combatEdge(weak, blue, Lane.TOP, 0);
        double strongEdge = resolver.combatEdge(strong, blue, Lane.TOP, 0);
        assertTrue(strongEdge > weakEdge);
        assertTrue(resolver.combatEdge(strong, blue, Lane.TOP, 100) > strongEdge);
    }

    @Test void decisiveAndSuccessProbabilitiesClampAtConfiguredBounds() {
        PlayerState mid = state().getBlueTeamState().playerAt(Position.MID);
        assertEquals(RoamRuleConfig.MAX_ROAM_DECISIVE_CHANCE, resolver.decisiveChance(mid, 100, 100), 1e-9);
        assertEquals(RoamRuleConfig.MIN_ROAM_SUCCESS_CHANCE, resolver.successChance(-100), 1e-9);
        assertEquals(RoamRuleConfig.MAX_ROAM_SUCCESS_CHANCE, resolver.successChance(100), 1e-9);
    }

    @Test void midTopSuccessAndReverseParticipantsAreValid() {
        ResolveResult success = resolveAt(240, new SequenceRandom(0, .99, 0, 0, 0, 0, 0));
        assertParticipantShape(success.data, TeamSide.BLUE, 1);
        ResolveResult reverse = resolveAt(240, new SequenceRandom(0, .99, 0, 0, 0, .99, 0));
        assertEquals(TeamSide.RED, reverse.data.winningSide());
        assertTrue(reverse.data.assistantPlayerIds().isEmpty());
    }

    @Test void midBotSuccessAndReverseHaveCorrectAssistantCounts() {
        ResolveResult success = resolveAt(240, new SequenceRandom(0, .99, 0, .99, 0, 0, 0, 0));
        assertEquals(Lane.BOT, success.data.targetLane());
        assertParticipantShape(success.data, TeamSide.BLUE, 2);
        ResolveResult reverse = resolveAt(240, new SequenceRandom(0, .99, 0, .99, 0, .99, 0, 0));
        assertEquals(TeamSide.RED, reverse.data.winningSide());
        assertEquals(1, reverse.data.assistantPlayerIds().size());
    }

    @Test void supportMidSuccessAndReverseParticipantsAreValid() {
        ResolveResult success = resolveSupport(new SequenceRandom(.99, .99, 0, .99, 0, 0, 0));
        assertParticipantShape(success.data, TeamSide.BLUE, 1);
        ResolveResult reverse = resolveSupport(new SequenceRandom(.99, .99, 0, .99, 0, 0, .99, 0));
        assertEquals(TeamSide.RED, reverse.data.winningSide());
        assertTrue(reverse.data.assistantPlayerIds().isEmpty());
    }

    @Test void commonKillRewardPays300And150AndShutdownOnlyToKiller() {
        TeamState blue = team("BLUE", attrs(14));
        TeamState red = team("RED", attrs(14));
        PlayerState killer = blue.playerAt(Position.MID), assist = blue.playerAt(Position.TOP), victim = red.playerAt(Position.TOP);
        victim.addImmediateBountyProgress(1_000);
        new KillRewardResolver().award(240, blue, killer, red, victim, List.of(assist), 10, false, null, new ArrayList<>());
        assertEquals(1, killer.getKills());
        assertTrue(killer.getGold() >= 800);
        assertEquals(650, assist.getGold());
        assertEquals(0, assist.getTotalShutdownGoldEarned());
        assertTrue(killer.getTotalShutdownGoldEarned() >= 0);
    }

    @Test void targetPressureShockUsesActualWinningSide() {
        ResolveResult success = resolveAt(240, new SequenceRandom(0, .99, 0, 0, 0, 0, 0));
        assertTrue(success.data.targetPressureAfter() > success.data.targetPressureBefore());
        ResolveResult reverse = resolveAt(240, new SequenceRandom(0, .99, 0, 0, 0, .99, 0));
        assertTrue(reverse.data.targetPressureAfter() < reverse.data.targetPressureBefore());
    }

    @Test void roamingPlayersAreExcludedFromGankLaneAndTeamfightParticipation() {
        GameState state = state();
        PlayerState mid = state.getBlueTeamState().playerAt(Position.MID);
        mid.beginRoamActivity(Lane.MID, Lane.TOP, 240);
        assertFalse(mid.canParticipateInMajorCombatAt(240));
        assertFalse(new LaneCombatResolver().eligible(state, Lane.MID, 240));
        assertFalse(new JungleGankResolver(false).laneEligible(state, Lane.MID, 240));
    }

    @Test void actualGankSkipsRoamWhileFailedGankFallsThroughToRoam() {
        boolean sawGankSkip = false, sawRoamAfterEvaluation = false;
        for (long seed = 1; seed <= 30 && !(sawGankSkip && sawRoamAfterEvaluation); seed++) {
            MatchSimulator.SimulationResult result = simulator(SimulationOptions.productionDefaults())
                    .simulateWithDiagnostics(domainTeam("BLUE"), domainTeam("RED"), seed);
            sawGankSkip |= result.roamExecutionStats().roamSkippedByHigherPriorityActualCombat() > 0;
            sawRoamAfterEvaluation |= result.roamExecutionStats().roamResolverEvaluations() > 0;
        }
        assertTrue(sawGankSkip);
        assertTrue(sawRoamAfterEvaluation);
    }

    @Test void failedRoamEvaluationFallsThroughToLaneCombatCall() {
        MatchSimulator.SimulationResult result = simulator(SimulationOptions.productionDefaults())
                .simulateWithDiagnostics(domainTeam("BLUE"), domainTeam("RED"), 7);
        assertTrue(result.roamExecutionStats().roamEvaluationFallthroughToLaneCombat() > 0);
    }

    @Test void actualRoamIncludingNoKillBlocksLaneCombatAndGeneric() {
        MatchSimulator.SimulationResult result = simulator(SimulationOptions.productionDefaults())
                .simulateWithDiagnostics(domainTeam("BLUE"), domainTeam("RED"), 7);
        RoamExecutionStatsSnapshot stats = result.roamExecutionStats();
        assertEquals(stats.actualRoamAttempts(), stats.roamBlockedLaneCombat());
        assertEquals(stats.actualRoamAttempts(), stats.roamBlockedGeneric());
        assertTrue(stats.roamNoKill() > 0);
    }

    @Test void summaryAndKillSourceRepresentOneCombatAttempt() {
        ResolveResult result = resolveAt(240, new SequenceRandom(0, .99, 0, 0, 0, 0, 0));
        assertEquals(1, result.events.stream().filter(e -> e.getType() == MatchEventType.ROAM).count());
        assertEquals(1, result.events.stream().filter(e -> e.getType() == MatchEventType.KILL
                && e.getCombatSource() == CombatSource.ROAM).count());
        assertEquals(1, result.state.getRoamExecutionStats().snapshot().actualRoamAttempts());
    }

    @Test void snapshotsExposeActivityAndPastSnapshotsRemainImmutable() {
        ResolveResult result = resolveBlueTopNoKill();
        SnapshotFactory factory = new SnapshotFactory();
        MatchSnapshot during = factory.create(result.state);
        PlayerSnapshot midDuring = snapshot(during, TeamSide.BLUE, Position.MID);
        assertEquals(PlayerActivityType.ROAMING, midDuring.getActivityType());
        result.state.advanceTimeSeconds(30);
        result.state.expireBaronBuffsIfNeeded();
        MatchSnapshot after = factory.create(result.state);
        assertEquals(PlayerActivityType.DEFAULT_ROLE, snapshot(after, TeamSide.BLUE, Position.MID).getActivityType());
        assertEquals(PlayerActivityType.ROAMING, midDuring.getActivityType());
    }

    @Test void explicitRoamOffHasNoRoamMutationEventOrRandomBranch() {
        SimulationOptions off = SimulationOptions.productionDefaults().withRoamEnabled(false);
        MatchSimulator.SimulationResult result = simulator(off).simulateWithDiagnostics(domainTeam("BLUE"), domainTeam("RED"), 7);
        assertTrue(result.timeline().getEvents().stream().noneMatch(e -> e.getType() == MatchEventType.ROAM));
        assertEquals(0, result.roamExecutionStats().roamResolverEvaluations());
        assertTrue(result.timeline().getEvents().stream().anyMatch(e -> e.getType() == MatchEventType.JUNGLE_GANK
                || e.getType() == MatchEventType.LANE_COMBAT || e.getCombatSource() == CombatSource.SKIRMISH));
        assertTrue(result.timeline().getSnapshots().stream().flatMap(s -> s.getPlayerSnapshots().stream())
                .allMatch(p -> p.getActivityType() == PlayerActivityType.DEFAULT_ROLE));
    }

    @Test void diagnosticsInstrumentationDoesNotChangeTimeline() {
        SimulationOptions on = SimulationOptions.productionDefaults().withDiagnosticsEnabled(true);
        SimulationOptions off = SimulationOptions.productionDefaults().withDiagnosticsEnabled(false);
        MatchTimeline a = simulator(on).simulate(domainTeam("BLUE"), domainTeam("RED"), 77);
        MatchTimeline b = simulator(off).simulate(domainTeam("BLUE"), domainTeam("RED"), 77);
        assertEquals(signature(a), signature(b));
    }

    @Test void sameSeedReproducesCompleteStructuredTimeline() {
        MatchSimulator simulator = simulator(SimulationOptions.productionDefaults());
        assertEquals(signature(simulator.simulate(domainTeam("BLUE"), domainTeam("RED"), 91)),
                signature(simulator.simulate(domainTeam("BLUE"), domainTeam("RED"), 91)));
    }

    @Test void roamCountersDistinguishEvaluationTriggerAttemptOutcomeAndActivity() {
        ResolveResult result = resolveBlueTopNoKill();
        RoamExecutionStatsSnapshot stats = result.state.getRoamExecutionStats().snapshot();
        assertAll(() -> assertEquals(1, stats.roamResolverEvaluations()),
                () -> assertEquals(2, stats.roamTriggerRolls()), () -> assertEquals(1, stats.roamTriggersBlue()),
                () -> assertEquals(1, stats.actualRoamAttempts()), () -> assertEquals(1, stats.actualMidRoams()),
                () -> assertEquals(1, stats.roamNoKill()), () -> assertEquals(1, stats.activityCreated()));
    }

    @Test void bAndCAttributeProfilesProduceIndependentMechanicsAndTeamfightingEdges() {
        GameState b = stateWithMidAttributes(new PlayerAttributes(18, 18, 14, 18), new PlayerAttributes(10, 10, 14, 10)), c = stateWithMidAttributes(new PlayerAttributes(14, 18, 14, 14), new PlayerAttributes(14, 10, 14, 14));
        RoamCombatEdgeBreakdown bBlue = resolver.combatEdgeBreakdown(b, candidate(TeamSide.BLUE, Position.MID), Lane.TOP, 0), cBlue = resolver.combatEdgeBreakdown(c, candidate(TeamSide.BLUE, Position.MID), Lane.TOP, 0), bRed = resolver.combatEdgeBreakdown(b, candidate(TeamSide.RED, Position.MID), Lane.TOP, 0), cRed = resolver.combatEdgeBreakdown(c, candidate(TeamSide.RED, Position.MID), Lane.TOP, 0);
        assertAll(() -> assertTrue(bBlue.combatEdge() > cBlue.combatEdge()), () -> assertTrue(bRed.combatEdge() < cRed.combatEdge()), () -> assertTrue(resolver.successChance(bBlue.combatEdge()) > resolver.successChance(cBlue.combatEdge())), () -> assertNotEquals(bBlue.mechanicsEdge(), cBlue.mechanicsEdge()), () -> assertNotEquals(bBlue.teamfightingEdge(), cBlue.teamfightingEdge()), () -> assertEquals(bBlue.aggressionEdge(), cBlue.aggressionEdge()), () -> assertEquals(bRed.aggressionEdge(), cRed.aggressionEdge()));
    }
    private ResolveResult resolveBlueTopNoKill() {
        return resolveAt(240, new SequenceRandom(0, .99, 0, 0, .99));
    }

    private ResolveResult resolveSupport(SequenceRandom random) { return resolveAt(300, random); }

    private ResolveResult resolveAt(int time, SequenceRandom random) {
        GameState state = at(state(), time);
        List<MatchEvent> events = new ArrayList<>();
        assertTrue(resolver.resolve(state, random, events));
        RoamData data = events.stream().filter(e -> e.getType() == MatchEventType.ROAM)
                .findFirst().orElseThrow().getRoam();
        return new ResolveResult(state, events, data, random);
    }

    private void assertParticipantShape(RoamData data, TeamSide winner, int assistants) {
        assertEquals(winner, data.winningSide());
        assertNotNull(data.killerPlayerId());
        assertNotNull(data.victimPlayerId());
        assertEquals(assistants, data.assistantPlayerIds().size());
        assertEquals(assistants, data.assistantPlayerIds().stream().distinct().count());
        assertFalse(data.assistantPlayerIds().contains(data.killerPlayerId()));
        assertFalse(data.assistantPlayerIds().contains(data.victimPlayerId()));
    }

    private PlayerSnapshot snapshot(MatchSnapshot snapshot, TeamSide side, Position position) {
        return snapshot.getPlayerSnapshots().stream().filter(p -> p.getTeamSide() == side && p.getPosition() == position)
                .findFirst().orElseThrow();
    }

    private RoamResolver.Candidate candidate(TeamSide side, Position position) {
        return new RoamResolver.Candidate(side, position, 0);
    }

    private GameState at(GameState state, int time) { state.advanceTimeSeconds(time); return state; }
    private GameState state() { return new GameState(team("BLUE", attrs(14)), team("RED", attrs(14))); }

    private GameState attributedState(int blueMechanics, int blueAggression, int blueTeamfighting,
                                      int redMechanics, int redAggression, int redTeamfighting) {
        return new GameState(team("BLUE", new PlayerAttributes(blueMechanics, blueAggression, 14, blueTeamfighting)),
                team("RED", new PlayerAttributes(redMechanics, redAggression, 14, redTeamfighting)));
    }

    private GameState stateWithMidAttributes(PlayerAttributes blueMid, PlayerAttributes redMid) {
        List<PlayerState> blue = new ArrayList<>(), red = new ArrayList<>();
        for (Position position : Position.values()) {
            blue.add(PlayerStateTestFixture.player("BLUE", position, position == Position.MID ? blueMid : attrs(14), 500));
            red.add(PlayerStateTestFixture.player("RED", position, position == Position.MID ? redMid : attrs(14), 500));
        }
        return new GameState(new TeamState("BLUE", blue), new TeamState("RED", red));
    }

    private PlayerAttributes attrs(int value) { return new PlayerAttributes(value, value, value, value); }
    private TeamState team(String side, PlayerAttributes attributes) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(PlayerStateTestFixture.player(side, position, attributes, 500));
        return new TeamState(side, players);
    }

    private Team domainTeam(String side) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(new Player(side + "-" + position, position, attrs(14)));
        return new Team(side, players);
    }

    private MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private String signature(MatchTimeline timeline) {
        return timeline.getDurationSeconds() + ":" + timeline.getWinner() + ":" + timeline.getEvents().stream().map(e ->
                e.getTimeSeconds() + ":" + e.getType() + ":" + e.getCombatSource() + ":" + e.getKiller() + ":"
                        + e.getVictim() + ":" + e.getAssists() + ":" + (e.getRoam() == null ? "" : e.getRoam())).toList()
                + ":" + timeline.getSnapshots().stream().map(s -> s.getTimeSeconds() + ":" + s.getBlueGold() + ":"
                + s.getRedGold() + ":" + s.getPlayerSnapshots().stream().map(p -> p.getTeamSide() + ":" + p.getPosition()
                + ":" + p.getKills() + ":" + p.getDeaths() + ":" + p.getAssists() + ":" + p.getCs() + ":"
                + p.getGold() + ":" + p.getActivityType()).toList()).toList();
    }

    private record ResolveResult(GameState state, List<MatchEvent> events, RoamData data, SequenceRandom random) { }
    private static final class CountingRandom extends Random {
        final double value; int calls;
        CountingRandom(double value) { this.value = value; }
        @Override public double nextDouble() { calls++; return value; }
    }
    private static final class SequenceRandom extends Random {
        final double[] values; int index;
        SequenceRandom(double... values) { this.values = values; }
        @Override public double nextDouble() { return index < values.length ? values[index++] : values[values.length - 1]; }
    }
}

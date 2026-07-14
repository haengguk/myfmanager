package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RoamTest {
    private final RoamResolver resolver = new RoamResolver();

    @Test void evaluationWindowDuplicateAndBackwardTimeAreMatchScoped() {
        GameState state = state();
        assertFalse(state.shouldResolveRoamAt(239));
        assertTrue(state.shouldResolveRoamAt(240));
        state.advanceTimeSeconds(240);
        CountingRandom random = new CountingRandom();
        assertFalse(resolver.resolve(state, random, new ArrayList<>()));
        int calls = random.calls;
        assertFalse(resolver.resolve(state, random, new ArrayList<>()));
        assertEquals(calls, random.calls);
        assertThrows(IllegalArgumentException.class, () -> state.shouldResolveRoamAt(239));
        assertTrue(state.shouldResolveRoamAt(840));
        assertFalse(state.shouldResolveRoamAt(841));
    }

    @Test void activeRoamerIsExclusiveAndReturnsAtBoundary() {
        GameState state = state();
        PlayerState mid = state.getBlueTeamState().playerAt(Position.MID);
        mid.beginRoamActivity(Lane.MID, Lane.TOP, 240);
        assertFalse(mid.canParticipateInMajorCombatAt(250));
        assertFalse(new LaneCombatResolver().eligible(state, Lane.MID, 250));
        state.advanceTimeSeconds(270);
        state.expireBaronBuffsIfNeeded();
        assertEquals(PlayerActivityType.DEFAULT_ROLE, mid.getActivityState().getActivityType());
        assertTrue(mid.canParticipateInMajorCombatAt(270));
    }

    @Test void midFarmBlockConsumesNoFarmRandomAndDeathClearsOnlyActivity() {
        GameState state = state();
        PlayerState mid = state.getBlueTeamState().playerAt(Position.MID);
        mid.getRoamActionState().recordAttempt(240, Lane.TOP, 30);
        mid.beginRoamActivity(Lane.MID, Lane.TOP, 240);
        CountingRandom random = new CountingRandom();
        new PositionEconomyResolver().resolve(state, state.getBlueTeamState(), TeamSide.BLUE, 250, 10, random);
        assertEquals(0, mid.getCs());
        assertEquals(3, random.calls); // TOP, JUNGLE, ADC; MID is blocked and SUPPORT has zero expected FARM.
        mid.markDead(250, 10);
        assertEquals(PlayerActivityType.DEFAULT_ROLE, mid.getActivityState().getActivityType());
        assertEquals(240, mid.getRoamActionState().getLastRoamAttemptAtSeconds());
        assertEquals(270, mid.getRoamActionState().getRoamFarmBlockedUntilSeconds());
    }

    @Test void pressureSignAndTargetEligibilityUseStructuredSideAndPosition() {
        GameState state = state();
        RoamResolver.Candidate blueMid = new RoamResolver.Candidate(TeamSide.BLUE, Position.MID, 0);
        state.laneState(Lane.TOP).setPressure(-60);
        assertTrue(resolver.attemptChance(state, blueMid) > RoamRuleConfig.BASE_MID_ROAM_ATTEMPT_CHANCE);
        assertTrue(resolver.eligible(state, blueMid, 240));
        state.getRedTeamState().playerAt(Position.TOP).beginRoamActivity(Lane.MID, Lane.TOP, 240);
        assertFalse(resolver.targetEligible(state, TeamSide.BLUE, Lane.TOP, 240));
    }

    private GameState state() { return new GameState(team("BLUE"), team("RED")); }
    private TeamState team(String prefix) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(new PlayerState(prefix + position, position,
                new PlayerAttributes(14, 14, 14, 14), 500));
        return new TeamState(prefix, players);
    }
    private static final class CountingRandom extends Random {
        int calls;
        @Override public double nextDouble() { calls++; return .99; }
    }
}

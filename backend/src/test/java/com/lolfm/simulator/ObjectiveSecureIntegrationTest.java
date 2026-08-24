package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ObjectiveSecureIntegrationTest {
    private final ObjectiveDecisionResolver resolver = new ObjectiveDecisionResolver();

    @Test
    void contestedStealUsesOneCaptureRewardPathAndDuplicateKeyConsumesNothing() {
        GameState state = ObjectivePlayerSkillTestSupport.detailedDragonState();
        ScriptedRandom random = new ScriptedRandom(0);
        List<MatchEvent> events = new ArrayList<>();

        MatchEvent capture = resolve(state, random, events).orElseThrow();

        assertThat(capture.getObjectiveDecision().result())
                .isEqualTo(ObjectiveDecisionResult.CONTEST_FIGHT);
        assertThat(capture.getObjectiveDecision().fightWinner()).isEqualTo(TeamSide.RED);
        assertThat(capture.getObjectiveDecision().captureSide()).isEqualTo(TeamSide.BLUE);
        assertThat(capture.getObjectiveDecision().objectiveSecureDecision()).isNotNull();
        assertThat(capture.getObjectiveDecision().objectiveSecureDecision().rollExecuted()).isTrue();
        assertThat(capture.getObjectiveDecision().objectiveSecureDecision().secureWon()).isTrue();
        assertThat(capture.getObjectiveDecision().objectiveSecureDecision().captureSucceeded()).isTrue();
        assertThat(capture.getObjectiveDecision().objectiveSecureDecision().actualSteal()).isTrue();
        assertThat(capture.getObjectiveDecision().objectiveFightSkillImpact()).isNotNull();
        assertThat(state.wasMajorCombatAttemptedThisTick()).isTrue();
        assertThat(state.getBlueTeamState().getDragons()).isOne();
        assertThat(state.getRedTeamState().getDragons()).isZero();
        assertThat(events.stream().filter(event -> event.getType() == MatchEventType.DRAGON)).hasSize(1);
        assertThat(events.stream().filter(event -> event.getType() == MatchEventType.KILL)
                .map(MatchEvent::getCombatSource)).containsOnly(CombatSource.OBJECTIVE_FIGHT);
        List<MatchEvent> fightEvents = events.stream()
                .filter(event -> event.getCombatSource() == CombatSource.OBJECTIVE_FIGHT).toList();
        assertThat(fightEvents).isNotEmpty();
        assertThat(fightEvents).allSatisfy(event -> {
            assertThat(event.getActionId()).isNotBlank();
            assertThat(event.getActionId()).endsWith(":FIGHT");
        });
        assertThat(fightEvents.stream().map(MatchEvent::getActionId).distinct()).hasSize(1);
        List<MatchEvent> summaries = fightEvents.stream()
                .filter(event -> event.getType() == MatchEventType.TEAMFIGHT
                        || event.getType() == MatchEventType.TEAMFIGHT_RESULT).toList();
        assertThat(summaries).hasSize(2);
        assertThat(summaries).allSatisfy(event -> assertThat(event.getObjectiveFight()).isNotNull());
        assertThat(summaries.getFirst().getObjectiveFight())
                .isEqualTo(summaries.getLast().getObjectiveFight());
        assertThat(summaries.getLast().getObjectiveFight().winningSide())
                .isEqualTo(capture.getObjectiveDecision().fightWinner());
        assertThat(summaries.getLast().getObjectiveFight().participantPlayerIds())
                .doesNotHaveDuplicates();
        assertThat(summaries.getLast().getObjectiveFight().kills()).isEqualTo(
                (int) fightEvents.stream().filter(event -> event.getType() == MatchEventType.KILL).count());
        assertThat(capture.getParentActionId()).isEqualTo(fightEvents.getFirst().getActionId());
        assertThat(capture.getActionId()).startsWith("OBJECTIVE_DECISION:DRAGON:");
        assertThat(state.getObjectiveDecisionState().getStats().snapshot().secureRolls()).isOne();
        assertThat(state.getObjectiveDecisionState().getStats().snapshot().objectiveSteals()).isOne();

        int eventCount = events.size();
        int doubleCalls = random.doubleCalls;
        int intCalls = random.intCalls;
        int booleanCalls = random.booleanCalls;
        int blueGold = state.getBlueTeamState().getGold();
        int redGold = state.getRedTeamState().getGold();

        assertThat(resolve(state, random, events)).isEmpty();

        assertThat(events).hasSize(eventCount);
        assertThat(random.doubleCalls).isEqualTo(doubleCalls);
        assertThat(random.intCalls).isEqualTo(intCalls);
        assertThat(random.booleanCalls).isEqualTo(booleanCalls);
        assertThat(state.getBlueTeamState().getGold()).isEqualTo(blueGold);
        assertThat(state.getRedTeamState().getGold()).isEqualTo(redGold);
        assertThat(state.getBlueTeamState().getDragons()).isOne();
        assertThat(state.getObjectiveDecisionState().getHistory()).hasSize(1);
    }

    @Test
    void staleCaptureKeepsSecureAttemptButDoesNotCountAnActualSteal() {
        GameState state = ObjectivePlayerSkillTestSupport.detailedDragonState();
        ScriptedRandom random = new ScriptedRandom(0);
        List<MatchEvent> events = new ArrayList<>();
        ObjectiveResolver failingCapture = new ObjectiveResolver() {
            @Override
            public java.util.Optional<MatchEvent> captureDragon(
                    GameState ignoredState, TeamSide ignoredSide, int ignoredTime,
                    DragonCaptureSource ignoredSource, String ignoredMessage) {
                return java.util.Optional.empty();
            }
        };

        java.util.Optional<MatchEvent> capture = resolver.resolve(
                state, ObjectiveType.DRAGON, TeamSide.BLUE, 0, random,
                failingCapture, new StructureResolver(), events, null);

        assertThat(capture).isEmpty();
        assertThat(state.getObjectiveState().isDragonAlive()).isTrue();
        assertThat(state.getObjectiveDecisionState().getHistory()).hasSize(1);
        assertThat(state.getObjectiveDecisionState().getHistory().getFirst().result())
                .isEqualTo(ObjectiveDecisionResult.STALE_OBJECTIVE);
        assertThat(state.getObjectiveDecisionState().getHistory().getFirst()
                .objectiveSecureDecision().secureWon()).isTrue();
        assertThat(state.getObjectiveDecisionState().getHistory().getFirst()
                .objectiveSecureDecision().captureSucceeded()).isFalse();
        assertThat(state.getObjectiveDecisionState().getHistory().getFirst()
                .objectiveSecureDecision().actualSteal()).isFalse();
        assertThat(state.getObjectiveDecisionState().getStats().snapshot().secureRolls()).isOne();
        assertThat(state.getObjectiveDecisionState().getStats().snapshot().objectiveSteals()).isZero();
    }

    @Test
    void sameScriptReplaysTheCompleteContestedDecisionAndEvents() {
        GameState firstState = ObjectivePlayerSkillTestSupport.detailedDragonState();
        GameState secondState = ObjectivePlayerSkillTestSupport.detailedDragonState();
        List<MatchEvent> firstEvents = new ArrayList<>();
        List<MatchEvent> secondEvents = new ArrayList<>();
        ScriptedRandom firstRandom = new ScriptedRandom(0);
        ScriptedRandom secondRandom = new ScriptedRandom(0);

        MatchEvent first = resolve(firstState, firstRandom, firstEvents).orElseThrow();
        MatchEvent second = resolve(secondState, secondRandom, secondEvents).orElseThrow();

        assertThat(second.getObjectiveDecision()).isEqualTo(first.getObjectiveDecision());
        assertThat(signatures(secondEvents)).isEqualTo(signatures(firstEvents));
        assertThat(secondRandom.doubleCalls).isEqualTo(firstRandom.doubleCalls);
        assertThat(secondRandom.intCalls).isEqualTo(firstRandom.intCalls);
        assertThat(secondRandom.booleanCalls).isEqualTo(firstRandom.booleanCalls);
    }

    private java.util.Optional<MatchEvent> resolve(
            GameState state, Random random, List<MatchEvent> events) {
        return resolver.resolve(state, ObjectiveType.DRAGON, TeamSide.BLUE, 0, random,
                new ObjectiveResolver(), new StructureResolver(), events, null);
    }

    private List<String> signatures(List<MatchEvent> events) {
        return events.stream().map(event -> event.getTimeSeconds() + "|" + event.getType()
                + "|" + event.getCombatSource() + "|" + event.getKillerPlayerId()
                + "|" + event.getVictimPlayerId() + "|" + event.getObjectiveDecision()).toList();
    }

    private static final class ScriptedRandom extends Random {
        private final double doubleValue;
        private int doubleCalls;
        private int intCalls;
        private int booleanCalls;

        private ScriptedRandom(double doubleValue) { this.doubleValue = doubleValue; }

        @Override public double nextDouble() { doubleCalls++; return doubleValue; }
        @Override public int nextInt(int bound) { intCalls++; return 0; }
        @Override public boolean nextBoolean() { booleanCalls++; return false; }
    }
}

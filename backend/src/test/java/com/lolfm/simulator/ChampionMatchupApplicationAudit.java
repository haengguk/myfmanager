package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionMatchupResolver;
import com.lolfm.champion.ChampionMatchupTestCatalogFactory;
import com.lolfm.champion.ChampionMatchupTestFixture;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;

final class ChampionMatchupApplicationAudit {
    private static final List<String> PARTICIPANT_MODES = List.of(
            "BOTH_ALIVE_PARTICIPANTS", "SOURCE_DEAD", "OPPONENT_DEAD",
            "SOURCE_NON_PARTICIPANT", "OPPONENT_NON_PARTICIPANT",
            "CROSS_POSITION", "SAME_TEAM_INVALID", "MISSING_ASSIGNMENT");

    List<ChampionMatchupApplicationRow> run() {
        List<ChampionMatchupApplicationRow> rows = new ArrayList<>();
        for (var pair : ChampionMatchupTestCatalogFactory.pairs()) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                for (String direction : List.of("FORWARD", "REVERSE")) {
                    for (String participantMode : PARTICIPANT_MODES) {
                        for (ChampionMatchupMode featureMode : ChampionMatchupMode.values()) {
                            rows.add(row(pair, context, direction, participantMode, featureMode));
                        }
                    }
                }
            }
        }
        return List.copyOf(rows);
    }

    private ChampionMatchupApplicationRow row(
            ChampionMatchupTestCatalogFactory.FocusedPair pair,
            ProgressionCombatContext context,
            String direction,
            String participantMode,
            ChampionMatchupMode featureMode
    ) {
        ChampionMatchupTestFixture fixture =
                new ChampionMatchupTestFixture(featureMode, true);
        boolean forward = direction.equals("FORWARD");
        TeamSide sourceSide = forward ? TeamSide.BLUE : TeamSide.RED;
        TeamSide opponentSide = sourceSide.opposite();
        PlayerState source = player(fixture, sourceSide, pair.position());
        PlayerState opponent = player(fixture, opponentSide, pair.position());
        GameState state = fixture.state();
        if (participantMode.equals("SOURCE_DEAD")) source.markDead(0, 60);
        if (participantMode.equals("OPPONENT_DEAD")) opponent.markDead(0, 60);
        if (participantMode.equals("SOURCE_NON_PARTICIPANT")) {
            source = outside("source-outside", pair.position());
        }
        if (participantMode.equals("OPPONENT_NON_PARTICIPANT")) {
            opponent = outside("opponent-outside", pair.position());
        }
        if (participantMode.equals("CROSS_POSITION")) {
            Position other = pair.position() == Position.TOP ? Position.MID : Position.TOP;
            opponent = player(fixture, opponentSide, other);
        }
        if (participantMode.equals("SAME_TEAM_INVALID")) {
            opponentSide = sourceSide;
            opponent = player(fixture, sourceSide, pair.position());
        }
        if (participantMode.equals("MISSING_ASSIGNMENT")) {
            state = new GameState(fixture.blueTeam(), fixture.redTeam());
            state.configureChampionMatchup(
                    ChampionMatchupTestCatalogFactory.focused(fixture.champions()),
                    featureMode);
        }
        int goldBefore = source.getGold() + opponent.getGold();
        int deathsBefore = source.getDeaths() + opponent.getDeaths();
        boolean slotBefore = state.wasMajorCombatAttemptedThisTick();
        var result = new ChampionMatchupResolver().evaluate(
                state, List.of(source), List.of(opponent), context,
                ProgressionApplicationStage.COMBAT_SCORE);
        boolean mutation = goldBefore != source.getGold() + opponent.getGold()
                || deathsBefore != source.getDeaths() + opponent.getDeaths()
                || slotBefore != state.wasMajorCombatAttemptedThisTick();
        boolean validParticipants = participantMode.equals("BOTH_ALIVE_PARTICIPANTS");
        int expectedApplications =
                featureMode == ChampionMatchupMode.ON && validParticipants ? 1 : 0;
        double expectedEdge = featureMode == ChampionMatchupMode.ON
                && validParticipants && context == pair.focusContext()
                ? (forward ? .25 : -.25) : 0.0;
        boolean passed = result.eligiblePairCount() == expectedApplications
                && Math.abs(result.matchupEdge() - expectedEdge) < 1e-12
                && state.getChampionMatchupExecutionStats().snapshot().directRandomCalls() == 0
                && !mutation;
        return new ChampionMatchupApplicationRow(
                pair.position() + ":" + pair.forwardChampion() + ":" + pair.reverseChampion(),
                pair.position(), context, direction, participantMode, featureMode,
                forward ? pair.forwardChampion() : pair.reverseChampion(),
                forward ? pair.reverseChampion() : pair.forwardChampion(),
                sourceSide, opponentSide, source.isAlive(state.getCurrentTimeSeconds()),
                opponent.isAlive(state.getCurrentTimeSeconds()),
                state.playerKeyOf(source).isPresent(), state.playerKeyOf(opponent).isPresent(),
                result.eligiblePairCount(), expectedEdge, result.matchupEdge(),
                result.pairContributions().size(), skipReason(participantMode, featureMode),
                state.getChampionMatchupExecutionStats().snapshot().directRandomCalls(),
                mutation, passed ? "PASS" : "FAIL");
    }

    private PlayerState player(
            ChampionMatchupTestFixture fixture,
            TeamSide side,
            Position position
    ) {
        return side == TeamSide.BLUE ? fixture.blue(position) : fixture.red(position);
    }

    private PlayerState outside(String name, Position position) {
        return new PlayerState(
                name, position, new PlayerAttributes(15, 15, 15, 15), 500);
    }

    private String skipReason(String participantMode, ChampionMatchupMode mode) {
        if (mode == ChampionMatchupMode.OFF) return "FEATURE_OFF";
        return participantMode.equals("BOTH_ALIVE_PARTICIPANTS")
                ? "NONE" : participantMode;
    }
}

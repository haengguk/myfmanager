package com.lolfm.simulator;

import com.lolfm.champion.ChampionLineupRequest;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionMatchupResolver;
import com.lolfm.champion.ChampionMatchupTestCatalogFactory;
import com.lolfm.champion.ChampionMatchupTestFixture;
import com.lolfm.champion.ChampionSelectionRequest;
import com.lolfm.champion.ChampionSelectionValidator;
import java.util.ArrayList;
import java.util.List;

final class ChampionMatchupMirrorAudit {
    List<ChampionMatchupMirrorRow> run() {
        List<ChampionMatchupMirrorRow> rows = new ArrayList<>();
        for (var pair : ChampionMatchupTestCatalogFactory.pairs()) {
            ChampionMatchupTestFixture original =
                    new ChampionMatchupTestFixture(ChampionMatchupMode.ON, true);
            var originalResult = evaluate(
                    original.state(), original.blue(pair.position()),
                    original.red(pair.position()), pair.focusContext());
            GameState mirrored = mirroredState(original);
            var mirroredBlueResult = evaluate(
                    mirrored, mirrored.getBlueTeamState().playerAt(pair.position()),
                    mirrored.getRedTeamState().playerAt(pair.position()), pair.focusContext());
            var mirroredLogicalResult = evaluate(
                    mirrored, mirrored.getRedTeamState().playerAt(pair.position()),
                    mirrored.getBlueTeamState().playerAt(pair.position()), pair.focusContext());
            ChampionMatchupCatalog neutral =
                    ChampionMatchupCatalog.neutral(original.champions());
            double neutralOriginal = neutral.contribution(
                    new com.lolfm.champion.ChampionId(pair.forwardChampion()),
                    new com.lolfm.champion.ChampionId(pair.reverseChampion()),
                    pair.position(), pair.focusContext());
            double neutralMirror = neutral.contribution(
                    new com.lolfm.champion.ChampionId(pair.reverseChampion()),
                    new com.lolfm.champion.ChampionId(pair.forwardChampion()),
                    pair.position(), pair.focusContext());
            boolean logical = originalResult.matchupEdge()
                    == mirroredLogicalResult.matchupEdge();
            boolean reversed = originalResult.matchupEdge()
                    == -mirroredBlueResult.matchupEdge();
            boolean zero = neutralOriginal == 0.0 && neutralMirror == 0.0
                    && Double.doubleToRawLongBits(neutralOriginal)
                    == Double.doubleToRawLongBits(neutralMirror);
            rows.add(new ChampionMatchupMirrorRow(
                    pair.position() + ":" + pair.forwardChampion()
                            + ":" + pair.reverseChampion(),
                    pair.position(), pair.focusContext(), originalResult.matchupEdge(),
                    mirroredBlueResult.matchupEdge(), originalResult.matchupEdge(),
                    mirroredLogicalResult.matchupEdge(), neutralOriginal, neutralMirror,
                    originalResult.eligiblePairCount()
                            + mirroredBlueResult.eligiblePairCount()
                            + mirroredLogicalResult.eligiblePairCount(),
                    original.state().getChampionMatchupExecutionStats().snapshot()
                            .directRandomCalls()
                            + mirrored.getChampionMatchupExecutionStats().snapshot()
                            .directRandomCalls(),
                    logical, reversed, zero,
                    logical && reversed && zero ? "PASS" : "FAIL"));
        }
        return List.copyOf(rows);
    }

    private com.lolfm.champion.ChampionMatchupResult evaluate(
            GameState state,
            PlayerState source,
            PlayerState opponent,
            ProgressionCombatContext context
    ) {
        return new ChampionMatchupResolver().evaluate(
                state, List.of(source), List.of(opponent), context,
                ProgressionApplicationStage.COMBAT_SCORE);
    }

    private GameState mirroredState(ChampionMatchupTestFixture fixture) {
        ChampionLineupRequest forward = new ChampionLineupRequest(
                "renekton", "lee-sin", "leblanc", "lucian", "nautilus");
        ChampionLineupRequest reverse = new ChampionLineupRequest(
                "jax", "viego", "viktor", "jinx", "lulu");
        var assignments = new ChampionSelectionValidator(fixture.champions()).resolve(
                new ChampionSelectionRequest(reverse, forward));
        GameState state = new GameState(
                fixture.blueTeam(), fixture.redTeam(), true, true, true,
                true, true, true, assignments);
        state.configureChampionMatchup(
                ChampionMatchupTestCatalogFactory.focused(fixture.champions()),
                ChampionMatchupMode.ON);
        return state;
    }
}

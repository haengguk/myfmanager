package com.lolfm.champion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChampionMatchupDilutionTest {
    @Test void oneNonZeroPairHasNoDilution() {
        assertEquals(1.0, result(.20).matchupDilutionRatio(), 1e-12);
    }

    @Test void oneOfTwoNonZeroPairShowsExpectedDilution() {
        ChampionMatchupResult result = result(.20, 0.0);
        assertEquals(2, result.matchupEligiblePairCount());
        assertEquals(1, result.matchupNonZeroPairCount());
        assertEquals(.5, result.matchupDilutionRatio(), 1e-12);
    }

    @Test void oneOfFiveNonZeroPairShowsExpectedDilution() {
        ChampionMatchupResult result = result(.20, 0.0, 0.0, 0.0, 0.0);
        assertEquals(.2, result.matchupDilutionRatio(), 1e-12);
    }

    @Test void allFiveNonZeroPairsUseArithmeticAverage() {
        ChampionMatchupResult result = result(.10, .20, .30, .10, .30);
        assertEquals(.20, result.matchupAverageEdge(), 1e-12);
        assertEquals(1.0, result.matchupDilutionRatio(), 1e-12);
    }

    @Test void oppositePairEdgesCanCancel() {
        ChampionMatchupResult result = result(.20, -.20);
        assertEquals(0.0, result.matchupAverageEdge());
        assertEquals(0.0, result.matchupDilutionRatio());
    }

    @Test void dilutionMetricsUseCorrectDenominators() {
        ChampionMatchupResult result = result(.30, 0.0, -.10, 0.0, 0.0);
        assertEquals(.04, result.matchupAverageEdge(), 1e-12);
        assertEquals(.10, result.matchupNonZeroAverageEdge(), 1e-12);
        assertEquals(.40, result.matchupDilutionRatio(), 1e-12);
    }

    private static ChampionMatchupResult result(double... edges) {
        List<ChampionMatchupPairContribution> values = new ArrayList<>();
        Position[] positions = Position.values();
        for (int index = 0; index < edges.length; index++) {
            Position position = positions[index];
            ChampionId first = new ChampionId("a-" + position.name().toLowerCase());
            ChampionId second = new ChampionId("b-" + position.name().toLowerCase());
            values.add(new ChampionMatchupPairContribution(
                    new ChampionMatchupPair(first, second, position),
                    new PlayerKey(TeamSide.BLUE, position),
                    new PlayerKey(TeamSide.RED, position),
                    ProgressionCombatContext.GENERIC_SKIRMISH, edges[index]));
        }
        double sum = values.stream().mapToDouble(ChampionMatchupPairContribution::edge).sum();
        return new ChampionMatchupResult(true, values.size(), sum,
                values.isEmpty() ? 0.0 : sum / values.size(), values,
                0, 0, 0, 0, 0, 0);
    }
}

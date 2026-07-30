package com.lolfm.champion;

import java.util.ArrayList;
import java.util.List;

/** Pure activation decision; verdicts are derived exclusively from measured audit inputs. */
public final class ChampionMatchupActivationGate {
    private ChampionMatchupActivationGate() {}
    public static Decision evaluate(Input input) {
        List<String> warnings = new ArrayList<>();
        if (input.nonZeroApplications() <= 0) warnings.add("NO_NON_ZERO_PRODUCTION_APPLICATION");
        if (input.winnerFlipRate() > .02) warnings.add("WINNER_FLIP_RATE_REVIEW");
        if (input.addedOrientationDifference() > .015) warnings.add("ADDED_ORIENTATION_DIFFERENCE_REVIEW");
        if (input.strongMatchupHardLocks() > 0) warnings.add("STRONG_MATCHUP_HARD_LOCK");
        if (input.championPowerHardLocks() > 0) warnings.add("CHAMPION_POWER_HARD_LOCK");
        if (input.unsupportedParityRows() > 0) warnings.add("UNSUPPORTED_DYNAMIC_PARITY_ROWS");
        if (!input.modeIsolationExact()) warnings.add("MODE_ISOLATION_WARNING");
        if (input.integrityErrorCount() > 0) {
            return new Decision("BLOCKED_BY_MATCHUP_PRODUCTION_INTEGRITY", ChampionMatchupMode.OFF,
                    false, false, List.copyOf(warnings), input.integrityErrorCount());
        }
        if (!warnings.isEmpty()) {
            return new Decision("REVIEW_MATCHUP_PRODUCTION_ACTIVATION", ChampionMatchupMode.OFF,
                    false, false, List.copyOf(warnings), 0);
        }
        return new Decision("MATCHUP_PRODUCTION_ACTIVATED", ChampionMatchupMode.GEOMETRIC_V2,
                true, true, List.of(), 0);
    }
    public record Input(int integrityErrorCount, long nonZeroApplications, double winnerFlipRate,
                        double addedOrientationDifference, long strongMatchupHardLocks,
                        long championPowerHardLocks, long unsupportedParityRows, boolean modeIsolationExact) {}
    public record Decision(String verdict, ChampionMatchupMode defaultMode,
                           boolean productionActivationAllowed, boolean productionActivated,
                           List<String> warningCodes, int integrityErrorCount) {
        public Decision { warningCodes = List.copyOf(warningCodes); }
    }
}

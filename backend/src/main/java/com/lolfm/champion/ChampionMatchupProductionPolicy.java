package com.lolfm.champion;

/** Immutable internal production rules; none of these values are runtime tuning inputs. */
public record ChampionMatchupProductionPolicy(
        ChampionMatchupMode mode, String formulaVersion, String profileVersion,
        String profileHash, double gain, double deadzone,
        int pairOverrideCount, int mechanicOverrideCount, String rulesVersion
) {
    public static final String FORMULA_VERSION = "EXPOSURE_GATED_GEOMETRIC_V2";
    public static final String PROFILE_VERSION = "initial-30-role-matchup-profile-candidate-v1";
    public static final String PROFILE_HASH = "c8956937e8c9032654feb2bb17ff7ef66d68a964b4f1f6ed98853400f5b3dc64";
    public static final String RULES_VERSION = "champion-matchup-geometric-v2-g1";
    public static final ChampionMatchupProductionPolicy GEOMETRIC_V2 = new ChampionMatchupProductionPolicy(
            ChampionMatchupMode.GEOMETRIC_V2, FORMULA_VERSION, PROFILE_VERSION, PROFILE_HASH,
            1.0, 0.0, 0, 0, RULES_VERSION);

    public ChampionMatchupProductionPolicy {
        if (mode != ChampionMatchupMode.GEOMETRIC_V2 || gain != 1.0 || deadzone != 0.0
                || pairOverrideCount != 0 || mechanicOverrideCount != 0) {
            throw new IllegalArgumentException("Production matchup policy is frozen");
        }
    }
}

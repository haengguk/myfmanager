package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.composition.TeamCompositionGameplayMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Closed registry of versioned, application-selectable runtime profiles. */
public final class SimulationRuntimeProfiles {
    public static final String PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION =
            "MATCH_SIMULATOR_PRE_JUNGLE_RULES_V3";
    public static final String JUNGLE_ECONOMY_ACTIVE_GAMEPLAY_RULES_VERSION =
            "MATCH_SIMULATOR_JUNGLE_ECONOMY_RULES_V3";
    public static final String JUNGLE_TEMPO_ACTIVE_GAMEPLAY_RULES_VERSION =
            "MATCH_SIMULATOR_JUNGLE_TEMPO_RULES_V2";
    public static final String CONFIGURATION_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_FIELD_LINES_TRAILING_NEWLINE_V1";

    private static final Map<SimulationRuntimeProfileId, ResolvedSimulationRuntimeProfile> PROFILES =
            buildProfiles();

    private SimulationRuntimeProfiles() {
    }

    public static ResolvedSimulationRuntimeProfile resolve(SimulationRuntimeProfileId profileId) {
        ResolvedSimulationRuntimeProfile resolved = PROFILES.get(
                Objects.requireNonNull(profileId, "profileId"));
        if (resolved == null) throw new IllegalArgumentException("Unknown runtime profile " + profileId);
        return resolved;
    }

    public static Map<SimulationRuntimeProfileId, ResolvedSimulationRuntimeProfile> all() {
        return PROFILES;
    }

    /** Rejects caller-fabricated resolved values at production execution boundaries. */
    public static ResolvedSimulationRuntimeProfile requireRegistered(
            ResolvedSimulationRuntimeProfile candidate
    ) {
        Objects.requireNonNull(candidate, "candidate");
        ResolvedSimulationRuntimeProfile registered = resolve(candidate.profileId());
        if (!registered.equals(candidate)) {
            throw new IllegalArgumentException(
                    "Runtime profile must be the exact closed-registry resolution for "
                            + candidate.profileId());
        }
        return registered;
    }

    public static String configurationHash(SimulationGameplayConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    configuration.canonicalSerialization().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Map<SimulationRuntimeProfileId, ResolvedSimulationRuntimeProfile> buildProfiles() {
        EnumMap<SimulationRuntimeProfileId, ResolvedSimulationRuntimeProfile> result =
                new EnumMap<>(SimulationRuntimeProfileId.class);
        register(result, SimulationRuntimeProfileId.BASELINE_V1,
                exactConfiguration(ChampionMatchupMode.OFF, TeamCompositionGameplayMode.OFF),
                "c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215",
                PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        register(result, SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                exactConfiguration(ChampionMatchupMode.GEOMETRIC_V2,
                        TeamCompositionGameplayMode.OFF),
                "58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec",
                PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        register(result, SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
                exactConfiguration(ChampionMatchupMode.GEOMETRIC_V2,
                        TeamCompositionGameplayMode.PRODUCTION_V2),
                "caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d",
                PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        register(result, SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1,
                exactConfiguration(ChampionMatchupMode.GEOMETRIC_V2,
                        TeamCompositionGameplayMode.PRODUCTION_V2),
                "caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d",
                PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        register(result,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                exactConfiguration(ChampionMatchupMode.GEOMETRIC_V2,
                        TeamCompositionGameplayMode.PRODUCTION_V2,
                        JungleClearContribution.ECONOMY_V1),
                "e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b",
                JUNGLE_ECONOMY_ACTIVE_GAMEPLAY_RULES_VERSION);
        register(result,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1,
                exactConfiguration(ChampionMatchupMode.GEOMETRIC_V2,
                        TeamCompositionGameplayMode.PRODUCTION_V2,
                        JungleClearContribution.ECONOMY_AND_GANK_TEMPO_V1),
                "c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c",
                JUNGLE_TEMPO_ACTIVE_GAMEPLAY_RULES_VERSION);
        return Collections.unmodifiableMap(result);
    }

    private static SimulationGameplayConfiguration exactConfiguration(
            ChampionMatchupMode matchupMode,
            TeamCompositionGameplayMode compositionMode
    ) {
        return exactConfiguration(matchupMode, compositionMode,
                JungleClearContribution.DISABLED_NOT_INTEGRATED);
    }

    private static SimulationGameplayConfiguration exactConfiguration(
            ChampionMatchupMode matchupMode,
            TeamCompositionGameplayMode compositionMode,
            JungleClearContribution jungleClearContribution
    ) {
        return new SimulationGameplayConfiguration(
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, matchupMode, compositionMode,
                jungleClearContribution);
    }

    private static void register(
            Map<SimulationRuntimeProfileId, ResolvedSimulationRuntimeProfile> target,
            SimulationRuntimeProfileId profileId,
            SimulationGameplayConfiguration configuration,
            String expectedHash,
            String activeGameplayRulesVersion
    ) {
        String actualHash = configurationHash(configuration);
        if (!expectedHash.equals(actualHash)) {
            throw new IllegalStateException(profileId
                    + " semantics changed without a versioned profile ID: expected="
                    + expectedHash + " actual=" + actualHash);
        }
        target.put(profileId,
                new ResolvedSimulationRuntimeProfile(
                        profileId, configuration, actualHash,
                        activeGameplayRulesVersion));
    }
}

package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Phase13GB1AuditContractTest {
    private static final Map<SimulationRuntimeProfileId, String> EXPECTED_HASHES = Map.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            "c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215",
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            "58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec",
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
            "caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d",
            SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
            "e04869bca5281f7f416c8191d7bf1b5be04b3129f33f6dfd4de83e8d8e92743b",
            SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1,
            "c835280cbaa1244f4fecb099b19f71111c6d77aa1aeb1b7110a6e86e6381451c");

    @Test
    void freezesTheExactFiveProfileComparisonOrderAndIdentity() {
        assertThat(Phase13GB1RealMatchHarness.AUDIT_PROFILES).containsExactly(
                SimulationRuntimeProfileId.BASELINE_V1,
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1);
        assertThat(Phase13GB1RealMatchHarness.AUDIT_PROFILES)
                .doesNotContain(SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1);
        assertThat(SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1)
                .gameplayConfiguration()).isEqualTo(SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1)
                .gameplayConfiguration());
        assertThat(Phase13GB1RealMatchHarness.AUDIT_PROFILES).allSatisfy(profileId -> {
            var profile = SimulationRuntimeProfiles.resolve(profileId);
            assertThat(profile.configurationHash()).isEqualTo(EXPECTED_HASHES.get(profileId));
            assertThat(SimulationRuntimeProfiles.configurationHash(
                    profile.gameplayConfiguration())).isEqualTo(profile.configurationHash());
        });
        assertThat(Phase13GB1RealMatchHarness.AUDIT_PROFILES.stream()
                .map(profileId -> SimulationRuntimeProfiles.resolve(profileId).configurationHash()))
                .doesNotHaveDuplicates();
    }

    @Test
    void separatesPreJungleEconomyAndTempoRulesWithoutChangingTheirMeaning() {
        assertThat(rules(SimulationRuntimeProfileId.BASELINE_V1))
                .isEqualTo(SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        assertThat(rules(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1))
                .isEqualTo(SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        assertThat(rules(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1))
                .isEqualTo(SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);
        assertThat(rules(SimulationRuntimeProfileId
                .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1))
                .isEqualTo(SimulationRuntimeProfiles.JUNGLE_ECONOMY_ACTIVE_GAMEPLAY_RULES_VERSION);
        assertThat(rules(SimulationRuntimeProfileId
                .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1))
                .isEqualTo(SimulationRuntimeProfiles.JUNGLE_TEMPO_ACTIVE_GAMEPLAY_RULES_VERSION);

        assertThat(contribution(SimulationRuntimeProfileId.BASELINE_V1))
                .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED);
        assertThat(contribution(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1))
                .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED);
        assertThat(contribution(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1))
                .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED);
        assertThat(contribution(SimulationRuntimeProfileId
                .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1))
                .isEqualTo(JungleClearContribution.ECONOMY_V1);
        assertThat(contribution(SimulationRuntimeProfileId
                .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1))
                .isEqualTo(JungleClearContribution.ECONOMY_AND_GANK_TEMPO_V1);
    }

    @Test
    void preparedFixtureHasNoCallerAccessibleConstructionPath() {
        assertThat(Phase13GB1RealMatchHarness.PreparedFixture.class.getDeclaredConstructors())
                .isNotEmpty()
                .allSatisfy(constructor -> assertThat(
                        Modifier.isPrivate(constructor.getModifiers())).isTrue());
    }

    private static String rules(SimulationRuntimeProfileId profileId) {
        return SimulationRuntimeProfiles.resolve(profileId).activeGameplayRulesVersion();
    }

    private static JungleClearContribution contribution(SimulationRuntimeProfileId profileId) {
        return SimulationRuntimeProfiles.resolve(profileId)
                .gameplayConfiguration().jungleClearContribution();
    }
}

package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.composition.TeamCompositionGameplayMode;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulationRuntimeProfilesTest {
    @Test
    void resolvesExactlyThreeFrozenProfilesWithFieldCompleteSemantics() {
        Map<SimulationRuntimeProfileId, ResolvedSimulationRuntimeProfile> profiles =
                SimulationRuntimeProfiles.all();

        assertThat(profiles).hasSize(3).containsOnlyKeys(SimulationRuntimeProfileId.values());
        assertThat(profiles.values())
                .extracting(ResolvedSimulationRuntimeProfile::activeGameplayRulesVersion)
                .containsOnly("MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2");
        assertExactCommonGameplay(profiles.get(SimulationRuntimeProfileId.BASELINE_V1));
        assertExactCommonGameplay(
                profiles.get(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1));
        assertExactCommonGameplay(
                profiles.get(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1));

        assertThat(profiles.get(SimulationRuntimeProfileId.BASELINE_V1)
                .gameplayConfiguration().championMatchupMode())
                .isEqualTo(ChampionMatchupMode.OFF);
        assertThat(profiles.get(SimulationRuntimeProfileId.BASELINE_V1)
                .gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.OFF);
        assertThat(profiles.get(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1)
                .gameplayConfiguration().championMatchupMode())
                .isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);
        assertThat(profiles.get(SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1)
                .gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.OFF);
        assertThat(profiles.get(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1)
                .gameplayConfiguration().championMatchupMode())
                .isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);
        assertThat(profiles.get(SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1)
                .gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.PRODUCTION_V2);
    }

    @Test
    void frozenConfigurationHashesDetectUnversionedSemanticChanges() {
        assertThat(SimulationRuntimeProfiles.resolve(SimulationRuntimeProfileId.BASELINE_V1)
                .configurationHash())
                .isEqualTo("c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215");
        assertThat(SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1).configurationHash())
                .isEqualTo("58714464c19a2cffd108d47a93a0909126513c8bb10cb0e19bbd87f8e78532ec");
        assertThat(SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1).configurationHash())
                .isEqualTo("caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d");
    }

    @Test
    void instrumentationIsExcludedFromGameplayCanonicalizationAndHash() {
        ResolvedSimulationRuntimeProfile baseline = SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.BASELINE_V1);
        SimulationOptions diagnosticsOn = baseline.gameplayConfiguration()
                .toSimulationOptions(SimulationInstrumentation.enabled());
        SimulationOptions diagnosticsOff = baseline.gameplayConfiguration()
                .toSimulationOptions(SimulationInstrumentation.disabled());

        assertThat(diagnosticsOn.diagnosticsEnabled()).isTrue();
        assertThat(diagnosticsOff.diagnosticsEnabled()).isFalse();
        assertThat(diagnosticsOn).usingRecursiveComparison()
                .ignoringFields("diagnosticsEnabled").isEqualTo(diagnosticsOff);
        assertThat(baseline.gameplayConfiguration().canonicalSerialization())
                .doesNotContain("diagnostics");
        assertThat(SimulationRuntimeProfiles.configurationHash(
                baseline.gameplayConfiguration())).isEqualTo(baseline.configurationHash());
    }

    @Test
    void baselineOptionsEqualTheExistingAutowiredConstructorSnapshot() {
        SimulationOptions existingSpringSnapshot = new SimulationOptions(
                true, true, true, true, true, true, true, true);
        SimulationOptions explicitBaseline = SimulationRuntimeProfiles.resolve(
                        SimulationRuntimeProfileId.BASELINE_V1)
                .gameplayConfiguration().toSimulationOptions(SimulationInstrumentation.enabled());

        assertThat(explicitBaseline).isEqualTo(existingSpringSnapshot);
    }

    @Test
    void profileRegistryAndModesDoNotExposeAuditOnlyRuntimePaths() {
        assertThatThrownBy(() -> new SimulationGameplayConfiguration(
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, ChampionMatchupMode.ON,
                TeamCompositionGameplayMode.OFF,
                JungleClearContribution.DISABLED_NOT_INTEGRATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not authorize matchup mode");
        assertThatThrownBy(() -> new SimulationGameplayConfiguration(
                true, true, true, true, true, true, true, true, true, true,
                true, true, true, ChampionMatchupMode.OFF,
                TeamCompositionGameplayMode.SHADOW,
                JungleClearContribution.DISABLED_NOT_INTEGRATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not authorize composition mode");
        assertThatThrownBy(() -> SimulationRuntimeProfiles.all().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void callerFabricatedResolvedProfileIsRejectedByTheClosedRegistry() {
        SimulationGameplayConfiguration custom = new SimulationGameplayConfiguration(
                false, true, true, true, true, true, true, true, true, true,
                true, true, true, ChampionMatchupMode.OFF,
                TeamCompositionGameplayMode.OFF,
                JungleClearContribution.DISABLED_NOT_INTEGRATED);
        ResolvedSimulationRuntimeProfile fabricated = new ResolvedSimulationRuntimeProfile(
                SimulationRuntimeProfileId.BASELINE_V1,
                custom,
                SimulationRuntimeProfiles.configurationHash(custom),
                SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION);

        assertThatThrownBy(() -> SimulationRuntimeProfiles.requireRegistered(fabricated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact closed-registry resolution");
    }

    @Test
    void applicationFactoryPublicCreateBoundaryAcceptsOnlyProfileIdAndInstrumentation() {
        var publicCreateMethods = Arrays.stream(ConfiguredMatchSimulatorFactory.class
                        .getDeclaredMethods())
                .filter(method -> method.getName().equals("create"))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();

        assertThat(publicCreateMethods).singleElement().satisfies(method ->
                assertThat(method.getParameterTypes()).containsExactly(
                        SimulationRuntimeProfileId.class, SimulationInstrumentation.class));
    }

    private static void assertExactCommonGameplay(ResolvedSimulationRuntimeProfile profile) {
        SimulationGameplayConfiguration configuration = profile.gameplayConfiguration();
        assertThat(configuration.laneCombatEnabled()).isTrue();
        assertThat(configuration.farmRecoveryEnabled()).isTrue();
        assertThat(configuration.jungleGankEnabled()).isTrue();
        assertThat(configuration.counterGankEnabled()).isTrue();
        assertThat(configuration.roamEnabled()).isTrue();
        assertThat(configuration.objectivePriorityEnabled()).isTrue();
        assertThat(configuration.lanePhaseEnabled()).isTrue();
        assertThat(configuration.midGameMacroEnabled()).isTrue();
        assertThat(configuration.objectiveDecisionEnabled()).isTrue();
        assertThat(configuration.lateGameMacroEnabled()).isTrue();
        assertThat(configuration.progressionEnabled()).isTrue();
        assertThat(configuration.progressionPowerEnabled()).isTrue();
        assertThat(configuration.championPowerEnabled()).isTrue();
        assertThat(configuration.jungleClearContribution())
                .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED);
    }
}

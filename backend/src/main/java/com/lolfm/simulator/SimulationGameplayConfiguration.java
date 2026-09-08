package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.composition.TeamCompositionGameplayMode;
import java.util.Objects;

/** Exact gameplay semantics owned by a versioned runtime profile. */
public record SimulationGameplayConfiguration(
        boolean laneCombatEnabled,
        boolean farmRecoveryEnabled,
        boolean jungleGankEnabled,
        boolean counterGankEnabled,
        boolean roamEnabled,
        boolean objectivePriorityEnabled,
        boolean lanePhaseEnabled,
        boolean midGameMacroEnabled,
        boolean objectiveDecisionEnabled,
        boolean lateGameMacroEnabled,
        boolean progressionEnabled,
        boolean progressionPowerEnabled,
        boolean championPowerEnabled,
        ChampionMatchupMode championMatchupMode,
        TeamCompositionGameplayMode teamCompositionGameplayMode,
        JungleClearContribution jungleClearContribution,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) boolean realismEnabled,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) boolean boundaryFixesEnabled
) {
    public static final String SCHEMA = "EXPLICIT_SIMULATION_RUNTIME_CONFIGURATION_V1";

    /** Legacy constructors retain V9 semantics. */
    public SimulationGameplayConfiguration(
            boolean laneCombatEnabled, boolean farmRecoveryEnabled, boolean jungleGankEnabled,
            boolean counterGankEnabled, boolean roamEnabled, boolean objectivePriorityEnabled,
            boolean lanePhaseEnabled, boolean midGameMacroEnabled, boolean objectiveDecisionEnabled,
            boolean lateGameMacroEnabled, boolean progressionEnabled, boolean progressionPowerEnabled,
            boolean championPowerEnabled, ChampionMatchupMode championMatchupMode,
            TeamCompositionGameplayMode teamCompositionGameplayMode,
            JungleClearContribution jungleClearContribution, boolean realismEnabled
    ) {
        this(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled, counterGankEnabled,
                roamEnabled, objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled,
                objectiveDecisionEnabled, lateGameMacroEnabled, progressionEnabled,
                progressionPowerEnabled, championPowerEnabled, championMatchupMode,
                teamCompositionGameplayMode, jungleClearContribution, realismEnabled, false);
    }

    /** Legacy constructors retain V9 semantics. */
    public SimulationGameplayConfiguration(
            boolean laneCombatEnabled, boolean farmRecoveryEnabled, boolean jungleGankEnabled,
            boolean counterGankEnabled, boolean roamEnabled, boolean objectivePriorityEnabled,
            boolean lanePhaseEnabled, boolean midGameMacroEnabled, boolean objectiveDecisionEnabled,
            boolean lateGameMacroEnabled, boolean progressionEnabled, boolean progressionPowerEnabled,
            boolean championPowerEnabled, ChampionMatchupMode championMatchupMode,
            TeamCompositionGameplayMode teamCompositionGameplayMode,
            JungleClearContribution jungleClearContribution
    ) {
        this(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled, counterGankEnabled,
                roamEnabled, objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled,
                objectiveDecisionEnabled, lateGameMacroEnabled, progressionEnabled,
                progressionPowerEnabled, championPowerEnabled, championMatchupMode,
                teamCompositionGameplayMode, jungleClearContribution, false);
    }

    public SimulationGameplayConfiguration {
        Objects.requireNonNull(championMatchupMode, "championMatchupMode");
        Objects.requireNonNull(teamCompositionGameplayMode, "teamCompositionGameplayMode");
        Objects.requireNonNull(jungleClearContribution, "jungleClearContribution");
        if (!progressionEnabled && progressionPowerEnabled) {
            throw new IllegalArgumentException(
                    "progressionPowerEnabled requires progressionEnabled");
        }
        if (championMatchupMode != ChampionMatchupMode.OFF
                && championMatchupMode != ChampionMatchupMode.GEOMETRIC_V2) {
            throw new IllegalArgumentException(
                    "Runtime profiles do not authorize matchup mode " + championMatchupMode);
        }
        if (teamCompositionGameplayMode != TeamCompositionGameplayMode.OFF
                && teamCompositionGameplayMode != TeamCompositionGameplayMode.PRODUCTION_V2) {
            throw new IllegalArgumentException(
                    "Runtime profiles do not authorize composition mode "
                            + teamCompositionGameplayMode);
        }
    }

    /** Stable, field-complete serialization used only for gameplay configuration identity. */
    public String canonicalSerialization() {
        return "simulationGameplayConfigurationSchema=" + SCHEMA + '\n'
                + "laneCombatEnabled=" + laneCombatEnabled + '\n'
                + "farmRecoveryEnabled=" + farmRecoveryEnabled + '\n'
                + "jungleGankEnabled=" + jungleGankEnabled + '\n'
                + "counterGankEnabled=" + counterGankEnabled + '\n'
                + "roamEnabled=" + roamEnabled + '\n'
                + "objectivePriorityEnabled=" + objectivePriorityEnabled + '\n'
                + "lanePhaseEnabled=" + lanePhaseEnabled + '\n'
                + "midGameMacroEnabled=" + midGameMacroEnabled + '\n'
                + "objectiveDecisionEnabled=" + objectiveDecisionEnabled + '\n'
                + "lateGameMacroEnabled=" + lateGameMacroEnabled + '\n'
                + "progressionEnabled=" + progressionEnabled + '\n'
                + "progressionPowerEnabled=" + progressionPowerEnabled + '\n'
                + "championPowerEnabled=" + championPowerEnabled + '\n'
                + "championMatchupMode=" + championMatchupMode.name() + '\n'
                + "teamCompositionGameplayMode=" + teamCompositionGameplayMode.name() + '\n'
                + "jungleClearContribution=" + jungleClearContribution.name() + '\n'
                + (realismEnabled ? "realismRules=MATCH_REALISM_V1\n" : "")
                + (boundaryFixesEnabled ? "boundaryFixes=UPPER_LOCAL_ELIGIBILITY_V2\n" : "");
    }

    public SimulationOptions toSimulationOptions(SimulationInstrumentation instrumentation) {
        Objects.requireNonNull(instrumentation, "instrumentation");
        return new SimulationOptions(
                laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled,
                counterGankEnabled, roamEnabled, instrumentation.diagnosticsEnabled(),
                objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled,
                objectiveDecisionEnabled, lateGameMacroEnabled, progressionEnabled,
                progressionPowerEnabled, championPowerEnabled, championMatchupMode,
                teamCompositionGameplayMode, jungleClearContribution, realismEnabled, boundaryFixesEnabled);
    }
}

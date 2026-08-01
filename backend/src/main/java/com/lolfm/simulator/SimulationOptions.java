package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.composition.TeamCompositionGameplayMode;
import java.util.Objects;

/** Per-simulator feature and observational-instrumentation options. */
public record SimulationOptions(
        boolean laneCombatEnabled,
        boolean farmRecoveryEnabled,
        boolean jungleGankEnabled,
        boolean counterGankEnabled,
        boolean roamEnabled,
        boolean diagnosticsEnabled,
        boolean objectivePriorityEnabled,
        boolean lanePhaseEnabled,
        boolean midGameMacroEnabled,
        boolean objectiveDecisionEnabled,
        boolean lateGameMacroEnabled,
        boolean progressionEnabled,
        boolean progressionPowerEnabled,
        boolean championPowerEnabled,
        ChampionMatchupMode championMatchupMode,
        TeamCompositionGameplayMode teamCompositionGameplayMode
) {
    public SimulationOptions {
        Objects.requireNonNull(championMatchupMode, "championMatchupMode");
        Objects.requireNonNull(teamCompositionGameplayMode, "teamCompositionGameplayMode");
        if (!progressionEnabled) progressionPowerEnabled = false;
    }

    public SimulationOptions(
            boolean laneCombatEnabled, boolean farmRecoveryEnabled, boolean jungleGankEnabled,
            boolean counterGankEnabled, boolean roamEnabled, boolean diagnosticsEnabled,
            boolean objectivePriorityEnabled, boolean lanePhaseEnabled, boolean midGameMacroEnabled,
            boolean objectiveDecisionEnabled, boolean lateGameMacroEnabled, boolean progressionEnabled,
            boolean progressionPowerEnabled, boolean championPowerEnabled, ChampionMatchupMode championMatchupMode
    ) {
        this(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled, counterGankEnabled, roamEnabled,
                diagnosticsEnabled, objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled,
                objectiveDecisionEnabled, lateGameMacroEnabled, progressionEnabled, progressionPowerEnabled,
                championPowerEnabled, championMatchupMode, TeamCompositionGameplayMode.OFF);
    }

    public SimulationOptions(
            boolean a, boolean b, boolean c, boolean d,
            boolean e, boolean f, boolean g, boolean h
    ) {
        this(a, b, c, d, e, f, g, h,
                true, true, true, true, true, true, ChampionMatchupMode.OFF, TeamCompositionGameplayMode.OFF);
    }

    public static SimulationOptions productionDefaults() {
        return new SimulationOptions(
                true, true, true, true, true, true, true,
                true, true, true, true, true, true, true, ChampionMatchupMode.GEOMETRIC_V2, TeamCompositionGameplayMode.OFF);
    }

    private SimulationOptions copy(
            Boolean roam, Boolean diagnostics, Boolean priority, Boolean lane,
            Boolean macro, Boolean decision, Boolean late, Boolean progression,
            Boolean power, Boolean champion, ChampionMatchupMode matchup
    ) {
        return copy(roam, diagnostics, priority, lane, macro, decision, late, progression, power, champion, matchup, null);
    }

    private SimulationOptions copy(
            Boolean roam, Boolean diagnostics, Boolean priority, Boolean lane,
            Boolean macro, Boolean decision, Boolean late, Boolean progression,
            Boolean power, Boolean champion, ChampionMatchupMode matchup,
            TeamCompositionGameplayMode composition
    ) {
        return new SimulationOptions(
                laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled, counterGankEnabled,
                roam == null ? roamEnabled : roam,
                diagnostics == null ? diagnosticsEnabled : diagnostics,
                priority == null ? objectivePriorityEnabled : priority,
                lane == null ? lanePhaseEnabled : lane,
                macro == null ? midGameMacroEnabled : macro,
                decision == null ? objectiveDecisionEnabled : decision,
                late == null ? lateGameMacroEnabled : late,
                progression == null ? progressionEnabled : progression,
                power == null ? progressionPowerEnabled : power,
                champion == null ? championPowerEnabled : champion,
                matchup == null ? championMatchupMode : matchup,
                composition == null ? teamCompositionGameplayMode : composition);
    }

    public SimulationOptions withRoamEnabled(boolean value) {
        return copy(value, null, null, null, null, null, null, null, null, null, null);
    }
    public SimulationOptions withDiagnosticsEnabled(boolean value) {
        return copy(null, value, null, null, null, null, null, null, null, null, null);
    }
    public SimulationOptions withObjectivePriorityEnabled(boolean value) {
        return copy(null, null, value, null, null, null, null, null, null, null, null);
    }
    public SimulationOptions withLanePhaseEnabled(boolean value) {
        return copy(null, null, null, value, null, null, null, null, null, null, null);
    }
    public SimulationOptions withMidGameMacroEnabled(boolean value) {
        return copy(null, null, null, null, value, null, null, null, null, null, null);
    }
    public SimulationOptions withObjectiveDecisionEnabled(boolean value) {
        return copy(null, null, null, null, null, value, null, null, null, null, null);
    }
    public SimulationOptions withLateGameMacroEnabled(boolean value) {
        return copy(null, null, null, null, null, null, value, null, null, null, null);
    }
    public SimulationOptions withProgressionEnabled(boolean value) {
        return copy(null, null, null, null, null, null, null, value,
                value ? progressionPowerEnabled : false, null, null);
    }
    public SimulationOptions withProgressionPowerEnabled(boolean value) {
        return copy(null, null, null, null, null, null, null, null, value, null, null);
    }
    public SimulationOptions withChampionPowerEnabled(boolean value) {
        return copy(null, null, null, null, null, null, null, null, null, value, null);
    }
    public SimulationOptions withChampionMatchupMode(ChampionMatchupMode value) {
        return copy(null, null, null, null, null, null, null, null, null, null, value);
    }
    public SimulationOptions withTeamCompositionGameplayMode(TeamCompositionGameplayMode value) {
        return copy(null, null, null, null, null, null, null, null, null, null, null, value);
    }
    public SimulationOptions withCompositionGameplayMode(TeamCompositionGameplayMode value) {
        return withTeamCompositionGameplayMode(value);
    }
}

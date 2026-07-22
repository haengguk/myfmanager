package com.lolfm.champion;import java.util.List;
public record ChampionPowerExecutionStatsSnapshot(List<ChampionPowerCombatSample> samples,int missingAssignment,int deadParticipantIncludedError,int nonparticipantIncludedError,int duplicateParticipantError,int randomCallCount){public ChampionPowerExecutionStatsSnapshot{samples=List.copyOf(samples);}}

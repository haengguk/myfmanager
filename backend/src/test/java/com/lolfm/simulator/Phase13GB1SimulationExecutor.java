package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupExecutionStatsSnapshot;
import com.lolfm.champion.ChampionPowerExecutionStatsSnapshot;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.ObjectiveDecisionData;
import com.lolfm.domain.Team;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Test-side bridge that exposes structured diagnostics without widening the production API. */
public final class Phase13GB1SimulationExecutor {
    public static final String STRUCTURED_DIAGNOSTICS_HASH_ALGORITHM =
            "SHA256_UTF8_RECORD_COMPONENT_MAP_KEY_CANONICAL_V1";

    private Phase13GB1SimulationExecutor() {
    }

    public static Execution execute(
            ConfiguredMatchSimulatorFactory simulators,
            Team blueTeam,
            Team redTeam,
            MatchChampionAssignments assignments,
            SimulationRuntimeProfileId profileId,
            long seed,
            String blueTeamCode,
            String redTeamCode
    ) {
        Objects.requireNonNull(simulators, "simulators");
        Objects.requireNonNull(assignments, "assignments");
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                seed,
                "PHASE_13G_B_FIXED_DRAFT",
                Objects.requireNonNull(blueTeamCode, "blueTeamCode"),
                Objects.requireNonNull(redTeamCode, "redTeamCode"),
                false);
        MatchSimulator.SimulationResult result = simulators.create(
                        Objects.requireNonNull(profileId, "profileId"),
                        SimulationInstrumentation.enabled())
                .simulateWithSideDiagnostics(blueTeam, redTeam, assignments, random);
        SimulationRandomFingerprint fingerprint = random.fingerprint();
        if (result.randomDrawCount() != fingerprint.randomDrawCount()
                || !result.randomTraceHash().equals(fingerprint.randomTraceHash())) {
            throw new IllegalStateException("Structured Random diagnostics differ from fingerprint");
        }
        StructuredDiagnostics diagnostics = new StructuredDiagnostics(
                result.pushAttempts(),
                result.pushSuccesses(),
                result.pushFailureCounts(),
                result.soulOwner(),
                result.soulClaimedAtSeconds(),
                result.dragonCaptureTimes(),
                result.dragonSpawnAliveSeconds(),
                result.generalDragonAttemptCount(),
                result.generalDragonCaptureCount(),
                result.postFightDragonCaptureCount(),
                result.dragonCaptures(),
                result.pushWindowCount(),
                result.pushWindowStructureCount(),
                result.aceWindowNexusEndCount(),
                result.duplicateEconomyResolutions(),
                result.combatExecutionStats(),
                result.roamExecutionStats(),
                result.objectivePriorityExecutionStats(),
                result.lanePhaseExecutionStats(),
                result.midGameMacroExecutionStats(),
                result.objectiveDecisionExecutionStats(),
                result.objectiveDecisionHistory(),
                result.structureActionExecutionStats(),
                result.progressionExecutionStats(),
                result.jungleEconomyExecutionStats(),
                result.jungleTempoExecutionStats(),
                result.championPowerExecutionStats(),
                result.championMatchupExecutionStats(),
                result.combatOutcomeExecutionStats(),
                result.compositionRuntimeDiagnostics());
        return new Execution(
                result.timeline(),
                result.endReason(),
                result.winnerSide(),
                diagnostics,
                fingerprint);
    }

    public record Execution(
            MatchTimeline timeline,
            GameEndReason endReason,
            TeamSide winnerSide,
            StructuredDiagnostics structuredDiagnostics,
            SimulationRandomFingerprint randomFingerprint
    ) {
        public Execution {
            Objects.requireNonNull(timeline, "timeline");
            Objects.requireNonNull(endReason, "endReason");
            Objects.requireNonNull(structuredDiagnostics, "structuredDiagnostics");
            Objects.requireNonNull(randomFingerprint, "randomFingerprint");
        }
    }

    public static String structuredDiagnosticsHash(StructuredDiagnostics diagnostics) {
        return structuredValueHash(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /** Canonical structured hash for one diagnostic component, including domain-keyed maps. */
    public static String structuredValueHash(Object value) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(value, canonical);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void appendCanonical(Object value, StringBuilder target) {
        if (value == null) {
            target.append("N;");
            return;
        }
        Class<?> type = value.getClass();
        if (value instanceof String text) {
            appendToken(target, "S", text);
        } else if (value instanceof Character character) {
            appendToken(target, "C", character.toString());
        } else if (value instanceof Boolean bool) {
            target.append(bool ? "B1;" : "B0;");
        } else if (value instanceof Double number) {
            appendToken(target, "D", Double.toHexString(number));
        } else if (value instanceof Float number) {
            appendToken(target, "F", Float.toHexString(number));
        } else if (value instanceof Number number) {
            appendToken(target, "I" + type.getName(), number.toString());
        } else if (value instanceof Enum<?> enumeration) {
            appendToken(target, "E" + type.getName(), enumeration.name());
        } else if (value instanceof Map<?, ?> map) {
            ArrayList<CanonicalMapEntry> entries = new ArrayList<>(map.size());
            for (var entry : map.entrySet()) {
                StringBuilder key = new StringBuilder();
                appendCanonical(entry.getKey(), key);
                entries.add(new CanonicalMapEntry(key.toString(), entry.getValue()));
            }
            entries.sort(Comparator.comparing(CanonicalMapEntry::key));
            target.append("M").append(entries.size()).append('{');
            for (CanonicalMapEntry entry : entries) {
                appendToken(target, "K", entry.key());
                appendCanonical(entry.value(), target);
            }
            target.append("};");
        } else if (value instanceof Set<?> set) {
            ArrayList<String> elements = new ArrayList<>(set.size());
            for (Object element : set) {
                StringBuilder encoded = new StringBuilder();
                appendCanonical(element, encoded);
                elements.add(encoded.toString());
            }
            elements.sort(String::compareTo);
            target.append("T").append(elements.size()).append('{');
            elements.forEach(element -> appendToken(target, "V", element));
            target.append("};");
        } else if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> elements = new ArrayList<>();
            iterable.forEach(elements::add);
            target.append("L").append(elements.size()).append('{');
            elements.forEach(element -> appendCanonical(element, target));
            target.append("};");
        } else if (type.isRecord()) {
            target.append("R");
            appendToken(target, "T", type.getName());
            target.append('{');
            for (var component : type.getRecordComponents()) {
                appendToken(target, "P", component.getName());
                try {
                    appendCanonical(component.getAccessor().invoke(value), target);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(
                            "Cannot canonicalize record " + type.getName(), error);
                }
            }
            target.append("};");
        } else {
            throw new IllegalArgumentException(
                    "Unsupported structured diagnostic type " + type.getName());
        }
    }

    private static void appendToken(StringBuilder target, String type, String value) {
        target.append(type).append(':').append(value.length()).append(':').append(value).append(';');
    }

    private record CanonicalMapEntry(String key, Object value) {
    }

    /** Every structured diagnostic component returned by SimulationResult except Random's raw trace. */
    public record StructuredDiagnostics(
            int pushAttempts,
            int pushSuccesses,
            Map<PushFailureReason, Integer> pushFailureCounts,
            TeamSide soulOwner,
            int soulClaimedAtSeconds,
            List<Integer> dragonCaptureTimes,
            List<Integer> dragonSpawnAliveSeconds,
            int generalDragonAttemptCount,
            int generalDragonCaptureCount,
            int postFightDragonCaptureCount,
            List<DragonCaptureRecord> dragonCaptures,
            int pushWindowCount,
            int pushWindowStructureCount,
            int aceWindowNexusEndCount,
            int duplicateEconomyResolutions,
            CombatExecutionStatsSnapshot combat,
            RoamExecutionStatsSnapshot roam,
            ObjectivePriorityExecutionStatsSnapshot objectivePriority,
            LanePhaseExecutionStatsSnapshot lanePhase,
            MidGameMacroExecutionStatsSnapshot midGameMacro,
            ObjectiveDecisionExecutionStatsSnapshot objectiveDecision,
            List<ObjectiveDecisionData> objectiveDecisionHistory,
            StructureActionExecutionStatsSnapshot structure,
            ProgressionExecutionStatsSnapshot progression,
            JungleEconomyExecutionStatsSnapshot jungleEconomy,
            JungleTempoExecutionStatsSnapshot jungleTempo,
            ChampionPowerExecutionStatsSnapshot championPower,
            ChampionMatchupExecutionStatsSnapshot championMatchup,
            CombatOutcomeExecutionStatsSnapshot combatOutcome,
            CompositionRuntimeDiagnostics composition
    ) {
        public StructuredDiagnostics {
            pushFailureCounts = Map.copyOf(pushFailureCounts);
            dragonCaptureTimes = List.copyOf(dragonCaptureTimes);
            dragonSpawnAliveSeconds = List.copyOf(dragonSpawnAliveSeconds);
            dragonCaptures = List.copyOf(dragonCaptures);
            objectiveDecisionHistory = List.copyOf(objectiveDecisionHistory);
            Objects.requireNonNull(combat, "combat");
            Objects.requireNonNull(roam, "roam");
            Objects.requireNonNull(objectivePriority, "objectivePriority");
            Objects.requireNonNull(lanePhase, "lanePhase");
            Objects.requireNonNull(midGameMacro, "midGameMacro");
            Objects.requireNonNull(objectiveDecision, "objectiveDecision");
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(progression, "progression");
            Objects.requireNonNull(jungleEconomy, "jungleEconomy");
            Objects.requireNonNull(jungleTempo, "jungleTempo");
            Objects.requireNonNull(championPower, "championPower");
            Objects.requireNonNull(championMatchup, "championMatchup");
            Objects.requireNonNull(combatOutcome, "combatOutcome");
            Objects.requireNonNull(composition, "composition");
        }
    }
}

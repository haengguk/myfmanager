package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.PlayerActivityType;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Deep-copied, immutable and schema-versioned Match Engine V1 output. */
public record MatchEngineV1Output(
        String schemaVersion,
        String matchIdentity,
        MatchEngineV1Policy.Snapshot productionPolicy,
        String configurationHash,
        MatchResultSummaryV1 resultSummary,
        MatchEngineV1Input.DraftInput finalDraft,
        TimelineV1 timeline,
        SimulationExecutionProvenance executionProvenance,
        String inputHash,
        String inputHashAlgorithm,
        String simulatorTimelineHash,
        String structuredTimelineHash,
        String structuredTimelineHashAlgorithm,
        String outputHash,
        String outputHashAlgorithm,
        String outputHashScope
) {
    public static final String SCHEMA = "MATCH_ENGINE_V1_OUTPUT_V1";
    public static final String OUTPUT_HASH_SCOPE =
            "V1_GAMEPLAY_IDENTITY_EXCLUDES_OUTPUT_HASH_DISPLAY_NAMES_MESSAGES_"
                    + "AND_LEGACY_DISPLAY_SENSITIVE_SIMULATOR_TIMELINE_HASH";

    public MatchEngineV1Output {
        schemaVersion = MatchEngineV1Policy.required(schemaVersion, "schemaVersion");
        if (!SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported Match Engine V1 output schema");
        }
        matchIdentity = MatchEngineV1Policy.required(matchIdentity, "matchIdentity");
        Objects.requireNonNull(productionPolicy, "productionPolicy");
        if (!productionPolicy.equals(MatchEngineV1Policy.authoritative())) {
            throw new IllegalArgumentException("Match Engine V1 output policy mismatch");
        }
        configurationHash = MatchEngineV1Policy.requiredHash(
                configurationHash, "configurationHash");
        Objects.requireNonNull(resultSummary, "resultSummary");
        Objects.requireNonNull(finalDraft, "finalDraft");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(executionProvenance, "executionProvenance");
        inputHash = MatchEngineV1Policy.requiredHash(inputHash, "inputHash");
        inputHashAlgorithm = MatchEngineV1Policy.required(
                inputHashAlgorithm, "inputHashAlgorithm");
        simulatorTimelineHash = MatchEngineV1Policy.requiredHash(
                simulatorTimelineHash, "simulatorTimelineHash");
        structuredTimelineHash = MatchEngineV1Policy.requiredHash(
                structuredTimelineHash, "structuredTimelineHash");
        structuredTimelineHashAlgorithm = MatchEngineV1Policy.required(
                structuredTimelineHashAlgorithm, "structuredTimelineHashAlgorithm");
        outputHash = MatchEngineV1Policy.requiredHash(outputHash, "outputHash");
        outputHashAlgorithm = MatchEngineV1Policy.required(
                outputHashAlgorithm, "outputHashAlgorithm");
        outputHashScope = MatchEngineV1Policy.required(outputHashScope, "outputHashScope");
        if (!configurationHash.equals(productionPolicy.configurationHash())
                || !configurationHash.equals(executionProvenance.configurationHash())
                || !simulatorTimelineHash.equals(executionProvenance.timelineHash())
                || resultSummary.durationSeconds() != timeline.durationSeconds()
                || resultSummary.winner() != timeline.winner()
                || resultSummary.endReason() != timeline.endReason()) {
            throw new IllegalArgumentException("Match Engine V1 output integrity mismatch");
        }
    }

    public boolean hasValidOutputHash(MatchEngineV1Canonicalizer canonicalizer) {
        Objects.requireNonNull(canonicalizer, "canonicalizer");
        if (!inputHashAlgorithm.equals(MatchEngineV1Input.INPUT_HASH_ALGORITHM)
                || !structuredTimelineHashAlgorithm.equals(
                MatchEngineV1Canonicalizer.HASH_ALGORITHM)
                || !outputHashAlgorithm.equals(MatchEngineV1Canonicalizer.HASH_ALGORITHM)
                || !outputHashScope.equals(OUTPUT_HASH_SCOPE)) {
            return false;
        }
        String actualStructuredTimelineHash = canonicalizer.hash(
                structuredTimelineHashMaterial(timeline));
        if (!structuredTimelineHash.equals(actualStructuredTimelineHash)) {
            return false;
        }
        return outputHash.equals(canonicalizer.hash(outputHashMaterial(
                schemaVersion, matchIdentity, productionPolicy, configurationHash,
                inputHash, resultSummary, finalDraft, structuredTimelineHash,
                executionProvenance)));
    }

    static Map<String, Object> outputHashMaterial(
            String schemaVersion,
            String matchIdentity,
            MatchEngineV1Policy.Snapshot productionPolicy,
            String configurationHash,
            String inputHash,
            MatchResultSummaryV1 resultSummary,
            MatchEngineV1Input.DraftInput finalDraft,
            String structuredTimelineHash,
            SimulationExecutionProvenance provenance
    ) {
        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("schemaVersion", schemaVersion);
        identity.put("matchIdentity", matchIdentity);
        identity.put("policyHash", productionPolicy.policyHash());
        identity.put("configurationHash", configurationHash);
        identity.put("inputHash", inputHash);
        identity.put("resultSummary", resultSummary);
        identity.put("finalDraft", finalDraft);
        identity.put("structuredTimelineHash", structuredTimelineHash);
        identity.put("replayProvenanceHash", provenance.replayProvenanceHash());
        identity.put("resourceProvenanceHash",
                provenance.resourceProvenance().resourceProvenanceHash());
        identity.put("randomFingerprint", provenance.randomFingerprint());
        identity.put("diagnosticsExcludedFromGameplayIdentity", true);
        return Collections.unmodifiableMap(identity);
    }

    static Map<String, Object> structuredTimelineHashMaterial(TimelineV1 timeline) {
        Objects.requireNonNull(timeline, "timeline");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", timeline.schemaVersion());
        result.put("durationSeconds", timeline.durationSeconds());
        result.put("winner", timeline.winner() == null ? "NONE" : timeline.winner().name());
        result.put("endReason", timeline.endReason().name());
        result.put("events", timeline.events().stream()
                .map(MatchEngineV1Output::structuredEventHashMaterial).toList());
        result.put("snapshots", timeline.snapshots());
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> structuredEventHashMaterial(EventV1 event) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("timeSeconds", event.timeSeconds());
        result.put("eventType", event.eventType());
        putIfNonNull(result, "actorSide", event.actorSide());
        putIfNonNull(result, "actorPosition", event.actorPosition());
        putIfNonNull(result, "lane", event.lane());
        putIfNonNull(result, "killerPlayerId", event.killerPlayerId());
        putIfNonNull(result, "victimPlayerId", event.victimPlayerId());
        result.put("assistantPlayerIds", event.assistantPlayerIds());
        putIfNonNull(result, "killerChampionId", event.killerChampionId());
        putIfNonNull(result, "victimChampionId", event.victimChampionId());
        result.put("assistantChampionIds", event.assistantChampionIds());
        putIfNonNull(result, "combatSource", event.combatSource());
        putIfNonNull(result, "structureActionSource", event.structureActionSource());
        putIfNonNull(result, "structureKind", event.structureKind());
        putIfNonNull(result, "structureTowerTier", event.structureTowerTier());
        putIfNonNull(result, "structureAttackingSide", event.structureAttackingSide());
        putIfNonNull(result, "structureDefendingSide", event.structureDefendingSide());
        result.put("goldAmount", event.goldAmount());
        result.put("bountyRawBeforePayout", event.bountyRawBeforePayout());
        result.put("structuredData", event.structuredData());
        return Collections.unmodifiableMap(result);
    }

    public record MatchResultSummaryV1(
            String schemaVersion,
            TeamSide winner,
            GameEndReason endReason,
            int durationSeconds,
            List<TeamResultV1> teams,
            List<PlayerResultV1> players,
            String finalDraftHash,
            String finalAssignmentHash,
            String runtimeProfileId,
            String configurationHash,
            String resourceProvenanceHash,
            String replayProvenanceHash
    ) {
        public static final String SCHEMA = "MATCH_RESULT_SUMMARY_V1";

        public MatchResultSummaryV1 {
            schemaVersion = MatchEngineV1Policy.required(schemaVersion, "summarySchemaVersion");
            if (!SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported result summary schema");
            }
            Objects.requireNonNull(endReason, "endReason");
            if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds");
            teams = List.copyOf(teams);
            players = List.copyOf(players);
            if (teams.size() != TeamSide.values().length || players.size() != 10) {
                throw new IllegalArgumentException("Match result summary cardinality mismatch");
            }
            if (endReason == GameEndReason.NEXUS_DESTROYED && winner == null
                    || endReason == GameEndReason.SIMULATION_TIMEOUT && winner != null) {
                throw new IllegalArgumentException("Match result summary winner/end mismatch");
            }
            if (!teams.stream().map(TeamResultV1::teamSide)
                    .collect(java.util.stream.Collectors.toSet())
                    .equals(EnumSet.allOf(TeamSide.class))) {
                throw new IllegalArgumentException("Match result summary team coverage mismatch");
            }
            Set<PlayerId> playerIds = new HashSet<>();
            Set<String> slots = new HashSet<>();
            for (PlayerResultV1 player : players) {
                if (!playerIds.add(player.playerId())
                        || !slots.add(player.teamSide() + ":" + player.position())) {
                    throw new IllegalArgumentException("Match result summary player identity mismatch");
                }
            }
            finalDraftHash = MatchEngineV1Policy.requiredHash(finalDraftHash, "finalDraftHash");
            finalAssignmentHash = MatchEngineV1Policy.requiredHash(
                    finalAssignmentHash, "finalAssignmentHash");
            runtimeProfileId = MatchEngineV1Policy.required(runtimeProfileId, "runtimeProfileId");
            configurationHash = MatchEngineV1Policy.requiredHash(
                    configurationHash, "configurationHash");
            resourceProvenanceHash = MatchEngineV1Policy.requiredHash(
                    resourceProvenanceHash, "resourceProvenanceHash");
            replayProvenanceHash = MatchEngineV1Policy.requiredHash(
                    replayProvenanceHash, "replayProvenanceHash");
        }
    }

    public record TeamResultV1(
            String teamIdentity,
            TeamSide teamSide,
            int kills,
            int totalGold,
            int dragons,
            boolean hasDragonSoul,
            boolean hasBaronBuff,
            boolean hasElderBuff,
            int towersDestroyed,
            int inhibitorsRemaining,
            int nexusTurretsRemaining,
            boolean nexusAlive,
            int alivePlayers
    ) {
        public TeamResultV1 {
            teamIdentity = MatchEngineV1Policy.required(teamIdentity, "teamIdentity");
            Objects.requireNonNull(teamSide, "teamSide");
        }
    }

    public record PlayerResultV1(
            PlayerId playerId,
            TeamSide teamSide,
            Position position,
            ChampionId championId,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            int totalExperience,
            int level
    ) {
        public PlayerResultV1 {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(championId, "championId");
        }
    }

    public record TimelineV1(
            String schemaVersion,
            int durationSeconds,
            TeamSide winner,
            GameEndReason endReason,
            List<EventV1> events,
            List<SnapshotV1> snapshots
    ) {
        public static final String SCHEMA = "MATCH_ENGINE_TIMELINE_V1";

        public TimelineV1 {
            schemaVersion = MatchEngineV1Policy.required(schemaVersion, "timelineSchemaVersion");
            if (!SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported timeline schema");
            }
            if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds");
            Objects.requireNonNull(endReason, "endReason");
            events = List.copyOf(events);
            snapshots = List.copyOf(snapshots);
            if (snapshots.isEmpty()) throw new IllegalArgumentException("snapshots must not be empty");
            if (snapshots.getLast().timeSeconds() != durationSeconds) {
                throw new IllegalArgumentException("Timeline final snapshot duration mismatch");
            }
            if (endReason == GameEndReason.NEXUS_DESTROYED && winner == null
                    || endReason == GameEndReason.SIMULATION_TIMEOUT && winner != null) {
                throw new IllegalArgumentException("Timeline winner/end mismatch");
            }
        }
    }

    public record EventV1(
            int timeSeconds,
            MatchEventType eventType,
            TeamSide actorSide,
            Position actorPosition,
            Lane lane,
            PlayerId actorPlayerId,
            PlayerId killerPlayerId,
            PlayerId victimPlayerId,
            List<PlayerId> assistantPlayerIds,
            ChampionId killerChampionId,
            ChampionId victimChampionId,
            List<ChampionId> assistantChampionIds,
            CombatSource combatSource,
            StructureActionSource structureActionSource,
            StructureKind structureKind,
            TowerTier structureTowerTier,
            TeamSide structureAttackingSide,
            TeamSide structureDefendingSide,
            int goldAmount,
            double bountyRawBeforePayout,
            String actionId,
            String parentActionId,
            String displayMessage,
            Map<String, Object> structuredData
    ) {
        public EventV1 {
            if (timeSeconds < 0) throw new IllegalArgumentException("timeSeconds");
            Objects.requireNonNull(eventType, "eventType");
            assistantPlayerIds = List.copyOf(assistantPlayerIds);
            assistantChampionIds = List.copyOf(assistantChampionIds);
            structuredData = immutableMap(structuredData);
        }
    }

    public record SnapshotV1(
            int timeSeconds,
            TeamStateV1 blueTeam,
            TeamStateV1 redTeam,
            List<PlayerStateV1> players,
            Map<String, Object> structuredState
    ) {
        public SnapshotV1 {
            if (timeSeconds < 0) throw new IllegalArgumentException("timeSeconds");
            Objects.requireNonNull(blueTeam, "blueTeam");
            Objects.requireNonNull(redTeam, "redTeam");
            players = List.copyOf(players);
            if (players.size() != 10) {
                throw new IllegalArgumentException("Snapshot must contain ten player states");
            }
            if (blueTeam.teamSide() != TeamSide.BLUE || redTeam.teamSide() != TeamSide.RED
                    || players.stream().map(value -> value.teamSide() + ":" + value.position())
                    .distinct().count() != 10) {
                throw new IllegalArgumentException("Snapshot structured identity coverage mismatch");
            }
            structuredState = immutableMap(structuredState);
        }
    }

    public record TeamStateV1(
            String teamIdentity,
            TeamSide teamSide,
            int kills,
            int gold,
            int dragons,
            boolean hasDragonSoul,
            boolean hasBaronBuff,
            boolean hasElderBuff,
            int elderBuffRemainingSeconds,
            int towersDestroyed,
            int inhibitorsRemaining,
            int nexusTurretsRemaining,
            boolean nexusAlive,
            int alivePlayers
    ) {
        public TeamStateV1 {
            teamIdentity = MatchEngineV1Policy.required(teamIdentity, "teamIdentity");
            Objects.requireNonNull(teamSide, "teamSide");
        }
    }

    public record PlayerStateV1(
            PlayerId playerId,
            TeamSide teamSide,
            Position position,
            ChampionId championId,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            boolean alive,
            int respawnAtSeconds,
            int respawnRemainingSeconds,
            boolean canFarm,
            int farmResumeAtSeconds,
            int farmReturnSecondsRemaining,
            int shutdownBountyGold,
            double bountyProgress,
            PlayerActivityType activityType,
            Lane activityOriginLane,
            Lane activityTargetLane,
            int activityUntilSeconds,
            int totalExperience,
            int level,
            String itemProgressStage,
            Map<String, Object> structuredProgression
    ) {
        public PlayerStateV1 {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(championId, "championId");
            itemProgressStage = MatchEngineV1Policy.required(
                    itemProgressStage, "itemProgressStage");
            structuredProgression = immutableMap(structuredProgression);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Map.of();
        TreeMap<String, Object> copy = new TreeMap<>();
        value.forEach((key, nested) -> copy.put(
                MatchEngineV1Policy.required(key, "structuredKey"), freeze(nested)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean) return value;
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> copy = new TreeMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), freeze(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> copy = new ArrayList<>();
            iterable.forEach(nested -> copy.add(freeze(nested)));
            return Collections.unmodifiableList(copy);
        }
        return String.valueOf(value);
    }

    private static void putIfNonNull(
            Map<String, Object> target, String key, Object value
    ) {
        if (value != null) target.put(key, value);
    }
}

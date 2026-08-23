package com.lolfm.dto;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftActionType;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.PlayerActivityType;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable HTTP-only contracts for the additive Real Match API V1 boundary. */
public final class RealMatchApiV1Dtos {
    public static final String OPTIONS_SCHEMA = "REAL_MATCH_OPTIONS_V1";
    public static final String REQUEST_SCHEMA = "REAL_MATCH_SIMULATE_REQUEST_V1";
    public static final String RESPONSE_SCHEMA = "REAL_MATCH_RESPONSE_V1";
    public static final String ERROR_SCHEMA = "REAL_MATCH_API_ERROR_V1";
    public static final String DRAFT_SCHEMA = "REAL_MATCH_DRAFT_V1";
    public static final String SEED_ENCODING = "SIGNED_INT64_DECIMAL_STRING";

    private RealMatchApiV1Dtos() {
    }

    public record SimulateRequest(
            String schemaVersion,
            String blueTeamCode,
            String redTeamCode,
            String seed
    ) {
        public SimulateRequest {
            schemaVersion = required(schemaVersion, "schemaVersion");
            blueTeamCode = required(blueTeamCode, "blueTeamCode");
            redTeamCode = required(redTeamCode, "redTeamCode");
            seed = required(seed, "seed");
        }

        public long seedAsLong() {
            return Long.parseLong(seed);
        }
    }

    public record OptionsResponse(
            String schemaVersion,
            String matchEngineContract,
            ProductionPolicy productionPolicy,
            SeedPolicy seedPolicy,
            List<OptionTeam> teams,
            ResourceVersions resourceVersions
    ) {
        public OptionsResponse {
            schemaVersion = required(schemaVersion, "schemaVersion");
            matchEngineContract = required(matchEngineContract, "matchEngineContract");
            Objects.requireNonNull(productionPolicy, "productionPolicy");
            Objects.requireNonNull(seedPolicy, "seedPolicy");
            teams = List.copyOf(teams);
            Objects.requireNonNull(resourceVersions, "resourceVersions");
        }
    }

    public record ProductionPolicy(
            String policyId,
            String policyHash,
            String runtimeProfileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            String matchupMode,
            String compositionMode,
            String jungleClearContribution,
            boolean economyCandidateActivation,
            boolean tempoCandidateActivation,
            boolean diagnosticsExcludedFromGameplayIdentity
    ) {
        public ProductionPolicy {
            policyId = required(policyId, "policyId");
            policyHash = required(policyHash, "policyHash");
            runtimeProfileId = required(runtimeProfileId, "runtimeProfileId");
            configurationHash = required(configurationHash, "configurationHash");
            activeGameplayRulesVersion = required(
                    activeGameplayRulesVersion, "activeGameplayRulesVersion");
            engineImplementationVersion = required(
                    engineImplementationVersion, "engineImplementationVersion");
            matchupMode = required(matchupMode, "matchupMode");
            compositionMode = required(compositionMode, "compositionMode");
            jungleClearContribution = required(
                    jungleClearContribution, "jungleClearContribution");
        }
    }

    public record SeedPolicy(boolean required, String encoding) {
        public SeedPolicy {
            encoding = RealMatchApiV1Dtos.required(encoding, "encoding");
        }
    }

    public record OptionTeam(String teamCode, String displayName, List<OptionPlayer> lineup) {
        public OptionTeam {
            teamCode = required(teamCode, "teamCode");
            displayName = required(displayName, "displayName");
            lineup = List.copyOf(lineup);
        }
    }

    public record OptionPlayer(String playerId, String nickname, Position position) {
        public OptionPlayer {
            playerId = required(playerId, "playerId");
            nickname = required(nickname, "nickname");
            Objects.requireNonNull(position, "position");
        }
    }

    public record ResourceVersions(
            String resourceProvenanceHash,
            Map<String, String> versions
    ) {
        public ResourceVersions {
            resourceProvenanceHash = required(
                    resourceProvenanceHash, "resourceProvenanceHash");
            versions = immutableStringMap(versions);
        }
    }

    public record Response(
            String schemaVersion,
            String matchIdentity,
            String seed,
            List<TeamPresentation> teams,
            Draft draft,
            Result result,
            Timeline timeline,
            Integrity integrity
    ) {
        public Response {
            schemaVersion = required(schemaVersion, "schemaVersion");
            matchIdentity = required(matchIdentity, "matchIdentity");
            seed = required(seed, "seed");
            teams = List.copyOf(teams);
            Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(timeline, "timeline");
            Objects.requireNonNull(integrity, "integrity");
        }
    }

    public record TeamPresentation(
            TeamSide teamSide,
            String teamCode,
            String displayName,
            List<PlayerPresentation> lineup
    ) {
        public TeamPresentation {
            Objects.requireNonNull(teamSide, "teamSide");
            teamCode = required(teamCode, "teamCode");
            displayName = required(displayName, "displayName");
            lineup = List.copyOf(lineup);
        }
    }

    public record PlayerPresentation(
            String playerId,
            String nickname,
            Position position,
            String championId,
            ChampionPresentation champion
    ) {
        public PlayerPresentation {
            playerId = required(playerId, "playerId");
            nickname = required(nickname, "nickname");
            Objects.requireNonNull(position, "position");
            championId = required(championId, "championId");
            Objects.requireNonNull(champion, "champion");
        }
    }

    public record ChampionPresentation(
            String championId,
            String displayNameKo,
            String displayNameEn,
            String portraitUrl
    ) {
        public ChampionPresentation {
            championId = required(championId, "championId");
            displayNameKo = required(displayNameKo, "displayNameKo");
            displayNameEn = required(displayNameEn, "displayNameEn");
            portraitUrl = required(portraitUrl, "portraitUrl");
        }
    }

    public record Draft(
            String schemaVersion,
            int seriesGameNumber,
            String draftRuleSetIdentity,
            String draftRuleSetHash,
            String draftScoringPolicyHash,
            List<String> hardFearlessExclusionsBeforeDraft,
            List<DraftDecision> decisions,
            List<String> blueBans,
            List<String> bluePicks,
            List<String> redBans,
            List<String> redPicks,
            List<FinalAssignment> finalAssignments,
            String finalDraftHash,
            String finalAssignmentHash
    ) {
        public Draft {
            schemaVersion = required(schemaVersion, "schemaVersion");
            draftRuleSetIdentity = required(draftRuleSetIdentity, "draftRuleSetIdentity");
            draftRuleSetHash = required(draftRuleSetHash, "draftRuleSetHash");
            draftScoringPolicyHash = required(
                    draftScoringPolicyHash, "draftScoringPolicyHash");
            hardFearlessExclusionsBeforeDraft = List.copyOf(
                    hardFearlessExclusionsBeforeDraft);
            decisions = List.copyOf(decisions);
            blueBans = List.copyOf(blueBans);
            bluePicks = List.copyOf(bluePicks);
            redBans = List.copyOf(redBans);
            redPicks = List.copyOf(redPicks);
            finalAssignments = List.copyOf(finalAssignments);
            finalDraftHash = required(finalDraftHash, "finalDraftHash");
            finalAssignmentHash = required(finalAssignmentHash, "finalAssignmentHash");
        }
    }

    public record DraftDecision(
            int turn,
            TeamSide teamSide,
            DraftActionType actionType,
            String championId
    ) {
        public DraftDecision {
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(actionType, "actionType");
            championId = required(championId, "championId");
        }
    }

    public record FinalAssignment(
            String playerId,
            TeamSide teamSide,
            Position position,
            String championId
    ) {
        public FinalAssignment {
            playerId = required(playerId, "playerId");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            championId = required(championId, "championId");
        }
    }

    public record Result(
            String schemaVersion,
            TeamSide winner,
            GameEndReason endReason,
            int durationSeconds,
            List<TeamResult> teams,
            List<PlayerResult> players,
            String finalDraftHash,
            String finalAssignmentHash,
            String runtimeProfileId,
            String configurationHash,
            String resourceProvenanceHash,
            String replayProvenanceHash
    ) {
        public Result {
            schemaVersion = required(schemaVersion, "schemaVersion");
            Objects.requireNonNull(endReason, "endReason");
            teams = List.copyOf(teams);
            players = List.copyOf(players);
            finalDraftHash = required(finalDraftHash, "finalDraftHash");
            finalAssignmentHash = required(finalAssignmentHash, "finalAssignmentHash");
            runtimeProfileId = required(runtimeProfileId, "runtimeProfileId");
            configurationHash = required(configurationHash, "configurationHash");
            resourceProvenanceHash = required(
                    resourceProvenanceHash, "resourceProvenanceHash");
            replayProvenanceHash = required(replayProvenanceHash, "replayProvenanceHash");
        }
    }

    public record TeamResult(
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
        public TeamResult {
            teamIdentity = required(teamIdentity, "teamIdentity");
            Objects.requireNonNull(teamSide, "teamSide");
        }
    }

    public record PlayerResult(
            String playerId,
            TeamSide teamSide,
            Position position,
            String championId,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            int totalExperience,
            int level
    ) {
        public PlayerResult {
            playerId = required(playerId, "playerId");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            championId = required(championId, "championId");
        }
    }

    public record Timeline(
            String schemaVersion,
            int durationSeconds,
            TeamSide winner,
            GameEndReason endReason,
            List<Event> events,
            List<Snapshot> snapshots
    ) {
        public Timeline {
            schemaVersion = required(schemaVersion, "schemaVersion");
            Objects.requireNonNull(endReason, "endReason");
            events = List.copyOf(events);
            snapshots = List.copyOf(snapshots);
        }
    }

    public record Event(
            int timeSeconds,
            MatchEventType eventType,
            TeamSide actorSide,
            Position actorPosition,
            Lane lane,
            String killerPlayerId,
            String victimPlayerId,
            List<String> assistantPlayerIds,
            String killerChampionId,
            String victimChampionId,
            List<String> assistantChampionIds,
            CombatSource combatSource,
            StructureActionSource structureActionSource,
            StructureKind structureKind,
            TowerTier structureTowerTier,
            TeamSide structureAttackingSide,
            TeamSide structureDefendingSide,
            int goldAmount,
            double bountyRawBeforePayout,
            String displayMessage,
            Map<String, Object> structuredData
    ) {
        public Event {
            Objects.requireNonNull(eventType, "eventType");
            assistantPlayerIds = List.copyOf(assistantPlayerIds);
            assistantChampionIds = List.copyOf(assistantChampionIds);
            structuredData = immutableObject(structuredData);
        }
    }

    public record Snapshot(
            int timeSeconds,
            TeamState blueTeam,
            TeamState redTeam,
            List<PlayerState> players,
            Map<String, Object> structuredState
    ) {
        public Snapshot {
            Objects.requireNonNull(blueTeam, "blueTeam");
            Objects.requireNonNull(redTeam, "redTeam");
            players = List.copyOf(players);
            structuredState = immutableObject(structuredState);
        }
    }

    public record TeamState(
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
        public TeamState {
            teamIdentity = required(teamIdentity, "teamIdentity");
            Objects.requireNonNull(teamSide, "teamSide");
        }
    }

    public record PlayerState(
            String playerId,
            TeamSide teamSide,
            Position position,
            String championId,
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
        public PlayerState {
            playerId = required(playerId, "playerId");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            championId = required(championId, "championId");
            itemProgressStage = required(itemProgressStage, "itemProgressStage");
            structuredProgression = immutableObject(structuredProgression);
        }
    }

    public record Integrity(
            String matchEngineContract,
            String policyId,
            String policyHash,
            String runtimeProfileId,
            String configurationHash,
            String engineImplementationVersion,
            String activeGameplayRulesVersion,
            String inputHash,
            String inputHashAlgorithm,
            String resourceProvenanceHash,
            String replayProvenanceHash,
            String replayProvenanceHashAlgorithm,
            String simulatorTimelineHash,
            String simulatorTimelineHashAlgorithm,
            String structuredTimelineHash,
            String structuredTimelineHashAlgorithm,
            String outputHash,
            String outputHashAlgorithm,
            String outputHashScope,
            RandomFingerprint randomFingerprint,
            boolean diagnosticsExcludedFromGameplayIdentity
    ) {
        public Integrity {
            matchEngineContract = required(matchEngineContract, "matchEngineContract");
            policyId = required(policyId, "policyId");
            policyHash = required(policyHash, "policyHash");
            runtimeProfileId = required(runtimeProfileId, "runtimeProfileId");
            configurationHash = required(configurationHash, "configurationHash");
            engineImplementationVersion = required(
                    engineImplementationVersion, "engineImplementationVersion");
            activeGameplayRulesVersion = required(
                    activeGameplayRulesVersion, "activeGameplayRulesVersion");
            inputHash = required(inputHash, "inputHash");
            inputHashAlgorithm = required(inputHashAlgorithm, "inputHashAlgorithm");
            resourceProvenanceHash = required(
                    resourceProvenanceHash, "resourceProvenanceHash");
            replayProvenanceHash = required(replayProvenanceHash, "replayProvenanceHash");
            replayProvenanceHashAlgorithm = required(
                    replayProvenanceHashAlgorithm, "replayProvenanceHashAlgorithm");
            simulatorTimelineHash = required(
                    simulatorTimelineHash, "simulatorTimelineHash");
            simulatorTimelineHashAlgorithm = required(
                    simulatorTimelineHashAlgorithm, "simulatorTimelineHashAlgorithm");
            structuredTimelineHash = required(
                    structuredTimelineHash, "structuredTimelineHash");
            structuredTimelineHashAlgorithm = required(
                    structuredTimelineHashAlgorithm, "structuredTimelineHashAlgorithm");
            outputHash = required(outputHash, "outputHash");
            outputHashAlgorithm = required(outputHashAlgorithm, "outputHashAlgorithm");
            outputHashScope = required(outputHashScope, "outputHashScope");
            Objects.requireNonNull(randomFingerprint, "randomFingerprint");
        }
    }

    public record RandomFingerprint(
            String schemaVersion,
            long randomDrawCount,
            String randomTraceHash,
            String randomTraceHashAlgorithm
    ) {
        public RandomFingerprint {
            schemaVersion = required(schemaVersion, "schemaVersion");
            randomTraceHash = required(randomTraceHash, "randomTraceHash");
            randomTraceHashAlgorithm = required(
                    randomTraceHashAlgorithm, "randomTraceHashAlgorithm");
        }
    }

    public record ErrorResponse(String schemaVersion, String code, String field, String message) {
        public ErrorResponse {
            schemaVersion = required(schemaVersion, "schemaVersion");
            code = required(code, "code");
            message = required(message, "message");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source) {
        TreeMap<String, String> copy = new TreeMap<>();
        Objects.requireNonNull(source, "source").forEach((key, value) ->
                copy.put(required(key, "resourceRole"), required(value, "resourceVersion")));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> immutableObject(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        TreeMap<String, Object> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(required(key, "structuredKey"), freeze(value)));
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
}

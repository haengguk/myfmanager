package com.lolfm.dto;

import com.lolfm.application.PlayerDraftSessionStatus;
import com.lolfm.application.SeriesFormat;
import com.lolfm.application.SeriesGameStatus;
import com.lolfm.application.SeriesStatus;
import com.lolfm.simulator.TeamSide;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Distinct additive wire contract for parent-owned BO3/BO5 lifecycle. */
public final class SeriesApiV1Dtos {
    public static final String CREATE_REQUEST_SCHEMA = "SERIES_CREATE_REQUEST_V1";
    public static final String DRAFT_CREATE_REQUEST_SCHEMA =
            "SERIES_DRAFT_SESSION_CREATE_REQUEST_V1";
    public static final String DRAFT_ACTION_REQUEST_SCHEMA =
            "SERIES_DRAFT_ACTION_REQUEST_V1";
    public static final String DRAFT_CANCEL_REQUEST_SCHEMA =
            "SERIES_DRAFT_CANCEL_REQUEST_V1";
    public static final String SIMULATE_REQUEST_SCHEMA = "SERIES_SIMULATE_REQUEST_V1";
    public static final String REPLAY_REQUEST_SCHEMA = "SERIES_GAME_REPLAY_REQUEST_V1";
    public static final String CANCEL_REQUEST_SCHEMA = "SERIES_CANCEL_REQUEST_V1";
    public static final String VIEW_SCHEMA = "SERIES_VIEW_V1";
    public static final String GAME_VIEW_SCHEMA = "SERIES_GAME_VIEW_V1";
    public static final String CHILD_ENVELOPE_SCHEMA = "SERIES_CHILD_DRAFT_SESSION_V1";
    public static final String SIMULATION_RESPONSE_SCHEMA = "SERIES_SIMULATION_RESPONSE_V1";
    public static final String REPLAY_RESPONSE_SCHEMA = "SERIES_GAME_REPLAY_RESPONSE_V1";
    public static final String ERROR_SCHEMA = "SERIES_API_ERROR_V1";

    private SeriesApiV1Dtos() {}

    public record CreateRequest(
            String schemaVersion,
            SeriesFormat format,
            String teamACode,
            String teamBCode,
            String managedTeamCode,
            String game1BlueTeamCode,
            String rootSeed,
            String clientCommandId
    ) {}

    public record DraftCreateRequest(
            String schemaVersion, long expectedRevision, String clientCommandId
    ) {}

    public record DraftActionRequest(
            String schemaVersion,
            long expectedSeriesRevision,
            long expectedDraftRevision,
            String clientCommandId,
            String championId
    ) {}

    public record DraftCancelRequest(
            String schemaVersion, long expectedRevision, String clientCommandId
    ) {}

    public record SimulateRequest(
            String schemaVersion,
            long expectedSeriesRevision,
            long expectedDraftRevision,
            String clientCommandId
    ) {}

    public record ReplayRequest(String schemaVersion, String clientCommandId) {}

    public record CancelRequest(
            String schemaVersion, long expectedRevision, String clientCommandId
    ) {}

    public record SeriesView(
            String schemaVersion,
            String seriesId,
            long revision,
            SeriesStatus status,
            String terminalReason,
            SeriesFormat format,
            int winsRequired,
            List<TeamIdentity> teams,
            String managedTeamCode,
            String opponentTeamCode,
            Map<String, Integer> score,
            int currentGameNumber,
            String rootSeed,
            String seedDerivationAlgorithm,
            String currentGameSeed,
            List<String> excludedChampionIds,
            String seriesHistoryBeforeHash,
            List<SeriesGameView> games,
            ChildDraftEnvelope activeDraftSession,
            ReservationView reservation,
            List<String> allowedCommands,
            String winnerTeamCode,
            Instant createdAt,
            Instant lastActivityAt,
            Instant expiresAt,
            boolean processLocalRestartLoss,
            ProductionIdentity productionIdentity
    ) {
        public SeriesView {
            teams = List.copyOf(teams);
            score = Map.copyOf(score);
            excludedChampionIds = List.copyOf(excludedChampionIds);
            games = List.copyOf(games);
            allowedCommands = List.copyOf(allowedCommands);
        }
    }

    public record TeamIdentity(String teamCode, String displayName) {}

    public record SeriesGameView(
            String schemaVersion,
            String gameId,
            int gameNumber,
            SeriesGameStatus status,
            String reason,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            String matchSeed,
            List<String> historyBeforeChampionIds,
            String historyBeforeHash,
            String childDraftSessionId,
            PlayerDraftSessionStatus childDraftStatus,
            Long childDraftRevision,
            CompactResult result,
            CompactReceipt receipt
    ) {
        public SeriesGameView {
            historyBeforeChampionIds = List.copyOf(historyBeforeChampionIds);
        }
    }

    public record ChildDraftEnvelope(
            String schemaVersion,
            SeriesBinding binding,
            PlayerDraftApiV1Dtos.SessionResponse session
    ) {}

    public record SeriesBinding(
            String seriesId,
            String gameId,
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            String managedTeamCode,
            TeamSide controlledSide,
            String matchSeed,
            List<String> hardFearlessExclusions,
            String historyBeforeHash
    ) {
        public SeriesBinding { hardFearlessExclusions = List.copyOf(hardFearlessExclusions); }
    }

    public record ReservationView(
            String commandId, Instant createdAt, Instant leaseExpiresAt
    ) {}

    public record CompactResult(
            String winnerTeamCode,
            TeamSide winnerSide,
            String endReason,
            int durationSeconds,
            Map<String, Integer> teamKills,
            Map<String, Integer> teamGold
    ) {
        public CompactResult {
            teamKills = Map.copyOf(teamKills);
            teamGold = Map.copyOf(teamGold);
        }
    }

    public record CompactReceipt(
            String schemaVersion,
            String inputHash,
            String replayProvenanceHash,
            String resourceProvenanceHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String controlEvidenceHash,
            String simulatorTimelineHash,
            String structuredTimelineHash,
            String outputHash,
            long randomDrawCount,
            String randomTraceHash
    ) {}

    public record ProductionIdentity(
            String policyId,
            String policyHash,
            String runtimeProfileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            String draftMetaVersion,
            String requiredLegalRoleKeyHash,
            String actualLegalRoleKeyHash
    ) {}

    public record SimulationResponse(
            String schemaVersion,
            boolean replayedCommand,
            SeriesView series,
            SeriesGameView game,
            PlayerDraftApiV1Dtos.MatchPayload match
    ) {}

    public record ReplayResponse(
            String schemaVersion,
            SeriesView series,
            SeriesGameView game,
            PlayerDraftApiV1Dtos.MatchPayload match
    ) {}

    public record ErrorResponse(
            String schemaVersion,
            String code,
            String field,
            String message,
            boolean retryable,
            Long currentRevision,
            SeriesStatus currentStatus
    ) {}
}

package com.lolfm.dto;

import com.lolfm.application.PlayerDraftSessionStatus;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftActionType;
import com.lolfm.draft.DraftDecisionAuthority;
import com.lolfm.draft.PlayerDraftUnavailableReason;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP-only contracts for the additive player-controlled Draft API. */
public final class PlayerDraftApiV1Dtos {
    public static final String START_REQUEST_SCHEMA = "PLAYER_DRAFT_START_REQUEST_V1";
    public static final String ACTION_REQUEST_SCHEMA = "PLAYER_DRAFT_ACTION_REQUEST_V1";
    public static final String SIMULATE_REQUEST_SCHEMA = "PLAYER_DRAFT_SIMULATE_REQUEST_V1";
    public static final String SESSION_SCHEMA = "PLAYER_DRAFT_SESSION_V1";
    public static final String SIMULATION_RESPONSE_SCHEMA =
            "PLAYER_DRAFT_MATCH_RESPONSE_V1";
    public static final String MATCH_PAYLOAD_SCHEMA =
            "PLAYER_DRAFT_MATCH_PAYLOAD_V1";
    public static final String ERROR_SCHEMA = "PLAYER_DRAFT_API_ERROR_V1";

    private PlayerDraftApiV1Dtos() {
    }

    public record StartRequest(
            String schemaVersion,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            String seed
    ) {
        public long seedAsLong() {
            return Long.parseLong(seed);
        }
    }

    public record ActionRequest(
            String schemaVersion,
            long expectedRevision,
            String clientActionId,
            String championId
    ) {
    }

    public record SimulateRequest(String schemaVersion) {
    }

    public record SessionResponse(
            String schemaVersion,
            String sessionId,
            long revision,
            PlayerDraftSessionStatus status,
            List<TeamIdentity> teams,
            TeamSide controlledSide,
            String seed,
            int seriesGameNumber,
            RuleIdentity draftRules,
            PolicyIdentity draftScoringPolicy,
            PolicyIdentity autoDraftSelectionPolicy,
            PolicyIdentity playerControlPolicy,
            CurrentTurn currentTurn,
            DraftState state,
            List<TurnEvidence> decisions,
            List<ChampionOption> selectableChampions,
            List<UnavailableChampion> unavailableChampions,
            List<Recommendation> advisoryRecommendations,
            String selectableSetIdentity,
            String stateHash,
            CompletedDraft completedDraft
    ) {
        public SessionResponse {
            teams = List.copyOf(teams);
            decisions = List.copyOf(decisions);
            selectableChampions = List.copyOf(selectableChampions);
            unavailableChampions = List.copyOf(unavailableChampions);
            advisoryRecommendations = List.copyOf(advisoryRecommendations);
        }
    }

    public record TeamIdentity(TeamSide teamSide, String teamCode, String displayName,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            List<RealMatchApiV1Dtos.OptionPlayer> lineup) {
        public TeamIdentity(TeamSide teamSide, String teamCode, String displayName) {
            this(teamSide, teamCode, displayName, null);
        }
        public TeamIdentity { if (lineup != null) lineup = List.copyOf(lineup); }
    }

    public record RuleIdentity(String identity, String hash) {
    }

    public record PolicyIdentity(String policyId, String policyHash) {
    }

    public record CurrentTurn(int turn, TeamSide teamSide, DraftActionType actionType) {
    }

    public record DraftState(
            List<String> blueBans,
            List<String> redBans,
            List<String> bluePicks,
            List<String> redPicks,
            List<String> hardFearlessExclusions
    ) {
        public DraftState {
            blueBans = List.copyOf(blueBans);
            redBans = List.copyOf(redBans);
            bluePicks = List.copyOf(bluePicks);
            redPicks = List.copyOf(redPicks);
            hardFearlessExclusions = List.copyOf(hardFearlessExclusions);
        }
    }

    public record TurnEvidence(
            int turn,
            TeamSide teamSide,
            DraftActionType actionType,
            String championId,
            DraftDecisionAuthority authority,
            String stateBeforeHash,
            String stateAfterHash,
            RealMatchApiV1Dtos.DraftSelectionTrace autoSelectionTrace,
            ManualSelectionEvidence playerSelectionEvidence
    ) {
    }

    public record ManualSelectionEvidence(
            TeamSide controlledSide,
            int turn,
            DraftActionType actionType,
            String championId,
            String stateBeforeHash,
            String selectableSetIdentity,
            String legalityResult,
            String clientActionId
    ) {
    }

    public record ChampionOption(
            RealMatchApiV1Dtos.ChampionPresentation champion,
            List<Position> feasibleRoles
    ) {
        public ChampionOption {
            feasibleRoles = List.copyOf(feasibleRoles);
        }
    }

    public record UnavailableChampion(
            RealMatchApiV1Dtos.ChampionPresentation champion,
            PlayerDraftUnavailableReason reason
    ) {
    }

    public record Recommendation(
            RealMatchApiV1Dtos.ChampionPresentation champion,
            int advisoryRank,
            double immediateScore,
            double continuationScore,
            double finalSearchScore,
            boolean advisoryOnly
    ) {
    }

    public record CompletedDraft(
            String draftIdentity,
            String controlEvidenceSchema,
            String controlEvidenceHash,
            String controlEvidenceHashAlgorithm,
            List<RealMatchApiV1Dtos.FinalAssignment> finalAssignments
    ) {
        public CompletedDraft {
            finalAssignments = List.copyOf(finalAssignments);
        }
    }

    public record SimulationResponse(
            String schemaVersion,
            SessionResponse session,
            MatchPayload match
    ) {
    }

    public record MatchPayload(
            String schemaVersion,
            String matchIdentity,
            String seed,
            RealMatchApiV1Dtos.ProductionPolicy productionPolicy,
            List<RealMatchApiV1Dtos.TeamPresentation> teams,
            MatchDraftBinding draft,
            RealMatchApiV1Dtos.Result result,
            RealMatchApiV1Dtos.Timeline timeline,
            MatchIntegrity integrity
    ) {
        public MatchPayload {
            teams = List.copyOf(teams);
        }
    }

    public record MatchDraftBinding(
            String draftIdentity,
            String finalDraftHash,
            String finalAssignmentHash,
            PolicyIdentity autoDraftSelectionPolicy,
            PolicyIdentity playerControlPolicy,
            String autoSelectionTraceHash,
            String controlEvidenceHash,
            List<TurnEvidence> decisions
    ) {
        public MatchDraftBinding {
            decisions = List.copyOf(decisions);
        }
    }

    public record MatchIntegrity(
            String runtimeProfileId,
            String configurationHash,
            String engineImplementationVersion,
            String activeGameplayRulesVersion,
            String controlPolicyId,
            String controlPolicyHash,
            String controlEvidenceHash,
            String inputHash,
            String replayProvenanceHash,
            String resourceProvenanceHash,
            String simulatorTimelineHash,
            String structuredTimelineHash,
            String outputHash,
            RealMatchApiV1Dtos.RandomFingerprint randomFingerprint,
            boolean diagnosticsExcludedFromGameplayIdentity
    ) {
    }

    public record ErrorResponse(
            String schemaVersion, String code, String field, String message
    ) {
    }
}

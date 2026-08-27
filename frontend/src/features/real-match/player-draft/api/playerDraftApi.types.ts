import type {
  RealMatchDraftSelectionTraceDto, RealMatchFinalAssignmentDto, RealMatchProductionPolicyDto,
  RealMatchResultDto, RealMatchTeamPresentationDto, RealMatchTimelineDto,
} from '../../api/realMatchApi.types';
import type { DraftActionType, Position, TeamSide } from '../../realMatch.contract';

export type PlayerDraftSessionStatus = 'ACTIVE' | 'COMPLETED' | 'SIMULATED' | 'CANCELLED' | 'EXPIRED';
export type PlayerDraftDecisionAuthority = 'AI' | 'PLAYER';
export type PlayerDraftUnavailableReason =
  | 'HARD_FEARLESS_EXCLUDED'
  | 'ALREADY_BANNED'
  | 'ALREADY_PICKED'
  | 'PARTIAL_ROLE_ASSIGNMENT_INFEASIBLE'
  | 'FUTURE_ROLE_COMPLETION_INFEASIBLE'
  | 'BAN_WOULD_BREAK_FUTURE_COMPLETION';

export interface PlayerDraftStartRequestDto {
  schemaVersion: 'PLAYER_DRAFT_START_REQUEST_V1';
  blueTeamCode: string;
  redTeamCode: string;
  controlledSide: TeamSide;
  seed: string;
}

export interface PlayerDraftActionRequestDto {
  schemaVersion: 'PLAYER_DRAFT_ACTION_REQUEST_V1';
  expectedRevision: number;
  clientActionId: string;
  championId: string;
}

export interface PlayerDraftSimulateRequestDto { schemaVersion: 'PLAYER_DRAFT_SIMULATE_REQUEST_V1'; }
export interface PlayerDraftTeamIdentityDto { teamSide: TeamSide; teamCode: string; displayName: string; }
export interface PlayerDraftRuleIdentityDto { identity: string; hash: string; }
export interface PlayerDraftPolicyIdentityDto { policyId: string; policyHash: string; }
export interface PlayerDraftCurrentTurnDto { turn: number; teamSide: TeamSide; actionType: DraftActionType; }
export interface PlayerDraftStateDto {
  blueBans: readonly string[]; redBans: readonly string[];
  bluePicks: readonly string[]; redPicks: readonly string[];
  hardFearlessExclusions: readonly string[];
}

export interface PlayerDraftManualSelectionEvidenceDto {
  controlledSide: TeamSide; turn: number; actionType: DraftActionType; championId: string;
  stateBeforeHash: string; selectableSetIdentity: string; legalityResult: string; clientActionId: string;
}

export interface PlayerDraftTurnEvidenceDto {
  turn: number; teamSide: TeamSide; actionType: DraftActionType; championId: string;
  authority: PlayerDraftDecisionAuthority; stateBeforeHash: string; stateAfterHash: string;
  autoSelectionTrace: RealMatchDraftSelectionTraceDto | null;
  playerSelectionEvidence: PlayerDraftManualSelectionEvidenceDto | null;
}

export interface PlayerDraftChampionPresentationDto {
  championId: string; displayNameKo: string; displayNameEn: string; portraitUrl: string;
}
export interface PlayerDraftChampionOptionDto { champion: PlayerDraftChampionPresentationDto; feasibleRoles: readonly Position[]; }
export interface PlayerDraftUnavailableChampionDto { champion: PlayerDraftChampionPresentationDto; reason: PlayerDraftUnavailableReason; }
export interface PlayerDraftRecommendationDto {
  champion: PlayerDraftChampionPresentationDto; advisoryRank: number; immediateScore: number;
  continuationScore: number; finalSearchScore: number; advisoryOnly: true;
}

export interface PlayerDraftCompletedDraftDto {
  draftIdentity: string; controlEvidenceSchema: string; controlEvidenceHash: string;
  controlEvidenceHashAlgorithm: string; finalAssignments: readonly RealMatchFinalAssignmentDto[];
}

export interface PlayerDraftSessionResponseDto {
  schemaVersion: 'PLAYER_DRAFT_SESSION_V1'; sessionId: string; revision: number;
  status: PlayerDraftSessionStatus; teams: readonly PlayerDraftTeamIdentityDto[];
  controlledSide: TeamSide; seed: string; seriesGameNumber: 1;
  draftRules: PlayerDraftRuleIdentityDto; draftScoringPolicy: PlayerDraftPolicyIdentityDto;
  autoDraftSelectionPolicy: PlayerDraftPolicyIdentityDto; playerControlPolicy: PlayerDraftPolicyIdentityDto;
  currentTurn: PlayerDraftCurrentTurnDto | null; state: PlayerDraftStateDto;
  decisions: readonly PlayerDraftTurnEvidenceDto[];
  selectableChampions: readonly PlayerDraftChampionOptionDto[];
  unavailableChampions: readonly PlayerDraftUnavailableChampionDto[];
  advisoryRecommendations: readonly PlayerDraftRecommendationDto[];
  selectableSetIdentity: string | null; stateHash: string;
  completedDraft: PlayerDraftCompletedDraftDto | null;
}

export interface PlayerDraftMatchDraftBindingDto {
  draftIdentity: string; finalDraftHash: string; finalAssignmentHash: string;
  autoDraftSelectionPolicy: PlayerDraftPolicyIdentityDto;
  playerControlPolicy: PlayerDraftPolicyIdentityDto;
  autoSelectionTraceHash: string; controlEvidenceHash: string;
  decisions: readonly PlayerDraftTurnEvidenceDto[];
}

export interface PlayerDraftMatchIntegrityDto {
  runtimeProfileId: string; configurationHash: string; engineImplementationVersion: string;
  activeGameplayRulesVersion: string; controlPolicyId: string; controlPolicyHash: string;
  controlEvidenceHash: string; inputHash: string; replayProvenanceHash: string;
  resourceProvenanceHash: string; simulatorTimelineHash: string; structuredTimelineHash: string;
  outputHash: string; randomFingerprint: {
    schemaVersion: string; randomDrawCount: number; randomTraceHash: string; randomTraceHashAlgorithm: string;
  }; diagnosticsExcludedFromGameplayIdentity: boolean;
}

export interface PlayerDraftMatchPayloadDto {
  schemaVersion: 'PLAYER_DRAFT_MATCH_PAYLOAD_V1'; matchIdentity: string; seed: string;
  productionPolicy: RealMatchProductionPolicyDto; teams: readonly RealMatchTeamPresentationDto[];
  draft: PlayerDraftMatchDraftBindingDto; result: RealMatchResultDto; timeline: RealMatchTimelineDto;
  integrity: PlayerDraftMatchIntegrityDto;
}

export interface PlayerDraftSimulationResponseDto {
  schemaVersion: 'PLAYER_DRAFT_MATCH_RESPONSE_V1';
  session: PlayerDraftSessionResponseDto;
  match: PlayerDraftMatchPayloadDto;
}

export interface PlayerDraftApiErrorDto {
  schemaVersion: 'PLAYER_DRAFT_API_ERROR_V1'; code: string; field: string | null; message: string;
}

export interface PlayerDraftSessionExpectation {
  sessionId?: string; blueTeamCode: string; redTeamCode: string; controlledSide: TeamSide; seed: string;
}

import type { PlayerDraftMatchPayloadDto, PlayerDraftSessionResponseDto } from '../../player-draft/api/playerDraftApi.types';
import type { TeamSide } from '../../realMatch.contract';

export type SeriesFormat = 'BO1' | 'BO3' | 'BO5';
export type SeriesStatus = 'ACTIVE' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED' | 'EXPIRED';
export type SeriesGameStatus =
  | 'DRAFT_PENDING'
  | 'DRAFT_ACTIVE'
  | 'DRAFT_COMPLETED'
  | 'SIMULATION_IN_PROGRESS'
  | 'SIMULATION_FAILED_RETRYABLE'
  | 'BLOCKED'
  | 'COMMITTED'
  | 'DRAFT_CANCELLED'
  | 'DRAFT_EXPIRED';
export type SeriesAllowedCommand =
  | 'GET'
  | 'CREATE_DRAFT_SESSION'
  | 'SUBMIT_DRAFT_ACTION'
  | 'CANCEL_DRAFT_SESSION'
  | 'SIMULATE'
  | 'CANCEL_SERIES';

export interface SeriesCreateRequestDto {
  schemaVersion: 'SERIES_CREATE_REQUEST_V1';
  format: SeriesFormat;
  teamACode: string;
  teamBCode: string;
  managedTeamCode: string;
  game1BlueTeamCode: string;
  rootSeed: string;
  clientCommandId: string;
}

export interface SeriesDraftCreateRequestDto {
  schemaVersion: 'SERIES_DRAFT_SESSION_CREATE_REQUEST_V1';
  expectedRevision: number;
  clientCommandId: string;
}

export interface SeriesDraftActionRequestDto {
  schemaVersion: 'SERIES_DRAFT_ACTION_REQUEST_V1';
  expectedSeriesRevision: number;
  expectedDraftRevision: number;
  clientCommandId: string;
  championId: string;
}

export interface SeriesDraftCancelRequestDto {
  schemaVersion: 'SERIES_DRAFT_CANCEL_REQUEST_V1';
  expectedRevision: number;
  clientCommandId: string;
}

export interface SeriesSimulateRequestDto {
  schemaVersion: 'SERIES_SIMULATE_REQUEST_V1';
  expectedSeriesRevision: number;
  expectedDraftRevision: number;
  clientCommandId: string;
}

export interface SeriesReplayRequestDto {
  schemaVersion: 'SERIES_GAME_REPLAY_REQUEST_V1';
  clientCommandId: string;
}

export interface SeriesCancelRequestDto {
  schemaVersion: 'SERIES_CANCEL_REQUEST_V1';
  expectedRevision: number;
  clientCommandId: string;
}

export interface SeriesTeamIdentityDto { teamCode: string; displayName: string; }

export interface SeriesBindingDto {
  seriesId: string;
  gameId: string;
  gameNumber: number;
  blueTeamCode: string;
  redTeamCode: string;
  managedTeamCode: string;
  controlledSide: TeamSide;
  matchSeed: string;
  hardFearlessExclusions: readonly string[];
  historyBeforeHash: string;
}

export interface SeriesChildDraftEnvelopeDto {
  schemaVersion: 'SERIES_CHILD_DRAFT_SESSION_V1';
  binding: SeriesBindingDto;
  session: PlayerDraftSessionResponseDto;
}

export interface SeriesReservationDto {
  commandId: string;
  createdAt: string;
  leaseExpiresAt: string;
}

export interface SeriesCompactResultDto {
  winnerTeamCode: string | null;
  winnerSide: TeamSide | null;
  endReason: string;
  durationSeconds: number;
  teamKills: Readonly<Record<string, number>>;
  teamGold: Readonly<Record<string, number>>;
}

export interface SeriesCompactReceiptDto {
  schemaVersion: string;
  inputHash: string;
  replayProvenanceHash: string;
  resourceProvenanceHash: string;
  finalDraftHash: string;
  finalAssignmentHash: string;
  controlEvidenceHash: string;
  simulatorTimelineHash: string;
  structuredTimelineHash: string;
  outputHash: string;
  randomDrawCount: number;
  randomTraceHash: string;
}

export interface SeriesGameViewDto {
  schemaVersion: 'SERIES_GAME_VIEW_V1';
  gameId: string;
  gameNumber: number;
  status: SeriesGameStatus;
  reason: string | null;
  blueTeamCode: string;
  redTeamCode: string;
  controlledSide: TeamSide;
  matchSeed: string;
  historyBeforeChampionIds: readonly string[];
  historyBeforeHash: string;
  childDraftSessionId: string | null;
  childDraftStatus: PlayerDraftSessionResponseDto['status'] | null;
  childDraftRevision: number | null;
  result: SeriesCompactResultDto | null;
  receipt: SeriesCompactReceiptDto | null;
}

export interface SeriesProductionIdentityDto {
  policyId: string;
  policyHash: string;
  runtimeProfileId: string;
  configurationHash: string;
  activeGameplayRulesVersion: string;
  engineImplementationVersion: string;
  draftMetaVersion: string;
  requiredLegalRoleKeyHash: string;
  actualLegalRoleKeyHash: string;
}

export interface SeriesViewDto {
  competitionContext?: { sideSelectionPolicy: string | null; inheritedChampionIds: readonly string[]; firstPickTeamCode: string; firstSideChoiceTeamCode: string; firstSideChoice: TeamSide; previousLoserOwnsNextSelection: boolean };
  schemaVersion: 'SERIES_VIEW_V1';
  seriesId: string;
  revision: number;
  status: SeriesStatus;
  terminalReason: string | null;
  format: SeriesFormat;
  winsRequired: number;
  teams: readonly SeriesTeamIdentityDto[];
  managedTeamCode: string;
  opponentTeamCode: string;
  score: Readonly<Record<string, number>>;
  currentGameNumber: number;
  rootSeed: string;
  seedDerivationAlgorithm: string;
  currentGameSeed: string;
  excludedChampionIds: readonly string[];
  seriesHistoryBeforeHash: string;
  games: readonly SeriesGameViewDto[];
  activeDraftSession: SeriesChildDraftEnvelopeDto | null;
  reservation: SeriesReservationDto | null;
  allowedCommands: readonly SeriesAllowedCommand[];
  winnerTeamCode: string | null;
  createdAt: string;
  lastActivityAt: string;
  expiresAt: string;
  processLocalRestartLoss: boolean;
  productionIdentity: SeriesProductionIdentityDto;
}

export interface SeriesDraftResponseDto {
  series: SeriesViewDto;
  draftSession: SeriesChildDraftEnvelopeDto;
  replayed: boolean;
}

export interface SeriesSimulationEnvelopeDto {
  schemaVersion: 'SERIES_SIMULATION_RESPONSE_V1';
  replayedCommand: boolean;
  series: SeriesViewDto;
  game: SeriesGameViewDto;
  match: unknown | null;
}

export interface SeriesReplayEnvelopeDto {
  schemaVersion: 'SERIES_GAME_REPLAY_RESPONSE_V1';
  series: SeriesViewDto;
  game: SeriesGameViewDto;
  match: unknown;
}

export interface SeriesSimulationResponseDto extends Omit<SeriesSimulationEnvelopeDto, 'match'> {
  match: PlayerDraftMatchPayloadDto | null;
}

export interface SeriesReplayResponseDto extends Omit<SeriesReplayEnvelopeDto, 'match'> {
  match: PlayerDraftMatchPayloadDto;
}

export interface SeriesApiErrorDto {
  schemaVersion: 'SERIES_API_ERROR_V1';
  code: string;
  field: string | null;
  message: string;
  retryable: boolean;
  currentRevision: number | null;
  currentStatus: SeriesStatus | null;
}

export interface SeriesRequestPerformance {
  payloadBytes: number;
  requestAndDownloadMs: number;
  jsonParseMs: number;
  runtimeValidationMs: number;
  requestStartedAt: number;
}

export interface SeriesSimulationResult {
  response: SeriesSimulationResponseDto;
  status: 200 | 202;
  draftSession: SeriesChildDraftEnvelopeDto | null;
  performance: SeriesRequestPerformance;
}

export interface SeriesReplayResult {
  response: SeriesReplayResponseDto;
  draftSession: SeriesChildDraftEnvelopeDto;
  performance: SeriesRequestPerformance;
}

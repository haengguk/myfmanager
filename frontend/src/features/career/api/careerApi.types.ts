export const CAREER_SCHEMAS = {
  createRequest: 'CAREER_CREATE_REQUEST_V1',
  createResponse: 'CAREER_CREATE_RESPONSE_V1',
  list: 'CAREER_LIST_V1',
  view: 'CAREER_VIEW_V1',
  error: 'CAREER_API_ERROR_V1',
} as const;

export type CareerResumeKind = 'LEAGUE_DASHBOARD' | 'PLAYER_SERIES' | 'SEASON_COMPLETE' | 'ATTENTION_REQUIRED';
export type CareerSeasonStatus = 'DRAFT' | 'FROZEN' | 'READY' | 'RUNNING' | 'PAUSED' | 'WAITING_FOR_PLAYER' | 'COMPLETED' | 'BLOCKED' | 'CANCELLED';
export type CareerAllowedCommand =
  | 'VIEW_STANDINGS'
  | 'VIEW_FIXTURE'
  | 'RUN_CURRENT_ROUND_AUTO_FIXTURES'
  | 'PAUSE_SEASON'
  | 'RESUME_SEASON'
  | 'CANCEL_SEASON'
  | 'START_PLAYER_SERIES'
  | 'RESUME_PLAYER_SERIES'
  | 'RECONCILE_PLAYER_SERIES_COMPLETION';

export interface CareerCreateRequestDto {
  schemaVersion: typeof CAREER_SCHEMAS.createRequest;
  saveName: string;
  managerName: string;
  managedTeamCode: string;
  clientCommandId: string;
}

export interface CareerResumeDto {
  kind: CareerResumeKind;
  leagueId: string;
  seasonId: string;
  fixtureId: string | null;
  seriesId: string | null;
  seasonLifecycleStatus: CareerSeasonStatus;
  currentRound: number;
  lifecycleRevision: number;
  standingsRevision: number;
  allowedCommands: readonly CareerAllowedCommand[];
}

export interface CareerSummaryDto {
  careerId: string;
  saveName: string;
  managerName: string;
  managedTeamCode: string;
  currentDate: string;
  leagueId: string;
  seasonId: string;
  lifecycleStatus: 'ACTIVE';
  resumeKind: CareerResumeKind;
  updatedAt: string;
}

export interface CareerViewDto {
  schemaVersion: typeof CAREER_SCHEMAS.view;
  careerId: string;
  saveName: string;
  managerName: string;
  managedTeamCode: string;
  startDate: string;
  currentDate: string;
  lifecycleStatus: 'ACTIVE';
  revision: number;
  leagueId: string;
  seasonId: string;
  rootSeedAlgorithmId: string;
  rootSeed: string;
  leagueFrozenSnapshotIdentity: string;
  leagueProductDecisionIdentity: string;
  referenceCatalogVersion: string;
  referenceCatalogHash: string;
  bindingSchemaVersion: string;
  bindingHash: string;
  resume: CareerResumeDto;
  createdAt: string;
  updatedAt: string;
}

export interface CareerCreateResponseDto {
  schemaVersion: typeof CAREER_SCHEMAS.createResponse;
  replayed: boolean;
  career: CareerViewDto;
}

export interface CareerListResponseDto {
  schemaVersion: typeof CAREER_SCHEMAS.list;
  careers: readonly CareerSummaryDto[];
  currentCount: number;
  maximumCount: 100;
  remainingCount: number;
}

export interface CareerErrorDto {
  schemaVersion: typeof CAREER_SCHEMAS.error;
  code: string;
  field: string | null;
  message: string;
}

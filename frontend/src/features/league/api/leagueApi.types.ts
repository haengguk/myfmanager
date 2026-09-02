export type LeagueSeasonMode = 'HYBRID_MANAGER' | 'SPECTATOR_FULL_AUTO';
export type LeagueSeasonStatus = 'DRAFT' | 'FROZEN' | 'READY' | 'RUNNING' | 'PAUSED' | 'WAITING_FOR_PLAYER' | 'COMPLETED' | 'BLOCKED' | 'CANCELLED';
export type LeagueFixtureStatus = 'SCHEDULED' | 'QUEUED' | 'LEASED' | 'RUNNING' | 'AWAITING_PLAYER' | 'PLAYER_SERIES_RESERVED' | 'PLAYER_SERIES_ACTIVE' | 'COMPLETION_PENDING_VERIFICATION' | 'RETRY_PENDING' | 'PLAYER_SERIES_RESTART_REQUIRED' | 'COMPLETED' | 'BLOCKED' | 'CANCELLED';
export type LeagueJobStatus = 'QUEUED' | 'LEASED' | 'RUNNING' | 'RETRY_PENDING' | 'COMPLETION_PENDING_VERIFICATION' | 'COMPLETED' | 'BLOCKED' | 'CANCELLED';
export type LeagueExecutionMode = 'FULL_AUTO' | 'PLAYER_CONTROLLED';
export type LeagueSeasonCommand = 'VIEW_STANDINGS' | 'VIEW_FIXTURE' | 'RUN_CURRENT_ROUND_AUTO_FIXTURES' | 'PAUSE_SEASON' | 'RESUME_SEASON' | 'CANCEL_SEASON' | LeaguePlayerCommand;
export type LeaguePlayerCommand = 'START_PLAYER_SERIES' | 'RESUME_PLAYER_SERIES' | 'RECONCILE_PLAYER_SERIES_COMPLETION';
export type LeagueFixtureCommand = 'VIEW_FIXTURE' | LeaguePlayerCommand;

export interface LeagueFixtureCountersDto { total: number; completed: number; inProgress: number; waiting: number; blocked: number; cancelled: number }
export interface LeagueStandingRowDto { position: number; teamCode: string; seriesWins: number; seriesLosses: number; gameWins: number; gameLosses: number; gameDifferential: number; deterministicDrawHash: string | null }
export interface LeagueFixtureViewDto {
  fixtureId: string; roundNumber: number; lifecycleStatus: LeagueFixtureStatus; revision: number;
  executionMode: LeagueExecutionMode; firstTeamCode: string; secondTeamCode: string;
  game1BlueTeamCode: string; game1RedTeamCode: string; seriesFormat: 'BO3'; fixtureRootSeed: string;
  boundSeriesId: string; bindingHash: string | null; playerSeriesStatus: string | null;
  completionStatus: 'NOT_CREATED' | 'PENDING_RECONCILIATION' | 'APPLIED'; jobId: string | null;
  jobStatus: LeagueJobStatus | null; allowedCommands: readonly LeagueFixtureCommand[];
}
export interface LeagueSeasonViewDto {
  leagueId: string; seasonId: string; lifecycleStatus: LeagueSeasonStatus; lifecycleRevision: number;
  standingsRevision: number; seasonMode: LeagueSeasonMode; managedTeamCode: string | null;
  seasonRootSeed: string; scheduleIdentity: string; frozenSnapshotIdentity: string;
  productDecisionHash: string; productionRuntimeIdentity: string; currentRound: number;
  fixtureCounters: LeagueFixtureCountersDto; standings: readonly LeagueStandingRowDto[];
  playableManagedFixture: LeagueFixtureViewDto | null; allowedCommands: readonly LeagueSeasonCommand[];
  updatedAt: string;
}
export interface LeagueSeasonResponseDto { schemaVersion: 'AI_LEAGUE_SEASON_VIEW_V1'; replayed: boolean; season: LeagueSeasonViewDto }
export interface LeagueFixtureListDto { schemaVersion: 'AI_LEAGUE_FIXTURE_LIST_V1'; leagueId: string; seasonId: string; lifecycleRevision: number; standingsRevision: number; fixtures: readonly LeagueFixtureViewDto[] }
export interface LeagueFixtureResponseDto { schemaVersion: 'AI_LEAGUE_FIXTURE_VIEW_V1'; fixture: LeagueFixtureViewDto }
export interface LeagueStandingsDto { schemaVersion: 'AI_LEAGUE_STANDINGS_VIEW_V1'; leagueId: string; seasonId: string; standingsRevision: number; standingsPolicyId: string; rows: readonly LeagueStandingRowDto[] }
export interface LeagueJobViewDto { jobId: string; fixtureId: string; lifecycleStatus: LeagueJobStatus; revision: number; attemptNumber: number; failureClass: 'TRANSIENT' | 'DETERMINISTIC' | null; failureCode: string | null; retryable: boolean; updatedAt: string }
export interface LeagueJobResponseDto { schemaVersion: 'AI_LEAGUE_JOB_VIEW_V1'; job: LeagueJobViewDto }
export interface LeagueRunResponseDto { schemaVersion: 'AI_LEAGUE_RUN_RESPONSE_V1'; replayed: boolean; queued: number; existing: number; playerFixturesExcluded: number; season: LeagueSeasonViewDto; jobs: readonly LeagueJobViewDto[] }
export interface LeaguePlayerSeriesViewDto { leagueId: string; seasonId: string; fixtureId: string; bindingHash: string; bindingRevision: number; lifecycleStatus: string; boundSeriesId: string; completionReceiptHash: string | null; allowedCommands: readonly LeaguePlayerCommand[] }
export interface LeaguePlayerSeriesResponseDto { schemaVersion: 'AI_LEAGUE_PLAYER_SERIES_VIEW_V1'; replayed: boolean; playerSeries: LeaguePlayerSeriesViewDto }
export interface LeagueCompletionStatusViewDto { leagueId: string; seasonId: string; fixtureId: string; fixtureStatus: LeagueFixtureStatus; bindingStatus: string | null; receiptHash: string | null; outboxStatus: 'NOT_CREATED' | 'PENDING' | 'DELIVERED'; standingsApplied: boolean; standingsRevision: number; allowedCommands: readonly LeagueFixtureCommand[] }
export interface LeagueCompletionStatusResponseDto { schemaVersion: 'AI_LEAGUE_COMPLETION_STATUS_VIEW_V1'; replayed: boolean; completion: LeagueCompletionStatusViewDto }
export interface LeagueApiErrorDto { schemaVersion: 'AI_LEAGUE_API_ERROR_V1'; code: string; field: string | null; message: string; retryable: boolean; currentLifecycleRevision: number | null; currentLifecycleStatus: string | null }

export interface LeagueCreateRequestDto { schemaVersion: 'AI_LEAGUE_CREATE_REQUEST_V1'; leagueKey: string; seasonKey: string; seasonMode: LeagueSeasonMode; managedTeamCode: string | null; seasonRootSeed: string; clientCommandId: string }
export interface LeagueRunRequestDto { schemaVersion: 'AI_LEAGUE_RUN_ROUND_COMMAND_V1'; expectedLifecycleRevision: number; clientCommandId: string }
export interface LeagueLifecycleRequestDto { schemaVersion: 'AI_LEAGUE_LIFECYCLE_COMMAND_V1'; expectedLifecycleRevision: number; clientCommandId: string }
export interface LeaguePlayerSeriesRequestDto { schemaVersion: 'AI_LEAGUE_PLAYER_SERIES_COMMAND_V1'; expectedLifecycleRevision: number; clientCommandId: string }
export interface LeagueCompletionRequestDto { schemaVersion: 'AI_LEAGUE_PLAYER_COMPLETION_COMMAND_V1'; expectedLifecycleRevision: number; clientCommandId: string; bindingHash: string }

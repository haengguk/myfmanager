export const CAREER_SCHEMAS = {
  createRequest: 'CAREER_CREATE_REQUEST_V1',
  createResponse: 'CAREER_CREATE_RESPONSE_V1',
  list: 'CAREER_LIST_V1',
  view: 'CAREER_VIEW_V1',
  calendarView: 'CAREER_CALENDAR_VIEW_V1',
  advanceRequest: 'CAREER_CALENDAR_ADVANCE_REQUEST_V1',
  advanceResponse: 'CAREER_CALENDAR_ADVANCE_RESPONSE_V1',
  competitionCommandRequest: 'CAREER_COMPETITION_COMMAND_REQUEST_V1',
  competitionCommandResponse: 'CAREER_COMPETITION_COMMAND_RESPONSE_V1',
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

export type CareerAdvanceMode = 'ADVANCE_ONE_DAY' | 'ADVANCE_TO_NEXT_EVENT';
export interface CareerAdvanceRequestDto { schemaVersion: typeof CAREER_SCHEMAS.advanceRequest; expectedCalendarRevision: number; mode: CareerAdvanceMode; clientCommandId: string }
export interface CareerPendingAdvanceDto { clientCommandId: string; mode: CareerAdvanceMode; expectedCalendarRevision: number; commandStatus: 'PENDING'; createdAt: string; updatedAt: string }
export interface CareerAdvanceCommandResultDto { clientCommandId: string; mode: CareerAdvanceMode; expectedCalendarRevision: number; commandStatus: 'PENDING' | 'COMPLETED'; resultingDate: string; resultingCalendarRevision: number; resultingStateHash: string; resultingLifecycleStatus: 'ACTIVE' | 'SEASON_ROLLOVER_REQUIRED'; resultingBlockingReason: string | null; stopReason: string | null; pending: boolean; backgroundAccepted: boolean; createdAt: string; updatedAt: string; completedAt: string | null }

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

export type CalendarOfficialStatus = 'OFFICIAL_CONFIRMED' | 'OFFICIAL_BY_NO_CHANGE_STATEMENT' | 'OFFICIAL_PARTIAL' | 'DERIVED' | 'OFFICIAL_PENDING' | 'SUPERSEDED';
export interface CareerCalendarStageDto { stageId: string; displayNameKo: string; startDate: string | null; endDate: string | null; officialStatus: CalendarOfficialStatus; teamCount: number | null; seriesCount: number | null; format: string; seriesRules: readonly string[] }
export interface CareerCalendarEventDto { eventId: string; templateId: string; sourceReferenceId: string; displayNameKo: string; startDate: string; endDate: string; timezone: string | null; timezoneScope: 'SINGLE_IANA_ZONE' | 'MULTI_ZONE'; locations: readonly string[]; officialStatus: CalendarOfficialStatus; projectionStatus: 'REFERENCE_YEAR_SOURCE' | 'GAME_PROJECTED_FROM_2026_TEMPLATE'; participationType: 'ALL_LCK' | 'RANKING_QUALIFIED' | 'REGION_SLOT' | 'NATIONAL_TEAM_RELEASE'; participation: string; teamCount: number | null; seriesCount: number | null; format: string; seriesRules: readonly string[]; draftMode: string | null; draftStatus: string; executionStatus: 'LINKED_EXISTING_LEAGUE_FIXTURES' | 'LINKED_COMPETITION_SERIES_EXECUTION' | 'FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED'; stages: readonly CareerCalendarStageDto[] }
export interface CareerCalendarFixtureDto { fixtureId: string; roundNumber: number; date: string; scheduleStatus: 'GAME_DERIVED_SCHEDULE_POLICY'; executionMode: 'FULL_AUTO' | 'PLAYER_CONTROLLED'; firstTeamCode: string; secondTeamCode: string; lifecycleStatus: string; seriesId: string; jobStatus: string | null; pendingOutbox: boolean }
export interface CareerCalendarProvenanceDto { referenceYear: 2026; sourceAsOf: string; referenceCatalogSnapshotAt: string; templateVersion: string; templateHash: string; projectionPolicy: 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1'; anchorAlgorithm: 'FIRST_FULL_CYCLE_AFTER_CURRENT_DATE_V1'; sourceCount: 15; calendarDefinitionCount: 11; qualificationEdgeCount: 6; derivedRestWindowCount: 7; pendingOfficialFieldCount: 6 }
export type CareerCompetitionRuleStatus = 'RULE_SOURCE_COMPLETE' | 'RULE_SOURCE_INCOMPLETE' | 'PRODUCT_POLICY_REQUIRED' | 'REFERENCE_TEMPLATE_ONLY' | 'VERIFIED_PRIOR_SEASON_REQUIRED';
export interface CareerCompetitionSummaryDto { competitionId: string; stageId: string; ruleStatus: CareerCompetitionRuleStatus; lifecycleStatus: string; blockingReason: string | null; revision: number; stateHash: string | null; completedFixtures: number; totalFixtures: number }
export interface CareerCompetitionFixtureDto { competitionId: string; matchId: string; fixtureId: string; seriesId: string; date: string; scheduleStatus: 'OFFICIAL_PROJECTED_DATE' | 'GAME_DERIVED_SCHEDULE_POLICY'; seriesFormat: 'BO3' | 'BO5'; hardFearless: true; firstTeamCode: string | null; secondTeamCode: string | null; executionMode: 'FULL_AUTO' | 'PLAYER_CONTROLLED'; lifecycleStatus: string; managedTeamIncluded: boolean; rootSeed: string; seedAlgorithm: 'CAREER_COMPETITION_MATCH_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1'; firstSelectorType: string; firstSelectorValue: string; secondSelectorType: string; secondSelectorValue: string; stageId: string; blockingReason: string | null; bindingHash: string | null; jobId: string | null; jobStatus: string | null; resultApplicationStatus: 'NOT_APPLIED' | 'APPLIED'; failureCode: string | null }
export interface CareerCompetitionStandingDto { groupId: 'BARON' | 'ELDER'; groupPoints: number; groupRank: number; teamCode: string; matchWins: number; matchLosses: number; gameWins: number; gameLosses: number; strengthOfVictory: number; winTimeSeconds: number; tieBreakTrace: string; standingsHash: string }
export interface CareerCompetitionSeedDto { competitionId: string; seedScope: 'CUP_PLAY_IN_SEED' | 'CUP_PLAYOFF_SEED' | 'PLAY_IN_SEED'; seedNumber: number; teamCode: string; sourceInputHash: string }
export interface CareerCompetitionViewDto { schemaVersion: 'CAREER_COMPETITION_VIEW_V1'; calendarSeasonYear: number; ruleResourceHash: string; ruleVersion: 'lck-career-competition-rules-2026-v2'; gamePolicyVersion: 'CAREER_COMPETITION_GAME_POLICY_V2'; projectionPolicy: 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1'; r3r4AllocationPolicy: 'LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1'; lifecycleStatus: string; revision: number; stateHash: string | null; currentCompetition: CareerCompetitionSummaryDto | null; nextCompetition: CareerCompetitionSummaryDto | null; nextFixture: CareerCompetitionFixtureDto | null; qualificationOutputs: readonly { competitionId: string; outputId: string; teamCode: string }[]; groupStandings: readonly CareerCompetitionStandingDto[]; currentSeeds: readonly CareerCompetitionSeedDto[]; externalExecutionLimited: boolean; activePendingCommand: { clientCommandId: string; competitionId: string; matchId: string; commandStatus: 'PENDING' | 'RUNNING' } | null; allowedCommands: readonly string[] }
export interface CareerSourceDataNoteDto { subject: 'KESPA_CUP'; status: 'REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE'; sourceReferenceYear: 2025; ruleVersion: 'KESPA_CUP_REFERENCE_TEMPLATE_2025'; blockers: readonly ['KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE', 'EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING'] }
export interface CareerCalendarViewDto { schemaVersion: typeof CAREER_SCHEMAS.calendarView; careerId: string; activeCalendarSeasonYear: number; currentDate: string; calendarRevision: number; lifecycleStatus: 'ACTIVE' | 'SEASON_ROLLOVER_REQUIRED'; blockingReason: string | null; calendarStateHash: string; stateHashAlgorithm: 'CAREER_CALENDAR_STATE_SHA256_CANONICAL_V1'; provenance: CareerCalendarProvenanceDto; projectionStatus: 'REFERENCE_YEAR_SOURCE' | 'GAME_PROJECTED_FROM_2026_TEMPLATE'; currentEvent: CareerCalendarEventDto | null; nextEvent: CareerCalendarEventDto | null; currentStage: CareerCalendarStageDto | null; nextStage: CareerCalendarStageDto | null; upcomingEvents: readonly CareerCalendarEventDto[]; fixtureOverlay: { schemaVersion: 'CAREER_R1_R2_FIXTURE_OVERLAY_V1'; allocationPolicy: 'ROUND_LINEAR_INCLUSIVE_WINDOW_ONE_SLOT_PER_ROUND_V1'; overlayHash: string; scheduleStatus: 'GAME_DERIVED_SCHEDULE_POLICY'; provenanceV2: { schemaVersion: 'CAREER_R1_R2_FIXTURE_OVERLAY_PROVENANCE_V2'; hashAlgorithm: 'SHA256_UTF8_EXPLICIT_ORDERED_R1_R2_OVERLAY_PROVENANCE_V2'; leagueId: string; seasonId: string; scheduleIdentity: string; overlayHash: string } }; upcomingFixtures: readonly CareerCalendarFixtureDto[]; nextManagedFixture: CareerCalendarFixtureDto | null; allowedAdvanceModes: readonly CareerAdvanceMode[]; activePendingAdvance: CareerPendingAdvanceDto | null; advanceRecoveryStatus: 'LEGACY_PENDING_RECONCILIATION_REQUIRED' | null; competition: CareerCompetitionViewDto; qualificationEdges: readonly { fromTemplateId: string; toTemplateId: string; rule: string; officialStatus: CalendarOfficialStatus }[]; pendingOfficialFields: readonly { id: string; field: string; reason: string }[]; sourceDataNotes: readonly CareerSourceDataNoteDto[] }
export interface CareerAdvanceResponseDto { schemaVersion: typeof CAREER_SCHEMAS.advanceResponse; replayed: boolean; pending: boolean; stopReason: string | null; backgroundAccepted: boolean; commandResult: CareerAdvanceCommandResultDto; calendar: CareerCalendarViewDto }
export interface CareerCompetitionCommandRequestDto { schemaVersion: typeof CAREER_SCHEMAS.competitionCommandRequest; expectedCompetitionRevision: number; clientCommandId: string }
export interface CareerCompetitionCommandResponseDto { schemaVersion: typeof CAREER_SCHEMAS.competitionCommandResponse; executionMode: 'FULL_AUTO' | 'PLAYER_CONTROLLED' | 'NONE'; fixtureId: string | null; matchId: string | null; seriesId: string | null; bindingHash: string | null; jobId: string | null; status: string; replayed: boolean; backgroundAccepted: boolean; failureCode: string | null }

export interface CareerErrorDto {
  schemaVersion: typeof CAREER_SCHEMAS.error;
  code: string;
  field: string | null;
  message: string;
}

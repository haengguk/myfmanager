import { validateCareerMarket, validateMarketCommand, validateMarketChange, marketOperationKey, readMarketOperation } from '../src/features/career/api/careerMarket.contract.ts';
import { validateCareerRoster, readRosterOperation, rosterOperationKey } from '../src/features/career/api/careerRoster.contract.ts';
import { CareerMutationGate } from '../src/features/career/career.mutation.ts';
import { CareerApiFailure } from '../src/features/career/api/careerApi.failure.ts';
import { validateCareerSeasons, validateCareerSeasonDetail, validateCareerTransition, validateCareerTransitionRequest, validateCareerAdvanceResponse, validateCareerCalendar, validateCareerCompetitionCommandResponse, validateCareerCreateResponse, validateCareerListResponse, validateCareerView } from '../src/features/career/api/careerApi.validation.ts';
import { careerResumeRoute } from '../src/features/career/career.adapter.ts';
import {
  careerPointerRecoveryAction, clearCareerCreateOperation, logicalCareerCreate,
  isAmbiguousCareerCreateFailure,
  careerCanonicalSelectionKey, readCareerCreateOperation, readCareerPointer, readCareerReturnContext,
  clearCareerAdvanceOperation, logicalCareerAdvance, readCareerAdvanceOperation,
  clearCareerCompetitionOperation, logicalCareerCompetition, readCareerCompetitionOperation,
  reconcileCareerCompetitionOperation,
  reconcileCareerAdvanceOperation, writeCareerPointer, writeCareerReturnContext,
} from '../src/features/career/career.pointer.ts';

const careerId = `career_${'1'.repeat(64)}`;
const secondCareerId = `career_${'2'.repeat(64)}`;
const leagueId = `league_${'3'.repeat(64)}`;
const seasonId = `season_${'4'.repeat(64)}`;
const fixtureId = `fixture_${'5'.repeat(64)}`;
const seriesId = `series_${'6'.repeat(64)}`;
const hash = 'a'.repeat(64);
const earlier = '2026-09-01T00:00:00Z';
const later = '2026-09-02T00:00:00Z';
const clone = (value) => structuredClone(value);
const storage = () => { const values = new Map(); return { values, getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }; };
function resume(kind = 'LEAGUE_DASHBOARD') {
  return { kind, leagueId, seasonId, fixtureId: kind === 'PLAYER_SERIES' ? fixtureId : null, seriesId: kind === 'PLAYER_SERIES' ? seriesId : null, seasonLifecycleStatus: kind === 'SEASON_COMPLETE' ? 'COMPLETED' : 'READY', currentRound: 1, lifecycleRevision: 1, standingsRevision: 0, allowedCommands: kind === 'PLAYER_SERIES' ? ['RESUME_PLAYER_SERIES'] : kind === 'SEASON_COMPLETE' ? ['VIEW_STANDINGS'] : ['VIEW_STANDINGS', 'RUN_CURRENT_ROUND_AUTO_FIXTURES', 'CANCEL_SEASON'] };
}
function view(kind = 'LEAGUE_DASHBOARD') {
  return { schemaVersion: 'CAREER_VIEW_V1', careerId, saveName: 'GEN 장기 저장', managerName: '김 감독', managedTeamCode: 'GEN', startDate: '2026-08-24', currentDate: '2026-08-24', lifecycleStatus: 'ACTIVE', revision: 0, leagueId, seasonId, rootSeedAlgorithmId: 'CAREER_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1', rootSeed: '-73', leagueFrozenSnapshotIdentity: hash, leagueProductDecisionIdentity: hash, referenceCatalogVersion: 'catalog-v1', referenceCatalogHash: hash, bindingSchemaVersion: 'CAREER_LEAGUE_BINDING_V1', bindingHash: hash, resume: resume(kind), createdAt: earlier, updatedAt: later };
}
function summary(id = careerId, updatedAt = later) { const source = view(); return { careerId: id, saveName: source.saveName, managerName: source.managerName, managedTeamCode: source.managedTeamCode, currentDate: source.currentDate, leagueId: source.leagueId, seasonId: source.seasonId, lifecycleStatus: source.lifecycleStatus, resumeKind: source.resume.kind, updatedAt }; }
function calendarEvent(index = 1) { return { eventId: `calendar_event_${String(index).repeat(64)}`, templateId: index === 1 ? 'LCK_CUP' : 'FIRST_STAND', sourceReferenceId: `calendar-source-${index}`, displayNameKo: `2027 공식 일정 ${index}`, startDate: index === 1 ? '2027-01-14' : '2027-03-16', endDate: index === 1 ? '2027-03-01' : '2027-03-22', timezone: 'Asia/Seoul', timezoneScope: 'SINGLE_IANA_ZONE', locations: ['Seoul'], officialStatus: 'OFFICIAL_CONFIRMED', projectionStatus: 'GAME_PROJECTED_FROM_2026_TEMPLATE', participationType: index === 1 ? 'ALL_LCK' : 'RANKING_QUALIFIED', participation: '공식 참가 정책', teamCount: index === 1 ? 10 : 8, seriesCount: index === 1 ? 40 : 13, format: 'Bo3 / Bo5', seriesRules: ['Bo3'], draftMode: 'Fearless Draft', draftStatus: 'OFFICIAL_CONFIRMED', executionStatus: index === 1 ? 'LINKED_COMPETITION_SERIES_EXECUTION' : 'FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED', stages: [] }; }
function calendarView() { return { schemaVersion: 'CAREER_CALENDAR_VIEW_V1', careerId, activeCalendarSeasonYear: 2027, currentDate: '2026-08-24', calendarRevision: 0, lifecycleStatus: 'ACTIVE', blockingReason: null, calendarStateHash: hash, stateHashAlgorithm: 'CAREER_CALENDAR_STATE_SHA256_CANONICAL_V1', provenance: { referenceYear: 2026, sourceAsOf: '2026-08-23', referenceCatalogSnapshotAt: '2026-08-24', templateVersion: 'lck-career-calendar-reference-2026-v1', templateHash: hash, projectionPolicy: 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1', anchorAlgorithm: 'FIRST_FULL_CYCLE_AFTER_CURRENT_DATE_V1', sourceCount: 15, calendarDefinitionCount: 11, qualificationEdgeCount: 6, derivedRestWindowCount: 7, pendingOfficialFieldCount: 6 }, projectionStatus: 'GAME_PROJECTED_FROM_2026_TEMPLATE', currentEvent: null, nextEvent: calendarEvent(1), currentStage: null, nextStage: null, upcomingEvents: [calendarEvent(1), calendarEvent(2)], fixtureOverlay: { schemaVersion: 'CAREER_R1_R2_FIXTURE_OVERLAY_V1', allocationPolicy: 'ROUND_LINEAR_INCLUSIVE_WINDOW_ONE_SLOT_PER_ROUND_V1', overlayHash: hash, scheduleStatus: 'GAME_DERIVED_SCHEDULE_POLICY' }, upcomingFixtures: [{ fixtureId, roundNumber: 1, date: '2027-04-01', scheduleStatus: 'GAME_DERIVED_SCHEDULE_POLICY', executionMode: 'PLAYER_CONTROLLED', firstTeamCode: 'GEN', secondTeamCode: 'T1', lifecycleStatus: 'AWAITING_PLAYER', seriesId, jobStatus: null, pendingOutbox: false }], nextManagedFixture: { fixtureId, roundNumber: 1, date: '2027-04-01', scheduleStatus: 'GAME_DERIVED_SCHEDULE_POLICY', executionMode: 'PLAYER_CONTROLLED', firstTeamCode: 'GEN', secondTeamCode: 'T1', lifecycleStatus: 'AWAITING_PLAYER', seriesId, jobStatus: null, pendingOutbox: false }, allowedAdvanceModes: ['ADVANCE_ONE_DAY', 'ADVANCE_TO_NEXT_EVENT'], qualificationEdges: Array.from({ length: 6 }, (_, index) => ({ fromTemplateId: 'LCK_CUP', toTemplateId: 'FIRST_STAND', rule: `공식 진출 규칙 ${index}`, officialStatus: 'OFFICIAL_CONFIRMED' })), pendingOfficialFields: Array.from({ length: 6 }, (_, index) => ({ id: `pending-${index}`, field: `field-${index}`, reason: '공식 발표 대기' })), sourceDataNotes: [{ subject: 'KESPA_CUP', status: 'REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE', sourceReferenceYear: 2025, ruleVersion: 'KESPA_CUP_REFERENCE_TEMPLATE_2025', blockers: ['KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE', 'EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING'] }] }; }
function hardenedCalendarView() {
  const value = calendarView();
  value.fixtureOverlay.provenanceV2 = { schemaVersion: 'CAREER_R1_R2_FIXTURE_OVERLAY_PROVENANCE_V2', hashAlgorithm: 'SHA256_UTF8_EXPLICIT_ORDERED_R1_R2_OVERLAY_PROVENANCE_V2', leagueId, seasonId, scheduleIdentity: hash, overlayHash: 'b'.repeat(64) };
  value.activePendingAdvance = null;
  value.advanceRecoveryStatus = null;
  value.competition = { schemaVersion: 'CAREER_COMPETITION_VIEW_V1', calendarSeasonYear: 2027, ruleResourceHash: 'c'.repeat(64), ruleVersion: 'lck-career-competition-rules-2026-v2', gamePolicyVersion: 'CAREER_COMPETITION_GAME_POLICY_V2', projectionPolicy: 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1', r3r4AllocationPolicy: 'LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1', lifecycleStatus: 'ACTIVE', revision: 0, stateHash: 'd'.repeat(64), currentCompetition: null, nextCompetition: { competitionId: 'LCK_CUP', stageId: 'CUP_INITIALIZATION', ruleStatus: 'RULE_SOURCE_COMPLETE', lifecycleStatus: 'READY', blockingReason: null, revision: 0, stateHash: 'e'.repeat(64), completedFixtures: 0, totalFixtures: 40 }, nextFixture: { competitionId: 'LCK_CUP', matchId: 'GROUP_B01_E01', fixtureId: `competition_fixture_${'7'.repeat(64)}`, seriesId, date: '2027-01-14', scheduleStatus: 'GAME_DERIVED_SCHEDULE_POLICY', seriesFormat: 'BO3', hardFearless: true, firstTeamCode: 'GEN', secondTeamCode: 'HLE', executionMode: 'PLAYER_CONTROLLED', lifecycleStatus: 'READY', managedTeamIncluded: true, rootSeed: '-91', seedAlgorithm: 'CAREER_COMPETITION_MATCH_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1' }, qualificationOutputs: [], externalExecutionLimited: false, activePendingCommand: null, allowedCommands: [] };
  Object.assign(value.competition.nextFixture, { firstSelectorType: 'TEAM', firstSelectorValue: 'GEN', secondSelectorType: 'TEAM', secondSelectorValue: 'HLE', stageId: 'GROUP_BATTLE', blockingReason: null, bindingHash: null, jobId: null, jobStatus: null, resultApplicationStatus: 'NOT_APPLIED', failureCode: null });
  value.competition.groupStandings = [];
  value.competition.currentSeeds = [];
  value.competition.allowedCommands = ['START_PLAYER_COMPETITION_SERIES'];
  return value;
}
function advanceResponse(calendar = hardenedCalendarView(), pending = false) {
  const commandId = '10000000-0000-4000-8000-000000000001';
  const stopReason = pending ? 'AUTO_FIXTURES_PENDING' : null;
  return { schemaVersion: 'CAREER_CALENDAR_ADVANCE_RESPONSE_V1', replayed: false, pending, stopReason, backgroundAccepted: true, commandResult: { clientCommandId: commandId, mode: 'ADVANCE_TO_NEXT_EVENT', expectedCalendarRevision: 0, commandStatus: pending ? 'PENDING' : 'COMPLETED', resultingDate: calendar.currentDate, resultingCalendarRevision: calendar.calendarRevision, resultingStateHash: hash, resultingLifecycleStatus: calendar.lifecycleStatus, resultingBlockingReason: pending ? 'AUTO_FIXTURES_PENDING' : null, stopReason, pending, backgroundAccepted: true, createdAt: earlier, updatedAt: later, completedAt: pending ? null : later }, calendar };
}
function competitionCommandResponse(executionMode = 'PLAYER_CONTROLLED') {
  const fullAuto = executionMode === 'FULL_AUTO';
  return { schemaVersion: 'CAREER_COMPETITION_COMMAND_RESPONSE_V1', executionMode, fixtureId: `competition_fixture_${'7'.repeat(64)}`, matchId: 'GROUP_B01_E01', seriesId, bindingHash: hash, jobId: fullAuto ? 'competition-job-1' : null, status: fullAuto ? 'PENDING' : 'ACTIVE', replayed: false, backgroundAccepted: fullAuto, failureCode: null };
}
function accepts(label, action) { try { action(); console.log(`PASS ${label}`); } catch (error) { console.error(`FAIL ${label}`, error); process.exitCode = 1; } }
function rejects(label, action) { try { action(); console.error(`FAIL ${label}: invalid value accepted`); process.exitCode = 1; } catch { console.log(`PASS ${label}`); } }

accepts('Career list, detail and create response contracts', () => {
  validateCareerView(view());
  validateCareerCreateResponse({ schemaVersion: 'CAREER_CREATE_RESPONSE_V1', replayed: false, career: view() });
  validateCareerListResponse({ schemaVersion: 'CAREER_LIST_V1', careers: [summary()], currentCount: 1, maximumCount: 100, remainingCount: 99 });
});

accepts('unknown, missing, wrong-type and empty display fields fail closed', () => {
  const unknown = clone(view()); unknown.unexpected = true;
  const missing = clone(view()); delete missing.bindingHash;
  const wrongType = clone(view()); wrongType.resume.currentRound = '1';
  const emptyDisplay = clone(view()); emptyDisplay.saveName = '';
  for (const invalid of [unknown, missing, wrongType, emptyDisplay]) {
    let rejected = false;
    try { validateCareerView(invalid); } catch { rejected = true; }
    if (!rejected) throw new Error('invalid Career response accepted');
  }
});

rejects('capacity metadata and authoritative ordering are cross-validated', () => {
  validateCareerListResponse({ schemaVersion: 'CAREER_LIST_V1', careers: [summary(careerId, earlier), summary(secondCareerId, later)], currentCount: 2, maximumCount: 100, remainingCount: 99 });
});

rejects('non-actionable Player Series resume relation is rejected', () => {
  const invalid = view('PLAYER_SERIES'); invalid.resume.allowedCommands = ['VIEW_STANDINGS']; validateCareerView(invalid);
});

accepts('one create UUID is reused only for one normalized payload', () => {
  const target = storage(); let sequence = 0;
  const uuid = () => sequence++ === 0 ? '10000000-0000-4000-8000-000000000001' : '20000000-0000-4000-8000-000000000002';
  const first = logicalCareerCreate(target, { saveName: '  GEN 장기 저장 ', managerName: ' 김 감독 ', managedTeamCode: 'GEN' }, uuid);
  const replay = logicalCareerCreate(target, { saveName: 'GEN 장기 저장', managerName: '김 감독', managedTeamCode: 'GEN' }, uuid);
  const changed = logicalCareerCreate(target, { saveName: 'GEN 두 번째', managerName: '김 감독', managedTeamCode: 'GEN' }, uuid);
  if (first.clientCommandId !== replay.clientCommandId || changed.clientCommandId === first.clientCommandId || sequence !== 2) throw new Error('logical create identity mismatch');
  if (!isAmbiguousCareerCreateFailure(new CareerApiFailure('TIMEOUT', 'timeout')) || !isAmbiguousCareerCreateFailure(new CareerApiFailure('BACKEND', 'retry', 503, 'CAREER_TEMPORARILY_UNAVAILABLE', null, true)) || isAmbiguousCareerCreateFailure(new CareerApiFailure('BACKEND', 'capacity', 409, 'CAREER_CAPACITY_REACHED'))) throw new Error('create failure retention policy');
  clearCareerCreateOperation(target); if (readCareerCreateOperation(target) !== null) throw new Error('completed operation was not cleared');
});

accepts('canonical selection key is named honestly and V1 storage migrates fail-closed', () => {
  const target = storage(); const selection = { saveName: ' GEN 장기 저장 ', managerName: ' 감독 ', managedTeamCode: 'GEN' }; const key = careerCanonicalSelectionKey(selection);
  target.setItem('lolmanager.career.create-operation.v1', JSON.stringify({ schemaVersion: 'CAREER_CREATE_OPERATION_V1', fingerprint: key, selection, clientCommandId: '10000000-0000-4000-8000-000000000001' }));
  const migrated = readCareerCreateOperation(target); if (!migrated || migrated.schemaVersion !== 'CAREER_CREATE_OPERATION_V2' || migrated.canonicalSelectionKey !== key || 'fingerprint' in migrated) throw new Error('V1 operation migration mismatch');
  const raw = target.getItem('lolmanager.career.create-operation.v2'); if (!raw || raw.includes('fingerprint') || !raw.includes('canonicalSelectionKey')) throw new Error('misleading key persisted');
  target.removeItem('lolmanager.career.create-operation.v2'); target.setItem('lolmanager.career.create-operation.v1', '{"schemaVersion":"CAREER_CREATE_OPERATION_V1","fingerprint":"tampered"}'); if (readCareerCreateOperation(target) !== null || target.getItem('lolmanager.career.create-operation.v1') !== null) throw new Error('invalid legacy value did not fail closed');
});

accepts('calendar and advance response validate official, projected and pending facts', () => {
  const calendar = validateCareerCalendar(hardenedCalendarView());
  validateCareerAdvanceResponse(advanceResponse(calendar));
  if (calendar.provenance.calendarDefinitionCount !== 11 || calendar.pendingOfficialFields.length !== 6 || calendar.sourceDataNotes[0].sourceReferenceYear !== 2025 || calendar.sourceDataNotes[0].blockers.length !== 2 || calendar.upcomingFixtures[0].scheduleStatus !== 'GAME_DERIVED_SCHEDULE_POLICY' || calendar.competition.nextCompetition?.totalFixtures !== 40) throw new Error('calendar facts mismatch');
});

rejects('competition resource identity and external limitation relation fail closed', () => {
  const invalid = hardenedCalendarView(); invalid.competition.ruleResourceHash = 'not-a-hash'; invalid.competition.externalExecutionLimited = true; validateCareerCalendar(invalid);
});

accepts('Competition command responses enforce bound Player and durable Auto identities', () => {
  const player = validateCareerCompetitionCommandResponse(competitionCommandResponse());
  const auto = validateCareerCompetitionCommandResponse(competitionCommandResponse('FULL_AUTO'));
  if (player.status !== 'ACTIVE' || auto.status !== 'PENDING' || !auto.backgroundAccepted) throw new Error('Competition execution mode mismatch');
});

rejects('Competition command response rejects unknown fields and mode/job mismatches', () => {
  const invalid = competitionCommandResponse('FULL_AUTO'); invalid.jobId = null; invalid.winnerTeamCode = 'GEN'; validateCareerCompetitionCommandResponse(invalid);
});

accepts('one Competition UUID is reused per Career until terminal reconciliation', () => {
  const target = storage(); let sequence = 0;
  const uuid = () => sequence++ === 0 ? '10000000-0000-4000-8000-000000000001' : '20000000-0000-4000-8000-000000000002';
  const first = logicalCareerCompetition(target, careerId, 0, uuid);
  const replay = logicalCareerCompetition(target, careerId, 1, uuid);
  const second = logicalCareerCompetition(target, secondCareerId, 4, uuid);
  if (first.clientCommandId !== replay.clientCommandId || replay.expectedCompetitionRevision !== 0 || second.clientCommandId === first.clientCommandId || sequence !== 2) throw new Error('logical Competition identity mismatch');
  clearCareerCompetitionOperation(target, careerId);
  if (readCareerCompetitionOperation(target, careerId) !== null || readCareerCompetitionOperation(target, secondCareerId) === null) throw new Error('one Competition clear affected another Career');
});

accepts('server pending Competition command restores a lost browser operation', () => {
  const target = storage();
  const pending = { clientCommandId: '10000000-0000-4000-8000-000000000001', competitionId: 'LCK_CUP', matchId: 'GB_B1_E2', commandStatus: 'PENDING' };
  const restored = reconcileCareerCompetitionOperation(target, careerId, 7, pending);
  if (!restored || restored.clientCommandId !== pending.clientCommandId || restored.expectedCompetitionRevision !== 7) throw new Error('server Competition operation was not restored');
  const stale = reconcileCareerCompetitionOperation(target, careerId, 8, null);
  if (stale !== null || readCareerCompetitionOperation(target, careerId) !== null) throw new Error('terminal stale Competition operation was not cleared');
});

rejects('calendar rejects invented KeSPA definitions and unknown response fields', () => {
  const invalid = hardenedCalendarView(); invalid.upcomingEvents[0].templateId = 'KESPA_CUP'; invalid.invented = true; validateCareerCalendar(invalid);
});

accepts('pending advances are isolated per Career and server state restores a lost local pointer', () => {
  const target = storage();
  const first = logicalCareerAdvance(target, careerId, 0, 'ADVANCE_TO_NEXT_EVENT', () => '10000000-0000-4000-8000-000000000001');
  const second = logicalCareerAdvance(target, secondCareerId, 3, 'ADVANCE_ONE_DAY', () => '20000000-0000-4000-8000-000000000002');
  if (readCareerAdvanceOperation(target, careerId)?.clientCommandId !== first.clientCommandId || readCareerAdvanceOperation(target, secondCareerId)?.clientCommandId !== second.clientCommandId) throw new Error('per-Career operation map mismatch');
  let replacementRejected = false;
  try { logicalCareerAdvance(target, careerId, 0, 'ADVANCE_ONE_DAY', () => '30000000-0000-4000-8000-000000000003'); } catch { replacementRejected = true; }
  if (!replacementRejected) throw new Error('pending operation was replaced');
  clearCareerAdvanceOperation(target, careerId);
  if (readCareerAdvanceOperation(target, careerId) !== null || readCareerAdvanceOperation(target, secondCareerId) === null) throw new Error('one Career clear affected another Career');
  const serverPending = { clientCommandId: first.clientCommandId, mode: first.mode, expectedCalendarRevision: first.expectedCalendarRevision, commandStatus: 'PENDING', createdAt: earlier, updatedAt: later };
  reconcileCareerAdvanceOperation(target, careerId, serverPending);
  if (readCareerAdvanceOperation(target, careerId)?.clientCommandId !== first.clientCommandId) throw new Error('server pending operation was not restored');
  const pendingCalendar = hardenedCalendarView(); pendingCalendar.allowedAdvanceModes = []; pendingCalendar.activePendingAdvance = serverPending;
  validateCareerCalendar(pendingCalendar);
  clearCareerAdvanceOperation(target, careerId);
  if (readCareerAdvanceOperation(target, secondCareerId) === null) throw new Error('server reconciliation erased another Career');
});

accepts('manual reconciliation after bounded retries preserves the exact command UUID', () => {
  const target = storage();
  const operation = logicalCareerAdvance(target, careerId, 7, 'ADVANCE_TO_NEXT_EVENT', () => '10000000-0000-4000-8000-000000000001');
  const serverPending = { clientCommandId: operation.clientCommandId, mode: operation.mode, expectedCalendarRevision: operation.expectedCalendarRevision, commandStatus: 'PENDING', createdAt: earlier, updatedAt: later };
  reconcileCareerAdvanceOperation(target, careerId, serverPending);
  const afterPendingGet = readCareerAdvanceOperation(target, careerId);
  const afterCompletedGet = reconcileCareerAdvanceOperation(target, careerId, null);
  if (!afterPendingGet || !afterCompletedGet || afterCompletedGet.clientCommandId !== operation.clientCommandId || afterCompletedGet.expectedCalendarRevision !== 7 || afterCompletedGet.mode !== operation.mode) throw new Error('read-only reconciliation replaced or cleared the logical command');
});

accepts('legacy pending recovery blocker opens a read-only Calendar with advances disabled', () => {
  const legacy = hardenedCalendarView();
  legacy.blockingReason = 'LEGACY_PENDING_RECONCILIATION_REQUIRED';
  legacy.advanceRecoveryStatus = 'LEGACY_PENDING_RECONCILIATION_REQUIRED';
  legacy.allowedAdvanceModes = [];
  const validated = validateCareerCalendar(legacy);
  if (validated.activePendingAdvance !== null || validated.allowedAdvanceModes.length !== 0) throw new Error('legacy pending recovery contract mismatch');
});

accepts('completed replay keeps its frozen command result while returning the live Calendar', () => {
  const live = hardenedCalendarView();
  live.currentDate = '2026-08-26'; live.calendarRevision = 2;
  const response = advanceResponse(live);
  response.replayed = true;
  response.commandResult.resultingDate = '2026-08-25';
  response.commandResult.resultingCalendarRevision = 1;
  const validated = validateCareerAdvanceResponse(response);
  if (validated.commandResult.resultingCalendarRevision !== 1 || validated.calendar.calendarRevision !== 2) throw new Error('frozen result and live Calendar were conflated');
});

rejects('advance receipt relation mismatches fail closed', () => {
  const invalid = advanceResponse(); invalid.pending = true; validateCareerAdvanceResponse(invalid);
});

accepts('pointer recovery clears only not-found and retains transient or integrity IDs', () => {
  const target = storage(); writeCareerPointer(target, careerId);
  if (readCareerPointer(target) !== careerId) throw new Error('pointer missing');
  if (careerPointerRecoveryAction(new CareerApiFailure('BACKEND', 'missing', 404, 'CAREER_NOT_FOUND')) !== 'CLEAR_NOT_FOUND') throw new Error('not-found policy');
  if (careerPointerRecoveryAction(new CareerApiFailure('NETWORK', 'network')) !== 'KEEP_RETRYABLE') throw new Error('network policy');
  if (careerPointerRecoveryAction(new CareerApiFailure('BACKEND', 'integrity', 500, 'CAREER_LINKED_SEASON_INTEGRITY_FAILURE')) !== 'KEEP_INTEGRITY') throw new Error('integrity policy');
  if ([...target.values.values()].some((value) => typeof value === 'string' && value.includes('managerName'))) throw new Error('full view persisted');
});

accepts('resume routing uses structured League and Player Series scopes', () => {
  const league = careerResumeRoute(validateCareerView(view())); const player = careerResumeRoute(validateCareerView(view('PLAYER_SERIES')));
  if (league.kind !== 'LEAGUE' || league.seasonId !== seasonId || player.kind !== 'PLAYER_SERIES' || player.fixtureId !== fixtureId || player.seriesId !== seriesId) throw new Error('resume route mismatch');
});

accepts('Career return context stores only canonical navigation identity', () => {
  const target = storage(); writeCareerReturnContext(target, careerId); const restored = readCareerReturnContext(target);
  if (!restored || restored.careerId !== careerId || Object.keys(restored).sort().join(',') !== 'careerId,schemaVersion') throw new Error('return context boundary');
});

if (!process.exitCode) {
  console.log('CAREER_TIME_AND_CALENDAR_PROGRESSION_V1_CONTRACT_VERIFICATION_PASSED');
  console.log('CAREER_CALENDAR_HARDENING_PHASE_A_CONTRACT_VERIFICATION_PASSED');
  console.log('CAREER_COMPETITION_LIFECYCLE_V1_FRONTEND_CONTRACT_VERIFICATION_PASSED');
  console.log('CAREER_COMPETITION_SERIES_EXECUTION_AND_RESULT_TRANSITION_V1_FRONTEND_CONTRACT_VERIFICATION_PASSED');
}

// Shared by the real-browser deferred-response check; no second DTO fixture catalog.
export { view, summary, hardenedCalendarView, competitionCommandResponse };

function domesticCalendar() {
  const value = hardenedCalendarView();
  Object.assign(value.competition, { ruleVersion: 'lck-career-competition-rules-2026-v3', gamePolicyVersion: 'CAREER_COMPETITION_GAME_POLICY_V3', domesticRuleCompatibility: 'CURRENT', domesticRankingDecisions: [{ competitionId: 'LCK_CUP', decisionId: 'CUP_BARON', status: 'RUNNING', inputHash: hash, policyVersion: 'LCK_DOMESTIC_RANKING_AND_SEEDED_TIE_DRAW_V1', detail: { pendingMatches: [{ matchId: 'TB_CUP_1', first: 'GEN', second: 'HLE' }] } }], finalRanking: null });
  value.competition.nextFixture.seriesFormat = 'BO1';
  value.competition.nextFixture.stageId = 'CUP_BARON_TIEBREAKER';
  value.competition.nextFixture.matchId = 'TB_CUP_1';
  return value;
}
function sealedDomesticCalendar() {
  const value = domesticCalendar();
  value.competition.domesticRankingDecisions = [];
  value.competition.finalRanking = { status: 'SEALED', sourceSeasonYear: 2027, sourceSeasonId: seasonId, championTeamCode: 'GEN', runnerUpTeamCode: 'T1', ranking: ['GEN', 'T1', 'HLE', 'DK', 'KT', 'NS', 'DNS', 'BRO', 'BFX', 'KRX'].map((teamCode, index) => ({ seed: index + 1, teamCode, seriesWins: 13, seriesLosses: 13, gameWins: 26, gameLosses: 26 })), stateHash: hash, ruleVersion: value.competition.ruleVersion, policyVersion: 'LCK_DOMESTIC_RANKING_AND_SEEDED_TIE_DRAW_V1', resultEvidenceHash: hash, worldsStatus: 'PENDING_IN_GAME_INTERNATIONAL_EVIDENCE', requiredInternationalEvidence: ['REGIONAL_SLOT_ALLOCATION', 'MSI_CHAMPION_AND_DOMESTIC_PLAYOFF_ELIGIBILITY'] };
  return value;
}
accepts('domestic BO1 tiebreak action carries resumable structured decision', () => validateCareerCalendar(domesticCalendar()));
accepts('ten sealed domestic places remain separate from pending Worlds evidence', () => validateCareerCalendar(sealedDomesticCalendar()));
rejects('duplicate final team cannot be presented as sealed', () => { const value = sealedDomesticCalendar(); value.competition.finalRanking.ranking[9].teamCode = 'GEN'; validateCareerCalendar(value); });
rejects('new rules require domestic decision projection', () => { const value = domesticCalendar(); delete value.competition.domesticRankingDecisions; delete value.competition.finalRanking; delete value.competition.domesticRuleCompatibility; validateCareerCalendar(value); });

function internationalCalendar() {
  const value = domesticCalendar();
  const codes = ['LCK:GEN', 'LCK:T1', 'LPL:BLG', 'LPL:JDG', 'LEC:G2', 'LCS:FLY', 'LCP:PSG', 'CBLOL:RED'];
  const entries = codes.map((team, i) => ({ team, region: team.split(':')[0], regionalSeed: i === 1 || i === 3 ? 2 : 1, pool: i === 1 || i === 3 ? 3 : i < 4 ? 1 : 2, phase: 'MAIN', qualification: i < 2 ? 'ACTUAL_CUP_RESULT' : 'TEMPORARY_OVERSEAS_RANK' }));
  value.competition.internationalCompetitions = [{ competitionId: 'FIRST_STAND', ruleVersion: 'career-international-rules-2026-v1', ruleResourceHash: hash, policyVersion: 'CAREER_INTERNATIONAL_GAME_POLICY_V1', selectionPolicy: 'TEMPORARY_OVERSEAS_FIVE_STARTER_RATING_SUM_LEXICAL_TEAM_V1', entries, rosterSnapshotIdentity: hash,
    bracket: { bouts: [{ id: 'FIRST_STAND_G0_O1', stage: 'GROUP', date: '2027-03-16', order: 1, format: 'BO5', first: 'LCK:GEN', second: 'LPL:JDG', selectionOwner: 'LCK:GEN', sidePolicy: 'INTERNATIONAL_ROFS_FIRST_PICK_OTHER_RED_LOSER_ROFS_V1', group: 'G0' }], placements: {}, draws: [{ scope: 'GROUPS', teams: codes, relaxation: null }], regionalPerformance: [], champion: null, complete: false }, results: {} }];
  Object.assign(value.competition.nextFixture, { competitionId: 'FIRST_STAND', firstTeamCode: 'LCK:GEN', secondTeamCode: 'LPL:JDG', firstSelectorType: 'REGISTERED_TEAM', firstSelectorValue: 'LCK:GEN', secondSelectorType: 'REGISTERED_TEAM', secondSelectorValue: 'LPL:JDG' });
  return value;
}
accepts('international registration and qualified fixture codes retain the Career contract', () => validateCareerCalendar(internationalCalendar()));
rejects('international duplicate regional seed fails closed', () => { const value = internationalCalendar(); value.competition.internationalCompetitions[0].entries[1].regionalSeed = 1; validateCareerCalendar(value); });
rejects('international result must reference the actual bracket participants', () => { const value = internationalCalendar(); value.competition.internationalCompetitions[0].results.FIRST_STAND_G0_O1 = { winner: 'LEC:G2', loser: 'LCK:GEN' }; validateCareerCalendar(value); });

function seasonsResponse() {
  return { schemaVersion: 'CAREER_SEASONS_V1', careerId, activeYear: 2029, calendarRevision: 5,
    seasons: [2029,2028,2027].map((year,i) => ({ year, ordinal: 3-i, leagueId: `league_${String(i+3).repeat(64)}`, seasonId: `season_${String(i+4).repeat(64)}`, status: i ? 'CLOSED' : 'ACTIVE', rosterHash: hash })),
    blockers: ['INCOMPLETE:LCK_CUP'], allowedCommands: [] };
}
function transitionResponse() {
  const seasons = seasonsResponse();
  return { replayed: true, receipt: { clientCommandId: '11111111-1111-4111-8111-111111111111', careerId, sourceYear: 2027, destinationYear: 2028, destinationSeasonId: seasons.seasons[1].seasonId, resultingRevision: 1, resultHash: hash, completedAt: later }, seasons };
}
accepts('original transition receipt can replay against a later active season', () => validateCareerTransition(transitionResponse()));
rejects('transition receipt cannot substitute the currently active destination', () => { const value = transitionResponse(); value.receipt.destinationSeasonId = value.seasons.seasons[0].seasonId; validateCareerTransition(value); });
rejects('blocked season cannot advertise a rollover command', () => { const value = seasonsResponse(); value.allowedCommands = ['START_NEXT_SEASON']; validateCareerSeasons(value); });
rejects('season continuity cannot skip a year or ordinal', () => { const value = seasonsResponse(); value.seasons[1].year = 2026; validateCareerSeasons(value); });
accepts('stored transition payload keeps its original source revision and UUID', () => validateCareerTransitionRequest({ schemaVersion: 'CAREER_SEASON_TRANSITION_REQUEST_V1', sourceYear: 2027, expectedCalendarRevision: 0, clientCommandId: '11111111-1111-4111-8111-111111111111' }));
rejects('historical detail cannot advertise mutable current-season state', () => validateCareerSeasonDetail({ schemaVersion: 'CAREER_SEASON_DETAIL_V1', careerId, season: seasonsResponse().seasons[1], readOnly: false, domestic: null, international: [], fixtures: [] }));
accepts('V2 selection authority is additive to preserved V1', () => { const value = internationalCalendar(); Object.assign(value.competition.internationalCompetitions[0], { ruleVersion: 'career-international-rules-v2', policyVersion: 'CAREER_INTERNATIONAL_GAME_POLICY_V2' }); validateCareerCalendar(value); });

accepts('a prior-season competition operation never reuses its UUID for a new season', () => {
  const storage = { value: null, getItem() { return this.value; }, setItem(_key, value) { this.value = value; }, removeItem() { this.value = null; } };
  const old = logicalCareerCompetition(storage, careerId, 0, () => '11111111-1111-4111-8111-111111111111', 2027);
  const next = logicalCareerCompetition(storage, careerId, 0, () => '22222222-2222-4222-8222-222222222222', 2028);
  if (old.clientCommandId === next.clientCommandId || next.sourceYear !== 2028) throw new Error('source year was overwritten');
});

accepts('a delayed season transition excludes calendar, fixture and roster mutations synchronously', () => {
  const states=[]; const gate=new CareerMutationGate(value=>states.push(value));
  const finish=gate.acquire();
  for(const command of ['advance','competition','roster']) if(gate.acquire()!==null) throw new Error(command+' entered during transition');
  finish(); if(gate.busy || String(states)!=='true,false') throw new Error('pending leaked');
});
accepts('ending an old request cannot release a newer mutation owner', () => {
  const gate=new CareerMutationGate(()=>{}); const old=gate.acquire(); old();
  const next=gate.acquire(); old(); if(!gate.busy)throw new Error('new owner released'); next();
});

// One representative five-role fixture validates directory/membership boundaries without mirroring 460 people.
function rosterView() {
  const ids = ['top','jungle','mid','adc','support'].map(x => `player-${x}`), roles = ['TOP','JUNGLE','MID','ADC','SUPPORT'];
  const players = Object.fromEntries(ids.map((id,i) => [id, { playerId:id, nickname:id, position:roles[i], provisional:true, initialOrganizationId:'LCK:KT', initialOwnerTeam:'LCK:KT', initialSquad:'FIRST_TEAM', eligibilityReason:null, detailsJson:'{"personal":{"birthDate":null}}', gameplay:{playerId:id,nickname:id,position:roles[i],ratings:Object.fromEntries(Array.from({length:12},(_,i)=>[`SKILL_${i}`,14])),proficiencies:[{championId:'fixture-champion',position:roles[i],value:14}]} }]));
  return { schemaVersion:'CAREER_ROSTER_VIEW_V1',careerId,seasonYear:2027,revision:0,readOnly:false,managedTeam:'LCK:KT',directory:{players,organizations:{'LCK:KT':{organizationId:'LCK:KT',displayName:'KT',competitiveTeam:'LCK:KT',kind:'CLUB'}}},state:{policyVersion:'CAREER_ROSTER_LINEUP_V1',members:Object.fromEntries(ids.map(id=>[id,{playerId:id,ownerTeam:'LCK:KT',organizationId:'LCK:KT',squad:'FIRST_TEAM',eligibilityReason:null}])),lineups:{'LCK:KT':ids}},registeredPlayers:{},activeSeriesPlayers:{},allowedCommands:['SELECT_STARTER','MOVE_SQUAD'],applicationPolicy:'UNSTARTED_SERIES_WITHIN_REGISTERED_POOL_ELSE_NEXT_REGISTRATION'};
}
accepts('roster directory retains provisional values and unknown personal data', () => validateCareerRoster(rosterView()));
rejects('a lineup cannot duplicate a player in two roles', () => { const value=rosterView();value.state.lineups['LCK:KT'][1]=value.state.lineups['LCK:KT'][0];validateCareerRoster(value); });
rejects('a historical roster cannot advertise mutation commands', () => { const value=rosterView();value.readOnly=true;validateCareerRoster(value); });
rejects('a registered pool cannot refer to an absent player', () => { const value=rosterView();value.registeredPlayers.MSI=['player-missing'];validateCareerRoster(value); });
accepts('a lost roster response restores its original year, revision, UUID and target', () => { const local=storage();const body={schemaVersion:'CAREER_ROSTER_COMMAND_V1',sourceYear:2027,team:'LCK:KT',playerId:'player-jiwoo',action:'SELECT_STARTER',targetOrganizationId:null,replacementPlayerId:null,expectedRevision:4,clientCommandId:'11111111-1111-4111-8111-111111111111'};local.setItem(rosterOperationKey(careerId),JSON.stringify(body));if(JSON.stringify(readRosterOperation(local,careerId))!==JSON.stringify(body))throw new Error('original roster command was replaced'); });

// Regression of the reported response-order race, exercising the production component with bounded hooks.
async function verifySeasonComponentRequestOwnership() {
  const fs=await import('node:fs'),vm=await import('node:vm'),ts=(await import('typescript')).default;
  const file=new URL('../src/features/career/CareerSeasonsPanel.tsx',import.meta.url);
const state=[],refs=[],effects=[];let si=0,ri=0,ei=0,todo=[];
const react={
 useState(initial){const i=si++;if(!(i in state))state[i]=initial;return[state[i],v=>{state[i]=typeof v==='function'?v(state[i]):v}]},
 useRef(initial){const i=ri++;return refs[i]??=( {current:initial})},
 useEffect(fn,deps){const i=ei++,old=effects[i];if(!old||deps.some((v,j)=>v!==old.deps[j]))todo.push(()=>{old?.cleanup?.();effects[i]={deps,cleanup:fn()}})}
};
const jsx=(type,props)=>({type,props});
const storage=new Map();let finishTransition;let starts=0,changed=0,released=0;
const list={schemaVersion:'CAREER_SEASONS_V1',careerId:'career',activeYear:2027,calendarRevision:10,seasons:[{year:2027,status:'ACTIVE'}],blockers:[],allowedCommands:['START_NEXT_SEASON']};
const api={CareerApiFailure:class extends Error{},getCareerSeasons:async()=>({...list,calendarRevision:props.revision}),getCareerSeason:async()=>({}),transitionCareerSeason:async(_c,body)=>new Promise(resolve=>{starts++;finishTransition=()=>resolve({receipt:{clientCommandId:body.clientCommandId,sourceYear:body.sourceYear},seasons:list})})};
const source=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText;
const exportsObject={};
const sandbox={exports:exportsObject,require(name){if(name==='react')return react;if(name==='react/jsx-runtime')return{jsx,jsxs:jsx};if(name.endsWith('careerApi.client'))return api;if(name.endsWith('careerApi.validation'))return{validateCareerTransitionRequest:x=>x};if(name.endsWith('career.pointer'))return{clearCareerAdvanceOperation(){},clearCareerCompetitionOperation(){}};throw Error(name)},AbortController,crypto:{randomUUID:()=> '11111111-1111-4111-8111-111111111111'},window:{sessionStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}}};
vm.runInNewContext(source,sandbox,{filename:file.pathname});
let props={careerId:'career',revision:10,busy:false,onBegin(){return ()=>{released++}},onChanged(){changed++},onHistory(){},onReplay(){}};
function render(){si=ri=ei=0;todo=[];const tree=exportsObject.CareerSeasonsPanel(props);for(const f of todo)f();return tree}
function button(tree){return tree.props.children[0].props.children[1]}
const tick=()=>new Promise(r=>setImmediate(r));

 render();await tick();let tree=render();button(tree).props.onClick();tree=render();
 const pendingAfterStart=tree.props['aria-busy'];
 // An independently enabled Calendar advance updates the revision while the transition response is delayed.
 props={...props,revision:11,busy:true};render();await tick();finishTransition();await tick();
 props={...props,busy:false};tree=render();
 const result={transitionRequests:starts,pendingAfterStart,pendingAfterResponse:tree.props['aria-busy'],transitionButtonDisabled:button(tree).props.disabled,onChangedCalls:changed,persistedRequestCount:storage.size};

 if(!pendingAfterStart||result.pendingAfterResponse||result.onChangedCalls!==1||storage.size!==0||released!==1)throw Error('Transition ownership leaked after read revision changed');


}
try { await verifySeasonComponentRequestOwnership(); console.log('PASS season transition completes and releases pending after query revision changes'); } catch(error) { console.error('FAIL season transition component ownership',error);process.exitCode=1; }

// Market DTOs and original-command recovery; gameplay choices are verified in the backend.
function marketView() {
  return { schemaVersion:'CAREER_MARKET_VIEW_V1',policyVersion:'CAREER_CONTRACT_MARKET_GAME_POLICY_V1',currency:'GAME_CREDITS',careerId,seasonYear:2027,currentDate:'2027-01-01',revision:3,readOnly:false,managedTeam:'LCK:KT',offseason:false,nextMarketEvent:'2027-01-04',players:[{playerId:'player-bo',status:'FREE_AGENT',currentContractId:null,scheduledContractId:null,availableStart:'2027-01-06',askingSalary:180000,releaseCost:0,eligibilityReason:null,preference:{compensation:30,opportunity:35,strength:20,stability:10,familiarity:5,relocationPenalty:5,inclination:'COMPARE_OFFERS',homeRegion:null}}],contracts:[],offers:[],finances:[{team:'LCK:KT',annualBudget:1500000,cash:3000000,reservedCash:20000,currentAnnualSalary:1000000,committedPeakSalary:1200000,rosterLimit:10}],decisions:[],events:[],ledger:[],missingPositions:{'LCK:KT':['JUNGLE']},supplements:[],allowedCommands:['SUBMIT'],registrationPolicy:'등록 집합과 현재 계약을 별도로 확인합니다.' };
}
function marketBody() { return {schemaVersion:'CAREER_MARKET_COMMAND_V1',sourceYear:2027,expectedRevision:3,action:'SUBMIT',playerId:'player-bo',offerId:null,terms:{startDate:'2027-01-06',endDate:'2029-01-05',annualSalary:180000,signingBonus:20000,role:'STARTER'},replacementPlayerId:null,competitionId:null,clientCommandId:'11111111-1111-4111-8111-111111111111'}; }
accepts('market shows game FA eligibility, budget reservation and a repairable lineup gap',()=>validateCareerMarket(marketView()));
accepts('ambiguous market transport restores the original year revision UUID and terms',()=>{const local=storage(),body=marketBody();local.setItem(marketOperationKey(careerId),JSON.stringify(body));if(JSON.stringify(readMarketOperation(local,careerId))!==JSON.stringify(body)||readMarketOperation(local,secondCareerId)!==null)throw Error('market request scope lost');});
rejects('market cannot display reserved money beyond available cash',()=>{const value=marketView();value.finances[0].reservedCash=value.finances[0].cash+1;validateCareerMarket(value);});
rejects('market command rejects an implicit revision or a second unrelated payload field',()=>validateMarketCommand({...marketBody(),expectedRevision:1.5,forceAccept:true}));
rejects('a market receipt from another Career cannot complete the displayed operation',()=>validateMarketChange({replayed:true,receipt:{clientCommandId:marketBody().clientCommandId,careerId:secondCareerId,sourceYear:2027,resultingRevision:3,action:'SUBMIT',referenceId:'offer',appliedDate:'2027-01-01',reason:'확인'},market:marketView()}));
rejects('historical contract view cannot advertise operations',()=>validateCareerMarket({...marketView(),readOnly:true}));
accepts('only the explicitly versioned operating roster permits a missing starter',()=>{const old=rosterView();old.state.lineups['LCK:KT'].pop();let rejected=false;try{validateCareerRoster(old)}catch{rejected=true}if(!rejected)throw Error('V1 meaning changed');old.schemaVersion='CAREER_ROSTER_VIEW_V2';old.state.policyVersion='CAREER_OPERATING_ROSTER_V2';validateCareerRoster(old);});
accepts('a competing decision references actual offers and keeps its recorded explanation',()=>{const value=marketView(),terms=marketBody().terms;value.offers=['LCK:KT','LPL:BLG'].map((team,i)=>({offerId:`offer-${i}`,playerId:'player-bo',team,terms,submittedDate:'2027-01-01',responseDate:'2027-01-03',decisionDate:'2027-01-06',expiresDate:'2027-01-07',revision:1,status:i?'REJECTED':'ACCEPTED',previousOfferId:null,round:1,requestedSalary:null,reason:i?'다른 실제 제안 선택':'선택'}));value.decisions=[{eventId:'decision',playerId:'player-bo',date:'2027-01-06',winningOfferId:'offer-0',evaluations:value.offers.map(o=>({offerId:o.offerId,team:o.team,compensation:80,opportunity:60,strength:85,stability:66,familiarity:0,relocation:5,score:7000,tieBreak:1,reason:'보수와 출전 기회를 비교'})),reason:'실제 두 제안을 비교해 선택',policyVersion:value.policyVersion}];validateCareerMarket(value);});
accepts('stove and roster repair retain server-authorized market date progression before year end',()=>{
  for(const [lifecycleStatus,blockingReason] of [['SEASON_ROLLOVER_REQUIRED','SEASON_ROLLOVER_REQUIRED'],['ACTIVE','ROSTER_REPAIR_REQUIRED']]) {
    const value=hardenedCalendarView();value.lifecycleStatus=lifecycleStatus;value.blockingReason=blockingReason;validateCareerCalendar(value);
    value.activePendingAdvance={clientCommandId:'11111111-1111-4111-8111-111111111111',mode:'ADVANCE_ONE_DAY',expectedCalendarRevision:0,commandStatus:'PENDING',createdAt:'2026-08-24T00:00:00Z',updatedAt:'2026-08-24T00:00:00Z'};let rejected=false;try{validateCareerCalendar(value)}catch{rejected=true}if(!rejected)throw Error('pending command bypassed');
  }
});

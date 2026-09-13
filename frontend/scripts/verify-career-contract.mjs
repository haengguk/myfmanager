import assert from 'node:assert/strict';
import { newerMarket, validateCareerMarket, validateMarketCommand, validateMarketChange, marketOperationKey, readMarketOperation } from '../src/features/career/api/careerMarket.contract.ts';
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
const squadOperation = {id:'plan',seasonYear:2027,date:'2027-01-11',team:'LCK:BRO',position:'TOP',squad:'FIRST_TEAM',action:'PROMOTE',status:'APPLIED',playerId:'player-bo',previousPlayerId:null,effectiveDate:'2027-01-11',referenceId:null,reason:'현재 기량 차이'};
accepts('public squad operation distinguishes applied movement from private planning',()=>validateCareerMarket({...marketView(),squadOperations:[squadOperation,{...squadOperation,id:'pending',status:'PROPOSED',playerId:null,effectiveDate:null}]}));
rejects('squad operation cannot expose an internal candidate score',()=>validateCareerMarket({...marketView(),squadOperations:[{...squadOperation,buyerLimit:10000}]}));
rejects('duplicate squad operation identities are rejected',()=>validateCareerMarket({...marketView(),squadOperations:[squadOperation,squadOperation]}));
accepts('market shows game FA eligibility, budget reservation and a repairable lineup gap',()=>validateCareerMarket(marketView()));
const negotiationPrice = {policyVersion:'CAREER_NEGOTIATION_PRICE_V2',pricedOn:'2027-01-01',region:'LCP',marketBasis:'STRUCTURED_ORIGIN',strength:180,abilityBand:'STARTER',annualDemand:84000000,referenceHash:'a'.repeat(64),fxPolicyVersion:'GAME_FIXED_FX_V1'};
accepts('new public asking price carries the V2 basis alongside an unchanged existing contract',()=>{const v=marketView();v.players[0].pricing=negotiationPrice;validateCareerMarket(v);});
rejects('unsupported pricing policy cannot silently label an old negotiation as V2',()=>{const v=marketView();v.players[0].pricing={...negotiationPrice,policyVersion:'UNKNOWN'};validateCareerMarket(v);});
rejects('invalid public demand is rejected before rendering a quote',()=>{const v=marketView();v.players[0].pricing={...negotiationPrice,annualDemand:-1};validateCareerMarket(v);});
accepts('ambiguous market transport restores the original year revision UUID and terms',()=>{const local=storage(),body=marketBody();local.setItem(marketOperationKey(careerId),JSON.stringify(body));if(JSON.stringify(readMarketOperation(local,careerId))!==JSON.stringify(body)||readMarketOperation(local,secondCareerId)!==null)throw Error('market request scope lost');});
rejects('market cannot display reserved money beyond available cash',()=>{const value=marketView();value.finances[0].reservedCash=value.finances[0].cash+1;validateCareerMarket(value);});
accepts('payroll recovery is visible without pretending unpaid obligations are available cash',()=>{const v=marketView();Object.assign(v.finances[0],{fundingPolicy:'PAYROLL_CASH_FLOW_AND_ARREARS_RECOVERY_V2',cash:0,reservedCash:20000,salaryArrears:45000,paymentHeadroom:0});validateCareerMarket(v);});
rejects('negative unpaid wages cannot be displayed as a recovered balance',()=>{const v=marketView();Object.assign(v.finances[0],{fundingPolicy:'PAYROLL_CASH_FLOW_AND_ARREARS_RECOVERY_V2',salaryArrears:-1,paymentHeadroom:0});validateCareerMarket(v);});
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

function managementView() { return { policyVersion:'CAREER_PROMISE_TRANSFER_LOAN_V1',observationStarted:'2027-01-01',promises:[{promiseId:'promise',playerId:'player-bo',team:'LCK:KT',contractId:'contract',loanId:null,role:'STARTER',startDate:'2027-01-01',endDate:'2028-12-31',observationStart:'2027-01-01',lastEvaluation:'2027-01-01',opportunities:0,starts:0,sets:0,satisfaction:60,trust:50,status:'OBSERVATION_PENDING',reason:'관찰 유예',policyVersion:'CAREER_PROMISE_TRANSFER_LOAN_V1'}],trades:[],loans:[],appearances:[],quotes:[]}; }
accepts('old saves can show neutral promises without invented appearances',()=>validateCareerMarket({...marketView(),management:managementView()}));
rejects('satisfaction cannot exceed its explicit range',()=>{const m=managementView();m.promises[0].satisfaction=101;validateCareerMarket({...marketView(),management:m});});
rejects('selected Series cannot exceed evaluation opportunities',()=>{const m=managementView();m.promises[0].starts=1;validateCareerMarket({...marketView(),management:m});});
import {validateTradeCommand,readTradeOperation,tradeOperationKey} from '../src/features/career/api/careerManagement.contract.ts';
function tradeBody() { const playerTerms={startDate:'2027-01-09',endDate:'2027-02-05',annualSalary:180000,signingBonus:0,role:'RESERVE'};return {schemaVersion:'CAREER_TRADE_COMMAND_V1',sourceYear:2027,expectedRevision:3,action:'SUBMIT',tradeId:null,terms:{kind:'LOAN',playerId:'player-jiwoo',seller:'LCK:KT',buyer:'LCK:T1',startDate:playerTerms.startDate,endDate:playerTerms.endDate,fee:25000,borrowerSalaryPercent:50,playerTerms,replacementPlayerId:null},replacementPlayerId:null,clientCommandId:marketBody().clientCommandId}; }
accepts('loan proposal preserves original pay and separates its two clubs',()=>validateTradeCommand(tradeBody()));
rejects('loan share cannot create more than one full salary obligation',()=>{const t=tradeBody();t.terms.borrowerSalaryPercent=101;validateTradeCommand(t);});
rejects('a club cannot loan a player to itself',()=>{const t=tradeBody();t.terms.buyer=t.terms.seller;validateTradeCommand(t);});
accepts('ambiguous trade response keeps the original UUID and source year',()=>{const body=tradeBody();const restored=readTradeOperation({getItem:key=>key===tradeOperationKey(careerId)?JSON.stringify(body):null},careerId);if(JSON.stringify(restored)!==JSON.stringify(body))throw new Error("original trade request changed");});
rejects('club approval alone cannot advertise a completed transfer',()=>{const m=managementView();m.trades=[{tradeId:'trade',terms:tradeBody().terms,status:'COMPLETED',sellerAgreed:true,buyerAgreed:false}];validateCareerMarket({...marketView(),management:m});});

import { currentAbility, potentialAbility } from '../src/features/player-data/playerAbility.ts';
accepts('CA maps twelve equal weights to 1..200 and rounds only once', () => {
  const ratings = Object.fromEntries(Array.from({ length: 12 }, (_, i) => [`skill${i}`, 1]));
  if (currentAbility(ratings) !== 1 || currentAbility(Object.fromEntries(Object.keys(ratings).map(k => [k, 20]))) !== 200) throw Error('CA endpoints');
  const zeus = [20,18,18,18,20,18,18,20,19,19,18,19], showmaker = [19,19,19,19,19,18,18,18,19,18,19,17];
  for (const [values, expected] of [[zeus,187],[showmaker,184]]) if (currentAbility(Object.fromEntries(values.map((v,i)=>[`skill${i}`,v]))) !== expected) throw Error('rounding');
});
rejects('CA cannot conceal a missing or fractional rating', () => currentAbility({ mechanics: 19.5 }));
accepts('PA preserves authored values below CA and old saves remain unassigned', () => {
  if (potentialAbility({ detailsJson: '{}' }) !== null || potentialAbility({ detailsJson: JSON.stringify({ abilityMetadata: { potentialAbility: 1 } }) }) !== 1) throw Error('PA was inferred or raised');
});

import {validateTrainingCommand,validateCareerDevelopment,validateTrainingChange,readTrainingOperation,trainingOperationKey} from '../src/features/career/api/careerDevelopment.contract.ts';
const trainingBody=()=>({schemaVersion:'CAREER_TRAINING_COMMAND_V1',sourceYear:2027,expectedRevision:2,playerId:'player-test',plan:{intensity:'NORMAL',focus:'CHAMPION_FOCUS',skill:null,champions:['aatrox','garen']},clearOverride:false,clientCommandId:marketBody().clientCommandId});
const developmentView=()=>({schemaVersion:'CAREER_DEVELOPMENT_VIEW_V1',careerId,seasonYear:2027,revision:3,currentDate:'2027-01-01',managedTeam:'LCK:T1',readOnly:false,teamPlan:null,players:[{playerId:'player-test',currentAbility:148,potentialAbility:null,growthStatus:'PA_MISSING',trainingEfficiency:1000,effectivePlan:trainingBody().plan,development:{internalRatings:Object.fromEntries(Array.from({length:12},(_,i)=>['skill'+i,15000+i])),internalProficiencies:{'aatrox|TOP':14040},fatigue:90,override:null}}],recentChanges:[],monthlySummaries:[],legalChampions:{TOP:['aatrox','garen']},policyVersion:'CAREER_DEVELOPMENT_FIXED_POINT_V1'});
accepts('training persists two targets as one original command',()=>validateTrainingCommand(trainingBody()));
rejects('duplicate champion targets cannot multiply the training budget',()=>{const b=trainingBody();b.plan.champions=['aatrox','aatrox'];validateTrainingCommand(b);});
rejects('team training cannot apply one position-specific champion plan to all roles',()=>validateTrainingCommand({...trainingBody(),playerId:null}));
accepts('override removal retains explicit player scope and no replacement plan',()=>validateTrainingCommand({...trainingBody(),plan:null,clearOverride:true}));
accepts('fractional progress and missing PA remain visible without a fabricated integer rise',()=>{const v=validateCareerDevelopment(developmentView());if(Math.floor(v.players[0].development.internalRatings.skill11/1000)!==15||v.players[0].potentialAbility!==null)throw Error('projection');});
rejects('out-of-range fatigue cannot become a hidden condition modifier',()=>{const v=developmentView();v.players[0].development.fatigue=1001;validateCareerDevelopment(v);});
accepts('training response loss restores the original UUID payload after refresh',()=>{const b=trainingBody();if(JSON.stringify(readTrainingOperation({getItem:k=>k===trainingOperationKey(careerId)?JSON.stringify(b):null},careerId))!==JSON.stringify(b))throw Error('recovery');});
rejects('old Career training receipt cannot overwrite the newly selected Career',()=>validateTrainingChange({replayed:true,receipt:{careerId:secondCareerId,sourceYear:2027,resultingRevision:3,effectiveOn:'2027-01-02',stateHash:'a'.repeat(64),clientCommandId:trainingBody().clientCommandId},development:developmentView()}));

import {validateCareerLifecycle,filterLifecycle} from '../src/features/career/api/careerLifecycle.contract.ts';
const lifecycleView=()=>({schemaVersion:'CAREER_LIFECYCLE_VIEW_V1',careerId,seasonYear:2027,date:'2027-10-31',readOnly:false,activeCount:1,archivedCount:0,policyVersion:'CAREER_PLAYER_LIFECYCLE_RETIREMENT_AND_ROOKIE_SUPPLY_V1',reviews:[],players:[{playerId:'player-new',nickname:'게임 신인',position:'TOP',currentCA:155,potentialAbility:181,gameAge:18,seasonGrowth:600,seasonDecline:0,seasonNet:600,lifecycle:{source:'GENERATED',intakeYear:2028,status:'ACTIVE',peakCA:155,peakObservedSince:'2027-10-31',declineRemainder:600,age:{publicBirthDate:null,simulationBirthDate:'2009-05-10'}}}]});
accepts('generated age and fractional growth remain explicit Career game data',()=>validateCareerLifecycle(lifecycleView()));
accepts('rookie and retired filters use structured lifecycle identity',()=>{const v=lifecycleView();if(filterLifecycle(v.players,'rookies').length!==1||filterLifecycle(v.players,'retired').length!==0)throw Error('filter');});
rejects('retirement cannot take effect before its announcement',()=>{const v=lifecycleView();Object.assign(v.players[0].lifecycle,{status:'RETIREMENT_ANNOUNCED',announcedOn:'2028-01-05',effectiveOn:'2028-01-01'});validateCareerLifecycle(v);});
rejects('sub-point decline cannot masquerade as a second whole-point loss',()=>{const v=lifecycleView();v.players[0].lifecycle.declineRemainder=1000;validateCareerLifecycle(v);});
rejects('season net change must reconcile growth and aging',()=>{const v=lifecycleView();v.players[0].seasonNet=-1000;validateCareerLifecycle(v);});
accepts('retired profile remains readable with the original effective date',()=>{const v=lifecycleView();v.activeCount=0;v.archivedCount=1;Object.assign(v.players[0].lifecycle,{status:'RETIRED',announcedOn:'2027-10-31',effectiveOn:'2028-01-01'});v.date='2028-01-01';v.readOnly=true;validateCareerLifecycle(v);});
rejects('zero observed opportunities cannot claim a starter appearance',()=>{const v=lifecycleView();v.reviews=[{seasonYear:2027,reviewedOn:'2027-10-31',rookieIds:[],supply:null,changes:[{playerId:'player-new',outcome:'ACTIVE',retirementProbability:0,retirementRoll:50,observation:{coverage:'INSUFFICIENT_OPPORTUNITIES',opportunities:0,starts:1,sets:0}}]}];validateCareerLifecycle(v);});

import { validateClCommand, validateCareerCl, validateClChange, validatePerformances, clOperationKey } from '../src/features/career/api/careerCl.contract.ts';
const clCommand = () => ({ sourceYear: 2027, expectedRevision: 2, expectedRosterRevision: 3, action: 'CONFIRM_LINEUP', players: ['top', 'jg', 'mid', 'adc', 'sup'], matchId: null, clientCommandId: '10000000-0000-4000-8000-000000000001' });
accepts('CL request preserves the original UUID and both selection revisions', () => { const s = storage(), r = clCommand(); s.setItem(clOperationKey(careerId), JSON.stringify(r)); const restored = validateClCommand(JSON.parse(s.getItem(clOperationKey(careerId)))); if (JSON.stringify(restored) !== JSON.stringify(r) || clOperationKey(careerId) === clOperationKey(secondCareerId)) throw Error('CL scope changed'); });
rejects('CL five cannot repeat a player', () => { const r = clCommand(); r.players[4] = r.players[0]; validateClCommand(r); });
rejects('CL selection revision cannot be coerced from text', () => { const r = clCommand(); r.expectedRosterRevision = '3'; validateClCommand(r); });
accepts('explicit CL Auto selection leaves the lineup out of the execution command', () => validateClCommand({ ...clCommand(), action: 'SELECT_AUTO', players: null, matchId: 'CL_R01_M1' }));
accepts('legacy Career exposes the future CL activation season without fabricated fixtures', () => validateCareerCl({ careerId, seasonYear: 2027, activationYear: 2028, active: false, readOnly: false, revision: 0, rosterRevision: 3, managedTeam: 'LCK:T1', clubs: [], fixtures: [], standings: [], ranking: [] }));
rejects('a CL receipt from another Career cannot replace the selected Career', () => validateClChange({ replayed: true, receipt: { careerId: secondCareerId, seasonYear: 2027, revision: 1, clientCommandId: clCommand().clientCommandId }, cl: { careerId, seasonYear: 2027, activationYear: 2028, active: false, readOnly: false, revision: 0, rosterRevision: 3, managedTeam: 'LCK:T1', clubs: [], fixtures: [], standings: [], ranking: [] } }));
accepts('CL completed sets and fractional growth remain scoped to the operating team', () => validatePerformances([{ seasonYear: 2027, date: '2027-04-05', seriesId, playerId: 'player-dal', team: 'LCK:T1', squad: 'DEVELOPMENT', competitionId: 'LCK_CL', sets: 2, champions: { Ornn: 1, Gnar: 1 }, internalGain: 53, proficiencyGain: 96 }]));
accepts('Calendar recognizes CL while preserving existing competition fields', () => { const v = hardenedCalendarView(); v.competition.nextFixture.competitionId = 'LCK_CL'; validateCareerCalendar(v); });

accepts('KRW commands keep large exact amounts and older credit responses cannot replace current finance', () => {
  const old = marketView();
  const next = { ...old, currency: 'KRW', revision: old.revision + 1 };
  assert.equal(newerMarket(next, old), next);
  const command = { ...marketBody(), schemaVersion: 'CAREER_MARKET_COMMAND_KRW_V1', terms: { ...marketBody().terms, annualSalary: 2_700_000_000, signingBonus: 100_000_000 } };
  assert.deepEqual(validateMarketCommand(command), command);
  const trade = { ...tradeBody(), schemaVersion: 'CAREER_TRADE_COMMAND_KRW_V1', terms: { ...tradeBody().terms, fee: 4_000_000_000 } };
  assert.deepEqual(validateTradeCommand(trade), trade);
  assert.throws(() => validateCareerMarket(next)); // KRW requires a matching finance policy snapshot.
});

import { validateOverseas } from '../src/features/career/api/careerOverseas.contract.ts';
const overseasView = () => ({careerId,seasonYear:2027,league:'LEC',active:true,readOnly:false,activation:{introducedYear:2027,activationYear:2027,extensionApplied:true},scheduleExplanation:'참고 규칙과 게임 보완 정책',events:[{eventId:'LEC_VERSUS',name:'LEC Versus',league:'LEC',status:'RUNNING',waitingReason:null,result:null,fixtures:[{competitionId:'LEC_VERSUS',matchId:'RR_ALL_1_1_1',seriesId,date:'2027-01-17',stageId:'REGULAR',seriesFormat:'BO1',firstTeamCode:'LEC:KC',secondTeamCode:'LEC:KCB',lifecycleStatus:'COMPLETED',executionMode:'FULL_AUTO',winnerTeamCode:'LEC:KCB'}],scores:{RR_ALL_1_1_1:{first:0,second:1}},standings:{},championshipPoints:{},qualifications:{}}]});
accepts('overseas event score and a distinct guest team survive reload',()=>{const v=overseasView();assert.deepEqual(validateOverseas(JSON.parse(JSON.stringify(v))),v);});
rejects('overseas event from another league cannot replace the selected event',()=>{const v=overseasView();v.events[0].eventId='LPL_SPLIT_1';validateOverseas(v);});
rejects('overseas scores require a completed fixture with the matching BO length',()=>{const v=overseasView();v.events[0].scores.RR_ALL_1_1_1.second=2;validateOverseas(v);});
rejects('overseas duplicate fixtures cannot inflate progress',()=>{const v=overseasView();v.events[0].fixtures.push({...v.events[0].fixtures[0]});validateOverseas(v);});
accepts('existing saves expose next whole season activation without invented results',()=>{const v=overseasView();v.active=false;v.activation.activationYear=2028;v.events=[];validateOverseas(v);});
accepts('Calendar accepts a foreign Auto fixture through the existing command contract',()=>{const v=hardenedCalendarView();v.competition.nextFixture.competitionId='LEC_VERSUS';v.competition.nextFixture.firstTeamCode='LEC:KC';v.competition.nextFixture.secondTeamCode='LEC:KCB';v.competition.nextFixture.executionMode='FULL_AUTO';v.competition.nextFixture.managedTeamIncluded=false;validateCareerCalendar(v);});
accepts('unexecuted legacy contract and trade keep their original revisions and amounts in storage',()=>{for(const [key,body,validate] of [[marketOperationKey(careerId),marketBody(),validateMarketCommand],[tradeOperationKey(careerId),tradeBody(),validateTradeCommand]]){const store=storage(),original=JSON.stringify(body);store.setItem(key,original);assert.deepEqual(validate(JSON.parse(store.getItem(key))),body);store.setItem(key+':legacy-review',store.getItem(key));store.removeItem(key);assert.equal(store.getItem(key+':legacy-review'),original);assert.equal(store.getItem(key),null);}});

const supportedSaved = { policyVersion: 'CAREER_SAVED_PLAYER_DIRECTORY_V1', dataSource: 'SAVED_CAREER', sourceChanged: true, managedTeamCode: 'GEN', managedTeamName: 'Gen.G', directoryVersion: 'EXPANDED_PLAYER_DIRECTORY_V1', status: 'SUPPORTED', reasonCode: null, message: null };
accepts('saved player authority survives changed source provenance', () => validateCareerView({ ...view(), compatibility: supportedSaved }));
accepts('unsupported save remains visible beside supported save', () => validateCareerListResponse({ schemaVersion: 'CAREER_LIST_V1', careers: [{ ...summary(), compatibility: supportedSaved }, { ...summary(secondCareerId), compatibility: { ...supportedSaved, status: 'UNSUPPORTED', dataSource: 'UNAVAILABLE', sourceChanged: false, directoryVersion: null, reasonCode: 'SAVE_COMPATIBILITY_VERSION_UNSUPPORTED', message: '지원하지 않는 저장 형식입니다.' } }], currentCount: 2, maximumCount: 100, remainingCount: 98 }));
rejects('compatibility cannot link a different managed team', () => validateCareerView({ ...view(), compatibility: { ...supportedSaved, managedTeamCode: 'T1' } }));
rejects('unsupported save cannot be opened as a valid detail', () => validateCareerView({ ...view(), compatibility: { ...supportedSaved, status: 'UNSUPPORTED', reasonCode: 'SAVE_COMPATIBILITY_DATA_MISSING', message: '원본 명부가 필요합니다.' } }));
function registrationRepairCalendar() { const c = hardenedCalendarView(); c.competition.nextCompetition.registrationWait = { code: 'ROSTER_REPAIR_REQUIRED', competitionId: 'LCK_CUP', requiredEventId: null, teamId: 'LPL:AL', ownerTeam: 'LPL:AL', responsibility: 'AI_CLUB', missingPositions: ['TOP'], obstacles: ['CASH_HEADROOM_SHORTFALL'] }; return c; }
accepts('structured registration repair includes owner and budget obstacle', () => validateCareerCalendar(registrationRepairCalendar(), careerId));
rejects('registration repair context cannot target another competition', () => { const c=registrationRepairCalendar(); c.competition.nextCompetition.registrationWait.competitionId='MSI'; validateCareerCalendar(c,careerId); });

const { validateCareerContinuous, validateCareerContinuousResponse } = await import('../src/features/career/api/careerApi.validation.ts');
const continuous = { schemaVersion: 'CAREER_CONTINUOUS_VIEW_V1', careerId, currentDate: '2027-01-04', run: { runId: 'continuous_test', careerId, seasonYear: 2027, mode: 'TARGET_DATE', targetDate: '2027-01-05', startDate: '2027-01-04', status: 'RUNNING', revision: 0, completedDates: 0, completedSeries: 0, completedGames: 0, intent: null, stop: null }, allowedCommands: ['PAUSE'] };
assert.deepEqual(validateCareerContinuous(continuous), continuous);
for (const status of ['WAITING', 'PAUSE_REQUESTED', 'PAUSED', 'STOPPED', 'COMPLETED', 'FAILED']) { const c = clone(continuous); c.run.status = status; validateCareerContinuous(c); }
for (const change of [v => { v.run.careerId = secondCareerId; }, v => { v.run.completedGames = -1; }, v => { v.run.targetDate = '2027-02-30'; }, v => { v.run.status = 'ALMOST_DONE'; }, v => { v.run.intent = { action: 'PLAY_USER_MATCH', commandId: 'bad' }; }]) { const c = clone(continuous); change(c); assert.throws(() => validateCareerContinuous(c)); }
const continuousReply = { replayed: true, receipt: { clientCommandId: '00000000-0000-4000-8000-000000000099', runId: 'continuous_test', action: 'START', resultingRevision: 0, status: 'RUNNING' }, progress: continuous };
assert.deepEqual(validateCareerContinuousResponse(continuousReply), continuousReply);
console.log('Career continuous: compact status, malformed boundary and original command replay verified.');

// Exercise the real TSX accept/poll boundary; no React renderer or browser-sized harness needed.
{
  const { default: ts } = await import('typescript');
  const { readFileSync } = await import('node:fs');
  const { runInNewContext } = await import('node:vm');
  const code = ts.transpileModule(readFileSync(new URL('../src/features/career/CareerContinuousPanel.tsx', import.meta.url), 'utf8').replace(/\r\n/g, '\n'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  async function probe(status, stamp = null, visible = true, failSync = false) {
    const effects = [], busy = []; let timer, calls = 0, finish;
    let next = { careerId: 'probe', currentDate: '2027-03-25', run: { runId: 'run', revision: 2, status }, allowedCommands: [] };
    const context = { exports: {}, AbortController, window: { sessionStorage: { getItem: () => null }, setTimeout: f => { timer = f; return 1; }, clearTimeout() {} }, require: name => name === 'react' ? { useState: v => [v, () => {}], useRef: v => ({ current: v }), useEffect: f => effects.push(f) } : name === 'react/jsx-runtime' ? { jsx() {}, jsxs() {} } : { getCareerContinuous: async () => next, CareerApiFailure: class extends Error {} } };
    runInNewContext(code, context);
    context.exports.CareerContinuousPanel({ visible, careerId: 'probe', currentDate: '2027-03-25', seasonYear: 2027, busy: false, appliedRun: stamp, onBusy: (_, v) => busy.push(v), onStopped: () => { calls++; return new Promise(resolve => { finish = resolve; }); }, onBegin: () => () => {}, onAction() {} });
    effects.forEach(f => f()); assert.equal(busy.at(-1), true); await new Promise(setImmediate);
    if (status === 'RUNNING') { assert.equal(calls, 0); next = { ...next, run: { ...next.run, revision: 3, status: 'STOPPED' } }; const tick = timer; timer = undefined; tick(); await new Promise(setImmediate); }
    if (!stamp) { assert.equal(calls, 1); assert.equal(busy.at(-1), true); if (failSync) { finish(false); await new Promise(setImmediate); assert.equal(busy.at(-1), true); const tick = timer; timer = undefined; assert.equal(typeof tick, 'function'); tick(); await new Promise(setImmediate); assert.equal(calls, 2); } finish(true); await new Promise(setImmediate); assert.equal(busy.at(-1), false); }
    else { assert.equal(calls, 0); assert.equal(busy.at(-1), false); }
    assert.equal(timer, undefined, 'terminal synchronization stops periodic reads'); assert.equal(calls, stamp ? 0 : failSync ? 2 : 1);
  }
  await probe('STOPPED'); await probe('COMPLETED'); await probe('RUNNING'); await probe('STOPPED', 'run:2');
  console.log('PASS actual continuous component initial terminal, same-date, synchronization barrier, repeated response and remount');
  await probe('STOPPED', null, true, true); console.log('PASS terminal synchronization failure retains one retry then stops after successful refresh');
  await probe('RUNNING', null, false); console.log('PASS hidden Continuous controls retain the same running observer and terminal synchronization');
}

{
  const { default: ts } = await import('typescript');
  const { readFileSync } = await import('node:fs');
  const { runInNewContext } = await import('node:vm');
  const code = ts.transpileModule(readFileSync(new URL('../src/features/career/api/careerRecords.ts', import.meta.url), 'utf8').replace(/\r\n/g, '\n'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const context = { exports: {}, require: () => ({ realMatchConfig: { apiBaseUrl: '' } }) };
  runInNewContext(code, context);
  const { restoreRecordSelection, acceptRecordView, recordsSelectionKey } = context.exports;
  const selection = { kind: 'PLAYER', entity: 'retired-player', year: 2027, competition: '', organization: false };
  const view = { careerId, kind: 'PLAYER', entity: 'retired-player', seasonYear: 2027, organization: false, competition: '', asOf: 4, matches: [{ careerId, games: [] }], awards: [] };
  assert.equal(acceptRecordView(view, careerId, selection), view);
  for (const patch of [{ careerId: secondCareerId }, { entity: 'another-player' }, { seasonYear: 2028 }, { organization: true }, { matches: [{ careerId: secondCareerId }] }]) assert.throws(() => acceptRecordView({ ...view, ...patch }, careerId, selection));
  assert.equal(restoreRecordSelection(JSON.stringify(selection), 2029).year, 2027);
  assert.equal(restoreRecordSelection('{broken', 2029).year, 2029);
  assert.notEqual(recordsSelectionKey(careerId), recordsSelectionKey(secondCareerId));
  console.log('PASS records Career/entity/season/organization boundary, historical filter reload and malformed optional storage');
  const valid={status:'COMPLETE',combat:50,economy:50,survival:50,rating:50,kpStatus:'OBSERVED',version:'CAREER_PERFORMANCE_V2',support:{status:'OBSERVED',score:50,observation:{objectiveOpportunities:2,objectiveParticipations:1,objectiveWins:0,roamAttempts:0,roamWins:0}}};
  context.exports.validateRating(valid);context.exports.validateRating({...valid,version:'CAREER_PERFORMANCE_V1',support:undefined});
  for(const support of [{...valid.support,score:101},{...valid.support,status:'NOT_COLLECTED'},{...valid.support,observation:{...valid.support.observation,objectiveParticipations:3}}])assert.throws(()=>context.exports.validateRating({...valid,support}));
  console.log('PASS optional legacy ratings remain readable while invalid V2 support ranges and observation counts are rejected');

}

// Inbox: six groups, including the actual component's asynchronous boundaries.
{
  const { default: ts } = await import('typescript');
  const { readFileSync } = await import('node:fs');
  const { runInNewContext } = await import('node:vm');
  const compile = path => ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8').replace(/\r\n/g, '\n'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const api = { exports: {}, require: () => ({ realMatchConfig: { apiBaseUrl: '' } }), fetch: async (...args) => { api.calls.push(args); return { ok: true, json: async () => ({}) }; }, calls: [] };
  runInNewContext(compile('../src/features/career/api/careerInbox.ts'), api);
  const link = { panel: 'MARKET', playerId: 'player', sourceId: 'offer', seasonYear: 2027, competition: null, positions: [] };
  const decision = { id: 'OFFER:offer', revision: '1', status: 'OPEN', responsibility: 'MANAGER', link };
  const entry = { sequence: 5, read: false, item: { title: '역제안', kind: 'CONTRACT_RESPONSE', link, facts: {}, date: '2027-01-04' } };
  const feed = { careerId, seasonYear: 2027, kind: '', includeDevelopment: false, asOf: 5, nextCursor: -1, unread: 1, items: [entry], decisions: [decision] };
  assert.equal(api.exports.acceptInbox(feed, careerId, 2027, '', false), feed);
  for (const patch of [{ careerId: secondCareerId }, { seasonYear: 2028 }, { kind: 'AWARD' }, { includeDevelopment: true }, { asOf: 4 }]) assert.throws(() => api.exports.acceptInbox({ ...feed, ...patch }, careerId, 2027, '', false));
  console.log('PASS inbox scope and stable page upper bound');
  assert.equal(api.exports.currentDecision(decision, feed), decision);
  assert.equal(api.exports.currentDecision(decision, { ...feed, decisions: [{ ...decision, revision: '2' }] }), null);
  assert.equal(api.exports.currentDecision(decision, { ...feed, decisions: [] }), null);
  console.log('PASS changed counter revision and resolved source invalidate stale action');
  await api.exports.inboxRequest(careerId, '/read', new AbortController().signal, { through: 5, seasonYear: 2027, kind: '', includeDevelopment: false });
  const [, options] = api.calls.at(-1); assert.equal(options.method, 'POST'); assert.equal(JSON.parse(options.body).through, 5); assert.equal(options.body.includes('expectedRevision'), false);
  console.log('PASS read watermark metadata excludes gameplay revision and commands');
  async function componentProbe(mode) {
    const calls = [], effects = [], states = [], navigation = []; let hook = 0;
    const seed = [feed, null, '', '', false, 2027, { cursor: 0, asOf: null }, 0, false];
    const ctx = { exports: {}, AbortController, URLSearchParams, window: { setInterval() {}, clearInterval() {} }, require: name => {
      if (name === 'react') return { useState: value => { const i = hook++; return [i < seed.length ? seed[i] : typeof value === 'function' ? value() : value, next => states.push([i, next])]; }, useRef: value => ({ current: value }), useEffect: f => effects.push(f) };
      if (name === 'react/jsx-runtime') return { jsx: (type, props) => ({ type, props }), jsxs: (type, props) => ({ type, props }) };
      return { ...api.exports, inboxRequest: async (_, path, signal, body) => { calls.push({ path, body }); if (mode === 'late') await new Promise(resolve => { ctx.finish = resolve; }); return path.startsWith('?') ? mode === 'changed' ? { ...feed, decisions: [] } : feed : path === '/read' ? {} : entry; } };
    } };
    runInNewContext(compile('../src/features/career/CareerInboxPanel.tsx'), ctx);
    const tree = ctx.exports.CareerInboxPanel({ careerId, year: 2027, revision: 0, running: false, busy: false, onNavigate: l => navigation.push(l) });
    const nodes = []; function walk(n) { if (!n || typeof n !== 'object') return; if (Array.isArray(n)) return n.forEach(walk); nodes.push(n); walk(n.props?.children); } walk(tree);
    assert.equal(calls.length, 0);
    const button = nodes.find(n => n.type === 'button' && JSON.stringify(n.props?.children)?.includes(mode === 'changed' ? '해당 업무로 이동' : '새 소식'));
    assert.ok(button);
    if (mode === 'late') { const cleanup = effects[0](); await new Promise(setImmediate); ctx.finish(); await new Promise(setImmediate); button.props.onClick(); await new Promise(setImmediate); cleanup(); ctx.finish(); await new Promise(setImmediate); assert.equal(calls.some(c => c.path === '/read'), false); }
    else { button.props.onClick(); await new Promise(setImmediate); }
    return { calls, navigation, states };
  }
  const opened = await componentProbe('open'); assert.deepEqual(opened.calls.map(c => c.path), ['/5', '/read']); assert.equal(opened.calls[1].body.sequence, 5); assert.equal(opened.navigation.length, 0);
  console.log('PASS actual inbox detail marks only selected item and never executes work');
  const changed = await componentProbe('changed'); assert.equal(changed.navigation.length, 0); assert.equal(changed.calls.length, 1);
  console.log('PASS actual inbox navigation revalidates original target before opening');
  await componentProbe('late');
  console.log('PASS actual inbox invalidates late response on Career or filter cleanup');
}

// Scouting: seven scenario groups, exercising production callbacks and effects.
{
 const {default:ts}=await import('typescript'),{readFileSync}=await import('node:fs'),{runInNewContext}=await import('node:vm');
 const source=n=>readFileSync(new URL(`../src/features/career/${n}`,import.meta.url),'utf8');
 const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const nav={exports:{}};runInNewContext(compile(source('careerNavigation.ts')),nav);const {matchDestination,currentNavigation}=nav.exports;
 const match={panel:'MATCH',current:true,seasonYear:2028,sourceId:'fixture',competition:'LCK_CUP',seriesId:'reserved',matchState:'UNSTARTED'};
 assert.equal(matchDestination(match),'COMPETITION_PREPARATION');assert.equal(matchDestination({...match,matchState:'IN_PROGRESS'}),'SERIES');assert.equal(matchDestination({...match,competition:'LCK_REGULAR_R1_R2'}),'LEAGUE_PREPARATION');
 console.log('PASS reserved Series needs explicit started state; League retains preparation route');
 const callback=source('CareerDashboardPage.tsx').split('  const navigateCareer =')[1].split('\n  useEffect(')[0];
 async function probe(current=true,late=false,started=false){const events=[];let finish;const ctx={exports:{},detail:{careerId},mutationGate:{current:{busy:false}},navigationGeneration:{current:0},selectedIdRef:{current:careerId},calendar:{activeCalendarSeasonYear:2028},latestCareer:{current:{careerId}},setManaging(){},setPage(){},setScheduleTab(){},setTrainingTab(){},pageForLink:()=> 'schedule',currentNavigation,matchDestination,loadDetail:()=>{events.push('load');return new Promise(r=>{finish=r;});},setHistorical:v=>events.push(['historical',v]),setHistoryYear:v=>events.push(['year',v]),setInboxFocus:v=>events.push(['focus',v.sourceId]),setMarketPlayer:v=>events.push(['player',v]),onOpenCompetitionSeries:id=>events.push(['series',id]),onResume:()=>events.push('league')};runInNewContext(compile(`export const navigateCareer =${callback}`),ctx);ctx.exports.navigateCareer({...match,current,matchState:started?'IN_PROGRESS':'UNSTARTED',playerId:'candidate'});if(current){assert.deepEqual(events,['load']);if(late)++ctx.navigationGeneration.current;finish(true);await new Promise(setImmediate);}return events;}
 const opened=await probe();assert.deepEqual(opened.slice(1,4),[['historical',false],['year',null],['focus','fixture']]);assert.equal(opened.some(e=>e[0]==='series'),false);assert.deepEqual(await probe(true,true),['load']);assert.ok((await probe(true,false,true)).some(e=>e[0]==='series'));
 console.log('PASS actual Dashboard awaits active source then resets parent/focus; superseded target cannot apply');
 assert.equal((await probe(false)).some(e=>e==='load'),false); assert.ok((await probe(false)).some(e=>e[0]==='year'&&e[1]===2028));
 console.log('PASS historical news retains its scope without a season-transition command');
 const effect=source('CareerSeasonsPanel.tsx').split('\n').find(s=>s.includes('if (selectedYear == null'));
 const actions=[];runInNewContext(compile(effect),{selectedYear:null,historyYear:2027,request:{current:{abort:()=>actions.push('abort')}},generation:{current:0},useEffect:f=>f(),setHistoryYear:v=>actions.push(['year',v]),setDetail:v=>actions.push(['detail',v])});assert.deepEqual(actions,['abort',['year',null],['detail',null]]);
 console.log('PASS actual season selector follows active parent and cancels historical data');
 const api={exports:{},require:()=>({realMatchConfig:{apiBaseUrl:''}}),calls:[],fetch:async(...args)=>{api.calls.push(args);return {ok:true,json:async()=>({})};}};runInNewContext(compile(source('api/careerScouting.ts')),api);
 const player=(id,ratings,status='CONTRACTED',ownerTeam='LCK:T1')=>({player:{playerId:id,nickname:id,position:'MID',gameplay:{ratings,proficiencies:[]}},membership:{ownerTeam},status,interest:{revision:2,selected:true}});
 const a=player('a',{LANING:60,TEAMFIGHT:70}),b=player('b',{TEAMFIGHT:65,LANING:80});assert.deepEqual(Array.from(api.exports.comparisonSkills([a,b])),['LANING','TEAMFIGHT']);assert.deepEqual(Array.from(api.exports.comparisonSkills([a,player('j',{PATHING:80,TEAMFIGHT:55})])),['TEAMFIGHT']);const restored=api.exports.restoreScouting(JSON.stringify({filters:{position:'MID'},chosen:['a','a','b','c','d','e']}));assert.equal(restored.chosen.join(','),'a,b,c,d');assert.equal(restored.filters.position,'MID');assert.equal(api.exports.restoreScouting('{bad').chosen.length,0);
 console.log('PASS skill names retain meaning across key order and optional storage restores unique IDs');
 assert.equal(api.exports.scoutLink(a,2028,'LCK:GEN').panel,'TRADE');assert.equal(api.exports.scoutLink(player('own',{},'CONTRACTED','LCK:GEN'),2028,'LCK:GEN').panel,'MARKET');assert.equal(api.exports.scoutLink(player('fa',{},'FREE_AGENT',null),2028,'LCK:GEN').playerId,'fa');assert.equal(api.calls.length,0);await api.exports.scoutingRequest(careerId,'/interest',new AbortController().signal,{playerId:'a',expectedRevision:2,selected:false});assert.deepEqual(JSON.parse(api.calls[0][1].body),{playerId:'a',expectedRevision:2,selected:false});
 const transferEffect=source('CareerTransferPanel.tsx').match(/useEffect\(\(\) => \{\s*if \(!focus[\s\S]*?\}, \[focus, view.revision\]\);/)[0];let transferError=false;runInNewContext(compile(transferEffect),{useEffect:f=>f(),focus:{panel:'TRADE',sourceId:null,seasonYear:2028},view:{seasonYear:2028,revision:0},setError:()=>{transferError=true;}});assert.equal(transferError,false);
 console.log('PASS exact contract/transfer links perform no proposal; explicit interest request uses metadata revision');
 const effects=[],updates=[];let complete;const ctx={exports:{},AbortController,URLSearchParams,sessionStorage:{getItem:()=>null,setItem(){}},require:name=>{if(name==='react')return {useState:v=>[v,next=>updates.push(next)],useRef:v=>({current:v}),useEffect:f=>effects.push(f)};if(name==='react/jsx-runtime')return {jsx:()=>({}),jsxs:()=>({})};return {...api.exports,SKILL_LABELS:{},scoutingRequest:()=>new Promise(r=>{complete=r;})};}};runInNewContext(compile(source('CareerScoutingPanel.tsx')),ctx);ctx.exports.CareerScoutingPanel({careerId,year:2027,revision:0,busy:false,onNavigate(){}});const cleanup=effects[2]();cleanup();const count=updates.length;complete({careerId,players:[]});await new Promise(setImmediate);assert.equal(updates.length,count);
 const selectionSource=source('CareerScoutingPanel.tsx').split(' const cancelComparison=')[1].split(' async function interest')[0];let aborted=false,clearedPending=false,chosen=[];const selectionContext={exports:{},action:{current:{abort:()=>{aborted=true;}}},setPending:v=>{clearedPending=v===false;},setComparison(){},setChosen:f=>{chosen=f(['old']);}};runInNewContext(compile(`const cancelComparison=${selectionSource}; exports.select=select;`),selectionContext);selectionContext.exports.select('new');assert.ok(aborted&&clearedPending);assert.equal(chosen.join(','),'old,new');
 console.log('PASS actual scouting effect rejects late Career/filter responses');
}

// Preparation and executable calendar work have independent structured targets.
{
 const {default:ts}=await import('typescript'),{readFileSync}=await import('node:fs'),{runInNewContext}=await import('node:vm');
 const read=n=>readFileSync(new URL(`../src/features/career/${n}`,import.meta.url),'utf8');
 const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const jsx=(type,props)=>({type,props});const ctx={exports:{},require:()=>({jsx,jsxs:jsx})};runInNewContext(compile(read('CareerCalendarPanel.tsx')),ctx);
 const c=hardenedCalendarView();c.competition.allowedCommands=['DISPATCH_AUTO_COMPETITION_FIXTURE'];c.competition.nextFixture={fixtureId:'auto-B',competitionId:'LCK_CUP',matchId:'B',executionMode:'FULL_AUTO',date:c.currentDate,seriesFormat:'BO3'};
 const flatten=n=>!n?[]:Array.isArray(n)?n.flatMap(flatten):typeof n==='object'?[n,...flatten(n.props?.children)]:[];
 let executed=0,closed=0;const render=(extra={})=>flatten(ctx.exports.CareerCalendarPanel({calendar:c,loading:false,pending:false,error:null,focusFixture:'managed-A',onCompetitionAction:()=>executed++,onClosePreparation:()=>closed++,...extra}));
 const button=nodes=>nodes.find(n=>n.type==='button'&&n.props.children==='Auto 경기 실행');
 assert.equal(button(render()).props.disabled,false);button(render()).props.onClick();assert.equal(executed,1);
 c.competition.allowedCommands=[];assert.equal(button(render()),undefined);c.competition.allowedCommands=['DISPATCH_AUTO_COMPETITION_FIXTURE'];assert.ok(render({competitionPending:true}).find(n=>n.type==='button'&&n.props.children==='대회 상태 확인 중…').props.disabled);
 assert.ok(!render().some(n=>n.type==='button'&&String(n.props.children).includes('관리 Series 시작')));
 render().find(n=>n.type==='button'&&n.props.children==='경기 준비 표시 닫기').props.onClick();assert.equal(closed,1);
 console.log('PASS actual Calendar permits prior Auto B with preparation A and keeps busy/close boundaries');
 const nav={exports:{}};runInNewContext(compile(read('careerNavigation.ts')),nav);const a={panel:'MATCH',current:true,sourceId:'A',seasonYear:2027,matchState:'UNSTARTED'};
 assert.equal(nav.exports.preparationStillCurrent(a,{...a},2027),true);
 for(const next of [null,{...a,sourceId:'B'},{...a,matchState:'IN_PROGRESS'},{...a,matchState:'COMPLETED'},{...a,matchState:'CANCELLED'}])assert.equal(nav.exports.preparationStillCurrent(a,next,2027),false);
 assert.equal(nav.exports.preparationStillCurrent(a,a,2028),false);
 const effect=read('CareerDashboardPage.tsx').match(/useEffect\(\(\) => \{\s*if \(!inboxFocus[\s\S]*?\}, \[inboxFocus, selectedId,[\s\S]*?\]\);/)[0];
 async function staleEffect(abort,cancelled=false){let finish,focus=a;const e={AbortController,inboxFocus:a,calendar:cancelled?{...c,blockingReason:'SEASON_CANCELLED'}:c,selectedId:careerId,continuousBusy:false,preparationStillCurrent:nav.exports.preparationStillCurrent,scoutingRequest:()=>new Promise(r=>finish=r),setInboxFocus:f=>focus=f(focus),useEffect:f=>e.cleanup=f()};runInNewContext(compile(effect),e);if(cancelled)return focus;if(abort)e.cleanup();finish({careerId,next:null});await new Promise(setImmediate);return focus;}
 assert.equal(await staleEffect(false),null);assert.equal(await staleEffect(true),a);assert.equal(await staleEffect(false,true),null);
 console.log('PASS actual preparation effect clears ended/replaced targets and ignores aborted responses');
}

// Play-flow boundaries: reuse server actions and approved records, never infer commands from navigation.
{
 const { default: ts } = await import('typescript'), { readFileSync } = await import('node:fs'), { runInNewContext } = await import('node:vm');
 const read = n => readFileSync(new URL(`../src/features/career/${n}`, import.meta.url), 'utf8').replace(/\r\n/g, '\n');
 const compile = s => ts.transpileModule(s, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
 const context = { exports: {} }; runInNewContext(compile(read('careerPlayFlow.ts')), context);
 const flow = context.exports, cal = hardenedCalendarView();
 cal.competition.nextFixture = { fixtureId: 'managed', seriesId: 'reserved-before-start', competitionId: 'LCK_CUP', executionMode: 'PLAYER_CONTROLLED', bindingHash: null };
 assert.equal(flow.calendarMatchLink(cal).matchState, 'UNSTARTED'); cal.competition.nextFixture.bindingHash = 'bound'; assert.equal(flow.calendarMatchLink(cal).matchState, 'IN_PROGRESS');
 cal.competition.nextFixture.executionMode = 'FULL_AUTO'; assert.equal(flow.calendarMatchLink(cal), null);
 console.log('PASS play navigation uses binding state, preserves reserved Series and never opens preceding Auto as Player');
 cal.competition.allowedCommands = ['DISPATCH_AUTO_COMPETITION_FIXTURE'];
 assert.equal(flow.nextPlayLabel(cal, false, false), '선행 Auto 경기 확인'); assert.equal(flow.nextPlayLabel(cal, false, true), '서버 진행 상태 보기'); assert.equal(flow.nextPlayLabel(cal, true, false), '현재 시즌 업무로 돌아가기');
 console.log('PASS next play follows current Calendar, processing and historical context');
 const trade = { status: 'CLUB_PENDING', sellerAgreed: true, buyerAgreed: false, decisionDate: '2027-01-12' };
 assert.match(flow.tradeProgress(trade), /받는 구단 검토 중/); assert.match(flow.tradeProgress({ ...trade, status: 'PLAYER_PENDING', buyerAgreed: true }), /선수 판단 대기/); assert.match(flow.tradeProgress({ ...trade, status: 'AGREED' }), /효력일 적용 대기/); assert.equal(flow.tradeProgress({ ...trade, status: 'COMPLETED' }), '효력일 적용 완료');
 console.log('PASS club acceptance, player consent and effective-date application stay distinct');
 const result = { careerId, seriesId: 'approved', seasonYear: 2027, firstTeam: 'LCK:T1', secondTeam: 'LCK:GEN', games: [{ winner: 'LCK:T1' }, { winner: 'LCK:GEN' }, { winner: 'LCK:T1' }] };
 assert.equal(flow.approvedSeriesScore(result, careerId, 'approved'), '2–1'); assert.throws(() => flow.approvedSeriesScore(result, secondCareerId, 'approved')); assert.throws(() => flow.approvedSeriesScore(result, careerId, 'other')); assert.equal(flow.approvedSeriesScore({ ...result, games: [] }, careerId, 'approved'), '세트 스코어 미수집');
 console.log('PASS approved result score binds Career and exact Series and does not fill missing sets with zero');
 const stop = read('CareerDashboardPage.tsx').split(' onAction={')[1].split(' appliedRun=')[0].slice(0, -1);
 const calls = [], cb = { exports: {}, openPage: p => calls.push(['page',p]), goProgress: () => calls.push(['page','schedule']), setScheduleTab() {}, calendar: cal, ...flow, navigateCareer: l => calls.push(['navigate', l.sourceId]), focusPlayTarget: s => calls.push(['focus', s]), executeCompetition: () => { throw Error('navigation must not execute'); }, onResume: () => { throw Error('stale League resume'); } };
 runInNewContext(compile(`exports.stop = ${stop};`), cb); cb.exports.stop('MATCH'); cb.exports.stop('MARKET'); assert.deepEqual(calls.map(c => c[0]), ['page', 'page']);
 cal.competition.nextFixture.executionMode = 'PLAYER_CONTROLLED'; cb.exports.stop('MATCH'); assert.deepEqual(calls.at(-1), ['navigate', 'managed']);
 console.log('PASS actual stop-link callback only navigates current targets and does not start a Series');
 const effects = [], updates = []; let finish;
 const ui = { exports: {}, AbortController, require: name => name === 'react' ? { useState: v => [v, n => updates.push(n)], useEffect: f => effects.push(f) } : name === 'react/jsx-runtime' ? { jsx() {}, jsxs() {} } : { ...flow, recordRequest: () => new Promise(r => { finish = r; }) } };
 runInNewContext(compile(read('CareerReturnResult.tsx')), ui); ui.exports.CareerReturnResult({ careerId, seriesId: 'approved', onNavigate() {} }); const cleanup = effects[0](); cleanup(); const prior = updates.length; finish({ result }); await new Promise(setImmediate); assert.equal(updates.length, prior);
 console.log('PASS actual return-result effect ignores responses after Career or Series cleanup');
 const updates2 = [], detail = { careerId }, c2 = { commitEntry() {}, fetchLckTeams: async () => ({ teams: [], catalog: {} }), setManaging(){}, navigationGeneration:{current:0}, queuedPage:{current:null}, readCareerLocation:()=>({page:'home',year:null,focus:null}),setScheduleTab(){},setTrainingTab(){},setPage(){},setHistoryYear(){},setInboxFocus(){},setMarketPlayer(){},setContinuousStatus(){}, onCareerSelectionChange: undefined, exports: {}, AbortController, window: { sessionStorage: {} }, useCallback: f => f, selectedIdRef: { current: careerId }, generationRef: { current: 0 }, requestRef: { current: null }, restoredAdvanceRef: { current: null }, teamsRef: { current: [] }, catalogRef: { current: null }, appliedContinuous: { current: new Map() }, getCareer: async () => detail, getCareerCalendar: async () => cal, requireCareerReference() {}, reconcileCareerAdvanceOperation() {}, reconcileCareerCompetitionOperation() {}, invalidateScreenRequests() {}, applyDetail() {}, setHistorical() {}, setSelectedId() {}, setDetail: v => updates2.push(['detail', v]), setCalendar: v => updates2.push(['calendar', v]), setDetailLoading: v => updates2.push(['loading', v]), setCalendarLoading() {}, setCalendarError() {}, setError() {}, setIntegrityError() {} };
 const load = read('CareerDashboardPage.tsx').split('  const loadDetail =')[1].split('\n\n  const loadWorkspace')[0];runInNewContext(compile(`exports.load = ${load}`), c2); assert.equal(await c2.exports.load(careerId), true); assert.ok(!updates2.some(([key, value]) => ['detail', 'calendar'].includes(key) && value === null)); assert.ok(updates2.some(([key, value]) => key === 'loading' && value === false));
 console.log('PASS actual same-Career refresh preserves mounted panels and their original request recovery');
 const root = readFileSync(new URL('../src/RootApp.tsx', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
 const select = read('CareerDashboardPage.tsx').match(/onLoad=\{(id => \{ commitEntry\('club'\); void loadDetail\(id, true\); \})\}/)[1];
 assert.equal((root.match(/onCareerSelectionChange=\{invalidateCareerSeriesRequest\}/g) ?? []).length, 2);
 for (const name of ['openLeagueSeries', 'openCareerCompetitionSeries']) {
  for (const failure of [false, true]) {
   let resolveSeries, rejectSeries; const applied = [];
   const rc = { exports: {}, AbortController, useCallback: f => f, seriesRequestRef: { current: null }, window: { sessionStorage: { setItem: (...v) => applied.push(['storage',...v]) } },
    getSeries: () => new Promise((r,j) => {resolveSeries=r;rejectSeries=j;}), loadMatchSetupOptions: async()=>({}), fetchPlayerDraftChampionCatalog:async()=>({}), SeriesApiFailure:class extends Error {},
    showToast:(...v)=>applied.push(['toast',...v]), createPlayerDraftChampionCatalog:()=>({}), createSeriesScreenState:()=>({}), LEAGUE_SERIES_CONTEXT_KEY:'league', CAREER_COMPETITION_SERIES_CONTEXT_KEY:'competition' };
   for (const fn of ['clearCareerCompetitionSeriesContext','setCareerCompetitionSeriesContext','setLeagueSeriesContext','writeSeriesPointer','setSeriesState','setLeagueSeriesReturn','clearLeagueSeriesContext','writeCareerReturnContext','setCareerReturnContext','setActiveScreen']) rc[fn]=(...v)=>applied.push([fn,...v]);
   for (const [exported,fn] of [['open',name],['invalidate','invalidateCareerSeriesRequest']]) runInNewContext(compile('exports.'+exported+' ='+root.split('  const '+fn+' =')[1].split('\n\n  const ')[0]),rc);
   const pc={...c2,exports:{},selectedIdRef:{current:careerId},requestRef:{current:null},onCareerSelectionChange:rc.exports.invalidate,career:{careerId:secondCareerId},getCareer:async id=>({careerId:id})};
   runInNewContext(compile('const loadDetail ='+load+'; exports.select = () => ('+select+')(career.careerId)'),pc);
   const open=()=>name==='openLeagueSeries'?rc.exports.open({boundSeriesId:'old'},{}):rc.exports.open('old',{careerId},'old match');
   const pending=open();pc.exports.select();await new Promise(setImmediate);assert.equal(pc.selectedIdRef.current,secondCareerId);
   pc.career={careerId};pc.exports.select();await new Promise(setImmediate);assert.equal(pc.selectedIdRef.current,careerId);
   if(failure)rejectSeries(new Error('late failure'));else resolveSeries({seriesId:'old'});await pending;assert.deepEqual(applied,[]);
   const normal=open();pc.exports.select();await new Promise(setImmediate);resolveSeries({seriesId:'old'});await normal;
   assert.ok(applied.some(v=>v[0]==='setActiveScreen'&&v[1]==='series-hub'));assert.ok(applied.some(v=>v[0]==='writeSeriesPointer'));
  }
  console.log('PASS actual saved-Career click A-B-A rejects delayed success/failure and permits normal '+name);
 }

 for(const failure of [false,true]) {
  let finish,fail;const applied=[];
  const rc={exports:{},AbortController,useCallback:f=>f,window:{sessionStorage:{}},seriesRequestRef:{current:null},careerResumeRoute:()=>({kind:'PLAYER_SERIES',leagueId:'l',seasonId:'s',fixtureId:'f',seriesId:'old'}),writeCareerReturnContext(){},setCareerReturnContext(){},connectCareerLeague(){},setActiveScreen:v=>applied.push(v),showToast:()=>applied.push('toast'),openLeagueSeries:()=>{throw Error('stale resume reached child');},getLeagueFixtures:()=>new Promise((r,j)=>{finish=r;fail=j;}),getLeaguePlayerSeries:async()=>({})};
  for(const [exported,fn] of [['resume','resumeCareer'],['invalidate','invalidateCareerSeriesRequest']])runInNewContext(compile('exports.'+exported+' ='+root.split('  const '+fn+' =')[1].split('\n\n  const ')[0]),rc);
  const pc={...c2,exports:{},selectedIdRef:{current:careerId},onCareerSelectionChange:rc.exports.invalidate,career:{careerId:secondCareerId}};
  runInNewContext(compile('const loadDetail ='+load+'; exports.select = () => ('+select+')(career.careerId)'),pc);
  const pending=rc.exports.resume({careerId});pc.exports.select();await new Promise(setImmediate);
  if(failure)fail(new Error('delayed lookup failure'));else finish({fixtures:[]});await pending;assert.deepEqual(applied,[]);
 }
 console.log('PASS actual save selection suppresses delayed resume prefetch errors and fallback navigation');
 const returning = { exports: {}, useCallback: f => f, window: { sessionStorage: {} }, careerReturnContext: null, leagueSeriesContext: null, seriesState: null, clearSeriesPointer: () => calls.push(['clear-auto-open']), clearCareerReturnContext() {}, setCareerReturnContext() {}, setActiveScreen: v => calls.push(['screen', v]) };
 runInNewContext(compile(`exports.back = ${root.split('  const returnToCareer =')[1].split('\n\n  const ')[0]}`), returning);returning.exports.back();assert.deepEqual(calls.slice(-2), [['clear-auto-open'], ['screen', 'career']]);
 console.log('PASS actual return-to-Career clears automatic Series reopening and restores the Career screen');
}

// Workspace navigation: prove the new page boundary without repeating API contracts.
{
  const { default: ts } = await import('typescript'), { readFileSync } = await import('node:fs'), { runInNewContext } = await import('node:vm');
  const read = name => readFileSync(new URL(`../src/features/career/${name}`, import.meta.url), 'utf8').replace(/\r\n/g, '\n');
  const compile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const workspace = { exports: {} }; runInNewContext(compile(read('careerWorkspace.ts')), workspace);
  const { readCareerLocation, writeCareerLocation, pageForLink } = workspace.exports;
  const values = new Map(), storage = { getItem: k => values.get(k) ?? null, setItem: (k,v) => values.set(k,v) };
  const focus = { panel: 'TRADE', playerId: 'original-player', sourceId: 'original-negotiation', competition: null, seasonYear: 2027, seriesId: null, positions: [], current: true };
  writeCareerLocation(storage, 'A', { page: 'market', year: null, focus, scheduleTab: 'cl' });
  writeCareerLocation(storage, 'B', { page: 'records', year: 2026, focus: null });
  assert.equal(readCareerLocation(storage, 'A').focus.sourceId, 'original-negotiation');
  assert.equal(readCareerLocation(storage, 'A').scheduleTab, 'cl'); assert.equal(readCareerLocation(storage, 'B').year, 2026);
  assert.equal(readCareerLocation(storage, 'missing').page, 'home'); assert.equal(pageForLink(focus), 'market');
  assert.equal(pageForLink({ ...focus, panel: 'FINANCE' }), 'finance');
  console.log('PASS per-Career menu restoration preserves historical year and original player/negotiation IDs');
  for (const raw of ['{bad', JSON.stringify({ page: 'unknown' }), JSON.stringify({ page: 'roster', year: '2027', focus: { panel: 'ROSTER' } })]) {
    const v = readCareerLocation({ getItem: () => raw }, 'A'); assert.equal(v.focus, null); assert.equal(v.year, null);
  }
  console.log('PASS invalid optional navigation state falls back without discarding command recovery');
  const dashboard = read('CareerDashboardPage.tsx');
  const source = dashboard.slice(dashboard.indexOf('  function openPage('), dashboard.indexOf('  useEffect(() => { if (!mutationPending'));
  const events = [], context = { exports: {}, navigationGeneration: { current: 0 }, mutationGate: { current: { busy: false } }, queuedPage: { current: null }, historical: false,
    setMarketPlayer() {}, setManaging: v => events.push(['managing',v]), setPage: v => events.push(['page',v]), setInboxFocus: v => events.push(['focus',v]), onNotify: () => events.push(['notice']),
    loadDetail: () => { throw Error('ordinary navigation must not load or mutate'); } };
  runInNewContext(compile(source + '; exports.open = openPage;'), context); context.exports.open('roster');
  assert.deepEqual(events, [['managing',true],['page','roster'],['focus',null]]);
  events.length = 0; context.mutationGate.current.busy = true; context.exports.open('market');
  assert.equal(context.queuedPage.current, 'market'); assert.deepEqual(events, [['notice']]);
  console.log('PASS actual menu navigation issues no command and queues navigation while the mutation owner is active');
  for (const stale of [false,true]) {
    let finish; const changed = []; const c = { ...context, exports: {}, navigationGeneration: { current: 0 }, mutationGate: { current: { busy: false } }, historical: true,
      detail: { careerId: 'A' }, selectedIdRef: { current: 'A' }, loadDetail: () => new Promise(r => { finish = r; }), setHistorical: v => changed.push(['historical',v]),
      setHistoryYear: v => changed.push(['year',v]), setManaging() {}, setPage: v => changed.push(['page',v]), setInboxFocus() {} };
    runInNewContext(compile(source + '; exports.open = openPage;'), c); c.exports.open('inbox'); assert.equal(changed.length,0);
    if (stale) ++c.navigationGeneration.current; finish(true); await new Promise(setImmediate);
    assert.equal(changed.length, stale ? 0 : 3);
    if (!stale) assert.deepEqual(changed, [['historical',false],['year',null],['page','inbox']]);
  }
  console.log('PASS current-work menu waits for active season and rejects a superseded navigation response');
  const advance = dashboard.split('  const advance =')[1].split('\n\n  const autoScope')[0];
  const execute = dashboard.split('  const executeCompetition =')[1].split('\n\n  useEffect(')[0];
  const c = { exports: {}, useCallback: f => f, detail: {}, calendar: {}, continuousBusy: true, advancePending: false, competitionPending: false, historical: false,
    mutationGate: { current: { acquire() { throw Error('running Continuous must block mutation'); } } }, applyDetail() {}, onNotify() {}, onOpenCompetitionSeries() {}, observeAcceptedAuto() {} };
  runInNewContext(compile(`exports.advance = ${advance}; exports.execute = ${execute};`), c);
  await c.exports.advance('ADVANCE_ONE_DAY'); await c.exports.execute();
  console.log('PASS actual Calendar handlers reject conflicting commands while shared Continuous state is busy');
}

{
  const { careerLeagueStorage, connectCareerLeague, readLeaguePointer, writeLeaguePointer, updateLeagueCommand } = await import('../src/features/league/league.pointer.ts');
  const local = storage(), original = { schemaVersion: 'AI_LEAGUE_POINTER_V1', leagueId, seasonId, command: { kind: 'START_PLAYER_SERIES', scopeKey: fixtureId, clientCommandId: 'original-command', expectedRevision: 7, bindingHash: null } };
  writeLeaguePointer(local, original);
  connectCareerLeague(local, careerId, leagueId, seasonId);
  assert.equal(readLeaguePointer(local), null);
  const careerStorage = careerLeagueStorage(local, careerId);
  assert.deepEqual(readLeaguePointer(careerStorage), original);
  const standalone = { ...original, leagueId: `league_${'9'.repeat(64)}`, command: { ...original.command, clientCommandId: 'standalone-command' } };
  writeLeaguePointer(local, standalone);
  connectCareerLeague(local, secondCareerId, `league_${'8'.repeat(64)}`, seasonId);
  connectCareerLeague(local, careerId, leagueId, seasonId);
  assert.deepEqual(readLeaguePointer(careerStorage), original);
  assert.deepEqual(readLeaguePointer(local), standalone);
  updateLeagueCommand(careerStorage, original, null);
  assert.deepEqual(readLeaguePointer(local), standalone);
  assert.equal(readLeaguePointer(careerLeagueStorage(local, secondCareerId)).leagueId, `league_${'8'.repeat(64)}`);
  console.log('PASS Career league re-entry preserves original recovery UUID and isolates standalone and other Career pointers');
}

{
  const { default: ts } = await import('typescript'), { readFileSync } = await import('node:fs'), { runInNewContext } = await import('node:vm');
  const text = readFileSync(new URL('../src/features/career/CareerTransferPanel.tsx', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
  const callback = text.slice(text.indexOf('  useEffect(() => {\n    if (focus?.panel'), text.indexOf('  const execute ='));
  const pending = { tradeId: 'original-trade', status: 'CLUB_COUNTER', terms: { playerId: 'original-player' } };
  const recovered = [], local = storage();
  local.setItem(`career-trade-focus:${careerId}:2027`, pending.tradeId);
  const context = { useEffect: f => f(), window: { sessionStorage: local }, focus: null, editing: null, selected: 'original-player', view: { careerId, seasonYear: 2027, revision: 1, management: { trades: [pending] } }, fill: value => recovered.push(value.tradeId) };
  const compiled = ts.transpileModule(callback, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  runInNewContext(compiled, context); assert.deepEqual(recovered, ['original-trade']);
  recovered.length = 0; context.view.careerId = secondCareerId; runInNewContext(compiled, context); assert.deepEqual(recovered, []);
  context.view.careerId = careerId; pending.status = 'COMPLETED'; runInNewContext(compiled, context); assert.deepEqual(recovered, []);
  pending.status = 'CLUB_COUNTER'; context.selected = 'other-player'; runInNewContext(compiled, context); assert.deepEqual(recovered, []);
  console.log('PASS remounted trade detail restores only the original live negotiation for the same Career season and player');
}

// Controlled time establishes long-running and reconnect behavior without slowing production or executing a game.
{
  const { observeCareerAuto } = await import('../src/features/career/careerAutoObservation.ts');
  const target = { careerId, sourceYear: 2027, fixtureId: `competition_fixture_${'7'.repeat(64)}`, clientCommandId: '10000000-0000-4000-8000-000000000001', jobId: 'original-job' };
  const running = hardenedCalendarView(); Object.assign(running.competition.nextFixture, { executionMode: 'FULL_AUTO', jobId: target.jobId, jobStatus: 'RUNNING' });
  running.competition.activePendingCommand = { clientCommandId: target.clientCommandId, competitionId: 'LCK_CUP', matchId: 'GROUP_B01_E01', commandStatus: 'RUNNING' };
  const completed = clone(running); completed.competition.nextFixture = null; completed.competition.activePendingCommand = null;
  let reads = 0, elapsed = 0, applied = 0, inFlight = 0, maximum = 0, refreshes = 0;
  const controller = new AbortController();
  const result = await observeCareerAuto(target, { signal: controller.signal, current: () => true,
    wait: async ms => { elapsed += ms; },
    read: async () => { maximum = Math.max(maximum, ++inFlight); try { reads++; if (reads === 3) throw new Error('lost poll'); return reads < 9 ? running : completed; } finally { inFlight--; } },
    calendar: () => { applied++; }, message: () => {}, complete: async () => { if (++refreshes === 1) throw new Error('lost terminal refresh'); },
  });
  assert.equal(result, 'COMPLETED'); assert.ok(elapsed > 10000); assert.equal(maximum, 1); assert.equal(reads, 10); assert.equal(refreshes, 2); assert.equal(applied, 9);
  console.log('PASS single Auto beyond ten seconds, transient read loss, terminal synchronization retry, bounded single observer');
  for (const changed of ['career', 'year', 'abort']) {
    let committed = 0; const scope = new AbortController(); const stale = clone(running);
    const stopped = await observeCareerAuto(target, { signal: scope.signal, current: () => true, wait: async () => {},
      read: async () => { if (changed === 'career') stale.careerId = secondCareerId; else if (changed === 'year') stale.activeCalendarSeasonYear = 2028; else scope.abort(); return stale; },
      calendar: () => { committed++; }, message: () => {}, complete: async () => { committed++; },
    });
    assert.equal(stopped, 'STALE'); assert.equal(committed, 0);
  }
  console.log('PASS single Auto late Career/year response and unmount abort never apply to the next screen');
}

// Real dashboard recovery callbacks: a lost POST must retain identity even when the first GET is already terminal.
{
 const { default: ts } = await import('typescript');
 const { readFileSync } = await import('node:fs'); const { runInNewContext } = await import('node:vm');
 const source = readFileSync(new URL('../src/features/career/CareerDashboardPage.tsx', import.meta.url),'utf8').replace(/\r\n/g,'\n');
 const compile = source => ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const callback = source.split('  const executeCompetition =')[1].split('\n  useEffect(() => {')[0];
 for (const completed of [false,true]) {
  const original = {clientCommandId:'original-auto',sourceYear:2027,expectedCompetitionRevision:3}; let stored=original, posts=0, releases=0; const observed=[];
  const fixture = {fixtureId:'original-fixture',competitionId:'LPL_SPLIT_1',matchId:'original-match',executionMode:'FULL_AUTO',jobId:'original-job'};
  const calendar = {careerId:'A',activeCalendarSeasonYear:2027,competition:{revision:3,nextFixture:fixture,allowedCommands:['DISPATCH_AUTO_COMPETITION_FIXTURE']}};
  class Failure extends Error { constructor(kind,message) {super(message);this.kind=kind;this.userMessage=message;} }
  const c={exports:{},useCallback:f=>f,AbortController,detail:{careerId:'A'},calendar,competitionPending:false,historical:false,continuousBusy:false,mutationGate:{current:{busy:false,acquire:()=>()=>{releases++;}}},competitionRequestRef:{current:null},generationRef:{current:1},selectedIdRef:{current:'A'},window:{sessionStorage:{}},setCompetitionPending(){},setCalendarError(){},logicalCareerCompetition:()=>original,CAREER_SCHEMAS:{competitionCommandRequest:'V1'},startOrResumeCareerCompetition:async()=>{posts++;throw new Failure('NETWORK','lost');},CareerApiFailure:Failure,isAmbiguousCareerCreateFailure:()=>true,loadFailure:String,readCareerCompetitionOperation:()=>stored,getCareerCalendar:async()=>({...calendar,competition:{...calendar.competition,nextFixture:completed?{fixtureId:'next'}:fixture,activePendingCommand:completed?null:{clientCommandId:'original-auto'}}}),reconcileCareerCompetitionOperation:()=>{if(completed)stored=null;},setCalendar(){},observeAcceptedAuto:async target=>{observed.push(target);},applyDetail(){},onNotify(){},onOpenCompetitionSeries(){},clearCareerCompetitionOperation(){throw Error('original must remain through ambiguous response');}};
  runInNewContext(compile('exports.execute = '+callback),c);await c.exports.execute();
  assert.equal(posts,1);assert.equal(releases,1);assert.equal(observed.length,1);assert.equal(observed[0].clientCommandId,original.clientCommandId);assert.equal(observed[0].fixtureId,fixture.fixtureId);
 }
 console.log('PASS lost Auto POST restores original fixture and UUID even when Calendar already advanced; no second dispatch');
 const recovery=source.split('  useEffect(() => {\n    if (!calendar || historical || competitionPending')[1].split('\n\n')[0];
 let finish,owned=false,observers=0;const effects=[];const c={managing:false,exports:{},useEffect:f=>effects.push(f),AbortController,calendar:{careerId:'A',activeCalendarSeasonYear:2027,competition:{nextFixture:{fixtureId:'same',competitionId:'LPL',matchId:'m',executionMode:'FULL_AUTO',jobStatus:'RUNNING',jobId:'same-job'},activePendingCommand:{competitionId:'LPL',matchId:'m',clientCommandId:'same-command'}}},historical:false,competitionPending:false,continuousBusy:false,selectedId:'A',mutationGate:{current:{acquire:()=>{if(owned)return null;owned=true;return()=>{owned=false;};}}},competitionRequestRef:{current:null},generationRef:{current:1},setCompetitionPending(){},observeAcceptedAuto:target=>{observers++;assert.equal(target.clientCommandId,'same-command');return new Promise(r=>finish=r);}};
 runInNewContext(compile('useEffect(() => {\n    if (!calendar || historical || competitionPending'+recovery),c);effects[0]();assert.equal(observers,0,'entry screen must not resume a hidden Auto observer');c.managing=true;effects[0]();effects[0]();assert.equal(observers,1);finish();await new Promise(setImmediate);assert.equal(owned,false);
 console.log('PASS remounted dashboard resumes the accepted job without POST and acquires only one observer');
 const resultSource=readFileSync(new URL('../src/features/career/CareerReturnResult.tsx',import.meta.url),'utf8');const recorded=[];const requests=[];const updates=[];
 const ui={exports:{},AbortController,encodeURIComponent,require:name=>name==='react'?{useState:v=>[v,n=>updates.push(n)],useEffect:(f,deps)=>recorded.push({f,deps})}:name==='react/jsx-runtime'?{jsx(){},jsxs(){}}:{recordRequest:()=>{let resolve;const promise=new Promise(r=>resolve=r);requests.push(resolve);return promise;},approvedSeriesScore:()=> '0–2'}};
 runInNewContext(compile(resultSource),ui);for(const revision of [0,1])ui.exports.CareerReturnResult({careerId:'A',seriesId:'s',revision,onNavigate(){}});
 assert.notDeepEqual(recorded[0].deps,recorded[1].deps);const cleanup=recorded[0].f();cleanup();recorded[1].f();requests[0]({result:null});requests[1]({result:{careerId:'A',seriesId:'s'}});await new Promise(setImmediate);assert.ok(updates.some(v=>v?.seriesId==='s'));
 console.log('PASS completion revision automatically reloads the same returned Series and rejects its older pending response');
}

{
 const {default:ts}=await import('typescript');const {readFileSync}=await import('node:fs');const {runInNewContext}=await import('node:vm');
 const code=ts.transpileModule(readFileSync(new URL('../src/features/career/CareerContinuousPanel.tsx',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 for(const status of ['COMPLETED','RUNNING']) {
  const nodes=[];let state=0;const view={careerId:'A',currentDate:'2028-01-01',run:{runId:'r',revision:1,status,targetDate:'2026-12-29',stop:null},allowedCommands:[]};const jsx=(type,props)=>{const node={type,props};nodes.push(node);return node;};
  const c={exports:{},require:name=>name==='react'?{useState:v=>[state++===0?view:state===2?'TARGET_DATE':v,()=>{}],useRef:v=>({current:v}),useEffect(){}}:name==='react/jsx-runtime'?{jsx,jsxs:jsx}:{}};runInNewContext(code,c);
  c.exports.CareerContinuousPanel({careerId:'A',currentDate:'2028-01-02',seasonYear:2028,busy:false,appliedRun:'r:1',onBusy(){},onStopped(){},onBegin(){},onAction(){}});
  const date=status==='RUNNING'?'2028-01-01':'2028-01-02';assert.equal(nodes.find(n=>n.type==='strong').props.children,date);assert.equal(nodes.find(n=>n.type==='input'&&n.props.type==='date').props.min,date);
 }
 console.log('PASS stopped Continuous uses the current Calendar date and target limit while running observation keeps its live date');
}

{
 const {default:ts}=await import('typescript');const {readFileSync}=await import('node:fs');const {runInNewContext}=await import('node:vm');
 const code=ts.transpileModule(readFileSync(new URL('../src/features/career/CareerContinuousPanel.tsx',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const refs=[],timers=new Map(),reads=[],busy=[];let cursor=0,effects=[],timerId=0,syncs=0;
 const c={exports:{},AbortController,window:{sessionStorage:{getItem:()=>null},setTimeout:(f,ms)=>{timers.set(++timerId,{f,ms});return timerId;},clearTimeout:id=>timers.delete(id)},require:name=>name==='react'?{useState:v=>[v,()=>{}],useRef:v=>{const i=cursor++;return refs[i]??(refs[i]={current:v});},useEffect:f=>effects.push(f)}:name==='react/jsx-runtime'?{jsx(){},jsxs(){}}:{getCareerContinuous:()=>new Promise(resolve=>reads.push(resolve)),CareerApiFailure:class extends Error{}}};runInNewContext(code,c);
 const render=visible=>{cursor=0;effects=[];c.exports.CareerContinuousPanel({visible,careerId:'A',currentDate:'2028-01-02',seasonYear:2028,busy:false,appliedRun:null,onBusy:(_,v)=>busy.push(v),onStopped:async()=>{syncs++;return true;},onBegin(){},onAction(){}});};
 render(false);const cleanup=effects[0]();effects[1]();assert.equal(reads.length,1);render(true);effects[1]();assert.equal(reads.length,1);assert.equal(timers.size,0,'visibility during a GET must not create another timer');
 const view={careerId:'A',currentDate:'2028-01-02',run:{runId:'r',revision:1,status:'RUNNING'},allowedCommands:[]};reads[0](view);await new Promise(setImmediate);assert.equal(timers.size,1);const [id,tick]=[...timers][0];assert.equal(tick.ms,0);timers.delete(id);tick.f();assert.equal(reads.length,2);assert.equal(timers.size,0);
 reads[1]({...view,run:{...view.run,revision:2,status:'COMPLETED'}});await new Promise(setImmediate);assert.equal(syncs,1);assert.equal(busy.at(-1),false);assert.equal(timers.size,0);cleanup();
 console.log('PASS visibility wake during an in-flight Continuous GET schedules one follow-up and leaves no terminal timer');
}

// Entry flow: keep rendering/preview separate from authoritative selection and creation.
{
 const {default:ts}=await import('typescript'),{readFileSync}=await import('node:fs'),{runInNewContext}=await import('node:vm');
 const read=n=>readFileSync(new URL(`../src/features/career/${n}`,import.meta.url),'utf8').replace(/\r\n/g,'\n');
 const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX}}).outputText;
 const helpers={exports:{}};runInNewContext(compile(read('careerEntry.ts')),helpers);
 const {readEntryView,entryKey,recentCareer,displayNameError}=helpers.exports;
 const local=storage(); assert.equal(readEntryView(local,'api-A',null),'main');
 assert.equal(readEntryView(local,'api-A',careerId),'club');local.setItem(entryKey('api-A'),'main');
 assert.equal(readEntryView(local,'api-A',careerId),'main');assert.equal(readEntryView(local,'api-B',careerId),'club');
 const rows=[{careerId:secondCareerId,compatibility:{status:'UNSUPPORTED'}},{careerId,compatibility:{status:'SUPPORTED'}}];
 assert.equal(recentCareer(rows,secondCareerId).career.careerId,secondCareerId);
 assert.equal(recentCareer(rows,null).career.careerId,careerId);assert.equal(recentCareer([],null).career,null);
 console.log('PASS entry starts at main without a pointer, preserves club refresh, scopes hints and retains unsupported recent-save explanations');
 const source=read('CareerEntryScreen.tsx'), effects=[], cells=[], refCells=[];let index=0,refIndex=0;
 const react={useState:initial=>{const i=index++;if(!(i in cells))cells[i]=typeof initial==='function'?initial():initial;return[cells[i],next=>{cells[i]=typeof next==='function'?next(cells[i]):next;}];},useRef:value=>refCells[refIndex++]??(refCells[refIndex-1]={current:value}),useEffect:f=>effects.push(f)};
 let writes=0;
 const ui={exports:{},sessionStorage:storage(),require:name=>name==='react'?react:name==='react/jsx-runtime'?{jsx:(type,props)=>({type,props}),jsxs:(type,props)=>({type,props})}:name==='./careerEntry'?helpers.exports:name==='./career.pointer'?{normalizeCareerSelection:value=>value}:name==='./api/careerApi.client'?{getCareer(){throw Error('render must not query');}}:{},Intl};
 runInNewContext(compile(source),ui);
 const props={view:'main',api:'api-A',list:{careers:[],currentCount:0,maximumCount:100,remainingCount:100},loading:false,error:null,preferred:null,teams:[],teamsLoading:false,teamsError:null,pending:false,operation:null,createError:null,onNavigate(){},onLoad(){writes++;},onCreate(){writes++;},onRefresh(){},onRetryTeams(){}};
 const render=()=>{index=0;refIndex=0;effects.length=0;return ui.exports.CareerEntryScreen(props);};
 const nodes=tree=>!tree||typeof tree!=='object'?[]:Array.isArray(tree)?tree.flatMap(nodes):[tree,...nodes(tree.props?.children)];
 let tree=render();assert.ok(nodes(tree).some(n=>n.type==='h1'));assert.equal(nodes(tree).filter(n=>n.type==='aside').length,0);assert.equal(writes,0);
 props.view='new';props.teams=[{teamCode:'A',starterCount:1,lineup:[{playerId:'a',nickname:'Alpha',position:'TOP'}]},{teamCode:'B',starterCount:1,lineup:[{playerId:'b',nickname:'Beta',position:'TOP'}]}];tree=render();
 const inputNode=id=>nodes(tree).find(n=>n.props?.id===id&&n.type==='input');
 inputNode('ce-save').props.onChange({target:{value:'내 저장 이름'}});inputNode('ce-manager').props.onChange({target:{value:'감독'}});
 for(const code of ['A','B','A']){tree=render();nodes(tree).find(n=>n.type==='button'&&n.props.children?.[0]?.props?.children===code).props.onClick();}
 tree=render();assert.equal(inputNode('ce-save').props.value,'내 저장 이름');assert.equal(cells[5].managedTeamCode,'A');assert.equal(writes,0);
 assert.equal(displayNameError('  감독  '),null);assert.ok(displayNameError(' '));assert.ok(displayNameError('a'.repeat(81)));assert.ok(displayNameError('a\u0001'));
 console.log('PASS actual main/new-game rendering and A-B-A team selection issue no writes and preserve typed names and server lineup identity');
 // Execute the actual preview effect; cleanup invalidates A before B is selected.
 const previewCode=source.slice(source.indexOf('  useEffect(() => {\n    const controller'),source.indexOf('  useEffect(() => { sessionStorage'));
 const responses=[],shown=[];let cleanup;
 const preview={AbortController,previewGeneration:{current:0},loading:false,target:{careerId},view:'load',list:{},useEffect:f=>{cleanup=f();},getCareer:(id,signal)=>new Promise(resolve=>responses.push({id,signal,resolve})),setPreview:v=>{if(v)shown.push(v.careerId);},setPreviewError(){},setPreviewLoading(){}};
 runInNewContext(compile(previewCode),preview);cleanup();preview.target={careerId:secondCareerId};runInNewContext(compile(previewCode),preview);
 responses[0].resolve({careerId});responses[1].resolve({careerId:secondCareerId});await new Promise(setImmediate);assert.deepEqual(shown,[secondCareerId]);cleanup();
 preview.target={careerId,compatibility:{status:'UNSUPPORTED'}};runInNewContext(compile(previewCode),preview);assert.equal(responses.length,2);
 console.log('PASS actual save preview rejects late A after B or cleanup and does not query unsupported entries or mutate pointers');
 const dashboard=read('CareerDashboardPage.tsx');
 const loadWorkspace=dashboard.split('  const loadWorkspace =')[1].split('\n  useEffect(() => { void loadWorkspace();')[0];
 const listSeen=[];const workspace={listRequestRef:{current:null},exports:{},useCallback:f=>f,AbortController,generationRef:{current:0},requestRef:{current:null},entryViewRef:{current:'main'},window:{sessionStorage:storage()},readCareerPointer:()=>null,invalidateScreenRequests(){},getCareers:async()=>({careers:rows}),setList:v=>listSeen.push(v),setInitialLoading(){},setError(){},setIntegrityError(){},loadDetail(){throw Error('main must not enter a save');},commitEntry(){},fetchLckTeams(){throw Error('save list cannot depend on new-game reference');},loadFailure:e=>e.message};
 runInNewContext(compile('exports.load ='+loadWorkspace),workspace);await workspace.exports.load();assert.equal(listSeen[0].careers.length,2);
 console.log('PASS actual main list load accepts mixed compatibility without fetching reference teams or entering a Career');
 const pointer=await import('../src/features/career/career.pointer.ts');
 function creation(){
  const disk=storage(),requests=[],applied=[],errors=[],gate=new CareerMutationGate(()=>{});let resolve,reject;
  const c={listRequestRef:{current:null},setInitialLoading(){},exports:{},useCallback:f=>f,AbortController,window:{sessionStorage:disk},createPending:false,createLock:{current:false},mutationGate:{current:gate},createRequestRef:{current:null},generationRef:{current:0},teamsRef:{current:[]},catalogRef:{current:null},apiScope:'api-A',draftKey:helpers.exports.draftKey,...pointer,CAREER_SCHEMAS:{createRequest:'CAREER_CREATE_REQUEST_V1'},CareerApiFailure,
   createCareer:body=>{requests.push(body);return new Promise((r,j)=>{resolve=r;reject=j;});},getCareerCalendar:async()=>{throw Error('calendar unavailable');},getCareers:async()=>{throw Error('list unavailable');},requireCareerReference(){},applyDetail:v=>applied.push(v.careerId),commitEntry:v=>applied.push(v),onNotify(){},loadFailure:e=>e.message,withCreatedCareer:(old,v)=>({careers:[v]})};
  for(const key of ['setCreatePending','setError','setIntegrityError','setPage','setHistorical','setHistoryYear','setInboxFocus','setMarketPlayer','setScheduleTab','setTrainingTab','setCalendarLoading','setCalendarError','setList'])c[key]=()=>{};
  c.setCreateError=e=>errors.push(e);
  runInNewContext(compile('exports.create ='+dashboard.split('  const create =')[1].split('\n\n  const advance =')[0]),c);
  return {c,disk,requests,applied,errors,resolve:v=>resolve(v),reject:e=>reject(e)};
 }
 const selection={managedTeamCode:'T1',saveName:'  새 저장  ',managerName:'감독'};
 const a=creation();let first=a.c.exports.create(selection);await a.c.exports.create(selection);assert.equal(a.requests.length,1);
 a.reject(new CareerApiFailure('NETWORK','response lost'));await first;const original=readCareerCreateOperation(a.disk);assert.ok(original);
 await a.c.exports.create({...selection,managedTeamCode:'GEN'});assert.equal(a.requests.length,1);assert.equal(readCareerCreateOperation(a.disk).clientCommandId,original.clientCommandId);
 first=a.c.exports.create(original.selection);assert.equal(a.requests[1].clientCommandId,a.requests[0].clientCommandId);assert.deepEqual(a.requests[1],a.requests[0]);
 a.resolve({career:{careerId},replayed:true});await first;assert.deepEqual(a.applied,[careerId,'club']);assert.equal(readCareerCreateOperation(a.disk),null);
 console.log('PASS actual create handler blocks double submission and changed payload, then recovers the original UUID after response loss');
 assert.equal(a.c.createLock.current,false);assert.equal(a.c.mutationGate.current.busy,false);
 first=a.c.exports.create(selection);assert.notEqual(a.requests[2].clientCommandId,a.requests[0].clientCommandId);a.resolve({career:{careerId:secondCareerId},replayed:false});await first;
 assert.deepEqual(a.applied,[careerId,'club',secondCareerId,'club']);assert.equal(readCareerCreateOperation(a.disk),null);
 console.log('PASS successful creation survives Calendar/list failure and a later identical new game gets a fresh logical UUID');
 const b=creation();first=b.c.exports.create(selection);++b.c.generationRef.current;b.c.createRequestRef.current.abort();b.resolve({career:{careerId},replayed:false});await first;
 assert.deepEqual(b.applied,[]);assert.ok(readCareerCreateOperation(b.disk));assert.equal(b.c.mutationGate.current.busy,false);
 console.log('PASS leaving an in-flight create suppresses late navigation while retaining the unresolved original operation');
}

// Exercise the production callbacks with controllable transport, including ownership across entry navigation.
{
 const {default:ts}=await import('typescript'),{readFileSync}=await import('node:fs'),{runInNewContext}=await import('node:vm');
 const source=readFileSync(new URL('../src/features/career/CareerDashboardPage.tsx',import.meta.url),'utf8').replace(/\r\n/g,'\n');
 const body=source.split('  const loadWorkspace =')[1].split('\n  useEffect(() => { void loadWorkspace();')[0];
 const requests=[],lists=[],loading=[];const c={exports:{},useCallback:f=>f,AbortController,generationRef:{current:0},listRequestRef:{current:null},entryViewRef:{current:'main'},window:{sessionStorage:{}},readCareerPointer:()=>null,getCareers:signal=>new Promise((resolve,reject)=>requests.push({signal,resolve,reject})),setInitialLoading:v=>loading.push(v),setList:v=>lists.push(v),setError(){},setIntegrityError(){},loadFailure:String,loadDetail(){throw Error('entry navigation cannot open a Career');},commitEntry(){}};
 runInNewContext(ts.transpileModule('exports.load ='+body,{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText,c);
 const first=c.exports.load();for(const view of ['new','load','main','new']){c.entryViewRef.current=view;c.generationRef.current++;}
 requests[0].resolve({careers:['latest'],remainingCount:99});await first;assert.equal(loading.at(-1),false);assert.equal(lists.length,1);assert.equal(requests[0].signal.aborted,false);
 const old=c.exports.load(),fresh=c.exports.load();requests[1].resolve({careers:['stale']});await old;assert.equal(loading.at(-1),true);assert.equal(lists.length,1);requests[2].reject(Error('list failed'));await fresh;assert.equal(loading.at(-1),false);assert.equal(c.listRequestRef.current,null);
 const retry=c.exports.load();requests[3].resolve({careers:['recovered'],remainingCount:98});await retry;assert.equal(lists.at(-1).remainingCount,98);assert.equal(loading.at(-1),false);
 console.log('PASS entry list survives navigation, superseded finally cannot unlock its successor, and failed GET can recover');
}
{
 const {observeCareerAuto}=await import('../src/features/career/careerAutoObservation.ts');
 const target={careerId,sourceYear:2027,fixtureId:`competition_fixture_${'7'.repeat(64)}`,clientCommandId:'10000000-0000-4000-8000-000000000001',jobId:null};
 const ready=hardenedCalendarView();Object.assign(ready.competition.nextFixture,{fixtureId:target.fixtureId,executionMode:'FULL_AUTO',jobId:null,jobStatus:null,failureCode:null,blockingReason:null,resultApplicationStatus:null});ready.competition.activePendingCommand=null;
 let reads=0,complete=0;const options={signal:new AbortController().signal,current:()=>true,wait:async()=>{},read:async()=>{reads++;return ready;},calendar(){},message(){},complete:async()=>{complete++;}};
 assert.equal(await observeCareerAuto(target,options),'UNCONFIRMED');assert.equal(reads,3);assert.equal(complete,0);
 reads=0;const running=clone(ready);Object.assign(running.competition.nextFixture,{jobId:'accepted',jobStatus:'RUNNING'});running.competition.activePendingCommand={clientCommandId:target.clientCommandId};const done=clone(ready);done.competition.nextFixture=null;
 assert.equal(await observeCareerAuto(target,{...options,read:async()=>++reads<3?ready:reads<12?running:done}),'COMPLETED');assert.equal(reads,12);assert.equal(complete,1);
 const disk=storage();const original=logicalCareerCompetition(disk,careerId,7,()=>target.clientCommandId,2027,target.fixtureId);reconcileCareerCompetitionOperation(disk,careerId,8,null,2027,target.fixtureId);assert.deepEqual(logicalCareerCompetition(disk,careerId,8,()=>{throw Error('new UUID');},2027,target.fixtureId),original);
 reconcileCareerCompetitionOperation(disk,careerId,8,{clientCommandId:target.clientCommandId},2027,target.fixtureId);assert.deepEqual(readCareerCompetitionOperation(disk,careerId),original);
 console.log('PASS unaccepted Auto releases observation after bounded uncertainty; delayed acceptance keeps original observation and retry payload');
}

// Real execute + real observer: a POST that never reached the server releases its gate and retries the identical body.
{
 const { default:ts }=await import('typescript'),{readFileSync}=await import('node:fs'),{runInNewContext}=await import('node:vm');
 const pointer=await import('../src/features/career/career.pointer.ts'),{observeCareerAuto}=await import('../src/features/career/careerAutoObservation.ts');
 const source=readFileSync(new URL('../src/features/career/CareerDashboardPage.tsx',import.meta.url),'utf8').replace(/\r\n/g,'\n');
 const compile=s=>ts.transpileModule(s,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText;
 const disk=storage(),posts=[],messages=[],gate=new CareerMutationGate(()=>{});let terminal=0,acceptedReads=0;
 const calendar=hardenedCalendarView();calendar.careerId=careerId;calendar.activeCalendarSeasonYear=2027;calendar.competition.allowedCommands=['START_AUTO_COMPETITION_FIXTURE'];calendar.competition.activePendingCommand=null;
 Object.assign(calendar.competition.nextFixture,{executionMode:'FULL_AUTO',jobId:null,jobStatus:null,blockingReason:null,failureCode:null,resultApplicationStatus:null});
 const c={exports:{},useCallback:f=>f,AbortController,window:{sessionStorage:disk},...pointer,detail:{careerId},calendar,competitionPending:false,historical:false,continuousBusy:false,mutationGate:{current:gate},competitionRequestRef:{current:null},generationRef:{current:1},selectedIdRef:{current:careerId},autoScope:{current:{managing:true,historical:false,year:2027}},teamsRef:{current:[]},catalogRef:{current:null},CAREER_SCHEMAS:{competitionCommandRequest:'CAREER_COMPETITION_COMMAND_REQUEST_V1'},CareerApiFailure,
  startOrResumeCareerCompetition:async(id,body)=>{posts.push(body);if(posts.length===1)throw new CareerApiFailure('NETWORK','미접수 모형');return {executionMode:'FULL_AUTO',status:'RUNNING',jobId:'original-accepted'};},
  getCareerCalendar:async()=>{const next=clone(calendar);if(posts.length>1){if(++acceptedReads<8){Object.assign(next.competition.nextFixture,{jobId:'original-accepted',jobStatus:'RUNNING'});next.competition.activePendingCommand={clientCommandId:posts[0].clientCommandId};}else next.competition.nextFixture=null;}return next;},
  observeCareerAuto:(target,options)=>observeCareerAuto(target,{...options,wait:async()=>{}}),getCareer:async()=>({careerId}),requireCareerReference(){},applyDetail(){terminal++;},onNotify(){},onOpenCompetitionSeries(){},setOperatingRevision(){},setCompetitionPending:v=>{c.competitionPending=v;},setCalendar:v=>{c.calendar=v;},setCalendarError:v=>messages.push(v),loadFailure:e=>e.message};
 runInNewContext(compile('exports.observe ='+source.split('  const observeAcceptedAuto =')[1].split('\n\n  const executeCompetition =')[0]),c);c.observeAcceptedAuto=c.exports.observe;
 runInNewContext(compile('exports.execute ='+source.split('  const executeCompetition =')[1].split('\n  useEffect(() => {')[0]),c);
 await c.exports.execute();assert.equal(gate.busy,false);assert.equal(c.competitionPending,false);assert.equal(terminal,0);assert.ok(messages.at(-1).includes('접수 여부'));assert.ok(pointer.readCareerCompetitionOperation(disk,careerId));
 await c.exports.execute();assert.equal(posts.length,2);assert.deepEqual(posts[1],posts[0]);assert.equal(terminal,1);assert.equal(acceptedReads,8);assert.equal(gate.busy,false);assert.equal(pointer.readCareerCompetitionOperation(disk,careerId),null);
 console.log('PASS actual execute and observer release unaccepted mutation gate, retry identical UUID/payload, and apply accepted result once');
 const target={careerId,sourceYear:2027,fixtureId:calendar.competition.nextFixture.fixtureId,clientCommandId:posts[0].clientCommandId,jobId:'accepted'};
 assert.equal(await observeCareerAuto(target,{signal:new AbortController().signal,current:()=>true,wait:async()=>{},read:async()=>{throw new CareerApiFailure('BACKEND','무결성 오류',409,'INTEGRITY');},calendar(){},complete:async()=>{throw Error('cannot complete rejected read');},message:m=>messages.push(m)}),'BLOCKED');assert.equal(messages.at(-1),'무결성 오류');
 console.log('PASS ordinary integrity rejection remains visible and never becomes successful Auto completion');
}

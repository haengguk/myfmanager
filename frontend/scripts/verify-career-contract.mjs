import { CareerApiFailure } from '../src/features/career/api/careerApi.failure.ts';
import { validateCareerCreateResponse, validateCareerListResponse, validateCareerView } from '../src/features/career/api/careerApi.validation.ts';
import { careerResumeRoute } from '../src/features/career/career.adapter.ts';
import {
  careerPointerRecoveryAction, clearCareerCreateOperation, logicalCareerCreate,
  isAmbiguousCareerCreateFailure,
  readCareerCreateOperation, readCareerPointer, readCareerReturnContext,
  writeCareerPointer, writeCareerReturnContext,
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

if (!process.exitCode) console.log('CAREER_DASHBOARD_FRONTEND_V1_CONTRACT_VERIFICATION_PASSED');

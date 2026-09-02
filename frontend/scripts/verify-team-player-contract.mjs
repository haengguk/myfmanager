import { createPlayerProfile, safeHttpUrl } from '../src/features/team-player/teamPlayer.adapter.ts';
import { PlayerDetailRequestCoordinator, resolveInitialSelection, selectTeam, writeSelection } from '../src/features/team-player/teamPlayer.selection.ts';
import { validateErrorResponse, validatePlayerResponse, validateWorkspace } from '../src/features/team-player/api/teamPlayerApi.validation.ts';

const H = 'a'.repeat(64);
const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const TEAM_CODES = ['BFX', 'BRO', 'DK', 'DNS', 'GEN', 'HLE', 'KRX', 'KT', 'NS', 'T1'];
const clone = (value) => structuredClone(value);
const resources = ['PLAYER_IDENTITY', 'PLAYER_RATING', 'CHAMPION_PROFICIENCY', 'PLAYER_CAREER'].map((role, index) => ({ role, version: `resource-${index}`, rawSha256: String(index + 1).repeat(64), snapshotAt: index === 3 ? '2026-08-24' : null, researchAsOf: null, dataCutoff: null }));
const catalog = { catalogSchemaVersion: 'TEAM_AND_PLAYER_INFORMATION_CATALOG_V1', catalogVersion: 'catalog-v1', catalogHashAlgorithm: 'SHA-256', catalogHash: H, championPoolVersion: 'pool-v1', sourceResources: resources };
const teams = TEAM_CODES.map((teamCode) => ({ teamCode, starterCount: 5, lineup: POSITIONS.map((position) => ({ playerId: `player-${teamCode.toLowerCase()}-${position.toLowerCase()}`, nickname: `${teamCode}-${position}`, position })) }));
const players = teams.flatMap((team) => team.lineup.map((player) => ({ ...player, currentTeamCode: team.teamCode, nationality: ['KR'], birthDate: '2001-01-01', contractEndDate: '2027-11-15', contractStatus: 'ACTIVE' })));
const metadata = {
  schemaVersion: 'TEAM_PLAYER_INFORMATION_METADATA_V1', leagueCode: 'LCK', catalog,
  counts: { teams: 10, players: 50, uniquePlayerIds: 50, teamHistoryRows: 248, teamAchievementRows: 154, individualAwardRows: 21, sourceRows: 248, authoredProficiencies: 732, neutralFallbackKeys: 1428, playersWithMajorHonorsListed: 43 },
  semantics: { contract: 'snapshot contract', career: 'ordered career', honors: 'major honors', prizeMoney: 'approximate public winnings', age: 'snapshot age' },
  limitations: { currentLckOnly: true, startersOnly: true, substitutesIncluded: false, salaryIncluded: false, marketValueIncluded: false, overallRatingIncluded: false, mutableCareerStateIncluded: false, affectsGameplayOrRandomIdentity: false },
};
const teamsResponse = { schemaVersion: 'TEAM_PLAYER_INFORMATION_TEAMS_V1', leagueCode: 'LCK', catalog, teams };
const playersResponse = { schemaVersion: 'TEAM_PLAYER_INFORMATION_PLAYERS_V1', leagueCode: 'LCK', catalog, filters: { teamCode: null, position: null }, players };
const workspace = validateWorkspace(metadata, teamsResponse, playersResponse);
const summary = players.find((player) => player.playerId === 'player-gen-mid');
const ratings = Array.from({ length: 12 }, (_, index) => ({ key: `rating-${index}`, skill: `SKILL_${index}`, displayNameKo: `능력치 ${index + 1}`, value: 10 + index % 10 }));
const detailResponse = {
  schemaVersion: 'TEAM_PLAYER_INFORMATION_PLAYER_V1', leagueCode: 'LCK', catalog,
  player: {
    summary,
    snapshotSemantics: { snapshotAt: '2026-08-24', ageMeaning: 'snapshot age', contractDaysMeaning: 'snapshot remaining days', prizeMoneyMeaning: 'approximate public winnings' },
    personal: { legalName: 'Legal Name', birthDate: summary.birthDate, ageAtSnapshot: 25, nationality: ['KR'] },
    contract: { endDate: summary.contractEndDate, daysRemainingAtSnapshot: 448, status: summary.contractStatus, sourceType: 'PUBLIC', sourceSnapshotAt: '2026-08-24', checkedAt: '2026-08-24' },
    career: { debutDate: '2020-01-01', yearsActiveAtSnapshot: 6.5, teamHistory: Array.from({ length: 6 }, (_, index) => ({ team: `TEAM-${index}`, from: `202${index}-01`, to: index === 5 ? null : `202${index}-12`, role: 'MID', datePrecision: 'MONTH' })), coverage: 'MAJOR_TEAMS' },
    honors: { teamAchievements: [], individualAwards: [], coverage: 'MAJOR_HONORS_ONLY' },
    careerPrizeMoney: { amountUsd: 123456.78, currency: 'USD', status: 'APPROXIMATE', sourceType: 'PUBLIC', checkedAt: null, meaning: 'approximate public tournament winnings' },
    dataQuality: { personal: 'VERIFIED', contract: 'VERIFIED', career: 'CURATED', honors: 'MAJOR_ONLY', prizeMoney: 'APPROXIMATE' },
    ratings: { scaleMin: 1, scaleMax: 20, resourceVersion: 'ratings-v1', attributes: ratings },
    championProficiency: { scaleMin: 1, scaleMax: 20, neutralFallback: 14, sparseOverridesOnly: true, omittedLegalRoleBehavior: 'Omitted legal role keys resolve to 14.', resourceVersion: 'proficiency-v1', authoredEntryCount: 2, authoredEntries: [
      { championId: 'azir', displayNameKo: '아지르', displayNameEn: 'Azir', portraitUrl: 'https://example.test/azir.png', position: 'MID', value: 20 },
      { championId: 'orianna', displayNameKo: '오리아나', displayNameEn: 'Orianna', portraitUrl: 'https://example.test/orianna.png', position: 'MID', value: 18 },
    ] },
    sources: [{ type: 'PROFILE', path: null, url: 'https://example.test/profile', checkedAt: null, sourceSnapshotAt: '2026-08-24' }],
  },
};

function accepts(label, action) {
  try { action(); console.log(`PASS ${label}`); }
  catch (error) { console.error(`FAIL ${label}`, error); process.exitCode = 1; }
}

async function acceptsAsync(label, action) {
  try { await action(); console.log(`PASS ${label}`); }
  catch (error) { console.error(`FAIL ${label}`, error); process.exitCode = 1; }
}

function rejects(label, action) {
  try { action(); console.error(`FAIL ${label}: invalid value accepted`); process.exitCode = 1; }
  catch { console.log(`PASS ${label}`); }
}

accepts('metadata, 10 teams, 50 players and roster join', () => {
  if (workspace.teams.teams.length !== 10 || workspace.players.players.length !== 50) throw new Error('catalog scope');
});
accepts('valid 12 ratings, sparse proficiency, nullable career/source', () => {
  const validated = validatePlayerResponse(detailResponse, summary, catalog);
  const profile = createPlayerProfile(validated);
  if (profile.ratings.length !== 12 || profile.proficiencies.length !== 2 || profile.teamHistory[5].toLabel !== '현재' || profile.sources[0].path !== null) throw new Error('profile projection');
});
accepts('additive response fields are accepted', () => {
  const value = clone(detailResponse); value.futureField = { additive: true }; value.player.summary.futureDisplay = 'safe';
  validatePlayerResponse(value, summary, catalog);
});
rejects('wrong schema is fail-closed', () => { const value = clone(teamsResponse); value.schemaVersion = 'WRONG'; validateWorkspace(metadata, value, playersResponse); });
rejects('catalog generation mismatch is fail-closed', () => { const value = clone(playersResponse); value.catalog.catalogHash = 'b'.repeat(64); validateWorkspace(metadata, teamsResponse, value); });
rejects('lineup/player identity mismatch is fail-closed', () => { const value = clone(playersResponse); value.players[0].nickname = 'mismatch'; validateWorkspace(metadata, teamsResponse, value); });
rejects('rating count mismatch is fail-closed', () => { const value = clone(detailResponse); value.player.ratings.attributes.pop(); validatePlayerResponse(value, summary, catalog); });
rejects('authored proficiency count mismatch is fail-closed', () => { const value = clone(detailResponse); value.player.championProficiency.authoredEntryCount = 3; validatePlayerResponse(value, summary, catalog); });
rejects('proficiency canonical order mismatch is fail-closed', () => { const value = clone(detailResponse); value.player.championProficiency.authoredEntries.reverse(); validatePlayerResponse(value, summary, catalog); });
accepts('structured 400/404/500 error DTOs', () => {
  ['REFERENCE_QUERY_INVALID', 'REFERENCE_PLAYER_NOT_FOUND', 'PLAYER_INFORMATION_RESOURCE_INTEGRITY_FAILURE'].forEach((code) => validateErrorResponse({ schemaVersion: 'TEAM_PLAYER_INFORMATION_API_ERROR_V1', code, field: code === 'REFERENCE_QUERY_INVALID' ? 'position' : null, message: 'safe' }));
});
accepts('only http and https source schemes become links', () => {
  if (!safeHttpUrl('https://example.test/source') || safeHttpUrl('javascript:alert(1)') !== null || safeHttpUrl('file:///tmp/source') !== null) throw new Error('unsafe link policy');
});
accepts('first canonical selection is not GEN/Chovy hardcoded', () => {
  const storage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
  const initial = resolveInitialSelection(workspace, storage);
  if (initial.teamCode !== 'BFX' || initial.playerId !== 'player-bfx-top') throw new Error('canonical first selection');
});
accepts('stored pointer is revalidated and contains no authoritative detail', () => {
  const data = new Map();
  const storage = { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: (key) => data.delete(key) };
  const selected = { teamCode: 'GEN', playerId: 'player-gen-mid' };
  writeSelection(storage, selected, catalog.catalogVersion);
  const restored = resolveInitialSelection(workspace, storage);
  const raw = data.values().next().value;
  if (restored.playerId !== selected.playerId || raw.includes('ratings') || raw.includes('contractEndDate')) throw new Error('pointer boundary');
  data.set('lolmanager.team-player.pointer.v1', JSON.stringify({ schemaVersion: 'TEAM_PLAYER_NAVIGATION_POINTER_V1', catalogVersion: catalog.catalogVersion, teamCode: 'GEN', playerId: 'player-unknown' }));
  if (resolveInitialSelection(workspace, storage).playerId !== 'player-bfx-top') throw new Error('unknown pointer was trusted');
});
accepts('team selection uses teamCode and first Position entry', () => {
  const selected = selectTeam(workspace, 'T1');
  if (selected.playerId !== 'player-t1-top') throw new Error('team selection order');
});
await acceptsAsync('same detail is deduplicated and stale detail is aborted', async () => {
  const coordinator = new PlayerDetailRequestCoordinator();
  const first = coordinator.load('player-a', (signal) => new Promise((resolve, reject) => {
    const timer = setTimeout(() => resolve('old'), 50);
    signal.addEventListener('abort', () => { clearTimeout(timer); reject(new DOMException('aborted', 'AbortError')); }, { once: true });
  }));
  const duplicate = coordinator.load('player-a', () => Promise.resolve('duplicate'));
  if (first !== duplicate) throw new Error('same player request duplicated');
  const firstOutcome = first.then(() => 'resolved', (error) => error.name);
  const latest = coordinator.load('player-b', () => Promise.resolve('latest'));
  if (await latest !== 'latest' || await firstOutcome !== 'AbortError') throw new Error('stale request was not aborted');
});

if (!process.exitCode) console.log('TEAM_AND_PLAYER_INFORMATION_FRONTEND_V1_CONTRACT_VERIFICATION_PASSED');

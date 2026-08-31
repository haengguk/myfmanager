import { baseSession, clone, completedSession, simulatedSession, simulation } from './verify-player-draft-contract.mjs';
import {
  validateSeriesApiErrorPayload,
  validateSeriesDraftResponsePayload,
  validateSeriesMatchPayload,
  validateSeriesReplayEnvelopePayload,
  validateSeriesSimulationEnvelopePayload,
  validateSeriesViewPayload,
} from '../src/features/real-match/series/api/seriesApi.validation.ts';
import { createSeriesRequest, shouldApplySeries } from '../src/features/real-match/series/series.adapter.ts';
import { clearSeriesPointer, readSeriesPointer, writeSeriesPointer } from '../src/features/real-match/series/series.pointer.ts';

const H = 'a'.repeat(64); const G = 'b'.repeat(64); const D = 'c'.repeat(64);
const seriesId = `series_${H}`; const gameId = (number) => `game_${String(number).repeat(64).slice(0, 64)}`;
const childId = (number) => `draft_${String(number + 2).repeat(64).slice(0, 64)}`;
const now = '2026-08-31T01:00:00Z'; const later = '2026-08-31T03:00:00Z';
const teams = [{ teamCode: 'DK', displayName: 'Dplus KIA' }, { teamCode: 'HLE', displayName: 'Hanwha Life Esports' }];
const productionIdentity = {
  policyId: 'PRODUCTION', policyHash: H, runtimeProfileId: 'PRODUCTION_MATCHUP_COMPOSITION_V1',
  configurationHash: H, activeGameplayRulesVersion: 'V1', engineImplementationVersion: 'MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9',
  draftMetaVersion: 'DRAFT_META_V1', requiredLegalRoleKeyHash: H, actualLegalRoleKeyHash: H,
};
const receipt = {
  schemaVersion: 'SERIES_GAME_RECEIPT_V1', inputHash: H, replayProvenanceHash: H,
  resourceProvenanceHash: H, finalDraftHash: H, finalAssignmentHash: D, controlEvidenceHash: G,
  simulatorTimelineHash: H, structuredTimelineHash: H, outputHash: H, randomDrawCount: 1, randomTraceHash: H,
};

const sideFor = (managed, blue) => managed === blue ? 'BLUE' : 'RED';
function game(number, { status = 'DRAFT_PENDING', blue = number % 2 ? 'DK' : 'HLE', managed = 'DK', seed = String(number * 100), history = [], child = null, result = null, gameReceipt = null } = {}) {
  return {
    schemaVersion: 'SERIES_GAME_VIEW_V1', gameId: gameId(number), gameNumber: number, status, reason: null,
    blueTeamCode: blue, redTeamCode: blue === 'DK' ? 'HLE' : 'DK', controlledSide: sideFor(managed, blue),
    matchSeed: seed, historyBeforeChampionIds: [...history].sort(), historyBeforeHash: H,
    childDraftSessionId: child?.sessionId ?? null, childDraftStatus: child?.status ?? null,
    childDraftRevision: child?.revision ?? null, result, receipt: gameReceipt,
  };
}
function view({ format = 'BO3', managed = 'DK', status = 'ACTIVE', score = { DK: 0, HLE: 0 }, games = [game(1, { managed })], excluded = [], activeDraftSession = null, reservation = null, commands = ['CREATE_DRAFT_SESSION', 'CANCEL_SERIES'], winner = null } = {}) {
  const current = games[games.length - 1];
  return {
    schemaVersion: 'SERIES_VIEW_V1', seriesId, revision: 0, status, terminalReason: status === 'BLOCKED' ? 'HARD_FEARLESS_LEGAL_POOL_EXHAUSTED' : status === 'CANCELLED' ? 'CANCELLED_BY_CLIENT' : null,
    format, winsRequired: format === 'BO3' ? 2 : 3, teams, managedTeamCode: managed,
    opponentTeamCode: managed === 'DK' ? 'HLE' : 'DK', score, currentGameNumber: current.gameNumber,
    rootSeed: '-73', seedDerivationAlgorithm: 'SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1',
    currentGameSeed: current.matchSeed, excludedChampionIds: [...excluded].sort(), seriesHistoryBeforeHash: H,
    games, activeDraftSession, reservation, allowedCommands: commands, winnerTeamCode: winner,
    createdAt: now, lastActivityAt: now, expiresAt: later, processLocalRestartLoss: true, productionIdentity,
  };
}
function sessionFrom(source, { status = source.status, number = 1, blue = 'DK', red = 'HLE', seed = '100', id = childId(number), history = [] } = {}) {
  const value = clone(source); value.sessionId = id; value.status = status; value.seriesGameNumber = number; value.seed = seed;
  value.teams[0].teamCode = blue; value.teams[0].displayName = blue; value.teams[1].teamCode = red; value.teams[1].displayName = red;
  value.state.hardFearlessExclusions = [...history].sort(); return value;
}
function child(series, gameValue, session) {
  return {
    schemaVersion: 'SERIES_CHILD_DRAFT_SESSION_V1',
    binding: {
      seriesId: series.seriesId, gameId: gameValue.gameId, gameNumber: gameValue.gameNumber,
      blueTeamCode: gameValue.blueTeamCode, redTeamCode: gameValue.redTeamCode,
      managedTeamCode: series.managedTeamCode, controlledSide: gameValue.controlledSide,
      matchSeed: gameValue.matchSeed, hardFearlessExclusions: gameValue.historyBeforeChampionIds,
      historyBeforeHash: gameValue.historyBeforeHash,
    }, session,
  };
}
function withChild(sourceSession, status) {
  const session = sessionFrom(sourceSession, { status });
  const gameValue = game(1, { status: status === 'ACTIVE' ? 'DRAFT_ACTIVE' : status === 'COMPLETED' ? 'DRAFT_COMPLETED' : 'COMMITTED', child: session });
  const base = view({ games: [gameValue], commands: status === 'ACTIVE' ? ['SUBMIT_DRAFT_ACTION', 'CANCEL_DRAFT_SESSION', 'CANCEL_SERIES'] : status === 'COMPLETED' ? ['SIMULATE', 'CANCEL_SERIES'] : ['GET'] });
  const envelope = child(base, gameValue, session); base.activeDraftSession = envelope; return { base, gameValue, envelope };
}

function accepts(label, fn) { try { fn(); console.log(`PASS ${label}`); } catch (error) { console.error(`FAIL ${label}`, error); process.exitCode = 1; } }
function rejects(label, mutate, validator = validateSeriesViewPayload) {
  try { validator(mutate()); console.error(`FAIL ${label}: accepted invalid payload`); process.exitCode = 1; }
  catch { console.log(`PASS ${label}`); }
}

const pendingBo3 = view();
const pendingBo5Red = view({ format: 'BO5', managed: 'HLE', games: [game(1, { managed: 'HLE', blue: 'DK', seed: '-100' })] });
const active = withChild(baseSession, 'ACTIVE');
const completed = withChild(completedSession, 'COMPLETED');

accepts('arbitrary LIVE team pair and managed-team create mapping', () => {
  const request = createSeriesRequest({ format: 'BO5', managedTeamCode: 'HLE', opponentTeamCode: 'KT', game1ManagedSide: 'RED', rootSeed: '-73' }, 'series-create-1');
  if (request.teamACode !== 'HLE' || request.teamBCode !== 'KT' || request.managedTeamCode !== 'HLE' || request.game1BlueTeamCode !== 'KT') throw new Error('explicit team/side mapping mismatch');
});
accepts('valid BO3 create view', () => validateSeriesViewPayload(pendingBo3));
accepts('valid BO5 managed RED create view', () => validateSeriesViewPayload(pendingBo5Red));
accepts('DRAFT_PENDING to DRAFT_ACTIVE child binding', () => validateSeriesDraftResponsePayload({ series: active.base, draftSession: active.envelope, replayed: false }));
accepts('PLAYER/AI 20-turn DRAFT_COMPLETED evidence', () => validateSeriesDraftResponsePayload({ series: completed.base, draftSession: completed.envelope, replayed: false }));

const inProgressSession = sessionFrom(completedSession, { status: 'COMPLETED' });
const inProgressGame = game(1, { status: 'SIMULATION_IN_PROGRESS', child: inProgressSession });
const inProgressSeries = view({ games: [inProgressGame], activeDraftSession: null, reservation: { commandId: 'simulate-1', createdAt: now, leaseExpiresAt: later }, commands: ['GET', 'CANCEL_SERIES'] });
inProgressSeries.activeDraftSession = child(inProgressSeries, inProgressGame, inProgressSession);
accepts('simulate 202 reservation with null match', () => validateSeriesSimulationEnvelopePayload({ schemaVersion: 'SERIES_SIMULATION_RESPONSE_V1', replayedCommand: false, series: inProgressSeries, game: inProgressGame, match: null }));

const usedPicks = [...completedSession.state.bluePicks, ...completedSession.state.redPicks].sort();
const simulated = sessionFrom(simulatedSession, { status: 'SIMULATED' });
const compact = { winnerTeamCode: 'DK', winnerSide: 'BLUE', endReason: 'NEXUS_DESTROYED', durationSeconds: 1, teamKills: { DK: 0, HLE: 0 }, teamGold: { DK: 2500, HLE: 2500 } };
const committedGame = game(1, { status: 'COMMITTED', child: simulated, result: compact, gameReceipt: receipt });
const nextGame = game(2, { blue: 'HLE', managed: 'DK', seed: '-200', history: usedPicks });
const committedSeries = view({ score: { DK: 1, HLE: 0 }, games: [committedGame, nextGame], excluded: usedPicks });
const committedChild = child(committedSeries, committedGame, simulated);
const full = clone(simulation.match); full.seed = '100';
full.teams[0].teamCode = 'DK'; full.teams[0].displayName = 'DK'; full.teams[1].teamCode = 'HLE'; full.teams[1].displayName = 'HLE';
full.result.winner = 'BLUE'; full.result.endReason = 'NEXUS_DESTROYED'; full.result.teams[0].teamIdentity = 'DK'; full.result.teams[1].teamIdentity = 'HLE'; full.result.teams[1].nexusAlive = false;
full.timeline.winner = 'BLUE'; full.timeline.endReason = 'NEXUS_DESTROYED';
full.timeline.snapshots.forEach((snapshot) => { snapshot.blueTeam.teamIdentity = 'DK'; snapshot.redTeam.teamIdentity = 'HLE'; });
full.timeline.snapshots[full.timeline.snapshots.length - 1].redTeam.nexusAlive = false;

accepts('simulate 200 full match envelope', () => validateSeriesSimulationEnvelopePayload({ schemaVersion: 'SERIES_SIMULATION_RESPONSE_V1', replayedCommand: false, series: committedSeries, game: committedGame, match: full }));
accepts('Series full match common semantic and production binding', () => validateSeriesMatchPayload(full, committedSeries, committedGame, committedChild));
accepts('committed null match followed by explicit replay envelope', () => {
  validateSeriesSimulationEnvelopePayload({ schemaVersion: 'SERIES_SIMULATION_RESPONSE_V1', replayedCommand: true, series: committedSeries, game: committedGame, match: null });
  validateSeriesReplayEnvelopePayload({ schemaVersion: 'SERIES_GAME_REPLAY_RESPONSE_V1', series: committedSeries, game: committedGame, match: full });
});
accepts('score update, next game side alternation, and history-before 10', () => {
  const value = validateSeriesViewPayload(committedSeries); if (value.score.DK !== 1 || value.currentGameNumber !== 2 || value.games[1].blueTeamCode !== 'HLE' || value.games[1].historyBeforeChampionIds.length !== 10) throw new Error('authoritative progression mismatch');
});

function terminalSeries(format, wins) {
  const count = wins; const exclusions = Array.from({ length: count * 10 }, (_, index) => `used-${String(index + 1).padStart(2, '0')}`);
  const gameValues = Array.from({ length: count }, (_, index) => game(index + 1, {
    status: 'COMMITTED', blue: index % 2 ? 'HLE' : 'DK', managed: 'DK', seed: String(300 + index), history: exclusions.slice(0, index * 10),
    result: { ...compact, winnerSide: index % 2 ? 'RED' : 'BLUE' }, gameReceipt: receipt,
  }));
  return view({ format, status: 'COMPLETED', score: { DK: wins, HLE: 0 }, games: gameValues, excluded: exclusions, commands: ['GET'], winner: 'DK' });
}
accepts('BO3 two-win completion and winner', () => validateSeriesViewPayload(terminalSeries('BO3', 2)));
accepts('BO5 three-win completion and winner', () => validateSeriesViewPayload(terminalSeries('BO5', 3)));
accepts('cancelled, blocked, and expired terminal views', () => {
  validateSeriesViewPayload(view({ status: 'CANCELLED', commands: ['GET'] }));
  validateSeriesViewPayload(view({ status: 'BLOCKED', commands: ['GET', 'CANCEL_SERIES'] }));
  const expired = view({ status: 'EXPIRED', commands: ['GET'] }); expired.terminalReason = 'PARENT_TTL_EXPIRED'; validateSeriesViewPayload(expired);
});

rejects('schema mismatch rejection', () => ({ ...clone(pendingBo3), schemaVersion: 'WRONG' }));
rejects('unknown field rejection', () => ({ ...clone(pendingBo3), optimisticScore: 1 }));
rejects('managed/opponent mismatch rejection', () => ({ ...clone(pendingBo3), opponentTeamCode: 'DK' }));
rejects('score key mismatch rejection', () => ({ ...clone(pendingBo3), score: { DK: 0, KT: 0 } }));
rejects('current game order mismatch rejection', () => ({ ...clone(pendingBo3), currentGameNumber: 2 }));
rejects('controlled side mismatch rejection', () => { const value = clone(pendingBo3); value.games[0].controlledSide = 'RED'; return value; });
rejects('current seed mismatch rejection', () => ({ ...clone(pendingBo3), currentGameSeed: '999' }));
rejects('history count mismatch rejection', () => ({ ...clone(committedSeries), excludedChampionIds: [] }));
rejects('child binding/session mismatch rejection', () => { const value = clone(active); value.envelope.binding.gameId = gameId(2); return { series: value.base, draftSession: value.envelope, replayed: false }; }, validateSeriesDraftResponsePayload);
rejects('invalid allowed command rejection', () => ({ ...clone(pendingBo3), allowedCommands: ['SIMULATE_GAME'] }));
rejects('winner/required wins mismatch rejection', () => ({ ...clone(terminalSeries('BO3', 2)), score: { DK: 1, HLE: 1 } }));
rejects('compact result team map mismatch rejection', () => { const value = clone(committedSeries); value.games[0].result.teamGold = { DK: 2500, KT: 2500 }; return value; });
rejects('compact receipt mismatch rejection', () => { const value = clone(committedSeries); value.games[0].receipt.outputHash = 'not-a-hash'; return value; });
rejects('production identity mismatch rejection', () => { const value = clone(pendingBo3); value.productionIdentity.policyHash = 'bad'; return value; });
rejects('full match participant binding mismatch rejection', () => { const value = clone(full); value.teams[0].teamCode = 'KT'; return value; }, (value) => validateSeriesMatchPayload(value, committedSeries, committedGame, committedChild));
rejects('malformed structured error rejection', () => ({ schemaVersion: 'SERIES_API_ERROR_V1', code: 'SERIES_STALE_REVISION', field: null, message: 'stale' }), validateSeriesApiErrorPayload);
accepts('valid structured error', () => validateSeriesApiErrorPayload({ schemaVersion: 'SERIES_API_ERROR_V1', code: 'SERIES_STALE_REVISION', field: null, message: 'stale', retryable: false, currentRevision: 1, currentStatus: 'ACTIVE' }));
accepts('stale revision ordering rejection', () => { const next = { ...clone(pendingBo3), revision: 2 }; if (!shouldApplySeries(pendingBo3, next) || shouldApplySeries(next, pendingBo3)) throw new Error('revision ordering mismatch'); });
accepts('sessionStorage pointer resumes by Series ID only', () => {
  const values = new Map(); const storage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) };
  writeSeriesPointer(storage, seriesId); if (readSeriesPointer(storage) !== seriesId || values.size !== 1) throw new Error('pointer mismatch'); clearSeriesPointer(storage); if (readSeriesPointer(storage) !== null) throw new Error('pointer not cleared');
});

if (!process.exitCode) console.log('SERIES_FRONTEND_CONTRACT_VERIFICATION_PASSED');

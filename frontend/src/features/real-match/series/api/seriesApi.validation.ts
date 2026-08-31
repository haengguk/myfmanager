import type { PlayerDraftSessionResponseDto } from '../../player-draft/api/playerDraftApi.types';
import {
  validatePlayerDraftSessionPayload,
  validatePlayerDraftSimulationPayload,
} from '../../player-draft/api/playerDraftApi.validation.ts';
import type { TeamSide } from '../../realMatch.contract';
import type {
  SeriesAllowedCommand,
  SeriesApiErrorDto,
  SeriesBindingDto,
  SeriesChildDraftEnvelopeDto,
  SeriesDraftResponseDto,
  SeriesFormat,
  SeriesGameStatus,
  SeriesGameViewDto,
  SeriesReplayEnvelopeDto,
  SeriesSimulationEnvelopeDto,
  SeriesStatus,
  SeriesViewDto,
} from './seriesApi.types';

type JsonRecord = Record<string, unknown>;

const SIDES = ['BLUE', 'RED'] as const;
const FORMATS = ['BO3', 'BO5'] as const;
const SERIES_STATUSES = ['ACTIVE', 'BLOCKED', 'COMPLETED', 'CANCELLED', 'EXPIRED'] as const;
const GAME_STATUSES = [
  'DRAFT_PENDING', 'DRAFT_ACTIVE', 'DRAFT_COMPLETED', 'SIMULATION_IN_PROGRESS',
  'SIMULATION_FAILED_RETRYABLE', 'BLOCKED', 'COMMITTED', 'DRAFT_CANCELLED', 'DRAFT_EXPIRED',
] as const;
const DRAFT_STATUSES = ['ACTIVE', 'COMPLETED', 'SIMULATED', 'CANCELLED', 'EXPIRED'] as const;
const ALLOWED_COMMANDS = [
  'GET', 'CREATE_DRAFT_SESSION', 'SUBMIT_DRAFT_ACTION', 'CANCEL_DRAFT_SESSION',
  'SIMULATE', 'CANCEL_SERIES',
] as const;
const SERIES_ID = /^series_[0-9a-f]{64}$/;
const GAME_ID = /^game_[0-9a-f]{64}$/;
const CHILD_ID = /^draft_[0-9a-f]{64}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const TEAM_CODE = /^[A-Z0-9]{2,8}$/;

export class SeriesContractError extends Error {
  public readonly path: string;

  constructor(path: string, message: string) {
    super(`${path}: ${message}`);
    this.name = 'SeriesContractError';
    this.path = path;
  }
}

function fail(path: string, message: string): never { throw new SeriesContractError(path, message); }
function record(value: unknown, path: string): JsonRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) fail(path, 'JSON 객체가 필요합니다.');
  return value as JsonRecord;
}
function exact(source: JsonRecord, keys: readonly string[], path: string): void {
  const actual = Object.keys(source).sort(); const expected = [...keys].sort();
  if (actual.length !== expected.length || actual.some((key, index) => key !== expected[index])) {
    fail(path, `필드 구성이 일치하지 않습니다. expected=${expected.join(',')}, actual=${actual.join(',')}`);
  }
}
function array(value: unknown, path: string): readonly unknown[] {
  if (!Array.isArray(value)) fail(path, '배열이 필요합니다.');
  return value;
}
function text(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.length === 0) fail(path, '비어 있지 않은 문자열이 필요합니다.');
  return value;
}
function nullableText(value: unknown, path: string): string | null { return value === null ? null : text(value, path); }
function integer(value: unknown, path: string, minimum = 0): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) fail(path, `${minimum} 이상의 안전한 정수가 필요합니다.`);
  return value;
}
function bool(value: unknown, path: string): boolean {
  if (typeof value !== 'boolean') fail(path, 'boolean 값이 필요합니다.');
  return value;
}
function literal(value: unknown, expected: string, path: string): void {
  if (value !== expected) fail(path, `${expected}가 필요합니다.`);
}
function oneOf<T extends string>(value: unknown, values: readonly T[], path: string): T {
  if (typeof value !== 'string' || !values.includes(value as T)) fail(path, `지원하지 않는 enum 값입니다: ${String(value)}`);
  return value as T;
}
function nullableOneOf<T extends string>(value: unknown, values: readonly T[], path: string): T | null {
  return value === null ? null : oneOf(value, values, path);
}
function identity(value: unknown, pattern: RegExp, path: string, label: string): string {
  const result = text(value, path); if (!pattern.test(result)) fail(path, `${label} 형식이 필요합니다.`); return result;
}
function sha(value: unknown, path: string): string { return identity(value, SHA256, path, '소문자 SHA-256'); }
function side(value: unknown, path: string): TeamSide { return oneOf(value, SIDES, path); }
function signedInt64(value: unknown, path: string): string {
  const result = text(value, path);
  if (!/^(0|-?[1-9][0-9]*)$/.test(result)) fail(path, 'canonical signed-long decimal string이 필요합니다.');
  try {
    const parsed = BigInt(result);
    if (parsed < -9223372036854775808n || parsed > 9223372036854775807n) fail(path, 'signed-long 범위를 벗어났습니다.');
  } catch { fail(path, 'signed-long 값을 해석할 수 없습니다.'); }
  return result;
}
function instant(value: unknown, path: string): string {
  const result = text(value, path); if (!Number.isFinite(Date.parse(result))) fail(path, 'ISO-8601 timestamp가 필요합니다.'); return result;
}
function uniqueStrings(value: unknown, path: string, sorted = false): readonly string[] {
  const result = array(value, path).map((entry, index) => text(entry, `${path}[${index}]`));
  if (new Set(result).size !== result.length) fail(path, '중복 문자열을 허용하지 않습니다.');
  if (sorted && result.some((entry, index) => index > 0 && result[index - 1].localeCompare(entry) > 0)) fail(path, 'canonical 오름차순이어야 합니다.');
  return result;
}
function exactValues(actual: readonly string[], expected: readonly string[], path: string): void {
  if (actual.length !== expected.length || actual.some((value, index) => value !== expected[index])) fail(path, 'authoritative binding과 정확히 일치해야 합니다.');
}

function validateTeam(value: unknown, path: string): string {
  const source = record(value, path); exact(source, ['teamCode', 'displayName'], path);
  const code = text(source.teamCode, `${path}.teamCode`); if (!TEAM_CODE.test(code)) fail(`${path}.teamCode`, 'stable team code 형식이 필요합니다.');
  text(source.displayName, `${path}.displayName`); return code;
}

function validateIntegerMap(value: unknown, expectedKeys: readonly string[], path: string): Readonly<Record<string, number>> {
  const source = record(value, path); exact(source, expectedKeys, path);
  expectedKeys.forEach((key) => integer(source[key], `${path}.${key}`));
  return source as Readonly<Record<string, number>>;
}

function validateReceipt(value: unknown, path: string): void {
  const source = record(value, path);
  exact(source, [
    'schemaVersion', 'inputHash', 'replayProvenanceHash', 'resourceProvenanceHash',
    'finalDraftHash', 'finalAssignmentHash', 'controlEvidenceHash', 'simulatorTimelineHash',
    'structuredTimelineHash', 'outputHash', 'randomDrawCount', 'randomTraceHash',
  ], path);
  text(source.schemaVersion, `${path}.schemaVersion`);
  for (const key of [
    'inputHash', 'replayProvenanceHash', 'resourceProvenanceHash', 'finalDraftHash',
    'finalAssignmentHash', 'controlEvidenceHash', 'simulatorTimelineHash',
    'structuredTimelineHash', 'outputHash', 'randomTraceHash',
  ]) sha(source[key], `${path}.${key}`);
  integer(source.randomDrawCount, `${path}.randomDrawCount`);
}

function validateResult(value: unknown, teams: readonly string[], game: JsonRecord, path: string): string | null {
  const source = record(value, path);
  exact(source, ['winnerTeamCode', 'winnerSide', 'endReason', 'durationSeconds', 'teamKills', 'teamGold'], path);
  const winnerCode = source.winnerTeamCode === null ? null : text(source.winnerTeamCode, `${path}.winnerTeamCode`);
  const winnerSide = source.winnerSide === null ? null : side(source.winnerSide, `${path}.winnerSide`);
  if ((winnerCode === null) !== (winnerSide === null)) fail(path, 'winner team과 side는 함께 존재하거나 함께 null이어야 합니다.');
  if (winnerCode !== null) {
    const expected = winnerSide === 'BLUE' ? game.blueTeamCode : game.redTeamCode;
    if (winnerCode !== expected || !teams.includes(winnerCode)) fail(`${path}.winnerTeamCode`, 'game side mapping과 일치하지 않습니다.');
  }
  text(source.endReason, `${path}.endReason`); integer(source.durationSeconds, `${path}.durationSeconds`, 1);
  validateIntegerMap(source.teamKills, teams, `${path}.teamKills`);
  validateIntegerMap(source.teamGold, teams, `${path}.teamGold`);
  return winnerCode;
}

function validateGame(value: unknown, teams: readonly string[], managed: string, expectedNumber: number, path: string): SeriesGameViewDto {
  const source = record(value, path);
  exact(source, [
    'schemaVersion', 'gameId', 'gameNumber', 'status', 'reason', 'blueTeamCode', 'redTeamCode',
    'controlledSide', 'matchSeed', 'historyBeforeChampionIds', 'historyBeforeHash',
    'childDraftSessionId', 'childDraftStatus', 'childDraftRevision', 'result', 'receipt',
  ], path);
  literal(source.schemaVersion, 'SERIES_GAME_VIEW_V1', `${path}.schemaVersion`);
  identity(source.gameId, GAME_ID, `${path}.gameId`, 'Series game ID');
  if (integer(source.gameNumber, `${path}.gameNumber`, 1) !== expectedNumber) fail(`${path}.gameNumber`, 'game은 1부터 연속 오름차순이어야 합니다.');
  const status = oneOf(source.status, GAME_STATUSES, `${path}.status`) as SeriesGameStatus;
  nullableText(source.reason, `${path}.reason`);
  const blue = text(source.blueTeamCode, `${path}.blueTeamCode`); const red = text(source.redTeamCode, `${path}.redTeamCode`);
  if (blue === red || !teams.includes(blue) || !teams.includes(red)) fail(path, 'BLUE/RED는 참가 팀의 정확한 두 team code여야 합니다.');
  const controlled = side(source.controlledSide, `${path}.controlledSide`);
  if ((controlled === 'BLUE' ? blue : red) !== managed) fail(`${path}.controlledSide`, 'managed team의 game side와 일치해야 합니다.');
  signedInt64(source.matchSeed, `${path}.matchSeed`);
  uniqueStrings(source.historyBeforeChampionIds, `${path}.historyBeforeChampionIds`, true);
  sha(source.historyBeforeHash, `${path}.historyBeforeHash`);
  const childId = source.childDraftSessionId === null ? null : identity(source.childDraftSessionId, CHILD_ID, `${path}.childDraftSessionId`, 'Series child Draft ID');
  const childStatus = nullableOneOf(source.childDraftStatus, DRAFT_STATUSES, `${path}.childDraftStatus`);
  const childRevision = source.childDraftRevision === null ? null : integer(source.childDraftRevision, `${path}.childDraftRevision`);
  if ([childId, childStatus, childRevision].filter((entry) => entry !== null).length !== 0
    && [childId, childStatus, childRevision].some((entry) => entry === null)) fail(path, 'child Draft identity/status/revision은 함께 존재해야 합니다.');
  const resultWinner = source.result === null ? null : validateResult(source.result, teams, source, `${path}.result`);
  if (source.receipt !== null) validateReceipt(source.receipt, `${path}.receipt`);
  if ((source.result === null) !== (source.receipt === null)) fail(path, 'compact result와 receipt는 함께 존재해야 합니다.');
  if (status === 'COMMITTED' && (source.result === null || source.receipt === null || resultWinner === null)) {
    fail(path, 'COMMITTED game에는 승자가 있는 compact result와 receipt가 필요합니다.');
  }
  if (status !== 'COMMITTED' && status !== 'BLOCKED' && (source.result !== null || source.receipt !== null)) {
    fail(path, `${status} game에는 compact result/receipt가 존재할 수 없습니다.`);
  }
  if (status === 'BLOCKED' && resultWinner !== null) fail(`${path}.result.winnerTeamCode`, 'BLOCKED game의 compact result는 승자를 가질 수 없습니다.');
  return value as SeriesGameViewDto;
}

function validateBinding(value: unknown, series: SeriesViewDto, game: SeriesGameViewDto, path: string): SeriesBindingDto {
  const source = record(value, path);
  exact(source, [
    'seriesId', 'gameId', 'gameNumber', 'blueTeamCode', 'redTeamCode', 'managedTeamCode',
    'controlledSide', 'matchSeed', 'hardFearlessExclusions', 'historyBeforeHash',
  ], path);
  if (source.seriesId !== series.seriesId || source.gameId !== game.gameId || source.gameNumber !== game.gameNumber
    || source.blueTeamCode !== game.blueTeamCode || source.redTeamCode !== game.redTeamCode
    || source.managedTeamCode !== series.managedTeamCode || source.controlledSide !== game.controlledSide
    || source.matchSeed !== game.matchSeed || source.historyBeforeHash !== game.historyBeforeHash) fail(path, 'Series/game parent binding과 일치하지 않습니다.');
  const exclusions = uniqueStrings(source.hardFearlessExclusions, `${path}.hardFearlessExclusions`, true);
  exactValues(exclusions, game.historyBeforeChampionIds, `${path}.hardFearlessExclusions`);
  return value as SeriesBindingDto;
}

function validateChild(value: unknown, series: SeriesViewDto, game: SeriesGameViewDto, path: string): SeriesChildDraftEnvelopeDto {
  const source = record(value, path); exact(source, ['schemaVersion', 'binding', 'session'], path);
  literal(source.schemaVersion, 'SERIES_CHILD_DRAFT_SESSION_V1', `${path}.schemaVersion`);
  const binding = validateBinding(source.binding, series, game, `${path}.binding`);
  const session = validatePlayerDraftSessionPayload(source.session, {
    sessionId: game.childDraftSessionId ?? undefined,
    blueTeamCode: game.blueTeamCode,
    redTeamCode: game.redTeamCode,
    controlledSide: game.controlledSide,
    seed: game.matchSeed,
    seriesGameNumber: game.gameNumber,
    hardFearlessExclusions: binding.hardFearlessExclusions,
    sessionIdentity: 'SERIES_CHILD',
  });
  if (game.childDraftSessionId !== session.sessionId || game.childDraftStatus !== session.status || game.childDraftRevision !== session.revision) {
    fail(`${path}.session`, 'game child identity/status/revision과 일치하지 않습니다.');
  }
  return value as SeriesChildDraftEnvelopeDto;
}

function validateProductionIdentity(value: unknown, path: string): void {
  const source = record(value, path);
  exact(source, [
    'policyId', 'policyHash', 'runtimeProfileId', 'configurationHash',
    'activeGameplayRulesVersion', 'engineImplementationVersion', 'draftMetaVersion',
    'requiredLegalRoleKeyHash', 'actualLegalRoleKeyHash',
  ], path);
  for (const key of ['policyId', 'runtimeProfileId', 'activeGameplayRulesVersion', 'engineImplementationVersion', 'draftMetaVersion']) text(source[key], `${path}.${key}`);
  for (const key of ['policyHash', 'configurationHash', 'requiredLegalRoleKeyHash', 'actualLegalRoleKeyHash']) sha(source[key], `${path}.${key}`);
}

export function validateSeriesViewPayload(value: unknown): SeriesViewDto {
  const root = record(value, '$');
  exact(root, [
    'schemaVersion', 'seriesId', 'revision', 'status', 'terminalReason', 'format', 'winsRequired',
    'teams', 'managedTeamCode', 'opponentTeamCode', 'score', 'currentGameNumber', 'rootSeed',
    'seedDerivationAlgorithm', 'currentGameSeed', 'excludedChampionIds', 'seriesHistoryBeforeHash',
    'games', 'activeDraftSession', 'reservation', 'allowedCommands', 'winnerTeamCode',
    'createdAt', 'lastActivityAt', 'expiresAt', 'processLocalRestartLoss', 'productionIdentity',
  ], '$');
  literal(root.schemaVersion, 'SERIES_VIEW_V1', '$.schemaVersion');
  identity(root.seriesId, SERIES_ID, '$.seriesId', 'Series ID'); integer(root.revision, '$.revision');
  const status = oneOf(root.status, SERIES_STATUSES, '$.status') as SeriesStatus; nullableText(root.terminalReason, '$.terminalReason');
  const format = oneOf(root.format, FORMATS, '$.format') as SeriesFormat;
  const winsRequired = integer(root.winsRequired, '$.winsRequired', 1);
  if (winsRequired !== (format === 'BO3' ? 2 : 3)) fail('$.winsRequired', 'format의 required wins와 일치해야 합니다.');
  const teamValues = array(root.teams, '$.teams'); if (teamValues.length !== 2) fail('$.teams', '참가 팀 두 개가 필요합니다.');
  const teamCodes = teamValues.map((team, index) => validateTeam(team, `$.teams[${index}]`));
  if (new Set(teamCodes).size !== 2) fail('$.teams', '서로 다른 참가 팀이 필요합니다.');
  const managed = text(root.managedTeamCode, '$.managedTeamCode'); const opponent = text(root.opponentTeamCode, '$.opponentTeamCode');
  if (!teamCodes.includes(managed) || !teamCodes.includes(opponent) || managed === opponent) fail('$', 'managed/opponent identity는 참가 팀에 정확히 결속돼야 합니다.');
  const score = validateIntegerMap(root.score, teamCodes, '$.score');
  teamCodes.forEach((teamCode) => {
    if (score[teamCode] > winsRequired) fail(`$.score.${teamCode}`, 'format required wins를 초과할 수 없습니다.');
  });
  const currentGameNumber = integer(root.currentGameNumber, '$.currentGameNumber', 1);
  signedInt64(root.rootSeed, '$.rootSeed'); text(root.seedDerivationAlgorithm, '$.seedDerivationAlgorithm'); const currentSeed = signedInt64(root.currentGameSeed, '$.currentGameSeed');
  const excluded = uniqueStrings(root.excludedChampionIds, '$.excludedChampionIds', true); sha(root.seriesHistoryBeforeHash, '$.seriesHistoryBeforeHash');
  const gameValues = array(root.games, '$.games'); if (gameValues.length < 1 || gameValues.length > (format === 'BO3' ? 3 : 5)) fail('$.games', 'format의 game 범위를 벗어났습니다.');
  const partial = value as SeriesViewDto;
  const games = gameValues.map((game, index) => validateGame(game, teamCodes, managed, index + 1, `$.games[${index}]`));
  const current = games[games.length - 1];
  if (current.gameNumber !== currentGameNumber || current.matchSeed !== currentSeed) fail('$.currentGameNumber', 'ordered games의 current game/seed와 일치해야 합니다.');
  const committed = games.filter((game) => game.status === 'COMMITTED');
  if (Object.values(score).reduce((sum, wins) => sum + wins, 0) !== committed.length) fail('$.score', 'committed game 수와 team-code score 합이 일치해야 합니다.');
  const winnerTally = Object.fromEntries(teamCodes.map((teamCode) => [teamCode, 0])) as Record<string, number>;
  committed.forEach((game) => {
    const winnerTeamCode = game.result?.winnerTeamCode;
    if (winnerTeamCode === null || winnerTeamCode === undefined || !teamCodes.includes(winnerTeamCode)) {
      fail(`$.games[${game.gameNumber - 1}].result.winnerTeamCode`, 'COMMITTED game의 참가 팀 승자가 필요합니다.');
    }
    winnerTally[winnerTeamCode] += 1;
  });
  teamCodes.forEach((teamCode) => {
    if (score[teamCode] !== winnerTally[teamCode]) fail(`$.score.${teamCode}`, 'COMMITTED game winner 집계와 정확히 일치해야 합니다.');
  });
  if (excluded.length !== committed.length * 10) fail('$.excludedChampionIds', 'committed game당 정확히 10개 pick이 누적돼야 합니다.');
  if (root.activeDraftSession !== null) validateChild(root.activeDraftSession, partial, current, '$.activeDraftSession');
  if ((root.activeDraftSession === null) !== (current.childDraftSessionId === null)) fail('$.activeDraftSession', 'current game child projection과 일치해야 합니다.');
  if (root.reservation !== null) {
    const reservation = record(root.reservation, '$.reservation'); exact(reservation, ['commandId', 'createdAt', 'leaseExpiresAt'], '$.reservation');
    text(reservation.commandId, '$.reservation.commandId'); const created = instant(reservation.createdAt, '$.reservation.createdAt'); const lease = instant(reservation.leaseExpiresAt, '$.reservation.leaseExpiresAt');
    if (Date.parse(lease) <= Date.parse(created) || current.status !== 'SIMULATION_IN_PROGRESS') fail('$.reservation', 'active simulation game과 유효한 lease가 필요합니다.');
  }
  const commands = array(root.allowedCommands, '$.allowedCommands').map((command, index) => oneOf(command, ALLOWED_COMMANDS, `$.allowedCommands[${index}]`) as SeriesAllowedCommand);
  if (new Set(commands).size !== commands.length) fail('$.allowedCommands', '명령은 중복될 수 없습니다.');
  const winner = root.winnerTeamCode === null ? null : text(root.winnerTeamCode, '$.winnerTeamCode');
  if (status === 'COMPLETED') {
    if (winner === null || !teamCodes.includes(winner) || score[winner] !== winsRequired || winnerTally[winner] !== winsRequired) fail('$.winnerTeamCode', 'COMPLETED Series winner는 required wins와 game winner 집계에 정확히 일치해야 합니다.');
  } else if (winner !== null) fail('$.winnerTeamCode', 'COMPLETED 이전에는 winner가 없어야 합니다.');
  if (status === 'ACTIVE' && teamCodes.some((teamCode) => score[teamCode] >= winsRequired)) fail('$.status', 'required wins에 도달한 Series는 ACTIVE일 수 없습니다.');
  if (status !== 'ACTIVE' && status !== 'BLOCKED' && commands.some((command) => command !== 'GET')) fail('$.allowedCommands', 'terminal Series는 GET만 허용할 수 있습니다.');
  instant(root.createdAt, '$.createdAt'); instant(root.lastActivityAt, '$.lastActivityAt'); instant(root.expiresAt, '$.expiresAt');
  if (bool(root.processLocalRestartLoss, '$.processLocalRestartLoss') !== true) fail('$.processLocalRestartLoss', 'V1 process-local restart loss는 true여야 합니다.');
  validateProductionIdentity(root.productionIdentity, '$.productionIdentity');
  return value as SeriesViewDto;
}

export function validateSeriesDraftResponsePayload(value: unknown): SeriesDraftResponseDto {
  const root = record(value, '$'); exact(root, ['series', 'draftSession', 'replayed'], '$');
  const series = validateSeriesViewPayload(root.series); const replayed = bool(root.replayed, '$.replayed');
  const bindingRecord = record(record(root.draftSession, '$.draftSession').binding, '$.draftSession.binding');
  const gameNumber = integer(bindingRecord.gameNumber, '$.draftSession.binding.gameNumber', 1);
  const game = series.games.find((candidate) => candidate.gameNumber === gameNumber);
  if (!game) fail('$.draftSession.binding.gameNumber', 'Series games에 존재하는 game이어야 합니다.');
  const draftSession = validateChild(root.draftSession, series, game, '$.draftSession');
  if (!replayed && gameNumber === series.currentGameNumber && series.activeDraftSession?.session.sessionId !== draftSession.session.sessionId) {
    fail('$.draftSession', '현재 successful child response는 Series active child와 일치해야 합니다.');
  }
  return value as SeriesDraftResponseDto;
}

function validateGameAgainstSeries(value: unknown, series: SeriesViewDto, path: string): SeriesGameViewDto {
  const source = record(value, path); const gameNumber = integer(source.gameNumber, `${path}.gameNumber`, 1);
  const expected = series.games.find((game) => game.gameNumber === gameNumber);
  if (!expected) fail(path, 'Series에 존재하는 game이어야 합니다.');
  const validated = validateGame(value, series.teams.map((team) => team.teamCode), series.managedTeamCode, gameNumber, path);
  if (JSON.stringify(validated) !== JSON.stringify(expected)) fail(path, 'Series games projection과 exact 일치해야 합니다.');
  return validated;
}

export function validateSeriesSimulationEnvelopePayload(value: unknown): SeriesSimulationEnvelopeDto {
  const root = record(value, '$'); exact(root, ['schemaVersion', 'replayedCommand', 'series', 'game', 'match'], '$');
  literal(root.schemaVersion, 'SERIES_SIMULATION_RESPONSE_V1', '$.schemaVersion'); bool(root.replayedCommand, '$.replayedCommand');
  const series = validateSeriesViewPayload(root.series); validateGameAgainstSeries(root.game, series, '$.game');
  if (root.match !== null) record(root.match, '$.match');
  return value as SeriesSimulationEnvelopeDto;
}

export function validateSeriesReplayEnvelopePayload(value: unknown): SeriesReplayEnvelopeDto {
  const root = record(value, '$'); exact(root, ['schemaVersion', 'series', 'game', 'match'], '$');
  literal(root.schemaVersion, 'SERIES_GAME_REPLAY_RESPONSE_V1', '$.schemaVersion');
  const series = validateSeriesViewPayload(root.series); const game = validateGameAgainstSeries(root.game, series, '$.game');
  if (game.status !== 'COMMITTED') fail('$.game.status', 'replay에는 COMMITTED game이 필요합니다.'); record(root.match, '$.match');
  return value as SeriesReplayEnvelopeDto;
}

export function validateSeriesMatchPayload(
  value: unknown,
  series: SeriesViewDto,
  game: SeriesGameViewDto,
  child: SeriesChildDraftEnvelopeDto,
) {
  if (child.session.status !== 'SIMULATED') fail('$.draftSession.session.status', 'Series match 검증에는 SIMULATED child가 필요합니다.');
  const synthetic = validatePlayerDraftSimulationPayload({
    schemaVersion: 'PLAYER_DRAFT_MATCH_RESPONSE_V1', session: child.session, match: value,
  }, {
    sessionId: child.session.sessionId,
    blueTeamCode: game.blueTeamCode,
    redTeamCode: game.redTeamCode,
    controlledSide: game.controlledSide,
    seed: game.matchSeed,
    seriesGameNumber: game.gameNumber,
    hardFearlessExclusions: game.historyBeforeChampionIds,
    sessionIdentity: 'SERIES_CHILD',
  });
  const match = synthetic.match;
  if (match.productionPolicy.policyId !== series.productionIdentity.policyId
    || match.productionPolicy.policyHash !== series.productionIdentity.policyHash
    || match.integrity.runtimeProfileId !== series.productionIdentity.runtimeProfileId
    || match.integrity.configurationHash !== series.productionIdentity.configurationHash
    || match.integrity.engineImplementationVersion !== series.productionIdentity.engineImplementationVersion
    || match.integrity.activeGameplayRulesVersion !== series.productionIdentity.activeGameplayRulesVersion) {
    fail('$.match', 'Series production identity와 match payload가 일치하지 않습니다.');
  }
  if (game.receipt && match.integrity.outputHash !== game.receipt.outputHash) fail('$.match.integrity.outputHash', 'committed game receipt와 일치해야 합니다.');
  if (game.result) {
    if (match.result.winner !== game.result.winnerSide || match.result.durationSeconds !== game.result.durationSeconds) fail('$.match.result', 'compact result와 full match 결과가 일치해야 합니다.');
  }
  return match;
}

export function validateSeriesApiErrorPayload(value: unknown): SeriesApiErrorDto {
  const root = record(value, '$');
  exact(root, ['schemaVersion', 'code', 'field', 'message', 'retryable', 'currentRevision', 'currentStatus'], '$');
  literal(root.schemaVersion, 'SERIES_API_ERROR_V1', '$.schemaVersion'); text(root.code, '$.code'); nullableText(root.field, '$.field'); text(root.message, '$.message'); bool(root.retryable, '$.retryable');
  if (root.currentRevision !== null) integer(root.currentRevision, '$.currentRevision'); nullableOneOf(root.currentStatus, SERIES_STATUSES, '$.currentStatus');
  return value as SeriesApiErrorDto;
}

// Kept as an import-time compatibility assertion: Series match validation deliberately uses
// the existing Player Draft error boundary rather than reinterpreting its messages.

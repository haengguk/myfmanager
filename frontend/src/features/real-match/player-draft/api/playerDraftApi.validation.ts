import type { DraftActionType, Position, TeamSide } from '../../realMatch.contract';
import type {
  PlayerDraftApiErrorDto, PlayerDraftDecisionAuthority, PlayerDraftSessionExpectation,
  PlayerDraftSessionResponseDto, PlayerDraftSimulationResponseDto, PlayerDraftTurnEvidenceDto,
  PlayerDraftUnavailableReason,
} from './playerDraftApi.types';

type JsonRecord = Record<string, unknown>;
type PlayerIdentity = { side: TeamSide; position: Position; championId: string };

const SIDES = ['BLUE', 'RED'] as const;
const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'] as const;
const ACTIONS = ['BAN', 'PICK'] as const;
const STATUSES = ['ACTIVE', 'COMPLETED', 'SIMULATED', 'CANCELLED', 'EXPIRED'] as const;
const AUTHORITIES = ['AI', 'PLAYER'] as const;
const UNAVAILABLE_REASONS = [
  'HARD_FEARLESS_EXCLUDED', 'ALREADY_BANNED', 'ALREADY_PICKED',
  'PARTIAL_ROLE_ASSIGNMENT_INFEASIBLE', 'FUTURE_ROLE_COMPLETION_INFEASIBLE',
  'BAN_WOULD_BREAK_FUTURE_COMPLETION',
] as const;
const END_REASONS = ['NEXUS_DESTROYED', 'SIMULATION_TIMEOUT'] as const;
const LANES = ['TOP', 'MID', 'BOT'] as const;
const EVENT_TYPES = [
  'GAME_START', 'KILL', 'ASSIST', 'JUNGLE_GANK', 'COUNTER_GANK', 'LANE_COMBAT', 'ROAM',
  'SHUTDOWN', 'DRAGON', 'BARON', 'ELDER', 'TOWER', 'STRUCTURE_ACTION', 'TEAMFIGHT',
  'TEAMFIGHT_RESULT', 'ACE', 'MATCH_PHASE_CHANGE', 'MACRO_ACTION', 'LATE_GAME_ACTION',
  'LEVEL_UP', 'ITEM_STAGE_REACHED', 'GAME_END',
] as const;
const COMBAT_SOURCES = [
  'COUNTER_GANK', 'JUNGLE_GANK', 'LANE_COMBAT', 'ROAM', 'SKIRMISH', 'TEAMFIGHT',
  'OBJECTIVE_FIGHT', 'LATE_GAME_SIEGE', 'BASE_DEFENSE', 'OTHER',
] as const;
const STRUCTURE_SOURCES = [
  'LANE_PRESSURE', 'POST_FIGHT', 'BARON_PRESSURE', 'MACRO_PLAY', 'MID_GAME_MACRO',
  'OBJECTIVE_TRADE', 'LATE_GAME_SIEGE', 'LATE_GAME_CROSS_MAP', 'NEXUS_FINISH',
] as const;
const STRUCTURE_KINDS = ['TOWER', 'INHIBITOR', 'NEXUS_TURRET', 'NEXUS'] as const;
const TOWER_TIERS = ['OUTER', 'INNER', 'INHIBITOR'] as const;
const ACTIVITIES = ['DEFAULT_ROLE', 'ROAMING', 'SIEGING'] as const;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const CLIENT_ACTION_ID = /^[A-Za-z0-9._:-]{1,100}$/;
const TEAM_CODE = /^[A-Z0-9]{2,5}$/;
const TURN_ORDER: readonly { side: TeamSide; action: DraftActionType }[] = [
  { side: 'BLUE', action: 'BAN' }, { side: 'RED', action: 'BAN' },
  { side: 'BLUE', action: 'BAN' }, { side: 'RED', action: 'BAN' },
  { side: 'BLUE', action: 'BAN' }, { side: 'RED', action: 'BAN' },
  { side: 'BLUE', action: 'PICK' }, { side: 'RED', action: 'PICK' },
  { side: 'RED', action: 'PICK' }, { side: 'BLUE', action: 'PICK' },
  { side: 'BLUE', action: 'PICK' }, { side: 'RED', action: 'PICK' },
  { side: 'RED', action: 'BAN' }, { side: 'BLUE', action: 'BAN' },
  { side: 'RED', action: 'BAN' }, { side: 'BLUE', action: 'BAN' },
  { side: 'RED', action: 'PICK' }, { side: 'BLUE', action: 'PICK' },
  { side: 'BLUE', action: 'PICK' }, { side: 'RED', action: 'PICK' },
];

export class PlayerDraftContractError extends Error {
  public readonly path: string;
  constructor(path: string, message: string) {
    super(`${path}: ${message}`);
    this.path = path;
    this.name = 'PlayerDraftContractError';
  }
}

function fail(path: string, message: string): never { throw new PlayerDraftContractError(path, message); }
function record(value: unknown, path: string): JsonRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) fail(path, 'JSON 객체가 필요합니다.');
  return value as JsonRecord;
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
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) fail(path, `${minimum} 이상의 정수가 필요합니다.`);
  return value;
}
function finite(value: unknown, path: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) fail(path, '유한한 숫자가 필요합니다.');
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
function side(value: unknown, path: string): TeamSide { return oneOf(value, SIDES, path); }
function position(value: unknown, path: string): Position { return oneOf(value, POSITIONS, path); }
function action(value: unknown, path: string): DraftActionType { return oneOf(value, ACTIONS, path); }
function strings(value: unknown, path: string): readonly string[] {
  return array(value, path).map((item, index) => text(item, `${path}[${index}]`));
}
function sha(value: unknown, path: string): string {
  const result = text(value, path);
  if (!SHA256.test(result)) fail(path, '소문자 SHA-256 형식이 필요합니다.');
  return result;
}
function uuid(value: unknown, path: string): string {
  const result = text(value, path);
  if (!UUID.test(result)) fail(path, 'canonical UUID 형식이 필요합니다.');
  return result;
}
function exactSet(values: readonly string[], expected: readonly string[], path: string): void {
  const actual = new Set(values);
  if (actual.size !== values.length || actual.size !== expected.length || expected.some((value) => !actual.has(value))) {
    fail(path, `${expected.join(', ')}의 중복 없는 정확한 구성이 필요합니다.`);
  }
}
function exactArray(values: readonly string[], expected: readonly string[], path: string): void {
  if (values.length !== expected.length || values.some((value, index) => value !== expected[index])) fail(path, '결정 순서에서 재구성한 상태와 일치해야 합니다.');
}
function signedInt64(value: unknown, path: string): string {
  const result = text(value, path);
  if (!/^-?(0|[1-9][0-9]*)$/.test(result)) fail(path, 'canonical signed-int64 decimal string이 필요합니다.');
  try {
    const parsed = BigInt(result);
    if (parsed < -9223372036854775808n || parsed > 9223372036854775807n) fail(path, 'signed-int64 범위를 벗어났습니다.');
  } catch { fail(path, 'signed-int64 값을 해석할 수 없습니다.'); }
  return result;
}

function validatePolicy(value: unknown, path: string): { policyId: string; policyHash: string } {
  const source = record(value, path);
  return { policyId: text(source.policyId, `${path}.policyId`), policyHash: sha(source.policyHash, `${path}.policyHash`) };
}
function validateRule(value: unknown, path: string): void {
  const source = record(value, path); text(source.identity, `${path}.identity`); sha(source.hash, `${path}.hash`);
}
function validateChampion(value: unknown, path: string): string {
  const champion = record(value, path);
  const id = text(champion.championId, `${path}.championId`);
  text(champion.displayNameKo, `${path}.displayNameKo`);
  text(champion.displayNameEn, `${path}.displayNameEn`);
  text(champion.portraitUrl, `${path}.portraitUrl`);
  return id;
}

function validateAutoTrace(value: unknown, evidence: JsonRecord, path: string): void {
  const trace = record(value, path);
  for (const key of ['policyId', 'policyMode', 'policyHash', 'selectionContextHash', 'bestCandidateId', 'selectedChampionId']) text(trace[key], `${path}.${key}`);
  sha(trace.policyHash, `${path}.policyHash`); sha(trace.selectionContextHash, `${path}.selectionContextHash`);
  if (integer(trace.turn, `${path}.turn`, 1) !== evidence.turn) fail(`${path}.turn`, '결정 turn과 일치해야 합니다.');
  if (side(trace.teamSide, `${path}.teamSide`) !== evidence.teamSide) fail(`${path}.teamSide`, '결정 side와 일치해야 합니다.');
  if (action(trace.actionType, `${path}.actionType`) !== evidence.actionType) fail(`${path}.actionType`, '결정 action과 일치해야 합니다.');
  finite(trace.bestCanonicalScore, `${path}.bestCanonicalScore`);
  const pool = array(trace.eligiblePool, `${path}.eligiblePool`);
  if (pool.length < 1 || pool.length > 3) fail(`${path}.eligiblePool`, '1~3개의 후보가 필요합니다.');
  const poolIds = new Set<string>();
  pool.forEach((entryValue, index) => {
    const entryPath = `${path}.eligiblePool[${index}]`; const entry = record(entryValue, entryPath);
    const id = text(entry.championId, `${entryPath}.championId`);
    if (poolIds.has(id)) fail(`${entryPath}.championId`, '후보 champion은 중복될 수 없습니다.');
    poolIds.add(id);
    if (integer(entry.canonicalRank, `${entryPath}.canonicalRank`, 1) !== index + 1) fail(`${entryPath}.canonicalRank`, '순위가 연속이어야 합니다.');
    for (const key of ['rawFinalSearchScore', 'canonicalFinalScore', 'canonicalScoreLoss', 'rankWeight']) finite(entry[key], `${entryPath}.${key}`);
  });
  const selectedRank = integer(trace.selectedRank, `${path}.selectedRank`, 1);
  if (selectedRank > pool.length) fail(`${path}.selectedRank`, '후보 범위를 벗어났습니다.');
  if (trace.selectedChampionId !== evidence.championId) fail(`${path}.selectedChampionId`, '결정 champion과 일치해야 합니다.');
  finite(trace.selectedCanonicalScoreLoss, `${path}.selectedCanonicalScoreLoss`);
  integer(trace.totalEligibleWeight, `${path}.totalEligibleWeight`, 1);
  const reason = oneOf(trace.reason, ['ONLY_ONE_WITHIN_WINDOW', 'SEEDED_WEIGHTED_SELECTION'] as const, `${path}.reason`);
  if (reason === 'ONLY_ONE_WITHIN_WINDOW') {
    if (trace.drawBucket !== null || pool.length !== 1) fail(path, '단일 후보 선택은 Random draw가 없어야 합니다.');
  } else integer(trace.drawBucket, `${path}.drawBucket`);
}

function validateEvidence(value: unknown, index: number, controlledSide: TeamSide, path: string): PlayerDraftTurnEvidenceDto {
  const evidence = record(value, path);
  const turn = integer(evidence.turn, `${path}.turn`, 1);
  if (turn !== index + 1 || turn > 20) fail(`${path}.turn`, '1부터 연속된 20 이하 turn이 필요합니다.');
  const expected = TURN_ORDER[index];
  const teamSide = side(evidence.teamSide, `${path}.teamSide`);
  const actionType = action(evidence.actionType, `${path}.actionType`);
  if (!expected || expected.side !== teamSide || expected.action !== actionType) fail(path, 'Professional Draft turn 순서와 일치하지 않습니다.');
  text(evidence.championId, `${path}.championId`);
  const authority = oneOf(evidence.authority, AUTHORITIES, `${path}.authority`) as PlayerDraftDecisionAuthority;
  if ((authority === 'PLAYER') !== (teamSide === controlledSide)) fail(`${path}.authority`, 'PLAYER/AI authority가 controlled side와 일치해야 합니다.');
  sha(evidence.stateBeforeHash, `${path}.stateBeforeHash`); sha(evidence.stateAfterHash, `${path}.stateAfterHash`);
  if (authority === 'AI') {
    if (evidence.autoSelectionTrace === null || evidence.playerSelectionEvidence !== null) fail(path, 'AI 결정에는 Auto trace만 필요합니다.');
    validateAutoTrace(evidence.autoSelectionTrace, evidence, `${path}.autoSelectionTrace`);
  } else {
    if (evidence.autoSelectionTrace !== null || evidence.playerSelectionEvidence === null) fail(path, 'PLAYER 결정에는 manual evidence만 필요합니다.');
    const manual = record(evidence.playerSelectionEvidence, `${path}.playerSelectionEvidence`);
    if (side(manual.controlledSide, `${path}.playerSelectionEvidence.controlledSide`) !== controlledSide) fail(`${path}.playerSelectionEvidence.controlledSide`, 'controlled side와 일치해야 합니다.');
    for (const key of ['turn', 'actionType', 'championId', 'stateBeforeHash'] as const) {
      if (manual[key] !== evidence[key]) fail(`${path}.playerSelectionEvidence.${key}`, '결정 evidence와 일치해야 합니다.');
    }
    sha(manual.selectableSetIdentity, `${path}.playerSelectionEvidence.selectableSetIdentity`);
    literal(manual.legalityResult, 'LEGAL', `${path}.playerSelectionEvidence.legalityResult`);
    const clientActionId = text(manual.clientActionId, `${path}.playerSelectionEvidence.clientActionId`);
    if (!CLIENT_ACTION_ID.test(clientActionId)) fail(`${path}.playerSelectionEvidence.clientActionId`, '허용된 clientActionId 형식이 아닙니다.');
  }
  return value as PlayerDraftTurnEvidenceDto;
}

function validateAssignments(value: unknown, picks: Record<TeamSide, readonly string[]>, path: string): void {
  const assignments = array(value, path);
  if (assignments.length !== 10) fail(path, '정확히 10개의 final assignment가 필요합니다.');
  const players = new Set<string>(); const champions: Record<TeamSide, string[]> = { BLUE: [], RED: [] };
  const positions: Record<TeamSide, string[]> = { BLUE: [], RED: [] };
  assignments.forEach((item, index) => {
    const itemPath = `${path}[${index}]`; const assignment = record(item, itemPath);
    const playerId = text(assignment.playerId, `${itemPath}.playerId`);
    if (players.has(playerId)) fail(`${itemPath}.playerId`, '중복 player assignment입니다.');
    players.add(playerId);
    const teamSide = side(assignment.teamSide, `${itemPath}.teamSide`);
    positions[teamSide].push(position(assignment.position, `${itemPath}.position`));
    champions[teamSide].push(text(assignment.championId, `${itemPath}.championId`));
  });
  exactSet(positions.BLUE, POSITIONS, `${path}.BLUE.position`); exactSet(positions.RED, POSITIONS, `${path}.RED.position`);
  exactSet(champions.BLUE, picks.BLUE, `${path}.BLUE.championId`); exactSet(champions.RED, picks.RED, `${path}.RED.championId`);
}

export function validatePlayerDraftSessionPayload(value: unknown, expectation: PlayerDraftSessionExpectation): PlayerDraftSessionResponseDto {
  const root = record(value, '$'); literal(root.schemaVersion, 'PLAYER_DRAFT_SESSION_V1', '$.schemaVersion');
  const sessionId = uuid(root.sessionId, '$.sessionId');
  if (expectation.sessionId && sessionId !== expectation.sessionId) fail('$.sessionId', '요청 session과 일치하지 않습니다.');
  const revision = integer(root.revision, '$.revision');
  const status = oneOf(root.status, STATUSES, '$.status');
  const teams = array(root.teams, '$.teams');
  if (teams.length !== 2) fail('$.teams', 'BLUE/RED 두 팀이 필요합니다.');
  const codes = new Map<TeamSide, string>();
  teams.forEach((value, index) => {
    const path = `$.teams[${index}]`; const team = record(value, path); const teamSide = side(team.teamSide, `${path}.teamSide`);
    if (codes.has(teamSide)) fail(`${path}.teamSide`, '중복 team side입니다.');
    const code = text(team.teamCode, `${path}.teamCode`);
    if (!TEAM_CODE.test(code)) fail(`${path}.teamCode`, 'stable team code 형식이 아닙니다.');
    if (code !== (teamSide === 'BLUE' ? expectation.blueTeamCode : expectation.redTeamCode)) fail(`${path}.teamCode`, '요청 팀과 일치하지 않습니다.');
    text(team.displayName, `${path}.displayName`); codes.set(teamSide, code);
  });
  exactSet([...codes.keys()], SIDES, '$.teams.teamSide');
  const controlledSide = side(root.controlledSide, '$.controlledSide');
  if (controlledSide !== expectation.controlledSide) fail('$.controlledSide', '요청 controlled side와 일치하지 않습니다.');
  const seed = signedInt64(root.seed, '$.seed');
  if (seed !== expectation.seed) fail('$.seed', '요청 seed와 일치하지 않습니다.');
  if (integer(root.seriesGameNumber, '$.seriesGameNumber', 1) !== 1) fail('$.seriesGameNumber', '현재는 Game 1만 지원합니다.');
  validateRule(root.draftRules, '$.draftRules'); validatePolicy(root.draftScoringPolicy, '$.draftScoringPolicy');
  validatePolicy(root.autoDraftSelectionPolicy, '$.autoDraftSelectionPolicy'); validatePolicy(root.playerControlPolicy, '$.playerControlPolicy');
  sha(root.stateHash, '$.stateHash');

  const state = record(root.state, '$.state');
  const blueBans = strings(state.blueBans, '$.state.blueBans'); const redBans = strings(state.redBans, '$.state.redBans');
  const bluePicks = strings(state.bluePicks, '$.state.bluePicks'); const redPicks = strings(state.redPicks, '$.state.redPicks');
  const fearless = strings(state.hardFearlessExclusions, '$.state.hardFearlessExclusions');
  for (const [name, values, max] of [['blueBans', blueBans, 5], ['redBans', redBans, 5], ['bluePicks', bluePicks, 5], ['redPicks', redPicks, 5]] as const) {
    if (values.length > max || new Set(values).size !== values.length) fail(`$.state.${name}`, '중복 없이 최대 5개여야 합니다.');
  }
  if (new Set([...blueBans, ...redBans, ...bluePicks, ...redPicks, ...fearless]).size !== blueBans.length + redBans.length + bluePicks.length + redPicks.length + fearless.length) fail('$.state', 'Draft champion identity가 중복될 수 없습니다.');

  const decisionValues = array(root.decisions, '$.decisions');
  if (decisionValues.length > 20) fail('$.decisions', '20턴을 초과할 수 없습니다.');
  const decisions = decisionValues.map((item, index) => validateEvidence(item, index, controlledSide, `$.decisions[${index}]`));
  if (new Set(decisions.map((decision) => decision.championId)).size !== decisions.length) fail('$.decisions.championId', 'Draft 전체 champion이 중복될 수 없습니다.');
  const rebuilt = { BLUE: { BAN: [] as string[], PICK: [] as string[] }, RED: { BAN: [] as string[], PICK: [] as string[] } };
  decisions.forEach((decision) => rebuilt[decision.teamSide][decision.actionType].push(decision.championId));
  exactArray(blueBans, rebuilt.BLUE.BAN, '$.state.blueBans'); exactArray(redBans, rebuilt.RED.BAN, '$.state.redBans');
  exactArray(bluePicks, rebuilt.BLUE.PICK, '$.state.bluePicks'); exactArray(redPicks, rebuilt.RED.PICK, '$.state.redPicks');
  if (revision !== decisions.filter((decision) => decision.authority === 'PLAYER').length) fail('$.revision', '수락된 PLAYER action 수와 일치해야 합니다.');

  const currentTurn = root.currentTurn;
  if (status === 'ACTIVE') {
    const current = record(currentTurn, '$.currentTurn'); const expected = TURN_ORDER[decisions.length];
    if (!expected || integer(current.turn, '$.currentTurn.turn', 1) !== decisions.length + 1) fail('$.currentTurn.turn', '다음 turn과 일치하지 않습니다.');
    if (side(current.teamSide, '$.currentTurn.teamSide') !== controlledSide || expected.side !== controlledSide) fail('$.currentTurn.teamSide', '응답은 다음 player turn까지 진행되어야 합니다.');
    if (action(current.actionType, '$.currentTurn.actionType') !== expected.action) fail('$.currentTurn.actionType', 'Draft 순서와 일치하지 않습니다.');
  } else if (currentTurn !== null) fail('$.currentTurn', 'terminal session에는 current turn이 없어야 합니다.');

  const selectable = array(root.selectableChampions, '$.selectableChampions');
  const unavailable = array(root.unavailableChampions, '$.unavailableChampions');
  const selectableIds = new Set<string>(); const unavailableIds = new Set<string>();
  selectable.forEach((value, index) => {
    const path = `$.selectableChampions[${index}]`; const option = record(value, path); const championId = validateChampion(option.champion, `${path}.champion`);
    if (selectableIds.has(championId)) fail(`${path}.champion.championId`, '중복 selectable champion입니다.');
    selectableIds.add(championId);
    const roles = array(option.feasibleRoles, `${path}.feasibleRoles`).map((role, roleIndex) => position(role, `${path}.feasibleRoles[${roleIndex}]`));
    if (new Set(roles).size !== roles.length) fail(`${path}.feasibleRoles`, 'feasible role은 중복될 수 없습니다.');
    if (status === 'ACTIVE' && root.currentTurn && record(root.currentTurn, '$.currentTurn').actionType === 'PICK' && roles.length === 0) {
      fail(`${path}.feasibleRoles`, 'PICK selectable champion에는 하나 이상의 feasible role이 필요합니다.');
    }
  });
  unavailable.forEach((value, index) => {
    const path = `$.unavailableChampions[${index}]`; const item = record(value, path); const championId = validateChampion(item.champion, `${path}.champion`);
    if (unavailableIds.has(championId) || selectableIds.has(championId)) fail(`${path}.champion.championId`, 'champion projection identity가 중복되거나 충돌합니다.');
    unavailableIds.add(championId); oneOf(item.reason, UNAVAILABLE_REASONS, `${path}.reason`) as PlayerDraftUnavailableReason;
  });
  const recommendations = array(root.advisoryRecommendations, '$.advisoryRecommendations');
  if (recommendations.length > 3) fail('$.advisoryRecommendations', '추천은 최대 3개입니다.');
  const recommendationIds = new Set<string>();
  recommendations.forEach((value, index) => {
    const path = `$.advisoryRecommendations[${index}]`; const recommendation = record(value, path);
    const championId = validateChampion(recommendation.champion, `${path}.champion`);
    if (!selectableIds.has(championId) || recommendationIds.has(championId)) fail(`${path}.champion.championId`, '추천은 selectable set의 중복 없는 champion이어야 합니다.');
    recommendationIds.add(championId);
    if (integer(recommendation.advisoryRank, `${path}.advisoryRank`, 1) !== index + 1 || index >= 3) fail(`${path}.advisoryRank`, '1~3의 연속 순위가 필요합니다.');
    for (const key of ['immediateScore', 'continuationScore', 'finalSearchScore']) finite(recommendation[key], `${path}.${key}`);
    if (recommendation.advisoryOnly !== true) fail(`${path}.advisoryOnly`, '추천은 advisory only여야 합니다.');
  });
  if (status === 'ACTIVE') {
    if (selectable.length === 0) fail('$.selectableChampions', 'ACTIVE player turn에는 legal champion이 필요합니다.');
    sha(root.selectableSetIdentity, '$.selectableSetIdentity');
  } else if (selectable.length || unavailable.length || recommendations.length || root.selectableSetIdentity !== null) fail('$', 'terminal session에는 선택 projection이 없어야 합니다.');

  const picks: Record<TeamSide, readonly string[]> = { BLUE: bluePicks, RED: redPicks };
  if (decisions.length === 20) {
    if (!['COMPLETED', 'SIMULATED', 'CANCELLED', 'EXPIRED'].includes(status)) fail('$.status', '20턴 완료 상태가 필요합니다.');
    const completed = record(root.completedDraft, '$.completedDraft');
    sha(completed.draftIdentity, '$.completedDraft.draftIdentity'); text(completed.controlEvidenceSchema, '$.completedDraft.controlEvidenceSchema');
    sha(completed.controlEvidenceHash, '$.completedDraft.controlEvidenceHash'); text(completed.controlEvidenceHashAlgorithm, '$.completedDraft.controlEvidenceHashAlgorithm');
    validateAssignments(completed.finalAssignments, picks, '$.completedDraft.finalAssignments');
  } else if (root.completedDraft !== null) fail('$.completedDraft', '미완료 Draft에는 final assignment가 없어야 합니다.');
  if ((status === 'COMPLETED' || status === 'SIMULATED') && decisions.length !== 20) fail('$.status', '완료 상태에는 20개 결정이 필요합니다.');
  return value as PlayerDraftSessionResponseDto;
}

function validateProductionPolicy(value: unknown, path: string): JsonRecord {
  const policy = record(value, path);
  for (const key of [
    'policyId', 'policyHash', 'activationDecisionSchema', 'activationDecisionCode', 'acceptanceStatus',
    'knownDiagnosticLimitation', 'rollbackProfileId', 'rollbackMode', 'draftSelectionPolicyId',
    'draftSelectionPolicyHash', 'runtimeProfileId', 'configurationHash', 'activeGameplayRulesVersion',
    'engineImplementationVersion', 'matchupMode', 'compositionMode', 'jungleClearContribution',
  ]) text(policy[key], `${path}.${key}`);
  for (const key of ['policyHash', 'draftSelectionPolicyHash', 'configurationHash']) sha(policy[key], `${path}.${key}`);
  const limitations = strings(policy.knownDiagnosticLimitations, `${path}.knownDiagnosticLimitations`);
  if (!limitations.length || limitations[0] !== policy.knownDiagnosticLimitation) fail(`${path}.knownDiagnosticLimitations`, 'primary limitation과 일치하는 제한이 필요합니다.');
  for (const key of ['statisticalHoldoutApproved', 'automaticFallback', 'economyCandidateActivation', 'tempoCandidateActivation', 'diagnosticsExcludedFromGameplayIdentity']) bool(policy[key], `${path}.${key}`);
  if (policy.statisticalHoldoutApproved !== false || policy.automaticFallback !== false) fail(path, '통계 holdout 승인과 자동 fallback은 false여야 합니다.');
  if (policy.runtimeProfileId !== 'PRODUCTION_MATCHUP_COMPOSITION_V1') fail(`${path}.runtimeProfileId`, 'Production runtime profile이 필요합니다.');
  if (policy.engineImplementationVersion !== 'MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9') fail(`${path}.engineImplementationVersion`, 'Production V9 engine이 필요합니다.');
  return policy;
}

function validateAbilityProfile(value: unknown, positionValue: Position, path: string): void {
  const lane = ['COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'FARMING', 'LANE_PRESSURE', 'MAP_AWARENESS', 'MECHANICS', 'POSITIONING', 'PRIORITY_CONVERSION', 'SIDE_LANE', 'TRADING', 'WAVE_MANAGEMENT'];
  const jungle = ['COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'ENEMY_JUNGLE_TRACKING', 'JUNGLE_RESOURCE_MANAGEMENT', 'LANE_INTERVENTION', 'MAP_AWARENESS', 'MECHANICS', 'OBJECTIVE_DECISION', 'OBJECTIVE_SECURE', 'PATHING', 'POSITIONING'];
  const support = ['ALLY_PROTECTION', 'AREA_SETUP', 'COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'ENGAGE_EXECUTION', 'LANE_SUPPORT', 'MAP_AWARENESS', 'MECHANICS', 'POSITIONING', 'ROTATION_PLANNING', 'VISION_CONTROL'];
  const expected = positionValue === 'JUNGLE' ? jungle : positionValue === 'SUPPORT' ? support : lane;
  const profile = record(value, path); literal(profile.schemaVersion, 'PLAYER_ABILITY_PROFILE_V1', `${path}.schemaVersion`);
  const base = record(profile.baseRatings, `${path}.baseRatings`); const realized = record(profile.realizedRatings, `${path}.realizedRatings`); const deltas = record(profile.realizationDeltas, `${path}.realizationDeltas`);
  exactSet(Object.keys(base), expected, `${path}.baseRatings`); exactSet(Object.keys(realized), expected, `${path}.realizedRatings`); exactSet(Object.keys(deltas), expected, `${path}.realizationDeltas`);
  expected.forEach((key) => { integer(base[key], `${path}.baseRatings.${key}`, 1); finite(realized[key], `${path}.realizedRatings.${key}`); finite(deltas[key], `${path}.realizationDeltas.${key}`); });
  integer(profile.selectedChampionProficiency, `${path}.selectedChampionProficiency`, 1); finite(profile.proficiencyExecutionAdjustment, `${path}.proficiencyExecutionAdjustment`);
}

function validateCommonMatch(match: JsonRecord, expectation: PlayerDraftSessionExpectation, assignments: readonly unknown[]): { players: Map<string, PlayerIdentity>; result: JsonRecord } {
  const teams = array(match.teams, '$.match.teams');
  if (teams.length !== 2) fail('$.match.teams', 'BLUE/RED 두 팀이 필요합니다.');
  const players = new Map<string, PlayerIdentity>(); const teamCodes = new Map<TeamSide, string>();
  teams.forEach((value, index) => {
    const path = `$.match.teams[${index}]`; const team = record(value, path); const teamSide = side(team.teamSide, `${path}.teamSide`);
    if (teamCodes.has(teamSide)) fail(`${path}.teamSide`, '중복 side입니다.');
    const teamCode = text(team.teamCode, `${path}.teamCode`);
    if (teamCode !== (teamSide === 'BLUE' ? expectation.blueTeamCode : expectation.redTeamCode)) fail(`${path}.teamCode`, 'session 팀과 일치하지 않습니다.');
    teamCodes.set(teamSide, teamCode); text(team.displayName, `${path}.displayName`);
    const lineup = array(team.lineup, `${path}.lineup`); if (lineup.length !== 5) fail(`${path}.lineup`, '선발 5명이 필요합니다.');
    const positions: string[] = [];
    lineup.forEach((playerValue, playerIndex) => {
      const playerPath = `${path}.lineup[${playerIndex}]`; const player = record(playerValue, playerPath); const playerId = text(player.playerId, `${playerPath}.playerId`);
      if (players.has(playerId)) fail(`${playerPath}.playerId`, '중복 player ID입니다.');
      text(player.nickname, `${playerPath}.nickname`); const playerPosition = position(player.position, `${playerPath}.position`); positions.push(playerPosition);
      const championId = text(player.championId, `${playerPath}.championId`); if (validateChampion(player.champion, `${playerPath}.champion`) !== championId) fail(`${playerPath}.champion`, 'champion identity가 일치하지 않습니다.');
      players.set(playerId, { side: teamSide, position: playerPosition, championId });
    }); exactSet(positions, POSITIONS, `${path}.lineup.position`);
  });
  exactSet([...teamCodes.keys()], SIDES, '$.match.teams.teamSide');
  assignments.forEach((value, index) => {
    const path = `$.session.completedDraft.finalAssignments[${index}]`; const assignment = record(value, path); const player = players.get(String(assignment.playerId));
    if (!player || player.side !== assignment.teamSide || player.position !== assignment.position || player.championId !== assignment.championId) fail(path, 'match presentation과 final assignment가 일치하지 않습니다.');
  });

  const result = record(match.result, '$.match.result'); literal(result.schemaVersion, 'MATCH_RESULT_SUMMARY_V1', '$.match.result.schemaVersion');
  const winner = result.winner === null ? null : side(result.winner, '$.match.result.winner');
  const endReason = oneOf(result.endReason, END_REASONS, '$.match.result.endReason'); const duration = integer(result.durationSeconds, '$.match.result.durationSeconds', 1);
  const resultTeams = array(result.teams, '$.match.result.teams'); if (resultTeams.length !== 2) fail('$.match.result.teams', '팀 결과 2개가 필요합니다.');
  const resultTeamMap = new Map<TeamSide, JsonRecord>();
  resultTeams.forEach((value, index) => {
    const path = `$.match.result.teams[${index}]`; const team = record(value, path); const teamSide = side(team.teamSide, `${path}.teamSide`);
    if (resultTeamMap.has(teamSide) || team.teamIdentity !== teamCodes.get(teamSide)) fail(path, '중복 없이 presentation과 일치하는 팀 결과가 필요합니다.');
    for (const key of ['kills', 'totalGold', 'dragons', 'towersDestroyed', 'inhibitorsRemaining', 'nexusTurretsRemaining', 'alivePlayers']) integer(team[key], `${path}.${key}`);
    for (const key of ['hasDragonSoul', 'hasBaronBuff', 'hasElderBuff', 'nexusAlive']) bool(team[key], `${path}.${key}`);
    resultTeamMap.set(teamSide, team);
  }); exactSet([...resultTeamMap.keys()], SIDES, '$.match.result.teams.teamSide');
  const resultPlayers = array(result.players, '$.match.result.players'); if (resultPlayers.length !== 10) fail('$.match.result.players', '선수 결과 10개가 필요합니다.');
  const resultPlayerMap = new Map<string, JsonRecord>();
  resultPlayers.forEach((value, index) => {
    const path = `$.match.result.players[${index}]`; const player = record(value, path); const playerId = text(player.playerId, `${path}.playerId`); const identity = players.get(playerId);
    if (!identity || resultPlayerMap.has(playerId) || identity.side !== player.teamSide || identity.position !== player.position || identity.championId !== player.championId) fail(path, 'presentation과 일치하는 중복 없는 선수 결과가 필요합니다.');
    for (const key of ['kills', 'deaths', 'assists', 'cs', 'gold', 'totalExperience', 'level']) integer(player[key], `${path}.${key}`);
    validateAbilityProfile(player.abilityProfile, identity.position, `${path}.abilityProfile`); resultPlayerMap.set(playerId, player);
  });
  for (const key of ['finalDraftHash', 'finalAssignmentHash', 'configurationHash', 'resourceProvenanceHash', 'replayProvenanceHash']) sha(result[key], `$.match.result.${key}`);
  text(result.runtimeProfileId, '$.match.result.runtimeProfileId');

  const timeline = record(match.timeline, '$.match.timeline'); literal(timeline.schemaVersion, 'MATCH_ENGINE_TIMELINE_V1', '$.match.timeline.schemaVersion');
  if (integer(timeline.durationSeconds, '$.match.timeline.durationSeconds', 1) !== duration || timeline.winner !== winner || timeline.endReason !== endReason) fail('$.match.timeline', 'result의 duration/winner/endReason과 일치해야 합니다.');
  const events = array(timeline.events, '$.match.timeline.events'); if (!events.length) fail('$.match.timeline.events', '이벤트가 필요합니다.'); let eventTime = -1;
  events.forEach((value, index) => {
    const path = `$.match.timeline.events[${index}]`; const event = record(value, path); const time = integer(event.timeSeconds, `${path}.timeSeconds`);
    if (time < eventTime || time > duration) fail(`${path}.timeSeconds`, '시간순으로 경기 범위 안에 있어야 합니다.'); eventTime = time;
    oneOf(event.eventType, EVENT_TYPES, `${path}.eventType`); if (event.actorSide !== null) side(event.actorSide, `${path}.actorSide`); if (event.actorPosition !== null) position(event.actorPosition, `${path}.actorPosition`); if (event.lane !== null) oneOf(event.lane, LANES, `${path}.lane`);
    for (const key of ['actorPlayerId', 'killerPlayerId', 'victimPlayerId', 'killerChampionId', 'victimChampionId', 'actionId', 'parentActionId', 'displayMessage']) nullableText(event[key], `${path}.${key}`);
    strings(event.assistantPlayerIds, `${path}.assistantPlayerIds`); strings(event.assistantChampionIds, `${path}.assistantChampionIds`);
    if (event.combatSource !== null) oneOf(event.combatSource, COMBAT_SOURCES, `${path}.combatSource`); if (event.structureActionSource !== null) oneOf(event.structureActionSource, STRUCTURE_SOURCES, `${path}.structureActionSource`);
    if (event.structureKind !== null) oneOf(event.structureKind, STRUCTURE_KINDS, `${path}.structureKind`); if (event.structureTowerTier !== null) oneOf(event.structureTowerTier, TOWER_TIERS, `${path}.structureTowerTier`);
    if (event.structureAttackingSide !== null) side(event.structureAttackingSide, `${path}.structureAttackingSide`); if (event.structureDefendingSide !== null) side(event.structureDefendingSide, `${path}.structureDefendingSide`);
    integer(event.goldAmount, `${path}.goldAmount`); finite(event.bountyRawBeforePayout, `${path}.bountyRawBeforePayout`); record(event.structuredData, `${path}.structuredData`);
  });
  const snapshots = array(timeline.snapshots, '$.match.timeline.snapshots'); if (snapshots.length < 2) fail('$.match.timeline.snapshots', '시작/종료 snapshot이 필요합니다.'); let snapshotTime = -1;
  snapshots.forEach((value, index) => {
    const path = `$.match.timeline.snapshots[${index}]`; const snapshot = record(value, path); const time = integer(snapshot.timeSeconds, `${path}.timeSeconds`);
    if (time < snapshotTime || time > duration || (index === 0 && time !== 0)) fail(`${path}.timeSeconds`, '0초부터 정렬된 snapshot이 필요합니다.'); snapshotTime = time;
    for (const [key, teamSide] of [['blueTeam', 'BLUE'], ['redTeam', 'RED']] as const) {
      const team = record(snapshot[key], `${path}.${key}`); if (team.teamIdentity !== teamCodes.get(teamSide) || team.teamSide !== teamSide) fail(`${path}.${key}`, 'presentation 팀과 일치해야 합니다.');
      for (const field of ['kills', 'gold', 'dragons', 'elderBuffRemainingSeconds', 'towersDestroyed', 'inhibitorsRemaining', 'nexusTurretsRemaining', 'alivePlayers']) integer(team[field], `${path}.${key}.${field}`);
      for (const field of ['hasDragonSoul', 'hasBaronBuff', 'hasElderBuff', 'nexusAlive']) bool(team[field], `${path}.${key}.${field}`);
    }
    const states = array(snapshot.players, `${path}.players`); if (states.length !== 10) fail(`${path}.players`, '선수 상태 10개가 필요합니다.'); const seen = new Set<string>();
    states.forEach((stateValue, stateIndex) => {
      const statePath = `${path}.players[${stateIndex}]`; const state = record(stateValue, statePath); const playerId = text(state.playerId, `${statePath}.playerId`); const identity = players.get(playerId);
      if (!identity || seen.has(playerId) || state.teamSide !== identity.side || state.position !== identity.position || state.championId !== identity.championId) fail(statePath, 'presentation과 일치하는 중복 없는 선수 상태가 필요합니다.'); seen.add(playerId);
      for (const key of ['kills', 'deaths', 'assists', 'cs', 'gold', 'respawnAtSeconds', 'respawnRemainingSeconds', 'farmResumeAtSeconds', 'farmReturnSecondsRemaining', 'shutdownBountyGold', 'totalExperience', 'level']) integer(state[key], `${statePath}.${key}`);
      integer(state.activityUntilSeconds, `${statePath}.activityUntilSeconds`, -1); bool(state.alive, `${statePath}.alive`); bool(state.canFarm, `${statePath}.canFarm`); finite(state.bountyProgress, `${statePath}.bountyProgress`);
      if (state.activityType !== null) oneOf(state.activityType, ACTIVITIES, `${statePath}.activityType`); if (state.activityOriginLane !== null) oneOf(state.activityOriginLane, LANES, `${statePath}.activityOriginLane`); if (state.activityTargetLane !== null) oneOf(state.activityTargetLane, LANES, `${statePath}.activityTargetLane`);
      text(state.itemProgressStage, `${statePath}.itemProgressStage`); record(state.structuredProgression, `${statePath}.structuredProgression`);
    }); record(snapshot.structuredState, `${path}.structuredState`);
  });
  const finalSnapshot = record(snapshots[snapshots.length - 1], '$.match.timeline.snapshots[last]'); if (finalSnapshot.timeSeconds !== duration) fail('$.match.timeline.snapshots[last].timeSeconds', '종료 시간과 일치해야 합니다.');
  return { players, result };
}

export function validatePlayerDraftSimulationPayload(value: unknown, expectation: PlayerDraftSessionExpectation): PlayerDraftSimulationResponseDto {
  const root = record(value, '$'); literal(root.schemaVersion, 'PLAYER_DRAFT_MATCH_RESPONSE_V1', '$.schemaVersion');
  const session = validatePlayerDraftSessionPayload(root.session, expectation);
  if (session.status !== 'SIMULATED' || !session.completedDraft) fail('$.session.status', 'SIMULATED completed session이 필요합니다.');
  const match = record(root.match, '$.match'); literal(match.schemaVersion, 'PLAYER_DRAFT_MATCH_PAYLOAD_V1', '$.match.schemaVersion'); text(match.matchIdentity, '$.match.matchIdentity');
  if (signedInt64(match.seed, '$.match.seed') !== session.seed) fail('$.match.seed', 'session seed와 일치해야 합니다.');
  const production = validateProductionPolicy(match.productionPolicy, '$.match.productionPolicy');
  const common = validateCommonMatch(match, expectation, session.completedDraft.finalAssignments);
  const draft = record(match.draft, '$.match.draft');
  const draftIdentity = sha(draft.draftIdentity, '$.match.draft.draftIdentity'); const finalDraftHash = sha(draft.finalDraftHash, '$.match.draft.finalDraftHash'); const finalAssignmentHash = sha(draft.finalAssignmentHash, '$.match.draft.finalAssignmentHash');
  if (draftIdentity !== session.completedDraft.draftIdentity) fail('$.match.draft.draftIdentity', 'completed Draft identity와 일치해야 합니다.');
  const autoPolicy = validatePolicy(draft.autoDraftSelectionPolicy, '$.match.draft.autoDraftSelectionPolicy'); const playerPolicy = validatePolicy(draft.playerControlPolicy, '$.match.draft.playerControlPolicy');
  if (autoPolicy.policyId !== session.autoDraftSelectionPolicy.policyId || autoPolicy.policyHash !== session.autoDraftSelectionPolicy.policyHash) fail('$.match.draft.autoDraftSelectionPolicy', 'session policy와 일치해야 합니다.');
  if (playerPolicy.policyId !== session.playerControlPolicy.policyId || playerPolicy.policyHash !== session.playerControlPolicy.policyHash) fail('$.match.draft.playerControlPolicy', 'session policy와 일치해야 합니다.');
  sha(draft.autoSelectionTraceHash, '$.match.draft.autoSelectionTraceHash'); const controlEvidenceHash = sha(draft.controlEvidenceHash, '$.match.draft.controlEvidenceHash');
  if (controlEvidenceHash !== session.completedDraft.controlEvidenceHash) fail('$.match.draft.controlEvidenceHash', 'completed control evidence와 일치해야 합니다.');
  const matchDecisions = array(draft.decisions, '$.match.draft.decisions').map((item, index) => validateEvidence(item, index, session.controlledSide, `$.match.draft.decisions[${index}]`));
  if (matchDecisions.length !== 20 || JSON.stringify(matchDecisions) !== JSON.stringify(session.decisions)) fail('$.match.draft.decisions', 'session의 mixed-authority evidence와 정확히 일치해야 합니다.');
  if (common.result.finalDraftHash !== finalDraftHash || common.result.finalAssignmentHash !== finalAssignmentHash) fail('$.match.result', 'Draft binding hash와 일치해야 합니다.');

  const integrity = record(match.integrity, '$.match.integrity');
  for (const key of ['runtimeProfileId', 'engineImplementationVersion', 'activeGameplayRulesVersion', 'controlPolicyId', 'randomFingerprint']) if (integrity[key] === undefined) fail(`$.match.integrity.${key}`, '필수 값입니다.');
  for (const key of ['configurationHash', 'controlPolicyHash', 'controlEvidenceHash', 'inputHash', 'replayProvenanceHash', 'resourceProvenanceHash', 'simulatorTimelineHash', 'structuredTimelineHash', 'outputHash']) sha(integrity[key], `$.match.integrity.${key}`);
  if (integrity.runtimeProfileId !== production.runtimeProfileId || integrity.configurationHash !== production.configurationHash || integrity.engineImplementationVersion !== production.engineImplementationVersion || integrity.activeGameplayRulesVersion !== production.activeGameplayRulesVersion) fail('$.match.integrity', 'Production policy identity와 일치해야 합니다.');
  if (integrity.controlPolicyId !== playerPolicy.policyId || integrity.controlPolicyHash !== playerPolicy.policyHash || integrity.controlEvidenceHash !== controlEvidenceHash) fail('$.match.integrity', 'Player control evidence와 일치해야 합니다.');
  if (integrity.resourceProvenanceHash !== common.result.resourceProvenanceHash || integrity.replayProvenanceHash !== common.result.replayProvenanceHash || integrity.configurationHash !== common.result.configurationHash || integrity.runtimeProfileId !== common.result.runtimeProfileId) fail('$.match.integrity', 'result provenance와 일치해야 합니다.');
  const random = record(integrity.randomFingerprint, '$.match.integrity.randomFingerprint'); text(random.schemaVersion, '$.match.integrity.randomFingerprint.schemaVersion'); integer(random.randomDrawCount, '$.match.integrity.randomFingerprint.randomDrawCount'); sha(random.randomTraceHash, '$.match.integrity.randomFingerprint.randomTraceHash'); text(random.randomTraceHashAlgorithm, '$.match.integrity.randomFingerprint.randomTraceHashAlgorithm');
  if (integrity.diagnosticsExcludedFromGameplayIdentity !== true) fail('$.match.integrity.diagnosticsExcludedFromGameplayIdentity', 'true여야 합니다.');
  return value as PlayerDraftSimulationResponseDto;
}

export function validatePlayerDraftApiErrorPayload(value: unknown): PlayerDraftApiErrorDto {
  const root = record(value, '$'); literal(root.schemaVersion, 'PLAYER_DRAFT_API_ERROR_V1', '$.schemaVersion');
  text(root.code, '$.code'); if (root.field !== null) text(root.field, '$.field'); text(root.message, '$.message');
  return value as PlayerDraftApiErrorDto;
}

export const playerDraftUnavailableReasons: readonly PlayerDraftUnavailableReason[] = UNAVAILABLE_REASONS;

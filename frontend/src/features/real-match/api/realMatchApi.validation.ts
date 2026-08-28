import type {
  AbilityRatingKey, CombatSource, DraftActionType, GameEndReason, Lane, MatchEventType,
  PlayerActivityType, Position, StructureActionSource, StructureKind, TeamSide, TowerTier,
} from '../realMatch.contract';
import type {
  RealMatchApiErrorDto, RealMatchOptionsDto, RealMatchResponseDto, RealMatchSimulateRequestDto,
} from './realMatchApi.types';
import { validateCommonMatchSemantics } from './commonMatchSemantic.validation.ts';

type JsonRecord = Record<string, unknown>;
type PresentationPlayerIdentity = { side: TeamSide; position: Position; championId: string };

const TEAM_SIDES = ['BLUE', 'RED'] as const;
const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'] as const;
const LANES = ['TOP', 'MID', 'BOT'] as const;
const DRAFT_ACTIONS = ['PICK', 'BAN'] as const;
const END_REASONS = ['NEXUS_DESTROYED', 'SIMULATION_TIMEOUT'] as const;
const EVENT_TYPES = [
  'GAME_START', 'KILL', 'ASSIST', 'JUNGLE_GANK', 'COUNTER_GANK', 'LANE_COMBAT', 'ROAM',
  'SHUTDOWN', 'DRAGON', 'BARON', 'ELDER', 'TOWER', 'TEAMFIGHT', 'TEAMFIGHT_RESULT', 'ACE',
  'STRUCTURE_ACTION',
  'MATCH_PHASE_CHANGE', 'MACRO_ACTION', 'LATE_GAME_ACTION', 'LEVEL_UP', 'ITEM_STAGE_REACHED', 'GAME_END',
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
const ACTIVITY_TYPES = ['DEFAULT_ROLE', 'ROAMING', 'SIEGING'] as const;
const LANE_RATINGS = [
  'COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'FARMING', 'LANE_PRESSURE',
  'MAP_AWARENESS', 'MECHANICS', 'POSITIONING', 'PRIORITY_CONVERSION', 'SIDE_LANE',
  'TRADING', 'WAVE_MANAGEMENT',
] as const;
const JUNGLE_RATINGS = [
  'COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'ENEMY_JUNGLE_TRACKING',
  'JUNGLE_RESOURCE_MANAGEMENT', 'LANE_INTERVENTION', 'MAP_AWARENESS', 'MECHANICS',
  'OBJECTIVE_DECISION', 'OBJECTIVE_SECURE', 'PATHING', 'POSITIONING',
] as const;
const SUPPORT_RATINGS = [
  'ALLY_PROTECTION', 'AREA_SETUP', 'COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING',
  'ENGAGE_EXECUTION', 'LANE_SUPPORT', 'MAP_AWARENESS', 'MECHANICS', 'POSITIONING',
  'ROTATION_PLANNING', 'VISION_CONTROL',
] as const;

export class RealMatchContractError extends Error {
  constructor(public readonly path: string, message: string) {
    super(`${path}: ${message}`);
    this.name = 'RealMatchContractError';
  }
}

function fail(path: string, message: string): never { throw new RealMatchContractError(path, message); }
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
function nullableText(value: unknown, path: string): string | null {
  return value === null ? null : text(value, path);
}
function integer(value: unknown, path: string, minimum = 0): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < minimum) fail(path, `${minimum} 이상의 정수가 필요합니다.`);
  return value;
}
function signedInteger(value: unknown, path: string): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value)) fail(path, 'safe integer가 필요합니다.');
  return value;
}
function finiteNumber(value: unknown, path: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) fail(path, '유한한 숫자가 필요합니다.');
  return value;
}
function bool(value: unknown, path: string): boolean {
  if (typeof value !== 'boolean') fail(path, 'boolean 값이 필요합니다.');
  return value;
}
function oneOf<T extends string>(value: unknown, values: readonly T[], path: string): T {
  if (typeof value !== 'string' || !values.includes(value as T)) fail(path, `지원하지 않는 enum 값입니다: ${String(value)}`);
  return value as T;
}
function nullableOneOf<T extends string>(value: unknown, values: readonly T[], path: string): T | null {
  return value === null ? null : oneOf(value, values, path);
}
function literal(value: unknown, expected: string, path: string): void {
  if (value !== expected) fail(path, `${expected}가 필요합니다.`);
}
function stringArray(value: unknown, path: string): readonly string[] {
  return array(value, path).map((item, index) => text(item, `${path}[${index}]`));
}
function exactSet(values: readonly string[], expected: readonly string[], path: string): void {
  const actual = new Set(values);
  if (actual.size !== values.length || actual.size !== expected.length || expected.some((item) => !actual.has(item))) {
    fail(path, `${expected.join(', ')}의 중복 없는 정확한 구성이 필요합니다.`);
  }
}
function exactArray(values: readonly string[], expected: readonly string[], path: string): void {
  if (values.length !== expected.length || values.some((item, index) => item !== expected[index])) {
    fail(path, `${expected.join(', ')} 순서와 정확히 일치해야 합니다.`);
  }
}
function teamSide(value: unknown, path: string): TeamSide { return oneOf(value, TEAM_SIDES, path); }
function position(value: unknown, path: string): Position { return oneOf(value, POSITIONS, path); }
function validateTeamCode(value: unknown, path: string): string {
  const code = text(value, path);
  if (!/^[A-Z0-9]{2,5}$/.test(code)) fail(path, 'stable team code 형식이 올바르지 않습니다.');
  return code;
}
function validateStringMap(value: unknown, path: string): void {
  const source = record(value, path);
  for (const [key, item] of Object.entries(source)) {
    text(key, `${path}.key`); text(item, `${path}.${key}`);
  }
}

function validateProductionPolicy(value: unknown, path: string): void {
  const source = record(value, path);
  for (const key of [
    'policyId', 'policyHash', 'activationDecisionSchema', 'activationDecisionCode',
    'acceptanceStatus', 'knownDiagnosticLimitation', 'rollbackProfileId', 'rollbackMode',
    'draftSelectionPolicyId', 'draftSelectionPolicyHash',
    'runtimeProfileId', 'configurationHash', 'activeGameplayRulesVersion',
    'engineImplementationVersion', 'matchupMode', 'compositionMode', 'jungleClearContribution',
  ]) text(source[key], `${path}.${key}`);
  const limitations = array(source.knownDiagnosticLimitations, `${path}.knownDiagnosticLimitations`);
  if (limitations.length === 0) fail(`${path}.knownDiagnosticLimitations`, '최소 한 개가 필요합니다.');
  limitations.forEach((value, index) => text(value, `${path}.knownDiagnosticLimitations[${index}]`));
  if (limitations[0] !== source.knownDiagnosticLimitation) fail(`${path}.knownDiagnosticLimitations`, '첫 제한은 compatibility primary 제한과 일치해야 합니다.');
  for (const key of ['statisticalHoldoutApproved', 'automaticFallback', 'economyCandidateActivation', 'tempoCandidateActivation', 'diagnosticsExcludedFromGameplayIdentity']) {
    bool(source[key], `${path}.${key}`);
  }
  if (source.statisticalHoldoutApproved !== false || source.automaticFallback !== false) fail(path, '통계 holdout 승인과 자동 fallback은 false여야 합니다.');
}

export function validateRealMatchOptionsPayload(value: unknown): RealMatchOptionsDto {
  const root = record(value, '$');
  literal(root.schemaVersion, 'REAL_MATCH_OPTIONS_V1', '$.schemaVersion');
  text(root.matchEngineContract, '$.matchEngineContract');
  validateProductionPolicy(root.productionPolicy, '$.productionPolicy');
  const seedPolicy = record(root.seedPolicy, '$.seedPolicy');
  bool(seedPolicy.required, '$.seedPolicy.required');
  literal(seedPolicy.encoding, 'SIGNED_INT64_DECIMAL_STRING', '$.seedPolicy.encoding');
  const teams = array(root.teams, '$.teams');
  if (teams.length !== 10) fail('$.teams', '정확히 10개 팀이 필요합니다.');
  const teamCodes = new Set<string>();
  const playerIds = new Set<string>();
  teams.forEach((teamValue, teamIndex) => {
    const path = `$.teams[${teamIndex}]`;
    const team = record(teamValue, path);
    const code = validateTeamCode(team.teamCode, `${path}.teamCode`);
    if (teamCodes.has(code)) fail(`${path}.teamCode`, '중복 team identity입니다.');
    teamCodes.add(code);
    text(team.displayName, `${path}.displayName`);
    const lineup = array(team.lineup, `${path}.lineup`);
    if (lineup.length !== 5) fail(`${path}.lineup`, '팀별 선발 선수는 정확히 5명이어야 합니다.');
    const teamPositions: string[] = [];
    lineup.forEach((playerValue, playerIndex) => {
      const playerPath = `${path}.lineup[${playerIndex}]`;
      const player = record(playerValue, playerPath);
      const playerId = text(player.playerId, `${playerPath}.playerId`);
      if (playerIds.has(playerId)) fail(`${playerPath}.playerId`, '중복 stable player ID입니다.');
      playerIds.add(playerId);
      text(player.nickname, `${playerPath}.nickname`);
      teamPositions.push(position(player.position, `${playerPath}.position`));
    });
    exactSet(teamPositions, POSITIONS, `${path}.lineup.position`);
  });
  if (playerIds.size !== 50) fail('$.teams', '정확히 50개의 stable player ID가 필요합니다.');
  const resources = record(root.resourceVersions, '$.resourceVersions');
  text(resources.resourceProvenanceHash, '$.resourceVersions.resourceProvenanceHash');
  validateStringMap(resources.versions, '$.resourceVersions.versions');
  return value as RealMatchOptionsDto;
}

function validateChampion(value: unknown, path: string, championId: string): void {
  const champion = record(value, path);
  if (text(champion.championId, `${path}.championId`) !== championId) fail(`${path}.championId`, '상위 championId와 일치해야 합니다.');
  text(champion.displayNameKo, `${path}.displayNameKo`);
  text(champion.displayNameEn, `${path}.displayNameEn`);
  text(champion.portraitUrl, `${path}.portraitUrl`);
}

function expectedRatings(positionValue: Position): readonly AbilityRatingKey[] {
  if (positionValue === 'JUNGLE') return JUNGLE_RATINGS;
  if (positionValue === 'SUPPORT') return SUPPORT_RATINGS;
  return LANE_RATINGS;
}
function validateAbilityProfile(value: unknown, positionValue: Position, path: string): void {
  const profile = record(value, path);
  literal(profile.schemaVersion, 'PLAYER_ABILITY_PROFILE_V1', `${path}.schemaVersion`);
  const expected = expectedRatings(positionValue);
  const baseRatings = record(profile.baseRatings, `${path}.baseRatings`);
  const realizedRatings = record(profile.realizedRatings, `${path}.realizedRatings`);
  const realizationDeltas = record(profile.realizationDeltas, `${path}.realizationDeltas`);
  for (const [field, ratings] of Object.entries({ baseRatings, realizedRatings, realizationDeltas })) {
    exactSet(Object.keys(ratings), expected, `${path}.${field}`);
  }
  for (const key of expected) {
    const base = integer(baseRatings[key], `${path}.baseRatings.${key}`, 1);
    if (base > 20) fail(`${path}.baseRatings.${key}`, '20 이하여야 합니다.');
    const realized = finiteNumber(realizedRatings[key], `${path}.realizedRatings.${key}`);
    if (realized < 1 || realized > 20) fail(`${path}.realizedRatings.${key}`, '1 이상 20 이하여야 합니다.');
    const delta = finiteNumber(realizationDeltas[key], `${path}.realizationDeltas.${key}`);
    if (realized - base !== delta) fail(`${path}.realizationDeltas.${key}`, 'realizedRating - baseRating과 일치해야 합니다.');
  }
  const proficiency = integer(profile.selectedChampionProficiency, `${path}.selectedChampionProficiency`, 1);
  if (proficiency > 20) fail(`${path}.selectedChampionProficiency`, '20 이하여야 합니다.');
  finiteNumber(profile.proficiencyExecutionAdjustment, `${path}.proficiencyExecutionAdjustment`);
}

function validateTeamState(value: unknown, expectedSide: TeamSide, expectedTeamCode: string, path: string): void {
  const state = record(value, path);
  if (text(state.teamIdentity, `${path}.teamIdentity`) !== expectedTeamCode) fail(`${path}.teamIdentity`, 'response team identity와 일치해야 합니다.');
  if (teamSide(state.teamSide, `${path}.teamSide`) !== expectedSide) fail(`${path}.teamSide`, `${expectedSide}여야 합니다.`);
  for (const key of ['kills', 'gold', 'dragons', 'elderBuffRemainingSeconds', 'towersDestroyed', 'inhibitorsRemaining', 'nexusTurretsRemaining', 'alivePlayers']) {
    integer(state[key], `${path}.${key}`);
  }
  for (const key of ['hasDragonSoul', 'hasBaronBuff', 'hasElderBuff', 'nexusAlive']) bool(state[key], `${path}.${key}`);
}

function validatePlayerState(value: unknown, path: string): { playerId: string; side: TeamSide; position: Position; championId: string } {
  const state = record(value, path);
  const playerId = text(state.playerId, `${path}.playerId`);
  const side = teamSide(state.teamSide, `${path}.teamSide`);
  const positionValue = position(state.position, `${path}.position`);
  const championId = text(state.championId, `${path}.championId`);
  for (const key of [
    'kills', 'deaths', 'assists', 'cs', 'gold', 'respawnAtSeconds', 'respawnRemainingSeconds',
    'farmResumeAtSeconds', 'farmReturnSecondsRemaining', 'shutdownBountyGold',
    'totalExperience', 'level',
  ]) integer(state[key], `${path}.${key}`);
  integer(state.activityUntilSeconds, `${path}.activityUntilSeconds`, -1);
  bool(state.alive, `${path}.alive`); bool(state.canFarm, `${path}.canFarm`);
  finiteNumber(state.bountyProgress, `${path}.bountyProgress`);
  nullableOneOf(state.activityType, ACTIVITY_TYPES, `${path}.activityType`) as PlayerActivityType | null;
  nullableOneOf(state.activityOriginLane, LANES, `${path}.activityOriginLane`) as Lane | null;
  nullableOneOf(state.activityTargetLane, LANES, `${path}.activityTargetLane`) as Lane | null;
  text(state.itemProgressStage, `${path}.itemProgressStage`);
  record(state.structuredProgression, `${path}.structuredProgression`);
  return { playerId, side, position: positionValue, championId };
}

function validateEvent(
  value: unknown,
  index: number,
  durationSeconds: number,
  presentationPlayers: ReadonlyMap<string, PresentationPlayerIdentity>,
): number {
  const path = `$.timeline.events[${index}]`;
  const event = record(value, path);
  const time = integer(event.timeSeconds, `${path}.timeSeconds`);
  if (time > durationSeconds) fail(`${path}.timeSeconds`, '경기 시간을 벗어났습니다.');
  oneOf(event.eventType, EVENT_TYPES, `${path}.eventType`) as MatchEventType;
  const actorSide = nullableOneOf(event.actorSide, TEAM_SIDES, `${path}.actorSide`) as TeamSide | null;
  const actorPosition = nullableOneOf(event.actorPosition, POSITIONS, `${path}.actorPosition`) as Position | null;
  nullableOneOf(event.lane, LANES, `${path}.lane`) as Lane | null;
  const actorPlayerId = nullableText(event.actorPlayerId, `${path}.actorPlayerId`);
  const killerPlayerId = nullableText(event.killerPlayerId, `${path}.killerPlayerId`);
  const victimPlayerId = nullableText(event.victimPlayerId, `${path}.victimPlayerId`);
  const killerChampionId = nullableText(event.killerChampionId, `${path}.killerChampionId`);
  const victimChampionId = nullableText(event.victimChampionId, `${path}.victimChampionId`);
  for (const key of ['actionId', 'parentActionId', 'displayMessage']) nullableText(event[key], `${path}.${key}`);
  const actor = actorPlayerId === null ? null : presentationPlayers.get(actorPlayerId);
  if (actorPlayerId !== null && !actor) fail(`${path}.actorPlayerId`, 'presentation에 없는 선수입니다.');
  if (actor && (actor.side !== actorSide || actor.position !== actorPosition)) fail(`${path}.actorPlayerId`, 'actor side/position과 선수 identity가 일치하지 않습니다.');
  for (const [role, playerId, championId] of [
    ['killer', killerPlayerId, killerChampionId], ['victim', victimPlayerId, victimChampionId],
  ] as const) {
    if ((playerId === null) !== (championId === null)) fail(`${path}.${role}PlayerId`, 'player/champion identity는 함께 제공되어야 합니다.');
    if (playerId !== null) {
      const identity = presentationPlayers.get(playerId);
      if (!identity) fail(`${path}.${role}PlayerId`, 'presentation에 없는 선수입니다.');
      if (identity.championId !== championId) fail(`${path}.${role}ChampionId`, '선수의 최종 champion identity와 일치하지 않습니다.');
    }
  }
  const assistantPlayerIds = stringArray(event.assistantPlayerIds, `${path}.assistantPlayerIds`);
  const assistantChampionIds = stringArray(event.assistantChampionIds, `${path}.assistantChampionIds`);
  if (assistantPlayerIds.length !== assistantChampionIds.length || new Set(assistantPlayerIds).size !== assistantPlayerIds.length) {
    fail(`${path}.assistantPlayerIds`, 'assistant player/champion identity가 중복 없이 쌍을 이뤄야 합니다.');
  }
  assistantPlayerIds.forEach((playerId, assistantIndex) => {
    const identity = presentationPlayers.get(playerId);
    if (!identity) fail(`${path}.assistantPlayerIds[${assistantIndex}]`, 'presentation에 없는 선수입니다.');
    if (identity.championId !== assistantChampionIds[assistantIndex]) fail(`${path}.assistantChampionIds[${assistantIndex}]`, '선수의 최종 champion identity와 일치하지 않습니다.');
  });
  nullableOneOf(event.combatSource, COMBAT_SOURCES, `${path}.combatSource`) as CombatSource | null;
  nullableOneOf(event.structureActionSource, STRUCTURE_SOURCES, `${path}.structureActionSource`) as StructureActionSource | null;
  nullableOneOf(event.structureKind, STRUCTURE_KINDS, `${path}.structureKind`) as StructureKind | null;
  nullableOneOf(event.structureTowerTier, TOWER_TIERS, `${path}.structureTowerTier`) as TowerTier | null;
  nullableOneOf(event.structureAttackingSide, TEAM_SIDES, `${path}.structureAttackingSide`) as TeamSide | null;
  nullableOneOf(event.structureDefendingSide, TEAM_SIDES, `${path}.structureDefendingSide`) as TeamSide | null;
  integer(event.goldAmount, `${path}.goldAmount`);
  finiteNumber(event.bountyRawBeforePayout, `${path}.bountyRawBeforePayout`);
  record(event.structuredData, `${path}.structuredData`);
  return time;
}

function validateIntegrity(value: unknown): void {
  const integrity = record(value, '$.integrity');
  for (const key of [
    'matchEngineContract', 'policyId', 'policyHash', 'runtimeProfileId', 'configurationHash',
    'acceptanceStatus', 'rollbackProfileId',
    'engineImplementationVersion', 'activeGameplayRulesVersion', 'draftSelectionPolicyId',
    'draftSelectionPolicyHash', 'draftSelectionTraceHashAlgorithm', 'draftSelectionTraceHash',
    'inputHash', 'inputHashAlgorithm',
    'resourceProvenanceHash', 'replayProvenanceHash', 'replayProvenanceHashAlgorithm',
    'simulatorTimelineHash', 'simulatorTimelineHashAlgorithm', 'structuredTimelineHash',
    'structuredTimelineHashAlgorithm', 'outputHash', 'outputHashAlgorithm', 'outputHashScope',
  ]) text(integrity[key], `$.integrity.${key}`);
  const limitations = array(integrity.knownDiagnosticLimitations, '$.integrity.knownDiagnosticLimitations');
  if (limitations.length === 0) fail('$.integrity.knownDiagnosticLimitations', '최소 한 개가 필요합니다.');
  limitations.forEach((value, index) => text(value, `$.integrity.knownDiagnosticLimitations[${index}]`));
  bool(integrity.statisticalHoldoutApproved, '$.integrity.statisticalHoldoutApproved');
  bool(integrity.automaticFallback, '$.integrity.automaticFallback');
  if (integrity.statisticalHoldoutApproved !== false || integrity.automaticFallback !== false) fail('$.integrity', '통계 holdout 승인과 자동 fallback은 false여야 합니다.');
  bool(integrity.diagnosticsExcludedFromGameplayIdentity, '$.integrity.diagnosticsExcludedFromGameplayIdentity');
  const random = record(integrity.randomFingerprint, '$.integrity.randomFingerprint');
  text(random.schemaVersion, '$.integrity.randomFingerprint.schemaVersion');
  integer(random.randomDrawCount, '$.integrity.randomFingerprint.randomDrawCount');
  text(random.randomTraceHash, '$.integrity.randomFingerprint.randomTraceHash');
  text(random.randomTraceHashAlgorithm, '$.integrity.randomFingerprint.randomTraceHashAlgorithm');
}

export function validateRealMatchResponsePayload(value: unknown, request: RealMatchSimulateRequestDto): RealMatchResponseDto {
  const root = record(value, '$');
  literal(root.schemaVersion, 'REAL_MATCH_RESPONSE_V1', '$.schemaVersion');
  text(root.matchIdentity, '$.matchIdentity');
  if (text(root.seed, '$.seed') !== request.seed) fail('$.seed', '요청 seed와 일치하지 않습니다.');

  const presentationTeams = array(root.teams, '$.teams');
  if (presentationTeams.length !== 2) fail('$.teams', 'BLUE/RED 두 팀이 필요합니다.');
  const responseTeamCodes = new Map<TeamSide, string>();
  const presentationPlayers = new Map<string, PresentationPlayerIdentity>();
  presentationTeams.forEach((teamValue, teamIndex) => {
    const path = `$.teams[${teamIndex}]`;
    const team = record(teamValue, path);
    const side = teamSide(team.teamSide, `${path}.teamSide`);
    if (responseTeamCodes.has(side)) fail(`${path}.teamSide`, '중복 team side입니다.');
    const code = validateTeamCode(team.teamCode, `${path}.teamCode`);
    const expectedCode = side === 'BLUE' ? request.blueTeamCode : request.redTeamCode;
    if (code !== expectedCode) fail(`${path}.teamCode`, '요청 팀과 일치하지 않습니다.');
    responseTeamCodes.set(side, code);
    text(team.displayName, `${path}.displayName`);
    const lineup = array(team.lineup, `${path}.lineup`);
    if (lineup.length !== 5) fail(`${path}.lineup`, '팀별 선발 5명이 필요합니다.');
    const teamPositions: string[] = [];
    lineup.forEach((playerValue, playerIndex) => {
      const playerPath = `${path}.lineup[${playerIndex}]`;
      const player = record(playerValue, playerPath);
      const playerId = text(player.playerId, `${playerPath}.playerId`);
      if (presentationPlayers.has(playerId)) fail(`${playerPath}.playerId`, '중복 stable player ID입니다.');
      text(player.nickname, `${playerPath}.nickname`);
      const positionValue = position(player.position, `${playerPath}.position`);
      teamPositions.push(positionValue);
      const championId = text(player.championId, `${playerPath}.championId`);
      validateChampion(player.champion, `${playerPath}.champion`, championId);
      presentationPlayers.set(playerId, { side, position: positionValue, championId });
    });
    exactSet(teamPositions, POSITIONS, `${path}.lineup.position`);
  });
  exactSet([...responseTeamCodes.keys()], TEAM_SIDES, '$.teams.teamSide');
  if (presentationPlayers.size !== 10) fail('$.teams.lineup', '정확히 10명의 선수가 필요합니다.');

  const draft = record(root.draft, '$.draft');
  literal(draft.schemaVersion, 'REAL_MATCH_DRAFT_V1', '$.draft.schemaVersion');
  if (integer(draft.seriesGameNumber, '$.draft.seriesGameNumber', 1) !== 1) fail('$.draft.seriesGameNumber', '현재 계약은 fresh Game 1만 지원합니다.');
  for (const key of [
    'draftRuleSetIdentity', 'draftRuleSetHash', 'draftScoringPolicyHash',
    'draftSelectionPolicyId', 'draftSelectionPolicyHash', 'draftSelectionTraceHashAlgorithm',
    'draftSelectionTraceHash',
    'finalDraftHash', 'finalAssignmentHash',
  ]) text(draft[key], `$.draft.${key}`);
  stringArray(draft.hardFearlessExclusionsBeforeDraft, '$.draft.hardFearlessExclusionsBeforeDraft');
  const decisions = array(draft.decisions, '$.draft.decisions');
  if (decisions.length !== 20) fail('$.draft.decisions', '정확히 20개의 자동 Draft 결정이 필요합니다.');
  const turns: number[] = [];
  const decisionChampions: Record<TeamSide, Record<DraftActionType, string[]>> = {
    BLUE: { PICK: [], BAN: [] }, RED: { PICK: [], BAN: [] },
  };
  const allDecisionChampions: string[] = [];
  decisions.forEach((decisionValue, index) => {
    const path = `$.draft.decisions[${index}]`;
    const decision = record(decisionValue, path);
    turns.push(integer(decision.turn, `${path}.turn`, 1));
    const side = teamSide(decision.teamSide, `${path}.teamSide`);
    const actionType = oneOf(decision.actionType, DRAFT_ACTIONS, `${path}.actionType`) as DraftActionType;
    const championId = text(decision.championId, `${path}.championId`);
    decisionChampions[side][actionType].push(championId);
    allDecisionChampions.push(championId);
  });
  const selectionTraces = array(draft.selectionTraces, '$.draft.selectionTraces');
  if (selectionTraces.length !== decisions.length) fail('$.draft.selectionTraces', 'Draft 결정별 selection trace가 필요합니다.');
  selectionTraces.forEach((traceValue, index) => {
    const path = `$.draft.selectionTraces[${index}]`;
    const trace = record(traceValue, path);
    if (text(trace.policyId, `${path}.policyId`) !== draft.draftSelectionPolicyId) fail(`${path}.policyId`, 'Draft selection policy와 일치해야 합니다.');
    text(trace.policyMode, `${path}.policyMode`);
    if (text(trace.policyHash, `${path}.policyHash`) !== draft.draftSelectionPolicyHash) fail(`${path}.policyHash`, 'Draft selection policy hash와 일치해야 합니다.');
    text(trace.selectionContextHash, `${path}.selectionContextHash`);
    const decision = record(decisions[index], `$.draft.decisions[${index}]`);
    if (integer(trace.turn, `${path}.turn`, 1) !== decision.turn) fail(`${path}.turn`, 'Draft 결정 turn과 일치해야 합니다.');
    if (teamSide(trace.teamSide, `${path}.teamSide`) !== decision.teamSide) fail(`${path}.teamSide`, 'Draft 결정 side와 일치해야 합니다.');
    const actionType = oneOf(trace.actionType, DRAFT_ACTIONS, `${path}.actionType`) as DraftActionType;
    if (actionType !== decision.actionType) fail(`${path}.actionType`, 'Draft 결정 action과 일치해야 합니다.');
    const bestCandidateId = text(trace.bestCandidateId, `${path}.bestCandidateId`);
    signedInteger(trace.bestCanonicalScore, `${path}.bestCanonicalScore`);
    const pool = array(trace.eligiblePool, `${path}.eligiblePool`);
    if (pool.length < 1 || pool.length > 3) fail(`${path}.eligiblePool`, 'selection pool은 1~3개여야 합니다.');
    const expectedWeights = actionType === 'BAN' ? [55, 30, 15] : [70, 22, 8];
    let totalWeight = 0;
    const poolIds = new Set<string>();
    pool.forEach((entryValue, poolIndex) => {
      const entryPath = `${path}.eligiblePool[${poolIndex}]`;
      const entry = record(entryValue, entryPath);
      const championId = text(entry.championId, `${entryPath}.championId`);
      if (poolIds.has(championId)) fail(`${entryPath}.championId`, 'selection pool champion은 중복될 수 없습니다.');
      poolIds.add(championId);
      if (integer(entry.canonicalRank, `${entryPath}.canonicalRank`, 1) !== poolIndex + 1) fail(`${entryPath}.canonicalRank`, 'canonical rank가 연속이어야 합니다.');
      finiteNumber(entry.rawFinalSearchScore, `${entryPath}.rawFinalSearchScore`);
      signedInteger(entry.canonicalFinalScore, `${entryPath}.canonicalFinalScore`);
      if (integer(entry.canonicalScoreLoss, `${entryPath}.canonicalScoreLoss`) > 2_000_000) fail(`${entryPath}.canonicalScoreLoss`, '2.0 score-loss window를 초과했습니다.');
      const weight = integer(entry.rankWeight, `${entryPath}.rankWeight`, 1);
      if (weight !== expectedWeights[poolIndex]) fail(`${entryPath}.rankWeight`, 'production rank weight와 일치해야 합니다.');
      totalWeight += weight;
    });
    const selectedRank = integer(trace.selectedRank, `${path}.selectedRank`, 1);
    if (selectedRank > pool.length) fail(`${path}.selectedRank`, 'selection pool 범위를 벗어났습니다.');
    const selectedEntry = record(pool[selectedRank - 1], `${path}.eligiblePool[selected]`);
    const selectedChampionId = text(trace.selectedChampionId, `${path}.selectedChampionId`);
    if (selectedChampionId !== decision.championId || selectedChampionId !== selectedEntry.championId) fail(`${path}.selectedChampionId`, '실제 Draft 결정과 일치해야 합니다.');
    if (bestCandidateId !== record(pool[0], `${path}.eligiblePool[0]`).championId) fail(`${path}.bestCandidateId`, 'pool rank 1과 일치해야 합니다.');
    const selectedLoss = integer(trace.selectedCanonicalScoreLoss, `${path}.selectedCanonicalScoreLoss`);
    if (selectedLoss !== selectedEntry.canonicalScoreLoss) fail(`${path}.selectedCanonicalScoreLoss`, '선택된 pool entry와 일치해야 합니다.');
    if (integer(trace.totalEligibleWeight, `${path}.totalEligibleWeight`, 1) !== totalWeight) fail(`${path}.totalEligibleWeight`, 'pool weight 합과 일치해야 합니다.');
    const reason = oneOf(trace.reason, ['ONLY_ONE_WITHIN_WINDOW', 'SEEDED_WEIGHTED_SELECTION'] as const, `${path}.reason`);
    if (reason === 'ONLY_ONE_WITHIN_WINDOW') {
      if (pool.length !== 1 || trace.drawBucket !== null) fail(path, 'singleton pool은 draw를 수행하지 않아야 합니다.');
    } else {
      const bucket = integer(trace.drawBucket, `${path}.drawBucket`);
      if (pool.length < 2 || bucket >= totalWeight) fail(`${path}.drawBucket`, 'weighted draw bucket 범위가 올바르지 않습니다.');
    }
  });
  exactSet(turns.map(String), Array.from({ length: 20 }, (_, index) => String(index + 1)), '$.draft.decisions.turn');
  if (new Set(allDecisionChampions).size !== 20) fail('$.draft.decisions.championId', 'Draft 전체에서 중복 없는 20개 챔피언이 필요합니다.');
  const blueBans = stringArray(draft.blueBans, '$.draft.blueBans');
  const bluePicks = stringArray(draft.bluePicks, '$.draft.bluePicks');
  const redBans = stringArray(draft.redBans, '$.draft.redBans');
  const redPicks = stringArray(draft.redPicks, '$.draft.redPicks');
  for (const [name, values] of Object.entries({ blueBans, bluePicks, redBans, redPicks })) {
    if (values.length !== 5 || new Set(values).size !== 5) fail(`$.draft.${name}`, '중복 없는 챔피언 5개가 필요합니다.');
  }
  if (new Set([...blueBans, ...bluePicks, ...redBans, ...redPicks]).size !== 20) fail('$.draft', 'Draft 전체에서 챔피언이 중복될 수 없습니다.');
  exactArray(decisionChampions.BLUE.BAN, blueBans, '$.draft.decisions.BLUE.BAN');
  exactArray(decisionChampions.BLUE.PICK, bluePicks, '$.draft.decisions.BLUE.PICK');
  exactArray(decisionChampions.RED.BAN, redBans, '$.draft.decisions.RED.BAN');
  exactArray(decisionChampions.RED.PICK, redPicks, '$.draft.decisions.RED.PICK');
  const assignments = array(draft.finalAssignments, '$.draft.finalAssignments');
  if (assignments.length !== 10) fail('$.draft.finalAssignments', '정확히 10개의 최종 배치가 필요합니다.');
  const assignedPlayers = new Set<string>();
  const assignedChampions: Record<TeamSide, string[]> = { BLUE: [], RED: [] };
  assignments.forEach((assignmentValue, index) => {
    const path = `$.draft.finalAssignments[${index}]`;
    const assignment = record(assignmentValue, path);
    const playerId = text(assignment.playerId, `${path}.playerId`);
    if (assignedPlayers.has(playerId)) fail(`${path}.playerId`, '중복 final assignment입니다.');
    assignedPlayers.add(playerId);
    const presented = presentationPlayers.get(playerId);
    if (!presented) fail(`${path}.playerId`, 'presentation에 없는 선수입니다.');
    const side = teamSide(assignment.teamSide, `${path}.teamSide`);
    const positionValue = position(assignment.position, `${path}.position`);
    const championId = text(assignment.championId, `${path}.championId`);
    if (presented.side !== side || presented.position !== positionValue || presented.championId !== championId) fail(path, 'presentation과 최종 배치가 일치하지 않습니다.');
    assignedChampions[side].push(championId);
  });
  if (new Set([...assignedChampions.BLUE, ...assignedChampions.RED]).size !== 10) fail('$.draft.finalAssignments.championId', '최종 배치 챔피언이 중복될 수 없습니다.');
  exactSet(assignedChampions.BLUE, bluePicks, '$.draft.finalAssignments.BLUE.championId');
  exactSet(assignedChampions.RED, redPicks, '$.draft.finalAssignments.RED.championId');

  const result = record(root.result, '$.result');
  literal(result.schemaVersion, 'MATCH_RESULT_SUMMARY_V1', '$.result.schemaVersion');
  const winner = nullableOneOf(result.winner, TEAM_SIDES, '$.result.winner') as TeamSide | null;
  const endReason = oneOf(result.endReason, END_REASONS, '$.result.endReason') as GameEndReason;
  const durationSeconds = integer(result.durationSeconds, '$.result.durationSeconds', 1);
  const resultTeams = array(result.teams, '$.result.teams');
  if (resultTeams.length !== 2) fail('$.result.teams', '팀 결과 2개가 필요합니다.');
  const resultTeamMap = new Map<TeamSide, JsonRecord>();
  resultTeams.forEach((teamValue, index) => {
    const path = `$.result.teams[${index}]`;
    const team = record(teamValue, path);
    const side = teamSide(team.teamSide, `${path}.teamSide`);
    if (resultTeamMap.has(side)) fail(`${path}.teamSide`, '중복 팀 결과입니다.');
    if (text(team.teamIdentity, `${path}.teamIdentity`) !== responseTeamCodes.get(side)) fail(`${path}.teamIdentity`, 'response team identity와 일치하지 않습니다.');
    for (const key of ['kills', 'totalGold', 'dragons', 'towersDestroyed', 'inhibitorsRemaining', 'nexusTurretsRemaining', 'alivePlayers']) integer(team[key], `${path}.${key}`);
    for (const key of ['hasDragonSoul', 'hasBaronBuff', 'hasElderBuff', 'nexusAlive']) bool(team[key], `${path}.${key}`);
    resultTeamMap.set(side, team);
  });
  exactSet([...resultTeamMap.keys()], TEAM_SIDES, '$.result.teams.teamSide');
  const resultPlayers = array(result.players, '$.result.players');
  if (resultPlayers.length !== 10) fail('$.result.players', '선수 결과 10개가 필요합니다.');
  const resultPlayerMap = new Map<string, JsonRecord>();
  resultPlayers.forEach((playerValue, index) => {
    const path = `$.result.players[${index}]`;
    const player = record(playerValue, path);
    const playerId = text(player.playerId, `${path}.playerId`);
    if (resultPlayerMap.has(playerId)) fail(`${path}.playerId`, '중복 선수 결과입니다.');
    const presented = presentationPlayers.get(playerId);
    if (!presented) fail(`${path}.playerId`, 'presentation에 없는 선수입니다.');
    const side = teamSide(player.teamSide, `${path}.teamSide`);
    const positionValue = position(player.position, `${path}.position`);
    const championId = text(player.championId, `${path}.championId`);
    if (presented.side !== side || presented.position !== positionValue || presented.championId !== championId) fail(path, 'presentation과 선수 결과 identity가 일치하지 않습니다.');
    for (const key of ['kills', 'deaths', 'assists', 'cs', 'gold', 'totalExperience', 'level']) integer(player[key], `${path}.${key}`);
    validateAbilityProfile(player.abilityProfile, positionValue, `${path}.abilityProfile`);
    resultPlayerMap.set(playerId, player);
  });
  if (text(result.finalDraftHash, '$.result.finalDraftHash') !== draft.finalDraftHash) fail('$.result.finalDraftHash', 'Draft hash와 일치하지 않습니다.');
  if (text(result.finalAssignmentHash, '$.result.finalAssignmentHash') !== draft.finalAssignmentHash) fail('$.result.finalAssignmentHash', 'assignment hash와 일치하지 않습니다.');
  for (const key of ['runtimeProfileId', 'configurationHash', 'resourceProvenanceHash', 'replayProvenanceHash']) text(result[key], `$.result.${key}`);

  const timeline = record(root.timeline, '$.timeline');
  literal(timeline.schemaVersion, 'MATCH_ENGINE_TIMELINE_V1', '$.timeline.schemaVersion');
  if (integer(timeline.durationSeconds, '$.timeline.durationSeconds', 1) !== durationSeconds) fail('$.timeline.durationSeconds', 'result duration과 일치하지 않습니다.');
  if (nullableOneOf(timeline.winner, TEAM_SIDES, '$.timeline.winner') !== winner) fail('$.timeline.winner', 'result winner와 일치하지 않습니다.');
  if (oneOf(timeline.endReason, END_REASONS, '$.timeline.endReason') !== endReason) fail('$.timeline.endReason', 'result endReason과 일치하지 않습니다.');
  const events = array(timeline.events, '$.timeline.events');
  if (events.length === 0) fail('$.timeline.events', '이벤트가 비어 있습니다.');
  let previousEventTime = -1;
  events.forEach((event, index) => {
    const time = validateEvent(event, index, durationSeconds, presentationPlayers);
    if (time < previousEventTime) fail(`$.timeline.events[${index}].timeSeconds`, '이벤트 시간이 정렬되어야 합니다.');
    previousEventTime = time;
  });
  const snapshots = array(timeline.snapshots, '$.timeline.snapshots');
  if (snapshots.length < 2) fail('$.timeline.snapshots', '시작/종료 snapshot이 필요합니다.');
  let previousSnapshotTime = -1;
  snapshots.forEach((snapshotValue, index) => {
    const path = `$.timeline.snapshots[${index}]`;
    const snapshot = record(snapshotValue, path);
    const time = integer(snapshot.timeSeconds, `${path}.timeSeconds`);
    if (time < previousSnapshotTime || time > durationSeconds) fail(`${path}.timeSeconds`, 'snapshot 시간이 정렬 범위를 벗어났습니다.');
    if (index === 0 && time !== 0) fail(`${path}.timeSeconds`, '첫 snapshot은 0초여야 합니다.');
    previousSnapshotTime = time;
    validateTeamState(snapshot.blueTeam, 'BLUE', responseTeamCodes.get('BLUE')!, `${path}.blueTeam`);
    validateTeamState(snapshot.redTeam, 'RED', responseTeamCodes.get('RED')!, `${path}.redTeam`);
    const players = array(snapshot.players, `${path}.players`);
    if (players.length !== 10) fail(`${path}.players`, 'snapshot마다 선수 10명이 필요합니다.');
    const snapshotPlayers = new Set<string>();
    const snapshotPositions: Record<TeamSide, string[]> = { BLUE: [], RED: [] };
    players.forEach((playerValue, playerIndex) => {
      const identity = validatePlayerState(playerValue, `${path}.players[${playerIndex}]`);
      if (snapshotPlayers.has(identity.playerId)) fail(`${path}.players[${playerIndex}].playerId`, '중복 snapshot 선수입니다.');
      snapshotPlayers.add(identity.playerId);
      const presented = presentationPlayers.get(identity.playerId);
      if (!presented || presented.side !== identity.side || presented.position !== identity.position || presented.championId !== identity.championId) fail(`${path}.players[${playerIndex}]`, 'presentation identity와 일치하지 않습니다.');
      snapshotPositions[identity.side].push(identity.position);
    });
    exactSet(snapshotPositions.BLUE, POSITIONS, `${path}.players.BLUE.position`);
    exactSet(snapshotPositions.RED, POSITIONS, `${path}.players.RED.position`);
    record(snapshot.structuredState, `${path}.structuredState`);
  });
  const finalSnapshot = record(snapshots[snapshots.length - 1], '$.timeline.snapshots[last]');
  if (finalSnapshot.timeSeconds !== durationSeconds) fail('$.timeline.snapshots[last].timeSeconds', '종료 시간과 일치해야 합니다.');
  const finalTeamBySide = { BLUE: record(finalSnapshot.blueTeam, '$.timeline.snapshots[last].blueTeam'), RED: record(finalSnapshot.redTeam, '$.timeline.snapshots[last].redTeam') };
  for (const side of TEAM_SIDES) {
    const finalTeam = finalTeamBySide[side]; const resultTeam = resultTeamMap.get(side)!;
    if (finalTeam.kills !== resultTeam.kills || finalTeam.gold !== resultTeam.totalGold) fail(`$.timeline.snapshots[last].${side}`, '최종 팀 결과와 kills/gold가 일치하지 않습니다.');
  }
  const finalPlayers = array(finalSnapshot.players, '$.timeline.snapshots[last].players');
  for (const playerValue of finalPlayers) {
    const player = record(playerValue, '$.timeline.snapshots[last].players[]');
    const resultPlayer = resultPlayerMap.get(String(player.playerId));
    if (!resultPlayer) fail('$.timeline.snapshots[last].players[].playerId', '최종 결과 선수를 찾을 수 없습니다.');
    for (const key of ['teamSide', 'position', 'championId', 'kills', 'deaths', 'assists', 'cs', 'gold', 'totalExperience', 'level']) {
      if (player[key] !== resultPlayer[key]) fail(`$.timeline.snapshots[last].players[].${key}`, '최종 선수 결과와 일치하지 않습니다.');
    }
  }

  validateCommonMatchSemantics({
    teams: root.teams,
    result: root.result,
    timeline: root.timeline,
    assignments,
    paths: { teams: '$.teams', result: '$.result', timeline: '$.timeline', assignments: '$.draft.finalAssignments' },
  }, fail);

  validateIntegrity(root.integrity);
  const integrity = record(root.integrity, '$.integrity');
  for (const key of ['draftSelectionPolicyId', 'draftSelectionPolicyHash',
    'draftSelectionTraceHashAlgorithm', 'draftSelectionTraceHash'] as const) {
    if (integrity[key] !== draft[key]) fail(`$.integrity.${key}`, `draft.${key}와 일치하지 않습니다.`);
  }
  for (const [resultKey, integrityKey] of [
    ['runtimeProfileId', 'runtimeProfileId'], ['configurationHash', 'configurationHash'],
    ['resourceProvenanceHash', 'resourceProvenanceHash'], ['replayProvenanceHash', 'replayProvenanceHash'],
  ] as const) if (result[resultKey] !== integrity[integrityKey]) fail(`$.integrity.${integrityKey}`, `result.${resultKey}와 일치하지 않습니다.`);
  return value as RealMatchResponseDto;
}

export function validateRealMatchApiErrorPayload(value: unknown): RealMatchApiErrorDto {
  const root = record(value, '$');
  literal(root.schemaVersion, 'REAL_MATCH_API_ERROR_V1', '$.schemaVersion');
  text(root.code, '$.code');
  if (root.field !== null) text(root.field, '$.field');
  text(root.message, '$.message');
  return value as RealMatchApiErrorDto;
}

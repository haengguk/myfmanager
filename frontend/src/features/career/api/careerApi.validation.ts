import {
  CAREER_SCHEMAS,
  type CareerAllowedCommand,
  type CareerAdvanceMode,
  type CareerAdvanceResponseDto,
  type CareerCalendarEventDto,
  type CareerCalendarFixtureDto,
  type CareerCalendarStageDto,
  type CareerCalendarViewDto,
  type CareerCreateResponseDto,
  type CareerCompetitionCommandResponseDto,
  type CareerErrorDto,
  type CareerListResponseDto,
  type CareerResumeDto,
  type CareerResumeKind,
  type CareerSummaryDto,
  type CareerViewDto,
} from './careerApi.types.ts';

export class CareerContractError extends Error {
  readonly path: string;

  constructor(path: string, message = 'required contract mismatch') {
    super(`${path}: ${message}`);
    this.name = 'CareerContractError';
    this.path = path;
  }
}

type RecordValue = Record<string, unknown>;
const CAREER_ID = /^career_[0-9a-f]{64}$/;
const LEAGUE_ID = /^league_[0-9a-f]{64}$/;
const SEASON_ID = /^season_[0-9a-f]{64}$/;
const FIXTURE_ID = /^fixture_[0-9a-f]{64}$/;
const SERIES_ID = /^series_[0-9a-f]{64}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const SIGNED_LONG = /^-?(?:0|[1-9]\d{0,18})$/;
const RESUME_KINDS = ['LEAGUE_DASHBOARD', 'PLAYER_SERIES', 'SEASON_COMPLETE', 'ATTENTION_REQUIRED'] as const;
const SEASON_STATUSES = ['DRAFT', 'FROZEN', 'READY', 'RUNNING', 'PAUSED', 'WAITING_FOR_PLAYER', 'COMPLETED', 'BLOCKED', 'CANCELLED'] as const;
const COMMANDS = [
  'VIEW_STANDINGS', 'VIEW_FIXTURE', 'RUN_CURRENT_ROUND_AUTO_FIXTURES', 'PAUSE_SEASON',
  'RESUME_SEASON', 'CANCEL_SEASON', 'START_PLAYER_SERIES', 'RESUME_PLAYER_SERIES',
  'RECONCILE_PLAYER_SERIES_COMPLETION',
] as const;
const OFFICIAL_STATUSES = ['OFFICIAL_CONFIRMED', 'OFFICIAL_BY_NO_CHANGE_STATEMENT', 'OFFICIAL_PARTIAL', 'DERIVED', 'OFFICIAL_PENDING', 'SUPERSEDED'] as const;
const ADVANCE_MODES = ['ADVANCE_ONE_DAY', 'ADVANCE_TO_NEXT_EVENT'] as const;
const CALENDAR_EVENT_ID = /^calendar_event_[0-9a-f]{64}$/;
const COMPETITION_FIXTURE_ID = /^competition_fixture_[0-9a-f]{64}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const COMPETITION_IDS = ['LCK_CUP', 'LCK_REGULAR_R1_R2', 'LCK_ROAD_TO_MSI', 'LCK_REGULAR_R3_R4', 'LCK_PLAY_IN', 'LCK_PLAYOFFS', 'FIRST_STAND', 'MSI', 'EWC_LOL', 'WORLDS', 'ASIAN_GAMES_LOL_RELEASE', 'KESPA_CUP'] as const;

function object(value: unknown, path: string): RecordValue {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new CareerContractError(path, 'object required');
  return value as RecordValue;
}
function exactKeys(value: RecordValue, expected: readonly string[], path: string): void {
  const actual = Object.keys(value).sort(); const required = [...expected].sort();
  if (actual.length !== required.length || actual.some((key, index) => key !== required[index])) throw new CareerContractError(path, 'unknown or missing field');
}
function text(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.length === 0) throw new CareerContractError(path, 'non-empty string required');
  return value;
}
function nullableText(value: unknown, path: string): string | null { return value === null ? null : text(value, path); }
function nullableInteger(value: unknown, path: string): number | null { return value === null ? null : integer(value, path); }
function nullableDate(value: unknown, path: string): string | null { return value === null ? null : date(value, path); }
function texts(value: unknown, path: string): string[] { if (!Array.isArray(value)) throw new CareerContractError(path, 'array required'); return value.map((entry, index) => text(entry, `${path}[${index}]`)); }
function oneOf<T extends string>(value: unknown, choices: readonly T[], path: string): T { if (typeof value !== 'string' || !choices.includes(value as T)) throw new CareerContractError(path, 'known value required'); return value as T; }
function nullableId(value: unknown, pattern: RegExp, path: string): string | null {
  if (value === null) return null;
  const result = text(value, path); if (!pattern.test(result)) throw new CareerContractError(path, 'canonical identity required'); return result;
}
function identity(value: unknown, pattern: RegExp, path: string): string {
  const result = text(value, path); if (!pattern.test(result)) throw new CareerContractError(path, 'canonical identity required'); return result;
}
function integer(value: unknown, path: string, min = 0): number {
  if (!Number.isSafeInteger(value) || Number(value) < min) throw new CareerContractError(path, `safe integer >= ${min} required`);
  return Number(value);
}
function bool(value: unknown, path: string): boolean {
  if (typeof value !== 'boolean') throw new CareerContractError(path, 'boolean required'); return value;
}
function date(value: unknown, path: string): string {
  const result = text(value, path);
  const parsed = new Date(`${result}T00:00:00Z`);
  if (!DATE.test(result) || Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== result) throw new CareerContractError(path, 'ISO date required');
  return result;
}
function timestamp(value: unknown, path: string): string {
  const result = text(value, path); if (Number.isNaN(Date.parse(result))) throw new CareerContractError(path, 'ISO timestamp required'); return result;
}
function display(value: unknown, path: string): string {
  const result = text(value, path);
  if (result !== result.trim() || result !== result.normalize('NFC') || [...result].length < 1 || [...result].length > 80 || [...result].some((character) => /[\u0000-\u001f\u007f]/.test(character))) throw new CareerContractError(path, 'normalized 1..80 character display name required');
  return result;
}
function resumeKind(value: unknown, path: string): CareerResumeKind {
  if (typeof value !== 'string' || !(RESUME_KINDS as readonly string[]).includes(value)) throw new CareerContractError(path, 'known resume kind required');
  return value as CareerResumeKind;
}
function commands(value: unknown, path: string): CareerAllowedCommand[] {
  if (!Array.isArray(value)) throw new CareerContractError(path, 'array required');
  const values = value.map((entry, index) => {
    if (typeof entry !== 'string' || !(COMMANDS as readonly string[]).includes(entry)) throw new CareerContractError(`${path}[${index}]`, 'known command required');
    return entry as CareerAllowedCommand;
  });
  if (new Set(values).size !== values.length) throw new CareerContractError(path, 'duplicate command');
  return values;
}

function summary(value: unknown, path: string): CareerSummaryDto {
  const item = object(value, path);
  exactKeys(item, ['careerId', 'saveName', 'managerName', 'managedTeamCode', 'currentDate', 'leagueId', 'seasonId', 'lifecycleStatus', 'resumeKind', 'updatedAt'], path);
  identity(item.careerId, CAREER_ID, `${path}.careerId`); display(item.saveName, `${path}.saveName`); display(item.managerName, `${path}.managerName`);
  if (!/^[A-Z0-9]{2,16}$/.test(text(item.managedTeamCode, `${path}.managedTeamCode`))) throw new CareerContractError(`${path}.managedTeamCode`, 'canonical team code required');
  date(item.currentDate, `${path}.currentDate`);
  identity(item.leagueId, LEAGUE_ID, `${path}.leagueId`); identity(item.seasonId, SEASON_ID, `${path}.seasonId`);
  if (item.lifecycleStatus !== 'ACTIVE') throw new CareerContractError(`${path}.lifecycleStatus`, 'ACTIVE required');
  resumeKind(item.resumeKind, `${path}.resumeKind`); timestamp(item.updatedAt, `${path}.updatedAt`);
  return item as unknown as CareerSummaryDto;
}

function resume(value: unknown, path: string, leagueId: string, seasonId: string): CareerResumeDto {
  const item = object(value, path);
  exactKeys(item, ['kind', 'leagueId', 'seasonId', 'fixtureId', 'seriesId', 'seasonLifecycleStatus', 'currentRound', 'lifecycleRevision', 'standingsRevision', 'allowedCommands'], path);
  const kind = resumeKind(item.kind, `${path}.kind`);
  if (identity(item.leagueId, LEAGUE_ID, `${path}.leagueId`) !== leagueId || identity(item.seasonId, SEASON_ID, `${path}.seasonId`) !== seasonId) throw new CareerContractError(path, 'linked League scope mismatch');
  const fixtureId = nullableId(item.fixtureId, FIXTURE_ID, `${path}.fixtureId`); const seriesId = nullableId(item.seriesId, SERIES_ID, `${path}.seriesId`);
  const status = text(item.seasonLifecycleStatus, `${path}.seasonLifecycleStatus`); if (!(SEASON_STATUSES as readonly string[]).includes(status)) throw new CareerContractError(`${path}.seasonLifecycleStatus`, 'known Season status required');
  integer(item.currentRound, `${path}.currentRound`, 1); integer(item.lifecycleRevision, `${path}.lifecycleRevision`); integer(item.standingsRevision, `${path}.standingsRevision`);
  const allowed = commands(item.allowedCommands, `${path}.allowedCommands`);
  if (kind === 'PLAYER_SERIES') {
    if (!fixtureId || !seriesId || !allowed.some((command) => command === 'RESUME_PLAYER_SERIES' || command === 'RECONCILE_PLAYER_SERIES_COMPLETION')) throw new CareerContractError(path, 'actionable Player Series relation required');
  } else if (kind === 'LEAGUE_DASHBOARD' || kind === 'SEASON_COMPLETE') {
    if (fixtureId !== null || seriesId !== null) throw new CareerContractError(path, 'League-only resume cannot carry Series scope');
  } else if (seriesId !== null && fixtureId === null) throw new CareerContractError(path, 'Series identity requires fixture identity');
  if (status === 'COMPLETED' && kind !== 'SEASON_COMPLETE') throw new CareerContractError(path, 'completed Season resume mismatch');
  if (kind === 'SEASON_COMPLETE' && status !== 'COMPLETED') throw new CareerContractError(path, 'Season complete resume mismatch');
  if (status === 'PAUSED' && (kind !== 'LEAGUE_DASHBOARD' || !allowed.includes('RESUME_SEASON'))) throw new CareerContractError(path, 'paused Season must resume in League');
  return item as unknown as CareerResumeDto;
}

export function validateCareerView(value: unknown): CareerViewDto {
  const item = object(value, '$');
  exactKeys(item, ['schemaVersion', 'careerId', 'saveName', 'managerName', 'managedTeamCode', 'startDate', 'currentDate', 'lifecycleStatus', 'revision', 'leagueId', 'seasonId', 'rootSeedAlgorithmId', 'rootSeed', 'leagueFrozenSnapshotIdentity', 'leagueProductDecisionIdentity', 'referenceCatalogVersion', 'referenceCatalogHash', 'bindingSchemaVersion', 'bindingHash', 'resume', 'createdAt', 'updatedAt'], '$');
  if (item.schemaVersion !== CAREER_SCHEMAS.view) throw new CareerContractError('$.schemaVersion', `expected ${CAREER_SCHEMAS.view}`);
  const summaryValue = summary({ careerId: item.careerId, saveName: item.saveName, managerName: item.managerName, managedTeamCode: item.managedTeamCode, currentDate: item.currentDate, leagueId: item.leagueId, seasonId: item.seasonId, lifecycleStatus: item.lifecycleStatus, resumeKind: object(item.resume, '$.resume').kind, updatedAt: item.updatedAt }, '$.summary');
  const startDate = date(item.startDate, '$.startDate'); if (startDate > summaryValue.currentDate) throw new CareerContractError('$.startDate', 'cannot follow currentDate');
  if (integer(item.revision, '$.revision') !== 0) throw new CareerContractError('$.revision', 'Career V1 revision must be zero');
  if (text(item.rootSeedAlgorithmId, '$.rootSeedAlgorithmId') !== 'CAREER_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1') throw new CareerContractError('$.rootSeedAlgorithmId', 'unsupported seed algorithm');
  const rootSeed = text(item.rootSeed, '$.rootSeed'); if (!SIGNED_LONG.test(rootSeed) || BigInt(rootSeed) < -(2n ** 63n) || BigInt(rootSeed) > (2n ** 63n) - 1n) throw new CareerContractError('$.rootSeed', 'signed long string required');
  ['leagueFrozenSnapshotIdentity', 'leagueProductDecisionIdentity', 'referenceCatalogHash', 'bindingHash'].forEach((key) => { if (!SHA256.test(text(item[key], `$.${key}`))) throw new CareerContractError(`$.${key}`, 'lowercase SHA-256 required'); });
  text(item.referenceCatalogVersion, '$.referenceCatalogVersion'); if (text(item.bindingSchemaVersion, '$.bindingSchemaVersion') !== 'CAREER_LEAGUE_BINDING_V1') throw new CareerContractError('$.bindingSchemaVersion', 'unsupported binding schema');
  resume(item.resume, '$.resume', summaryValue.leagueId, summaryValue.seasonId);
  const created = timestamp(item.createdAt, '$.createdAt'); const updated = timestamp(item.updatedAt, '$.updatedAt'); if (Date.parse(created) > Date.parse(updated)) throw new CareerContractError('$.updatedAt', 'must not precede createdAt');
  return item as unknown as CareerViewDto;
}

export function validateCareerCreateResponse(value: unknown): CareerCreateResponseDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'replayed', 'career'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.createResponse) throw new CareerContractError('$.schemaVersion');
  bool(root.replayed, '$.replayed'); validateCareerView(root.career); return root as unknown as CareerCreateResponseDto;
}

export function validateCareerListResponse(value: unknown): CareerListResponseDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'careers', 'currentCount', 'maximumCount', 'remainingCount'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.list) throw new CareerContractError('$.schemaVersion');
  if (!Array.isArray(root.careers)) throw new CareerContractError('$.careers', 'array required');
  const careers = root.careers.map((entry, index) => summary(entry, `$.careers[${index}]`));
  const current = integer(root.currentCount, '$.currentCount'); const maximum = integer(root.maximumCount, '$.maximumCount', 1); const remaining = integer(root.remainingCount, '$.remainingCount');
  if (maximum !== 100 || current !== careers.length || current > maximum || remaining !== maximum - current) throw new CareerContractError('$', 'capacity metadata mismatch');
  if (new Set(careers.map((career) => career.careerId)).size !== careers.length) throw new CareerContractError('$.careers', 'duplicate Career identity');
  for (let index = 1; index < careers.length; index += 1) {
    const before = careers[index - 1]; const after = careers[index]; const beforeTime = Date.parse(before.updatedAt); const afterTime = Date.parse(after.updatedAt);
    if (beforeTime < afterTime || (beforeTime === afterTime && before.careerId > after.careerId)) throw new CareerContractError('$.careers', 'authoritative ordering mismatch');
  }
  return root as unknown as CareerListResponseDto;
}

function calendarStage(value: unknown, path: string): CareerCalendarStageDto {
  const item = object(value, path); exactKeys(item, ['stageId', 'displayNameKo', 'startDate', 'endDate', 'officialStatus', 'teamCount', 'seriesCount', 'format', 'seriesRules'], path);
  text(item.stageId, `${path}.stageId`); text(item.displayNameKo, `${path}.displayNameKo`); const start = nullableDate(item.startDate, `${path}.startDate`); const end = nullableDate(item.endDate, `${path}.endDate`); if ((start === null) !== (end === null) || start && end && start > end) throw new CareerContractError(path, 'stage date range mismatch'); oneOf(item.officialStatus, OFFICIAL_STATUSES, `${path}.officialStatus`); nullableInteger(item.teamCount, `${path}.teamCount`); nullableInteger(item.seriesCount, `${path}.seriesCount`); text(item.format, `${path}.format`); texts(item.seriesRules, `${path}.seriesRules`); return item as unknown as CareerCalendarStageDto;
}

function calendarEvent(value: unknown, path: string): CareerCalendarEventDto {
  const item = object(value, path); exactKeys(item, ['eventId', 'templateId', 'sourceReferenceId', 'displayNameKo', 'startDate', 'endDate', 'timezone', 'timezoneScope', 'locations', 'officialStatus', 'projectionStatus', 'participationType', 'participation', 'teamCount', 'seriesCount', 'format', 'seriesRules', 'draftMode', 'draftStatus', 'executionStatus', 'stages'], path);
  identity(item.eventId, CALENDAR_EVENT_ID, `${path}.eventId`); const templateId = text(item.templateId, `${path}.templateId`); if (/\d{4}|KESPA/i.test(templateId)) throw new CareerContractError(`${path}.templateId`, 'year-neutral sourced template required'); text(item.sourceReferenceId, `${path}.sourceReferenceId`); text(item.displayNameKo, `${path}.displayNameKo`); const start = date(item.startDate, `${path}.startDate`); const end = date(item.endDate, `${path}.endDate`); if (start > end) throw new CareerContractError(path, 'event date range mismatch'); const scope = oneOf(item.timezoneScope, ['SINGLE_IANA_ZONE', 'MULTI_ZONE'] as const, `${path}.timezoneScope`); const timezone = nullableText(item.timezone, `${path}.timezone`); if ((scope === 'SINGLE_IANA_ZONE') !== (timezone !== null)) throw new CareerContractError(`${path}.timezone`, 'timezone scope mismatch'); texts(item.locations, `${path}.locations`); oneOf(item.officialStatus, OFFICIAL_STATUSES, `${path}.officialStatus`); oneOf(item.projectionStatus, ['REFERENCE_YEAR_SOURCE', 'GAME_PROJECTED_FROM_2026_TEMPLATE'] as const, `${path}.projectionStatus`); oneOf(item.participationType, ['ALL_LCK', 'RANKING_QUALIFIED', 'REGION_SLOT', 'NATIONAL_TEAM_RELEASE'] as const, `${path}.participationType`); text(item.participation, `${path}.participation`); nullableInteger(item.teamCount, `${path}.teamCount`); nullableInteger(item.seriesCount, `${path}.seriesCount`); text(item.format, `${path}.format`); texts(item.seriesRules, `${path}.seriesRules`); nullableText(item.draftMode, `${path}.draftMode`); text(item.draftStatus, `${path}.draftStatus`); oneOf(item.executionStatus, ['LINKED_EXISTING_LEAGUE_FIXTURES', 'LINKED_COMPETITION_SERIES_EXECUTION', 'FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED', 'EXCLUDED_BY_GAME_POLICY'] as const, `${path}.executionStatus`); if (!Array.isArray(item.stages)) throw new CareerContractError(`${path}.stages`, 'array required'); item.stages.forEach((stage, index) => calendarStage(stage, `${path}.stages[${index}]`)); return item as unknown as CareerCalendarEventDto;
}

function calendarFixture(value: unknown, path: string): CareerCalendarFixtureDto {
  const item = object(value, path); exactKeys(item, ['fixtureId', 'roundNumber', 'date', 'scheduleStatus', 'executionMode', 'firstTeamCode', 'secondTeamCode', 'lifecycleStatus', 'seriesId', 'jobStatus', 'pendingOutbox'], path); identity(item.fixtureId, FIXTURE_ID, `${path}.fixtureId`); integer(item.roundNumber, `${path}.roundNumber`, 1); if (Number(item.roundNumber) > 18) throw new CareerContractError(`${path}.roundNumber`); date(item.date, `${path}.date`); if (item.scheduleStatus !== 'GAME_DERIVED_SCHEDULE_POLICY') throw new CareerContractError(`${path}.scheduleStatus`); oneOf(item.executionMode, ['FULL_AUTO', 'PLAYER_CONTROLLED'] as const, `${path}.executionMode`); for (const key of ['firstTeamCode', 'secondTeamCode'] as const) if (!/^[A-Z0-9]{2,16}$/.test(text(item[key], `${path}.${key}`))) throw new CareerContractError(`${path}.${key}`); text(item.lifecycleStatus, `${path}.lifecycleStatus`); identity(item.seriesId, SERIES_ID, `${path}.seriesId`); nullableText(item.jobStatus, `${path}.jobStatus`); bool(item.pendingOutbox, `${path}.pendingOutbox`); return item as unknown as CareerCalendarFixtureDto;
}

function competitionSummary(value: unknown, path: string): RecordValue {
  const item = object(value, path); exactKeys(item, ['competitionId', 'stageId', 'ruleStatus', 'lifecycleStatus', 'blockingReason', 'revision', 'stateHash', 'completedFixtures', 'totalFixtures'], path); oneOf(item.competitionId, COMPETITION_IDS, `${path}.competitionId`); if (!/^[A-Z0-9_]+$/.test(text(item.stageId, `${path}.stageId`))) throw new CareerContractError(`${path}.stageId`, 'structured stage identity required'); const ruleStatus = oneOf(item.ruleStatus, ['RULE_SOURCE_COMPLETE', 'RULE_SOURCE_INCOMPLETE', 'PRODUCT_POLICY_REQUIRED', 'REFERENCE_TEMPLATE_ONLY', 'VERIFIED_PRIOR_SEASON_REQUIRED', 'GAME_POLICY_DEFINED'] as const, `${path}.ruleStatus`); const lifecycle = text(item.lifecycleStatus, `${path}.lifecycleStatus`); nullableText(item.blockingReason, `${path}.blockingReason`); integer(item.revision, `${path}.revision`); if (item.stateHash !== null) identity(item.stateHash, SHA256, `${path}.stateHash`); if ((item.stateHash === null) !== (ruleStatus === 'VERIFIED_PRIOR_SEASON_REQUIRED' && lifecycle === 'BLOCKED' && item.stageId === 'UNMATERIALIZED')) throw new CareerContractError(`${path}.stateHash`, 'materialization/hash relation mismatch'); const completed = integer(item.completedFixtures, `${path}.completedFixtures`); const total = integer(item.totalFixtures, `${path}.totalFixtures`); if (completed > total) throw new CareerContractError(path, 'fixture progress mismatch'); return item;
}

const COMPETITION_TEAM = /^(?:[A-Z0-9]{2,16}|(?:LCK|LPL|LEC|LCS|LCP|CBLOL):[A-Z0-9]{1,8})$/;
function internationalCompetition(value: unknown, path: string): void {
  const item = object(value, path);
  exactKeys(item, ['competitionId', 'ruleVersion', 'ruleResourceHash', 'policyVersion', 'selectionPolicy', 'entries', 'rosterSnapshotIdentity', 'bracket', 'results'], path);
  oneOf(item.competitionId, ['FIRST_STAND', 'MSI', 'EWC_LOL', 'WORLDS'] as const, path);
  if (item.ruleVersion !== 'career-international-rules-2026-v1' || item.policyVersion !== 'CAREER_INTERNATIONAL_GAME_POLICY_V1') throw new CareerContractError(path, 'international authority required');
  identity(item.ruleResourceHash, SHA256, path); identity(item.rosterSnapshotIdentity, SHA256, path); text(item.selectionPolicy, path);
  if (!Array.isArray(item.entries)) throw new CareerContractError(path);
  const seeds = new Set<string>();
  const teams = item.entries.map((entry, i) => {
    const p = `${path}.entries[${i}]`; const row = object(entry, p);
    exactKeys(row, ['team', 'region', 'regionalSeed', 'pool', 'phase', 'qualification'], p);
    const team = text(row.team, p), region = text(row.region, p);
    if (!COMPETITION_TEAM.test(team) || !team.startsWith(`${region}:`)) throw new CareerContractError(p, 'qualified regional team required');
    const seed = integer(row.regionalSeed, p), pool = integer(row.pool, p);
    if (seed < 1 || pool < 1 || pool > 4 || seeds.has(`${region}:${seed}`)) throw new CareerContractError(p);
    seeds.add(`${region}:${seed}`); oneOf(row.phase, ['MAIN', 'PLAY_IN'] as const, p); text(row.qualification, p); return team;
  });
  const expected = item.competitionId === 'FIRST_STAND' ? 8 : item.competitionId === 'MSI' ? 11 : item.competitionId === 'EWC_LOL' ? 16 : 19;
  if (new Set(teams).size !== expected || teams.length !== expected) throw new CareerContractError(path, 'entrant cardinality mismatch');
  const bracket = object(item.bracket, `${path}.bracket`);
  exactKeys(bracket, ['bouts', 'placements', 'draws', 'regionalPerformance', 'champion', 'complete'], path);
  if (!Array.isArray(bracket.bouts) || !Array.isArray(bracket.draws)) throw new CareerContractError(path);
  const bouts = new Map<string, readonly string[]>();
  bracket.bouts.forEach((value, i) => {
    const p = `${path}.bracket.bouts[${i}]`, bout = object(value, p);
    exactKeys(bout, ['id', 'stage', 'date', 'order', 'format', 'first', 'second', 'selectionOwner', 'sidePolicy', 'group'], p);
    const id = text(bout.id, p), first = text(bout.first, p), second = text(bout.second, p);
    if (bouts.has(id) || first === second || !teams.includes(first) || !teams.includes(second) || ![first, second].includes(text(bout.selectionOwner, p))) throw new CareerContractError(p);
    bouts.set(id, [first, second]); text(bout.stage, p); date(bout.date, p); integer(bout.order, p);
    oneOf(bout.format, ['BO1', 'BO3', 'BO5'] as const, p); oneOf(bout.sidePolicy, ['INTERNATIONAL_ROFS_FIRST_PICK_OTHER_RED_LOSER_ROFS_V1', 'INTERNATIONAL_RODS_BLUE_FIRST_PICK_LOSER_ROFS_V1'] as const, p); nullableText(bout.group, p);
  });
  bracket.draws.forEach(value => { const draw = object(value, path); exactKeys(draw, ['scope', 'teams', 'relaxation'], path); text(draw.scope, path); texts(draw.teams, path); nullableText(draw.relaxation, path); if ((draw.teams as string[]).some(t => !teams.includes(t))) throw new CareerContractError(path); });
  const placements = object(bracket.placements, path);
  Object.entries(placements).forEach(([team, rank]) => { if (!teams.includes(team) || integer(rank, path) < 1 || (rank as number) > expected) throw new CareerContractError(path); });
  texts(bracket.regionalPerformance, path); const champion = nullableText(bracket.champion, path), complete = bool(bracket.complete, path);
  if (complete !== (champion !== null) || champion !== null && (!teams.includes(champion) || placements[champion] !== 1 || Object.keys(placements).length !== expected)) throw new CareerContractError(path);
  Object.entries(object(item.results, path)).forEach(([id, value]) => { const result = object(value, path); exactKeys(result, ['winner', 'loser'], path); const pair = bouts.get(id); if (!pair || result.winner === result.loser || !pair.includes(text(result.winner, path)) || !pair.includes(text(result.loser, path))) throw new CareerContractError(path); });
}

function competitionView(value: unknown, path: string, seasonYear: number): void {
  const item = object(value, path); exactKeys(item, ['schemaVersion', 'calendarSeasonYear', 'ruleResourceHash', 'ruleVersion', 'gamePolicyVersion', 'projectionPolicy', 'r3r4AllocationPolicy', 'lifecycleStatus', 'revision', 'stateHash', 'currentCompetition', 'nextCompetition', 'nextFixture', 'qualificationOutputs', 'groupStandings', 'currentSeeds', 'externalExecutionLimited', 'activePendingCommand', 'allowedCommands', ...('internationalCompetitions' in item ? ['internationalCompetitions'] : []), ...('domesticRankingDecisions' in item ? ['domesticRankingDecisions', 'finalRanking', 'domesticRuleCompatibility'] : [])], path);
  if (item.schemaVersion !== 'CAREER_COMPETITION_VIEW_V1' || item.calendarSeasonYear !== seasonYear || !['lck-career-competition-rules-2026-v2', 'lck-career-competition-rules-2026-v3'].includes(String(item.ruleVersion)) || item.gamePolicyVersion !== (item.ruleVersion === 'lck-career-competition-rules-2026-v3' ? 'CAREER_COMPETITION_GAME_POLICY_V3' : 'CAREER_COMPETITION_GAME_POLICY_V2') || item.projectionPolicy !== 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1' || item.r3r4AllocationPolicy !== 'LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1') throw new CareerContractError(path, 'competition authority identity mismatch'); identity(item.ruleResourceHash, SHA256, `${path}.ruleResourceHash`); text(item.lifecycleStatus, `${path}.lifecycleStatus`); integer(item.revision, `${path}.revision`); if (item.stateHash !== null) identity(item.stateHash, SHA256, `${path}.stateHash`);
  if (item.ruleVersion === 'lck-career-competition-rules-2026-v3' && !('domesticRankingDecisions' in item)) throw new CareerContractError(path, 'domestic decision projection required');
  if ('domesticRankingDecisions' in item) {
    text(item.domesticRuleCompatibility, `${path}.domesticRuleCompatibility`);
    if (!Array.isArray(item.domesticRankingDecisions)) throw new CareerContractError(`${path}.domesticRankingDecisions`);
    item.domesticRankingDecisions.forEach((entry, i) => {
      const p = `${path}.domesticRankingDecisions[${i}]`; const decision = object(entry, p);
      exactKeys(decision, ['competitionId', 'decisionId', 'status', 'inputHash', 'policyVersion', 'detail'], p);
      oneOf(decision.competitionId, COMPETITION_IDS, p); text(decision.decisionId, p);
      oneOf(decision.status, ['RUNNING', 'SEALED'] as const, p); identity(decision.inputHash, SHA256, p); text(decision.policyVersion, p);
      const detail = object(decision.detail, `${p}.detail`);
      if (detail.pendingMatches !== undefined) {
        if (!Array.isArray(detail.pendingMatches)) throw new CareerContractError(p);
        detail.pendingMatches.forEach((m, j) => { const match = object(m, `${p}.pendingMatches[${j}]`); exactKeys(match, ['matchId', 'first', 'second'], p); text(match.matchId, p); text(match.first, p); text(match.second, p); if (match.first === match.second) throw new CareerContractError(p); });
      }
    });
    if (item.finalRanking !== null) {
      const p = `${path}.finalRanking`; const final = object(item.finalRanking, p);
      exactKeys(final, ['status', 'sourceSeasonYear', 'sourceSeasonId', 'championTeamCode', 'runnerUpTeamCode', 'ranking', 'stateHash', 'ruleVersion', 'policyVersion', 'resultEvidenceHash', 'worldsStatus', 'requiredInternationalEvidence'], p);
      if (final.status !== 'SEALED' || final.sourceSeasonYear !== seasonYear || final.worldsStatus !== 'PENDING_IN_GAME_INTERNATIONAL_EVIDENCE') throw new CareerContractError(p);
      identity(final.sourceSeasonId, SEASON_ID, p); identity(final.stateHash, SHA256, p);
      nullableText(final.ruleVersion, p); nullableText(final.policyVersion, p); if (final.resultEvidenceHash !== null) identity(final.resultEvidenceHash, SHA256, p);
      texts(final.requiredInternationalEvidence, p);
      if (!Array.isArray(final.ranking) || final.ranking.length !== 10) throw new CareerContractError(p);
      const teams = final.ranking.map((entry, i) => { const row = object(entry, p); exactKeys(row, ['seed', 'teamCode', 'seriesWins', 'seriesLosses', 'gameWins', 'gameLosses'], p); if (row.seed !== i + 1) throw new CareerContractError(p); ['seriesWins', 'seriesLosses', 'gameWins', 'gameLosses'].forEach(k => integer(row[k], p)); return text(row.teamCode, p); });
      if (new Set(teams).size !== 10 || final.championTeamCode !== teams[0] || final.runnerUpTeamCode !== teams[1]) throw new CareerContractError(p, 'final ranking must contain ten distinct teams and matching finalists');
    }
  }
  if (item.internationalCompetitions !== undefined) { if (!Array.isArray(item.internationalCompetitions)) throw new CareerContractError(path); item.internationalCompetitions.forEach((value, i) => internationalCompetition(value, `${path}.internationalCompetitions[${i}]`)); }
  const current = item.currentCompetition === null ? null : competitionSummary(item.currentCompetition, `${path}.currentCompetition`); const next = item.nextCompetition === null ? null : competitionSummary(item.nextCompetition, `${path}.nextCompetition`);
  if (item.nextFixture !== null) { const fixture = object(item.nextFixture, `${path}.nextFixture`); exactKeys(fixture, ['competitionId', 'matchId', 'fixtureId', 'seriesId', 'date', 'scheduleStatus', 'seriesFormat', 'hardFearless', 'firstTeamCode', 'secondTeamCode', 'executionMode', 'lifecycleStatus', 'managedTeamIncluded', 'rootSeed', 'seedAlgorithm', 'firstSelectorType', 'firstSelectorValue', 'secondSelectorType', 'secondSelectorValue', 'stageId', 'blockingReason', 'bindingHash', 'jobId', 'jobStatus', 'resultApplicationStatus', 'failureCode'], `${path}.nextFixture`); oneOf(fixture.competitionId, COMPETITION_IDS, `${path}.nextFixture.competitionId`); text(fixture.matchId, `${path}.nextFixture.matchId`); identity(fixture.fixtureId, COMPETITION_FIXTURE_ID, `${path}.nextFixture.fixtureId`); identity(fixture.seriesId, SERIES_ID, `${path}.nextFixture.seriesId`); date(fixture.date, `${path}.nextFixture.date`); oneOf(fixture.scheduleStatus, ['OFFICIAL_PROJECTED_DATE', 'GAME_DERIVED_SCHEDULE_POLICY'] as const, `${path}.nextFixture.scheduleStatus`); oneOf(fixture.seriesFormat, ['BO1', 'BO3', 'BO5'] as const, `${path}.nextFixture.seriesFormat`); if (fixture.hardFearless !== true) throw new CareerContractError(`${path}.nextFixture.hardFearless`, 'hard Fearless required'); const first = fixture.firstTeamCode === null ? null : text(fixture.firstTeamCode, `${path}.nextFixture.firstTeamCode`); const second = fixture.secondTeamCode === null ? null : text(fixture.secondTeamCode, `${path}.nextFixture.secondTeamCode`); if (first !== null && !COMPETITION_TEAM.test(first) || second !== null && !COMPETITION_TEAM.test(second) || first !== null && first === second) throw new CareerContractError(`${path}.nextFixture`, 'team identity mismatch'); oneOf(fixture.executionMode, ['FULL_AUTO', 'PLAYER_CONTROLLED'] as const, `${path}.nextFixture.executionMode`); text(fixture.lifecycleStatus, `${path}.nextFixture.lifecycleStatus`); bool(fixture.managedTeamIncluded, `${path}.nextFixture.managedTeamIncluded`); if (!SIGNED_LONG.test(text(fixture.rootSeed, `${path}.nextFixture.rootSeed`)) || fixture.seedAlgorithm !== 'CAREER_COMPETITION_MATCH_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1') throw new CareerContractError(`${path}.nextFixture.rootSeed`, 'competition seed identity mismatch'); text(fixture.firstSelectorType, `${path}.nextFixture.firstSelectorType`); text(fixture.firstSelectorValue, `${path}.nextFixture.firstSelectorValue`); text(fixture.secondSelectorType, `${path}.nextFixture.secondSelectorType`); text(fixture.secondSelectorValue, `${path}.nextFixture.secondSelectorValue`); text(fixture.stageId, `${path}.nextFixture.stageId`); nullableText(fixture.blockingReason, `${path}.nextFixture.blockingReason`); nullableId(fixture.bindingHash, SHA256, `${path}.nextFixture.bindingHash`); nullableText(fixture.jobId, `${path}.nextFixture.jobId`); nullableText(fixture.jobStatus, `${path}.nextFixture.jobStatus`); oneOf(fixture.resultApplicationStatus, ['NOT_APPLIED', 'APPLIED'] as const, `${path}.nextFixture.resultApplicationStatus`); nullableText(fixture.failureCode, `${path}.nextFixture.failureCode`); }
  if (!Array.isArray(item.qualificationOutputs) || item.qualificationOutputs.length > 32) throw new CareerContractError(`${path}.qualificationOutputs`, 'bounded array required'); const outputIds = new Set<string>(); item.qualificationOutputs.forEach((entry, index) => { const output = object(entry, `${path}.qualificationOutputs[${index}]`); exactKeys(output, ['competitionId', 'outputId', 'teamCode'], `${path}.qualificationOutputs[${index}]`); oneOf(output.competitionId, COMPETITION_IDS, `${path}.qualificationOutputs[${index}].competitionId`); const outputId = text(output.outputId, `${path}.qualificationOutputs[${index}].outputId`); if (!/^[A-Z0-9_]+$/.test(outputId) || !outputIds.add(outputId) || !/^[A-Z0-9]{2,16}$/.test(text(output.teamCode, `${path}.qualificationOutputs[${index}].teamCode`))) throw new CareerContractError(`${path}.qualificationOutputs[${index}]`, 'qualification identity mismatch'); });
  if (!Array.isArray(item.groupStandings) || item.groupStandings.length > 10) throw new CareerContractError(`${path}.groupStandings`); item.groupStandings.forEach((entry, index) => { const standing = object(entry, `${path}.groupStandings[${index}]`); exactKeys(standing, ['groupId', 'groupPoints', 'groupRank', 'teamCode', 'matchWins', 'matchLosses', 'gameWins', 'gameLosses', 'strengthOfVictory', 'winTimeSeconds', 'tieBreakTrace', 'standingsHash'], `${path}.groupStandings[${index}]`); oneOf(standing.groupId, ['BARON', 'ELDER'] as const, `${path}.groupStandings[${index}].groupId`); integer(standing.groupPoints, `${path}.groupStandings[${index}].groupPoints`); const rank = integer(standing.groupRank, `${path}.groupStandings[${index}].groupRank`, 1); if (rank > 5) throw new CareerContractError(`${path}.groupStandings[${index}].groupRank`); if (!/^[A-Z0-9]{2,16}$/.test(text(standing.teamCode, `${path}.groupStandings[${index}].teamCode`))) throw new CareerContractError(`${path}.groupStandings[${index}].teamCode`); ['matchWins', 'matchLosses', 'gameWins', 'gameLosses', 'strengthOfVictory', 'winTimeSeconds'].forEach((field) => integer(standing[field], `${path}.groupStandings[${index}].${field}`)); text(standing.tieBreakTrace, `${path}.groupStandings[${index}].tieBreakTrace`); identity(standing.standingsHash, SHA256, `${path}.groupStandings[${index}].standingsHash`); });
  if (!Array.isArray(item.currentSeeds) || item.currentSeeds.length > 32) throw new CareerContractError(`${path}.currentSeeds`); item.currentSeeds.forEach((entry, index) => { const seed = object(entry, `${path}.currentSeeds[${index}]`); exactKeys(seed, ['competitionId', 'seedScope', 'seedNumber', 'teamCode', 'sourceInputHash'], `${path}.currentSeeds[${index}]`); oneOf(seed.competitionId, COMPETITION_IDS, `${path}.currentSeeds[${index}].competitionId`); oneOf(seed.seedScope, ['CUP_PLAY_IN_SEED', 'CUP_PLAYOFF_SEED', 'PLAY_IN_SEED', 'LCK_PLAYOFF_SEED'] as const, `${path}.currentSeeds[${index}].seedScope`); integer(seed.seedNumber, `${path}.currentSeeds[${index}].seedNumber`, 1); if (!/^[A-Z0-9]{2,16}$/.test(text(seed.teamCode, `${path}.currentSeeds[${index}].teamCode`))) throw new CareerContractError(`${path}.currentSeeds[${index}].teamCode`); identity(seed.sourceInputHash, SHA256, `${path}.currentSeeds[${index}].sourceInputHash`); });
  const external = bool(item.externalExecutionLimited, `${path}.externalExecutionLimited`); const hasExternal = [current, next].some((summary) => summary?.blockingReason === 'EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED'); if (external !== hasExternal) throw new CareerContractError(`${path}.externalExecutionLimited`, 'external limitation mismatch');
  if (item.activePendingCommand !== null) { const pending = object(item.activePendingCommand, `${path}.activePendingCommand`); exactKeys(pending, ['clientCommandId', 'competitionId', 'matchId', 'commandStatus'], `${path}.activePendingCommand`); if (!UUID.test(text(pending.clientCommandId, `${path}.activePendingCommand.clientCommandId`))) throw new CareerContractError(`${path}.activePendingCommand`, 'pending competition command mismatch'); oneOf(pending.commandStatus, ['PENDING', 'RUNNING'] as const, `${path}.activePendingCommand.commandStatus`); oneOf(pending.competitionId, COMPETITION_IDS, `${path}.activePendingCommand.competitionId`); text(pending.matchId, `${path}.activePendingCommand.matchId`); }
  const competitionCommands = ['START_PLAYER_COMPETITION_SERIES', 'RESUME_PLAYER_COMPETITION_SERIES', 'DISPATCH_AUTO_COMPETITION_FIXTURE', 'RECONCILE_COMPETITION_FIXTURE']; if (!Array.isArray(item.allowedCommands) || item.allowedCommands.some((command) => typeof command !== 'string' || !competitionCommands.includes(command)) || new Set(item.allowedCommands).size !== item.allowedCommands.length) throw new CareerContractError(`${path}.allowedCommands`, 'authoritative competition commands required');
}

export function validateCareerCalendar(value: unknown): CareerCalendarViewDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'careerId', 'activeCalendarSeasonYear', 'currentDate', 'calendarRevision', 'lifecycleStatus', 'blockingReason', 'calendarStateHash', 'stateHashAlgorithm', 'provenance', 'projectionStatus', 'currentEvent', 'nextEvent', 'currentStage', 'nextStage', 'upcomingEvents', 'fixtureOverlay', 'upcomingFixtures', 'nextManagedFixture', 'allowedAdvanceModes', 'activePendingAdvance', 'advanceRecoveryStatus', 'competition', 'qualificationEdges', 'pendingOfficialFields', 'sourceDataNotes'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.calendarView) throw new CareerContractError('$.schemaVersion'); identity(root.careerId, CAREER_ID, '$.careerId'); const year = integer(root.activeCalendarSeasonYear, '$.activeCalendarSeasonYear', 2026); const current = date(root.currentDate, '$.currentDate'); integer(root.calendarRevision, '$.calendarRevision'); oneOf(root.lifecycleStatus, ['ACTIVE', 'SEASON_ROLLOVER_REQUIRED'] as const, '$.lifecycleStatus'); nullableText(root.blockingReason, '$.blockingReason'); identity(root.calendarStateHash, SHA256, '$.calendarStateHash'); if (root.stateHashAlgorithm !== 'CAREER_CALENDAR_STATE_SHA256_CANONICAL_V1') throw new CareerContractError('$.stateHashAlgorithm');
  const provenance = object(root.provenance, '$.provenance'); exactKeys(provenance, ['referenceYear', 'sourceAsOf', 'referenceCatalogSnapshotAt', 'templateVersion', 'templateHash', 'projectionPolicy', 'anchorAlgorithm', 'sourceCount', 'calendarDefinitionCount', 'qualificationEdgeCount', 'derivedRestWindowCount', 'pendingOfficialFieldCount'], '$.provenance'); if (provenance.referenceYear !== 2026 || date(provenance.sourceAsOf, '$.provenance.sourceAsOf') !== '2026-08-23' || date(provenance.referenceCatalogSnapshotAt, '$.provenance.referenceCatalogSnapshotAt') !== '2026-08-24' || provenance.projectionPolicy !== 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1' || provenance.anchorAlgorithm !== 'FIRST_FULL_CYCLE_AFTER_CURRENT_DATE_V1' || provenance.sourceCount !== 15 || provenance.calendarDefinitionCount !== 11 || provenance.qualificationEdgeCount !== 6 || provenance.derivedRestWindowCount !== 7 || provenance.pendingOfficialFieldCount !== 6) throw new CareerContractError('$.provenance', 'frozen source identity mismatch'); text(provenance.templateVersion, '$.provenance.templateVersion'); identity(provenance.templateHash, SHA256, '$.provenance.templateHash'); const projectionStatus = oneOf(root.projectionStatus, ['REFERENCE_YEAR_SOURCE', 'GAME_PROJECTED_FROM_2026_TEMPLATE'] as const, '$.projectionStatus'); if ((year === 2026) !== (projectionStatus === 'REFERENCE_YEAR_SOURCE')) throw new CareerContractError('$.projectionStatus', 'materialized year mismatch');
  const currentEvent = root.currentEvent === null ? null : calendarEvent(root.currentEvent, '$.currentEvent'); if (currentEvent && (current < currentEvent.startDate || current > currentEvent.endDate)) throw new CareerContractError('$.currentEvent', 'does not contain current date'); const nextEvent = root.nextEvent === null ? null : calendarEvent(root.nextEvent, '$.nextEvent'); if (nextEvent && nextEvent.startDate <= current) throw new CareerContractError('$.nextEvent'); const currentStage = root.currentStage === null ? null : calendarStage(root.currentStage, '$.currentStage'); if (currentStage?.startDate && (current < currentStage.startDate || current > currentStage.endDate!)) throw new CareerContractError('$.currentStage'); const nextStage = root.nextStage === null ? null : calendarStage(root.nextStage, '$.nextStage'); if (nextStage?.startDate && nextStage.startDate <= current) throw new CareerContractError('$.nextStage'); if (!Array.isArray(root.upcomingEvents) || root.upcomingEvents.length > 8) throw new CareerContractError('$.upcomingEvents'); const events = root.upcomingEvents.map((entry, index) => calendarEvent(entry, `$.upcomingEvents[${index}]`)); if (new Set(events.map((event) => event.eventId)).size !== events.length || events.some((event, index) => index > 0 && event.startDate < events[index - 1].startDate)) throw new CareerContractError('$.upcomingEvents', 'event order or identity mismatch');
  const overlay = object(root.fixtureOverlay, '$.fixtureOverlay'); exactKeys(overlay, ['schemaVersion', 'allocationPolicy', 'overlayHash', 'scheduleStatus', 'provenanceV2'], '$.fixtureOverlay'); if (overlay.schemaVersion !== 'CAREER_R1_R2_FIXTURE_OVERLAY_V1' || overlay.allocationPolicy !== 'ROUND_LINEAR_INCLUSIVE_WINDOW_ONE_SLOT_PER_ROUND_V1' || overlay.scheduleStatus !== 'GAME_DERIVED_SCHEDULE_POLICY') throw new CareerContractError('$.fixtureOverlay'); identity(overlay.overlayHash, SHA256, '$.fixtureOverlay.overlayHash'); const overlayV2 = object(overlay.provenanceV2, '$.fixtureOverlay.provenanceV2'); exactKeys(overlayV2, ['schemaVersion', 'hashAlgorithm', 'leagueId', 'seasonId', 'scheduleIdentity', 'overlayHash'], '$.fixtureOverlay.provenanceV2'); if (overlayV2.schemaVersion !== 'CAREER_R1_R2_FIXTURE_OVERLAY_PROVENANCE_V2' || overlayV2.hashAlgorithm !== 'SHA256_UTF8_EXPLICIT_ORDERED_R1_R2_OVERLAY_PROVENANCE_V2') throw new CareerContractError('$.fixtureOverlay.provenanceV2'); identity(overlayV2.leagueId, LEAGUE_ID, '$.fixtureOverlay.provenanceV2.leagueId'); identity(overlayV2.seasonId, SEASON_ID, '$.fixtureOverlay.provenanceV2.seasonId'); identity(overlayV2.scheduleIdentity, SHA256, '$.fixtureOverlay.provenanceV2.scheduleIdentity'); identity(overlayV2.overlayHash, SHA256, '$.fixtureOverlay.provenanceV2.overlayHash'); if (!Array.isArray(root.upcomingFixtures) || root.upcomingFixtures.length > 8) throw new CareerContractError('$.upcomingFixtures'); const fixtures = root.upcomingFixtures.map((entry, index) => calendarFixture(entry, `$.upcomingFixtures[${index}]`)); if (fixtures.some((fixture, index) => index > 0 && (fixture.date < fixtures[index - 1].date || fixture.date === fixtures[index - 1].date && fixture.fixtureId < fixtures[index - 1].fixtureId))) throw new CareerContractError('$.upcomingFixtures', 'fixture order mismatch'); const managed = root.nextManagedFixture === null ? null : calendarFixture(root.nextManagedFixture, '$.nextManagedFixture'); if (managed && managed.executionMode !== 'PLAYER_CONTROLLED') throw new CareerContractError('$.nextManagedFixture');
  let pendingAdvance = null; if (root.activePendingAdvance !== null) { const pending = object(root.activePendingAdvance, '$.activePendingAdvance'); exactKeys(pending, ['clientCommandId', 'mode', 'expectedCalendarRevision', 'commandStatus', 'createdAt', 'updatedAt'], '$.activePendingAdvance'); if (!UUID.test(text(pending.clientCommandId, '$.activePendingAdvance.clientCommandId'))) throw new CareerContractError('$.activePendingAdvance.clientCommandId'); oneOf(pending.mode, ADVANCE_MODES, '$.activePendingAdvance.mode'); integer(pending.expectedCalendarRevision, '$.activePendingAdvance.expectedCalendarRevision'); if (pending.commandStatus !== 'PENDING') throw new CareerContractError('$.activePendingAdvance.commandStatus'); const created = timestamp(pending.createdAt, '$.activePendingAdvance.createdAt'); const updated = timestamp(pending.updatedAt, '$.activePendingAdvance.updatedAt'); if (Date.parse(updated) < Date.parse(created)) throw new CareerContractError('$.activePendingAdvance.updatedAt'); pendingAdvance = pending; }
  const advanceRecovery = root.advanceRecoveryStatus === null ? null : oneOf(root.advanceRecoveryStatus, ['LEGACY_PENDING_RECONCILIATION_REQUIRED'] as const, '$.advanceRecoveryStatus'); if (pendingAdvance && advanceRecovery) throw new CareerContractError('$.advanceRecoveryStatus', 'canonical and legacy pending are mutually exclusive');
  if (!Array.isArray(root.allowedAdvanceModes)) throw new CareerContractError('$.allowedAdvanceModes'); const modes = root.allowedAdvanceModes.map((entry, index) => oneOf(entry, ADVANCE_MODES, `$.allowedAdvanceModes[${index}]`) as CareerAdvanceMode); if (new Set(modes).size !== modes.length) throw new CareerContractError('$.allowedAdvanceModes'); const lifecycleBlocked = root.blockingReason !== null; if ((pendingAdvance || advanceRecovery || lifecycleBlocked || root.lifecycleStatus !== 'ACTIVE') && modes.length !== 0) throw new CareerContractError('$.allowedAdvanceModes', 'blocked Calendar cannot advance'); if (!pendingAdvance && !advanceRecovery && !lifecycleBlocked && root.lifecycleStatus === 'ACTIVE' && (modes.length !== 2 || !ADVANCE_MODES.every((mode) => modes.includes(mode)))) throw new CareerContractError('$.allowedAdvanceModes', 'active Calendar modes mismatch'); competitionView(root.competition, '$.competition', year); if (!Array.isArray(root.qualificationEdges) || root.qualificationEdges.length !== 6) throw new CareerContractError('$.qualificationEdges'); root.qualificationEdges.forEach((entry, index) => { const item = object(entry, `$.qualificationEdges[${index}]`); exactKeys(item, ['fromTemplateId', 'toTemplateId', 'rule', 'officialStatus'], `$.qualificationEdges[${index}]`); text(item.fromTemplateId, `$.qualificationEdges[${index}].fromTemplateId`); text(item.toTemplateId, `$.qualificationEdges[${index}].toTemplateId`); text(item.rule, `$.qualificationEdges[${index}].rule`); oneOf(item.officialStatus, OFFICIAL_STATUSES, `$.qualificationEdges[${index}].officialStatus`); }); if (!Array.isArray(root.pendingOfficialFields) || root.pendingOfficialFields.length !== 6) throw new CareerContractError('$.pendingOfficialFields'); root.pendingOfficialFields.forEach((entry, index) => { const item = object(entry, `$.pendingOfficialFields[${index}]`); exactKeys(item, ['id', 'field', 'reason'], `$.pendingOfficialFields[${index}]`); text(item.id, `$.pendingOfficialFields[${index}].id`); text(item.field, `$.pendingOfficialFields[${index}].field`); text(item.reason, `$.pendingOfficialFields[${index}].reason`); }); if (!Array.isArray(root.sourceDataNotes) || root.sourceDataNotes.length !== 1) throw new CareerContractError('$.sourceDataNotes'); const note = object(root.sourceDataNotes[0], '$.sourceDataNotes[0]'); exactKeys(note, ['subject', 'status', 'sourceReferenceYear', 'ruleVersion', 'blockers'], '$.sourceDataNotes[0]'); const blockers = texts(note.blockers, '$.sourceDataNotes[0].blockers'); if (note.subject !== 'KESPA_CUP' || note.status !== 'REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE' || note.sourceReferenceYear !== 2025 || note.ruleVersion !== 'KESPA_CUP_REFERENCE_TEMPLATE_2025' || blockers.length !== 2 || blockers[0] !== 'KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE' || blockers[1] !== 'EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING') throw new CareerContractError('$.sourceDataNotes[0]'); return root as unknown as CareerCalendarViewDto;
}

export function validateCareerAdvanceResponse(value: unknown): CareerAdvanceResponseDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'replayed', 'pending', 'stopReason', 'backgroundAccepted', 'commandResult', 'calendar'], '$'); if (root.schemaVersion !== CAREER_SCHEMAS.advanceResponse) throw new CareerContractError('$.schemaVersion'); bool(root.replayed, '$.replayed'); const pending = bool(root.pending, '$.pending'); const stopReason = nullableText(root.stopReason, '$.stopReason'); const accepted = bool(root.backgroundAccepted, '$.backgroundAccepted'); const result = object(root.commandResult, '$.commandResult'); exactKeys(result, ['clientCommandId', 'mode', 'expectedCalendarRevision', 'commandStatus', 'resultingDate', 'resultingCalendarRevision', 'resultingStateHash', 'resultingLifecycleStatus', 'resultingBlockingReason', 'stopReason', 'pending', 'backgroundAccepted', 'createdAt', 'updatedAt', 'completedAt'], '$.commandResult'); if (!UUID.test(text(result.clientCommandId, '$.commandResult.clientCommandId'))) throw new CareerContractError('$.commandResult.clientCommandId'); oneOf(result.mode, ADVANCE_MODES, '$.commandResult.mode'); integer(result.expectedCalendarRevision, '$.commandResult.expectedCalendarRevision'); const status = oneOf(result.commandStatus, ['PENDING', 'COMPLETED'] as const, '$.commandResult.commandStatus'); date(result.resultingDate, '$.commandResult.resultingDate'); integer(result.resultingCalendarRevision, '$.commandResult.resultingCalendarRevision'); identity(result.resultingStateHash, SHA256, '$.commandResult.resultingStateHash'); oneOf(result.resultingLifecycleStatus, ['ACTIVE', 'SEASON_ROLLOVER_REQUIRED'] as const, '$.commandResult.resultingLifecycleStatus'); nullableText(result.resultingBlockingReason, '$.commandResult.resultingBlockingReason'); const resultStop = nullableText(result.stopReason, '$.commandResult.stopReason'); const resultPending = bool(result.pending, '$.commandResult.pending'); const resultAccepted = bool(result.backgroundAccepted, '$.commandResult.backgroundAccepted'); const created = timestamp(result.createdAt, '$.commandResult.createdAt'); const updated = timestamp(result.updatedAt, '$.commandResult.updatedAt'); const completed = result.completedAt === null ? null : timestamp(result.completedAt, '$.commandResult.completedAt'); if (pending !== resultPending || pending !== (status === 'PENDING') || stopReason !== resultStop || accepted !== resultAccepted || Date.parse(updated) < Date.parse(created) || (status === 'COMPLETED') !== (completed !== null)) throw new CareerContractError('$.commandResult', 'command receipt relation mismatch'); validateCareerCalendar(root.calendar); return root as unknown as CareerAdvanceResponseDto;
}

export function validateCareerCompetitionCommandResponse(
  value: unknown,
): CareerCompetitionCommandResponseDto {
  const root = object(value, '$');
  exactKeys(root, ['schemaVersion', 'executionMode', 'fixtureId', 'matchId', 'seriesId', 'bindingHash', 'jobId', 'status', 'replayed', 'backgroundAccepted', 'failureCode'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.competitionCommandResponse) throw new CareerContractError('$.schemaVersion');
  const mode = oneOf(root.executionMode, ['FULL_AUTO', 'PLAYER_CONTROLLED', 'NONE'] as const, '$.executionMode');
  const fixtureId = nullableId(root.fixtureId, COMPETITION_FIXTURE_ID, '$.fixtureId');
  const matchId = nullableText(root.matchId, '$.matchId');
  const seriesId = nullableId(root.seriesId, SERIES_ID, '$.seriesId');
  const bindingHash = nullableId(root.bindingHash, SHA256, '$.bindingHash');
  const jobId = nullableText(root.jobId, '$.jobId');
  const status = oneOf(root.status, ['NOT_STARTED', 'PENDING', 'RUNNING', 'ACTIVE', 'COMPLETED', 'BLOCKED', 'EXPIRED', 'CANCELLED'] as const, '$.status');
  bool(root.replayed, '$.replayed');
  const accepted = bool(root.backgroundAccepted, '$.backgroundAccepted');
  nullableText(root.failureCode, '$.failureCode');
  if (mode === 'NONE') {
    if (fixtureId !== null || matchId !== null || seriesId !== null || bindingHash !== null || jobId !== null || accepted || status !== 'NOT_STARTED') throw new CareerContractError('$', 'empty reconciliation scope mismatch');
  } else if (!fixtureId || !matchId || !seriesId || !bindingHash) throw new CareerContractError('$', 'bound Competition identity required');
  if ((mode === 'PLAYER_CONTROLLED' && jobId !== null)
    || (mode === 'FULL_AUTO' && jobId === null)) throw new CareerContractError('$.jobId', 'execution mode/job mismatch');
  if ((mode === 'FULL_AUTO' && !['PENDING', 'RUNNING', 'COMPLETED', 'BLOCKED'].includes(status))
    || (mode === 'PLAYER_CONTROLLED' && !['ACTIVE', 'COMPLETED', 'BLOCKED', 'EXPIRED', 'CANCELLED'].includes(status))) throw new CareerContractError('$.status', 'execution mode/status mismatch');
  if (accepted !== (mode === 'FULL_AUTO' && ['PENDING', 'RUNNING'].includes(status))) throw new CareerContractError('$.backgroundAccepted', 'worker acceptance mismatch');
  return root as unknown as CareerCompetitionCommandResponseDto;
}

export function validateCareerError(value: unknown): CareerErrorDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'code', 'field', 'message'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.error) throw new CareerContractError('$.schemaVersion');
  text(root.code, '$.code'); if (root.field !== null && typeof root.field !== 'string') throw new CareerContractError('$.field'); text(root.message, '$.message');
  return root as unknown as CareerErrorDto;
}

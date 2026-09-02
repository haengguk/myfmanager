import {
  CAREER_SCHEMAS,
  type CareerAllowedCommand,
  type CareerCreateResponseDto,
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

export function validateCareerError(value: unknown): CareerErrorDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'code', 'field', 'message'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.error) throw new CareerContractError('$.schemaVersion');
  text(root.code, '$.code'); if (root.field !== null && typeof root.field !== 'string') throw new CareerContractError('$.field'); text(root.message, '$.message');
  return root as unknown as CareerErrorDto;
}

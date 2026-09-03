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
  identity(item.eventId, CALENDAR_EVENT_ID, `${path}.eventId`); const templateId = text(item.templateId, `${path}.templateId`); if (/\d{4}|KESPA/i.test(templateId)) throw new CareerContractError(`${path}.templateId`, 'year-neutral sourced template required'); text(item.sourceReferenceId, `${path}.sourceReferenceId`); text(item.displayNameKo, `${path}.displayNameKo`); const start = date(item.startDate, `${path}.startDate`); const end = date(item.endDate, `${path}.endDate`); if (start > end) throw new CareerContractError(path, 'event date range mismatch'); const scope = oneOf(item.timezoneScope, ['SINGLE_IANA_ZONE', 'MULTI_ZONE'] as const, `${path}.timezoneScope`); const timezone = nullableText(item.timezone, `${path}.timezone`); if ((scope === 'SINGLE_IANA_ZONE') !== (timezone !== null)) throw new CareerContractError(`${path}.timezone`, 'timezone scope mismatch'); texts(item.locations, `${path}.locations`); oneOf(item.officialStatus, OFFICIAL_STATUSES, `${path}.officialStatus`); oneOf(item.projectionStatus, ['REFERENCE_YEAR_SOURCE', 'GAME_PROJECTED_FROM_2026_TEMPLATE'] as const, `${path}.projectionStatus`); oneOf(item.participationType, ['ALL_LCK', 'RANKING_QUALIFIED', 'REGION_SLOT', 'NATIONAL_TEAM_RELEASE'] as const, `${path}.participationType`); text(item.participation, `${path}.participation`); nullableInteger(item.teamCount, `${path}.teamCount`); nullableInteger(item.seriesCount, `${path}.seriesCount`); text(item.format, `${path}.format`); texts(item.seriesRules, `${path}.seriesRules`); nullableText(item.draftMode, `${path}.draftMode`); text(item.draftStatus, `${path}.draftStatus`); oneOf(item.executionStatus, ['LINKED_EXISTING_LEAGUE_FIXTURES', 'FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED'] as const, `${path}.executionStatus`); if (!Array.isArray(item.stages)) throw new CareerContractError(`${path}.stages`, 'array required'); item.stages.forEach((stage, index) => calendarStage(stage, `${path}.stages[${index}]`)); return item as unknown as CareerCalendarEventDto;
}

function calendarFixture(value: unknown, path: string): CareerCalendarFixtureDto {
  const item = object(value, path); exactKeys(item, ['fixtureId', 'roundNumber', 'date', 'scheduleStatus', 'executionMode', 'firstTeamCode', 'secondTeamCode', 'lifecycleStatus', 'seriesId', 'jobStatus', 'pendingOutbox'], path); identity(item.fixtureId, FIXTURE_ID, `${path}.fixtureId`); integer(item.roundNumber, `${path}.roundNumber`, 1); if (Number(item.roundNumber) > 18) throw new CareerContractError(`${path}.roundNumber`); date(item.date, `${path}.date`); if (item.scheduleStatus !== 'GAME_DERIVED_SCHEDULE_POLICY') throw new CareerContractError(`${path}.scheduleStatus`); oneOf(item.executionMode, ['FULL_AUTO', 'PLAYER_CONTROLLED'] as const, `${path}.executionMode`); for (const key of ['firstTeamCode', 'secondTeamCode'] as const) if (!/^[A-Z0-9]{2,16}$/.test(text(item[key], `${path}.${key}`))) throw new CareerContractError(`${path}.${key}`); text(item.lifecycleStatus, `${path}.lifecycleStatus`); identity(item.seriesId, SERIES_ID, `${path}.seriesId`); nullableText(item.jobStatus, `${path}.jobStatus`); bool(item.pendingOutbox, `${path}.pendingOutbox`); return item as unknown as CareerCalendarFixtureDto;
}

export function validateCareerCalendar(value: unknown): CareerCalendarViewDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'careerId', 'activeCalendarSeasonYear', 'currentDate', 'calendarRevision', 'lifecycleStatus', 'blockingReason', 'calendarStateHash', 'stateHashAlgorithm', 'provenance', 'projectionStatus', 'currentEvent', 'nextEvent', 'currentStage', 'nextStage', 'upcomingEvents', 'fixtureOverlay', 'upcomingFixtures', 'nextManagedFixture', 'allowedAdvanceModes', 'qualificationEdges', 'pendingOfficialFields', 'sourceDataNotes'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.calendarView) throw new CareerContractError('$.schemaVersion'); identity(root.careerId, CAREER_ID, '$.careerId'); const year = integer(root.activeCalendarSeasonYear, '$.activeCalendarSeasonYear', 2026); const current = date(root.currentDate, '$.currentDate'); integer(root.calendarRevision, '$.calendarRevision'); oneOf(root.lifecycleStatus, ['ACTIVE', 'SEASON_ROLLOVER_REQUIRED'] as const, '$.lifecycleStatus'); nullableText(root.blockingReason, '$.blockingReason'); identity(root.calendarStateHash, SHA256, '$.calendarStateHash'); if (root.stateHashAlgorithm !== 'CAREER_CALENDAR_STATE_SHA256_CANONICAL_V1') throw new CareerContractError('$.stateHashAlgorithm');
  const provenance = object(root.provenance, '$.provenance'); exactKeys(provenance, ['referenceYear', 'sourceAsOf', 'referenceCatalogSnapshotAt', 'templateVersion', 'templateHash', 'projectionPolicy', 'anchorAlgorithm', 'sourceCount', 'calendarDefinitionCount', 'qualificationEdgeCount', 'derivedRestWindowCount', 'pendingOfficialFieldCount'], '$.provenance'); if (provenance.referenceYear !== 2026 || date(provenance.sourceAsOf, '$.provenance.sourceAsOf') !== '2026-08-23' || date(provenance.referenceCatalogSnapshotAt, '$.provenance.referenceCatalogSnapshotAt') !== '2026-08-24' || provenance.projectionPolicy !== 'SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1' || provenance.anchorAlgorithm !== 'FIRST_FULL_CYCLE_AFTER_CURRENT_DATE_V1' || provenance.sourceCount !== 15 || provenance.calendarDefinitionCount !== 11 || provenance.qualificationEdgeCount !== 6 || provenance.derivedRestWindowCount !== 7 || provenance.pendingOfficialFieldCount !== 6) throw new CareerContractError('$.provenance', 'frozen source identity mismatch'); text(provenance.templateVersion, '$.provenance.templateVersion'); identity(provenance.templateHash, SHA256, '$.provenance.templateHash'); const projectionStatus = oneOf(root.projectionStatus, ['REFERENCE_YEAR_SOURCE', 'GAME_PROJECTED_FROM_2026_TEMPLATE'] as const, '$.projectionStatus'); if ((year === 2026) !== (projectionStatus === 'REFERENCE_YEAR_SOURCE')) throw new CareerContractError('$.projectionStatus', 'materialized year mismatch');
  const currentEvent = root.currentEvent === null ? null : calendarEvent(root.currentEvent, '$.currentEvent'); if (currentEvent && (current < currentEvent.startDate || current > currentEvent.endDate)) throw new CareerContractError('$.currentEvent', 'does not contain current date'); const nextEvent = root.nextEvent === null ? null : calendarEvent(root.nextEvent, '$.nextEvent'); if (nextEvent && nextEvent.startDate <= current) throw new CareerContractError('$.nextEvent'); const currentStage = root.currentStage === null ? null : calendarStage(root.currentStage, '$.currentStage'); if (currentStage?.startDate && (current < currentStage.startDate || current > currentStage.endDate!)) throw new CareerContractError('$.currentStage'); const nextStage = root.nextStage === null ? null : calendarStage(root.nextStage, '$.nextStage'); if (nextStage?.startDate && nextStage.startDate <= current) throw new CareerContractError('$.nextStage'); if (!Array.isArray(root.upcomingEvents) || root.upcomingEvents.length > 8) throw new CareerContractError('$.upcomingEvents'); const events = root.upcomingEvents.map((entry, index) => calendarEvent(entry, `$.upcomingEvents[${index}]`)); if (new Set(events.map((event) => event.eventId)).size !== events.length || events.some((event, index) => index > 0 && event.startDate < events[index - 1].startDate)) throw new CareerContractError('$.upcomingEvents', 'event order or identity mismatch');
  const overlay = object(root.fixtureOverlay, '$.fixtureOverlay'); exactKeys(overlay, ['schemaVersion', 'allocationPolicy', 'overlayHash', 'scheduleStatus'], '$.fixtureOverlay'); if (overlay.schemaVersion !== 'CAREER_R1_R2_FIXTURE_OVERLAY_V1' || overlay.allocationPolicy !== 'ROUND_LINEAR_INCLUSIVE_WINDOW_ONE_SLOT_PER_ROUND_V1' || overlay.scheduleStatus !== 'GAME_DERIVED_SCHEDULE_POLICY') throw new CareerContractError('$.fixtureOverlay'); identity(overlay.overlayHash, SHA256, '$.fixtureOverlay.overlayHash'); if (!Array.isArray(root.upcomingFixtures) || root.upcomingFixtures.length > 8) throw new CareerContractError('$.upcomingFixtures'); const fixtures = root.upcomingFixtures.map((entry, index) => calendarFixture(entry, `$.upcomingFixtures[${index}]`)); if (fixtures.some((fixture, index) => index > 0 && (fixture.date < fixtures[index - 1].date || fixture.date === fixtures[index - 1].date && fixture.fixtureId < fixtures[index - 1].fixtureId))) throw new CareerContractError('$.upcomingFixtures', 'fixture order mismatch'); const managed = root.nextManagedFixture === null ? null : calendarFixture(root.nextManagedFixture, '$.nextManagedFixture'); if (managed && managed.executionMode !== 'PLAYER_CONTROLLED') throw new CareerContractError('$.nextManagedFixture');
  if (!Array.isArray(root.allowedAdvanceModes)) throw new CareerContractError('$.allowedAdvanceModes'); const modes = root.allowedAdvanceModes.map((entry, index) => oneOf(entry, ADVANCE_MODES, `$.allowedAdvanceModes[${index}]`) as CareerAdvanceMode); if (new Set(modes).size !== modes.length) throw new CareerContractError('$.allowedAdvanceModes'); if (!Array.isArray(root.qualificationEdges) || root.qualificationEdges.length !== 6) throw new CareerContractError('$.qualificationEdges'); root.qualificationEdges.forEach((entry, index) => { const item = object(entry, `$.qualificationEdges[${index}]`); exactKeys(item, ['fromTemplateId', 'toTemplateId', 'rule', 'officialStatus'], `$.qualificationEdges[${index}]`); text(item.fromTemplateId, `$.qualificationEdges[${index}].fromTemplateId`); text(item.toTemplateId, `$.qualificationEdges[${index}].toTemplateId`); text(item.rule, `$.qualificationEdges[${index}].rule`); oneOf(item.officialStatus, OFFICIAL_STATUSES, `$.qualificationEdges[${index}].officialStatus`); }); if (!Array.isArray(root.pendingOfficialFields) || root.pendingOfficialFields.length !== 6) throw new CareerContractError('$.pendingOfficialFields'); root.pendingOfficialFields.forEach((entry, index) => { const item = object(entry, `$.pendingOfficialFields[${index}]`); exactKeys(item, ['id', 'field', 'reason'], `$.pendingOfficialFields[${index}]`); text(item.id, `$.pendingOfficialFields[${index}].id`); text(item.field, `$.pendingOfficialFields[${index}].field`); text(item.reason, `$.pendingOfficialFields[${index}].reason`); }); if (!Array.isArray(root.sourceDataNotes) || root.sourceDataNotes.length !== 1) throw new CareerContractError('$.sourceDataNotes'); const note = object(root.sourceDataNotes[0], '$.sourceDataNotes[0]'); exactKeys(note, ['subject', 'status'], '$.sourceDataNotes[0]'); if (note.subject !== 'KESPA_CUP' || note.status !== 'SOURCE_DATA_NOT_PRESENT') throw new CareerContractError('$.sourceDataNotes[0]'); return root as unknown as CareerCalendarViewDto;
}

export function validateCareerAdvanceResponse(value: unknown): CareerAdvanceResponseDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'replayed', 'pending', 'stopReason', 'backgroundAccepted', 'calendar'], '$'); if (root.schemaVersion !== CAREER_SCHEMAS.advanceResponse) throw new CareerContractError('$.schemaVersion'); bool(root.replayed, '$.replayed'); bool(root.pending, '$.pending'); nullableText(root.stopReason, '$.stopReason'); bool(root.backgroundAccepted, '$.backgroundAccepted'); validateCareerCalendar(root.calendar); return root as unknown as CareerAdvanceResponseDto;
}

export function validateCareerError(value: unknown): CareerErrorDto {
  const root = object(value, '$'); exactKeys(root, ['schemaVersion', 'code', 'field', 'message'], '$');
  if (root.schemaVersion !== CAREER_SCHEMAS.error) throw new CareerContractError('$.schemaVersion');
  text(root.code, '$.code'); if (root.field !== null && typeof root.field !== 'string') throw new CareerContractError('$.field'); text(root.message, '$.message');
  return root as unknown as CareerErrorDto;
}

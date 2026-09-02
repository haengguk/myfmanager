import {
  LCK_POSITIONS, TEAM_PLAYER_SCHEMAS,
  type CatalogMetadataDto, type ChampionProficiencyDto, type LckPosition,
  type PlayerDetailDto, type PlayerResponseDto, type PlayersResponseDto,
  type PlayerSummaryDto, type TeamPlayerErrorDto, type TeamPlayerMetadataDto,
  type TeamPlayerWorkspaceDto, type TeamsResponseDto,
} from './teamPlayerApi.types.ts';

export class TeamPlayerContractError extends Error {
  readonly path: string;
  constructor(path: string, message = 'required contract mismatch') {
    super(`${path}: ${message}`);
    this.name = 'TeamPlayerContractError';
    this.path = path;
  }
}

type RecordValue = Record<string, unknown>;
const PLAYER_ID = /^player-[a-z0-9]+(?:-[a-z0-9]+)*$/;
const SHA256 = /^[a-f0-9]{64}$/;

function object(value: unknown, path: string): RecordValue {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new TeamPlayerContractError(path, 'object required');
  return value as RecordValue;
}
function array(value: unknown, path: string): unknown[] {
  if (!Array.isArray(value)) throw new TeamPlayerContractError(path, 'array required');
  return value;
}
function text(value: unknown, path: string): string {
  if (typeof value !== 'string' || value.trim() === '') throw new TeamPlayerContractError(path, 'non-empty string required');
  return value;
}
function nullableText(value: unknown, path: string): string | null {
  if (value === null) return null;
  return text(value, path);
}
function integer(value: unknown, path: string, min?: number, max?: number): number {
  if (!Number.isInteger(value) || (min !== undefined && Number(value) < min) || (max !== undefined && Number(value) > max)) {
    throw new TeamPlayerContractError(path, `integer${min === undefined ? '' : ` ${min}..${max ?? '∞'}`} required`);
  }
  return Number(value);
}
function numberValue(value: unknown, path: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new TeamPlayerContractError(path, 'finite number required');
  return value;
}
function bool(value: unknown, path: string): boolean {
  if (typeof value !== 'boolean') throw new TeamPlayerContractError(path, 'boolean required');
  return value;
}
function position(value: unknown, path: string): LckPosition {
  if (typeof value !== 'string' || !(LCK_POSITIONS as readonly string[]).includes(value)) throw new TeamPlayerContractError(path, 'known Position required');
  return value as LckPosition;
}
function exact(value: unknown, expected: string, path: string): void {
  if (value !== expected) throw new TeamPlayerContractError(path, `expected ${expected}`);
}
function textList(value: unknown, path: string): string[] {
  return array(value, path).map((entry, index) => text(entry, `${path}[${index}]`));
}
function unique(values: readonly string[], path: string): void {
  if (new Set(values).size !== values.length) throw new TeamPlayerContractError(path, 'duplicates are not allowed');
}

function catalog(value: unknown, path: string): CatalogMetadataDto {
  const item = object(value, path);
  text(item.catalogSchemaVersion, `${path}.catalogSchemaVersion`);
  text(item.catalogVersion, `${path}.catalogVersion`);
  exact(item.catalogHashAlgorithm, 'SHA-256', `${path}.catalogHashAlgorithm`);
  if (!SHA256.test(text(item.catalogHash, `${path}.catalogHash`))) throw new TeamPlayerContractError(`${path}.catalogHash`, 'lowercase SHA-256 required');
  text(item.championPoolVersion, `${path}.championPoolVersion`);
  const resources = array(item.sourceResources, `${path}.sourceResources`);
  if (resources.length < 4) throw new TeamPlayerContractError(`${path}.sourceResources`, 'at least four source resources required');
  resources.forEach((entry, index) => {
    const resource = object(entry, `${path}.sourceResources[${index}]`);
    text(resource.role, `${path}.sourceResources[${index}].role`);
    text(resource.version, `${path}.sourceResources[${index}].version`);
    if (!SHA256.test(text(resource.rawSha256, `${path}.sourceResources[${index}].rawSha256`))) throw new TeamPlayerContractError(`${path}.sourceResources[${index}].rawSha256`, 'lowercase SHA-256 required');
    nullableText(resource.snapshotAt, `${path}.sourceResources[${index}].snapshotAt`);
    nullableText(resource.researchAsOf, `${path}.sourceResources[${index}].researchAsOf`);
    nullableText(resource.dataCutoff, `${path}.sourceResources[${index}].dataCutoff`);
  });
  unique(resources.map((entry) => text(object(entry, path).role, `${path}.role`)), `${path}.sourceResources.role`);
  return value as CatalogMetadataDto;
}

function catalogIdentity(value: CatalogMetadataDto): string {
  return [value.catalogSchemaVersion, value.catalogVersion, value.catalogHashAlgorithm, value.catalogHash, value.championPoolVersion,
    ...value.sourceResources.map((entry) => `${entry.role}:${entry.version}:${entry.rawSha256}`)].join('|');
}

function playerSummary(value: unknown, path: string): PlayerSummaryDto {
  const item = object(value, path);
  if (!PLAYER_ID.test(text(item.playerId, `${path}.playerId`))) throw new TeamPlayerContractError(`${path}.playerId`, 'canonical PlayerId required');
  text(item.nickname, `${path}.nickname`);
  text(item.currentTeamCode, `${path}.currentTeamCode`);
  position(item.position, `${path}.position`);
  textList(item.nationality, `${path}.nationality`);
  text(item.birthDate, `${path}.birthDate`);
  text(item.contractEndDate, `${path}.contractEndDate`);
  text(item.contractStatus, `${path}.contractStatus`);
  return value as PlayerSummaryDto;
}

export function validateMetadata(value: unknown): TeamPlayerMetadataDto {
  const root = object(value, '$');
  exact(root.schemaVersion, TEAM_PLAYER_SCHEMAS.metadata, '$.schemaVersion');
  exact(root.leagueCode, 'LCK', '$.leagueCode');
  catalog(root.catalog, '$.catalog');
  const counts = object(root.counts, '$.counts');
  const expected: Record<string, number> = {
    teams: 10, players: 50, uniquePlayerIds: 50, teamHistoryRows: 248,
    teamAchievementRows: 154, individualAwardRows: 21, sourceRows: 248,
    authoredProficiencies: 732, neutralFallbackKeys: 1428, playersWithMajorHonorsListed: 43,
  };
  Object.entries(expected).forEach(([key, required]) => {
    if (integer(counts[key], `$.counts.${key}`, 0) !== required) throw new TeamPlayerContractError(`$.counts.${key}`, `expected ${required}`);
  });
  const semantics = object(root.semantics, '$.semantics');
  ['contract', 'career', 'honors', 'prizeMoney', 'age'].forEach((key) => text(semantics[key], `$.semantics.${key}`));
  const limits = object(root.limitations, '$.limitations');
  ['currentLckOnly', 'startersOnly', 'substitutesIncluded', 'salaryIncluded', 'marketValueIncluded', 'overallRatingIncluded', 'mutableCareerStateIncluded', 'affectsGameplayOrRandomIdentity']
    .forEach((key) => bool(limits[key], `$.limitations.${key}`));
  if (limits.currentLckOnly !== true || limits.startersOnly !== true || ['substitutesIncluded', 'salaryIncluded', 'marketValueIncluded', 'overallRatingIncluded', 'mutableCareerStateIncluded', 'affectsGameplayOrRandomIdentity'].some((key) => limits[key] !== false)) {
    throw new TeamPlayerContractError('$.limitations', 'unsupported V1 scope');
  }
  return value as TeamPlayerMetadataDto;
}

export function validateTeams(value: unknown): TeamsResponseDto {
  const root = object(value, '$');
  exact(root.schemaVersion, TEAM_PLAYER_SCHEMAS.teams, '$.schemaVersion');
  exact(root.leagueCode, 'LCK', '$.leagueCode');
  catalog(root.catalog, '$.catalog');
  const teams = array(root.teams, '$.teams');
  if (teams.length !== 10) throw new TeamPlayerContractError('$.teams', 'exactly 10 teams required');
  const teamCodes: string[] = [];
  teams.forEach((entry, teamIndex) => {
    const team = object(entry, `$.teams[${teamIndex}]`);
    teamCodes.push(text(team.teamCode, `$.teams[${teamIndex}].teamCode`));
    if (integer(team.starterCount, `$.teams[${teamIndex}].starterCount`) !== 5) throw new TeamPlayerContractError(`$.teams[${teamIndex}].starterCount`, 'exactly 5 starters required');
    const lineup = array(team.lineup, `$.teams[${teamIndex}].lineup`);
    if (lineup.length !== 5) throw new TeamPlayerContractError(`$.teams[${teamIndex}].lineup`, 'exactly 5 starters required');
    const playerIds: string[] = [];
    lineup.forEach((rawPlayer, playerIndex) => {
      const player = object(rawPlayer, `$.teams[${teamIndex}].lineup[${playerIndex}]`);
      const playerId = text(player.playerId, `$.teams[${teamIndex}].lineup[${playerIndex}].playerId`);
      if (!PLAYER_ID.test(playerId)) throw new TeamPlayerContractError(`$.teams[${teamIndex}].lineup[${playerIndex}].playerId`, 'canonical PlayerId required');
      playerIds.push(playerId);
      text(player.nickname, `$.teams[${teamIndex}].lineup[${playerIndex}].nickname`);
      const actualPosition = position(player.position, `$.teams[${teamIndex}].lineup[${playerIndex}].position`);
      if (actualPosition !== LCK_POSITIONS[playerIndex]) throw new TeamPlayerContractError(`$.teams[${teamIndex}].lineup`, 'canonical Position order required');
    });
    unique(playerIds, `$.teams[${teamIndex}].lineup.playerId`);
  });
  unique(teamCodes, '$.teams.teamCode');
  return value as TeamsResponseDto;
}

export function validatePlayers(value: unknown): PlayersResponseDto {
  const root = object(value, '$');
  exact(root.schemaVersion, TEAM_PLAYER_SCHEMAS.players, '$.schemaVersion');
  exact(root.leagueCode, 'LCK', '$.leagueCode');
  catalog(root.catalog, '$.catalog');
  const filters = object(root.filters, '$.filters');
  nullableText(filters.teamCode, '$.filters.teamCode');
  if (filters.position !== null) position(filters.position, '$.filters.position');
  if (filters.teamCode !== null || filters.position !== null) throw new TeamPlayerContractError('$.filters', 'unfiltered workspace response required');
  const players = array(root.players, '$.players');
  if (players.length !== 50) throw new TeamPlayerContractError('$.players', 'exactly 50 players required');
  players.forEach((entry, index) => playerSummary(entry, `$.players[${index}]`));
  unique(players.map((entry) => text(object(entry, '$.players').playerId, '$.players.playerId')), '$.players.playerId');
  return value as PlayersResponseDto;
}

export function validateWorkspace(metadataValue: unknown, teamsValue: unknown, playersValue: unknown): TeamPlayerWorkspaceDto {
  const metadata = validateMetadata(metadataValue);
  const teams = validateTeams(teamsValue);
  const players = validatePlayers(playersValue);
  if (new Set([catalogIdentity(metadata.catalog), catalogIdentity(teams.catalog), catalogIdentity(players.catalog)]).size !== 1) {
    throw new TeamPlayerContractError('$.catalog', 'workspace responses come from different catalog generations');
  }
  const lineup = teams.teams.flatMap((team) => team.lineup.map((entry) => ({ ...entry, teamCode: team.teamCode })));
  const summaries = new Map(players.players.map((entry) => [entry.playerId, entry]));
  if (lineup.length !== players.players.length) throw new TeamPlayerContractError('$.players', 'roster cardinality mismatch');
  lineup.forEach((entry) => {
    const summary = summaries.get(entry.playerId);
    if (!summary || summary.nickname !== entry.nickname || summary.position !== entry.position || summary.currentTeamCode !== entry.teamCode) {
      throw new TeamPlayerContractError(`$.players.${entry.playerId}`, 'lineup and player summary identity mismatch');
    }
  });
  return { metadata, teams, players };
}

function validateUrlText(value: unknown, path: string): string | null {
  return nullableText(value, path);
}

function validatePlayerDetail(value: unknown, path: string): PlayerDetailDto {
  const root = object(value, path);
  const summary = playerSummary(root.summary, `${path}.summary`);
  const snapshot = object(root.snapshotSemantics, `${path}.snapshotSemantics`);
  text(snapshot.snapshotAt, `${path}.snapshotSemantics.snapshotAt`);
  ['ageMeaning', 'contractDaysMeaning', 'prizeMoneyMeaning'].forEach((key) => text(snapshot[key], `${path}.snapshotSemantics.${key}`));
  const personal = object(root.personal, `${path}.personal`);
  text(personal.legalName, `${path}.personal.legalName`);
  if (text(personal.birthDate, `${path}.personal.birthDate`) !== summary.birthDate) throw new TeamPlayerContractError(`${path}.personal.birthDate`, 'summary mismatch');
  integer(personal.ageAtSnapshot, `${path}.personal.ageAtSnapshot`, 0);
  textList(personal.nationality, `${path}.personal.nationality`);
  const contract = object(root.contract, `${path}.contract`);
  if (text(contract.endDate, `${path}.contract.endDate`) !== summary.contractEndDate || text(contract.status, `${path}.contract.status`) !== summary.contractStatus) throw new TeamPlayerContractError(`${path}.contract`, 'summary mismatch');
  integer(contract.daysRemainingAtSnapshot, `${path}.contract.daysRemainingAtSnapshot`);
  text(contract.sourceType, `${path}.contract.sourceType`);
  nullableText(contract.sourceSnapshotAt, `${path}.contract.sourceSnapshotAt`);
  nullableText(contract.checkedAt, `${path}.contract.checkedAt`);
  const career = object(root.career, `${path}.career`);
  text(career.debutDate, `${path}.career.debutDate`);
  numberValue(career.yearsActiveAtSnapshot, `${path}.career.yearsActiveAtSnapshot`);
  text(career.coverage, `${path}.career.coverage`);
  array(career.teamHistory, `${path}.career.teamHistory`).forEach((entry, index) => {
    const history = object(entry, `${path}.career.teamHistory[${index}]`);
    text(history.team, `${path}.career.teamHistory[${index}].team`);
    text(history.from, `${path}.career.teamHistory[${index}].from`);
    nullableText(history.to, `${path}.career.teamHistory[${index}].to`);
    position(history.role, `${path}.career.teamHistory[${index}].role`);
    text(history.datePrecision, `${path}.career.teamHistory[${index}].datePrecision`);
  });
  const honors = object(root.honors, `${path}.honors`);
  text(honors.coverage, `${path}.honors.coverage`);
  array(honors.teamAchievements, `${path}.honors.teamAchievements`).forEach((entry, index) => {
    const item = object(entry, `${path}.honors.teamAchievements[${index}]`);
    ['season', 'competition', 'team', 'result'].forEach((key) => text(item[key], `${path}.honors.teamAchievements[${index}].${key}`));
    validateUrlText(item.sourceUrl, `${path}.honors.teamAchievements[${index}].sourceUrl`);
  });
  array(honors.individualAwards, `${path}.honors.individualAwards`).forEach((entry, index) => {
    const item = object(entry, `${path}.honors.individualAwards[${index}]`);
    ['season', 'award', 'competition'].forEach((key) => text(item[key], `${path}.honors.individualAwards[${index}].${key}`));
    validateUrlText(item.sourceUrl, `${path}.honors.individualAwards[${index}].sourceUrl`);
  });
  const prize = object(root.careerPrizeMoney, `${path}.careerPrizeMoney`);
  numberValue(prize.amountUsd, `${path}.careerPrizeMoney.amountUsd`);
  ['currency', 'status', 'sourceType', 'meaning'].forEach((key) => text(prize[key], `${path}.careerPrizeMoney.${key}`));
  nullableText(prize.checkedAt, `${path}.careerPrizeMoney.checkedAt`);
  const quality = object(root.dataQuality, `${path}.dataQuality`);
  ['personal', 'contract', 'career', 'honors', 'prizeMoney'].forEach((key) => text(quality[key], `${path}.dataQuality.${key}`));
  const ratings = object(root.ratings, `${path}.ratings`);
  const ratingMin = integer(ratings.scaleMin, `${path}.ratings.scaleMin`);
  const ratingMax = integer(ratings.scaleMax, `${path}.ratings.scaleMax`);
  if (ratingMin !== 1 || ratingMax !== 20) throw new TeamPlayerContractError(`${path}.ratings`, '1..20 scale required');
  text(ratings.resourceVersion, `${path}.ratings.resourceVersion`);
  const attributes = array(ratings.attributes, `${path}.ratings.attributes`);
  if (attributes.length !== 12) throw new TeamPlayerContractError(`${path}.ratings.attributes`, 'exactly 12 authored attributes required');
  const ratingKeys: string[] = [];
  attributes.forEach((entry, index) => {
    const item = object(entry, `${path}.ratings.attributes[${index}]`);
    ratingKeys.push(text(item.key, `${path}.ratings.attributes[${index}].key`));
    text(item.skill, `${path}.ratings.attributes[${index}].skill`);
    text(item.displayNameKo, `${path}.ratings.attributes[${index}].displayNameKo`);
    integer(item.value, `${path}.ratings.attributes[${index}].value`, ratingMin, ratingMax);
  });
  unique(ratingKeys, `${path}.ratings.attributes.key`);
  const proficiency = object(root.championProficiency, `${path}.championProficiency`);
  const proficiencyMin = integer(proficiency.scaleMin, `${path}.championProficiency.scaleMin`);
  const proficiencyMax = integer(proficiency.scaleMax, `${path}.championProficiency.scaleMax`);
  integer(proficiency.neutralFallback, `${path}.championProficiency.neutralFallback`, proficiencyMin, proficiencyMax);
  if (!bool(proficiency.sparseOverridesOnly, `${path}.championProficiency.sparseOverridesOnly`)) throw new TeamPlayerContractError(`${path}.championProficiency.sparseOverridesOnly`, 'sparse entries required');
  text(proficiency.omittedLegalRoleBehavior, `${path}.championProficiency.omittedLegalRoleBehavior`);
  text(proficiency.resourceVersion, `${path}.championProficiency.resourceVersion`);
  const entries = array(proficiency.authoredEntries, `${path}.championProficiency.authoredEntries`);
  if (integer(proficiency.authoredEntryCount, `${path}.championProficiency.authoredEntryCount`, 0) !== entries.length) throw new TeamPlayerContractError(`${path}.championProficiency.authoredEntryCount`, 'rendered entry count mismatch');
  const parsedEntries: ChampionProficiencyDto[] = [];
  entries.forEach((entry, index) => {
    const item = object(entry, `${path}.championProficiency.authoredEntries[${index}]`);
    const parsed = {
      championId: text(item.championId, `${path}.championProficiency.authoredEntries[${index}].championId`),
      displayNameKo: text(item.displayNameKo, `${path}.championProficiency.authoredEntries[${index}].displayNameKo`),
      displayNameEn: text(item.displayNameEn, `${path}.championProficiency.authoredEntries[${index}].displayNameEn`),
      portraitUrl: text(item.portraitUrl, `${path}.championProficiency.authoredEntries[${index}].portraitUrl`),
      position: position(item.position, `${path}.championProficiency.authoredEntries[${index}].position`),
      value: integer(item.value, `${path}.championProficiency.authoredEntries[${index}].value`, proficiencyMin, proficiencyMax),
    };
    if (parsed.position !== summary.position) throw new TeamPlayerContractError(`${path}.championProficiency.authoredEntries[${index}].position`, 'player position mismatch');
    parsedEntries.push(parsed);
  });
  unique(parsedEntries.map((entry) => entry.championId), `${path}.championProficiency.authoredEntries.championId`);
  parsedEntries.forEach((entry, index) => {
    if (index === 0) return;
    const previous = parsedEntries[index - 1];
    if (previous.value < entry.value || (previous.value === entry.value && previous.championId.localeCompare(entry.championId) > 0)) throw new TeamPlayerContractError(`${path}.championProficiency.authoredEntries`, 'canonical value/id order required');
  });
  array(root.sources, `${path}.sources`).forEach((entry, index) => {
    const item = object(entry, `${path}.sources[${index}]`);
    text(item.type, `${path}.sources[${index}].type`);
    nullableText(item.path, `${path}.sources[${index}].path`);
    validateUrlText(item.url, `${path}.sources[${index}].url`);
    nullableText(item.checkedAt, `${path}.sources[${index}].checkedAt`);
    nullableText(item.sourceSnapshotAt, `${path}.sources[${index}].sourceSnapshotAt`);
  });
  return value as PlayerDetailDto;
}

export function validatePlayerResponse(value: unknown, expectedPlayer: PlayerSummaryDto, expectedCatalog: CatalogMetadataDto): PlayerResponseDto {
  const root = object(value, '$');
  exact(root.schemaVersion, TEAM_PLAYER_SCHEMAS.player, '$.schemaVersion');
  exact(root.leagueCode, 'LCK', '$.leagueCode');
  const responseCatalog = catalog(root.catalog, '$.catalog');
  if (catalogIdentity(responseCatalog) !== catalogIdentity(expectedCatalog)) throw new TeamPlayerContractError('$.catalog', 'detail response comes from a different catalog generation');
  const detail = validatePlayerDetail(root.player, '$.player');
  if (detail.summary.playerId !== expectedPlayer.playerId || detail.summary.currentTeamCode !== expectedPlayer.currentTeamCode || detail.summary.position !== expectedPlayer.position || detail.summary.nickname !== expectedPlayer.nickname) {
    throw new TeamPlayerContractError('$.player.summary', 'selected player identity mismatch');
  }
  return value as PlayerResponseDto;
}

export function validateErrorResponse(value: unknown): TeamPlayerErrorDto {
  const root = object(value, '$');
  exact(root.schemaVersion, TEAM_PLAYER_SCHEMAS.error, '$.schemaVersion');
  text(root.code, '$.code');
  nullableText(root.field, '$.field');
  text(root.message, '$.message');
  return value as TeamPlayerErrorDto;
}

export const TEAM_PLAYER_SCHEMAS = {
  metadata: 'TEAM_PLAYER_INFORMATION_METADATA_V1',
  teams: 'TEAM_PLAYER_INFORMATION_TEAMS_V1',
  players: 'TEAM_PLAYER_INFORMATION_PLAYERS_V1',
  player: 'TEAM_PLAYER_INFORMATION_PLAYER_V1',
  error: 'TEAM_PLAYER_INFORMATION_API_ERROR_V1',
} as const;

export const LCK_POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'] as const;
export type LckPosition = typeof LCK_POSITIONS[number];

export interface ResourceMetadataDto {
  role: string;
  version: string;
  rawSha256: string;
  snapshotAt: string | null;
  researchAsOf: string | null;
  dataCutoff: string | null;
}

export interface CatalogMetadataDto {
  catalogSchemaVersion: string;
  catalogVersion: string;
  catalogHashAlgorithm: string;
  catalogHash: string;
  championPoolVersion: string;
  sourceResources: ResourceMetadataDto[];
}

export interface CatalogCountsDto {
  teams: number;
  players: number;
  uniquePlayerIds: number;
  teamHistoryRows: number;
  teamAchievementRows: number;
  individualAwardRows: number;
  sourceRows: number;
  authoredProficiencies: number;
  neutralFallbackKeys: number;
  playersWithMajorHonorsListed: number;
}

export interface TeamPlayerMetadataDto {
  schemaVersion: typeof TEAM_PLAYER_SCHEMAS.metadata;
  leagueCode: 'LCK';
  catalog: CatalogMetadataDto;
  counts: CatalogCountsDto;
  semantics: { contract: string; career: string; honors: string; prizeMoney: string; age: string };
  limitations: {
    currentLckOnly: boolean;
    startersOnly: boolean;
    substitutesIncluded: boolean;
    salaryIncluded: boolean;
    marketValueIncluded: boolean;
    overallRatingIncluded: boolean;
    mutableCareerStateIncluded: boolean;
    affectsGameplayOrRandomIdentity: boolean;
  };
}

export interface LineupPlayerDto { playerId: string; nickname: string; position: LckPosition }
export interface TeamSummaryDto { teamCode: string; starterCount: number; lineup: LineupPlayerDto[] }

export interface TeamsResponseDto {
  schemaVersion: typeof TEAM_PLAYER_SCHEMAS.teams;
  leagueCode: 'LCK';
  catalog: CatalogMetadataDto;
  teams: TeamSummaryDto[];
}

export interface PlayerSummaryDto {
  playerId: string;
  nickname: string;
  currentTeamCode: string;
  position: LckPosition;
  nationality: string[];
  birthDate: string;
  contractEndDate: string;
  contractStatus: string;
}

export interface PlayersResponseDto {
  schemaVersion: typeof TEAM_PLAYER_SCHEMAS.players;
  leagueCode: 'LCK';
  catalog: CatalogMetadataDto;
  filters: { teamCode: string | null; position: LckPosition | null };
  players: PlayerSummaryDto[];
}

export interface RatingAttributeDto { key: string; skill: string; displayNameKo: string; value: number }
export interface ChampionProficiencyDto {
  championId: string;
  displayNameKo: string;
  displayNameEn: string;
  portraitUrl: string;
  position: LckPosition;
  value: number;
}
export interface TeamHistoryDto { team: string; from: string; to: string | null; role: LckPosition; datePrecision: string }
export interface TeamAchievementDto { season: string; competition: string; team: string; result: string; sourceUrl: string | null }
export interface IndividualAwardDto { season: string; award: string; competition: string; sourceUrl: string | null }
export interface SourceCitationDto { type: string; path: string | null; url: string | null; checkedAt: string | null; sourceSnapshotAt: string | null }

export interface PlayerDetailDto {
  summary: PlayerSummaryDto;
  snapshotSemantics: { snapshotAt: string; ageMeaning: string; contractDaysMeaning: string; prizeMoneyMeaning: string };
  personal: { legalName: string; birthDate: string; ageAtSnapshot: number; nationality: string[] };
  contract: { endDate: string; daysRemainingAtSnapshot: number; status: string; sourceType: string; sourceSnapshotAt: string | null; checkedAt: string | null };
  career: { debutDate: string; yearsActiveAtSnapshot: number; teamHistory: TeamHistoryDto[]; coverage: string };
  honors: { teamAchievements: TeamAchievementDto[]; individualAwards: IndividualAwardDto[]; coverage: string };
  careerPrizeMoney: { amountUsd: number; currency: string; status: string; sourceType: string; checkedAt: string | null; meaning: string };
  dataQuality: { personal: string; contract: string; career: string; honors: string; prizeMoney: string };
  ratings: { scaleMin: number; scaleMax: number; resourceVersion: string; attributes: RatingAttributeDto[] };
  championProficiency: {
    scaleMin: number;
    scaleMax: number;
    neutralFallback: number;
    sparseOverridesOnly: boolean;
    omittedLegalRoleBehavior: string;
    resourceVersion: string;
    authoredEntryCount: number;
    authoredEntries: ChampionProficiencyDto[];
  };
  sources: SourceCitationDto[];
}

export interface PlayerResponseDto {
  schemaVersion: typeof TEAM_PLAYER_SCHEMAS.player;
  leagueCode: 'LCK';
  catalog: CatalogMetadataDto;
  player: PlayerDetailDto;
}

export interface TeamPlayerErrorDto {
  schemaVersion: typeof TEAM_PLAYER_SCHEMAS.error;
  code: string;
  field: string | null;
  message: string;
}

export interface TeamPlayerWorkspaceDto {
  metadata: TeamPlayerMetadataDto;
  teams: TeamsResponseDto;
  players: PlayersResponseDto;
}

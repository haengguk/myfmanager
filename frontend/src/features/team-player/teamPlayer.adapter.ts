import type {
  ChampionProficiencyDto, IndividualAwardDto, PlayerResponseDto, RatingAttributeDto,
  SourceCitationDto, TeamAchievementDto, TeamHistoryDto,
} from './api/teamPlayerApi.types';

export interface LinkViewModel { label: string; href: string | null }
export interface TeamHistoryViewModel extends TeamHistoryDto { toLabel: string }
export interface TeamAchievementViewModel extends TeamAchievementDto { source: LinkViewModel }
export interface IndividualAwardViewModel extends IndividualAwardDto { source: LinkViewModel }
export interface SourceCitationViewModel extends SourceCitationDto { link: LinkViewModel }

export interface PlayerProfileViewModel {
  playerId: string;
  nickname: string;
  initials: string;
  currentTeamCode: string;
  position: string;
  legalName: string;
  nationality: string;
  birthDate: string;
  ageAtSnapshot: number;
  snapshotAt: string;
  contractEndDate: string;
  contractStatus: string;
  contractDaysRemaining: number;
  contractSourceType: string;
  contractSourceSnapshotAt: string | null;
  contractCheckedAt: string | null;
  debutDate: string;
  yearsActiveAtSnapshot: number;
  careerCoverage: string;
  teamHistory: TeamHistoryViewModel[];
  teamAchievements: TeamAchievementViewModel[];
  individualAwards: IndividualAwardViewModel[];
  honorsCoverage: string;
  prizeMoneyAmountUsd: number;
  prizeMoneyCurrency: string;
  prizeMoneyStatus: string;
  prizeMoneySourceType: string;
  prizeMoneyCheckedAt: string | null;
  prizeMoneyMeaning: string;
  ratings: RatingAttributeDto[];
  ratingScale: { min: number; max: number; resourceVersion: string };
  proficiencies: ChampionProficiencyDto[];
  proficiency: { min: number; max: number; neutralFallback: number; authoredEntryCount: number; omittedLegalRoleBehavior: string; resourceVersion: string };
  dataQuality: PlayerResponseDto['player']['dataQuality'];
  sources: SourceCitationViewModel[];
}

export function safeHttpUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null;
  } catch { return null; }
}

function sourceLink(url: string | null): LinkViewModel {
  return { label: url ?? '링크 없음', href: safeHttpUrl(url) };
}

export function createPlayerProfile(response: PlayerResponseDto): PlayerProfileViewModel {
  const player = response.player;
  return {
    playerId: player.summary.playerId,
    nickname: player.summary.nickname,
    initials: player.summary.nickname.replace(/\s/g, '').slice(0, 2).toUpperCase(),
    currentTeamCode: player.summary.currentTeamCode,
    position: player.summary.position,
    legalName: player.personal.legalName,
    nationality: player.personal.nationality.join(' · '),
    birthDate: player.personal.birthDate,
    ageAtSnapshot: player.personal.ageAtSnapshot,
    snapshotAt: player.snapshotSemantics.snapshotAt,
    contractEndDate: player.contract.endDate,
    contractStatus: player.contract.status,
    contractDaysRemaining: player.contract.daysRemainingAtSnapshot,
    contractSourceType: player.contract.sourceType,
    contractSourceSnapshotAt: player.contract.sourceSnapshotAt,
    contractCheckedAt: player.contract.checkedAt,
    debutDate: player.career.debutDate,
    yearsActiveAtSnapshot: player.career.yearsActiveAtSnapshot,
    careerCoverage: player.career.coverage,
    teamHistory: player.career.teamHistory.map((entry) => ({ ...entry, toLabel: entry.to ?? '현재' })),
    teamAchievements: player.honors.teamAchievements.map((entry) => ({ ...entry, source: sourceLink(entry.sourceUrl) })),
    individualAwards: player.honors.individualAwards.map((entry) => ({ ...entry, source: sourceLink(entry.sourceUrl) })),
    honorsCoverage: player.honors.coverage,
    prizeMoneyAmountUsd: player.careerPrizeMoney.amountUsd,
    prizeMoneyCurrency: player.careerPrizeMoney.currency,
    prizeMoneyStatus: player.careerPrizeMoney.status,
    prizeMoneySourceType: player.careerPrizeMoney.sourceType,
    prizeMoneyCheckedAt: player.careerPrizeMoney.checkedAt,
    prizeMoneyMeaning: player.careerPrizeMoney.meaning,
    ratings: player.ratings.attributes,
    ratingScale: { min: player.ratings.scaleMin, max: player.ratings.scaleMax, resourceVersion: player.ratings.resourceVersion },
    proficiencies: player.championProficiency.authoredEntries,
    proficiency: {
      min: player.championProficiency.scaleMin,
      max: player.championProficiency.scaleMax,
      neutralFallback: player.championProficiency.neutralFallback,
      authoredEntryCount: player.championProficiency.authoredEntryCount,
      omittedLegalRoleBehavior: player.championProficiency.omittedLegalRoleBehavior,
      resourceVersion: player.championProficiency.resourceVersion,
    },
    dataQuality: player.dataQuality,
    sources: player.sources.map((entry) => ({ ...entry, link: sourceLink(entry.url) })),
  };
}

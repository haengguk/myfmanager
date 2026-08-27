import type { MatchSessionPerformance, MatchSetupOptionsViewModel, MatchSetupSelection } from '../matchSession.types';
import type { Position } from '../realMatch.contract';
import type {
  PlayerDraftChampionPresentationDto, PlayerDraftSessionResponseDto, PlayerDraftUnavailableReason,
} from './api/playerDraftApi.types';

export interface PlayerDraftChampionCatalogEntry {
  champion: PlayerDraftChampionPresentationDto;
  feasibleRoles: readonly Position[];
  unavailableReason: PlayerDraftUnavailableReason | null;
}

export interface PlayerDraftScreenState {
  session: PlayerDraftSessionResponseDto;
  options: MatchSetupOptionsViewModel;
  selection: MatchSetupSelection;
  championsById: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
}

export interface PlayerDraftSimulationPerformance extends Omit<MatchSessionPerformance, 'normalizationMs'> {}

export const playerDraftUnavailableReasonLabels: Readonly<Record<PlayerDraftUnavailableReason, string>> = {
  HARD_FEARLESS_EXCLUDED: '이전 경기 사용으로 선택할 수 없습니다.',
  ALREADY_BANNED: '이미 밴된 챔피언입니다.',
  ALREADY_PICKED: '이미 선택된 챔피언입니다.',
  PARTIAL_ROLE_ASSIGNMENT_INFEASIBLE: '현재 조합에서는 포지션에 배치할 수 없습니다.',
  FUTURE_ROLE_COMPLETION_INFEASIBLE: '남은 픽으로 포지션 구성을 완성할 수 없습니다.',
  BAN_WOULD_BREAK_FUTURE_COMPLETION: '이 밴을 선택하면 남은 포지션 구성이 불가능해집니다.',
};

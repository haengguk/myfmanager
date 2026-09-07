import type { RosterPlayer } from '../career/api/careerRoster.contract';

export function currentAbility(ratings: Record<string, number>): number {
  const values = Object.values(ratings);
  if (values.length !== 12 || values.some(v => !Number.isInteger(v) || v < 1 || v > 20)) throw new Error('능력치 12개의 정수 값이 필요합니다.');
  return Math.round(1 + (values.reduce((sum, v) => sum + v, 0) - 12) * 199 / 228);
}
export function potentialAbility(player: Pick<RosterPlayer, 'detailsJson'>): number | null {
  const value: unknown = JSON.parse(player.detailsJson).abilityMetadata?.potentialAbility;
  return typeof value === 'number' && Number.isInteger(value) && value >= 1 && value <= 200 ? value : null;
}
export const SKILL_LABELS: Record<string, string> = {
  MECHANICS: '메카닉', DECISION_MAKING: '판단력', MAP_AWARENESS: '맵 이해', POSITIONING: '포지셔닝',
  COMBAT_EXECUTION: '교전 수행', CONSISTENCY: '꾸준함', CS_ACQUISITION: 'CS 수급', TRADING: '딜 교환',
  FARMING: '미니언 수급', PRIORITY_CONVERSION: '주도권 활용', SIDE_LANE: '사이드 운영',
  PATHING: '동선 설계', JUNGLE_RESOURCE_MANAGEMENT: '정글 자원 관리', ENEMY_JUNGLE_TRACKING: '상대 동선 추적',
  LANE_INTERVENTION: '라인 개입', OBJECTIVE_DECISION: '목표물 판단', OBJECTIVE_SECURE: '목표물 마무리',
  VISION_CONTROL: '시야 장악', LANE_SUPPORT: '라인 보조', ROTATION_PLANNING: '합류 설계', ENGAGE_EXECUTION: '교전 개시', ALLY_PROTECTION: '아군 보호', AREA_SETUP: '지역 선점',
  WAVE_MANAGEMENT: '웨이브 관리', LANE_PRESSURE: '라인 압박', INITIATIVE_CONVERSION: '주도권 활용', SIDE_LANE_MANAGEMENT: '사이드 운영',
};

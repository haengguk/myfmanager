import type { CareerCalendarViewDto } from './api/careerApi.types';
import type { Negotiation } from './api/careerManagement.contract';
import type { RecordSeries } from './api/careerRecords';
import type { InboxLink } from './api/careerInbox';

export function calendarMatchLink(calendar: CareerCalendarViewDto): InboxLink | null {
  const f = calendar.competition.nextFixture;
  if (!f || f.executionMode !== 'PLAYER_CONTROLLED') return null;
  return { panel: 'MATCH', sourceId: f.fixtureId, seriesId: f.seriesId, playerId: null, competition: f.competitionId, seasonYear: calendar.activeCalendarSeasonYear, positions: [], current: true, matchState: f.bindingHash ? 'IN_PROGRESS' : 'UNSTARTED' };
}
export function nextPlayLabel(calendar: CareerCalendarViewDto | null, historical: boolean, running: boolean): string {
  if (historical) return '현재 시즌 업무로 돌아가기';
  if (!calendar) return '진행 상태 확인 중';
  if (running) return '서버 진행 상태 보기';
  const command = calendar.competition.allowedCommands[0];
  if (command === 'START_PLAYER_COMPETITION_SERIES') return '관리 경기 준비 확인';
  if (command === 'RESUME_PLAYER_COMPETITION_SERIES') return '진행 중인 관리 경기 보기';
  if (command === 'DISPATCH_AUTO_COMPETITION_FIXTURE') return '선행 Auto 경기 확인';
  if (command === 'RECONCILE_COMPETITION_FIXTURE' || calendar.activePendingAdvance) return '처리 결과 확인';
  return calendar.allowedAdvanceModes.length ? '날짜·연속 진행 선택' : '현재 업무와 진행 조건 확인';
}
export function tradeProgress(t: Pick<Negotiation, 'status' | 'sellerAgreed' | 'buyerAgreed' | 'decisionDate'>): string {
  if (t.status === 'COMPLETED') return '효력일 적용 완료';
  if (t.status === 'AGREED') return '구단·선수 합의 완료 · 효력일 적용 대기';
  if (t.status === 'PLAYER_PENDING') return `양 구단 동의 완료 · ${t.decisionDate} 선수 판단 대기`;
  if (['CLUB_PENDING', 'CLUB_COUNTER'].includes(t.status)) return `조건 접수 · 보내는 구단 ${t.sellerAgreed ? '동의' : '검토 중'} · 받는 구단 ${t.buyerAgreed ? '동의' : '검토 중'}`;
  return '협상 종료 · 저장된 처리 결과';
}
export function approvedSeriesScore(record: RecordSeries, career: string, series: string): string {
  if (record.careerId !== career || record.seriesId !== series || !Number.isInteger(record.seasonYear)) throw new Error('복귀한 경기와 승인 기록의 저장 범위가 다릅니다.');
  if (!record.games.length) return '세트 스코어 미수집';
  if (record.games.some(g => ![record.firstTeam, record.secondTeam].includes(g.winner))) throw new Error('승인 경기의 승리 팀을 확인할 수 없습니다.');
  return `${record.games.filter(g => g.winner === record.firstTeam).length}–${record.games.filter(g => g.winner === record.secondTeam).length}`;
}
export function focusPlayTarget(selector: string): void {
  const target = document.querySelector<HTMLElement>(selector); if (!target) return;
  if (target instanceof HTMLDetailsElement) target.open = true;
  target.tabIndex = -1; target.focus({ preventScroll: true }); target.scrollIntoView({ block: 'start', behavior: 'smooth' });
}

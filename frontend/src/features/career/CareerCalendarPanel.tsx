import type { CareerAdvanceMode, CareerCalendarViewDto } from './api/careerApi.types';

const STATUS_COPY: Readonly<Record<string, string>> = {
  OFFICIAL_CONFIRMED: '공식 확정', OFFICIAL_BY_NO_CHANGE_STATEMENT: '공식 유지', OFFICIAL_PARTIAL: '공식 일부', DERIVED: '계산 파생', OFFICIAL_PENDING: '공식 발표 대기', SUPERSEDED: '대체됨',
};

function range(start: string, end: string): string { return start === end ? start : `${start} — ${end}`; }

export function CareerCalendarPanel({ calendar, loading, pending, error, onAdvance, onRefresh }: {
  calendar: CareerCalendarViewDto | null;
  loading: boolean;
  pending: boolean;
  error: string | null;
  onAdvance: (mode: CareerAdvanceMode) => void;
  onRefresh: () => void;
}) {
  if (loading) return <section className="ca-calendar ca-calendar--loading" aria-label="Career 캘린더" aria-busy="true"><span>CAREER TIME</span><strong>캘린더 확인 중…</strong></section>;
  if (error || !calendar) return <section className="ca-calendar ca-calendar--error" aria-label="Career 캘린더"><span>CAREER TIME</span><strong>날짜 진행을 사용할 수 없습니다</strong><p>{error ?? '서버 캘린더 응답이 없습니다.'}</p><button type="button" className="lm-text-button" onClick={onRefresh}>캘린더 다시 확인</button></section>;
  const current = calendar.currentEvent;
  return <section className="ca-calendar" aria-label="Career 캘린더">
    <header>
      <div><span>CAREER TIME / {calendar.activeCalendarSeasonYear}</span><strong><time dateTime={calendar.currentDate}>{calendar.currentDate}</time></strong><small>{current ? current.displayNameKo : '첫 공식 일정 이전'}{calendar.currentStage ? ` · ${calendar.currentStage.displayNameKo}` : calendar.nextStage ? ` · 다음 단계 ${calendar.nextStage.displayNameKo}` : ''}</small></div>
      <div className="ca-calendar__revision"><span>REV</span><strong>{calendar.calendarRevision}</strong><small>{calendar.projectionStatus === 'GAME_PROJECTED_FROM_2026_TEMPLATE' ? '2026 기준 투영' : '기준 연도'}</small></div>
    </header>
    {calendar.blockingReason ? <div className={`ca-calendar__stop is-${calendar.blockingReason.toLowerCase()}`} role="alert"><strong>{calendar.blockingReason}</strong><span>{calendar.blockingReason === 'MANAGED_FIXTURE_REQUIRED' ? '관리 팀 경기를 League에서 완료해야 날짜가 지나갑니다.' : calendar.blockingReason === 'AUTO_FIXTURES_PENDING' ? '저장된 Auto 경기와 outbox 완료를 기다립니다.' : calendar.blockingReason === 'SEASON_ROLLOVER_REQUIRED' ? '새 시즌 생성 없이 여기서 멈췄습니다.' : 'League 상태 확인이 필요합니다.'}</span></div> : null}
    <div className="ca-calendar__controls">
      <button type="button" className="lm-secondary-button" disabled={pending || !calendar.allowedAdvanceModes.includes('ADVANCE_ONE_DAY')} onClick={() => onAdvance('ADVANCE_ONE_DAY')}>{pending ? '진행 확인 중…' : '하루 진행'}</button>
      <button type="button" className="lm-primary-button" disabled={pending || !calendar.allowedAdvanceModes.includes('ADVANCE_TO_NEXT_EVENT')} onClick={() => onAdvance('ADVANCE_TO_NEXT_EVENT')}>다음 일정까지 진행</button>
      <button type="button" className="lm-text-button" disabled={pending} onClick={onRefresh}>새로고침</button>
    </div>
    <div className="ca-calendar__grid">
      <div className="ca-calendar__events"><span>UPCOMING / OFFICIAL + PROJECTED</span><ol>{calendar.upcomingEvents.slice(0, 6).map((event) => <li key={event.eventId}><time>{range(event.startDate, event.endDate)}</time><div><strong>{event.displayNameKo}</strong><small>{STATUS_COPY[event.officialStatus] ?? event.officialStatus} · {event.executionStatus === 'LINKED_EXISTING_LEAGUE_FIXTURES' ? 'League 연동' : '형식만 정의됨'}</small></div></li>)}</ol></div>
      <div className="ca-calendar__fixtures"><span>R1–R2 FIXTURE OVERLAY</span>{calendar.upcomingFixtures.length ? <ol>{calendar.upcomingFixtures.slice(0, 4).map((fixture) => <li key={fixture.fixtureId}><time>{fixture.date}</time><strong>R{fixture.roundNumber} · {fixture.firstTeamCode} vs {fixture.secondTeamCode}</strong><small>{fixture.executionMode === 'PLAYER_CONTROLLED' ? '관리 경기' : fixture.jobStatus ?? fixture.lifecycleStatus}</small></li>)}</ol> : <p>남은 R1–R2 fixture가 없습니다.</p>}<footer><span>공식 미정 {calendar.pendingOfficialFields.length}</span><span>KeSPA Cup · {calendar.sourceDataNotes[0]?.status ?? 'SOURCE_DATA_NOT_PRESENT'}</span></footer></div>
    </div>
  </section>;
}

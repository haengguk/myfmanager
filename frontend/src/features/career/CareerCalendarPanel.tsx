import type { CareerAdvanceMode, CareerCalendarViewDto } from './api/careerApi.types';

const STATUS_COPY: Readonly<Record<string, string>> = {
  OFFICIAL_CONFIRMED: '공식 확정', OFFICIAL_BY_NO_CHANGE_STATEMENT: '공식 유지', OFFICIAL_PARTIAL: '공식 일부', DERIVED: '계산 파생', OFFICIAL_PENDING: '공식 발표 대기', SUPERSEDED: '대체됨',
};
const COMPETITION_COPY: Readonly<Record<string, string>> = {
  LCK_CUP: 'LCK Cup', LCK_REGULAR_R1_R2: 'LCK R1–R2', LCK_ROAD_TO_MSI: 'Road to MSI', LCK_REGULAR_R3_R4: 'LCK R3–R4', LCK_PLAY_IN: 'LCK 플레이인', LCK_PLAYOFFS: 'LCK 플레이오프', FIRST_STAND: 'First Stand', MSI: 'MSI', EWC_LOL: 'EWC', WORLDS: 'Worlds', ASIAN_GAMES_LOL_RELEASE: '아시안게임 차출 기간',
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
  const reconciling = pending || calendar.activePendingAdvance !== null;
  const competition = calendar.competition.currentCompetition ?? calendar.competition.nextCompetition;
  return <section className="ca-calendar" aria-label="Career 캘린더">
    <header>
      <div><span>CAREER TIME / {calendar.activeCalendarSeasonYear}</span><strong><time dateTime={calendar.currentDate}>{calendar.currentDate}</time></strong><small>{current ? current.displayNameKo : '첫 공식 일정 이전'}{calendar.currentStage ? ` · ${calendar.currentStage.displayNameKo}` : calendar.nextStage ? ` · 다음 단계 ${calendar.nextStage.displayNameKo}` : ''}</small></div>
      <div className="ca-calendar__revision"><span>REV</span><strong>{calendar.calendarRevision}</strong><small>{calendar.projectionStatus === 'GAME_PROJECTED_FROM_2026_TEMPLATE' ? '2026 기준 투영' : '기준 연도'}</small></div>
    </header>
    {calendar.activePendingAdvance ? <div className="ca-calendar__stop is-auto_fixtures_pending" role="status" aria-live="polite"><strong>서버 작업 복구 중</strong><span>{calendar.activePendingAdvance.mode === 'ADVANCE_ONE_DAY' ? '하루 진행' : '다음 일정 진행'} · revision {calendar.activePendingAdvance.expectedCalendarRevision} · 기존 command를 재사용합니다.</span></div> : calendar.blockingReason ? <div className={`ca-calendar__stop is-${calendar.blockingReason.toLowerCase()}`} role="alert"><strong>{calendar.blockingReason}</strong><span>{calendar.blockingReason === 'MANAGED_FIXTURE_REQUIRED' ? '관리 팀 경기를 League에서 완료해야 날짜가 지나갑니다.' : calendar.blockingReason === 'AUTO_FIXTURES_PENDING' ? '저장된 Auto 경기와 outbox 완료를 기다립니다.' : calendar.blockingReason === 'SEASON_PAUSED' ? 'League 시즌이 일시 정지되어 날짜를 진행하지 않습니다.' : calendar.blockingReason === 'SEASON_CANCELLED' ? '취소된 시즌에서는 날짜를 진행하지 않습니다.' : calendar.blockingReason === 'COMPETITION_TRANSITION_REQUIRED' ? '검증된 R1–R2 완료 결과가 아직 다음 대회 입력으로 봉인되지 않았습니다.' : calendar.blockingReason === 'MANAGED_COMPETITION_FIXTURE_REQUIRED' ? '관리 팀의 국내 대회 Series 완료가 필요합니다.' : calendar.blockingReason === 'AUTO_COMPETITION_FIXTURE_REQUIRED' ? '국내 대회 Auto Series 실행이 필요합니다.' : calendar.blockingReason === 'LCK_PLAYOFF_BRACKET_RULE_SOURCE_INCOMPLETE' ? '10경기 날짜는 확인됐지만 승자·패자 routing 근거가 부족해 대진을 생성하지 않습니다.' : calendar.blockingReason === 'SEASON_NOT_READY' ? 'League 시즌이 아직 실행 준비 상태가 아닙니다.' : calendar.blockingReason === 'SEASON_ROLLOVER_REQUIRED' ? '새 시즌 생성 없이 여기서 멈췄습니다.' : '서버 권위 상태 확인이 필요합니다.'}</span></div> : null}
    {competition ? <div className="ca-calendar__competition" aria-label="Career 대회 상태">
      <span>{calendar.competition.currentCompetition ? 'CURRENT COMPETITION' : 'NEXT COMPETITION'}</span>
      <strong>{COMPETITION_COPY[competition.competitionId] ?? competition.competitionId}</strong>
      <small>{competition.stageId} · {competition.lifecycleStatus} · {competition.completedFixtures}/{competition.totalFixtures}{competition.blockingReason ? ` · ${competition.blockingReason}` : ''}</small>
      {calendar.competition.nextFixture ? <p><time>{calendar.competition.nextFixture.date}</time><b>{COMPETITION_COPY[calendar.competition.nextFixture.competitionId] ?? calendar.competition.nextFixture.competitionId} {calendar.competition.nextFixture.matchId}</b><em>{calendar.competition.nextFixture.firstTeamCode ?? 'TBD'} vs {calendar.competition.nextFixture.secondTeamCode ?? 'TBD'} · {calendar.competition.nextFixture.seriesFormat} · {calendar.competition.nextFixture.executionMode === 'PLAYER_CONTROLLED' ? '관리 경기' : 'Auto'}</em></p> : null}
      {calendar.competition.externalExecutionLimited ? <i>국제대회는 LCK 진출 slot만 보존하며 경기는 실행하지 않습니다.</i> : null}
    </div> : null}
    <div className="ca-calendar__controls">
      <button type="button" className="lm-secondary-button" disabled={reconciling || !calendar.allowedAdvanceModes.includes('ADVANCE_ONE_DAY')} onClick={() => onAdvance('ADVANCE_ONE_DAY')}>{reconciling ? '진행 확인 중…' : '하루 진행'}</button>
      <button type="button" className="lm-primary-button" disabled={reconciling || !calendar.allowedAdvanceModes.includes('ADVANCE_TO_NEXT_EVENT')} onClick={() => onAdvance('ADVANCE_TO_NEXT_EVENT')}>다음 일정까지 진행</button>
      <button type="button" className="lm-text-button" disabled={reconciling} onClick={onRefresh}>새로고침</button>
    </div>
    <div className="ca-calendar__grid">
      <div className="ca-calendar__events"><span>UPCOMING / OFFICIAL + PROJECTED</span><ol>{calendar.upcomingEvents.slice(0, 6).map((event) => <li key={event.eventId}><time>{range(event.startDate, event.endDate)}</time><div><strong>{event.displayNameKo}</strong><small>{STATUS_COPY[event.officialStatus] ?? event.officialStatus} · {event.executionStatus === 'LINKED_EXISTING_LEAGUE_FIXTURES' ? 'League 연동' : '형식만 정의됨'}</small></div></li>)}</ol></div>
      <div className="ca-calendar__fixtures"><span>R1–R2 FIXTURE OVERLAY</span>{calendar.upcomingFixtures.length ? <ol>{calendar.upcomingFixtures.slice(0, 4).map((fixture) => <li key={fixture.fixtureId}><time>{fixture.date}</time><strong>R{fixture.roundNumber} · {fixture.firstTeamCode} vs {fixture.secondTeamCode}</strong><small>{fixture.executionMode === 'PLAYER_CONTROLLED' ? '관리 경기' : fixture.jobStatus ?? fixture.lifecycleStatus}</small></li>)}</ol> : <p>남은 R1–R2 fixture가 없습니다.</p>}<footer><span>공식 미정 {calendar.pendingOfficialFields.length}</span><span>KeSPA Cup · {calendar.sourceDataNotes[0]?.status ?? 'SOURCE_DATA_NOT_PRESENT'}</span></footer></div>
    </div>
  </section>;
}

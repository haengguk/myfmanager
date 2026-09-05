import type { CareerAdvanceMode, CareerCalendarViewDto } from './api/careerApi.types';

const STATUS_COPY: Readonly<Record<string, string>> = {
  OFFICIAL_CONFIRMED: '공식 확정', OFFICIAL_BY_NO_CHANGE_STATEMENT: '공식 유지', OFFICIAL_PARTIAL: '공식 일부', DERIVED: '계산 파생', OFFICIAL_PENDING: '공식 발표 대기', SUPERSEDED: '대체됨',
};
const COMPETITION_COPY: Readonly<Record<string, string>> = {
  LCK_CUP: 'LCK Cup', LCK_REGULAR_R1_R2: 'LCK R1–R2', LCK_ROAD_TO_MSI: 'Road to MSI', LCK_REGULAR_R3_R4: 'LCK R3–R4', LCK_PLAY_IN: 'LCK 플레이인', LCK_PLAYOFFS: 'LCK 플레이오프', FIRST_STAND: 'First Stand', MSI: 'MSI', EWC_LOL: 'EWC', WORLDS: 'Worlds', ASIAN_GAMES_LOL_RELEASE: '아시안게임 차출 기간', KESPA_CUP: 'KeSPA Cup',
};
const EXECUTION_COPY: Readonly<Record<CareerCalendarViewDto['upcomingEvents'][number]['executionStatus'], string>> = {
  LINKED_EXISTING_LEAGUE_FIXTURES: 'League 연동',
  LINKED_COMPETITION_SERIES_EXECUTION: '대회 Series 연동',
  FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED: '형식만 정의됨',
  EXCLUDED_BY_GAME_POLICY: '게임 정책상 제외',
};

function range(start: string, end: string): string { return start === end ? start : `${start} — ${end}`; }

export function CareerCalendarPanel({ calendar, loading, pending, competitionPending = false, error, onAdvance, onRefresh, onCompetitionAction, onReconcilePending = onRefresh }: {
  calendar: CareerCalendarViewDto | null;
  loading: boolean;
  pending: boolean;
  competitionPending?: boolean;
  error: string | null;
  onAdvance: (mode: CareerAdvanceMode) => void;
  onRefresh: () => void;
  onCompetitionAction?: () => void;
  onReconcilePending?: () => void;
}) {
  if (loading) return <section className="ca-calendar ca-calendar--loading" aria-label="Career 캘린더" aria-busy="true"><span>CAREER TIME</span><strong>캘린더 확인 중…</strong></section>;
  if (!calendar) return <section className="ca-calendar ca-calendar--error" aria-label="Career 캘린더"><span>CAREER TIME</span><strong>날짜 진행을 사용할 수 없습니다</strong><p>{error ?? '서버 캘린더 응답이 없습니다.'}</p><button type="button" className="lm-text-button" onClick={onRefresh}>캘린더 다시 확인</button></section>;
  const current = calendar.currentEvent;
  const reconciling = pending || calendar.activePendingAdvance !== null;
  const competition = calendar.competition.currentCompetition ?? calendar.competition.nextCompetition;
  const competitionCommand = calendar.competition.allowedCommands[0] ?? null;
  const competitionActionLabel = competitionCommand === 'START_PLAYER_COMPETITION_SERIES' ? '관리 Series 시작'
    : competitionCommand === 'RESUME_PLAYER_COMPETITION_SERIES' ? '관리 Series 계속'
      : competitionCommand === 'DISPATCH_AUTO_COMPETITION_FIXTURE' ? 'Auto 경기 실행'
        : competitionCommand === 'RECONCILE_COMPETITION_FIXTURE' ? '대회 결과 확인' : null;
  return <section className="ca-calendar" aria-label="Career 캘린더">
    <header>
      <div><span>CAREER TIME / {calendar.activeCalendarSeasonYear}</span><strong><time dateTime={calendar.currentDate}>{calendar.currentDate}</time></strong><small>{current ? current.displayNameKo : '첫 공식 일정 이전'}{calendar.currentStage ? ` · ${calendar.currentStage.displayNameKo}` : calendar.nextStage ? ` · 다음 단계 ${calendar.nextStage.displayNameKo}` : ''}</small></div>
      <div className="ca-calendar__revision"><span>REV</span><strong>{calendar.calendarRevision}</strong><small>{calendar.projectionStatus === 'GAME_PROJECTED_FROM_2026_TEMPLATE' ? '2026 기준 투영' : '기준 연도'}</small></div>
    </header>
    {calendar.activePendingAdvance ? <div className="ca-calendar__stop is-auto_fixtures_pending" role="status" aria-live="polite"><strong>서버 작업 복구 중</strong><span>{calendar.activePendingAdvance.mode === 'ADVANCE_ONE_DAY' ? '하루 진행' : '다음 일정 진행'} · revision {calendar.activePendingAdvance.expectedCalendarRevision} · 기존 command를 재사용합니다.{error ? ` ${error}` : ''}</span><button type="button" className="lm-text-button" disabled={pending} onClick={onReconcilePending}>상태 다시 확인</button></div> : calendar.blockingReason ? <div className={`ca-calendar__stop is-${calendar.blockingReason.toLowerCase()}`} role="alert"><strong>{calendar.blockingReason}</strong><span>{calendar.blockingReason === 'MANAGED_FIXTURE_REQUIRED' ? '관리 팀 경기를 League에서 완료해야 날짜가 지나갑니다.' : calendar.blockingReason === 'AUTO_FIXTURES_PENDING' ? '저장된 Auto 경기와 outbox 완료를 기다립니다.' : calendar.blockingReason === 'SEASON_PAUSED' ? 'League 시즌이 일시 정지되어 날짜를 진행하지 않습니다.' : calendar.blockingReason === 'SEASON_CANCELLED' ? '취소된 시즌에서는 날짜를 진행하지 않습니다.' : calendar.blockingReason === 'COMPETITION_TRANSITION_REQUIRED' ? '검증된 R1–R2 완료 결과가 아직 다음 대회 입력으로 봉인되지 않았습니다.' : calendar.blockingReason === 'LEGACY_PENDING_RECONCILIATION_REQUIRED' ? '이전 버전 작업은 원본 mode와 revision을 증명할 수 없어 새 날짜 진행을 차단합니다.' : calendar.blockingReason === 'MANAGED_COMPETITION_FIXTURE_REQUIRED' ? '관리 팀의 대회 Series 완료가 필요합니다.' : calendar.blockingReason === 'AUTO_COMPETITION_FIXTURE_REQUIRED' ? '대회 Auto Series 실행이 필요합니다.' : calendar.blockingReason === 'LCK_PLAYOFF_BRACKET_RULE_SOURCE_INCOMPLETE' ? '10경기 날짜는 확인됐지만 승자·패자 routing 근거가 부족해 대진을 생성하지 않습니다.' : calendar.blockingReason === 'SEASON_NOT_READY' ? 'League 시즌이 아직 실행 준비 상태가 아닙니다.' : calendar.blockingReason === 'SEASON_ROLLOVER_REQUIRED' ? '새 시즌 생성 없이 여기서 멈췄습니다.' : '서버 권위 상태 확인이 필요합니다.'}</span></div> : null}
    {competition ? <div className="ca-calendar__competition" aria-label="Career 대회 상태">
      <span>{calendar.competition.currentCompetition ? 'CURRENT COMPETITION' : 'NEXT COMPETITION'}</span>
      <strong>{COMPETITION_COPY[competition.competitionId] ?? competition.competitionId}</strong>
      <small>{competition.stageId} · {competition.lifecycleStatus} · {competition.completedFixtures}/{competition.totalFixtures}{competition.blockingReason ? ` · ${competition.blockingReason}` : ''}</small>
      {calendar.competition.nextFixture ? <p><time>{calendar.competition.nextFixture.date}</time><b>{COMPETITION_COPY[calendar.competition.nextFixture.competitionId] ?? calendar.competition.nextFixture.competitionId} {calendar.competition.nextFixture.matchId}</b><em>{calendar.competition.nextFixture.firstTeamCode ?? 'TBD'} vs {calendar.competition.nextFixture.secondTeamCode ?? 'TBD'} · {calendar.competition.nextFixture.seriesFormat} · {calendar.competition.nextFixture.executionMode === 'PLAYER_CONTROLLED' ? '관리 경기' : 'Auto'}{calendar.competition.nextFixture.jobStatus ? ` · ${calendar.competition.nextFixture.jobStatus}` : ''}{calendar.competition.nextFixture.blockingReason ? ` · ${calendar.competition.nextFixture.blockingReason}` : ''}</em></p> : null}
      {competitionActionLabel && onCompetitionAction ? <button type="button" className="lm-primary-button" disabled={competitionPending} onClick={onCompetitionAction}>{competitionPending ? '대회 상태 확인 중…' : competitionActionLabel}</button> : null}
      {calendar.competition.internationalCompetitions?.map(international => <details key={international.competitionId} className="ca-calendar__competition-data">
        <summary>{COMPETITION_COPY[international.competitionId]} · {international.bracket.complete ? `우승 ${international.bracket.champion}` : `${international.entries.length}팀 참가 확정`}</summary>
        <p>해외 대표는 임시 능력치 순위로 선정됩니다. 대회 경기는 실제 Draft와 시뮬레이션으로 진행합니다.</p>
        <ol>{international.entries.map(entry => <li key={entry.team}><strong>{entry.team}</strong><small>{entry.region} #{entry.regionalSeed} · Pool {entry.pool} · {entry.phase === 'PLAY_IN' ? 'Play-in' : '본선'}{international.bracket.placements[entry.team] ? ` · 최종 ${international.bracket.placements[entry.team]}위` : ''}</small></li>)}</ol>
        <ol>{international.bracket.bouts.map(bout => <li key={bout.id}><b>{bout.stage} · {bout.format}</b><small>{bout.date} · {bout.first} vs {bout.second}{international.results[bout.id] ? ` · 승리 ${international.results[bout.id].winner}` : ' · 예정'}</small></li>)}</ol>
        {international.bracket.draws.some(draw => draw.relaxation) ? <p>유효한 추첨이 불가능한 전적 그룹에서 재대결 또는 첫 라운드 지역 제한을 완화했습니다.</p> : null}
      </details>)}
      {calendar.competition.groupStandings.length ? <div className="ca-calendar__competition-data"><span>GROUP STANDINGS</span><ol>{calendar.competition.groupStandings.map((standing) => <li key={`${standing.groupId}-${standing.teamCode}`}><b>{standing.groupId} {standing.groupRank}</b><strong>{standing.teamCode}</strong><small>{standing.matchWins}승 {standing.matchLosses}패 · {standing.gameWins - standing.gameLosses >= 0 ? '+' : ''}{standing.gameWins - standing.gameLosses} · 그룹 {standing.groupPoints}점</small></li>)}</ol></div> : null}
      {calendar.competition.currentSeeds.length ? <div className="ca-calendar__competition-seeds"><span>CURRENT SEEDS</span>{calendar.competition.currentSeeds.map((seed) => <small key={`${seed.competitionId}-${seed.seedScope}-${seed.seedNumber}`}>{seed.seedScope} {seed.seedNumber} · <b>{seed.teamCode}</b></small>)}</div> : null}
      {calendar.competition.externalExecutionLimited ? <i>국제대회는 LCK 진출 slot만 보존하며 경기는 실행하지 않습니다.</i> : null}
    </div> : null}
    {calendar.competition.domesticRankingDecisions?.filter(d => d.status === 'RUNNING').map(d => <div className="ca-calendar__competition-data" key={`${d.competitionId}-${d.decisionId}`} aria-label="동률 순위 결정"><strong>동률 순위 결정 중</strong><p>{COMPETITION_COPY[d.competitionId]} · {d.decisionId}</p>{d.detail.pendingMatches?.map(m => <p key={m.matchId}>{m.first} vs {m.second}</p>)}{d.detail.nextMatchId ? <small>다음 세트 · {d.detail.nextMatchId}</small> : null}<small>위 대회 경기 행동으로 계속 진행합니다.</small></div>)}
    {calendar.competition.finalRanking ? <div className="ca-calendar__competition-data" aria-label="LCK 최종 순위"><strong>{calendar.competition.finalRanking.sourceSeasonYear} LCK 우승 · {calendar.competition.finalRanking.championTeamCode}</strong><p>준우승 · {calendar.competition.finalRanking.runnerUpTeamCode}</p><ol>{calendar.competition.finalRanking.ranking.map(row => <li key={row.teamCode}><b>{row.seed}위</b><strong>{row.teamCode}</strong><small>정규시즌 {row.seriesWins}승 {row.seriesLosses}패 · 세트 {row.gameWins}승 {row.gameLosses}패</small></li>)}</ol><p>국내 최종 순위 확정 · {calendar.competition.internationalCompetitions?.some(v => v.competitionId === 'WORLDS') ? 'Worlds 참가 명단은 국제대회 항목에서 확인' : 'Worlds 자격은 게임 내 국제대회 근거 대기'}</p></div> : null}
    <div className="ca-calendar__controls">
      <button type="button" className="lm-secondary-button" disabled={reconciling || !calendar.allowedAdvanceModes.includes('ADVANCE_ONE_DAY')} onClick={() => onAdvance('ADVANCE_ONE_DAY')}>{reconciling ? '진행 확인 중…' : '하루 진행'}</button>
      <button type="button" className="lm-primary-button" disabled={reconciling || !calendar.allowedAdvanceModes.includes('ADVANCE_TO_NEXT_EVENT')} onClick={() => onAdvance('ADVANCE_TO_NEXT_EVENT')}>다음 일정까지 진행</button>
      <button type="button" className="lm-text-button" disabled={reconciling} onClick={onRefresh}>새로고침</button>
    </div>
    <div className="ca-calendar__grid">
      <div className="ca-calendar__events"><span>UPCOMING / OFFICIAL + PROJECTED</span><ol>{calendar.upcomingEvents.slice(0, 6).map((event) => <li key={event.eventId}><time>{range(event.startDate, event.endDate)}</time><div><strong>{event.displayNameKo}</strong><small>{STATUS_COPY[event.officialStatus] ?? event.officialStatus} · {EXECUTION_COPY[event.executionStatus]}</small></div></li>)}</ol></div>
      <div className="ca-calendar__fixtures"><span>R1–R2 FIXTURE OVERLAY</span>{calendar.upcomingFixtures.length ? <ol>{calendar.upcomingFixtures.slice(0, 4).map((fixture) => <li key={fixture.fixtureId}><time>{fixture.date}</time><strong>R{fixture.roundNumber} · {fixture.firstTeamCode} vs {fixture.secondTeamCode}</strong><small>{fixture.executionMode === 'PLAYER_CONTROLLED' ? '관리 경기' : fixture.jobStatus ?? fixture.lifecycleStatus}</small></li>)}</ol> : <p>남은 R1–R2 fixture가 없습니다.</p>}<footer><span>공식 미정 {calendar.pendingOfficialFields.length}</span><span>KeSPA Cup · 2025 참고 규칙 · 2026 공식 규칙/외부 참가팀 미확정</span></footer></div>
    </div>
  </section>;
}

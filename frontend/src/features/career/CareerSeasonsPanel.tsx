import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, getCareerSeason, getCareerSeasons, transitionCareerSeason } from './api/careerApi.client';
import type { CareerSeasonDetailDto, CareerSeasonsDto, CareerTransitionRequestDto } from './api/careerApi.types';
import { validateCareerTransitionRequest } from './api/careerApi.validation';
import { clearCareerAdvanceOperation, clearCareerCompetitionOperation } from './career.pointer';

const storageKey = (career: string) => `lolfm.career.season-transition.v1.${career}`;
export function readSeasonTransition(storage: Pick<Storage, 'getItem'>, career: string): CareerTransitionRequestDto | null {
  const raw = storage.getItem(storageKey(career));
  if (!raw) return null;
  return validateCareerTransitionRequest(JSON.parse(raw));
}
function failure(cause: unknown): string { return cause instanceof CareerApiFailure ? cause.userMessage : '시즌 기록을 확인하지 못했습니다. 저장된 전환 요청으로 다시 확인해 주세요.'; }

export function CareerSeasonsPanel({ careerId, revision, busy, onBegin, onChanged, onHistory, onReplay, selectedYear }: {
  selectedYear?: number | null; careerId: string; revision: number; busy: boolean; onBegin: () => (() => void) | null; onChanged: () => void;
  onHistory: (historical: boolean, year?: number) => void; onReplay: (series: string, matchup: string) => void;
}) {
  const [seasons, setSeasons] = useState<CareerSeasonsDto | null>(null);
  const [detail, setDetail] = useState<CareerSeasonDetailDto | null>(null);
  const [historyYear, setHistoryYear] = useState<number | null>(null);
  const [pending, setPending] = useState(false);
  const [operation, setOperation] = useState<CareerTransitionRequestDto | null>(null);
  const [corruptOperation, setCorruptOperation] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);
  const request = useRef<AbortController | null>(null);
  const transitionRequest = useRef<{ controller: AbortController; release: () => void } | null>(null);
  useEffect(() => {
    const token = ++generation.current; const controller = new AbortController(); request.current = controller;
    try { setOperation(readSeasonTransition(window.sessionStorage, careerId)); } catch { setCorruptOperation(true); setError('저장된 시즌 전환 요청이 손상되었습니다. 원본 요청을 확인해야 합니다.'); }
    void getCareerSeasons(careerId, controller.signal).then(value => { if (!controller.signal.aborted && generation.current === token) setSeasons(value); })
      .catch(cause => { if (!controller.signal.aborted && generation.current === token) setError(failure(cause)); });
    return () => { ++generation.current; controller.abort(); };
  }, [careerId, revision]);
  useEffect(() => () => { request.current?.abort(); transitionRequest.current?.controller.abort(); transitionRequest.current?.release(); transitionRequest.current = null; }, []);
  useEffect(() => { if (selectedYear == null && historyYear !== null) { request.current?.abort(); ++generation.current; setHistoryYear(null); setDetail(null); } }, [selectedYear]);
  const history = async (year: number) => {
    request.current?.abort(); const controller = new AbortController(); request.current = controller; const token = ++generation.current;
    setError(null); setHistoryYear(year); setDetail(null); onHistory(year !== seasons?.activeYear, year);
    if (year === seasons?.activeYear) { setDetail(null); return; }
    try {
      const value = await getCareerSeason(careerId, year, controller.signal);
      if (!controller.signal.aborted && generation.current === token) setDetail(value);
    } catch (cause) { if (!controller.signal.aborted && generation.current === token) setError(failure(cause)); }
  };
  const transition = async () => {
    if (!seasons || transitionRequest.current || pending || busy || corruptOperation || historyYear !== null && historyYear !== seasons.activeYear) return;
    if (!operation && !seasons.allowedCommands.includes('START_NEXT_SEASON')) return;
    const release = onBegin(); if (!release) return;
    request.current?.abort(); const controller = new AbortController(); const owned = { controller, release }; transitionRequest.current = owned; ++generation.current;
    setPending(true); setError(null);
    try {
    let body = operation;
    if (!body) {
      body = { schemaVersion: 'CAREER_SEASON_TRANSITION_REQUEST_V1', sourceYear: seasons.activeYear, expectedCalendarRevision: seasons.calendarRevision, clientCommandId: crypto.randomUUID() };
      window.sessionStorage.setItem(storageKey(careerId), JSON.stringify(body)); setOperation(body);
    }
      const result = await transitionCareerSeason(careerId, body, controller.signal);
      if (controller.signal.aborted || transitionRequest.current !== owned) return;
      if (result.receipt.clientCommandId !== body.clientCommandId || result.receipt.sourceYear !== body.sourceYear) throw new Error('transition receipt mismatch');
      window.sessionStorage.removeItem(storageKey(careerId)); clearCareerAdvanceOperation(window.sessionStorage, careerId); clearCareerCompetitionOperation(window.sessionStorage, careerId);
      setOperation(null); setSeasons(result.seasons); setDetail(null); setHistoryYear(null); onHistory(false); onChanged();
    } catch (cause) {
      if (!controller.signal.aborted && transitionRequest.current === owned) {
        setError(failure(cause));
        // A definitive stale/invalid response proves this command did not commit. Never rewrite its UUID with a new revision.
        if (cause instanceof CareerApiFailure && ['CAREER_CALENDAR_STALE_REVISION', 'CAREER_REQUEST_INVALID'].includes(cause.code ?? '')) {
          window.sessionStorage.removeItem(storageKey(careerId)); setOperation(null);
          try { const latest = await getCareerSeasons(careerId, controller.signal); if (!controller.signal.aborted && transitionRequest.current === owned) setSeasons(latest); } catch { /* keep original error */ }
        }
      }
    } finally { release(); if (transitionRequest.current === owned) { transitionRequest.current = null; setPending(false); } }
  };
  return <section className="ca-calendar" aria-label="시즌 전환과 기록" aria-busy={pending}>
    <header><div><span>SEASONS</span><strong>{seasons ? `${seasons.activeYear} 시즌` : '시즌 기록 확인 중'}</strong></div>
      <button type="button" className="lm-secondary-button" disabled={pending || busy || corruptOperation || historyYear !== null && historyYear !== seasons?.activeYear || !seasons || (!operation && !seasons.allowedCommands.length)} onClick={() => { void transition(); }}>
        {pending ? '시즌 전환 확인 중…' : operation ? `${operation.sourceYear} 시즌 전환 다시 확인` : '시즌 마감 · 다음 시즌 시작'}
      </button></header>
    {error ? <p role="alert">{error}</p> : null}
    {seasons ? <><p>{seasons.blockers.includes('OFFSEASON_OPERATIONS_UNTIL_DECEMBER_31') ? '대회 종료 후 계약 시장에서 스토브에 진입해 선수단을 운영하세요. 12월 31일부터 다음 시즌을 시작할 수 있습니다.' : seasons.blockers.length ? '필수 대회와 결과 반영을 마치면 다음 시즌을 시작할 수 있습니다.' : '플레이 가능한 대회가 모두 끝났습니다. 현재 로스터로 다음 시즌을 시작합니다.'} KeSPA Cup은 비활성 참고 대회입니다.</p>
      {seasons.blockers.length ? <details><summary>마감 대기 사유 {seasons.blockers.length}개</summary><ul>{seasons.blockers.map(reason => <li key={reason}>{reason === 'OFFSEASON_OPERATIONS_UNTIL_DECEMBER_31' ? '스토브 운영: 12월 31일까지 계약 사건 진행 필요' : reason}</li>)}</ul></details> : null}
      <nav aria-label="시즌 기록 선택">{seasons.seasons.map(season => <button key={season.year} type="button" className="lm-text-button" disabled={pending || busy} onClick={() => { void history(season.year); }}>{season.year === seasons.activeYear ? `${season.year} 현재 시즌으로` : `${season.year} 시즌 기록`}</button>)}</nav></> : null}
    {detail ? <div><h3>{detail.season.year} 시즌 기록 · 읽기 전용</h3><p>국내 우승 {detail.domestic?.championTeamCode ?? '미확정'} · 준우승 {detail.domestic?.runnerUpTeamCode ?? '미확정'}</p>
      {detail.domestic ? <p>최종 순위: {detail.domestic.ranking.map(r => `${r.seed}. ${r.teamCode}`).join(' · ')}</p> : null}
      {detail.international.map(competition => <details key={competition.competitionId}><summary>{competition.competitionId} · 우승 {competition.bracket.champion ?? '미확정'}</summary><p>{competition.entries.map(entry => `${entry.team} (${competition.bracket.placements[entry.team] ?? '진행 중'}위)`).join(' · ')}</p></details>)}
      <details><summary>저장된 경기와 리플레이</summary><ul>{detail.fixtures.filter(f => f.status === 'COMPLETED').map(f => <li key={`${f.competitionId}:${f.matchId}`}>{f.replayAvailable ? <button type="button" className="lm-text-button" onClick={() => onReplay(f.seriesId, `${f.firstTeam} vs ${f.secondTeam}`)}>{f.competitionId} · {f.firstTeam} vs {f.secondTeam} · 승리 {f.winner} · 리플레이</button> : <span>{f.competitionId} · {f.firstTeam} vs {f.secondTeam} · 승리 {f.winner}</span>}</li>)}</ul></details>
    </div> : null}
  </section>;
}

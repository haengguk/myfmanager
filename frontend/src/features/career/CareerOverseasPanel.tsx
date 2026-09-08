import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, getCareerOverseas, getCareerOverseasResult } from './api/careerApi.client';
import { OVERSEAS_EVENTS } from './api/careerOverseas.contract';
import type { CareerOverseas, OverseasLeague } from './api/careerOverseas.contract';
import type { ClResult } from './api/careerCl.contract';

const phase = (value: string) => ({ REGULAR: '정규', SWISS: 'Swiss', PLAYOFFS: '플레이오프', PLAY_IN: '플레이인', KNIGHTS: 'Knights', TIEBREAKER: '순위 결정전', REGIONAL_FINALS: '최종 선발전', FINAL_SEEDING: '최종 시드 결정전' }[value] ?? value);
const path = (value: string) => value.includes('HIGHEST_CP') ? '직행 팀을 제외한 CP 순위' : value.includes('REGIONAL_FINALS') ? 'Regional Finals 성적' : value.includes('CHAMPION') ? '우승 자격' : value.includes('MSI_') ? 'MSI 성적과 국내 PO 자격' : value.includes('ACTUAL_') ? 'Career 실제 대회 성적' : '도입 이전 임시 자격';
export function CareerOverseasPanel({ careerId, year, revision, onReplay }: { careerId: string; year: number; revision: number; onReplay?: (id: string, matchup: string) => void }) {
  const [league, setLeague] = useState<OverseasLeague>('LPL'), [event, setEvent] = useState('LPL_SPLIT_1');
  const [view, setView] = useState<CareerOverseas | null>(null), [error, setError] = useState(''), [page, setPage] = useState(0);
  const [match, setMatch] = useState<string | null>(null), [result, setResult] = useState<ClResult | null>(null);
  const generation = useRef(0);
  useEffect(() => {
    const token = ++generation.current, controller = new AbortController();setView(null);setError('');setPage(0);setMatch(null);setResult(null);
    void getCareerOverseas(careerId, year, league, event, controller.signal).then(v => {
      if (controller.signal.aborted || token !== generation.current) return;
      if (v.careerId !== careerId || v.seasonYear !== year || v.league !== league || v.events.some(e => e.eventId !== event)) throw new Error('선택한 시즌·대회와 다른 응답입니다.');
      setView(v);
    }).catch(e => { if (!controller.signal.aborted && token === generation.current) setError(e instanceof CareerApiFailure ? e.userMessage : String(e)); });
    return () => { ++generation.current;controller.abort(); };
  }, [careerId, year, league, event, revision]);
  useEffect(() => {
    const controller = new AbortController();setResult(null);if (!match) return;
    void getCareerOverseasResult(careerId, year, event, match, controller.signal).then(v => { if (!controller.signal.aborted && v.careerId === careerId && v.seasonYear === year && v.matchId === match) setResult(v); }).catch(e => { if (!controller.signal.aborted) setError(e instanceof CareerApiFailure ? e.userMessage : '경기 결과 조회 실패'); });
    return () => controller.abort();
  }, [careerId, year, event, match, revision]);
  const selected = view?.events[0], fixtures = selected?.fixtures ?? [], completed = fixtures.filter(f => f.lifecycleStatus === 'COMPLETED').length;
  const ranking = selected?.result?.ranking.length ? selected.result.ranking : selected?.result?.regularRanking.length ? selected.result.regularRanking : Object.keys(selected?.standings ?? {}).sort();
  return <section className="ca-calendar" id="career-overseas" aria-label="해외 리그">
    <header><strong>{year} 해외 리그</strong><span>{view?.readOnly ? '지난 시즌 기록' : 'Calendar와 함께 Auto 진행'}</span></header>
    <label>리그 <select aria-label="해외 리그 선택" value={league} onChange={e => { const next = e.target.value as OverseasLeague;setLeague(next);setEvent(OVERSEAS_EVENTS[next][0][0]); }}>
      {Object.keys(OVERSEAS_EVENTS).map(id => <option key={id}>{id}</option>)}
    </select></label>{' '}
    <label>대회 <select aria-label="해외 대회 선택" value={event} onChange={e => setEvent(e.target.value)}>{OVERSEAS_EVENTS[league].map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
    {error ? <p role="alert">{error}</p> : !view ? <p role="status">해외 대회를 확인하고 있습니다…</p> : !view.active ? <p>{view.activation ? `${view.activation.activationYear} 시즌부터 해외 실제 경기를 진행합니다. 도입 시즌의 기존 국제 자격과 기록은 보존됩니다.` : '해외 실행 도입 전 저장입니다.'}</p> : selected ? <>
      <p>{selected.waitingReason ?? (selected.result?.complete ? '최종 결과 확정' : `실제 경기 ${completed} / 현재 확정 일정 ${fixtures.length} 완료`)}</p>
      <p>{view.scheduleExplanation}</p>
      {event === 'AMERICAS_CUP' ? <p>우승팀은 부트캠프 지원 자격을 얻습니다. MSI 진출권이나 현금·능력치 효과는 추가하지 않습니다.</p> : null}
      {selected.result?.seasonEliminated.length ? <p>이번 시즌 경기 종료: {selected.result.seasonEliminated.join(' · ')}. 다음 시즌 Split 1에는 14개 구단이 다시 참가합니다.</p> : null}
      <details open><summary>{selected.result?.complete ? '최종 순위 · 누적 CP' : '정규·Swiss 성적'}</summary>
        {ranking.length ? <table><thead><tr><th>구단</th><th>정규 승–패</th><th>게임 승–패</th><th>최종 순위</th><th>누적 CP</th></tr></thead><tbody>{ranking.map(team => { const row = selected.standings[team];return <tr key={team}><td>{team}{team === 'LEC:KCB' ? ' (고용·재정 KC)' : ''}</td><td>{row ? `${row.wins}–${row.losses}` : '—'}</td><td>{row ? `${row.gameWins}–${row.gameLosses}` : '—'}</td><td>{selected.result?.placements[team] ?? '미확정'}</td><td>{selected.championshipPoints[team] ?? '—'}</td></tr>; })}</tbody></table> : <p>선행 결과를 기다립니다.</p>}
      </details>
      <details><summary>국제대회 진출 근거</summary>{Object.values(selected.qualifications).some(rows => rows.length) ? Object.entries(selected.qualifications).filter(([, rows]) => rows.length).map(([id, entries]) => <div key={id}><strong>{id}</strong><ul>{entries.map(q => <li key={q.team}>{q.regionalSeed}시드 {q.team} · {path(q.qualification)}</li>)}</ul></div>) : <p>필요한 국내·국제 결과가 완료되고 참가 등록이 확정되면 실제 진출 팀을 표시합니다. 현재 순위로 미리 등록하지 않습니다.</p>}</details>
      <details open><summary>일정 · 플레이오프 · 실제 결과 ({fixtures.length})</summary>
        <table><thead><tr><th>날짜 / 단계</th><th>대진</th><th>형식</th><th>결과</th></tr></thead><tbody>{fixtures.slice(page * 12, (page + 1) * 12).map(f => { const score = selected.scores[f.matchId];return <tr key={f.matchId}><td>{f.date}<br />{phase(f.stageId)}</td><td>{f.firstTeamCode} – {f.secondTeamCode}</td><td>{f.seriesFormat}</td><td>{score ? <button className="lm-secondary-button" onClick={() => setMatch(f.matchId)}>{score.first}–{score.second} · 결과</button> : f.lifecycleStatus === 'READY' ? '예정' : '진행·재개 대기'}</td></tr>; })}</tbody></table>
        <button className="lm-secondary-button" disabled={page === 0} onClick={() => setPage(v => v - 1)}>이전 일정</button> <span>{page + 1} / {Math.max(1, Math.ceil(fixtures.length / 12))}</span> <button className="lm-secondary-button" disabled={(page + 1) * 12 >= fixtures.length} onClick={() => setPage(v => v + 1)}>다음 일정</button>
      </details>
      {result ? <div aria-label="해외 경기 결과"><strong>{result.firstTeam} {result.firstScore}–{result.secondScore} {result.secondTeam}</strong>{result.games.map(g => <p key={g.game}>{g.game}세트 · 승리 {g.winner} · {g.picks.map(p => `${p.nickname} (${p.champion})`).join(' · ')}</p>)}{result.replayAvailable && onReplay ? <button className="lm-secondary-button" onClick={() => onReplay(result.seriesId, `${result.firstTeam} – ${result.secondTeam}`)}>경기 리플레이</button> : null}</div> : null}
    </> : null}
  </section>;
}

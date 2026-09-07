import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, changeCareerCl, getCareerCl, getCareerClResult, getCareerRoster } from './api/careerApi.client';
import { clOperationKey, validateClCommand } from './api/careerCl.contract';
import type { CareerCl, ClCommand, ClResult } from './api/careerCl.contract';
import { ROSTER_ROLES } from './api/careerRoster.contract';
import type { CareerRoster } from './api/careerRoster.contract';

export function CareerClPanel({ careerId, year, revision, historical, busy, nextMatch, onBegin, onChanged, onExecute, onReplay }: {
  careerId: string; year: number; revision: number; historical: boolean; busy: boolean; nextMatch: string | null;
  onBegin: () => (() => void) | null; onChanged: () => void; onExecute: () => void; onReplay?: (id: string, matchup: string) => void;
}) {
  const [resultMatch, setResultMatch] = useState<string | null>(null), [result, setResult] = useState<ClResult | null>(null);
  useEffect(() => { setResult(null); if (!resultMatch) return; const controller = new AbortController(); void getCareerClResult(careerId, year, resultMatch, controller.signal).then(v => { if (!controller.signal.aborted && v.careerId === careerId && v.seasonYear === year && v.matchId === resultMatch) setResult(v); }).catch(() => { if (!controller.signal.aborted) setError('CL 경기 결과를 불러오지 못했습니다.'); }); return () => controller.abort(); }, [careerId, year, resultMatch]);
  const [view, setView] = useState<CareerCl | null>(null), [roster, setRoster] = useState<CareerRoster | null>(null), [selection, setSelection] = useState<Record<string, string>>({});
  const [operation, setOperation] = useState<ClCommand | null>(null), [error, setError] = useState(''), [notice, setNotice] = useState(''), [corrupt, setCorrupt] = useState(false), [pending, setPending] = useState(false);
  const generation = useRef(0), owned = useRef<{ controller: AbortController; release: () => void } | null>(null), dirty = useRef(false);
  useEffect(() => { setView(null); setRoster(null); setSelection({}); setOperation(null); setError(''); setNotice(''); setCorrupt(false); setPending(false); setResultMatch(null); dirty.current = false; return () => { owned.current?.controller.abort(); owned.current?.release(); owned.current = null; }; }, [careerId, year]);
  useEffect(() => {
    const controller = new AbortController(), token = ++generation.current;
    try { const raw = sessionStorage.getItem(clOperationKey(careerId)); setOperation(raw ? validateClCommand(JSON.parse(raw)) : null); } catch { setCorrupt(true); setError('저장된 CL 요청을 해석할 수 없습니다. 원본 요청 확인이 필요합니다.'); }
    void Promise.all([getCareerCl(careerId, year, controller.signal), getCareerRoster(careerId, year, controller.signal)]).then(([v, r]) => {
      if (controller.signal.aborted || token !== generation.current || v.careerId !== careerId || v.seasonYear !== year) return;
      setView(v); setRoster(r); if (!dirty.current) setSelection(Object.fromEntries((v.clubs.find(c => c.team === v.managedTeam)?.lineup ?? []).filter(id => v.clubs.find(c => c.team === v.managedTeam)?.candidates.includes(id) && r.directory.players[id]).map(id => [r.directory.players[id].position, id])));
    }).catch(e => { if (!controller.signal.aborted && token === generation.current) setError(e instanceof CareerApiFailure ? e.userMessage : String(e)); });
    return () => { ++generation.current; controller.abort(); };
  }, [careerId, year, revision]);
  async function save(action?: ClCommand['action']) {
    if (!view || historical || busy || owned.current || corrupt) return; const release = onBegin(); if (!release) return;
    const current = { controller: new AbortController(), release }; owned.current = current; ++generation.current; setPending(true); setError('');
    try {
      const command = operation ?? validateClCommand({ sourceYear: year, expectedRevision: view.revision, expectedRosterRevision: view.rosterRevision, action, players: action === 'CONFIRM_LINEUP' ? ROSTER_ROLES.map(p => selection[p]) : null, matchId: action === 'SELECT_AUTO' ? nextMatch : null, clientCommandId: crypto.randomUUID() });
      sessionStorage.setItem(clOperationKey(careerId), JSON.stringify(command)); setOperation(command);
      const result = await changeCareerCl(careerId, command, current.controller.signal);
      if (current.controller.signal.aborted || owned.current !== current) return;
      if (result.receipt.clientCommandId !== command.clientCommandId || result.receipt.seasonYear !== command.sourceYear || result.cl.careerId !== careerId) throw new Error('CL 응답 범위 오류');
      sessionStorage.removeItem(clOperationKey(careerId)); setOperation(null); setView(result.cl); dirty.current = false;
      setNotice(command.action === 'SELECT_AUTO' ? 'Auto 실행을 선택했습니다. Calendar의 경기 시작으로 진행하세요.' : 'CL 등록·선발 5명을 저장했습니다. 이미 시작한 Series는 그대로 유지됩니다.'); onChanged();
    } catch (e) {
      if (!current.controller.signal.aborted && owned.current === current) { setError(e instanceof CareerApiFailure ? e.userMessage : '응답을 확인하지 못했습니다. 원본 CL 요청을 다시 확인하세요.');
        if (e instanceof CareerApiFailure && ['CAREER_REQUEST_INVALID', 'CAREER_CALENDAR_STALE_REVISION'].includes(e.code ?? '')) { sessionStorage.removeItem(clOperationKey(careerId)); setOperation(null); onChanged(); }
      }
    } finally { release(); if (owned.current === current) { owned.current = null; setPending(false); } }
  }
  const club = view?.clubs.find(c => c.team === view.managedTeam), next = view?.fixtures.find(f => f.matchId === nextMatch), players = roster?.directory.players ?? {};
  const disabled = busy || pending || historical || !!view?.readOnly || !!operation || corrupt;
  return <section className="ca-calendar" id="career-cl" aria-label="LCK CL"><header><strong>{year} LCK CL · 육성 리그</strong></header>
    {error ? <p role="alert">{error}</p> : null}{notice ? <p role="status">{notice}</p> : null}
    {!view || !roster ? <p>CL 확인 중…</p> : !view.active ? <p>{view.activationYear ? `${view.activationYear} 시즌부터 CL을 진행합니다. 현재 시즌 기록은 유지됩니다.` : 'CL 도입 전 저장입니다. 다음 서버 시작 시 활성화 시즌을 확인합니다.'}</p> : <>
      <p>현재 단계: {view.fixtures.some(f => f.stageId === 'CL_PLAYOFFS') ? view.fixtures.some(f => f.matchId === 'CL_FINAL' && f.lifecycleStatus === 'COMPLETED') ? '시즌 종료' : '플레이오프' : view.fixtures.some(f => f.stageId === 'CL_TIEBREAKER') ? '순위 결정전' : '정규시즌·등록 준비'}</p><p>정규시즌 90 BO3 · 상위 6팀 단일 탈락 BO5. 공식 규정 전체 재현이 아닌 이 게임의 대회 형식입니다.</p>
      <p>1군과 별도의 CL 선발입니다. <a href="#career-roster">선수 명부에서 육성팀 배치</a> 또는 <a href="#career-contract-market">계약 시장에서 영입</a> 후 각 포지션을 선택하세요. 1군 주전은 자동으로 이동하지 않습니다.</p>
      {operation ? <button disabled={busy || pending || historical} onClick={() => void save()}>원본 CL 요청 다시 확인</button> : null}
      {club?.blockers.length ? <div role="status"><strong>CL 명단 보완 필요</strong><ul>{club.blockers.map(reason => <li key={reason}>{reason}</li>)}</ul></div> : <p>현재 관리 구단 CL 명단 준비 완료</p>}
      <fieldset disabled={disabled}><legend>{view.managedTeam} CL 선발</legend>{ROSTER_ROLES.map(role => <label key={role}>{role} <select aria-label={`CL ${role} 선발`} value={selection[role] ?? ''} onChange={e => { dirty.current = true; setSelection(old => ({ ...old, [role]: e.target.value })); }}><option value="">선수 선택</option>{(club?.candidates ?? []).filter(id => players[id]?.position === role).map(id => <option key={id} value={id}>{players[id].nickname}</option>)}</select></label>)}<button disabled={ROSTER_ROLES.some(role => !selection[role])} onClick={() => void save('CONFIRM_LINEUP')}>CL 등록·선발 확정</button></fieldset>
      <p>등록 {club?.registered.length ?? 0}명 · {club?.lineup.map(id => players[id]?.nickname ?? id).join(' · ')}</p>
      {next ? <p>다음 실행: {next.date} {next.firstTeamCode} CL – {next.secondTeamCode} CL ({next.seriesFormat}) <button disabled={disabled} onClick={onExecute}>Calendar 경기 실행·복귀</button>{next.executionMode === 'PLAYER_CONTROLLED' ? <button disabled={disabled} onClick={() => void save('SELECT_AUTO')}>이 CL 경기 Auto 선택</button> : null}</p> : <p>다음 CL 경기는 Calendar 진행 순서에 도달하면 실행할 수 있습니다.</p>}
      <details><summary>CL 순위 · 플레이오프 시드</summary><p>시리즈 승률 → 게임 득실 → 동률 집단 상대 전적 → 게임 승리 수. 진출·시드 동률은 추가 경기로 확정합니다.</p><table><thead><tr><th>구단</th><th>승–패</th><th>게임 승–패</th><th>확정 시드</th></tr></thead><tbody>{view.standings.map(r => <tr key={r.team}><td>{r.team} CL</td><td>{r.wins}–{r.losses}</td><td>{r.gameWins}–{r.gameLosses}</td><td>{view.ranking.indexOf(r.team) >= 0 && view.ranking.indexOf(r.team) < 6 ? view.ranking.indexOf(r.team) + 1 : '—'}</td></tr>)}</tbody></table></details>
      <details><summary>CL 일정·결과 {view.fixtures.filter(f => f.lifecycleStatus === 'COMPLETED').length}/{view.fixtures.length}</summary><ul>{view.fixtures.map(f => <li key={f.matchId}>{f.date} · {f.firstTeamCode} CL – {f.secondTeamCode} CL · {f.seriesFormat} {f.winnerTeamCode ? `· ${f.winnerTeamCode} 승` : ''}{f.lifecycleStatus === 'COMPLETED' ? <button onClick={() => setResultMatch(f.matchId)}>결과 보기</button> : null}</li>)}</ul></details>
      {result ? <section aria-label="CL 경기 결과"><h4>{result.firstTeam} CL {result.firstScore}–{result.secondScore} {result.secondTeam} CL</h4><p>검증된 실제 세트 결과 · 경기 시작 당시 선수와 챔피언</p>{result.games.map(game => <details key={game.game}><summary>Game {game.game} · {game.winner} 승 · {Math.floor(game.durationSeconds / 60)}분 {game.durationSeconds % 60}초</summary><ul>{game.picks.map(p => <li key={p.playerId}>{p.team} CL · {p.position} · {p.nickname} · {p.champion}</li>)}</ul></details>)}{result.replayAvailable && onReplay ? <button onClick={() => onReplay(result.seriesId, `${result.firstTeam} CL vs ${result.secondTeam} CL`)}>기존 Series 화면 열기</button> : null}</section> : null}
      <details><summary>다른 구단의 등록 준비</summary>{view.clubs.filter(c => c.team !== view.managedTeam).map(c => <p key={c.team}>{c.team} · {c.lineup.length}/5명 · {c.blockers.join(' / ') || '준비 완료'}</p>)}</details>
    </>}
  </section>;
}

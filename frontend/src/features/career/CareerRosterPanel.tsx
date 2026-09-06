import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, changeCareerRoster, getCareerRoster } from './api/careerApi.client';
import { readRosterOperation, rosterOperationKey, ROSTER_ROLES } from './api/careerRoster.contract';
import type { CareerRoster, RosterCommand, RosterPlayer } from './api/careerRoster.contract';

const squadName = (s: string) => ({ FIRST_TEAM: '1군', DEVELOPMENT: '육성팀', UNAFFILIATED: '무소속', UNCONFIRMED: '소속 미확인' }[s] ?? s);
const reasonName = (s: string | null) => s ? ({ AFFILIATION_UNCONFIRMED: '소속 미확인', NO_COMPETITIVE_TEAM: '실행 대상 경쟁 팀 없음', UNAFFILIATED_SIGNING_NOT_IMPLEMENTED: '무소속 · 영입 기능 미지원' }[s] ?? '현재 소속으로 출전할 수 없음') : null;
function message(cause: unknown): string { return cause instanceof CareerApiFailure ? cause.userMessage : '선수단 요청을 확인하지 못했습니다. 저장된 원본 요청으로 다시 확인해 주세요.'; }
function show(v: unknown): string { return v === null || v === undefined || v === '' ? '미확인' : Array.isArray(v) ? v.length ? v.map(show).join(' · ') : '미확인' : typeof v === 'object' ? Object.entries(v as Record<string, unknown>).map(([k, x]) => `${k}: ${show(x)}`).join(' · ') : String(v); }
function PlayerDetails({ player }: { player: RosterPlayer }) {
  const d = JSON.parse(player.detailsJson) as Record<string, unknown>;
  const personal = (d.personal ?? {}) as Record<string, unknown>, contract = (d.contract ?? {}) as Record<string, unknown>, career = (d.career ?? {}) as Record<string, unknown>, honors = (d.honors ?? {}) as Record<string, unknown>;
  const history = Array.isArray(career.teamHistory) ? career.teamHistory as Record<string, unknown>[] : [];
  return <div className="ca-player-details"><h4>{player.nickname} · {player.position}</h4><p>{player.playerId}</p>
    <p>{player.provisional ? '추정 능력치 · 공식 측정값 아님 · 중립 숙련도 14와 조사 초안의 개별값 사용' : '기존 게임 능력치'} · 자료 기준일 {show(d.snapshotAt ?? contract.checkedAt)}. 과거 조사 당시의 계약·나이이며 게임 날짜에 따라 자동 갱신되지 않습니다.</p>
    <dl><dt>실명</dt><dd>{show(personal.legalName)}</dd><dt>생년월일 / 조사 당시 나이</dt><dd>{show(personal.birthDate)} / {show(personal.ageAsOfSnapshot)}</dd><dt>국적 (표시 정보)</dt><dd>{show(personal.nationality)}</dd><dt>계약 종료 / 조사 당시 남은 일수</dt><dd>{show(contract.endDate)} / {show(contract.daysRemainingAsOfSnapshot)}</dd><dt>데뷔일</dt><dd>{show(career.debutDate)}</dd></dl>
    <details><summary>부분 경력·수상 · 빈 항목은 없음이 아닌 미확인</summary>{history.length ? <ul>{history.map((h, i) => <li key={i}>{show(h.team)} · {show(h.from)} ~ {show(h.to)}</li>)}</ul> : <p>경력 미확인</p>}<p>팀 수상: {show(honors.teamAchievements)}</p><p>개인 수상: {show(honors.individualAwards)}</p></details>
    <details><summary>능력치 12개 · 1–20</summary><dl>{Object.entries(player.gameplay.ratings).map(([name, value]) => <div key={name}><dt>{name}</dt><dd>{value}</dd></div>)}</dl></details>
    <details><summary>합법 포지션 챔피언 숙련도 {player.gameplay.proficiencies.length}개</summary><p>{player.gameplay.proficiencies.map(p => `${p.championId} ${p.value}`).join(' · ')}</p></details>
    <details><summary>출처와 미확인·충돌 사항</summary><p>실제 실력의 정확성과 밸런스를 검증한 자료가 아닙니다.</p><p>{show((d.dataQuality as Record<string, unknown> | undefined)?.issues)}</p><p>{show(d.normalizationIssues)}</p><p>{show((d.ratingEvidence as Record<string, unknown> | undefined)?.classification)}</p>{Array.isArray(d.sources) ? <ul>{(d.sources as Record<string, unknown>[]).map((s, i) => <li key={i}>{typeof s.url === 'string' && /^https?:\/\//.test(s.url) ? <a href={s.url} target="_blank" rel="noreferrer">{show(s.type)} · {show(s.checkedAt)}</a> : show(s.type)}</li>)}</ul> : <p>추가 출처 미확인</p>}</details>
  </div>;
}
export function CareerRosterPanel({ careerId, year, revision, historical, busy, onBegin, onChanged }: {
  careerId: string; year: number; revision: number; historical: boolean; busy: boolean; onBegin: () => (() => void) | null; onChanged: () => void;
}) {
  const [view, setView] = useState<CareerRoster | null>(null), [error, setError] = useState<string | null>(null), [pending, setPending] = useState(false);
  const [operation, setOperation] = useState<RosterCommand | null>(null), [corrupt, setCorrupt] = useState(false);
  const [filter, setFilter] = useState('managed'), [search, setSearch] = useState(''), [selected, setSelected] = useState<string | null>(null);
  const [destination, setDestination] = useState(''), [replacement, setReplacement] = useState('');
  const generation = useRef(0), mutation = useRef<{ controller: AbortController; release: () => void } | null>(null);
  useEffect(() => {
    const controller = new AbortController(), token = ++generation.current;
    try { setOperation(readRosterOperation(window.sessionStorage, careerId)); } catch { setCorrupt(true); setError('저장된 명단 변경 요청이 손상되었습니다. 원본 요청을 확인해야 합니다.'); }
    void getCareerRoster(careerId, year, controller.signal).then(v => { if (!controller.signal.aborted && generation.current === token) { setView(v); } }).catch(e => { if (!controller.signal.aborted && generation.current === token) setError(message(e)); });
    return () => { ++generation.current; controller.abort(); };
  }, [careerId, year, revision]);
  useEffect(() => () => { mutation.current?.controller.abort(); mutation.current?.release(); mutation.current = null; }, []);
  const change = async (action?: RosterCommand['action']) => {
    if (!view || historical || view.readOnly || busy || mutation.current || corrupt) return;
    const release = onBegin(); if (!release) return;
    const owned = { controller: new AbortController(), release }; mutation.current = owned; ++generation.current; setPending(true); setError(null);
    try {
      let body = operation;
      if (!body) {
        if (!selected || !action) return;
        body = { schemaVersion: 'CAREER_ROSTER_COMMAND_V1', sourceYear: view.seasonYear, team: view.managedTeam, playerId: selected, action, targetOrganizationId: action === 'MOVE_SQUAD' ? destination || null : null, replacementPlayerId: action === 'MOVE_SQUAD' ? replacement || null : null, expectedRevision: view.revision, clientCommandId: crypto.randomUUID() };
        window.sessionStorage.setItem(rosterOperationKey(careerId), JSON.stringify(body)); setOperation(body);
      }
      const result = await changeCareerRoster(careerId, body, owned.controller.signal);
      if (owned.controller.signal.aborted || mutation.current !== owned) return;
      if (result.receipt.clientCommandId !== body.clientCommandId || result.receipt.sourceYear !== body.sourceYear) throw new Error('roster receipt mismatch');
      window.sessionStorage.removeItem(rosterOperationKey(careerId)); setOperation(null); setView(result.roster); setReplacement(''); onChanged();
    } catch (cause) {
      if (!owned.controller.signal.aborted && mutation.current === owned) {
        setError(message(cause));
        if (cause instanceof CareerApiFailure && ['CAREER_CALENDAR_STALE_REVISION', 'CAREER_REQUEST_INVALID'].includes(cause.code ?? '')) {
          window.sessionStorage.removeItem(rosterOperationKey(careerId)); setOperation(null);
          try { const latest = await getCareerRoster(careerId, year, owned.controller.signal); if (!owned.controller.signal.aborted && mutation.current === owned) setView(latest); } catch { /* Preserve the failure reason. */ }
        }
      }
    } finally { release(); if (mutation.current === owned) { mutation.current = null; setPending(false); } }
  };
  if (!view) return <section className="ca-calendar" aria-label="선수 명부"><p>{error ?? '선수 명부 확인 중…'}</p></section>;
  const { players, organizations } = view.directory, { members, lineups } = view.state, lineup = lineups[view.managedTeam];
  const names = (ids: string[]) => ids.map(id => `${players[id].nickname} (${players[id].position})`).join(' · ');
  const rows = Object.values(players).filter(p => {
    const m = members[p.playerId]; return (filter === 'all' || filter === 'managed' && m.ownerTeam === view.managedTeam || filter === 'bench' && m.ownerTeam === view.managedTeam && m.squad === 'FIRST_TEAM' && !lineup.includes(p.playerId) || filter === 'development' && m.ownerTeam === view.managedTeam && m.squad === 'DEVELOPMENT' || filter === 'free' && (m.squad === 'UNAFFILIATED' || m.squad === 'UNCONFIRMED')) && `${p.nickname} ${p.playerId} ${p.position} ${m.organizationId ?? ''}`.toLowerCase().includes(search.toLowerCase());
  }).sort((a, b) => ROSTER_ROLES.indexOf(a.position) - ROSTER_ROLES.indexOf(b.position) || a.playerId.localeCompare(b.playerId));
  const player = selected ? players[selected] : null, member = selected ? members[selected] : null;
  const disabled = busy || pending || historical || view.readOnly || !!operation || corrupt;
  const editable = member?.ownerTeam === view.managedTeam && member.eligibilityReason === null;
  return <section className="ca-calendar ca-roster" aria-label="선수 명부" aria-busy={pending}><header><div><span>ROSTER</span><strong>{view.seasonYear} 선수 명부 · 전체 {Object.keys(players).length}명</strong></div><span>명단 revision {view.revision}{historical || view.readOnly ? ' · 읽기 전용' : ''}</span></header>
    <h3>현재 구단 선발 5명</h3><p data-testid="career-lineup">{names(lineup)}</p><p>선발 변경은 아직 시작하지 않은 다음 Series부터 적용됩니다. 시작한 Series는 고정된 5명으로 끝납니다. 국제대회는 등록된 선수만 기용하며, 등록 밖의 선수는 다음 대회 등록부터 적용됩니다.</p>
    <details><summary>현재 대회 등록 선수 · 진행 중 경기 출전 선수</summary>{Object.keys(view.registeredPlayers).length ? Object.entries(view.registeredPlayers).map(([c, ids]) => <p key={c}><strong>{c} 등록:</strong> {names(ids)}{lineup.some(id => !ids.includes(id)) ? ' · 현재 선발 중 등록 밖의 선수는 이 대회에 적용되지 않습니다. 해당 포지션은 최초 등록 선발을 유지합니다.' : ' · 현재 선발 모두 등록 범위 내'}</p>) : <p>관리 구단의 확정 국제 등록 없음</p>}{Object.keys(view.activeSeriesPlayers).length ? Object.entries(view.activeSeriesPlayers).map(([s, ids]) => <p key={s}><strong>진행 중 경기:</strong> {names(ids)} <small>{s}</small></p>) : <p>진행 중인 고정 Series 없음</p>}</details>
    {error ? <p role="alert">{error}</p> : null}{operation ? <button className="lm-secondary-button" disabled={busy || historical || pending} onClick={() => { void change(); }}>{pending ? '명단 변경 확인 중…' : `${operation.sourceYear} 명단 변경 다시 확인`}</button> : null}
    <label>명부 범위 <select value={filter} onChange={e => setFilter(e.target.value)}><option value="managed">관리 구단 전체</option><option value="bench">1군 후보</option><option value="development">연계 육성팀</option><option value="free">무소속·소속 미확인</option><option value="all">전체 선수·해외 구단</option></select></label> <label>선수 검색 <input value={search} onChange={e => setSearch(e.target.value)} placeholder="닉네임·선수 ID·포지션·팀 코드" /></label>
    <div style={{ maxHeight: 320, overflow: 'auto' }}><table><thead><tr><th>선수</th><th>포지션</th><th>현재 배치</th><th>기용 상태</th></tr></thead><tbody>{rows.map(p => { const m = members[p.playerId]; return <tr key={p.playerId}><td><button className="lm-text-button" onClick={() => { setSelected(p.playerId); setDestination(''); setReplacement(''); }}>{p.nickname}</button>{p.provisional ? ' · 추정' : ''}</td><td>{p.position}</td><td>{organizations[m.organizationId ?? '']?.displayName ?? squadName(m.squad)} · {squadName(m.squad)}</td><td>{reasonName(m.eligibilityReason) ?? (lineups[m.ownerTeam ?? '']?.includes(p.playerId) ? '구단 선발' : m.squad === 'DEVELOPMENT' ? '승격 후 선발 가능' : '후보')}</td></tr>; })}</tbody></table></div>
    {player && member ? <><PlayerDetails player={player} /><p>현재 소속: {organizations[member.organizationId ?? '']?.displayName ?? squadName(member.squad)} · {squadName(member.squad)} {reasonName(member.eligibilityReason)}</p>{editable ? <div><button className="lm-secondary-button" disabled={disabled || member.squad !== 'FIRST_TEAM' || lineup.includes(player.playerId)} onClick={() => { void change('SELECT_STARTER'); }}>{player.nickname} 선발로 선택</button> <label>이동할 조직 <select value={destination} onChange={e => setDestination(e.target.value)} disabled={disabled}><option value="">선택</option>{Object.values(organizations).filter(o => o.competitiveTeam === view.managedTeam && o.organizationId !== member.organizationId).map(o => <option key={o.organizationId} value={o.organizationId}>{o.displayName} · {o.kind === 'DEVELOPMENT' ? '육성팀' : '1군'}</option>)}</select></label>{lineup.includes(player.playerId) && organizations[destination]?.kind === 'DEVELOPMENT' ? <label>같은 포지션 대체 선발 <select value={replacement} onChange={e => setReplacement(e.target.value)} disabled={disabled}><option value="">대체 선수가 없으면 이동 불가</option>{Object.values(players).filter(p => p.playerId !== player.playerId && p.position === player.position && members[p.playerId].ownerTeam === view.managedTeam && members[p.playerId].squad === 'FIRST_TEAM' && !members[p.playerId].eligibilityReason).map(p => <option key={p.playerId} value={p.playerId}>{p.nickname}</option>)}</select></label> : null} <button className="lm-secondary-button" disabled={disabled || !destination} onClick={() => { void change('MOVE_SQUAD'); }}>배치 이동 저장</button><p>명시적으로 연결된 조직 사이의 이동을 허용하는 이번 게임 정책입니다. 후보를 자동 선발하지 않습니다.</p></div> : <p>다른 구단·무소속·소속 미확인 선수는 조회만 가능합니다. FA 영입은 지원하지 않습니다.</p>}</> : <p>선수를 선택하면 상세와 변경 동작을 확인할 수 있습니다.</p>}
  </section>;
}

import type { InboxLink } from './api/careerInbox';
import { currentAbility, potentialAbility, SKILL_LABELS } from '../player-data/playerAbility';
import { CareerPlayerAppearances } from './CareerPlayerAppearances';
import { CareerPlayerPromises } from './CareerPlayerPromises';
import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, changeCareerRoster, getCareerRoster, getCareerMarket } from './api/careerApi.client';
import { readRosterOperation, rosterOperationKey, ROSTER_ROLES } from './api/careerRoster.contract';
import type { CareerMarket } from './api/careerMarket.contract';
import type { CareerRoster, RosterCommand, RosterPlayer } from './api/careerRoster.contract';

const squadName = (s: string) => ({ FIRST_TEAM: '1군', DEVELOPMENT: '육성팀', UNAFFILIATED: '무소속', UNCONFIRMED: '소속 미확인' }[s] ?? s);
const reasonName = (s: string | null) => s ? ({ RETIRED: '은퇴 · 기록 보존', AFFILIATION_UNCONFIRMED: '소속 미확인', V4_REGISTERED_ROLE_REVIEW_REQUIRED: '작성 포지션과 현재 등록 역할 검토 필요', NO_COMPETITIVE_TEAM: '실행 대상 경쟁 팀 없음', UNAFFILIATED_SIGNING_NOT_IMPLEMENTED: '무소속 · 게임 계약 시장에서 확인' }[s] ?? '현재 소속으로 출전할 수 없음') : null;
function message(cause: unknown): string { return cause instanceof CareerApiFailure ? cause.userMessage : '선수단 요청을 확인하지 못했습니다. 저장된 원본 요청으로 다시 확인해 주세요.'; }
function show(v: unknown): string { return v === null || v === undefined || v === '' ? '미확인' : Array.isArray(v) ? v.length ? v.map(show).join(' · ') : '미확인' : typeof v === 'object' ? Object.entries(v as Record<string, unknown>).map(([k, x]) => `${k}: ${show(x)}`).join(' · ') : String(v); }
function PlayerDetails({ player }: { player: RosterPlayer }) {
  const d = JSON.parse(player.detailsJson) as Record<string, unknown>;
  const personal = (d.personal ?? {}) as Record<string, unknown>, contract = (d.contract ?? {}) as Record<string, unknown>, career = (d.career ?? {}) as Record<string, unknown>, honors = (d.honors ?? {}) as Record<string, unknown>;
  const generated = d.generated as Record<string, unknown> | undefined;
  const history = Array.isArray(career.teamHistory) ? career.teamHistory as Record<string, unknown>[] : [];
  return <div className="ca-player-details"><h4>{player.nickname} · {player.position}</h4><p>{player.playerId}</p><p><strong>CA {currentAbility(player.gameplay.ratings)} · PA {potentialAbility(player) ?? '미입력'}</strong> · CA는 12개 능력치 동일 비중, PA는 평생 능력 천장의 작성값입니다.</p>
    <p>{generated ? `게임 생성 신인 · ${show(generated.intakeYear)} 클래스` : player.provisional ? '추정 능력치 · 공식 측정값 아님 · 중립 숙련도 14와 저장된 작성값 사용' : '기존 게임 능력치'} · 자료 기준일 {show(d.snapshotAt ?? contract.checkedAt)}. {generated ? '이 Career에서 생성한 게임 데이터입니다. 현재 나이와 생애주기는 시즌 성장·은퇴·신인에서 확인하세요.' : '과거 조사 당시의 계약·나이이며 게임 날짜에 따라 자동 갱신되지 않습니다.'}</p>
    <dl><dt>{generated ? '가상 본명 · 게임 생성' : '실명'}</dt><dd>{show(personal.legalName)}</dd><dt>생년월일 / 조사 당시 나이</dt><dd>{show(personal.birthDate)} / {show(personal.ageAsOfSnapshot)}</dd><dt>국적 (표시 정보)</dt><dd>{show(personal.nationality)}</dd><dt>계약 종료 / 조사 당시 남은 일수</dt><dd>{show(contract.endDate)} / {show(contract.daysRemainingAsOfSnapshot)}</dd><dt>{generated ? '게임 생성 생일 (공개 생일 아님)' : '공식 경기 데뷔일'}</dt><dd>{show(generated?.simulationBirthDate ?? career.debutDate)}</dd><dt>최초 확인 소속일 (데뷔일 아님)</dt><dd>{show((d.careerAuthoringDetail as Record<string, unknown> | undefined)?.firstKnownRosterRecordDate)}</dd><dt>조사 원문 배치 / 등록 역할</dt><dd>{show((d.roster as Record<string, unknown> | undefined)?.squadTier)} / {show(d.currentRegisteredRole)}</dd></dl>
    <details><summary>부분 경력·수상 · 빈 항목은 없음이 아닌 미확인</summary>{history.length ? <ul>{history.map((h, i) => <li key={i}>{show(h.team)} · {show(h.from)} ~ {show(h.to)}</li>)}</ul> : <p>경력 미확인</p>}<p>팀 수상: {show(honors.teamAchievements)}</p><p>개인 수상: {show(honors.individualAwards)}</p></details>
    <details><summary>능력치 12개 · 1–20</summary><dl>{Object.entries(player.gameplay.ratings).map(([name, value]) => <div key={name}><dt>{SKILL_LABELS[name] ?? name}</dt><dd>{value}</dd></div>)}</dl></details>
    <details><summary>합법 포지션 챔피언 숙련도 {player.gameplay.proficiencies.length}개</summary><p>{player.gameplay.proficiencies.map(p => `${p.championId} ${p.value}`).join(' · ')}</p></details>
    <details><summary>출처와 미확인·충돌 사항</summary><p>실제 실력의 정확성과 밸런스를 검증한 자료가 아닙니다.</p><p>{show((d.dataQuality as Record<string, unknown> | undefined)?.issues)}</p><p>{show(d.normalizationIssues)}</p><p>{show((d.roster as Record<string, unknown> | undefined)?.verificationNote)}</p><p>{show(d.contractAuthoringDetail)}</p><p>{show((d.ratingEvidence as Record<string, unknown> | undefined)?.classification)}</p>{Array.isArray(d.sources) ? <ul>{(d.sources as Record<string, unknown>[]).map((s, i) => <li key={i}>{typeof s.url === 'string' && /^https?:\/\//.test(s.url) ? <a href={s.url} target="_blank" rel="noreferrer">{show(s.type)} · {show(s.checkedAt)}</a> : show(s.type)}</li>)}</ul> : <p>추가 출처 미확인</p>}</details>
  </div>;
}
export function CareerRosterPanel({ careerId, year, revision, historical, busy, onBegin, onChanged, onManageContract, focus }: {
  focus?: InboxLink | null; careerId: string; year: number; revision: number; historical: boolean; busy: boolean; onBegin: () => (() => void) | null; onChanged: () => void; onManageContract?: (playerId: string) => void;
}) {
  const [view, setView] = useState<CareerRoster | null>(null), [error, setError] = useState<string | null>(null), [pending, setPending] = useState(false);
  const [market, setMarket] = useState<CareerMarket | null>(null);
  const [operation, setOperation] = useState<RosterCommand | null>(null), [corrupt, setCorrupt] = useState(false);
  const [filter, setFilter] = useState('managed'), [search, setSearch] = useState(''), [selected, setSelected] = useState<string | null>(null);
  useEffect(() => { if (focus?.panel === 'ROSTER') { setFilter('managed'); setSearch(focus.positions[0] ?? ''); setSelected(null); } }, [focus]);
  const [sort, setSort] = useState('position');
  const [destination, setDestination] = useState(''), [replacement, setReplacement] = useState('');
  const generation = useRef(0), mutation = useRef<{ controller: AbortController; release: () => void } | null>(null);
  useEffect(() => {
    const controller = new AbortController(), token = ++generation.current;
    try { setOperation(readRosterOperation(window.sessionStorage, careerId)); } catch { setCorrupt(true); setError('저장된 명단 변경 요청이 손상되었습니다. 원본 요청을 확인해야 합니다.'); }
    void getCareerRoster(careerId, year, controller.signal).then(v => { if (!controller.signal.aborted && generation.current === token) { setView(v); } }).catch(e => { if (!controller.signal.aborted && generation.current === token) setError(message(e)); });
    void getCareerMarket(careerId, year, controller.signal).then(v => { if (!controller.signal.aborted && generation.current === token) setMarket(v); }).catch(() => { if (!controller.signal.aborted && generation.current === token) setMarket(null); });
    return () => { ++generation.current; controller.abort(); };
  }, [careerId, year, revision, focus]);
  useEffect(() => { setView(null); setMarket(null); setSelected(null); setPending(false); return () => { mutation.current?.controller.abort(); mutation.current?.release(); mutation.current = null; }; }, [careerId, year]);
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
  if (!view) return <section className="ca-calendar" id="career-roster" aria-label="선수 명부"><p>{error ?? '선수 명부 확인 중…'}</p></section>;
  const { players, organizations } = view.directory, { members, lineups } = view.state, lineup = lineups[view.managedTeam];
  const names = (ids: string[]) => ids.map(id => `${players[id].nickname} (${players[id].position})`).join(' · ');
  const contractLabel = (id: string) => {
    const p = market?.players.find(p => p.playerId === id), c = market?.contracts.find(c => c.contractId === p?.currentContractId);
    return p ? `${c ? `계약 중 · ${c.terms.endDate} 종료` : p.status === 'FREE_AGENT' ? '영입 가능한 FA' : '영입 자격 미확인'}${p.scheduledContractId ? ' · 미래 계약 확정' : p.availableStart ? ' · 협상 가능' : ''}` : '계약 시장에서 확인';
  };
  const rows = Object.values(players).filter(p => {
    const m = members[p.playerId]; return (filter === 'all' || filter === 'managed' && m.ownerTeam === view.managedTeam || filter === 'bench' && m.ownerTeam === view.managedTeam && m.squad === 'FIRST_TEAM' && !lineup.includes(p.playerId) || filter === 'development' && m.ownerTeam === view.managedTeam && m.squad === 'DEVELOPMENT' || filter === 'free' && (m.squad === 'UNAFFILIATED' || m.squad === 'UNCONFIRMED')) && `${p.nickname} ${p.playerId} ${p.position} ${m.organizationId ?? ''}`.toLowerCase().includes(search.toLowerCase());
  }).sort((a, b) => (sort === 'ca' ? currentAbility(b.gameplay.ratings) - currentAbility(a.gameplay.ratings) : 0) || ROSTER_ROLES.indexOf(a.position) - ROSTER_ROLES.indexOf(b.position) || a.playerId.localeCompare(b.playerId));
  const player = selected ? players[selected] : null, member = selected ? members[selected] : null;
  const disabled = busy || pending || historical || view.readOnly || !!operation || corrupt;
  const editable = member?.ownerTeam === view.managedTeam && member.eligibilityReason === null;
  return <section className="ca-calendar ca-roster" id="career-roster" aria-label="선수 명부" aria-busy={pending}>{focus?.panel === 'ROSTER' ? <p role="status">등록 대상 {focus.competition} · 필요한 포지션 {focus.positions.join(', ') || '선발·자격 확인'}</p> : null}<header><div><span>ROSTER</span><strong>{view.seasonYear} 선수 명부 · 전체 {Object.keys(players).length}명</strong></div><span>명단 revision {view.revision}{historical || view.readOnly ? ' · 읽기 전용' : ''}</span></header>
    <h3>현재 구단 선발 {lineup.length}/5명</h3><p data-testid="career-lineup">{names(lineup)}</p><p>선발 변경은 아직 시작하지 않은 다음 Series부터 적용됩니다. 시작한 Series는 고정된 5명으로 끝납니다. 국제대회는 등록된 선수만 기용하며, 등록 밖의 선수는 다음 대회 등록부터 적용됩니다.</p>
    <details><summary>현재 대회 등록 선수 · 진행 중 경기 출전 선수</summary>{Object.keys(view.registeredPlayers).length ? Object.entries(view.registeredPlayers).map(([c, ids]) => <p key={c}><strong>{c} 등록:</strong> {names(ids)}{ids.some(id => members[id].ownerTeam !== view.managedTeam || members[id].eligibilityReason) ? ' · 소속을 떠났거나 현재 출전 자격이 없는 등록 선수 포함' : ''}{lineup.some(id => !ids.includes(id)) ? ' · 현재 선발 중 등록 밖의 선수는 이 대회에 적용되지 않습니다. 현재 유효한 등록 대체 선수가 필요하며, 모두 떠난 포지션은 보충등록을 확인하세요.' : ' · 현재 선발 모두 등록 범위 내'}</p>) : <p>관리 구단의 확정 국제 등록 없음</p>}{Object.keys(view.activeSeriesPlayers).length ? Object.entries(view.activeSeriesPlayers).map(([s, ids]) => <p key={s}><strong>진행 중 경기:</strong> {names(ids)} <small>{s}</small></p>) : <p>진행 중인 고정 Series 없음</p>}</details>
    {error ? <p role="alert">{error}</p> : null}{operation ? <button className="lm-secondary-button" disabled={busy || historical || pending} onClick={() => { void change(); }}>{pending ? '명단 변경 확인 중…' : `${operation.sourceYear} 명단 변경 다시 확인`}</button> : null}
    <label>명부 범위 <select value={filter} onChange={e => setFilter(e.target.value)}><option value="managed">관리 구단 전체</option><option value="bench">1군 후보</option><option value="development">연계 육성팀</option><option value="free">무소속·소속 미확인</option><option value="all">전체 선수·해외 구단</option></select></label> <label>선수 검색 <input value={search} onChange={e => setSearch(e.target.value)} placeholder="닉네임·선수 ID·포지션·팀 코드" /></label> <label>비교 순서 <select value={sort} onChange={e => setSort(e.target.value)}><option value="position">포지션</option><option value="ca">CA 높은 순</option></select></label><p>CA로 비교한 뒤 상세 능력치와 챔피언 숙련도를 함께 확인하세요.</p>
    <div style={{ maxHeight: 320, overflow: 'auto' }}><table><thead><tr><th>선수</th><th>포지션</th><th>CA</th><th>PA</th><th>현재 배치</th><th>기용 상태</th><th>게임 계약</th></tr></thead><tbody>{rows.map(p => { const m = members[p.playerId]; return <tr key={p.playerId}><td><button className="lm-text-button" onClick={() => { setSelected(p.playerId); setDestination(''); setReplacement(''); }}>{p.nickname}</button>{p.provisional ? ' · 추정' : ''}</td><td>{p.position}</td><td>{currentAbility(p.gameplay.ratings)}</td><td>{potentialAbility(p) ?? '미입력'}</td><td>{organizations[m.organizationId ?? '']?.displayName ?? squadName(m.squad)} · {squadName(m.squad)}</td><td>{reasonName(m.eligibilityReason) ?? (lineups[m.ownerTeam ?? '']?.includes(p.playerId) ? '구단 선발' : m.squad === 'DEVELOPMENT' ? 'CL 등록·선발 또는 1군 승격 가능' : '후보')}</td><td>{contractLabel(p.playerId)}</td></tr>; })}</tbody></table></div>
    {player && member ? <><PlayerDetails player={player} /><CareerPlayerAppearances careerId={careerId} year={year} playerId={player.playerId} revision={revision} /><CareerPlayerPromises market={market} playerId={player.playerId} />{onManageContract ? <button className="lm-secondary-button" onClick={() => onManageContract(player.playerId)}>게임 계약·재계약·방출 확인</button> : null}<p>현재 소속: {organizations[member.organizationId ?? '']?.displayName ?? squadName(member.squad)} · {squadName(member.squad)} {reasonName(member.eligibilityReason)}</p>{editable ? <div><button className="lm-secondary-button" disabled={disabled || member.squad !== 'FIRST_TEAM' || lineup.includes(player.playerId)} onClick={() => { void change('SELECT_STARTER'); }}>{player.nickname} 선발로 선택</button> <label>이동할 조직 <select value={destination} onChange={e => setDestination(e.target.value)} disabled={disabled}><option value="">선택</option>{Object.values(organizations).filter(o => o.competitiveTeam === view.managedTeam && o.organizationId !== member.organizationId).map(o => <option key={o.organizationId} value={o.organizationId}>{o.displayName} · {o.kind === 'DEVELOPMENT' ? '육성팀' : '1군'}</option>)}</select></label>{lineup.includes(player.playerId) && organizations[destination]?.kind === 'DEVELOPMENT' ? <label>같은 포지션 대체 선발 <select value={replacement} onChange={e => setReplacement(e.target.value)} disabled={disabled}><option value="">대체 선수가 없으면 이동 불가</option>{Object.values(players).filter(p => p.playerId !== player.playerId && p.position === player.position && members[p.playerId].ownerTeam === view.managedTeam && members[p.playerId].squad === 'FIRST_TEAM' && !members[p.playerId].eligibilityReason).map(p => <option key={p.playerId} value={p.playerId}>{p.nickname}</option>)}</select></label> : null} <button className="lm-secondary-button" disabled={disabled || !destination} onClick={() => { void change('MOVE_SQUAD'); }}>배치 이동 저장</button><p>명시적으로 연결된 조직 사이의 이동을 허용하는 이번 게임 정책입니다. 후보를 자동 선발하지 않습니다.</p></div> : <p>다른 구단의 선발은 변경할 수 없습니다. FA 영입 및 계약 가능 상태는 계약 시장에서 확인하세요.</p>}</> : <p>선수를 선택하면 상세와 변경 동작을 확인할 수 있습니다.</p>}
  </section>;
}

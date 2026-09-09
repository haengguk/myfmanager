import type { InboxLink } from './api/careerInbox';
import { careerMoney } from './careerMoney';
import { CareerFinancePanel } from './CareerFinancePanel';
import { CareerTransferPanel } from './CareerTransferPanel';
import { CareerPlayerPromises } from './CareerPlayerPromises';
import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, changeCareerMarket, getCareerMarket, getCareerRoster } from './api/careerApi.client';
import { marketOperationKey, readMarketOperation, newerMarket } from './api/careerMarket.contract';
import type { CareerMarket, MarketCommand, MarketOffer, MarketRole } from './api/careerMarket.contract';
import type { CareerRoster } from './api/careerRoster.contract';
const roleName = (s: string) => ({ STARTER: '주전 약속', RESERVE: '후보', DEVELOPMENT: '육성' }[s] ?? s);
const statusName = (s: string) => ({ RETIRED: '은퇴', CANCELLED_RETIREMENT: '은퇴로 취소', ACTIVE: '계약 중', SCHEDULED: '입단 예정', EXPIRED: '만료', RELEASED: '방출', TRANSFERRED: '이적 종료', SUBMITTED: '검토 중', COUNTER: '수정 요청', ACCEPTED: '수락', REJECTED: '거절', WITHDRAWN: '철회', SUPERSEDED: '수정안으로 대체', FREE_AGENT: '영입 가능한 FA', CONTRACTED: '계약 중', UNAVAILABLE: '영입 자격 미확인' }[s] ?? s);
const eventName = (s: string) => ({ OFFER_SUBMITTED: '제안 제출', OFFER_REJECTED: '제안 거절', COUNTER_REQUESTED: '조건 수정 요청', OFFER_WITHDRAWN: '제안 철회', PLAYER_DECISION: '선수 결정', CONTRACT_SIGNED: '계약 체결', CONTRACT_ACTIVATED: '입단', CONTRACT_RELEASED: '방출', TRANSFERRED: '이적 종료', CONTRACT_EXPIRED: '계약 만료', EXPIRY_WARNING: '만료 예고', SALARY_ARREARS_RECORDED: '미지급 급여 발생' }[s] ?? '시장 소식');
const inclination = (s: string) => ({ COMPARE_OFFERS: '여러 제안 비교', SEEK_OPPORTUNITY: '출전 기회 우선', PREFER_RENEWAL: '재계약 우선 검토' }[s] ?? '제안 검토');
function failure(cause: unknown) { return cause instanceof CareerApiFailure ? cause.userMessage : '응답을 확인하지 못했습니다. 원본 요청으로 다시 확인해 주세요.'; }
function contractEnd(start: string, years: number) { const d = new Date(`${start}T00:00:00Z`); d.setUTCFullYear(d.getUTCFullYear() + years); d.setUTCDate(d.getUTCDate() - 1); return d.toISOString().slice(0, 10); }
export function CareerMarketPanel({ careerId, year, revision, historical, busy, focusPlayer, focus, onBegin, onChanged }: {
  careerId: string; year: number; revision: number; historical: boolean; busy: boolean; focusPlayer: string | null; focus?: InboxLink | null; onBegin: () => (() => void) | null; onChanged: () => void | Promise<unknown>;
}) {
  const loadedFocus = useRef<InboxLink | null | undefined>(undefined);
  const [legacyRefresh, setLegacyRefresh] = useState(false);
  const [operationTeam, setOperationTeam] = useState('LCK:BRO');
  const [view, setView] = useState<CareerMarket | null>(null), [roster, setRoster] = useState<CareerRoster | null>(null);
  const [selected, setSelected] = useState<string | null>(null), [filter, setFilter] = useState('free'), [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null), [pending, setPending] = useState(false), [corrupt, setCorrupt] = useState(false);
  const [operation, setOperation] = useState<MarketCommand | null>(null), [editing, setEditing] = useState<string | null>(null);
  const [salary, setSalary] = useState(0), [bonus, setBonus] = useState(0), [years, setYears] = useState(2), [role, setRole] = useState<MarketRole>('RESERVE'), [start, setStart] = useState('');
  const [releaseReview, setReleaseReview] = useState(false), [replacement, setReplacement] = useState(''), [competition, setCompetition] = useState('');
  const generation = useRef(0), mutation = useRef<{ controller: AbortController; release: () => void } | null>(null);
  useEffect(() => {
    const controller = new AbortController(), token = ++generation.current; loadedFocus.current = undefined; setView(null);
    try { setOperation(readMarketOperation(window.sessionStorage, careerId)); } catch { setCorrupt(true); setError('보관된 계약 요청이 손상되었습니다. 원본 요청을 확인해야 합니다.'); }
    void Promise.all([getCareerMarket(careerId, year, controller.signal), getCareerRoster(careerId, year, controller.signal)]).then(([m, r]) => { if (!controller.signal.aborted && token === generation.current) { loadedFocus.current = focus; setView(old => newerMarket(old, m)); setRoster(r); setError(null); } }).catch(e => { if (!controller.signal.aborted && token === generation.current) setError(failure(e)); });
    return () => { ++generation.current; controller.abort(); };
  }, [careerId, year, revision, focus]);
  useEffect(() => { setLegacyRefresh(false); setPending(false); setSelected(null); setEditing(null); setReleaseReview(false); setView(null); setRoster(null); return () => { mutation.current?.controller.abort(); mutation.current?.release(); mutation.current = null; }; }, [careerId, year]);
  const money = (n: number) => careerMoney(n, view?.currency ?? 'GAME_CREDITS');
  const pick = (id: string, offer?: MarketOffer) => {
    const p = view?.players.find(p => p.playerId === id); setSelected(id); setEditing(offer?.offerId ?? null); setReleaseReview(false); setReplacement('');
    setStart(offer && p?.availableStart && offer.terms.startDate < p.availableStart ? p.availableStart : offer?.terms.startDate ?? p?.availableStart ?? ''); setSalary(offer?.requestedSalary ?? offer?.terms.annualSalary ?? p?.askingSalary ?? 0); setBonus(offer?.terms.signingBonus ?? 0); setRole(offer?.terms.role ?? 'RESERVE'); setYears(2);
  };
  useEffect(() => { if (focusPlayer && view) { pick(focusPlayer); setFilter('all'); } }, [focusPlayer, view?.careerId]); // Selection does not submit or draw a new decision.
  useEffect(() => {
    if (loadedFocus.current !== focus || !focus?.playerId || !view || focus.seasonYear !== view.seasonYear) return;
    const offer = view.offers.find(o => o.offerId === focus.sourceId && o.playerId === focus.playerId);
    pick(focus.playerId, offer?.status === 'COUNTER' ? offer : undefined); setFilter('all'); document.getElementById('career-contract-market')?.scrollIntoView({ behavior: 'smooth' });
    if (focus.panel === 'MARKET' && focus.sourceId && !offer) setError('원래 협상은 현재 계약 제안에서 찾을 수 없습니다. 보존 소식과 현재 선수 상태를 확인하세요.');
  }, [focus, view?.careerId, view?.revision]);
  const execute = async (action?: MarketCommand['action'], offerId?: string) => {
    if (!view || historical || view.readOnly || busy || corrupt || mutation.current) return;
    const release = onBegin(); if (!release) return;
    const owned = { controller: new AbortController(), release }; mutation.current = owned; ++generation.current; setPending(true); setError(null);
    try {
      let body = operation;
      if (!body) {
        if (!action) return;
        const terms = action === 'SUBMIT' || action === 'REVISE' ? { startDate: start, endDate: contractEnd(start, years), annualSalary: salary, signingBonus: bonus, role } : null;
        body = { schemaVersion: view.currency === 'KRW' ? 'CAREER_MARKET_COMMAND_KRW_V1' : 'CAREER_MARKET_COMMAND_V1', sourceYear: view.seasonYear, expectedRevision: view.revision, action,
          playerId: action === 'OPEN_STOVE' || action === 'WITHDRAW' ? null : selected, offerId: action === 'REVISE' ? editing : action === 'WITHDRAW' ? offerId ?? null : null,
          terms, replacementPlayerId: action === 'RELEASE' ? replacement || null : null, competitionId: action === 'SUPPLEMENT' ? competition || null : null, clientCommandId: crypto.randomUUID() };
        window.sessionStorage.setItem(marketOperationKey(careerId), JSON.stringify(body)); setOperation(body);
      }
      const result = await changeCareerMarket(careerId, body, owned.controller.signal);
      if (owned.controller.signal.aborted || mutation.current !== owned) return;
      if (result.receipt.clientCommandId !== body.clientCommandId || result.receipt.sourceYear !== body.sourceYear || result.receipt.action !== body.action) throw new Error('market receipt scope');
      window.sessionStorage.removeItem(marketOperationKey(careerId)); setOperation(null); setView(old => newerMarket(old, result.market)); setEditing(null); setReleaseReview(false);
      const currentRoster = await getCareerRoster(careerId, result.market.seasonYear, owned.controller.signal);
      if (!owned.controller.signal.aborted && mutation.current === owned) { setRoster(currentRoster); await onChanged(); }
    } catch (cause) {
      if (!owned.controller.signal.aborted && mutation.current === owned) {
        setError(failure(cause));
        if (cause instanceof CareerApiFailure && cause.code === 'CAREER_MONEY_POLICY_REFRESH_REQUIRED') {
          setLegacyRefresh(false);
          try { const latest = await getCareerMarket(careerId, year, owned.controller.signal); if (!owned.controller.signal.aborted && mutation.current === owned && latest.careerId === careerId && latest.seasonYear === year && latest.currency === 'KRW') { setView(old => newerMarket(old, latest)); setLegacyRefresh(true); } } catch { /* Retain the original request until current terms can be loaded. */ }
        }
        if (operation?.schemaVersion !== 'CAREER_MARKET_COMMAND_V1' && cause instanceof CareerApiFailure && ['CAREER_CALENDAR_STALE_REVISION', 'CAREER_REQUEST_INVALID'].includes(cause.code ?? '')) {
          window.sessionStorage.removeItem(marketOperationKey(careerId)); setOperation(null);
          try { const latest = await getCareerMarket(careerId, year, owned.controller.signal); if (!owned.controller.signal.aborted && mutation.current === owned) setView(old => newerMarket(old, latest)); } catch { /* Keep the original failure visible. */ }
        }
      }
    } finally { release(); if (mutation.current === owned) { mutation.current = null; setPending(false); } }
  };
  if (!view || !roster) return <section className="ca-calendar" aria-label="계약과 FA 시장"><p>{error ?? '계약과 FA 시장 확인 중…'}</p></section>;
  const operationClub = view.finances.some(f => f.team === operationTeam && f.team !== view.managedTeam) ? operationTeam : view.finances.find(f => f.team !== view.managedTeam)?.team ?? '';
  const names = roster.directory.players, member = selected ? roster.state.members[selected] : null;
  const name = (id: string | null) => id ? names[id]?.nickname ?? id : '—';
  const player = view.players.find(p => p.playerId === selected), own = player?.currentContractId ? view.contracts.find(c => c.contractId === player.currentContractId) : null;
  const budget = view.finances.find(a => a.team === view.managedTeam), disabled = busy || pending || historical || view.readOnly || !!operation || corrupt;
  const rows = view.players.filter(p => (filter === 'all' || filter === 'free' && p.status === 'FREE_AGENT' || filter === 'managed' && (roster.state.members[p.playerId]?.ownerTeam === view.managedTeam || view.contracts.some(c => c.contractId === p.currentContractId && c.team === view.managedTeam)) || filter === 'expiring' && p.status === 'CONTRACTED' && p.availableStart !== null) && `${name(p.playerId)} ${names[p.playerId]?.position} ${roster.state.members[p.playerId]?.ownerTeam ?? ''}`.toLowerCase().includes(search.toLowerCase()));
  const selectedOffers = view.offers.filter(o => o.playerId === selected), ownOffers = view.offers.filter(o => o.team === view.managedTeam);
  return <section id="career-contract-market" className="ca-calendar" aria-label="계약과 FA 시장" aria-busy={pending}>
    <header><strong>{view.offseason ? '스토브 운영' : '계약과 FA 시장'} · {view.currentDate}</strong><span>{historical || view.readOnly ? '과거 기록 · 읽기 전용' : `다음 시장 사건 ${view.nextMarketEvent ?? '없음'}`}</span></header>
    <p>공개 조사 계약과 별도로 저장되는 게임 계약입니다. 게임 금액·구단 예산·선수 선호는 실제 연봉이나 성격을 뜻하지 않는 게임 정책입니다. 날짜 진행으로 협상 응답·선수 결정·만료·급여를 처리합니다.</p>
    {budget ? <dl><dt>연봉 예산 / 기간별 최대 약정·제안</dt><dd>{money(budget.annualBudget)} / {money(budget.committedPeakSalary)}</dd><dt>잔액 / 제안 계약금 예약</dt><dd>{money(budget.cash)} / {money(budget.reservedCash)}</dd><dt>현재 연봉 부담</dt><dd>{money(budget.currentAnnualSalary)}</dd><dt>현재 약정의 급여 확보 후 추가 지급 여유</dt><dd>{money(budget.paymentHeadroom ?? 0)}</dd><dt>미지급 급여</dt><dd>{money(budget.salaryArrears ?? 0)}</dd></dl> : null}
    <CareerFinancePanel market={view} />
    {!view.offseason && !historical ? <button className="lm-secondary-button" disabled={disabled} onClick={() => { void execute('OPEN_STOVE'); }}>대회 마감 · 스토브 진입</button> : null}
    {view.offseason ? <p>대회 마감 당시 명단과 결과는 보존됩니다. 현재 선수단을 운영하며 Calendar로 계약 사건을 진행하세요. 12월 31일부터 다음 시즌에 진입할 수 있습니다.</p> : null}
    {view.missingPositions[view.managedTeam]?.length ? <p role="status">선발 공백: {view.missingPositions[view.managedTeam].join(' · ')}. FA 계약·육성팀 승격 후 선수 명부에서 선발을 설정하세요.</p> : null}
    {operation?.schemaVersion === 'CAREER_MARKET_COMMAND_V1' && view.currency === 'KRW' ? <details open><summary>전환 전 원본 요청 · 크레딧</summary><pre>{JSON.stringify(operation.terms, null, 2)}</pre><p>먼저 원본 요청 확인으로 이미 실행된 요청인지 복구하세요. 미실행으로 확인되면 최신 원화 조건을 새로 작성할 수 있습니다.</p><button disabled={pending || busy || !legacyRefresh} onClick={() => { setLegacyRefresh(false); window.sessionStorage.setItem(`${marketOperationKey(careerId)}:legacy-review`, JSON.stringify(operation)); window.sessionStorage.removeItem(marketOperationKey(careerId)); const id = operation.playerId; setOperation(null); if (id) pick(id); }}>원본 보관 · 최신 원화 조건으로 다시 작성</button></details> : null}
    {error ? <p role="alert">{error}</p> : null}{operation ? <button className="lm-secondary-button" disabled={busy || pending || historical} onClick={() => { void execute(); }}>{pending ? '원본 계약 요청 확인 중…' : `${operation.sourceYear} 계약 요청 다시 확인`}</button> : null}
    <label>시장 범위 <select value={filter} onChange={e => setFilter(e.target.value)}><option value="free">영입 가능한 FA</option><option value="managed">관리 구단 계약</option><option value="expiring">만료 예정 · 협상 가능</option><option value="all">전체 선수</option></select></label> <label>시장 선수 검색 <input value={search} onChange={e => setSearch(e.target.value)} placeholder="선수·포지션·구단" /></label>
    <div style={{ maxHeight: 280, overflow: 'auto' }}><table><thead><tr><th>선수</th><th>포지션</th><th>게임 계약</th><th>현재 구단</th></tr></thead><tbody>{rows.map(p => <tr key={p.playerId}><td><button className="lm-text-button" onClick={() => pick(p.playerId)}>{name(p.playerId)}</button></td><td>{names[p.playerId]?.position}</td><td>{statusName(p.status)}{p.scheduledContractId ? ' · 미래 계약 확정' : ''}</td><td>{roster.state.members[p.playerId]?.ownerTeam ?? '—'}</td></tr>)}</tbody></table></div>
    {budget && (budget.salaryArrears ?? 0) > 0 ? <p role="status">미지급 급여가 있어 추가 계약 지출을 제한합니다. Calendar 진행은 가능하며 다음 확정 수입에서 먼저 정산합니다. 현금을 임의로 보충하지 않습니다.</p> : null}
    {player ? <div><h3>{name(player.playerId)} 계약 협상</h3><p>{inclination(player.preference.inclination)} · 실제 제안과 조건에 따라 달라지는 게임 의향입니다.</p><p>요구 연봉 기준 {money(player.askingSalary)} · 계약 종료와 공통 결정일을 반영한 시작 가능일 {player.availableStart ?? '현재 제안 불가'}</p>{player.eligibilityReason ? <p>{player.eligibilityReason}</p> : null}<p>{view.registrationPolicy}</p>
      <CareerPlayerPromises market={view} playerId={player.playerId} />{own ? <p>현재 고용 구단 {own.team ?? own.organizationId} · {own.terms.startDate} ~ {own.terms.endDate} · 연봉 {money(own.terms.annualSalary)} · {roleName(own.terms.role)}. 재계약 협상 중에도 기존 계약이 유지됩니다. {own.origin === 'PUBLIC_END_DATE_SHIFTED_FROM_2026_GAME_POLICY' ? '초기 종료일은 공개 조사일을 첫 시즌 연도로 이동한 게임 기준입니다.' : own.origin.includes('PROTECTION') ? '기존 저장의 선수단을 보호하기 위해 부여한 게임 초기 계약입니다.' : own.origin.includes('INITIAL') ? '미확인 공개 조건을 보완한 게임 초기 계약입니다.' : '이 Career에서 협상하여 체결한 게임 계약입니다.'}</p> : null}
      {selectedOffers.filter(o => o.status === 'SUBMITTED' || o.status === 'COUNTER').length > 1 ? <p>실제 경쟁 제안 {selectedOffers.filter(o => o.status === 'SUBMITTED' || o.status === 'COUNTER').length}건을 선수가 함께 비교합니다.</p> : null}
      {player.availableStart ? <fieldset disabled={disabled}><legend>{editing ? '수정 제안' : own?.team === view.managedTeam ? '예약 재계약 제안' : '영입 제안'}</legend><label>효력 시작일 <input type="date" value={start} min={player.availableStart} onChange={e => setStart(e.target.value)} /></label> <label>기간 <select value={years} onChange={e => setYears(Number(e.target.value))}><option value={1}>1년</option><option value={2}>2년</option><option value={3}>3년</option></select></label> <label>연봉 <input type="number" min={1} step={1000} value={salary} onChange={e => setSalary(Number(e.target.value))} /></label> <label>계약금 <input type="number" min={0} step={1000} value={bonus} onChange={e => setBonus(Number(e.target.value))} /></label> <label>약속 역할 <select value={role} onChange={e => setRole(e.target.value as MarketRole)}><option value="STARTER">주전</option><option value="RESERVE">후보</option><option value="DEVELOPMENT">육성</option></select></label><p>주전 약속만으로 기존 선발이 바뀌지 않습니다. 선수는 같은 포지션 경쟁 상황도 평가합니다. 제안은 결정일까지 비교되며 수정 요청에 응답하지 않으면 자동 동의하지 않습니다.</p><button className="lm-secondary-button" disabled={!start || salary <= 0} onClick={() => { void execute(editing ? 'REVISE' : 'SUBMIT'); }}>{editing ? '수정 조건 제안' : '계약 조건 제안'}</button></fieldset> : null}
      {own?.team === view.managedTeam ? <div><button className="lm-secondary-button" disabled={disabled || !!player.scheduledContractId} onClick={() => setReleaseReview(true)}>방출 영향 확인</button>{releaseReview ? <div role="group" aria-label="방출 확인"><p>{name(player.playerId)}의 계약을 종료합니다. 해지 비용 {money(player.releaseCost)}와 미정산 급여를 지급합니다. {roster.state.lineups[view.managedTeam].includes(player.playerId) ? '현재 선발이므로 대체 선수를 선택하거나 공백으로 남길 수 있습니다.' : '현재 선발에는 포함되지 않습니다.'}</p><p>진행 중 Series {Object.keys(roster.activeSeriesPlayers).length}건의 고정 입력은 유지됩니다. 새 Series의 현재 계약·등록 자격은 사라집니다.</p><label>대체 선발 <select value={replacement} onChange={e => setReplacement(e.target.value)}><option value="">공백 허용 · 나중에 구성</option>{Object.values(names).filter(p => p.playerId !== selected && p.position === names[player.playerId].position && roster.state.members[p.playerId].ownerTeam === view.managedTeam && roster.state.members[p.playerId].squad === 'FIRST_TEAM').map(p => <option key={p.playerId} value={p.playerId}>{p.nickname}</option>)}</select></label> <button className="lm-secondary-button" disabled={disabled} onClick={() => { void execute('RELEASE'); }}>비용 지급 후 방출 확정</button> <button className="lm-text-button" onClick={() => setReleaseReview(false)}>돌아가기</button></div> : null}</div> : null}
      {member?.ownerTeam === view.managedTeam && member.squad === 'FIRST_TEAM' ? <details><summary>등록 공백의 보충등록</summary><p>유효한 등록 대체 선수가 같은 포지션에 한 명도 없을 때만 허용되는 게임 구제 정책입니다. 기존 등록 원본과 시작된 경기는 유지합니다.</p><label>대회 <select value={competition} onChange={e => setCompetition(e.target.value)}><option value="">선택</option>{Object.keys(roster.registeredPlayers).map(c => <option key={c} value={c}>{c}</option>)}</select></label> <button className="lm-secondary-button" disabled={disabled || !competition} onClick={() => { void execute('SUPPLEMENT'); }}>등록 공백 확인 후 보충등록</button></details> : null}
      <details><summary>이 선수의 계약 이력 · 경쟁 제안</summary>{view.contracts.filter(c => c.playerId === selected).map(c => <p key={c.contractId}>{statusName(c.status)} · {c.team ?? c.organizationId} · {c.terms.startDate}~{c.terms.endDate} · 연봉 {money(c.terms.annualSalary)} · 계약금 {money(c.terms.signingBonus)}</p>)}{selectedOffers.map(o => <p key={o.offerId}>{o.team} · {statusName(o.status)} · 연봉 {money(o.terms.annualSalary)} · {roleName(o.terms.role)} · 결정일 {o.decisionDate} · {o.reason}</p>)}</details>
    </div> : <p>선수를 선택해 현재 계약과 실제 협상 조건을 확인하세요.</p>}
    <details><summary>보충등록 기록</summary>{view.supplements.filter(s => s.team === view.managedTeam).map(s => <p key={`${s.competitionId}:${s.revision}`}>{s.date} · {s.competitionId} · {name(s.playerId)} ({s.position}) · 등록 변경 {s.revision}회 · 해당 포지션의 유효한 등록 대체 선수 없음</p>)}</details><h3>우리 구단의 제안과 선수 응답</h3>{ownOffers.length ? ownOffers.slice(0, 20).map(o => <div key={o.offerId}><p>{name(o.playerId)} · {statusName(o.status)} · 응답 {o.responseDate} / 결정 {o.decisionDate} · {o.reason}{o.requestedSalary ? ` 요구 연봉 ${money(o.requestedSalary)}` : ''}</p>{['SUBMITTED', 'COUNTER'].includes(o.status) ? <><button className="lm-secondary-button" disabled={disabled} onClick={() => pick(o.playerId, o)}>조건 수정</button> <button className="lm-text-button" disabled={disabled} onClick={() => { void execute('WITHDRAW', o.offerId); }}>제안 철회</button></> : null}</div>) : <p>제출한 제안이 없습니다.</p>}
    <details><summary>AI 구단 선수단 운영</summary>
      <label>운영 구단 <select value={operationClub} onChange={e => setOperationTeam(e.target.value)}>{view.finances.filter(f => f.team !== view.managedTeam).map(f => <option key={f.team} value={f.team}>{f.team}</option>)}</select></label>
      <p>현재 1군 선발: {(roster.state.lineups[operationClub] ?? []).map(name).join(' · ') || '미확정'}</p>
      <p>현재 육성 배치: {Object.values(roster.state.members).filter(m => m.ownerTeam === operationClub && m.squad === 'DEVELOPMENT').map(m => name(m.playerId)).join(' · ') || '없음'}</p>
      <p>60일 이내 만료: {view.contracts.filter(c => c.team === operationClub && c.status === 'ACTIVE' && Date.parse(c.terms.endDate) - Date.parse(view.currentDate) <= 60 * 86400000).map(c => `${name(c.playerId)} (${c.terms.endDate})`).join(' · ') || '없음'}</p>
      <p>기존 선발 유지와 필요한 보완을 주간 검토합니다. 협상 중인 상대 구단의 후보와 조건은 공개하지 않습니다.</p>
      {(view.squadOperations ?? []).filter(o => o.team === operationClub).slice(0, 30).map(o => <p key={o.id}>{o.date} · {o.squad === 'DEVELOPMENT' ? '육성팀' : '1군'} {o.position} · {({ APPLIED: '적용 완료', RETAINED: '유지', DEFERRED: '보류', PLANNED: '계획 중', PROPOSED: '제안 중', AWAITING_CONSENT: '동의 대기', AGREED: '합의 완료', CLOSED: '협상 종료' }[o.status] ?? o.status)}{o.playerId ? ` · ${o.previousPlayerId ? `${name(o.previousPlayerId)} → ` : ''}${name(o.playerId)}` : ''}{o.effectiveDate ? ` · 효력 ${o.effectiveDate}` : ''} · {o.reason}</p>)}
      {!(view.squadOperations ?? []).some(o => o.team === operationClub) ? <p>아직 기록된 운영 판단이 없습니다.</p> : null}
    </details>
    <details><summary>시장 결과 · AI 구단 이동과 선택 이유</summary>{view.events.slice(0, 60).map(e => <p key={e.eventId}>{e.date} · {eventName(e.kind)} · {name(e.playerId)} · {e.team ?? ''} · {e.reason}</p>)}{view.decisions.slice(0, 15).map(d => <p key={d.eventId}>{d.date} {name(d.playerId)}: {d.reason}</p>)}</details>
    <details><summary>구단 지급·미지급 내역</summary><p>미지급 급여 발생액은 현금 수입이 아닌 지급 의무입니다. 실제 정산 지출과 구분합니다.</p>{view.ledger.slice(0, 30).map(l => <p key={l.entryId}>{l.date} · {({ SALARY: '급여', SALARY_ACCRUED: '미지급 급여 발생 (현금 수입 아님)', SALARY_ARREARS_PAYMENT: '미지급 급여 정산', SIGNING_BONUS: '계약금', RELEASE_COST: '해지 비용', TRANSFER_FEE_PAID: '이적료 지급', TRANSFER_FEE_RECEIVED: '이적료 수입', LOAN_FEE_PAID: '임대료 지급', LOAN_FEE_RECEIVED: '임대료 수입', LOAN_SALARY_PARENT: '임대 원소속 급여 분담', LOAN_SALARY_BORROWER: '임대 구단 급여 분담', INITIAL_ALLOCATION: '초기 현금', ANNUAL_ALLOCATION: '종전 연간 지급', GAME_CLUB_SUPPORT: '게임 구단 지원', GAME_BASE_SPONSOR: '가상 기본 후원', NON_WAGE_OPERATING: '비급여 운영비', TOURNAMENT_PLACEMENT_PRIZE: '대회 순위 상금 입금', GAME_SPONSOR_PERFORMANCE_BONUS: '가상 후원 성과 보너스' }[l.kind] ?? '계약 지급')} · {money(l.amount)}</p>)}</details>
    <CareerTransferPanel focus={focus} view={view} roster={roster} selected={selected} disabled={disabled} onBegin={onBegin} onResult={async next => { setView(old => newerMarket(old, next)); await onChanged(); }} />
  </section>;
}

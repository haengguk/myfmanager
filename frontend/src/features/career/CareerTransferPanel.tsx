import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, changeCareerTrade } from './api/careerApi.client';
import type { CareerMarket, MarketRole } from './api/careerMarket.contract';
import type { CareerRoster } from './api/careerRoster.contract';
import { readTradeOperation, tradeOperationKey, validateTradeCommand } from './api/careerManagement.contract';
import type { Negotiation, TradeCommand, TradeTerms } from './api/careerManagement.contract';
const names: Record<string, string> = { CLUB_PENDING: '상대 구단 검토', CLUB_COUNTER: '구단 역제안', PLAYER_PENDING: '선수 결정 대기', AGREED: '합의 완료·적용일 대기', COMPLETED: '거래 완료', REJECTED: '거절', WITHDRAWN: '철회', EXPIRED: '만료', SUPERSEDED: '수정안으로 대체' };
const addDays = (date: string, days: number) => { const d = new Date(`${date}T00:00:00Z`); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); };
export function CareerTransferPanel({ view, roster, selected, disabled, onBegin, onResult }: { view: CareerMarket; roster: CareerRoster; selected: string | null; disabled: boolean; onBegin: () => (() => void) | null; onResult: (view: CareerMarket) => void }) {
  const [kind, setKind] = useState<TradeTerms['kind']>('TRANSFER'), [buyer, setBuyer] = useState(''), [start, setStart] = useState(''), [end, setEnd] = useState('');
  const [fee, setFee] = useState(0), [salary, setSalary] = useState(0), [bonus, setBonus] = useState(0), [share, setShare] = useState(50), [role, setRole] = useState<MarketRole>('RESERVE');
  const [replacement, setReplacement] = useState(''), [editing, setEditing] = useState<Negotiation | null>(null), [error, setError] = useState<string | null>(null), [corrupt, setCorrupt] = useState(false);
  const [operation, setOperation] = useState<TradeCommand | null>(null), [pending, setPending] = useState(false);
  const mutation = useRef<{ controller: AbortController; release: () => void } | null>(null);
  const player = view.players.find(p => p.playerId === selected), contract = view.contracts.find(c => c.contractId === player?.currentContractId);
  const quote = view.management?.quotes.find(q => q.playerId === selected);
  const own = contract?.team === view.managedTeam;
  const nickname = (id: string) => roster.directory.players[id]?.nickname ?? id;
  useEffect(() => {
    setPending(false); setEditing(null); setCorrupt(false); setError(null);
    try { setOperation(readTradeOperation(window.sessionStorage, view.careerId)); } catch { setCorrupt(true); setError('보관된 이적/임대 요청이 손상되었습니다. 원본 요청을 확인해 주세요.'); }
    return () => { mutation.current?.controller.abort(); mutation.current?.release(); mutation.current = null; };
  }, [view.careerId, view.seasonYear]);
  useEffect(() => {
    setEditing(null); setReplacement(''); setKind('TRANSFER'); setBuyer(own ? '' : view.managedTeam);
    setStart(quote?.earliestStart ?? ''); setEnd(quote ? addDays(quote.earliestStart, 729) : '');
    setFee(quote?.suggestedTransferFee ?? 0); setSalary(Math.floor((quote?.referenceSalary ?? 0) * 1.25)); setBonus(0); setRole('RESERVE');
  }, [selected, view.careerId, view.seasonYear, view.currentDate, contract?.contractId]);
  const fill = (t: Negotiation) => { setEditing(t); setKind(t.terms.kind); setBuyer(t.terms.buyer); setStart(t.terms.startDate); setEnd(t.terms.endDate); setFee(t.terms.fee); setSalary(t.terms.playerTerms.annualSalary); setBonus(t.terms.playerTerms.signingBonus); setRole(t.terms.playerTerms.role); setShare(t.terms.borrowerSalaryPercent); setReplacement(t.terms.replacementPlayerId ?? ''); };
  const execute = async (action?: TradeCommand['action'], trade?: Negotiation) => {
    if (disabled || pending || corrupt || view.readOnly || mutation.current) return;
    const release = onBegin(); if (!release) return;
    const owned = { controller: new AbortController(), release }; mutation.current = owned; setPending(true); setError(null);
    try {
      let body = operation;
      if (!body) {
        if (!action) return;
        let terms: TradeTerms | null = null;
        if (action === 'SUBMIT' || action === 'COUNTER') {
          const playerId = editing?.terms.playerId ?? selected, seller = editing?.terms.seller ?? contract?.team;
          if (!playerId || !seller) throw new Error('원계약 선수를 선택해 주세요.');
          const original = view.contracts.find(c => c.playerId === playerId && c.status === 'ACTIVE');
          terms = { kind, playerId, seller, buyer, startDate: start, endDate: end, fee, borrowerSalaryPercent: kind === 'LOAN' ? share : 0,
            playerTerms: { startDate: start, endDate: end, annualSalary: kind === 'LOAN' ? original?.terms.annualSalary ?? 0 : salary, signingBonus: kind === 'LOAN' ? 0 : bonus, role }, replacementPlayerId: replacement || null };
        }
        body = validateTradeCommand({ schemaVersion: 'CAREER_TRADE_COMMAND_V1', sourceYear: view.seasonYear, expectedRevision: view.revision, action,
          tradeId: action === 'SUBMIT' ? null : trade?.tradeId ?? editing?.tradeId ?? null, terms, replacementPlayerId: action === 'ACCEPT' ? replacement || null : null, clientCommandId: crypto.randomUUID() });
        window.sessionStorage.setItem(tradeOperationKey(view.careerId), JSON.stringify(body)); setOperation(body);
      }
      const result = await changeCareerTrade(view.careerId, body, owned.controller.signal);
      if (owned.controller.signal.aborted || mutation.current !== owned) return;
      if (result.market.careerId !== view.careerId || result.receipt.clientCommandId !== body.clientCommandId || result.receipt.sourceYear !== body.sourceYear || result.receipt.action !== `TRADE_${body.action}`) throw new Error('거래 응답의 원본 요청 범위가 다릅니다.');
      window.sessionStorage.removeItem(tradeOperationKey(view.careerId)); setOperation(null); setEditing(null); onResult(result.market);
    } catch (e) {
      if (!owned.controller.signal.aborted && mutation.current === owned) {
        setError(e instanceof CareerApiFailure ? e.userMessage : e instanceof Error ? e.message : '거래 응답을 확인하지 못했습니다. 원본 요청으로 다시 확인하세요.');
        if (e instanceof CareerApiFailure && ['CAREER_CALENDAR_STALE_REVISION', 'CAREER_REQUEST_INVALID'].includes(e.code ?? '')) { window.sessionStorage.removeItem(tradeOperationKey(view.careerId)); setOperation(null); onResult(view); }
      }
    } finally { release(); if (mutation.current === owned) { mutation.current = null; setPending(false); } }
  };
  if (!view.management) return null;
  const blocked = disabled || pending || !!operation || corrupt || view.readOnly;
  const seller = editing?.terms.seller ?? contract?.team;
  const target = editing?.terms.playerId ?? selected;
  const alternatives = Object.values(roster.directory.players).filter(p => p.playerId !== target && p.position === roster.directory.players[target ?? '']?.position && roster.state.members[p.playerId]?.ownerTeam === seller && roster.state.members[p.playerId]?.squad === 'FIRST_TEAM' && !roster.state.members[p.playerId]?.eligibilityReason);
  return <div aria-label="유료 이적과 임대"><h3>유료 이적·임대 협상</h3><p>추정 가치는 저장된 능력치 기준 연봉과 남은 계약기간의 게임 계산입니다. 구단 요구액·실제 합의 금액과 다르며 선수의 동의도 필요합니다.</p>
    {error ? <p role="alert">{error}</p> : null}{operation ? <button disabled={disabled || pending || view.readOnly} onClick={() => void execute()}>원본 이적·임대 요청 다시 확인</button> : null}
    {quote ? <p>{nickname(quote.playerId)} · 기준 연봉 {quote.referenceSalary.toLocaleString()} / 추정 이적가치 {quote.estimatedValue.toLocaleString()} 크레딧 · 판매 요구 참고 {quote.suggestedTransferFee.toLocaleString()} 크레딧<br />{quote.unavailableReason ?? `적용 가능일 ${quote.earliestStart}부터 · 실제 조건은 적용일까지 재검사합니다.`}</p> : <p>시장 선수 목록에서 계약 중인 선수를 선택하세요.</p>}
    {(contract?.team && !quote?.unavailableReason || editing) && !view.readOnly ? <fieldset disabled={blocked}><legend>{editing ? `${nickname(editing.terms.playerId)} 역제안` : own ? '판매·임대 보내기 제안' : '구매·임대 받기 제안'}</legend>
      <label>거래 종류 <select value={kind} onChange={e => { const next = e.target.value as TradeTerms['kind']; setKind(next); if (next === 'LOAN' && start) { const stop = addDays(start, 179); setEnd(contract && contract.terms.endDate < stop ? contract.terms.endDate : stop); setFee(Math.floor((quote?.referenceSalary ?? 0) * 0.1)); setBonus(0); } }}><option value="TRANSFER">유료 이적</option><option value="LOAN">임대</option></select></label>
      <label>받는 구단 <select value={buyer} disabled={!own && !editing || !!editing} onChange={e => setBuyer(e.target.value)}><option value="">구단 선택</option>{view.finances.filter(f => f.team !== seller).map(f => <option key={f.team}>{f.team}</option>)}</select></label>
      <label>거래 시작일 <input type="date" value={start} onChange={e => setStart(e.target.value)} /></label><label>{kind === 'LOAN' ? '임대 종료일' : '새 계약 종료일'} <input type="date" value={end} onChange={e => setEnd(e.target.value)} /></label>
      <label>{kind === 'LOAN' ? '임대료' : '이적료'} <input type="number" min={0} value={fee} onChange={e => setFee(Number(e.target.value))} /></label>
      {kind === 'LOAN' ? <label>임대 구단 급여 분담 (%) <input type="number" min={0} max={100} value={share} onChange={e => setShare(Number(e.target.value))} /></label> : <><label>새 계약 연봉 <input type="number" min={1} value={salary} onChange={e => setSalary(Number(e.target.value))} /></label><label>선수 계약금 <input type="number" min={0} value={bonus} onChange={e => setBonus(Number(e.target.value))} /></label></>}
      <label>합의할 역할 <select value={role} onChange={e => setRole(e.target.value as MarketRole)}><option value="STARTER">주전</option><option value="RESERVE">후보</option><option value="DEVELOPMENT">육성</option></select></label>
      {seller === view.managedTeam ? <label>판매·임대 후 대체 선발 <select value={replacement} onChange={e => setReplacement(e.target.value)}><option value="">후보 선수 이동이면 생략 가능</option>{alternatives.map(p => <option key={p.playerId} value={p.playerId}>{p.nickname}</option>)}</select></label> : null}
      <p>임대는 원계약을 유지하며 28~366일, 원계약 종료 이내입니다. 원소속은 복귀 자리를 확보하고 임대 구단만 현재 선발을 관리합니다. 진행 중 Series와 기존 국제 등록은 보존합니다.</p>
      <button disabled={!buyer || !start || !end} onClick={() => void execute(editing ? 'COUNTER' : 'SUBMIT')}>{editing ? '역제안 제출' : '구단 간 제안 제출'}</button>
    </fieldset> : null}
    <h4>우리 구단 거래와 응답</h4>{view.management.trades.length ? view.management.trades.map(t => <article key={t.tradeId}><strong>{nickname(t.terms.playerId)} · {t.terms.kind === 'LOAN' ? '임대' : '이적'} · {names[t.status]}</strong><p>{t.terms.seller} → {t.terms.buyer} · {t.terms.fee.toLocaleString()} 크레딧 · 적용 {t.terms.startDate} / 종료 {t.terms.endDate}<br />구단 응답 {t.responseDate} · 선수 공통 결정 {t.decisionDate} · {t.reason}</p>
      {['CLUB_PENDING', 'CLUB_COUNTER', 'PLAYER_PENDING'].includes(t.status) ? <div><button disabled={blocked} onClick={() => fill(t)}>조건 확인·역제안</button> <button disabled={blocked || (t.terms.seller === view.managedTeam ? t.sellerAgreed : t.buyerAgreed)} onClick={() => void execute('ACCEPT', t)}>구단 조건 수락</button> <button disabled={blocked} onClick={() => void execute('REJECT', t)}>거절</button> <button disabled={blocked} onClick={() => void execute('WITHDRAW', t)}>철회</button></div> : null}</article>) : <p>진행하거나 완료한 우리 구단 거래가 없습니다.</p>}
  </div>;
}

import { useEffect, useRef, useState } from 'react';
import { acceptInbox, currentDecision, inboxKinds, inboxRequest, type InboxDecision, type InboxEntry, type InboxFeed, type InboxLink } from './api/careerInbox';
import { CareerRecordsPanel } from './CareerRecordsPanel';

export function CareerInboxPanel({ careerId, year, revision, running, busy, onNavigate }: { careerId: string; year: number; revision: number; running: boolean; busy: boolean; onNavigate: (link: InboxLink) => void }) {
  const [feed, setFeed] = useState<InboxFeed | null>(null), [detail, setDetail] = useState<InboxEntry | null>(null), [error, setError] = useState('');
  const [kind, setKind] = useState(''), [development, setDevelopment] = useState(false), [season, setSeason] = useState<number | null>(year);
  const [page, setPage] = useState({ cursor: 0, asOf: null as number | null }), [refresh, setRefresh] = useState(0), [pending, setPending] = useState(false);
  const generation = useRef(0), action = useRef<AbortController | null>(null);
  const query = (fresh = false) => { const p = new URLSearchParams({ kind, includeDevelopment: String(development), cursor: String(fresh ? 0 : page.cursor) }); if (season !== null) p.set('year', String(season)); if (!fresh && page.asOf !== null) p.set('asOf', String(page.asOf)); return `?${p}`; };
  useEffect(() => {
    const controller = new AbortController(), token = ++generation.current; setError('');
    void inboxRequest<InboxFeed>(careerId, query(), controller.signal).then(v => { if (!controller.signal.aborted && token === generation.current) setFeed(acceptInbox(v, careerId, season, kind, development)); }).catch(e => { if (!controller.signal.aborted) setError(String(e)); });
    return () => { ++generation.current; controller.abort(); action.current?.abort(); setPending(false); };
  }, [careerId, season, kind, development, page, refresh, revision]);
  useEffect(() => { setFeed(null); setDetail(null); setSeason(year); setPage({ cursor: 0, asOf: null }); }, [careerId, year]);
  useEffect(() => { if (!running) return; const timer = window.setInterval(() => setRefresh(v => v + 1), 10000); return () => clearInterval(timer); }, [running]);
  async function act(work: (signal: AbortSignal, valid: () => boolean) => Promise<void>) {
    if (pending) return; const controller = new AbortController(), token = generation.current; action.current?.abort(); action.current = controller; setPending(true); setError('');
    const valid = () => !controller.signal.aborted && token === generation.current;
    try { await work(controller.signal, valid); } catch (e) { if (valid()) setError(String(e)); } finally { if (action.current === controller) { action.current = null; setPending(false); } }
  }
  function open(entry: InboxEntry) { void act(async (signal, valid) => {
    const next = await inboxRequest<InboxEntry>(careerId, `/${entry.sequence}`, signal); if (!valid()) return;
    await inboxRequest(careerId, '/read', signal, { sequence: entry.sequence }); if (!valid()) return;
    setDetail({ ...next, read: true }); setFeed(old => old ? { ...old, unread: Math.max(0, old.unread - (entry.read ? 0 : 1)), items: old.items.map(i => i.sequence === entry.sequence ? { ...i, read: true } : i) } : old);
  }); }
  function navigate(decision: InboxDecision) { void act(async (signal, valid) => {
    const latest = acceptInbox(await inboxRequest<InboxFeed>(careerId, query(true), signal), careerId, season, kind, development); if (!valid()) return;
    setFeed(latest); const current = currentDecision(decision, latest);
    if (!current) { setError('이미 종료되었거나 조건이 변경된 업무입니다. 최신 처리 목록을 확인하세요.'); return; }
    onNavigate(current.link);
  }); }
  const decisions = feed?.decisions.filter(d => d.responsibility === 'MANAGER') ?? [], errors = feed?.decisions.filter(d => d.responsibility !== 'MANAGER') ?? [];
  return <section className="ca-calendar ca-inbox" aria-label="Career 소식함">
    <header><div><span>CAREER INBOX</span><strong>처리 필요 {decisions.length}건 · 소식 {feed?.unread ?? 0}건 미읽음</strong></div><button onClick={() => { setPage({ cursor: 0, asOf: null }); setRefresh(v => v + 1); }}>소식 새로고침</button></header>
    <p>읽음은 확인 표시입니다. 실제 계약·등록·경기 처리는 연결된 화면에서 직접 실행하며, 연속 진행 재개도 직접 선택합니다.</p>
    {error ? <p role="alert">{error}</p> : null}
    <h3>현재 처리 필요</h3>{decisions.length ? decisions.map(d => <article key={`${d.id}:${d.revision}`}><strong>{d.title}</strong><p>{d.date} · 미해결 · {d.blocksProgress ? '진행 대기' : '확인용'} · 기한 {d.deadline ?? '별도 기한 없음'}</p><p>{d.summary}</p><button disabled={pending || busy} onClick={() => navigate(d)}>해당 업무로 이동</button></article>) : <p>현재 사용자 응답이 필요한 업무가 없습니다.</p>}
    {errors.map(d => <p key={d.id} role="status">오류·복구 확인: {d.summary}</p>)}
    <h3>일반 소식</h3><div className="ca-calendar__controls"><label>시즌 <input type="number" value={season ?? ''} placeholder="통산" onChange={e => { setSeason(e.target.value ? Number(e.target.value) : null); setPage({ cursor: 0, asOf: null }); setDetail(null); }} /></label><label>종류 <select value={kind} onChange={e => { setKind(e.target.value); setPage({ cursor: 0, asOf: null }); setDetail(null); }}><option value="">모든 소식</option>{Object.entries(inboxKinds).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><label><input type="checkbox" checked={development} onChange={e => { setDevelopment(e.target.checked); setPage({ cursor: 0, asOf: null }); }} />CL 소식 포함</label><button disabled={!feed || pending} onClick={() => void act(async (signal, valid) => { if (!feed) return; await inboxRequest(careerId, '/read', signal, { through: feed.asOf, seasonYear: feed.seasonYear, kind: feed.kind, includeDevelopment: feed.includeDevelopment }); if (valid()) setRefresh(v => v + 1); })}>조회 기준까지 모두 읽음</button></div>
    {feed?.items.map(entry => <article key={entry.sequence}><button disabled={pending} onClick={() => open(entry)}>{entry.read ? '읽음' : '새 소식'} · {entry.item.title}</button><p>{entry.item.date} · {inboxKinds[entry.item.kind] ?? entry.item.kind} · {entry.item.summary}</p></article>)}
    {feed && !feed.items.length ? <p>이 범위에서 수집한 소식이 없습니다.</p> : null}{feed && feed.nextCursor >= 0 ? <button onClick={() => { setDetail(null); setPage({ cursor: feed.nextCursor, asOf: feed.asOf }); }}>이전 소식 더 보기</button> : null}
    <p>{feed?.collectionNote}</p>
    {detail ? <section aria-label="소식 상세"><h3>{detail.item.title}</h3><p>{detail.item.date} · 읽음 · 현재 원본 상태 {detail.currentStatus}</p><p>{detail.item.summary}</p><p>이 내용은 발생 당시의 사실입니다. 연결 화면은 최신 원본 상태와 조건을 다시 확인합니다.</p>
      {detail.item.kind === 'SEASON_RECAP' ? <><SeasonRecap facts={detail.item.facts} /><CareerRecordsPanel key={`recap:${careerId}:${detail.sequence}`} careerId={careerId} year={detail.item.link.seasonYear} revision={0} busy={true} managedTeam={detail.item.team ?? undefined} recordAsOf={typeof detail.item.facts.recordRevision === 'number' ? detail.item.facts.recordRevision : undefined} focus={{ ...detail.item.link, panel: 'RECORDS' }} /></> : <button disabled={busy} onClick={() => onNavigate({ ...detail.item.link, teamId: detail.item.team })}>원본 업무·기록 보기</button>}
      <button onClick={() => setDetail(null)}>상세 닫기</button>
    </section> : null}
  </section>;
}
function SeasonRecap({ facts }: { facts: Record<string, unknown> }) {
  const growth = (facts.growth ?? []) as { playerId: string; name: string; seasonDelta: number | null }[];
  const prizes = (facts.prizes ?? []) as { competition: string; krw: number; paidOn: string | null }[], targets = (facts.targets ?? []) as { sportingStatus: string; financeStatus: string; closingCash: number }[];
  return <>{growth.map(p => <p key={p.playerId}>{p.name}: 시즌 능력치 합계 변화 {p.seasonDelta == null ? '시작 관측 미수집' : `${p.seasonDelta >= 0 ? '+' : ''}${(p.seasonDelta / 1000).toFixed(3)}`} · 구단 재직 기간 성장과 구분</p>)}<p>기존 최종 마감에서 보존한 결산입니다. 순위·개인상·출전·성장·영입과 이탈은 아래 해당 시즌 기록에서 확인합니다.</p>{prizes.map((p, i) => <p key={i}>{p.competition}: {p.krw.toLocaleString()}원 · {p.paidOn ? `${p.paidOn} 구단 입금` : '구단 미수 상금'}</p>)}{targets.map((t, i) => <p key={i}>시즌 목표: {t.sportingStatus} · 재정 {t.financeStatus} · 평가 시점 구단 현금 {t.closingCash.toLocaleString()}원</p>)}<p>개인 포상금은 미지급 권리로, 구단 수입·현금에 합산하지 않습니다. 누락된 성적과 성장 관측은 미수집으로 표시합니다.</p></>;
}

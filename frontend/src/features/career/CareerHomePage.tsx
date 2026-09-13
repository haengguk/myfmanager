import { useEffect, useState } from 'react';
import { acceptInbox, inboxRequest, type InboxFeed, type InboxLink } from './api/careerInbox';
import { scoutingRequest, type Opponent, type ScoutPage } from './api/careerScouting';
import { careerMoney } from './careerMoney';
import type { CareerPage } from './careerWorkspace';
import { CareerReturnResult } from './CareerReturnResult';

export function CareerHomePage({ careerId, year, revision, busy, onNavigate, onPage, returnedSeriesId }: {
  returnedSeriesId?: string;
  careerId: string; year: number; revision: number; busy: boolean; onNavigate: (link: InboxLink) => void; onPage: (page: CareerPage) => void;
}) {
  const [feed, setFeed] = useState<InboxFeed | null>(null), [opponent, setOpponent] = useState<Opponent | null>(null), [overview, setOverview] = useState<ScoutPage | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  useEffect(() => {
    const controller = new AbortController(); setFeed(null); setOpponent(null); setOverview(null); setErrors([]);
    const fail = (label: string) => { if (!controller.signal.aborted) setErrors(v => [...v, `${label}을 불러오지 못했습니다. 새로고침하거나 해당 메뉴에서 다시 확인하세요.`]); };
    void inboxRequest<InboxFeed>(careerId, `?year=${year}&includeDevelopment=false&cursor=0`, controller.signal).then(v => {
      if (!controller.signal.aborted) setFeed(acceptInbox(v, careerId, year, '', false));
    }).catch(() => fail('현재 업무'));
    void scoutingRequest<Opponent>(careerId, '/opponent', controller.signal).then(v => {
      if (!controller.signal.aborted) { if (v.careerId !== careerId || v.activeYear !== year) throw Error('scope'); setOpponent(v); }
    }).catch(() => fail('다음 경기'));
    void scoutingRequest<ScoutPage>(careerId, '?cursor=0&limit=1', controller.signal).then(v => {
      if (!controller.signal.aborted) { if (v.careerId !== careerId || v.activeYear !== year) throw Error('scope'); setOverview(v); }
    }).catch(() => fail('구단 요약'));
    return () => controller.abort();
  }, [careerId, year, revision]);
  const decisions = feed?.decisions.filter(d => d.responsibility === 'MANAGER') ?? [];
  const next = opponent?.next, finance = overview?.finance;
  const latestResult = feed?.items.find(e => e.item.kind === 'SERIES_RESULT' && e.item.link.seasonYear === year && e.item.link.seriesId);
  return <div className="ca-home">
    {errors.map(e => <p role="alert" key={e}>{e}</p>)}
    <section className="ca-home-work" aria-label="구단 홈 현재 업무"><header><h2>현재 업무</h2><button onClick={() => onPage('inbox')}>소식함 열기</button></header>
      {!feed ? <p role="status">현재 업무 확인 중…</p> : decisions.length ? decisions.slice(0, 3).map(d => <article key={d.id}><div><strong>{d.title}</strong><p>{d.summary}</p><small>{d.blocksProgress ? '진행을 위해 처리 필요' : '확인 필요'} · {d.deadline ? `기한 ${d.deadline}` : '별도 기한 없음'}</small></div><button disabled={busy} onClick={() => onNavigate({ ...d.link, current: true })}>업무 처리</button></article>) : <p>지금 응답이 필요한 업무가 없습니다. 다음 경기와 진행 방법을 확인하세요.</p>}
      {decisions.length > 3 ? <button onClick={() => onPage('inbox')}>현재 업무 {decisions.length}건 모두 보기</button> : null}
      {feed?.decisions.filter(d => d.responsibility !== 'MANAGER').map(d => <p role="status" key={d.id}>{d.title} · {d.summary}</p>)}
    </section>
    <section className="ca-home-match" aria-label="다음 관리 경기"><header><h2>다음 관리 경기</h2></header>{next ? <><p>{next.date} · {next.link.competition}</p><h3>{next.first} <span>vs</span> {next.second}</h3><p>{next.link.matchState === 'IN_PROGRESS' ? '진행 중인 경기' : '경기 준비 대기'}</p><button className="lm-primary-button" disabled={busy} onClick={() => onNavigate(next.link)}>{next.link.matchState === 'IN_PROGRESS' ? '기존 경기 이어하기' : '경기 준비 확인'}</button></> : <><p>{opponent ? '서버에서 제공한 다음 관리 경기가 없습니다.' : '다음 경기 확인 중…'}</p><button onClick={() => onPage('schedule')}>일정 · 진행 조건 보기</button></>}</section>
    <section aria-label="최근 구단 소식"><header><h2>최근 소식</h2><span>{feed ? `${year} 시즌 · CL 제외 · 미읽음 ${feed.unread}건` : '소식 확인 중'}</span></header>{feed?.items.filter(e => e !== latestResult).slice(0, 3).map(e => <article key={e.sequence}><time>{e.item.date}</time><button onClick={() => onNavigate({ ...e.item.link, teamId: e.item.team })}>{e.item.title}</button><p>{e.item.summary}</p></article>)}{feed && !feed.items.length ? <p>아직 수집한 소식이 없습니다.</p> : null}<p className="ca-muted">{feed?.collectionNote}</p></section>
    <section aria-label="구단 운영 요약"><header><h2>구단 운영</h2></header><dl className="ca-home-facts"><div><dt>현재 선발</dt><dd>{overview ? `${overview.starters.length}명` : '미제공'}</dd></div><div><dt>구단 현금</dt><dd>{finance ? careerMoney(finance.cash) : '미제공'}</dd></div><div><dt>지급 여유</dt><dd>{finance ? careerMoney(finance.paymentHeadroom) : '미제공'}</dd></div><div><dt>연봉 예산</dt><dd>{finance ? careerMoney(finance.annualBudget) : '미제공'}</dd></div></dl><div className="ca-home-links"><button onClick={() => onPage('roster')}>선수단 운영</button><button onClick={() => onPage('finance')}>재정 상세</button><button onClick={() => onPage('records')}>경기 기록 · 수상</button></div></section>
    {latestResult && latestResult.item.link.seriesId !== returnedSeriesId ? <CareerReturnResult careerId={careerId} seriesId={latestResult.item.link.seriesId!} expectedYear={year} title="최근 승인 경기 결과" onNavigate={onNavigate} /> : feed && !returnedSeriesId ? <p className="ca-muted">최근 소식 범위에 승인된 경기 기록이 없습니다. 전체 이력은 기록 메뉴에서 확인하세요.</p> : null}
  </div>;
}

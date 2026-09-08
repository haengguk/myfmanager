import { careerMoney } from './careerMoney';
import type { CareerMarket } from './api/careerMarket.contract';
const returnDate = (end: string) => new Date(Date.parse(`${end}T00:00:00Z`) + 86400000).toISOString().slice(0, 10);
const roleName = { STARTER: '주전', RESERVE: '후보', DEVELOPMENT: '육성' };
export function CareerPlayerPromises({ market, playerId }: { market: CareerMarket | null; playerId: string }) {
  const promises = market?.management?.promises.filter(p => p.playerId === playerId) ?? [];
  const loans = market?.management?.loans.filter(l => l.playerId === playerId) ?? [];
  const facts = market?.management?.appearances.flatMap(a => a.opportunities.filter(o => o.playerId === playerId && o.selected).map(o => ({ ...o, date: a.date, sets: a.completedSets, series: a.seriesId }))) ?? [];
  return <div aria-label="출전·약속·만족도"><h4>출전과 역할 약속</h4><p>게임 내 평가입니다. 능력치·Draft·승패에는 영향을 주지 않습니다. 28일·평가 기회 6 Series를 관찰하고 14일 간격으로 평가합니다.</p>
    {promises.length ? promises.map(p => <p key={p.promiseId}><strong>{p.team} · {roleName[p.role]} 약속</strong> · {p.startDate} ~ {p.endDate}<br />선발 {p.starts} / 평가 기회 {p.opportunities} Series · 실제 출전 {p.sets}세트 · 만족도 {p.satisfaction}/100 · 구단 신뢰 {p.trust}/100<br />{p.reason}{p.satisfaction <= 40 ? ' · 다른 구단 또는 임대 기회 검토 의향이 높음' : ''} · 최근 평가 {p.lastEvaluation}</p>) : <p>합의한 역할의 평가 근거가 없습니다. 과거 출전 부족을 추정하지 않습니다.</p>}
    {loans.map(l => <p key={l.loanId}><strong>{l.status === 'ACTIVE' ? '임대 중' : '임대 종료'}</strong> · 원소속 {l.parentTeam} / 임대 출전 구단 {l.borrowingTeam} · {l.startDate} ~ {l.endDate} (복귀일 {returnDate(l.endDate)})<br />임대료 {careerMoney(l.fee, market?.currency ?? 'GAME_CREDITS')} · 급여 원소속 {100 - l.borrowerSalaryPercent}% / 임대 구단 {l.borrowerSalaryPercent}%</p>)}
    <details><summary>{market?.seasonYear} 시즌 확인된 출전 {facts.length} Series</summary>{facts.length ? facts.map(f => <p key={`${f.series}-${f.team}`}>{f.date} · {f.team} · {f.sets}세트</p>) : <p>완료된 경기 출전 기록 없음. 등록·Draft 진입만으로 출전을 부여하지 않습니다.</p>}</details>
  </div>;
}

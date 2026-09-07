import { useEffect, useState } from 'react';
import { getCareerAppearances } from './api/careerApi.client';
import type { AppearancePerformance } from './api/careerCl.contract';
export function CareerPlayerAppearances({ careerId, year, playerId, revision }: { careerId: string; year: number; playerId: string; revision: number }) {
  const [rows, setRows] = useState<AppearancePerformance[]>([]), [error, setError] = useState('');
  useEffect(() => { const controller = new AbortController(); setRows([]); setError(''); void getCareerAppearances(careerId, year, playerId, controller.signal).then(v => { if (!controller.signal.aborted) setRows(v); }).catch(() => { if (!controller.signal.aborted) setError('출전 기록을 불러오지 못했습니다.'); }); return () => controller.abort(); }, [careerId, year, playerId, revision]);
  const groups = new Map<string, AppearancePerformance[]>(); for (const row of rows) { const key = `${row.team}|${row.squad}`; groups.set(key, [...(groups.get(key) ?? []), row]); }
  return <section aria-label="선수단별 출전·성장"><h4>{year} 선수단별 실제 출전·경기 성장</h4>{error ? <p role="alert">{error}</p> : rows.length === 0 ? <p>이 기록 도입 이후 검증된 경기 완료가 없습니다. 과거 미수집 기록을 무출전으로 판단하지 않습니다.</p> : [...groups].map(([key, group]) => {
    const champions: Record<string, number> = {}; group.forEach(g => Object.entries(g.champions).forEach(([c, n]) => { champions[c] = (champions[c] ?? 0) + n; }));
    return <div key={key}><strong>{group[0].team} · {group[0].squad === 'DEVELOPMENT' ? 'CL' : '1군'}</strong><p>{group.length} Series · {group.reduce((n, r) => n + r.sets, 0)} 완료 세트 · 경기 능력 성장 +{(group.reduce((n, r) => n + r.internalGain, 0) / 1000).toFixed(3)}점 합계 · 숙련도 성장 +{(group.reduce((n, r) => n + r.proficiencyGain, 0) / 1000).toFixed(3)}점 합계</p><p>챔피언 사용: {Object.entries(champions).map(([c, n]) => `${c} ${n}세트`).join(' · ')}</p></div>;
  })}<p>당시 구단·선수단에 기록합니다. CL 출전은 1군 주전 약속의 기회·이행 수에 포함되지 않습니다. 육성 계약은 배치 약속이며 CL 선발 비율을 보장하지 않습니다.</p></section>;
}

import { useEffect, useState } from 'react';
import { recordRequest, type RecordSeries } from './api/careerRecords';
import type { InboxLink } from './api/careerInbox';
import { approvedSeriesScore } from './careerPlayFlow';

export function CareerReturnResult({ careerId, seriesId, onNavigate, expectedYear, revision = 0, busy = false, title = '경기에서 Career로 복귀' }: { careerId: string; seriesId: string; expectedYear?: number; revision?: number; busy?: boolean; title?: string; onNavigate: (link: InboxLink) => void }) {
  const [record, setRecord] = useState<RecordSeries | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState(''), [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setRecord(null); setLoading(true); setError('');
    void recordRequest<{ result: RecordSeries | null }>(careerId, `/series/${encodeURIComponent(seriesId)}`, controller.signal).then(({ result: r }) => {
      if (controller.signal.aborted) return;
      if (r) { approvedSeriesScore(r, careerId, seriesId); if (expectedYear !== undefined && r.seasonYear !== expectedYear) throw new Error('승인 기록의 조회 시즌이 다릅니다.'); } setRecord(r);
    }).catch(e => { if (!controller.signal.aborted) setError(String(e)); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [careerId, seriesId, expectedYear, refresh, revision]);
  return <section className="ca-calendar ca-return-result" aria-label="돌아온 경기의 승인 결과"><h3>{title}</h3>
    {loading ? <p role="status">이 경기의 Career 승인 기록을 확인하고 있습니다.</p> : error ? <p role="alert">{error}</p> : record ? <><p>{record.seasonYear} · {record.competition} · {record.date}</p><strong>{record.firstTeam} {approvedSeriesScore(record, careerId, seriesId)} {record.secondTeam}</strong><p>Career에 승인된 결과입니다. 다음 행동은 현재 업무와 캘린더에서 확인하세요.</p><button type="button" className="lm-secondary-button" disabled={busy} onClick={() => onNavigate({ panel: 'RECORDS', playerId: null, sourceId: record.recordId, seriesId: record.seriesId, competition: record.competition, seasonYear: record.seasonYear, positions: [] })}>이 경기 기록·확정 수상 확인</button><p>수상 수집 여부와 선정 근거는 기록 상세에서 확인합니다.</p></> : <p role="status">아직 이 경기의 Career 승인 기록이 없습니다. 준비·진행 중인 경기는 기존 화면에서 계속하고, 완료한 경기는 캘린더의 결과 확인 상태를 확인하세요.</p>}
    {!loading ? <button type="button" className="lm-secondary-button" onClick={() => setRefresh(v => v + 1)}>승인 결과 다시 확인</button> : null}
  </section>;
}

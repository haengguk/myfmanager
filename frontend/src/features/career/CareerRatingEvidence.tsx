import type { Rating } from './api/careerRecords';
export const evaluationLabel = (version?: unknown) => version === 'CAREER_PERFORMANCE_V2' ? '평가 V2' : version == null || version === 'CAREER_PERFORMANCE_V1' ? '평가 V1' : '별도 평가 버전';
export function CareerRatingEvidence({ rating: r }: { rating: Rating }) {
  const s = r.support, o = s?.observation;
  return <details><summary>{evaluationLabel(r.version)} · 팀 플레이 {s?.score == null ? s?.status === 'NO_OPPORTUNITY' ? '관측 기회 없음' : '미수집' : s.score.toFixed(1)}</summary>
    <p>전투 {r.combat?.toFixed(1) ?? '미수집'} · 경제 {r.economy?.toFixed(1) ?? '미수집'} · 생존 {r.survival?.toFixed(1) ?? '미수집'}</p>
    {o ? <p>실제 오브젝트 교전 {o.objectiveParticipations}/{o.objectiveOpportunities}회 참가 · 참가 교전 {o.objectiveWins}승. 본인 로밍 {o.roamAttempts}회 · {o.roamWins}회 성공. 어시스트를 별도 지원 점수로 다시 더하지 않습니다.</p> : <p>이 경기의 팀 플레이 근거는 수집하지 않았습니다. 0회 실적으로 처리하지 않습니다.</p>}
    <p>와드·시야·힐·보호막·CC 시간은 현재 미계측입니다. 교전 뒤 사망해도 실제 참가는 보존합니다.</p>
  </details>;
}

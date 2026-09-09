import "./CareerTrainingPanel.css";
import { useEffect, useRef, useState } from 'react';
import { SKILL_LABELS } from '../player-data/playerAbility';
import { CareerApiFailure, changeCareerTraining, getCareerDevelopment, getCareerRoster } from './api/careerApi.client';
import type { CareerRoster } from './api/careerRoster.contract';
import { readTrainingOperation, trainingOperationKey, validateTrainingCommand } from './api/careerDevelopment.contract';
import type { CareerDevelopment, TrainingCommand, TrainingIntensity, TrainingFocus } from './api/careerDevelopment.contract';
const intensityNames = { RECOVERY: '회복', LIGHT: '가볍게', NORMAL: '정상', INTENSIVE: '강훈련' };
const focusNames = { BALANCED: '균형', COMMON_SKILLS: '공통 능력 집중', ROLE_SKILLS: '포지션 능력 집중', SPECIFIC_SKILL: '특정 능력 집중', CHAMPION_FOCUS: '챔피언 집중' };
const growthNames: Record<string, string> = { RETIRED: '은퇴 · 훈련과 성장 종료', PA_MISSING: 'PA 미입력 · 영구 능력치 성장 보류', PA_REACHED: 'PA 상한 도달', INITIAL_CA_ABOVE_PA: '시작 CA가 PA보다 높음 · 원래 능력치 유지, 추가 성장 제한', INITIAL_ROUNDING_BOUNDARY: '표시 반올림 경계 · 내부 총량 상한 초과, 추가 성장 제한', GROWING: '성장 가능 · PA 도달을 보장하지 않습니다' };
export function CareerTrainingPanel({ careerId, year, revision, historical, busy, onBegin, onChanged, focusPlayer }: { focusPlayer?: string | null; careerId: string; year: number; revision: number; historical: boolean; busy: boolean; onBegin: () => (() => void) | null; onChanged: () => void }) {
  const [view, setView] = useState<CareerDevelopment | null>(null), [roster, setRoster] = useState<CareerRoster | null>(null);
  const [squad, setSquad] = useState<'FIRST_TEAM' | 'DEVELOPMENT'>('FIRST_TEAM');
  const [selected, setSelected] = useState(''), [intensity, setIntensity] = useState<TrainingIntensity>('NORMAL'), [focus, setFocus] = useState<TrainingFocus>('BALANCED'), [skill, setSkill] = useState(''), [champions, setChampions] = useState<string[]>([]);
  const [operation, setOperation] = useState<TrainingCommand | null>(null), [error, setError] = useState<string | null>(null), [notice, setNotice] = useState(''), [corrupt, setCorrupt] = useState(false), [pending, setPending] = useState(false), [all, setAll] = useState(false);
  const generation = useRef(0), mutation = useRef<{ controller: AbortController; release: () => void } | null>(null);
  useEffect(() => { setView(null); setRoster(null); setSelected(''); setNotice(''); setCorrupt(false); setPending(false); return () => { mutation.current?.controller.abort(); mutation.current?.release(); mutation.current = null; }; }, [careerId, year]);
  useEffect(() => {
    const controller = new AbortController(), token = ++generation.current;
    try { setOperation(readTrainingOperation(window.sessionStorage, careerId)); } catch { setCorrupt(true); setError('보관된 훈련 요청이 손상되었습니다. 원본 요청 확인이 필요합니다.'); }
    void Promise.all([getCareerDevelopment(careerId, year, controller.signal), getCareerRoster(careerId, year, controller.signal)]).then(([v, r]) => { if (!controller.signal.aborted && generation.current === token) { setView(v); setRoster(r); } }).catch(e => { if (!controller.signal.aborted && generation.current === token) setError(e instanceof CareerApiFailure ? e.userMessage : String(e)); });
    return () => { controller.abort(); ++generation.current; };
  }, [careerId, year, revision]);
  const player = view?.players.find(p => p.playerId === selected), definition = roster?.directory.players[selected];
  const teamPlan = squad === 'DEVELOPMENT' ? view?.developmentTeamPlan : view?.teamPlan;
  useEffect(() => {
    const plan = player?.development.override?.pending ?? player?.effectivePlan ?? teamPlan?.pending ?? teamPlan?.current;
    setIntensity(plan?.intensity ?? 'NORMAL'); setFocus(plan?.focus ?? 'BALANCED'); setSkill(plan?.skill ?? ''); setChampions(plan?.champions ?? []);
  }, [selected, squad, view?.revision, view?.currentDate]);
  const execute = async (clear = false) => {
    if (!view || historical || busy || corrupt || mutation.current) return;
    const release = onBegin(); if (!release) return; const owned = { controller: new AbortController(), release }; mutation.current = owned; ++generation.current; setPending(true); setError(null);
    try {
      let body = operation;
      if (!body) { body = validateTrainingCommand({ schemaVersion: 'CAREER_TRAINING_COMMAND_V1', squad, sourceYear: year, expectedRevision: view.revision, playerId: selected || null, clearOverride: clear, plan: clear ? null : { intensity, focus, skill: focus === 'SPECIFIC_SKILL' ? skill : null, champions: focus === 'CHAMPION_FOCUS' ? champions : [] }, clientCommandId: crypto.randomUUID() }); window.sessionStorage.setItem(trainingOperationKey(careerId), JSON.stringify(body)); setOperation(body); }
      const result = await changeCareerTraining(careerId, body, owned.controller.signal);
      if (owned.controller.signal.aborted || mutation.current !== owned) return;
      if (result.development.careerId !== careerId || result.receipt.clientCommandId !== body.clientCommandId || result.receipt.sourceYear !== body.sourceYear) throw new Error('훈련 응답의 원본 요청 범위가 다릅니다.');
      window.sessionStorage.removeItem(trainingOperationKey(careerId)); setOperation(null); setView(result.development); setNotice(`${result.replayed ? '원본 요청 복구 완료 · ' : '저장 완료 · '}${result.receipt.effectiveOn}부터 적용됩니다.`); onChanged();
    } catch (e) {
      if (!owned.controller.signal.aborted && mutation.current === owned) {
        setError(e instanceof CareerApiFailure ? e.userMessage : '응답을 확인하지 못했습니다. 원본 훈련 요청으로 다시 확인하세요.');
        if (e instanceof CareerApiFailure && ['CAREER_REQUEST_INVALID', 'CAREER_CALENDAR_STALE_REVISION'].includes(e.code ?? '')) { window.sessionStorage.removeItem(trainingOperationKey(careerId)); setOperation(null); onChanged(); }
      }
    } finally { release(); if (mutation.current === owned) { mutation.current = null; setPending(false); } }
  };
  const invalidTargets = focus === 'SPECIFIC_SKILL' && !skill || focus === 'CHAMPION_FOCUS' && (!champions.length || new Set(champions).size !== champions.length);
  const disabled = player?.growthStatus === 'RETIRED' || busy || pending || historical || !!operation || corrupt || !!view?.readOnly;
  const own = !selected || roster?.state.members[selected]?.ownerTeam === view?.managedTeam;
  useEffect(() => { if (focusPlayer) { setSelected(focusPlayer); setAll(true); } }, [focusPlayer]);
  const visible = view?.players.filter(p => all || roster?.state.members[p.playerId]?.ownerTeam === view.managedTeam) ?? [];
  const schedule = selected ? player?.development.override : teamPlan;
  const recent = view?.recentChanges.filter(g => g.playerId === selected) ?? [], monthly = view?.monthlySummaries.filter(g => g.playerId === selected) ?? [];
  return <details className="ca-training" id="career-training"><summary>훈련·성장 · 챔피언 숙련도와 피로</summary>
    <p>계획은 다음 날부터 적용됩니다. 날짜 마감과 검증된 실제 출전으로 성장합니다. 피로는 훈련 효율에만 영향을 주며 경기력 감점은 0입니다.</p>
    {error ? <p role="alert">{error}</p> : null}{notice ? <p role="status">{notice}</p> : null}
    {operation ? <button disabled={busy || pending || historical} onClick={() => void execute()}>원본 훈련 요청 다시 확인</button> : null}
    {!view || !roster ? <p>훈련 상태 불러오는 중…</p> : <>
      <p>기준일 {view.currentDate}{historical ? ' · 과거 시즌 마감 기록 (읽기 전용)' : ''}</p>
      <label><input type="checkbox" checked={all} onChange={e => setAll(e.target.checked)} /> 다른 구단·FA도 확인</label>
      <label>팀 기본 훈련 범위 <select value={squad} onChange={e => { setSquad(e.target.value as 'FIRST_TEAM' | 'DEVELOPMENT'); setSelected(''); }}><option value="FIRST_TEAM">1군</option><option value="DEVELOPMENT">CL·육성팀</option></select></label><label>훈련 대상 <select value={selected} onChange={e => setSelected(e.target.value)}><option value="">우리 팀 기본 계획</option>{visible.map(p => <option key={p.playerId} value={p.playerId}>{roster.directory.players[p.playerId]?.nickname ?? p.playerId} · CA {p.currentAbility} / PA {p.potentialAbility ?? '미입력'} · 피로 {(p.development.fatigue / 10).toFixed(1)}</option>)}</select></label>
      <p>현재 유효 계획: {intensityNames[player?.effectivePlan.intensity ?? teamPlan?.current?.intensity ?? 'NORMAL']} / {focusNames[player?.effectivePlan.focus ?? teamPlan?.current?.focus ?? 'BALANCED']}</p>
      {schedule?.pendingOn && schedule.pendingOn > view.currentDate ? <p>예약 적용일 {schedule.pendingOn}: {schedule.pendingClear ? '선수 설정 해제 → 팀 계획 따르기' : `${intensityNames[schedule.pending!.intensity]} / ${focusNames[schedule.pending!.focus]}`}</p> : null}
      {own ? <fieldset disabled={disabled}><legend>{selected ? '선수별 계획 설정' : '팀 기본 계획'}</legend>
        <label>훈련 강도 <select value={intensity} onChange={e => setIntensity(e.target.value as TrainingIntensity)}>{Object.entries(intensityNames).map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
        <label>훈련 초점 <select value={focus} onChange={e => setFocus(e.target.value as TrainingFocus)}>{Object.entries(focusNames).filter(([id]) => selected || !['SPECIFIC_SKILL', 'CHAMPION_FOCUS'].includes(id)).map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
        {focus === 'SPECIFIC_SKILL' ? <label>집중 능력치 <select value={skill} onChange={e => setSkill(e.target.value)}><option value="">항목 선택</option>{Object.keys(player?.development.internalRatings ?? {}).map(k => <option key={k} value={k}>{SKILL_LABELS[k] ?? k}</option>)}</select></label> : null}
        {focus === 'CHAMPION_FOCUS' ? <div><p>챔피언 1~2개를 선택하세요. 하루 숙련도 총예산을 나누며, 일반 능력치 훈련은 절반입니다.</p>{[0, 1].map(i => <label key={i}>집중 챔피언 {i + 1}<select value={champions[i] ?? ''} onChange={e => { const next = [...champions]; next[i] = e.target.value; setChampions(next.filter(Boolean)); }}><option value="">선택 안 함</option>{(view.legalChampions[definition?.position ?? ''] ?? []).map(c => <option key={c} value={c}>{c}</option>)}</select></label>)}</div> : null}
        <button disabled={invalidTargets} onClick={() => void execute()}>다음 날 훈련 저장</button>{selected ? <button onClick={() => void execute(true)}>선수 설정 해제 예약</button> : null}
      </fieldset> : <p>다른 구단의 선수입니다. 임대 중에는 임대팀이 훈련을 지휘합니다.</p>}
      <p>정상 훈련: 성장 기준 10 / 피로 +4. 강훈련: 성장 1.2배 / 피로 +15. 가볍게: 성장 0.5배 / 피로 +1.5. 회복: 성장 0 / 추가 회복 8. 일일 기본 회복 12, 실제 게임당 피로 +9.</p>
      {player ? <section aria-label="현재 성장 상태"><h3>{definition?.nickname} · CA {player.currentAbility} / PA {player.potentialAbility ?? '미입력'}</h3><p>{growthNames[player.growthStatus] ?? player.growthStatus}</p>
        <p>피로 {(player.development.fatigue / 10).toFixed(1)} / 100 · 훈련 효율 {(player.trainingEfficiency / 10).toFixed(1)}% · {player.development.fatigue > 300 ? '훈련 효율 저하 · 회복 권장' : '정상 훈련 가능'} · 경기력 감점 0, 자동 선발 교체 없음</p>
        <table><thead><tr><th>능력치</th><th>경기에 적용되는 정수</th><th>다음 1점 진행도</th></tr></thead><tbody>{Object.entries(player.development.internalRatings).map(([k, v]) => <tr key={k}><td>{SKILL_LABELS[k] ?? k}</td><td>{Math.floor(v / 1000)}</td><td>{v === 20000 ? '최대' : `+${((v % 1000) / 1000).toFixed(3)}`}</td></tr>)}</tbody></table>
        <p>최근 30일 내부 성장 +{(recent.reduce((n, g) => n + g.internalGain, 0) / 1000).toFixed(3)}점 합계 · 시즌 +{(monthly.reduce((n, g) => n + g.internalGain, 0) / 1000).toFixed(3)}점 합계. 소수 진행은 아직 경기 정수 능력치 상승이 아닙니다.</p>
        <ul>{recent.filter(g => Object.keys(g.integerRises).length).map(g => <li key={g.date}>{g.date} · {Object.entries(g.integerRises).map(([k, v]) => `${SKILL_LABELS[k] ?? k} +${v}`).join(', ')}</li>)}</ul>
        <details><summary>챔피언 숙련도 · 미사용 하락 없음</summary><p>표시값은 내림합니다. 20에 가까워질수록 성장 속도가 느려집니다.</p><ul>{Object.entries(player.development.internalProficiencies).map(([k, v]) => <li key={k}>{k.split('|')[0]} · {Math.floor(v / 1000)} / 20 {v < 20000 ? `(+${((v % 1000) / 1000).toFixed(3)} 진행)` : ''}</li>)}</ul></details>
        <details><summary>월간·시즌 성장 요약</summary><ul>{monthly.map(g => <li key={g.date}>{g.date.slice(0, 7)} · 능력치 합계 +{(g.internalGain / 1000).toFixed(3)} · 숙련도 합계 +{(g.proficiencyGain / 1000).toFixed(3)}</li>)}</ul></details>
      </section> : null}
    </>}
  </details>;
}

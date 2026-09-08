import { useEffect, useRef, useState } from 'react';
import { CareerApiFailure, commandCareerContinuous, getCareerContinuous } from './api/careerApi.client';
import type { CareerContinuousCommand, CareerContinuousView } from './api/careerApi.types';
const ACTIVE = ['RUNNING', 'WAITING', 'PAUSE_REQUESTED'];
const STATUS: Record<string, string> = { RUNNING: '진행 중', WAITING: 'AI 경기 처리 중', PAUSE_REQUESTED: '정지 요청 중 · 현재 작업 완료 대기', PAUSED: '일시정지 완료', STOPPED: '확인이 필요해 멈췄습니다', COMPLETED: '목표 날짜 처리 완료', FAILED: '진행 중 오류 발생' };
const NEXT_ACTION: Record<string, string> = { MATCH: '관리 경기로 이동', MARKET: '계약 시장에서 응답', ROSTER: '선수 명부 확인', SEASONS: '시즌 전환 확인' };
const REASON: Record<string, string> = {
  PLAYER_MATCH: '다음 관리 경기를 직접 시작하세요.', PLAYER_SERIES: '진행 중인 관리 경기를 계속하세요.', PLAYER_CHOICE: '경기 화면에서 상대 또는 진영을 선택하세요.',
  CONTRACT_RESPONSE: '계약 시장에서 선수의 역제안에 응답하세요.', TRADE_RESPONSE: '계약 시장에서 이적·임대 조건에 응답하세요.', ROSTER_DECISION: '선수단의 선발·등록을 확인하세요.', FINANCE_DECISION: '계약 시장에서 구단 재정을 확인하세요.',
  SEASON_TRANSITION: '시즌 마감 조건을 확인한 뒤 다음 시즌을 직접 시작하세요.', TARGET_REACHED: '목표 날짜의 필수 처리를 마쳤습니다.', USER_PAUSED: '원하는 작업을 마친 뒤 재개할 수 있습니다.', JOB_PENDING: '서버가 기존 경기의 완료와 결과 반영을 기다립니다.',
  NO_PROGRESS: '캘린더의 중단 사유를 확인한 뒤 재개하세요.', UNSUPPORTED_SAVE: '필수 저장 자료가 없어 진행할 수 없습니다.', EXECUTION_ERROR: '실행 오류로 중단되었습니다. 캘린더와 저장 상태를 확인하세요.', SAFETY_LIMIT: '안전 실행 한도에 도달했습니다. 재개하면 이어갑니다.', TARGET_REPAIR_BOUNDARY: 'AI 명부 복구에 다음 날짜가 필요합니다. 목표 날짜를 늘려 새로 시작하세요.', AI_ROSTER_REPAIR: 'AI 선수단 복구 상태를 계약 시장에서 확인하세요.',
};
export function CareerContinuousPanel({ careerId, currentDate, seasonYear, busy, onBusy, onStopped, onBegin, onAction }: {
  careerId: string; currentDate: string; seasonYear: number; busy: boolean; onBusy: (career: string, active: boolean) => void; onStopped: () => void; onBegin: () => (() => void) | null; onAction: (action: string) => void;
}) {
  const [view, setView] = useState<CareerContinuousView | null>(null);
  const [mode, setMode] = useState<'NEXT_MANAGED_MATCH' | 'TARGET_DATE'>('NEXT_MANAGED_MATCH');
  const [target, setTarget] = useState(currentDate); const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false); const [retry, setRetry] = useState(false);
  const readEpoch = useRef(0); const commandLock = useRef(false); const generation = useRef(0); const request = useRef<AbortController | null>(null);
  const latest = useRef(view); const callbacks = useRef({ onBusy, onStopped, onBegin }); callbacks.current = { onBusy, onStopped, onBegin };
  const operation = useRef<CareerContinuousCommand | null>(null);
  const storageKey = `career-continuous:${careerId}`;
  function accept(next: CareerContinuousView) {
    if (next.careerId !== careerId) throw new Error('Career 응답이 현재 저장과 다릅니다.');
    const previous = latest.current;
    if (previous?.run?.runId === next.run?.runId && (previous?.run?.revision ?? -1) > (next.run?.revision ?? -1)) return;
    latest.current = next; setView(next); callbacks.current.onBusy(careerId, !!next.run && ACTIVE.includes(next.run.status));
    if (previous?.run && ACTIVE.includes(previous.run.status) && next.run && !ACTIVE.includes(next.run.status)) callbacks.current.onStopped();
  }
  useEffect(() => {
    const token = ++generation.current; const controller = new AbortController(); let timer = 0;
    try { const stored = window.sessionStorage.getItem(storageKey); if (stored) { operation.current = JSON.parse(stored) as CareerContinuousCommand; setRetry(true); } } catch { setError('이전 요청을 읽지 못했습니다. 서버 상태를 먼저 확인하세요.'); }
    const poll = async () => {
      try { if (!commandLock.current) { const epoch = readEpoch.current; const next = await getCareerContinuous(careerId, controller.signal); if (epoch === readEpoch.current && token === generation.current && !controller.signal.aborted && !commandLock.current) { accept(next); if (!operation.current) setError(null); } } }
      catch (e) { if (!controller.signal.aborted) setError(e instanceof CareerApiFailure ? e.userMessage : '진행 상태를 확인하지 못했습니다.'); }
      finally { if (!controller.signal.aborted) timer = window.setTimeout(() => { void poll(); }, 2500); }
    };
    void poll(); return () => { ++generation.current; controller.abort(); request.current?.abort(); window.clearTimeout(timer); };
    // Mounted with a Career key. Callbacks are read through refs so polling is stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [careerId, storageKey]);
  async function send(action: CareerContinuousCommand['action']) {
    if (commandLock.current) return; const release = callbacks.current.onBegin(); if (!release) return;
    ++readEpoch.current; commandLock.current = true; setPending(true); setError(null); const token = generation.current; const controller = new AbortController(); request.current = controller;
    try {
      const body = operation.current ?? { schemaVersion: 'CAREER_CONTINUOUS_COMMAND_V1', action, clientCommandId: crypto.randomUUID(), runId: action === 'START' ? null : view!.run!.runId, expectedRevision: action === 'START' ? null : view!.run!.revision, mode: action === 'START' ? mode : null, targetDate: action === 'START' && mode === 'TARGET_DATE' ? target : null };
      operation.current = body; window.sessionStorage.setItem(storageKey, JSON.stringify(body));
      const response = await commandCareerContinuous(careerId, body, controller.signal);
      if (token !== generation.current || controller.signal.aborted) return;
      if (response.receipt.clientCommandId !== body.clientCommandId) throw new Error('요청 결과의 원본 명령이 다릅니다.');
      operation.current = null; window.sessionStorage.removeItem(storageKey); setRetry(false); accept(response.progress);
    } catch (e) {
      if (token !== generation.current || controller.signal.aborted) return;
      setError(e instanceof CareerApiFailure ? e.userMessage : e instanceof Error ? e.message : '요청 결과를 확인하지 못했습니다.');
      if (e instanceof CareerApiFailure && e.kind === 'BACKEND' && e.httpStatus !== 503) { operation.current = null; window.sessionStorage.removeItem(storageKey); setRetry(false); }
      else setRetry(true);
    } finally { commandLock.current = false; release(); if (token === generation.current) setPending(false); }
  }
  const run = view?.run;
  return <section className="ca-calendar ca-continuous" aria-label="Career 연속 진행">
    <header><div><span>연속 진행</span><strong>{view?.currentDate ?? currentDate}</strong></div><b role="status">{run ? STATUS[run.status] : '진행 대기'}</b></header>
    <p>AI 경기와 일별 정산을 자동 처리합니다. 내 경기·계약 응답·선발 결정·시즌 전환 전에 멈춥니다. 창을 닫아도 서버는 계속 진행합니다.</p>
    {run ? <p>목표: {run.mode === 'NEXT_MANAGED_MATCH' ? '다음 내 경기' : run.targetDate} · {run.completedDates}일 진행 · {run.completedSeries}경기 / {run.completedGames}세트 완료</p> : null}
    {run?.stop ? <p role="status">{REASON[run.stop.reason] ?? '캘린더에서 필요한 다음 행동을 확인하세요.'}</p> : null}
    {run?.stop?.nextAction && ['MATCH', 'MARKET', 'ROSTER', 'SEASONS'].includes(run.stop.nextAction) ? <p><button type="button" className="lm-secondary-button" disabled={pending || busy} onClick={() => onAction(run.stop!.nextAction!)}>{NEXT_ACTION[run.stop.nextAction]}</button></p> : null}
    {error ? <p role="alert">{error}</p> : null}
    <div className="ca-calendar__controls">
      <label>진행 모드 <select value={mode} disabled={pending || busy || !!run && ACTIVE.includes(run.status)} onChange={e => setMode(e.target.value as typeof mode)}><option value="NEXT_MANAGED_MATCH">다음 내 경기까지</option><option value="TARGET_DATE">지정 날짜까지</option></select></label>
      {mode === 'TARGET_DATE' ? <label>목표 날짜 <input type="date" value={target} min={view?.currentDate ?? currentDate} max={`${seasonYear}-12-31`} disabled={pending || busy || !!run && ACTIVE.includes(run.status)} onChange={e => setTarget(e.target.value)} /></label> : null}
      {retry ? <button type="button" className="lm-primary-button" disabled={pending || busy} onClick={() => { void send(operation.current?.action ?? 'START'); }}>원래 요청 결과 확인</button> : view?.allowedCommands.map(action => <button type="button" className="lm-primary-button" key={action} disabled={pending || busy || action === 'PAUSE' && run?.status === 'PAUSE_REQUESTED'} onClick={() => { void send(action); }}>{action === 'START' ? '연속 진행 시작' : action === 'PAUSE' ? '일시정지' : '재개'}</button>)}
    </div>
  </section>;
}

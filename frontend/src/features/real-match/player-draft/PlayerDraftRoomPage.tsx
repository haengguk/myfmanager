import { useCallback, useEffect, useRef, useState } from 'react';
import { MatchUtilityBar } from '../MatchChrome';
import type { MatchRequestStage } from '../api/realMatchApi.types';
import type { TeamSide } from '../realMatch.contract';
import {
  cancelPlayerDraftSession, PlayerDraftApiFailure, refreshPlayerDraftSession,
  simulatePlayerDraftMatch, submitPlayerDraftAction, type PlayerDraftSimulationResult,
} from './api/playerDraftApi.client';
import type { PlayerDraftSessionExpectation, PlayerDraftSessionResponseDto } from './api/playerDraftApi.types';
import { PlayerDraftCancelDialog } from './PlayerDraftCancelDialog';
import { PlayerDraftChampionWorkspace } from './PlayerDraftChampionWorkspace';
import { PlayerDraftDeveloperPanel } from './PlayerDraftDeveloperPanel';
import { PlayerDraftHeader } from './PlayerDraftHeader';
import { PlayerDraftTeamPanel } from './PlayerDraftTeamPanel';
import type { PlayerDraftScreenState } from './playerDraft.types';
import { playerDraftActionWasApplied, shouldApplyPlayerDraftSession } from './playerDraftSessionOrder';
import { markPlayerDraftLatency } from './playerDraftLatencyObserver';

const STAGE_LABELS: Readonly<Record<MatchRequestStage, string>> = {
  CONNECTING: '서버 계산 대기', DOWNLOADING: '응답 다운로드', PARSING: 'JSON 해석', VALIDATING: '응답 검증', NORMALIZING: '화면 데이터 준비',
};

function expectation(state: PlayerDraftScreenState): PlayerDraftSessionExpectation {
  return {
    sessionId: state.session.sessionId, blueTeamCode: state.selection.blueTeamId,
    redTeamCode: state.selection.redTeamId, controlledSide: state.session.controlledSide, seed: state.selection.seed,
  };
}

function teamForSide(state: PlayerDraftScreenState, side: TeamSide) {
  const id = side === 'BLUE' ? state.selection.blueTeamId : state.selection.redTeamId;
  const team = state.options.teams.find((candidate) => candidate.code === id);
  if (!team) throw new Error(`${side} 팀을 options에서 찾을 수 없습니다.`);
  return team;
}

export function PlayerDraftRoomPage({ state, onSessionChange, onSimulationComplete, onCancelled, onReviewBack }: {
  state: PlayerDraftScreenState;
  onSessionChange: (session: PlayerDraftSessionResponseDto) => void;
  onSimulationComplete: (simulation: PlayerDraftSimulationResult) => void;
  onCancelled: () => void;
  onReviewBack: () => void;
}) {
  const { session } = state;
  const [selectedChampionId, setSelectedChampionId] = useState<string | null>(null);
  const [logicalAction, setLogicalAction] = useState<{ turn: number; championId: string; clientActionId: string } | null>(null);
  const [actionPending, setActionPending] = useState(false); const [refreshPending, setRefreshPending] = useState(false);
  const [simulationPending, setSimulationPending] = useState(false); const [simulationStage, setSimulationStage] = useState<MatchRequestStage>('CONNECTING');
  const [statusMessage, setStatusMessage] = useState<string | null>(null); const [error, setError] = useState<string | null>(null);
  const [terminalFailure, setTerminalFailure] = useState<'NOT_FOUND' | 'EXPIRED' | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false); const [cancelPending, setCancelPending] = useState(false); const [cancelError, setCancelError] = useState<string | null>(null);
  const [developerView, setDeveloperView] = useState(false);
  const [cancelReturnFocus, setCancelReturnFocus] = useState<HTMLElement | null>(null);
  const [revealFrom, setRevealFrom] = useState(session.decisions.length);
  const previousDecisionCountRef = useRef(session.decisions.length);
  const actionControllerRef = useRef<AbortController | null>(null); const simulationControllerRef = useRef<AbortController | null>(null); const refreshControllerRef = useRef<AbortController | null>(null); const cancelControllerRef = useRef<AbortController | null>(null);
  const actionPendingRef = useRef(false); const simulationPendingRef = useRef(false); const refreshPendingRef = useRef(false); const cancelPendingRef = useRef(false);
  const sessionRef = useRef(session); const mountedRef = useRef(true);
  const pendingDomCorrelationRef = useRef<string | null>(null);
  const actionSequenceRef = useRef(0); const simulationSequenceRef = useRef(0); const refreshSequenceRef = useRef(0); const cancelSequenceRef = useRef(0);
  const blueTeam = teamForSide(state, 'BLUE'); const redTeam = teamForSide(state, 'RED');

  useEffect(() => { sessionRef.current = session; }, [session]);
  useEffect(() => {
    if (session.decisions.length > previousDecisionCountRef.current) setRevealFrom(previousDecisionCountRef.current);
    previousDecisionCountRef.current = session.decisions.length;
    setSelectedChampionId(null); setLogicalAction(null);
    const correlationId = pendingDomCorrelationRef.current;
    if (correlationId) {
      markPlayerDraftLatency('PLAYER_ACTION_DOM_STABLE', correlationId, {
        revision: session.revision, decisionCount: session.decisions.length,
        status: session.status, nextTurn: session.currentTurn?.turn ?? null,
      });
      pendingDomCorrelationRef.current = null;
    }
  }, [session.currentTurn?.turn, session.decisions.length]);
  useEffect(() => {
    // React StrictMode intentionally runs an extra setup/cleanup cycle in development.
    // Re-arm the lifecycle guard on every setup so valid responses are not discarded.
    mountedRef.current = true;
    return () => {
      mountedRef.current = false; actionSequenceRef.current += 1; simulationSequenceRef.current += 1; refreshSequenceRef.current += 1; cancelSequenceRef.current += 1;
      actionControllerRef.current?.abort(); simulationControllerRef.current?.abort(); refreshControllerRef.current?.abort(); cancelControllerRef.current?.abort();
    };
  }, []);

  const applySession = useCallback((next: PlayerDraftSessionResponseDto, correlationId: string | null = null) => {
    if (!mountedRef.current || !shouldApplyPlayerDraftSession(sessionRef.current, next)) return false;
    pendingDomCorrelationRef.current = correlationId;
    sessionRef.current = next; onSessionChange(next); return true;
  }, [onSessionChange]);

  const updateFailure = (failure: unknown) => {
    if (failure instanceof PlayerDraftApiFailure) {
      if (failure.code === 'PLAYER_DRAFT_SESSION_NOT_FOUND') setTerminalFailure('NOT_FOUND');
      if (failure.code === 'PLAYER_DRAFT_SESSION_EXPIRED') setTerminalFailure('EXPIRED');
      setError(failure.userMessage); return failure;
    }
    setError('직접 밴픽 요청을 완료하지 못했습니다. 서버 연결 상태를 확인하세요.'); return null;
  };

  const refresh = useCallback(async (reason?: string, duringAction = false): Promise<PlayerDraftSessionResponseDto | null> => {
    if (refreshPendingRef.current || (!duringAction && (actionPendingRef.current || simulationPendingRef.current || cancelPendingRef.current))) return null;
    const requestSequence = ++refreshSequenceRef.current;
    const controller = new AbortController(); refreshControllerRef.current = controller; refreshPendingRef.current = true; setRefreshPending(true); setError(null);
    try {
      const latest = await refreshPlayerDraftSession(expectation(state), controller.signal);
      if (controller.signal.aborted || requestSequence !== refreshSequenceRef.current) return null;
      if (applySession(latest)) setStatusMessage(reason ?? '서버의 최신 Draft 상태를 반영했습니다.');
      return latest;
    } catch (refreshError) {
      if (requestSequence === refreshSequenceRef.current && !controller.signal.aborted) updateFailure(refreshError);
      return null;
    } finally {
      if (refreshControllerRef.current === controller) refreshControllerRef.current = null;
      if (requestSequence === refreshSequenceRef.current) { refreshPendingRef.current = false; setRefreshPending(false); }
    }
  }, [applySession, state]);

  const submit = async () => {
    const currentSession = sessionRef.current;
    if (!currentSession.currentTurn || !selectedChampionId || actionPendingRef.current || simulationPendingRef.current || cancelPendingRef.current || refreshPendingRef.current) return;
    const pending = logicalAction?.turn === currentSession.currentTurn.turn && logicalAction.championId === selectedChampionId
      ? logicalAction : { turn: currentSession.currentTurn.turn, championId: selectedChampionId, clientActionId: crypto.randomUUID() };
    markPlayerDraftLatency('PLAYER_ACTION_CONFIRM_INPUT', pending.clientActionId, { turn: currentSession.currentTurn.turn, championId: selectedChampionId });
    markPlayerDraftLatency('PLAYER_ACTION_REQUEST_PREPARED', pending.clientActionId, { turn: pending.turn, championId: pending.championId, expectedRevision: currentSession.revision });
    setLogicalAction(pending); actionPendingRef.current = true; setActionPending(true); setError(null); setStatusMessage('선택을 제출했습니다. 상대 AI가 다음 내 턴까지 계산하고 있습니다.');
    const requestSequence = ++actionSequenceRef.current;
    const controller = new AbortController(); actionControllerRef.current = controller;
    try {
      const next = await submitPlayerDraftAction(expectation(state), {
        schemaVersion: 'PLAYER_DRAFT_ACTION_REQUEST_V1', expectedRevision: currentSession.revision,
        clientActionId: pending.clientActionId, championId: pending.championId,
      }, controller.signal);
      if (controller.signal.aborted || requestSequence !== actionSequenceRef.current) return;
      if (applySession(next, pending.clientActionId)) {
        setLogicalAction(null); setSelectedChampionId(null);
        setStatusMessage(next.status === 'COMPLETED' ? '20턴 Draft가 완료되었습니다. 최종 포지션 배치를 확인하세요.' : `AI 응답을 반영했습니다. TURN ${next.currentTurn?.turn ?? next.decisions.length}에서 선택하세요.`);
      }
    } catch (submitError) {
      if (requestSequence !== actionSequenceRef.current || !mountedRef.current) return;
      const failure = updateFailure(submitError);
      let latest: PlayerDraftSessionResponseDto | null = null;
      if (failure?.code === 'STALE_DRAFT_REVISION') latest = await refresh('최신 Draft 상태로 갱신했습니다. 현재 턴을 다시 확인하세요.', true);
      if (failure && ['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind)) latest = await refresh('응답 수신이 끊겨 서버 반영 상태를 다시 확인했습니다.', true);
      if (latest && playerDraftActionWasApplied(latest.decisions, pending.clientActionId)) {
        setLogicalAction(null); setSelectedChampionId(null); setError(null);
        setStatusMessage(latest.status === 'COMPLETED' ? '서버에서 마지막 선택 반영과 Draft 완료를 확인했습니다.' : '서버에서 선택 반영을 확인했습니다. 다음 차례를 진행하세요.');
      }
      if (failure?.code === 'CLIENT_ACTION_ID_PAYLOAD_CONFLICT' || failure?.code === 'ILLEGAL_DRAFT_SELECTION') setLogicalAction(null);
    } finally {
      if (actionControllerRef.current === controller) actionControllerRef.current = null;
      if (requestSequence === actionSequenceRef.current) { actionPendingRef.current = false; setActionPending(false); }
    }
  };

  const simulate = async () => {
    const currentSession = sessionRef.current;
    if (simulationPendingRef.current || actionPendingRef.current || cancelPendingRef.current || refreshPendingRef.current || currentSession.status !== 'COMPLETED') return;
    markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_CLICK', currentSession.sessionId, { revision: currentSession.revision, decisionCount: currentSession.decisions.length });
    const requestSequence = ++simulationSequenceRef.current;
    const controller = new AbortController(); simulationControllerRef.current = controller; simulationPendingRef.current = true; setSimulationPending(true); setSimulationStage('CONNECTING'); setError(null);
    try {
      const simulation = await simulatePlayerDraftMatch(expectation(state), controller.signal, setSimulationStage);
      if (controller.signal.aborted || requestSequence !== simulationSequenceRef.current || !shouldApplyPlayerDraftSession(currentSession, simulation.response.session)) return;
      sessionRef.current = simulation.response.session; setSimulationStage('NORMALIZING'); onSimulationComplete(simulation);
    } catch (simulationError) { if (requestSequence === simulationSequenceRef.current && mountedRef.current) updateFailure(simulationError); }
    finally {
      if (simulationControllerRef.current === controller) simulationControllerRef.current = null;
      if (requestSequence === simulationSequenceRef.current) { simulationPendingRef.current = false; setSimulationPending(false); }
    }
  };

  const stopSimulationResponse = () => {
    simulationSequenceRef.current += 1; simulationControllerRef.current?.abort(); simulationControllerRef.current = null;
    simulationPendingRef.current = false; setSimulationPending(false);
    setStatusMessage('응답 수신을 중단했습니다. 서버 계산은 완료되었을 수 있으며 같은 세션으로 다시 확인할 수 있습니다.');
  };

  const requestBack = () => {
    if (session.status === 'SIMULATED') { onReviewBack(); return; }
    if (actionPendingRef.current || simulationPendingRef.current || refreshPendingRef.current || cancelPendingRef.current) { setStatusMessage('진행 중인 응답을 먼저 확인하세요. 요청 중단이 서버의 작업 취소를 뜻하지 않습니다.'); return; }
    setCancelReturnFocus(document.activeElement as HTMLElement); setCancelError(null); setCancelOpen(true);
  };
  const cancel = async () => {
    if (cancelPendingRef.current || actionPendingRef.current || simulationPendingRef.current) return;
    const requestSequence = ++cancelSequenceRef.current; const controller = new AbortController(); cancelControllerRef.current = controller; cancelPendingRef.current = true; setCancelPending(true); setCancelError(null);
    try {
      await cancelPlayerDraftSession(sessionRef.current.sessionId, controller.signal);
      if (controller.signal.aborted || requestSequence !== cancelSequenceRef.current) return;
      setCancelOpen(false); onCancelled();
    } catch (cancelFailure) {
      if (requestSequence !== cancelSequenceRef.current || !mountedRef.current) return;
      const failure = cancelFailure instanceof PlayerDraftApiFailure ? cancelFailure.userMessage : 'Draft 취소 여부를 확인하지 못했습니다.'; setCancelError(`${failure} 세션은 만료 시점까지 서버에 남아 있을 수 있습니다.`);
    } finally {
      if (cancelControllerRef.current === controller) cancelControllerRef.current = null;
      if (requestSequence === cancelSequenceRef.current) { cancelPendingRef.current = false; setCancelPending(false); }
    }
  };

  const completed = session.completedDraft;
  if (terminalFailure) return (
    <div className="rm-draft-app pd-terminal-app"><MatchUtilityBar meta="직접 Draft · 세션 종료" onBack={onCancelled} /><main className="pd-terminal-state"><span aria-hidden="true">!</span><h1>{terminalFailure === 'EXPIRED' ? 'Draft 세션이 만료되었습니다' : 'Draft 세션을 찾을 수 없습니다'}</h1><p>로컬 상태로 결과를 추측하지 않았습니다. 경기 설정에서 새 직접 밴픽을 시작하세요.</p><button className="rm-primary-action" type="button" onClick={onCancelled}>경기 설정으로 이동</button></main></div>
  );

  return (
    <div className="rm-draft-app pd-draft-app">
      <MatchUtilityBar meta={`02_DRAFT_ROOM · 직접 Draft · seed ${session.seed}`} backLabel={session.status === 'SIMULATED' ? '재생/결과로 돌아가기' : '경기 설정으로 돌아가기'} onBack={requestBack}
        secondaryLabel={developerView ? 'Draft 화면' : '개발자 확인'} onSecondary={() => setDeveloperView((current) => !current)} />
      <PlayerDraftHeader session={session} options={state.options} blueTeam={blueTeam} redTeam={redTeam} catalog={state.championsById} />
      <div className="lm-sr-only" role="status" aria-live="polite" aria-atomic="true">{actionPending || refreshPending ? '상대 AI와 최신 Draft 상태를 확인하고 있습니다.' : statusMessage}</div>
      {error ? <div className="lm-sr-only" role="alert" aria-live="assertive">{error}</div> : null}
      {developerView ? <PlayerDraftDeveloperPanel session={session} catalog={state.championsById} revealFrom={revealFrom} /> : <main className="rm-draft-stage">
        <PlayerDraftTeamPanel side="BLUE" teamCode={blueTeam.code} roster={blueTeam.roster} bans={session.state.blueBans} picks={session.state.bluePicks} controlledSide={session.controlledSide} catalog={state.championsById} completedDraft={completed} />
        {session.status === 'ACTIVE' && session.currentTurn ? (
          <PlayerDraftChampionWorkspace catalog={state.championsById} currentTurn={session.currentTurn} decisionCount={session.decisions.length} selectedChampionId={selectedChampionId} busy={actionPending || refreshPending} error={error} onSelect={(championId) => { setSelectedChampionId(championId); if (logicalAction?.championId !== championId) setLogicalAction(null); setError(null); }} onConfirm={submit} onRefresh={() => refresh()} />
        ) : (
          <section className="pd-completed-workspace" aria-labelledby="pd-completed-title">
            <span className="pd-complete-mark" aria-hidden="true">✓</span><p>20 / 20 · MIXED AUTHORITY</p>
            <h1 id="pd-completed-title">Draft 완료</h1>
            <p>양 팀의 최종 포지션 배치와 PLAYER/AI 결정 기록이 확정되었습니다. 이 Draft 그대로 Production V9 경기를 실행합니다.</p>
            {completed ? <dl><div><dt>Draft identity</dt><dd title={completed.draftIdentity}>{completed.draftIdentity.slice(0, 12)}…</dd></div><div><dt>Control evidence</dt><dd title={completed.controlEvidenceHash}>{completed.controlEvidenceHash.slice(0, 12)}…</dd></div><div><dt>내 진영</dt><dd>{session.controlledSide} · {session.controlledSide === 'BLUE' ? blueTeam.code : redTeam.code}</dd></div></dl> : null}
            {session.status === 'COMPLETED' ? <div className="pd-simulate-actions"><button className="rm-primary-action" type="button" disabled={simulationPending} onClick={simulate}>{simulationPending ? <><span className="rm-spinner" aria-hidden="true" />{STAGE_LABELS[simulationStage]}</> : 'Production V9 경기 실행'}</button>{simulationPending ? <button className="rm-secondary-action" type="button" onClick={stopSimulationResponse}>응답 수신 중단</button> : null}<small>{simulationPending ? '중단해도 서버 계산은 완료되었을 수 있습니다. 같은 세션으로 다시 실행할 수 있습니다.' : 'Draft를 다시 수행하지 않고 현재 completed session을 실행합니다.'}</small></div> : <button className="rm-primary-action" type="button" onClick={onReviewBack}>경기 재생으로 돌아가기</button>}
            {error ? <div className="pd-complete-error" role="alert"><p>{error}</p>{session.status === 'COMPLETED' ? <button type="button" onClick={simulate}>같은 세션으로 다시 시도</button> : null}</div> : null}
          </section>
        )}
        <PlayerDraftTeamPanel side="RED" teamCode={redTeam.code} roster={redTeam.roster} bans={session.state.redBans} picks={session.state.redPicks} controlledSide={session.controlledSide} catalog={state.championsById} completedDraft={completed} />
      </main>}
      <PlayerDraftCancelDialog open={cancelOpen} teamCode={session.controlledSide === 'BLUE' ? blueTeam.code : redTeam.code} pending={cancelPending} error={cancelError} returnFocus={cancelReturnFocus} onClose={() => setCancelOpen(false)} onConfirm={cancel} />
    </div>
  );
}

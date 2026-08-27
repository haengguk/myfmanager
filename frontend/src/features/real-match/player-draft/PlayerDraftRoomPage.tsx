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
import { PlayerDraftHistory } from './PlayerDraftHistory';
import { PlayerDraftTeamPanel } from './PlayerDraftTeamPanel';
import type { PlayerDraftScreenState } from './playerDraft.types';

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
  const [cancelReturnFocus, setCancelReturnFocus] = useState<HTMLElement | null>(null);
  const [revealFrom, setRevealFrom] = useState(session.decisions.length);
  const previousDecisionCountRef = useRef(session.decisions.length);
  const actionControllerRef = useRef<AbortController | null>(null); const simulationControllerRef = useRef<AbortController | null>(null); const refreshControllerRef = useRef<AbortController | null>(null); const cancelControllerRef = useRef<AbortController | null>(null);
  const actionPendingRef = useRef(false); const simulationPendingRef = useRef(false);
  const blueTeam = teamForSide(state, 'BLUE'); const redTeam = teamForSide(state, 'RED');

  useEffect(() => {
    if (session.decisions.length > previousDecisionCountRef.current) setRevealFrom(previousDecisionCountRef.current);
    previousDecisionCountRef.current = session.decisions.length;
    setSelectedChampionId(null); setLogicalAction(null);
  }, [session.currentTurn?.turn, session.decisions.length]);
  useEffect(() => () => {
    actionControllerRef.current?.abort(); simulationControllerRef.current?.abort(); refreshControllerRef.current?.abort(); cancelControllerRef.current?.abort();
  }, []);

  const updateFailure = (failure: unknown) => {
    if (failure instanceof PlayerDraftApiFailure) {
      if (failure.code === 'PLAYER_DRAFT_SESSION_NOT_FOUND') setTerminalFailure('NOT_FOUND');
      if (failure.code === 'PLAYER_DRAFT_SESSION_EXPIRED') setTerminalFailure('EXPIRED');
      setError(failure.userMessage); return failure;
    }
    setError('직접 밴픽 요청을 완료하지 못했습니다. 서버 연결 상태를 확인하세요.'); return null;
  };

  const refresh = useCallback(async (reason?: string) => {
    if (refreshPending) return;
    const controller = new AbortController(); refreshControllerRef.current = controller; setRefreshPending(true); setError(null);
    try {
      const latest = await refreshPlayerDraftSession(expectation(state), controller.signal); onSessionChange(latest);
      setStatusMessage(reason ?? '서버의 최신 Draft 상태를 반영했습니다.'); setLogicalAction(null);
    } catch (refreshError) { updateFailure(refreshError); }
    finally { if (refreshControllerRef.current === controller) refreshControllerRef.current = null; setRefreshPending(false); }
  }, [onSessionChange, refreshPending, state]);

  const submit = async () => {
    if (!session.currentTurn || !selectedChampionId || actionPendingRef.current) return;
    const pending = logicalAction?.turn === session.currentTurn.turn && logicalAction.championId === selectedChampionId
      ? logicalAction : { turn: session.currentTurn.turn, championId: selectedChampionId, clientActionId: crypto.randomUUID() };
    setLogicalAction(pending); actionPendingRef.current = true; setActionPending(true); setError(null); setStatusMessage('선택을 제출했습니다. 상대 AI가 다음 내 턴까지 계산하고 있습니다.');
    const controller = new AbortController(); actionControllerRef.current = controller;
    try {
      const next = await submitPlayerDraftAction(expectation(state), {
        schemaVersion: 'PLAYER_DRAFT_ACTION_REQUEST_V1', expectedRevision: session.revision,
        clientActionId: pending.clientActionId, championId: pending.championId,
      }, controller.signal);
      onSessionChange(next); setLogicalAction(null); setSelectedChampionId(null);
      setStatusMessage(next.status === 'COMPLETED' ? '20턴 Draft가 완료되었습니다. 최종 포지션 배치를 확인하세요.' : `AI 응답을 반영했습니다. TURN ${next.currentTurn?.turn ?? next.decisions.length}에서 선택하세요.`);
    } catch (submitError) {
      const failure = updateFailure(submitError);
      if (failure?.code === 'STALE_DRAFT_REVISION') await refresh('최신 Draft 상태로 갱신했습니다. 현재 턴을 다시 확인하세요.');
      if (failure?.code === 'CLIENT_ACTION_ID_PAYLOAD_CONFLICT' || failure?.code === 'ILLEGAL_DRAFT_SELECTION') setLogicalAction(null);
    } finally {
      if (actionControllerRef.current === controller) actionControllerRef.current = null;
      actionPendingRef.current = false; setActionPending(false);
    }
  };

  const simulate = async () => {
    if (simulationPendingRef.current || session.status !== 'COMPLETED') return;
    const controller = new AbortController(); simulationControllerRef.current = controller; simulationPendingRef.current = true; setSimulationPending(true); setSimulationStage('CONNECTING'); setError(null);
    try {
      const simulation = await simulatePlayerDraftMatch(expectation(state), controller.signal, setSimulationStage);
      setSimulationStage('NORMALIZING'); onSimulationComplete(simulation);
    } catch (simulationError) { updateFailure(simulationError); }
    finally { if (simulationControllerRef.current === controller) simulationControllerRef.current = null; simulationPendingRef.current = false; setSimulationPending(false); }
  };

  const requestBack = () => {
    if (session.status === 'SIMULATED') { onReviewBack(); return; }
    if (actionPending || simulationPending) { setStatusMessage('진행 중인 응답을 먼저 확인하세요. 요청 중단이 서버의 작업 취소를 뜻하지 않습니다.'); return; }
    setCancelReturnFocus(document.activeElement as HTMLElement); setCancelError(null); setCancelOpen(true);
  };
  const cancel = async () => {
    if (cancelPending) return; const controller = new AbortController(); cancelControllerRef.current = controller; setCancelPending(true); setCancelError(null);
    try { await cancelPlayerDraftSession(session.sessionId, controller.signal); setCancelOpen(false); onCancelled(); }
    catch (cancelFailure) { const failure = cancelFailure instanceof PlayerDraftApiFailure ? cancelFailure.userMessage : 'Draft 취소 여부를 확인하지 못했습니다.'; setCancelError(`${failure} 세션은 만료 시점까지 서버에 남아 있을 수 있습니다.`); }
    finally { if (cancelControllerRef.current === controller) cancelControllerRef.current = null; setCancelPending(false); }
  };

  const completed = session.completedDraft;
  const statusLabel = session.status === 'ACTIVE' ? '진행 중' : session.status === 'COMPLETED' ? 'Draft 완료' : session.status === 'SIMULATED' ? '경기 실행 완료' : session.status;
  if (terminalFailure) return (
    <div className="pd-draft-app"><MatchUtilityBar meta="직접 Draft · 세션 종료" onBack={onCancelled} /><main className="pd-terminal-state"><span aria-hidden="true">!</span><h1>{terminalFailure === 'EXPIRED' ? 'Draft 세션이 만료되었습니다' : 'Draft 세션을 찾을 수 없습니다'}</h1><p>로컬 상태로 결과를 추측하지 않았습니다. 경기 설정에서 새 직접 밴픽을 시작하세요.</p><button className="rm-primary-action" type="button" onClick={onCancelled}>경기 설정으로 이동</button></main></div>
  );

  return (
    <div className="pd-draft-app">
      <MatchUtilityBar meta={`직접 Draft · Game 1 · seed ${session.seed}`} backLabel={session.status === 'SIMULATED' ? '재생/결과로 돌아가기' : '경기 설정으로 돌아가기'} onBack={requestBack} />
      <header className="pd-draft-header">
        <div><span>PLAYER_CONTROLLED · {session.controlledSide}</span><h1>{blueTeam.code} <i>vs</i> {redTeam.code}</h1><p>{session.controlledSide === 'BLUE' ? blueTeam.code : redTeam.code}의 10개 결정을 직접 수행합니다.</p></div>
        <div className="pd-header-progress"><span>TURN</span><strong>{session.currentTurn?.turn ?? session.decisions.length}<small>/20</small></strong><div><i style={{ width: `${session.decisions.length / 20 * 100}%` }} /></div></div>
        <dl><div><dt>현재 행동</dt><dd>{session.currentTurn ? actionPending ? '상대 AI 처리 중' : `${session.currentTurn.actionType === 'BAN' ? '밴' : '픽'} · 내 선택` : '모든 결정 완료'}</dd></div><div><dt>세션 상태</dt><dd>{statusLabel}</dd></div><div><dt>Revision</dt><dd>{session.revision}</dd></div></dl>
      </header>
      {(statusMessage || error || actionPending || refreshPending) ? <div className={`pd-network-status${error ? ' is-error' : ''}`} role={error ? 'alert' : 'status'} aria-live="polite"><span className={(actionPending || refreshPending) ? 'rm-spinner' : ''} aria-hidden="true" /><p>{error ?? (actionPending ? '상대 AI가 다음 수를 계산하고 있습니다. 이 화면을 닫아도 서버 작업이 되돌려진다고 가정하지 않습니다.' : statusMessage)}</p>{!actionPending && !refreshPending && session.status === 'ACTIVE' ? <button type="button" onClick={() => refresh()}>최신 상태 확인</button> : null}</div> : null}
      <main className="pd-draft-stage">
        <PlayerDraftTeamPanel side="BLUE" teamCode={blueTeam.code} roster={blueTeam.roster} bans={session.state.blueBans} picks={session.state.bluePicks} controlledSide={session.controlledSide} catalog={state.championsById} completedDraft={completed} />
        {session.status === 'ACTIVE' && session.currentTurn ? (
          <PlayerDraftChampionWorkspace catalog={state.championsById} recommendations={session.advisoryRecommendations} currentTurn={session.currentTurn} selectedChampionId={selectedChampionId} busy={actionPending} onSelect={(championId) => { setSelectedChampionId(championId); if (logicalAction?.championId !== championId) setLogicalAction(null); setError(null); }} onConfirm={submit} />
        ) : (
          <section className="pd-completed-workspace" aria-labelledby="pd-completed-title">
            <span className="pd-complete-mark" aria-hidden="true">✓</span><p>20 / 20 · MIXED AUTHORITY</p>
            <h1 id="pd-completed-title">Draft 완료</h1>
            <p>양 팀의 최종 포지션 배치와 PLAYER/AI 결정 기록이 확정되었습니다. 이 Draft 그대로 Production V9 경기를 실행합니다.</p>
            {completed ? <dl><div><dt>Draft identity</dt><dd title={completed.draftIdentity}>{completed.draftIdentity.slice(0, 12)}…</dd></div><div><dt>Control evidence</dt><dd title={completed.controlEvidenceHash}>{completed.controlEvidenceHash.slice(0, 12)}…</dd></div><div><dt>내 진영</dt><dd>{session.controlledSide} · {session.controlledSide === 'BLUE' ? blueTeam.code : redTeam.code}</dd></div></dl> : null}
            {session.status === 'COMPLETED' ? <div className="pd-simulate-actions"><button className="rm-primary-action" type="button" disabled={simulationPending} onClick={simulate}>{simulationPending ? <><span className="rm-spinner" aria-hidden="true" />{STAGE_LABELS[simulationStage]}</> : 'Production V9 경기 실행'}</button>{simulationPending ? <button className="rm-secondary-action" type="button" onClick={() => simulationControllerRef.current?.abort()}>응답 수신 중단</button> : null}<small>{simulationPending ? '중단해도 서버 계산은 완료되었을 수 있습니다. 같은 세션으로 다시 실행할 수 있습니다.' : 'Draft를 다시 수행하지 않고 현재 completed session을 실행합니다.'}</small></div> : <button className="rm-primary-action" type="button" onClick={onReviewBack}>경기 재생으로 돌아가기</button>}
            {error ? <div className="pd-complete-error" role="alert"><p>{error}</p>{session.status === 'COMPLETED' ? <button type="button" onClick={simulate}>같은 세션으로 다시 시도</button> : null}</div> : null}
          </section>
        )}
        <PlayerDraftTeamPanel side="RED" teamCode={redTeam.code} roster={redTeam.roster} bans={session.state.redBans} picks={session.state.redPicks} controlledSide={session.controlledSide} catalog={state.championsById} completedDraft={completed} />
      </main>
      <PlayerDraftHistory decisions={session.decisions} catalog={state.championsById} revealFrom={revealFrom} />
      <PlayerDraftCancelDialog open={cancelOpen} teamCode={session.controlledSide === 'BLUE' ? blueTeam.code : redTeam.code} pending={cancelPending} error={cancelError} returnFocus={cancelReturnFocus} onClose={() => setCancelOpen(false)} onConfirm={cancel} />
    </div>
  );
}

import { useEffect, useRef, useState } from 'react';
import { MatchUtilityBar } from '../MatchChrome';
import { SeriesApiFailure, cancelSeries, createSeriesDraft, getSeries } from './api/seriesApi.client';
import type { SeriesChildDraftEnvelopeDto, SeriesViewDto } from './api/seriesApi.types';
import { SeriesCancelDialog } from './SeriesCancelDialog';
import { SeriesContextBar } from './SeriesContextBar';
import type { SeriesScreenState } from './series.types';

const TERMINAL_COPY: Readonly<Record<Exclude<SeriesViewDto['status'], 'ACTIVE'>, string>> = {
  BLOCKED: '서버의 무결성 또는 피어리스 규칙으로 다음 진행이 차단되었습니다.',
  COMPLETED: '필요 승수에 도달해 시리즈가 완료되었습니다.',
  CANCELLED: '사용자 요청으로 시리즈가 취소되었습니다.',
  EXPIRED: 'process-local TTL이 지나 시리즈가 만료되었습니다.',
};

export function SeriesHubPage({ state, onBack, onStateChange, onStartDraft, onOpenGame, onNewSeries }: {
  state: SeriesScreenState;
  onBack: () => void;
  onStateChange: (series: SeriesViewDto, draft: SeriesChildDraftEnvelopeDto | null) => void;
  onStartDraft: () => void;
  onOpenGame: (gameNumber: number) => void;
  onNewSeries: () => void;
}) {
  const [pending, setPending] = useState<'DRAFT' | 'REFRESH' | 'CANCEL' | null>(null);
  const [error, setError] = useState<string | null>(null); const [cancelOpen, setCancelOpen] = useState(false);
  const [returnFocus, setReturnFocus] = useState<HTMLElement | null>(null); const controllerRef = useRef<AbortController | null>(null);
  const draftCommandRef = useRef<{ revision: number; id: string } | null>(null);
  const commands = new Set(state.series.allowedCommands); const current = state.series.games[state.series.games.length - 1];
  const winner = state.series.winnerTeamCode;

  useEffect(() => () => controllerRef.current?.abort(), []);

  const run = async (kind: typeof pending, operation: (signal: AbortSignal) => Promise<void>) => {
    if (pending) return; const controller = new AbortController(); controllerRef.current = controller; setPending(kind); setError(null);
    try { await operation(controller.signal); }
    catch (cause) { setError(cause instanceof SeriesApiFailure ? cause.userMessage : '시리즈 요청을 완료하지 못했습니다.'); }
    finally { if (controllerRef.current === controller) controllerRef.current = null; setPending(null); }
  };
  const createDraft = () => run('DRAFT', async (signal) => {
    const logical = draftCommandRef.current?.revision === state.series.revision
      ? draftCommandRef.current : { revision: state.series.revision, id: crypto.randomUUID() };
    draftCommandRef.current = logical;
    try {
      const response = await createSeriesDraft(state.series.seriesId, {
        schemaVersion: 'SERIES_DRAFT_SESSION_CREATE_REQUEST_V1', expectedRevision: logical.revision, clientCommandId: logical.id,
      }, signal);
      draftCommandRef.current = null; onStateChange(response.series, response.draftSession); onStartDraft();
    } catch (cause) {
      if (!(cause instanceof SeriesApiFailure) || !['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(cause.kind)) draftCommandRef.current = null;
      throw cause;
    }
  });
  const refresh = () => run('REFRESH', async (signal) => {
    const series = await getSeries(state.series.seriesId, signal); onStateChange(series, series.activeDraftSession);
  });
  const confirmCancel = () => run('CANCEL', async (signal) => {
    await cancelSeries(state.series.seriesId, {
      schemaVersion: 'SERIES_CANCEL_REQUEST_V1', expectedRevision: state.series.revision, clientCommandId: crypto.randomUUID(),
    }, signal);
    const series = await getSeries(state.series.seriesId, signal); onStateChange(series, series.activeDraftSession); setCancelOpen(false);
  });

  return <div className="sr-hub-app" aria-busy={pending !== null}>
    <MatchUtilityBar meta={`${state.series.format} · ${state.series.seriesId.slice(0, 18)}…`} onBack={onBack} backLabel="경기 센터로 돌아가기" />
    <SeriesContextBar series={state.series} catalog={state.championsById} onOpenGame={onOpenGame} />
    <main className="sr-hub-stage">
      <section className="sr-current-game" aria-labelledby="sr-current-title">
        <span>현재 작업</span><h2 id="sr-current-title">Game {current.gameNumber}</h2>
        <p><b className="is-blue">{current.blueTeamCode} BLUE</b><i>vs</i><b className="is-red">{current.redTeamCode} RED</b></p>
        <dl><div><dt>내 진영</dt><dd>{current.controlledSide}</dd></div><div><dt>Game seed</dt><dd>{current.matchSeed}</dd></div><div><dt>Series revision</dt><dd>{state.series.revision}</dd></div></dl>
        {state.series.status === 'ACTIVE' ? <>
          {commands.has('CREATE_DRAFT_SESSION') ? <button className="rm-primary-action" type="button" disabled={pending !== null} onClick={createDraft}>{pending === 'DRAFT' ? 'Draft 생성 중…' : `Game ${current.gameNumber} 직접 Draft 시작`}</button> : null}
          {state.series.activeDraftSession && ['ACTIVE', 'COMPLETED'].includes(state.series.activeDraftSession.session.status)
            ? <button className="rm-primary-action" type="button" disabled={pending !== null} onClick={onStartDraft}>{state.series.activeDraftSession.session.status === 'COMPLETED' ? '완료된 Draft에서 경기 실행' : '진행 중인 Draft 계속'}</button> : null}
          {state.series.reservation ? <div className="sr-reservation" role="status"><span className="rm-spinner" aria-hidden="true" /><p><strong>서버 계산 진행 중</strong>lease 만료 {new Date(state.series.reservation.leaseExpiresAt).toLocaleTimeString('ko-KR')}</p></div> : null}
          {!commands.has('CREATE_DRAFT_SESSION') && !state.series.activeDraftSession && !state.series.reservation ? <p className="sr-disabled-copy">서버가 현재 새 Draft 명령을 허용하지 않습니다. 최신 상태를 확인하세요.</p> : null}
        </> : <div className={`sr-terminal is-${state.series.status.toLowerCase()}`}><strong>{winner ? `${winner} 시리즈 승리` : state.series.status === 'BLOCKED' ? '시리즈 진행 차단' : '시리즈 종료'}</strong><p>{TERMINAL_COPY[state.series.status]}</p>{state.series.terminalReason ? <small>{state.series.terminalReason}</small> : null}</div>}
      </section>
      <aside className="sr-hub-actions" aria-label="시리즈 작업">
        <h2>시리즈 관리</h2><p>점수·진영·다음 game·피어리스 제외는 최신 서버 view만 사용합니다.</p>
        <button type="button" disabled={pending !== null} onClick={refresh}>{pending === 'REFRESH' ? '확인 중…' : '서버 상태 새로고침'}</button>
        {commands.has('CANCEL_SERIES') ? <button type="button" className="is-danger" disabled={pending !== null} onClick={(event) => { setReturnFocus(event.currentTarget); setCancelOpen(true); }}>시리즈 전체 취소</button> : null}
        {['COMPLETED', 'CANCELLED', 'EXPIRED'].includes(state.series.status) ? <button className="rm-primary-action" type="button" onClick={onNewSeries}>새 시리즈 시작</button> : null}
        <small>페이지를 닫아도 자동 취소하지 않습니다. 같은 탭 새로고침은 Series ID로 서버 상태를 복구합니다.</small>
      </aside>
      {error ? <div className="sr-hub-error" role="alert"><strong>요청을 완료하지 못했습니다</strong><p>{error}</p></div> : null}
    </main>
    <SeriesCancelDialog open={cancelOpen} pending={pending === 'CANCEL'} error={pending === 'CANCEL' ? error : null} returnFocus={returnFocus} onClose={() => setCancelOpen(false)} onConfirm={confirmCancel} />
  </div>;
}

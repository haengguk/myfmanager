import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MatchCenter from './App';
import { ProgressModal } from './components/ProgressModal';
import { Toast } from './components/Toast';
import { inboxMessages } from './features/inbox/inbox.fixtures';
import { InboxPage } from './features/inbox/InboxPage';
import type { ToastMessage } from './features/inbox/inbox.types';
import { DraftRoomPage } from './features/real-match/draft/DraftRoomPage';
import { MatchPlaybackPage } from './features/real-match/playback/MatchPlaybackPage';
import { createMatchSession, loadMatchSetupOptions } from './features/real-match/matchDataSource';
import type { MatchRequestStage } from './features/real-match/api/realMatchApi.types';
import type { MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection } from './features/real-match/matchSession.types';
import { realMatchConfig } from './features/real-match/realMatch.config';
import { MatchSetupPage } from './features/real-match/setup/MatchSetupPage';
import { MatchResultPage } from './features/real-match/result/MatchResultPage';
import { createPlayerDraftSession, fetchPlayerDraftChampionCatalog, fetchPlayerDraftChampionRoleCatalog } from './features/real-match/player-draft/api/playerDraftApi.client';
import type { PlayerDraftSessionResponseDto } from './features/real-match/player-draft/api/playerDraftApi.types';
import { createPlayerDraftChampionCatalog, createPlayerDraftMatchSession, mergePlayerDraftChampionCatalog } from './features/real-match/player-draft/playerDraft.adapter';
import { PlayerDraftRoomPage } from './features/real-match/player-draft/PlayerDraftRoomPage';
import type { PlayerDraftScreenState } from './features/real-match/player-draft/playerDraft.types';
import { shouldApplyPlayerDraftSession } from './features/real-match/player-draft/playerDraftSessionOrder';
import { markPlayerDraftLatency } from './features/real-match/player-draft/playerDraftLatencyObserver';
import { getSeries, replaySeriesGame, SeriesApiFailure } from './features/real-match/series/api/seriesApi.client';
import type { SeriesChildDraftEnvelopeDto, SeriesSimulationResult, SeriesViewDto } from './features/real-match/series/api/seriesApi.types';
import { createSeriesMatchSession, createSeriesScreenState, shouldApplySeries } from './features/real-match/series/series.adapter';
import type { SeriesScreenState } from './features/real-match/series/series.types';
import { clearSeriesPointer, readSeriesPointer, seriesPointerRecoveryAction, writeSeriesPointer } from './features/real-match/series/series.pointer';
import { SeriesSetupPage } from './features/real-match/series/SeriesSetupPage';
import { SeriesHubPage } from './features/real-match/series/SeriesHubPage';
import { SeriesDraftRoomPage } from './features/real-match/series/SeriesDraftRoomPage';
import { SeriesDraftReviewPage } from './features/real-match/series/SeriesDraftReviewPage';
import { SeriesContextBar } from './features/real-match/series/SeriesContextBar';
import { AppShell } from './layout/AppShell';
import type { AppSection } from './layout/Sidebar';

type ActiveScreen = AppSection | 'setup' | 'draft' | 'player-draft' | 'playback' | 'result'
  | 'series-setup' | 'series-hub' | 'series-draft' | 'series-draft-review' | 'series-playback' | 'series-result';

function waitFor(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(resolve, milliseconds);
    signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('Series wait aborted', 'AbortError')); }, { once: true });
  });
}

const SERIES_SIMULATION_POLL_DELAYS = [500, 800, 1200, ...Array.from({ length: 45 }, () => 2000)] as const;

function RootApp() {
  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('inbox');
  const [matchSession, setMatchSession] = useState<MatchSessionViewModel | null>(null);
  const [playerDraftState, setPlayerDraftState] = useState<PlayerDraftScreenState | null>(null);
  const [seriesState, setSeriesState] = useState<SeriesScreenState | null>(null);
  const [draftReturnScreen, setDraftReturnScreen] = useState<ActiveScreen>('setup');
  const [searchValue, setSearchValue] = useState('');
  const [gameTime, setGameTime] = useState('오후 1:42');
  const [progressModalOpen, setProgressModalOpen] = useState(false);
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const toastTimerRef = useRef<number | null>(null);
  const matchRequestRef = useRef<AbortController | null>(null);
  const seriesRequestRef = useRef<AbortController | null>(null);
  const seriesRestoreStartedRef = useRef(false);
  const matchRequestSequenceRef = useRef(0);
  const measuredPlaybackSessionsRef = useRef<Set<string>>(new Set());
  const playbackNavigationStartedAtRef = useRef<number | null>(null);
  const [unreadIds, setUnreadIds] = useState<Set<string>>(
    () => new Set(inboxMessages.filter((message) => message.initiallyUnread).map((message) => message.id)),
  );

  const unreadImportantCount = useMemo(
    () => inboxMessages.filter((message) => message.important && unreadIds.has(message.id)).length,
    [unreadIds],
  );
  const showToast = useCallback((title: string, message: string) => {
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    setToast({ title, message });
    toastTimerRef.current = window.setTimeout(() => setToast(null), 2800);
  }, []);

  useEffect(() => {
    if (seriesRestoreStartedRef.current) return;
    seriesRestoreStartedRef.current = true;
    const seriesId = readSeriesPointer(window.sessionStorage);
    if (!seriesId) return;
    const controller = new AbortController(); seriesRequestRef.current = controller;
    Promise.all([
      getSeries(seriesId, controller.signal),
      loadMatchSetupOptions('LIVE', controller.signal),
      fetchPlayerDraftChampionCatalog(controller.signal),
    ]).then(([series, options, catalogResource]) => {
      if (controller.signal.aborted) return;
      let catalog = createPlayerDraftChampionCatalog(catalogResource);
      if (series.activeDraftSession) catalog = mergePlayerDraftChampionCatalog(series.activeDraftSession.session, catalog, catalogResource.rolesByChampionId);
      setSeriesState(createSeriesScreenState(series, options, catalog));
      setActiveScreen('series-hub');
      showToast('시리즈 복구 완료', `${series.format} Game ${series.currentGameNumber} · revision ${series.revision} 상태를 서버에서 불러왔습니다.`);
    }).catch((error: unknown) => {
      if (controller.signal.aborted) return;
      if (error instanceof SeriesApiFailure) {
        const action = seriesPointerRecoveryAction(error);
        if (action === 'CLEAR_NOT_FOUND' || action === 'CLEAR_EXPIRED') {
          clearSeriesPointer(window.sessionStorage); setSeriesState(null); setActiveScreen('series-setup');
          showToast(
            action === 'CLEAR_EXPIRED' ? '시리즈가 만료되었습니다' : '시리즈를 복구할 수 없습니다',
            action === 'CLEAR_EXPIRED'
              ? '만료된 Series ID를 정리했습니다. 새 시리즈를 시작하세요.'
              : '백엔드 재시작으로 진행 기록이 사라졌을 수 있어 Series ID를 정리했습니다. 새 시리즈를 시작하세요.',
          );
          return;
        }
        if (action === 'KEEP_VERSION_ERROR') {
          setSeriesState(null); setActiveScreen('series-setup');
          showToast('Series API 버전 확인 필요', '저장된 ID의 로컬 상태를 사용하지 않았습니다. 백엔드와 프런트엔드 버전을 맞춘 뒤 다시 불러오세요.');
          return;
        }
        showToast('시리즈 복구 보류', `${error.userMessage} Series ID는 유지했으므로 일시 오류가 해소된 뒤 새로고침하면 다시 확인합니다.`);
        return;
      }
      showToast('시리즈 복구 보류', '저장된 Series ID는 유지했습니다. LIVE Options와 챔피언 카탈로그 연결을 확인한 뒤 새로고침하세요.');
    }).finally(() => { if (seriesRequestRef.current === controller) seriesRequestRef.current = null; });
    return () => { controller.abort(); seriesRestoreStartedRef.current = false; };
  }, [showToast]);

  useEffect(() => () => {
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    matchRequestRef.current?.abort();
    seriesRequestRef.current?.abort();
  }, []);

  useEffect(() => {
    document.title = activeScreen.startsWith('series-')
      ? 'lolmanager — BO3 / BO5 Series'
      : activeScreen === 'setup'
      ? 'lolmanager — Match Setup'
      : activeScreen === 'draft'
      ? 'lolmanager — 자동 Draft 결과'
      : activeScreen === 'player-draft'
        ? 'lolmanager — 직접 Draft'
      : activeScreen === 'playback'
        ? 'lolmanager — Match Playback'
        : activeScreen === 'result'
          ? 'lolmanager — Match Result'
        : activeScreen === 'match'
          ? 'lolmanager — 경기 센터'
          : 'lolmanager — 홈·수신함';
  }, [activeScreen]);

  const markRead = useCallback((messageId: string) => {
    setUnreadIds((current) => {
      if (!current.has(messageId)) return current;
      const next = new Set(current);
      next.delete(messageId);
      return next;
    });
  }, []);

  const closeProgressModal = useCallback(() => setProgressModalOpen(false), []);
  const confirmProgress = useCallback(() => {
    setProgressModalOpen(false);
    setGameTime('오후 2:00');
    showToast('시간 진행 완료', '게임 시간이 오후 2:00로 진행되었습니다.');
  }, [showToast]);

  const cancelMatchRequest = useCallback(() => {
    matchRequestSequenceRef.current += 1;
    matchRequestRef.current?.abort();
    matchRequestRef.current = null;
  }, []);

  const startMatch = async (selection: MatchSetupSelection, options: MatchSetupOptionsViewModel, onStage: (stage: MatchRequestStage) => void) => {
    if (matchRequestRef.current && !matchRequestRef.current.signal.aborted) return;
    const requestId = ++matchRequestSequenceRef.current;
    const controller = new AbortController();
    matchRequestRef.current = controller;
    setMatchSession(null);
    try {
      if (selection.draftMode === 'PLAYER_CONTROLLED') {
        if (options.source !== 'LIVE') throw new Error('직접 밴픽은 LIVE 데이터에서만 사용할 수 있습니다.');
        onStage('CONNECTING');
        const rolesByChampionId = await fetchPlayerDraftChampionRoleCatalog(controller.signal);
        const session = await createPlayerDraftSession({
          schemaVersion: 'PLAYER_DRAFT_START_REQUEST_V1', blueTeamCode: selection.blueTeamId,
          redTeamCode: selection.redTeamId, controlledSide: selection.controlledSide, seed: selection.seed,
        }, controller.signal);
        if (controller.signal.aborted || requestId !== matchRequestSequenceRef.current) throw new DOMException('Stale player Draft response', 'AbortError');
        onStage('NORMALIZING');
        setPlayerDraftState({ session, options, selection, championsById: mergePlayerDraftChampionCatalog(session, {}, rolesByChampionId) });
        setDraftReturnScreen('setup'); setActiveScreen('player-draft');
        return;
      }
      const session = await createMatchSession(options.source, options, selection, controller.signal, onStage);
      if (controller.signal.aborted || requestId !== matchRequestSequenceRef.current) throw new DOMException('Stale match response', 'AbortError');
      setMatchSession(session);
      setPlayerDraftState(null);
      console.info('[real-match-performance]', JSON.stringify({
        source: session.source,
        matchIdentity: session.sessionId,
        payloadBytes: session.performance.payloadBytes,
        requestAndDownloadMs: Number(session.performance.requestAndDownloadMs.toFixed(1)),
        jsonParseMs: Number(session.performance.jsonParseMs.toFixed(1)),
        runtimeValidationMs: Number(session.performance.runtimeValidationMs.toFixed(1)),
        normalizationMs: Number(session.performance.normalizationMs.toFixed(1)),
        rawPayloadRetained: false,
      }));
      setDraftReturnScreen('setup');
      setActiveScreen('draft');
    } finally {
      if (matchRequestRef.current === controller) matchRequestRef.current = null;
    }
  };

  const updatePlayerDraftSession = useCallback((session: PlayerDraftSessionResponseDto) => {
    setPlayerDraftState((current) => {
      if (!current || !shouldApplyPlayerDraftSession(current.session, session)) return current;
      const correlationId = [...session.decisions].reverse().find((decision) => decision.authority === 'PLAYER')?.playerSelectionEvidence?.clientActionId ?? null;
      if (correlationId) markPlayerDraftLatency('PLAYER_ACTION_STATE_ADAPTER_START', correlationId);
      const championsById = mergePlayerDraftChampionCatalog(session, current.championsById);
      if (correlationId) markPlayerDraftLatency('PLAYER_ACTION_STATE_ADAPTER_COMPLETE', correlationId);
      return {
        ...current, session, championsById,
      };
    });
  }, []);

  const updateSeriesState = useCallback((series: SeriesViewDto, draft: SeriesChildDraftEnvelopeDto | null, session?: PlayerDraftSessionResponseDto) => {
    writeSeriesPointer(window.sessionStorage, series.seriesId);
    setSeriesState((current) => {
      if (!current || !shouldApplySeries(current.series, series)) return current;
      const projectedSession = session ?? draft?.session ?? null;
      return {
        ...current, series, draft,
        championsById: projectedSession ? mergePlayerDraftChampionCatalog(projectedSession, current.championsById) : current.championsById,
      };
    });
  }, []);

  const initializeSeries = useCallback(async (series: SeriesViewDto, options: MatchSetupOptionsViewModel) => {
    const controller = new AbortController(); seriesRequestRef.current?.abort(); seriesRequestRef.current = controller;
    try {
      const resource = await fetchPlayerDraftChampionCatalog(controller.signal);
      let catalog = createPlayerDraftChampionCatalog(resource);
      if (series.activeDraftSession) catalog = mergePlayerDraftChampionCatalog(series.activeDraftSession.session, catalog, resource.rolesByChampionId);
      writeSeriesPointer(window.sessionStorage, series.seriesId);
      setSeriesState(createSeriesScreenState(series, options, catalog)); setActiveScreen('series-hub');
    } catch (error) {
      showToast('챔피언 카탈로그 확인 필요', error instanceof Error ? error.message : '챔피언 카탈로그를 불러오지 못했습니다.');
      writeSeriesPointer(window.sessionStorage, series.seriesId);
      setSeriesState(createSeriesScreenState(series, options, {})); setActiveScreen('series-hub');
    } finally { if (seriesRequestRef.current === controller) seriesRequestRef.current = null; }
  }, [showToast]);

  const presentSeriesMatch = useCallback((
    series: SeriesViewDto,
    child: SeriesChildDraftEnvelopeDto,
    match: Parameters<typeof createSeriesMatchSession>[3],
    requestPerformance: Parameters<typeof createSeriesMatchSession>[5],
  ) => {
    setSeriesState((current) => {
      if (!current || !shouldApplySeries(current.series, series)) return current;
      const game = series.games.find((candidate) => candidate.gameNumber === child.binding.gameNumber);
      if (!game) return current;
      const matchSession = createSeriesMatchSession(series, game, child, match, current.options, requestPerformance);
      return {
        ...current, series, draft: series.activeDraftSession, reviewDraft: child, matchSession,
        matchGameNumber: game.gameNumber,
        championsById: mergePlayerDraftChampionCatalog(child.session, current.championsById),
      };
    });
    playbackNavigationStartedAtRef.current = performance.now(); setActiveScreen('series-playback');
  }, []);

  const openSeriesGame = useCallback(async (gameNumber: number) => {
    if (!seriesState) return;
    const controller = new AbortController(); seriesRequestRef.current?.abort(); seriesRequestRef.current = controller;
    showToast('경기 재생 준비', `Game ${gameNumber}의 full replay를 서버에서 검증하고 있습니다.`);
    try {
      const replay = await replaySeriesGame(seriesState.series.seriesId, gameNumber, {
        schemaVersion: 'SERIES_GAME_REPLAY_REQUEST_V1', clientCommandId: crypto.randomUUID(),
      }, controller.signal);
      presentSeriesMatch(replay.response.series, replay.draftSession, replay.response.match, replay.performance);
    } catch (error) {
      if (controller.signal.aborted) return;
      showToast('경기 재생 불가', error instanceof SeriesApiFailure ? error.userMessage : 'Game replay를 준비하지 못했습니다.');
    } finally { if (seriesRequestRef.current === controller) seriesRequestRef.current = null; }
  }, [presentSeriesMatch, seriesState, showToast]);

  const reconcileSeriesSimulation = useCallback(async (seriesId: string, gameNumber: number) => {
    const controller = new AbortController(); seriesRequestRef.current?.abort(); seriesRequestRef.current = controller;
    try {
      for (const delay of SERIES_SIMULATION_POLL_DELAYS) {
        if (document.hidden) { showToast('백그라운드 확인 중단', '탭이 다시 보이면 시리즈 허브에서 서버 상태를 새로고침하세요.'); return; }
        await waitFor(delay, controller.signal);
        const series = await getSeries(seriesId, controller.signal); updateSeriesState(series, series.activeDraftSession);
        const game = series.games.find((candidate) => candidate.gameNumber === gameNumber);
        if (!game) throw new Error('commit 대상 game을 찾을 수 없습니다.');
        if (game.status === 'COMMITTED') {
          showToast('경기 계산 완료', `Game ${gameNumber} commit을 확인했습니다. 명시적 replay로 전체 타임라인을 불러옵니다.`);
          const replay = await replaySeriesGame(seriesId, gameNumber, {
            schemaVersion: 'SERIES_GAME_REPLAY_REQUEST_V1', clientCommandId: crypto.randomUUID(),
          }, controller.signal);
          presentSeriesMatch(replay.response.series, replay.draftSession, replay.response.match, replay.performance); return;
        }
        if (game.status === 'SIMULATION_FAILED_RETRYABLE' || game.status === 'BLOCKED' || series.status !== 'ACTIVE') {
          setActiveScreen('series-hub'); showToast('경기 실행 상태 변경', game.reason ?? '서버의 최신 시리즈 상태를 확인하세요.'); return;
        }
      }
      showToast('계산 계속 진행 중', '자동 확인 횟수를 마쳤습니다. 시리즈 허브에서 명시적으로 새로고침할 수 있습니다.');
    } catch (error) {
      if (!controller.signal.aborted) showToast('상태 확인 실패', error instanceof SeriesApiFailure ? error.userMessage : '경기 실행 상태를 확인하지 못했습니다.');
    } finally { if (seriesRequestRef.current === controller) seriesRequestRef.current = null; }
  }, [presentSeriesMatch, showToast, updateSeriesState]);

  const handleSeriesSimulation = useCallback((result: SeriesSimulationResult) => {
    updateSeriesState(result.response.series, result.response.series.activeDraftSession, result.draftSession?.session);
    const gameNumber = result.response.game.gameNumber;
    if (result.status === 202) {
      showToast('경기 계산 진행 중', `Game ${gameNumber} reservation을 확인했습니다. 중복 실행 없이 서버 상태를 조회합니다.`);
      void reconcileSeriesSimulation(result.response.series.seriesId, gameNumber); return;
    }
    if (result.response.match && result.draftSession) {
      presentSeriesMatch(result.response.series, result.draftSession, result.response.match, result.performance); return;
    }
    showToast('전체 재생 복원', `Game ${gameNumber}은 compact 응답입니다. explicit replay로 전체 타임라인을 준비합니다.`);
    void openSeriesGame(gameNumber);
  }, [openSeriesGame, presentSeriesMatch, reconcileSeriesSimulation, showToast, updateSeriesState]);

  const seriesToast = <Toast toast={toast} />;

  if (activeScreen === 'series-setup') {
    return <><SeriesSetupPage onBack={() => setActiveScreen('setup')} onCreated={(series, options) => { void initializeSeries(series, options); }} />{seriesToast}</>;
  }

  if (activeScreen === 'series-hub' && seriesState) {
    return <><SeriesHubPage state={seriesState} onBack={() => setActiveScreen('setup')}
      onStateChange={updateSeriesState} onStartDraft={() => setActiveScreen('series-draft')}
      onOpenGame={(gameNumber) => { void openSeriesGame(gameNumber); }}
      onNewSeries={() => { clearSeriesPointer(window.sessionStorage); setSeriesState(null); setActiveScreen('series-setup'); }} />{seriesToast}</>;
  }

  if (activeScreen === 'series-draft' && seriesState?.draft) {
    return <><SeriesDraftRoomPage state={seriesState} onStateChange={updateSeriesState}
      onSimulation={handleSeriesSimulation} onHub={() => setActiveScreen('series-hub')}
      onOpenGame={(gameNumber) => { void openSeriesGame(gameNumber); }} />{seriesToast}</>;
  }

  if (activeScreen === 'series-draft-review' && seriesState?.reviewDraft) {
    return <><SeriesDraftReviewPage state={seriesState} onBack={() => setActiveScreen(draftReturnScreen === 'series-result' ? 'series-result' : 'series-playback')}
      onOpenGame={(gameNumber) => { void openSeriesGame(gameNumber); }} />{seriesToast}</>;
  }

  if (activeScreen === 'series-playback' && seriesState?.matchSession) {
    const session = seriesState.matchSession;
    return <><div className="sr-match-shell"><SeriesContextBar series={seriesState.series} catalog={seriesState.championsById} onOpenGame={(gameNumber) => { void openSeriesGame(gameNumber); }} />
      <MatchPlaybackPage viewModel={session.playback} draftContextLabel={`Series Game ${seriesState.matchGameNumber} · 직접 Draft`}
        onBack={() => setActiveScreen('series-hub')} onDraft={() => { setDraftReturnScreen('series-playback'); setActiveScreen('series-draft-review'); }}
        onComplete={() => setActiveScreen('series-result')} /></div>{seriesToast}</>;
  }

  if (activeScreen === 'series-result' && seriesState?.matchSession) {
    const session = seriesState.matchSession;
    return <><div className="sr-match-shell"><SeriesContextBar series={seriesState.series} catalog={seriesState.championsById} onOpenGame={(gameNumber) => { void openSeriesGame(gameNumber); }} />
      <MatchResultPage result={session.result} championsById={session.playback.championsById}
        draftContextLabel={`Series Game ${seriesState.matchGameNumber} · 직접 Draft`}
        onBack={() => setActiveScreen('series-hub')} onDraft={() => { setDraftReturnScreen('series-result'); setActiveScreen('series-draft-review'); }}
        onPlayback={() => setActiveScreen('series-playback')} onRerun={() => setActiveScreen('series-playback')}
        onNewMatch={() => { setSeriesState((current) => current ? { ...current, draft: current.series.activeDraftSession, matchSession: null, matchGameNumber: null } : current); setActiveScreen('series-hub'); }} />
    </div>{seriesToast}</>;
  }

  if (activeScreen.startsWith('series-')) {
    if (!seriesState) return <><SeriesSetupPage onBack={() => setActiveScreen('setup')} onCreated={(series, options) => { void initializeSeries(series, options); }} />{seriesToast}</>;
    return <><SeriesHubPage state={seriesState} onBack={() => setActiveScreen('setup')}
      onStateChange={updateSeriesState} onStartDraft={() => setActiveScreen('series-draft')}
      onOpenGame={(gameNumber) => { void openSeriesGame(gameNumber); }}
      onNewSeries={() => { clearSeriesPointer(window.sessionStorage); setSeriesState(null); setActiveScreen('series-setup'); }} />{seriesToast}</>;
  }

  if (activeScreen === 'setup') {
    return <MatchSetupPage dataSource={realMatchConfig.dataSource} onBack={() => { cancelMatchRequest(); setActiveScreen('inbox'); }} onLegacy={() => { cancelMatchRequest(); setActiveScreen('match'); }} onSeries={() => setActiveScreen('series-setup')} onStart={startMatch} onCancelStart={cancelMatchRequest} />;
  }

  if (activeScreen === 'draft' && matchSession) {
    return <DraftRoomPage viewModel={matchSession.draft} onBack={() => setActiveScreen(draftReturnScreen)} onContinue={() => {
      playbackNavigationStartedAtRef.current = performance.now();
      setActiveScreen('playback');
    }} />;
  }

  if (activeScreen === 'player-draft' && playerDraftState) {
    return <PlayerDraftRoomPage state={playerDraftState} onSessionChange={updatePlayerDraftSession}
      onSimulationComplete={(simulation) => {
        const correlationId = simulation.response.session.sessionId;
        markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_REACT_STATE_START', correlationId);
        const session = createPlayerDraftMatchSession(simulation, playerDraftState.options, playerDraftState.selection);
        setMatchSession(session);
        setPlayerDraftState((current) => current ? {
          ...current, session: simulation.response.session,
          championsById: mergePlayerDraftChampionCatalog(simulation.response.session, current.championsById),
        } : current);
        playbackNavigationStartedAtRef.current = performance.now(); setDraftReturnScreen('playback'); setActiveScreen('playback');
        markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_REACT_STATE_COMPLETE', correlationId);
      }}
      onCancelled={() => { setPlayerDraftState(null); setMatchSession(null); setDraftReturnScreen('setup'); setActiveScreen('setup'); }}
      onReviewBack={() => setActiveScreen(draftReturnScreen)} />;
  }

  if (activeScreen === 'playback' && matchSession) {
    const playerControlled = matchSession.draftOrigin.mode === 'PLAYER_CONTROLLED';
    const draftContextLabel = matchSession.draftOrigin.mode === 'PLAYER_CONTROLLED' ? `직접 Draft · ${matchSession.draftOrigin.controlledSide} PLAYER` : '자동 Draft';
    return <MatchPlaybackPage viewModel={matchSession.playback} draftContextLabel={draftContextLabel} onBack={() => setActiveScreen('setup')} onDraft={() => { setDraftReturnScreen('playback'); setActiveScreen(playerControlled ? 'player-draft' : 'draft'); }} onComplete={() => setActiveScreen('result')}
      onFirstPaint={() => {
        if (measuredPlaybackSessionsRef.current.has(matchSession.sessionId)) return;
        measuredPlaybackSessionsRef.current.add(matchSession.sessionId);
        if (matchSession.draftOrigin.mode === 'PLAYER_CONTROLLED') {
          markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_PLAYBACK_DOM_STABLE', matchSession.draftOrigin.sessionId, { matchIdentity: matchSession.sessionId });
        }
        const memory = (performance as Performance & { memory?: { usedJSHeapSize: number; totalJSHeapSize: number } }).memory;
        console.info('[real-match-first-playback]', JSON.stringify({
          matchIdentity: matchSession.sessionId,
          navigationToFirstPaintMs: playbackNavigationStartedAtRef.current === null
            ? null : Number((performance.now() - playbackNavigationStartedAtRef.current).toFixed(1)),
          usedJsHeapBytes: memory?.usedJSHeapSize ?? null,
          totalJsHeapBytes: memory?.totalJSHeapSize ?? null,
        }));
      }} />;
  }

  if (activeScreen === 'result' && matchSession) {
    const playerControlled = matchSession.draftOrigin.mode === 'PLAYER_CONTROLLED';
    const draftContextLabel = matchSession.draftOrigin.mode === 'PLAYER_CONTROLLED' ? `직접 Draft · ${matchSession.draftOrigin.controlledSide} PLAYER` : '자동 Draft';
    return <MatchResultPage result={matchSession.result} championsById={matchSession.playback.championsById} draftContextLabel={draftContextLabel} onBack={() => setActiveScreen('setup')} onDraft={() => { setDraftReturnScreen('result'); setActiveScreen(playerControlled ? 'player-draft' : 'draft'); }} onPlayback={() => setActiveScreen('playback')} onRerun={() => setActiveScreen('playback')} onNewMatch={() => { setMatchSession(null); setPlayerDraftState(null); setDraftReturnScreen('setup'); setActiveScreen('setup'); }} />;
  }

  if (activeScreen === 'draft' || activeScreen === 'player-draft' || activeScreen === 'playback' || activeScreen === 'result') {
    return <MatchSetupPage dataSource={realMatchConfig.dataSource} onBack={() => setActiveScreen('inbox')} onLegacy={() => setActiveScreen('match')} onSeries={() => setActiveScreen('series-setup')} onStart={startMatch} onCancelStart={cancelMatchRequest} />;
  }

  const activeSection: AppSection = activeScreen === 'inbox' ? 'inbox' : 'match';

  return (
    <>
      <AppShell
        activeSection={activeSection}
        screenTitle={activeSection === 'inbox' ? '수신함' : '경기 센터'}
        searchValue={searchValue}
        gameTime={gameTime}
        primaryActionLabel={activeSection === 'match' ? '경기 준비' : '다음 진행'}
        onNavigate={(section) => setActiveScreen(section === 'match' ? 'setup' : section)}
        onSearchChange={setSearchValue}
        onContinue={() => activeSection === 'match' ? setActiveScreen('setup') : setProgressModalOpen(true)}
        onNotify={showToast}
      >
        {activeSection === 'inbox' ? (
          <InboxPage
            messages={inboxMessages}
            unreadIds={unreadIds}
            searchValue={searchValue}
            onSearchChange={setSearchValue}
            onMarkRead={markRead}
            onNotify={showToast}
          />
        ) : (
          <div className="lm-match-workspace" aria-label="경기 센터">
            <MatchCenter />
          </div>
        )}
      </AppShell>
      <ProgressModal
        open={progressModalOpen}
        unreadImportantCount={unreadImportantCount}
        onCancel={closeProgressModal}
        onConfirm={confirmProgress}
      />
      <Toast toast={toast} />
    </>
  );
}

export default RootApp;

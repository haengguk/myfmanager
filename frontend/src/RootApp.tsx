import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MatchCenter from './App';
import { ProgressModal } from './components/ProgressModal';
import { Toast } from './components/Toast';
import { inboxMessages } from './features/inbox/inbox.fixtures';
import { InboxPage } from './features/inbox/InboxPage';
import type { ToastMessage } from './features/inbox/inbox.types';
import { DraftRoomPage } from './features/real-match/draft/DraftRoomPage';
import { MatchPlaybackPage } from './features/real-match/playback/MatchPlaybackPage';
import { createMatchSession } from './features/real-match/matchDataSource';
import type { MatchRequestStage } from './features/real-match/api/realMatchApi.types';
import type { MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection } from './features/real-match/matchSession.types';
import { realMatchConfig } from './features/real-match/realMatch.config';
import { MatchSetupPage } from './features/real-match/setup/MatchSetupPage';
import { MatchResultPage } from './features/real-match/result/MatchResultPage';
import { AppShell } from './layout/AppShell';
import type { AppSection } from './layout/Sidebar';

type ActiveScreen = AppSection | 'setup' | 'draft' | 'playback' | 'result';

function RootApp() {
  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('inbox');
  const [matchSession, setMatchSession] = useState<MatchSessionViewModel | null>(null);
  const [draftReturnScreen, setDraftReturnScreen] = useState<ActiveScreen>('setup');
  const [searchValue, setSearchValue] = useState('');
  const [gameTime, setGameTime] = useState('오후 1:42');
  const [progressModalOpen, setProgressModalOpen] = useState(false);
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const toastTimerRef = useRef<number | null>(null);
  const matchRequestRef = useRef<AbortController | null>(null);
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

  useEffect(() => () => {
    if (toastTimerRef.current !== null) window.clearTimeout(toastTimerRef.current);
    matchRequestRef.current?.abort();
  }, []);

  useEffect(() => {
    document.title = activeScreen === 'setup'
      ? 'lolmanager — Match Setup'
      : activeScreen === 'draft'
      ? 'lolmanager — 자동 Draft 결과'
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
      const session = await createMatchSession(options.source, options, selection, controller.signal, onStage);
      if (controller.signal.aborted || requestId !== matchRequestSequenceRef.current) throw new DOMException('Stale match response', 'AbortError');
      setMatchSession(session);
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

  if (activeScreen === 'setup') {
    return <MatchSetupPage dataSource={realMatchConfig.dataSource} onBack={() => { cancelMatchRequest(); setActiveScreen('inbox'); }} onLegacy={() => { cancelMatchRequest(); setActiveScreen('match'); }} onStart={startMatch} onCancelStart={cancelMatchRequest} />;
  }

  if (activeScreen === 'draft' && matchSession) {
    return <DraftRoomPage viewModel={matchSession.draft} onBack={() => setActiveScreen(draftReturnScreen)} onContinue={() => {
      playbackNavigationStartedAtRef.current = performance.now();
      setActiveScreen('playback');
    }} />;
  }

  if (activeScreen === 'playback' && matchSession) {
    return <MatchPlaybackPage viewModel={matchSession.playback} onBack={() => setActiveScreen('setup')} onDraft={() => { setDraftReturnScreen('playback'); setActiveScreen('draft'); }} onComplete={() => setActiveScreen('result')}
      onFirstPaint={() => {
        if (measuredPlaybackSessionsRef.current.has(matchSession.sessionId)) return;
        measuredPlaybackSessionsRef.current.add(matchSession.sessionId);
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
    return <MatchResultPage result={matchSession.result} championsById={matchSession.playback.championsById} onBack={() => setActiveScreen('setup')} onDraft={() => { setDraftReturnScreen('result'); setActiveScreen('draft'); }} onPlayback={() => setActiveScreen('playback')} onRerun={() => setActiveScreen('playback')} onNewMatch={() => { setMatchSession(null); setDraftReturnScreen('setup'); setActiveScreen('setup'); }} />;
  }

  if (activeScreen === 'draft' || activeScreen === 'playback' || activeScreen === 'result') {
    return <MatchSetupPage dataSource={realMatchConfig.dataSource} onBack={() => setActiveScreen('inbox')} onLegacy={() => setActiveScreen('match')} onStart={startMatch} onCancelStart={cancelMatchRequest} />;
  }

  const activeSection: AppSection = activeScreen;

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

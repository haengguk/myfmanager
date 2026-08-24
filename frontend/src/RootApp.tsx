import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MatchCenter from './App';
import { ProgressModal } from './components/ProgressModal';
import { Toast } from './components/Toast';
import { inboxMessages } from './features/inbox/inbox.fixtures';
import { InboxPage } from './features/inbox/InboxPage';
import type { ToastMessage } from './features/inbox/inbox.types';
import { DraftRoomPage } from './features/real-match/draft/DraftRoomPage';
import { MatchPlaybackPage } from './features/real-match/playback/MatchPlaybackPage';
import { createMatchResult, createMatchSession, completeSessionDraft } from './features/real-match/matchSession.adapter';
import { matchSetupOptionsFixture } from './features/real-match/matchSession.fixtures';
import type { MatchSessionViewModel, MatchSetupSelection } from './features/real-match/matchSession.types';
import { MatchSetupPage } from './features/real-match/setup/MatchSetupPage';
import { MatchResultPage } from './features/real-match/result/MatchResultPage';
import { AppShell } from './layout/AppShell';
import type { AppSection } from './layout/Sidebar';

type ActiveScreen = AppSection | 'setup' | 'draft' | 'playback' | 'result';

function RootApp() {
  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('inbox');
  const [matchSession, setMatchSession] = useState<MatchSessionViewModel | null>(null);
  const [draftReview, setDraftReview] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [gameTime, setGameTime] = useState('오후 1:42');
  const [progressModalOpen, setProgressModalOpen] = useState(false);
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const toastTimerRef = useRef<number | null>(null);
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
  }, []);

  useEffect(() => {
    document.title = activeScreen === 'setup'
      ? 'lolmanager — Match Setup'
      : activeScreen === 'draft'
      ? 'lolmanager — Draft Room'
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

  const startMatch = (selection: MatchSetupSelection) => {
    setMatchSession(createMatchSession(matchSetupOptionsFixture, selection));
    setDraftReview(false);
    setActiveScreen('draft');
  };

  if (activeScreen === 'setup') {
    return <MatchSetupPage options={matchSetupOptionsFixture} onBack={() => setActiveScreen('match')} onStart={startMatch} />;
  }

  if (activeScreen === 'draft' && matchSession) {
    return <DraftRoomPage viewModel={matchSession.draft} reviewResult={draftReview ? matchSession.draftResult : null} onBack={() => setActiveScreen(draftReview ? 'result' : 'match')} onComplete={(result) => { setMatchSession((current) => current ? completeSessionDraft(current, result) : current); setDraftReview(false); setActiveScreen('playback'); }} onReviewContinue={() => setActiveScreen('playback')} />;
  }

  if (activeScreen === 'playback' && matchSession) {
    return <MatchPlaybackPage viewModel={matchSession.playback} onBack={() => setActiveScreen('match')} onDraft={() => { setDraftReview(Boolean(matchSession.draftResult)); setActiveScreen('draft'); }} onComplete={() => { setMatchSession((current) => current ? { ...current, result: createMatchResult(current) } : current); setActiveScreen('result'); }} />;
  }

  if (activeScreen === 'result' && matchSession?.result) {
    return <MatchResultPage result={matchSession.result} championsById={matchSession.playback.championsById} onBack={() => setActiveScreen('match')} onDraft={() => { setDraftReview(true); setActiveScreen('draft'); }} onPlayback={() => setActiveScreen('playback')} onRerun={() => startMatch(matchSession.setup)} onNewMatch={() => { setMatchSession(null); setDraftReview(false); setActiveScreen('setup'); }} />;
  }

  if (activeScreen === 'draft' || activeScreen === 'playback' || activeScreen === 'result') {
    return <MatchSetupPage options={matchSetupOptionsFixture} onBack={() => setActiveScreen('match')} onStart={startMatch} />;
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
        onNavigate={setActiveScreen}
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

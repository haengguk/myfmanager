import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MatchCenter from './App';
import { ProgressModal } from './components/ProgressModal';
import { Toast } from './components/Toast';
import { inboxMessages } from './features/inbox/inbox.fixtures';
import { InboxPage } from './features/inbox/InboxPage';
import type { ToastMessage } from './features/inbox/inbox.types';
import { draftFixture, playbackFixture } from './features/real-match/realMatch.fixtures';
import { applyDraftResult } from './features/real-match/realMatch.adapter';
import { DraftRoomPage } from './features/real-match/draft/DraftRoomPage';
import { MatchPlaybackPage } from './features/real-match/playback/MatchPlaybackPage';
import type { DraftResultViewModel } from './features/real-match/realMatch.types';
import { AppShell } from './layout/AppShell';
import type { AppSection } from './layout/Sidebar';

type ActiveScreen = AppSection | 'draft' | 'playback';

function RootApp() {
  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('inbox');
  const [draftResult, setDraftResult] = useState<DraftResultViewModel | null>(null);
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
  const playbackViewModel = useMemo(
    () => draftResult ? applyDraftResult(playbackFixture, draftResult) : playbackFixture,
    [draftResult],
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
    document.title = activeScreen === 'draft'
      ? 'lolmanager — Draft Room'
      : activeScreen === 'playback'
        ? 'lolmanager — Match Playback'
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

  if (activeScreen === 'draft') {
    return <DraftRoomPage viewModel={draftFixture} onBack={() => setActiveScreen('match')} onComplete={(result) => { setDraftResult(result); setActiveScreen('playback'); }} />;
  }

  if (activeScreen === 'playback') {
    return <MatchPlaybackPage viewModel={playbackViewModel} onBack={() => setActiveScreen('match')} onDraft={() => { setDraftResult(null); setActiveScreen('draft'); }} />;
  }

  const activeSection: AppSection = activeScreen;

  return (
    <>
      <AppShell
        activeSection={activeSection}
        screenTitle={activeSection === 'inbox' ? '수신함' : '경기 센터'}
        searchValue={searchValue}
        gameTime={gameTime}
        primaryActionLabel={activeSection === 'match' ? '드래프트 룸' : '다음 진행'}
        onNavigate={setActiveScreen}
        onSearchChange={setSearchValue}
        onContinue={() => activeSection === 'match' ? setActiveScreen('draft') : setProgressModalOpen(true)}
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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MatchCenter from './App';
import { ProgressModal } from './components/ProgressModal';
import { Toast } from './components/Toast';
import { inboxMessages } from './features/inbox/inbox.fixtures';
import { InboxPage } from './features/inbox/InboxPage';
import type { ToastMessage } from './features/inbox/inbox.types';
import { AppShell } from './layout/AppShell';
import type { AppSection } from './layout/Sidebar';

function RootApp() {
  const [activeSection, setActiveSection] = useState<AppSection>('inbox');
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

  return (
    <>
      <AppShell
        activeSection={activeSection}
        screenTitle={activeSection === 'inbox' ? '수신함' : '경기 센터'}
        searchValue={searchValue}
        gameTime={gameTime}
        onNavigate={setActiveSection}
        onSearchChange={setSearchValue}
        onContinue={() => setProgressModalOpen(true)}
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

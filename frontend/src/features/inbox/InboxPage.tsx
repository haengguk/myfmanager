import { useEffect, useMemo, useState } from 'react';
import { InboxDetail } from './InboxDetail';
import { InboxList } from './InboxList';
import type { InboxMessage, InboxTab } from './inbox.types';

interface InboxPageProps {
  messages: readonly InboxMessage[];
  unreadIds: ReadonlySet<string>;
  searchValue: string;
  onSearchChange: (value: string) => void;
  onMarkRead: (messageId: string) => void;
  onNotify: (title: string, message: string) => void;
}

const tabs: readonly { id: InboxTab; label: string }[] = [
  { id: 'all', label: '수신함' },
  { id: 'social', label: '소셜 피드' },
  { id: 'team', label: '팀 소식' },
  { id: 'league', label: '리그 소식' },
  { id: 'transfer', label: '이적 시장 소식' },
];

export function InboxPage({ messages, unreadIds, searchValue, onSearchChange, onMarkRead, onNotify }: InboxPageProps) {
  const [activeTab, setActiveTab] = useState<InboxTab>('all');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [selectedId, setSelectedId] = useState(messages[0]?.id ?? '');
  const normalizedSearch = searchValue.trim().toLocaleLowerCase('ko-KR');

  const visibleMessages = useMemo(() => messages.filter((message) => {
    const matchesTab = activeTab === 'all' || message.tab === activeTab;
    const matchesUnread = !unreadOnly || unreadIds.has(message.id);
    const searchTarget = `${message.sender} ${message.department} ${message.subject} ${message.preview}`.toLocaleLowerCase('ko-KR');
    return matchesTab && matchesUnread && (!normalizedSearch || searchTarget.includes(normalizedSearch));
  }), [activeTab, messages, normalizedSearch, unreadIds, unreadOnly]);

  useEffect(() => {
    if (visibleMessages.length > 0 && !visibleMessages.some((message) => message.id === selectedId)) {
      setSelectedId(visibleMessages[0].id);
    }
  }, [selectedId, visibleMessages]);

  const selectedMessage = messages.find((message) => message.id === selectedId) ?? messages[0];

  const selectMessage = (message: InboxMessage) => {
    setSelectedId(message.id);
    onMarkRead(message.id);
  };

  const handleAction = (action: string, message: InboxMessage) => {
    if (action === '확인 완료') {
      onMarkRead(message.id);
      onNotify('확인 완료', `${message.subject} 메시지를 확인 처리했습니다.`);
      return;
    }
    if (action === '선수와 대화') {
      onNotify('면담 요청 등록', `${message.sender}과의 대화를 오늘 일정에 추가했습니다.`);
      return;
    }
    if (action === '일정 확인') {
      onNotify('일정 확인', '관련 일정이 운영 타임라인에 표시되었습니다.');
      return;
    }
    onNotify(action, '현재 화면에서 요청을 준비했습니다.');
  };

  const handleTabKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const offset = event.key === 'ArrowRight' ? 1 : -1;
    const nextIndex = (index + offset + tabs.length) % tabs.length;
    setActiveTab(tabs[nextIndex].id);
    const tabList = event.currentTarget.parentElement;
    window.requestAnimationFrame(() => tabList?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[nextIndex]?.focus());
  };

  return (
    <>
      <nav className="lm-subtabs" aria-label="수신함 피드" role="tablist">
        {tabs.map((tab, index) => (
          <button
            className={`lm-subtab${activeTab === tab.id ? ' is-active' : ''}`}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            tabIndex={activeTab === tab.id ? 0 : -1}
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            onKeyDown={(event) => handleTabKeyDown(event, index)}
          >
            {tab.label}
          </button>
        ))}
      </nav>
      <div className="lm-inbox-workspace">
        <InboxList
          messages={visibleMessages}
          selectedId={selectedId}
          unreadIds={unreadIds}
          searchValue={searchValue}
          unreadOnly={unreadOnly}
          onSearchChange={onSearchChange}
          onUnreadOnlyChange={setUnreadOnly}
          onSelect={selectMessage}
        />
        {selectedMessage && (
          <InboxDetail
            message={selectedMessage}
            onAction={handleAction}
            onAttachment={(message) => onNotify('첨부 자료', `${message.attachment.name}을 열 준비가 되었습니다.`)}
          />
        )}
      </div>
    </>
  );
}

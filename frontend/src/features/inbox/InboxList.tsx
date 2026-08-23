import type { CSSProperties } from 'react';
import { Icon } from '../../components/Icon';
import type { InboxMessage } from './inbox.types';

interface InboxListProps {
  messages: readonly InboxMessage[];
  selectedId: string;
  unreadIds: ReadonlySet<string>;
  searchValue: string;
  unreadOnly: boolean;
  onSearchChange: (value: string) => void;
  onUnreadOnlyChange: (value: boolean) => void;
  onSelect: (message: InboxMessage) => void;
}

export function InboxList({ messages, selectedId, unreadIds, searchValue, unreadOnly, onSearchChange, onUnreadOnlyChange, onSelect }: InboxListProps) {
  const dates = [...new Set(messages.map((message) => message.date))];

  return (
    <section className="lm-message-panel" aria-labelledby="message-list-heading">
      <h2 className="lm-sr-only" id="message-list-heading">메시지 목록</h2>
      <div className="lm-message-tools">
        <label className="lm-list-search">
          <Icon name="search" />
          <span className="lm-sr-only">현재 피드 검색</span>
          <input
            type="search"
            value={searchValue}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="현재 피드 검색…"
            autoComplete="off"
          />
        </label>
        <button
          className="lm-icon-button lm-filter-button"
          type="button"
          aria-label={unreadOnly ? '모든 메시지 표시' : '안 읽은 메시지만 표시'}
          title={unreadOnly ? '모든 메시지' : '안 읽은 메시지만'}
          aria-pressed={unreadOnly}
          onClick={() => onUnreadOnlyChange(!unreadOnly)}
        >
          <Icon name="filter" />
        </button>
      </div>
      <div className="lm-message-list" aria-live="polite">
        {messages.length === 0 ? (
          <div className="lm-empty-list">조건에 맞는 메시지가 없습니다.<br />검색어나 필터를 확인하세요.</div>
        ) : dates.map((date) => {
          const dateMessages = messages.filter((message) => message.date === date);
          return (
            <div className="lm-date-section" key={date}>
              <div className="lm-date-group">
                <span>{date}</span>
                <span>{dateMessages.length}건</span>
              </div>
              {dateMessages.map((message) => {
                const unread = unreadIds.has(message.id);
                const selected = message.id === selectedId;
                return (
                  <button
                    className={`lm-message-item${unread ? ' is-unread' : ''}${selected ? ' is-selected' : ''}`}
                    type="button"
                    key={message.id}
                    style={{ '--message-tone': `var(--tone-${message.tone})` } as CSSProperties}
                    aria-current={selected ? 'true' : undefined}
                    aria-label={`${message.subject}, ${message.sender}, ${message.time}, ${unread ? '읽지 않음' : '읽음'}`}
                    onClick={() => onSelect(message)}
                  >
                    <span className="lm-message-meta">
                      <span className="lm-message-sender">{message.sender} · {message.department}</span>
                      <time className="lm-message-time">{message.time}</time>
                    </span>
                    <span className="lm-message-subject-row">
                      <span className="lm-message-subject">{message.subject}</span>
                      {message.important && <span className="lm-importance">중요</span>}
                    </span>
                    <span className="lm-message-preview">{message.preview}</span>
                  </button>
                );
              })}
            </div>
          );
        })}
      </div>
    </section>
  );
}

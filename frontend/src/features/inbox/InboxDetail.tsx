import type { CSSProperties } from 'react';
import { Icon } from '../../components/Icon';
import type { InboxMessage } from './inbox.types';

interface InboxDetailProps {
  message: InboxMessage;
  onAction: (action: string, message: InboxMessage) => void;
  onAttachment: (message: InboxMessage) => void;
}

export function InboxDetail({ message, onAction, onAttachment }: InboxDetailProps) {
  return (
    <main className="lm-detail" aria-labelledby="message-detail-title">
      <article className="lm-detail__inner" key={message.id}>
        <header className="lm-sender-head">
          <div className="lm-avatar" aria-hidden="true">{message.initials}</div>
          <div className="lm-sender-copy">
            <strong>{message.sender}</strong>
            <span>{message.role} · {message.department}</span>
          </div>
          <div className="lm-sender-time"><span>{message.date}</span><br /><span>{message.time}</span></div>
        </header>

        <div className="lm-title-block">
          <div className="lm-eyebrow-row">
            <span className="lm-category-tag" style={{ '--category-tone': `var(--tone-${message.tone})` } as CSSProperties}>{message.category}</span>
            {message.important && <span className="lm-priority-tag">중요</span>}
          </div>
          <h1 id="message-detail-title">{message.subject}</h1>
          <p className="lm-summary">{message.summary}</p>
        </div>

        <div className="lm-body-copy">
          {message.body.map((paragraph, index) => (
            <p key={`${message.id}-paragraph-${index}`}>
              {paragraph.text}
              {paragraph.emphasis && <strong>{paragraph.emphasis}</strong>}
              {paragraph.suffix}
            </p>
          ))}
        </div>

        <h2 className="lm-section-title" id="metrics-heading">핵심 지표</h2>
        <section className="lm-metric-strip" aria-labelledby="metrics-heading">
          {message.metrics.map((metric) => (
            <div className="lm-metric" key={metric.label}>
              <span>{metric.label}</span>
              <strong>{metric.value}</strong>
            </div>
          ))}
        </section>

        <h2 className="lm-section-title" id="context-heading">{message.contextTitle}</h2>
        <section className="lm-context-block" aria-labelledby="context-heading">
          <table className="lm-context-table">
            <thead><tr><th>항목</th><th>현재</th><th>권장</th><th>메모</th></tr></thead>
            <tbody>
              {message.rows.map((row, index) => (
                <tr key={row.label}>
                  <td>{row.label}</td>
                  <td>{row.current}</td>
                  <td>
                    <span className="lm-recommendation">
                      <span className="lm-data-bar" aria-hidden="true"><i style={{ width: `${64 + index * 8}%` }} /></span>
                      {row.recommendation}
                    </span>
                  </td>
                  <td>{row.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <h2 className="lm-section-title">첨부 자료</h2>
        <button className="lm-attachment" type="button" onClick={() => onAttachment(message)}>
          <span className="lm-file-icon"><Icon name="file" /></span>
          <span><strong>{message.attachment.name}</strong><span>{message.attachment.meta}</span></span>
          <em>열기</em>
        </button>

        <div className="lm-detail-actions">
          {message.actions.map((action, index) => (
            <button
              className={index === 0 ? 'lm-secondary-button' : 'lm-text-button'}
              type="button"
              key={action}
              onClick={() => onAction(action, message)}
            >
              {action}
            </button>
          ))}
        </div>
      </article>
    </main>
  );
}

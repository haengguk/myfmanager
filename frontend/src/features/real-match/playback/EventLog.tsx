import { useEffect, useRef } from 'react';
import { eventPresentation, formatMatchTime } from '../realMatch.adapter';
import type { PlaybackEventViewModel } from '../realMatch.types';

export function EventLog({ events, currentSeconds, selectedEventId, onSelect }: {
  events: readonly PlaybackEventViewModel[];
  currentSeconds: number;
  selectedEventId: string | null;
  onSelect: (event: PlaybackEventViewModel) => void;
}) {
  const logRef = useRef<HTMLDivElement>(null);
  const currentIndex = events.reduce((lastIndex, event, index) => event.occurredAtSeconds <= currentSeconds ? index : lastIndex, -1);
  useEffect(() => {
    const row = logRef.current?.querySelector<HTMLElement>('[data-current="true"]');
    if (row && logRef.current) logRef.current.scrollTop = Math.max(0, row.offsetTop - logRef.current.clientHeight / 2);
  }, [currentIndex]);

  return (
    <section className="rm-event-panel" aria-labelledby="rm-event-heading">
      <header className="rm-event-head">
        <h1 id="rm-event-heading">경기 이벤트 로그</h1>
        <div className="rm-event-legend" aria-label="이벤트 유형 색상"><span className="tone-kill"><i />킬</span><span className="tone-objective"><i />오브젝트</span><span className="tone-teamfight"><i />한타</span><span className="tone-phase"><i />전환</span></div>
      </header>
      <div className="rm-event-log rm-scroll-area" ref={logRef}>
        {events.map((event, index) => {
          const current = index === currentIndex;
          const future = event.occurredAtSeconds > currentSeconds;
          const presentation = eventPresentation[event.eventType];
          return <button type="button" key={event.id}
            className={`rm-event-row tone-${presentation.tone}${future ? ' is-future' : ''}${current ? ' is-current' : ''}${event.isMajor ? ' is-major' : ''}${selectedEventId === event.id ? ' is-selected' : ''}`}
            data-current={current} aria-pressed={selectedEventId === event.id} onClick={() => onSelect(event)}>
            <time>[{formatMatchTime(event.occurredAtSeconds)}]</time><span className="rm-event-dot" aria-hidden="true" />
            <span className="rm-event-message">{event.displayMessage}</span><span className="rm-event-type">{presentation.label}</span>
          </button>;
        })}
      </div>
    </section>
  );
}

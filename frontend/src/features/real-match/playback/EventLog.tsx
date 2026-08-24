import { useEffect, useRef } from 'react';
import { eventPresentation, formatMatchTime } from '../realMatch.adapter';
import type { PlaybackEventViewModel } from '../realMatch.types';

export function EventLog({ events, currentSeconds }: { events: readonly PlaybackEventViewModel[]; currentSeconds: number }) {
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
      <div className="rm-event-log rm-scroll-area" ref={logRef} aria-live="polite">
        {events.map((event, index) => {
          const isCurrent = index === currentIndex;
          return <MatchEventRow key={event.id} event={event} current={isCurrent} future={event.occurredAtSeconds > currentSeconds} />;
        })}
      </div>
    </section>
  );
}

function MatchEventRow({ event, current, future }: { event: PlaybackEventViewModel; current: boolean; future: boolean }) {
  const presentation = eventPresentation[event.eventType];
  return <article className={`rm-event-row tone-${presentation.tone}${future ? ' is-future' : ''}${current ? ' is-current' : ''}${event.isMajor ? ' is-major' : ''}`} data-current={current} data-engagement-id={event.engagementId ?? undefined} data-engagement-role={event.engagementRole ?? undefined}><time>[{formatMatchTime(event.occurredAtSeconds)}]</time><span className="rm-event-dot" aria-hidden="true" /><div>{event.description}</div><span className="rm-event-type">{presentation.label}</span></article>;
}

import type { TeamSide } from './realMatch.types';

export function MatchUtilityBar({ meta, onBack, secondaryLabel, onSecondary }: { meta: string; onBack: () => void; secondaryLabel?: string; onSecondary?: () => void }) {
  return (
    <header className="rm-utility-bar">
      <button className="rm-back-link" type="button" onClick={onBack}>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m14 6-6 6 6 6" /></svg>
        경기 센터로 돌아가기
      </button>
      <span className="rm-utility-divider" aria-hidden="true" />
      <span className="rm-utility-meta">{meta}</span>
      {secondaryLabel && onSecondary ? <button className="rm-back-link rm-utility-secondary" type="button" onClick={onSecondary}>{secondaryLabel}</button> : null}
    </header>
  );
}

export function TeamEmblem({ side, code }: { side: TeamSide; code: string }) {
  return (
    <div className="rm-team-emblem" role="img" aria-label={`${code} ${side === 'BLUE' ? '블루' : '레드'} 진영 엠블럼`}>
      {side === 'BLUE' ? (
        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 4 27 10v12L16 28 5 22V10zM10 12h12l-6 10z" /></svg>
      ) : (
        <svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="11" /><path d="M9 11h14M16 11v12M11 23h10" /></svg>
      )}
    </div>
  );
}

export function MatchToast({ title, message, visible }: { title: string; message: string; visible: boolean }) {
  return (
    <div className={`rm-toast${visible ? ' is-visible' : ''}`} role="status" aria-live="polite">
      <span className="rm-toast-mark" aria-hidden="true" />
      <div><strong>{title}</strong><span>{message}</span></div>
    </div>
  );
}

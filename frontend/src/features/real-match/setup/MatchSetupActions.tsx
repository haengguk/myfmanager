export function MatchSetupActions({
  ready,
  creating,
  elapsedSeconds,
  stageLabel,
  statusTitle,
  statusCopy,
  onStart,
  onCancel,
}: {
  ready: boolean;
  creating: boolean;
  elapsedSeconds: number;
  stageLabel: string;
  statusTitle: string;
  statusCopy: string;
  onStart: () => void;
  onCancel: () => void;
}) {
  return (
    <footer className="rm-setup-actions">
      <div aria-live="polite"><strong>{statusTitle}</strong><span>{statusCopy}</span></div>
      <div className="rm-setup-action-buttons">
        {creating ? <button className="rm-secondary-action" type="button" onClick={onCancel}>요청 취소</button> : null}
        <button className="rm-primary-action" type="button" disabled={!ready || creating} onClick={onStart}>
          {creating ? <><span className="rm-spinner" aria-hidden="true" />{stageLabel} · {elapsedSeconds}초</> : '실제 경기 실행'}
        </button>
      </div>
    </footer>
  );
}

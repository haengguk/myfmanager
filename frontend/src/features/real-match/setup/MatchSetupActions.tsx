export function MatchSetupActions({
  ready,
  creating,
  statusTitle,
  statusCopy,
  onStart,
}: {
  ready: boolean;
  creating: boolean;
  statusTitle: string;
  statusCopy: string;
  onStart: () => void;
}) {
  return (
    <footer className="rm-setup-actions">
      <div aria-live="polite"><strong>{statusTitle}</strong><span>{statusCopy}</span></div>
      <button className="rm-primary-action" type="button" disabled={!ready || creating} onClick={onStart}>
        {creating ? <><span className="rm-spinner" aria-hidden="true" />경기 생성 중…</> : '경기 시작'}
      </button>
    </footer>
  );
}

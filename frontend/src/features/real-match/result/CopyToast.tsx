export function CopyToast({ visible, label }: { visible: boolean; label: string }) {
  if (!visible) return null;
  return (
    <div className={`rm-copy-toast${visible ? ' is-visible' : ''}`} role="status" aria-live="polite">
      <i aria-hidden="true" /><div><strong>복사 완료</strong><span>{label} 값을 클립보드에 복사했습니다.</span></div>
    </div>
  );
}

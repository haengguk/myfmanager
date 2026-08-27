import { useEffect, useRef } from 'react';

export function PlayerDraftCancelDialog({ open, teamCode, pending, error, returnFocus, onClose, onConfirm }: {
  open: boolean; teamCode: string; pending: boolean; error: string | null;
  returnFocus: HTMLElement | null; onClose: () => void; onConfirm: () => void;
}) {
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    closeRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape' && !pending) onClose(); };
    window.addEventListener('keydown', onKeyDown);
    return () => { window.removeEventListener('keydown', onKeyDown); returnFocus?.focus(); };
  }, [open, onClose, pending, returnFocus]);
  if (!open) return null;
  return (
    <div className="pd-modal-backdrop" role="presentation">
      <section className="pd-cancel-dialog" role="dialog" aria-modal="true" aria-labelledby="pd-cancel-title" aria-describedby="pd-cancel-copy">
        <span className="pd-dialog-mark" aria-hidden="true">!</span>
        <h2 id="pd-cancel-title">직접 밴픽을 취소할까요?</h2>
        <p id="pd-cancel-copy">{teamCode}의 현재 선택과 AI 응답을 포함한 이 Draft 세션을 서버에서 취소합니다. 취소하면 이어서 진행할 수 없습니다.</p>
        {error ? <p className="pd-dialog-error" role="alert">{error}</p> : null}
        <div><button ref={closeRef} className="rm-secondary-action" type="button" disabled={pending} onClick={onClose}>계속 Draft 진행</button><button className="rm-primary-action is-danger" type="button" disabled={pending} onClick={onConfirm}>{pending ? '취소 확인 중…' : 'Draft 취소 후 설정으로 이동'}</button></div>
      </section>
    </div>
  );
}

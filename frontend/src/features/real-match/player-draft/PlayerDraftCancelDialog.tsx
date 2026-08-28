import { useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent } from 'react';

export function PlayerDraftCancelDialog({ open, teamCode, pending, error, returnFocus, onClose, onConfirm }: {
  open: boolean; teamCode: string; pending: boolean; error: string | null;
  returnFocus: HTMLElement | null; onClose: () => void; onConfirm: () => void;
}) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLElement>(null);
  const pendingRef = useRef(pending);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => { pendingRef.current = pending; }, [pending]);
  const focusableElements = () => dialogRef.current
    ? [...dialogRef.current.querySelectorAll<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )].filter((element) => !element.hasAttribute('hidden'))
    : [];
  const handleDialogKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') {
      event.preventDefault(); event.stopPropagation();
      if (!pending) onClose();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = focusableElements();
    if (!focusable.length) { event.preventDefault(); dialogRef.current?.focus(); return; }
    const first = focusable[0]; const last = focusable[focusable.length - 1];
    if (!dialogRef.current?.contains(document.activeElement)) { event.preventDefault(); first.focus(); }
    else if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  };
  useEffect(() => {
    if (!open) return;
    returnFocusRef.current = returnFocus;
    const focusFrame = window.requestAnimationFrame(() => closeRef.current?.focus());
    const keepFocusInside = (event: FocusEvent) => {
      const dialog = dialogRef.current;
      if (!dialog || dialog.contains(event.target as Node)) return;
      event.stopPropagation();
      if (!pendingRef.current && closeRef.current && !closeRef.current.disabled) closeRef.current.focus();
      else dialog.focus();
    };
    document.addEventListener('focusin', keepFocusInside, true);
    return () => {
      window.cancelAnimationFrame(focusFrame); document.removeEventListener('focusin', keepFocusInside, true);
      const target = returnFocusRef.current;
      window.requestAnimationFrame(() => {
        if (target?.isConnected && !target.hasAttribute('disabled')) target.focus();
        else document.querySelector<HTMLElement>('.rm-utility-bar .rm-back-link:not(:disabled)')?.focus();
      });
    };
  }, [open]);
  useEffect(() => {
    if (!open || !pending) return;
    const active = document.activeElement;
    if (!dialogRef.current?.contains(active) || (active instanceof HTMLButtonElement && active.disabled)) dialogRef.current?.focus();
  }, [open, pending]);
  if (!open) return null;
  return (
    <div className="pd-modal-backdrop" role="presentation">
      <section ref={dialogRef} className="pd-cancel-dialog" role="dialog" tabIndex={-1} aria-modal="true" aria-labelledby="pd-cancel-title" aria-describedby={`pd-cancel-copy${error ? ' pd-cancel-error' : ''}`} aria-busy={pending} onKeyDown={handleDialogKeyDown}>
        <span className="pd-dialog-mark" aria-hidden="true">!</span>
        <h2 id="pd-cancel-title">직접 밴픽을 취소할까요?</h2>
        <p id="pd-cancel-copy">{teamCode}의 현재 선택과 AI 응답을 포함한 이 Draft 세션을 서버에서 취소합니다. 취소하면 이어서 진행할 수 없습니다.</p>
        {error ? <p id="pd-cancel-error" className="pd-dialog-error" role="alert">{error}</p> : null}
        <div><button ref={closeRef} className="rm-secondary-action" type="button" disabled={pending} onClick={onClose}>계속 Draft 진행</button><button className="rm-primary-action is-danger" type="button" disabled={pending} onClick={onConfirm}>{pending ? '취소 확인 중…' : 'Draft 취소 후 설정으로 이동'}</button></div>
      </section>
    </div>
  );
}

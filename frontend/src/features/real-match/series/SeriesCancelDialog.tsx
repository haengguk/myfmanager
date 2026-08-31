import { useEffect, useRef, type KeyboardEvent as ReactKeyboardEvent } from 'react';

export function SeriesCancelDialog({ open, pending, error, returnFocus, onClose, onConfirm }: {
  open: boolean; pending: boolean; error: string | null; returnFocus: HTMLElement | null;
  onClose: () => void; onConfirm: () => void;
}) {
  const dialogRef = useRef<HTMLElement>(null); const continueRef = useRef<HTMLButtonElement>(null);
  const pendingRef = useRef(pending); const returnFocusRef = useRef<HTMLElement | null>(null);
  useEffect(() => { pendingRef.current = pending; }, [pending]);

  const focusableElements = () => dialogRef.current
    ? [...dialogRef.current.querySelectorAll<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )].filter((element) => !element.hasAttribute('hidden'))
    : [];
  const onKeyDown = (event: ReactKeyboardEvent<HTMLElement>) => {
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
    const frame = window.requestAnimationFrame(() => continueRef.current?.focus());
    const keepFocusInside = (event: FocusEvent) => {
      const dialog = dialogRef.current;
      if (!dialog || dialog.contains(event.target as Node)) return;
      event.stopPropagation();
      if (!pendingRef.current && continueRef.current && !continueRef.current.disabled) continueRef.current.focus();
      else dialog.focus();
    };
    document.addEventListener('focusin', keepFocusInside, true);
    return () => {
      window.cancelAnimationFrame(frame); document.removeEventListener('focusin', keepFocusInside, true);
      const target = returnFocusRef.current;
      window.requestAnimationFrame(() => {
        if (target?.isConnected && !target.hasAttribute('disabled')) target.focus();
        else document.querySelector<HTMLElement>('.sr-hub-actions button:not(:disabled)')?.focus();
      });
    };
  }, [open]);
  useEffect(() => {
    if (!open || !pending) return;
    const active = document.activeElement;
    if (!dialogRef.current?.contains(active) || (active instanceof HTMLButtonElement && active.disabled)) dialogRef.current?.focus();
  }, [open, pending]);
  if (!open) return null;
  return <div className="pd-modal-backdrop" role="presentation"><section ref={dialogRef} className="pd-cancel-dialog sr-cancel-dialog" role="alertdialog" tabIndex={-1} aria-modal="true" aria-labelledby="sr-cancel-title" aria-describedby={`sr-cancel-copy${error ? ' sr-cancel-error' : ''}`} aria-busy={pending} onKeyDown={onKeyDown}>
    <span aria-hidden="true">!</span><h2 id="sr-cancel-title">시리즈 전체를 취소할까요?</h2>
    <p id="sr-cancel-copy">현재 Draft와 실행 예약을 포함한 이 BO3/BO5를 종료합니다. 완료된 game 기록은 서버 TTL 동안 조회할 수 있지만 다시 진행할 수 없습니다.</p>
    {error ? <p id="sr-cancel-error" role="alert" aria-live="assertive">{error}</p> : null}
    <div><button ref={continueRef} type="button" disabled={pending} onClick={onClose}>계속 진행</button><button type="button" disabled={pending} onClick={onConfirm}>{pending ? '서버 취소 상태 확인 중…' : '시리즈 취소'}</button></div>
  </section></div>;
}

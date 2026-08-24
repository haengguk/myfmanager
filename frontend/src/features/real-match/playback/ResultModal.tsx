import { useEffect, useRef, type RefObject } from 'react';

export function ResultModal({ open, blueName, redName, blueScore, redScore, outcomeLabel, returnFocusRef, onClose, onConfirm }: { open: boolean; blueName: string; redName: string; blueScore: number; redScore: number; outcomeLabel: string; returnFocusRef: RefObject<HTMLButtonElement>; onClose: () => void; onConfirm: () => void }) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    closeButtonRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = [...dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), [href], input:not(:disabled), [tabindex]:not([tabindex="-1"])')];
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => { document.removeEventListener('keydown', onKeyDown); window.setTimeout(() => returnFocusRef.current?.focus(), 0); };
  }, [open, onClose, returnFocusRef]);

  if (!open) return null;
  return (
    <div className="rm-modal-layer" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="rm-dialog" ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="rm-result-title">
        <p className="rm-dialog-kicker">V8 Reference 결과</p><h2 id="rm-result-title">승인된 최종 결과를 확인할까요?</h2><p>현재 재생과 동일한 GEN 대 T1, seed 73의 실제 fixed response 결과를 표시합니다.</p>
        <div className="rm-result-grid"><div><span>{blueName}</span><strong>{blueScore}</strong></div><em>{outcomeLabel}</em><div><span>{redName}</span><strong>{redScore}</strong></div></div>
        <div className="rm-dialog-actions"><button className="rm-secondary-action" ref={closeButtonRef} type="button" onClick={onClose}>닫기</button><button className="rm-primary-action" type="button" onClick={onConfirm}>결과 화면으로 이동</button></div>
      </div>
    </div>
  );
}

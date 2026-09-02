import { useEffect, useRef } from 'react';

export function LeagueCancelDialog({ open, pending, returnFocus, onClose, onConfirm }: { open: boolean; pending: boolean; returnFocus: HTMLElement | null; onClose: () => void; onConfirm: () => void }) {
  const dialogRef = useRef<HTMLElement>(null); const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => { if (open) { if (pending) dialogRef.current?.focus(); else cancelRef.current?.focus(); } else returnFocus?.focus(); }, [open, pending, returnFocus]);
  if (!open) return null;
  return <div className="lm-modal-layer" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !pending) onClose(); }}><section ref={dialogRef} tabIndex={-1} className="lm-dialog lg-cancel-dialog" role="alertdialog" aria-modal="true" aria-labelledby="lg-cancel-title" aria-describedby="lg-cancel-copy" onKeyDown={(event) => {
    if (event.key === 'Escape' && !pending) onClose();
    if (event.key !== 'Tab') return; const controls = [...(dialogRef.current?.querySelectorAll<HTMLButtonElement>('button:not([disabled])') ?? [])]; if (controls.length === 0) { event.preventDefault(); return; } const first = controls[0]; const last = controls[controls.length - 1]; if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }}><p className="lm-dialog__label">DESTRUCTIVE COMMAND</p><h2 id="lg-cancel-title">시즌을 취소할까요?</h2><p id="lg-cancel-copy">현재 시즌은 다시 진행할 수 없습니다. 완료된 경기와 순위 기록은 서버 조회용으로 남습니다.</p><div className="lm-dialog__actions"><button ref={cancelRef} className="lm-secondary-button" type="button" disabled={pending} onClick={onClose}>돌아가기</button><button className="lg-danger" type="button" disabled={pending} onClick={onConfirm}>{pending ? '취소 확인 중…' : '시즌 취소'}</button></div></section></div>;
}

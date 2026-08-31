import { useEffect, useRef } from 'react';

export function SeriesCancelDialog({ open, pending, error, returnFocus, onClose, onConfirm }: {
  open: boolean; pending: boolean; error: string | null; returnFocus: HTMLElement | null;
  onClose: () => void; onConfirm: () => void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null); const continueRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return; const frame = requestAnimationFrame(() => continueRef.current?.focus());
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !pending) { event.preventDefault(); onClose(); return; }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const controls = [...dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled)')];
      const first = controls[0]; const last = controls[controls.length - 1]; if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { cancelAnimationFrame(frame); document.removeEventListener('keydown', onKey); returnFocus?.focus(); };
  }, [onClose, open, pending, returnFocus]);
  if (!open) return null;
  return <div className="pd-dialog-backdrop"><div ref={dialogRef} className="pd-cancel-dialog sr-cancel-dialog" role="alertdialog" aria-modal="true" aria-labelledby="sr-cancel-title" aria-describedby="sr-cancel-copy" aria-busy={pending}>
    <span aria-hidden="true">!</span><h2 id="sr-cancel-title">시리즈 전체를 취소할까요?</h2>
    <p id="sr-cancel-copy">현재 Draft와 실행 예약을 포함한 이 BO3/BO5를 종료합니다. 완료된 game 기록은 서버 TTL 동안 조회할 수 있지만 다시 진행할 수 없습니다.</p>
    {error ? <p role="alert">{error}</p> : null}
    <div><button ref={continueRef} type="button" disabled={pending} onClick={onClose}>계속 진행</button><button type="button" disabled={pending} onClick={onConfirm}>{pending ? '취소 확인 중…' : '시리즈 취소'}</button></div>
  </div></div>;
}

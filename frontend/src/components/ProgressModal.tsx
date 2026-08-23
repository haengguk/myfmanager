import { useEffect, useRef } from 'react';

interface ProgressModalProps {
  open: boolean;
  unreadImportantCount: number;
  onCancel: () => void;
  onConfirm: () => void;
}

export function ProgressModal({ open, unreadImportantCount, onCancel, onConfirm }: ProgressModalProps) {
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const frame = window.requestAnimationFrame(() => cancelButtonRef.current?.focus());
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancel();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])');
      if (!focusable?.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', onKeyDown);
      previouslyFocused?.focus();
    };
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div className="lm-modal-layer" onMouseDown={(event) => event.target === event.currentTarget && onCancel()}>
      <div className="lm-dialog" role="dialog" aria-modal="true" aria-labelledby="progress-dialog-title" aria-describedby="progress-dialog-description" ref={dialogRef}>
        <p className="lm-dialog__label">시간 진행</p>
        <h2 id="progress-dialog-title">오후 2:00까지 진행할까요?</h2>
        <p id="progress-dialog-description">읽지 않은 중요 메시지 {unreadImportantCount}건이 남아 있습니다. 진행 후에도 수신함에서 확인할 수 있습니다.</p>
        <div className="lm-dialog__actions">
          <button className="lm-secondary-button" type="button" onClick={onCancel} ref={cancelButtonRef}>취소</button>
          <button className="lm-primary-button" type="button" onClick={onConfirm}>계속 진행</button>
        </div>
      </div>
    </div>
  );
}

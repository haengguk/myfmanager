import type { ToastMessage } from '../features/inbox/inbox.types';

interface ToastProps {
  toast: ToastMessage | null;
}

export function Toast({ toast }: ToastProps) {
  return (
    <div className={`lm-toast${toast ? ' is-visible' : ''}`} role="status" aria-live="polite" aria-atomic="true">
      <span className="lm-toast__mark" aria-hidden="true" />
      <div>
        <strong>{toast?.title ?? '처리 완료'}</strong>
        <span>{toast?.message ?? '변경 사항이 반영되었습니다.'}</span>
      </div>
    </div>
  );
}

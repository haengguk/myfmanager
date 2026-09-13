import type { ToastMessage } from '../features/inbox/inbox.types';

interface ToastProps {
  toast: ToastMessage | null;
}

export function Toast({ toast }: ToastProps) {
  if (!toast) return null;
  return (
    <div className={`lm-toast${toast ? ' is-visible' : ''}`} role="status" aria-live="polite" aria-atomic="true">
      <span className="lm-toast__mark" aria-hidden="true" />
      <div>
        <strong>{toast.title}</strong>
        <span>{toast.message}</span>
      </div>
    </div>
  );
}

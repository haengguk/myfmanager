import { useEffect, useRef } from 'react';
import type { MatchResultViewModel } from '../matchSession.types';

export type ResultConfirmAction = 'rerun' | 'new-match';

export function ConfirmModal({ action, result, returnFocus, onClose, onConfirm }: { action: ResultConfirmAction | null; result: MatchResultViewModel; returnFocus: HTMLElement | null; onClose: () => void; onConfirm: (action: ResultConfirmAction) => void }) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!action) return;
    cancelRef.current?.focus();
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const controls = [...dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled)')];
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', keydown);
    return () => { document.removeEventListener('keydown', keydown); window.setTimeout(() => returnFocus?.focus(), 0); };
  }, [action, onClose, returnFocus]);
  if (!action) return null;
  const rerun = action === 'rerun';
  return (
    <div className="rm-modal-layer" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="rm-dialog" ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="rm-confirm-title">
        <p className="rm-dialog-kicker">{rerun ? `${result.integrity.source} 재생` : '새 경기'}</p>
        <h2 id="rm-confirm-title">{rerun ? '이 경기를 처음부터 재생할까요?' : '새 경기 준비로 이동할까요?'}</h2>
        <p>{rerun ? 'backend 요청을 다시 만들지 않고 현재 세션 timeline을 0:00부터 재생합니다.' : '현재 세션을 비우고 팀과 seed 선택 화면으로 이동합니다.'}</p>
        <div className="rm-dialog-condition"><strong>{result.teams.BLUE.code}</strong><span>seed {result.seed}</span><strong>{result.teams.RED.code}</strong></div>
        <div className="rm-dialog-actions"><button className="rm-secondary-action" ref={cancelRef} type="button" onClick={onClose}>취소</button><button className="rm-primary-action" type="button" onClick={() => onConfirm(action)}>{rerun ? '처음부터 재생' : '새 경기 준비'}</button></div>
      </div>
    </div>
  );
}

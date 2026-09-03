import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import type { TeamSummaryDto } from '../team-player/api/teamPlayerApi.types';
import { normalizeCareerSelection, type CareerCreateSelection } from './career.pointer';

function validDisplay(value: string): boolean {
  const normalized = value.normalize('NFC').trim();
  return [...normalized].length >= 1 && [...normalized].length <= 80 && ![...normalized].some((character) => /[\u0000-\u001f\u007f]/.test(character));
}

export function CareerCreateDialog({ teams, initial, pending, error, returnFocus, onClose, onCreate }: {
  teams: readonly TeamSummaryDto[];
  initial: CareerCreateSelection | null;
  pending: boolean;
  error: string | null;
  returnFocus: HTMLElement | null;
  onClose: () => void;
  onCreate: (selection: CareerCreateSelection) => void;
}) {
  const firstTeamCode = initial?.managedTeamCode ?? teams[0]?.teamCode ?? '';
  const [saveName, setSaveName] = useState(initial?.saveName ?? (firstTeamCode ? `${firstTeamCode} 장기 저장` : ''));
  const [managerName, setManagerName] = useState(initial?.managerName ?? '');
  const [teamCode, setTeamCode] = useState(firstTeamCode);
  const [validation, setValidation] = useState<string | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const firstRef = useRef<HTMLInputElement>(null);
  const saveNameEditedRef = useRef(initial !== null);

  useEffect(() => {
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : returnFocus;
    firstRef.current?.focus();
    return () => trigger?.focus();
  }, [returnFocus]);

  useEffect(() => {
    if (initial || teamCode || !teams[0]) return;
    setTeamCode(teams[0].teamCode);
    if (!saveNameEditedRef.current) setSaveName(`${teams[0].teamCode} 장기 저장`);
  }, [initial, teamCode, teams]);

  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape' && !pending) { event.preventDefault(); onClose(); return; }
    if (event.key !== 'Tab') return;
    const focusable = [...(dialogRef.current?.querySelectorAll<HTMLElement>('input, select, button:not(:disabled)') ?? [])];
    if (focusable.length === 0) return;
    const first = focusable[0]; const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!validDisplay(saveName) || !validDisplay(managerName)) { setValidation('저장 이름과 감독 이름은 1~80자의 제어문자 없는 값이어야 합니다.'); return; }
    if (!teams.some((team) => team.teamCode === teamCode)) { setValidation('서버에서 확인한 LCK 팀을 선택하세요.'); return; }
    setValidation(null); onCreate(normalizeCareerSelection({ saveName, managerName, managedTeamCode: teamCode }));
  }

  return <div className="ca-modal-layer">
    <div ref={dialogRef} className="ca-dialog" role="dialog" aria-modal="true" aria-labelledby="ca-create-title" aria-describedby="ca-create-copy" onKeyDown={keyDown}>
      <header><div><span>NEW CAREER</span><h2 id="ca-create-title">새 커리어 만들기</h2></div><button type="button" aria-label="새 커리어 창 닫기" onClick={onClose} disabled={pending}>×</button></header>
      <p id="ca-create-copy">선택한 팀으로 전용 LCK Hybrid Season과 첫 전체 캘린더를 한 번 생성합니다. 감독 이름은 직접 입력하세요.</p>
      <form onSubmit={submit} noValidate>
        <label><span>저장 이름</span><input ref={firstRef} value={saveName} onChange={(event) => { saveNameEditedRef.current = true; setSaveName(event.target.value); }} maxLength={80} autoComplete="off" disabled={pending} /></label>
        <label><span>감독 이름</span><input value={managerName} onChange={(event) => setManagerName(event.target.value)} maxLength={80} autoComplete="name" disabled={pending} /></label>
        <label><span>관리 팀</span><select value={teamCode} onChange={(event) => { const next = event.target.value; setTeamCode(next); if (!saveNameEditedRef.current) setSaveName(`${next} 장기 저장`); }} disabled={pending || teams.length === 0}>{teams.map((team) => <option key={team.teamCode} value={team.teamCode}>{team.teamCode} · LCK 주전 {team.starterCount}명</option>)}</select></label>
        {validation || error ? <div className="ca-dialog__error" role="alert">{validation ?? error}</div> : null}
        <footer><button type="button" className="lm-secondary-button" onClick={onClose} disabled={pending}>취소</button><button type="submit" className="lm-primary-button" disabled={pending || teams.length === 0}>{pending ? '서버 저장 확인 중…' : '커리어 생성'}</button></footer>
      </form>
    </div>
  </div>;
}

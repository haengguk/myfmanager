import { SetupErrorMessage } from './SetupErrorMessage';

interface SeedInputProps {
  value: string; disabled: boolean; referenceApplied: boolean; error: string | null;
  onChange: (value: string) => void; onUseReference: () => void; onClear: () => void;
}

export function SeedInput({ value, disabled, referenceApplied, error, onChange, onUseReference, onClear }: SeedInputProps) {
  return (
    <div className="rm-seed-form">
      <label className="rm-seed-label" htmlFor="rm-seed-input">signed long seed <span>JSON string</span></label>
      <div className="rm-seed-control">
        <input id="rm-seed-input" className={error ? 'is-invalid' : ''} type="text" value={value}
          inputMode="numeric" autoComplete="off" spellCheck={false} disabled={disabled}
          aria-invalid={Boolean(error)} aria-describedby="rm-seed-message" onChange={(event) => onChange(event.target.value)} />
        <button type="button" disabled={disabled} aria-label="승인 reference GEN 대 T1, seed 73 적용" title="승인 reference 적용" onClick={onUseReference}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5 9.2 17 19 7" /></svg>
        </button>
        <button type="button" disabled={disabled} aria-label="seed 입력값 지우기" title="seed 입력값 지우기" onClick={onClear}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 7 10 10M17 7 7 17" /></svg>
        </button>
      </div>
      <div id="rm-seed-message" className={`rm-seed-message${referenceApplied && !error ? ' is-success' : ''}`} aria-live="polite">
        {error ? <SetupErrorMessage>{error}</SetupErrorMessage> : <><span aria-hidden="true">i</span><p>{referenceApplied ? '승인된 reference seed 73이 적용되었습니다.' : 'canonical signed-int64 decimal string만 사용할 수 있습니다.'}</p></>}
      </div>
    </div>
  );
}

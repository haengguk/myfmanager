import { SetupErrorMessage } from './SetupErrorMessage';

interface SeedInputProps {
  value: string; disabled: boolean; defaultApplied: boolean; error: string | null;
  onChange: (value: string) => void; onUseDefault: () => void; onClear: () => void;
}

export function SeedInput({ value, disabled, defaultApplied, error, onChange, onUseDefault, onClear }: SeedInputProps) {
  return (
    <div className="rm-seed-form">
      <label className="rm-seed-label" htmlFor="rm-seed-input">signed long seed <span>JSON string</span></label>
      <div className="rm-seed-control">
        <input id="rm-seed-input" className={error ? 'is-invalid' : ''} type="text" value={value}
          inputMode="numeric" autoComplete="off" spellCheck={false} disabled={disabled}
          aria-invalid={Boolean(error)} aria-describedby="rm-seed-message" onChange={(event) => onChange(event.target.value)} />
        <button type="button" disabled={disabled} aria-label="기본 seed 73 적용" title="기본 seed 적용" onClick={onUseDefault}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5 9.2 17 19 7" /></svg>
        </button>
        <button type="button" disabled={disabled} aria-label="seed 입력값 지우기" title="seed 입력값 지우기" onClick={onClear}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 7 10 10M17 7 7 17" /></svg>
        </button>
      </div>
      <div id="rm-seed-message" className={`rm-seed-message${defaultApplied && !error ? ' is-success' : ''}`} aria-live="polite">
        {error ? <SetupErrorMessage>{error}</SetupErrorMessage> : <><span aria-hidden="true">i</span><p>{defaultApplied ? '기본 seed 73이 적용되었습니다.' : 'canonical signed-int64 decimal string만 사용할 수 있습니다.'}</p></>}
      </div>
    </div>
  );
}

import { SetupErrorMessage } from './SetupErrorMessage';

interface SeedInputProps {
  value: string;
  disabled: boolean;
  generated: boolean;
  error: string | null;
  onChange: (value: string) => void;
  onGenerate: () => void;
  onClear: () => void;
}

export function SeedInput({ value, disabled, generated, error, onChange, onGenerate, onClear }: SeedInputProps) {
  return (
    <div className="rm-seed-form">
      <label className="rm-seed-label" htmlFor="rm-seed-input">문자열 seed <span>1–48자</span></label>
      <div className="rm-seed-control">
        <input
          id="rm-seed-input"
          className={error ? 'is-invalid' : ''}
          type="text"
          value={value}
          maxLength={48}
          autoComplete="off"
          spellCheck={false}
          disabled={disabled}
          aria-invalid={Boolean(error)}
          aria-describedby="rm-seed-message"
          onChange={(event) => onChange(event.target.value)}
        />
        <button type="button" disabled={disabled} aria-label="seed 자동 생성" title="seed 자동 생성" onClick={onGenerate}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h3c5 0 5 10 10 10h3m-3-3 3 3-3 3M4 17h3c2 0 3-1.5 4-3m3-4c1-2 2-3 3-3h3m-3-3 3 3-3 3" /></svg>
        </button>
        <button type="button" disabled={disabled} aria-label="seed 입력값 지우기" title="seed 입력값 지우기" onClick={onClear}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 7 10 10M17 7 7 17" /></svg>
        </button>
      </div>
      <div id="rm-seed-message" className={`rm-seed-message${generated && !error ? ' is-success' : ''}`} aria-live="polite">
        {error ? <SetupErrorMessage>{error}</SetupErrorMessage> : <><span aria-hidden="true">i</span><p>{generated ? '새 seed가 자동으로 생성되었습니다.' : '같은 팀과 seed를 사용하면 동일한 경기 조건을 재현할 수 있습니다.'}</p></>}
      </div>
    </div>
  );
}

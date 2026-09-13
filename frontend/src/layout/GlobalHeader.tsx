import { Icon } from '../components/Icon';

interface GlobalHeaderProps {
  screenTitle: string;
  searchValue: string;
  gameTime: string;
  primaryActionLabel?: string;
  searchPlaceholder?: string;
  contextMode?: 'DEFAULT' | 'CAREER';
  onSearchChange: (value: string) => void;
  onContinue: () => void;
  onNotify: (title: string, message: string) => void;
}

export function GlobalHeader({ screenTitle, searchValue, primaryActionLabel = '다음 진행', searchPlaceholder = '메시지 검색…', contextMode = 'DEFAULT', onSearchChange, onContinue }: GlobalHeaderProps) {
  const career = contextMode === 'CAREER';
  return (
    <header className="lm-global-header">
      <div className="lm-screen-name">
        <strong>{screenTitle}</strong>
        <span>{career ? '선택 Career에 연결된 정규리그' : '추가 모드 · 활성 Career와 별도 운영'}</span>
      </div>
      <label className="lm-global-search">
        <Icon name="search" />
        <span className="lm-sr-only">{searchPlaceholder}</span>
        <input
          type="search"
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={searchPlaceholder}
          autoComplete="off"
        />
      </label>
      <button className="lm-primary-button lm-continue-button" type="button" onClick={onContinue}>{primaryActionLabel}</button>
    </header>
  );
}

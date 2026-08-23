import { Icon } from '../components/Icon';

interface GlobalHeaderProps {
  screenTitle: string;
  searchValue: string;
  gameTime: string;
  onSearchChange: (value: string) => void;
  onContinue: () => void;
  onNotify: (title: string, message: string) => void;
}

export function GlobalHeader({ screenTitle, searchValue, gameTime, onSearchChange, onContinue, onNotify }: GlobalHeaderProps) {
  return (
    <header className="lm-global-header">
      <button className="lm-icon-button" type="button" aria-label="이전 화면" title="이전 화면" onClick={() => onNotify('이전 화면', '이전 화면 기록이 없습니다.')}>
        <Icon name="chevron" />
      </button>
      <button className="lm-icon-button" type="button" aria-label="다음 화면" title="다음 화면" onClick={() => onNotify('다음 화면', '다음 화면 기록이 없습니다.')}>
        <Icon name="chevron" className="lm-icon--reverse" />
      </button>
      <div className="lm-header-divider" aria-hidden="true" />
      <div className="lm-team-emblem" aria-label="Northvale 중립 팀 엠블럼" role="img">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m12 3 7 4v9l-7 5-7-5V7z" />
          <path d="m8 15 4-7 4 7M9.5 12.5h5" />
        </svg>
      </div>
      <div className="lm-screen-name">
        <strong>{screenTitle}</strong>
        <span>Northvale 운영 본부</span>
      </div>
      <div className="lm-schedule">
        <span>다음 경기 · 오후 7:00</span>
        <strong>Northvale 대 Coastline · BO3</strong>
      </div>
      <label className="lm-global-search">
        <Icon name="search" />
        <span className="lm-sr-only">전체 메시지 검색</span>
        <input
          type="search"
          value={searchValue}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="메시지 검색…"
          autoComplete="off"
        />
      </label>
      <button className="lm-icon-button lm-notification" type="button" aria-label="알림 3건 보기" title="알림" onClick={() => onNotify('알림 3건', '중요 메시지와 오늘 경기 준비 알림이 있습니다.')}>
        <Icon name="bell" />
      </button>
      <div className="lm-date-time">
        <strong>2026년 8월 24일</strong>
        <span>{gameTime}</span>
      </div>
      <button className="lm-icon-button" type="button" aria-label="설정 열기" title="설정" onClick={() => onNotify('설정', '환경 설정은 아직 준비 중입니다.')}>
        <Icon name="settings" />
      </button>
      <button className="lm-primary-button lm-continue-button" type="button" onClick={onContinue}>다음 진행</button>
    </header>
  );
}

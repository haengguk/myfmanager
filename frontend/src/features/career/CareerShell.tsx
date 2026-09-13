import { useEffect, type ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import type { AppSection } from '../../layout/Sidebar';
import { careerPages, type CareerPage } from './careerWorkspace';
import './careerWorkspace.css';

export function CareerShell({ children, page, club, date, year, historical, scopeLabel, status, managing, disabled, onPage, onSaves, onMain, onTool, onContinue, onRefresh }: {
  children: ReactNode; page: CareerPage; club: string | null; date: string | null; year: number | null;
  scopeLabel?: string; historical: boolean; status: string; managing: boolean; disabled: boolean;
  onPage: (page: CareerPage) => void; onSaves: () => void; onMain?: () => void; onTool?: (tool: AppSection) => void;
  onContinue: () => void; onRefresh: () => void;
}) {
  useEffect(() => { document.title = managing && club ? `${club} · ${careerPages.find(([id]) => id === page)?.[1]} — lolmanager` : 'Career 시작 — lolmanager'; }, [club, page, managing]);
  return <div className="lm-app-shell ca-club-shell">
    <a className="ca-skip" href="#ca-page-title">본문으로 이동</a>
    <aside className="lm-rail">
      <div className="lm-brand"><div className="lm-brand__mark">LM</div><div className="lm-brand__copy"><strong>LOL MANAGER</strong><span>구단 운영</span></div></div>
      <nav className="lm-rail__nav" aria-label="구단 메뉴">
        <div className="lm-rail__group">{careerPages.map(([id, label, icon]) => <button key={id} type="button" className={`lm-nav-item${managing && page === id ? ' is-active' : ''}`} aria-current={managing && page === id ? 'page' : undefined} title={label} aria-label={label} disabled={!club} onClick={() => onPage(id)}><Icon name={icon} /><span>{label}</span></button>)}</div>
        <div className="lm-rail__group"><button className="lm-nav-item" onClick={onSaves}><Icon name="club" /><span>저장 불러오기</span></button></div>
        {onMain ? <button className="lm-nav-item" onClick={onMain}><Icon name="home" /><span>게임 메인</span></button> : null}
        {onTool ? <details className="ca-tools"><summary>추가 모드 · 데이터</summary><button onClick={() => onTool('match')}>단독 경기 · Series</button><button onClick={() => onTool('league')}>독립 AI 리그</button><button onClick={() => onTool('squad')}>전역 선수 데이터</button></details> : null}
      </nav>
    </aside>
    <header className="lm-global-header ca-club-header">
      <div className="lm-screen-name"><strong>{club ?? 'Career 시작'}</strong><span>{managing ? careerPages.find(([id]) => id === page)?.[1] : '새 구단 운영 · 저장 불러오기'}</span></div>
      <div className="ca-header-context"><strong>{date ?? '저장 선택 대기'}</strong><span>{scopeLabel ?? (year ? `${historical ? '조회' : '운영'} 시즌 ${year}${historical ? ' · 과거 기록' : ''}` : '게임 날짜는 저장 선택 후 표시됩니다')}</span></div>
      <span className="ca-progress-status" role="status">{status}</span>
      {club ? <><button className="lm-secondary-button" disabled={disabled} onClick={onRefresh}>새로고침</button><button className="lm-primary-button" onClick={onContinue}>진행 · 경기 준비</button></> : null}
    </header>
    {children}
  </div>;
}

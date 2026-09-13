import { Icon, type IconName } from '../components/Icon';

export type AppSection = 'inbox' | 'career' | 'squad' | 'match' | 'league';

interface SidebarProps {
  activeSection: AppSection;
  onNavigate: (section: AppSection) => void;
  onUnavailable: (label: string) => void;
}

type NavigationItem = {
  readonly label: string;
  readonly icon: IconName;
  readonly section?: AppSection;
};

const navigationGroups: readonly (readonly NavigationItem[])[] = [
  [{ label: 'Career 구단 운영', icon: 'club', section: 'career' }],
  [{ label: '전역 선수 데이터', icon: 'users', section: 'squad' }, { label: '독립 AI 리그', icon: 'league', section: 'league' }, { label: '단독 경기', icon: 'match', section: 'match' }],
];

export function Sidebar({ activeSection, onNavigate, onUnavailable }: SidebarProps) {
  return (
    <aside className="lm-rail">
      <div className="lm-brand" aria-label="lolmanager">
        <div className="lm-brand__mark" aria-hidden="true">LM</div>
        <div className="lm-brand__copy"><strong>LOL MANAGER</strong><span>FRONT OFFICE</span></div>
      </div>
      <nav className="lm-rail__nav" aria-label="주 메뉴">
        {navigationGroups.map((group, groupIndex) => (
          <div className="lm-rail__group" key={`navigation-group-${groupIndex}`}>
            {group.map((item) => {
              const active = item.section === activeSection;
              return (
                <button
                  className={`lm-nav-item${active ? ' is-active' : ''}`}
                  type="button"
                  key={item.label}
                  aria-current={active ? 'page' : undefined}
                  aria-label={`${item.label}${active ? ', 현재 화면' : ''}`}
                  title={item.label}
                  data-tooltip={item.label}
                  onClick={() => item.section ? onNavigate(item.section) : onUnavailable(item.label)}
                >
                  <Icon name={item.icon} />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        ))}
      </nav>
      <div className="lm-rail__status">추가 모드 · 데이터</div>
    </aside>
  );
}

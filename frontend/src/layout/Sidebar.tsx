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
  [
    { label: '홈', icon: 'home' },
    { label: '수신함', icon: 'inbox', section: 'inbox' },
  ],
  [
    { label: '커리어', icon: 'club', section: 'career' },
    { label: '선수단', icon: 'users', section: 'squad' },
    { label: '전술 및 라인업', icon: 'tactics' },
    { label: '훈련', icon: 'training' },
    { label: '일정', icon: 'calendar' },
    { label: '대회', icon: 'league', section: 'league' },
    { label: '경기 센터', icon: 'match', section: 'match' },
  ],
  [
    { label: '스카우팅', icon: 'scout' },
    { label: '이적 시장', icon: 'transfer' },
    { label: '코칭스태프', icon: 'staff' },
  ],
  [
    { label: '구단 정보', icon: 'club' },
    { label: '재정', icon: 'finance' },
    { label: '육성 센터', icon: 'academy' },
  ],
];

export function Sidebar({ activeSection, onNavigate, onUnavailable }: SidebarProps) {
  return (
    <aside className="lm-rail">
      <div className="lm-brand" aria-label="lolmanager">
        <div className="lm-brand__mark" aria-hidden="true">LM</div>
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
      <div className="lm-rail__status">24일 차</div>
    </aside>
  );
}

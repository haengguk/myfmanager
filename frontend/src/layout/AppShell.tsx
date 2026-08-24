import type { ReactNode } from 'react';
import { GlobalHeader } from './GlobalHeader';
import { Sidebar, type AppSection } from './Sidebar';

interface AppShellProps {
  activeSection: AppSection;
  screenTitle: string;
  searchValue: string;
  gameTime: string;
  primaryActionLabel?: string;
  children: ReactNode;
  onNavigate: (section: AppSection) => void;
  onSearchChange: (value: string) => void;
  onContinue: () => void;
  onNotify: (title: string, message: string) => void;
}

export function AppShell({ activeSection, screenTitle, searchValue, gameTime, primaryActionLabel, children, onNavigate, onSearchChange, onContinue, onNotify }: AppShellProps) {
  return (
    <div className="lm-app-shell">
      <Sidebar activeSection={activeSection} onNavigate={onNavigate} onUnavailable={(label) => onNotify(label, '이 메뉴는 다음 운영 업데이트에서 제공됩니다.')} />
      <GlobalHeader
        screenTitle={screenTitle}
        searchValue={searchValue}
        gameTime={gameTime}
        primaryActionLabel={primaryActionLabel}
        onSearchChange={onSearchChange}
        onContinue={onContinue}
        onNotify={onNotify}
      />
      {children}
    </div>
  );
}

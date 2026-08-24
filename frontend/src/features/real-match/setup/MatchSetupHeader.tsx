import { MatchUtilityBar } from '../MatchChrome';
import type { MatchSetupOptionsViewModel } from '../matchSession.types';

export function MatchSetupHeader({ options, onBack }: { options: MatchSetupOptionsViewModel; onBack: () => void }) {
  return (
    <>
      <MatchUtilityBar meta={options.seasonLabel} onBack={onBack} />
      <section className="rm-setup-context" aria-labelledby="match-setup-heading">
        <div>
          <span>경기 센터 / 경기 준비</span>
          <h1 id="match-setup-heading">경기 준비</h1>
        </div>
        <div className="rm-setup-meta" aria-label="경기 조건">
          <span><small>현재 게임</small><strong>Game {options.gameNumber}</strong></span>
          <span><small>경기 형식</small><strong>{options.seriesType}</strong></span>
          <span><small>드래프트</small><strong>{options.draftRule}</strong></span>
          <em>팀당 5명</em>
        </div>
      </section>
    </>
  );
}

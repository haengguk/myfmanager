import { MatchUtilityBar } from '../MatchChrome';
import type { MatchDraftMode, MatchSetupOptionsViewModel } from '../matchSession.types';

export function MatchSetupHeader({ options, draftMode, onBack, onLegacy, onSeries }: { options: MatchSetupOptionsViewModel; draftMode: MatchDraftMode; onBack: () => void; onLegacy: () => void; onSeries: () => void }) {
  return (
    <>
      <MatchUtilityBar meta={options.seasonLabel} onBack={onBack} backLabel="대시보드로 돌아가기" secondaryLabel="기존 시뮬레이터" onSecondary={onLegacy} />
      <section className="rm-setup-context" aria-labelledby="match-setup-heading">
        <div>
          <span>경기 센터 / 경기 준비</span>
          <h1 id="match-setup-heading">경기 준비</h1>
        </div>
        <div className="rm-setup-meta" aria-label="경기 조건">
          <span><small>현재 게임</small><strong>Game {options.gameNumber}</strong></span>
          <span><small>경기 형식</small><strong>{options.seriesType}</strong></span>
          <span><small>드래프트</small><strong>{draftMode === 'PLAYER_CONTROLLED' ? 'Professional Draft · 직접' : options.draftRule}</strong></span>
          <em>팀당 5명</em>
          <button type="button" className="rm-series-entry" onClick={onSeries}>BO3 / BO5 시리즈</button>
        </div>
      </section>
    </>
  );
}

import { TeamEmblem } from '../MatchChrome';
import type { DraftViewModel } from '../realMatch.types';

export function DraftHeader({ viewModel }: { viewModel: DraftViewModel }) {
  return (
    <section className="rm-match-header" aria-label="자동 Draft 경기 정보">
      {(['BLUE', 'RED'] as const).map((side) => {
        const team = viewModel.teams[side];
        return <div className={`rm-team-head rm-side-${side.toLowerCase()}`} key={side}>
          <TeamEmblem side={side} code={team.code} />
          <div className="rm-team-copy"><span>{side} · {side === 'BLUE' ? '블루 진영' : '레드 진영'}</span><h2>{team.code}</h2><p>{team.detail}</p></div>
          <div className="rm-series-record"><span>Game</span><strong>1</strong></div>
        </div>;
      })}
      <div className="rm-draft-clock rm-auto-draft-clock">
        <div className="rm-clock-meta"><strong>Game {viewModel.gameNumber}</strong><span>·</span><span>seed {viewModel.simulationSeed}</span><span>·</span><span>읽기 전용</span></div>
        <div className="rm-auto-draft-status" aria-label="자동 Draft 완료"><strong>20 / 20</strong><span>자동 Draft 완료</span></div>
        <div className="rm-reference-badge">V8 Reference Fixture</div>
      </div>
    </section>
  );
}

import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import { TeamEmblem } from '../MatchChrome';
import type { ChampionViewModel, DraftTurnViewModel, DraftViewModel } from '../realMatch.types';

interface DraftHeaderProps {
  viewModel: DraftViewModel;
  championsById: Readonly<Record<string, ChampionViewModel>>;
  currentTurn: DraftTurnViewModel | null;
  seconds: number;
  complete: boolean;
}

export function DraftHeader({ viewModel, championsById, currentTurn, seconds, complete }: DraftHeaderProps) {
  return (
    <section className="rm-match-header" aria-label="밴픽 경기 정보">
      {(['BLUE', 'RED'] as const).map((side) => {
        const team = viewModel.teams[side];
        return <div className={`rm-team-head rm-side-${side.toLowerCase()}`} key={side}><TeamEmblem side={side} code={team.code} /><div className="rm-team-copy"><span>{side} · {side === 'BLUE' ? '블루 진영' : '레드 진영'}</span><h2>{team.code}</h2><p>{team.record}</p></div><div className="rm-series-record"><span>시리즈</span><strong>{team.seriesScore}</strong></div></div>;
      })}
      <div className="rm-draft-clock"><DraftTimer viewModel={viewModel} currentTurn={currentTurn} seconds={seconds} complete={complete} /><FearlessChampionList viewModel={viewModel} championsById={championsById} /></div>
    </section>
  );
}

function DraftTimer({ viewModel, currentTurn, seconds, complete }: Pick<DraftHeaderProps, 'viewModel' | 'currentTurn' | 'seconds' | 'complete'>) {
  const phaseLabel = currentTurn ? `${currentTurn.round}차 ${currentTurn.phase === 'BAN' ? '밴' : '픽'}` : '밴픽 완료';
  const turnLabel = currentTurn ? `${currentTurn.side} · ${currentTurn.phase === 'BAN' ? '챔피언 밴' : `${currentTurn.position} 픽`} 차례` : '모든 선택 완료';
  return <><div className="rm-clock-meta"><strong>Game {viewModel.gameNumber}</strong><span>·</span><span>{viewModel.seriesType}</span><span>·</span><span>{phaseLabel}</span></div><div className={`rm-timer${seconds <= 10 && !complete ? ' is-urgent' : ''}`} aria-live="polite">{String(complete ? 0 : seconds).padStart(2, '0')}</div><div className="rm-turn-copy">{turnLabel}</div></>;
}

function FearlessChampionList({ viewModel, championsById }: Pick<DraftHeaderProps, 'viewModel' | 'championsById'>) {
  return <div className="rm-fearless-row"><span>Game 2 · 하드 피어리스</span>{viewModel.fearlessChampionIds.map((id) => <span className="rm-portrait" key={id} title={`이전 게임 사용: ${championsById[id].name}`}><ChampionPortrait name={championsById[id].name} portraitUrl={championsById[id].portraitUrl} /></span>)}</div>;
}

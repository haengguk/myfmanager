import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { ChampionViewModel } from '../realMatch.types';
import type { FinalPlayerViewModel, MatchResultViewModel } from '../matchSession.types';

const formatGold = (value: number) => `${(value / 1000).toFixed(1)}K`;
const formatDifference = (value: number) => value === 0 ? '0' : `${value > 0 ? '+' : ''}${value.toLocaleString('ko-KR')}`;

function PlayerCell({ player, champion }: { player: FinalPlayerViewModel; champion: ChampionViewModel | undefined }) {
  return (
    <div className="rm-result-player">
      <span className="rm-portrait">
        {champion ? <ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /> : <span className="champion-fallback">—</span>}
      </span>
      <span><strong>{player.playerName}</strong><small>{champion?.name ?? '선택 없음'}</small></span>
    </div>
  );
}

function Metrics({ player }: { player: FinalPlayerViewModel }) {
  return (
    <div className="rm-result-player-metrics">
      <span><small>KDA</small><strong>{player.kills}/{player.deaths}/{player.assists}</strong></span>
      <span><small>CS</small><strong>{player.cs}</strong></span>
      <span><small>골드</small><strong>{formatGold(player.gold)}</strong></span>
      <span><small>라인 골드</small><strong className={player.laneGoldDifference > 0 ? 'is-positive' : player.laneGoldDifference < 0 ? 'is-negative' : ''}>{formatDifference(player.laneGoldDifference)}</strong></span>
      <span><small>레벨</small><strong>{player.level}</strong></span>
    </div>
  );
}

export function PlayerResultComparison({ result, championsById }: { result: MatchResultViewModel; championsById: Readonly<Record<string, ChampionViewModel>> }) {
  return (
    <section className="rm-player-results" aria-labelledby="rm-player-results-heading">
      <h2 id="rm-player-results-heading">선수 최종 기록</h2>
      <div className="rm-player-results-head" aria-hidden="true"><span>{result.teams.BLUE.code}</span><span>포지션별 비교</span><span>{result.teams.RED.code}</span></div>
      <div className="rm-player-result-list">
        {result.players.map((row) => (
          <div className="rm-player-result-row" key={row.position}>
            <div className="rm-result-side-data"><PlayerCell player={row.blue} champion={championsById[row.blue.championId]} /><Metrics player={row.blue} /></div>
            <strong className="rm-result-position">{row.position}</strong>
            <div className="rm-result-side-data rm-side-red"><Metrics player={row.red} /><PlayerCell player={row.red} champion={championsById[row.red.championId]} /></div>
          </div>
        ))}
      </div>
    </section>
  );
}

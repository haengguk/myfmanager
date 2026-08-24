import { useState } from 'react';
import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { FinalPlayerViewModel, MatchResultViewModel } from '../matchSession.types';
import type { ChampionViewModel } from '../realMatch.types';
import { AbilityProfileModal } from './AbilityProfileModal';

const formatGold = (value: number) => `${(value / 1000).toFixed(1)}K`;
const formatDifference = (value: number) => value === 0 ? '0' : `${value > 0 ? '+' : ''}${value.toLocaleString('ko-KR')}`;

function PlayerCell({ player, champion, onInspect }: { player: FinalPlayerViewModel; champion: ChampionViewModel | undefined; onInspect: () => void }) {
  return (
    <div className="rm-result-player">
      <span className="rm-portrait">{champion ? <ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /> : <span className="champion-fallback">—</span>}</span>
      <span><strong>{player.playerName}</strong><small>{champion?.name ?? player.championId}</small><button type="button" onClick={onInspect}>능력치</button></span>
    </div>
  );
}

function Metrics({ player }: { player: FinalPlayerViewModel }) {
  return (
    <div className="rm-result-player-metrics">
      <span><small>KDA</small><strong>{player.kills}/{player.deaths}/{player.assists}</strong></span>
      <span><small>CS</small><strong>{player.cs}</strong></span>
      <span><small>골드</small><strong>{formatGold(player.gold)}</strong></span>
      <span><small>골드 차</small><strong className={player.goldDifference > 0 ? 'is-positive' : player.goldDifference < 0 ? 'is-negative' : ''}>{formatDifference(player.goldDifference)}</strong></span>
      <span><small>레벨</small><strong>{player.level}</strong></span>
    </div>
  );
}

export function PlayerResultComparison({ result, championsById }: { result: MatchResultViewModel; championsById: Readonly<Record<string, ChampionViewModel>> }) {
  const [selectedPlayer, setSelectedPlayer] = useState<FinalPlayerViewModel | null>(null);
  return (
    <section className="rm-player-results" aria-labelledby="rm-player-results-heading">
      <h2 id="rm-player-results-heading">선수 최종 기록 · V8 ability profile</h2>
      <div className="rm-player-results-head" aria-hidden="true"><span>{result.teams.BLUE.code}</span><span>포지션별 비교</span><span>{result.teams.RED.code}</span></div>
      <div className="rm-player-result-list">
        {result.players.map((row) => (
          <div className="rm-player-result-row" key={row.position}>
            <div className="rm-result-side-data"><PlayerCell player={row.blue} champion={championsById[row.blue.championId]} onInspect={() => setSelectedPlayer(row.blue)} /><Metrics player={row.blue} /></div>
            <strong className="rm-result-position">{row.position}</strong>
            <div className="rm-result-side-data rm-side-red"><Metrics player={row.red} /><PlayerCell player={row.red} champion={championsById[row.red.championId]} onInspect={() => setSelectedPlayer(row.red)} /></div>
          </div>
        ))}
      </div>
      <AbilityProfileModal player={selectedPlayer} champion={selectedPlayer ? championsById[selectedPlayer.championId] : undefined} onClose={() => setSelectedPlayer(null)} />
    </section>
  );
}

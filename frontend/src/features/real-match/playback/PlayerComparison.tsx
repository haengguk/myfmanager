import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import { formatMatchTime } from '../realMatch.adapter';
import type { ChampionViewModel, PositionComparisonViewModel } from '../realMatch.types';

const formatGold = (value: number) => `${value.toFixed(1)}K`;
const formatLead = (value: number) => `+${Math.abs(value).toFixed(1)}K`;

export function PlayerComparison({ rows, currentSeconds, championsById, blueTeamCode, redTeamCode }: { rows: readonly PositionComparisonViewModel[]; currentSeconds: number; championsById: Readonly<Record<string, ChampionViewModel>>; blueTeamCode: string; redTeamCode: string }) {
  return (
    <section className="rm-comparison" aria-labelledby="rm-comparison-heading">
      <header><h2 id="rm-comparison-heading">포지션별 선수 비교</h2><span>{formatMatchTime(currentSeconds)} 시점</span></header>
      <table>
        <thead><tr><th>{blueTeamCode} 선수 / 챔피언</th><th>K/D/A</th><th>CS</th><th>보유 골드</th><th className="rm-lead-cell"><span className="lm-sr-only">{blueTeamCode} 골드 우위</span></th><th className="rm-position-cell">포지션</th><th className="rm-lead-cell"><span className="lm-sr-only">{redTeamCode} 골드 우위</span></th><th>보유 골드</th><th>CS</th><th>K/D/A</th><th>{redTeamCode} 선수 / 챔피언</th></tr></thead>
        <tbody>{rows.map((row) => {
          const lead = row.blue.gold - row.red.gold;
          return <tr key={row.position} aria-label={`${row.position} ${row.blue.playerName} 대 ${row.red.playerName}`}>
            <td><PlayerCell player={row.blue} champion={championsById[row.blue.championId]} side="blue" /></td><td>{row.blue.kills}/{row.blue.deaths}/{row.blue.assists}</td><td>{row.blue.cs}</td><td>{formatGold(row.blue.gold)}</td><td className="rm-lead-cell">{lead > .04 ? <span className="rm-lead-badge rm-blue">{formatLead(lead)}</span> : null}</td><td className="rm-position-cell">{row.position}</td><td className="rm-lead-cell">{lead < -.04 ? <span className="rm-lead-badge rm-red">{formatLead(lead)}</span> : null}</td><td>{formatGold(row.red.gold)}</td><td>{row.red.cs}</td><td>{row.red.kills}/{row.red.deaths}/{row.red.assists}</td><td><PlayerCell player={row.red} champion={championsById[row.red.championId]} side="red" /></td>
          </tr>;
        })}</tbody>
      </table>
    </section>
  );
}

function PlayerCell({ player, champion, side }: { player: PositionComparisonViewModel['blue']; champion: ChampionViewModel; side: 'blue' | 'red' }) {
  return <div className={`rm-player-cell rm-${side}`}><span className="rm-portrait"><ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /></span><span><strong>{player.playerName}</strong><small>{champion.name} · Lv. {player.level}</small></span></div>;
}

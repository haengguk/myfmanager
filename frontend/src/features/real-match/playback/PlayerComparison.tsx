import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import { formatMatchTime } from '../realMatch.adapter';
import type { ChampionViewModel, PositionComparisonViewModel } from '../realMatch.types';

const formatLead = (value: number) => Math.abs(value) < 1000
  ? `+${Math.abs(value).toLocaleString('ko-KR')}G`
  : `+${(Math.abs(value) / 1000).toFixed(1)}K`;

export function PlayerComparison({ rows, currentSeconds, championsById, blueTeamCode, redTeamCode }: { rows: readonly PositionComparisonViewModel[]; currentSeconds: number; championsById: Readonly<Record<string, ChampionViewModel>>; blueTeamCode: string; redTeamCode: string }) {
  return (
    <section className="rm-comparison" aria-labelledby="rm-comparison-heading">
      <header><h2 id="rm-comparison-heading">포지션별 선수 비교</h2><span>{formatMatchTime(currentSeconds)} 시점</span></header>
      <table>
        <colgroup><col /><col /><col /><col /><col /><col /><col /></colgroup>
        <thead><tr><th>{blueTeamCode} KDA</th><th>{blueTeamCode} CS</th><th>{blueTeamCode} 선수 / 챔피언</th><th className="rm-matchup-lead-cell">골드 격차</th><th>{redTeamCode} 선수 / 챔피언</th><th>{redTeamCode} CS</th><th>{redTeamCode} KDA</th></tr></thead>
        <tbody>{rows.map((row) => {
          const lead = row.blue.gold - row.red.gold;
          const leadingCode = lead > 40 ? blueTeamCode : lead < -40 ? redTeamCode : null;
          return <tr key={row.position} aria-label={`${row.position} ${row.blue.playerName} 대 ${row.red.playerName}`}>
            <td><KdaCell player={row.blue} side="blue" /></td>
            <td><CsCell cs={row.blue.cs} /></td>
            <td><PlayerCell player={row.blue} champion={championsById[row.blue.championId]} side="blue" /></td>
            <td className="rm-matchup-lead-cell">{leadingCode
              ? <span className={`rm-matchup-lead is-${lead > 0 ? 'blue' : 'red'}`}>{leadingCode} {formatLead(lead)}</span>
              : <span className="rm-matchup-lead is-even">동률</span>}</td>
            <td><PlayerCell player={row.red} champion={championsById[row.red.championId]} side="red" /></td>
            <td><CsCell cs={row.red.cs} /></td>
            <td><KdaCell player={row.red} side="red" /></td>
          </tr>;
        })}</tbody>
      </table>
    </section>
  );
}

function PlayerCell({ player, champion, side }: { player: PositionComparisonViewModel['blue']; champion: ChampionViewModel; side: 'blue' | 'red' }) {
  return <div className={`rm-player-cell rm-${side}`}><span className="rm-portrait"><ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /></span><span><strong>{player.playerName}</strong><small>{champion.name} · Lv. {player.level}</small></span></div>;
}

function KdaCell({ player, side }: { player: PositionComparisonViewModel['blue']; side: 'blue' | 'red' }) {
  return <div className={`rm-kda-cell rm-${side}`}>
    <small>{player.shutdownBountyGold > 0 ? `현상금 +${player.shutdownBountyGold.toLocaleString('ko-KR')}G` : '현상금 없음'}</small>
    <strong>{player.kills}/{player.deaths}/{player.assists}</strong>
  </div>;
}

function CsCell({ cs }: { cs: number }) {
  return <strong className="rm-cs-cell">{cs}</strong>;
}
